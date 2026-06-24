package com.example.garygame

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.garygame.engine.GameEngine
import com.example.garygame.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 魂核装备功能桩测试 — 在真机/模拟器上运行，验证 GameEngine 装备/卸下/升级逻辑
 */
@RunWith(AndroidJUnit4::class)
class SoulCoreEquipInstrumentedTest {

    private lateinit var ctx: Context
    private val testPassive = PassiveSkill("力量", PassiveSkillType.STAT_BOOST, listOf(3, 5, 8, 12, 18, 25), "")

    @Before
    fun setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // 清除存档数据，确保每次测试从干净状态开始
        ctx.getSharedPreferences("DouluoIdleGameV2", Context.MODE_PRIVATE)
            .edit().clear().apply()
        GameEngine.init(ctx)
        GameEngine.clearLog()
    }

    @After
    fun tearDown() {
        // 测试结束后清理日志
        GameEngine.clearLog()
    }

    // ============================================================
    // 辅助方法 — 快速构造测试魂核
    // ============================================================

    private fun makeCore(name: String, rarity: Int = 0, value: Int = 100, level: Int = 0): DroppedSoulCore {
        // 根据名称获取对应的 passive（简化测试数据创建）
        val def = SoulCorePool.all.find { it.name == name }
        val passive = def?.passiveSkill ?: testPassive
        return DroppedSoulCore(name, rarity, passive, value, level)
    }

    /** 向背包添加一个魂核，返回它在背包中的索引 */
    private fun addCoreToBackpack(core: DroppedSoulCore): Int {
        val s = GameEngine.state
        val idx = s.backpackSoulCores.size
        s.backpackSoulCores.add(core)
        return idx
    }

    // ============================================================
    // 2. doEquipSoulCore — 核心装备逻辑
    // ============================================================

    @Test
    fun `装备力量之核到攻击槽_0转_成功`() {
        // 用户报告的 bug 场景：0 转生，POWER 魂核 → ATTACK 槽
        val core = makeCore("力量之核", rarity = 0, value = 100)
        val idx = addCoreToBackpack(core)
        assertEquals("背包应有1个魂核", 1, GameEngine.state.backpackSoulCores.size)

        val result = GameEngine.doEquipSoulCore(idx, SoulCoreSlotType.ATTACK)

        assertTrue("装备应成功", result)

        // 验证装备结果
        val equipped = GameEngine.state.equippedSoulCores[SoulCoreSlotType.ATTACK]
        assertNotNull("攻击槽应已装备魂核", equipped)
        assertEquals("装备的魂核名称应为力量之核", "力量之核", equipped?.name)
        assertEquals("背包魂核已被移除", 0, GameEngine.state.backpackSoulCores.size)
    }

    @Test
    fun `装备魂核后背包减少_日志记录正确`() {
        val core = makeCore("力量之核")
        val idx = addCoreToBackpack(core)
        GameEngine.clearLog()

        GameEngine.doEquipSoulCore(idx, SoulCoreSlotType.ATTACK)

        val logs = GameEngine.getLog()
        val equipLog = logs.find { it.contains("攻击槽装备") && it.contains("力量之核") }
        assertNotNull("应有装备成功的日志", equipLog)
    }

    @Test
    fun `力量之核装备到防御槽_失败_类型不兼容`() {
        // POWER 只能装 ATTACK，不能装 DEFENSE
        val core = makeCore("力量之核")
        val idx = addCoreToBackpack(core)
        assertEquals("背包应有1个魂核", 1, GameEngine.state.backpackSoulCores.size)

        val result = GameEngine.doEquipSoulCore(idx, SoulCoreSlotType.DEFENSE)

        assertFalse("力量之核不能装到防御槽，应返回false", result)
        // 魂核应放回背包
        assertEquals("魂核应仍在背包", 1, GameEngine.state.backpackSoulCores.size)
        assertNull("防御槽应无装备", GameEngine.state.equippedSoulCores[SoulCoreSlotType.DEFENSE])
    }

    @Test
    fun `通用之核可装备到任意槽位`() {
        val core = makeCore("生命结晶") // UNIVERSAL → 所有槽位
        val idx = addCoreToBackpack(core)

        val r1 = GameEngine.doEquipSoulCore(idx, SoulCoreSlotType.ATTACK)
        assertTrue("生命结晶可装攻击槽", r1)

        // 换装到防御槽
        val idx2 = addCoreToBackpack(core)
        val r2 = GameEngine.doEquipSoulCore(idx2, SoulCoreSlotType.DEFENSE)
        assertTrue("生命结晶可装防御槽", r2)

        // 需要 3 转才能装辅助槽 —— 先模拟转生
        GameEngine.state.prestigeCount = 3
        val idx3 = addCoreToBackpack(core)
        val r3 = GameEngine.doEquipSoulCore(idx3, SoulCoreSlotType.UTILITY)
        assertTrue("生命结晶可装辅助槽（3转已解锁）", r3)
    }

    @Test
    fun `辅助槽在0转时不可装备_失败`() {
        val core = makeCore("生命结晶") // UNIVERSAL
        val idx = addCoreToBackpack(core)

        val result = GameEngine.doEquipSoulCore(idx, SoulCoreSlotType.UTILITY)

        assertFalse("辅助槽未解锁，应返回false", result)
        assertEquals("魂核应放回背包", 1, GameEngine.state.backpackSoulCores.size)
    }

    @Test
    fun `无效背包索引_失败`() {
        val result = GameEngine.doEquipSoulCore(999, SoulCoreSlotType.ATTACK)
        assertFalse("无效索引应返回false", result)
    }

    @Test
    fun `装备新魂核替换旧魂核_旧核放回背包`() {
        // 先装备力量之核到攻击槽
        val coreA = makeCore("力量之核", rarity = 0, value = 100)
        val idxA = addCoreToBackpack(coreA)
        assertTrue("首次装备应成功", GameEngine.doEquipSoulCore(idxA, SoulCoreSlotType.ATTACK))
        assertEquals("背包应为空", 0, GameEngine.state.backpackSoulCores.size)

        // 再装备生命结晶到攻击槽（替换）
        val coreB = makeCore("生命结晶", rarity = 1, value = 200)
        val idxB = addCoreToBackpack(coreB)
        assertTrue("替换装备应成功", GameEngine.doEquipSoulCore(idxB, SoulCoreSlotType.ATTACK))

        // 验证：攻击槽现在是生命结晶
        val equipped = GameEngine.state.equippedSoulCores[SoulCoreSlotType.ATTACK]
        assertEquals("攻击槽应装生命结晶", "生命结晶", equipped?.name)
        // 旧魂核放回背包
        assertEquals("背包应有旧的力量之核", 1, GameEngine.state.backpackSoulCores.size)
        assertEquals("背包中应是力量之核", "力量之核", GameEngine.state.backpackSoulCores[0].name)
    }

    @Test
    fun `守护之核可装备到攻击和防御槽`() {
        val core = makeCore("守护之核") // GUARD: [ATTACK, DEFENSE]
        val idx1 = addCoreToBackpack(core)
        assertTrue("可装攻击槽", GameEngine.doEquipSoulCore(idx1, SoulCoreSlotType.ATTACK))

        val idx2 = addCoreToBackpack(core)
        assertTrue("可装防御槽", GameEngine.doEquipSoulCore(idx2, SoulCoreSlotType.DEFENSE))
    }

    @Test
    fun `不朽之核只能装辅助槽_3转解锁`() {
        val core = makeCore("不朽之核") // IMMORTAL: [UTILITY]
        val idx = addCoreToBackpack(core)

        // 0 转时尝试 → 失败（槽位未解锁）
        val r1 = GameEngine.doEquipSoulCore(idx, SoulCoreSlotType.UTILITY)
        assertFalse("0转时辅助槽未解锁", r1)

        // 设置 3 转后再试 → 成功
        GameEngine.state.prestigeCount = 3
        val idx2 = addCoreToBackpack(core)
        val r2 = GameEngine.doEquipSoulCore(idx2, SoulCoreSlotType.UTILITY)
        assertTrue("3转后应可装备到辅助槽", r2)
        assertNotNull("辅助槽应有装备", GameEngine.state.equippedSoulCores[SoulCoreSlotType.UTILITY])
    }

    // ============================================================
    // 3. doUnequipSoulCore — 卸下魂核
    // ============================================================

    @Test
    fun `卸下有魂核的槽位_魂核放回背包`() {
        val core = makeCore("力量之核")
        val idx = addCoreToBackpack(core)
        GameEngine.doEquipSoulCore(idx, SoulCoreSlotType.ATTACK)
        assertEquals("装备后背包应空", 0, GameEngine.state.backpackSoulCores.size)

        val result = GameEngine.doUnequipSoulCore(SoulCoreSlotType.ATTACK)

        assertTrue("卸下应成功", result)
        assertNull("攻击槽应为空", GameEngine.state.equippedSoulCores[SoulCoreSlotType.ATTACK])
        assertEquals("背包应有卸下的魂核", 1, GameEngine.state.backpackSoulCores.size)
        assertEquals("背包中魂核名称", "力量之核", GameEngine.state.backpackSoulCores[0].name)
    }

    @Test
    fun `卸下空槽位_失败`() {
        val result = GameEngine.doUnequipSoulCore(SoulCoreSlotType.ATTACK)
        assertFalse("空槽位卸下应返回false", result)
    }

    @Test
    fun `卸下后日志记录正确`() {
        val core = makeCore("力量之核")
        val idx = addCoreToBackpack(core)
        GameEngine.doEquipSoulCore(idx, SoulCoreSlotType.ATTACK)
        GameEngine.clearLog()

        GameEngine.doUnequipSoulCore(SoulCoreSlotType.ATTACK)

        val logs = GameEngine.getLog()
        assertTrue("应有卸下日志", logs.any { it.contains("卸下") && it.contains("力量之核") })
    }

    // ============================================================
    // 4. doSoulCoreGacha — Boss币抽魂核
    // ============================================================

    @Test
    fun `抽魂核_扣除Boss币_添加到背包`() {
        val s = GameEngine.state
        s.bossCoin = 9999
        val initialSize = s.backpackSoulCores.size

        val result = GameEngine.doSoulCoreGacha()

        assertTrue("抽卡应成功", result)
        assertTrue("Boss币应减少", s.bossCoin < 9999)
        assertEquals("背包魂核应增加1", initialSize + 1, s.backpackSoulCores.size)
    }

    @Test
    fun `抽魂核_Boss币不足_失败`() {
        val s = GameEngine.state
        s.bossCoin = 0

        val result = GameEngine.doSoulCoreGacha()

        assertFalse("Boss币不足应失败", result)
    }

    @Test
    fun `抽魂核_包满时自动分解_不占用背包`() {
        val s = GameEngine.state
        s.bossCoin = 9999
        // 填满背包（需计算容量）
        val cap = s.backpackCapacity
        val initialGold = s.gold
        // 用其他物品填满
        repeat(cap - s.backpackTotalItems) {
            s.backpackRings.add(DroppedRing(0, 0, 0, mutableListOf(), null))
        }
        assertTrue("背包应已满", s.backpackIsFull)

        val result = GameEngine.doSoulCoreGacha()

        assertTrue("包满自动分解应成功", result)
        assertTrue("自动分解应获得金币", s.gold > initialGold)
    }

    // ============================================================
    // 5. doUpgradeSoulCore — 升级魂核
    // ============================================================

    @Test
    fun `升级魂核_消耗同名魂核_等级提升`() {
        val s = GameEngine.state
        // 3个同名力量之核（1个目标 + 2个消耗，从Lv.0→Lv.1需要1个同名）
        val need = SoulCoreLevelData.requiredCopies(0) // 1
        val target = makeCore("力量之核", value = 100, level = 0)
        s.backpackSoulCores.add(target)
        repeat(need) {
            s.backpackSoulCores.add(makeCore("力量之核", value = 50, level = 0))
        }

        val targetIdx = 0
        val totalBefore = s.backpackSoulCores.size

        val result = GameEngine.doUpgradeSoulCore(targetIdx)

        assertTrue("升级应成功", result)
        assertEquals("消耗后背包总数应减少${need}", totalBefore - need, s.backpackSoulCores.size)
        assertEquals("目标魂核等级应+1", 1, s.backpackSoulCores[0].level)
    }

    @Test
    fun `升级魂核_同名数量不足_失败`() {
        val s = GameEngine.state
        val target = makeCore("力量之核", value = 100, level = 0)
        s.backpackSoulCores.add(target)
        // 不添加同名消耗品

        val result = GameEngine.doUpgradeSoulCore(0)

        assertFalse("无同名魂核，升级应失败", result)
        assertEquals("背包应不变", 1, s.backpackSoulCores.size)
        assertEquals("等级不变", 0, s.backpackSoulCores[0].level)
    }

    @Test
    fun `升级魂核_满级时_失败`() {
        val s = GameEngine.state
        val target = makeCore("力量之核", value = 100, level = SoulCoreLevelData.MAX_LEVEL)
        s.backpackSoulCores.add(target)

        val result = GameEngine.doUpgradeSoulCore(0)

        assertFalse("满级魂核升级应失败", result)
        assertEquals("等级应保持MAX", SoulCoreLevelData.MAX_LEVEL, s.backpackSoulCores[0].level)
    }

    @Test
    fun `升级魂核_无效索引_失败`() {
        val result = GameEngine.doUpgradeSoulCore(999)
        assertFalse("无效索引应返回false", result)
    }

    @Test
    fun `升级后日志包含升级成功信息`() {
        val s = GameEngine.state
        val target = makeCore("力量之核", value = 100, level = 0)
        s.backpackSoulCores.add(target)
        s.backpackSoulCores.add(makeCore("力量之核", value = 50, level = 0))
        GameEngine.clearLog()

        GameEngine.doUpgradeSoulCore(0)

        val logs = GameEngine.getLog()
        assertTrue("应有升级成功的日志", logs.any { it.contains("升级成功") && it.contains("力量之核") })
    }

    // ============================================================
    // 6. 回归测试 — 完整装备/卸下/升级流程
    // ============================================================

    @Test
    fun `完整装备流程_多次转生场景`() {
        val s = GameEngine.state
        s.prestigeCount = 3 // 模拟 3 转

        // 1. 抽到 3 个魂核
        s.bossCoin = 9999
        GameEngine.doSoulCoreGacha()
        GameEngine.doSoulCoreGacha()
        GameEngine.doSoulCoreGacha()
        val initialCount = s.backpackSoulCores.size
        assertTrue("背包应有魂核", initialCount > 0)

        // 2. 装备第一个魂核到其中一个兼容槽位
        val firstCore = s.backpackSoulCores[0]
        val compatibleSlots = firstCore.category.compatibleSlots.filter {
            SoulCoreSlotType.isUnlocked(it, s.prestigeCount)
        }
        assertTrue("应有兼容槽位", compatibleSlots.isNotEmpty())
        val targetSlot = compatibleSlots.first()
        val r1 = GameEngine.doEquipSoulCore(0, targetSlot)
        assertTrue("装备应成功", r1)
        assertEquals("装备后背包减少1个", initialCount - 1, s.backpackSoulCores.size)

        // 3. 再装备不同类别的魂核到其他槽位
        while (s.backpackSoulCores.isNotEmpty()) {
            val core = s.backpackSoulCores[0]
            val slots = core.category.compatibleSlots.filter {
                SoulCoreSlotType.isUnlocked(it, s.prestigeCount)
            }
            // 找一个空槽位
            val emptySlot = slots.find { s.equippedSoulCores[it] == null }
            if (emptySlot != null) {
                val r = GameEngine.doEquipSoulCore(0, emptySlot)
                assertTrue("装备到空槽位应成功", r)
            } else {
                break
            }
        }

        // 4. 卸下所有装备
        for (slot in SoulCoreSlotType.entries) {
            if (s.equippedSoulCores[slot] != null) {
                val r = GameEngine.doUnequipSoulCore(slot)
                assertTrue("卸下${slot.displayName}应成功", r)
            }
        }

        // 5. 验证所有槽位清空，魂核回到背包
        val allEmpty = SoulCoreSlotType.entries.all { s.equippedSoulCores[it] == null }
        assertTrue("所有槽位应为空", allEmpty)
        assertTrue("背包应有魂核", s.backpackSoulCores.isNotEmpty())
    }
}
