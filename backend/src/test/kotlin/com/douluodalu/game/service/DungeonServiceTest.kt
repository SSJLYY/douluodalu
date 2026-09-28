package com.douluodalu.game.service

import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.entity.DungeonProgressEntity
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.AchievementRepository
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.DungeonProgressRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import com.douluodalu.game.repository.EquippedRingRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.TalentRepository
import com.douluodalu.game.repository.UserTitleRepository
import com.douluodalu.game.repository.UserRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional

/**
 * 每日副本服务单测（第二十九轮，设计文档 §8）：
 *  - 5 难度定义/奖励预览逐位（照 §8 表钉契约）+ 解锁矩阵（1~5 转边界）；
 *  - 当日挑战唯一性（胜、败都占名额；扫荡同占）；
 *  - 胜局奖励逐位（金币×转生倍率、杀气直加、掉落钩子按 tier→level 映射被调）；
 *  - 扫荡前置（未通关拒绝、已通关扣魂力逐位、魂力不足拒绝）；
 *  - 跨日惰性重置（challenge_date 昨天 → 恢复可挑战、tier_completed 归 -1）。
 *
 * mock 风格参照 GameServiceTest：仓库全 mock、EquipmentPowerService/GameService 用真实实例
 * （wire 同一组 mock 仓库）——胜负确定性由数值构造保证（level=100 碾压局必胜 / level=1
 * 裸装必败），战斗结算与掉落走真实公式；掉落 level 映射用掉落物
 * percentage = 100 + level×12 + rand(80) 的区间断言钉住（tier7 → level 84 → [1108, 1188)）。
 */
class DungeonServiceTest {
    @Mock
    private lateinit var dungeonProgressRepo: DungeonProgressRepository

    @Mock
    private lateinit var profileRepo: PlayerProfileRepository

    @Mock
    private lateinit var backpackRepo: BackpackItemRepository

    @Mock
    private lateinit var talentRepo: TalentRepository

    @Mock
    private lateinit var equippedRingRepo: EquippedRingRepository

    @Mock
    private lateinit var equippedBoneRepo: EquippedBoneRepository

    @Mock
    private lateinit var equippedCoreRepo: EquippedCoreRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var webSocketService: WebSocketService

    @Mock
    private lateinit var checkInService: CheckInService

    @Mock
    private lateinit var dailyQuestService: DailyQuestService

    @Mock
    private lateinit var achievementRepo: AchievementRepository

    /** 第二十九轮杀气商店：EquipmentPowerService 构造新增（本套用例无称号，恒空仓） */
    @Mock
    private lateinit var userTitleRepo: UserTitleRepository

    @Mock
    private lateinit var achievementService: AchievementService

    private lateinit var gameService: GameService
    private lateinit var dungeonService: DungeonService

    /** 真实 Micrometer 注册表（计数器断言用；测试间 clear 防串扰） */
    private val meterRegistry = SimpleMeterRegistry()

    /**
     * 副本进度行的内存仓：save 落仓、findById 读仓——「先打一场再查重」的当日唯一性用例
     * 需要第二次 findById 读到第一场写入的行（静态 thenReturn 桩做不到）。
     */
    private val savedRows = mutableMapOf<Long, DungeonProgressEntity>()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doAnswer { it.arguments[0] }.whenever(profileRepo).save(any())
        doAnswer { it.arguments[0] }.whenever(backpackRepo).save(any())
        doAnswer { inv ->
            val entity = inv.arguments[0] as DungeonProgressEntity
            savedRows[entity.userId] = entity
            entity
        }.whenever(dungeonProgressRepo).save(any())
        doAnswer { Optional.ofNullable(savedRows[it.arguments[0] as Long]) }
            .whenever(dungeonProgressRepo).findById(any())
        meterRegistry.clear()
        val equipmentPowerService =
            EquipmentPowerService(
                equippedRingRepo, equippedBoneRepo, equippedCoreRepo, achievementRepo,
                userTitleRepo, profileRepo
            )
        gameService = GameService(
            profileRepo, backpackRepo, talentRepo, equippedRingRepo, equippedBoneRepo, equippedCoreRepo,
            userRepository, webSocketService, checkInService, dailyQuestService,
            equipmentPowerService,
            achievementService,
            meterRegistry,
            EquipService(profileRepo, backpackRepo, equippedRingRepo, equippedBoneRepo, equippedCoreRepo, achievementService)
        )
        dungeonService = DungeonService(
            dungeonProgressRepo, profileRepo, gameService, equipmentPowerService, achievementService, meterRegistry
        )
    }

    /**
     * 满血推图进度玩家：map0/stage1 的基础怪 hp=224、atk=16（GameService.monsterStats 口径）。
     * level=100 无装备 → 玩家 atk 1050，对 tier0 Boss（hp 672/防 9）30 回合内必胜；
     * level=1 无装备 → 玩家 atk ≤90（×转生倍率），对 tier4 Boss（hp 4480）30 回合内必败。
     */
    private fun profile(level: Int = 100, prestige: Int = 0): PlayerProfileEntity =
        PlayerProfileEntity(userId = 1L, level = level).apply {
            prestigeCount = prestige
            currentMapId = 0
            currentStage = 1
            currentHp = GameBalance.playerBaseMaxHp(level)
        }

    private fun row(
        challengeDate: LocalDate? = null,
        tierCompleted: Int = -1,
        everCleared: Int = 0
    ) = DungeonProgressEntity(
        userId = 1L, challengeDate = challengeDate, tierCompleted = tierCompleted, everCleared = everCleared
    )

    private fun stubProfile(p: PlayerProfileEntity) {
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
    }

    private fun stubRow(r: DungeonProgressEntity?) {
        savedRows.clear()
        if (r != null) savedRows[1L] = r
    }

    private val today: LocalDate get() = LocalDate.now()
    private val yesterday: LocalDate get() = LocalDate.now().minusDays(1)

    // ==================== 状态：定义逐位 + 解锁矩阵 ====================

    @Test
    fun `state should expose five tiers matching the design doc table bit by bit`() {
        stubProfile(profile(level = 100, prestige = 0)) // 0 转 → 转生倍率 1.0，预览 == 名义值
        stubRow(null)

        val state = dungeonService.getState(1L)

        assertEquals(5, state.tiers.size)
        // 逐位钉 §8 表（name/difficulty/boss + 五元组 hpMult/atkMult/gold/killing/dropTier）
        val expected = listOf(
            Triple("魂兽森林", "简单", "千年魂兽·泰坦巨猿") to Quint(3.0, 1.8, 5_000L, 10, 7),
            Triple("暗影峡谷", "普通", "暗影君王·鬼魅") to Quint(5.0, 2.5, 15_000L, 25, 12),
            Triple("龙墓禁地", "困难", "远古龙皇·赤王") to Quint(8.0, 3.5, 40_000L, 50, 17),
            Triple("神之遗迹", "噩梦", "堕落天使·路西法") to Quint(12.0, 5.0, 100_000L, 100, 22),
            Triple("深渊之门", "地狱", "深渊之主·阿萨谢尔") to Quint(20.0, 8.0, 300_000L, 200, 24)
        )
        expected.forEachIndexed { tier, (names, q) ->
            val t = state.tiers[tier]
            assertEquals(tier, t.tier)
            assertEquals(names.first, t.name)
            assertEquals(names.second, t.difficultyName)
            assertEquals(names.third, t.bossName)
            assertEquals(q.hpMult, t.hpMult)
            assertEquals(q.atkMult, t.atkMult)
            assertEquals(q.gold, t.goldReward)
            assertEquals(q.killing, t.killingReward)
            assertEquals(q.dropTier, t.dropTier)
            assertEquals(tier + 1, t.unlockPrestige)
        }
        // 扫荡费用预览 = 50 + 等级×5（level=100 → 550）
        assertEquals(550L, state.tiers.first().sweepSoulPowerCost)
    }

    /** 五元组小工具（测试表可读性；Kotlin 无内置 Quint） */
    private data class Quint(val hpMult: Double, val atkMult: Double, val gold: Long, val killing: Int, val dropTier: Int)

    @Test
    fun `state should expose the unlock matrix by prestige count`() {
        // 解锁条件 1~5 转：转数 n 恰好解锁前 n 个难度（边界逐位）
        for (prestige in 0..5) {
            stubProfile(profile(level = 100, prestige = prestige))
            stubRow(null)
            val state = dungeonService.getState(1L)
            assertEquals(List(5) { it < prestige }, state.tiers.map { it.unlocked },
                "prestige=$prestige 的解锁矩阵不符")
        }
    }

    @Test
    fun `state should report fresh progress for a player without a progress row`() {
        stubProfile(profile())
        stubRow(null)

        val state = dungeonService.getState(1L)

        assertFalse(state.challengedToday)
        assertEquals(-1, state.tierCompleted)
        assertTrue(state.tiers.none { it.challengedToday || it.clearedToday || it.sweepable })
    }

    @Test
    fun `state should expose sweepable only for historically cleared tiers`() {
        stubProfile(profile(level = 100, prestige = 5))
        // 昨天用过名额、历史上通过 tier0/tier2（位掩码 0b101）；tierCompleted=4（昨日旧值）
        stubRow(row(challengeDate = yesterday, tierCompleted = 4, everCleared = 0b101))

        val state = dungeonService.getState(1L)

        // 跨天惰性重置（读侧）：challenge_date 昨天 → 当日机会未用、tierCompleted 视为 -1
        assertFalse(state.challengedToday)
        assertEquals(-1, state.tierCompleted)
        assertTrue(state.tiers.none { it.clearedToday })
        // 可扫荡 = 历史通关过该难度（位掩码精确判定），未通关的难度不给扫荡
        assertEquals(listOf(true, false, true, false, false), state.tiers.map { it.sweepable })
    }

    // ==================== 挑战：胜局奖励逐位 ====================

    @Test
    fun `fight should win at tier 0 with bit exact rewards and tier drop level`() {
        // 最低难度也需 1 转（§8 解锁条件列）——生产上副本胜局恒有转生倍率 ≥1.1，
        // 本用例取 1 转锚点断言 5000×1.1=5500；纯名义 5000 的场景只存在于状态预览（见上方用例）
        val p = profile(level = 100, prestige = 1)
        p.gold = 0
        stubProfile(p)
        stubRow(null)

        val response = dungeonService.fight(1L, 0)

        assertTrue(response.won)
        // Boss 数值：map0/stage1 基础怪 hp=224 × 3.0 = 672（与 battle 同源 monsterStats × 倍率）
        assertEquals(672L, response.monsterMaxHp)
        assertEquals("千年魂兽·泰坦巨猿", response.monsterName)
        // 金币 ×转生倍率（1 转 → 1.1）：5500；杀气直加：10（不乘倍率）
        assertEquals(5_500L, response.goldGained)
        assertEquals(5_500L, p.gold)
        assertEquals(10, response.killingGained)
        assertEquals(10, p.killingIntent)
        // 通关标记：tier_completed=0、ever_cleared 置位、challenge_date=今天
        val rowCaptor = ArgumentCaptor.forClass(DungeonProgressEntity::class.java)
        verify(dungeonProgressRepo).save(rowCaptor.capture())
        assertEquals(0, rowCaptor.value.tierCompleted)
        assertEquals(GameBalance.dungeonClearedBit(0), rowCaptor.value.everCleared)
        assertEquals(today, rowCaptor.value.challengeDate)
        // 掉落钩子被调且 level 映射正确：tier 7 → level 84 → percentage = 100+84×12+rand(80) ∈ [1108, 1188)
        val dropCaptor = ArgumentCaptor.forClass(BackpackItemEntity::class.java)
        verify(backpackRepo).save(dropCaptor.capture())
        assertTrue(dropCaptor.value.percentage in 1108..1187,
            "掉落 percentage=${dropCaptor.value.percentage} 应在 [1108, 1188)（level=84 映射）")
        assertEquals(1, response.drops.size)
        assertTrue(response.battleLog.isNotEmpty())
        assertEquals(1L, p.totalBattleWins)
        verify(achievementService).sync(1L)
    }

    @Test
    fun `fight should apply prestige multiplier to gold but not to killing intent`() {
        val p = profile(level = 100, prestige = 2)
        stubProfile(p)
        stubRow(null)

        val response = dungeonService.fight(1L, 0)

        assertTrue(response.won)
        // 金币 ×(1+2×0.1) = 6000（2 转锚点与 1 转胜局用例区分）；杀气为稀缺货币直加 10（不乘倍率）
        assertEquals(6_000L, response.goldGained)
        assertEquals(10, response.killingGained)
    }

    // ==================== 挑战：败局零奖励 + 当日唯一性 ====================

    @Test
    fun `fight should consume the daily attempt on loss with no rewards`() {
        val p = profile(level = 1, prestige = 5) // 1 级裸装 vs tier4 Boss（hp 4480）→ 30 回合内必败
        p.gold = 100L
        p.totalBattleLosses = 0
        stubProfile(p)
        stubRow(null)

        val response = dungeonService.fight(1L, 4)

        assertFalse(response.won)
        // 失败无奖励：金币/杀气/掉落全零，掉落钩子未被调
        assertEquals(100L, p.gold)
        assertEquals(0, p.killingIntent)
        assertEquals(0, response.killingGained)
        assertTrue(response.drops.isEmpty())
        verify(backpackRepo, never()).save(any())
        // 败局也算当日已挑战：challenge_date 落今天、tier_completed 保持 -1、历史通关不置位
        val rowCaptor = ArgumentCaptor.forClass(DungeonProgressEntity::class.java)
        verify(dungeonProgressRepo).save(rowCaptor.capture())
        assertEquals(today, rowCaptor.value.challengeDate)
        assertEquals(-1, rowCaptor.value.tierCompleted)
        assertEquals(0, rowCaptor.value.everCleared)
        // 战败回满血（battle 同款含转生倍率口径：150×1.5=225）
        assertEquals((GameBalance.playerBaseMaxHp(1) * GameBalance.prestigeMultiplier(5)).toLong(), p.currentHp)
        assertEquals(1L, p.totalBattleLosses)
        verify(achievementService, never()).sync(any())
    }

    @Test
    fun `fight should reject second attempt on the same day after a win`() {
        stubProfile(profile(prestige = 1)) // 已解锁 tier0，命中「今日已挑战」而非解锁校验
        stubRow(row(challengeDate = today, tierCompleted = 2, everCleared = 0b111))

        val ex = assertThrows(IllegalArgumentException::class.java) { dungeonService.fight(1L, 0) }
        assertEquals(DungeonService.MSG_ALREADY_CHALLENGED, ex.message)
    }

    @Test
    fun `fight should reject second attempt on the same day after a loss`() {
        stubProfile(profile(level = 1, prestige = 5))
        stubRow(null)

        // 先真实打一场必败局（占当日名额，行经内存仓落库），再挑战任何难度都应被拒
        assertFalse(dungeonService.fight(1L, 4).won)
        val ex = assertThrows(IllegalArgumentException::class.java) { dungeonService.fight(1L, 0) }
        assertEquals(DungeonService.MSG_ALREADY_CHALLENGED, ex.message)
    }

    @Test
    fun `fight should reject invalid tier and locked tier`() {
        stubProfile(profile()) // 0 转

        assertEquals(DungeonService.MSG_INVALID_TIER,
            assertThrows(IllegalArgumentException::class.java) { dungeonService.fight(1L, 5) }.message)
        assertEquals(DungeonService.MSG_INVALID_TIER,
            assertThrows(IllegalArgumentException::class.java) { dungeonService.fight(1L, -1) }.message)
        assertEquals(DungeonService.lockedMessage(GameBalance.DUNGEON_DEFS[0]),
            assertThrows(IllegalArgumentException::class.java) { dungeonService.fight(1L, 0) }.message)
    }

    // ==================== 扫荡：前置 + 扣费逐位 ====================

    @Test
    fun `sweep should deduct soul power bit exact and grant rewards without a battle`() {
        val p = profile(level = 5, prestige = 1) // 1 转解锁 tier0；扫荡费 50+5×5=75
        p.gold = 0L
        p.soulPower = 100L
        stubProfile(p)
        // 昨日通关过 tier0（位掩码）、昨日挑战名额已用、tier_completed 是昨日旧值 3
        stubRow(row(challengeDate = yesterday, tierCompleted = 3, everCleared = GameBalance.dungeonClearedBit(0)))

        val response = dungeonService.sweep(1L, 0)

        // 扣魂力逐位：100 - (50 + 5×5) = 25
        assertEquals(75L, response.soulPowerSpent)
        assertEquals(25L, p.soulPower)
        // 奖励逐位：金币 ×转生倍率（1 转 → 5500）、杀气直加 10
        assertEquals(5_500L, response.goldGained)
        assertEquals(5_500L, p.gold)
        assertEquals(10, response.killingGained)
        assertEquals(10, p.killingIntent)
        // 掉落钩子被调且 level 映射正确（tier 7 → level 84 → percentage ∈ [1108, 1188)）
        val dropCaptor = ArgumentCaptor.forClass(BackpackItemEntity::class.java)
        verify(backpackRepo).save(dropCaptor.capture())
        assertTrue(dropCaptor.value.percentage in 1108..1187)
        assertEquals(1, response.drops.size)
        // 占当日名额 + 跨天惰性重置（扫荡无通关事实：tier_completed 归 -1、位掩码不动）
        val rowCaptor = ArgumentCaptor.forClass(DungeonProgressEntity::class.java)
        verify(dungeonProgressRepo).save(rowCaptor.capture())
        assertEquals(today, rowCaptor.value.challengeDate)
        assertEquals(-1, rowCaptor.value.tierCompleted)
        assertEquals(GameBalance.dungeonClearedBit(0), rowCaptor.value.everCleared)
    }

    @Test
    fun `sweep should reject a tier that was never cleared`() {
        stubProfile(profile(level = 5, prestige = 1))
        // 无行 = 从未参与副本
        stubRow(null)
        assertEquals(DungeonService.MSG_NOT_CLEARED,
            assertThrows(IllegalArgumentException::class.java) { dungeonService.sweep(1L, 0) }.message)

        // 有行但只通关过 tier1：sweep tier0（未通关）拒绝
        stubRow(row(everCleared = GameBalance.dungeonClearedBit(1)))
        assertEquals(DungeonService.MSG_NOT_CLEARED,
            assertThrows(IllegalArgumentException::class.java) { dungeonService.sweep(1L, 0) }.message)
    }

    @Test
    fun `sweep should reject when the daily reward slot is already used`() {
        stubProfile(profile(level = 5, prestige = 1))
        stubRow(row(challengeDate = today, everCleared = GameBalance.dungeonClearedBit(0)))

        // 当日名额已被战斗/扫荡占用（「同当日唯一一次奖励」）
        assertEquals(DungeonService.MSG_ALREADY_CHALLENGED,
            assertThrows(IllegalArgumentException::class.java) { dungeonService.sweep(1L, 0) }.message)
    }

    @Test
    fun `sweep should reject when soul power is insufficient`() {
        val p = profile(level = 5, prestige = 1)
        p.soulPower = 74L // 74 < 75
        stubProfile(p)
        stubRow(row(challengeDate = yesterday, everCleared = GameBalance.dungeonClearedBit(0)))

        val ex = assertThrows(IllegalArgumentException::class.java) { dungeonService.sweep(1L, 0) }
        assertTrue(ex.message!!.contains("75"), "应提示需要 75 魂力，实际：${ex.message}")
        assertEquals(74L, p.soulPower) // 零改动
        assertEquals(0L, p.gold)
    }

    // ==================== 跨日惰性重置：恢复可挑战 ====================

    @Test
    fun `fight should be available again after the challenge date goes stale`() {
        val p = profile(level = 100, prestige = 5)
        stubProfile(p)
        // 昨天打到 tier4（历史全通），新的一天（challenge_date 还是昨天）→ 全部恢复可挑战
        stubRow(row(challengeDate = yesterday, tierCompleted = 4, everCleared = 0b11111))

        // 不抛「今日已挑战」即恢复可挑战；胜局后当日标记归位（惰性重置从 -1 重算）
        val response = dungeonService.fight(1L, 0)
        assertTrue(response.won)
        val rowCaptor = ArgumentCaptor.forClass(DungeonProgressEntity::class.java)
        verify(dungeonProgressRepo).save(rowCaptor.capture())
        assertEquals(today, rowCaptor.value.challengeDate)
        assertEquals(0, rowCaptor.value.tierCompleted)
    }
}
