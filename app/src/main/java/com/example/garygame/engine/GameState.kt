package com.example.garygame.engine

import android.content.Context
import com.example.garygame.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 游戏状态管理 - 所有可变数据 + 存档/读档
 */
class GameState(private val context: Context) {

    // ======== 核心数值 ========
    var level: Int = 1              // 境界等级
    var gold: Long = 0              // 金魂币
    var soulPower: Long = 0         // 魂力 (用于突破)

    // ======== 武魂 & 流派 ========
    var martialSoul: MartialSoul? = null
    var chosenSchool: SoulSchool? = null      // 开局选择的流派（null=尚未选择）

    // ======== 魂环 (槽位制：Int=槽位索引) ========
    var soulRings: MutableMap<Int, SoulRingInstance> = mutableMapOf()

    // ======== 魂骨 (带随机词缀) ========
    var soulBones: MutableMap<Int, SoulBoneInstance> = mutableMapOf()

    // ======== 战斗系统 ========
    var currentHp: Long = 100       // 当前血量
    var currentMapId: Int = 0       // 当前地图ID
    var currentFloor: Int = 1       // 当前地图层数
    var inBattle: Boolean = false   // 战斗中
    var totalBattleWins: Long = 0
    var totalBattleLosses: Long = 0

    // ======== 商店升级 ========
    var shopLevels: MutableMap<Int, Int> = mutableMapOf()

    // ======== 杀戮之都 ========
    var towerFloor: Int = 0         // 当前已通关层数
    var towerBossKills: Int = 0     // Boss击杀数
    var towerTempHp: Long = 0       // 塔内剩余血量
    var towerInRun: Boolean = false // 是否在塔内旅程中
    var killingIntent: Int = 0      // 杀气货币
    val unlockedTowerTitles: MutableSet<String> = mutableSetOf()  // 已兑换的塔称号
    val towerAttributeLevels: MutableMap<String, Int> = mutableMapOf()  // 塔属性购买次数

    // ======== 每日副本 ========
    var lastDungeonTime: Long = 0   // 上次进入每日副本时间
    var dungeonTierCompleted: Int = -1  // 今日已完成的副本层级(-1=未完成)

    // ======== 统计 ========
    var totalGoldEarned: Long = 0

    // ======== 成就 ========
    var unlockedAchievements: MutableSet<String> = mutableSetOf()

    // ======== 转生 & 天赋树 ========
    var prestigeCount: Int = 0
    var talentPoints: Int = 0
    val talentLevels: MutableMap<TalentBranch, Int> = TalentBranch.entries.associateWith { 0 }.toMutableMap()

    fun getTalentLevel(branch: TalentBranch): Int = talentLevels[branch] ?: 0
    fun setTalentLevel(branch: TalentBranch, level: Int) {
        talentLevels[branch] = level.coerceIn(0, branch.maxLevel)
    }

    // ======== 离线收益 ========
    var lastExitTime: Long = 0
    val maxOfflineSec = 43200L // 12小时

    // ======== 自动战斗 ========
    var autoBattle: Boolean = false
    var battleSpeed: Int = 1              // 战斗倍速(1=1x, 2=2x)
    // ======== 地图自动跳转开关 ========
    var autoAdvanceMap: Boolean = true  // 通关15关后自动进入下一幅图

    // ======== 技能冷却 ========
    var skillCooldown: Int = 0

    // ======== V2 改版：新字段 ========
    var bossCoin: Long = 0                    // Boss货币
    var equippedSoulCores: MutableMap<SoulCoreSlotType, SoulCoreInstance?> = mutableMapOf(
        SoulCoreSlotType.ATTACK to null,
        SoulCoreSlotType.DEFENSE to null,
        SoulCoreSlotType.UTILITY to null
    )  // 多槽位已装备魂核
    var currentStage: Int = 1                  // 当前关卡(1~15,替代currentFloor)
    var autoBreakthrough: Boolean = true       // 自动突破开关
    val backpackSoulCores: MutableList<DroppedSoulCore> = mutableListOf()  // 背包魂核
    var battleSoulPower: Int = 100             // 战斗魂力(用于释放技能)
    var activeSkillCooldowns: MutableMap<Int, Int> = mutableMapOf()  // 魂环槽位->剩余冷却

    // ======== 多目标战斗 ========
    var currentMonsters: MutableList<Monster> = mutableListOf()  // 当前怪物组
    var currentMonsterTarget: Int = 0          // 当前攻击目标索引

    // ======== 图鉴系统 ========
    var codexKills: Long = 0

    // ======== 当前怪物追踪(用于UI血条显示) ========
    var currentMonster: Monster? = null
    var battleRound: Int = 0          // 当前战斗已进行回合数

    // ======== Boss/怪物刷新机制 ========
    var smallKillStreak: Int = 0      // 连续击杀小怪数（触发Boss）
    var bossDeathCount: Int = 0       // 当前Boss战斗死亡次数
    val mapStars: MutableMap<Int, Int> = mutableMapOf()  // 地图星级 (mapId -> 0~5星)

    // ======== 掉落背包 ========
    val backpackRings: MutableList<DroppedRing> = mutableListOf()
    val backpackBones: MutableList<DroppedBone> = mutableListOf()

    // ======== 限时珍品（10分钟刷新） ========
    var limitedShopRefreshTime: Long = 0L
    val limitedShopRings: MutableList<DroppedRing> = mutableListOf()
    val limitedShopBones: MutableList<DroppedBone> = mutableListOf()

    // ======== 背包容量 ========
    var backpackCapacityTier: Int = 0           // 已扩容次数
    var freeExpandRemaining: Int = 0            // 免费扩容剩余次数（财富之道Lv3）
    val backpackCapacity: Int get() = BASE_BACKPACK_CAPACITY + backpackCapacityTier * BACKPACK_EXPAND_SLOTS
    val backpackTotalItems: Int get() = backpackRings.size + backpackBones.size + backpackSoulCores.size
    val backpackIsFull: Boolean get() = backpackTotalItems >= backpackCapacity

    // ======== 魂核战斗临时状态（每场战斗重置，不存档） ========
    var coreReviveCharges: Int = 0

    // ======== 背包锁定 ========
    val lockedBackpackRingIndices: MutableSet<Int> = mutableSetOf()    // 已锁定的魂环索引
    val lockedBackpackBoneIndices: MutableSet<Int> = mutableSetOf()    // 已锁定的魂骨索引

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

    // ======== 自动售卖 ========
    var autoSellRings: Boolean = false       // 自动卖魂环
    var autoSellBones: Boolean = false        // 自动卖魂骨
    var autoSellRingThreshold: Int = 1        // 卖出低于此年份的魂环（默认千年以下）
    var autoSellBoneThreshold: Int = 0        // 卖出低于此品质的魂骨（默认千年以下）

    // ======== 掉落过滤（勾选的年份/品质不进背包，自动卖出）========
    val ringFilterYears: MutableSet<Int> = mutableSetOf()       // 被过滤的魂环年份(0=百年,1=千年,2=万年,3=十万年,4=百万年)
    val ringFilterQualities: MutableSet<Int> = mutableSetOf()    // 被过滤的魂环品质(0=劣等,1=普通,2=精良,3=优秀,4=完美)
    val boneFilterYears: MutableSet<Int> = mutableSetOf()        // 被过滤的魂骨年份(0~4)
    val boneFilterRarities: MutableSet<Int> = mutableSetOf()     // 被过滤的魂骨品质(0~4)
    val soulCoreFilterRarities: MutableSet<Int> = mutableSetOf() // 被过滤的魂核稀有度(0~5)
    val soulCoreFilterCategories: MutableSet<SoulCoreCategory> = mutableSetOf() // 被过滤的魂核分类

    // ======== 技能自动开关(关闭后不会自动释放，需手动点击) ========
    val disabledAutoSkillSlots: MutableSet<Int> = mutableSetOf()  // 禁用了自动释放的魂环槽位

    // ======== 卡级修炼 ========
    var isLevelCapped: Boolean = false         // 是否开启卡级模式(仅境界门槛Lv.10/20/30...可开启)
    var excessSoulPower: Long = 0L             // 超额储备魂力(卡级期间修炼获得)
    val capStatLevels: MutableMap<String, Int> = mutableMapOf()  // 凝练等级: hp/atk/matk/pdef/mdef -> level

    // ======== 操作确认跳过 ========
    var skipCultivateConfirm: Boolean = false
    var skipBreakthroughConfirm: Boolean = false
    var skipAwakenConfirm: Boolean = false
    var skipPrestigeConfirm: Boolean = false

    // ======== 通知跳过 ========
    var skipNotifyBossPurchase: Boolean = false  // Boss商店购买通知
    var skipNotifyShopPurchase: Boolean = false   // 限时珍品购买通知
    var skipNotifyHighDrop: Boolean = false       // 高品质掉落通知
    // 称号通知一直显示，无跳过选项

    // ======== 新手引导 ========
    var tutorialStep: Int = 1           // 1~6=引导步骤, 7=已完成, 0=旧档跳过

    // ======== 功能解锁通知日志 ========
    val unlockedFeatureLog: MutableSet<String> = mutableSetOf()

    // ======== 计算属性 ========
    fun getSnapshot(): GameSnapshot = GameSnapshot(
        level = level,
        soulRings = soulRings.size,
        totalBattleWins = totalBattleWins,
        towerFloor = towerFloor,
        prestigeCount = prestigeCount,
        totalGold = totalGoldEarned
    )

    // ======== 存档 ========
    companion object {
        private const val PREFS = "DouluoIdleGameV2"
        const val BASE_BACKPACK_CAPACITY = 20       // 基础容量
        const val BACKPACK_EXPAND_SLOTS = 5         // 每次扩容+5格
    }

    fun save() {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().apply {
            putInt("level", level)
            putLong("gold", gold)
            putLong("soulPower", soulPower)

            // 武魂 & 流派
            putString("wuhunName", martialSoul?.name ?: "")
            putString("chosenSchool", chosenSchool?.name ?: "")

            // 魂环 (JSON序列化，包含槽位索引)
            val ringArr = JSONArray()
            soulRings.forEach { (slotIdx, ring) ->
                val obj = JSONObject()
                obj.put("slot", slotIdx)
                obj.put("year", ring.yearOrdinal)
                obj.put("quality", ring.qualityOrdinal)
                obj.put("percentage", ring.percentage)
                val affArr = JSONArray()
                ring.affixes.forEach { aff ->
                    val aObj = JSONObject()
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
            putString("soulRings", ringArr.toString())

            // 魂骨 (JSON序列化包含词缀和被动)
            val boneObj = JSONObject()
            soulBones.forEach { (k, bone) ->
                val bObj = JSONObject()
                bObj.put("year", bone.yearOrdinal)
                bObj.put("rarity", bone.rarityOrdinal)
                bObj.put("enhance", bone.enhanceLevel)
                val affArr = JSONArray()
                bone.affixes.forEach { aff ->
                    val aObj = JSONObject()
                    aObj.put("type", aff.type.ordinal)
                    aObj.put("value", aff.value)
                    affArr.put(aObj)
                }
                bObj.put("affixes", affArr)
                if (bone.passiveSkill != null) {
                    bObj.put("passiveName", bone.passiveSkill.name)
                    bObj.put("passiveType", bone.passiveSkill.type.displayName)
                }
                boneObj.put(k.toString(), bObj)
            }
            putString("soulBones", boneObj.toString())

            // 战斗状态
            putLong("currentHp", currentHp)
            putInt("currentMapId", currentMapId)
            putInt("currentFloor", currentFloor)
            putInt("currentStage", currentStage)
            putBoolean("inBattle", inBattle)
            putLong("totalBattleWins", totalBattleWins)
            putLong("totalBattleLosses", totalBattleLosses)

            putInt("towerFloor", towerFloor)
            putInt("towerBossKills", towerBossKills)
            putLong("towerTempHp", towerTempHp)
            putBoolean("towerInRun", towerInRun)
            putInt("killingIntent", killingIntent)
            val ttArr = JSONArray()
            unlockedTowerTitles.forEach { ttArr.put(it) }
            putString("unlockedTowerTitles", ttArr.toString())
            val taObj = JSONObject()
            towerAttributeLevels.forEach { (k, v) -> taObj.put(k, v) }
            putString("towerAttributeLevels", taObj.toString())
            putLong("lastDungeonTime", lastDungeonTime)
            putInt("dungeonTierCompleted", dungeonTierCompleted)

            // 商店
            val shopObj = JSONObject()
            shopLevels.forEach { (k, v) -> shopObj.put(k.toString(), v) }
            putString("shopLevels", shopObj.toString())

            putLong("totalGoldEarned", totalGoldEarned)

            // 成就
            val achArr = JSONArray()
            unlockedAchievements.forEach { achArr.put(it) }
            putString("achievements", achArr.toString())

            putInt("prestigeCount", prestigeCount)
            // 天赋树
            putInt("talentPoints", talentPoints)
            val talentObj = JSONObject()
            talentLevels.forEach { (k, v) -> talentObj.put(k.name, v) }
            putString("talentLevels", talentObj.toString())
            putBoolean("autoBattle", autoBattle)
            putInt("skillCooldown", skillCooldown)
            putLong("codexKills", codexKills)
            putLong("lastExit", System.currentTimeMillis())
            putInt("smallKillStreak", smallKillStreak)
            putInt("bossDeathCount", bossDeathCount)
            // 地图星级
            val starObj = JSONObject()
            mapStars.forEach { (k, v) -> starObj.put(k.toString(), v) }
            putString("mapStars", starObj.toString())
            // V2新字段
            putLong("bossCoin", bossCoin)
            putBoolean("autoBreakthrough", autoBreakthrough)
            putInt("battleSoulPower", battleSoulPower)
            // 多槽位魂核存档
            for ((slot, core) in equippedSoulCores) {
                if (core != null) {
                    val scObj = JSONObject()
                    scObj.put("name", core.name)
                    scObj.put("rarity", core.rarityOrdinal)
                    scObj.put("passiveName", core.passiveSkill.name)
                    scObj.put("value", core.value)
                    scObj.put("level", core.level)
                    scObj.put("slotType", core.slotType.name)
                    putString("equippedSoulCore_${slot.name}", scObj.toString())
                } else {
                    putString("equippedSoulCore_${slot.name}", null)
                }
            }
            val scArr = JSONArray()
            backpackSoulCores.forEach { sc ->
                val obj = JSONObject()
                obj.put("name", sc.name)
                obj.put("rarity", sc.rarityOrdinal)
                obj.put("passiveName", sc.passiveSkill.name)
                obj.put("value", sc.value)
                obj.put("level", sc.level)
                scArr.put(obj)
            }
            putString("bpSoulCores", scArr.toString())
            // 背包(保存技能/被动)
            val bpRingArr = JSONArray()
            backpackRings.forEach { r ->
                val obj = JSONObject()
                obj.put("year", r.yearOrdinal)
                obj.put("quality", r.qualityOrdinal)
                val affArr = JSONArray()
                r.affixes.forEach { aff ->
                    val aObj = JSONObject()
                    aObj.put("type", aff.type.ordinal)
                    aObj.put("value", aff.value)
                    affArr.put(aObj)
                }
                obj.put("affixes", affArr)
                if (r.skill != null) obj.put("skillName", r.skill.name)
                obj.put("percentage", r.percentage)
                bpRingArr.put(obj)
            }
            putString("bpRings", bpRingArr.toString())
            val bpBoneArr = JSONArray()
            backpackBones.forEach { b ->
                val obj = JSONObject()
                obj.put("slot", b.boneTypeOrdinal)
                obj.put("year", b.yearOrdinal)
                obj.put("rarity", b.rarityOrdinal)
                val affArr = JSONArray()
                b.affixes.forEach { aff ->
                    val aObj = JSONObject()
                    aObj.put("type", aff.type.ordinal)
                    aObj.put("value", aff.value)
                    affArr.put(aObj)
                }
                obj.put("affixes", affArr)
                if (b.passiveSkill != null) obj.put("passiveName", b.passiveSkill.name)
                bpBoneArr.put(obj)
            }
            putString("bpBones", bpBoneArr.toString())
            // 自动售卖
            putBoolean("autoSellRings", autoSellRings)
            putBoolean("autoSellBones", autoSellBones)
            putInt("autoSellRingTh", autoSellRingThreshold)
            putInt("autoSellBoneTh", autoSellBoneThreshold)
            // 掉落过滤
            val rfYear = JSONArray()
            ringFilterYears.forEach { rfYear.put(it) }
            putString("ringFilterYears", rfYear.toString())
            val rfQual = JSONArray()
            ringFilterQualities.forEach { rfQual.put(it) }
            putString("ringFilterQualities", rfQual.toString())
            val bfYear = JSONArray()
            boneFilterYears.forEach { bfYear.put(it) }
            putString("boneFilterYears", bfYear.toString())
            val bfRar = JSONArray()
            boneFilterRarities.forEach { bfRar.put(it) }
            putString("boneFilterRarities", bfRar.toString())
            // 魂核过滤
            val scRar = JSONArray()
            soulCoreFilterRarities.forEach { scRar.put(it) }
            putString("soulCoreFilterRarities", scRar.toString())
            val scCat = JSONArray()
            soulCoreFilterCategories.forEach { scCat.put(it.ordinal) }
            putString("soulCoreFilterCategories", scCat.toString())
            // 卡级修炼
            putBoolean("isLevelCapped", isLevelCapped)
            putLong("excessSoulPower", excessSoulPower)
            val capObj = JSONObject()
            capStatLevels.forEach { (k, v) -> capObj.put(k, v) }
            putString("capStatLevels", capObj.toString())
            // 技能自动开关
            val disabledArr = JSONArray()
            disabledAutoSkillSlots.forEach { disabledArr.put(it) }
            putString("disabledAutoSkillSlots", disabledArr.toString())
            // 背包容量
            putInt("backpackCapTier", backpackCapacityTier)
            putInt("freeExpandRemaining", freeExpandRemaining)
            // 锁定集合
            val lockRingArr = JSONArray()
            lockedBackpackRingIndices.forEach { lockRingArr.put(it) }
            putString("lockedRings", lockRingArr.toString())
            val lockBoneArr = JSONArray()
            lockedBackpackBoneIndices.forEach { lockBoneArr.put(it) }
            putString("lockedBones", lockBoneArr.toString())
            // 操作确认跳过
            putBoolean("skipCultivate", skipCultivateConfirm)
            putBoolean("skipBreakthrough", skipBreakthroughConfirm)
            putBoolean("skipAwaken", skipAwakenConfirm)
            putBoolean("skipPrestige", skipPrestigeConfirm)
            putBoolean("skipNotifyBoss", skipNotifyBossPurchase)
            putBoolean("skipNotifyShop", skipNotifyShopPurchase)
            putBoolean("skipNotifyDrop", skipNotifyHighDrop)
            // 新手引导
            putInt("tutorialStep", tutorialStep)
            // 功能解锁通知日志
            val featureLogArr = JSONArray()
            unlockedFeatureLog.forEach { featureLogArr.put(it) }
            putString("unlockedFeatureLog", featureLogArr.toString())
            // 地图自动跳转
            putBoolean("autoAdvanceMap", autoAdvanceMap)
            // 限时珍品
            putLong("limitedShopRefresh", limitedShopRefreshTime)
            val lsRingArr = JSONArray()
            limitedShopRings.forEach { r ->
                val obj = JSONObject()
                obj.put("year", r.yearOrdinal)
                obj.put("quality", r.qualityOrdinal)
                val affArr = JSONArray()
                r.affixes.forEach { aff ->
                    val aObj = JSONObject()
                    aObj.put("type", aff.type.ordinal)
                    aObj.put("value", aff.value)
                    affArr.put(aObj)
                }
                obj.put("affixes", affArr)
                if (r.skill != null) obj.put("skillName", r.skill.name)
                obj.put("percentage", r.percentage)
                lsRingArr.put(obj)
            }
            putString("limitedShopRings", lsRingArr.toString())
            val lsBoneArr = JSONArray()
            limitedShopBones.forEach { b ->
                val obj = JSONObject()
                obj.put("slot", b.boneTypeOrdinal)
                obj.put("year", b.yearOrdinal)
                obj.put("rarity", b.rarityOrdinal)
                val affArr = JSONArray()
                b.affixes.forEach { aff ->
                    val aObj = JSONObject()
                    aObj.put("type", aff.type.ordinal)
                    aObj.put("value", aff.value)
                    affArr.put(aObj)
                }
                obj.put("affixes", affArr)
                if (b.passiveSkill != null) obj.put("passiveName", b.passiveSkill.name)
                lsBoneArr.put(obj)
            }
            putString("limitedShopBones", lsBoneArr.toString())
            apply()
        }
    }

    fun load() {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        level = sp.getInt("level", 1)
        gold = sp.getLong("gold", 0)
        soulPower = sp.getLong("soulPower", 0)

        val wuhunName = sp.getString("wuhunName", "") ?: ""
        martialSoul = MartialSoulPool.all.find { it.name == wuhunName }
        val schoolName = sp.getString("chosenSchool", "") ?: ""
        chosenSchool = SoulSchool.entries.find { it.name == schoolName }

        // 魂环 (带词缀+槽位+技能)
        soulRings.clear()
        try {
            val ringStr = sp.getString("soulRings", "[]") ?: "[]"
            val ringArr = JSONArray(ringStr)
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

        // 魂骨 (带词缀+被动)
        soulBones.clear()
        try {
            val boneStr = sp.getString("soulBones", "{}") ?: "{}"
            val boneObj = JSONObject(boneStr)
            for (key in boneObj.keys()) {
                val bObj = boneObj.getJSONObject(key)
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

        currentHp = sp.getLong("currentHp", 100)
        currentMapId = sp.getInt("currentMapId", 0)
        currentFloor = sp.getInt("currentFloor", 1)
        currentStage = sp.getInt("currentStage", 1)
        inBattle = sp.getBoolean("inBattle", false)
        totalBattleWins = sp.getLong("totalBattleWins", 0)
        totalBattleLosses = sp.getLong("totalBattleLosses", 0)

        towerFloor = sp.getInt("towerFloor", 0)
        towerBossKills = sp.getInt("towerBossKills", 0)
        towerTempHp = sp.getLong("towerTempHp", 0)
        towerInRun = sp.getBoolean("towerInRun", false)
        killingIntent = sp.getInt("killingIntent", 0)
        unlockedTowerTitles.clear()
        try {
            val ttStr = sp.getString("unlockedTowerTitles", "[]") ?: "[]"
            val ttArr = JSONArray(ttStr)
            for (i in 0 until ttArr.length()) unlockedTowerTitles.add(ttArr.getString(i))
        } catch (_: Exception) {}
        towerAttributeLevels.clear()
        try {
            val taStr = sp.getString("towerAttributeLevels", "{}") ?: "{}"
            val taObj = JSONObject(taStr)
            for (key in taObj.keys()) towerAttributeLevels[key] = taObj.getInt(key)
        } catch (_: Exception) {}
        lastDungeonTime = sp.getLong("lastDungeonTime", 0)
        dungeonTierCompleted = sp.getInt("dungeonTierCompleted", -1)

        try {
            val shopStr = sp.getString("shopLevels", "{}") ?: "{}"
            val shopObj = JSONObject(shopStr)
            for (key in shopObj.keys()) {
                shopLevels[key.toInt()] = shopObj.getInt(key)
            }
        } catch (_: Exception) {}

        totalGoldEarned = sp.getLong("totalGoldEarned", 0)

        unlockedAchievements.clear()
        try {
            val achStr = sp.getString("achievements", "[]") ?: "[]"
            val achArr = JSONArray(achStr)
            for (i in 0 until achArr.length()) {
                unlockedAchievements.add(achArr.getString(i))
            }
        } catch (_: Exception) {}

        prestigeCount = sp.getInt("prestigeCount", 0)
        // 天赋树
        talentPoints = sp.getInt("talentPoints", 0)
        talentLevels.clear()
        try {
            val tStr = sp.getString("talentLevels", "{}") ?: "{}"
            val tObj = JSONObject(tStr)
            TalentBranch.entries.forEach { branch ->
                talentLevels[branch] = tObj.optInt(branch.name, 0)
            }
        } catch (_: Exception) {}
        autoBattle = sp.getBoolean("autoBattle", false)
        skillCooldown = sp.getInt("skillCooldown", 0)
        codexKills = sp.getLong("codexKills", 0)
        lastExitTime = sp.getLong("lastExit", 0)
        smallKillStreak = sp.getInt("smallKillStreak", 0)
        bossDeathCount = sp.getInt("bossDeathCount", 0)
        mapStars.clear()
        try {
            val starStr = sp.getString("mapStars", "{}") ?: "{}"
            val starObj = JSONObject(starStr)
            for (key in starObj.keys()) { mapStars[key.toInt()] = starObj.getInt(key) }
        } catch (_: Exception) {}

        // V2新字段加载
        bossCoin = sp.getLong("bossCoin", 0)
        autoBreakthrough = sp.getBoolean("autoBreakthrough", true)
        autoAdvanceMap = sp.getBoolean("autoAdvanceMap", true)
        battleSoulPower = sp.getInt("battleSoulPower", 100).coerceAtMost(RealmData.battleSoulPowerMax(level))
        // 多槽位魂核加载（兼容旧档单核）
        equippedSoulCores.clear()
        for (slot in SoulCoreSlotType.entries) {
            equippedSoulCores[slot] = null
        }
        try {
            // 尝试加载多槽位新格式
            for (slot in SoulCoreSlotType.entries) {
                val scStr = sp.getString("equippedSoulCore_${slot.name}", null)
                if (scStr != null) {
                    val scObj = JSONObject(scStr)
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
        // 兼容旧档：如果新格式无数据但有旧格式单核，迁移到攻击槽
        val oldScStr = sp.getString("equippedSoulCore", null)
        if (oldScStr != null && equippedSoulCores.values.all { it == null }) {
            try {
                val scObj = JSONObject(oldScStr)
                val scName = scObj.getString("name")
                val scRarity = scObj.getInt("rarity")
                val scPassiveName = scObj.getString("passiveName")
                val scValue = scObj.getInt("value")
                val scPassive = PassiveSkillPool.all.find { it.name == scPassiveName }
                    ?: PassiveSkill("未知", PassiveSkillType.STAT_BOOST, listOf(0), "")
                equippedSoulCores[SoulCoreSlotType.ATTACK] = SoulCoreInstance(scName, scRarity, scPassive, scValue, 0, SoulCoreSlotType.ATTACK)
            } catch (_: Exception) {}
        }
        backpackSoulCores.clear()
        try {
            val scArrStr = sp.getString("bpSoulCores", "[]") ?: "[]"
            val scArr = JSONArray(scArrStr)
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

        // 背包加载(含技能/被动)
        backpackRings.clear()
        try {
            val bpRStr = sp.getString("bpRings", "[]") ?: "[]"
            val bpRArr = JSONArray(bpRStr)
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
            val bpBStr = sp.getString("bpBones", "[]") ?: "[]"
            val bpBArr = JSONArray(bpBStr)
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

        autoSellRings = sp.getBoolean("autoSellRings", false)
        autoSellBones = sp.getBoolean("autoSellBones", false)
        autoSellRingThreshold = sp.getInt("autoSellRingTh", 1)
        autoSellBoneThreshold = sp.getInt("autoSellBoneTh", 0)
        // 掉落过滤加载
        ringFilterYears.clear()
        ringFilterQualities.clear()
        boneFilterYears.clear()
        boneFilterRarities.clear()
        try {
            JSONArray(sp.getString("ringFilterYears", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) ringFilterYears.add(arr.getInt(i))
            }
            JSONArray(sp.getString("ringFilterQualities", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) ringFilterQualities.add(arr.getInt(i))
            }
            JSONArray(sp.getString("boneFilterYears", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) boneFilterYears.add(arr.getInt(i))
            }
            JSONArray(sp.getString("boneFilterRarities", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) boneFilterRarities.add(arr.getInt(i))
            }
        } catch (_: Exception) {}
        // 魂核过滤加载
        soulCoreFilterRarities.clear()
        soulCoreFilterCategories.clear()
        try {
            JSONArray(sp.getString("soulCoreFilterRarities", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) soulCoreFilterRarities.add(arr.getInt(i))
            }
            JSONArray(sp.getString("soulCoreFilterCategories", "[]") ?: "[]").let { arr ->
                for (i in 0 until arr.length()) soulCoreFilterCategories.add(SoulCoreCategory.entries[arr.getInt(i)])
            }
        } catch (_: Exception) {}
        // 卡级修炼加载
        isLevelCapped = sp.getBoolean("isLevelCapped", false)
        excessSoulPower = sp.getLong("excessSoulPower", 0L)
        capStatLevels.clear()
        try {
            val capStr = sp.getString("capStatLevels", "{}") ?: "{}"
            val capObj = JSONObject(capStr)
            for (key in capObj.keys()) capStatLevels[key] = capObj.getInt(key)
        } catch (_: Exception) {}
        // 技能自动开关加载
        disabledAutoSkillSlots.clear()
        try {
            val dsStr = sp.getString("disabledAutoSkillSlots", "[]") ?: "[]"
            val dsArr = JSONArray(dsStr)
            for (i in 0 until dsArr.length()) disabledAutoSkillSlots.add(dsArr.getInt(i))
        } catch (_: Exception) {}
        backpackCapacityTier = sp.getInt("backpackCapTier", 0)
        freeExpandRemaining = sp.getInt("freeExpandRemaining", 0)
        // 锁定集合加载
        lockedBackpackRingIndices.clear()
        try {
            val lockRStr = sp.getString("lockedRings", "[]") ?: "[]"
            val lockRArr = JSONArray(lockRStr)
            for (i in 0 until lockRArr.length()) {
                lockedBackpackRingIndices.add(lockRArr.getInt(i))
            }
        } catch (_: Exception) {}
        lockedBackpackBoneIndices.clear()
        try {
            val lockBStr = sp.getString("lockedBones", "[]") ?: "[]"
            val lockBArr = JSONArray(lockBStr)
            for (i in 0 until lockBArr.length()) {
                lockedBackpackBoneIndices.add(lockBArr.getInt(i))
            }
        } catch (_: Exception) {}
        skipCultivateConfirm = sp.getBoolean("skipCultivate", false)
        skipBreakthroughConfirm = sp.getBoolean("skipBreakthrough", false)
        skipAwakenConfirm = sp.getBoolean("skipAwaken", false)
        skipPrestigeConfirm = sp.getBoolean("skipPrestige", false)
        skipNotifyBossPurchase = sp.getBoolean("skipNotifyBoss", false)
        skipNotifyShopPurchase = sp.getBoolean("skipNotifyShop", false)
        skipNotifyHighDrop = sp.getBoolean("skipNotifyDrop", false)
        // 新手引导：旧档无此字段返回0，自动跳过引导
        tutorialStep = sp.getInt("tutorialStep", 0).let { orig ->
            if (orig == 0 && (chosenSchool != null || level > 1)) 7
            else if (orig >= 6) 7
            else orig.coerceIn(1, 5)
        }
        // 功能解锁通知日志
        unlockedFeatureLog.clear()
        try {
            val flStr = sp.getString("unlockedFeatureLog", "[]") ?: "[]"
            val flArr = JSONArray(flStr)
            for (i in 0 until flArr.length()) unlockedFeatureLog.add(flArr.getString(i))
        } catch (_: Exception) {}
        // 限时珍品
        limitedShopRefreshTime = sp.getLong("limitedShopRefresh", 0L)
        limitedShopRings.clear()
        try {
            val lsRStr = sp.getString("limitedShopRings", "[]") ?: "[]"
            val lsRArr = JSONArray(lsRStr)
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
            val lsBStr = sp.getString("limitedShopBones", "[]") ?: "[]"
            val lsBArr = JSONArray(lsBStr)
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

        // ====== 修复：确保已装备魂环/魂骨不超过当前等级上限 ======
        val maxRings = RealmData.maxSoulRings(level)
        soulRings.keys.toList().filter { it >= maxRings }.forEach { soulRings.remove(it) }
        val maxBones = RealmData.maxSoulBones(level)
        soulBones.keys.toList().filter { it >= maxBones }.forEach { soulBones.remove(it) }
    }
}
