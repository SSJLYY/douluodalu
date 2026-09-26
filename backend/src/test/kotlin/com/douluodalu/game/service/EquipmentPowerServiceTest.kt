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
 * 成就系统集成：bonusFor 并入已解锁成就 hp/atk；转生集成：bonusFor 乘转生倍率、
 * detail 六行求和不变量（base+ring+core+bone+achievement+prestige）。
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
        // 第十七轮扩展：本用例只验证「装备更好 → 结果单调」，怪物防御置 0 使 defFactor=1.0，
        // 与旧数学（纯减算互拍）语义对齐
        val monster = GameService.MonsterStats(hp = 6_000, atk = 250, matk = 250, pdef = 0, mdef = 0)
        val baseAtk = GameBalance.PLAYER_ATK_BASE + level * GameBalance.PLAYER_ATK_PER_LEVEL

        val weakOutcome = GameService.resolveBattle(
            GameService.CombatStats(atk = baseAtk + weak.atkBonus), playerHp, monster, 30, Random(42)
        )
        val strongOutcome = GameService.resolveBattle(
            GameService.CombatStats(atk = baseAtk + strong.atkBonus), playerHp, monster, 30, Random(42)
        )
        // 弱装备数学上不可能在 30 回合内击杀 6500 HP（atk 172 → 每回合上限 206、30 回合上限 6180）；
        // 强装备（atk ≈ 2658）必然击杀
        assertFalse(weakOutcome.won, "无有效装备加成的 172 攻击应在 30 回合内打不死 6000 HP")
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

    @Test
    fun `bonusFor should scale combined equipment and achievement bonus by prestige multiplier`() {
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
        val svc = EquipmentPowerService(ringRepo, boneRepo, coreRepo, achRepo)

        // 0 转回归：倍率 1.0，与不加参的旧行为逐位一致
        val b0 = svc.bonusFor(1L, 10, prestigeCount = 0)
        assertEquals(svc.bonusFor(1L, 10), b0, "0 转时 bonusFor 应与旧签名行为完全一致")

        // 2 转：装备+成就合计加成 ×1.2（取整 .toLong() 截断）
        val b2 = svc.bonusFor(1L, 10, prestigeCount = 2)
        val equipOnly = EquipmentPowerService.bonus(10, listOf(ring(year = 2, quality = 1, percentage = 500)), emptyList(), emptyList())
        val ach = EquipmentPowerService.achievementBonus(listOf("cult_10", "battle_10"))
        val mult = GameBalance.prestigeMultiplier(2)
        assertEquals(((equipOnly.atkBonus + ach.atkBonus) * mult).toLong(), b2.atkBonus)
        assertEquals(((equipOnly.hpBonus + ach.hpBonus) * mult).toLong(), b2.hpBonus)
        assertTrue(b2.atkBonus > b0.atkBonus && b2.hpBonus > b0.hpBonus, "2 转加成应严格大于 0 转")
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
            val prestigeCount = rng.nextInt(0, 6) // 0~5 转：六行不变量覆盖含倍率场景
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
            val d = EquipmentPowerService.detail(level, rings, bones, cores, EquipmentBonus(0, 0), prestigeCount)
            assertEquals(b.atkBonus, d.ringAtk + d.boneAtk + d.coreAtk, "atk 拆分失衡: level=$level $rings $bones $cores")
            assertEquals(b.hpBonus, d.ringHp + d.boneHp, "hp 拆分失衡: level=$level $rings $bones $cores")
            // 六行求和 == 含转生倍率的总战力（0 转时倍率 1.0，退化为原五行恒等）
            assertEquals(
                EquipmentPowerService.powerOf(level, EquipmentPowerService.applyPrestige(b, prestigeCount)),
                d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement + d.prestige,
                "战力拆分失衡: level=$level prestige=$prestigeCount $rings $bones $cores"
            )
            if (prestigeCount == 0) assertEquals(0L, d.prestige, "0 转时倍率增量行必须为 0")
        }
    }

    @Test
    fun `power detail with achievement bonus keeps six-row sum invariant with prestige multiplier`() {
        val level = 47
        val rings = listOf(ring(year = 1, quality = 2, percentage = 137), ring(year = 3, quality = 1, percentage = 903))
        val bones = listOf(bone(year = 2, quality = 3, enhance = 5))
        val cores = listOf(core(rarity = 3, value = 173))
        // 成就加成（cult_50 + battle_50 + tower_50 + tower_30 量级）：atk 245 / hp 4600（可被 10 整除便于精确断言）
        val ach = EquipmentBonus(atkBonus = 245, hpBonus = 4600)
        val prestigeCount = 3

        val combined = EquipmentPowerService.bonus(level, rings, bones, cores, ach)
        val d = EquipmentPowerService.detail(level, rings, bones, cores, ach, prestigeCount)
        val eff = EquipmentPowerService.applyPrestige(combined, prestigeCount)
        val power = EquipmentPowerService.powerOf(level, eff)

        // 六行求和不变量：base + ring + core + bone + achievement + prestige == 含倍率总战力
        assertEquals(power, d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement + d.prestige,
            "含成就与转生倍率的六行战力求和必须等于总战力")
        // prestige 行 = 倍率增量（含倍率战力 − 1.0 倍战力），单列不污染五行拆分
        val powerWithoutPrestige = EquipmentPowerService.powerOf(level, combined)
        assertEquals(powerWithoutPrestige, d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement,
            "前五行之和应保持 1.0 倍口径战力")
        assertEquals(power - powerWithoutPrestige, d.prestige)
        assertTrue(d.prestige > 0L, "3 转倍率必须产生正增量")
        // 成独行 = 成就 atk + 成就 hp 折算（hp 可被 POWER_HP_DIVISOR 整除 → 精确）
        assertEquals(ach.atkBonus + (ach.hpBonus / GameBalance.POWER_HP_DIVISOR).toLong(), d.achievement)
        // 装备拆分行保持装备口径（不含成就加成、不含倍率）
        val equipOnly = EquipmentPowerService.bonus(level, rings, bones, cores)
        assertEquals(equipOnly.atkBonus, d.ringAtk + d.boneAtk + d.coreAtk)
        assertEquals(equipOnly.hpBonus, d.ringHp + d.boneHp)
        assertEquals(combined.atkBonus, equipOnly.atkBonus + ach.atkBonus)
        assertEquals(combined.hpBonus, equipOnly.hpBonus + ach.hpBonus)
    }

    @Test
    fun `power detail at zero prestiges keeps legacy five-row invariant`() {
        // 回归保证：prestigeCount=0（默认）时 prestige 行恒 0，五行恒等与旧版逐位一致
        val level = 47
        val rings = listOf(ring(year = 1, quality = 2, percentage = 137), ring(year = 3, quality = 1, percentage = 903))
        val bones = listOf(bone(year = 2, quality = 3, enhance = 5))
        val cores = listOf(core(rarity = 3, value = 173))
        val ach = EquipmentBonus(atkBonus = 245, hpBonus = 4600)

        val combined = EquipmentPowerService.bonus(level, rings, bones, cores, ach)
        val d = EquipmentPowerService.detail(level, rings, bones, cores, ach)
        assertEquals(0L, d.prestige)
        assertEquals(EquipmentPowerService.powerOf(level, combined),
            d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement,
            "0 转时五行求和必须等于总战力（旧行为不变）")
    }

    // ======== 第十七轮战斗模型扩展：五属性加成与 powerOf 新权重 ========

    @Test
    fun `achievement bonus sums all seven reward fields`() {
        // cult_30: hp 300/atk 15/pdef 5/mdef 5；ring_1: matk 10/critRate 1；prestige_1: hp 500/pdef 20/mdef 20
        val ach = EquipmentPowerService.achievementBonus(listOf("cult_30", "ring_1", "prestige_1"))
        assertEquals(800L, ach.hpBonus, "hp = 300 + 500")
        assertEquals(15L, ach.atkBonus, "atk = cult_30 15")
        assertEquals(10L, ach.matkBonus, "matk = ring_1 10")
        assertEquals(25L, ach.pdefBonus, "pdef = cult_30 5 + prestige_1 20（cult_30 的 pdef=5 必须计入）")
        assertEquals(25L, ach.mdefBonus, "mdef = cult_30 5 + prestige_1 20")
        assertEquals(1L, ach.critRateBonus, "critRate = ring_1 1")
        assertEquals(0L, ach.critDmgBonus)
    }

    @Test
    fun `powerOf folds five attributes with calibrated weights`() {
        val base = EquipmentPowerService.powerOf(50, EquipmentBonus(0, 0))
        // matk×0.5(50) + pdef×0.2(2) + mdef×0.2(2) + critRate×5(5) + critDmg×0.2(2) = +61
        val b = EquipmentBonus(
            atkBonus = 0, hpBonus = 0, matkBonus = 100, pdefBonus = 10, mdefBonus = 10,
            critRateBonus = 1, critDmgBonus = 10
        )
        assertEquals(base + 61L, EquipmentPowerService.powerOf(50, b),
            "critRate 1 点 ≈ 5 战力、防御 10 点 ≈ 2 战力、matk 2 点 ≈ 1 战力（权重标定见 GameBalance）")
        // 单调性：任一五属性字段增加 → 战力不减（严格增加，除非增量被截断抹平）
        assertTrue(EquipmentPowerService.powerOf(50, b.copy(critRateBonus = 2)) >
                EquipmentPowerService.powerOf(50, b))
        assertTrue(EquipmentPowerService.powerOf(50, b.copy(pdefBonus = 20)) >
                EquipmentPowerService.powerOf(50, b))
    }

    @Test
    fun `power detail six-row invariant holds with five-attribute achievement bonuses`() {
        // 第十七轮扩展回归：五属性成就加成经 newAttrPower 并入成就行后，六行求和恒等仍严格成立
        val rng = Random(20260926)
        repeat(200) {
            val level = rng.nextInt(1, 121)
            val prestigeCount = rng.nextInt(0, 6)
            val rings = List(rng.nextInt(0, 7)) {
                ring(year = rng.nextInt(0, 6), quality = rng.nextInt(0, 5), percentage = rng.nextInt(0, 1000))
            }
            val bones = List(rng.nextInt(0, 5)) {
                bone(year = rng.nextInt(0, 6), quality = rng.nextInt(0, 5), enhance = rng.nextInt(0, 13))
            }
            val cores = List(rng.nextInt(0, 3)) {
                core(rarity = rng.nextInt(0, 5), value = rng.nextInt(1, 300))
            }
            val ach = EquipmentBonus(
                atkBonus = rng.nextLong(0, 500), hpBonus = rng.nextLong(0, 5000),
                matkBonus = rng.nextLong(0, 300), pdefBonus = rng.nextLong(0, 100),
                mdefBonus = rng.nextLong(0, 100), critRateBonus = rng.nextLong(0, 20),
                critDmgBonus = rng.nextLong(0, 60)
            )
            val b = EquipmentPowerService.bonus(level, rings, bones, cores, ach)
            val d = EquipmentPowerService.detail(level, rings, bones, cores, ach, prestigeCount)
            assertEquals(
                EquipmentPowerService.powerOf(level, EquipmentPowerService.applyPrestige(b, prestigeCount)),
                d.basePower + d.ringPower + d.bonePower + d.corePower + d.achievement + d.prestige,
                "含五属性成就加成的六行战力求和必须等于总战力: level=$level prestige=$prestigeCount ach=$ach"
            )
            if (prestigeCount == 0) assertEquals(0L, d.prestige, "0 转时倍率增量行必须为 0")
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
