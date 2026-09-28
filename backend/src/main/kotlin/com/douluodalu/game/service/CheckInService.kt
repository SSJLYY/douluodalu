package com.douluodalu.game.service

import com.douluodalu.game.dto.CheckInResult
import com.douluodalu.game.dto.CheckInRewardDto
import com.douluodalu.game.dto.CheckInStatusDto
import com.douluodalu.game.dto.MakeupResponse
import com.douluodalu.game.entity.CheckInEntity
import com.douluodalu.game.exception.PlayerSaveNotFoundException
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.CheckInRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 每日签到：7 日循环奖励 + 补签。写操作在本类 checkIn()/makeup()；状态查询（getCheckInStatus）
 * 只读，供 GameService.getGameState 在其 readOnly 事务内组装。
 *
 * 补签（第二十一轮）语义边界：
 *  - 只能补「昨天」这一天，花费 GameBalance.CHECKIN_MAKEUP_COST_GOLD；
 *  - 只修复连签、【不补发】当日循环奖励（金币/Boss币/魂力一律不发），也不触发每日任务的
 *    「完成今日签到」计数（该任务口径是今天签了没有，补昨天不改变其进度）；
 *  - 补签记录的 cycleDay/快照按正常签到口径计算（见 makeup 内注释）；
 *  - uk_user_date 唯一键天然防同一自然日重复落库（预检 + DIVE 兜底，同 checkIn）。
 */
@Service
class CheckInService(
    private val checkInRepository: CheckInRepository,
    private val profileRepo: PlayerProfileRepository,
    private val dailyQuestService: DailyQuestService,
    /** 业务计数器（Micrometer，Spring Boot 自动配置 bean）；测试注入 SimpleMeterRegistry */
    private val meterRegistry: MeterRegistry
) {
    companion object {
        /** 与 DTO 契约一致的固定文案：当日已签（含并发双击竞态）统一归一为此异常 → 400 */
        const val ALREADY_SIGNED_MESSAGE = "今日已签到，明天再来吧"

        /** 补签失败固定文案：昨日已有记录（预检 200+success=false；DIVE 竞态归一为同文案 400） */
        const val MAKEUP_ALREADY_MESSAGE = "昨日已签到，无需补签"

        // ===== 业务计数器名（ops Grafana 面板按名建面板，逐字契约，勿改） =====
        const val METRIC_CHECKIN_TOTAL = "douluo.checkin.total"
        const val METRIC_CHECKIN_MAKEUP_TOTAL = "douluo.checkin.makeup.total"
    }

    @Transactional
    fun checkIn(userId: Long): CheckInResult {
        val today = LocalDate.now()
        val last = checkInRepository.findFirstByUserIdOrderByCheckDateDesc(userId)
        if (last?.checkDate == today) throw IllegalArgumentException(ALREADY_SIGNED_MESSAGE)

        // 连续判定：昨天签过 → streak+1，断了（或首签）→ 重置为 1
        val streak = if (last != null && last.checkDate == today.minusDays(1)) last.streakAtSign + 1L else 1L
        val total = (last?.totalDaysAtSign ?: 0L) + 1
        val cycleDay = (((streak - 1) % GameBalance.CHECK_IN_CYCLE) + 1).toInt()
        val reward = GameBalance.CHECK_IN_REWARDS[cycleDay - 1]

        // 落库前预检 + 唯一键兜底：并发双击时后提交者捕获唯一约束冲突，
        // 归一为「今日已签到」400，防止同一自然日双发奖励
        if (checkInRepository.existsByUserIdAndCheckDate(userId, today)) {
            throw IllegalArgumentException(ALREADY_SIGNED_MESSAGE)
        }
        try {
            checkInRepository.save(
                CheckInEntity(
                    userId = userId,
                    checkDate = today,
                    streakAtSign = streak,
                    totalDaysAtSign = total,
                    cycleDay = cycleDay
                )
            )
        } catch (e: DataIntegrityViolationException) {
            throw IllegalArgumentException(ALREADY_SIGNED_MESSAGE)
        }

        val profile = profileRepo.findByUserId(userId)
            ?: throw PlayerSaveNotFoundException()
        profile.gold += reward.gold
        profile.bossCoin += reward.bossCoin
        profile.soulPower += reward.soulPower
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)

        // 每日任务挂点（副路径）：完成今日签到即计数，失败不击穿签到主流程（见 DailyQuestService）
        dailyQuestService.recordCheckin(userId)

        // 业务计数器：签到成功（Micrometer 计数是内存操作不抛业务异常，无需 try/catch，不影响主流程）
        counter(METRIC_CHECKIN_TOTAL)

        return CheckInResult(
            goldGained = reward.gold,
            bossCoinGained = reward.bossCoin,
            soulPowerGained = reward.soulPower,
            streak = streak,
            totalDays = total,
            cycleDay = cycleDay
        )
    }

    /**
     * 补签（第二十一轮）：只能补「昨天」，花费 CHECKIN_MAKEUP_COST_GOLD 修复连签，不补发当日奖励。
     * 失败语义照 breakthrough：无历史签到 / 昨日已签 / 金币不足 → HTTP 200 + success=false + message。
     *
     * 连签修复数学（快照按正常签到口径计算）：
     *  - streak = 「昨天之前最后一条记录」的 streakAtSign + 1（基准可能是前天——单日断档的标准修复，
     *    也可能更早——只能补昨天一天，隔多天断档时单次补签只把「昨天」接回链上；基准不存在
     *    （历史全部落在今天及以后）→ 视为基准 streak 0，补签 streak=1）；
     *  - totalDays = 最近一条记录（按日期倒序第一条）的 totalDaysAtSign + 1——补签新增一个已签
     *    自然日，累计口径与「今天已签后再补昨天」（last=今天行）和「今天未签直接补」（last=基准行）
     *    两种次序都自洽：补签后总记录数 = 总快照数；
     *  - cycleDay = ((streak-1) % 7) + 1，与 checkIn 同式；
     *  - 补签后「今天再正签 / 明天再正签」读到昨天行即为昨天 → streak 天然连续（正常 checkIn 逻辑）。
     *  注意：若玩家先签了今天（断签重置 streak=1）再补昨天，今天行快照不回改——前向连签从
     *  明天起以今天行（streak=1）为基准继续，补签在该次序下只修复累计天数不修复前向 streak
     *  （历史链已修复），属「只能补昨天」规则的固有边界。
     */
    @Transactional
    fun makeup(userId: Long): MakeupResponse {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val last = checkInRepository.findFirstByUserIdOrderByCheckDateDesc(userId)
        // 失败①：无任何历史签到——没有可修复的连签链
        if (last == null) {
            return MakeupResponse(success = false, message = "暂无签到历史，无可补签记录")
        }
        // 失败②：昨日已有记录（昨天直接签的；或最近一条是今天、昨天也已签过）
        if (last.checkDate == yesterday || checkInRepository.existsByUserIdAndCheckDate(userId, yesterday)) {
            return MakeupResponse(success = false, message = MAKEUP_ALREADY_MESSAGE)
        }
        val profile = profileRepo.findByUserId(userId)
            ?: throw PlayerSaveNotFoundException()
        // 失败③：金币不足
        if (profile.gold < GameBalance.CHECKIN_MAKEUP_COST_GOLD) {
            return MakeupResponse(
                success = false,
                message = "补签需要${GameBalance.CHECKIN_MAKEUP_COST_GOLD}金币（当前${profile.gold}）"
            )
        }
        val base = checkInRepository.findFirstByUserIdAndCheckDateLessThanOrderByCheckDateDesc(userId, yesterday)
        val streak = (base?.streakAtSign ?: 0L) + 1
        val total = last.totalDaysAtSign + 1
        val cycleDay = (((streak - 1) % GameBalance.CHECK_IN_CYCLE) + 1).toInt()
        // uk_user_date 兜底：并发双补同一「昨天」时后提交者撞唯一键，照 checkIn 惯例归一为 400。
        // 注：这里不能静默 return success=false——DIVE 穿过仓库事务边界时已把当前事务标记
        // rollback-only，正常返回会在提交时炸 UnexpectedRollbackException，必须以异常结束事务。
        try {
            checkInRepository.save(
                CheckInEntity(
                    userId = userId,
                    checkDate = yesterday,
                    streakAtSign = streak,
                    totalDaysAtSign = total,
                    cycleDay = cycleDay
                )
            )
        } catch (e: DataIntegrityViolationException) {
            throw IllegalArgumentException(MAKEUP_ALREADY_MESSAGE)
        }
        profile.gold -= GameBalance.CHECKIN_MAKEUP_COST_GOLD
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)

        // 业务计数器：补签成功（同 checkIn，Micrometer 不抛业务异常）
        counter(METRIC_CHECKIN_MAKEUP_TOTAL)

        return MakeupResponse(
            success = true,
            streak = streak,
            totalDays = total,
            goldSpent = GameBalance.CHECKIN_MAKEUP_COST_GOLD,
            message = "补签成功！连签已修复（补签只修复连签，不补发当日奖励）"
        )
    }

    /** 签到状态（只读）：rewards 固定返回 7 天循环全表，前端渲染预览格用 */
    fun getCheckInStatus(userId: Long): CheckInStatusDto {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val last = checkInRepository.findFirstByUserIdOrderByCheckDateDesc(userId)
        val streak = last?.streakAtSign ?: 0L
        // 补签可用性（精确三态）：有历史签到 且 昨日无记录。
        //  - 从未签到（last=null）→ false；
        //  - 最近一条就是昨天 → 昨日已签 false；
        //  - 最近一条是今天 → 昨日是否已签需查库（签了今天仍可补昨日——不影响本判定）；
        //  - 最近一条早于昨天 → 它之后无更晚记录，昨日必无记录 → true（断签可补）。
        val signedYesterday = when {
            last == null -> false
            last.checkDate == yesterday -> true
            last.checkDate == today -> checkInRepository.existsByUserIdAndCheckDate(userId, yesterday)
            else -> false
        }
        return CheckInStatusDto(
            signedToday = last?.checkDate == today,
            streak = streak,
            totalDays = last?.totalDaysAtSign ?: 0L,
            nextCycleDay = ((streak % GameBalance.CHECK_IN_CYCLE) + 1).toInt(),
            rewards = GameBalance.CHECK_IN_REWARDS.map {
                CheckInRewardDto(day = it.day, gold = it.gold, bossCoin = it.bossCoin, soulPower = it.soulPower)
            },
            makeupAvailable = last != null && !signedYesterday
        )
    }

    /** 业务计数器薄封装：Micrometer 计数为内存操作、不抛业务异常，直接增量即可（不影响主流程） */
    private fun counter(name: String, vararg tags: String) {
        meterRegistry.counter(name, *tags).increment()
    }
}
