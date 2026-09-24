package com.douluodalu.game.service

import com.douluodalu.game.entity.EquippedRing
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 负荷计算与 shared 数值对齐的回归测试。
 * 期望值全部由 shared Models.kt SoulRingSystem（291~333 行）的公式逐项推得，
 * 含浮点截断行为（IEEE 754 双精度下 1000×0.35×0.7 = 244.99999999999997 → 截断为 244）。
 */
class RingLoadCalculatorTest {

    @Test
    fun `ring load equals effective years per shared formula`() {
        // 年份=下一档位基础值 × percentage/1000 × (0.6+品质×0.1)
        assertEquals(60L, RingLoadCalculator.ringLoad(0, 0, 100))       // 百年档劣等(10.0%)：1000×0.1×0.6
        assertEquals(400L, RingLoadCalculator.ringLoad(0, 2, 500))      // 百年档精良(50%)：1000×0.5×0.8
        assertEquals(4000L, RingLoadCalculator.ringLoad(1, 2, 500))     // 千年档精良(50%)：10000×0.5×0.8
        assertEquals(9990L, RingLoadCalculator.ringLoad(1, 4, 999))     // 千年档完美(99.9%)
        assertEquals(72000L, RingLoadCalculator.ringLoad(2, 3, 800))    // 万年档优秀(80%)：100000×0.8×0.9
        assertEquals(9990000L, RingLoadCalculator.ringLoad(4, 4, 999))  // 百万年档封顶=1e7×0.999×1.0
        // 浮点截断与 shared 一致：0.35×1000×0.7 = 244.999... → 244
        assertEquals(244L, RingLoadCalculator.ringLoad(0, 1, 350))
    }

    @Test
    fun `ring load clamps out-of-range inputs like shared coerceIn`() {
        assertEquals(1L, RingLoadCalculator.ringLoad(0, 0, 0))          // 0% → 截断为0 → 下限1
        assertEquals(1L, RingLoadCalculator.ringLoad(0, 0, -5))         // 负数钳到0 → 1
        assertEquals(5000000L, RingLoadCalculator.ringLoad(7, 9, 500))  // 年份/品质越界钳到4档完美：1e7×0.5×1.0
        assertEquals(9990L, RingLoadCalculator.ringLoad(1, 4, 1200))    // percentage 越界钳到999
    }

    @Test
    fun `capacity is root bone times six with floor 100`() {
        // 根骨 = atk×3 + matk×3 + pdef×2 + mdef×2 + maxHp/100（matk/pdef/mdef 后端未建模传0）
        // Lv.8 无装备：atk=50+80=130, hp=500 → 根骨395 → 容量2370
        assertEquals(2370L, RingLoadCalculator.absorptionCapacity(RingLoadCalculator.calcRootBone(500, 130, 0, 0, 0)))
        // 全零属性也保底 100
        assertEquals(100L, RingLoadCalculator.absorptionCapacity(RingLoadCalculator.calcRootBone(0, 0, 0, 0, 0)))
        // 五维公式与 shared Models.kt:322 完全一致：100×3+50×3+30×2+20×2+4300/100 = 593
        assertEquals(593.0, RingLoadCalculator.calcRootBone(4300, 100, 50, 30, 20), 1e-9)
    }

    @Test
    fun `total ring load sums equipped rings and overload message reports shortage`() {
        val rings = listOf(
            EquippedRing(userId = 1, slotIndex = 0, ringId = 1, yearOrdinal = 1, qualityOrdinal = 2, percentage = 500), // 4000
            EquippedRing(userId = 1, slotIndex = 1, ringId = 2, yearOrdinal = 0, qualityOrdinal = 0, percentage = 100), // 60
        )
        assertEquals(4060L, RingLoadCalculator.totalRingLoad(rings))
        // 刚好等载允许（<=）
        assertNull(RingLoadCalculator.overloadMessage(4060, 940, 5000))
        // 超出 1 点即拒绝，并给出「还需X」
        val msg = RingLoadCalculator.overloadMessage(4060, 941, 5000)
        assertNotNull(msg)
        assertTrue(msg!!.contains("负荷不足"))
        assertTrue(msg.contains("还需 1"))
    }
}
