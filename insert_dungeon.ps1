$filePath = 'E:\android\testGame\app\src\main\java\com\example\garygame\model\Models.kt'
$content = Get-Content -Path $filePath -Raw -Encoding UTF8

$dungeonData = @"

// ============ 每日副本（V4品质掉落）============
object DungeonData {
    const val MIN_PRESTIGE = 1
    const val DAILY_RESET_MS = 86400000L

    data class DungeonTier(
        val name: String,
        val difficulty: String,
        val bossName: String,
        val bossDesc: String,
        val hpMult: Double,
        val atkMult: Double,
        val goldReward: Long,
        val killingIntentReward: Int,
        val boneRarity: Int,
        val ringQuality: Int
    )

    val tiers = listOf(
        DungeonTier("魂兽森林", "简单", "千年魂兽·泰坦巨猿",
            "力量的化身，皮糙肉厚", 3.0, 1.8, 5000, 10,
            boneRarity = 1, ringQuality = 1),
        DungeonTier("暗影峡谷", "普通", "暗影君王·鬼魅",
            "来去无踪，一击致命", 5.0, 2.5, 15000, 25,
            boneRarity = 2, ringQuality = 2),
        DungeonTier("龙墓禁地", "困难", "远古龙皇·赤王",
            "龙息焚天，万法不侵", 8.0, 3.5, 40000, 50,
            boneRarity = 3, ringQuality = 3),
        DungeonTier("神之遗迹", "噩梦", "堕落天使·路西法",
            "天使与恶魔的双面化身", 12.0, 5.0, 100000, 100,
            boneRarity = 4, ringQuality = 4),
        DungeonTier("深渊之门", "地狱", "深渊之主·阿萨谢尔",
            "凝视深渊者，终将被吞噬", 20.0, 8.0, 300000, 200,
            boneRarity = 4, ringQuality = 4)
    )

    fun getMaxTier(prestigeCount: Int): Int =
        (prestigeCount - 1).coerceIn(0, tiers.size - 1)
}

"@

$marker = "// ============ 天赋树系统 ============"
$replacement = $dungeonData + "`r`n" + $marker
$newContent = $content.Replace($marker, $replacement)
Set-Content -Path $filePath -Value $newContent -NoNewline -Encoding UTF8
Write-Host "DungeonData inserted successfully"
