$filePath = 'E:\android\testGame\app\src\main\java\com\example\garygame\model\Models.kt'
$content = Get-Content -Path $filePath -Raw -Encoding UTF8

$oldBossShop = @'
object BossShopData {
    /** Boss币掉落 */
    fun bossCoinReward(mapId: Int, isBoss: Boolean): Int {
        val base = 5 + mapId * 5
        return if (isBoss) base * 3 else base
    }

    /** 魂核抽取价格 */
    const val SOUL_CORE_GACHA_COST = 50

    /** 定向购买魂核价格倍率 */
    fun soulCoreBuyCost(def: SoulCoreDef): Int = def.bossCoinCost

    /** 优秀魂环价格 */
    const val RING_EXCELLENT_COST = 300
    /** 完美魂环价格 */
    const val RING_PERFECT_COST = 800

    /** 优秀魂骨价格 */
    const val BONE_EXCELLENT_COST = 600
    /** 完美魂骨价格 */
    const val BONE_PERFECT_COST = 1500

    /** 魂力结晶价格(直接加魂力) */
    const val SOUL_CRYSTAL_COST = 20
    const val SOUL_CRYSTAL_VALUE = 500L
}
'@

$newBossShop = @'
object BossShopData {
    /** Boss币掉落 */
    fun bossCoinReward(mapId: Int, isBoss: Boolean): Int {
        val base = 5 + mapId * 5
        return if (isBoss) base * 3 else base
    }

    /** 魂核抽取价格 */
    const val SOUL_CORE_GACHA_COST = 50

    /** 定向购买魂核价格倍率 */
    fun soulCoreBuyCost(def: SoulCoreDef): Int = def.bossCoinCost

    /** 魂力结晶价格(直接加魂力) */
    const val SOUL_CRYSTAL_COST = 20
    const val SOUL_CRYSTAL_VALUE = 500L

    // ======== Boss商店阶梯系统（V4品质）========

    enum class BossShopTier(val label: String, val description: String, val minMapId: Int) {
        TIER_1("劣等·普通", "地图3+", 3),
        TIER_2("精良", "地图5+", 5),
        TIER_3("优秀", "地图7+", 7),
        TIER_4("完美", "地图9+", 9)
    }

    data class BossRingItem(val yearOrdinal: Int, val tier: BossShopTier, val cost: Int, val icon: String, val label: String)
    data class BossBoneItem(val rarityOrdinal: Int, val tier: BossShopTier, val cost: Int, val icon: String, val label: String)

    val ringItems = listOf(
        BossRingItem(0, BossShopTier.TIER_1, 50, "\uD83D\uDC8D", "劣等魂环"),
        BossRingItem(1, BossShopTier.TIER_1, 120, "\uD83D\uDC8D", "普通魂环"),
        BossRingItem(2, BossShopTier.TIER_2, 250, "\uD83D\uDC8D", "精良魂环"),
        BossRingItem(3, BossShopTier.TIER_3, 500, "\uD83D\uDC8D", "优秀魂环"),
        BossRingItem(4, BossShopTier.TIER_4, 1200, "\uD83D\uDC8D", "完美魂环")
    )

    val boneItems = listOf(
        BossBoneItem(0, BossShopTier.TIER_1, 100, "\uD83E\uDDB4", "劣等魂骨"),
        BossBoneItem(1, BossShopTier.TIER_1, 250, "\uD83E\uDDB4", "普通魂骨"),
        BossBoneItem(2, BossShopTier.TIER_2, 500, "\uD83E\uDDB4", "精良魂骨"),
        BossBoneItem(3, BossShopTier.TIER_3, 1000, "\uD83E\uDDB4", "优秀魂骨"),
        BossBoneItem(4, BossShopTier.TIER_4, 2500, "\uD83E\uDDB4", "完美魂骨")
    )

    fun getAllRingItems(): List<BossRingItem> = ringItems
    fun getAllBoneItems(): List<BossBoneItem> = boneItems

    fun isTierUnlocked(tier: BossShopTier, mapId: Int): Boolean = mapId >= tier.minMapId
}
'@

$newContent = $content.Replace($oldBossShop, $newBossShop)
Set-Content -Path $filePath -Value $newContent -NoNewline -Encoding UTF8
Write-Host "BossShopData updated. Length: $($newContent.Length)"
