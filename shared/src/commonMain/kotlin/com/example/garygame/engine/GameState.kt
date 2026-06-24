package com.example.garygame.engine

import com.example.garygame.model.*
import com.example.garygame.platform.*

class GameState(private val storage: PlatformStorage) {

    var level: Int = 1
    var gold: Long = 0
    var soulPower: Long = 0
    var martialSoul: MartialSoul? = null
    var chosenSchool: SoulSchool? = null
    var soulRings: MutableMap<Int, SoulRingInstance> = mutableMapOf()
    var soulBones: MutableMap<Int, SoulBoneInstance> = mutableMapOf()
    var currentHp: Long = 100
    var currentMapId: Int = 0
    var currentFloor: Int = 1
    var inBattle: Boolean = false
    var totalBattleWins: Long = 0
    var totalBattleLosses: Long = 0
    var shopLevels: MutableMap<Int, Int> = mutableMapOf()
    var towerFloor: Int = 0
    var towerBossKills: Int = 0
    var towerTempHp: Long = 0
    var towerInRun: Boolean = false
    var killingIntent: Int = 0
    val unlockedTowerTitles: MutableSet<String> = mutableSetOf()
    val towerAttributeLevels: MutableMap<String, Int> = mutableMapOf()
    var lastDungeonTime: Long = 0
    var dungeonTierCompleted: Int = -1
    var totalGoldEarned: Long = 0
    var unlockedAchievements: MutableSet<String> = mutableSetOf()
    var prestigeCount: Int = 0
    var talentPoints: Int = 0
    val talentLevels: MutableMap<TalentBranch, Int> = TalentBranch.entries.associateWith { 0 }.toMutableMap()

    fun getTalentLevel(branch: TalentBranch): Int = talentLevels[branch] ?: 0
    fun setTalentLevel(branch: TalentBranch, level: Int) {
        talentLevels[branch] = level.coerceIn(0, branch.maxLevel)
    }

    var lastExitTime: Long = 0
    val maxOfflineSec = 43200L
    var autoBattle: Boolean = false
    var battleSpeed: Int = 1
    var autoAdvanceMap: Boolean = true
    var skillCooldown: Int = 0
    var bossCoin: Long = 0
    var equippedSoulCores: MutableMap<SoulCoreSlotType, SoulCoreInstance?> = mutableMapOf(
        SoulCoreSlotType.ATTACK to null,
        SoulCoreSlotType.DEFENSE to null,
        SoulCoreSlotType.UTILITY to null
    )
    var currentStage: Int = 1
    var autoBreakthrough: Boolean = true
    val backpackSoulCores: MutableList<DroppedSoulCore> = mutableListOf()
    var battleSoulPower: Int = 100
    var activeSkillCooldowns: MutableMap<Int, Int> = mutableMapOf()
    var currentMonsters: MutableList<Monster> = mutableListOf()
    var currentMonsterTarget: Int = 0
    var codexKills: Long = 0
    var currentMonster: Monster? = null
    var battleRound: Int = 0
    var smallKillStreak: Int = 0
    var bossDeathCount: Int = 0
    val mapStars: MutableMap<Int, Int> = mutableMapOf()
    val backpackRings: MutableList<DroppedRing> = mutableListOf()
    val backpackBones: MutableList<DroppedBone> = mutableListOf()
    var limitedShopRefreshTime: Long = 0L
    val limitedShopRings: MutableList<DroppedRing> = mutableListOf()
    val limitedShopBones: MutableList<DroppedBone> = mutableListOf()
    var backpackCapacityTier: Int = 0
    var freeExpandRemaining: Int = 0
    val backpackCapacity: Int get() = BASE_BACKPACK_CAPACITY + backpackCapacityTier * BACKPACK_EXPAND_SLOTS
    val backpackTotalItems: Int get() = backpackRings.size + backpackBones.size + backpackSoulCores.size
    val backpackIsFull: Boolean get() = backpackTotalItems >= backpackCapacity
    var coreReviveCharges: Int = 0
    val lockedBackpackRingIndices: MutableSet<Int> = mutableSetOf()
    val lockedBackpackBoneIndices: MutableSet<Int> = mutableSetOf()

    fun isRingLocked(index: Int): Boolean = lockedBackpackRingIndices.contains(index)
    fun isBoneLocked(index: Int): Boolean = lockedBackpackBoneIndices.contains(index)

    fun toggleRingLock(index: Int): Boolean {
        return if (lockedBackpackRingIndices.contains(index)) {
            lockedBackpackRingIndices.remove(index); false
        } else { lockedBackpackRingIndices.add(index); true }
    }
    fun toggleBoneLock(index: Int): Boolean {
        return if (lockedBackpackBoneIndices.contains(index)) {
            lockedBackpackBoneIndices.remove(index); false
        } else { lockedBackpackBoneIndices.add(index); true }
    }

    var autoSellRings: Boolean = false
    var autoSellBones: Boolean = false
    var autoSellRingThreshold: Int = 1
    var autoSellBoneThreshold: Int = 0
    val ringFilterYears: MutableSet<Int> = mutableSetOf()
    val ringFilterQualities: MutableSet<Int> = mutableSetOf()
    val boneFilterYears: MutableSet<Int> = mutableSetOf()
    val boneFilterRarities: MutableSet<Int> = mutableSetOf()
    val soulCoreFilterRarities: MutableSet<Int> = mutableSetOf()
    val soulCoreFilterCategories: MutableSet<SoulCoreCategory> = mutableSetOf()
    val disabledAutoSkillSlots: MutableSet<Int> = mutableSetOf()
    var isLevelCapped: Boolean = false
    var excessSoulPower: Long = 0L
    val capStatLevels: MutableMap<String, Int> = mutableMapOf()
    var skipCultivateConfirm: Boolean = false
    var skipBreakthroughConfirm: Boolean = false
    var skipAwakenConfirm: Boolean = false
    var skipPrestigeConfirm: Boolean = false
    var skipNotifyBossPurchase: Boolean = false
    var skipNotifyShopPurchase: Boolean = false
    var skipNotifyHighDrop: Boolean = false
    var tutorialStep: Int = 1
    val unlockedFeatureLog: MutableSet<String> = mutableSetOf()

    fun getSnapshot(): GameSnapshot = GameSnapshot(
        level = level,
        soulRings = soulRings.size,
        totalBattleWins = totalBattleWins,
        towerFloor = towerFloor,
        prestigeCount = prestigeCount,
        totalGold = totalGoldEarned
    )

    companion object {
        const val BASE_BACKPACK_CAPACITY = 20
        const val BACKPACK_EXPAND_SLOTS = 5
    }

    fun save() {
        storage.putInt("level", level)
        storage.putLong("gold", gold)
        storage.putLong("soulPower", soulPower)
        storage.putString("wuhunName", martialSoul?.name ?: "")
        storage.putString("chosenSchool", chosenSchool?.name ?: "")

        val ringArr = GameJsonArray()
        soulRings.forEach { (slotIdx, ring) ->
            val obj = GameJsonObject()
            obj.put("slot", slotIdx)
            obj.put("year", ring.yearOrdinal)
            obj.put("quality", ring.qualityOrdinal)
            obj.put("percentage", ring.percentage)
            val affArr = GameJsonArray()
            ring.affixes.forEach { aff ->
                val aObj = GameJsonObject()
                aObj.put("type", aff.type.ordinal)
                aObj.put("value", aff.value)
                affArr.put(aObj)
            }
            obj.put("affixes", affArr)
            val activeSkill = ring.skill
            if (activeSkill != null) {
                val skillIdx = ActiveSkillPool.all.indexOfFirst { it.name == activeSkill.name }
                obj.put("skillIdx", skillIdx)
            }
            ringArr.put(obj)
        }
        storage.putString("soulRings", ringArr.toJsonString())

        val boneObj = GameJsonObject()
        soulBones.forEach { (k, bone) ->
            val bObj = GameJsonObject()
            bObj.put("year", bone.yearOrdinal)
            bObj.put("rarity", bone.rarityOrdinal)
            bObj.put("enhance", bone.enhanceLevel)
            val affArr = GameJsonArray()
            bone.affixes.forEach { aff ->
                val aObj = GameJsonObject()
                aObj.put("type", aff.type.ordinal)
                aObj.put("value", aff.value)
                affArr.put(aObj)
            }
            bObj.put("affixes", affArr)
            if (bone.passiveSkill != null) {
                bObj.put("passiveName", bone.passiveSkill.name)
                bObj.put("passiveType", bone.passiveSkill.type.displayName)
            }
            boneObj.put(k.toString(), bObj.toJsonString())
        }
        storage.putString("soulBones", boneObj.toJsonString())

        storage.putLong("currentHp", currentHp)
        storage.putInt("currentMapId", currentMapId)
        storage.putInt("currentFloor", currentFloor)
        storage.putInt("currentStage", currentStage)
        storage.putBoolean("inBattle", inBattle)
        storage.putLong("totalBattleWins", totalBattleWins)
        storage.putLong("totalBattleLosses", totalBattleLosses)
        storage.putInt("towerFloor", towerFloor)
        storage.putInt("towerBossKills", towerBossKills)
        storage.putLong("towerTempHp", towerTempHp)
        storage.putBoolean("towerInRun", towerInRun)
        storage.putInt("killingIntent", killingIntent)
        val ttArr = GameJsonArray()
        unlockedTowerTitles.forEach { ttArr.put(it) }
        storage.putString("unlockedTowerTitles", ttArr.toJsonString())
        val taObj = GameJsonObject()
        towerAttributeLevels.forEach { (k, v) -> taObj.put(k, v) }
        storage.putString("towerAttributeLevels", taObj.toJsonString())
        storage.putLong("lastDungeonTime", lastDungeonTime)
        storage.putInt("dungeonTierCompleted", dungeonTierCompleted)
        val shopObj = GameJsonObject()
        shopLevels.forEach { (k, v) -> shopObj.put(k.toString(), v) }
        storage.putString("shopLevels", shopObj.toJsonString())
        storage.putLong("totalGoldEarned", totalGoldEarned)
        val achArr = GameJsonArray()
        unlockedAchievements.forEach { achArr.put(it) }
        storage.putString("achievements", achArr.toJsonString())
        storage.putInt("prestigeCount", prestigeCount)
        storage.putInt("talentPoints", talentPoints)
        val talentObj = GameJsonObject()
        talentLevels.forEach { (k, v) -> talentObj.put(k.name, v) }
        storage.putString("talentLevels", talentObj.toJsonString())
        storage.putBoolean("autoBattle", autoBattle)
        storage.putInt("skillCooldown", skillCooldown)
        storage.putLong("codexKills", codexKills)
        storage.putLong("lastExit", PlatformTime.currentTimeMillis())
        storage.putInt("smallKillStreak", smallKillStreak)
        storage.putInt("bossDeathCount", bossDeathCount)
        val starObj = GameJsonObject()
        mapStars.forEach { (k, v) -> starObj.put(k.toString(), v) }
        storage.putString("mapStars", starObj.toJsonString())
        storage.putLong("bossCoin", bossCoin)
        storage.putBoolean("autoBreakthrough", autoBreakthrough)
        storage.putInt("battleSoulPower", battleSoulPower)
        for ((slot, core) in equippedSoulCores) {
            if (core != null) {
                val scObj = GameJsonObject()
                scObj.put("name", core.name)
                scObj.put("rarity", core.rarityOrdinal)
                scObj.put("passiveName", core.passiveSkill.name)
                scObj.put("value", core.value)
                scObj.put("level", core.level)
                scObj.put("slotType", core.slotType.name)
                storage.putString("equippedSoulCore_${slot.name}", scObj.toJsonString())
            } else {
                storage.putString("equippedSoulCore_${slot.name}", null)
            }
        }
        val scArr = GameJsonArray()
        backpackSoulCores.forEach { sc ->
            val obj = GameJsonObject()
            obj.put("name", sc.name)
            obj.put("rarity", sc.rarityOrdinal)
            obj.put("passiveName", sc.passiveSkill.name)
            obj.put("value", sc.value)
            obj.put("level", sc.level)
            scArr.put(obj)
        }
        storage.putString("bpSoulCores", scArr.toJsonString())
        val bpRingArr = GameJsonArray()
        backpackRings.forEach { r ->
            val obj = GameJsonObject()
            obj.put("year", r.yearOrdinal)
            obj.put("quality", r.qualityOrdinal)
            val affArr = GameJsonArray()
            r.affixes.forEach { aff ->
                val aObj = GameJsonObject()
                aObj.put("type", aff.type.ordinal)
                aObj.put("value", aff.value)
                affArr.put(aObj)
            }
            obj.put("affixes", affArr)
            if (r.skill != null) obj.put("skillName", r.skill.name)
            obj.put("percentage", r.percentage)
            bpRingArr.put(obj)
        }
        storage.putString("bpRings", bpRingArr.toJsonString())
        val bpBoneArr = GameJsonArray()
        backpackBones.forEach { b ->
            val obj = GameJsonObject()
            obj.put("slot", b.boneTypeOrdinal)
            obj.put("year", b.yearOrdinal)
            obj.put("rarity", b.rarityOrdinal)
            val affArr = GameJsonArray()
            b.affixes.forEach { aff ->
                val aObj = GameJsonObject()
                aObj.put("type", aff.type.ordinal)
                aObj.put("value", aff.value)
                affArr.put(aObj)
            }
            obj.put("affixes", affArr)
            if (b.passiveSkill != null) obj.put("passiveName", b.passiveSkill.name)
            bpBoneArr.put(obj)
        }
        storage.putString("bpBones", bpBoneArr.toJsonString())
        storage.putBoolean("autoSellRings", autoSellRings)
        storage.putBoolean("autoSellBones", autoSellBones)
        storage.putInt("autoSellRingTh", autoSellRingThreshold)
        storage.putInt("autoSellBoneTh", autoSellBoneThreshold)
        val rfYear = GameJsonArray()
        ringFilterYears.forEach { rfYear.put(it) }
        storage.putString("ringFilterYears", rfYear.toJsonString())
        val rfQual = GameJsonArray()
        ringFilterQualities.forEach { rfQual.put(it) }
        storage.putString("ringFilterQualities", rfQual.toJsonString())
        val bfYear = GameJsonArray()
        boneFilterYears.forEach { bfYear.put(it) }
        storage.putString("boneFilterYears", bfYear.toJsonString())
        val bfRar = GameJsonArray()
        boneFilterRarities.forEach { bfRar.put(it) }
        storage.putString("boneFilterRarities", bfRar.toJsonString())
        val scRar = GameJsonArray()
        soulCoreFilterRarities.forEach { scRar.put(it) }
        storage.putString("soulCoreFilterRarities", scRar.toJsonString())
        val scCat = GameJsonArray()
        soulCoreFilterCategories.forEach { scCat.put(it.ordinal) }
        storage.putString("soulCoreFilterCategories", scCat.toJsonString())
        storage.putBoolean("isLevelCapped", isLevelCapped)
        storage.putLong("excessSoulPower", excessSoulPower)
        val capObj = GameJsonObject()
        capStatLevels.forEach { (k, v) -> capObj.put(k, v) }
        storage.putString("capStatLevels", capObj.toJsonString())
        val disabledArr = GameJsonArray()
        disabledAutoSkillSlots.forEach { disabledArr.put(it) }
        storage.putString("disabledAutoSkillSlots", disabledArr.toJsonString())
        storage.putInt("backpackCapTier", backpackCapacityTier)
        storage.putInt("freeExpandRemaining", freeExpandRemaining)
        val lockRingArr = GameJsonArray()
        lockedBackpackRingIndices.forEach { lockRingArr.put(it) }
        storage.putString("lockedRings", lockRingArr.toJsonString())
        val lockBoneArr = GameJsonArray()
        lockedBackpackBoneIndices.forEach { lockBoneArr.put(it) }
        storage.putString("lockedBones", lockBoneArr.toJsonString())
        storage.putBoolean("skipCultivate", skipCultivateConfirm)
        storage.putBoolean("skipBreakthrough", skipBreakthroughConfirm)
        storage.putBoolean("skipAwaken", skipAwakenConfirm)
        storage.putBoolean("skipPrestige", skipPrestigeConfirm)
        storage.putBoolean("skipNotifyBoss", skipNotifyBossPurchase)
        storage.putBoolean("skipNotifyShop", skipNotifyShopPurchase)
        storage.putBoolean("skipNotifyDrop", skipNotifyHighDrop)
        storage.putInt("tutorialStep", tutorialStep)
        val featureLogArr = GameJsonArray()
        unlockedFeatureLog.forEach { featureLogArr.put(it) }
        storage.putString("unlockedFeatureLog", featureLogArr.toJsonString())
        storage.putBoolean("autoAdvanceMap", autoAdvanceMap)
        storage.putLong("limitedShopRefresh", limitedShopRefreshTime)
        val lsRingArr = GameJsonArray()
        limitedShopRings.forEach { r ->
            val obj = GameJsonObject()
            obj.put("year", r.yearOrdinal)
            obj.put("quality", r.qualityOrdinal)
            val affArr = GameJsonArray()
            r.affixes.forEach { aff ->
                val aObj = GameJsonObject()
                aObj.put("type", aff.type.ordinal)
                aObj.put("value", aff.value)
                affArr.put(aObj)
            }
            obj.put("affixes", affArr)
            if (r.skill != null) obj.put("skillName", r.skill.name)
            obj.put("percentage", r.percentage)
            lsRingArr.put(obj)
        }
        storage.putString("limitedShopRings", lsRingArr.toJsonString())
        val lsBoneArr = GameJsonArray()
        limitedShopBones.forEach { b ->
            val obj = GameJsonObject()
            obj.put("slot", b.boneTypeOrdinal)
            obj.put("year", b.yearOrdinal)
            obj.put("rarity", b.rarityOrdinal)
            val affArr = GameJsonArray()
            b.affixes.forEach { aff ->
                val aObj = GameJsonObject()
                aObj.put("type", aff.type.ordinal)
                aObj.put("value", aff.value)
                affArr.put(aObj)
            }
            obj.put("affixes", affArr)
            if (b.passiveSkill != null) obj.put("passiveName", b.passiveSkill.name)
            lsBoneArr.put(obj)
        }
        storage.putString("limitedShopBones", lsBoneArr.toJsonString())
        storage.apply()
    }

    fun load() {
        level = storage.getInt("level", 1)
        gold = storage.getLong("gold", 0)
        soulPower = storage.getLong("soulPower", 0)
        val wuhunName = storage.getString("wuhunName", "") ?: ""
        martialSoul = MartialSoulPool.all.find { it.name == wuhunName }
        val schoolName = storage.getString("chosenSchool", "") ?: ""
        chosenSchool = SoulSchool.entries.find { it.name == schoolName }

        soulRings.clear()
        try {
            val ringStr = storage.getString("soulRings", "[]") ?: "[]"
            val ringArr = GameJsonArray.fromString(ringStr)
            for (i in 0 until ringArr.length()) {
                val obj = ringArr.getJSONObject(i)
                val slotIdx = obj.optInt("slot", i)
                val year = obj.optInt("year", 0)
                val quality = obj.optInt("quality", -1)
                val affixes = mutableListOf<EquipAffixValue>()
                val affArr = obj.optJSONArray("affixes")
                if (affArr != null) {
                    for (j in 0 until affArr.length()) {
                        val aObj = affArr.getJSONObject(j)
                        affixes.add(EquipAffixValue(
                            EquipAffix.entries[aObj.getInt("type")],
                            aObj.getInt("value")
                        ))
                    }
                }
                var skill: ActiveSkill? = null
                if (obj.has("skillIdx")) {
                    val skillIdx = obj.getInt("skillIdx")
                    if (skillIdx in ActiveSkillPool.all.indices) skill = ActiveSkillPool.all[skillIdx]
                }
                soulRings[slotIdx] = if (quality >= 0) {
                    val pct = obj.optInt("percentage", SoulRingSystem.randomPercentage())
                    SoulRingInstance(year, quality, pct, affixes, skill)
                } else {
                    SoulRingInstance(year, affixes, skill)
                }
            }
        } catch (_: Exception) {}

        soulBones.clear()
        try {
            val boneStr = storage.getString("soulBones", "{}") ?: "{}"
            val boneObj = GameJsonObject.fromString(boneStr)
            for (key in boneObj.keys()) {
                val bObjStr = boneObj.getString(key, "{}")
                val bObj = GameJsonObject.fromString(bObjStr)
                val year = bObj.optInt("year", -1)
                val rarity = bObj.getInt("rarity")
                val enhance = bObj.optInt("enhance", 0)
                val affixes = mutableListOf<EquipAffixValue>()
                val affArr = bObj.optJSONArray("affixes")
                if (affArr != null) {
                    for (j in 0 until affArr.length()) {
                        val aObj = affArr.getJSONObject(j)
                        affixes.add(EquipAffixValue(
                            EquipAffix.entries[aObj.getInt("type")],
                            aObj.getInt("value")
                        ))
                    }
                }
                var passive: PassiveSkill? = null
                if (bObj.has("passiveName")) {
                    val pName = bObj.getString("passiveName")
                    passive = PassiveSkillPool.all.find { it.name == pName }
                }
                soulBones[key.toInt()] = if (year >= 0) SoulBoneInstance(year, rarity, affixes, enhance, passive) else SoulBoneInstance(rarity, affixes, enhance, passive)
            }
        } catch (_: Exception) {}

        currentHp = storage.getLong("currentHp", 100)
        currentMapId = storage.getInt("currentMapId", 0)
        currentFloor = storage.getInt("currentFloor", 1)
        currentStage = storage.getInt("currentStage", 1)
        inBattle = storage.getBoolean("inBattle", false)
        totalBattleWins = storage.getLong("totalBattleWins", 0)
        totalBattleLosses = storage.getLong("totalBattleLosses", 0)
        towerFloor = storage.getInt("towerFloor", 0)
        towerBossKills = storage.getInt("towerBossKills", 0)
        towerTempHp = storage.getLong("towerTempHp", 0)
        towerInRun = storage.getBoolean("towerInRun", false)
        killingIntent = storage.getInt("killingIntent", 0)
        unlockedTowerTitles.clear()
        try {
            val ttStr = storage.getString("unlockedTowerTitles", "[]") ?: "[]"
            val ttArr = GameJsonArray.fromString(ttStr)
            for (i in 0 until ttArr.length()) unlockedTowerTitles.add(ttArr.getString(i))
        } catch (_: Exception) {}
        towerAttributeLevels.clear()
        try {
            val taStr = storage.getString("towerAttributeLevels", "{}") ?: "{}"
            val taObj = GameJsonObject.fromString(taStr)
            for (key in taObj.keys()) towerAttributeLevels[key] = taObj.getInt(key)
        } catch (_: Exception) {}
        lastDungeonTime = storage.getLong("lastDungeonTime", 0)
        dungeonTierCompleted = storage.getInt("dungeonTierCompleted", -1)
        try {
            val shopStr = storage.getString("shopLevels", "{}") ?: "{}"
            val shopObj = GameJsonObject.fromString(shopStr)
            for (key in shopObj.keys()) { shopLevels[key.toInt()] = shopObj.getInt(key) }
        } catch (_: Exception) {}
        totalGoldEarned = storage.getLong("totalGoldEarned", 0)
        unlockedAchievements.clear()
        try {
            val achStr = storage.getString("achievements", "[]") ?: "[]"
            val achArr = GameJsonArray.fromString(achStr)
            for (i in 0 until achArr.length()) { unlockedAchievements.add(achArr.getString(i)) }
        } catch (_: Exception) {}
        prestigeCount = storage.getInt("prestigeCount", 0)
        talentPoints = storage.getInt("talentPoints", 0)
        talentLevels.clear()
        try {
            val tStr = storage.getString("talentLevels", "{}") ?: "{}"
            val tObj = GameJsonObject.fromString(tStr)
            TalentBranch.entries.forEach { branch ->
                talentLevels[branch] = tObj.optInt(branch.name, 0)
            }
        } catch (_: Exception) {}
        autoBattle = storage.getBoolean("autoBattle", false)
        skillCooldown = storage.getInt("skillCooldown", 0)
        codexKills = storage.getLong("codexKills", 0)
        lastExitTime = storage.getLong("lastExit", 0)
        smallKillStreak = storage.getInt("smallKillStreak", 0)
        bossDeathCount = storage.getInt("bossDeathCount", 0)
        mapStars.clear()
        try {
            val starStr = storage.getString("mapStars", "{}") ?: "{}"
            val starObj = GameJsonObject.fromString(starStr)
            for (key in starObj.keys()) { mapStars[key.toInt()] = starObj.getInt(key) }
        } catch (_: Exception) {}
        bossCoin = storage.getLong("bossCoin", 0)
        autoBreakthrough = storage.getBoolean("autoBreakthrough", true)
        autoAdvanceMap = storage.getBoolean("autoAdvanceMap", true)
        battleSoulPower = storage.getInt("battleSoulPower", 100).coerceAtMost(RealmData.battleSoulPowerMax(level))

        equippedSoulCores.clear()
        for (slot in SoulCoreSlotType.entries) { equippedSoulCores[slot] = null }
        try {
            for (slot in SoulCoreSlotType.entries) {
                val scStr = storage.getString("equippedSoulCore_${slot.name}", null)
                if (scStr != null) {
                    val scObj = GameJsonObject.fromString(scStr)
                    val scName = scObj.getString("name")
                    val scRarity = scObj.getInt("rarity")
                    val scPassiveName = scObj.getString("passiveName")
                    val scValue = scObj.getInt("value")
                    val scLevel = scObj.optInt("level", 0)
                    val scSlotName = scObj.optString("slotType", slot.name)
                    val scSlot = try { SoulCoreSlotType.valueOf(scSlotName) } catch (_: Exception) { slot }
                    val scPassive = PassiveSkillPool.all.find { it.name == scPassiveName }
                        ?: PassiveSkill("未知", PassiveSkillType.STAT_BOOST, listOf(0), "")
                    equippedSoulCores[scSlot] = SoulCoreInstance(scName, scRarity, scPassive, scValue, scLevel, scSlot)
                }
            }
        } catch (_: Exception) {}

        backpackSoulCores.clear()
        try {
            val scArrStr = storage.getString("bpSoulCores", "[]") ?: "[]"
            val scArr = GameJsonArray.fromString(scArrStr)
            for (i in 0 until scArr.length()) {
                val obj = scArr.getJSONObject(i)
                val scName = obj.getString("name")
                val scRarity = obj.getInt("rarity")
                val scPassiveName = obj.getString("passiveName")
                val scValue = obj.getInt("value")
                val scPassive = PassiveSkillPool.all.find { it.name == scPassiveName }
                    ?: PassiveSkill("未知", PassiveSkillType.STAT_BOOST, listOf(0), "")
                val scLevel = obj.optInt("level", 0)
                backpackSoulCores.add(DroppedSoulCore(scName, scRarity, scPassive, scValue, scLevel))
            }
        } catch (_: Exception) {}

        backpackRings.clear()
        try {
            val bpRStr = storage.getString("bpRings", "[]") ?: "[]"
            val bpRArr = GameJsonArray.fromString(bpRStr)
            for (i in 0 until bpRArr.length()) {
                val obj = bpRArr.getJSONObject(i)
                val affixes = mutableListOf<EquipAffixValue>()
                val affArr = obj.optJSONArray("affixes")
                if (affArr != null) {
                    for (j in 0 until affArr.length()) {
                        val aObj = affArr.getJSONObject(j)
                        affixes.add(EquipAffixValue(EquipAffix.entries[aObj.getInt("type")], aObj.getInt("value")))
                    }
                }
                var skill: ActiveSkill? = null
                if (obj.has("skillName")) {
                    val skillName = obj.getString("skillName")
                    skill = ActiveSkillPool.all.find { it.name == skillName }
                }
                val year = obj.optInt("year", 0)
                val quality = obj.optInt("quality", -1)
                val pct = obj.optInt("percentage", SoulRingSystem.randomPercentage())
                backpackRings.add(if (quality >= 0) DroppedRing(year, quality, pct, affixes, skill) else DroppedRing(year, affixes, skill))
            }
        } catch (_: Exception) {}
        backpackBones.clear()
        try {
            val bpBStr = storage.getString("bpBones", "[]") ?: "[]"
            val bpBArr = GameJsonArray.fromString(bpBStr)
            for (i in 0 until bpBArr.length()) {
                val obj = bpBArr.getJSONObject(i)
                val affixes = mutableListOf<EquipAffixValue>()
                val affArr = obj.optJSONArray("affixes")
                if (affArr != null) {
                    for (j in 0 until affArr.length()) {
                        val aObj = affArr.getJSONObject(j)
                        affixes.add(EquipAffixValue(EquipAffix.entries[aObj.getInt("type")], aObj.getInt("value")))
                    }
                }
                var passive: PassiveSkill? = null
                if (obj.has("passiveName")) {
                    val pName = obj.getString("passiveName")
                    passive = PassiveSkillPool.all.find { it.name == pName }
                }
                val slot = obj.getInt("slot")
                val year = obj.optInt("year", -1)
                val rarity = obj.getInt("rarity")
                backpackBones.add(if (year >= 0) DroppedBone(slot, year, rarity, affixes, passive) else DroppedBone(slot, rarity, affixes, passive))
            }
        } catch (_: Exception) {}

        autoSellRings = storage.getBoolean("autoSellRings", false)
        autoSellBones = storage.getBoolean("autoSellBones", false)
        autoSellRingThreshold = storage.getInt("autoSellRingTh", 1)
        autoSellBoneThreshold = storage.getInt("autoSellBoneTh", 0)
        ringFilterYears.clear(); ringFilterQualities.clear()
        boneFilterYears.clear(); boneFilterRarities.clear()
        try {
            GameJsonArray.fromString(storage.getString("ringFilterYears", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) ringFilterYears.add(arr.getInt(i))
            }
            GameJsonArray.fromString(storage.getString("ringFilterQualities", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) ringFilterQualities.add(arr.getInt(i))
            }
            GameJsonArray.fromString(storage.getString("boneFilterYears", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) boneFilterYears.add(arr.getInt(i))
            }
            GameJsonArray.fromString(storage.getString("boneFilterRarities", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) boneFilterRarities.add(arr.getInt(i))
            }
        } catch (_: Exception) {}
        soulCoreFilterRarities.clear(); soulCoreFilterCategories.clear()
        try {
            GameJsonArray.fromString(storage.getString("soulCoreFilterRarities", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) soulCoreFilterRarities.add(arr.getInt(i))
            }
            GameJsonArray.fromString(storage.getString("soulCoreFilterCategories", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) soulCoreFilterCategories.add(SoulCoreCategory.entries[arr.getInt(i)])
            }
        } catch (_: Exception) {}
        isLevelCapped = storage.getBoolean("isLevelCapped", false)
        excessSoulPower = storage.getLong("excessSoulPower", 0L)
        capStatLevels.clear()
        try {
            val capStr = storage.getString("capStatLevels", "{}") ?: "{}"
            val capObj = GameJsonObject.fromString(capStr)
            for (key in capObj.keys()) capStatLevels[key] = capObj.getInt(key)
        } catch (_: Exception) {}
        disabledAutoSkillSlots.clear()
        try {
            val dsStr = storage.getString("disabledAutoSkillSlots", "[]") ?: "[]"
            val dsArr = GameJsonArray.fromString(dsStr)
            for (i in 0 until dsArr.length()) disabledAutoSkillSlots.add(dsArr.getInt(i))
        } catch (_: Exception) {}
        backpackCapacityTier = storage.getInt("backpackCapTier", 0)
        freeExpandRemaining = storage.getInt("freeExpandRemaining", 0)
        lockedBackpackRingIndices.clear()
        try {
            val lockRStr = storage.getString("lockedRings", "[]") ?: "[]"
            val lockRArr = GameJsonArray.fromString(lockRStr)
            for (i in 0 until lockRArr.length()) { lockedBackpackRingIndices.add(lockRArr.getInt(i)) }
        } catch (_: Exception) {}
        lockedBackpackBoneIndices.clear()
        try {
            val lockBStr = storage.getString("lockedBones", "[]") ?: "[]"
            val lockBArr = GameJsonArray.fromString(lockBStr)
            for (i in 0 until lockBArr.length()) { lockedBackpackBoneIndices.add(lockBArr.getInt(i)) }
        } catch (_: Exception) {}
        skipCultivateConfirm = storage.getBoolean("skipCultivate", false)
        skipBreakthroughConfirm = storage.getBoolean("skipBreakthrough", false)
        skipAwakenConfirm = storage.getBoolean("skipAwaken", false)
        skipPrestigeConfirm = storage.getBoolean("skipPrestige", false)
        skipNotifyBossPurchase = storage.getBoolean("skipNotifyBoss", false)
        skipNotifyShopPurchase = storage.getBoolean("skipNotifyShop", false)
        skipNotifyHighDrop = storage.getBoolean("skipNotifyDrop", false)
        tutorialStep = storage.getInt("tutorialStep", 0).let { orig ->
            if (orig == 0 && (chosenSchool != null || level > 1)) 7
            else if (orig >= 6) 7
            else orig.coerceIn(1, 5)
        }
        unlockedFeatureLog.clear()
        try {
            val flStr = storage.getString("unlockedFeatureLog", "[]") ?: "[]"
            val flArr = GameJsonArray.fromString(flStr)
            for (i in 0 until flArr.length()) unlockedFeatureLog.add(flArr.getString(i))
        } catch (_: Exception) {}
        limitedShopRefreshTime = storage.getLong("limitedShopRefresh", 0L)
        limitedShopRings.clear()
        try {
            val lsRStr = storage.getString("limitedShopRings", "[]") ?: "[]"
            val lsRArr = GameJsonArray.fromString(lsRStr)
            for (i in 0 until lsRArr.length()) {
                val obj = lsRArr.getJSONObject(i)
                val affixes = mutableListOf<EquipAffixValue>()
                val affArr = obj.optJSONArray("affixes")
                if (affArr != null) {
                    for (j in 0 until affArr.length()) {
                        val aObj = affArr.getJSONObject(j)
                        affixes.add(EquipAffixValue(EquipAffix.entries[aObj.getInt("type")], aObj.getInt("value")))
                    }
                }
                var skill: ActiveSkill? = null
                if (obj.has("skillName")) {
                    val skillName = obj.getString("skillName")
                    skill = ActiveSkillPool.all.find { it.name == skillName }
                }
                val year = obj.optInt("year", 0)
                val quality = obj.optInt("quality", -1)
                val pct = obj.optInt("percentage", SoulRingSystem.randomPercentage())
                limitedShopRings.add(if (quality >= 0) DroppedRing(year, quality, pct, affixes, skill) else DroppedRing(year, affixes, skill))
            }
        } catch (_: Exception) {}
        limitedShopBones.clear()
        try {
            val lsBStr = storage.getString("limitedShopBones", "[]") ?: "[]"
            val lsBArr = GameJsonArray.fromString(lsBStr)
            for (i in 0 until lsBArr.length()) {
                val obj = lsBArr.getJSONObject(i)
                val affixes = mutableListOf<EquipAffixValue>()
                val affArr = obj.optJSONArray("affixes")
                if (affArr != null) {
                    for (j in 0 until affArr.length()) {
                        val aObj = affArr.getJSONObject(j)
                        affixes.add(EquipAffixValue(EquipAffix.entries[aObj.getInt("type")], aObj.getInt("value")))
                    }
                }
                var passive: PassiveSkill? = null
                if (obj.has("passiveName")) {
                    val pName = obj.getString("passiveName")
                    passive = PassiveSkillPool.all.find { it.name == pName }
                }
                val slot = obj.getInt("slot")
                val year = obj.optInt("year", -1)
                val rarity = obj.getInt("rarity")
                limitedShopBones.add(if (year >= 0) DroppedBone(slot, year, rarity, affixes, passive) else DroppedBone(slot, rarity, affixes, passive))
            }
        } catch (_: Exception) {}

        val maxRings = RealmData.maxSoulRings(level)
        soulRings.keys.toList().filter { it >= maxRings }.forEach { soulRings.remove(it) }
        val maxBones = RealmData.maxSoulBones(level)
        soulBones.keys.toList().filter { it >= maxBones }.forEach { soulBones.remove(it) }
    }
}
