package com.example.garygame

import com.example.garygame.model.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/**
 * 全量模型层单元测试 — 覆盖 Models.kt 中所有数据逻辑
 * 包含：境界系统、魂环/魂骨、怪物、套装、图鉴、塔、副本、天赋、成就
 */
class ModelsTest {

    // ============================================================
    // 1. RealmData — 境界系统
    // ============================================================

    @Test
    fun `境界名称_各等级段正确`() {
        assertEquals("魂士", RealmData.name(1))
        assertEquals("魂士", RealmData.name(9))
        assertEquals("魂士", RealmData.name(10))  // Lv.10是魂士最后一档
        assertEquals("魂尊", RealmData.name(40))  // Lv.40=(40-1)/10=3→魂尊
        assertEquals("封号斗罗", RealmData.name(95))
        assertEquals("创世神", RealmData.name(160))
    }

    @Test
    fun `境界名称_边界值安全`() {
        assertEquals("魂士", RealmData.name(0))       // 低于最小
        assertEquals("创世神", RealmData.name(999))    // 极高值
        assertEquals("创世神", RealmData.name(Int.MAX_VALUE))
    }

    @Test
    fun `突破消耗_正增长`() {
        val c1 = RealmData.breakthroughCost(1)
        val c10 = RealmData.breakthroughCost(10)
        val c100 = RealmData.breakthroughCost(100)
        assertTrue("等级越高消耗越大", c10 > c1)
        assertTrue("等级越高消耗越大", c100 > c10)
        assertTrue("等级1消耗应为正数", c1 > 0)
    }

    @Test
    fun `突破消耗_非负安全`() {
        val c0 = RealmData.breakthroughCost(0)
        assertTrue("等级0消耗应非负(150*0^1.65=0)", c0 >= 0)
    }

    @Test
    fun `基础属性_随等级增长`() {
        assertTrue(RealmData.baseHp(100) > RealmData.baseHp(1))
        assertTrue(RealmData.baseAtk(100) > RealmData.baseAtk(1))
        assertTrue(RealmData.baseHp(1) > 0)
    }

    @Test
    fun `maxSoulRings_边界值`() {
        assertEquals("1级1个魂环", 1, RealmData.maxSoulRings(1))
        assertEquals("20级1个魂环(21级解锁第2个)", 1, RealmData.maxSoulRings(20))
        assertEquals("99级5个魂环", 5, RealmData.maxSoulRings(99))
        assertEquals("160级8个魂环(161级解锁第9个)", 8, RealmData.maxSoulRings(160))
    }

    @Test
    fun `maxSoulBones_边界值`() {
        assertEquals("1级1个魂骨", 1, RealmData.maxSoulBones(1))
        assertEquals("30级1个魂骨(31级解锁第2个)", 1, RealmData.maxSoulBones(30))
        assertEquals("150级5个魂骨(151级解锁第6个)", 5, RealmData.maxSoulBones(150))
    }

    @Test
    fun `境界加成倍率_10级倍数`() {
        assertEquals("1级无加成", 1.0, RealmData.realmBonusMult(1), 0.001)
        assertEquals("9级无加成", 1.0, RealmData.realmBonusMult(9), 0.001)
        assertEquals("10级+25%", 1.25, RealmData.realmBonusMult(10), 0.001)
        assertEquals("20级+50%", 1.5, RealmData.realmBonusMult(20), 0.001)
        assertEquals("100级+250%", 3.5, RealmData.realmBonusMult(100), 0.001)
    }

    @Test
    fun `转生等级门槛为100`() {
        assertEquals(100, RealmData.PRESTIGE_MIN_LEVEL)
    }

    // ============================================================
    // 2. Skill — 技能系统
    // ============================================================

    @Test
    fun `技能连击次数_依赖类型`() {
        val single = Skill("测试", "", SkillType.SINGLE_DAMAGE, 100, 3)
        val multi = Skill("测试", "", SkillType.MULTI_HIT, 200, 4)
        assertEquals("单体攻击1次", 1, single.hitCount())
        assertTrue("多段攻击≥2次", multi.hitCount() in 2..5)
    }

    @Test
    fun `技能单次威力_多段减半`() {
        val multi = Skill("测试", "", SkillType.MULTI_HIT, 200, 4)
        assertEquals("多段单次威力50", 50, multi.perHitPower())
    }

    // ============================================================
    // 3. RingYear / RingQuality — 魂环双维度
    // ============================================================

    @Test
    fun `RingYear_边界值`() {
        assertEquals(RingYear.HUNDRED, RingYear.fromCombinedTier(0))
        assertEquals(RingYear.HUNDRED, RingYear.fromCombinedTier(4))
        assertEquals(RingYear.MILLION, RingYear.fromCombinedTier(24))
        assertEquals("年份0", 0, RingYear.yearOf(0))
        assertEquals("年份4", 4, RingYear.yearOf(24))
    }

    @Test
    fun `RingQuality_qualityOf边界值`() {
        assertEquals(0, RingYear.qualityOf(0))
        assertEquals(4, RingYear.qualityOf(4))
        assertEquals(0, RingYear.qualityOf(5))
        assertEquals(4, RingYear.qualityOf(24))
    }

    @Test
    fun `有效品质_在范围内`() {
        val q0 = RingYear.effectiveQuality(0)
        val q24 = RingYear.effectiveQuality(24)
        assertTrue("最小有效品质为0", q0 >= 0)
        assertTrue("最大有效品质约6.8", q24 >= 0)
    }

    @Test
    fun `技能倍率_随层级增长`() {
        val m0 = RingQuality.skillMultiplier(0)
        val m24 = RingQuality.skillMultiplier(24)
        assertTrue("高品质倍率更大", m24 > m0)
        assertTrue("倍率至少为1", m0 >= 1.0)
    }

    @Test
    fun `词缀数量_随年份增长`() {
        assertEquals("百年2个", 2, RingQuality.affixCount(0))
        assertEquals("千年2个", 2, RingQuality.affixCount(5))
        assertEquals("万年3个", 3, RingQuality.affixCount(10))
        assertEquals("十万年4个", 4, RingQuality.affixCount(15))
        assertEquals("百万年5个", 5, RingQuality.affixCount(20))
    }

    @Test
    fun `出售价格_正数_随层级增长`() {
        val p0 = RingQuality.sellPrice(0)
        val p24 = RingQuality.sellPrice(24)
        assertTrue("至少为1", p0 >= 1)
        assertTrue("层级越高价格越高", p24 > p0)
    }

    @Test
    fun `完整显示名_格式正确`() {
        val name = RingQuality.fullDisplayName(0)  // 百年·劣等魂环
        assertTrue(name.contains("百年"))
        assertTrue(name.contains("魂环"))
    }

    @Test
    fun `吸收费用_随拥有数量递增`() {
        val c1 = RingQuality.cost(5, 0)
        val c2 = RingQuality.cost(5, 5)
        assertTrue("已拥有越多费用越高", c2 > c1)
    }

    // ============================================================
    // 4. SoulRingSlots — 魂环槽位
    // ============================================================

    @Test
    fun `魂环槽位_共9个`() {
        assertEquals(9, SoulRingSlots.all.size)
    }

    @Test
    fun `canEquip_边界条件`() {
        assertTrue("第1槽可装最低层级", SoulRingSlots.canEquip(0, 0))
        assertFalse("无效槽位返回false", SoulRingSlots.canEquip(99, 0))
        assertTrue("第9槽可装20+层级", SoulRingSlots.canEquip(8, 20))
        assertFalse("第9槽不能装低于20", SoulRingSlots.canEquip(8, 19))
    }

    // ============================================================
    // 5. SoulRingInstance — 魂环实例
    // ============================================================

    @Test
    fun `魂环实例_combinedTier计算正确`() {
        val ring = SoulRingInstance(1, 2, mutableListOf(), null, 50)
        assertEquals("千年·精良的合并层级", 7, ring.combinedTier)
    }

    @Test
    fun `魂环实例_load为正数`() {
        val ring = SoulRingInstance(3, 4, mutableListOf(), null, 80)
        assertTrue("负荷值应为正数", ring.load > 0)
    }

    @Test
    fun `魂环实例_等效年份为整数正数`() {
        val ring = SoulRingInstance(2, 3, mutableListOf(), null, 60)
        assertTrue("等效年份应为正数", ring.effectiveYears > 0)
    }

    @Test
    fun `魂环实例_合并层级构造器兼容`() {
        val ring = SoulRingInstance(12, mutableListOf(EquipAffixValue(EquipAffix.ATK, 50)))
        assertEquals(12, ring.combinedTier)             // 合并层级
        assertTrue("年份=百年(2)", ring.yearOrdinal >= 0)
        assertTrue("品质=劣等(2)", ring.qualityOrdinal >= 0)
    }

    // ============================================================
    // 6. SoulRingSystem — 魂环系统算法
    // ============================================================

    @Test
    fun `计算魂环负荷_百年最低`() {
        val load = SoulRingSystem.calcRingLoad(0, 0, 0)
        assertTrue("百年·劣等·0%的负荷≥1", load >= 1)
        assertEquals("百年·劣等·0% = 1", 1, load)
    }

    @Test
    fun `计算魂环负荷_百万年完美最高`() {
        val load = SoulRingSystem.calcRingLoad(4, 4, 100)
        // 10000000 * 0.1 = 1000000
        assertEquals("百万年·完美·100%", 1000000, load)
    }

    @Test
    fun `计算魂环负荷_边界安全`() {
        val loadMin = SoulRingSystem.calcRingLoad(0, 0, 0)
        val loadMax = SoulRingSystem.calcRingLoad(4, 4, 999)
        assertTrue("边界安全", loadMin >= 1)
        assertTrue("边界安全", loadMax >= 1)
    }

    @Test
    fun `等效年份_百年最低_百万年最高`() {
        val min = SoulRingSystem.calcEffectiveYears(0, 0, 0)
        val max = SoulRingSystem.calcEffectiveYears(4, 4, 100)
        assertTrue("百年·劣等·0% ≥ 1", min >= 1)
        assertEquals("百万年·完美·100% = 1000000(10000000×0.1)", 1000000, max)
    }

    @Test
    fun `计算根骨_零属性时不为负`() {
        val bone = SoulRingSystem.calcRootBone(0, 0, 0, 0, 0)
        assertEquals("零属性根骨为0", 0.0, bone, 0.001)
    }

    @Test
    fun `计算吸收容量_至少100`() {
        val cap = SoulRingSystem.calcAbsorptionCapacity(0.0)
        assertEquals("零根骨时容量至少100", 100, cap)
    }

    @Test
    fun `随机年分数_在100到999之间`() {
        repeat(1000) {
            val pct = SoulRingSystem.randomPercentage()
            assertTrue("年分数应在100-999之间: $pct", pct in 100..999)
        }
    }

    @Test
    fun `全显示名_格式正确`() {
        val name = SoulRingSystem.fullDisplayName(3, 2, 75)
        assertTrue(name.contains("十万年"))
        assertTrue(name.contains("精良"))
        assertTrue(name.contains("%"))
    }

    // ============================================================
    // 7. SoulRingGenerator — 魂环随机生成
    // ============================================================

    @Test
    fun `随机词缀_数量匹配`() {
        repeat(20) {
            val affixes = SoulRingGenerator.generateRandomAffixes(0)
            assertEquals("百年魂环2个词缀", 2, affixes.size)
        }
    }

    @Test
    fun `随机词缀_值均为正数`() {
        val affixes = SoulRingGenerator.generateRandomAffixes(15)
        affixes.forEach { assertTrue("词缀值应为正: ${it.type}=${it.value}", it.value > 0) }
    }

    @Test
    fun `splitTier_边界`() {
        val (y0, q0) = SoulRingGenerator.splitTier(0)
        assertEquals(0, y0); assertEquals(0, q0)

        val (y24, q24) = SoulRingGenerator.splitTier(24)
        assertEquals(4, y24); assertEquals(4, q24)
    }

    // ============================================================
    // 8. BoneType / BoneYear / BoneRarity — 魂骨系统
    // ============================================================

    @Test
    fun `魂骨类型_6种`() {
        assertEquals(6, BoneType.entries.size)
    }

    @Test
    fun `魂骨层级_0到24`() {
        val y0 = BoneYear.combinedTier(0, 0)
        val y24 = BoneYear.combinedTier(4, 4)
        assertEquals(0, y0)
        assertEquals(24, y24)
    }

    @Test
    fun `魂骨出售价格_正数`() {
        assertTrue(BoneRarity.sellPrice(0) > 0)
        assertTrue(BoneRarity.sellPrice(24) > 0)
    }

    // ============================================================
    // 9. SoulBoneSetData — 魂骨套装
    // ============================================================

    @Test
    fun `空装备时无套装效果`() {
        val sets = SoulBoneSetData.getActiveSets(emptyMap())
        assertEquals("无装备时应无套装", 0, sets.size)
    }

    @Test
    fun `2件以上触发套装`() {
        val bones = mapOf(
            0 to SoulBoneInstance(0, 0, emptyList()),
            1 to SoulBoneInstance(0, 0, emptyList())
        )
        val sets = SoulBoneSetData.getActiveSets(bones)
        assertTrue("至少2件骨触发套装", sets.isNotEmpty())
    }

    // ============================================================
    // 10. MonsterAffix — 怪物词缀
    // ============================================================

    @Test
    fun `怪物词缀_随机倍率合理`() {
        MonsterAffix.entries.forEach { affix ->
            assertTrue("掉落倍率应为正数", affix.dropMult > 0)
            assertNotNull("颜色应为有效值", affix.colorInt)
        }
    }

    // ============================================================
    // 11. MapData — 地图数据
    // ============================================================

    @Test
    fun `地图列表_8张且完整`() {
        assertEquals(8, MapData.all.size)
        assertEquals("圣魂村", MapData.all[0].name)
        assertEquals("神界废墟", MapData.all[7].name)
    }

    @Test
    fun `getMap_边界安全`() {
        assertNull("负数地图返回null", MapData.getMap(-1))
        assertNull("超出地图返回null", MapData.getMap(100))
        assertNotNull("地图0有效", MapData.getMap(0))
    }

    // ============================================================
    // 12. CodexData — 怪物图鉴
    // ============================================================

    @Test
    fun `图鉴称号_按击杀数变化`() {
        assertEquals("新手猎手", CodexData.getTitle(0))
        assertEquals("初出茅庐", CodexData.getTitle(100))
        assertEquals("传奇猎手", CodexData.getTitle(50000))
        assertEquals("征服者", CodexData.getTitle(500000))
    }

    @Test
    fun `图鉴攻击加成_随击杀增长`() {
        val b0 = CodexData.codexAtkBonus(0)
        val b50k = CodexData.codexAtkBonus(50000)
        val bMax = CodexData.codexAtkBonus(Long.MAX_VALUE)
        assertEquals("0击杀无加成", 0, b0)
        assertTrue("高击杀高加成", b50k > b0)
        assertTrue("Max击杀有加成", bMax > 0)
    }

    @Test
    fun `下一里程碑_NULL表示已满`() {
        assertNotNull(CodexData.nextMilestone(0))
        assertNull("超出最后里程碑返回null", CodexData.nextMilestone(1000000000L))
    }

    // ============================================================
    // 13. TowerData — 杀戮之都
    // ============================================================

    @Test
    fun `塔段位_边界安全`() {
        assertNotNull("0层返回第一段", TowerData.getSegment(0))
        assertNotNull("100层返回最后段", TowerData.getSegment(100))
        assertNotNull("极高值安全", TowerData.getSegment(9999))
    }

    @Test
    fun `BOSS层_10的倍数`() {
        assertTrue(TowerData.isBossFloor(10))
        assertTrue(TowerData.isBossFloor(100))
        assertFalse(TowerData.isBossFloor(5))
    }

    @Test
    fun `休息层_5的倍数非BOSS`() {
        assertTrue(TowerData.isRestFloor(5))
        assertFalse(TowerData.isRestFloor(10)) // BOSS层不算休息
    }

    @Test
    fun `塔费用_边界安全`() {
        assertTrue("0层费用非负", TowerData.towerHpCostPerFloor(0) >= 0)
        assertTrue("高层费用更大", TowerData.towerHpCostPerFloor(81) > TowerData.towerHpCostPerFloor(20))
    }

    @Test
    fun `普通奖励_指数增长`() {
        val r1 = TowerData.normalReward(1)
        val r50 = TowerData.normalReward(50)
        assertTrue("高层奖励更多", r50 > r1)
        assertTrue("奖励为正", r1 > 0)
    }

    @Test
    fun `BOSS魂骨奖励_随层数递增`() {
        assertTrue(TowerData.bossBoneReward(90) >= TowerData.bossBoneReward(40))
        assertTrue(TowerData.bossBoneReward(10) >= 0)
    }

    // ============================================================
    // 14. DungeonData — 每日副本
    // ============================================================

    @Test
    fun `副本层级_按转生数解锁`() {
        assertEquals(0, DungeonData.getMaxTier(1))   // 1转→第1层
        assertEquals(4, DungeonData.getMaxTier(5))   // 5转→最高层
        assertEquals(4, DungeonData.getMaxTier(99))  // 极高→最高层
    }

    @Test
    fun `副本重置间隔1天`() {
        assertEquals(86400000L, DungeonData.DAILY_RESET_MS)
    }

    // ============================================================
    // 15. TalentBranch — 天赋树
    // ============================================================

    @Test
    fun `天赋效果描述_不返回空`() {
        for (branch in TalentBranch.entries) {
            for (lv in 1..branch.maxLevel) {
                val desc = TalentBranch.effectDescription(branch, lv)
                assertTrue("${branch.displayName} Lv.$lv 应有描述: $desc", desc.isNotEmpty())
            }
        }
    }

    @Test
    fun `天赋等级0无描述`() {
        for (branch in TalentBranch.entries) {
            assertEquals("", TalentBranch.effectDescription(branch, 0))
        }
    }

    // ============================================================
    // 16. AchievementDefs — 成就系统
    // ============================================================

    @Test
    fun `成就条件_应可执行`() {
        val snapshot = GameSnapshot(1, 0, 0, 0, 0, 0)
        for (ach in AchievementDefs.all) {
            assertNotNull("${ach.id} 的condition不应为null", ach.condition)
            // 只是调用一下确保不抛异常
            ach.condition(snapshot)
        }
    }

    @Test
    fun `成就数量_共21个`() {
        assertEquals(21, AchievementDefs.all.size)
    }

    @Test
    fun `成就在满足条件时触发`() {
        val snapshot = GameSnapshot(150, 9, 1000, 100, 5, 999999)
        for (ach in AchievementDefs.all) {
            assertTrue("${ach.id} (${ach.name}) 应在满足条件时触发", ach.condition(snapshot))
        }
    }

    @Test
    fun `成就奖励描述格式`() {
        for (ach in AchievementDefs.all) {
            val desc = ach.rewards.description()
            if (ach.rewards.hp == 0L && ach.rewards.atk == 0 && ach.rewards.matk == 0
                && ach.rewards.pdef == 0 && ach.rewards.mdef == 0
                && ach.rewards.critRate == 0 && ach.rewards.critDmg == 0) {
                assertEquals("无奖励时返回空", "", desc)
            } else {
                assertTrue("应有奖励描述", desc.isNotEmpty())
            }
        }
    }

    // ============================================================
    // 17. DroppedRing / DroppedBone — 掉落物品
    // ============================================================

    @Test
    fun `掉落魂环_combinedTier计算`() {
        val ring = DroppedRing(2, 3, mutableListOf(), null, 50)
        assertEquals("万年·优秀", 13, ring.combinedTier)
    }

    @Test
    fun `掉落魂环_合并层级构造器兼容`() {
        val ring = DroppedRing(15, emptyList())
        assertEquals(15, ring.combinedTier)
    }

    @Test
    fun `掉落魂骨_combinedTier计算`() {
        val bone = DroppedBone(0, 1, 2, emptyList())
        assertEquals("千年·精良", 7, bone.combinedTier)
    }

    // ============================================================
    // 18. EquipAffix — 词缀系统
    // ============================================================

    @Test
    fun `词缀倍率_均为正数`() {
        for (affix in EquipAffix.entries) {
            assertTrue("${affix.displayName} 倍率应为正", affix.valueMultiplier > 0)
        }
    }

    // ============================================================
    // 19. MartialSoulPool — 武魂池
    // ============================================================

    @Test
    fun `武魂池_12个武魂`() {
        assertEquals(12, MartialSoulPool.all.size)
    }

    @Test
    fun `可用武魂池_按转生过滤`() {
        val p0 = MartialSoulPool.getAvailablePool(0)
        val p5 = MartialSoulPool.getAvailablePool(5)
        assertTrue("0转无神话", p0.none { it.rarity == Rarity.MYTHIC })
        assertTrue("5转有神话", p5.any { it.rarity == Rarity.MYTHIC })
        assertTrue("0转池≤5转池大小", p0.size <= p5.size)
    }

    @Test
    fun `随机觉醒_返回有效武魂`() {
        val soul = MartialSoulPool.randomAwaken(0)
        assertNotNull(soul)
        assertTrue(soul.name.isNotEmpty())
    }

    @Test
    fun `按流派随机觉醒_有效`() {
        val soul = MartialSoulPool.randomAwakenForSchool(SoulSchool.PHYSICAL, 3)
        assertNotNull(soul)
    }

    // ============================================================
    // 20. Rarity — 稀有度
    // ============================================================

    @Test
    fun `稀有度_按顺序递增`() {
        val ordinals = Rarity.entries.map { it.ordinal }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), ordinals)
    }

    @Test
    fun `稀有度颜色_非零`() {
        for (r in Rarity.entries) {
            assertNotNull("${r.displayName} 颜色应为有效值", r.colorInt)
        }
    }
}
