package com.example.garygame.model

import kotlin.math.*
import kotlin.random.Random

// 品质/稀有度 (每个年份各有5个品质)
enum class Rarity(val displayName: String, val colorHex: Long) {
    COMMON("普通", 0xFF9E9E9E),
    UNCOMMON("精良", 0xFF4CAF50),
    RARE("稀有", 0xFF2196F3),
    EPIC("史诗", 0xFF9C27B0),
    LEGENDARY("传说", 0xFFFF9800),
    MYTHIC("神话", 0xFFFF1744);

    val colorInt: Int get() = colorHex.toInt()
}

// ============ 玩家属性 ============
data class PlayerAttributes(
    val maxHp: Long, val atk: Int, val matk: Int,
    val critRate: Int, val critDmg: Int, val pdef: Int, val mdef: Int
)

// ============ 境界系统 ============
object RealmData {
    val names = listOf(
        "魂士", "魂师", "大魂师", "魂尊", "魂宗",
        "魂王", "魂帝", "魂圣", "魂斗罗", "封号斗罗",
        "极限斗罗", "半神", "神祇", "神王", "至高神王", "创世神"
    )

    fun name(level: Int): String {
        val idx = ((level - 1) / 10).coerceIn(0, names.size - 1)
        return names[idx]
    }

    fun breakthroughCost(level: Int): Long = (150.0 * level.toDouble().pow(1.65)).toLong()
    fun baseHp(level: Int): Long = (50L * level + 100L)
    fun baseAtk(level: Int): Int = level * 10
    fun baseMatk(level: Int): Int = level * 8
    fun basePdef(level: Int): Int = level * 5
    fun baseMdef(level: Int): Int = level * 4
    fun autoGoldBonus(level: Int): Int = level * 8
    fun maxSoulRings(level: Int): Int = ((level - 1) / 20 + 1).coerceIn(1, 9)
    fun maxSoulBones(level: Int): Int = ((level - 1) / 30 + 1).coerceIn(1, 6)
    fun ringSlotUnlockLevel(slotIndex: Int): Int = slotIndex * 20 + 1
    fun boneSlotUnlockLevel(slotIndex: Int): Int = slotIndex * 30 + 1
    fun battleSoulPowerMax(level: Int): Int = 100 + level * 5

    fun realmBonusMult(level: Int): Double {
        val completedRealms = level / 10
        return 1.0 + completedRealms * 0.25
    }

    const val PRESTIGE_MIN_LEVEL = 100
}

// ============ 技能系统 ============
enum class SkillType(val displayName: String) {
    SINGLE_DAMAGE("单体攻击"), MULTI_HIT("多段攻击"), HEAL("治疗回复"),
    IGNORE_DEFENSE("无视防御"), BERSERK("狂暴打击")
}

data class Skill(
    val name: String, val description: String, val type: SkillType,
    val power: Int = 100, val cooldown: Int = 3
) {
    fun hitCount(): Int = when (type) {
        SkillType.MULTI_HIT -> (power / 50).coerceIn(2, 5)
        else -> 1
    }
    fun perHitPower(): Int = when (type) {
        SkillType.MULTI_HIT -> 50
        SkillType.SINGLE_DAMAGE -> power
        SkillType.IGNORE_DEFENSE -> power
        SkillType.BERSERK -> power
        else -> 100
    }
}

// ============ 武魂系统 ============
data class MartialSoul(
    val name: String, val rarity: Rarity,
    val baseHp: Long = 50L, val baseAtk: Int = 20, val baseMatk: Int = 10,
    val critRate: Int = 5, val critDmg: Int = 150, val pdef: Int = 5, val mdef: Int = 5,
    val autoGoldBonus: Int = 0, val description: String = "",
    val skill: Skill = Skill("普通攻击", "基础攻击", SkillType.SINGLE_DAMAGE, 100, 3),
    val school: SoulSchool? = null
)

object MartialSoulPool {
    val all = listOf(
        MartialSoul("蓝银草", Rarity.COMMON, 50, 20, 10, 5, 150, 5, 5, 2, "最普通的武魂，但蕴含着无限可能", Skill("缠绕", "蓝银草缠绕敌人造成额外伤害", SkillType.SINGLE_DAMAGE, 140, 3)),
        MartialSoul("幽冥灵猫", Rarity.COMMON, 40, 25, 5, 10, 150, 3, 3, 3, "敏捷型武魂，速度极快", Skill("幽冥突刺", "快速突刺连续攻击两次", SkillType.MULTI_HIT, 100, 4)),
        MartialSoul("柔骨兔", Rarity.UNCOMMON, 80, 30, 15, 8, 160, 8, 8, 5, "柔韧型武魂，小舞的本命武魂", Skill("腰弓", "柔技爆发造成大量伤害", SkillType.SINGLE_DAMAGE, 180, 4)),
        MartialSoul("蓝银皇", Rarity.UNCOMMON, 100, 40, 30, 8, 160, 10, 12, 8, "蓝银草的进化形态", Skill("蓝银囚笼", "囚笼束缚敌人造成持续伤害", SkillType.SINGLE_DAMAGE, 160, 3)),
        MartialSoul("七宝琉璃塔", Rarity.RARE, 120, 50, 60, 12, 180, 15, 20, 12, "辅助系顶级武魂", Skill("七宝转出", "琉璃塔之力回复30%血量", SkillType.HEAL, 30, 5)),
        MartialSoul("邪火凤凰", Rarity.RARE, 110, 55, 70, 15, 200, 12, 10, 10, "强攻系武魂，火焰之力惊人", Skill("凤凰啸天击", "火焰爆发造成大量魔攻伤害", SkillType.SINGLE_DAMAGE, 220, 4)),
        MartialSoul("白虎", Rarity.RARE, 200, 60, 20, 10, 170, 25, 15, 10, "戴沐白的武魂，攻防兼备", Skill("白虎烈光波", "光波冲击造成两次伤害", SkillType.MULTI_HIT, 120, 4)),
        MartialSoul("昊天锤", Rarity.EPIC, 250, 80, 30, 12, 200, 30, 20, 18, "唐昊的武魂，力量型巅峰", Skill("乱披风锤法", "连续锤击造成三次伤害", SkillType.MULTI_HIT, 150, 5)),
        MartialSoul("九宝琉璃塔", Rarity.EPIC, 180, 75, 90, 15, 200, 20, 25, 25, "七宝琉璃塔的终极进化", Skill("九宝护体", "九宝之力回复50%血量", SkillType.HEAL, 50, 5)),
        MartialSoul("六翼天使", Rarity.LEGENDARY, 350, 120, 100, 18, 220, 35, 30, 35, "天使神的武魂，光之极致", Skill("天使圣光", "天使之力造成无视防御的伤害", SkillType.IGNORE_DEFENSE, 180, 4)),
        MartialSoul("海神三叉戟", Rarity.LEGENDARY, 300, 130, 120, 15, 250, 30, 35, 40, "海神的武器，水系至尊", Skill("海神之怒", "召唤海神之力造成巨量伤害", SkillType.SINGLE_DAMAGE, 250, 5)),
        MartialSoul("修罗魔剑", Rarity.MYTHIC, 500, 200, 150, 25, 300, 40, 35, 60, "修罗神的佩剑，杀戮之力", Skill("修罗斩", "修罗之力的全力一击", SkillType.IGNORE_DEFENSE, 300, 5))
    )

    fun getAvailablePool(prestigeCount: Int): List<MartialSoul> {
        val maxRarity = when {
            prestigeCount >= 5 -> Rarity.MYTHIC
            prestigeCount >= 3 -> Rarity.LEGENDARY
            prestigeCount >= 2 -> Rarity.EPIC
            prestigeCount >= 1 -> Rarity.RARE
            else -> Rarity.UNCOMMON
        }
        return all.filter { it.rarity.ordinal <= maxRarity.ordinal }
    }

    fun randomAwaken(prestigeCount: Int): MartialSoul {
        val pool = getAvailablePool(prestigeCount)
        val weights = pool.map { soul ->
            when (soul.rarity) {
                Rarity.COMMON -> 40.0; Rarity.UNCOMMON -> 25.0; Rarity.RARE -> 15.0
                Rarity.EPIC -> 8.0; Rarity.LEGENDARY -> 2.0; Rarity.MYTHIC -> 0.5
            }
        }
        val total = weights.sum()
        var rand = Math.random() * total
        for (i in pool.indices) { rand -= weights[i]; if (rand <= 0) return pool[i] }
        return pool.first()
    }

    fun randomAwakenForSchool(school: SoulSchool, prestigeCount: Int): MartialSoul {
        val pool = getAvailablePool(prestigeCount)
        val schoolPool = pool.filter { it.school == null || it.school == school }
        val effectivePool = if (schoolPool.isNotEmpty()) schoolPool else pool
        val weights = effectivePool.map { soul ->
            when (soul.rarity) {
                Rarity.COMMON -> 40.0; Rarity.UNCOMMON -> 25.0; Rarity.RARE -> 15.0
                Rarity.EPIC -> 8.0; Rarity.LEGENDARY -> 2.0; Rarity.MYTHIC -> 0.5
            }
        }
        val total = weights.sum()
        var rand = Math.random() * total
        for (i in effectivePool.indices) { rand -= weights[i]; if (rand <= 0) return effectivePool[i] }
        return effectivePool.first()
    }
}

// ============ 装备词缀系统 ============
enum class EquipAffix(val displayName: String, val valueMultiplier: Double) {
    HP("生命", 5.0), ATK("攻击", 1.0), MATK("魔攻", 1.0),
    CRIT_RATE("暴击", 0.06), CRIT_DMG("爆伤", 0.3), PDEF("物防", 0.5), MDEF("魔防", 0.5)
}

data class EquipAffixValue(val type: EquipAffix, val value: Int)

// ============ 魂环系统（V5双维度：年份×品质 = 5×5=25层）============

enum class RingYear(val displayName: String, val costBase: Int, val idx: Int, val colorHex: Long = 0xFFCCCCCC) {
    HUNDRED("百年", 500, 0, 0xFFB0BEC5),
    THOUSAND("千年", 3000, 1, 0xFF4CAF50),
    TEN_THOUSAND("万年", 15000, 2, 0xFF2196F3),
    HUNDRED_THOUSAND("十万年", 80000, 3, 0xFF9C27B0),
    MILLION("百万年", 400000, 4, 0xFFFF9800);

    val colorInt: Int get() = colorHex.toInt()

    companion object {
        val size = entries.size
        fun fromCombinedTier(tier: Int): RingYear = entries[tier / 5]
        fun combinedTier(yearOrdinal: Int, qualityOrdinal: Int): Int = yearOrdinal * 5 + qualityOrdinal
        fun yearOf(tier: Int): Int = tier / 5
        fun qualityOf(tier: Int): Int = tier % 5
        fun effectiveQuality(tier: Int): Double {
            val y = yearOf(tier)
            val q = qualityOf(tier)
            return y + q * 0.7
        }
        fun cost(combinedTier: Int, ringCount: Int): Long {
            val year = fromCombinedTier(combinedTier)
            return (year.costBase * (1.0 + ringCount * 0.5)).toLong()
        }
    }
}

enum class RingQuality(val displayName: String, val colorHex: Long) {
    INFERIOR("劣等", 0xFF9E9E9E),
    NORMAL("普通", 0xFF4CAF50),
    FINE("精良", 0xFF2196F3),
    EXCELLENT("优秀", 0xFF9C27B0),
    PERFECT("完美", 0xFFFFD700);

    val colorInt: Int get() = colorHex.toInt()

    companion object {
        val size = entries.size
        /** 技能倍率：基于有效品质（完美是劣等9倍） */
        fun skillMultiplier(tier: Int): Double {
            val q = RingYear.effectiveQuality(tier)
            return 1.0 + 0.5 * q * q
        }
        /** 属性倍率：基于有效品质（完美是劣等约4倍） */
        fun statMultiplier(tier: Int): Double {
            val q = RingYear.effectiveQuality(tier)
            return 1.0 + 0.18 * q * q
        }
        /** 词缀数量：年份决定基础（百年2→千年3→万年4→十万年5→百万年6） */
        fun affixCount(tier: Int): Int {
            val y = RingYear.yearOf(tier)
            return when (y) { 0 -> 2; 1 -> 2; 2 -> 3; 3 -> 4; 4 -> 5; else -> 2 }
        }
        /** 词缀基础值：基于有效品质平滑插值 */
        fun baseValue(tier: Int): Int {
            val q = RingYear.effectiveQuality(tier)
            val lo = q.toInt().coerceIn(0, 3)
            val hi = (lo + 1).coerceAtMost(4)
            val frac = q - lo
            val anchors = listOf(8, 18, 40, 85, 180)
            return (anchors[lo] + (anchors[hi] - anchors[lo]) * frac).toInt()
        }
        /** 出售价格：年份基数×品质系数 */
        fun sellPrice(tier: Int): Long {
            val y = RingYear.yearOf(tier)
            val q = RingYear.qualityOf(tier)
            return (RingYear.entries[y].costBase * (1.0 + q * 0.6)).toLong()
        }
        /** 完整显示名：百年·劣等魂环 */
        fun fullName(year: Int, quality: Int): String =
            "${RingYear.entries[year].displayName}·${entries[quality].displayName}魂环"
        /** V5兼容：基于合并层级显示全名 */
        fun fullDisplayName(combinedTier: Int): String {
            val y = RingYear.yearOf(combinedTier)
            val q = RingYear.qualityOf(combinedTier)
            return fullName(y, q)
        }
        /** 获取年份颜色 */
        fun yearColorInt(year: Int): Int = RingYear.entries[year].colorInt
        /** 向后兼容：吸收魂环费用（基于合并层级+已拥有数量） */
        fun cost(combinedTier: Int, ownedCount: Int): Long {
            val y = RingYear.yearOf(combinedTier)
            val q = RingYear.qualityOf(combinedTier)
            return (RingYear.entries[y].costBase * (1.0 + q * 0.5) * (1 + ownedCount * 0.3)).toLong()
        }
    }
}

data class SoulRingSlot(val index: Int, val displayName: String, val minCombinedTier: Int)

object SoulRingSlots {
    val all = listOf(
        SoulRingSlot(0, "第一魂环", 0),   SoulRingSlot(1, "第二魂环", 0),
        SoulRingSlot(2, "第三魂环", 5),   SoulRingSlot(3, "第四魂环", 5),
        SoulRingSlot(4, "第五魂环", 10),  SoulRingSlot(5, "第六魂环", 10),
        SoulRingSlot(6, "第七魂环", 15),  SoulRingSlot(7, "第八魂环", 15),
        SoulRingSlot(8, "第九魂环", 20)
    )

    fun get(index: Int): SoulRingSlot? = all.getOrNull(index)

    fun canEquip(slotIndex: Int, combinedTier: Int): Boolean {
        val slot = get(slotIndex) ?: return false
        return combinedTier >= slot.minCombinedTier
    }
}

/** 魂环实例：年份×品质×年分数 三维度 */
data class SoulRingInstance(
    val yearOrdinal: Int,
    val qualityOrdinal: Int,
    val affixes: List<EquipAffixValue>,
    val skill: ActiveSkill? = null,
    val percentage: Int = 0          // 年分数 100~999(10.0%~99.9%)，在年份档位内的成熟度百分比
) {
    val combinedTier: Int get() = RingYear.combinedTier(yearOrdinal, qualityOrdinal)
    /** 魂环负荷值（用于吸收上限检测） */
    val load: Long get() = SoulRingSystem.calcRingLoad(yearOrdinal, qualityOrdinal, percentage)
    /** 等效年份（如 千年·精良(72%) ≈ 5896年） */
    val effectiveYears: Int get() = SoulRingSystem.calcEffectiveYears(yearOrdinal, qualityOrdinal, percentage)
    /** 向后兼容：接受合并层级(0-24)自动分解 */
    constructor(combinedTier: Int, affixes: List<EquipAffixValue>, skill: ActiveSkill? = null) :
        this(combinedTier / 5, combinedTier % 5, affixes, skill, 0)
    /** 向后兼容：仅合并层级+词缀(无技能) */
    constructor(combinedTier: Int, affixes: List<EquipAffixValue>) :
        this(combinedTier / 5, combinedTier % 5, affixes, null, 0)
    /** 三维度构造（含年分数） */
    constructor(yearOrdinal: Int, qualityOrdinal: Int, percentage: Int, affixes: List<EquipAffixValue>, skill: ActiveSkill?) :
        this(yearOrdinal, qualityOrdinal, affixes, skill, percentage)
}

// ============ 魂环系统·三维度算法（年分数+负荷+根骨+吸收容量） ============
object SoulRingSystem {
    // 年份档位基础值：直接对应年份范围
    // 百年: 100-999, 千年: 1000-9999, 万年: 10000-99999...
    private val yearBaseValues = intArrayOf(100, 1000, 10000, 100000, 1000000)

    /** 计算魂环负荷值（直接等于等效年份） */
    fun calcRingLoad(yearOrdinal: Int, qualityOrdinal: Int, percentage: Int): Long {
        return calcEffectiveYears(yearOrdinal, qualityOrdinal, percentage).toLong()
    }

    /** 计算等效年份（核心公式：年份 = 下一档位基础值 × 成熟度比例 × 品质系数）
     *  品质系数: 劣等0.6 普通0.7 精良0.8 优秀0.9 完美1.0
     *  例如：千年·精良(72%) = 10000×0.72×0.8 = 5760年 */
    fun calcEffectiveYears(yearOrdinal: Int, qualityOrdinal: Int, percentage: Int): Int {
        val y = yearOrdinal.coerceIn(0, 4)
        val q = qualityOrdinal.coerceIn(0, 4)
        val nextBaseYear = if (y < 4) yearBaseValues[y + 1] else yearBaseValues[y] * 10

        // percentage直接表示百分比（100-999表示10.0%-99.9%）
        // 例如：percentage=100 → 10.0% → 百年档位 → 1000×10% = 100年
        //      percentage=798 → 79.8% → 百年档位 → 1000×79.8% = 798年
        val positionRatio = percentage.toDouble().coerceIn(0.0, 999.0) / 1000.0  // 0.000 - 0.999

        // 品质系数：品质越高等效年份越高（劣等0.6倍 → 完美1.0倍）
        val qualityMult = 0.6 + q * 0.1

        // 等效年份 = 下一档位基础值 × 位置比例 × 品质系数
        return (nextBaseYear * positionRatio * qualityMult).toInt().coerceAtLeast(1)
    }

    /** 计算玩家根骨值（基于综合属性，不含转生加成） */
    fun calcRootBone(maxHp: Long, atk: Int, matk: Int, pdef: Int, mdef: Int): Double {
        // 核心: 攻击力占主导，防御次之，血量辅助
        return atk * 3.0 + matk * 3.0 + pdef * 2.0 + mdef * 2.0 + maxHp / 100.0
    }

    /** 从PlayerAttributes计算根骨 */
    fun calcRootBoneFromAttr(attr: PlayerAttributes): Double =
        calcRootBone(attr.maxHp, attr.atk, attr.matk, attr.pdef, attr.mdef)

    /** 计算吸收容量 = 根骨 × 6 */
    fun calcAbsorptionCapacity(rootBone: Double): Long =
        (rootBone * 6).toLong().coerceAtLeast(100)

    /** 生成魂环的随机成熟度（10%-99.9%，避免极端值） */
    fun randomPercentage(): Int {
        // 生成100-999的整数，表示10.0%-99.9%
        return Random.nextInt(100, 1000)
    }

    /** 魂环三维度全名：劣等·百年(10.0%) */
    fun fullDisplayName(yearOrdinal: Int, qualityOrdinal: Int, percentage: Int): String {
        val yearName = RingYear.entries[yearOrdinal.coerceIn(0, 4)].displayName
        val qualName = RingQuality.entries[qualityOrdinal.coerceIn(0, 4)].displayName
        // 显示为：品质·年份(成熟度%)
        val percentStr = "${percentage / 10}.${percentage % 10}%"
        return "${qualName}·${yearName}(${percentStr})"
    }
}

object SoulRingGenerator {
    fun generateRandomAffixes(combinedTier: Int): List<EquipAffixValue> {
        val count = RingQuality.affixCount(combinedTier)
        val bv = RingQuality.baseValue(combinedTier)
        val baseValue = Random.nextInt((bv * 0.7).toInt(), (bv * 1.3).toInt() + 1).coerceAtLeast(3)
        val affixes = mutableListOf<EquipAffixValue>()
        val usedTypes = mutableSetOf<EquipAffix>()
        repeat(count) {
            val type = EquipAffix.entries.filter { it !in usedTypes }.random()
            usedTypes.add(type)
            val variance = Random.nextInt(-baseValue / 4, baseValue / 4 + 1)
            val scaled = ((baseValue + variance) * type.valueMultiplier).toInt().coerceAtLeast(1)
            affixes.add(EquipAffixValue(type, scaled))
        }
        return affixes
    }
    fun splitTier(combinedTier: Int): Pair<Int, Int> {
        val t = combinedTier.coerceIn(0, 24)
        return Pair(t / RingQuality.size, t % RingQuality.size)
    }
}

// ============ 魂骨系统 ============
enum class BoneType(val displayName: String) {
    HEAD("头骨"), LEFT_ARM("左臂骨"), RIGHT_ARM("右臂骨"),
    TORSO("躯干骨"), LEFT_LEG("左腿骨"), RIGHT_LEG("右腿骨")
}

enum class BoneYear(val displayName: String, val costBase: Long, val colorHex: Long = 0xFFCCCCCC) {
    HUNDRED("百年", 8000, 0xFFB0BEC5),
    THOUSAND("千年", 35000, 0xFF4CAF50),
    TEN_THOUSAND("万年", 150000, 0xFF2196F3),
    HUNDRED_THOUSAND("十万年", 600000, 0xFF9C27B0),
    MILLION("百万年", 3000000, 0xFFFF9800);

    val colorInt: Int get() = colorHex.toInt()

    companion object {
        val size = entries.size
        fun fromCombinedTier(tier: Int): BoneYear = entries[tier / 5]
        fun combinedTier(yearOrdinal: Int, qualityOrdinal: Int): Int = yearOrdinal * 5 + qualityOrdinal
        fun yearOf(tier: Int): Int = tier / 5
        fun qualityOf(tier: Int): Int = tier % 5
        fun cost(combinedTier: Int): Long = fromCombinedTier(combinedTier).costBase
    }
}

/** 魂骨品质：年份内的细分档位（副层级） */
enum class BoneRarity(val displayName: String, val skillLevel: Int) {
    INFERIOR("劣等", 1),
    NORMAL("普通", 2),
    FINE("精良", 3),
    EXCELLENT("优秀", 4),
    PERFECT("完美", 5);

    /** 向后兼容：装备魂骨费用 */
    val cost: Long get() = when (this) {
        INFERIOR -> 3000L
        NORMAL -> 10000L
        FINE -> 35000L
        EXCELLENT -> 120000L
        PERFECT -> 500000L
    }

    companion object {
        val size = entries.size
        fun skillMultiplier(tier: Int): Double = RingQuality.skillMultiplier(tier)
        fun statMultiplier(tier: Int): Double = RingQuality.statMultiplier(tier)
        fun affixCount(tier: Int): Int = RingQuality.affixCount(tier)
        fun baseValue(tier: Int): Int = RingQuality.baseValue(tier)
        fun sellPrice(tier: Int): Long {
            val y = BoneYear.yearOf(tier)
            val q = BoneYear.qualityOf(tier)
            return (BoneYear.entries[y].costBase * (1.0 + q * 0.6)).toLong()
        }
        fun fullName(year: Int, quality: Int): String =
            "${BoneYear.entries[year].displayName}·${entries[quality].displayName}魂骨"
        /** V5兼容：基于合并层级显示全名 */
        fun fullDisplayName(combinedTier: Int): String {
            val y = BoneYear.yearOf(combinedTier)
            val q = BoneYear.qualityOf(combinedTier)
            return fullName(y, q)
        }
        fun yearColorInt(year: Int): Int = BoneYear.entries[year].colorInt
    }
}

data class SoulBoneInstance(
    val yearOrdinal: Int,
    val rarityOrdinal: Int,
    val affixes: List<EquipAffixValue>,
    val enhanceLevel: Int = 0,
    val passiveSkill: PassiveSkill? = null
) {
    val combinedTier: Int get() = BoneYear.combinedTier(yearOrdinal, rarityOrdinal)
    /** 向后兼容：合并层级+词缀 */
    constructor(combinedTier: Int, affixes: List<EquipAffixValue>) :
        this(combinedTier / 5, combinedTier % 5, affixes, 0, null)
    /** 向后兼容：合并层级+词缀+强化+被动 */
    constructor(combinedTier: Int, affixes: List<EquipAffixValue>, enhanceLevel: Int, passiveSkill: PassiveSkill?) :
        this(combinedTier / 5, combinedTier % 5, affixes, enhanceLevel, passiveSkill)
}

object SoulBoneGenerator {
    fun generateRandomAffix(combinedTier: Int): List<EquipAffixValue> {
        val count = BoneRarity.affixCount(combinedTier)
        val bv = BoneRarity.baseValue(combinedTier)
        val baseValue = Random.nextInt((bv * 0.7).toInt(), (bv * 1.3).toInt() + 1).coerceAtLeast(5)
        val affixes = mutableListOf<EquipAffixValue>()
        val usedTypes = mutableSetOf<EquipAffix>()
        repeat(count) {
            val type = EquipAffix.entries.filter { it !in usedTypes }.random()
            usedTypes.add(type)
            val variance = Random.nextInt(-baseValue / 4, baseValue / 4 + 1)
            val scaled = ((baseValue + variance) * type.valueMultiplier).toInt().coerceAtLeast(1)
            affixes.add(EquipAffixValue(type, scaled))
        }
        return affixes
    }
}

// ============ 怪物词缀系统 ============
enum class MonsterAffix(val displayName: String, val desc: String, val colorHex: Long, val dropMult: Double) {
    GIANT("巨大的", "+50%血量 +10%双防", 0xFFFF9800, 1.5),
    TOUGH("坚硬的", "+100%双防", 0xFF9E9E9E, 1.3),
    FRAGILE("脆弱的", "-50%全属性", 0xFF4CAF50, 0.6),
    SWIFT("快速的", "+30%攻击", 0xFF2196F3, 1.3),
    BURNING("燃烧的", "+50%魔攻", 0xFFFF5722, 1.4),
    BERSERK("狂暴的", "+50%攻击 -30%双防", 0xFFFF1744, 1.5),
    SHADOW("暗影的", "+20%暴击 +30%爆伤", 0xFF9C27B0, 1.4),
    HOLY("神圣的", "+30%全属性", 0xFFFFD700, 2.0),
    REGENERATING("复生的", "每回合恢复5%HP", 0xFF4CAF50, 1.3),
    TUTOR("新手导师", "+10%全属性", 0xFF66BB6A, 1.1),
    BOSS("领主", "大幅提升全属性", 0xFFFF1744, 5.0);
    val colorInt: Int get() = colorHex.toInt()
}

// ============ 怪物系统 ============
data class Monster(
    val name: String, val affixes: List<MonsterAffix>,
    val maxHp: Long, var hp: Long, val atk: Int, val matk: Int,
    val critRate: Int, val critDmg: Int, val pdef: Int, val mdef: Int,
    val expReward: Long, val goldReward: Long, val canDropEquip: Boolean = false
)

// ============ 地图系统 ============
data class MapDef(
    val id: Int, val name: String, val description: String,
    val unlockLevel: Int, val unlockCost: Long, val monsterNamePrefix: String,
    val baseStats: MapStats, val floors: Int = 100,
    val effect: MapEffect = MapEffect("无特殊效果"),
    val dropInfo: MapDropInfo? = null  // 新增：地图掉落信息
)

/** 地图掉落信息 */
data class MapDropInfo(
    val ringYearRange: Pair<Int, Int>,  // 魂环年份范围（RingYear ordinal）
    val ringQualityChance: Map<Int, Float>,  // 品质概率 {qualityOrdinal: chance}
    val boneDropChance: Float,  // 魂骨掉落几率
    val bossCoinMin: Long,  // Boss币最小值
    val bossCoinMax: Long,  // Boss币最大值
    val goldMult: Float = 1.0f,  // 金币倍率
    val expMult: Float = 1.0f  // 经验倍率
) {
    fun getRingYearDisplayName(): String {
        val (min, max) = ringYearRange
        if (min == max) return RingYear.entries[min].displayName
        return "${RingYear.entries[min].displayName}~${RingYear.entries[max].displayName}"
    }
}
data class MapStats(
    val hp: Long = 100, val atk: Int = 10, val matk: Int = 5,
    val pdef: Int = 5, val mdef: Int = 3, val critRate: Int = 5,
    val critDmg: Int = 150, val expReward: Long = 10, val goldReward: Long = 5
)
data class MapEffect(
    val description: String, val monsterHpMult: Double = 1.0,
    val monsterAtkMult: Double = 1.0, val monsterDefMult: Double = 1.0,
    val goldMult: Double = 1.0, val expMult: Double = 1.0
)

object MapData {
    val all = listOf(
        MapDef(0, "圣魂村", "唐三的故乡，低级魂兽出没", 1, 0, "野",
            MapStats(200, 15, 8, 7, 4, 5, 150, 20, 30),
            effect = MapEffect("安宁之地，无特殊效果"),
            dropInfo = MapDropInfo(
                ringYearRange = 0 to 0,  // 百年
                ringQualityChance = mapOf(0 to 0.6f, 1 to 0.3f, 2 to 0.1f),  // 劣等60%, 普通30%, 精良10%
                boneDropChance = 0.03f,  // 3%掉魂骨
                bossCoinMin = 10, bossCoinMax = 20,
                goldMult = 1.0f, expMult = 1.0f
            )),
        MapDef(1, "诺丁城外", "学院周边，中级魂兽", 10, 2000, "凶",
            MapStats(500, 33, 18, 13, 10, 6, 155, 60, 96),
            effect = MapEffect("富饶之地，金币掉落+20%", goldMult = 1.2),
            dropInfo = MapDropInfo(
                ringYearRange = 0 to 1,  // 百年~千年
                ringQualityChance = mapOf(0 to 0.4f, 1 to 0.4f, 2 to 0.15f, 3 to 0.05f),
                boneDropChance = 0.05f,
                bossCoinMin = 15, bossCoinMax = 30,
                goldMult = 1.2f, expMult = 1.0f
            )),
        MapDef(2, "星斗外围", "星斗大森林边缘地带", 25, 15000, "狂暴",
            MapStats(1200, 75, 40, 25, 20, 7, 160, 140, 210),
            effect = MapEffect("魂兽活跃，怪物攻击+15%", monsterAtkMult = 1.15),
            dropInfo = MapDropInfo(
                ringYearRange = 1 to 2,  // 千年~万年（✅解决20级无法获取万年魂环问题）
                ringQualityChance = mapOf(1 to 0.3f, 2 to 0.4f, 3 to 0.2f, 4 to 0.1f),
                boneDropChance = 0.08f,
                bossCoinMin = 20, bossCoinMax = 40,  // ✅降低BOSS币产出
                goldMult = 1.0f, expMult = 1.2f
            )),
        MapDef(3, "落日森林", "危险的中级魂兽区域", 45, 80000, "剧毒",
            MapStats(3000, 170, 90, 50, 40, 8, 165, 320, 480),
            effect = MapEffect("毒雾弥漫，每回合受毒伤"),
            dropInfo = MapDropInfo(
                ringYearRange = 2 to 2,  // 万年
                ringQualityChance = mapOf(1 to 0.2f, 2 to 0.4f, 3 to 0.3f, 4 to 0.1f),
                boneDropChance = 0.10f,
                bossCoinMin = 25, bossCoinMax = 50,
                goldMult = 1.0f, expMult = 1.3f
            )),
        MapDef(4, "极北之地", "冰天雪地的凶险区域", 70, 400000, "冰霜",
            MapStats(8000, 400, 220, 100, 80, 9, 170, 800, 1150),
            effect = MapEffect("极寒之地，怪物HP+30%", monsterHpMult = 1.3),
            dropInfo = MapDropInfo(
                ringYearRange = 2 to 3,  // 万年~十万年
                ringQualityChance = mapOf(2 to 0.2f, 3 to 0.5f, 4 to 0.3f),
                boneDropChance = 0.12f,
                bossCoinMin = 30, bossCoinMax = 60,
                goldMult = 1.0f, expMult = 1.5f
            )),
        MapDef(5, "海神岛", "深海中的神秘岛屿", 100, 2000000, "深海",
            MapStats(22000, 950, 550, 220, 180, 10, 175, 2200, 3120),
            effect = MapEffect("深海压力，怪物双防+25%", monsterDefMult = 1.25),
            dropInfo = MapDropInfo(
                ringYearRange = 3 to 3,  // 十万年
                ringQualityChance = mapOf(2 to 0.15f, 3 to 0.5f, 4 to 0.35f),
                boneDropChance = 0.15f,
                bossCoinMin = 35, bossCoinMax = 70,
                goldMult = 1.0f, expMult = 1.8f
            )),
        MapDef(6, "杀戮之都外域", "修罗神的试炼之地", 140, 10000000, "杀戮",
            MapStats(60000, 2300, 1400, 500, 420, 12, 180, 6000, 8350),
            effect = MapEffect("杀戮气息，玩家攻击+15%但怪暴击+10%", monsterAtkMult = 1.10),
            dropInfo = MapDropInfo(
                ringYearRange = 3 to 4,  // 十万年~百万年
                ringQualityChance = mapOf(3 to 0.4f, 4 to 0.6f),
                boneDropChance = 0.18f,
                bossCoinMin = 40, bossCoinMax = 80,
                goldMult = 1.0f, expMult = 2.0f
            )),
        MapDef(7, "神界废墟", "众神陨落之地", 190, 50000000, "神级",
            MapStats(180000, 6000, 3800, 1200, 1000, 15, 190, 16000, 23500),
            effect = MapEffect("神之领域，所有怪物全属性+20%", monsterHpMult = 1.2, monsterAtkMult = 1.2, monsterDefMult = 1.2, goldMult = 1.5, expMult = 1.5),
            dropInfo = MapDropInfo(
                ringYearRange = 4 to 4,  // 百万年
                ringQualityChance = mapOf(3 to 0.3f, 4 to 0.7f),
                boneDropChance = 0.20f,
                bossCoinMin = 50, bossCoinMax = 100,
                goldMult = 1.5f, expMult = 2.5f
            ))
    )
    fun getMap(mapId: Int): MapDef? = all.getOrNull(mapId)
    fun getMaxMapId(): Int = all.size - 1
}

// ============ 魂骨套装 ============
data class SoulBoneSet(
    val name: String, val icon: String,
    val requiredSlots: List<BoneType>,
    val tier2Atk: Int = 0, val tier2Def: Int = 0, val tier2Hp: Long = 0, val tier2Crit: Int = 0,
    val tier2Desc: String = "",
    val fullAtk: Int = 0, val fullDef: Int = 0, val fullHp: Long = 0, val fullCrit: Int = 0,
    val fullDesc: String = ""
)
data class ActiveSetInfo(val set: SoulBoneSet, val matchedCount: Int, val totalCount: Int) {
    val hasTier2: Boolean get() = matchedCount >= 2
    val isFullSet: Boolean get() = matchedCount == totalCount
}

object SoulBoneSetData {
    val sets = listOf(
        SoulBoneSet("基础套", "⚪",
            listOf(BoneType.HEAD, BoneType.LEFT_ARM, BoneType.RIGHT_ARM, BoneType.TORSO, BoneType.LEFT_LEG, BoneType.RIGHT_LEG),
            tier2Atk = 15, tier2Def = 10, tier2Hp = 100, tier2Desc = "物攻+15 双防+10 HP+100",
            fullAtk = 40, fullDef = 30, fullHp = 250, fullCrit = 3, fullDesc = "物攻+40 双防+30 HP+250 暴击+3%"),
        SoulBoneSet("精英套", "🔵",
            listOf(BoneType.TORSO, BoneType.LEFT_LEG, BoneType.RIGHT_LEG),
            tier2Def = 20, tier2Hp = 250, tier2Desc = "双防+20 生命+250",
            fullDef = 30, fullHp = 400, fullDesc = "双防+30 生命+400"),
        SoulBoneSet("神装套", "👑",
            listOf(BoneType.HEAD, BoneType.LEFT_ARM, BoneType.RIGHT_ARM, BoneType.TORSO, BoneType.LEFT_LEG, BoneType.RIGHT_LEG),
            tier2Atk = 60, tier2Def = 30, tier2Hp = 300, tier2Desc = "物攻+60 双防+30 生命+300",
            fullAtk = 150, fullDef = 80, fullHp = 1000, fullCrit = 10, fullDesc = "物攻+150 双防+80 生命+1000 暴击+10%")
    )
    fun getActiveSets(equippedBones: Map<Int, SoulBoneInstance>): List<ActiveSetInfo> {
        val equippedTypes = equippedBones.keys.map { BoneType.entries[it] }.toSet()
        return sets.mapNotNull { set ->
            val matched = set.requiredSlots.count { it in equippedTypes }
            if (matched >= 2) ActiveSetInfo(set, matched, set.requiredSlots.size) else null
        }
    }
}

// ============ 怪物图鉴 ============
object CodexData {
    val milestones = listOf(
        100L to "初出茅庐", 500L to "猎魂勇士", 2000L to "百战精英",
        5000L to "屠戮者", 15000L to "魂兽克星", 50000L to "传奇猎手",
        150000L to "万人斩", 500000L to "征服者"
    )
    fun getTitle(kills: Long): String {
        var title = "新手猎手"
        for ((threshold, t) in milestones) { if (kills >= threshold) title = t }
        return title
    }
    fun nextMilestone(kills: Long): Pair<Long, String>? = milestones.firstOrNull { it.first > kills }
    fun codexAtkBonus(kills: Long): Int {
        var bonus = 0
        for ((threshold, _) in milestones) { if (kills >= threshold) bonus += (threshold / 20).toInt() }
        return bonus
    }
}

// ============ 战斗结果 ============
data class BattleResult(
    val won: Boolean, val rounds: Int, val monsterName: String,
    val monsterAffixes: List<MonsterAffix>, val expGained: Long, val goldGained: Long,
    val playerDmgDealt: Long, val monsterDmgDealt: Long,
    val isCritKill: Boolean = false, val dropItem: String? = null, val skillUsed: String? = null
)

// ============ 掉落背包物品 ============
data class DroppedRing(
    val yearOrdinal: Int,
    val qualityOrdinal: Int,
    val affixes: List<EquipAffixValue>,
    val skill: ActiveSkill? = null,
    val percentage: Int = 0          // 年分数 100~999(10.0%~99.9%)
) {
    val combinedTier: Int get() = RingYear.combinedTier(yearOrdinal, qualityOrdinal)
    /** 向后兼容：接受合并层级(0-24)自动分解 */
    constructor(combinedTier: Int, affixes: List<EquipAffixValue>, skill: ActiveSkill? = null) :
        this(combinedTier / 5, combinedTier % 5, affixes, skill, 0)
    /** 三维度构造 */
    constructor(yearOrdinal: Int, qualityOrdinal: Int, percentage: Int, affixes: List<EquipAffixValue>, skill: ActiveSkill?) :
        this(yearOrdinal, qualityOrdinal, affixes, skill, percentage)
}
data class DroppedBone(val boneTypeOrdinal: Int, val yearOrdinal: Int, val rarityOrdinal: Int, val affixes: List<EquipAffixValue>, val passiveSkill: PassiveSkill? = null) {
    val combinedTier: Int get() = BoneYear.combinedTier(yearOrdinal, rarityOrdinal)
    /** 向后兼容：接受合并层级(0-24)自动分解为年份+品质 */
    constructor(boneTypeOrdinal: Int, combinedTier: Int, affixes: List<EquipAffixValue>, passiveSkill: PassiveSkill? = null) :
        this(boneTypeOrdinal, combinedTier / 5, combinedTier % 5, affixes, passiveSkill)
}

// ============ 杀戮之都 ============
data class TowerSegment(
    val name: String, val theme: String, val bossName: String, val bossDesc: String,
    val startFloor: Int, val endFloor: Int
)

object TowerData {
    const val MAX_FLOOR = 100; const val SWEEP_POWER_MULTIPLIER = 3.0
    val segments = listOf(
        TowerSegment("鲜血荒原", "杀戮之都外围", "血屠夫", "挥舞巨斧的狂爆屠夫", 1, 10),
        TowerSegment("岩浆炼狱", "地狱回廊", "熔岩巨兽", "全身覆盖岩浆的巨型怪兽", 11, 20),
        TowerSegment("修罗试炼场", "修罗战场", "修罗骑士", "身披黑甲的不死亡灵骑士", 21, 35),
        TowerSegment("无尽深渊", "黑暗深渊", "深渊领主", "掌控黑暗力量的深渊领主", 36, 50),
        TowerSegment("血月祭坛", "血月领域", "血月祭司", "在血月下获得无尽力量的大祭司", 51, 65),
        TowerSegment("杀戮圣殿", "杀戮神殿", "杀戮天使", "被堕落之力腐蚀的六翼天使", 66, 80),
        TowerSegment("修罗王域", "修罗王座", "修罗将军", "修罗神座下第一战将", 81, 95),
        TowerSegment("终焉之地", "杀戮之巅", "杀戮之王·修罗神", "杀戮之都的最终统治者，修罗神化身", 96, 100)
    )
    fun getSegment(floor: Int): TowerSegment = segments.firstOrNull { floor in it.startFloor..it.endFloor } ?: segments.last()
    fun isBossFloor(floor: Int): Boolean = floor % 10 == 0
    fun isRestFloor(floor: Int): Boolean = floor % 5 == 0 && !isBossFloor(floor)
    fun towerHpCostPerFloor(floor: Int): Int = when { floor <= 20 -> 8; floor <= 40 -> 9; floor <= 60 -> 10; floor <= 80 -> 12; else -> 15 }
    fun floorHpMultiplier(floor: Int): Double = 1.0 + floor * 0.08
    fun floorAtkMultiplier(floor: Int): Double = 1.0 + floor * 0.06
    fun monsterPower(floor: Int): Long { val base = 200L + floor * 80L; return (base * (1.0 + floor * 0.05)).toLong() }
    fun bossGoldBonus(floor: Int): Long = when { floor <= 20 -> 500L + floor * 50L; floor <= 50 -> 2000L + floor * 120L; floor <= 80 -> 8000L + floor * 250L; else -> 30000L + floor * 500L }
    fun normalReward(floor: Int): Long = (100.0 * 1.08.pow(floor.toDouble())).toLong()
    fun bossBoneReward(floor: Int): Int = when { floor >= 90 -> 22; floor >= 70 -> 17; floor >= 40 -> 12; floor >= 20 -> 7; else -> 2 }
    fun bossAtkBonus(floor: Int): Int = floor * 5
}

// ============ 每日副本（V5）============
object DungeonData {
    const val MIN_PRESTIGE = 1; const val DAILY_RESET_MS = 86400000L
    data class DungeonTier(
        val name: String, val difficulty: String, val bossName: String, val bossDesc: String,
        val hpMult: Double, val atkMult: Double, val goldReward: Long, val killingIntentReward: Int,
        val boneTier: Int, val ringTier: Int
    )
    val tiers = listOf(
        DungeonTier("魂兽森林", "简单", "千年魂兽·泰坦巨猿", "力量的化身，皮糙肉厚", 3.0, 1.8, 5000, 10, boneTier = 7, ringTier = 7),
        DungeonTier("暗影峡谷", "普通", "暗影君王·鬼魅", "来去无踪，一击致命", 5.0, 2.5, 15000, 25, boneTier = 12, ringTier = 12),
        DungeonTier("龙墓禁地", "困难", "远古龙皇·赤王", "龙息焚天，万法不侵", 8.0, 3.5, 40000, 50, boneTier = 17, ringTier = 17),
        DungeonTier("神之遗迹", "噩梦", "堕落天使·路西法", "天使与恶魔的双面化身", 12.0, 5.0, 100000, 100, boneTier = 22, ringTier = 22),
        DungeonTier("深渊之门", "地狱", "深渊之主·阿萨谢尔", "凝视深渊者，终将被吞噬", 20.0, 8.0, 300000, 200, boneTier = 24, ringTier = 24)
    )
    fun getMaxTier(prestigeCount: Int): Int = (prestigeCount - 1).coerceIn(0, tiers.size - 1)
}

// ============ 天赋树 ============
enum class TalentBranch(val displayName: String, val icon: String, val desc: String, val maxLevel: Int = 3) {
    WAR_GOD("战神之道", "⚔️", "提升攻击与暴击"),
    SOUL_MASTER("魂师之道", "💠", "提升装备词缀效果"),
    WEALTH("财富之道", "🪙", "提升金币使用效率"),
    DIVINE("神祇之道", "🛡️", "提升生命与减伤");
    companion object {
        fun effectDescription(branch: TalentBranch, level: Int): String = when (branch) {
            WAR_GOD -> when (level) { 1 -> "攻击+6% 防御+3%"; 2 -> "攻击+12% 防御+6%"; 3 -> "攻击+18% 防御+9% 暴击+5%"; else -> "" }
            SOUL_MASTER -> when (level) { 1 -> "词缀效果+8%"; 2 -> "词缀效果+16%"; 3 -> "词缀效果+24%"; else -> "" }
            WEALTH -> when (level) { 1 -> "背包扩容费用-20%"; 2 -> "背包扩容费用-40%"; 3 -> "背包扩容费用-60% 免费扩容1次"; else -> "" }
            DIVINE -> when (level) { 1 -> "生命+10%"; 2 -> "生命+20%"; 3 -> "生命+30% 减伤+5%"; else -> "" }
        }
        fun retentionDescription(branch: TalentBranch, level: Int): String = effectDescription(branch, level)
        fun nextLevelDescription(branch: TalentBranch, level: Int): String = effectDescription(branch, level + 1)
    }
}

// ============ 成就系统 ============
enum class AchievementCategory(val displayName: String) {
    CULTIVATION("修炼"), SOUL_RING("魂环"), BATTLE("战斗"), TOWER("爬塔"), PRESTIGE("转生")
}
data class AchievementRewards(
    val hp: Long = 0, val atk: Int = 0, val matk: Int = 0,
    val pdef: Int = 0, val mdef: Int = 0, val critRate: Int = 0, val critDmg: Int = 0
) {
    fun description(): String {
        val parts = mutableListOf<String>()
        if (hp > 0) parts.add("HP+$hp"); if (atk > 0) parts.add("物攻+$atk")
        if (matk > 0) parts.add("魔攻+$matk"); if (pdef > 0) parts.add("物防+$pdef")
        if (mdef > 0) parts.add("魔防+$mdef"); if (critRate > 0) parts.add("暴击+${critRate}%")
        if (critDmg > 0) parts.add("爆伤+${critDmg}%")
        return parts.joinToString(" | ")
    }
}
data class AchievementDef(
    val id: String, val name: String, val description: String,
    val category: AchievementCategory,
    val condition: (GameSnapshot) -> Boolean, val rewards: AchievementRewards
)
data class GameSnapshot(
    val level: Int, val soulRings: Int, val totalBattleWins: Long,
    val towerFloor: Int, val prestigeCount: Int, val totalGold: Long
)
object AchievementDefs {
    val all = listOf(
        AchievementDef("cult_10", "初出茅庐", "达到10级", AchievementCategory.CULTIVATION, { it.level >= 10 }, AchievementRewards(hp = 100, atk = 5)),
        AchievementDef("cult_30", "魂尊之路", "达到30级", AchievementCategory.CULTIVATION, { it.level >= 30 }, AchievementRewards(hp = 300, atk = 15, pdef = 5, mdef = 5)),
        AchievementDef("cult_50", "魂宗威名", "达到50级", AchievementCategory.CULTIVATION, { it.level >= 50 }, AchievementRewards(hp = 600, atk = 30, pdef = 10, mdef = 10, critRate = 2)),
        AchievementDef("cult_80", "封号斗罗", "达到80级", AchievementCategory.CULTIVATION, { it.level >= 80 }, AchievementRewards(hp = 1500, atk = 60, pdef = 20, mdef = 20, critRate = 5)),
        AchievementDef("cult_100", "极限斗罗", "达到100级", AchievementCategory.CULTIVATION, { it.level >= 100 }, AchievementRewards(hp = 3000, atk = 120, pdef = 40, mdef = 40, critRate = 8, critDmg = 15)),
        AchievementDef("cult_150", "神王降临", "达到150级", AchievementCategory.CULTIVATION, { it.level >= 150 }, AchievementRewards(hp = 8000, atk = 300, pdef = 80, mdef = 80, critRate = 12, critDmg = 30)),
        AchievementDef("ring_1", "初获魂环", "获得第一个魂环", AchievementCategory.SOUL_RING, { it.soulRings >= 1 }, AchievementRewards(matk = 10, critRate = 1)),
        AchievementDef("ring_3", "三环齐聚", "获得3个魂环", AchievementCategory.SOUL_RING, { it.soulRings >= 3 }, AchievementRewards(matk = 35, critRate = 3, critDmg = 10)),
        AchievementDef("ring_5", "五环辉煌", "获得5个魂环", AchievementCategory.SOUL_RING, { it.soulRings >= 5 }, AchievementRewards(matk = 70, critRate = 5, critDmg = 20, hp = 300)),
        AchievementDef("ring_9", "九环圆满", "获得9个魂环", AchievementCategory.SOUL_RING, { it.soulRings >= 9 }, AchievementRewards(hp = 500, matk = 200, critRate = 10, critDmg = 40)),
        AchievementDef("battle_10", "十战勇士", "赢得10场战斗", AchievementCategory.BATTLE, { it.totalBattleWins >= 10 }, AchievementRewards(hp = 100, atk = 10)),
        AchievementDef("battle_50", "百战老兵", "赢得50场战斗", AchievementCategory.BATTLE, { it.totalBattleWins >= 50 }, AchievementRewards(hp = 400, atk = 30, pdef = 5)),
        AchievementDef("battle_200", "千战精英", "赢得200场战斗", AchievementCategory.BATTLE, { it.totalBattleWins >= 200 }, AchievementRewards(hp = 1000, atk = 80, pdef = 15, mdef = 15)),
        AchievementDef("battle_1000", "万战传说", "赢得1000场战斗", AchievementCategory.BATTLE, { it.totalBattleWins >= 1000 }, AchievementRewards(hp = 3000, atk = 250, pdef = 50, mdef = 50, critRate = 5)),
        AchievementDef("tower_10", "塔十层", "通关杀戮之都第10层", AchievementCategory.TOWER, { it.towerFloor >= 10 }, AchievementRewards(hp = 200, atk = 15, matk = 10)),
        AchievementDef("tower_30", "塔三十层", "通关杀戮之都第30层", AchievementCategory.TOWER, { it.towerFloor >= 30 }, AchievementRewards(hp = 600, atk = 50, matk = 40, pdef = 10, mdef = 10, critRate = 3)),
        AchievementDef("tower_50", "塔五十层", "通关杀戮之都第50层", AchievementCategory.TOWER, { it.towerFloor >= 50 }, AchievementRewards(hp = 1500, atk = 120, matk = 100, pdef = 25, mdef = 25, critRate = 5, critDmg = 15)),
        AchievementDef("tower_100", "杀戮之王", "通关杀戮之都第100层", AchievementCategory.TOWER, { it.towerFloor >= 100 }, AchievementRewards(hp = 8000, atk = 500, matk = 400, pdef = 100, mdef = 100, critRate = 15, critDmg = 50)),
        AchievementDef("prestige_1", "初次转生", "完成第一次神位传承", AchievementCategory.PRESTIGE, { it.prestigeCount >= 1 }, AchievementRewards(hp = 500, pdef = 20, mdef = 20)),
        AchievementDef("prestige_3", "三生三世", "完成3次神位传承", AchievementCategory.PRESTIGE, { it.prestigeCount >= 3 }, AchievementRewards(hp = 2000, pdef = 60, mdef = 60, critDmg = 20)),
        AchievementDef("prestige_5", "五世轮回", "完成5次神位传承", AchievementCategory.PRESTIGE, { it.prestigeCount >= 5 }, AchievementRewards(hp = 5000, pdef = 150, mdef = 150, critRate = 5, critDmg = 50))
    )
}

// __MODELS_END__
