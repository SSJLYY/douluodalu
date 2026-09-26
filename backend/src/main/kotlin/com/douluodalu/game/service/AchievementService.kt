package com.douluodalu.game.service

import com.douluodalu.game.dto.AchievementDto
import com.douluodalu.game.dto.AchievementRewardDto
import com.douluodalu.game.entity.AchievementEntity
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.AchievementRepository
import com.douluodalu.game.repository.EquippedRingRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 成就系统：自动解锁 + 属性加成即时生效，无领取端点。
 *
 * 设计要点：
 *  - 解锁模型：奖励是永久属性加成（hp/atk，第一版战斗模型只消费这两项），不存在「领取」
 *    动作——解锁记录行（achievement_record）本身就是事实源，属性口径按已解锁集合求和，
 *    天然幂等无双花（与 EquipmentPowerService.achievementBonus 纯函数同源）。
 *  - 解锁：save 记录行 + 捕获 DataIntegrityViolationException 幂等吞掉（条件原子 INSERT
 *    不可行；并发双解锁同一行由 V9 uk_user_achievement 唯一键兜底）。
 *  - 副路径：sync 由各玩法主流程在成功出口调用，任何异常全吞记 warn，绝不击穿主流程。
 *  - 无循环依赖：本类只依赖仓库（Achievement/PlayerProfile/EquippedRing），不反向依赖
 *    GameService/EquipmentPowerService 实例（加成求和走 EquipmentPowerService 的 companion
 *    纯函数 achievementBonus，非 Bean 注入）。
 *  - 进度口径映射收敛在 companion 纯函数 progressOf：CULTIVATION→level、BATTLE→totalBattleWins、
 *    TOWER→towerFloor、SOUL_RING→已装备魂环数、PRESTIGE→prestigeCount（写点：GameService.prestige）。
 */
@Service
class AchievementService(
    private val achievementRepo: AchievementRepository,
    private val profileRepo: PlayerProfileRepository,
    private val equippedRingRepo: EquippedRingRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(AchievementService::class.java)

        /**
         * 进度口径映射（纯函数，LongRunSimulationTest 镜像同源）：
         * 成就类别 → 镜像玩家状态上的当前进度值。
         */
        fun progressOf(
            category: String,
            level: Int,
            totalBattleWins: Long,
            towerFloor: Int,
            equippedRingCount: Int,
            prestigeCount: Int
        ): Long = when (category) {
            "CULTIVATION" -> level.toLong()
            "BATTLE" -> totalBattleWins
            "TOWER" -> towerFloor.toLong()
            "SOUL_RING" -> equippedRingCount.toLong()
            "PRESTIGE" -> prestigeCount.toLong()
            else -> 0L
        }

        private fun progressOf(def: GameBalance.AchievementDef, profile: PlayerProfileEntity, equippedRingCount: Int): Long =
            progressOf(def.category, profile.level, profile.totalBattleWins, profile.towerFloor, equippedRingCount, profile.prestigeCount)
    }

    /**
     * 成就同步（副路径）：读存档一次 + 已装备环数一次 + 已解锁 id 集一次，遍历全部定义
     * 算进度，达标的解锁（save 记录行）。任何异常全吞记 warn —— 成就是玩法主流程的附属，
     * 绝不能击穿战斗/修炼/爬塔/离线/装环。
     */
    @Transactional
    fun sync(userId: Long) {
        try {
            val profile = profileRepo.findByUserId(userId) ?: return
            val ringCount = equippedRingRepo.findByUserId(userId).size
            val unlocked = achievementRepo.findByUserId(userId).map { it.achievementId }.toHashSet()
            for (def in GameBalance.AchievementDefs.all) {
                if (def.id in unlocked) continue
                if (progressOf(def, profile, ringCount) < def.requiredValue) continue
                try {
                    achievementRepo.save(
                        AchievementEntity(userId = userId, achievementId = def.id, unlockedAt = LocalDateTime.now())
                    )
                } catch (e: DataIntegrityViolationException) {
                    // 并发双解锁竞态：uk_user_achievement 兜底——记录行已存在即视为解锁成功，幂等吞掉
                    log.warn("成就并发解锁撞唯一键（幂等吞掉）：userId={} achievementId={}", userId, def.id)
                }
            }
        } catch (e: Exception) {
            // 副路径兜底：同步失败只记日志，继续主流程
            log.warn("成就同步失败（副路径，忽略）：userId={}", userId, e)
        }
    }

    /**
     * 成就面板（只读合成）：定义表 × 实时进度 × 已解锁集合（含 unlockedAt），缺省 unlocked=false。
     * 不写库；readOnly 语义由 GameService.getGameState 的只读事务托管。
     */
    fun getStatus(userId: Long): List<AchievementDto> {
        val profile = profileRepo.findByUserId(userId)
        val unlockedRows = achievementRepo.findByUserId(userId).associateBy { it.achievementId }
        val ringCount = profile?.let { equippedRingRepo.findByUserId(userId).size } ?: 0
        return GameBalance.AchievementDefs.all.map { def ->
            val row = unlockedRows[def.id]
            AchievementDto(
                id = def.id,
                name = def.name,
                description = def.description,
                category = def.category,
                target = def.requiredValue,
                progress = profile?.let { progressOf(def, it, ringCount) } ?: 0L,
                unlocked = row != null,
                unlockedAt = row?.unlockedAt?.toLocalDate()?.toString(),
                rewards = AchievementRewardDto(
                    hp = def.rewards.hp, atk = def.rewards.atk, matk = def.rewards.matk,
                    pdef = def.rewards.pdef, mdef = def.rewards.mdef,
                    critRate = def.rewards.critRate, critDmg = def.rewards.critDmg
                )
            )
        }
    }

    /**
     * 已解锁成就的七字段属性加成求和（供 GameService.getGameState 状态组装；
     * 与解锁记录行天然同源、幂等无双花）。第十七轮战斗模型扩展起七字段全消费。
     */
    fun unlockedBonus(userId: Long): EquipmentBonus =
        EquipmentPowerService.achievementBonus(achievementRepo.findByUserId(userId).map { it.achievementId })
}
