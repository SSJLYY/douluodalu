package com.example.garygame

import com.example.garygame.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * 魂核系统模型层单元测试 — 测试纯逻辑（无 Android 依赖）
 */
class SoulCoreModelTest {

    // ============================================================
    // 1. SoulCoreSlotType — 槽位解锁
    // ============================================================

    @Test
    fun `ATTACK槽位在0转生时可装备`() {
        assertTrue(SoulCoreSlotType.isUnlocked(SoulCoreSlotType.ATTACK, 0))
        assertTrue(SoulCoreSlotType.isUnlocked(SoulCoreSlotType.ATTACK, 1))
        assertTrue(SoulCoreSlotType.isUnlocked(SoulCoreSlotType.ATTACK, 99))
    }

    @Test
    fun `DEFENSE槽位在0转生时可装备`() {
        assertTrue(SoulCoreSlotType.isUnlocked(SoulCoreSlotType.DEFENSE, 0))
        assertTrue(SoulCoreSlotType.isUnlocked(SoulCoreSlotType.DEFENSE, 1))
    }

    @Test
    fun `UTILITY槽位需至少3转`() {
        assertFalse("0转时辅助槽未解锁", SoulCoreSlotType.isUnlocked(SoulCoreSlotType.UTILITY, 0))
        assertFalse("2转时辅助槽未解锁", SoulCoreSlotType.isUnlocked(SoulCoreSlotType.UTILITY, 2))
        assertTrue("3转时辅助槽解锁", SoulCoreSlotType.isUnlocked(SoulCoreSlotType.UTILITY, 3))
        assertTrue("5转时辅助槽解锁", SoulCoreSlotType.isUnlocked(SoulCoreSlotType.UTILITY, 5))
    }

    @Test
    fun `unlockedSlots返回全部3个槽位`() {
        assertEquals(3, SoulCoreSlotType.unlockedSlots.size)
        assertTrue(SoulCoreSlotType.unlockedSlots.containsAll(SoulCoreSlotType.entries))
    }

    // ============================================================
    // 2. SoulCoreCategory — 兼容槽位
    // ============================================================

    @Test
    fun `力量之核仅兼容攻击槽`() {
        assertEquals(listOf(SoulCoreSlotType.ATTACK), SoulCoreCategory.POWER.compatibleSlots)
    }

    @Test
    fun `暴击和毁灭之核仅兼容攻击槽`() {
        assertEquals(listOf(SoulCoreSlotType.ATTACK), SoulCoreCategory.CRIT.compatibleSlots)
        assertEquals(listOf(SoulCoreSlotType.ATTACK), SoulCoreCategory.DESTRUCTION.compatibleSlots)
    }

    @Test
    fun `守护荆棘龙鳞之核兼容攻击和防御槽`() {
        val expected = listOf(SoulCoreSlotType.ATTACK, SoulCoreSlotType.DEFENSE)
        assertEquals(expected, SoulCoreCategory.GUARD.compatibleSlots)
        assertEquals(expected, SoulCoreCategory.THORNS.compatibleSlots)
        assertEquals(expected, SoulCoreCategory.DRAGON.compatibleSlots)
    }

    @Test
    fun `嗜血再生疾风之核兼容攻击和辅助槽`() {
        val expected = listOf(SoulCoreSlotType.ATTACK, SoulCoreSlotType.UTILITY)
        assertEquals(expected, SoulCoreCategory.VAMP.compatibleSlots)
        assertEquals(expected, SoulCoreCategory.REGEN.compatibleSlots)
        assertEquals(expected, SoulCoreCategory.DODGE.compatibleSlots)
    }

    @Test
    fun `不朽之核仅兼容辅助槽`() {
        assertEquals(listOf(SoulCoreSlotType.UTILITY), SoulCoreCategory.IMMORTAL.compatibleSlots)
    }

    @Test
    fun `起源虚空通用之核兼容全部槽位`() {
        val all = SoulCoreSlotType.entries
        assertEquals(all, SoulCoreCategory.ORIGIN.compatibleSlots)
        assertEquals(all, SoulCoreCategory.VOID.compatibleSlots)
        assertEquals(all, SoulCoreCategory.UNIVERSAL.compatibleSlots)
    }

    // ============================================================
    // 3. String.toSoulCoreCategory() — 名称→分类映射
    // ============================================================

    @Test
    fun `名称到分类映射全部正确`() {
        assertEquals(SoulCoreCategory.UNIVERSAL, "生命结晶".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.POWER, "力量之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.CRIT, "暴击之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.DESTRUCTION, "毁灭之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.GUARD, "守护之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.THORNS, "荆棘之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.DRAGON, "龙鳞之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.VAMP, "嗜血之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.REGEN, "再生之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.DODGE, "疾风之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.IMMORTAL, "不朽之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.ORIGIN, "起源之核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.VOID, "虚空之核".toSoulCoreCategory())
    }

    @Test
    fun `未知名称映射到通用`() {
        assertEquals(SoulCoreCategory.UNIVERSAL, "未知魂核".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.UNIVERSAL, "".toSoulCoreCategory())
        assertEquals(SoulCoreCategory.UNIVERSAL, "测试之核".toSoulCoreCategory())
    }

    // ============================================================
    // 4. DroppedSoulCore — 背包物品
    // ============================================================

    private val samplePassive = PassiveSkill("力量", PassiveSkillType.STAT_BOOST, listOf(3, 5, 8, 12, 18, 25), "物攻+%")

    @Test
    fun `DroppedSoulCore的category根据名称计算`() {
        val core = DroppedSoulCore("力量之核", 0, samplePassive, 10, 0)
        assertEquals(SoulCoreCategory.POWER, core.category)
    }

    @Test
    fun `DroppedSoulCore的effectiveValue随等级增长`() {
        val baseValue = 100
        val coreLv0 = DroppedSoulCore("力量之核", 0, samplePassive, baseValue, 0)
        val coreLv1 = DroppedSoulCore("力量之核", 0, samplePassive, baseValue, 1)
        val coreLv5 = DroppedSoulCore("力量之核", 0, samplePassive, baseValue, 5)

        assertEquals(100, coreLv0.effectiveValue)                                            // 100 × 1.0
        assertEquals((100.0 * SoulCoreLevelData.effectMultiplier(1)).toInt(), coreLv1.effectiveValue)
        assertEquals((100.0 * SoulCoreLevelData.effectMultiplier(5)).toInt(), coreLv5.effectiveValue)
        assertTrue("等级越高有效值越大", coreLv1.effectiveValue > coreLv0.effectiveValue)
        assertTrue("等级越高有效值越大", coreLv5.effectiveValue > coreLv1.effectiveValue)
    }

    @Test
    fun `DroppedSoulCore的不同名称映射不同category`() {
        val cores = listOf(
            "力量之核" to SoulCoreCategory.POWER,
            "守护之核" to SoulCoreCategory.GUARD,
            "不朽之核" to SoulCoreCategory.IMMORTAL,
            "起源之核" to SoulCoreCategory.ORIGIN
        )
        for ((name, expectedCat) in cores) {
            val core = DroppedSoulCore(name, 0, samplePassive, 10)
            assertEquals("$name 的分类应为 ${expectedCat.displayName}", expectedCat, core.category)
        }
    }

    // ============================================================
    // 5. SoulCoreInstance — 已装备魂核
    // ============================================================

    @Test
    fun `SoulCoreInstance的category根据名称计算`() {
        val instance = SoulCoreInstance("力量之核", 0, samplePassive, 100, 0, SoulCoreSlotType.ATTACK)
        assertEquals(SoulCoreCategory.POWER, instance.category)
    }

    @Test
    fun `SoulCoreInstance的effectiveValue随等级增长`() {
        val base = 200
        val instLv0 = SoulCoreInstance("力量之核", 0, samplePassive, base, 0, SoulCoreSlotType.ATTACK)
        val instLv3 = SoulCoreInstance("力量之核", 0, samplePassive, base, 3, SoulCoreSlotType.ATTACK)

        assertEquals(200, instLv0.effectiveValue)
        assertEquals((200.0 * SoulCoreLevelData.effectMultiplier(3)).toInt(), instLv3.effectiveValue)
        assertTrue("等级越高有效值越大", instLv3.effectiveValue > instLv0.effectiveValue)
    }

    @Test
    fun `SoulCoreInstance的isSameName正确判断同名`() {
        val inst = SoulCoreInstance("力量之核", 0, samplePassive, 100, 0, SoulCoreSlotType.ATTACK)
        val same = DroppedSoulCore("力量之核", 0, samplePassive, 100)
        val diff = DroppedSoulCore("暴击之核", 0, samplePassive, 100)
        assertTrue("同名应返回true", inst.isSameName(same))
        assertFalse("不同名应返回false", inst.isSameName(diff))
    }

    // ============================================================
    // 6. SoulCoreLevelData — 升级配置
    // ============================================================

    @Test
    fun `升级所需同名魂核数量正确`() {
        assertEquals(1, SoulCoreLevelData.requiredCopies(0))  // 0→1
        assertEquals(2, SoulCoreLevelData.requiredCopies(1))  // 1→2
        assertEquals(3, SoulCoreLevelData.requiredCopies(2))  // 2→3
        assertEquals(5, SoulCoreLevelData.requiredCopies(3))  // 3→4
        assertEquals(8, SoulCoreLevelData.requiredCopies(4))  // 4→5
    }

    @Test
    fun `满级后所需的同名魂核数量为Int_MAX`() {
        assertEquals(Int.MAX_VALUE, SoulCoreLevelData.requiredCopies(5))
        assertEquals(Int.MAX_VALUE, SoulCoreLevelData.requiredCopies(10))
    }

    @Test
    fun `升级效果倍率正确`() {
        assertEquals(1.0, SoulCoreLevelData.effectMultiplier(0), 0.001)
        assertEquals(1.15, SoulCoreLevelData.effectMultiplier(1), 0.001)
        assertEquals(1.30, SoulCoreLevelData.effectMultiplier(2), 0.001)
        assertEquals(1.45, SoulCoreLevelData.effectMultiplier(3), 0.001)
        assertEquals(1.60, SoulCoreLevelData.effectMultiplier(4), 0.001)
        assertEquals(1.75, SoulCoreLevelData.effectMultiplier(5), 0.001)
    }

    @Test
    fun `MAX_LEVEL为5`() {
        assertEquals(5, SoulCoreLevelData.MAX_LEVEL)
    }

    // ============================================================
    // 7. SoulCorePool — 魂核池
    // ============================================================

    @Test
    fun `魂核池包含所有预定义的魂核`() {
        val names = SoulCorePool.all.map { it.name }
        assertTrue(names.contains("力量之核"))
        assertTrue(names.contains("生命结晶"))
        assertTrue(names.contains("守护之核"))
        assertTrue(names.contains("暴击之核"))
        assertTrue(names.contains("嗜血之核"))
        assertTrue(names.contains("荆棘之核"))
        assertTrue(names.contains("再生之核"))
        assertTrue(names.contains("疾风之核"))
        assertTrue(names.contains("毁灭之核"))
        assertTrue(names.contains("龙鳞之核"))
        assertTrue(names.contains("不朽之核"))
        assertTrue(names.contains("起源之核"))
        assertTrue(names.contains("虚空之核"))
        assertEquals("应有13个魂核定义", 13, SoulCorePool.all.size)
    }

    @Test
    fun `池中每个SoulCoreDef的category与名称映射一致`() {
        for (def in SoulCorePool.all) {
            val expected = def.name.toSoulCoreCategory()
            assertEquals("${def.name}的category与名称映射不一致", expected, def.category)
        }
    }

    @Test
    fun `randomSoulCore尊重最大稀有度限制`() {
        // 重复多次取样，确保结果均在限定范围内
        repeat(100) {
            val core = SoulCorePool.randomSoulCore(1) // 最大稀有度1
            assertTrue("稀有度应≤1，实际${core.rarityOrdinal}", core.rarityOrdinal <= 1)
        }
    }

    @Test
    fun `randomSoulCore最大稀有度5时包含所有魂核`() {
        var foundRarity5 = false
        repeat(200) {
            val core = SoulCorePool.randomSoulCore(5)
            if (core.rarityOrdinal == 5) foundRarity5 = true
        }
        assertTrue("稀有度5的魂核（起源、虚空）应能被抽到", foundRarity5)
    }

    // ============================================================
    // 8. BossShopData — 购买消耗
    // ============================================================

    @Test
    fun `Boss商店魂核消耗与定义一致`() {
        for (def in SoulCorePool.all) {
            assertEquals(def.bossCoinCost, BossShopData.soulCoreBuyCost(def))
        }
    }

    @Test
    fun `抽卡基础消耗为50Boss币`() {
        assertEquals(50, BossShopData.SOUL_CORE_GACHA_COST)
    }
}
