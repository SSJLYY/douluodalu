package com.douluodalu.game.service

import com.douluodalu.game.dto.ClaimQuestResponse
import com.douluodalu.game.dto.DailyQuestDto
import com.douluodalu.game.dto.DailyQuestsDto
import com.douluodalu.game.entity.DailyQuestProgressEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.DailyQuestProgressRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 每日任务：进度记录（副路径，失败不击穿主流程）+ 状态查询（只读）+ 领奖。
 *
 * 设计要点：
 *  - 跨天重置：进度行按 quest_date 自然日隔离，新的一天从零开始，无需定时清理；
 *    日期口径与签到一致（JVM 本地时区 LocalDate.now()，serverTimezone=Asia/Shanghai）。
 *  - 计数：条件原子 UPDATE（DB 侧串行累加，重复记录天然幂等即防抖），UPDATE 返回 0
 *    说明当天还没有行 → INSERT（progress=1）；并发首记撞 uk_user_date_quest 唯一键时
 *    捕获 DataIntegrityViolationException 重试一次原子 UPDATE。
 *  - 领取：条件原子 UPDATE（claimed=0 AND progress>=target）防重复领取/并发双花，
 *    rows==1 才发奖；rows==0 时读行归因（未达标 / 已领取 / 未知任务）。
 *  - 无循环依赖：本类只依赖仓库，不反向依赖 GameService/ShopService/CheckInService；
 *    读路径（getTodayStatus）不建行、readOnly 语义由 GameService.getGameState 的只读事务托管。
 */
@Service
class DailyQuestService(
    private val dailyQuestRepo: DailyQuestProgressRepository,
    private val profileRepo: PlayerProfileRepository,
    /** 业务计数器（Micrometer，Spring Boot 自动配置 bean）；测试注入 SimpleMeterRegistry */
    private val meterRegistry: MeterRegistry
) {
    companion object {
        private val log = LoggerFactory.getLogger(DailyQuestService::class.java)

        /** 与 DTO 契约一致的固定文案：IllegalArgumentException → GlobalExceptionHandler 统一 400 */
        const val MSG_NOT_ENOUGH = "任务未达标"
        const val MSG_ALREADY_CLAIMED = "今日已领取"
        const val MSG_UNKNOWN = "未知任务"

        /** 业务计数器名（ops Grafana 面板按名建面板，逐字契约，勿改） */
        const val METRIC_QUEST_CLAIM_TOTAL = "douluo.quest.claim.total"
    }

    /**
     * 计数入口（副路径）：由各玩法在主流程成功出口调用。
     * 任何异常（DB 抖动、约束冲突重试后仍失败等）都吞掉记 warn —— 每日任务是
     * 战斗/修炼/爬塔/签到/购物主流程的附属功能，绝不能击穿主流程。
     */
    @Transactional
    fun recordProgress(userId: Long, questId: String) {
        try {
            val today = LocalDate.now()
            if (dailyQuestRepo.incrementProgress(userId, today, questId) == 0) {
                // 当天还没有该任务的行 → 首次计数建行
                try {
                    dailyQuestRepo.save(
                        DailyQuestProgressEntity(userId = userId, questDate = today, questId = questId, progress = 1)
                    )
                } catch (e: DataIntegrityViolationException) {
                    // 并发首记竞态：另一个请求刚插入当天首行 → 重试一次原子 UPDATE（幂等累加）
                    dailyQuestRepo.incrementProgress(userId, today, questId)
                }
            }
        } catch (e: Exception) {
            // 副路径兜底：计数失败只记日志，继续主流程
            log.warn("每日任务计数失败（副路径，忽略）：userId={} questId={}", userId, questId, e)
        }
    }

    // ===== 五个计数薄方法（questId 映射收敛于此，玩法侧无需感知任务定义）=====
    fun recordBattleWin(userId: Long) = recordProgress(userId, GameBalance.QUEST_BATTLE_WINS)

    fun recordCultivate(userId: Long) = recordProgress(userId, GameBalance.QUEST_CULTIVATE)

    /** 挑战即计数，不论胜负 */
    fun recordTower(userId: Long) = recordProgress(userId, GameBalance.QUEST_TOWER)

    fun recordCheckin(userId: Long) = recordProgress(userId, GameBalance.QUEST_CHECKIN)

    fun recordShopBuy(userId: Long) = recordProgress(userId, GameBalance.QUEST_SHOP_BUY)

    /**
     * 每日任务面板（只读）：读当天全部进度行（一次查询）与定义表合成；
     * quests 固定返回全部任务定义（缺行 progress=0），不写库。
     */
    fun getTodayStatus(userId: Long): DailyQuestsDto {
        val today = LocalDate.now()
        val rows = dailyQuestRepo.findByUserIdAndQuestDate(userId, today).associateBy { it.questId }
        return DailyQuestsDto(
            date = today.toString(),
            quests = GameBalance.DAILY_QUESTS.map { def ->
                val row = rows[def.id]
                DailyQuestDto(
                    id = def.id,
                    description = def.description,
                    target = def.target,
                    progress = row?.progress ?: 0,
                    claimed = row?.claimed ?: false,
                    rewardGold = def.rewardGold,
                    rewardBossCoin = def.rewardBossCoin,
                    rewardSoulPower = def.rewardSoulPower
                )
            }
        )
    }

    /**
     * 领取每日任务奖励：条件原子 UPDATE 抢占领取资格（防并发双花），命中后才发奖。
     * 失败归因：任务 id 不在定义表才是「未知任务」；已知 id 当天无进度行视同 progress=0 归「未达标」。
     */
    @Transactional
    fun claim(userId: Long, questId: String): ClaimQuestResponse {
        val def = GameBalance.DAILY_QUEST_BY_ID[questId] ?: throw IllegalArgumentException(MSG_UNKNOWN)
        val today = LocalDate.now()
        val granted = dailyQuestRepo.claimIfEligible(userId, today, questId, def.target)
        if (granted == 1) {
            val profile = profileRepo.findByUserId(userId)
                ?: throw IllegalStateException("玩家存档不存在")
            profile.gold += def.rewardGold
            profile.bossCoin += def.rewardBossCoin
            profile.soulPower += def.rewardSoulPower
            profile.updatedAt = LocalDateTime.now()
            profileRepo.save(profile)
            // 业务计数器：领奖成功（Micrometer 计数是内存操作不抛业务异常，无需 try/catch，不影响主流程）
            meterRegistry.counter(METRIC_QUEST_CLAIM_TOTAL, "questId", questId).increment()
            return ClaimQuestResponse(
                questId = questId,
                goldGained = def.rewardGold,
                bossCoinGained = def.rewardBossCoin,
                soulPowerGained = def.rewardSoulPower
            )
        }
        // 条件 UPDATE 未命中：读行区分失败原因（无行 = 当天从未计数，progress 视为 0）
        val row = dailyQuestRepo.findByUserIdAndQuestDateAndQuestId(userId, today, questId)
        if (row != null && row.claimed) throw IllegalArgumentException(MSG_ALREADY_CLAIMED)
        throw IllegalArgumentException(MSG_NOT_ENOUGH)
    }
}
