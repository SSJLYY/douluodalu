package com.douluodalu.game.service

import com.douluodalu.game.dto.CheckInStatusDto
import com.douluodalu.game.dto.DailyQuestsDto
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import com.douluodalu.game.repository.EquippedRingRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.TalentRepository
import com.douluodalu.game.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.math.abs

class GameServiceTest {
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

    @InjectMocks
    private lateinit var gameService: GameService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doAnswer { it.arguments[0] }.whenever(profileRepo).save(any())
    }

    private fun profile(userId: Long = 1L) = PlayerProfileEntity(userId = userId, level = 5)

    @Test
    fun `cultivate should gain soul power within expected range`() {
        val p = profile()
        p.soulPower = 100L
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val response = gameService.cultivate(1L)

        // level=5 → baseGain = 10 + 5*2 = 20，随机加成为 [0, baseGain/4) = [0, 5)
        val baseGain = 10L + 5L * 2L
        assertTrue(response.soulPowerGained in baseGain until (baseGain + baseGain / 4),
            "gain=${response.soulPowerGained} 应在 [$baseGain, ${baseGain + baseGain / 4}) 区间")
        assertEquals(100L + response.soulPowerGained, response.totalSoulPower)
        assertEquals(p.soulPower, response.totalSoulPower)
        assertEquals(5, response.level)
    }

    @Test
    fun `claimOfflineReward should cap offline time at 12 hours`() {
        val p = profile()
        p.lastLogoutTime = LocalDateTime.now().minusHours(20)
        p.gold = 0
        p.soulPower = 0
        p.totalBattleWins = 0
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val response = gameService.claimOfflineReward(1L)

        val maxSeconds = 12 * 3600L
        assertEquals(maxSeconds, response.offlineSeconds)

        // P1 修复后按「小时」计费：level=5 → gold/h = (10 + 5*2) * 0.8 = 16.0，exp/h = (5 + 5) * 0.8 = 8.0
        // 12h 封顶 → 金币 16×12=192、魂力 8×12=96（旧「每秒」语义为 691,200/345,600，通胀数千倍）
        assertEquals((16.0 * 12).toLong(), response.goldGained)
        assertEquals((8.0 * 12).toLong(), response.expGained)
        assertEquals(maxSeconds / 5, response.battleWins) // P6 胜场折算维持原逻辑（本次不修）

        assertEquals(response.goldGained, p.gold)
        assertEquals(response.expGained, p.soulPower)
        // 领取后登出时间基准被刷新，重复领取不应再次发奖
        assertNotNull(p.lastLogoutTime)
        assertTrue(java.time.Duration.between(p.lastLogoutTime, LocalDateTime.now()).seconds < 60)
    }

    @Test
    fun `claimOfflineReward should fall back to lastLoginAt when lastLogoutTime is null`() {
        val user = UserEntity(id = 1, username = "u", nickname = "n", passwordHash = "h")
        user.lastLoginAt = LocalDateTime.now().minusHours(2)
        val p = profile()
        p.user = user
        p.lastLogoutTime = null
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val response = gameService.claimOfflineReward(1L)

        // 回退到最后登录时间（约 2 小时前），允许秒级误差
        assertTrue(abs(response.offlineSeconds - 2 * 3600L) < 5,
            "offlineSeconds=${response.offlineSeconds} 应约为 7200")
        assertTrue(response.goldGained > 0)
        assertTrue(response.expGained > 0)
    }

    @Test
    fun `battle response should expose combat power including equipment bonus`() {
        val p = profile() // level=5
        p.currentHp = 350L
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        val ring = EquippedRing(userId = 1L, slotIndex = 0, ringId = 1L, yearOrdinal = 4, qualityOrdinal = 4, percentage = 900)
        whenever(equippedRingRepo.findByUserId(1L)).thenReturn(listOf(ring))
        whenever(equippedBoneRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedCoreRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(backpackRepo.countByUserId(1L)).thenReturn(0L)

        val response = gameService.battle(1L)

        val expectedPower = EquipmentPowerService.powerOf(
            5, EquipmentPowerService.bonus(5, listOf(ring), emptyList(), emptyList())
        )
        assertTrue(expectedPower > 0)
        assertEquals(expectedPower, response.power, "BattleResponse.power 应等于 等级+装备 折算的战斗力")
    }

    @Test
    fun `rollBackpackDrop should refuse to grant and return null when backpack is full`() {
        val p = profile()
        p.backpackCapacity = 5
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(backpackRepo.countByUserId(1L)).thenReturn(5L)

        val dto = gameService.rollBackpackDrop(1L, 10)

        assertNull(dto)
        verify(backpackRepo, never()).save(any())
    }

    @Test
    fun `rollBackpackDrop should save item when backpack has space`() {
        val p = profile()
        p.backpackCapacity = 5
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(backpackRepo.countByUserId(1L)).thenReturn(4L)
        doAnswer { it.arguments[0] }.whenever(backpackRepo).save(any())

        val dto = gameService.rollBackpackDrop(1L, 10)

        assertNotNull(dto)
        verify(backpackRepo).save(any())
    }

    @Test
    fun `getGameState should assemble checkIn status from CheckInService`() {
        val p = profile()
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(talentRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedRingRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedBoneRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedCoreRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(backpackRepo.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        whenever(checkInService.getCheckInStatus(1L)).thenReturn(
            CheckInStatusDto(signedToday = true, streak = 3, totalDays = 10, nextCycleDay = 4)
        )
        whenever(dailyQuestService.getTodayStatus(1L)).thenReturn(
            DailyQuestsDto(date = "2026-09-26", quests = emptyList())
        )

        val response = gameService.getGameState(1L)

        assertTrue(response.checkIn.signedToday)
        assertEquals(3L, response.checkIn.streak)
        assertEquals(10L, response.checkIn.totalDays)
        assertEquals(4, response.checkIn.nextCycleDay)
        verify(checkInService).getCheckInStatus(1L)
        // 每日任务面板同样委托 DailyQuestService 只读查询
        assertEquals("2026-09-26", response.dailyQuests.date)
        verify(dailyQuestService).getTodayStatus(1L)
    }

    @Test
    fun `task22 tower ring drops should be year-capped while bone and core keep full tier`() {
        // 塔满层 towerLevel=300：旧公式所有装备恒为 4 档环（负荷 ~9.99M 死掉落）；
        // 现在魂环封顶 GameBalance.TOWER_RING_DROP_YEAR_CAP，魂骨/魂核（不占负荷）保留 4 档战力曲线
        val p = profile()
        p.backpackCapacity = 5
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(backpackRepo.countByUserId(1L)).thenReturn(0L)
        doAnswer { it.arguments[0] }.whenever(backpackRepo).save(any())

        var sawRing = false
        var sawNonRing = false
        repeat(600) {
            val dto = gameService.rollBackpackDrop(1L, 300)!!
            if (dto.itemType == "RING") {
                sawRing = true
                assertTrue(dto.yearOrdinal <= GameBalance.TOWER_RING_DROP_YEAR_CAP,
                    "塔环年份 ${dto.yearOrdinal} 应 ≤ ${GameBalance.TOWER_RING_DROP_YEAR_CAP}")
            } else {
                sawNonRing = true
                assertEquals(4, dto.yearOrdinal, "level=300 时魂骨/魂核年份应仍饱和在 4 档")
            }
        }
        assertTrue(sawRing && sawNonRing, "600 次掉落应同时覆盖魂环与非环分支")
    }

    // ==================== 每日任务挂点 ====================

    @Test
    fun `battle victory should record battle_wins daily quest`() {
        // level=5 满血 vs 第1图第1关怪（hp 224 / atk 16）：怪伤 3 回合至多 ~57 << 350，确定性获胜
        val p = profile()
        p.currentHp = 350L
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(equippedRingRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedBoneRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedCoreRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(backpackRepo.countByUserId(1L)).thenReturn(0L)

        val response = gameService.battle(1L)

        assertTrue(response.won, "level=5 满血打 1-1 怪应确定性获胜")
        verify(dailyQuestService).recordBattleWin(1L)
    }

    @Test
    fun `battle defeat should not record battle_wins daily quest`() {
        // level=1 vs 7图15关怪（hp (200+2100)×2.8=6440 / atk 532）：玩家 30 回合至多打出 2160 伤，确定性战败
        val p = PlayerProfileEntity(userId = 1L, level = 1)
        p.currentMapId = 7
        p.currentStage = 15
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(equippedRingRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedBoneRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedCoreRepo.findByUserId(1L)).thenReturn(emptyList())

        val response = gameService.battle(1L)

        assertFalse(response.won, "level=1 打 7-15 怪应确定性战败")
        verify(dailyQuestService, never()).recordBattleWin(any())
    }

    // ==================== 塔战逐回合日志 ====================

    @Test
    fun `tower response should carry round log with hp invariants aligned to rounds`() {
        val p = profile()
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(equippedRingRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedBoneRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedCoreRepo.findByUserId(1L)).thenReturn(emptyList())

        val response = gameService.towerBattle(1L)

        // 挑战即计数，不论胜负
        verify(dailyQuestService).recordTower(1L)
        assertTrue(response.battleLog.isNotEmpty(), "塔战必须产出逐回合日志（前端塔页复用 BattleReplay）")
        assertEquals(response.battleLog.size, response.rounds, "rounds 应与日志回放一致")
        var lastRound = 0
        response.battleLog.forEach { r ->
            assertEquals(r.playerHpBefore - r.monsterDamage, r.playerHpAfter, "第${r.round}回合玩家 HP 不变量 before-damage==after")
            // 击杀回合怪物 HP 余量按 resolveBattle 既有语义截断为 0（与普通战斗日志形状一致）
            assertEquals(
                (r.monsterHpBefore - r.playerDamage).coerceAtLeast(0), r.monsterHpAfter,
                "第${r.round}回合怪物 HP 不变量 before-damage==after（击杀回合截断为 0）"
            )
            assertEquals(lastRound + 1, r.round, "回合号应从 1 连续递增")
            lastRound = r.round
        }
        // 首回合玩家满血口径（level=5：50×5+100=350，无装备加成）
        assertEquals(350L, response.battleLog.first().playerHpBefore)
        // 胜负与日志呈现一致
        if (response.won) {
            assertTrue(response.battleLog.last().monsterHpAfter <= 0, "won=true 的日志最后一回合怪应死亡")
        } else {
            assertTrue(response.battleLog.last().monsterHpAfter > 0, "won=false 的日志最后一回合怪应存活")
        }
    }

    @Test
    fun `tower battle log should be reproducible from userId and floor via independent seed`() {
        // 纯函数口径：同 (userId, floor, won) 两次调用日志逐字段一致（独立种子 Random(userId*1_000_003L+floor)）
        val logA = GameService.buildTowerBattleLog(userId = 42L, floor = 7, won = true, playerAtk = 100, playerMaxHp = 350)
        val logB = GameService.buildTowerBattleLog(userId = 42L, floor = 7, won = true, playerAtk = 100, playerMaxHp = 350)
        assertEquals(logA, logB)
        assertTrue(logA.isNotEmpty())
        assertTrue(logA.last().monsterHpAfter <= 0, "won=true 的日志应击杀怪物")

        // 不同楼层 → 种子与塔怪属性都不同 → 首回合 monsterHpBefore 必然不同（防种子退化为常量）
        val otherFloor = GameService.buildTowerBattleLog(userId = 42L, floor = 8, won = true, playerAtk = 100, playerMaxHp = 350)
        assertNotEquals(logA, otherFloor)

        // 集成口径：同一存档同一楼层重复挑战，胜负一致时日志逐字段一致（种子只由 userId+挑战时楼层决定）
        val p = profile()
        p.towerFloor = 5
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(equippedRingRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedBoneRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedCoreRepo.findByUserId(1L)).thenReturn(emptyList())
        val r1 = gameService.towerBattle(1L)
        p.towerFloor = 5
        val r2 = gameService.towerBattle(1L)
        if (r1.won == r2.won) assertEquals(r1.battleLog, r2.battleLog, "同 (userId, floor) 同胜负应日志一致")
    }
}
