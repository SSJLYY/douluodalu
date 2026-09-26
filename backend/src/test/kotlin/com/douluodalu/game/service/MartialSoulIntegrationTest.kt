package com.douluodalu.game.service

import com.douluodalu.game.dto.CheckInStatusDto
import com.douluodalu.game.dto.DailyQuestsDto
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.AchievementRepository
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import com.douluodalu.game.repository.EquippedRingRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.TalentRepository
import com.douluodalu.game.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * 第十八轮武魂觉醒集成断言（mock 仓库 + 真实 GameService/EquipmentPowerService，不起 Spring）：
 *  - awaken 流程：首醒免费写档 / 重醒扣 5000 金 / 金币不足 success=false 零改动 / 重醒重算战力值；
 *  - 属性接入：soulBonusOf 七字段换算与转生倍率、playerCombatStats 经加成包消费武魂五属性、
 *    powerOf 武魂贡献（atk 1:1、hp/10 与装备加成同权重）、getGameState 的 soulRarity 徽章与七行恒等。
 */
class MartialSoulIntegrationTest {
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

    @Mock
    private lateinit var achievementService: AchievementService

    private lateinit var gameService: GameService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doAnswer { it.arguments[0] }.whenever(profileRepo).save(any())
        gameService = GameService(
            profileRepo, backpackRepo, talentRepo, equippedRingRepo, equippedBoneRepo, equippedCoreRepo,
            userRepository, webSocketService, checkInService, dailyQuestService,
            EquipmentPowerService(equippedRingRepo, equippedBoneRepo, equippedCoreRepo, achievementRepo),
            achievementService
        )
    }

    private fun profile(userId: Long = 1L) = PlayerProfileEntity(userId = userId, level = 5)

    // ======== awaken 流程 ========

    @Test
    fun `first awaken is free and writes soul name and battle soul power`() {
        val p = profile() // martialSoulName=null, gold=0
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.awaken(1L)

        assertTrue(resp.success)
        assertFalse(resp.reawakened)
        assertEquals(0L, resp.goldSpent, "首醒必须免费")
        assertEquals(0L, p.gold, "首醒不得扣金币")
        val soul = GameBalance.soulByName(resp.martialSoulName)!!
        assertEquals(soul.name, p.martialSoulName)
        assertEquals(soul.rarity.name, resp.rarity)
        // battleSoulPower 写点：武魂战力公式值（宗门 Boss 伤害消费）
        assertEquals(GameBalance.martialSoulPower(soul), p.battleSoulPower.toLong())
        assertTrue(resp.message.contains(soul.name))
        // 设计决定留档：觉醒不改成就进度维度 → 不触发 sync
        verify(achievementService, never()).sync(any())
    }

    @Test
    fun `first awaken at zero prestige rolls from the zero-prestige pool`() {
        val p = profile()
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.awaken(1L)

        val zeroPool = GameBalance.availableSoulPool(0).map { it.name }
        assertTrue(resp.martialSoulName in zeroPool, "0 转首醒只能出精良及以下池（§2.3）：${resp.martialSoulName}")
        assertTrue(resp.rarity == "COMMON" || resp.rarity == "UNCOMMON")
    }

    @Test
    fun `reawaken costs 5000 gold, rewrites soul and recalculates battle soul power`() {
        val p = profile()
        p.martialSoulName = "蓝银草"
        p.battleSoulPower = 60
        p.gold = 12_000L
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.awaken(1L)

        assertTrue(resp.success)
        assertTrue(resp.reawakened)
        assertEquals(5_000L, resp.goldSpent)
        assertEquals(7_000L, p.gold, "重醒应扣除 REAWAKEN_COST_GOLD")
        val newSoul = GameBalance.soulByName(resp.martialSoulName)!!
        assertEquals(newSoul.name, p.martialSoulName, "重醒会重新 roll 并写档（名字可与旧不同）")
        assertEquals(GameBalance.martialSoulPower(newSoul).toInt(), p.battleSoulPower, "重醒必须重算 battleSoulPower")
        assertEquals(newSoul.rarity.name, resp.rarity)
    }

    @Test
    fun `reawaken with insufficient gold fails with zero mutation`() {
        val p = profile()
        p.martialSoulName = "白虎"
        p.battleSoulPower = 157
        p.gold = GameBalance.REAWAKEN_COST_GOLD - 1
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.awaken(1L)

        // breakthrough 惯例：HTTP 200 + success=false + message，不抛异常、零改动
        assertFalse(resp.success)
        assertEquals(0L, resp.goldSpent)
        assertTrue(resp.reawakened, "金币不足的玩家此前已觉醒")
        assertEquals(GameBalance.REAWAKEN_COST_GOLD - 1, p.gold, "失败不得扣金")
        assertEquals("白虎", p.martialSoulName, "失败不得改武魂")
        assertEquals(157, p.battleSoulPower, "失败不得改战力值")
        assertTrue(resp.message.contains("5000"))
    }

    @Test
    fun `awaken at five prestiges can roll from the full pool`() {
        val p = profile()
        p.prestigeCount = 5
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.awaken(1L)

        val fullPool = GameBalance.availableSoulPool(5).map { it.name }
        assertTrue(resp.martialSoulName in fullPool)
        val soul = GameBalance.soulByName(resp.martialSoulName)!!
        assertEquals(GameBalance.martialSoulPower(soul), p.battleSoulPower.toLong())
    }

    // ======== 属性接入 ========

    @Test
    fun `soulBonusOf maps seven fields and applies prestige multiplier`() {
        assertEquals(EquipmentBonus(0, 0), GameService.soulBonusOf(null, 0), "未觉醒 = 零加成（零漂移）")
        assertEquals(EquipmentBonus(0, 0), GameService.soulBonusOf("历史脏数据名字", 3), "反查失败兜底零加成")
        val b = GameService.soulBonusOf("修罗魔剑", 0)
        assertEquals(200L, b.atkBonus); assertEquals(500L, b.hpBonus); assertEquals(150L, b.matkBonus)
        assertEquals(40L, b.pdefBonus); assertEquals(35L, b.mdefBonus)
        assertEquals(25L, b.critRateBonus); assertEquals(300L, b.critDmgBonus)
        // 2 转倍率 1.2：加成通道逐字段缩放（与装备/成就加成同款 applyPrestige）
        val scaled = GameService.soulBonusOf("修罗魔剑", 2)
        assertEquals(240L, scaled.atkBonus); assertEquals(600L, scaled.hpBonus)
        assertEquals(180L, scaled.matkBonus); assertEquals(30L, scaled.critRateBonus); assertEquals(360L, scaled.critDmgBonus)
    }

    @Test
    fun `playerCombatStats consumes soul attributes via folded bonus`() {
        // 柔骨兔: hp 80 / atk 30 / matk 15 / crit 8%/160% / pdef 8 / mdef 8
        val soulB = GameService.soulBonusOf("柔骨兔", 0)
        val combat = GameService.playerCombatStats(1, 0, EquipmentPowerService.plus(EquipmentBonus(0, 0), soulB))
        // level=1：基础 atk 60 + 武魂 30；matk 基础 30 + 15；双防基础 2 + 8；暴击 0+8 / 150+160
        assertEquals(90L, combat.atk)
        assertEquals(45L, combat.matk)
        assertEquals(10L, combat.pdef)
        assertEquals(10L, combat.mdef)
        assertEquals(8, combat.critRate)
        assertEquals(310, combat.critDmg)
        // 未觉醒（零加成）与旧口径逐位一致（零漂移回归）
        val plain = GameService.playerCombatStats(1, 0, EquipmentBonus(0, 0))
        assertEquals(60L, plain.atk)
        assertEquals(0, plain.critRate)
        assertEquals(150, plain.critDmg)
    }

    @Test
    fun `powerOf with folded soul bonus uses equipment-consistent weights`() {
        val level = 40
        val equip = EquipmentBonus(atkBonus = 300, hpBonus = 2_000)
        val base = EquipmentPowerService.powerOf(level, equip)
        // 白虎: atk 60 / hp 200 / matk 20 / pdef 25 / mdef 15 / crit 10%/170%
        val soulB = GameService.soulBonusOf("白虎", 0)
        val withSoul = EquipmentPowerService.powerOf(level, EquipmentPowerService.plus(equip, soulB))
        // 增量 = atk 60(1:1) + hp 200/10=20 + matk 20×0.5=10 + pdef 25×0.2=5
        //        + mdef 15×0.2=3 + critRate 10×5=50 + critDmg 170×0.2=34
        assertEquals(60L + 20L + 10L + 5L + 3L + 50L + 34L, withSoul - base,
            "武魂战力贡献权重必须与装备加成一致（atk 1:1、hp/10、五属性 POWER_*_WEIGHT）")
    }

    @Test
    fun `getGameState exposes soulRarity badge, soul combat stats and seven-row identity`() {
        val p = profile()
        p.martialSoulName = "白虎"
        p.battleSoulPower = 157
        stubGameState(p)
        whenever(achievementService.unlockedBonus(1L)).thenReturn(EquipmentBonus(0, 0))

        val resp = gameService.getGameState(1L)

        // 品质徽章：由名字反查，不落库
        assertEquals("RARE", resp.profile.soulRarity)
        assertEquals("白虎", resp.profile.martialSoulName)
        // combatStats 含武魂五属性（level=5：双防基础 10）
        assertEquals(10, resp.combatStats.critRate)
        assertEquals(320, resp.combatStats.critDmg)
        assertEquals(35L, resp.combatStats.pdef)
        assertEquals(25L, resp.combatStats.mdef)
        // 七行求和恒等：base/ring/core/bone/achievement/prestige/soul == power
        val d = resp.powerDetail
        assertEquals(
            resp.power,
            d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement + d.prestige + d.soul,
            "觉醒玩家的七行战力明细必须与 power 严格一致"
        )
        assertTrue(d.soul > 0L, "觉醒玩家的 soul 行必须为正（白虎 atk 60 + hp 20 + 五属性折算）")
    }

    @Test
    fun `unawakened player keeps legacy profile values with zero soul row`() {
        val p = profile()
        stubGameState(p)
        whenever(achievementService.unlockedBonus(1L)).thenReturn(EquipmentBonus(0, 0))

        val resp = gameService.getGameState(1L)

        assertNull(resp.profile.soulRarity, "未觉醒 soulRarity 为 null")
        assertNull(resp.profile.martialSoulName)
        assertEquals(0L, resp.powerDetail.soul, "未觉醒 soul 行恒 0")
        assertEquals(0, resp.combatStats.critRate)
        assertEquals(150, resp.combatStats.critDmg)
        // soul 行 0 时七行恒等退化为原六行恒等
        val d = resp.powerDetail
        assertEquals(resp.power, d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement + d.prestige)
    }

    // ======== 第二十轮武魂技能 ========

    @Test
    fun `battle log carries soul skill name for awakened player`() {
        // 蓝银草·缠绕（SINGLE 140/3）：第 1 回合首放（log 首行带技能名），冷却 3 → 第 2 回合不放
        val p = profile()
        p.martialSoulName = "蓝银草"
        p.currentHp = 350L
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(equippedRingRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedBoneRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedCoreRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(backpackRepo.countByUserId(1L)).thenReturn(0L)

        val resp = gameService.battle(1L)

        assertTrue(resp.won, "level=5 满血（蓝银草加成）打 1-1 怪应确定性获胜")
        assertEquals("缠绕", resp.battleLog.first().skillName, "第 1 回合应首放武魂技能")
        assertTrue(resp.battleLog.drop(1).all { it.skillName == null }, "cooldown=3 时第 2 回合不应释放")
    }

    @Test
    fun `profile dto exposes soulSkillName from soul lookup and null when unawakened`() {
        val awakened = profile()
        awakened.martialSoulName = "白虎"
        stubGameState(awakened)
        whenever(achievementService.unlockedBonus(1L)).thenReturn(EquipmentBonus(0, 0))
        assertEquals("白虎烈光波", gameService.getGameState(1L).profile.soulSkillName)

        val plain = profile()
        stubGameState(plain)
        whenever(achievementService.unlockedBonus(1L)).thenReturn(EquipmentBonus(0, 0))
        assertNull(gameService.getGameState(1L).profile.soulSkillName, "未觉醒 soulSkillName 为 null")
    }

    /** getGameState 的仓库/服务桩（空装备空成就，GameServiceTest 同款接线；p 为注册进桩的存档实例） */
    private fun stubGameState(p: PlayerProfileEntity) {
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        whenever(talentRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedRingRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedBoneRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(equippedCoreRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(backpackRepo.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        whenever(checkInService.getCheckInStatus(1L)).thenReturn(CheckInStatusDto())
        whenever(dailyQuestService.getTodayStatus(1L)).thenReturn(DailyQuestsDto())
        whenever(achievementService.getStatus(1L)).thenReturn(emptyList())
    }
}
