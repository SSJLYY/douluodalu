package com.douluodalu.game.model


// 【第二十一轮死数据清理留档】本文件曾有 PlayerAttributes / RealmData / EquipAffix /
// EquipAffixValue / MonsterAffix / MapDropInfo / MapStats / MapEffect / MapDef / MapData /
// TowerSegment / TowerData 共 12 个符号，全后端（src/main + src/test）零消费（玩法实际口径
// 分别由 GameService.REALM_NAMES/monsterStats 与 GameBalance.TOWER_* 等替代），已删除；
// 历史实现见 git 记录。保留的 Rarity/RingYear/RingQuality/BoneType/BoneYear/BoneRarity/
// SoulCoreTier/TalentBranch/ShopItem/三商店数据均有消费（ShopService/TalentService/GameBalance 等）。

// ============ 品质/稀有度 ============
enum class Rarity(val displayName: String) {
    COMMON("普通"), UNCOMMON("精良"), RARE("稀有"),
    EPIC("史诗"), LEGENDARY("传说"), MYTHIC("神话")
}


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

// 普通商店：金币购买的基础商品（前期过渡用）
object NormalShopData {
    val items = listOf(
        ShopItem(201, "百年魂环箱", "随机获得一个百年魂环", 500, "GOLD", "RING_BOX", "HUNDRED"),
        ShopItem(202, "千年魂环箱", "随机获得一个千年魂环", 4000, "GOLD", "RING_BOX", "THOUSAND"),
        ShopItem(203, "百年魂骨箱", "随机获得一个百年魂骨", 800, "GOLD", "BONE_BOX", "HUNDRED"),
        ShopItem(204, "魂力精华(小)", "获得500魂力", 300, "GOLD", "SOUL_POWER", "500"),
        ShopItem(205, "背包扩展券", "背包容量+5", 3000, "GOLD", "BACKPACK_EXPAND", "5")
    )
}

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
    // id 必须与其他商店（Normal 2xx / Boss 1-10 / Guild 3xx）全局不冲突：
    // shop_purchase_record 只按 (user_id, item_id) 记账，撞 id 会互相串用限购计数
    // （历史 bug：此处 1/2/3 与 BossShopData 前三项相同，买过"万年魂环箱"即误判限量魂核"已售罄"）。
    // 旧库里挂在 itemId 1-3 的记录归属 Boss 商店，改名后限量额度自然重置，属一次性可接受偏差。
    val items = listOf(
        ShopItem(901, "传说魂核", "必得一个传说级魂核", 2000, "BOSS_COIN", "CORE_BOX", "MYTHIC", stock = 1),
        ShopItem(902, "百万年魂骨箱", "随机获得一个百万年魂骨", 1500, "BOSS_COIN", "BONE_BOX", "MILLION", stock = 3)
        // 903 "神赐礼包"(GIFT_PACK) 已下架：ShopService 没有 GIFT_PACK 发放分支，
        // rewardValidationError 预检虽能"干净拒绝"（不扣币），但对用户是"看得见买不了"的坏体验。
        // 恢复条件：在 ShopService 的奖励发放 when(item.itemType) 中实现 GIFT_PACK
        // （定义并派发稀有材料包）+ 从 rewardValidationError 拒绝名单放行 + 补发放测试，然后再挂回列表。
    )
}


