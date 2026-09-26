package com.douluodalu.game.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
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
 * 第十九轮流派集成断言（mock 仓库 + 真实 GameService/EquipmentPowerService，不起 Spring）：
 *  - 数据完整性：GameBalance.SCHOOLS 六流派系数/图标/门槛与 shared ModelsExt.kt:615-641 逐字对照；
 *  - chooseSchool 流程：未知流派拒绝 / 首选免费落库 / 门槛拦截（SUPPORT Lv.49/1转拒、Lv.50+1转过）/
 *    已选同流派拒绝 / 重选扣 5000 金 / 金币不足零改动 / 不重 roll 武魂、不触发成就 sync；
 *  - 系数接入：playerCombatStats 乘区（PHYSICAL atk×1.30 matk×0.45）、maxHp 乘 hpMod（乘在
 *    基础+加成加总后）、八行战力恒等（school 行 = 含流派 − 七行之和）、null 流派零漂移回归。
 */
class SchoolIntegrationTest {
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
            achievementService,
            SimpleMeterRegistry()
        )
    }

    private fun profile(userId: Long = 1L) = PlayerProfileEntity(userId = userId, level = 5)

    // ======== 数据完整性（shared ModelsExt.kt:615-641 逐字对照） ========

    @Test
    fun `six schools match the shared stat table verbatim`() {
        assertEquals(6, GameBalance.SCHOOLS.size)
        val table = mapOf(
            "BALANCED" to Quad("⚖️", "均衡流派", "BASIC", GameBalance.SchoolMods(1.05, 1.00, 1.00, 1.05, 1.05, 5, 5), 1, 0),
            "PHYSICAL" to Quad("⚔️", "物理流派", "BASIC", GameBalance.SchoolMods(1.00, 1.30, 0.45, 1.15, 0.70, 8, 0), 1, 0),
            "MAGIC" to Quad("🔮", "法系流派", "BASIC", GameBalance.SchoolMods(0.95, 0.45, 1.30, 0.70, 1.15, 0, 8), 1, 0),
            "SUPPORT" to Quad("🛡️", "辅助流派", "SPECIAL", GameBalance.SchoolMods(1.15, 0.80, 0.90, 1.20, 1.20, 3, 0), 50, 1),
            "CONTROL" to Quad("🌿", "控制流派", "SPECIAL", GameBalance.SchoolMods(1.05, 0.95, 1.10, 1.00, 1.05, 6, 6), 70, 2),
            "ASSASSIN" to Quad("🗡️", "暗杀流派", "SPECIAL", GameBalance.SchoolMods(0.90, 1.40, 0.50, 0.60, 0.60, 12, 15), 90, 3)
        )
        for (s in GameBalance.SCHOOLS) {
            val q = table[s.name] ?: error("意外流派 ${s.name}")
            assertEquals(q.icon, s.icon, "${s.name} icon")
            assertEquals(q.displayName, s.displayName, "${s.name} displayName")
            assertEquals(q.category, s.category, "${s.name} category")
            assertEquals(q.mods, s.mods, "${s.name} mods 必须与 shared SchoolStatMods 逐字一致")
            assertEquals(q.requiredLevel, s.requiredLevel, "${s.name} requiredLevel")
            assertEquals(q.requiredPrestige, s.requiredPrestige, "${s.name} requiredPrestige")
            assertTrue(s.description.isNotBlank(), "${s.name} description 不为空")
        }
        assertEquals(table.keys, GameBalance.SCHOOLS.map { it.name }.toSet())
        assertEquals(5000L, GameBalance.RESCHOOL_COST_GOLD, "重选定价对齐 REAWAKEN_COST_GOLD 惯例")
    }

    /** 断言用七元组（Kotlin 无内置，替代 Pair 嵌套） */
    private data class Quad(
        val icon: String, val displayName: String, val category: String,
        val mods: GameBalance.SchoolMods, val requiredLevel: Int, val requiredPrestige: Int
    )

    @Test
    fun `school gate boundary table`() {
        // 基础流派开局可选；进阶流派双门槛（等级+转数）逐一对照边界
        val byName = { n: String -> GameBalance.schoolByName(n)!! }
        for (basic in listOf("BALANCED", "PHYSICAL", "MAGIC")) {
            assertTrue(GameBalance.isSchoolUnlocked(byName(basic), 1, 0), "$basic Lv1/0转 开局可选")
        }
        // SUPPORT：Lv.50 + 1转
        assertFalse(GameBalance.isSchoolUnlocked(byName("SUPPORT"), 49, 1), "SUPPORT Lv49 拒")
        assertFalse(GameBalance.isSchoolUnlocked(byName("SUPPORT"), 50, 0), "SUPPORT 0转拒")
        assertTrue(GameBalance.isSchoolUnlocked(byName("SUPPORT"), 50, 1), "SUPPORT Lv50+1转 过")
        // CONTROL：Lv.70 + 2转；ASSASSIN：Lv.90 + 3转
        assertFalse(GameBalance.isSchoolUnlocked(byName("CONTROL"), 70, 1))
        assertTrue(GameBalance.isSchoolUnlocked(byName("CONTROL"), 70, 2))
        assertFalse(GameBalance.isSchoolUnlocked(byName("ASSASSIN"), 90, 2))
        assertTrue(GameBalance.isSchoolUnlocked(byName("ASSASSIN"), 90, 3))
    }

    // ======== chooseSchool 流程 ========

    @Test
    fun `unknown school name fails with zero mutation`() {
        val p = profile()
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.chooseSchool(1L, "SWORD")

        assertFalse(resp.success)
        assertEquals("", resp.chosenSchool)
        assertEquals("未知流派", resp.message)
        assertNull(p.chosenSchool, "未知流派不得写档")
        assertEquals(0L, p.gold, "未知流派不得扣金")
    }

    @Test
    fun `first choice is free and persists chosenSchool`() {
        val p = profile()
        p.martialSoulName = "白虎" // 已觉醒玩家选流派：武魂不被重 roll
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.chooseSchool(1L, "MAGIC")

        assertTrue(resp.success)
        assertEquals("MAGIC", resp.chosenSchool)
        assertEquals("MAGIC", p.chosenSchool, "chosenSchool 必须落库")
        assertEquals(0L, p.gold, "首选免费")
        assertEquals("白虎", p.martialSoulName, "选流派不重 roll 武魂（shared 会 roll，实现分歧留档）")
        assertTrue(resp.message.contains("法系流派"))
        // 成就无流派维度 → 不触发 sync（与 awaken 同款决定）
        verify(achievementService, never()).sync(any())
    }

    @Test
    fun `gate blocks SUPPORT below level 50 or prestige 1 with requirement message`() {
        val p = profile()
        p.level = 49
        p.prestigeCount = 1
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.chooseSchool(1L, "SUPPORT")

        assertFalse(resp.success)
        assertEquals("", resp.chosenSchool)
        assertTrue(resp.message.contains("需要 Lv.50 且转生≥1"), "门槛 message 必须给出要求（实测：${resp.message}）")
        assertNull(p.chosenSchool, "门槛不足零改动")
    }

    @Test
    fun `gate passes SUPPORT exactly at level 50 with 1 prestige`() {
        val p = profile()
        p.level = 50
        p.prestigeCount = 1
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.chooseSchool(1L, "SUPPORT")

        assertTrue(resp.success)
        assertEquals("SUPPORT", p.chosenSchool)
    }

    @Test
    fun `rechoosing the same school is rejected without charge`() {
        val p = profile()
        p.chosenSchool = "PHYSICAL"
        p.gold = 100_000L
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.chooseSchool(1L, "PHYSICAL")

        assertFalse(resp.success)
        assertTrue(resp.message.contains("已选择物理流派"))
        assertEquals("PHYSICAL", p.chosenSchool)
        assertEquals(100_000L, p.gold, "重复选择不得扣金")
    }

    @Test
    fun `reschool costs 5000 gold and rewrites chosenSchool`() {
        val p = profile()
        p.chosenSchool = "BALANCED"
        p.gold = 12_000L
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.chooseSchool(1L, "PHYSICAL")

        assertTrue(resp.success)
        assertEquals("PHYSICAL", resp.chosenSchool)
        assertEquals("PHYSICAL", p.chosenSchool, "改选后 chosenSchool 落库")
        assertEquals(7_000L, p.gold, "改选应扣除 RESCHOOL_COST_GOLD")
        assertTrue(resp.message.contains("5000"))
    }

    @Test
    fun `reschool with insufficient gold fails with zero mutation`() {
        val p = profile()
        p.chosenSchool = "BALANCED"
        p.level = 95
        p.prestigeCount = 3
        p.gold = GameBalance.RESCHOOL_COST_GOLD - 1
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val resp = gameService.chooseSchool(1L, "ASSASSIN")

        // breakthrough 惯例：HTTP 200 + success=false + message，不抛异常、零改动
        assertFalse(resp.success)
        assertEquals("", resp.chosenSchool)
        assertTrue(resp.message.contains("5000"))
        assertEquals(GameBalance.RESCHOOL_COST_GOLD - 1, p.gold, "失败不得扣金")
        assertEquals("BALANCED", p.chosenSchool, "失败不得改流派")
    }

    // ======== 系数接入 ========

    @Test
    fun `physical school multiplies combat stats per the mods table`() {
        val school = GameBalance.schoolByName("PHYSICAL")!!.mods
        // Lv.5 零加成：atk 基础 100、matk 基础 50、双防基础 10
        val combat = GameService.playerCombatStats(5, 0, EquipmentBonus(0, 0), school)
        assertEquals(130L, combat.atk, "atk (100)×1.30")
        assertEquals(22L, combat.matk, "matk (50)×0.45 = 22.5 截断")
        assertEquals(11L, combat.pdef, "pdef (10)×1.15 = 11.5 截断")
        assertEquals(7L, combat.mdef, "mdef (10)×0.70")
        assertEquals(8, combat.critRate, "暴击率走加数 +8")
        assertEquals(150, combat.critDmg, "PHYSICAL 无爆伤加成")
    }

    @Test
    fun `chosen school flows into getGameState combat stats`() {
        val p = profile()
        p.chosenSchool = "PHYSICAL"
        stubGameState(p)
        whenever(achievementService.unlockedBonus(1L)).thenReturn(EquipmentBonus(0, 0))

        val resp = gameService.getGameState(1L)

        // level=5：matk 基础 50×0.45=22、pdef 10×1.15=11、mdef 10×0.70=7、暴击 0+8/150
        assertEquals(22L, resp.combatStats.matk)
        assertEquals(11L, resp.combatStats.pdef)
        assertEquals(7L, resp.combatStats.mdef)
        assertEquals(8, resp.combatStats.critRate)
        assertEquals(150, resp.combatStats.critDmg)
        assertEquals("PHYSICAL", resp.profile.chosenSchool)
    }

    @Test
    fun `max hp multiplies hpMod after summing base and folded bonuses`() {
        // 生产 battle/towerBattle/仿真镜像的 maxHp 组装唯一乘区（schoolScaledMaxHp）：
        // 乘在「基础(已乘转生) + 加成包(装备/成就/武魂)」加总之后——武魂 hp 也吃到系数
        val base350PlusSoulHp200 = 550L // Lv.5 基础 350 + 白虎武魂 hp 200
        assertEquals(550L, GameService.schoolScaledMaxHp(base350PlusSoulHp200, null), "未选流派恒等（零漂移）")
        assertEquals(577L, GameService.schoolScaledMaxHp(base350PlusSoulHp200, GameBalance.schoolByName("BALANCED")!!.mods),
            "BALANCED 550×1.05 = 577.5 截断")
        assertEquals(495L, GameService.schoolScaledMaxHp(base350PlusSoulHp200, GameBalance.schoolByName("ASSASSIN")!!.mods),
            "ASSASSIN 550×0.90")
        assertEquals(632L, GameService.schoolScaledMaxHp(base350PlusSoulHp200, GameBalance.schoolByName("SUPPORT")!!.mods),
            "SUPPORT 550×1.15 = 632.5 截断")
    }

    @Test
    fun `power detail keeps eight-row identity with nonzero school row`() {
        val p = profile()
        p.chosenSchool = "PHYSICAL"
        p.martialSoulName = "白虎" // 武魂 hp/atk 也吃流派系数（加成包同通道）
        p.battleSoulPower = 157
        stubGameState(p)
        whenever(achievementService.unlockedBonus(1L)).thenReturn(EquipmentBonus(0, 0))

        val resp = gameService.getGameState(1L)

        val d = resp.powerDetail
        // 八行求和恒等：base/ring/bone/core/achievement/prestige/soul/school == power
        assertEquals(
            resp.power,
            d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement + d.prestige + d.soul + d.school,
            "选流派玩家的八行战力明细必须与 power 严格一致"
        )
        // school 行 = 含流派战力 − 七行之和（差值法定义复核）：白虎加成包 (60,200,20,25,15,10,170)
        // 经 PHYSICAL applySchool → (78,200,9,28,17,18,170)，战力增量 52
        val withSoul = EquipmentPowerService.plus(
            EquipmentPowerService.applyPrestige(EquipmentBonus(0, 0), 0),
            GameService.soulBonusOf("白虎", 0)
        )
        val expected = EquipmentPowerService.powerOf(5, EquipmentPowerService.applySchool(withSoul, GameBalance.schoolByName("PHYSICAL")!!.mods)) -
                EquipmentPowerService.powerOf(5, withSoul)
        assertEquals(expected, d.school, "school 行必须等于含流派口径与七行口径的差值")
        assertTrue(d.school > 0L, "PHYSICAL atk×1.30 使白虎 atk 60→78，school 行必须为正")
    }

    @Test
    fun `null school keeps legacy values with zero drift`() {
        val p = profile()
        stubGameState(p)
        whenever(achievementService.unlockedBonus(1L)).thenReturn(EquipmentBonus(0, 0))

        val resp = gameService.getGameState(1L)

        // chosenSchool=null → 系数恒等：combatStats/power/明细与第十八轮口径逐位一致
        assertNull(resp.profile.chosenSchool)
        assertEquals(50L, resp.combatStats.matk, "Lv.5 零加成 matk 基础 = 25+25 = 50")
        assertEquals(10L, resp.combatStats.pdef)
        assertEquals(10L, resp.combatStats.mdef)
        assertEquals(0, resp.combatStats.critRate)
        assertEquals(150, resp.combatStats.critDmg)
        assertEquals(0L, resp.powerDetail.school, "未选流派 school 行恒 0")
        // school 行 0 时八行恒等退化为原七行恒等
        val d = resp.powerDetail
        assertEquals(
            resp.power,
            d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement + d.prestige + d.soul
        )
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
