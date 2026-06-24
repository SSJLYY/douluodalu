package com.douluodalu.game.model

import kotlin.math.pow
import kotlin.random.Random

// ============ 品质/稀有度 ============
enum class Rarity(val displayName: String) {
    COMMON("普通"), UNCOMMON("精良"), RARE("稀有"),
    EPIC("史诗"), LEGENDARY("传说"), MYTHIC("神话")
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

// ============ 装备词缀系统 ============
enum class EquipAffix(val displayName: String, val valueMultiplier: Double) {
    HP("生命", 5.0), ATK("攻击", 1.0), MATK("魔攻", 1.0),
    CRIT_RATE("暴击", 0.06), CRIT_DMG("爆伤", 0.3), PDEF("物防", 0.5), MDEF("魔防", 0.5)
}

data class EquipAffixValue(val type: EquipAffix, val value: Int)

// ============ 魂环系统 ============
enum class RingYear(val displayName: String, val costBase: Int, val idx: Int) {
    HUNDRED("百年", 500, 0), THOUSAND("千年", 3000, 1),
    TEN_THOUSAND("万年", 15000, 2), HUNDRED_THOUSAND("十万年", 80000, 3),
    MILLION("百万年", 400000, 4);

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

enum class RingQuality(val displayName: String) {
    INFERIOR("劣等"), NORMAL("普通"), FINE("精良"), EXCELLENT("优秀"), PERFECT("完美");

    companion object {
        val size = entries.size
        fun skillMultiplier(tier: Int): Double {
            val q = RingYear.effectiveQuality(tier)
            return 1.0 + 0.5 * q * q
        }
        fun statMultiplier(tier: Int): Double {
            val q = RingYear.effectiveQuality(tier)
            return 1.0 + 0.18 * q * q
        }
        fun affixCount(tier: Int): Int {
            val y = RingYear.yearOf(tier)
            return when (y) { 0 -> 2; 1 -> 2; 2 -> 3; 3 -> 4; 4 -> 5; else -> 2 }
        }
        fun baseValue(tier: Int): Int {
            val q = RingYear.effectiveQuality(tier)
            val lo = q.toInt().coerceIn(0, 3)
            val hi = (lo + 1).coerceAtMost(4)
            val frac = q - lo
            val anchors = listOf(8, 18, 40, 85, 180)
            return (anchors[lo] + (anchors[hi] - anchors[lo]) * frac).toInt()
        }
        fun sellPrice(tier: Int): Long {
            val y = RingYear.yearOf(tier)
            val q = RingYear.qualityOf(tier)
            return (RingYear.entries[y].costBase * (1.0 + q * 0.6)).toLong()
        }
        fun fullName(year: Int, quality: Int): String =
            "${RingYear.entries[year].displayName}·${entries[quality].displayName}魂环"
        fun fullDisplayName(combinedTier: Int): String {
            val y = RingYear.yearOf(combinedTier)
            val q = RingYear.qualityOf(combinedTier)
            return fullName(y, q)
        }
        fun cost(combinedTier: Int, ownedCount: Int): Long {
            val y = RingYear.yearOf(combinedTier)
            val q = RingYear.qualityOf(combinedTier)
            return (RingYear.entries[y].costBase * (1.0 + q * 0.5) * (1 + ownedCount * 0.3)).toLong()
        }
    }
}

// ============ 魂骨系统 ============
enum class BoneType(val displayName: String) {
    HEAD("头骨"), LEFT_ARM("左臂骨"), RIGHT_ARM("右臂骨"),
    TORSO("躯干骨"), LEFT_LEG("左腿骨"), RIGHT_LEG("右腿骨")
}

enum class BoneYear(val displayName: String, val costBase: Long) {
    HUNDRED("百年", 8000), THOUSAND("千年", 35000),
    TEN_THOUSAND("万年", 150000), HUNDRED_THOUSAND("十万年", 600000),
    MILLION("百万年", 3000000);

    companion object {
        val size = entries.size
        fun fromCombinedTier(tier: Int): BoneYear = entries[tier / 5]
        fun combinedTier(yearOrdinal: Int, qualityOrdinal: Int): Int = yearOrdinal * 5 + qualityOrdinal
        fun yearOf(tier: Int): Int = tier / 5
        fun qualityOf(tier: Int): Int = tier % 5
        fun cost(combinedTier: Int): Long = fromCombinedTier(combinedTier).costBase
    }
}

enum class BoneRarity(val displayName: String, val skillLevel: Int) {
    INFERIOR("劣等", 1), NORMAL("普通", 2), FINE("精良", 3),
    EXCELLENT("优秀", 4), PERFECT("完美", 5);

    companion object {
        val size = entries.size
        fun fullName(year: Int, quality: Int): String =
            "${BoneYear.entries[year].displayName}·${entries[quality].displayName}魂骨"
        fun fullDisplayName(combinedTier: Int): String {
            val y = BoneYear.yearOf(combinedTier)
            val q = BoneYear.qualityOf(combinedTier)
            return fullName(y, q)
        }
    }
}

// ============ 魂核系统 ============
enum class SoulCoreTier(val displayName: String) {
    FRACTURE("裂痕"), CHIPPED("碎裂"), NORMAL("普通"),
    RARE("稀有"), EPIC("史诗"), MYTHIC("神话"), DIVINE("神级")
}

// ============ 天赋系统 ============
enum class TalentBranch(val displayName: String, val maxLevel: Int = 3) {
    WAR_GOD("战神之道"), SOUL_MASTER("魂师之道"),
    WEALTH("财富之道"), DIVINE("神祇之道");

    companion object {
        fun effectDescription(branch: TalentBranch, level: Int): String = when (branch) {
            WAR_GOD -> when (level) { 1 -> "攻击+6% 防御+3%"; 2 -> "攻击+12% 防御+6%"; 3 -> "攻击+18% 防御+9% 暴击+5%"; else -> "" }
            SOUL_MASTER -> when (level) { 1 -> "词缀效果+8%"; 2 -> "词缀效果+16%"; 3 -> "词缀效果+24%"; else -> "" }
            WEALTH -> when (level) { 1 -> "背包扩容费用-20%"; 2 -> "背包扩容费用-40%"; 3 -> "背包扩容费用-60% 免费扩容1次"; else -> "" }
            DIVINE -> when (level) { 1 -> "生命+10%"; 2 -> "生命+20%"; 3 -> "生命+30% 减伤+5%"; else -> "" }
        }
    }
}

// ============ 商店系统 ============
data class ShopItem(
    val id: Long,
    val name: String,
    val description: String,
    val price: Long,
    val currencyType: String, // "GOLD", "BOSS_COIN"
    val itemType: String,
    val itemData: String,
    val stock: Int = -1, // -1 = 无限
    val requiresLevel: Int = 1
)

object BossShopData {
    val items = listOf(
        ShopItem(1, "万年魂环箱", "随机获得一个万年魂环", 50, "BOSS_COIN", "RING_BOX", "TEN_THOUSAND"),
        ShopItem(2, "十万年魂环箱", "随机获得一个十万年魂环", 200, "BOSS_COIN", "RING_BOX", "HUNDRED_THOUSAND"),
        ShopItem(3, "百万年魂环箱", "随机获得一个百万年魂环", 800, "BOSS_COIN", "RING_BOX", "MILLION"),
        ShopItem(4, "千年魂骨箱", "随机获得一个千年魂骨", 80, "BOSS_COIN", "BONE_BOX", "THOUSAND"),
        ShopItem(5, "万年魂骨箱", "随机获得一个万年魂骨", 300, "BOSS_COIN", "BONE_BOX", "TEN_THOUSAND"),
        ShopItem(6, "高级魂核", "随机获得一个高级魂核", 150, "BOSS_COIN", "CORE_BOX", "RARE"),
        ShopItem(7, "史诗魂核", "随机获得一个史诗魂核", 500, "BOSS_COIN", "CORE_BOX", "EPIC"),
        ShopItem(8, "金币袋(大)", "获得10000金币", 30, "BOSS_COIN", "GOLD_BAG", "10000"),
        ShopItem(9, "魂力精华", "获得5000魂力", 50, "BOSS_COIN", "SOUL_POWER", "5000"),
        ShopItem(10, "背包扩展券", "背包容量+5", 100, "BOSS_COIN", "BACKPACK_EXPAND", "5")
    )
}

object LimitedShopData {
    val items = listOf(
        ShopItem(1, "传说魂核", "必得一个传说级魂核", 2000, "BOSS_COIN", "CORE_BOX", "MYTHIC", stock = 1),
        ShopItem(2, "百万年魂骨箱", "随机获得一个百万年魂骨", 1500, "BOSS_COIN", "BONE_BOX", "MILLION", stock = 3),
        ShopItem(3, "神赐礼包", "包含大量稀有材料", 3000, "BOSS_COIN", "GIFT_PACK", "DIVINE", stock = 1, requiresLevel = 100)
    )
}

// ============ 怪物词缀系统 ============
enum class MonsterAffix(val displayName: String, val dropMult: Double) {
    GIANT("巨大的", 1.5), TOUGH("坚硬的", 1.3), FRAGILE("脆弱的", 0.6),
    SWIFT("快速的", 1.3), BURNING("燃烧的", 1.4), BERSERK("狂暴的", 1.5),
    SHADOW("暗影的", 1.4), HOLY("神圣的", 2.0), REGENERATING("复生的", 1.3),
    TUTOR("新手导师", 1.1), BOSS("领主", 5.0)
}

// ============ 地图系统 ============
data class MapDropInfo(
    val ringYearRange: Pair<Int, Int>,
    val ringQualityChance: Map<Int, Float>,
    val boneDropChance: Float,
    val bossCoinMin: Long,
    val bossCoinMax: Long,
    val goldMult: Float = 1.0f,
    val expMult: Float = 1.0f
)

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

data class MapDef(
    val id: Int, val name: String, val description: String,
    val unlockLevel: Int, val unlockCost: Long, val monsterNamePrefix: String,
    val baseStats: MapStats, val floors: Int = 100,
    val effect: MapEffect = MapEffect("无特殊效果"),
    val dropInfo: MapDropInfo? = null
)

object MapData {
    val all = listOf(
        MapDef(0, "圣魂村", "唐三的故乡，低级魂兽出没", 1, 0, "野",
            MapStats(200, 15, 8, 7, 4, 5, 150, 20, 30),
            effect = MapEffect("安宁之地，无特殊效果"),
            dropInfo = MapDropInfo(0 to 0, mapOf(0 to 0.6f, 1 to 0.3f, 2 to 0.1f), 0.03f, 10, 20)),
        MapDef(1, "诺丁城外", "学院周边，中级魂兽", 10, 2000, "凶",
            MapStats(500, 33, 18, 13, 10, 6, 155, 60, 96),
            effect = MapEffect("富饶之地，金币掉落+20%", goldMult = 1.2),
            dropInfo = MapDropInfo(0 to 1, mapOf(0 to 0.4f, 1 to 0.4f, 2 to 0.15f, 3 to 0.05f), 0.05f, 15, 30)),
        MapDef(2, "星斗外围", "星斗大森林边缘地带", 25, 15000, "狂暴",
            MapStats(1200, 75, 40, 25, 20, 7, 160, 140, 210),
            effect = MapEffect("魂兽活跃，怪物攻击+15%", monsterAtkMult = 1.15),
            dropInfo = MapDropInfo(1 to 2, mapOf(1 to 0.3f, 2 to 0.4f, 3 to 0.2f, 4 to 0.1f), 0.08f, 20, 40)),
        MapDef(3, "落日森林", "危险的中级魂兽区域", 45, 80000, "剧毒",
            MapStats(3000, 170, 90, 50, 40, 8, 165, 320, 480),
            effect = MapEffect("毒雾弥漫，每回合受毒伤"),
            dropInfo = MapDropInfo(2 to 2, mapOf(1 to 0.2f, 2 to 0.4f, 3 to 0.3f, 4 to 0.1f), 0.10f, 25, 50)),
        MapDef(4, "极北之地", "冰天雪地的凶险区域", 70, 400000, "冰霜",
            MapStats(8000, 400, 220, 100, 80, 9, 170, 800, 1150),
            effect = MapEffect("极寒之地，怪物HP+30%", monsterHpMult = 1.3),
            dropInfo = MapDropInfo(2 to 3, mapOf(2 to 0.2f, 3 to 0.5f, 4 to 0.3f), 0.12f, 30, 60)),
        MapDef(5, "海神岛", "深海中的神秘岛屿", 100, 2000000, "深海",
            MapStats(22000, 950, 550, 220, 180, 10, 175, 2200, 3120),
            effect = MapEffect("深海压力，怪物双防+25%", monsterDefMult = 1.25),
            dropInfo = MapDropInfo(3 to 3, mapOf(2 to 0.15f, 3 to 0.5f, 4 to 0.35f), 0.15f, 35, 70)),
        MapDef(6, "杀戮之都外域", "修罗神的试炼之地", 140, 10000000, "杀戮",
            MapStats(60000, 2300, 1400, 500, 420, 12, 180, 6000, 8350),
            effect = MapEffect("杀戮气息，玩家攻击+15%但怪暴击+10%", monsterAtkMult = 1.10),
            dropInfo = MapDropInfo(3 to 4, mapOf(3 to 0.4f, 4 to 0.6f), 0.18f, 40, 80)),
        MapDef(7, "神界废墟", "众神陨落之地", 190, 50000000, "神级",
            MapStats(180000, 6000, 3800, 1200, 1000, 15, 190, 16000, 23500),
            effect = MapEffect("神之领域，所有怪物全属性+20%", monsterHpMult = 1.2, monsterAtkMult = 1.2, monsterDefMult = 1.2, goldMult = 1.5, expMult = 1.5),
            dropInfo = MapDropInfo(4 to 4, mapOf(3 to 0.3f, 4 to 0.7f), 0.20f, 50, 100))
    )
    fun getMap(mapId: Int): MapDef? = all.getOrNull(mapId)
    fun getMaxMapId(): Int = all.size - 1
}

// ============ 杀戮之都 ============
data class TowerSegment(
    val name: String, val theme: String, val bossName: String, val bossDesc: String,
    val startFloor: Int, val endFloor: Int
)

object TowerData {
    const val MAX_FLOOR = 100
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
    fun floorHpMultiplier(floor: Int): Double = 1.0 + floor * 0.08
    fun floorAtkMultiplier(floor: Int): Double = 1.0 + floor * 0.06
    fun monsterPower(floor: Int): Long { val base = 200L + floor * 80L; return (base * (1.0 + floor * 0.05)).toLong() }
    fun bossGoldBonus(floor: Int): Long = when { floor <= 20 -> 500L + floor * 50L; floor <= 50 -> 2000L + floor * 120L; floor <= 80 -> 8000L + floor * 250L; else -> 30000L + floor * 500L }
    fun normalReward(floor: Int): Long = (100.0 * 1.08.pow(floor.toDouble())).toLong()
    fun bossBoneReward(floor: Int): Int = when { floor >= 90 -> 22; floor >= 70 -> 17; floor >= 40 -> 12; floor >= 20 -> 7; else -> 2 }
}

// ============ 成就系统 ============
data class AchievementRewards(
    val hp: Long = 0, val atk: Int = 0, val matk: Int = 0,
    val pdef: Int = 0, val mdef: Int = 0, val critRate: Int = 0, val critDmg: Int = 0
)

data class AchievementDef(
    val id: String, val name: String, val description: String,
    val category: String, val requiredValue: Long, val rewards: AchievementRewards
)

object AchievementDefs {
    val all = listOf(
        AchievementDef("cult_10", "初出茅庐", "达到10级", "CULTIVATION", 10, AchievementRewards(hp = 100, atk = 5)),
        AchievementDef("cult_30", "魂尊之路", "达到30级", "CULTIVATION", 30, AchievementRewards(hp = 300, atk = 15, pdef = 5, mdef = 5)),
        AchievementDef("cult_50", "魂宗威名", "达到50级", "CULTIVATION", 50, AchievementRewards(hp = 600, atk = 30, pdef = 10, mdef = 10, critRate = 2)),
        AchievementDef("cult_80", "封号斗罗", "达到80级", "CULTIVATION", 80, AchievementRewards(hp = 1500, atk = 60, pdef = 20, mdef = 20, critRate = 5)),
        AchievementDef("cult_100", "极限斗罗", "达到100级", "CULTIVATION", 100, AchievementRewards(hp = 3000, atk = 120, pdef = 40, mdef = 40, critRate = 8, critDmg = 15)),
        AchievementDef("ring_1", "初获魂环", "获得第一个魂环", "SOUL_RING", 1, AchievementRewards(matk = 10, critRate = 1)),
        AchievementDef("ring_3", "三环齐聚", "获得3个魂环", "SOUL_RING", 3, AchievementRewards(matk = 35, critRate = 3, critDmg = 10)),
        AchievementDef("ring_5", "五环辉煌", "获得5个魂环", "SOUL_RING", 5, AchievementRewards(matk = 70, critRate = 5, critDmg = 20, hp = 300)),
        AchievementDef("ring_9", "九环圆满", "获得9个魂环", "SOUL_RING", 9, AchievementRewards(hp = 500, matk = 200, critRate = 10, critDmg = 40)),
        AchievementDef("battle_10", "十战勇士", "赢得10场战斗", "BATTLE", 10, AchievementRewards(hp = 100, atk = 10)),
        AchievementDef("battle_50", "百战老兵", "赢得50场战斗", "BATTLE", 50, AchievementRewards(hp = 400, atk = 30, pdef = 5)),
        AchievementDef("tower_10", "塔十层", "通关杀戮之都第10层", "TOWER", 10, AchievementRewards(hp = 200, atk = 15, matk = 10)),
        AchievementDef("tower_30", "塔三十层", "通关杀戮之都第30层", "TOWER", 30, AchievementRewards(hp = 600, atk = 50, matk = 40, pdef = 10, mdef = 10, critRate = 3)),
        AchievementDef("tower_50", "塔五十层", "通关杀戮之都第50层", "TOWER", 50, AchievementRewards(hp = 1500, atk = 120, matk = 100, pdef = 25, mdef = 25, critRate = 5, critDmg = 15)),
        AchievementDef("prestige_1", "初次转生", "完成第一次神位传承", "PRESTIGE", 1, AchievementRewards(hp = 500, pdef = 20, mdef = 20)),
        AchievementDef("prestige_3", "三生三世", "完成3次神位传承", "PRESTIGE", 3, AchievementRewards(hp = 2000, pdef = 60, mdef = 60, critDmg = 20))
    )
}