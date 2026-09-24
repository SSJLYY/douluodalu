package com.douluodalu.game.service

import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.model.GameBalance

/**
 * 魂环负荷/吸收容量计算器（任务#21）。
 *
 * **行为基准**：逐行移植 shared 引擎
 * `shared/src/commonMain/kotlin/com/example/garygame/model/Models.kt` 的 `SoulRingSystem`（291~333 行），
 * 而非《魂环负荷与年份对应关系.md》中的早期草案公式（文档的 calcRingLoad=baseYear×qualityMult×pctMult
 * 已被 shared 引擎推翻：现行实现为「负荷 = 等效年份 = 下一档位基础值 × 成熟度比例 × 品质系数」）。
 * 成熟度 percentage 与 shared 一致采用 100~999（即 10.0%~99.9%）语义，掉落生成侧已符合。
 *
 * 容量 = 根骨 × GameBalance.RING_CAPACITY_ROOT_MULT（shared Models.kt:332 原值 6，任务#22 负荷回路仿真调参为 24），
 * 根骨 = atk×3 + matk×3 + pdef×2 + mdef×2 + maxHp/100（Models.kt:322）。
 * 后端战斗模型只建模 攻击/生命 两维（GameService.battle），故 matk/pdef/mdef 传 0，
 * atk/maxHp 与战斗结算同源：PLAYER_ATK_BASE + level×PER_LEVEL + 装备攻击加成、50×level+100 + 装备生命加成。
 *
 * 魂骨/魂核不占负荷：设计文档明确「当前：魂骨不占用负荷」，50% 负荷只是「可选建议」未采纳。
 */
object RingLoadCalculator {

    /** 年份档位基础值（Models.kt:294）：百年=100, 千年=1000, 万年=10000, 十万年=100000, 百万年=1000000 */
    private val YEAR_BASE_VALUES = intArrayOf(100, 1000, 10000, 100000, 1000000)

    /** 等效年份（Models.kt:304~319 原样移植，含 IEEE 浮点截断行为） */
    fun calcEffectiveYears(yearOrdinal: Int, qualityOrdinal: Int, percentage: Int): Int {
        val y = yearOrdinal.coerceIn(0, 4)
        val q = qualityOrdinal.coerceIn(0, 4)
        val nextBaseYear = if (y < 4) YEAR_BASE_VALUES[y + 1] else YEAR_BASE_VALUES[y] * 10

        // percentage 直接表示千分比（100-999 → 10.0%-99.9%）
        val positionRatio = percentage.toDouble().coerceIn(0.0, 999.0) / 1000.0

        // 品质系数：劣等0.6 → 完美1.0
        val qualityMult = 0.6 + q * 0.1

        // 等效年份 = 下一档位基础值 × 位置比例 × 品质系数
        return (nextBaseYear * positionRatio * qualityMult).toInt().coerceAtLeast(1)
    }

    /** 魂环负荷 = 等效年份（Models.kt:297~299） */
    fun ringLoad(yearOrdinal: Int, qualityOrdinal: Int, percentage: Int): Long =
        calcEffectiveYears(yearOrdinal, qualityOrdinal, percentage).toLong()

    fun ringLoad(ring: EquippedRing): Long =
        ringLoad(ring.yearOrdinal, ring.qualityOrdinal, ring.percentage)

    /** 当前已装备魂环总负荷（GameEngine.kt:462~464 的 sumOf{load}） */
    fun totalRingLoad(rings: List<EquippedRing>): Long = rings.sumOf { ringLoad(it) }

    /** 根骨值（Models.kt:322~325） */
    fun calcRootBone(maxHp: Long, atk: Long, matk: Int, pdef: Int, mdef: Int): Double =
        atk * 3.0 + matk * 3.0 + pdef * 2.0 + mdef * 2.0 + maxHp / 100.0

    /** 吸收容量 = 根骨 × RING_CAPACITY_ROOT_MULT（shared Models.kt:331~333 的 ×6），下限 100 */
    fun absorptionCapacity(rootBone: Double): Long =
        absorptionCapacity(rootBone, GameBalance.RING_CAPACITY_ROOT_MULT)

    /** 重载：容量乘数可注入（默认读 GameBalance；LongRunSimulationTest 调参扫描用） */
    fun absorptionCapacity(rootBone: Double, capacityMult: Long): Long =
        (rootBone * capacityMult).toLong().coerceAtLeast(100)

    /**
     * 超负荷校验（shared GameEngine.kt:1851~1861 吸收检测的同构逻辑）：
     * 装后总负荷 > 容量时返回「还需 X」文案，可装下返回 null。
     */
    fun overloadMessage(currentLoad: Long, newLoad: Long, capacity: Long): String? {
        if (currentLoad + newLoad <= capacity) return null
        val overload = currentLoad + newLoad - capacity
        return "负荷不足！当前负荷 $currentLoad/$capacity，该魂环需负荷 $newLoad，还需 $overload 才可吸收"
    }
}
