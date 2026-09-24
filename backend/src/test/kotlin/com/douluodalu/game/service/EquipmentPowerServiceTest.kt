package com.douluodalu.game.service

import com.douluodalu.game.entity.EquippedBone
import com.douluodalu.game.entity.EquippedCore
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.model.GameBalance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * 任务#20：装备战力公式（P7）与塔胜率（P2）的纯函数断言。
 * 全部走 EquipmentPowerService / GameService 的 companion 纯函数，不依赖 Spring。
 */
class EquipmentPowerServiceTest {

    private fun ring(year: Int = 0, quality: Int = 0, percentage: Int = 100) = EquippedRing(
        userId = 1L, slotIndex = 0, ringId = 1L, yearOrdinal = year, qualityOrdinal = quality, percentage = percentage
    )

    private fun bone(year: Int = 0, quality: Int = 0, enhance: Int = 0) = EquippedBone(
        userId = 1L, slotIndex = 0, boneId = 1L, yearOrdinal = year, qualityOrdinal = quality,
        boneTypeOrdinal = 0, enhanceLevel = enhance
    )

    private fun core(rarity: Int = 0, value: Int = 100) = EquippedCore(
        userId = 1L, slotType = "LEFT", coreId = 1L, rarityOrdinal = rarity, coreName = "攻击魂核",
        coreValue = value, coreLevel = 1
    )

    // ======== P7：装备加成与战斗力的单调性 ========

    @Test
    fun `ring atk hp bonus grows monotonically with year, quality and percentage`() {
        var prev = EquipmentBonus(0, 0)
        val ladder = listOf(
            ring(year = 0, quality = 0, percentage = 100),
            ring(year = 1, quality = 0, percentage = 100),
            ring(year = 1, quality = 2, percentage = 100),
            ring(year = 2, quality = 2, percentage = 500),
            ring(year = 3, quality = 4, percentage = 900),
        )
        for (r in ladder) {
            val b = EquipmentPowerService.bonus(10, listOf(r), emptyList(), emptyList())
            assertTrue(b.atkBonus > prev.atkBonus, "年份/品质/成熟度提升后攻击加成应严格增加: $r -> $b")
            assertTrue(b.hpBonus > prev.hpBonus, "生命加成应随之增加: $r -> $b")
            prev = b
        }
    }

    @Test
    fun `bone enhance and core value increase bonus, power is monotone in equipment`() {
        val weak = EquipmentPowerService.bonus(
            50, listOf(ring()), listOf(bone(year = 1, quality = 1, enhance = 1)), listOf(core(rarity = 0, value = 50))
        )
        val strong = EquipmentPowerService.bonus(
            50, listOf(ring()), listOf(bone(year = 1, quality = 1, enhance = 9)), listOf(core(rarity = 4, value = 150))
        )
        assertTrue(strong.atkBonus > weak.atkBonus)
        val pWeak = EquipmentPowerService.powerOf(50, weak)
        val pStrong = EquipmentPowerService.powerOf(50, strong)
        assertTrue(pStrong > pWeak, "同一等级下装备更好 → 战斗力必须更高")
        // 魂核加成受 CORE_ATK_PCT_CAP 封顶，不随 coreValue 线性失控
        val capped = EquipmentPowerService.bonus(50, emptyList(), emptyList(), listOf(core(rarity = 4, value = 100_000)))
        val baseAtk = GameBalance.PLAYER_ATK_BASE + 50 * GameBalance.PLAYER_ATK_PER_LEVEL
        assertTrue(capped.atkBonus <= 2 * (baseAtk * GameBalance.CORE_ATK_PCT_CAP).toLong())
    }

    @Test
    fun `better equipment improves deterministic battle outcome at same level`() {
        // 同一等级（level=10 → 基础攻击 150）+ 装备差异；怪物固定，随机种子相同
        val level = 10
        val weak = EquipmentPowerService.bonus(level, listOf(ring(year = 0, quality = 0, percentage = 100)), emptyList(), emptyList())
        val strong = EquipmentPowerService.bonus(level, (0 until 6).map { ring(year = 4, quality = 4, percentage = 900) }, emptyList(), emptyList())
        assertTrue(strong.atkBonus > weak.atkBonus)

        val playerHp = 10_000L
        val monsterHp = 6_000L
        val monsterAtk = 250
        val baseAtk = GameBalance.PLAYER_ATK_BASE + level * GameBalance.PLAYER_ATK_PER_LEVEL

        val weakOutcome = GameService.resolveBattle(baseAtk + weak.atkBonus, playerHp, monsterHp, monsterAtk, 30, Random(42))
        val strongOutcome = GameService.resolveBattle(baseAtk + strong.atkBonus, playerHp, monsterHp, monsterAtk, 30, Random(42))
        // 弱装备数学上不可能在 30 回合内击杀 6000 HP（每回合上限 150+29）；强装备必然击杀
        assertFalse(weakOutcome.won, "无装备加成的 150 攻击应在 30 回合内打不死 6000 HP")
        assertTrue(strongOutcome.won, "换装后应击杀同一怪物 —— 换装变强必须可感")
        assertTrue(strongOutcome.playerHpLeft > weakOutcome.playerHpLeft)
    }

    // ======== 任务#23：战力明细拆分（拆分求和 == bonus/power 总值） ========

    @Test
    fun `power detail with empty equipment only has base row`() {
        val d = EquipmentPowerService.detail(30, emptyList(), emptyList(), emptyList())
        assertEquals(GameBalance.PLAYER_ATK_BASE + 30 * GameBalance.PLAYER_ATK_PER_LEVEL, d.baseAtk)
        assertEquals(0L, d.baseHp)
        assertEquals(0L, d.ringAtk); assertEquals(0L, d.ringHp); assertEquals(0L, d.ringPower)
        assertEquals(0L, d.boneAtk); assertEquals(0L, d.boneHp); assertEquals(0L, d.bonePower)
        assertEquals(0L, d.coreAtk); assertEquals(0L, d.coreHp); assertEquals(0L, d.corePower)
        val power = EquipmentPowerService.powerOf(30, EquipmentBonus(0, 0))
        assertEquals(power, d.basePower)
    }

    @Test
    fun `power detail splits sum exactly to bonus and total power`() {
        val level = 47
        val rings = listOf(ring(year = 1, quality = 2, percentage = 137), ring(year = 3, quality = 1, percentage = 903), ring(year = 0, quality = 4, percentage = 47))
        val bones = listOf(bone(year = 2, quality = 3, enhance = 5), bone(year = 4, quality = 0, enhance = 11))
        val cores = listOf(core(rarity = 3, value = 173), core(rarity = 1, value = 61))
        val b = EquipmentPowerService.bonus(level, rings, bones, cores)
        val d = EquipmentPowerService.detail(level, rings, bones, cores)

        // 攻击/生命拆分求和 == bonus 总值（含取整余数）
        assertEquals(b.atkBonus, d.ringAtk + d.boneAtk + d.coreAtk, "攻击拆分求和必须等于 atkBonus")
        assertEquals(b.hpBonus, d.ringHp + d.boneHp, "生命拆分求和必须等于 hpBonus")
        // 四行战力求和 == powerOf 总值
        val power = EquipmentPowerService.powerOf(level, b)
        assertEquals(power, d.basePower + d.ringPower + d.bonePower + d.corePower, "战力明细四行求和必须等于总战力")
        // 来源语义：魂核只加攻击、玩家模型无基础生命
        assertEquals(0L, d.coreHp)
        assertEquals(0L, d.baseHp)
        assertEquals(d.coreAtk, d.corePower)
        // 每行内部：战力贡献 = 攻击 + 生命折算（±1 内，折算余数按最大余数法归行）
        assertTrue(d.ringPower in (d.ringAtk + d.ringHp / GameBalance.POWER_HP_DIVISOR).toLong()..(d.ringAtk + d.ringHp / GameBalance.POWER_HP_DIVISOR).toLong() + 1)
        assertTrue(d.bonePower in (d.boneAtk + d.boneHp / GameBalance.POWER_HP_DIVISOR).toLong()..(d.boneAtk + d.boneHp / GameBalance.POWER_HP_DIVISOR).toLong() + 1)
        // 基础行与 powerOf 常数项同式
        assertEquals(GameBalance.POWER_BASE + GameBalance.POWER_LEVEL_WEIGHT * level + d.baseAtk, d.basePower)
    }

    @Test
    fun `power detail split invariants hold across randomized equipment`() {
        val rng = Random(20260923)
        repeat(200) {
            val level = rng.nextInt(1, 121)
            val rings = List(rng.nextInt(0, 7)) {
                ring(year = rng.nextInt(0, 6), quality = rng.nextInt(0, 5), percentage = rng.nextInt(0, 1000))
            }
            val bones = List(rng.nextInt(0, 5)) {
                bone(year = rng.nextInt(0, 6), quality = rng.nextInt(0, 5), enhance = rng.nextInt(0, 13))
            }
            val cores = List(rng.nextInt(0, 3)) {
                core(rarity = rng.nextInt(0, 5), value = rng.nextInt(1, 300))
            }
            val b = EquipmentPowerService.bonus(level, rings, bones, cores)
            val d = EquipmentPowerService.detail(level, rings, bones, cores)
            assertEquals(b.atkBonus, d.ringAtk + d.boneAtk + d.coreAtk, "atk 拆分失衡: level=$level $rings $bones $cores")
            assertEquals(b.hpBonus, d.ringHp + d.boneHp, "hp 拆分失衡: level=$level $rings $bones $cores")
            assertEquals(
                EquipmentPowerService.powerOf(level, b),
                d.basePower + d.ringPower + d.bonePower + d.corePower,
                "战力拆分失衡: level=$level $rings $bones $cores"
            )
        }
    }

    // ======== P2：塔胜率修复 ========

    @Test
    fun `tower win chance stays positive at floor 99 without any equipment`() {
        // 旧公式 1-(0.25+38×0.02)≤0 → floor≥38 恒败；新公式 floor=99 基础胜率 = 1-0.25-0.495 = 0.255
        val chance = EquipmentPowerService.towerWinChance(99, power = 0L)
        assertTrue(chance > 0.0, "floor=99 胜率必须 > 0（实测 $chance）")
        assertEquals(0.255, chance, 1e-9)
        assertTrue(EquipmentPowerService.towerWinChance(GameBalance.TOWER_MAX_FLOOR, 0L) > 0.0)
    }

    @Test
    fun `tower win chance grows with power and stays bounded`() {
        val required = 99 * GameBalance.TOWER_POWER_PER_FLOOR
        val noGear = EquipmentPowerService.towerWinChance(99, 0L)
        val onPar = EquipmentPowerService.towerWinChance(99, required)
        val overLeveled = EquipmentPowerService.towerWinChance(99, required * 10)
        assertTrue(overLeveled > onPar && onPar > noGear, "战力更高 → 塔胜率单调不减")
        for (floor in 0..GameBalance.TOWER_MAX_FLOOR) {
            val c = EquipmentPowerService.towerWinChance(floor, Long.MAX_VALUE / 2)
            val c0 = EquipmentPowerService.towerWinChance(floor, 0)
            assertTrue(c in GameBalance.TOWER_WIN_CHANCE_MIN..GameBalance.TOWER_WIN_CHANCE_MAX, "floor=$floor 越界: $c")
            assertTrue(c0 >= GameBalance.TOWER_WIN_CHANCE_MIN)
        }
    }
}
