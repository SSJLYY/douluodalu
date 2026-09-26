package com.douluodalu.game.service

import com.douluodalu.game.entity.AchievementEntity
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.AchievementRepository
import com.douluodalu.game.repository.EquippedRingRepository
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
import java.time.LocalDateTime

class AchievementServiceTest {
    @Mock
    private lateinit var achievementRepo: AchievementRepository

    @Mock
    private lateinit var profileRepo: PlayerProfileRepository

    @Mock
    private lateinit var equippedRingRepo: EquippedRingRepository

    @InjectMocks
    private lateinit var achievementService: AchievementService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    private fun profile(
        level: Int = 1,
        wins: Long = 0,
        towerFloor: Int = 0,
        prestige: Int = 0
    ) = PlayerProfileEntity(userId = 1L, level = level, totalBattleWins = wins, towerFloor = towerFloor, prestigeCount = prestige)

    private fun row(id: String, at: LocalDateTime = LocalDateTime.of(2026, 9, 26, 10, 30)) =
        AchievementEntity(userId = 1L, achievementId = id, unlockedAt = at)

    private fun rings(n: Int) = (0 until n).map { EquippedRing(userId = 1L, slotIndex = it, ringId = it + 1L) }

    // ==================== sync 解锁 ====================

    @Test
    fun `first qualifying achievement should insert a record row`() {
        doReturn(profile(level = 10)).whenever(profileRepo).findByUserId(1L)

        achievementService.sync(1L)

        val captor = ArgumentCaptor.forClass(AchievementEntity::class.java)
        verify(achievementRepo).save(captor.capture())
        assertEquals(1L, captor.value.userId)
        assertEquals("cult_10", captor.value.achievementId)
        assertEquals(GameBalance.ACHIEVEMENT_BY_ID["cult_10"]!!.requiredValue, 10L)
    }

    @Test
    fun `already unlocked achievements should not be re-saved`() {
        doReturn(profile(level = 50)).whenever(profileRepo).findByUserId(1L)
        doReturn(listOf(row("cult_10"), row("cult_30"))).whenever(achievementRepo).findByUserId(1L)

        achievementService.sync(1L)

        // level=50 只解锁尚未入库的 cult_50；cult_10/cult_30 已解锁绝不重复建行
        val captor = ArgumentCaptor.forClass(AchievementEntity::class.java)
        verify(achievementRepo, times(1)).save(captor.capture())
        assertEquals("cult_50", captor.value.achievementId)
    }

    @Test
    fun `concurrent unlock hitting unique key should be swallowed idempotently`() {
        // 并发双解锁同一成就：save 撞 uk_user_achievement → DataIntegrityViolationException 幂等吞掉，
        // 记录行已存在即视为解锁成功，且不阻断同一轮其余成就的解锁
        doReturn(profile(level = 30)).whenever(profileRepo).findByUserId(1L)
        doThrow(DataIntegrityViolationException("uk_user_achievement")).whenever(achievementRepo).save(any())

        assertDoesNotThrow { achievementService.sync(1L) }

        verify(achievementRepo, times(2)).save(any()) // cult_10 + cult_30 都尝试过
    }

    @Test
    fun `sync failure should be swallowed to protect the main game flow`() {
        // 副路径兜底：同步链路任何异常（DB 抖动等）都不得击穿战斗/修炼/爬塔/离线/装环主流程
        doThrow(RuntimeException("db down")).whenever(profileRepo).findByUserId(1L)

        assertDoesNotThrow { achievementService.sync(1L) }

        verify(achievementRepo, never()).save(any())
    }

    @Test
    fun `sync with missing profile should be a no-op`() {
        doReturn(null).whenever(profileRepo).findByUserId(1L)

        achievementService.sync(1L)

        verify(achievementRepo, never()).findByUserId(any())
        verify(equippedRingRepo, never()).findByUserId(any())
        verify(achievementRepo, never()).save(any())
    }

    // ==================== 五类进度口径映射 ====================

    @Test
    fun `cultivation progress should map to level`() {
        doReturn(profile(level = 30)).whenever(profileRepo).findByUserId(1L)

        achievementService.sync(1L)

        val captor = ArgumentCaptor.forClass(AchievementEntity::class.java)
        verify(achievementRepo, times(2)).save(captor.capture())
        assertEquals(listOf("cult_10", "cult_30"), captor.allValues.map { it.achievementId })
    }

    @Test
    fun `battle progress should map to totalBattleWins`() {
        doReturn(profile(wins = 60)).whenever(profileRepo).findByUserId(1L)

        achievementService.sync(1L)

        val captor = ArgumentCaptor.forClass(AchievementEntity::class.java)
        verify(achievementRepo, times(2)).save(captor.capture())
        assertEquals(listOf("battle_10", "battle_50"), captor.allValues.map { it.achievementId })
    }

    @Test
    fun `tower progress should map to towerFloor`() {
        doReturn(profile(towerFloor = 30)).whenever(profileRepo).findByUserId(1L)

        achievementService.sync(1L)

        val captor = ArgumentCaptor.forClass(AchievementEntity::class.java)
        verify(achievementRepo, times(2)).save(captor.capture())
        assertEquals(listOf("tower_10", "tower_30"), captor.allValues.map { it.achievementId })
    }

    @Test
    fun `soul ring progress should map to equipped ring count`() {
        // 口径 = 已装备魂环数（背包里的环不算）：3 环解锁 ring_1/ring_3，ring_5 不解锁
        doReturn(profile()).whenever(profileRepo).findByUserId(1L)
        doReturn(rings(3)).whenever(equippedRingRepo).findByUserId(1L)

        achievementService.sync(1L)

        val captor = ArgumentCaptor.forClass(AchievementEntity::class.java)
        verify(achievementRepo, times(2)).save(captor.capture())
        assertEquals(listOf("ring_1", "ring_3"), captor.allValues.map { it.achievementId })
    }

    @Test
    fun `prestige progress should map to prestigeCount`() {
        // prestigeCount 当前无写点（待转生玩法）：0 进度不解锁；手造 1 次即解锁 prestige_1
        doReturn(profile(prestige = 1)).whenever(profileRepo).findByUserId(1L)

        achievementService.sync(1L)

        val captor = ArgumentCaptor.forClass(AchievementEntity::class.java)
        verify(achievementRepo, times(1)).save(captor.capture())
        assertEquals("prestige_1", captor.value.achievementId)
    }

    @Test
    fun `below-threshold progress should not unlock anything`() {
        doReturn(profile(level = 5, wins = 9, towerFloor = 9)).whenever(profileRepo).findByUserId(1L)
        doReturn(emptyList<EquippedRing>()).whenever(equippedRingRepo).findByUserId(1L)

        achievementService.sync(1L)

        verify(achievementRepo, never()).save(any())
    }

    // ==================== getStatus 状态合成 ====================

    @Test
    fun `status should synthesize all defs with live progress and default unlocked false`() {
        doReturn(profile(level = 12, wins = 15, towerFloor = 3)).whenever(profileRepo).findByUserId(1L)
        doReturn(emptyList<AchievementEntity>()).whenever(achievementRepo).findByUserId(1L)
        doReturn(rings(1)).whenever(equippedRingRepo).findByUserId(1L)

        val status = achievementService.getStatus(1L)

        // 固定返回全部定义，顺序与定义表一致
        assertEquals(GameBalance.AchievementDefs.all.map { it.id }, status.map { it.id })
        // 未解锁（无记录行）缺省 unlocked=false，即使进度已达标（解锁以 sync 落库为准）
        status.forEach { dto -> assertFalse(dto.unlocked); assertNull(dto.unlockedAt) }
        // 实时进度按口径映射
        assertEquals(12L, status.first { it.id == "cult_10" }.progress)
        assertEquals(15L, status.first { it.id == "battle_10" }.progress)
        assertEquals(3L, status.first { it.id == "tower_10" }.progress)
        assertEquals(1L, status.first { it.id == "ring_1" }.progress)
        assertEquals(0L, status.first { it.id == "prestige_1" }.progress)
    }

    @Test
    fun `status targets and rewards should mirror the balance definition table`() {
        doReturn(profile()).whenever(profileRepo).findByUserId(1L)

        val status = achievementService.getStatus(1L)

        status.forEach { dto ->
            val def = GameBalance.ACHIEVEMENT_BY_ID[dto.id]!!
            assertEquals(def.name, dto.name)
            assertEquals(def.description, dto.description)
            assertEquals(def.category, dto.category)
            assertEquals(def.requiredValue, dto.target)
            assertEquals(def.rewards.hp, dto.rewards.hp)
            assertEquals(def.rewards.atk, dto.rewards.atk)
            assertEquals(def.rewards.matk, dto.rewards.matk)
            assertEquals(def.rewards.pdef, dto.rewards.pdef)
            assertEquals(def.rewards.mdef, dto.rewards.mdef)
            assertEquals(def.rewards.critRate, dto.rewards.critRate)
            assertEquals(def.rewards.critDmg, dto.rewards.critDmg)
        }
        // SOUL_RING 描述口径 = 「装备」而非「获得」（与已装备环数进度一致）
        assertTrue(status.first { it.id == "ring_1" }.description.startsWith("装备"))
    }

    @Test
    fun `status should pass through unlocked rows with yyyy-MM-dd unlockedAt`() {
        doReturn(profile(level = 1)).whenever(profileRepo).findByUserId(1L)
        doReturn(listOf(row("cult_10"))).whenever(achievementRepo).findByUserId(1L)

        val status = achievementService.getStatus(1L)

        val unlocked = status.first { it.id == "cult_10" }
        assertTrue(unlocked.unlocked)
        assertEquals("2026-09-26", unlocked.unlockedAt)
        assertEquals(10, unlocked.unlockedAt!!.length)
        val locked = status.first { it.id == "cult_30" }
        assertFalse(locked.unlocked)
        assertNull(locked.unlockedAt)
    }

    // ==================== unlockedBonus 属性加成求和 ====================

    @Test
    fun `unlockedBonus should sum hp and atk from unlocked definitions`() {
        doReturn(listOf(row("cult_10"), row("battle_10"))).whenever(achievementRepo).findByUserId(1L)

        val bonus = achievementService.unlockedBonus(1L)

        // 与定义表同源求和：cult_10(hp100,atk5) + battle_10(hp100,atk10)
        val hp = GameBalance.ACHIEVEMENT_BY_ID.getValue("cult_10").rewards.hp +
                GameBalance.ACHIEVEMENT_BY_ID.getValue("battle_10").rewards.hp
        val atk = (GameBalance.ACHIEVEMENT_BY_ID.getValue("cult_10").rewards.atk +
                GameBalance.ACHIEVEMENT_BY_ID.getValue("battle_10").rewards.atk).toLong()
        assertEquals(hp, bonus.hpBonus)
        assertEquals(atk, bonus.atkBonus)
    }

    @Test
    fun `unlockedBonus should ignore unknown achievement ids`() {
        // 定义表已下线的历史记录行：静默忽略不加成
        doReturn(listOf(row("ghost_id"))).whenever(achievementRepo).findByUserId(1L)

        val bonus = achievementService.unlockedBonus(1L)

        assertEquals(0L, bonus.atkBonus)
        assertEquals(0L, bonus.hpBonus)
    }
}
