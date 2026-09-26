package com.douluodalu.game.service

import com.douluodalu.game.entity.DailyQuestProgressEntity
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.DailyQuestProgressRepository
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

class DailyQuestServiceTest {
    @Mock
    private lateinit var dailyQuestRepo: DailyQuestProgressRepository

    @Mock
    private lateinit var profileRepo: PlayerProfileRepository

    @InjectMocks
    private lateinit var dailyQuestService: DailyQuestService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doAnswer { it.arguments[0] }.whenever(profileRepo).save(any())
    }

    private fun profile() = PlayerProfileEntity(userId = 1L, level = 5)

    private fun todayRow(questId: String, progress: Int, claimed: Boolean = false) = DailyQuestProgressEntity(
        userId = 1L, questDate = LocalDate.now(), questId = questId,
        progress = progress, claimed = claimed
    )

    // ==================== recordProgress 计数 ====================

    @Test
    fun `first progress count should insert a new row with progress 1`() {
        // 原子 UPDATE 返回 0 = 当天还没有行 → 建行
        doReturn(0).whenever(dailyQuestRepo).incrementProgress(eq(1L), any(), any())

        dailyQuestService.recordBattleWin(1L)

        val captor = ArgumentCaptor.forClass(DailyQuestProgressEntity::class.java)
        verify(dailyQuestRepo).save(captor.capture())
        assertEquals(1L, captor.value.userId)
        assertEquals(GameBalance.QUEST_BATTLE_WINS, captor.value.questId)
        assertEquals(LocalDate.now(), captor.value.questDate)
        assertEquals(1, captor.value.progress)
        assertFalse(captor.value.claimed)
    }

    @Test
    fun `incremental count should not insert when atomic update hits an existing row`() {
        // UPDATE 返回 1 = 行已存在 → 只累加，不 insert（condition UPDATE 天然幂等即防抖）
        doReturn(1).whenever(dailyQuestRepo).incrementProgress(eq(1L), any(), eq(GameBalance.QUEST_SHOP_BUY))

        dailyQuestService.recordShopBuy(1L)

        verify(dailyQuestRepo, never()).save(any())
    }

    @Test
    fun `concurrent first count should retry atomic update when insert hits unique constraint`() {
        // 并发首记竞态：两个请求同时拿到 UPDATE=0，后提交者 INSERT 撞 uk_user_date_quest → 重试一次 UPDATE
        doReturn(0, 1).whenever(dailyQuestRepo).incrementProgress(eq(1L), any(), any())
        doThrow(DataIntegrityViolationException("uk_user_date_quest")).whenever(dailyQuestRepo).save(any())

        assertDoesNotThrow { dailyQuestService.recordProgress(1L, GameBalance.QUEST_BATTLE_WINS) }

        verify(dailyQuestRepo, times(2)).incrementProgress(eq(1L), any(), eq(GameBalance.QUEST_BATTLE_WINS))
        verify(dailyQuestRepo).save(any())
    }

    @Test
    fun `record progress failure should be swallowed to protect the main game flow`() {
        // 副路径兜底：计数链路任何异常（DB 抖动等）都不得击穿战斗/修炼/签到/购物主流程
        doThrow(RuntimeException("db down")).whenever(dailyQuestRepo).incrementProgress(any(), any(), any())

        assertDoesNotThrow { dailyQuestService.recordCultivate(1L) }

        verify(dailyQuestRepo, never()).save(any())
        verify(profileRepo, never()).save(any())
    }

    @Test
    fun `five thin recorders should map to their quest ids`() {
        doReturn(1).whenever(dailyQuestRepo).incrementProgress(any(), any(), any())

        dailyQuestService.recordBattleWin(1L)
        dailyQuestService.recordCultivate(2L)
        dailyQuestService.recordTower(3L)
        dailyQuestService.recordCheckin(4L)
        dailyQuestService.recordShopBuy(5L)

        verify(dailyQuestRepo).incrementProgress(eq(1L), any(), eq(GameBalance.QUEST_BATTLE_WINS))
        verify(dailyQuestRepo).incrementProgress(eq(2L), any(), eq(GameBalance.QUEST_CULTIVATE))
        verify(dailyQuestRepo).incrementProgress(eq(3L), any(), eq(GameBalance.QUEST_TOWER))
        verify(dailyQuestRepo).incrementProgress(eq(4L), any(), eq(GameBalance.QUEST_CHECKIN))
        verify(dailyQuestRepo).incrementProgress(eq(5L), any(), eq(GameBalance.QUEST_SHOP_BUY))
    }

    // ==================== claim 领奖 ====================

    @Test
    fun `claim should grant rewards matching the definition when conditional update hits`() {
        val p = profile()
        doReturn(1).whenever(dailyQuestRepo).claimIfEligible(eq(1L), any(), eq(GameBalance.QUEST_BATTLE_WINS), eq(3))
        doReturn(p).whenever(profileRepo).findByUserId(1L)

        val result = dailyQuestService.claim(1L, GameBalance.QUEST_BATTLE_WINS)

        val def = GameBalance.DAILY_QUEST_BY_ID[GameBalance.QUEST_BATTLE_WINS]!!
        assertEquals(def.rewardGold, result.goldGained)
        assertEquals(def.rewardBossCoin, result.bossCoinGained)
        assertEquals(def.rewardSoulPower, result.soulPowerGained)
        // 奖励入账到存档
        assertEquals(def.rewardGold, p.gold)
        assertEquals(def.rewardBossCoin, p.bossCoin)
        assertEquals(def.rewardSoulPower, p.soulPower)
    }

    @Test
    fun `claim below target should be rejected as not-enough without any grant`() {
        doReturn(0).whenever(dailyQuestRepo).claimIfEligible(eq(1L), any(), eq(GameBalance.QUEST_BATTLE_WINS), eq(3))
        doReturn(todayRow(GameBalance.QUEST_BATTLE_WINS, progress = 1)).whenever(dailyQuestRepo)
            .findByUserIdAndQuestDateAndQuestId(eq(1L), any(), eq(GameBalance.QUEST_BATTLE_WINS))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            dailyQuestService.claim(1L, GameBalance.QUEST_BATTLE_WINS)
        }

        assertEquals(DailyQuestService.MSG_NOT_ENOUGH, ex.message)
        verify(profileRepo, never()).save(any())
    }

    @Test
    fun `repeat claim after success should fail through conditional update path without re-granting`() {
        // 重复领取走条件 UPDATE 失败路径：claimed=1 使 claimIfEligible 必然返回 0 → 归因为「今日已领取」
        doReturn(0).whenever(dailyQuestRepo).claimIfEligible(eq(1L), any(), eq(GameBalance.QUEST_TOWER), eq(2))
        doReturn(todayRow(GameBalance.QUEST_TOWER, progress = 2, claimed = true)).whenever(dailyQuestRepo)
            .findByUserIdAndQuestDateAndQuestId(eq(1L), any(), eq(GameBalance.QUEST_TOWER))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            dailyQuestService.claim(1L, GameBalance.QUEST_TOWER)
        }

        assertEquals(DailyQuestService.MSG_ALREADY_CLAIMED, ex.message)
        verify(dailyQuestRepo).claimIfEligible(eq(1L), any(), eq(GameBalance.QUEST_TOWER), eq(2))
        verify(profileRepo, never()).save(any())
    }

    @Test
    fun `claim with unknown quest id should be rejected before touching the repository`() {
        val ex = assertThrows(IllegalArgumentException::class.java) { dailyQuestService.claim(1L, "no_such_quest") }

        assertEquals(DailyQuestService.MSG_UNKNOWN, ex.message)
        verify(dailyQuestRepo, never()).claimIfEligible(any(), any(), any(), any())
    }

    @Test
    fun `claim with missing today row should be attributed to not-enough for a known quest`() {
        // 已知任务当天从未计数（无进度行）视同 progress=0，归因「未达标」而非「未知任务」
        doReturn(0).whenever(dailyQuestRepo).claimIfEligible(eq(1L), any(), eq(GameBalance.QUEST_CULTIVATE), eq(5))
        doReturn(null).whenever(dailyQuestRepo)
            .findByUserIdAndQuestDateAndQuestId(eq(1L), any(), eq(GameBalance.QUEST_CULTIVATE))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            dailyQuestService.claim(1L, GameBalance.QUEST_CULTIVATE)
        }

        assertEquals(DailyQuestService.MSG_NOT_ENOUGH, ex.message)
        verify(profileRepo, never()).save(any())
    }

    // ==================== getTodayStatus 状态合成 ====================

    @Test
    fun `status should synthesize all quest defs and fill missing rows with zero progress`() {
        val rows = listOf(todayRow(GameBalance.QUEST_BATTLE_WINS, progress = 2, claimed = true))
        doReturn(rows).whenever(dailyQuestRepo).findByUserIdAndQuestDate(eq(1L), any())

        val status = dailyQuestService.getTodayStatus(1L)

        assertEquals(LocalDate.now().toString(), status.date)
        // 固定返回全部任务定义，顺序与定义表一致
        assertEquals(GameBalance.DAILY_QUESTS.map { it.id }, status.quests.map { it.id })
        // 已有行：真实进度
        val battle = status.quests.first { it.id == GameBalance.QUEST_BATTLE_WINS }
        assertEquals(2, battle.progress)
        assertTrue(battle.claimed)
        // 缺行：补 progress=0 / claimed=false
        val cultivate = status.quests.first { it.id == GameBalance.QUEST_CULTIVATE }
        assertEquals(0, cultivate.progress)
        assertFalse(cultivate.claimed)
    }

    @Test
    fun `status rewards and targets should mirror the balance definition table`() {
        doReturn(emptyList<DailyQuestProgressEntity>()).whenever(dailyQuestRepo).findByUserIdAndQuestDate(eq(1L), any())

        val status = dailyQuestService.getTodayStatus(1L)

        assertEquals(GameBalance.DAILY_QUESTS.size, status.quests.size)
        status.quests.forEach { dto ->
            val def = GameBalance.DAILY_QUEST_BY_ID[dto.id]!!
            assertEquals(def.description, dto.description)
            assertEquals(def.target, dto.target)
            assertEquals(def.rewardGold, dto.rewardGold)
            assertEquals(def.rewardBossCoin, dto.rewardBossCoin)
            assertEquals(def.rewardSoulPower, dto.rewardSoulPower)
        }
    }

    @Test
    fun `status date should use today in local timezone same as check-in`() {
        doReturn(emptyList<DailyQuestProgressEntity>()).whenever(dailyQuestRepo).findByUserIdAndQuestDate(eq(1L), any())

        val status = dailyQuestService.getTodayStatus(1L)

        // yyyy-MM-dd，与签到（LocalDate.now()）同一时区口径
        assertEquals(LocalDate.now().toString(), status.date)
        assertEquals(10, status.date.length)
    }
}
