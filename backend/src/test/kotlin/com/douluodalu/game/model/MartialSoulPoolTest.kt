package com.douluodalu.game.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * 第十八轮武魂觉醒：GameBalance 武魂池区块断言。
 *  - 12 条定义与 shared 引擎 Models.kt MartialSoulPool（设计文档 §2.1 表）逐字对照（抽查锚点）；
 *  - 品质池转数门槛 §2.3（0转无 RARE+ / 5转全量）；
 *  - roll 权重 §2.2（注入固定 rng 保持确定性，边界用 nextDouble 恒 0 的桩 rng）。
 */
class MartialSoulPoolTest {

    @Test
    fun `pool has 12 souls with unique names and design-doc rarity distribution`() {
        assertEquals(12, GameBalance.MARTIAL_SOULS.size)
        assertEquals(12, GameBalance.MARTIAL_SOULS.map { it.name }.toSet().size, "武魂名不得重复（名字是反查主键）")
        // §2.1 品质分布：2 普通 / 2 精良 / 3 稀有 / 2 史诗 / 2 传说 / 1 神话
        val byRarity = GameBalance.MARTIAL_SOULS.groupingBy { it.rarity }.eachCount()
        assertEquals(2, byRarity[Rarity.COMMON])
        assertEquals(2, byRarity[Rarity.UNCOMMON])
        assertEquals(3, byRarity[Rarity.RARE])
        assertEquals(2, byRarity[Rarity.EPIC])
        assertEquals(2, byRarity[Rarity.LEGENDARY])
        assertEquals(1, byRarity[Rarity.MYTHIC])
    }

    @Test
    fun `spot-check souls against shared engine values (design doc 2-1)`() {
        // shared Models.kt:93 蓝银草: COMMON, 50/20/10/5/150/5/5
        val lanyin = GameBalance.soulByName("蓝银草")!!
        assertEquals(Rarity.COMMON, lanyin.rarity)
        assertEquals(50L, lanyin.baseHp); assertEquals(20, lanyin.baseAtk); assertEquals(10, lanyin.baseMatk)
        assertEquals(5, lanyin.critRate); assertEquals(150, lanyin.critDmg)
        assertEquals(5, lanyin.pdef); assertEquals(5, lanyin.mdef)
        // shared Models.kt:99 白虎: RARE, 200/60/20/10/170/25/15
        val baihu = GameBalance.soulByName("白虎")!!
        assertEquals(Rarity.RARE, baihu.rarity)
        assertEquals(200L, baihu.baseHp); assertEquals(60, baihu.baseAtk); assertEquals(20, baihu.baseMatk)
        assertEquals(10, baihu.critRate); assertEquals(170, baihu.critDmg)
        assertEquals(25, baihu.pdef); assertEquals(15, baihu.mdef)
        // shared Models.kt:102 六翼天使: LEGENDARY, 350/120/100/18/220/35/30
        val angel = GameBalance.soulByName("六翼天使")!!
        assertEquals(Rarity.LEGENDARY, angel.rarity)
        assertEquals(350L, angel.baseHp); assertEquals(120, angel.baseAtk); assertEquals(100, angel.baseMatk)
        assertEquals(18, angel.critRate); assertEquals(220, angel.critDmg)
        assertEquals(35, angel.pdef); assertEquals(30, angel.mdef)
        // shared Models.kt:104 修罗魔剑: MYTHIC, 500/200/150/25/300/40/35
        val xiuluo = GameBalance.soulByName("修罗魔剑")!!
        assertEquals(Rarity.MYTHIC, xiuluo.rarity)
        assertEquals(500L, xiuluo.baseHp); assertEquals(200, xiuluo.baseAtk); assertEquals(150, xiuluo.baseMatk)
        assertEquals(25, xiuluo.critRate); assertEquals(300, xiuluo.critDmg)
        assertEquals(40, xiuluo.pdef); assertEquals(35, xiuluo.mdef)
    }

    @Test
    fun `soulByName returns null for unknown or blank name`() {
        assertNull(GameBalance.soulByName("不存在的武魂"))
        assertNull(GameBalance.soulByName(""))
    }

    @Test
    fun `availableSoulPool gates rarity by prestige count (design doc 2-3)`() {
        fun names(p: Int) = GameBalance.availableSoulPool(p).map { it.name }.toSet()
        // 0 转：无 RARE+（2 COMMON + 2 UNCOMMON 恰好 4 条）
        val zero = names(0)
        assertEquals(setOf("蓝银草", "幽冥灵猫", "柔骨兔", "蓝银皇"), zero)
        // 1 转 +3 RARE；2 转 +2 EPIC；3 转 +2 LEGENDARY（4 转同 3 转）；5 转 +1 MYTHIC 全量
        assertEquals(zero + setOf("七宝琉璃塔", "邪火凤凰", "白虎"), names(1))
        assertEquals(names(1) + setOf("昊天锤", "九宝琉璃塔"), names(2))
        assertEquals(names(2) + setOf("六翼天使", "海神三叉戟"), names(3))
        assertEquals(names(3), names(4), "4 转门槛与 3 转相同（下一档是 5 转）")
        assertEquals(GameBalance.MARTIAL_SOULS.map { it.name }.toSet(), names(5))
        assertEquals(names(5), names(6), "5 转以上全量（MYTHIC 为上限）")
    }

    @Test
    fun `rollMartialSoul is deterministic per seed and stays inside the pool`() {
        val a = GameBalance.rollMartialSoul(5, Random(20260926))
        val b = GameBalance.rollMartialSoul(5, Random(20260926))
        assertEquals(a, b, "同种子必须同结果（仿真镜像可复现前提）")
        assertTrue(GameBalance.availableSoulPool(5).contains(a))
        // 边界：nextDouble 恒 0 的桩 rng → rand=0，扣减第一项权重即 ≤0 → 池首（shared randomAwaken 同款边界）
        val alwaysZero = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        assertEquals("蓝银草", GameBalance.rollMartialSoul(0, alwaysZero).name)
    }

    @Test
    fun `rollMartialSoul respects pool gating and section 2-2 weights`() {
        val rng = Random(20260926)
        // 权重按「魂」计不按品质计（shared randomAwaken 同款）：0 转池总权重 40+40+25+25=130，
        // COMMON 两魂叠加 → 频率 ≈80/130≈61.5%（期望 12308/20000，σ≈68）
        repeat(20_000) {
            assertTrue(GameBalance.rollMartialSoul(0, rng).rarity.ordinal <= Rarity.UNCOMMON.ordinal,
                "0 转池不可能 roll 出 RARE+（§2.3 门槛）")
        }
        val commons = (1..20_000).count { GameBalance.rollMartialSoul(0, rng).rarity == Rarity.COMMON }
        assertTrue(commons in 11_800..12_800, "0 转池 COMMON 频率应 ≈61.5%（80/130），实测 $commons")
        // 5 转全量池（总权重 195.5）：MYTHIC 权重 0.5（≈0.26%，期望 ≈51/20000）必然出现
        val mythics = (1..20_000).count { GameBalance.rollMartialSoul(5, rng).rarity == Rarity.MYTHIC }
        assertTrue(mythics > 0, "5 转池 2 万次 roll 应出现 MYTHIC（权重 0.5/195.5）")
        val legendaries = (1..20_000).count { GameBalance.rollMartialSoul(5, rng).rarity == Rarity.LEGENDARY }
        assertTrue(legendaries > mythics * 2, "LEGENDARY 总权重(2×2)是 MYTHIC(0.5) 的 8 倍，频率应显著更高（实测 传说=$legendaries 神话=$mythics）")
    }

    @Test
    fun `martialSoulPower formula anchors (battleSoulPower write point)`() {
        // hp/10 + atk + matk/2 + pdef + mdef + critRate + critDmg/10
        // 蓝银草: 5+20+5+5+5+5+15 = 60；修罗魔剑: 50+200+75+40+35+25+30 = 455
        assertEquals(60L, GameBalance.martialSoulPower(GameBalance.soulByName("蓝银草")!!))
        assertEquals(455L, GameBalance.martialSoulPower(GameBalance.soulByName("修罗魔剑")!!))
    }

    @Test
    fun `reawaken cost constant anchors at 5000 gold`() {
        // 文档未定价的实现决策（防无限免费刷池），GameService.awaken 与前端文案共同依赖
        assertEquals(5000L, GameBalance.REAWAKEN_COST_GOLD)
    }
}
