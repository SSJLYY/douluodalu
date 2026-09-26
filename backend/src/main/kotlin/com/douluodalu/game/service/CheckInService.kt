package com.douluodalu.game.service

import com.douluodalu.game.dto.CheckInResult
import com.douluodalu.game.dto.CheckInRewardDto
import com.douluodalu.game.dto.CheckInStatusDto
import com.douluodalu.game.entity.CheckInEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.CheckInRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 每日签到：7 日循环奖励。写操作全在本类 checkIn()；状态查询（getCheckInStatus）
 * 只读，供 GameService.getGameState 在其 readOnly 事务内组装。
 */
@Service
class CheckInService(
    private val checkInRepository: CheckInRepository,
    private val profileRepo: PlayerProfileRepository,
    private val dailyQuestService: DailyQuestService
) {
    companion object {
        /** 与 DTO 契约一致的固定文案：当日已签（含并发双击竞态）统一归一为此异常 → 400 */
        const val ALREADY_SIGNED_MESSAGE = "今日已签到，明天再来吧"
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
            ?: throw IllegalStateException("玩家存档不存在")
        profile.gold += reward.gold
        profile.bossCoin += reward.bossCoin
        profile.soulPower += reward.soulPower
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)

        // 每日任务挂点（副路径）：完成今日签到即计数，失败不击穿签到主流程（见 DailyQuestService）
        dailyQuestService.recordCheckin(userId)

        return CheckInResult(
            goldGained = reward.gold,
            bossCoinGained = reward.bossCoin,
            soulPowerGained = reward.soulPower,
            streak = streak,
            totalDays = total,
            cycleDay = cycleDay
        )
    }

    /** 签到状态（只读）：rewards 固定返回 7 天循环全表，前端渲染预览格用 */
    fun getCheckInStatus(userId: Long): CheckInStatusDto {
        val today = LocalDate.now()
        val last = checkInRepository.findFirstByUserIdOrderByCheckDateDesc(userId)
        val streak = last?.streakAtSign ?: 0L
        return CheckInStatusDto(
            signedToday = last?.checkDate == today,
            streak = streak,
            totalDays = last?.totalDaysAtSign ?: 0L,
            nextCycleDay = ((streak % GameBalance.CHECK_IN_CYCLE) + 1).toInt(),
            rewards = GameBalance.CHECK_IN_REWARDS.map {
                CheckInRewardDto(day = it.day, gold = it.gold, bossCoin = it.bossCoin, soulPower = it.soulPower)
            }
        )
    }
}
