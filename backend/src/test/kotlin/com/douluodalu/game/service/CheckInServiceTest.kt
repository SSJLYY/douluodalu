package com.douluodalu.game.service

import com.douluodalu.game.entity.CheckInEntity
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.CheckInRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
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

    /** 真实 Micrometer 注册表（计数器断言用；测试间 clear 防串扰） */
    private val meterRegistry = SimpleMeterRegistry()

    private lateinit var checkInService: CheckInService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doAnswer { it.arguments[0] }.whenever(checkInRepository).save(any())
        doAnswer { it.arguments[0] }.whenever(profileRepo).save(any())
        meterRegistry.clear()
        checkInService = CheckInService(checkInRepository, profileRepo, dailyQuestService, meterRegistry)
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

    // ==================== 签到业务计数器 ====================

    @Test
    fun `check-in success should increment micrometer counter`() {
        doReturn(null).whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(false).whenever(checkInRepository).existsByUserIdAndCheckDate(eq(1L), any())
        doReturn(profile()).whenever(profileRepo).findByUserId(1L)

        checkInService.checkIn(1L)

        assertEquals(1.0, meterRegistry.get(CheckInService.METRIC_CHECKIN_TOTAL).counter().count())
    }

    // ==================== 补签（第二十一轮） ====================

    @Test
    fun `makeup should reject when user has no check-in history`() {
        doReturn(null).whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)

        val result = checkInService.makeup(1L)

        assertFalse(result.success)
        verify(profileRepo, never()).findByUserId(any())
        verify(checkInRepository, never()).save(any())
    }

    @Test
    fun `makeup should reject when yesterday is the latest record`() {
        doReturn(lastRecord(LocalDate.now().minusDays(1), streak = 3, total = 9))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)

        val result = checkInService.makeup(1L)

        assertFalse(result.success)
        assertEquals(CheckInService.MAKEUP_ALREADY_MESSAGE, result.message)
        verify(checkInRepository, never()).save(any())
    }

    @Test
    fun `makeup should reject when yesterday is signed behind a today record`() {
        // 今天已签且昨天也签过（最近一条是今天行，昨日判定需查库）→ 无需补签
        doReturn(lastRecord(LocalDate.now(), streak = 4, total = 10))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(true).whenever(checkInRepository)
            .existsByUserIdAndCheckDate(1L, LocalDate.now().minusDays(1))

        val result = checkInService.makeup(1L)

        assertFalse(result.success)
        assertEquals(CheckInService.MAKEUP_ALREADY_MESSAGE, result.message)
        verify(checkInRepository, never()).save(any())
    }

    @Test
    fun `makeup should reject when gold is insufficient`() {
        doReturn(lastRecord(LocalDate.now().minusDays(2), streak = 3, total = 9))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(profile()).whenever(profileRepo).findByUserId(1L) // gold=0 < 500

        val result = checkInService.makeup(1L)

        assertFalse(result.success)
        assertTrue(result.message.contains("500"), result.message)
        verify(checkInRepository, never()).save(any())
        verify(profileRepo, never()).save(any())
    }

    @Test
    fun `makeup success should repair streak from pre-yesterday snapshot without granting rewards`() {
        // 前天签过（streak=3/total=9）、昨天断、今天未签 → 补昨天 streak=4/total=10
        val p = profile()
        p.gold = 1000L
        doReturn(lastRecord(LocalDate.now().minusDays(2), streak = 3, total = 9))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(lastRecord(LocalDate.now().minusDays(2), streak = 3, total = 9))
            .whenever(checkInRepository)
            .findFirstByUserIdAndCheckDateLessThanOrderByCheckDateDesc(eq(1L), any())
        doReturn(false).whenever(checkInRepository).existsByUserIdAndCheckDate(eq(1L), any())
        doReturn(p).whenever(profileRepo).findByUserId(1L)

        val result = checkInService.makeup(1L)

        assertTrue(result.success)
        assertEquals(4L, result.streak)
        assertEquals(10L, result.totalDays)
        assertEquals(500L, result.goldSpent)
        assertEquals(500L, p.gold) // 只扣 500 金
        assertEquals(0L, p.bossCoin) // 不补发当日奖励
        assertEquals(0L, p.soulPower)
        val captor = ArgumentCaptor.forClass(CheckInEntity::class.java)
        verify(checkInRepository).save(captor.capture())
        assertEquals(LocalDate.now().minusDays(1), captor.value.checkDate)
        assertEquals(4L, captor.value.streakAtSign)
        assertEquals(10L, captor.value.totalDaysAtSign)
        assertEquals(4, captor.value.cycleDay) // ((4-1)%7)+1
        // 补签是「昨天」的动作，不触发每日任务「完成今日签到」计数
        verify(dailyQuestService, never()).recordCheckin(any())
        // 业务计数器（ops 面板名逐字契约）
        assertEquals(1.0, meterRegistry.get(CheckInService.METRIC_CHECKIN_MAKEUP_TOTAL).counter().count())
    }

    @Test
    fun `signing today after makeup should continue the streak`() {
        // 补签昨天（streak=4）后今天正签 → streak 连续到 5（连签修复的前向语义）
        val p = profile()
        p.gold = 1000L
        doReturn(lastRecord(LocalDate.now().minusDays(2), streak = 3, total = 9))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(lastRecord(LocalDate.now().minusDays(2), streak = 3, total = 9))
            .whenever(checkInRepository)
            .findFirstByUserIdAndCheckDateLessThanOrderByCheckDateDesc(eq(1L), any())
        doReturn(false).whenever(checkInRepository).existsByUserIdAndCheckDate(eq(1L), any())
        doReturn(p).whenever(profileRepo).findByUserId(1L)

        val makeup = checkInService.makeup(1L)
        assertTrue(makeup.success)

        // 补签落库后，最近一条变为昨天行（mock 切换到补签后的世界状态）
        doReturn(lastRecord(LocalDate.now().minusDays(1), streak = makeup.streak, total = makeup.totalDays))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)

        val sign = checkInService.checkIn(1L)

        assertEquals(5L, sign.streak)
        assertEquals(11L, sign.totalDays)
        assertEquals(5, sign.cycleDay)
    }

    @Test
    fun `concurrent duplicate makeup should be normalized by unique constraint like check-in`() {
        // 并发双补同一「昨天」：save 撞 uk_user_date → 照 checkIn 惯例归一为 400
        doReturn(lastRecord(LocalDate.now().minusDays(2), streak = 3, total = 9))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(false).whenever(checkInRepository).existsByUserIdAndCheckDate(eq(1L), any())
        doReturn(profile().apply { gold = 1000L }).whenever(profileRepo).findByUserId(1L)
        doThrow(DataIntegrityViolationException("uk_user_date")).whenever(checkInRepository).save(any())

        val ex = assertThrows(IllegalArgumentException::class.java) { checkInService.makeup(1L) }

        assertEquals(CheckInService.MAKEUP_ALREADY_MESSAGE, ex.message)
        verify(profileRepo, never()).save(any())
    }

    @Test
    fun `makeup with no pre-yesterday base should start streak at 1`() {
        // 历史只有今天一条（首签当天就补昨天）：基准缺失 → streak=1，total=今天行快照+1
        doReturn(lastRecord(LocalDate.now(), streak = 1, total = 1))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(false).whenever(checkInRepository).existsByUserIdAndCheckDate(eq(1L), any())
        doReturn(null).whenever(checkInRepository)
            .findFirstByUserIdAndCheckDateLessThanOrderByCheckDateDesc(eq(1L), any())
        doReturn(profile().apply { gold = 1000L }).whenever(profileRepo).findByUserId(1L)

        val result = checkInService.makeup(1L)

        assertTrue(result.success)
        assertEquals(1L, result.streak)
        assertEquals(2L, result.totalDays)
        val captor = ArgumentCaptor.forClass(CheckInEntity::class.java)
        verify(checkInRepository).save(captor.capture())
        assertEquals(1, captor.value.cycleDay) // streak=1 → 循环第 1 天
    }

    // ==================== makeupAvailable 三态 ====================

    @Test
    fun `makeupAvailable should be false for a player who never signed in`() {
        doReturn(null).whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)

        assertFalse(checkInService.getCheckInStatus(1L).makeupAvailable)
    }

    @Test
    fun `makeupAvailable should be true when streak is broken`() {
        doReturn(lastRecord(LocalDate.now().minusDays(5), streak = 6, total = 20))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)

        assertTrue(checkInService.getCheckInStatus(1L).makeupAvailable)
    }

    @Test
    fun `makeupAvailable should be false when yesterday is the latest record`() {
        doReturn(lastRecord(LocalDate.now().minusDays(1), streak = 3, total = 9))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)

        assertFalse(checkInService.getCheckInStatus(1L).makeupAvailable)
    }

    @Test
    fun `makeupAvailable should be true after signing today while yesterday was missed`() {
        // 签了今天仍可补昨日（last=今天行、昨日无记录）
        doReturn(lastRecord(LocalDate.now(), streak = 1, total = 5))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(false).whenever(checkInRepository)
            .existsByUserIdAndCheckDate(1L, LocalDate.now().minusDays(1))

        assertTrue(checkInService.getCheckInStatus(1L).makeupAvailable)
    }

    @Test
    fun `makeupAvailable should be false when today and yesterday are both signed`() {
        doReturn(lastRecord(LocalDate.now(), streak = 4, total = 10))
            .whenever(checkInRepository).findFirstByUserIdOrderByCheckDateDesc(1L)
        doReturn(true).whenever(checkInRepository)
            .existsByUserIdAndCheckDate(1L, LocalDate.now().minusDays(1))

        assertFalse(checkInService.getCheckInStatus(1L).makeupAvailable)
    }
}
