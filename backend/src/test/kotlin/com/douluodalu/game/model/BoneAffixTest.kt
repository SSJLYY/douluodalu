package com.douluodalu.game.model

import com.douluodalu.game.model.GameBalance.BoneAffixType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * 第二十八轮魂骨词缀（GameBalance「魂骨词缀」区块）：
 *  - 数值表锚点（品质 0/2/4 三档，线性插值半往上取整）；
 *  - 条数 = 品质+1、类型不重复、数值由品质唯一决定（无 RNG）；
 *  - RNG 掷点次序钉死为 nextInt(5−已选) 契约（LongRunSimulationTest 掉落镜像调用同一
 *    rollBoneAffixList 同源防漂移，本用例再钉死函数内部的消耗次序防实现漂移）；
 *  - JSON round-trip 与脏数据宽容。
 */
class BoneAffixTest {

    @Test
    fun `affix value anchors at quality 0 2 4 for all five types`() {
        // 锚点：q0=区间下界、q4=区间上界、q2=中点（Math.round 半往上取整）
        val expected = mapOf(
            BoneAffixType.CRIT_RATE to listOf(1, 5, 8),   // 1→8：中点 4.5 → 5
            BoneAffixType.CRIT_DMG to listOf(5, 23, 40),  // 5→40：中点 22.5 → 23
            BoneAffixType.PDEF to listOf(3, 32, 60),      // 3→60：中点 31.5 → 32
            BoneAffixType.MDEF to listOf(3, 32, 60),
            BoneAffixType.MATK to listOf(4, 42, 80)       // 4→80：中点 42（整点）
        )
        for ((type, anchors) in expected) {
            assertEquals(anchors[0], GameBalance.boneAffixValue(type, 0), "$type q0")
            assertEquals(anchors[1], GameBalance.boneAffixValue(type, 2), "$type q2")
            assertEquals(anchors[2], GameBalance.boneAffixValue(type, 4), "$type q4")
        }
    }

    @Test
    fun `rolled affix count equals quality+1 and types never repeat`() {
        val rng = Random(20260927)
        for (q in 0..4) {
            repeat(50) {
                val affixes = GameBalance.rollBoneAffixList(q, rng)
                assertEquals(q + 1, affixes.size, "品质 $q 应掷 ${q + 1} 条")
                assertEquals(affixes.map { it.first }.distinct().size, affixes.size, "类型不得重复")
                assertTrue(
                    affixes.all { it.second == GameBalance.boneAffixValue(it.first, q) },
                    "数值应由品质唯一决定（无 RNG）：$affixes"
                )
            }
        }
    }

    @Test
    fun `roll consumes RNG strictly as nextInt 5 4 3 2 1 for quality 4 (mirror sync contract)`() {
        // 契约：条数无掷点 → 逐条类型 nextInt(5−已选)（品质 4 依次 5/4/3/2/1 五掷）→ 数值无掷点。
        // 同种子重放逐位比对，并用「其后 RNG 状态一致」证明总消耗逐位相同。
        val r1 = Random(42L)
        val rolled = GameBalance.rollBoneAffixList(4, r1)
        val r2 = Random(42L)
        val remaining = BoneAffixType.entries.toMutableList()
        val replayed = listOf(5, 4, 3, 2, 1).map { bound -> remaining.removeAt(r2.nextInt(bound)) }
        assertEquals(replayed, rolled.map { it.first }, "掷点次序必须为 nextInt(5−已选) 的契约次序")
        assertEquals(r2.nextInt(1_000_000), r1.nextInt(1_000_000), "数值侧必须无掷点（RNG 状态应一致）")
        assertEquals(r2.nextDouble(), r1.nextDouble(), 0.0)
    }

    @Test
    fun `affixes JSON round-trips through parseBoneAffixes`() {
        val rng = Random(7L)
        for (q in 0..4) {
            val affixes = GameBalance.rollBoneAffixList(q, rng)
            val json = GameBalance.serializeBoneAffixes(affixes)
            assertTrue(json.startsWith("[") && json.contains("\"type\"") && json.contains("\"value\""), json)
            assertEquals(affixes, GameBalance.parseBoneAffixes(json), "round-trip 必须逐位还原：$json")
        }
    }

    @Test
    fun `parseBoneAffixes tolerates dirty data`() {
        assertTrue(GameBalance.parseBoneAffixes(null).isEmpty())
        assertTrue(GameBalance.parseBoneAffixes("").isEmpty())
        assertTrue(GameBalance.parseBoneAffixes("   ").isEmpty())
        assertTrue(GameBalance.parseBoneAffixes("not-json").isEmpty())
        assertTrue(GameBalance.parseBoneAffixes("[{]]").isEmpty())
        // 未知类型剔除、负值钳 0（防脏数据击穿 detail 恒等拆分的非负前提）
        assertEquals(
            listOf(BoneAffixType.PDEF to 0),
            GameBalance.parseBoneAffixes("""[{"type":"UNKNOWN","value":9},{"type":"PDEF","value":-3}]""")
        )
    }

    @Test
    fun `rollBoneAffixes returns parseable json at drop-side signature`() {
        // 掉落侧唯一写点签名（yearOrdinal 为预留参数，不影响掷点）
        val json = GameBalance.rollBoneAffixes(yearOrdinal = 3, qualityOrdinal = 2, rng = Random(11L))
        assertEquals(3, GameBalance.parseBoneAffixes(json).size)  // 品质 2 → 3 条
    }
}
