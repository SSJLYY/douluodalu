$file = 'E:\android\testGame\app\src\main\java\com\example\garygame\engine\GameEngine.kt'
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)

# === 1. Fix battle drop section ===
# Replace qualityMean -> tierMean, maxDropQuality -> maxDropTier, maxBoneDropQuality -> maxBoneDropTier
$content = $content -replace 'DropDistribution\.qualityMean\(mapId\)', 'DropDistribution.tierMean(mapId)'
$content = $content -replace 'DropDistribution\.maxDropQuality\(mapId\)', 'DropDistribution.maxDropTier(mapId)'
$content = $content -replace 'DropDistribution\.maxBoneDropQuality\(mapId\)', 'DropDistribution.maxBoneDropTier(mapId)'

# Replace variable names
$content = $content -replace 'val maxRingQ =', 'val maxRingTier ='
$content = $content -replace 'val maxBoneQ =', 'val maxBoneTier ='
$content = $content -replace 'if \(q > maxRingQ\)', 'if (qualityOrd > 4)'
$content = $content -replace 'if \(q > maxBoneQ\)', 'if (qualityOrd > 4)'

# Replace ringDropChance function signature and body
$content = $content -replace 'fun ringDropChance\(q: Int, boss: Boolean, elite: Boolean\): Float \{', 'fun ringDropChance(qualityOrd: Int, boss: Boolean, elite: Boolean): Float {'
$content = $content -replace 'fun boneDropChance\(q: Int, boss: Boolean, elite: Boolean\): Float \{', 'fun boneDropChance(qualityOrd: Int, boss: Boolean, elite: Boolean): Float {'

# Replace q references in the drop chance functions
$content = $content -replace 'when \(q\)', 'when (qualityOrd)'
$content = $content -replace 'boss && q >= 2', 'boss && qualityOrd >= 2'
$content = $content -replace 'elite && q <= 2', 'elite && qualityOrd <= 2'

# Replace rollQuality -> rollTier, QUALITY_STD_DEV -> TIER_STD_DEV
$content = $content -replace 'DropDistribution\.rollQuality\(mean, DropDistribution\.QUALITY_STD_DEV, maxRingTier\)', 'DropDistribution.rollTier(mean, DropDistribution.TIER_STD_DEV, maxRingTier)'
$content = $content -replace 'DropDistribution\.rollQuality\(mean - 0\.5, DropDistribution\.QUALITY_STD_DEV, maxBoneTier\)', 'DropDistribution.rollTier(mean - 0.5, DropDistribution.TIER_STD_DEV, maxBoneTier)'

# Replace ring drop section: ringQuality -> ringTier, split into year+quality
$oldRingDrop = @'
        // V4: 魂环掉落 — 阶梯品质\+精英/Boss加成
        val isEliteStage = StageData\.getStageType\(s\.currentStage\) == StageType\.ELITE
        val ringQuality = DropDistribution\.rollTier\(mean, DropDistribution\.TIER_STD_DEV, maxRingTier\)
        if \(Random\.nextFloat\(\) < ringDropChance\(ringQuality, isBossStage, isEliteStage\)\) \{
            val quality = ringQuality
            val affixes = SoulRingGenerator\.generateRandomAffixes\(quality\)
            val skill = ActiveSkillPool\.randomSkill\(quality\)
            val dropped = DroppedRing\(quality, affixes, skill\)
            if \(s\.backpackIsFull\) \{
                val price = calcRingSellPrice\(quality\)
                s\.gold \+= price; s\.totalGoldEarned \+= price
                addLog\("💍 掉落: \$\{RingQuality\.entries\[quality\]\.displayName\}\[\$\{skill\.name\}\] → 包满自动售出 \+\$\{formatNum\(price\)\}💰"\)
            \} else \{
                s\.backpackRings\.add\(dropped\)
                addLog\("💍 掉落: \$\{RingQuality\.entries\[quality\]\.displayName\}\[\$\{skill\.name\}\] \(包\$\{s\.backpackTotalItems\}/\$\{s\.backpackCapacity\}\)"\)
                if \(s\.autoSellRings && quality < s\.autoSellRingThreshold\) applyAutoSellRings\(\)
            \}
        \}
'@

$newRingDrop = @'
        // V4: 魂环掉落 — 年份×品质二维掉落 + 精英/Boss加成
        val isEliteStage = StageData.getStageType(s.currentStage) == StageType.ELITE
        val ringTier = DropDistribution.rollTier(mean, DropDistribution.TIER_STD_DEV, maxRingTier)
        val (ringYear, ringQual) = SoulRingGenerator.splitTier(ringTier)
        if (Random.nextFloat() < ringDropChance(ringQual, isBossStage, isEliteStage)) {
            val affixes = SoulRingGenerator.generateRandomAffixes(ringTier)
            val skill = ActiveSkillPool.randomSkill(ringTier)
            val dropped = DroppedRing(ringYear, ringQual, affixes, skill)
            if (s.backpackIsFull) {
                val price = calcRingSellPrice(ringTier)
                s.gold += price; s.totalGoldEarned += price
                addLog("💍 掉落: ${RingQuality.fullName(ringYear, ringQual)}[${skill.name}] → 包满自动售出 +${formatNum(price)}💰")
            } else {
                s.backpackRings.add(dropped)
                addLog("💍 掉落: ${RingQuality.fullName(ringYear, ringQual)}[${skill.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")
                if (s.autoSellRings && ringQual < s.autoSellRingThreshold) applyAutoSellRings()
            }
        }
'@

$content = $content -replace $oldRingDrop, $newRingDrop

# Replace bone drop section
$oldBoneDrop = @'
        // V4: 魂骨掉落 — 阶梯品质\+精英/Boss加成
        val boneQuality = DropDistribution\.rollTier\(mean - 0\.5, DropDistribution\.TIER_STD_DEV, maxBoneTier\)
        if \(Random\.nextFloat\(\) < boneDropChance\(boneQuality, isBossStage, isEliteStage\)\) \{
            val quality = boneQuality
            val boneType = Random\.nextInt\(BoneType\.entries\.size\)
            val affixes = SoulBoneGenerator\.generateRandomAffix\(quality\)
            val passive = PassiveSkillPool\.randomPassive\(boneType, quality\)
            val dropped = DroppedBone\(boneType, quality, affixes, passive\)
            if \(s\.backpackIsFull\) \{
                val price = calcBoneSellPrice\(quality\)
                s\.gold \+= price; s\.totalGoldEarned \+= price
                addLog\("🦴 掉落: \$\{BoneRarity\.entries\[quality\]\.displayName\}\[\$\{passive\.name\}\] → 包满自动售出 \+\$\{formatNum\(price\)\}💰"\)
            \} else \{
                s\.backpackBones\.add\(dropped\)
                addLog\("🦴 掉落: \$\{BoneRarity\.entries\[quality\]\.displayName\}\(\$\{BoneType\.entries\[boneType\]\.displayName\}\)\[\$\{passive\.name\}\] \(包\$\{s\.backpackTotalItems\}/\$\{s\.backpackCapacity\}\)"\)
                if \(s\.autoSellBones && quality < s\.autoSellBoneThreshold\) applyAutoSellBones\(\)
            \}
        \}
'@

$newBoneDrop = @'
        // V4: 魂骨掉落 — 年份×品质二维掉落 + 精英/Boss加成
        val boneTier = DropDistribution.rollTier(mean - 0.5, DropDistribution.TIER_STD_DEV, maxBoneTier)
        val (boneYear, boneRar) = SoulRingGenerator.splitTier(boneTier)
        if (Random.nextFloat() < boneDropChance(boneRar, isBossStage, isEliteStage)) {
            val boneType = Random.nextInt(BoneType.entries.size)
            val affixes = SoulBoneGenerator.generateRandomAffix(boneTier)
            val passive = PassiveSkillPool.randomPassive(boneType, boneTier)
            val dropped = DroppedBone(boneType, boneYear, boneRar, affixes, passive)
            if (s.backpackIsFull) {
                val price = calcBoneSellPrice(boneTier)
                s.gold += price; s.totalGoldEarned += price
                addLog("🦴 掉落: ${BoneRarity.fullName(boneYear, boneRar)}(${BoneType.entries[boneType].displayName})[${passive.name}] → 包满自动售出 +${formatNum(price)}💰")
            } else {
                s.backpackBones.add(dropped)
                addLog("🦴 掉落: ${BoneRarity.fullName(boneYear, boneRar)}(${BoneType.entries[boneType].displayName})[${passive.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")
                if (s.autoSellBones && boneRar < s.autoSellBoneThreshold) applyAutoSellBones()
            }
        }
'@

$content = $content -replace $oldBoneDrop, $newBoneDrop

# Update comment
$content = $content -replace '// V4: 阶梯式品质掉落 — 品质越高概率越低, Boss/精英加成, 地图限制品质上限', '// V4: 阶梯式品质掉落 — 品质越高概率越低, Boss/精英加成, 每个年份各有5个品质'

[System.IO.File]::WriteAllText($file, $content, [System.Text.Encoding]::UTF8)
Write-Host "fix_gameengine.ps1 completed"
