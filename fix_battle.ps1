$file = 'E:\android\testGame\app\src\main\java\com\example\garygame\engine\GameEngine.kt'
$lines = [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)
$out = [System.Collections.ArrayList]::new()

# Track what section we're in
$inRingDrop = $false
$inBoneDrop = $false
$ringDropDone = $false
$boneDropDone = $false

for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    
    # Line 717-718: update comment
    if ($i -eq 718) {
        [void]$out.Add($line -replace 'DropDistribution\.qualityMean\(mapId\)', 'DropDistribution.tierMean(mapId)')
        continue
    }
    if ($i -eq 719) {
        [void]$out.Add($line -replace 'maxDropQuality\(mapId\)', 'maxDropTier(mapId)')
        continue
    }
    if ($i -eq 720) {
        [void]$out.Add($line -replace 'maxBoneDropQuality\(mapId\)', 'maxBoneDropTier(mapId)' -replace 'maxBoneQ', 'maxBoneTier')
        continue
    }
    
    # Line 722: update comment
    if ($i -eq 722) {
        [void]$out.Add('        // V4: 阶梯式品质掉落 - 品质越高概率越低, Boss/精英加成, 每个年份各有5个品质')
        continue
    }
    
    # Line 723: fun ringDropChance
    if ($i -eq 723) {
        [void]$out.Add('        fun ringDropChance(qualityOrd: Int, boss: Boolean, elite: Boolean): Float {')
        continue
    }
    # Line 724: if (q > maxRingQ) -> if (qualityOrd > 4)
    if ($i -eq 724) {
        [void]$out.Add('            if (qualityOrd > 4) return 0f')
        continue
    }
    # Line 725: when (q) -> when (qualityOrd)
    if ($i -eq 725) {
        [void]$out.Add('            val base = when (qualityOrd) {')
        continue
    }
    
    # Line 732: fun boneDropChance
    if ($i -eq 732) {
        [void]$out.Add('        fun boneDropChance(qualityOrd: Int, boss: Boolean, elite: Boolean): Float {')
        continue
    }
    # Line 733: if (q > maxBoneQ) -> if (qualityOrd > 4)
    if ($i -eq 733) {
        [void]$out.Add('            if (qualityOrd > 4) return 0f')
        continue
    }
    # Line 734: when (q) -> when (qualityOrd)
    if ($i -eq 734) {
        [void]$out.Add('            val base = when (qualityOrd) {')
        continue
    }
    
    # New ring drop section (replace lines 742-759)
    if ($i -eq 741) {
        [void]$out.Add('')
        [void]$out.Add('        // V4: 魂环掉落 - 年份x品质二维掉落 + 精英/Boss加成')
        [void]$out.Add('        val isEliteStage = StageData.getStageType(s.currentStage) == StageType.ELITE')
        [void]$out.Add('        val ringTier = DropDistribution.rollTier(mean, DropDistribution.TIER_STD_DEV, maxRingTier)')
        [void]$out.Add('        val (ringYear, ringQual) = SoulRingGenerator.splitTier(ringTier)')
        [void]$out.Add('        if (Random.nextFloat() < ringDropChance(ringQual, isBossStage, isEliteStage)) {')
        [void]$out.Add('            val affixes = SoulRingGenerator.generateRandomAffixes(ringTier)')
        [void]$out.Add('            val skill = ActiveSkillPool.randomSkill(ringTier)')
        [void]$out.Add('            val dropped = DroppedRing(ringYear, ringQual, affixes, skill)')
        [void]$out.Add('            if (s.backpackIsFull) {')
        [void]$out.Add('                val price = calcRingSellPrice(ringTier)')
        [void]$out.Add('                s.gold += price; s.totalGoldEarned += price')
        [void]$out.Add('                addLog("💍 掉落: ${RingQuality.fullName(ringYear, ringQual)}[${skill.name}] -> 包满自动售出 +${formatNum(price)}💰")')
        [void]$out.Add('            } else {')
        [void]$out.Add('                s.backpackRings.add(dropped)')
        [void]$out.Add('                addLog("💍 掉落: ${RingQuality.fullName(ringYear, ringQual)}[${skill.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")')
        [void]$out.Add('                if (s.autoSellRings && ringQual < s.autoSellRingThreshold) applyAutoSellRings()')
        [void]$out.Add('            }')
        [void]$out.Add('        }')
        $ringDropDone = $true
        continue
    }
    # Skip lines 742-759 (old ring drop section)
    if ($i -ge 742 -and $i -le 759) {
        continue
    }
    
    # New bone drop section (replace lines 761-778)
    if ($i -eq 760) {
        [void]$out.Add('')
        [void]$out.Add('        // V4: 魂骨掉落 - 年份x品质二维掉落 + 精英/Boss加成')
        [void]$out.Add('        val boneTier = DropDistribution.rollTier(mean - 0.5, DropDistribution.TIER_STD_DEV, maxBoneTier)')
        [void]$out.Add('        val (boneYear, boneRar) = SoulRingGenerator.splitTier(boneTier)')
        [void]$out.Add('        if (Random.nextFloat() < boneDropChance(boneRar, isBossStage, isEliteStage)) {')
        [void]$out.Add('            val boneType = Random.nextInt(BoneType.entries.size)')
        [void]$out.Add('            val affixes = SoulBoneGenerator.generateRandomAffix(boneTier)')
        [void]$out.Add('            val passive = PassiveSkillPool.randomPassive(boneType, boneTier)')
        [void]$out.Add('            val dropped = DroppedBone(boneType, boneYear, boneRar, affixes, passive)')
        [void]$out.Add('            if (s.backpackIsFull) {')
        [void]$out.Add('                val price = calcBoneSellPrice(boneTier)')
        [void]$out.Add('                s.gold += price; s.totalGoldEarned += price')
        [void]$out.Add('                addLog("🦴 掉落: ${BoneRarity.fullName(boneYear, boneRar)}(${BoneType.entries[boneType].displayName})[${passive.name}] -> 包满自动售出 +${formatNum(price)}💰")')
        [void]$out.Add('            } else {')
        [void]$out.Add('                s.backpackBones.add(dropped)')
        [void]$out.Add('                addLog("🦴 掉落: ${BoneRarity.fullName(boneYear, boneRar)}(${BoneType.entries[boneType].displayName})[${passive.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")')
        [void]$out.Add('                if (s.autoSellBones && boneRar < s.autoSellBoneThreshold) applyAutoSellBones()')
        [void]$out.Add('            }')
        [void]$out.Add('        }')
        $boneDropDone = $true
        continue
    }
    # Skip lines 761-778 (old bone drop section)
    if ($i -ge 761 -and $i -le 778) {
        continue
    }
    
    [void]$out.Add($line)
}

[System.IO.File]::WriteAllLines($file, $out, [System.Text.Encoding]::UTF8)
Write-Host "Battle drop section fixed. ringDropDone=$ringDropDone boneDropDone=$boneDropDone"
