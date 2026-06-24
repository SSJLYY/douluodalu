$file = 'E:\android\testGame\app\src\main\java\com\example\garygame\engine\GameEngine.kt'
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)

# 1. Fix battle drop ring - rename variable and decompose combinedTier
$content = $content.Replace(
'        val ringQuality = DropDistribution.rollQuality(mean, DropDistribution.QUALITY_STD_DEV, maxRingQ)
        if (Random.nextFloat() < ringDropChance(ringQuality, isBossStage, isEliteStage)) {
            val quality = ringQuality
            val affixes = SoulRingGenerator.generateRandomAffixes(quality)
            val skill = ActiveSkillPool.randomSkill(quality)
            val dropped = DroppedRing(quality, affixes, skill)
            if (s.backpackIsFull) {
                val price = calcRingSellPrice(quality)
                s.gold += price; s.totalGoldEarned += price
                addLog("💍 掉落: ${RingQuality.entries[quality].displayName}[${skill.name}] → 包满自动售出 +${formatNum(price)}💰")
            } else {
                s.backpackRings.add(dropped)
                addLog("💍 掉落: ${RingQuality.entries[quality].displayName}[${skill.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")
                if (s.autoSellRings && quality < s.autoSellRingThreshold) applyAutoSellRings()
            }
        }',
'        val ringCombinedTier = DropDistribution.rollQuality(mean, DropDistribution.QUALITY_STD_DEV, maxRingQ)
        if (Random.nextFloat() < ringDropChance(ringCombinedTier, isBossStage, isEliteStage)) {
            val tier = ringCombinedTier
            val yearOrd = RingYear.yearOf(tier)
            val qualityOrd = RingYear.qualityOf(tier)
            val affixes = SoulRingGenerator.generateRandomAffixes(tier)
            val skill = ActiveSkillPool.randomSkill(tier)
            val dropped = DroppedRing(yearOrd, qualityOrd, affixes, skill)
            if (s.backpackIsFull) {
                val price = calcRingSellPrice(tier)
                s.gold += price; s.totalGoldEarned += price
                addLog("💍 掉落: ${RingQuality.fullName(yearOrd, qualityOrd)}[${skill.name}] → 包满自动售出 +${formatNum(price)}💰")
            } else {
                s.backpackRings.add(dropped)
                addLog("💍 掉落: ${RingQuality.fullName(yearOrd, qualityOrd)}[${skill.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")
                if (s.autoSellRings && tier < s.autoSellRingThreshold) applyAutoSellRings()
            }
        }'
)

[System.IO.File]::WriteAllText($file, $content, [System.Text.Encoding]::UTF8)
Write-Output "Script completed successfully"
