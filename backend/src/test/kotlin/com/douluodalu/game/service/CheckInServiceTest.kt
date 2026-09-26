package com.douluodalu.game.service

import com.douluodalu.game.entity.CheckInEntity
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.CheckInRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDate

class CheckInServiceTest {
    @Mock
    private lateinit var checkInRepository: CheckInRepository

    @Mock
    private lateinit var profileRepo: PlayerProfileRepository

    @Mock
    private lateinit var dailyQuestService: DailyQuestService

    @InjectMocks
    private lateinit var checkInService: CheckInService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doAnswer { it.arguments[0] }.whenever(checkInRepository).save(any())
        doAnswer { it.arguments[0] }.whenever(profileRepo).save(any())
    }

    private fun profile() = PlayerProfileEntity(userId = 1L, level = 5)

    private fun lastRecord(checkDate: LocalDate, streak: Long, total: Long) = CheckInEntity(
        id = 99L, userId = 1L, checkDate = checkDate,
        streakAtSign = streak, totalDaysAtSign = total,
        cycleDay = (((streak - 1) % GameBalance.CHECK_IN_CYCLE) + 1).toInt()
    )

    @Test
    fun `first check-in should start streak at 1 and grant cycle day 1 reward`() {
        val p = profile()
        doReturn(null).whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(false).whenever(checkInRepository).existsByUserIdAndCheckDate(eq(1L), any())
        doReturn(p).whenever(profileRepo).findByUserId(1L)

        val result = checkInService.checkIn(1L)

        assertEquals(1L, result.streak)
        assertEquals(1L, result.totalDays)
        assertEquals(1, result.cycleDay)
        val expected = GameBalance.CHECK_IN_REWARDS[0]
        assertEquals(expected.gold, result.goldGained)
        assertEquals(expected.bossCoin, result.bossCoinGained)
        assertEquals(expected.soulPower, result.soulPowerGained)
        // 奖励入账到存档
        assertEquals(expected.gold, p.gold)
        assertEquals(expected.bossCoin, p.bossCoin)
        assertEquals(expected.soulPower, p.soulPower)
        val captor = ArgumentCaptor.forClass(CheckInEntity::class.java)
        verify(checkInRepository).save(captor.capture())
        assertEquals(1, captor.value.cycleDay)
        assertEquals(LocalDate.now(), captor.value.checkDate)
    }

    @Test
    fun `consecutive check-in after yesterday should extend streak and advance cycle day`() {
        val p = profile()
        doReturn(lastRecord(LocalDate.now().minusDays(1), streak = 3, total = 9))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(p).whenever(profileRepo).findByUserId(1L)

        val result = checkInService.checkIn(1L)

        assertEquals(4L, result.streak)
        assertEquals(10L, result.totalDays)
        assertEquals(4, result.cycleDay)
        val expected = GameBalance.CHECK_IN_REWARDS[3]
        assertEquals(expected.gold, result.goldGained)
        assertEquals(expected.gold, p.gold)
    }

    @Test
    fun `broken streak should reset streak to 1 and cycle day to 1`() {
        doReturn(lastRecord(LocalDate.now().minusDays(5), streak = 6, total = 20))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(profile()).whenever(profileRepo).findByUserId(1L)

        val result = checkInService.checkIn(1L)

        assertEquals(1L, result.streak)
        assertEquals(21L, result.totalDays) // 累计不因断签清零
        assertEquals(1, result.cycleDay)
    }

    @Test
    fun `same-day duplicate check-in should be rejected without any writes`() {
        doReturn(lastRecord(LocalDate.now(), streak = 2, total = 5))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)

        val ex = assertThrows(IllegalArgumentException::class.java) { checkInService.checkIn(1L) }

        assertEquals(CheckInService.ALREADY_SIGNED_MESSAGE, ex.message)
        verify(checkInRepository, never()).save(any())
        verify(profileRepo, never()).save(any())
    }

    @Test
    fun `exists pre-check should reject before save when the day was already recorded`() {
        doReturn(lastRecord(LocalDate.now().minusDays(1), streak = 3, total = 9))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(true).whenever(checkInRepository).existsByUserIdAndCheckDate(eq(1L), any())

        val ex = assertThrows(IllegalArgumentException::class.java) { checkInService.checkIn(1L) }

        assertEquals(CheckInService.ALREADY_SIGNED_MESSAGE, ex.message)
        verify(checkInRepository, never()).save(any())
    }

    @Test
    fun `concurrent double check-in should be normalized to already-signed by unique constraint`() {
        // 双击竞态：预检时都未落库，save 阶段后提交者撞 uk_user_date 唯一键
        doReturn(lastRecord(LocalDate.now().minusDays(1), streak = 3, total = 9))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(false).whenever(checkInRepository).existsByUserIdAndCheckDate(eq(1L), any())
        doThrow(DataIntegrityViolationException("uk_user_date")).whenever(checkInRepository).save(any())

        val ex = assertThrows(IllegalArgumentException::class.java) { checkInService.checkIn(1L) }

        assertEquals(CheckInService.ALREADY_SIGNED_MESSAGE, ex.message)
        verify(profileRepo, never()).save(any())
    }

    @Test
    fun `cycle should wrap back to day 1 after streak of 7`() {
        doReturn(lastRecord(LocalDate.now().minusDays(1), streak = 7, total = 7))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(profile()).whenever(profileRepo).findByUserId(1L)

        val result = checkInService.checkIn(1L)

        assertEquals(8L, result.streak)
        assertEquals(1, result.cycleDay)
        assertEquals(GameBalance.CHECK_IN_REWARDS[0].gold, result.goldGained)
    }

    @Test
    fun `getCheckInStatus should report signedToday and tomorrow's cycle day`() {
        doReturn(lastRecord(LocalDate.now(), streak = 3, total = 10))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)

        val status = checkInService.getCheckInStatus(1L)

        assertTrue(status.signedToday)
        assertEquals(3L, status.streak)
        assertEquals(10L, status.totalDays)
        assertEquals(4, status.nextCycleDay) // (3 % 7) + 1 = 明天的循环日
        assertEquals(GameBalance.CHECK_IN_REWARDS.size, status.rewards.size) // 固定 7 天全表
        assertEquals(7, status.rewards.last().day)
    }

    @Test
    fun `getCheckInStatus should return defaults for player without any record`() {
        doReturn(null).whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)

        val status = checkInService.getCheckInStatus(1L)

        assertFalse(status.signedToday)
        assertEquals(0L, status.streak)
        assertEquals(0L, status.totalDays)
        assertEquals(1, status.nextCycleDay)
        assertEquals(GameBalance.CHECK_IN_REWARDS.size, status.rewards.size)
    }

    // ==================== 每日任务挂点 ====================

    @Test
    fun `check-in should record checkin daily quest only on the successful path`() {
        doReturn(null).whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(false).whenever(checkInRepository).existsByUserIdAndCheckDate(eq(1L), any())
        doReturn(profile()).whenever(profileRepo).findByUserId(1L)

        checkInService.checkIn(1L)

        verify(dailyQuestService).recordCheckin(1L)

        // 当日重复签到（早退）不再计数：仍只有首次成功那 1 次
        doReturn(lastRecord(LocalDate.now(), streak = 1, total = 1))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        assertThrows(IllegalArgumentException::class.java) { checkInService.checkIn(1L) }
        verify(dailyQuestService).recordCheckin(1L)
    }
}
