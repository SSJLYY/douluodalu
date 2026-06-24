$file = 'E:\android\testGame\app\src\main\java\com\example\garygame\model\Models.kt'
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)

# 1. Replace SoulRingYear enum with RingQuality
$oldEnum = @'
enum class SoulRingYear(val displayName: String, val costBase: Int, val colorHex: Long) {
    HUNDRED("百年魂环", 800, 0xFFFFEB3B),
    THOUSAND("千年魂环", 3000, 0xFF9C27B0),
    TEN_THOUSAND("万年魂环", 15000, 0xFF00BCD4),
    HUNDRED_THOUSAND("十万年魂环", 80000, 0xFFFF1744),
    MILLION("百万年魂环", 500000, 0xFFFFD700);

    fun cost(ringLevel: Int): Long = (costBase * (1.0 + ringLevel * 0.5)).toLong()
}
'@

$newEnum = @'
enum class RingQuality(val displayName: String, val costBase: Int, val colorHex: Long, val skillLevel: Int) {
    INFERIOR("劣等魂环", 500, 0xFF9E9E9E, 1),
    NORMAL("普通魂环", 2000, 0xFF4CAF50, 2),
    FINE("精良魂环", 8000, 0xFF2196F3, 3),
    EXCELLENT("优秀魂环", 30000, 0xFF9C27B0, 4),
    PERFECT("完美魂环", 150000, 0xFFFFD700, 5);

    fun cost(ringLevel: Int): Long = (costBase * (1.0 + ringLevel * 0.5)).toLong()

    val colorInt: Int get() = colorHex.toInt()

    companion object {
        /** 技能乘数曲线: 1.0 + 0.5*q^2 */
        fun skillMultiplier(qualityOrdinal: Int): Double {
            val q = qualityOrdinal.coerceIn(0, entries.size - 1)
            return 1.0 + 0.5 * q * q
        }
        /** 属性乘数曲线: 1.0 + 0.18*q^2 */
        fun statMultiplier(qualityOrdinal: Int): Double {
            val q = qualityOrdinal.coerceIn(0, entries.size - 1)
            return 1.0 + 0.18 * q * q
        }
    }
}
'@

if ($content.Contains('enum class SoulRingYear')) {
    $content = $content.Replace($oldEnum, $newEnum)
    Write-Host "SoulRingYear -> RingQuality: REPLACED"
} else {
    Write-Host "ERROR: SoulRingYear enum not found!"
}

# 2. Replace all remaining SoulRingYear references with RingQuality
$content = $content.Replace('SoulRingYear', 'RingQuality')

# 3. Replace SoulRingGenerator references
$content = $content.Replace('SoulRingGenerator', 'SoulRingGenerator')

# Save
[System.IO.File]::WriteAllText($file, $content, [System.Text.Encoding]::UTF8)
Write-Host "Models.kt V4 Phase 1a done."
