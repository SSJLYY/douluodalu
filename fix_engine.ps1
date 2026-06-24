# Fix GameEngine.kt for V5 dual-dimension ring/bone system
$file = 'E:\android\testGame\app\src\main\java\com\example\garygame\engine\GameEngine.kt'
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)

# 1. Fix bone.rarityOrdinal -> bone.combinedTier in calcAttributes
$content = $content -replace 'ps\.getValue\(bone\.rarityOrdinal\)', 'ps.getValue(bone.combinedTier)'

# 2. Fix bone.rarityOrdinal in doBattle (line 661)
$content = $content -replace 'val valR = ps\.getValue\(bone\.rarityOrdinal\)', 'val valR = ps.getValue(bone.combinedTier)'

# 3. Fix calcRingSellPrice - expand to 0-24 range (5 year * 5 quality)
$oldRingPrice = @'
    fun calcRingSellPrice(quality: Int): Long = when (quality) {
        0 -> 80L        // 劣等
        1 -> 350L       // 普通
        2 -> 1500L      // 精良
        3 -> 6000L      // 优秀
        4 -> 30000L     // 完美
        else -> 80L
    }
'@
$newRingPrice = @'
    fun calcRingSellPrice(combinedTier: Int): Long {
        val year = combinedTier / 5
        val qual = combinedTier % 5
        val base = when (year) {
            0 -> listOf(80L, 200L, 500L, 1200L, 3000L)
            1 -> listOf(350L, 800L, 2000L, 5000L, 12000L)
            2 -> listOf(1500L, 3500L, 8000L, 20000L, 50000L)
            3 -> listOf(6000L, 15000L, 35000L, 80000L, 200000L)
            4 -> listOf(30000L, 70000L, 150000L, 350000L, 800000L)
            else -> listOf(80L, 200L, 500L, 1200L, 3000L)
        }
        return base[qual.coerceIn(0, 4)]
    }
'@
$content = $content -replace [regex]::Escape($oldRingPrice), $newRingPrice

# 4. Fix calcBoneSellPrice - expand to 0-24 range
$oldBonePrice = @'
    fun calcBoneSellPrice(quality: Int): Long = when (quality) {
        0 -> 800L       // 劣等
        1 -> 3500L      // 普通
        2 -> 15000L     // 精良
        3 -> 80000L     // 优秀
        4 -> 400000L    // 完美
        else -> 800L
    }
'@
$newBonePrice = @'
    fun calcBoneSellPrice(combinedTier: Int): Long {
        val year = combinedTier / 5
        val qual = combinedTier % 5
        val base = when (year) {
            0 -> listOf(800L, 2000L, 5000L, 12000L, 30000L)
            1 -> listOf(3500L, 8000L, 20000L, 50000L, 120000L)
            2 -> listOf(15000L, 35000L, 80000L, 200000L, 500000L)
            3 -> listOf(80000L, 180000L, 400000L, 900000L, 2000000L)
            4 -> listOf(400000L, 900000L, 1800000L, 4000000L, 9000000L)
            else -> listOf(800L, 2000L, 5000L, 12000L, 30000L)
        }
        return base[qual.coerceIn(0, 4)]
    }
'@
$content = $content -replace [regex]::Escape($oldBonePrice), $newBonePrice

# 5. Fix sortBackpackRings
$content = $content -replace 'state\.backpackRings\.sortByDescending \{ it\.qualityOrdinal \}', 'state.backpackRings.sortByDescending { it.combinedTier }'

# 6. Fix sortBackpackBones
$content = $content -replace 'state\.backpackBones\.sortByDescending \{ it\.rarityOrdinal \}', 'state.backpackBones.sortByDescending { it.combinedTier }'

# 7. Fix sellBackpackRing - ring.qualityOrdinal -> ring.combinedTier
$content = $content -replace 'calcRingSellPrice\(ring\.qualityOrdinal\)', 'calcRingSellPrice(ring.combinedTier)'
$oldSellRingLog = 'addLog\("💰 卖出 \$\{RingQuality\.entries\[ring\.qualityOrdinal\]\.displayName\} \+'
$newSellRingLog = 'addLog("💰 卖出 ${RingQuality.fullName(ring.yearOrdinal, ring.qualityOrdinal)} +'
$content = $content -replace $oldSellRingLog, $newSellRingLog

# 8. Fix sellBackpackBone - bone.rarityOrdinal -> bone.combinedTier
$content = $content -replace 'calcBoneSellPrice\(bone\.rarityOrdinal\)', 'calcBoneSellPrice(bone.combinedTier)'
$oldSellBoneLog = 'addLog\("💰 卖出 \$\{BoneRarity\.entries\[bone\.rarityOrdinal\]\.displayName\}\(\$typeName\) \+'
$newSellBoneLog = 'addLog("💰 卖出 ${BoneRarity.fullName(bone.yearOrdinal, bone.rarityOrdinal)}($typeName) +'
$content = $content -replace $oldSellBoneLog, $newSellBoneLog

# 9. Fix sellAllBackpackRings - qualityOrdinal -> combinedTier
$content = $content -replace 'calcRingSellPrice\(state\.backpackRings\[i\]\.qualityOrdinal\)', 'calcRingSellPrice(state.backpackRings[i].combinedTier)'

# 10. Fix sellAllBackpackBones - rarityOrdinal -> combinedTier
$content = $content -replace 'calcBoneSellPrice\(state\.backpackBones\[i\]\.rarityOrdinal\)', 'calcBoneSellPrice(state.backpackBones[i].combinedTier)'

# 11. Fix unequipRing - full rewrite
$oldUnequipRing = @'
    fun unequipRing(slotIdx: Int): Boolean {
        if (slotIdx !in state.soulRings) { addLog("该槽位没有魂环"); notifyUI(); return false }
        val ring = state.soulRings.remove(slotIdx)!!
        val slotName = SoulRingSlots.get(slotIdx)?.displayName ?: "魂环${slotIdx + 1}"
        val quality = RingQuality.entries[ring.qualityOrdinal]
        // 放回背包
        if (state.backpackIsFull) {
            val price = calcRingSellPrice(ring.qualityOrdinal)
            state.gold += price; state.totalGoldEarned += price
            addLog("卸下 ${slotName}: ${quality.displayName} → 包满自动售出 +${formatNum(price)}💰")
        } else {
            state.backpackRings.add(DroppedRing(ring.qualityOrdinal, ring.affixes, ring.skill))
            addLog("卸下 ${slotName}: ${quality.displayName} → 已放入背包")
        }
        notifyUI()
        return true
    }
'@
$newUnequipRing = @'
    fun unequipRing(slotIdx: Int): Boolean {
        if (slotIdx !in state.soulRings) { addLog("该槽位没有魂环"); notifyUI(); return false }
        val ring = state.soulRings.remove(slotIdx)!!
        val slotName = SoulRingSlots.get(slotIdx)?.displayName ?: "魂环${slotIdx + 1}"
        val fullName = RingQuality.fullName(ring.yearOrdinal, ring.qualityOrdinal)
        // 放回背包
        if (state.backpackIsFull) {
            val price = calcRingSellPrice(ring.combinedTier)
            state.gold += price; state.totalGoldEarned += price
            addLog("卸下 ${slotName}: ${fullName} → 包满自动售出 +${formatNum(price)}💰")
        } else {
            state.backpackRings.add(DroppedRing(ring.yearOrdinal, ring.qualityOrdinal, ring.affixes, ring.skill))
            addLog("卸下 ${slotName}: ${fullName} → 已放入背包")
        }
        notifyUI()
        return true
    }
'@
$content = $content -replace [regex]::Escape($oldUnequipRing), $newUnequipRing

# 12. Fix unequipBone - full rewrite
$oldUnequipBone = @'
    fun unequipBone(boneTypeOrdinal: Int): Boolean {
        if (boneTypeOrdinal !in state.soulBones) { addLog("该部位没有魂骨"); notifyUI(); return false }
        val bone = state.soulBones.remove(boneTypeOrdinal)!!
        val typeName = BoneType.entries.getOrNull(boneTypeOrdinal)?.displayName ?: "魂骨"
        val rarity = BoneRarity.entries[bone.rarityOrdinal]
        // 放回背包
        if (state.backpackIsFull) {
            val price = calcBoneSellPrice(bone.rarityOrdinal)
            state.gold += price; state.totalGoldEarned += price
            addLog("卸下 ${typeName}: ${rarity.displayName} → 包满自动售出 +${formatNum(price)}💰")
        } else {
            state.backpackBones.add(DroppedBone(boneTypeOrdinal, bone.rarityOrdinal, bone.affixes, bone.passiveSkill))
            addLog("卸下 ${typeName}: ${rarity.displayName} → 已放入背包")
        }
        notifyUI()
        return true
    }
'@
$newUnequipBone = @'
    fun unequipBone(boneTypeOrdinal: Int): Boolean {
        if (boneTypeOrdinal !in state.soulBones) { addLog("该部位没有魂骨"); notifyUI(); return false }
        val bone = state.soulBones.remove(boneTypeOrdinal)!!
        val typeName = BoneType.entries.getOrNull(boneTypeOrdinal)?.displayName ?: "魂骨"
        val fullName = BoneRarity.fullName(bone.yearOrdinal, bone.rarityOrdinal)
        // 放回背包
        if (state.backpackIsFull) {
            val price = calcBoneSellPrice(bone.combinedTier)
            state.gold += price; state.totalGoldEarned += price
            addLog("卸下 ${typeName}: ${fullName} → 包满自动售出 +${formatNum(price)}💰")
        } else {
            state.backpackBones.add(DroppedBone(boneTypeOrdinal, bone.yearOrdinal, bone.rarityOrdinal, bone.affixes, bone.passiveSkill))
            addLog("卸下 ${typeName}: ${fullName} → 已放入背包")
        }
        notifyUI()
        return true
    }
'@
$content = $content -replace [regex]::Escape($oldUnequipBone), $newUnequipBone

# 13. Fix applyAutoSellRings
$content = $content -replace 'r\.qualityOrdinal < th', 'r.combinedTier < th'
$content = $content -replace 'calcRingSellPrice\(r\.qualityOrdinal\)', 'calcRingSellPrice(r.combinedTier)'

# 14. Fix applyAutoSellBones
$content = $content -replace 'b\.rarityOrdinal < th', 'b.combinedTier < th'
$content = $content -replace 'calcBoneSellPrice\(b\.rarityOrdinal\)', 'calcBoneSellPrice(b.combinedTier)'

# 15. Fix toggleAutoSellRings log
$oldToggleRing = 'addLog\("🔄 魂环自动售卖已开启（售出<\$\{RingQuality\.entries\[state\.autoSellRingThreshold\]\.displayName\}）"\)'
$newToggleRing = 'addLog("🔄 魂环自动售卖已开启（售出<Lv.${state.autoSellRingThreshold}）")'
$content = $content -replace $oldToggleRing, $newToggleRing

# 16. Fix toggleAutoSellBones log
$oldToggleBone = 'addLog\("🔄 魂骨自动售卖已开启（售出<\$\{BoneRarity\.entries\[state\.autoSellBoneThreshold\]\.displayName\}）"\)'
$newToggleBone = 'addLog("🔄 魂骨自动售卖已开启（售出<Lv.${state.autoSellBoneThreshold}）")'
$content = $content -replace $oldToggleBone, $newToggleBone

# 17. Fix setAutoSellRingThreshold
$oldSetRingTh = @'
    fun setAutoSellRingThreshold(years: Int) {
        state.autoSellRingThreshold = years.coerceIn(0, RingQuality.entries.size)
        addLog("魂环自动售卖阈值: <${RingQuality.entries.getOrNull(state.autoSellRingThreshold)?.displayName ?: "关闭"}")
        if (state.autoSellRings) applyAutoSellRings()
        notifyUI()
    }
'@
$newSetRingTh = @'
    fun setAutoSellRingThreshold(tier: Int) {
        state.autoSellRingThreshold = tier.coerceIn(0, 24)
        val yearName = if (tier == 0) "关闭" else RingYear.entries[tier / 5].displayName
        val qualName = RingQuality.entries[tier % 5].displayName
        addLog("魂环自动售卖阈值: <${yearName}·${qualName}")
        if (state.autoSellRings) applyAutoSellRings()
        notifyUI()
    }
'@
$content = $content -replace [regex]::Escape($oldSetRingTh), $newSetRingTh

# 18. Fix setAutoSellBoneThreshold
$oldSetBoneTh = @'
    fun setAutoSellBoneThreshold(rarity: Int) {
        state.autoSellBoneThreshold = rarity.coerceIn(0, BoneRarity.entries.size)
        addLog("魂骨自动售卖阈值: <${BoneRarity.entries.getOrNull(state.autoSellBoneThreshold)?.displayName ?: "关闭"}")
        if (state.autoSellBones) applyAutoSellBones()
        notifyUI()
    }
'@
$newSetBoneTh = @'
    fun setAutoSellBoneThreshold(tier: Int) {
        state.autoSellBoneThreshold = tier.coerceIn(0, 24)
        val yearName = if (tier == 0) "关闭" else BoneYear.entries[tier / 5].displayName
        val qualName = BoneRarity.entries[tier % 5].displayName
        addLog("魂骨自动售卖阈值: <${yearName}·${qualName}")
        if (state.autoSellBones) applyAutoSellBones()
        notifyUI()
    }
'@
$content = $content -replace [regex]::Escape($oldSetBoneTh), $newSetBoneTh

# 19. Fix doEquipBackpackRing - full rewrite
$oldEquipRing = @'
    fun doEquipBackpackRing(index: Int): Boolean {
        if (index !in state.backpackRings.indices) { addLog("背包物品不存在"); notifyUI(); return false }
        val dropped = state.backpackRings[index]
        val maxRings = RealmData.maxSoulRings(state.level)
        val qualityOrdinal = dropped.qualityOrdinal

        // 找到可用的空槽位（满足品质要求）
        val emptySlot = SoulRingSlots.all.firstOrNull { sl ->
            sl.index < maxRings && sl.index !in state.soulRings && SoulRingSlots.canEquip(sl.index, qualityOrdinal)
        }
        if (emptySlot != null) {
            state.soulRings[emptySlot.index] = SoulRingInstance(qualityOrdinal, dropped.affixes)
            addLog("${emptySlot.displayName}装备: ${RingQuality.entries[qualityOrdinal].displayName}")
            state.backpackRings.removeAt(index)
            checkAchievements(); notifyUI()
            return true
        }

        // 无空槽位：找同部位（满足品质）中品质最低的替换
        val replaceSlot = state.soulRings.entries
            .filter { SoulRingSlots.canEquip(it.key, qualityOrdinal) }
            .minByOrNull { it.value.qualityOrdinal }
        if (replaceSlot != null && qualityOrdinal > replaceSlot.value.qualityOrdinal) {
            val slotName = SoulRingSlots.get(replaceSlot.key)?.displayName ?: "魂环${replaceSlot.key + 1}"
            val oldQuality = RingQuality.entries[replaceSlot.value.qualityOrdinal]
            state.soulRings[replaceSlot.key] = SoulRingInstance(qualityOrdinal, dropped.affixes)
            addLog("$slotName 替换: ${oldQuality.displayName} → ${RingQuality.entries[qualityOrdinal].displayName}")
            state.backpackRings.removeAt(index)
            checkAchievements(); notifyUI()
            return true
        }

        // 无法装备：槽位年限不足或品质不高于现有
        addLog("没有合适的魂环槽位可装备该${RingQuality.entries[qualityOrdinal].displayName}")
        notifyUI()
        return false
    }
'@
$newEquipRing = @'
    fun doEquipBackpackRing(index: Int): Boolean {
        if (index !in state.backpackRings.indices) { addLog("背包物品不存在"); notifyUI(); return false }
        val dropped = state.backpackRings[index]
        val maxRings = RealmData.maxSoulRings(state.level)
        val combinedTier = dropped.combinedTier
        val fullName = RingQuality.fullName(dropped.yearOrdinal, dropped.qualityOrdinal)

        // 找到可用的空槽位（满足品质要求）
        val emptySlot = SoulRingSlots.all.firstOrNull { sl ->
            sl.index < maxRings && sl.index !in state.soulRings && SoulRingSlots.canEquip(sl.index, combinedTier)
        }
        if (emptySlot != null) {
            state.soulRings[emptySlot.index] = SoulRingInstance(dropped.yearOrdinal, dropped.qualityOrdinal, dropped.affixes, dropped.skill)
            addLog("${emptySlot.displayName}装备: ${fullName}")
            state.backpackRings.removeAt(index)
            checkAchievements(); notifyUI()
            return true
        }

        // 无空槽位：找同部位（满足品质）中品质最低的替换
        val replaceSlot = state.soulRings.entries
            .filter { SoulRingSlots.canEquip(it.key, combinedTier) }
            .minByOrNull { it.value.combinedTier }
        if (replaceSlot != null && combinedTier > replaceSlot.value.combinedTier) {
            val slotName = SoulRingSlots.get(replaceSlot.key)?.displayName ?: "魂环${replaceSlot.key + 1}"
            val oldFullName = RingQuality.fullName(replaceSlot.value.yearOrdinal, replaceSlot.value.qualityOrdinal)
            state.soulRings[replaceSlot.key] = SoulRingInstance(dropped.yearOrdinal, dropped.qualityOrdinal, dropped.affixes, dropped.skill)
            addLog("$slotName 替换: ${oldFullName} → ${fullName}")
            state.backpackRings.removeAt(index)
            checkAchievements(); notifyUI()
            return true
        }

        // 无法装备：槽位年限不足或品质不高于现有
        addLog("没有合适的魂环槽位可装备该${fullName}")
        notifyUI()
        return false
    }
'@
$content = $content -replace [regex]::Escape($oldEquipRing), $newEquipRing

# 20. Fix doEquipBackpackBone - full rewrite
$oldEquipBoneBP = @'
    fun doEquipBackpackBone(index: Int): Boolean {
        if (index !in state.backpackBones.indices) { addLog("背包物品不存在"); notifyUI(); return false }
        val dropped = state.backpackBones[index]
        val maxBones = RealmData.maxSoulBones(state.level)
        if (state.soulBones.size >= maxBones && !state.soulBones.containsKey(dropped.boneTypeOrdinal)) {
            addLog("当前境界最多装备 $maxBones 块魂骨"); notifyUI(); return false
        }
        state.soulBones[dropped.boneTypeOrdinal] = SoulBoneInstance(dropped.rarityOrdinal, dropped.affixes)
        val boneName = BoneType.entries[dropped.boneTypeOrdinal].displayName
        addLog("装备魂骨: ${BoneRarity.entries[dropped.rarityOrdinal].displayName}（$boneName）")
        state.backpackBones.removeAt(index)
        notifyUI()
        return true
    }
'@
$newEquipBoneBP = @'
    fun doEquipBackpackBone(index: Int): Boolean {
        if (index !in state.backpackBones.indices) { addLog("背包物品不存在"); notifyUI(); return false }
        val dropped = state.backpackBones[index]
        val maxBones = RealmData.maxSoulBones(state.level)
        if (state.soulBones.size >= maxBones && !state.soulBones.containsKey(dropped.boneTypeOrdinal)) {
            addLog("当前境界最多装备 $maxBones 块魂骨"); notifyUI(); return false
        }
        state.soulBones[dropped.boneTypeOrdinal] = SoulBoneInstance(dropped.yearOrdinal, dropped.rarityOrdinal, dropped.affixes, passiveSkill = dropped.passiveSkill)
        val boneName = BoneType.entries[dropped.boneTypeOrdinal].displayName
        val fullName = BoneRarity.fullName(dropped.yearOrdinal, dropped.rarityOrdinal)
        addLog("装备魂骨: ${fullName}（$boneName）")
        state.backpackBones.removeAt(index)
        notifyUI()
        return true
    }
'@
$content = $content -replace [regex]::Escape($oldEquipBoneBP), $newEquipBoneBP

# 21. Fix drop section (onBattleWin) - full rewrite of ring+bone drop blocks
$oldDropSection = @'
        // V4: 阶梯式品质掉落 — 品质越高概率越低, Boss/精英加成, 地图限制品质上限
        fun ringDropChance(q: Int, boss: Boolean, elite: Boolean): Float {
            if (q > maxRingQ) return 0f  // 超过地图品质上限不掉落
            val base = when (q) {
                0 -> 0.45f; 1 -> 0.22f; 2 -> 0.10f; 3 -> 0.03f; 4 -> 0.008f; else -> 0f
            }
            val bossMult = if (boss && q >= 2) 2.0f else if (elite && q <= 2) 1.5f else 1.0f
            val mapMult = 1.0f + mapId * 0.06f
            return (base * bossMult * mapMult).coerceAtMost(0.55f)
        }
        fun boneDropChance(q: Int, boss: Boolean, elite: Boolean): Float {
            if (q > maxBoneQ) return 0f
            val base = when (q) {
                0 -> 0.25f; 1 -> 0.12f; 2 -> 0.05f; 3 -> 0.015f; 4 -> 0.004f; else -> 0f
            }
            val bossMult = if (boss && q >= 2) 2.2f else if (elite && q <= 2) 1.5f else 1.0f
            val mapMult = 1.0f + mapId * 0.05f
            return (base * bossMult * mapMult).coerceAtMost(0.40f)
        }

        // V4: 魂环掉落 — 阶梯品质+精英/Boss加成
        val isEliteStage = StageData.getStageType(s.currentStage) == StageType.ELITE
        val ringQuality = DropDistribution.rollQuality(mean, DropDistribution.QUALITY_STD_DEV, maxRingQ)
        if (Random.nextFloat() < ringDropChance(ringQuality, isBossStage, isEliteStage)) {
            val quality = ringQuality
            val affixes = SoulRingGenerator.generateRandomAffixes(quality)
            val skill = ActiveSkillPool.randomSkill(quality)
            val dropped = DroppedRing(quality, affixes, skill)
            if (s.backpackIsFull) {
                val price = calcRingSellPrice(quality)
                s.gold += price; s.totalGoldEarned += price
                addLog("`$`💍 掉落: `$`{RingQuality.entries[quality].displayName}[`$`{skill.name}] → 包满自动售出 +`$`{formatNum(price)}💰")
            } else {
                s.backpackRings.add(dropped)
                addLog("`$`💍 掉落: `$`{RingQuality.entries[quality].displayName}[`$`{skill.name}] (包`$`{s.backpackTotalItems}/`$`{s.backpackCapacity})")
                if (s.autoSellRings && quality < s.autoSellRingThreshold) applyAutoSellRings()
            }
        }

        // V4: 魂骨掉落 — 阶梯品质+精英/Boss加成
        val boneQuality = DropDistribution.rollQuality(mean - 0.5, DropDistribution.QUALITY_STD_DEV, maxBoneQ)
        if (Random.nextFloat() < boneDropChance(boneQuality, isBossStage, isEliteStage)) {
            val quality = boneQuality
            val boneType = Random.nextInt(BoneType.entries.size)
            val affixes = SoulBoneGenerator.generateRandomAffix(quality)
            val passive = PassiveSkillPool.randomPassive(boneType, quality)
            val dropped = DroppedBone(boneType, quality, affixes, passive)
            if (s.backpackIsFull) {
                val price = calcBoneSellPrice(quality)
                s.gold += price; s.totalGoldEarned += price
                addLog("`$`🦴 掉落: `$`{BoneRarity.entries[quality].displayName}[`$`{passive.name}] → 包满自动售出 +`$`{formatNum(price)}💰")
            } else {
                s.backpackBones.add(dropped)
                addLog("`$`🦴 掉落: `$`{BoneRarity.entries[quality].displayName}(`$`{BoneType.entries[boneType].displayName})[`$`{passive.name}] (包`$`{s.backpackTotalItems}/`$`{s.backpackCapacity})")
                if (s.autoSellBones && quality < s.autoSellBoneThreshold) applyAutoSellBones()
            }
        }
'@
$newDropSection = @'
        // V5: 双维度掉落(年份×品质) — 25档制, Boss/精英加成, 地图限制品质上限
        fun ringDropChance(q: Int, boss: Boolean, elite: Boolean): Float {
            if (q > maxRingQ) return 0f
            val base = when (q / 5) {
                0 -> 0.45f; 1 -> 0.22f; 2 -> 0.10f; 3 -> 0.03f; 4 -> 0.008f; else -> 0f
            }
            val bossMult = if (boss && q / 5 >= 1) 2.0f else if (elite && q / 5 <= 1) 1.5f else 1.0f
            val mapMult = 1.0f + mapId * 0.06f
            return (base * bossMult * mapMult).coerceAtMost(0.55f)
        }
        fun boneDropChance(q: Int, boss: Boolean, elite: Boolean): Float {
            if (q > maxBoneQ) return 0f
            val base = when (q / 5) {
                0 -> 0.25f; 1 -> 0.12f; 2 -> 0.05f; 3 -> 0.015f; 4 -> 0.004f; else -> 0f
            }
            val bossMult = if (boss && q / 5 >= 1) 2.2f else if (elite && q / 5 <= 1) 1.5f else 1.0f
            val mapMult = 1.0f + mapId * 0.05f
            return (base * bossMult * mapMult).coerceAtMost(0.40f)
        }

        // V5: 魂环掉落 — 双维度+精英/Boss加成
        val isEliteStage = StageData.getStageType(s.currentStage) == StageType.ELITE
        val ringTier = DropDistribution.rollQuality(mean, DropDistribution.QUALITY_STD_DEV, maxRingQ)
        if (Random.nextFloat() < ringDropChance(ringTier, isBossStage, isEliteStage)) {
            val rYear = RingYear.yearOf(ringTier)
            val rQuality = RingYear.qualityOf(ringTier)
            val affixes = SoulRingGenerator.generateRandomAffixes(ringTier)
            val skill = ActiveSkillPool.randomSkill(ringTier)
            val dropped = DroppedRing(rYear, rQuality, affixes, skill)
            if (s.backpackIsFull) {
                val price = calcRingSellPrice(ringTier)
                s.gold += price; s.totalGoldEarned += price
                addLog("`$`💍 掉落: `$`{RingQuality.fullName(rYear, rQuality)}[`$`{skill.name}] → 包满自动售出 +`$`{formatNum(price)}💰")
            } else {
                s.backpackRings.add(dropped)
                addLog("`$`💍 掉落: `$`{RingQuality.fullName(rYear, rQuality)}[`$`{skill.name}] (包`$`{s.backpackTotalItems}/`$`{s.backpackCapacity})")
                if (s.autoSellRings && ringTier < s.autoSellRingThreshold) applyAutoSellRings()
            }
        }

        // V5: 魂骨掉落 — 双维度+精英/Boss加成
        val boneTier = DropDistribution.rollQuality(mean - 0.5, DropDistribution.QUALITY_STD_DEV, maxBoneQ)
        if (Random.nextFloat() < boneDropChance(boneTier, isBossStage, isEliteStage)) {
            val bYear = BoneYear.yearOf(boneTier)
            val bRarity = BoneYear.qualityOf(boneTier)
            val boneType = Random.nextInt(BoneType.entries.size)
            val affixes = SoulBoneGenerator.generateRandomAffix(boneTier)
            val passive = PassiveSkillPool.randomPassive(boneType, boneTier)
            val dropped = DroppedBone(boneType, bYear, bRarity, affixes, passive)
            if (s.backpackIsFull) {
                val price = calcBoneSellPrice(boneTier)
                s.gold += price; s.totalGoldEarned += price
                addLog("`$`🦴 掉落: `$`{BoneRarity.fullName(bYear, bRarity)}[`$`{passive.name}] → 包满自动售出 +`$`{formatNum(price)}💰")
            } else {
                s.backpackBones.add(dropped)
                addLog("`$`🦴 掉落: `$`{BoneRarity.fullName(bYear, bRarity)}(`$`{BoneType.entries[boneType].displayName})[`$`{passive.name}] (包`$`{s.backpackTotalItems}/`$`{s.backpackCapacity})")
                if (s.autoSellBones && boneTier < s.autoSellBoneThreshold) applyAutoSellBones()
            }
        }
'@
$content = $content -replace [regex]::Escape($oldDropSection), $newDropSection

# 22. Fix doAbsorbSoulRing - parameter grade:Int -> combinedTier:Int
$oldAbsorb = @'
    fun doAbsorbSoulRing(grade: Int): Boolean {
        val maxRings = RealmData.maxSoulRings(state.level)
        if (state.soulRings.size >= maxRings) { addLog("当前境界最多拥有 `$maxRings 个魂环"); notifyUI(); return false }
        if (grade !in RingQuality.entries.indices) { addLog("无效的魂环品质"); notifyUI(); return false }
        // 找到下一个空槽位
        val nextSlot = SoulRingSlots.all.firstOrNull { sl ->
            sl.index !in state.soulRings && sl.index < maxRings
        } ?: run { addLog("没有可用槽位"); notifyUI(); return false }
        // 检查年限是否满足槽位最低要求
        if (!SoulRingSlots.canEquip(nextSlot.index, grade)) {
            val minQ = RingQuality.entries[nextSlot.minQualityOrdinal]
            addLog("`$`{nextSlot.displayName}需要`$`{minQ.displayName}或以上"); notifyUI(); return false
        }
        val quality = RingQuality.entries[grade]
        val cost = quality.cost(state.soulRings.size)
        if (state.gold < cost) { addLog("金魂币不足，需要 `$`{formatNum(cost)}"); notifyUI(); return false }
        state.gold -= cost
        val affixes = SoulRingGenerator.generateRandomAffixes(grade)
        state.soulRings[nextSlot.index] = SoulRingInstance(grade, affixes)
        val affixStr = affixes.joinToString(" ") { "`$`{it.type.displayName}+`$`{it.value}" }
        addLog("`$`{nextSlot.displayName}吸收`$`{quality.displayName}成功！属性: `$`affixStr")
        if (state.tutorialStep == 4) advanceTutorial()
        checkAchievements(); notifyUI()
        return true
    }
'@
$newAbsorb = @'
    fun doAbsorbSoulRing(combinedTier: Int): Boolean {
        val maxRings = RealmData.maxSoulRings(state.level)
        if (state.soulRings.size >= maxRings) { addLog("当前境界最多拥有 `$maxRings 个魂环"); notifyUI(); return false }
        if (combinedTier !in 0..24) { addLog("无效的魂环等级"); notifyUI(); return false }
        // 找到下一个空槽位
        val nextSlot = SoulRingSlots.all.firstOrNull { sl ->
            sl.index !in state.soulRings && sl.index < maxRings
        } ?: run { addLog("没有可用槽位"); notifyUI(); return false }
        // 检查合并等级是否满足槽位最低要求
        if (!SoulRingSlots.canEquip(nextSlot.index, combinedTier)) {
            val minYear = RingYear.entries[nextSlot.minYearOrdinal]
            val minQual = RingQuality.entries[nextSlot.minQualityOrdinal]
            addLog("`$`{nextSlot.displayName}需要`$`{minYear.displayName}·`$`{minQual.displayName}或以上"); notifyUI(); return false
        }
        val yearOrd = RingYear.yearOf(combinedTier)
        val qualOrd = RingYear.qualityOf(combinedTier)
        val fullName = RingQuality.fullName(yearOrd, qualOrd)
        val cost = RingYear.entries[yearOrd].costBase * (1.0 + state.soulRings.size * 0.5).toLong()
        if (state.gold < cost) { addLog("金魂币不足，需要 `$`{formatNum(cost)}"); notifyUI(); return false }
        state.gold -= cost
        val affixes = SoulRingGenerator.generateRandomAffixes(combinedTier)
        val skill = ActiveSkillPool.randomSkill(combinedTier)
        state.soulRings[nextSlot.index] = SoulRingInstance(yearOrd, qualOrd, affixes, skill)
        val affixStr = affixes.joinToString(" ") { "`$`{it.type.displayName}+`$`{it.value}" }
        addLog("`$`{nextSlot.displayName}吸收`$`{fullName}成功！属性: `$`affixStr")
        if (state.tutorialStep == 4) advanceTutorial()
        checkAchievements(); notifyUI()
        return true
    }
'@
$content = $content -replace [regex]::Escape($oldAbsorb), $newAbsorb

# 23. Fix getNextSoulRingGrade
$oldNextGrade = @'
    fun getNextSoulRingGrade(): Int {
        val maxRings = RealmData.maxSoulRings(state.level)
        val nextSlot = SoulRingSlots.all.firstOrNull { sl ->
            sl.index !in state.soulRings && sl.index < maxRings
        } ?: return RingQuality.entries.size - 1
        return nextSlot.minQualityOrdinal.coerceAtMost(RingQuality.entries.size - 1)
    }
'@
$newNextGrade = @'
    fun getNextSoulRingGrade(): Int {
        val maxRings = RealmData.maxSoulRings(state.level)
        val nextSlot = SoulRingSlots.all.firstOrNull { sl ->
            sl.index !in state.soulRings && sl.index < maxRings
        } ?: return 24
        return nextSlot.minCombinedTier.coerceAtMost(24)
    }
'@
$content = $content -replace [regex]::Escape($oldNextGrade), $newNextGrade

# 24. Fix doEquipBone - rarityOrdinal:int -> combinedTier:int
$oldEquipBone = @'
    fun doEquipBone(boneTypeOrdinal: Int, rarityOrdinal: Int): Boolean {
        val maxBones = RealmData.maxSoulBones(state.level)
        if (state.soulBones.size >= maxBones && !state.soulBones.containsKey(boneTypeOrdinal)) {
            addLog("当前境界最多装备 `$maxBones 块魂骨"); notifyUI(); return false
        }
        if (rarityOrdinal !in BoneRarity.entries.indices) return false
        val rarity = BoneRarity.entries[rarityOrdinal]
        val currentBone = state.soulBones[boneTypeOrdinal]
        val isReplace = currentBone != null
        if (state.gold < rarity.cost) { addLog("金魂币不足，需要 `$`{formatNum(rarity.cost)}"); notifyUI(); return false }
        state.gold -= rarity.cost
        val affixes = SoulBoneGenerator.generateRandomAffix(rarityOrdinal)
        state.soulBones[boneTypeOrdinal] = SoulBoneInstance(rarityOrdinal, affixes)
        val boneName = BoneType.entries[boneTypeOrdinal].displayName
        val affixStr = affixes.joinToString(" ") { "`$`{it.type.displayName}+`$`{it.value}" }
        val actionStr = if (isReplace) "替换" else "装备"
        addLog("`$actionStr `$`{rarity.displayName}（`$bonename）随机属性: `$affixStr"); notifyUI()
        return true
    }
'@
$newEquipBone = @'
    fun doEquipBone(boneTypeOrdinal: Int, combinedTier: Int): Boolean {
        val maxBones = RealmData.maxSoulBones(state.level)
        if (state.soulBones.size >= maxBones && !state.soulBones.containsKey(boneTypeOrdinal)) {
            addLog("当前境界最多装备 `$maxBones 块魂骨"); notifyUI(); return false
        }
        if (combinedTier !in 0..24) return false
        val yearOrd = BoneYear.yearOf(combinedTier)
        val rarityOrd = BoneYear.qualityOf(combinedTier)
        val fullName = BoneRarity.fullName(yearOrd, rarityOrd)
        val cost = BoneYear.entries[yearOrd].costBase
        val currentBone = state.soulBones[boneTypeOrdinal]
        val isReplace = currentBone != null
        if (state.gold < cost) { addLog("金魂币不足，需要 `$`{formatNum(cost)}"); notifyUI(); return false }
        state.gold -= cost
        val affixes = SoulBoneGenerator.generateRandomAffix(combinedTier)
        val passive = PassiveSkillPool.randomPassive(boneTypeOrdinal, combinedTier)
        state.soulBones[boneTypeOrdinal] = SoulBoneInstance(yearOrd, rarityOrd, affixes, passiveSkill = passive)
        val boneName = BoneType.entries[boneTypeOrdinal].displayName
        val affixStr = affixes.joinToString(" ") { "`$`{it.type.displayName}+`$`{it.value}" }
        val actionStr = if (isReplace) "替换" else "装备"
        addLog("`$actionStr `$`{fullName}（`$bonename）随机属性: `$affixStr"); notifyUI()
        return true
    }
'@
$content = $content -replace [regex]::Escape($oldEquipBone), $newEquipBone

# 25. Fix doBuyBossRing
$oldBuyBossRing = @'
    fun doBuyBossRing(qualityOrdinal: Int): Boolean {
        val s = state
        val item = BossShopData.ringItems.find { it.yearOrdinal == qualityOrdinal }
            ?: return false.also { addLog("商品不存在"); notifyUI() }
        if (!BossShopData.isTierUnlocked(item.tier, s.currentMapId)) {
            addLog("🔒 `$`{item.tier.label}阶梯未解锁（需进入`$`{item.tier.description}）"); notifyUI(); return false
        }
        if (s.bossCoin < item.cost) { addLog("Boss币不足，需要 `$`{item.cost} Boss币"); notifyUI(); return false }
        s.bossCoin -= item.cost
        val affixes = SoulRingGenerator.generateRandomAffixes(qualityOrdinal)
        val skill = ActiveSkillPool.randomSkill(qualityOrdinal)
        val ring = DroppedRing(qualityOrdinal, affixes, skill)
        val qualityName = RingQuality.entries[qualityOrdinal].displayName
        if (s.backpackIsFull) {
            val price = calcRingSellPrice(qualityOrdinal)
            s.gold += price; s.totalGoldEarned += price
            addLog("💍 `$`{qualityName}魂环 → 包满自动售出 +`$`{formatNum(price)}💰")
        } else {
            s.backpackRings.add(ring)
            addLog("💍 购买`$`{qualityName}魂环 [`$`{skill.name}] (包`$`{s.backpackTotalItems}/`$`{s.backpackCapacity})")
        }
        notifyUI(); return true
    }
'@
$newBuyBossRing = @'
    fun doBuyBossRing(combinedTier: Int): Boolean {
        val s = state
        val item = BossShopData.ringItems.find { it.combinedTier == combinedTier }
            ?: return false.also { addLog("商品不存在"); notifyUI() }
        if (!BossShopData.isTierUnlocked(item.tier, s.currentMapId)) {
            addLog("🔒 `$`{item.tier.label}阶梯未解锁（需进入`$`{item.tier.description}）"); notifyUI(); return false
        }
        if (s.bossCoin < item.cost) { addLog("Boss币不足，需要 `$`{item.cost} Boss币"); notifyUI(); return false }
        s.bossCoin -= item.cost
        val yearOrd = RingYear.yearOf(combinedTier)
        val qualOrd = RingYear.qualityOf(combinedTier)
        val fullName = RingQuality.fullName(yearOrd, qualOrd)
        val affixes = SoulRingGenerator.generateRandomAffixes(combinedTier)
        val skill = ActiveSkillPool.randomSkill(combinedTier)
        val ring = DroppedRing(yearOrd, qualOrd, affixes, skill)
        if (s.backpackIsFull) {
            val price = calcRingSellPrice(combinedTier)
            s.gold += price; s.totalGoldEarned += price
            addLog("💍 `$`{fullName} → 包满自动售出 +`$`{formatNum(price)}💰")
        } else {
            s.backpackRings.add(ring)
            addLog("💍 购买`$`{fullName} [`$`{skill.name}] (包`$`{s.backpackTotalItems}/`$`{s.backpackCapacity})")
        }
        notifyUI(); return true
    }
'@
$content = $content -replace [regex]::Escape($oldBuyBossRing), $newBuyBossRing

# 26. Fix doBuyBossBone
$oldBuyBossBone = @'
    fun doBuyBossBone(rarityOrdinal: Int): Boolean {
        val s = state
        val item = BossShopData.boneItems.find { it.rarityOrdinal == rarityOrdinal }
            ?: return false.also { addLog("商品不存在"); notifyUI() }
        if (!BossShopData.isTierUnlocked(item.tier, s.currentMapId)) {
            addLog("🔒 `$`{item.tier.label}阶梯未解锁（需进入`$`{item.tier.description}）"); notifyUI(); return false
        }
        if (s.bossCoin < item.cost) { addLog("Boss币不足，需要 `$`{item.cost} Boss币"); notifyUI(); return false }
        s.bossCoin -= item.cost
        val boneType = Random.nextInt(BoneType.entries.size)
        val affixes = SoulBoneGenerator.generateRandomAffix(rarityOrdinal)
        val passive = PassiveSkillPool.randomPassive(boneType, rarityOrdinal)
        val bone = DroppedBone(boneType, rarityOrdinal, affixes, passive)
        val rarityName = BoneRarity.entries[rarityOrdinal].displayName
        if (s.backpackIsFull) {
            val price = calcBoneSellPrice(rarityOrdinal)
            s.gold += price; s.totalGoldEarned += price
            addLog("🦴 `$`{rarityName} → 包满自动售出 +`$`{formatNum(price)}💰")
        } else {
            s.backpackBones.add(bone)
            addLog("🦴 购买`$`{rarityName}(`$`{BoneType.entries[boneType].displayName})[`$`{passive.name}] (包`$`{s.backpackTotalItems}/`$`{s.backpackCapacity})")
        }
        notifyUI(); return true
    }
'@
$newBuyBossBone = @'
    fun doBuyBossBone(combinedTier: Int): Boolean {
        val s = state
        val item = BossShopData.boneItems.find { it.combinedTier == combinedTier }
            ?: return false.also { addLog("商品不存在"); notifyUI() }
        if (!BossShopData.isTierUnlocked(item.tier, s.currentMapId)) {
            addLog("🔒 `$`{item.tier.label}阶梯未解锁（需进入`$`{item.tier.description}）"); notifyUI(); return false
        }
        if (s.bossCoin < item.cost) { addLog("Boss币不足，需要 `$`{item.cost} Boss币"); notifyUI(); return false }
        s.bossCoin -= item.cost
        val boneType = Random.nextInt(BoneType.entries.size)
        val yearOrd = BoneYear.yearOf(combinedTier)
        val rarityOrd = BoneYear.qualityOf(combinedTier)
        val fullName = BoneRarity.fullName(yearOrd, rarityOrd)
        val affixes = SoulBoneGenerator.generateRandomAffix(combinedTier)
        val passive = PassiveSkillPool.randomPassive(boneType, combinedTier)
        val bone = DroppedBone(boneType, yearOrd, rarityOrd, affixes, passive)
        if (s.backpackIsFull) {
            val price = calcBoneSellPrice(combinedTier)
            s.gold += price; s.totalGoldEarned += price
            addLog("🦴 `$`{fullName} → 包满自动售出 +`$`{formatNum(price)}💰")
        } else {
            s.backpackBones.add(bone)
            addLog("🦴 购买`$`{fullName}(`$`{BoneType.entries[boneType].displayName})[`$`{passive.name}] (包`$`{s.backpackTotalItems}/`$`{s.backpackCapacity})")
        }
        notifyUI(); return true
    }
'@
$content = $content -replace [regex]::Escape($oldBuyBossBone), $newBuyBossBone

# 27. Fix doBuy100KRing / doBuyDivineBone
$content = $content -replace 'fun doBuy100KRing(): Boolean = doBuyBossRing(3)', 'fun doBuy100KRing(): Boolean = doBuyBossRing(15)'
$content = $content -replace 'fun doBuyDivineBone(): Boolean = doBuyBossBone(3)', 'fun doBuyDivineBone(): Boolean = doBuyBossBone(15)'

# 28. Fix buyLimitedShopRing
$oldBuyLmtRing = @'
    fun buyLimitedShopRing(index: Int): Boolean {
        refreshLimitedShop()
        if (index !in state.limitedShopRings.indices) { addLog("物品不存在"); notifyUI(); return false }
        val ring = state.limitedShopRings[index]
        val price = LimitedShopData.ringBuyPrice(ring.qualityOrdinal)
        if (state.gold < price) { addLog("金魂币不足，需要 `$`{formatNum(price)}"); notifyUI(); return false }
        state.gold -= price
        val dropped = state.limitedShopRings.removeAt(index)
        val quality = RingQuality.entries[dropped.qualityOrdinal]
        val skillName = dropped.skill?.name ?: "无技能"
        state.backpackRings.add(dropped)
        addLog("💍 购买 `$`{quality.displayName}[`$`{skillName}] 花费 `$`{formatNum(price)}💰 (包`$`{state.backpackTotalItems}/`$`{state.backpackCapacity})")
        notifyUI(); return true
    }
'@
$newBuyLmtRing = @'
    fun buyLimitedShopRing(index: Int): Boolean {
        refreshLimitedShop()
        if (index !in state.limitedShopRings.indices) { addLog("物品不存在"); notifyUI(); return false }
        val ring = state.limitedShopRings[index]
        val price = LimitedShopData.ringBuyPrice(ring.combinedTier)
        if (state.gold < price) { addLog("金魂币不足，需要 `$`{formatNum(price)}"); notifyUI(); return false }
        state.gold -= price
        val dropped = state.limitedShopRings.removeAt(index)
        val fullName = RingQuality.fullName(dropped.yearOrdinal, dropped.qualityOrdinal)
        val skillName = dropped.skill?.name ?: "无技能"
        state.backpackRings.add(dropped)
        addLog("💍 购买 `$`{fullName}[`$`{skillName}] 花费 `$`{formatNum(price)}💰 (包`$`{state.backpackTotalItems}/`$`{state.backpackCapacity})")
        notifyUI(); return true
    }
'@
$content = $content -replace [regex]::Escape($oldBuyLmtRing), $newBuyLmtRing

# 29. Fix buyLimitedShopBone
$oldBuyLmtBone = @'
    fun buyLimitedShopBone(index: Int): Boolean {
        refreshLimitedShop()
        if (index !in state.limitedShopBones.indices) { addLog("物品不存在"); notifyUI(); return false }
        val bone = state.limitedShopBones[index]
        val price = LimitedShopData.boneBuyPrice(bone.rarityOrdinal)
        if (state.gold < price) { addLog("金魂币不足，需要 `$`{formatNum(price)}"); notifyUI(); return false }
        state.gold -= price
        val dropped = state.limitedShopBones.removeAt(index)
        val rarity = BoneRarity.entries[dropped.rarityOrdinal]
        val typeName = BoneType.entries.getOrNull(dropped.boneTypeOrdinal)?.displayName ?: "魂骨"
        state.backpackBones.add(dropped)
        addLog("🦴 购买 `$`{rarity.displayName}(`$`typeName) 花费 `$`{formatNum(price)}💰 (包`$`{state.backpackTotalItems}/`$`{state.backpackCapacity})")
        notifyUI(); return true
    }
'@
$newBuyLmtBone = @'
    fun buyLimitedShopBone(index: Int): Boolean {
        refreshLimitedShop()
        if (index !in state.limitedShopBones.indices) { addLog("物品不存在"); notifyUI(); return false }
        val bone = state.limitedShopBones[index]
        val price = LimitedShopData.boneBuyPrice(bone.combinedTier)
        if (state.gold < price) { addLog("金魂币不足，需要 `$`{formatNum(price)}"); notifyUI(); return false }
        state.gold -= price
        val dropped = state.limitedShopBones.removeAt(index)
        val fullName = BoneRarity.fullName(dropped.yearOrdinal, dropped.rarityOrdinal)
        val typeName = BoneType.entries.getOrNull(dropped.boneTypeOrdinal)?.displayName ?: "魂骨"
        state.backpackBones.add(dropped)
        addLog("🦴 购买 `$`{fullName}(`$`typeName) 花费 `$`{formatNum(price)}💰 (包`$`{state.backpackTotalItems}/`$`{state.backpackCapacity})")
        notifyUI(); return true
    }
'@
$content = $content -replace [regex]::Escape($oldBuyLmtBone), $newBuyLmtBone

# Save
[System.IO.File]::WriteAllText($file, $content, [System.Text.Encoding]::UTF8)
Write-Host "GameEngine.kt updated successfully!"
