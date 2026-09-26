package com.douluodalu.game.service

import com.douluodalu.game.model.GameBalance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * 第十七轮战斗模型扩展：resolveBattle 新数学（防御 defFactor / 暴击 / 魔物理混合 / 怪物不暴击）
 * 与 monsterStats 新字段的纯函数断言。全部固定种子、不起 Spring。
 *
 * 掷点次序契约（GameService.resolveBattle 注释同源，此处用「同种子复刻掷点序列」双向锚定）：
 *   玩家三掷 ①攻击类型 → ②暴击 → ③浮动；怪物两掷 ①攻击类型 → ②浮动（v1 不暴击）。
 */
class BattleMathTest {

    private fun player(
        atk: Long = 100, matk: Long = 100, pdef: Long = 0, mdef: Long = 0,
        critRate: Int = 0, critDmg: Int = 150
    ) = GameService.CombatStats(atk, matk, pdef, mdef, critRate, critDmg)

    private fun monster(
        hp: Long = 1_000_000, atk: Int = 100, matk: Int = atk, pdef: Int = 0, mdef: Int = 0
    ) = GameService.MonsterStats(hp, atk, matk, pdef, mdef)

    /** shared 引擎同源减伤公式（GameService.defFactor 镜像，锚定公式不漂移） */
    private fun defFactor(def: Long): Double =
        (1.0 - def.toDouble() / (def + GameBalance.DEF_K)).coerceIn(0.1, 1.0)

    // ======== 掷点次序与伤害算式（同种子复刻） ========

    @Test
    fun `player round consumes rng in fixed order type, crit, variance`() {
        val p = player(atk = 200, matk = 60, critRate = 18, critDmg = 160)
        val m = monster(hp = 1_000_000, atk = 10, pdef = 25, mdef = 25)
        val seed = 20260926L
        val outcome = GameService.resolveBattle(p, 10_000, m, 1, Random(seed))

        // 复刻掷点序列：①类型（nextInt(100) < 15 → 魔法）→ ②暴击 → ③浮动；伤害 = (攻击+浮动)×defFactor(×爆伤)
        val r = Random(seed)
        val magic = r.nextInt(100) < GameBalance.ATTACK_MAGIC_SHARE
        val crit = r.nextInt(100) < p.critRate
        val base = if (magic) p.matk else p.atk
        val variance = r.nextLong(base / 5)
        var dmg = (base + variance) * defFactor((if (magic) m.mdef else m.pdef).toLong())
        if (crit) dmg *= p.critDmg / 100.0
        assertEquals(dmg.toLong().coerceAtLeast(1), outcome.log[0].playerDamage,
            "玩家伤害必须按 类型→暴击→浮动 的掷点次序与 (攻击+浮动)×defFactor×爆伤 算式产出")
    }

    @Test
    fun `monster round consumes rng in fixed order type, variance and never crits`() {
        val p = player(atk = 10, matk = 10, pdef = 25, mdef = 25, critRate = 100, critDmg = 999)
        val m = monster(hp = 1_000_000, atk = 200, matk = 200)
        val seed = 42L
        val outcome = GameService.resolveBattle(p, 1_000_000, m, 1, Random(seed))

        // 复刻同回合掷点序列：玩家三掷（类型→暴击→浮动）先行消耗，随后怪物两掷（类型→浮动）：
        // 若怪物存在任何暴击掷点/乘区，浮动脉冲会移位或伤害放大，逐位断言即失败
        val r = Random(seed)
        val pBase = if (r.nextInt(100) < GameBalance.ATTACK_MAGIC_SHARE) p.matk else p.atk
        r.nextInt(100)                       // 玩家暴击掷（结果不影响本断言，仅消耗 RNG）
        r.nextLong((pBase / 5).coerceAtLeast(1))  // 玩家浮动掷（仅消耗 RNG）
        val magic = r.nextInt(100) < GameBalance.ATTACK_MAGIC_SHARE
        val variance = r.nextLong(m.atk / 5L)
        val expected = (((m.atk + variance) * defFactor((if (magic) p.mdef else p.pdef).toLong())).toLong()).coerceAtLeast(1)
        assertEquals(expected, outcome.log[0].monsterDamage, "怪物伤害按 类型→浮动 两掷产出（无暴击掷点）")
    }

    // ======== defFactor 边界 ========

    @Test
    fun `zero defense means no mitigation`() {
        // def=0 → defFactor=1.0：玩家每回合伤害 = atk + [0, atk/5)，即 [100, 120)
        val outcome = GameService.resolveBattle(player(atk = 100), 10_000, monster(), 30, Random(7))
        for (round in outcome.log) {
            assertTrue(round.playerDamage in 100..119, "无防御时应无减免（实测 ${round.playerDamage}）")
        }
    }

    @Test
    fun `huge defense clamps mitigation to 0_1 floor`() {
        // def 巨大 → 1 - def/(def+200) → 0 被夹取到 0.1 下限：伤害 = (atk+浮动)×0.1 ∈ [10, 12]
        val huge = Int.MAX_VALUE / 2
        val outcome = GameService.resolveBattle(player(atk = 100), 10_000, monster(pdef = huge, mdef = huge), 30, Random(7))
        for (round in outcome.log) {
            assertTrue(round.playerDamage in 10..12, "极端防御应命中 0.1 下限夹取（实测 ${round.playerDamage}）")
        }
    }

    @Test
    fun `defense mitigation is monotone - higher def never increases damage taken`() {
        // 物理伤害吃 pdef：pdef=50 vs pdef=150（同种子、同浮动流），后者每回合伤害必须 ≤ 前者
        val weakDef = GameService.resolveBattle(player(atk = 300, matk = 300), 100_000, monster(pdef = 50, mdef = 50), 30, Random(9))
        val strongDef = GameService.resolveBattle(player(atk = 300, matk = 300), 100_000, monster(pdef = 150, mdef = 150), 30, Random(9))
        for (i in weakDef.log.indices) {
            assertTrue(strongDef.log[i].playerDamage <= weakDef.log[i].playerDamage,
                "防御更高时同种子伤害不得更高：round=$i ${strongDef.log[i].playerDamage} vs ${weakDef.log[i].playerDamage}")
        }
    }

    @Test
    fun `physical and magical attacks consume matching defense`() {
        // 玩家 pdef=0（物理全额）、mdef 巨大（魔法 0.1 下限）：怪物 atk=200 / matk=200
        // 物理回合伤害 ∈ [200, 240)、魔法回合 ∈ [20, 24) —— 魔法攻击确实吃了 mdef
        val huge = Int.MAX_VALUE / 2L
        val p = player(atk = 10, matk = 10, pdef = 0, mdef = huge)
        val outcome = GameService.resolveBattle(p, 1_000_000, monster(atk = 200, matk = 200), 30, Random(11))
        for (round in outcome.log) {
            val physicalLike = round.monsterDamage >= 200
            val magicalLike = round.monsterDamage <= 30
            assertTrue(physicalLike || magicalLike, "怪物魔法攻击必须吃玩家 mdef（实测 ${round.monsterDamage}）")
        }
    }

    // ======== 暴击 ========

    @Test
    fun `crit multiplies damage by critDmg exactly when triggered`() {
        // critRate=100：每回合必暴击；同种子 critRate=0 的伤害流 D（双精度）×2.0（critDmg=200）截断
        // 与 D.toLong()×2 逐位相等（×2.0 是双精度精确缩放）→ 暴击伤害必须恰为非暴击的 2 倍
        val noCrit = GameService.resolveBattle(player(atk = 150, critRate = 0, critDmg = 200), 100_000, monster(atk = 10), 30, Random(5))
        val allCrit = GameService.resolveBattle(player(atk = 150, critRate = 100, critDmg = 200), 100_000, monster(atk = 10), 30, Random(5))
        for (i in noCrit.log.indices) {
            assertEquals(noCrit.log[i].playerDamage * 2, allCrit.log[i].playerDamage,
                "critDmg=200 时暴击回合伤害必须恰为非暴击的 2 倍：round=$i")
        }
    }

    @Test
    fun `crit never triggers at zero rate and monster never crits`() {
        // critRate=0 + critDmg=999：玩家伤害必须全落在 [atk, atk×1.2) —— 无暴击翻倍
        val outcome = GameService.resolveBattle(player(atk = 100, critRate = 0, critDmg = 999), 100_000, monster(atk = 10), 30, Random(3))
        for (round in outcome.log) {
            assertTrue(round.playerDamage in 100..119, "critRate=0 时不得暴击（实测 ${round.playerDamage}）")
            // 怪物侧同样两掷封顶 atk×1.2（无暴击乘区）
            assertTrue(round.monsterDamage in 10..12, "怪物不暴击（实测 ${round.monsterDamage}）")
        }
    }

    @Test
    fun `crit rate drives trigger frequency statistically`() {
        // critRate=50、critDmg=200：大样本下暴击回合（伤害 == 非暴击 2 倍流）占比应接近 50%
        // 用同种子 0% 流做基准，统计 50% 流中伤害 ≥ 基准 2 倍 -1（容截断）的回合数
        val rounds = 4000
        val base = GameService.resolveBattle(player(atk = 100, critRate = 0, critDmg = 200), Long.MAX_VALUE / 4, monster(atk = 1), rounds, Random(17))
        val half = GameService.resolveBattle(player(atk = 100, critRate = 50, critDmg = 200), Long.MAX_VALUE / 4, monster(atk = 1), rounds, Random(17))
        var crits = 0
        for (i in base.log.indices) {
            if (half.log[i].playerDamage >= base.log[i].playerDamage * 2 - 1) crits++
        }
        val share = crits * 100.0 / rounds
        assertTrue(share in 45.0..55.0, "critRate=50 的触发频率应在 45%~55%（实测 $share%）")
    }

    // ======== 魔法/物理分流比例 ========

    @Test
    fun `magic share of player and monster attacks converges to 15 percent`() {
        // matk = 3×atk、无防御：魔法回合伤害 ∈ [300, 360)，物理 ∈ [100, 120) —— 干净分离。
        // 怪物侧 matk 刻意取 300（生产 monsterStats 恒 matk=atk，此处用 MonsterStats 的自由度
        // 单测 resolveBattle 对怪物 matk 的消费路径）
        val rounds = 4000
        val outcome = GameService.resolveBattle(
            player(atk = 100, matk = 300), Long.MAX_VALUE / 4, monster(atk = 100, matk = 300), rounds, Random(23)
        )
        val playerMagic = outcome.log.count { it.playerDamage >= 250 }
        val monsterMagic = outcome.log.count { it.monsterDamage >= 250 }
        val playerShare = playerMagic * 100.0 / rounds
        val monsterShare = monsterMagic * 100.0 / rounds
        assertTrue(playerShare in 12.0..18.0, "玩家魔法占比应收敛到 15%（实测 $playerShare%）")
        assertTrue(monsterShare in 12.0..18.0, "怪物魔法占比应收敛到 15%（实测 $monsterShare%）")
    }

    // ======== 伤害下限与击杀截断 ========

    @Test
    fun `damage has a floor of one against extreme defense`() {
        // atk=1 且 def 巨大：(1+0)×0.1 → 截断为 0 必须兜底到 1（defFactor 0.1 下限配套的保底语义）
        val huge = Int.MAX_VALUE / 2
        val outcome = GameService.resolveBattle(player(atk = 1, matk = 1), 100_000, monster(hp = 100, atk = 1, pdef = huge, mdef = huge), 200, Random(31))
        assertTrue(outcome.log.isNotEmpty())
        // 击杀回合怪物伤害按既有语义记 0（怪物未出手），其余回合双方伤害均 ≥ 1
        assertTrue(outcome.log.all { it.playerDamage >= 1 }, "玩家伤害下限必须为 1")
        assertTrue(outcome.log.dropLast(1).all { it.monsterDamage >= 1 }, "非击杀回合怪物伤害下限必须为 1")
        assertTrue(outcome.won && outcome.rounds <= 100, "atk=1 vs 极端防御也应靠保底伤害在 100 回合内磨死 100 HP")
    }

    // ======== monsterStats 新字段 ========

    @Test
    fun `monster stats expose matk equal to atk and pdef mdef scaled by factor`() {
        val m = GameService.monsterStats(mapId = 3, stage = 7)
        assertEquals(m.atk.toLong(), m.matk.toLong(), "怪 matk 必须等于 atk")
        assertEquals(m.pdef.toLong(), m.mdef.toLong(), "怪双防同源")
        val expectedAtk = ((GameBalance.MONSTER_ATK_BASE + 3 * GameBalance.MONSTER_ATK_PER_MAP) *
                (1.0 + 7 * GameBalance.MONSTER_STAGE_GROWTH)).toInt()
        assertEquals(expectedAtk, m.atk)
        assertEquals((expectedAtk * GameBalance.MONSTER_DEF_FACTOR).toInt(), m.pdef,
            "怪双防 = atk × MONSTER_DEF_FACTOR")
    }

    @Test
    fun `monster defenses grow monotonically with map and stage`() {
        for (stage in 1 until GameBalance.STAGES_PER_MAP) {
            for (mapId in 0 until GameBalance.MAX_MAP_ID) {
                val cur = GameService.monsterStats(mapId, stage)
                val nextMap = GameService.monsterStats(mapId + 1, stage)
                val nextStage = GameService.monsterStats(mapId, stage + 1)
                // 跨图（Δatk=25 → Δpdef≥8.75）取整后仍严格递增
                assertTrue(nextMap.pdef > cur.pdef && nextMap.mdef > cur.mdef,
                    "mapId $mapId→${mapId + 1}（stage=$stage）双防应严格单调增加")
                // 跨关（Δatk≈0.12×atk，低图取整可吞掉 <1 的双防步长）断言非严格不减
                assertTrue(nextStage.pdef >= cur.pdef && nextStage.mdef >= cur.mdef,
                    "stage $stage→${stage + 1}（mapId=$mapId）双防应单调不减")
                assertTrue(nextMap.hp > cur.hp && nextStage.hp > cur.hp, "hp 单调性回归")
                assertTrue(nextStage.atk >= cur.atk, "atk 单调性回归")
            }
        }
    }

    // ======== playerCombatStats 组装 ========

    @Test
    fun `player combat stats assemble from level and equipment bonus`() {
        // Lv.50 零加成：atk=550、matk=镜像×0.5=275、双防=50×2=100、暴击 0/150
        val bare = GameService.playerCombatStats(50, 0, EquipmentBonus(0, 0))
        assertEquals(550L, bare.atk)
        assertEquals(275L, bare.matk)
        assertEquals(100L, bare.pdef)
        assertEquals(100L, bare.mdef)
        assertEquals(0, bare.critRate)
        assertEquals(150, bare.critDmg)

        // 七字段加成并入（装备侧五属性恒 0，成就侧带值——这里直接以加成包注入）
        val geared = GameService.playerCombatStats(
            50, 0,
            EquipmentBonus(atkBonus = 10, hpBonus = 100, matkBonus = 20, pdefBonus = 5, mdefBonus = 5, critRateBonus = 3, critDmgBonus = 25)
        )
        assertEquals(560L, geared.atk)
        assertEquals(295L, geared.matk)
        assertEquals(105L, geared.pdef)
        assertEquals(105L, geared.mdef)
        assertEquals(3, geared.critRate)
        assertEquals(175, geared.critDmg)
    }

    @Test
    fun `player combat stats scale base attributes with prestige multiplier`() {
        // 2 转倍率 1.2：基础部分 ×1.2（atk 550→660、matk 275→330、双防 100→120），加成部分不重复乘
        val equip = EquipmentBonus(atkBonus = 100, hpBonus = 0, matkBonus = 50, pdefBonus = 10, mdefBonus = 10)
        val s = GameService.playerCombatStats(50, 2, equip)
        assertEquals((550L * 1.2).toLong() + 100, s.atk)
        assertEquals((275L * 1.2).toLong() + 50, s.matk)
        assertEquals((100L * 1.2).toLong() + 10, s.pdef)
        assertEquals((100L * 1.2).toLong() + 10, s.mdef)
        // 0 转时与倍率恒 1.0 的逐位一致回归
        val zero = GameService.playerCombatStats(50, 0, equip)
        assertEquals(650L, zero.atk)
        assertEquals(325L, zero.matk)
        assertEquals(110L, zero.pdef)
        assertEquals(110L, zero.mdef)
    }

    // ======== 第十九轮流派：乘区作用于「基础+加成」之和，暴击走加数 ========

    @Test
    fun `school mods multiply base plus bonus and add crit points`() {
        val equip = EquipmentBonus(atkBonus = 100, hpBonus = 500, matkBonus = 50, pdefBonus = 20, mdefBonus = 20)
        // Lv.50：atk 基础 550、matk 基础 275、双防基础 100；ASSASSIN(0.90,1.40,0.50,0.60,0.60,12,15)
        val assassin = GameService.playerCombatStats(50, 0, equip, GameBalance.schoolByName("ASSASSIN")!!.mods)
        // 650×1.4 的双精度表示为 909.999…（1.4 无法精确表示、向下圆整），沿用 .toLong() 截断惯例
        // （与 applyPrestige 同款）→ 909
        assertEquals(909L, assassin.atk, "atk (550+100)×1.40 截断")
        assertEquals(162L, assassin.matk, "matk (275+50)×0.50 = 162.5 截断")
        assertEquals(72L, assassin.pdef, "pdef (100+20)×0.60 = 72")
        assertEquals(72L, assassin.mdef)
        assertEquals(12, assassin.critRate, "暴击率走加数：0+12")
        assertEquals(165, assassin.critDmg, "爆伤走加数：150+15")
        // BALANCED：atk/matk ×1.00、双防 ×1.05、暴击 +5/+5
        val balanced = GameService.playerCombatStats(50, 0, equip, GameBalance.schoolByName("BALANCED")!!.mods)
        assertEquals(650L, balanced.atk)
        assertEquals(325L, balanced.matk)
        assertEquals(126L, balanced.pdef, "(100+20)×1.05 = 126")
        assertEquals(126L, balanced.mdef)
        assertEquals(5, balanced.critRate)
        assertEquals(155, balanced.critDmg)
        // school=null（未选流派）与旧口径逐位一致（零漂移回归）
        val none = GameService.playerCombatStats(50, 0, equip)
        assertEquals(650L, none.atk)
        assertEquals(325L, none.matk)
        assertEquals(120L, none.pdef)
        assertEquals(120L, none.mdef)
        assertEquals(0, none.critRate)
        assertEquals(150, none.critDmg)
    }

    @Test
    fun `assassin crit burst outdamages balanced school at the same seed`() {
        // ASSASSIN（atk×1.40 + 暴击 12%/165%）vs BALANCED（×1.00 + 5%/155%）：
        // 同种子、恒定零防怪、双方都打满 4000 回合，期望伤害比 ≈1.39（atk 1.4 × 暴击期望 1.078/1.0275）
        val equip = EquipmentBonus(atkBonus = 100, hpBonus = 0, matkBonus = 50)
        val balanced = GameService.playerCombatStats(50, 0, equip, GameBalance.schoolByName("BALANCED")!!.mods)
        val assassin = GameService.playerCombatStats(50, 0, equip, GameBalance.schoolByName("ASSASSIN")!!.mods)
        val monster = GameService.MonsterStats(hp = 10_000_000, atk = 1, matk = 1, pdef = 0, mdef = 0)
        val rounds = 4000
        val b = GameService.resolveBattle(balanced, 100_000, monster, rounds, Random(97))
        val a = GameService.resolveBattle(assassin, 100_000, monster, rounds, Random(97))
        val balancedDmg = b.log.sumOf { it.playerDamage }
        val assassinDmg = a.log.sumOf { it.playerDamage }
        assertTrue(b.log.size == rounds && a.log.size == rounds, "双方都应打满 $rounds 回合（量级自检）")
        assertTrue(assassinDmg > balancedDmg * 12 / 10,
            "ASSASSIN 暴击流总伤必须显著高于 BALANCED（实测 $assassinDmg vs $balancedDmg）")
    }
}
