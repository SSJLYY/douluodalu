package com.douluodalu.game.service

import com.douluodalu.game.entity.AchievementEntity
import com.douluodalu.game.entity.EquippedBone
import com.douluodalu.game.entity.EquippedCore
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.AchievementRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import com.douluodalu.game.repository.EquippedRingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.random.Random

/**
 * 任务#20：装备战力公式（P7）与塔胜率（P2）的纯函数断言。
 * 全部走 EquipmentPowerService / GameService 的 companion 纯函数，不依赖 Spring。
 * 成就系统集成：bonusFor 并入已解锁成就 hp/atk；detail 五行求和不变量（base+ring+core+bone+achievement）。
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

    // ======== 成就系统集成：bonusFor 并入成就加成 ========

    @Test
    fun `bonusFor should include achievement bonus on top of equipment bonus`() {
        val ringRepo = mock<EquippedRingRepository>()
        val boneRepo = mock<EquippedBoneRepository>()
        val coreRepo = mock<EquippedCoreRepository>()
        val achRepo = mock<AchievementRepository>()
        whenever(ringRepo.findByUserId(1L)).thenReturn(listOf(ring(year = 2, quality = 1, percentage = 500)))
        whenever(boneRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(coreRepo.findByUserId(1L)).thenReturn(emptyList())
        whenever(achRepo.findByUserId(1L)).thenReturn(
            listOf(AchievementEntity(userId = 1L, achievementId = "cult_10"), AchievementEntity(userId = 1L, achievementId = "battle_10"))
        )

        val b = EquipmentPowerService(ringRepo, boneRepo, coreRepo, achRepo).bonusFor(1L, 10)

        val equipOnly = EquipmentPowerService.bonus(10, listOf(ring(year = 2, quality = 1, percentage = 500)), emptyList(), emptyList())
        val ach = EquipmentPowerService.achievementBonus(listOf("cult_10", "battle_10"))
        assertTrue(ach.atkBonus > 0 && ach.hpBonus > 0, "cult_10+battle_10 应有非零 hp/atk 成就加成")
        assertEquals(equipOnly.atkBonus + ach.atkBonus, b.atkBonus, "bonusFor 应等于装备加成 + 成就加成")
        assertEquals(equipOnly.hpBonus + ach.hpBonus, b.hpBonus)
        // 求和与定义表同源（achievementBonus 纯函数幂等）
        val defSumAtk = (GameBalance.ACHIEVEMENT_BY_ID.getValue("cult_10").rewards.atk + GameBalance.ACHIEVEMENT_BY_ID.getValue("battle_10").rewards.atk).toLong()
        val defSumHp = GameBalance.ACHIEVEMENT_BY_ID.getValue("cult_10").rewards.hp + GameBalance.ACHIEVEMENT_BY_ID.getValue("battle_10").rewards.hp
        assertEquals(defSumAtk, ach.atkBonus)
        assertEquals(defSumHp, ach.hpBonus)
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
        assertEquals(0L, d.achievement, "无成就加成时成就行必须为 0")
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

        // 攻击/生命拆分求和 == bonus 总值（含取整余数；拆分行保持装备口径，不含成就加成）
        assertEquals(b.atkBonus, d.ringAtk + d.boneAtk + d.coreAtk, "攻击拆分求和必须等于 atkBonus")
        assertEquals(b.hpBonus, d.ringHp + d.boneHp, "生命拆分求和必须等于 hpBonus")
        // 五行战力求和 == powerOf 总值（成就加成缺省为 0，成就行 = 0）
        val power = EquipmentPowerService.powerOf(level, b)
        assertEquals(0L, d.achievement)
        assertEquals(power, d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement, "战力明细五行求和必须等于总战力")
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
                d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement,
                "战力拆分失衡: level=$level $rings $bones $cores"
            )
        }
    }

    @Test
    fun `power detail with achievement bonus keeps five-row sum invariant`() {
        val level = 47
        val rings = listOf(ring(year = 1, quality = 2, percentage = 137), ring(year = 3, quality = 1, percentage = 903))
        val bones = listOf(bone(year = 2, quality = 3, enhance = 5))
        val cores = listOf(core(rarity = 3, value = 173))
        // 成就加成（cult_50 + battle_50 + tower_50 + tower_30 量级）：atk 245 / hp 4600（可被 10 整除便于精确断言）
        val ach = EquipmentBonus(atkBonus = 245, hpBonus = 4600)

        val combined = EquipmentPowerService.bonus(level, rings, bones, cores, ach)
        val d = EquipmentPowerService.detail(level, rings, bones, cores, ach)
        val power = EquipmentPowerService.powerOf(level, combined)

        // 五行求和不变量：base + ring + core + bone + achievement == power
        assertEquals(power, d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement,
            "含成就加成的五行战力求和必须等于总战力")
        // 成独行 = 成就 atk + 成就 hp 折算（hp 可被 POWER_HP_DIVISOR 整除 → 精确）
        assertEquals(ach.atkBonus + (ach.hpBonus / GameBalance.POWER_HP_DIVISOR).toLong(), d.achievement)
        // 装备拆分行保持装备口径（不含成就加成）
        val equipOnly = EquipmentPowerService.bonus(level, rings, bones, cores)
        assertEquals(equipOnly.atkBonus, d.ringAtk + d.boneAtk + d.coreAtk)
        assertEquals(equipOnly.hpBonus, d.ringHp + d.boneHp)
        assertEquals(combined.atkBonus, equipOnly.atkBonus + ach.atkBonus)
        assertEquals(combined.hpBonus, equipOnly.hpBonus + ach.hpBonus)
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
