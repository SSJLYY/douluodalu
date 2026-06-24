package com.example.garygame.engine

import android.content.Context
import com.example.garygame.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object GameEngine {
    lateinit var state: GameState
        private set

    private val listeners = mutableListOf<() -> Unit>()
    private val logMessages = mutableListOf<String>()
    private var lastCultivateTime: Long = 0
    private var tickCount: Int = 0  // 用于定期自动存档(每60 tick≈30秒)

    // ======== 性能优化：批量UI刷新 + 属性缓存 ========
    /** 自动战斗时，延迟所有notifyUI到tick末尾统一刷新 */
    private var batchNotify = false
    private var batchNotifyPending = false
    /** 单tick内属性缓存：自动战斗多次doBattle()复用 */
    private var tickCachedAttr: PlayerAttributes? = null

    // ======== 魂技魂力消耗层级倍率(低级便宜·高级昂贵，迫使后期协调技能配置) ========
    fun soulCostMult(skill: ActiveSkill): Int = when (skill.tier) {
        0 -> 1      // 百年: ×1 (便宜，持续可用)
        1 -> 2      // 千年: ×2
        2 -> 4      // 万年: ×4
        3 -> 7      // 十万年: ×7 (昂贵)
        4 -> 12     // 百万年: ×12 (非常昂贵，不可随意释放)
        else -> 2
    }

    // ======== 通知事件系统 ========
    enum class NotifyType { TITLE, BOSS_PURCHASE, SHOP_PURCHASE, HIGH_DROP, FEATURE_UNLOCK }
    data class NotifyEvent(val type: NotifyType, val title: String, val message: String)
    private val pendingNotifyEvents = mutableListOf<NotifyEvent>()

    // ======== 功能解锁系统 ========

    /** 所有需要等级解锁的游戏功能 */
    enum class FeatureType(
        val unlockLevel: Int,
        val icon: String,
        val displayName: String,
        val description: String
    ) {
        SHOP(3, "🏪", "商店", "购买基础物资（魂环/魂骨材料）"),
        ADVENTURE(5, "🗺️", "冒险", "探索地图，攻略关卡获取更高级材料"),
        AUTO_BATTLE(8, "🤖", "自动战斗", "开启自动挂机战斗，解放双手"),
        ACHIEVEMENT(10, "🏆", "成就系统", "完成成就目标获得全属性奖励"),
        SOUL_RING(12, "💍", "魂环系统", "吸收魂环获得主动技能增强战力"),
        SOUL_BONE(15, "🦴", "魂骨系统", "装备魂骨获得被动属性加成"),
        TALENT(22, "🌳", "天赋树", "消耗天赋点获得永久能力加成"),
        BOSS_SHOP(25, "👑", "Boss商店", "使用Boss货币兑换稀有魂环魂骨"),
        KILLING_CITY(35, "🏰", "杀戮之都", "挑战高塔层数获取杀气奖励"),
        LEVEL_CAP(10, "🔒", "卡级修炼", "在境界门槛卡级，积累超额魂力凝练属性"),
        SOUL_CORE(45, "💠", "魂核系统", "装备魂核获得特殊战斗特效"),
        SUPPORT_SCHOOL(50, "💚", "辅助流派", "解锁辅助流派（需转生1次）"),
        SKILL_TOGGLE(55, "🔄", "技能开关", "手动控制各魂环技能的自动释放"),
        LIMITED_SHOP(65, "⏳", "限时珍品", "限时刷新稀有魂环魂骨，10分钟刷新一次"),
        DAILY_DUNGEON(75, "📅", "每日副本", "每日挑战获取大量经验和金币奖励"),
        CONTROL_SCHOOL(70, "🧠", "控制流派", "解锁控制流派（需转生2次）"),
        ASSASSIN_SCHOOL(90, "🗡️", "刺客流派", "解锁刺客流派（需转生3次）"),
        PRESTIGE(100, "🔄", "神位传承", "重置等级获得天赋点，全属性永久提升");

        val shownName: String get() = "${icon} ${displayName}"

        /** 是否依赖额外的转生条件 */
        val extraPrestigeRequired: Int get() = when (this) {
            SUPPORT_SCHOOL -> 1
            CONTROL_SCHOOL -> 2
            ASSASSIN_SCHOOL -> 3
            DAILY_DUNGEON -> 1
            else -> 0
        }
    }

    fun isFeatureUnlocked(feature: FeatureType): Boolean {
        val s = state
        if (s.level < feature.unlockLevel) return false
        if (s.prestigeCount < feature.extraPrestigeRequired) return false
        return true
    }

    fun getFeatureLockReason(feature: FeatureType): String {
        return when {
            state.level < feature.unlockLevel -> "Lv.${feature.unlockLevel}解锁"
            state.prestigeCount < feature.extraPrestigeRequired ->
                "需转生${feature.extraPrestigeRequired}次"
            else -> "已解锁"
        }
    }

    /** 检查是否有新功能解锁（突破/转生后调用） */
    fun checkForNewFeatures(): List<FeatureType> {
        val newlyUnlocked = mutableListOf<FeatureType>()
        val s = state
        FeatureType.entries.forEach { feature ->
            if (feature.shownName !in s.unlockedFeatureLog && isFeatureUnlocked(feature)) {
                s.unlockedFeatureLog.add(feature.shownName)
                newlyUnlocked.add(feature)
                addLog("🎉 新功能解锁：【${feature.icon} ${feature.displayName}】")
                addLog("   💡 ${feature.description}")
            }
        }
        if (newlyUnlocked.isNotEmpty()) s.save()
        return newlyUnlocked
    }

    /** 获取新解锁的功能（供UI弹窗通知用） */
    private val pendingFeatureUnlocks = mutableListOf<FeatureType>()

    fun pollNewFeatureUnlocks(): List<FeatureType> {
        val list = pendingFeatureUnlocks.toList()
        pendingFeatureUnlocks.clear()
        return list
    }

    fun pollNotifyEvents(): List<NotifyEvent> {
        val events = pendingNotifyEvents.toList()
        pendingNotifyEvents.clear()
        return events
    }

    fun pushNotifyEvent(type: NotifyType, title: String, message: String) {
        pendingNotifyEvents.add(NotifyEvent(type, title, message))
    }

    // ======== 战斗视觉数据（供UI层渲染） ========
    /** 每个回合的伤害/技能/怪物状态 */
    data class CombatRoundData(
        val roundNum: Int = 0,
        val skillName: String? = null,
        val playerDmg: Long = 0,
        val isCrit: Boolean = false,
        val targetIndex: Int = 0,
        val totalTargets: Int = 1,
        val monsterName: String = "",
        val monsterHpAfter: Long = 0,
        val monsterMaxHp: Long = 0,
        val monsterDied: Boolean = false,
        val playerDmgTaken: Long = 0,
        val isDodged: Boolean = false,
        val isBlocked: Boolean = false,
        val isThorned: Boolean = false
    )

    /** 单场通关的掉落汇总 */
    data class BattleDropGroup(
        val gold: Long = 0,
        val exp: Long = 0,
        val bossCoin: Long = 0,
        val stageType: String = "",
        val stageNum: Int = 0,
        val ringDrop: String? = null,
        val boneDrop: String? = null
    )

    /** 最新回合数据（每doBattle更新） */
    var lastCombatRound: CombatRoundData? = null

    /** 最新通关掉落汇总（每onBattleWin更新） */
    var lastBattleDrops: BattleDropGroup? = null

    /** 临时掉落追踪(仅供onBattleWin内部使用, 每次刷新) */
    private var _tmpRingDropName: String? = null
    private var _tmpBoneDropName: String? = null
    private var _tmpStageGold: Long = 0L
    private var _tmpStageExp: Long = 0L
    private var _tmpStageBossCoin: Long = 0L

    /** 获取冷却修炼剩余秒数（0=就绪） */
    fun getCultivateCooldownRemaining(): Int {
        val elapsed = System.currentTimeMillis() - lastCultivateTime
        if (elapsed >= 5000) return 0
        return ((5000 - elapsed) / 1000).toInt() + 1
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyUI() {
        if (batchNotify) { batchNotifyPending = true; return }
        listeners.forEach { it() }
    }

    fun getLog(): List<String> = logMessages.toList()
    fun clearLog() { logMessages.clear() }

    fun addLog(msg: String) {
        logMessages.add(0, msg)
        if (logMessages.size > 50) logMessages.removeAt(logMessages.size - 1)
    }

    fun init(context: Context) {
        state = GameState(context)
        state.load()
        if (state.currentHp <= 0) state.currentHp = calcMaxHp()
        if (state.currentMonsters.isEmpty()) {
            state.currentMonsters.addAll(generateMonsterWave())
            state.currentMonster = state.currentMonsters.firstOrNull()
        }
    }

    // ======== 属性计算 ========

    fun calcAttributes(): PlayerAttributes {
        if (tickCachedAttr != null) return tickCachedAttr!!
        val result = computeAttributes()
        tickCachedAttr = result
        return result
    }

    /** 实际计算属性（无缓存） */
    private fun computeAttributes(): PlayerAttributes {
        val s = state
        var maxHp = RealmData.baseHp(s.level)
        var atk = RealmData.baseAtk(s.level)
        var matk = RealmData.baseMatk(s.level)
        var pdef = RealmData.basePdef(s.level)
        var mdef = RealmData.baseMdef(s.level)
        var critRate = 5
        var critDmg = 150

        s.martialSoul?.let { soul ->
            maxHp += soul.baseHp; atk += soul.baseAtk; matk += soul.baseMatk
            critRate += soul.critRate; critDmg += soul.critDmg
            pdef += soul.pdef; mdef += soul.mdef
        }

        // 【武魂改革】流派属性偏重 - 在武魂基础上应用流派系数
        val school = s.chosenSchool ?: s.martialSoul?.school
        if (school != null) {
            val mod = SchoolStatMods.get(school)
            maxHp = (maxHp * mod.hp).toLong()
            atk = (atk * mod.atk).toInt()
            matk = (matk * mod.matk).toInt()
            pdef = (pdef * mod.pdef).toInt()
            mdef = (mdef * mod.mdef).toInt()
            critRate += mod.critRateBonus
            critDmg += mod.critDmgBonus
        }

        // 每10级境界突破大加成（已完成境界数×25%）
        val realmMult = RealmData.realmBonusMult(s.level)
        maxHp = (maxHp * realmMult).toLong()
        atk = (atk * realmMult).toInt()
        matk = (matk * realmMult).toInt()
        pdef = (pdef * realmMult).toInt()
        mdef = (mdef * realmMult).toInt()

        for (ring in s.soulRings.values) {
            val ringPctMult = 1.0 + ring.percentage / 1000.0  // 成熟度倍率（1.1~2.0），年份差距由词缀基础值体现
            for (affix in ring.affixes) {
                val soulMasterLv = s.getTalentLevel(TalentBranch.SOUL_MASTER)
                val affixMult = (1.0 + soulMasterLv * 0.08) * ringPctMult
                when (affix.type) {
                    EquipAffix.HP -> maxHp += (affix.value * affixMult).toLong()
                    EquipAffix.ATK -> atk += (affix.value * affixMult).toInt()
                    EquipAffix.MATK -> matk += (affix.value * affixMult).toInt()
                    EquipAffix.CRIT_RATE -> critRate += (affix.value * affixMult).toInt()
                    EquipAffix.CRIT_DMG -> critDmg += (affix.value * affixMult).toInt()
                    EquipAffix.PDEF -> pdef += (affix.value * affixMult).toInt()
                    EquipAffix.MDEF -> mdef += (affix.value * affixMult).toInt()
                }
            }
        }

        for (bone in s.soulBones.values) {
            val enhMult = EnhancementData.enhanceEffect(bone.enhanceLevel)
            val soulMasterLv = s.getTalentLevel(TalentBranch.SOUL_MASTER)
            val affixMult = enhMult * (1.0 + soulMasterLv * 0.08)
            for (affix in bone.affixes) {
                when (affix.type) {
                    EquipAffix.HP -> maxHp += (affix.value * affixMult).toLong()
                    EquipAffix.ATK -> atk += (affix.value * affixMult).toInt()
                    EquipAffix.MATK -> matk += (affix.value * affixMult).toInt()
                    EquipAffix.CRIT_RATE -> critRate += (affix.value * affixMult).toInt()
                    EquipAffix.CRIT_DMG -> critDmg += (affix.value * affixMult).toInt()
                    EquipAffix.PDEF -> pdef += (affix.value * affixMult).toInt()
                    EquipAffix.MDEF -> mdef += (affix.value * affixMult).toInt()
                }
            }
        }

        // 战斗升级已移除——过去是最终乘数，与转生乘算导致数值爆炸

        atk += s.towerBossKills * 15  // 每击杀Boss永久+15ATK

        // V3: 杀气称号属性
        for (title in TowerShopData.titleItems) {
            if (s.unlockedTowerTitles.contains(title.id)) {
                maxHp += title.hp; atk += title.atk; matk += title.matk
                pdef += title.pdef; mdef += title.mdef
                critRate += title.critRate; critDmg += title.critDmg
            }
        }

        // V3: 杀气属性购买次数
        for (stat in TowerShopData.statItems) {
            val count = s.towerAttributeLevels[stat.id] ?: 0
            if (count > 0) {
                maxHp += stat.hp * count
                atk += stat.atk * count
            }
        }

        // 天赋树：战神之道（百分比加成，在装备/转生之后叠加）
        val warGodLv = s.getTalentLevel(TalentBranch.WAR_GOD)
        if (warGodLv > 0) {
            val atkMult = 1.0 + warGodLv * 0.06
            val defMult = 1.0 + (warGodLv * 0.03).coerceAtMost(0.09)
            atk = (atk * atkMult).toInt()
            matk = (matk * atkMult).toInt()
            pdef = (pdef * defMult).toInt()
            mdef = (mdef * defMult).toInt()
            if (warGodLv >= 3) critRate += 5
        }

        // 天赋树：神祇之道（生命加成+减伤在战斗中处理）
        val divineLv = s.getTalentLevel(TalentBranch.DIVINE)
        if (divineLv > 0) {
            val hpMult = 1.0 + divineLv * 0.10
            maxHp = (maxHp * hpMult).toLong()
            // 减伤效果在 resolveBattle 中处理
        }

        // 成就全属性奖励
        AchievementDefs.all.forEach { ach ->
            if (s.unlockedAchievements.contains(ach.id)) {
                val r = ach.rewards
                maxHp += r.hp; atk += r.atk; matk += r.matk
                pdef += r.pdef; mdef += r.mdef
                critRate += r.critRate; critDmg += r.critDmg
            }
        }

        // V3: 转生属性加成削弱（0.2→0.1，延长游戏寿命）
        val prestigeMult = 1.0 + s.prestigeCount * 0.1
        maxHp = (maxHp * prestigeMult).toLong()
        atk = (atk * prestigeMult).toInt()
        matk = (matk * prestigeMult).toInt()
        pdef = (pdef * prestigeMult).toInt()
        mdef = (mdef * prestigeMult).toInt()

        // 魂骨套装效果（阶梯：2件+全套）
        val activeSets = SoulBoneSetData.getActiveSets(s.soulBones)
        for (info in activeSets) {
            val st = info.set
            if (info.hasTier2) {
                maxHp += st.tier2Hp; atk += st.tier2Atk
                pdef += st.tier2Def; mdef += st.tier2Def
                critRate += st.tier2Crit
            }
            if (info.isFullSet) {
                maxHp += st.fullHp; atk += st.fullAtk
                pdef += st.fullDef; mdef += st.fullDef
                critRate += st.fullCrit
            }
        }
        // 图鉴永久加成
        atk += CodexData.codexAtkBonus(s.codexKills)

        // V2: 魂骨被动技能加成
        for (bone in s.soulBones.values) {
            bone.passiveSkill?.let { ps ->
                val valAtRarity = ps.getValue(bone.combinedTier)
                when (ps.type) {
                    PassiveSkillType.STAT_BOOST -> {
                        if (ps.name.contains("物攻") || ps.name == "力量增幅" || ps.name == "强攻" || ps.name == "力量") atk += (atk * valAtRarity / 100).coerceAtLeast(1)
                        if (ps.name.contains("魔防") || ps.name == "魔抗") mdef += (mdef * valAtRarity / 100).coerceAtLeast(1)
                        if (ps.name.contains("物防") || ps.name == "铁壁") pdef += (pdef * valAtRarity / 100).coerceAtLeast(1)
                        if (ps.name.contains("生命") || ps.name == "生命源泉") maxHp += (maxHp * valAtRarity / 100).coerceAtLeast(1)
                        if (ps.name.contains("全属性") || ps.name == "起源") {
                            maxHp += (maxHp * valAtRarity / 100).coerceAtLeast(1)
                            atk += (atk * valAtRarity / 100).coerceAtLeast(1)
                            matk += (matk * valAtRarity / 100).coerceAtLeast(1)
                            pdef += (pdef * valAtRarity / 100).coerceAtLeast(1)
                            mdef += (mdef * valAtRarity / 100).coerceAtLeast(1)
                            critRate += valAtRarity
                        }
                        if (ps.name.contains("双防") || ps.name == "守护") {
                            pdef += (pdef * valAtRarity / 100).coerceAtLeast(1)
                            mdef += (mdef * valAtRarity / 100).coerceAtLeast(1)
                        }
                    }
                    PassiveSkillType.CRIT_BOOST -> {
                        if (ps.name.contains("暴击") || ps.name == "会心一击" || ps.name == "灵巧" || ps.name == "暴击之核") critRate += valAtRarity
                        if (ps.name.contains("暴伤") || ps.name == "毁灭打击" || ps.name == "毁灭") critDmg += valAtRarity
                        if (ps.name == "弱点打击") critDmg += valAtRarity * 2
                    }
                    PassiveSkillType.LIFESTEAL -> {} // 战斗中处理
                    PassiveSkillType.DMG_REDUCE -> {} // 战斗中处理
                    PassiveSkillType.THORNS -> {} // 战斗中处理
                    PassiveSkillType.DODGE -> {} // 战斗中处理
                    PassiveSkillType.REGEN -> {} // tick中处理
                    PassiveSkillType.EXECUTE -> {} // 战斗中处理
                    PassiveSkillType.RAGE -> {} // 战斗中处理
                    PassiveSkillType.ARMOR_BREAK -> {} // 战斗中处理
                    else -> {}
                }
            }
        }

        // V2: 魂核被动加成（多槽位）
        for (core in s.equippedSoulCores.values) {
            if (core == null) continue
            val valAtRarity = core.effectiveValue
            when (core.passiveSkill.name) {
                "生命" -> maxHp += (maxHp * valAtRarity / 100).coerceAtLeast(1)
                "力量" -> atk += (atk * valAtRarity / 100).coerceAtLeast(1)
                "守护" -> { pdef += (pdef * valAtRarity / 100).coerceAtLeast(1); mdef += (mdef * valAtRarity / 100).coerceAtLeast(1) }
                "暴击" -> critRate += valAtRarity
                "毁灭" -> critDmg += valAtRarity
                "起源" -> {
                    maxHp += (maxHp * valAtRarity / 100).coerceAtLeast(1)
                    atk += (atk * valAtRarity / 100).coerceAtLeast(1)
                    matk += (matk * valAtRarity / 100).coerceAtLeast(1)
                    pdef += (pdef * valAtRarity / 100).coerceAtLeast(1)
                    mdef += (mdef * valAtRarity / 100).coerceAtLeast(1)
                }
            }
        }

        // 卡级修炼凝练属性加成
        val capHpLv = s.capStatLevels.getOrDefault("hp", 0)
        val capAtkLv = s.capStatLevels.getOrDefault("atk", 0)
        val capMatkLv = s.capStatLevels.getOrDefault("matk", 0)
        val capPdefLv = s.capStatLevels.getOrDefault("pdef", 0)
        val capMdefLv = s.capStatLevels.getOrDefault("mdef", 0)
        maxHp += capHpLv * 50L
        atk += capAtkLv * 10
        matk += capMatkLv * 8
        pdef += capPdefLv * 5
        mdef += capMdefLv * 4

        return PlayerAttributes(
            maxHp = maxHp.coerceAtLeast(1), atk = atk.coerceAtLeast(1),
            matk = matk.coerceAtLeast(1), critRate = critRate.coerceIn(0, 95),
            critDmg = critDmg.coerceAtLeast(100), pdef = pdef.coerceAtLeast(0),
            mdef = mdef.coerceAtLeast(0)
        )
    }

    fun calcMaxHp(): Long = calcAttributes().maxHp
    fun calcAttackPower(): Int = calcAttributes().atk

    /** 计算根骨值（基于属性综合评分，转生属性加成自然参与计算但不额外加乘上限） */
    fun calcRootBone(): Double {
        val attr = calcAttributes()
        // 转生提升的属性作为属性本身参与计算，无额外上限加成
        return SoulRingSystem.calcRootBone(
            attr.maxHp, attr.atk, attr.matk, attr.pdef, attr.mdef
        )
    }

    /** 计算吸收容量 */
    fun calcAbsorptionCapacity(): Long =
        SoulRingSystem.calcAbsorptionCapacity(calcRootBone())

    /** 计算当前已装备魂环总负荷 */
    fun calcTotalRingLoad(): Long =
        state.soulRings.values.sumOf { it.load }

    fun getPlayerSkill(): Skill {
        return state.martialSoul?.skill ?: Skill("普通攻击", "基础攻击", SkillType.SINGLE_DAMAGE, 100, 3)
    }

    // ======== V2: 怪物波次生成(1vN) ========

    fun generateMonsterWave(): List<Monster> {
        val s = state
        val mapId = s.currentMapId
        val stage = s.currentStage
        val stageType = StageData.getStageType(stage)
        val count = StageData.getMonsterCount(stage)
        val scale = StageData.stageScale(stage)
        val monsters = mutableListOf<Monster>()

        for (i in 0 until count) {
            val isBoss = stageType == StageType.BOSS
            val monster = if (isBoss) MonsterGenerator.generateBoss(mapId, stage)
                          else MonsterGenerator.generateMonster(mapId, stage)
            // 关卡缩放
            val scaled = monster.copy(
                maxHp = (monster.maxHp * scale).toLong().coerceAtLeast(1),
                atk = (monster.atk * scale).toInt().coerceAtLeast(1),
                matk = (monster.matk * scale).toInt().coerceAtLeast(1),
                pdef = (monster.pdef * scale).toInt().coerceAtLeast(1),
                mdef = (monster.mdef * scale).toInt().coerceAtLeast(1),
                hp = (monster.hp * scale).toLong().coerceAtLeast(1),
                expReward = (monster.expReward * scale).toLong().coerceAtLeast(1),
                goldReward = (monster.goldReward * scale).toLong().coerceAtLeast(1)
            )
            // Boss命名
            val bossNames = StageData.bossNames(mapId)
            val finalMonster = if (isBoss && bossNames.isNotEmpty()) {
                val bossName = bossNames[i.coerceAtMost(bossNames.size - 1)]
                scaled.copy(name = bossName)
            } else scaled
            monsters.add(finalMonster)
        }
        return monsters
    }

    // ======== V2: 主动技能系统(魂环技能) ========

    /** 武魂魂技槽位常量 */
    const val WUHUN_SKILL_SLOT = -1

    /** 获取可以释放的魂环主动技能列表(有魂力且冷却结束)，含武魂本命魂技 */
    fun getAvailableRingSkills(): List<Pair<Int, ActiveSkill>> {
        val s = state
        val available = mutableListOf<Pair<Int, ActiveSkill>>()
        for ((slotIdx, ring) in s.soulRings) {
            val skill = ring.skill ?: continue
            val cd = s.activeSkillCooldowns[slotIdx] ?: 0
            if (cd <= 0 && s.battleSoulPower >= skill.soulCost * soulCostMult(skill)) {
                available.add(slotIdx to skill)
            }
        }
        // 武魂本命魂技（slot=-1，不受魂力限制，只看冷却）
        if (state.martialSoul != null) {
            val wuhunCd = s.activeSkillCooldowns[WUHUN_SKILL_SLOT] ?: 0
            if (wuhunCd <= 0) {
                val wuhunSkill = wuhunToActiveSkill(state.martialSoul!!)
                if (wuhunSkill != null) {
                    available.add(WUHUN_SKILL_SLOT to wuhunSkill)
                }
            }
        }
        return available
    }

    /** 获取自动战斗可用的技能(排除玩家手动关闭自动的槽位) */
    private fun getAutoSelectableSkills(): List<Pair<Int, ActiveSkill>> {
        val all = getAvailableRingSkills()
        return all.filter { (slotIdx, _) -> !state.disabledAutoSkillSlots.contains(slotIdx) }
    }

    /** 切换指定魂环槽位的自动释放开关 */
    fun toggleSkillAuto(slotIdx: Int) {
        val s = state
        if (s.disabledAutoSkillSlots.contains(slotIdx)) {
            s.disabledAutoSkillSlots.remove(slotIdx)
            addLog("🟢 槽位${slotIdx + 1}自动释放已开启")
        } else {
            s.disabledAutoSkillSlots.add(slotIdx)
            addLog("🔴 槽位${slotIdx + 1}自动释放已关闭")
        }
        notifyUI()
    }

    /** 指定魂环槽位的自动释放是否启用 */
    fun isSkillAutoEnabled(slotIdx: Int): Boolean = !state.disabledAutoSkillSlots.contains(slotIdx)

    /** 自动选择最佳主动技能（魂环优先，武魂后备） */
    fun selectBestRingSkill(): Pair<Int, ActiveSkill>? {
        val all = getAutoSelectableSkills()
        // 优先选魂环技能（排除武魂slot=-1），再考虑武魂
        val ringSkills = all.filter { it.first >= 0 }
        return if (ringSkills.isNotEmpty()) ringSkills.maxByOrNull { it.second.power }
               else all.maxByOrNull { it.second.power }
    }

    /** 武魂Skill转换为ActiveSkill（统一接口） */
    internal fun wuhunToActiveSkill(soul: MartialSoul): ActiveSkill? = when (soul.skill.type) {
        SkillType.SINGLE_DAMAGE -> ActiveSkill(soul.skill.name, ActiveSkillType.SINGLE_STRIKE, soul.skill.power, 0, soul.skill.cooldown, soul.skill.description, 0)
        SkillType.MULTI_HIT -> ActiveSkill(soul.skill.name, ActiveSkillType.MULTI_HIT, soul.skill.power, 0, soul.skill.cooldown, soul.skill.description, 0)
        SkillType.HEAL -> ActiveSkill(soul.skill.name, ActiveSkillType.HEAL, soul.skill.power, 0, soul.skill.cooldown, soul.skill.description, 0)
        SkillType.IGNORE_DEFENSE -> ActiveSkill(soul.skill.name, ActiveSkillType.SINGLE_STRIKE, soul.skill.power, 0, soul.skill.cooldown, soul.skill.description, 0)
        SkillType.BERSERK -> ActiveSkill(soul.skill.name, ActiveSkillType.FURY, soul.skill.power, 0, soul.skill.cooldown, soul.skill.description, 0)
    }

    /** 判断技能是否为法术型（使用MATK而非ATK） */
    private fun isMagicalSkill(skill: ActiveSkill): Boolean = when (skill.type) {
        ActiveSkillType.AOE_STRIKE, ActiveSkillType.HEAL, ActiveSkillType.SHIELD,
        ActiveSkillType.POISON, ActiveSkillType.BLEED, ActiveSkillType.STUN,
        ActiveSkillType.ICE, ActiveSkillType.AMPLIFY, ActiveSkillType.FURY,
        ActiveSkillType.CHAIN, ActiveSkillType.CURSE, ActiveSkillType.DOMAIN -> true
        ActiveSkillType.SINGLE_STRIKE -> {
            // 法伤系元素技能（火球/凤凰啸天/天雷降世/天使圣光/海神之怒）使用MATK
            skill.name in setOf("火球", "凤凰啸天", "天雷降世", "天使圣光", "海神之怒")
        }
        else -> false  // MULTI_HIT, LIFE_STEAL, EXECUTE, SOUL_DRAIN → 物理
    }

    /** 对单个怪物执行主动技能伤害（slotIdx: 魂环槽位，-1=武魂本命魂技） */
    fun applyActiveSkillDamage(slotIdx: Int, skill: ActiveSkill, attr: PlayerAttributes, monster: Monster): Long {
        val s = state
        s.battleSoulPower = (s.battleSoulPower - skill.soulCost * soulCostMult(skill)).coerceAtLeast(0)
        s.activeSkillCooldowns[slotIdx] = skill.cooldown  // 按槽位存储冷却

        val magical = isMagicalSkill(skill)
        val baseAtk = if (magical) attr.matk else attr.atk
        val baseDef = if (magical) monster.mdef else monster.pdef

        val critRoll = Random.nextInt(100) < attr.critRate
        var totalDmg = 0L

        when (skill.type) {
            ActiveSkillType.SINGLE_STRIKE -> {
                var rawDmg = (baseAtk * skill.power / 100).coerceAtLeast(1)
                val ignoreDef = skill.name == "破甲击" || skill.name == "崩山击" || skill.name == "神魔破" || skill.name == "混沌之击"
                val def = if (ignoreDef) (baseDef * 0.5).toInt() else baseDef
                var dmg = rawDmg * (1.0 - def.toDouble() / (def + 200)).coerceIn(0.1, 1.0)
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = dmg.toLong().coerceAtLeast(1)
            }
            ActiveSkillType.AOE_STRIKE -> {
                var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) *
                    (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0)
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = dmg.toLong().coerceAtLeast(1)
            }
            ActiveSkillType.MULTI_HIT -> {
                val hits = when {
                    skill.name.contains("二连") -> 2; skill.name.contains("三连") || skill.name == "幽冥突刺" || skill.name == "虎爪裂空" -> 3
                    skill.name == "乱披风" || skill.name.contains("百爪") || skill.name == "风暴连斩" -> 4
                    skill.name == "凤凰流星" -> 5; skill.name == "千手连击" -> 6
                    skill.name == "无限连击" -> 4; else -> 2
                }
                var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1)
                var perDmg = dmg * (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0)
                if (critRoll) perDmg *= attr.critDmg / 100.0
                totalDmg = (perDmg.toLong() * hits).coerceAtLeast(1)
            }
            ActiveSkillType.LIFE_STEAL -> {
                var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) *
                    (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0)
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = dmg.toLong().coerceAtLeast(1)
                val healPct = when { skill.name.contains("生命汲取") -> 20; skill.name.contains("噬血") || skill.name == "血祭" -> 40
                    skill.name.contains("血气唤醒") || skill.name.contains("暗影噬魂") -> 50
                    skill.name.contains("血月") -> 60; skill.name.contains("嗜血神术") -> 100
                    skill.name.contains("吞噬") -> 30; else -> 25
                }
                val heal = (totalDmg * healPct / 100).coerceAtLeast(1L)
                s.currentHp = (s.currentHp + heal).coerceAtMost(attr.maxHp)
                addLog("💚 吸血回复 +${formatNum(heal)}HP")
            }
            ActiveSkillType.HEAL -> {
                val healPct = skill.power
                val heal = (attr.maxHp * healPct / 100).coerceAtLeast(1L)
                s.currentHp = (s.currentHp + heal).coerceAtMost(attr.maxHp)
                addLog("💚 回复 +${formatNum(heal)}HP")
                return 0
            }
            ActiveSkillType.SHIELD -> {
                val shieldPct = skill.power
                val shield = (attr.maxHp * shieldPct / 100).coerceAtLeast(1L)
                // 简化: 直接回血
                s.currentHp = (s.currentHp + shield).coerceAtMost(attr.maxHp)
                addLog("🛡️ 护盾 +${formatNum(shield)} (简化为回血)")
            }
            ActiveSkillType.AMPLIFY, ActiveSkillType.FURY -> {
                // Buff：简化为直接增伤效果
                val buffPct = skill.power
                var dmg = ((baseAtk * (100 + buffPct) / 100)).coerceAtLeast(1) *
                    (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0)
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = dmg.toLong().coerceAtLeast(1)
                addLog("⚡ Buff提升伤害!")
            }
            ActiveSkillType.POISON, ActiveSkillType.BLEED -> {
                var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) *
                    (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0)
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = dmg.toLong().coerceAtLeast(1)
            }
            ActiveSkillType.STUN, ActiveSkillType.ICE -> {
                var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) *
                    (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0)
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = dmg.toLong().coerceAtLeast(1)
            }
            ActiveSkillType.CHAIN -> {
                var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) *
                    (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0)
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = (dmg * 1.8).toLong().coerceAtLeast(1) // 链式弹射效果
            }
            ActiveSkillType.EXECUTE -> {
                val hpPct = monster.hp.toDouble() / monster.maxHp
                val execMult = if (hpPct < 0.3) 3.0 else 1.5
                var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) *
                    (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0) * execMult
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = dmg.toLong().coerceAtLeast(1)
            }
            ActiveSkillType.SOUL_DRAIN -> {
                var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) *
                    (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0)
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = dmg.toLong().coerceAtLeast(1)
                val soulSteal = 10 + skill.tier * 3
                s.battleSoulPower += soulSteal
                addLog("💠 魂力汲取 +$soulSteal")
            }
            ActiveSkillType.CURSE, ActiveSkillType.DOMAIN -> {
                var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) *
                    (1.0 - baseDef.toDouble() / (baseDef + 200)).coerceIn(0.1, 1.0)
                if (critRoll) dmg *= attr.critDmg / 100.0
                totalDmg = dmg.toLong().coerceAtLeast(1)
            }
        }

        val critStr = if (critRoll) " 💥暴击!" else ""
        addLog("⚡【${skill.name}】-${formatNum(totalDmg)}$critStr (魂力-${skill.soulCost * soulCostMult(skill)})")
        return totalDmg
    }

    /** 获取所有魂环槽位的冷却信息 */
    fun getRingCooldownInfo(): Map<Int, Pair<ActiveSkill, Int>> {
        val s = state
        val result = mutableMapOf<Int, Pair<ActiveSkill, Int>>()
        for ((slotIdx, ring) in s.soulRings) {
            val skill = ring.skill ?: continue
            val cd = s.activeSkillCooldowns[slotIdx] ?: 0
            result[slotIdx] = skill to cd
        }
        return result
    }

    private fun calcPhysicalDmg(atk: Int, pdef: Int, critRoll: Boolean, critDmg: Int): Long {
        var dmg = atk * (1.0 - pdef.toDouble() / (pdef + 200)).coerceIn(0.1, 1.0)
        if (critRoll) dmg *= critDmg / 100.0
        return max(1L, dmg.toLong())
    }

    private fun calcMagicDmg(matk: Int, mdef: Int, critRoll: Boolean, critDmg: Int): Long {
        var dmg = matk * (1.0 - mdef.toDouble() / (mdef + 200)).coerceIn(0.1, 1.0)
        if (critRoll) dmg *= critDmg / 100.0
        return max(1L, dmg.toLong())
    }

    // ======== 怪物生成 ========

    fun generateCurrentMonster(): Monster {
        var monster = MonsterGenerator.generateMonster(state.currentMapId, state.currentFloor)
        val effect = MapData.getMap(state.currentMapId)?.effect
        if (effect != null && effect.description != "无特殊效果") {
            monster = monster.copy(
                maxHp = (monster.maxHp * effect.monsterHpMult).toLong().coerceAtLeast(1),
                atk = (monster.atk * effect.monsterAtkMult).toInt().coerceAtLeast(1),
                matk = (monster.matk * effect.monsterAtkMult).toInt().coerceAtLeast(1),
                pdef = (monster.pdef * effect.monsterDefMult).toInt().coerceAtLeast(1),
                mdef = (monster.mdef * effect.monsterDefMult).toInt().coerceAtLeast(1),
                expReward = (monster.expReward * effect.expMult).toLong().coerceAtLeast(1),
                goldReward = (monster.goldReward * effect.goldMult).toLong().coerceAtLeast(1)
            )
            monster.hp = (monster.hp * effect.monsterHpMult).toLong().coerceAtLeast(1)
        }
        return monster
    }

    // ======== 战斗系统（回合制：每次点击/每tick = 一回合）========

    /**
     * V2: 多目标战斗系统 (1vN)
     * 每回合攻击当前目标。当前目标死亡后切换到下一目标。
     * 全部击杀 = 胜利。玩家死亡 = 失败。
     */
    fun doBattle(): Boolean {
        val s = state
        if (s.currentHp <= 0) { addLog("你已阵亡，等待恢复中..."); notifyUI(); return false }

        // 首次攻击：生成怪物波次
        if (!s.inBattle) {
            s.inBattle = true
            s.battleRound = 0
            s.currentMonsterTarget = 0
            // 如果已有怪物组(比如Boss重新挑战)则复用，否则生成新波次
            if (s.currentMonsters.isNotEmpty() && s.currentMonsters.any { it.hp > 0 }) {
                addLog("⚔️ 继续挑战! ${s.currentMonsters.size}个敌人")
            } else {
                s.currentMonsters.clear()
                s.currentMonsters.addAll(generateMonsterWave())
                val stageType = StageData.getStageType(s.currentStage)
                addLog("⚔️ 进入关卡${s.currentStage} (${stageType.name})! ${s.currentMonsters.size}个敌人")
                s.currentMonster = s.currentMonsters.firstOrNull()
            }
        }

        val attr = calcAttributes()
        s.battleRound++

        // ---- 魂核战斗临时状态初始化（每场战斗首次回合重置） ----
        if (s.battleRound == 1) {
            s.coreReviveCharges = state.equippedSoulCores.values
                .filterNotNull()
                .filter { it.passiveSkill.type == PassiveSkillType.REVIVE }
                .sumOf { it.effectiveValue }
        }

        // ---- 找到当前目标 ----
        while (s.currentMonsterTarget < s.currentMonsters.size && s.currentMonsters[s.currentMonsterTarget].hp <= 0) {
            s.currentMonsterTarget++
        }
        if (s.currentMonsterTarget >= s.currentMonsters.size) {
            return onBattleWin() // 所有怪物已死亡
        }

        val monster = s.currentMonsters[s.currentMonsterTarget]
        s.currentMonster = monster

        // ---- 降低冷却 ----
        for ((slot, cd) in s.activeSkillCooldowns.toMap()) {
            if (cd > 0) s.activeSkillCooldowns[slot] = cd - 1
        }
        // 虚空之核：额外冷却减少
        for (core in s.equippedSoulCores.values) {
            if (core != null && core.passiveSkill.type == PassiveSkillType.IMMUNE) {
                val reduceTurns = core.effectiveValue
                if (reduceTurns > 0) {
                    for ((slot, cd) in s.activeSkillCooldowns.toMap()) {
                        if (cd > 0) s.activeSkillCooldowns[slot] = (cd - reduceTurns).coerceAtLeast(0)
                    }
                }
            }
        }

        // ---- 魂力上限随等级 + 每回合自动回复 -速度降低 ----
        val soulMax = RealmData.battleSoulPowerMax(s.level)
        val soulRegen = (3 + s.level / 20).coerceAtLeast(1)
        s.battleSoulPower = (s.battleSoulPower + soulRegen).coerceAtMost(soulMax)

        var dmgToMonster = 0L
        var isCrit = false
        var skillUsed = false

        // ---- 优先使用魂环主动技能（含武魂本命魂技，排除手动关闭自动的）----
        val autoSkills = getAutoSelectableSkills()
        // 优先选魂环技能（排除武魂slot=-1），再考虑武魂
        val ringSkills = autoSkills.filter { it.first >= 0 }
        val ringSkill = if (ringSkills.isNotEmpty()) ringSkills.maxByOrNull { it.second.power }
                       else autoSkills.maxByOrNull { it.second.power }
        if (ringSkill != null) {
            dmgToMonster = applyActiveSkillDamage(ringSkill.first, ringSkill.second, attr, monster)
            isCrit = true
            skillUsed = true
        }

        // 普通攻击(无技能时) - 根据流派偏好选择物理/魔法
        if (!skillUsed) {
            val school = s.chosenSchool
            val prefPhysical = when (school) {
                SoulSchool.PHYSICAL -> Random.nextFloat() < 0.85f || attr.matk <= 0
                SoulSchool.MAGIC -> Random.nextFloat() < 0.15f && attr.matk > 0
                else -> Random.nextFloat() < 0.55f || attr.matk <= 0
            }
            val critRoll = Random.nextInt(100) < attr.critRate
            dmgToMonster = if (prefPhysical) calcPhysicalDmg(attr.atk, monster.pdef, critRoll, attr.critDmg)
                           else calcMagicDmg(attr.matk, monster.mdef, critRoll, attr.critDmg)
            isCrit = critRoll
        }

        monster.hp = (monster.hp - dmgToMonster).coerceAtLeast(0)
        val critStr = if (isCrit) " 💥暴击!" else ""
        val targetInfo = if (s.currentMonsters.size > 1) "[目标${s.currentMonsterTarget + 1}/${s.currentMonsters.size}] " else ""
        addLog("🗡️ 第${s.battleRound}回合${targetInfo}-${formatNum(dmgToMonster)}$critStr (敌HP:${formatNum(monster.hp)}/${formatNum(monster.maxHp)})")

        // 嗜血之核：攻击回复生命
        for (core in s.equippedSoulCores.values) {
            if (core != null && core.passiveSkill.type == PassiveSkillType.LIFESTEAL && dmgToMonster > 0) {
                val lifestealPct = core.effectiveValue
                val heal = (dmgToMonster * lifestealPct / 100).coerceAtLeast(1L)
                s.currentHp = (s.currentHp + heal).coerceAtMost(attr.maxHp)
                addLog("💚 嗜血 +${formatNum(heal)}HP")
            }
        }

        // 检查当前目标怪物是否死亡
        if (monster.hp <= 0) {
            addLog("💀 ${monster.name} 被击杀!")
            s.currentMonsterTarget++
            s.codexKills++

            // ---- 立即结算单怪收益 ----
            val isBossStage = StageData.getStageType(s.currentStage) == StageType.BOSS
            val dropMult = s.currentMonsters.maxOfOrNull { it.affixes.maxOfOrNull { af -> af.dropMult } ?: 1.0 } ?: 1.0
            val killGold = (monster.goldReward * dropMult).toLong()
            val killExp = (monster.expReward * dropMult).toLong()
            val killBc = BossShopData.bossCoinReward(s.currentMapId, monster.isBoss() || isBossStage)
            s.gold += killGold; s.totalGoldEarned += killGold
            s.soulPower += killExp
            s.bossCoin += killBc
            _tmpStageGold += killGold
            _tmpStageExp += killExp
            _tmpStageBossCoin += killBc
            val bcLog = if (killBc > 0) " +${killBc}Boss币" else ""
            addLog("💰 +${formatNum(killGold)}💰 +${formatNum(killExp)}经验${bcLog}")
            lastCombatRound = CombatRoundData(
                roundNum = s.battleRound,
                skillName = if (skillUsed) ringSkill?.second?.name else null,
                playerDmg = dmgToMonster,
                isCrit = isCrit,
                targetIndex = s.currentMonsterTarget,
                totalTargets = s.currentMonsters.size,
                monsterName = monster.name,
                monsterHpAfter = 0L,
                monsterMaxHp = monster.maxHp,
                monsterDied = true
            )

            // 检查是否全部击杀
            val allDead = s.currentMonsters.all { it.hp <= 0 }
            if (allDead) {
                return onBattleWin()
            }
            // 切换到下一目标
            val nextMonster = s.currentMonsters.getOrNull(s.currentMonsterTarget)
            if (nextMonster != null) {
                s.currentMonster = nextMonster
                addLog("🎯 切换目标: ${nextMonster.name} (HP:${formatNum(nextMonster.hp)}/${formatNum(nextMonster.maxHp)})")
            }
            notifyUI()
            return true
        }

        // ---- 怪物反击(当前目标) ----
        val monsterCrit = Random.nextInt(100) < monster.critRate
        val monsterUsePhysical = Random.nextFloat() < 0.5f
        var dmgToPlayer = if (monsterUsePhysical) calcPhysicalDmg(monster.atk, attr.pdef, monsterCrit, monster.critDmg)
                          else calcMagicDmg(monster.matk, attr.mdef, monsterCrit, monster.critDmg)

        // 疾风之核：闪避
        var dodged = false
        for (core in s.equippedSoulCores.values) {
            if (core != null && core.passiveSkill.type == PassiveSkillType.DODGE) {
                val dodgeChance = core.effectiveValue
                if (Random.nextInt(100) < dodgeChance) {
                    dodged = true
                    addLog("💨 疾风 闪避!")
                    break
                }
            }
        }

        if (!dodged) {
            // 魂骨减伤被动（格挡）
            for (bone in s.soulBones.values) {
                bone.passiveSkill?.let { ps ->
                    if (ps.type == PassiveSkillType.DMG_REDUCE) {
                        val valR = ps.getValue(bone.combinedTier)
                        if (ps.name == "格挡" && Random.nextInt(100) < valR) {
                            dmgToPlayer = (dmgToPlayer * 0.5).toLong()
                            addLog("🛡️ 格挡! 减伤50%")
                        }
                    }
                }
            }
            // 龙鳞之核：减伤
            for (core in s.equippedSoulCores.values) {
                if (core != null && core.passiveSkill.type == PassiveSkillType.DMG_REDUCE) {
                    val reducePct = core.effectiveValue
                    dmgToPlayer = (dmgToPlayer * (100 - reducePct) / 100).coerceAtLeast(1)
                }
            }

            s.currentHp = (s.currentHp - dmgToPlayer).coerceAtLeast(0)
            addLog("👾 ${monster.name}反击: -${formatNum(dmgToPlayer)} (HP:${formatNum(s.currentHp)}/${formatNum(attr.maxHp)})")

            // 荆棘之核：反伤
            for (core in s.equippedSoulCores.values) {
                if (core != null && core.passiveSkill.type == PassiveSkillType.THORNS && dmgToPlayer > 0) {
                    val thornsPct = core.effectiveValue
                    val thornsDmg = (dmgToPlayer * thornsPct / 100).coerceAtLeast(1L)
                    monster.hp = (monster.hp - thornsDmg).coerceAtLeast(0)
                    addLog("⚡ 荆棘反伤 ${formatNum(thornsDmg)}")
                }
            }
        } else {
            // 闪避成功，不受伤、无反伤
            addLog("👾 ${monster.name}反击: -0 (闪避)")
        }
        if (monster.affixes.contains(MonsterAffix.REGENERATING)) {
            monster.hp = minOf(monster.hp + (monster.maxHp / 20), monster.maxHp)
        }

        // 荆棘/反伤击杀：立即结算收益（避免丢失金币和经验）
        if (monster.hp <= 0) {
            addLog("💀 ${monster.name} 被反伤击杀!")
            s.currentMonsterTarget++
            s.codexKills++
            val isBossStage = StageData.getStageType(s.currentStage) == StageType.BOSS
            val dropMult = s.currentMonsters.maxOfOrNull { it.affixes.maxOfOrNull { af -> af.dropMult } ?: 1.0 } ?: 1.0
            val killGold = (monster.goldReward * dropMult).toLong()
            val killExp = (monster.expReward * dropMult).toLong()
            val killBc = BossShopData.bossCoinReward(s.currentMapId, monster.isBoss() || isBossStage)
            s.gold += killGold; s.totalGoldEarned += killGold
            s.soulPower += killExp; s.bossCoin += killBc
            _tmpStageGold += killGold; _tmpStageExp += killExp; _tmpStageBossCoin += killBc
            addLog("💰 +${formatNum(killGold)}💰 +${formatNum(killExp)}经验${if (killBc > 0) " +${killBc}Boss币" else ""}")
            val allDead = s.currentMonsters.all { it.hp <= 0 }
            if (allDead) {
                return onBattleWin()
            }
            notifyUI()
            return true
        }

        // 检查玩家是否死亡
        if (s.currentHp <= 0) {
            // 不朽之核：致命保命
            if (s.coreReviveCharges > 0) {
                s.coreReviveCharges--
                s.currentHp = 1
                addLog("💠 不朽之核 触发！保留1HP (剩余${s.coreReviveCharges}次)")
            } else {
                return onBattleLose()
            }
        }

        // 30回合上限
        if (s.battleRound >= 30) {
            addLog("⏰ 战斗超时，敌人逃跑了")
            s.inBattle = false
            s.currentMonsters.clear()
            s.currentMonsters.addAll(generateMonsterWave())
            s.currentMonster = s.currentMonsters.firstOrNull()
            notifyUI()
            return false
        }

        lastCombatRound = CombatRoundData(
            roundNum = s.battleRound,
            skillName = if (skillUsed) ringSkill?.second?.name else null,
            playerDmg = dmgToMonster,
            isCrit = isCrit,
            targetIndex = s.currentMonsterTarget,
            totalTargets = s.currentMonsters.size,
            monsterName = monster.name,
            monsterHpAfter = monster.hp,
            monsterMaxHp = monster.maxHp,
            monsterDied = false,
            playerDmgTaken = if (dodged) 0L else dmgToPlayer,
            isDodged = dodged
        )
        notifyUI()
        return true
    }

    private fun onBattleWin(): Boolean {
        val s = state
        val isBossStage = StageData.getStageType(s.currentStage) == StageType.BOSS

        s.inBattle = false
        s.totalBattleWins++

        // 战斗后魂力恢复（不再限制为100）
        val soulMax = RealmData.battleSoulPowerMax(s.level)
        s.battleSoulPower = (s.battleSoulPower + 5).coerceAtMost(soulMax)
        s.currentHp = (s.currentHp - (s.currentHp * 0.05).toLong()).coerceAtLeast(1)

        val stageName = when (StageData.getStageType(s.currentStage)) {
            StageType.NORMAL -> "普通关"
            StageType.ELITE -> "精英关"
            StageType.BOSS -> "Boss关"
        }
        addLog("🏆 ${stageName}${s.currentStage} 通关!")

        // ---- 正态分布掉落(受地图品质上限限制) ----
        val mapId = s.currentMapId.coerceIn(0, 7)
        val mean = DropDistribution.tierMean(mapId) + (if (isBossStage) DropDistribution.BOSS_MEAN_OFFSET else 0.0)
        val maxRingQ = DropDistribution.maxDropTier(mapId)
        val maxBoneQ = DropDistribution.maxBoneDropTier(mapId)

        // V5: 阶梯式掉落 — 合并层级越高概率越低, Boss/精英加成
        fun ringDropChance(tier: Int, boss: Boolean, elite: Boolean): Float {
            if (tier > maxRingQ) return 0f
            val base = when {
                tier >= 20 -> 0.005f; tier >= 15 -> 0.015f; tier >= 10 -> 0.04f
                tier >= 5 -> 0.10f; tier >= 1 -> 0.22f; else -> 0.45f
            }
            val bossMult = if (boss && tier >= 10) 2.0f else if (elite && tier <= 10) 1.5f else 1.0f
            val mapMult = 1.0f + mapId * 0.06f
            return (base * bossMult * mapMult).coerceAtMost(0.55f)
        }
        fun boneDropChance(tier: Int, boss: Boolean, elite: Boolean): Float {
            if (tier > maxBoneQ) return 0f
            val base = when {
                tier >= 20 -> 0.003f; tier >= 15 -> 0.010f; tier >= 10 -> 0.03f
                tier >= 5 -> 0.08f; tier >= 1 -> 0.15f; else -> 0.25f
            }
            val bossMult = if (boss && tier >= 10) 2.2f else if (elite && tier <= 10) 1.5f else 1.0f
            val mapMult = 1.0f + mapId * 0.05f
            return (base * bossMult * mapMult).coerceAtMost(0.40f)
        }

        // V5: 魂环掉落 — 双维度年份+品质
        val isEliteStage = StageData.getStageType(s.currentStage) == StageType.ELITE
        val ringTier = DropDistribution.rollTier(mean, DropDistribution.TIER_STD_DEV, maxRingQ)
        if (Random.nextFloat() < ringDropChance(ringTier, isBossStage, isEliteStage)) {
            val (yearOrd, qualOrd) = SoulRingGenerator.splitTier(ringTier)
            val affixes = SoulRingGenerator.generateRandomAffixes(ringTier)
            val skill = ActiveSkillPool.randomSkill(ringTier)
            val pct = SoulRingSystem.randomPercentage()
            val dropped = DroppedRing(yearOrd, qualOrd, pct, affixes, skill)
            val isFiltered = yearOrd in s.ringFilterYears || qualOrd in s.ringFilterQualities
            _tmpRingDropName = RingQuality.fullDisplayName(ringTier) + "[" + skill.name + "]"
            if (s.backpackIsFull || isFiltered) {
                val price = calcRingSellPrice(ringTier)
                s.gold += price; s.totalGoldEarned += price
                val reason = if (s.backpackIsFull) "包满" else "🎯已过滤"
                addLog("💍 掉落: ${RingQuality.fullDisplayName(ringTier)}[${skill.name}] → ${reason}自动售出 +${formatNum(price)}💰")
            } else {
                s.backpackRings.add(dropped)
                addLog("💍 掉落: ${RingQuality.fullDisplayName(ringTier)}[${skill.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")
                if (RingYear.qualityOf(ringTier) >= 3) pushNotifyEvent(NotifyType.HIGH_DROP, "💍 高品质魂环", "掉落: ${RingQuality.fullDisplayName(ringTier)}[${skill?.name ?: "无技能"}]")
                if (s.autoSellRings && RingYear.qualityOf(ringTier) < s.autoSellRingThreshold) applyAutoSellRings()
            }
        }

        // V5: 魂骨掉落 — 双维度年份+品质
        val boneTier = DropDistribution.rollTier(mean - 2.0, DropDistribution.TIER_STD_DEV, maxBoneQ)
        if (Random.nextFloat() < boneDropChance(boneTier, isBossStage, isEliteStage)) {
            val (boneYearOrd, boneRarityOrd) = SoulRingGenerator.splitTier(boneTier)
            val boneType = Random.nextInt(BoneType.entries.size)
            val affixes = SoulBoneGenerator.generateRandomAffix(boneTier)
            val passive = PassiveSkillPool.randomPassive(boneType, boneTier)
            val dropped = DroppedBone(boneType, boneYearOrd, boneRarityOrd, affixes, passive)
            val isFiltered = boneYearOrd in s.boneFilterYears || boneRarityOrd in s.boneFilterRarities
            _tmpBoneDropName = BoneRarity.fullDisplayName(boneTier) + "(" + BoneType.entries[boneType].displayName + ")[" + passive.name + "]"
            if (s.backpackIsFull || isFiltered) {
                val price = calcBoneSellPrice(boneTier)
                s.gold += price; s.totalGoldEarned += price
                val reason = if (s.backpackIsFull) "包满" else "🎯已过滤"
                addLog("🦴 掉落: ${BoneRarity.fullDisplayName(boneTier)}(${BoneType.entries[boneType].displayName})[${passive.name}] → ${reason}自动售出 +${formatNum(price)}💰")
            } else {
                s.backpackBones.add(dropped)
                addLog("🦴 掉落: ${BoneRarity.fullDisplayName(boneTier)}(${BoneType.entries[boneType].displayName})[${passive.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")
                if (BoneYear.qualityOf(boneTier) >= 3) pushNotifyEvent(NotifyType.HIGH_DROP, "🦴 高品质魂骨", "掉落: ${BoneRarity.fullDisplayName(boneTier)}(${BoneType.entries[boneType].displayName})[${passive.name}]")
                if (s.autoSellBones && BoneYear.qualityOf(boneTier) < s.autoSellBoneThreshold) applyAutoSellBones()
            }
        }

        // ---- 关卡推进(完胜自动跳转) ----
        s.currentStage++
        if (s.currentStage > StageData.STAGES_PER_MAP) {
            s.currentStage = 1
            if (s.autoAdvanceMap) {
                val nextMapId = s.currentMapId + 1
                if (nextMapId <= MapData.getMaxMapId()) {
                    val nextMap = MapData.getMap(nextMapId)
                    if (nextMap != null && s.level >= nextMap.unlockLevel) {
                        val cost = nextMap.unlockCost.toLong()
                        if (cost > 0 && s.gold < cost) {
                            addLog("💸 金魂币不足，无法自动进入${nextMap.name}（需要 ${formatNum(cost)}💰）")
                        } else {
                            if (cost > 0) { s.gold -= cost }
                            s.currentMapId = nextMapId
                            addLog("🗺️ 进入新地图: ${nextMap.name}，回到第1关")
                        }
                    } else {
                        addLog("🔁 已通关关底，回到第1关（下一地图需Lv.${nextMap?.unlockLevel ?: "?"}解锁）")
                    }
                } else {
                    addLog("🏁 已通关所有地图！回到第1关继续修炼")
                }
            } else {
                addLog("🔁 已通关关底，回到第1关")
            }
        }

        // 刷新下一波怪物
        s.currentMonsters.clear()
        s.currentMonsters.addAll(generateMonsterWave())
        s.currentMonster = s.currentMonsters.firstOrNull()
        s.currentMonsterTarget = 0

        lastBattleDrops = BattleDropGroup(
            gold = _tmpStageGold,
            exp = _tmpStageExp,
            bossCoin = _tmpStageBossCoin,
            stageType = stageName,
            stageNum = s.currentStage,
            ringDrop = _tmpRingDropName,
            boneDrop = _tmpBoneDropName
        )
        _tmpRingDropName = null
        _tmpBoneDropName = null
        _tmpStageGold = 0L
        _tmpStageExp = 0L
        _tmpStageBossCoin = 0L

        checkAchievements()
        if (state.tutorialStep == 3) advanceTutorial()
        notifyUI()
        return true
    }

    private fun onBattleLose(): Boolean {
        val s = state
        val curMonster = s.currentMonster
        val isBoss = curMonster?.isBoss() ?: false
        s.inBattle = false; s.totalBattleLosses++
        val oldStage = s.currentStage
        s.currentStage = 1
        s.currentHp = 1L // 死亡不直接回满，靠被动恢复20%/秒
        addLog("💀 被击败！退回第1关（HP恢复中...）")
        if (oldStage > 1) addLog("📍 从第${oldStage}关退回到第1关")
        // 刷新第1关怪物波次
        s.currentMonsters.clear()
        s.currentMonsters.addAll(generateMonsterWave())
        s.currentMonster = s.currentMonsters.firstOrNull()
        s.currentMonsterTarget = 0
        notifyUI()
        return false
    }

    // ======== 全量即时战斗(用于杀戮之都等) ========

    /** V2: 全量即时战斗（杀戮之都等）—— 使用统一技能系统 */
    private fun resolveBattle(monster: Monster): BattleResult {
        val attr = calcAttributes()
        var playerHp = state.currentHp; var monsterHp = monster.hp
        var totalPlayerDmg = 0L; var totalMonsterDmg = 0L
        var isCritKill = false; var skillNameUsed: String? = null
        val maxRounds = 50; var round = 0
        val monsterHasRegen = monster.affixes.contains(MonsterAffix.REGENERATING)

        // 魂核战斗临时状态初始化（每场战斗首次重置）
        state.coreReviveCharges = state.equippedSoulCores.values
            .filterNotNull()
            .filter { it.passiveSkill.type == PassiveSkillType.REVIVE }
            .sumOf { it.effectiveValue }

        // 本地冷却+魂力追踪（不污染全局战斗状态）
        val localCooldowns = state.activeSkillCooldowns.toMutableMap()
        var localSoulPower = state.battleSoulPower
        val soulMax = RealmData.battleSoulPowerMax(state.level)
        val soulRegen = (3 + state.level / 20).coerceAtLeast(1)

        /** 对怪物造成技能伤害（本地计算，不修改全局状态） */
        fun applySkillDmg(skill: ActiveSkill): Long {
            val magical = isMagicalSkill(skill)
            val baseAtk = if (magical) attr.matk else attr.atk
            val baseDef = if (magical) monster.mdef else monster.pdef
            val critRoll = Random.nextInt(100) < attr.critRate
            val useDef = when (skill.name) {
                "破甲击", "崩山击", "神魔破", "混沌之击" -> (baseDef * 0.5).toInt()
                else -> baseDef
            }
            val defFactor = (1.0 - useDef.toDouble() / (useDef + 200)).coerceIn(0.1, 1.0)

            var totalDmg = 0L
            when (skill.type) {
                ActiveSkillType.SINGLE_STRIKE -> {
                    var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) * defFactor
                    if (critRoll) dmg *= attr.critDmg / 100.0
                    totalDmg = dmg.toLong().coerceAtLeast(1)
                }
                ActiveSkillType.MULTI_HIT -> {
                    val hits = when {
                        skill.name.contains("二连") -> 2; skill.name.contains("三连") || skill.name == "幽冥突刺" || skill.name == "虎爪裂空" -> 3
                        skill.name == "乱披风" || skill.name.contains("百爪") || skill.name == "风暴连斩" -> 4
                        skill.name == "凤凰流星" -> 5; skill.name == "千手连击" -> 6
                        skill.name == "无限连击" -> 4; else -> 2
                    }
                    for (i in 0 until hits) {
                        val perCrit = Random.nextInt(100) < attr.critRate
                        var perDmg = (baseAtk * skill.power / 100).coerceAtLeast(1) * defFactor
                        if (perCrit) perDmg *= attr.critDmg / 100.0
                        totalDmg += perDmg.toLong().coerceAtLeast(1)
                    }
                }
                ActiveSkillType.HEAL -> {
                    val heal = (attr.maxHp * skill.power / 100).coerceAtLeast(1L)
                    playerHp = (playerHp + heal).coerceAtMost(attr.maxHp)
                    return 0
                }
                ActiveSkillType.LIFE_STEAL -> {
                    var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) * defFactor
                    if (critRoll) dmg *= attr.critDmg / 100.0
                    totalDmg = dmg.toLong().coerceAtLeast(1)
                    val healPct = when {
                        skill.name.contains("生命汲取") -> 20; skill.name.contains("噬血") || skill.name == "血祭" -> 40
                        skill.name.contains("血气唤醒") || skill.name.contains("暗影噬魂") -> 50
                        skill.name.contains("血月") -> 60; skill.name.contains("嗜血神术") -> 100
                        skill.name.contains("吞噬") -> 30; else -> 25
                    }
                    playerHp = (playerHp + totalDmg * healPct / 100).coerceAtMost(attr.maxHp)
                }
                ActiveSkillType.FURY, ActiveSkillType.AMPLIFY -> {
                    var dmg = (baseAtk * (100 + skill.power) / 100).coerceAtLeast(1) * defFactor
                    if (critRoll) dmg *= attr.critDmg / 100.0
                    totalDmg = dmg.toLong().coerceAtLeast(1)
                }
                ActiveSkillType.SHIELD -> {
                    val shield = (attr.maxHp * skill.power / 100).coerceAtLeast(1L)
                    playerHp = (playerHp + shield).coerceAtMost(attr.maxHp)
                    return 0
                }
                ActiveSkillType.EXECUTE -> {
                    val hpPct = monsterHp.toDouble() / monster.maxHp
                    val execMult = if (hpPct < 0.3) 3.0 else 1.5
                    var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) * defFactor * execMult
                    if (critRoll) dmg *= attr.critDmg / 100.0
                    totalDmg = dmg.toLong().coerceAtLeast(1)
                }
                ActiveSkillType.SOUL_DRAIN -> {
                    var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) * defFactor
                    if (critRoll) dmg *= attr.critDmg / 100.0
                    totalDmg = dmg.toLong().coerceAtLeast(1)
                    localSoulPower += 10 + skill.tier * 3
                }
                else -> {
                    var dmg = (baseAtk * skill.power / 100).coerceAtLeast(1) * defFactor
                    if (critRoll) dmg *= attr.critDmg / 100.0
                    totalDmg = dmg.toLong().coerceAtLeast(1)
                }
            }
            return totalDmg
        }

        while (round < maxRounds) {
            round++
            // 降低冷却
            for ((slot, cd) in localCooldowns.toMap()) {
                if (cd > 0) localCooldowns[slot] = cd - 1
            }
            // 虚空之核：额外冷却减少
            for (core in state.equippedSoulCores.values) {
                if (core != null && core.passiveSkill.type == PassiveSkillType.IMMUNE) {
                    val reduceTurns = core.effectiveValue
                    if (reduceTurns > 0) {
                        for ((slot, cd) in localCooldowns.toMap()) {
                            if (cd > 0) localCooldowns[slot] = (cd - reduceTurns).coerceAtLeast(0)
                        }
                    }
                }
            }
            // 魂力回复
            localSoulPower = (localSoulPower + soulRegen).coerceAtMost(soulMax)

            // 收集可用技能（魂环 + 武魂）
            val availableSkills = mutableListOf<Pair<Int, ActiveSkill>>()
            for ((slotIdx, ring) in state.soulRings) {
                val sk = ring.skill ?: continue
                val cd = localCooldowns[slotIdx] ?: 0
                if (cd <= 0 && localSoulPower >= sk.soulCost * soulCostMult(sk)) {
                    availableSkills.add(slotIdx to sk)
                }
            }
            if (state.martialSoul != null) {
                val wuhunCd = localCooldowns[WUHUN_SKILL_SLOT] ?: 0
                if (wuhunCd <= 0) {
                    val ws = wuhunToActiveSkill(state.martialSoul!!)
                    if (ws != null) availableSkills.add(WUHUN_SKILL_SLOT to ws)
                }
            }

            // 优先魂环技能，其次武魂
            val bestRing = availableSkills.filter { it.first >= 0 }.maxByOrNull { it.second.power }
            val bestSkill = bestRing ?: availableSkills.maxByOrNull { it.second.power }

            var skillUsed = false
            var roundDmg = 0L
            if (bestSkill != null) {
                val (slotIdx, sk) = bestSkill
                localSoulPower = (localSoulPower - sk.soulCost * soulCostMult(sk)).coerceAtLeast(0)
                localCooldowns[slotIdx] = sk.cooldown
                skillNameUsed = sk.name
                val dmg = applySkillDmg(sk)
                monsterHp -= dmg; totalPlayerDmg += dmg
                roundDmg = dmg
                skillUsed = true
            }

            // 普通攻击 - 根据流派偏好选择物理/魔法
            if (!skillUsed) {
                val critRoll = Random.nextInt(100) < attr.critRate
                val prefPhysical = when (state.chosenSchool) {
                    SoulSchool.PHYSICAL -> Random.nextFloat() < 0.85f || attr.matk <= 0
                    SoulSchool.MAGIC -> Random.nextFloat() < 0.15f && attr.matk > 0
                    else -> Random.nextFloat() < 0.55f || attr.matk <= 0
                }
                val normalDmg = if (prefPhysical) calcPhysicalDmg(attr.atk, monster.pdef, critRoll, attr.critDmg)
                                else calcMagicDmg(attr.matk, monster.mdef, critRoll, attr.critDmg)
                val monsterHpBefore = monsterHp
                monsterHp -= normalDmg; totalPlayerDmg += normalDmg
                roundDmg = normalDmg
                if (critRoll && normalDmg >= monsterHpBefore) isCritKill = true
            }

            // 嗜血之核：攻击回复生命
            if (roundDmg > 0) {
                for (core in state.equippedSoulCores.values) {
                    if (core != null && core.passiveSkill.type == PassiveSkillType.LIFESTEAL) {
                        val lifestealPct = core.effectiveValue
                        val heal = (roundDmg * lifestealPct / 100).coerceAtLeast(1L)
                        playerHp = (playerHp + heal).coerceAtMost(attr.maxHp)
                    }
                }
            }

            if (monsterHp <= 0) {
                state.currentHp = playerHp
                return BattleResult(won = true, rounds = round, monsterName = monster.name,
                    monsterAffixes = monster.affixes, expGained = monster.expReward,
                    goldGained = monster.goldReward, playerDmgDealt = totalPlayerDmg,
                    monsterDmgDealt = totalMonsterDmg, isCritKill = isCritKill,
                    dropItem = if (monster.canDropEquip) "材料" else null, skillUsed = skillNameUsed)
            }

            // 怪物反击
            val monsterCrit = Random.nextInt(100) < monster.critRate
            val monsterUsePhysical = Random.nextFloat() < 0.5f
            val rawDmg = if (monsterUsePhysical) calcPhysicalDmg(monster.atk, attr.pdef, monsterCrit, monster.critDmg)
                         else calcMagicDmg(monster.matk, attr.mdef, monsterCrit, monster.critDmg)
            var dmgToPlayer = rawDmg
            if (state.getTalentLevel(TalentBranch.DIVINE) >= 3) {
                dmgToPlayer = (rawDmg * 0.95).toLong().coerceAtLeast(1)
            }

            // 疾风之核：闪避
            var dodged = false
            for (core in state.equippedSoulCores.values) {
                if (core != null && core.passiveSkill.type == PassiveSkillType.DODGE) {
                    val dodgeChance = core.effectiveValue
                    if (Random.nextInt(100) < dodgeChance) {
                        dodged = true
                        break
                    }
                }
            }

            if (!dodged) {
                // 龙鳞之核：减伤
                for (core in state.equippedSoulCores.values) {
                    if (core != null && core.passiveSkill.type == PassiveSkillType.DMG_REDUCE) {
                        val reducePct = core.effectiveValue
                        dmgToPlayer = (dmgToPlayer * (100 - reducePct) / 100).coerceAtLeast(1)
                    }
                }

                playerHp -= dmgToPlayer; totalMonsterDmg += dmgToPlayer

                // 荆棘之核：反伤
                for (core in state.equippedSoulCores.values) {
                    if (core != null && core.passiveSkill.type == PassiveSkillType.THORNS && dmgToPlayer > 0) {
                        val thornsPct = core.effectiveValue
                        val thornsDmg = (dmgToPlayer * thornsPct / 100).coerceAtLeast(1L)
                        monsterHp -= thornsDmg
                    }
                }
            }

            if (monsterHasRegen) monsterHp = min(monsterHp + (monster.maxHp * 0.05).toLong(), monster.maxHp)

            if (playerHp <= 0) {
                // 不朽之核：致命保命
                if (state.coreReviveCharges > 0) {
                    state.coreReviveCharges--
                    playerHp = 1
                } else {
                    state.currentHp = playerHp
                    return BattleResult(won = false, rounds = round, monsterName = monster.name,
                        monsterAffixes = monster.affixes, expGained = 0, goldGained = 0,
                        playerDmgDealt = totalPlayerDmg, monsterDmgDealt = totalMonsterDmg,
                        isCritKill = false, skillUsed = skillNameUsed)
                }
            }
        }

        state.currentHp = playerHp
        return BattleResult(won = false, rounds = maxRounds, monsterName = monster.name,
            monsterAffixes = monster.affixes, expGained = 0, goldGained = 0,
            playerDmgDealt = totalPlayerDmg, monsterDmgDealt = totalMonsterDmg,
            isCritKill = false, skillUsed = skillNameUsed)
    }

    // ======== 地图(关卡制) ========

    fun doChangeMap(mapId: Int): Boolean {
        val map = MapData.getMap(mapId) ?: return false
        if (state.level < map.unlockLevel) { addLog("需要 ${map.unlockLevel} 级才能进入${map.name}"); notifyUI(); return false }
        if (state.currentMapId == mapId) { addLog("已在${map.name}"); notifyUI(); return false }
        val cost = map.unlockCost.toLong()
        if (cost > 0 && state.gold < cost) {
            addLog("金魂币不足，进入${map.name}需要 ${formatNum(cost)}💰"); notifyUI(); return false
        }
        if (cost > 0) {
            state.gold -= cost
            addLog("💰 花费 ${formatNum(cost)} 金魂币进入${map.name}")
        }
        state.currentMapId = mapId; state.currentStage = 1
        state.inBattle = false
        state.currentMonsters.clear()
        state.currentMonsters.addAll(generateMonsterWave())
        state.currentMonster = state.currentMonsters.firstOrNull()
        state.currentMonsterTarget = 0
        state.currentHp = calcMaxHp()
        addLog("🗺️ 进入 ${map.name}")
        if (state.tutorialStep == 5) advanceTutorial()
        notifyUI()
        return true
    }

    /** 重置当前地图回到第1关，满血 */
    fun doResetMap(): Boolean {
        val map = MapData.getMap(state.currentMapId) ?: return false
        if (state.currentStage <= 1 && !state.inBattle) {
            addLog("已在${map.name}第1关"); notifyUI(); return false
        }
        state.currentStage = 1
        state.inBattle = false
        state.autoBattle = false
        state.currentMonsters.clear()
        state.currentMonsters.addAll(generateMonsterWave())
        state.currentMonster = state.currentMonsters.firstOrNull()
        state.currentMonsterTarget = 0
        state.currentHp = calcMaxHp()
        addLog("🔄 重置${map.name}，回到第1关（HP已回满）"); notifyUI()
        return true
    }

    fun isMapUnlocked(mapId: Int): Boolean {
        val map = MapData.getMap(mapId) ?: return false
        return state.level >= map.unlockLevel
    }

    // ======== 修炼 ========

    fun doCultivate(): Long {
        if (state.inBattle) { addLog("战斗中无法修炼！"); notifyUI(); return 0 }
        val now = System.currentTimeMillis()
        val cooldownMs = 5000L  // 5秒冷却
        val elapsed = now - lastCultivateTime
        if (elapsed < cooldownMs) {
            val remainSec = ((cooldownMs - elapsed) / 1000).toInt() + 1
            addLog("修炼冷却中... ${remainSec}秒后可修炼"); notifyUI(); return 0
        }
        lastCultivateTime = now
        val gain = (state.level * 28L * prestigeMultiplier()).toLong()
        if (state.isLevelCapped) {
            state.excessSoulPower += gain
            addLog("修炼成功 +${formatNum(gain)} 魂力（卡级·存入超额储备 ${formatNum(state.excessSoulPower)}）")
        } else {
            state.soulPower += gain
            addLog("修炼成功 +${formatNum(gain)} 魂力")
        }
        if (state.tutorialStep == 2) advanceTutorial()
        notifyUI()
        return gain
    }

    fun doBreakthrough(): Boolean {
        if (state.isLevelCapped) {
            addLog("⛔ 卡级修炼中，无法突破！请先解除卡级再突破")
            notifyUI(); return false
        }
        // 魂环卡级：即将解锁新槽位时，必须先填满当前槽位
        val currentSlots = RealmData.maxSoulRings(state.level)
        val nextSlots = RealmData.maxSoulRings(state.level + 1)
        if (nextSlots > currentSlots && state.soulRings.size < currentSlots) {
            addLog("⛔ 境界提升将解锁第${nextSlots}魂环槽位，需要先吸收第${currentSlots}魂环！")
            addLog("💡 前往【装备】页吸收魂环后再突破")
            notifyUI(); return false
        }
        val cost = RealmData.breakthroughCost(state.level)
        if (state.soulPower < cost) { addLog("魂力不足，需要 ${formatNum(cost)}"); notifyUI(); return false }
        // 卡级储备突破加成：超额魂力转化为属性
        val excess = state.excessSoulPower
        if (excess > 0) {
            val statGain = (excess / 2000).toInt().coerceIn(1, 10)
            for (key in listOf("hp", "atk", "matk", "pdef", "mdef")) {
                state.capStatLevels[key] = state.capStatLevels.getOrDefault(key, 0) + statGain
            }
            val goldRefund = (excess % 2000) * 10L
            state.gold += goldRefund; state.totalGoldEarned += goldRefund
            addLog("🌟 卡级积蓄爆发！全属性凝练 +${statGain}级，回收${formatNum(goldRefund)}💰")
            state.excessSoulPower = 0
        }
        state.soulPower -= cost; state.level++
        state.currentHp = calcMaxHp()
        addLog("突破成功！${RealmData.name(state.level)} (${state.level}级)")
        if (state.level % 10 == 0) {
            addLog("🌟 境界跨越！全属性大幅提升！")
        }
        val newFeatures = checkForNewFeatures()
        if (newFeatures.isNotEmpty()) {
            pendingFeatureUnlocks.addAll(newFeatures)
            val names = newFeatures.joinToString("、") { "${it.icon} ${it.displayName}" }
            pushNotifyEvent(NotifyType.FEATURE_UNLOCK, "🎉 新功能已解锁！", names)
        }
        checkAchievements(); notifyUI()
        return true
    }

    // ======== 卡级修炼 ========

    /** 当前等级是否为10的倍数(可卡级门槛) */
    fun isAtCapLevel(): Boolean = state.level % 10 == 0

    /** 切换卡级模式 */
    fun toggleLevelCap(): Boolean {
        val s = state
        if (s.isLevelCapped) {
            s.isLevelCapped = false
            addLog("🔓 已解除卡级修炼，可以正常突破（超额储备 ${formatNum(s.excessSoulPower)} 魂力将在突破时转化为奖励）")
            notifyUI(); return true
        }
        if (!isAtCapLevel()) {
            addLog("⛔ 仅在境界门槛（Lv.10/20/30/40/50...）可开启卡级修炼")
            notifyUI(); return false
        }
        s.isLevelCapped = true
        addLog("🔒 卡级修炼开启！修炼魂力将存入超额储备，突破暂时锁定")
        addLog("   💡 消耗超额魂力可凝练属性或兑换金币，积累越多突破奖励越大")
        notifyUI(); return true
    }

    /** 凝练体魄：消耗超额魂力提升指定属性等级 */
    fun consumeExcessForStat(statKey: String): Boolean {
        val s = state
        if (!s.isLevelCapped) { addLog("⛔ 未开启卡级模式"); notifyUI(); return false }
        if (s.excessSoulPower < 100) {
            addLog("超额魂力不足，需要至少100超额魂力"); notifyUI(); return false
        }
        val curLv = s.capStatLevels.getOrDefault(statKey, 0)
        val cost = (100L + curLv * 80L).coerceAtMost(50000L)
        if (s.excessSoulPower < cost) {
            addLog("超额魂力不足，${statDisplayName(statKey)}凝练 Lv.${curLv + 1} 需要 ${formatNum(cost)}")
            notifyUI(); return false
        }
        s.excessSoulPower -= cost
        s.capStatLevels[statKey] = curLv + 1
        val bonus = when (statKey) {
            "hp" -> "HP+50"; "atk" -> "攻击+10"; "matk" -> "魔攻+8"
            "pdef" -> "物防+5"; "mdef" -> "魔防+5"; else -> ""
        }
        addLog("💪 体魄凝练·${statDisplayName(statKey)} Lv.${curLv + 1}！(${bonus}) 消耗${formatNum(cost)}超额魂力")
        notifyUI(); return true
    }

    /** 魂力转化：消耗超额魂力兑换金币 */
    fun consumeExcessForGold(): Boolean {
        val s = state
        if (!s.isLevelCapped) { addLog("⛔ 未开启卡级模式"); notifyUI(); return false }
        if (s.excessSoulPower < 100) {
            addLog("超额魂力不足，至少需要100"); notifyUI(); return false
        }
        val consume = minOf(s.excessSoulPower / 10 * 10, 5000L).coerceAtLeast(100)
        val goldGain = consume * 10L
        s.excessSoulPower -= consume
        s.gold += goldGain; s.totalGoldEarned += goldGain
        addLog("💰 魂力转化：消耗 ${formatNum(consume)} 超额魂力 → +${formatNum(goldGain)}💰")
        notifyUI(); return true
    }

    private fun statDisplayName(key: String): String = when (key) {
        "hp" -> "生命"; "atk" -> "攻击"; "matk" -> "魔攻"; "pdef" -> "物防"; "mdef" -> "魔防"; else -> key
    }

    /** 修炼每tick(5秒)获得的魂力基数（用于界面显示公式） */
    fun cultivatePerTick(): Long = (state.level * 28L * prestigeMultiplier()).toLong()

    /** 魂力注入：将修炼魂力(soulPower)直接转入超额储备(最多转入amount) */
    fun transferSoulToExcess(amount: Long): Boolean {
        val s = state
        if (!s.isLevelCapped) { addLog("⛔ 未开启卡级模式"); notifyUI(); return false }
        val actual = minOf(amount, s.soulPower)
        if (actual <= 0) { addLog("修炼魂力不足"); notifyUI(); return false }
        s.soulPower -= actual
        s.excessSoulPower += actual
        addLog("📥 注入 ${formatNum(actual)} 魂力 → 超额储备 (总${formatNum(s.excessSoulPower)})")
        notifyUI(); return true
    }

    /** 计算指定属性最多可批量凝练的次数 */
    fun maxBatchForStat(statKey: String): Int {
        var count = 0
        var remaining = state.excessSoulPower
        var curLv = state.capStatLevels.getOrDefault(statKey, 0)
        while (remaining >= 100) {
            val cost = (100L + curLv * 80L).coerceAtMost(50000L)
            if (remaining < cost) break
            remaining -= cost
            curLv++
            count++
            if (count >= 999) break // 防止死循环
        }
        return count
    }

    /** 批量凝练体魄 */
    fun batchConsumeExcessForStat(statKey: String, times: Int): Int {
        val s = state
        if (!s.isLevelCapped) { addLog("⛔ 未开启卡级模式"); return 0 }
        var successCount = 0
        var totalCost = 0L
        for (i in 0 until times) {
            val curLv = s.capStatLevels.getOrDefault(statKey, 0)
            val cost = (100L + curLv * 80L).coerceAtMost(50000L)
            if (s.excessSoulPower < cost) break
            s.excessSoulPower -= cost
            totalCost += cost
            s.capStatLevels[statKey] = curLv + 1
            successCount++
        }
        if (successCount > 0) {
            val statName = statDisplayName(statKey)
            val bonus = when (statKey) {
                "hp" -> "HP+${successCount * 50}"; "atk" -> "攻击+${successCount * 10}"
                "matk" -> "魔攻+${successCount * 8}"; "pdef" -> "物防+${successCount * 5}"
                "mdef" -> "魔防+${successCount * 4}"; else -> ""
            }
            addLog("💪 体魄凝练·${statName} ×${successCount}！消耗${formatNum(totalCost)}超额魂力 (${bonus})")
        } else {
            addLog("超额魂力不足，无法凝练")
        }
        notifyUI(); return successCount
    }

    /** 批量金币转化：消耗x次(最多5000每轮) */
    fun batchConsumeExcessForGold(times: Int): Int {
        var totalConsumed = 0L
        var totalGold = 0L
        var count = 0
        for (i in 0 until times) {
            val s = state
            if (!s.isLevelCapped || s.excessSoulPower < 100) break
            val consume = minOf(s.excessSoulPower / 10 * 10, 5000L).coerceAtLeast(100)
            val goldGain = consume * 10L
            s.excessSoulPower -= consume
            s.gold += goldGain; s.totalGoldEarned += goldGain
            totalConsumed += consume
            totalGold += goldGain
            count++
        }
        if (count > 0) {
            addLog("💰 魂力转化 ×${count}：消耗 ${formatNum(totalConsumed)} 超额魂力 → +${formatNum(totalGold)}💰")
        } else {
            addLog("超额魂力不足，至少需要100")
        }
        notifyUI(); return count
    }

    // ======== 武魂流派系统（改革后） ========

    /** 开局选择流派 - 替代旧的随机觉醒 */
    /** 检查特殊流派是否已解锁 */
    fun isSchoolUnlocked(school: SoulSchool): Boolean = when (school) {
        SoulSchool.BALANCED, SoulSchool.PHYSICAL, SoulSchool.MAGIC -> true
        SoulSchool.SUPPORT -> state.level >= 50 && state.prestigeCount >= 1
        SoulSchool.CONTROL -> state.level >= 70 && state.prestigeCount >= 2
        SoulSchool.ASSASSIN -> state.level >= 90 && state.prestigeCount >= 3
    }

    /** 获取流派解锁条件描述 */
    fun schoolUnlockRequirement(school: SoulSchool): String = when (school) {
        SoulSchool.SUPPORT -> "Lv.50 + 转生≥1"
        SoulSchool.CONTROL -> "Lv.70 + 转生≥2"
        SoulSchool.ASSASSIN -> "Lv.90 + 转生≥3"
        else -> "开局可选"
    }

    fun doChooseSchool(school: SoulSchool): Boolean {
        if (!isSchoolUnlocked(school)) {
            addLog("🔒 ${school.displayName}未解锁：${schoolUnlockRequirement(school)}")
            notifyUI()
            return false
        }
        if (state.chosenSchool != null) {
            addLog("流派已选择：${state.chosenSchool!!.displayName}，转生后可重新选择")
            notifyUI()
            return false
        }
        state.chosenSchool = school
        // 从流派池中选择初始武魂
        val soul = MartialSoulPool.randomAwakenForSchool(school, state.prestigeCount)
        state.martialSoul = soul
        state.activeSkillCooldowns[WUHUN_SKILL_SLOT] = 0
        addLog("${school.icon} 选择【${school.displayName}】！初始武魂觉醒：${soul.rarity.displayName}·${soul.name}")
        addLog("📖 ${soul.description}")
        addLog("⚡ 本命魂技：【${soul.skill.name}】${soul.skill.description}")
        if (state.tutorialStep == 1) advanceTutorial()
        notifyUI()
        return true
    }

    /** 武魂觉醒（改革后：在流派池内随机觉醒） */
    fun doAwaken(): MartialSoul? {
        val school = state.chosenSchool
        if (school == null) {
            addLog("⚠️ 请先选择流派！")
            notifyUI()
            return null
        }
        if (state.martialSoul != null) {
            val oldName = state.martialSoul!!.name
            state.martialSoul = null  // 先清除，让随机觉醒重新选择
            val soul = MartialSoulPool.randomAwakenForSchool(school, state.prestigeCount)
            state.martialSoul = soul
            state.activeSkillCooldowns[WUHUN_SKILL_SLOT] = 0
            addLog("🔄 武魂重醒：${oldName} → ${soul.rarity.displayName}·${soul.name}")
            addLog("📖 ${soul.description}")
            addLog("⚡ 本命魂技：【${soul.skill.name}】${soul.skill.description}")
            checkAchievements()
            notifyUI()
            return soul
        } else {
            val soul = MartialSoulPool.randomAwakenForSchool(school, state.prestigeCount)
            state.martialSoul = soul
            state.activeSkillCooldowns[WUHUN_SKILL_SLOT] = 0
            addLog("✨ 武魂觉醒：${soul.rarity.displayName}·${soul.name}")
            addLog("📖 ${soul.description}")
            addLog("⚡ 本命魂技：【${soul.skill.name}】${soul.skill.description}")
            checkAchievements()
            notifyUI()
            return soul
        }
    }

    /** 转生时可重新选择流派 */
    fun doReselectSchool(school: SoulSchool): Boolean {
        if (!isSchoolUnlocked(school)) {
            addLog("🔒 ${school.displayName}未解锁：${schoolUnlockRequirement(school)}")
            notifyUI()
            return false
        }
        state.chosenSchool = school
        val soul = MartialSoulPool.randomAwakenForSchool(school, state.prestigeCount)
        state.martialSoul = soul
        state.activeSkillCooldowns[WUHUN_SKILL_SLOT] = 0
        addLog("${school.icon} 转生选择【${school.displayName}】！武魂：${soul.rarity.displayName}·${soul.name}")
        notifyUI()
        return true
    }

    // ======== 魂环 ========

    fun doAbsorbSoulRing(combinedTier: Int): Boolean {
        val maxRings = RealmData.maxSoulRings(state.level)
        if (state.soulRings.size >= maxRings) { addLog("当前境界最多拥有 $maxRings 个魂环"); notifyUI(); return false }
        if (combinedTier !in 0..24) { addLog("无效的魂环品质"); notifyUI(); return false }
        val nextSlot = SoulRingSlots.all.firstOrNull { sl ->
            sl.index !in state.soulRings && sl.index < maxRings
        } ?: run { addLog("没有可用槽位"); notifyUI(); return false }
        if (!SoulRingSlots.canEquip(nextSlot.index, combinedTier)) {
            val minName = RingQuality.fullDisplayName(nextSlot.minCombinedTier)
            addLog("${nextSlot.displayName}需要${minName}或以上"); notifyUI(); return false
        }
        // 魂环不可更换：槽位必须为空
        if (nextSlot.index in state.soulRings) {
            addLog("${nextSlot.displayName}已有魂环，不可更换"); notifyUI(); return false
        }
        // 吸收容量检测
        val (yearOrd, qualOrd) = SoulRingGenerator.splitTier(combinedTier)
        val pct = SoulRingSystem.randomPercentage()
        val newLoad = SoulRingSystem.calcRingLoad(yearOrd, qualOrd, pct)
        val currentLoad = calcTotalRingLoad()
        val capacity = calcAbsorptionCapacity()
        if (currentLoad + newLoad > capacity) {
            val overload = currentLoad + newLoad - capacity
            addLog("⛔ 吸收容量不足！当前负荷 ${currentLoad}/${capacity}，新环需${newLoad}（超${overload}）")
            addLog("💡 可通过提升等级/装备/体魄凝练来增加根骨→提高吸收容量")
            notifyUI(); return false
        }
        val cost = RingYear.cost(combinedTier, state.soulRings.size)
        if (state.gold < cost) { addLog("金魂币不足，需要 ${formatNum(cost)}"); notifyUI(); return false }
        state.gold -= cost
        val affixes = SoulRingGenerator.generateRandomAffixes(combinedTier)
        val skill = ActiveSkillPool.randomSkill(combinedTier)
        state.soulRings[nextSlot.index] = SoulRingInstance(yearOrd, qualOrd, pct, affixes, skill)
        val fullName = SoulRingSystem.fullDisplayName(yearOrd, qualOrd, pct)
        val affixStr = affixes.joinToString(" ") { "${it.type.displayName}+${it.value}" }
        addLog("${nextSlot.displayName}吸收${fullName}成功！负荷 ${newLoad}/${capacity} | 属性: $affixStr")
        if (state.tutorialStep == 4) advanceTutorial()
        checkAchievements(); notifyUI()
        return true
    }

    /** 获取建议的下一个魂环合并层级 */
    fun getNextSoulRingGrade(): Int {
        val maxRings = RealmData.maxSoulRings(state.level)
        val nextSlot = SoulRingSlots.all.firstOrNull { sl ->
            sl.index !in state.soulRings && sl.index < maxRings
        } ?: return 24
        return nextSlot.minCombinedTier.coerceAtMost(24)
    }

    // ======== 魂骨 ========

    fun doEquipBone(boneTypeOrdinal: Int, combinedTier: Int): Boolean {
        val maxBones = RealmData.maxSoulBones(state.level)
        if (state.soulBones.size >= maxBones && !state.soulBones.containsKey(boneTypeOrdinal)) {
            addLog("当前境界最多装备 $maxBones 块魂骨"); notifyUI(); return false
        }
        if (combinedTier !in 0..24) return false
        val cost = BoneYear.cost(combinedTier)
        val currentBone = state.soulBones[boneTypeOrdinal]
        val isReplace = currentBone != null
        if (state.gold < cost) { addLog("金魂币不足，需要 ${formatNum(cost)}"); notifyUI(); return false }
        state.gold -= cost
        val (yearOrd, rarityOrd) = SoulRingGenerator.splitTier(combinedTier)
        val affixes = SoulBoneGenerator.generateRandomAffix(combinedTier)
        val passive = PassiveSkillPool.randomPassive(boneTypeOrdinal, combinedTier)
        state.soulBones[boneTypeOrdinal] = SoulBoneInstance(yearOrd, rarityOrd, affixes, passiveSkill = passive)
        val boneName = BoneType.entries[boneTypeOrdinal].displayName
        val affixStr = affixes.joinToString(" ") { "${it.type.displayName}+${it.value}" }
        val actionStr = if (isReplace) "替换" else "装备"
        addLog("$actionStr ${BoneRarity.fullDisplayName(combinedTier)}（$boneName）[${passive.name}] 属性: $affixStr"); notifyUI()
        return true
    }

    fun doEnhanceBone(boneTypeOrdinal: Int): Boolean {
        val bone = state.soulBones[boneTypeOrdinal] ?: run {
            addLog("该部位没有魂骨可强化"); notifyUI(); return false
        }
        if (bone.enhanceLevel >= EnhancementData.MAX_ENHANCE) {
            addLog("该魂骨已达最大强化等级（${EnhancementData.MAX_ENHANCE}）"); notifyUI(); return false
        }
        val cost = EnhancementData.enhanceCost(bone.enhanceLevel)
        if (state.gold < cost) { addLog("金魂币不足，需要 ${formatNum(cost)}"); notifyUI(); return false }
        state.gold -= cost
        val newLevel = bone.enhanceLevel + 1
        state.soulBones[boneTypeOrdinal] = bone.copy(enhanceLevel = newLevel)
        val boneName = BoneType.entries[boneTypeOrdinal].displayName
        addLog("🔨 $boneName 强化成功！+${newLevel} → 词缀效果 ×${String.format("%.1f", EnhancementData.enhanceEffect(newLevel))}")
        notifyUI(); return true
    }

    // ======== Boss商店/魂核系统 ========

    /** Boss币魂核随机抽取 */
    fun doSoulCoreGacha(): Boolean {
        val s = state
        val cost = BossShopData.SOUL_CORE_GACHA_COST
        if (s.bossCoin < cost) { addLog("Boss币不足，需要 $cost Boss币"); notifyUI(); return false }
        s.bossCoin -= cost
        val maxRarity = (s.currentMapId / 2).coerceIn(0, 5)
        val def = SoulCorePool.randomSoulCore(maxRarity)
        val rarity = (def.rarityOrdinal + Random.nextInt(3)).coerceIn(0, 5)
        val value = def.passiveSkill.getSoulCoreValue(rarity)
        val instance = DroppedSoulCore(def.name, rarity, def.passiveSkill, value, 0)
        // 检查过滤
        val isFiltered = isSoulCoreFiltered(def)
        if (s.backpackIsFull || isFiltered) {
            val refund = rarity * 50 + 100
            s.gold += refund
            s.totalGoldEarned += refund
            val reason = if (s.backpackIsFull) "包满" else "🎯已过滤"
            addLog("💠 ${def.name}(${Rarity.entries[rarity].displayName}) → ${reason}自动分解 +${formatNum(refund)}💰")
        } else {
            s.backpackSoulCores.add(instance)
            addLog("💠 抽取魂核: ${def.name}(${Rarity.entries[rarity].displayName}) +${value} (可装备于${def.category.compatibleSlots.joinToString("/"){it.displayName}})")
        }
        notifyUI(); return true
    }

    /** 装备魂核到指定槽位 */
    fun doEquipSoulCore(backpackIndex: Int, slotType: SoulCoreSlotType): Boolean {
        val s = state
        if (backpackIndex !in s.backpackSoulCores.indices) { addLog("物品不存在"); notifyUI(); return false }
        val core = s.backpackSoulCores.removeAt(backpackIndex)
        // 检查兼容性
        if (slotType !in core.category.compatibleSlots) {
            s.backpackSoulCores.add(backpackIndex, core)
            addLog("❌ ${core.name}不能装备到${slotType.displayName}")
            notifyUI(); return false
        }
        // 检查槽位是否解锁
        if (!SoulCoreSlotType.isUnlocked(slotType, s.prestigeCount)) {
            s.backpackSoulCores.add(backpackIndex, core)
            addLog("🔒 ${slotType.displayName}需转生${slotType.minPrestige}次解锁")
            notifyUI(); return false
        }
        // 如果该槽位已装备，把旧魂核放回背包
        val oldCore = s.equippedSoulCores[slotType]
        if (oldCore != null) {
            s.backpackSoulCores.add(DroppedSoulCore(oldCore.name, oldCore.rarityOrdinal, oldCore.passiveSkill, oldCore.value, oldCore.level))
            addLog("💠 ${slotType.displayName}: ${oldCore.name} → 已放回背包")
        }
        s.equippedSoulCores[slotType] = SoulCoreInstance(core.name, core.rarityOrdinal, core.passiveSkill, core.value, core.level, slotType)
        addLog("💠 ${slotType.displayName}装备: ${core.name}(${Rarity.entries[core.rarityOrdinal].displayName}) Lv.${core.level}")
        s.save()
        notifyUI(); return true
    }

    /** 卸下指定槽位的魂核 */
    fun doUnequipSoulCore(slotType: SoulCoreSlotType): Boolean {
        val s = state
        val core = s.equippedSoulCores[slotType] ?: return false
        s.backpackSoulCores.add(DroppedSoulCore(core.name, core.rarityOrdinal, core.passiveSkill, core.value, core.level))
        s.equippedSoulCores[slotType] = null
        addLog("卸下${slotType.displayName}: ${core.name}")
        s.save()
        notifyUI(); return true
    }

    /** 升级背包中的魂核（消耗同名低级魂核） */
    fun doUpgradeSoulCore(backpackIndex: Int): Boolean {
        val s = state
        if (backpackIndex !in s.backpackSoulCores.indices) { addLog("物品不存在"); notifyUI(); return false }
        val target = s.backpackSoulCores[backpackIndex]
        if (target.level >= SoulCoreLevelData.MAX_LEVEL) {
            addLog("${target.name}已达最大等级"); notifyUI(); return false
        }
        val need = SoulCoreLevelData.requiredCopies(target.level)
        // 在背包中查找同名的其他魂核（不含自身）
        val consumeIndices = s.backpackSoulCores.indices
            .filter { it != backpackIndex && s.backpackSoulCores[it].name == target.name }
            .take(need)
        if (consumeIndices.size < need) {
            addLog("❌ 升级需要${need}个同名${target.name}，背包中仅有${consumeIndices.size}个")
            notifyUI(); return false
        }
        // 消耗同名魂核
        consumeIndices.sortedByDescending { it }.forEach { s.backpackSoulCores.removeAt(it) }
        val newLevel = target.level + 1
        s.backpackSoulCores[backpackIndex] = target.copy(level = newLevel)
        val mult = SoulCoreLevelData.effectMultiplier(newLevel)
        addLog("⬆ ${target.name} 升级成功! Lv.${newLevel} (效果×${String.format("%.2f", mult)})")
        s.save()
        notifyUI(); return true
    }

    /** Boss币购买魂力结晶 */
    fun doBuySoulCrystal(): Boolean {
        val s = state
        if (s.bossCoin < BossShopData.SOUL_CRYSTAL_COST) { addLog("Boss币不足"); notifyUI(); return false }
        s.bossCoin -= BossShopData.SOUL_CRYSTAL_COST
        s.soulPower += BossShopData.SOUL_CRYSTAL_VALUE
        addLog("💎 购买魂力结晶 +${BossShopData.SOUL_CRYSTAL_VALUE}魂力")
        notifyUI(); return true
    }

    /** Boss币购买魂环（阶梯式，V5双维度） */
    fun doBuyBossRing(combinedTier: Int): Boolean {
        val s = state
        val item = BossShopData.ringItems.find { it.combinedTier == combinedTier }
            ?: return false.also { addLog("商品不存在"); notifyUI() }
        if (!BossShopData.isTierUnlocked(item.tier, s.currentMapId)) {
            addLog("🔒 ${item.tier.label}阶梯未解锁（需进入${item.tier.description}）"); notifyUI(); return false
        }
        if (s.bossCoin < item.cost) { addLog("Boss币不足，需要 ${item.cost} Boss币"); notifyUI(); return false }
        s.bossCoin -= item.cost
        val (yearOrd, qualOrd) = SoulRingGenerator.splitTier(combinedTier)
        val affixes = SoulRingGenerator.generateRandomAffixes(combinedTier)
        val skill = ActiveSkillPool.randomSkill(combinedTier)
        val pct = SoulRingSystem.randomPercentage()
        val ring = DroppedRing(yearOrd, qualOrd, pct, affixes, skill)
        val qualityName = RingQuality.fullDisplayName(combinedTier)
        if (s.backpackIsFull) {
            val price = calcRingSellPrice(combinedTier)
            s.gold += price; s.totalGoldEarned += price
            addLog("💍 ${qualityName}魂环 → 包满自动售出 +${formatNum(price)}💰")
        } else {
            s.backpackRings.add(ring)
            addLog("💍 购买${qualityName}魂环 [${skill.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")
        }
        pushNotifyEvent(NotifyType.BOSS_PURCHASE, "💍 Boss商店", "购买${qualityName}魂环 [${skill.name}]")
        notifyUI(); return true
    }

    /** Boss币购买魂骨（阶梯式，V5双维度） */
    fun doBuyBossBone(combinedTier: Int): Boolean {
        val s = state
        val item = BossShopData.boneItems.find { it.combinedTier == combinedTier }
            ?: return false.also { addLog("商品不存在"); notifyUI() }
        if (!BossShopData.isTierUnlocked(item.tier, s.currentMapId)) {
            addLog("🔒 ${item.tier.label}阶梯未解锁（需进入${item.tier.description}）"); notifyUI(); return false
        }
        if (s.bossCoin < item.cost) { addLog("Boss币不足，需要 ${item.cost} Boss币"); notifyUI(); return false }
        s.bossCoin -= item.cost
        val boneType = Random.nextInt(BoneType.entries.size)
        val (yearOrd, rarityOrd) = SoulRingGenerator.splitTier(combinedTier)
        val affixes = SoulBoneGenerator.generateRandomAffix(combinedTier)
        val passive = PassiveSkillPool.randomPassive(boneType, combinedTier)
        val bone = DroppedBone(boneType, yearOrd, rarityOrd, affixes, passive)
        val rarityName = BoneRarity.fullDisplayName(combinedTier)
        if (s.backpackIsFull) {
            val price = calcBoneSellPrice(combinedTier)
            s.gold += price; s.totalGoldEarned += price
            addLog("🦴 ${rarityName} → 包满自动售出 +${formatNum(price)}💰")
        } else {
            s.backpackBones.add(bone)
            addLog("🦴 购买${rarityName}(${BoneType.entries[boneType].displayName})[${passive.name}] (包${s.backpackTotalItems}/${s.backpackCapacity})")
        }
        pushNotifyEvent(NotifyType.BOSS_PURCHASE, "🦴 Boss商店", "购买${rarityName}(${BoneType.entries[boneType].displayName})[${passive.name}]")
        notifyUI(); return true
    }

    /** 兼容旧接口：购买十万年魂环 */
    fun doBuy100KRing(): Boolean = doBuyBossRing(15)

    /** 兼容旧接口：购买神级魂骨 */
    fun doBuyDivineBone(): Boolean = doBuyBossBone(15)

    // ======== 限时珍品商店（10分钟刷新，金魂币购买） ========

    /** 检查限时珍品是否需要刷新，若需则刷新并返回true */
    fun refreshLimitedShop(): Boolean {
        val now = System.currentTimeMillis()
        if (now - state.limitedShopRefreshTime < LimitedShopData.REFRESH_INTERVAL_MS) return false
        state.limitedShopRefreshTime = now
        state.limitedShopRings.clear()
        state.limitedShopRings.addAll(LimitedShopData.generateRings(state.currentMapId))
        state.limitedShopBones.clear()
        state.limitedShopBones.addAll(LimitedShopData.generateBones(state.currentMapId))
        addLog("🔄 限时珍品已刷新！")
        notifyUI()
        return true
    }

    /** 获取限时珍品剩余刷新秒数 */
    fun limitedShopRemainSec(): Int {
        val elapsed = System.currentTimeMillis() - state.limitedShopRefreshTime
        val remain = (LimitedShopData.REFRESH_INTERVAL_MS - elapsed).coerceAtLeast(0)
        return (remain / 1000).toInt()
    }

    /** 购买限时珍品中的魂环 */
    fun buyLimitedShopRing(index: Int): Boolean {
        refreshLimitedShop()
        if (index !in state.limitedShopRings.indices) { addLog("物品不存在"); notifyUI(); return false }
        val ring = state.limitedShopRings[index]
        val price = LimitedShopData.ringBuyPrice(ring.combinedTier)
        if (state.gold < price) { addLog("金魂币不足，需要 ${formatNum(price)}"); notifyUI(); return false }
        state.gold -= price
        val dropped = state.limitedShopRings.removeAt(index)
        val qualityName = RingQuality.fullDisplayName(dropped.combinedTier)
        val skillName = dropped.skill?.name ?: "无技能"
        state.backpackRings.add(dropped)
        addLog("💍 购买 ${qualityName}[${skillName}] 花费 ${formatNum(price)}💰 (包${state.backpackTotalItems}/${state.backpackCapacity})")
        pushNotifyEvent(NotifyType.SHOP_PURCHASE, "💍 限时珍品", "购买 ${qualityName}[${skillName}]")
        notifyUI(); return true
    }

    /** 购买限时珍品中的魂骨 */
    fun buyLimitedShopBone(index: Int): Boolean {
        refreshLimitedShop()
        if (index !in state.limitedShopBones.indices) { addLog("物品不存在"); notifyUI(); return false }
        val bone = state.limitedShopBones[index]
        val price = LimitedShopData.boneBuyPrice(bone.combinedTier)
        if (state.gold < price) { addLog("金魂币不足，需要 ${formatNum(price)}"); notifyUI(); return false }
        state.gold -= price
        val dropped = state.limitedShopBones.removeAt(index)
        val rarityName = BoneRarity.fullDisplayName(dropped.combinedTier)
        val typeName = BoneType.entries.getOrNull(dropped.boneTypeOrdinal)?.displayName ?: "魂骨"
        state.backpackBones.add(dropped)
        addLog("🦴 购买 ${rarityName}($typeName) 花费 ${formatNum(price)}💰 (包${state.backpackTotalItems}/${state.backpackCapacity})")
        pushNotifyEvent(NotifyType.SHOP_PURCHASE, "🦴 限时珍品", "购买 ${rarityName}($typeName)")
        notifyUI(); return true
    }

    // ======== 背包管理 ========

    /** V5: 魂环卖出价格（基于合并层级0-24） */
    fun calcRingSellPrice(combinedTier: Int): Long = when {
        combinedTier < 5 -> 80L + combinedTier * 55L
        combinedTier < 10 -> 350L + (combinedTier - 5) * 230L
        combinedTier < 15 -> 1500L + (combinedTier - 10) * 900L
        combinedTier < 20 -> 6000L + (combinedTier - 15) * 4800L
        else -> 30000L + (combinedTier - 20) * 20000L
    }

    /** V5: 魂骨卖出价格（基于合并层级0-24） */
    fun calcBoneSellPrice(combinedTier: Int): Long = when {
        combinedTier < 5 -> 800L + combinedTier * 550L
        combinedTier < 10 -> 3500L + (combinedTier - 5) * 2300L
        combinedTier < 15 -> 15000L + (combinedTier - 10) * 13000L
        combinedTier < 20 -> 80000L + (combinedTier - 15) * 64000L
        else -> 400000L + (combinedTier - 20) * 200000L
    }

    /** 切换魂环锁定状态 */
    fun toggleLockRing(index: Int): Boolean {
        if (index !in state.backpackRings.indices) return false
        val nowLocked = state.toggleRingLock(index)
        addLog(if (nowLocked) "🔒 已锁定魂环" else "🔓 已解锁魂环")
        notifyUI()
        return nowLocked
    }

    /** 切换魂骨锁定状态 */
    fun toggleLockBone(index: Int): Boolean {
        if (index !in state.backpackBones.indices) return false
        val nowLocked = state.toggleBoneLock(index)
        addLog(if (nowLocked) "🔒 已锁定魂骨" else "🔓 已解锁魂骨")
        notifyUI()
        return nowLocked
    }

    /** 卖出背包中的单个魂环 */
    fun sellBackpackRing(index: Int): Boolean {
        if (index !in state.backpackRings.indices) { addLog("物品不存在"); notifyUI(); return false }
        if (state.isRingLocked(index)) { addLog("🔒 该魂环已锁定，无法卖出"); notifyUI(); return false }
        val ring = state.backpackRings.removeAt(index)
        state.lockedBackpackRingIndices.remove(index)
        state.lockedBackpackRingIndices.toList().filter { it > index }.forEach {
            state.lockedBackpackRingIndices.remove(it)
            state.lockedBackpackRingIndices.add(it - 1)
        }
        val price = calcRingSellPrice(ring.combinedTier)
        state.gold += price
        state.totalGoldEarned += price
        addLog("💰 卖出 ${RingQuality.fullDisplayName(ring.combinedTier)} +${formatNum(price)}金魂币")
        notifyUI()
        return true
    }

    /** 卖出背包中的单个魂骨 */
    fun sellBackpackBone(index: Int): Boolean {
        if (index !in state.backpackBones.indices) { addLog("物品不存在"); notifyUI(); return false }
        if (state.isBoneLocked(index)) { addLog("🔒 该魂骨已锁定，无法卖出"); notifyUI(); return false }
        val bone = state.backpackBones.removeAt(index)
        state.lockedBackpackBoneIndices.remove(index)
        state.lockedBackpackBoneIndices.toList().filter { it > index }.forEach {
            state.lockedBackpackBoneIndices.remove(it)
            state.lockedBackpackBoneIndices.add(it - 1)
        }
        val price = calcBoneSellPrice(bone.combinedTier)
        state.gold += price
        state.totalGoldEarned += price
        val typeName = BoneType.entries.getOrNull(bone.boneTypeOrdinal)?.displayName ?: "魂骨"
        addLog("💰 卖出 ${BoneRarity.fullDisplayName(bone.combinedTier)}($typeName) +${formatNum(price)}金魂币")
        notifyUI()
        return true
    }

    /** 一键卖出所有未锁定魂环 */
    fun sellAllBackpackRings(): Long {
        var total = 0L
        var soldCount = 0
        var lockedCount = 0
        val toSell = mutableListOf<Int>()
        for (i in state.backpackRings.indices.reversed()) {
            if (state.isRingLocked(i)) { lockedCount++; continue }
            total += calcRingSellPrice(state.backpackRings[i].combinedTier)
            toSell.add(i)
        }
        for (idx in toSell) {
            state.backpackRings.removeAt(idx)
            soldCount++
        }
        state.lockedBackpackRingIndices.clear()
        state.gold += total
        state.totalGoldEarned += total
        val msg = if (lockedCount > 0)
            "💰 售出${soldCount}个魂环 +${formatNum(total)}💰 (${lockedCount}个已锁定跳过)"
        else "💰 一键售出全部魂环 +${formatNum(total)}金魂币"
        addLog(msg)
        notifyUI()
        return total
    }

    /** 一键卖出所有未锁定魂骨 */
    fun sellAllBackpackBones(): Long {
        var total = 0L
        var soldCount = 0
        var lockedCount = 0
        val toSell = mutableListOf<Int>()
        for (i in state.backpackBones.indices.reversed()) {
            if (state.isBoneLocked(i)) { lockedCount++; continue }
            total += calcBoneSellPrice(state.backpackBones[i].combinedTier)
            toSell.add(i)
        }
        for (idx in toSell) {
            state.backpackBones.removeAt(idx)
            soldCount++
        }
        state.lockedBackpackBoneIndices.clear()
        state.gold += total
        state.totalGoldEarned += total
        val msg = if (lockedCount > 0)
            "💰 售出${soldCount}个魂骨 +${formatNum(total)}💰 (${lockedCount}个已锁定跳过)"
        else "💰 一键售出全部魂骨 +${formatNum(total)}金魂币"
        addLog(msg)
        notifyUI()
        return total
    }

    /** 整理魂环背包（品质降序） */
    fun sortBackpackRings() {
        state.backpackRings.sortByDescending { it.combinedTier }
        addLog("📦 魂环背包已整理（品质↓）")
        notifyUI()
    }

    /** 整理魂骨背包（品质降序） */
    fun sortBackpackBones() {
        state.backpackBones.sortByDescending { it.combinedTier }
        addLog("📦 魂骨背包已整理（品质↓）")
        notifyUI()
    }

    // ======== 卸下装备（魂骨/魂环可卸下） ========

    /** 卸下指定部位的魂骨 */
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

    /** 卸下指定槽位的魂环 */
    fun unequipRing(boneTypeOrdinal: Int): Boolean {
        val slotIdx = boneTypeOrdinal
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
            state.backpackRings.add(DroppedRing(ring.yearOrdinal, ring.qualityOrdinal, ring.affixes, ring.skill, ring.percentage))
            addLog("卸下 ${slotName}: ${fullName} → 已放入背包")
        }
        notifyUI()
        return true
    }

    // ======== 背包容量扩容 ========

    /** 计算下一次扩容价格：2000 × tier × 2^tier */
    fun expandBackpackCost(): Long {
        val tier = state.backpackCapacityTier + 1  // 下一次扩容的tier
        return (2000L * tier * (1L shl tier)).coerceAtMost(100_000_000L)
    }

    /** 扩容背包（支持WEALTH折扣和免费） */
    fun expandBackpack(): Boolean {
        val s = state
        // 检查免费扩容
        if (s.freeExpandRemaining > 0) {
            s.freeExpandRemaining--
            s.backpackCapacityTier++
            val newCap = s.backpackCapacity
            addLog("📦 背包免费扩容成功！当前容量: $newCap 格 (剩余免费${s.freeExpandRemaining}次)")
            notifyUI()
            return true
        }
        var cost = expandBackpackCost()
        // WEALTH天赋折扣
        val wealthLv = s.getTalentLevel(TalentBranch.WEALTH)
        val discount = when (wealthLv) { 1 -> 0.20; 2 -> 0.40; 3 -> 0.60; else -> 0.0 }
        if (discount > 0) {
            cost = (cost * (1.0 - discount)).toLong().coerceAtLeast(1L)
            addLog("🪙 财富之道Lv${wealthLv}折扣: -${(discount * 100).toInt()}%")
        }
        if (s.gold < cost) {
            addLog("金魂币不足，扩容需要 ${formatNum(cost)}💰")
            notifyUI()
            return false
        }
        s.gold -= cost
        s.backpackCapacityTier++
        val newCap = s.backpackCapacity
        addLog("📦 背包扩容成功！当前容量: $newCap 格 (花费 ${formatNum(cost)}💰)")
        notifyUI()
        return true
    }

    /** 自动售卖魂环（卖出低于阈值的所有魂环） */
    private fun applyAutoSellRings() {
        val th = state.autoSellRingThreshold
        if (th <= 0) return
        var sold = 0
        var gold = 0L
        val iter = state.backpackRings.iterator()
        while (iter.hasNext()) {
            val r = iter.next()
            if (r.qualityOrdinal < th) {
                gold += calcRingSellPrice(r.combinedTier)
                iter.remove()
                sold++
            }
        }
        if (sold > 0) {
            state.gold += gold
            state.totalGoldEarned += gold
        }
    }

    /** 自动售卖魂骨（卖出低于阈值的所有魂骨） */
    private fun applyAutoSellBones() {
        val th = state.autoSellBoneThreshold
        if (th <= 0) return
        var sold = 0
        var gold = 0L
        val iter = state.backpackBones.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            if (b.rarityOrdinal < th) {
                gold += calcBoneSellPrice(b.combinedTier)
                iter.remove()
                sold++
            }
        }
        if (sold > 0) {
            state.gold += gold
            state.totalGoldEarned += gold
        }
    }

    /** 切换魂环自动售卖 */
    fun toggleAutoSellRings(): Boolean {
        state.autoSellRings = !state.autoSellRings
        if (state.autoSellRings) {
            applyAutoSellRings()
            addLog("🔄 魂环自动售卖已开启（售出<${RingQuality.entries[state.autoSellRingThreshold].displayName}）")
        } else {
            addLog("🔄 魂环自动售卖已关闭")
        }
        notifyUI()
        return state.autoSellRings
    }

    /** 切换魂骨自动售卖 */
    fun toggleAutoSellBones(): Boolean {
        state.autoSellBones = !state.autoSellBones
        if (state.autoSellBones) {
            applyAutoSellBones()
            addLog("🔄 魂骨自动售卖已开启（售出<${BoneRarity.entries[state.autoSellBoneThreshold].displayName}）")
        } else {
            addLog("🔄 魂骨自动售卖已关闭")
        }
        notifyUI()
        return state.autoSellBones
    }

    /** 设置魂环自动售卖阈值 */
    fun setAutoSellRingThreshold(years: Int) {
        state.autoSellRingThreshold = years.coerceIn(0, RingQuality.entries.size)
        addLog("魂环自动售卖阈值: <${RingQuality.entries.getOrNull(state.autoSellRingThreshold)?.displayName ?: "关闭"}")
        if (state.autoSellRings) applyAutoSellRings()
        notifyUI()
    }

    /** 设置魂骨自动售卖阈值 */
    fun setAutoSellBoneThreshold(rarity: Int) {
        state.autoSellBoneThreshold = rarity.coerceIn(0, BoneRarity.entries.size)
        addLog("魂骨自动售卖阈值: <${BoneRarity.entries.getOrNull(state.autoSellBoneThreshold)?.displayName ?: "关闭"}")
        if (state.autoSellBones) applyAutoSellBones()
        notifyUI()
    }

    // ======== 掉落过滤开关 ========

    /** 切换魂环年份过滤 */
    fun toggleRingYearFilter(yearOrdinal: Int) {
        val set = state.ringFilterYears
        if (set.contains(yearOrdinal)) set.remove(yearOrdinal) else set.add(yearOrdinal)
        notifyUI()
    }

    /** 切换魂环品质过滤 */
    fun toggleRingQualityFilter(qualOrdinal: Int) {
        val set = state.ringFilterQualities
        if (set.contains(qualOrdinal)) set.remove(qualOrdinal) else set.add(qualOrdinal)
        notifyUI()
    }

    /** 切换魂骨年份过滤 */
    fun toggleBoneYearFilter(yearOrdinal: Int) {
        val set = state.boneFilterYears
        if (set.contains(yearOrdinal)) set.remove(yearOrdinal) else set.add(yearOrdinal)
        notifyUI()
    }

    /** 切换魂骨品质过滤 */
    fun toggleBoneRarityFilter(rarityOrdinal: Int) {
        val set = state.boneFilterRarities
        if (set.contains(rarityOrdinal)) set.remove(rarityOrdinal) else set.add(rarityOrdinal)
        notifyUI()
    }

    /** 切换魂核稀有度过滤 */
    fun toggleSoulCoreRarityFilter(rarityOrdinal: Int) {
        val set = state.soulCoreFilterRarities
        if (set.contains(rarityOrdinal)) set.remove(rarityOrdinal) else set.add(rarityOrdinal)
        notifyUI()
    }

    /** 切换魂核分类过滤 */
    fun toggleSoulCoreCategoryFilter(category: SoulCoreCategory) {
        val set = state.soulCoreFilterCategories
        if (set.contains(category)) set.remove(category) else set.add(category)
        notifyUI()
    }

    /** 检查魂核是否被过滤 */
    private fun isSoulCoreFiltered(def: SoulCoreDef): Boolean {
        val s = state
        return s.soulCoreFilterRarities.contains(def.rarityOrdinal) ||
               s.soulCoreFilterCategories.contains(def.category)
    }

    // ======== 背包装备 ========

    /** 从背包装备魂环到对应年限槽位 */
    fun doEquipBackpackRing(index: Int): Boolean {
        if (index !in state.backpackRings.indices) { addLog("背包物品不存在"); notifyUI(); return false }
        val dropped = state.backpackRings[index]
        val maxRings = RealmData.maxSoulRings(state.level)
        val combined = dropped.combinedTier
        val dropName = RingQuality.fullDisplayName(combined)

        // 找到可用的空槽位（满足品质要求）
        val emptySlot = SoulRingSlots.all.firstOrNull { sl ->
            sl.index < maxRings && sl.index !in state.soulRings && SoulRingSlots.canEquip(sl.index, combined)
        }
        if (emptySlot != null) {
            // 吸收容量检测
            val newLoad = SoulRingSystem.calcRingLoad(dropped.yearOrdinal, dropped.qualityOrdinal, dropped.percentage)
            val currentLoad = calcTotalRingLoad()
            val capacity = calcAbsorptionCapacity()
            if (currentLoad + newLoad > capacity) {
                val overload = currentLoad + newLoad - capacity
                addLog("⛔ 吸收容量不足！当前负荷 ${currentLoad}/${capacity}，该环需${newLoad}（超${overload}）")
                addLog("💡 可通过提升等级/装备/体魄凝练来增加根骨→提高吸收容量")
                notifyUI(); return false
            }
            state.soulRings[emptySlot.index] = SoulRingInstance(dropped.yearOrdinal, dropped.qualityOrdinal, dropped.percentage, dropped.affixes, dropped.skill)
            addLog("${emptySlot.displayName}装备: ${dropName}（负荷 ${newLoad}/${capacity}）")
            state.backpackRings.removeAt(index)
            checkAchievements(); notifyUI()
            return true
        }

        // 魂环不可更换：没有空槽位则无法装备
        addLog("没有合适的魂环槽位可装备该${dropName}")
        notifyUI()
        return false
    }

    /** 从背包装备魂骨（替换现有同部位魂骨） */
    fun doEquipBackpackBone(index: Int): Boolean {
        if (index !in state.backpackBones.indices) { addLog("背包物品不存在"); notifyUI(); return false }
        val dropped = state.backpackBones[index]
        val maxBones = RealmData.maxSoulBones(state.level)
        if (state.soulBones.size >= maxBones && !state.soulBones.containsKey(dropped.boneTypeOrdinal)) {
            addLog("当前境界最多装备 $maxBones 块魂骨"); notifyUI(); return false
        }
        val boneName = BoneType.entries[dropped.boneTypeOrdinal].displayName
        state.soulBones[dropped.boneTypeOrdinal] = SoulBoneInstance(dropped.yearOrdinal, dropped.rarityOrdinal, dropped.affixes, passiveSkill = dropped.passiveSkill)
        addLog("装备魂骨: ${BoneRarity.fullDisplayName(dropped.combinedTier)}（$boneName）")
        state.backpackBones.removeAt(index)
        notifyUI()
        return true
    }

    // ======== 金币消耗功能 ========

    /** 紧急修炼：消耗金币获取魂力 */
    /** 金币魂核随机抽取 */
    fun doGoldSoulCoreDraw(): Boolean {
        val s = state
        val cost = GoldSinkData.SOUL_CORE_DRAW_COST
        if (s.gold < cost) { addLog("金魂币不足，需要 ${formatNum(cost)}💰"); notifyUI(); return false }
        if (s.inBattle) { addLog("战斗中无法抽取！"); notifyUI(); return false }
        s.gold -= cost
        val maxRarity = (s.currentMapId / 2).coerceIn(0, 5)
        val def = SoulCorePool.randomSoulCore(maxRarity)
        val rarity = (def.rarityOrdinal + Random.nextInt(3)).coerceIn(0, 5)
        val value = def.passiveSkill.getSoulCoreValue(rarity)
        val instance = DroppedSoulCore(def.name, rarity, def.passiveSkill, value, 0)
        // 检查过滤
        val isFiltered = isSoulCoreFiltered(def)
        if (s.backpackIsFull || isFiltered) {
            val refund = rarity * 50 + 100
            s.gold += refund
            s.totalGoldEarned += refund
            val reason = if (s.backpackIsFull) "包满" else "🎯已过滤"
            addLog("💠 ${def.name}(${Rarity.entries[rarity].displayName}) → ${reason}自动分解 +${formatNum(refund)}💰")
        } else {
            s.backpackSoulCores.add(instance)
            addLog("💠 金币抽卡: ${def.name}(${Rarity.entries[rarity].displayName}) +${value} (花费${formatNum(cost)}💰)")
        }
        notifyUI(); return true
    }

    /** 金币强制刷新限时商店 */
    fun doGoldRefreshShop(): Boolean {
        val s = state
        val cost = GoldSinkData.REFRESH_SHOP_COST
        if (s.gold < cost) { addLog("金魂币不足，需要 ${formatNum(cost)}💰"); notifyUI(); return false }
        if (s.inBattle) { addLog("战斗中无法刷新！"); notifyUI(); return false }
        s.gold -= cost
        // 强制刷新
        s.limitedShopRefreshTime = 0L
        refreshLimitedShop()
        addLog("🔄 限时商店已强制刷新 (花费 ${formatNum(cost)}💰)")
        notifyUI(); return true
    }

    fun prestigeMultiplier(): Double = 1.0 + state.prestigeCount * 0.1

    /** 修炼消耗金魂币（公式：等级×50） */
    fun cultivationCost(): Long = (state.level * 50L).coerceAtLeast(50L)

    /** 魂力倍率（基于修炼消耗） */
    fun soulPowerMultiplier(): Double = prestigeMultiplier() * 2.0

    /** 切换战斗倍速(1x/2x) */
    fun toggleBattleSpeed() {
        state.battleSpeed = if (state.battleSpeed == 1) 2 else 1
        addLog("⚡ 战斗倍速切换至: ${if (state.battleSpeed == 2) "2倍速·每回合执行2次" else "1倍速"}")
        notifyUI()
    }

    // ======== 杀戮之都（RPG爬塔流程重构） ========

    /** 进入杀戮之都 */
    fun doTowerEnter(): Boolean {
        if (state.towerFloor >= TowerData.MAX_FLOOR) {
            addLog("🏆 已通关杀戮之都全部楼层！"); notifyUI(); return false
        }
        if (state.currentHp <= 0) { addLog("你已阵亡，等待恢复中..."); notifyUI(); return false }
        state.towerInRun = true
        state.towerTempHp = calcMaxHp()
        val nextFloor = state.towerFloor + 1
        val seg = TowerData.getSegment(nextFloor)
        addLog("🗼 进入杀戮之都 (${seg.theme}) ${seg.name} 第${nextFloor}层")
        addLog("👊 塔内HP: ${formatNum(state.towerTempHp)}")
        notifyUI()
        return true
    }

    /** 挑战当前层 */
    fun doTowerChallenge(): Boolean {
        val s = state
        if (!s.towerInRun) {
            addLog("⚠️ 请先进入杀戮之都"); notifyUI(); return false
        }
        if (s.towerTempHp <= 0) {
            addLog("💀 塔内HP耗尽，请退出后重新进入"); notifyUI(); return false
        }
        val nextFloor = s.towerFloor + 1
        if (nextFloor > TowerData.MAX_FLOOR) {
            addLog("已通关全部楼层！"); notifyUI(); return false
        }

        val seg = TowerData.getSegment(nextFloor)
        val isBoss = TowerData.isBossFloor(nextFloor)
        val isRest = TowerData.isRestFloor(nextFloor)

        // 塔内战斗使用 towerTempHp 作为HP池
        val savedMapHp = s.currentHp
        s.currentHp = s.towerTempHp

        // 生成怪物
        val towerMonster = generateTowerMonster(nextFloor)
        s.inBattle = true
        val result = resolveBattle(towerMonster)
        s.inBattle = false

        // 将战斗剩余HP存回塔内HP
        s.towerTempHp = s.currentHp
        // 恢复地图HP
        s.currentHp = savedMapHp

        if (result.won) {
            // 消耗塔内HP
            val hpCostPct = TowerData.towerHpCostPerFloor(nextFloor)
            if (hpCostPct > 0) {
                s.towerTempHp = (s.towerTempHp * (1.0 - hpCostPct)).toLong().coerceAtLeast(0)
            }

            val reward = TowerData.normalReward(nextFloor)
            s.gold += reward; s.totalGoldEarned += reward
            s.towerFloor = nextFloor
            s.codexKills++
            s.totalBattleWins++

            // V3: 杀气掉落
            val ki = TowerShopData.normalKillingIntent(nextFloor)
            s.killingIntent += ki

            if (isBoss) {
                s.towerBossKills++
                val segIdx = (nextFloor / 10) - 1
                val atkBonus = TowerData.bossAtkBonus(segIdx)
                val bossGold = TowerData.bossGoldBonus(nextFloor)
                val bossKi = TowerShopData.bossKillingIntent(nextFloor)
                s.gold += bossGold; s.totalGoldEarned += bossGold
                s.killingIntent += bossKi
                val boneRarity = TowerData.bossBoneReward(nextFloor)

                addLog("👑 击败【${seg.bossName}】！永久ATK+${atkBonus}")
                addLog("💎 掉落：${BoneRarity.fullDisplayName(boneRarity)}魂骨 +${formatNum(bossGold)}💰 +${ki + bossKi}⚔️杀气")

                // 放置魂骨到空槽位
                val emptySlot = BoneType.entries.indices.firstOrNull { !s.soulBones.containsKey(it) }
                if (emptySlot != null) {
                    val currentBone = s.soulBones[emptySlot]
                    if (currentBone == null || boneRarity > (currentBone.combinedTier)) {
                        val affixes = SoulBoneGenerator.generateRandomAffix(boneRarity)
                        s.soulBones[emptySlot] = SoulBoneInstance(boneRarity, affixes)
                        val boneName = BoneType.entries[emptySlot].displayName
                        addLog("🦴 ${boneName}已装备${BoneRarity.fullDisplayName(boneRarity)}魂骨")
                    }
                }
                addLog("🎉 ${seg.name}段位通关！")
            } else if (isRest) {
                // 休息层：回复塔内HP
                val maxHp = calcMaxHp()
                s.towerTempHp = maxHp
                addLog("🛌 休息层！塔内HP回满！")
            }

            addLog("🏆 第${nextFloor}层通关 +${formatNum(reward)}💰 +${ki}⚔️杀气 (塔内HP:${formatNum(s.towerTempHp)})")
            checkAchievements()

            // 通关全部后自动退出
            if (s.towerFloor >= TowerData.MAX_FLOOR) {
                s.towerInRun = false
                addLog("🌟 恭喜！成功征服杀戮之都全部100层！")
            }
        } else {
            // 失败：塔内HP归零，强制退出
            s.towerTempHp = 0
            s.towerInRun = false
            s.totalBattleLosses++
            addLog("💀 第${nextFloor}层挑战失败！塔内HP耗尽，退出杀戮之都")
        }
        notifyUI(); s.save()
        return result.won
    }

    /** 退出杀戮之都（保留进度） */
    fun doTowerExit(): Boolean {
        if (!state.towerInRun) {
            addLog("未在塔内旅程中"); notifyUI(); return false
        }
        state.towerInRun = false
        state.towerTempHp = 0
        addLog("🚪 退出杀戮之都，进度已保存（当前第${state.towerFloor}层）")
        state.save()
        notifyUI()
        return true
    }

    /** 扫荡：自动战斗已通关楼层，一口气跳N层 */
    fun doTowerSweep(): Boolean {
        val s = state
        if (!s.towerInRun) {
            addLog("⚠️ 请先进入杀戮之都"); notifyUI(); return false
        }
        if (s.towerTempHp <= 0) {
            addLog("💀 塔内HP不足"); notifyUI(); return false
        }

        val attr = calcAttributes()
        val playerPower = attr.atk.toLong() + attr.matk.toLong()
        var swept = 0
        var totalGold = 0L
        var totalKi = 0

        // 连续扫荡直到血量不足或战力不够
        while (s.towerFloor < TowerData.MAX_FLOOR && s.towerTempHp > 0) {
            val nextFloor = s.towerFloor + 1
            val reqPower = TowerData.monsterPower(nextFloor)

            // 战力需碾压才能扫荡
            if (playerPower < reqPower * TowerData.SWEEP_POWER_MULTIPLIER) break

            val hpCostPct = TowerData.towerHpCostPerFloor(nextFloor)
            if (hpCostPct > 0) {
                val afterHp = (s.towerTempHp * (1.0 - hpCostPct)).toLong()
                if (afterHp <= 0) break
                s.towerTempHp = afterHp
            }

            val reward = TowerData.normalReward(nextFloor)
            totalGold += reward
            totalKi += TowerShopData.normalKillingIntent(nextFloor)
            s.towerFloor = nextFloor
            s.codexKills++
            swept++

            // Boss层特殊处理
            if (TowerData.isBossFloor(nextFloor)) {
                s.towerBossKills++
                totalGold += TowerData.bossGoldBonus(nextFloor)
                totalKi += TowerShopData.bossKillingIntent(nextFloor)
                val segIdx = (nextFloor / 10) - 1
                // ATK奖励
                val boneRarity = TowerData.bossBoneReward(nextFloor)
                val emptySlot = BoneType.entries.indices.firstOrNull { !s.soulBones.containsKey(it) }
                if (emptySlot != null) {
                    val currentBone = s.soulBones[emptySlot]
                    if (currentBone == null || boneRarity > (currentBone.combinedTier)) {
                        val affixes = SoulBoneGenerator.generateRandomAffix(boneRarity)
                        s.soulBones[emptySlot] = SoulBoneInstance(boneRarity, affixes)
                    }
                }
            }

            // 休息层回复
            if (TowerData.isRestFloor(nextFloor)) {
                s.towerTempHp = calcMaxHp()
            }
        }

        if (swept > 0) {
            s.gold += totalGold; s.totalGoldEarned += totalGold
            s.killingIntent += totalKi
            addLog("⚡ 扫荡完成！一口气通关 ${swept} 层 +${formatNum(totalGold)}💰 +${totalKi}⚔️杀气")
            addLog("📍 当前到达第${s.towerFloor}层 (塔内HP:${formatNum(s.towerTempHp)})")
            checkAchievements()
        } else {
            addLog("⚠️ 无法扫荡：战力不足或HP耗尽")
        }
        s.save()
        notifyUI()
        return swept > 0
    }

    /** V3: 重置杀戮之都进度（保留Boss击杀数和杀气货币） */
    fun doTowerReset(): Boolean {
        val s = state
        if (s.towerInRun) { addLog("⚠️ 请先退出塔内旅程"); notifyUI(); return false }
        if (s.towerFloor == 0) { addLog("已在第1层，无需重置"); notifyUI(); return false }
        val oldFloor = s.towerFloor
        s.towerFloor = 0
        s.towerTempHp = 0
        addLog("🔄 杀戮之都已重置！从第${oldFloor}层回到起点")
        addLog("  Boss击杀数(${s.towerBossKills})和杀气(${s.killingIntent})已保留")
        s.save()
        notifyUI()
        return true
    }

    /** V3: 购买杀气称号 */
    fun doBuyTowerTitle(itemId: String): Boolean {
        val s = state
        val item = TowerShopData.titleItems.find { it.id == itemId } ?: run {
            addLog("无效的称号"); notifyUI(); return false
        }
        if (s.unlockedTowerTitles.contains(itemId)) {
            addLog("已拥有该称号: ${item.name}"); notifyUI(); return false
        }
        if (s.killingIntent < item.cost) {
            addLog("杀气不足！需要 ${item.cost}⚔️，当前 ${s.killingIntent}⚔️"); notifyUI(); return false
        }
        s.killingIntent -= item.cost
        s.unlockedTowerTitles.add(itemId)
        addLog("🛡️ 兑换称号【${item.name}】！${item.desc}")
        pushNotifyEvent(NotifyType.TITLE, "🛡️ 称号解锁", "获得称号【${item.name}】\n${item.desc}")
        s.save()
        notifyUI()
        return true
    }

    /** V3: 购买杀气属性（可重复） */
    fun doBuyTowerStat(itemId: String): Boolean {
        val s = state
        val item = TowerShopData.statItems.find { it.id == itemId } ?: run {
            addLog("无效的属性项"); notifyUI(); return false
        }
        if (s.killingIntent < item.cost) {
            addLog("杀气不足！需要 ${item.cost}⚔️，当前 ${s.killingIntent}⚔️"); notifyUI(); return false
        }
        s.killingIntent -= item.cost
        val current = s.towerAttributeLevels[itemId] ?: 0
        s.towerAttributeLevels[itemId] = current + 1
        val count = current + 1
        val bonusDesc = buildString {
            if (item.hp > 0) append("HP+${item.hp * count}")
            if (item.atk > 0) append(if (isNotEmpty()) " " else "").append("ATK+${item.atk * count}")
        }
        addLog("⚔️ ${item.name} ×${count} → ${bonusDesc}")
        s.save()
        notifyUI()
        return true
    }

    // ======== V3: 每日副本 ========

    /** 是否可以进入每日副本 */
    fun canEnterDungeon(): Boolean {
        val s = state
        if (s.prestigeCount < DungeonData.MIN_PRESTIGE) return false
        val now = System.currentTimeMillis()
        // 检查是否跨天
        val lastDay = s.lastDungeonTime / DungeonData.DAILY_RESET_MS
        val today = now / DungeonData.DAILY_RESET_MS
        return lastDay < today  // 今天还没打过
    }

    /** 进入每日副本 */
    fun doDungeonEnter(): Boolean {
        if (state.prestigeCount < DungeonData.MIN_PRESTIGE) {
            addLog("需要转生${DungeonData.MIN_PRESTIGE}次才能进入每日副本"); notifyUI(); return false
        }
        if (!canEnterDungeon()) {
            addLog("今日已完成每日副本，明天再来！"); notifyUI(); return false
        }
        if (state.currentHp <= 0) { addLog("你已阵亡，等待恢复中..."); notifyUI(); return false }

        val maxTier = DungeonData.getMaxTier(state.prestigeCount)
        val tier = DungeonData.tiers[maxTier]
        addLog("⚔️ 进入每日副本: ${tier.name} (${tier.difficulty})")
        addLog("👤 Boss: ${tier.bossName} — ${tier.bossDesc}")
        addLog("⚠️ 警告: Boss极强，准备充分再挑战！")
        notifyUI()
        return true
    }

    /** 挑战每日副本Boss */
    fun doDungeonFight(): Boolean {
        val s = state
        if (s.prestigeCount < DungeonData.MIN_PRESTIGE) {
            addLog("需要转生${DungeonData.MIN_PRESTIGE}次"); notifyUI(); return false
        }
        if (!canEnterDungeon()) {
            addLog("今日已完成每日副本"); notifyUI(); return false
        }

        val maxTier = DungeonData.getMaxTier(s.prestigeCount)
        val tier = DungeonData.tiers[maxTier]
        val attr = calcAttributes()

        // 生成副本Boss（极强）
        val dungeonBoss = Monster(
            name = tier.bossName,
            affixes = listOf(MonsterAffix.BOSS, MonsterAffix.BERSERK, MonsterAffix.HOLY),
            maxHp = (attr.maxHp * tier.hpMult).toLong(),
            hp = (attr.maxHp * tier.hpMult).toLong(),
            atk = (attr.atk * tier.atkMult * 1.5).toInt().coerceAtLeast(1),
            matk = (attr.matk * tier.atkMult * 1.5).toInt().coerceAtLeast(1),
            critRate = 60,
            critDmg = 300,
            pdef = (attr.atk / 2),
            mdef = (attr.matk / 2),
            expReward = 0,
            goldReward = tier.goldReward,
            canDropEquip = tier.boneTier >= 0
        )

        s.inBattle = true
        val result = resolveBattle(dungeonBoss)
        s.inBattle = false

        if (result.won) {
            s.gold += tier.goldReward; s.totalGoldEarned += tier.goldReward
            s.killingIntent += tier.killingIntentReward
            s.lastDungeonTime = System.currentTimeMillis()
            s.dungeonTierCompleted = maxTier
            addLog("🏆 副本【${tier.name}】通关！+${formatNum(tier.goldReward)}💰 +${tier.killingIntentReward}⚔️杀气")

            // 掉落奖励
            if (tier.boneTier in 0..24) {
                val bt = tier.boneTier
                val emptySlot = BoneType.entries.indices.firstOrNull { !s.soulBones.containsKey(it) }
                if (emptySlot != null) {
                    val currentBone = s.soulBones[emptySlot]
                    if (currentBone == null || bt > currentBone.combinedTier) {
                        val affixes = SoulBoneGenerator.generateRandomAffix(bt)
                        s.soulBones[emptySlot] = SoulBoneInstance(bt, affixes)
                        addLog("🦴 获得${BoneRarity.fullDisplayName(bt)}魂骨")
                    }
                }
            }
            if (tier.ringTier in 0..24) {
                val qt = tier.ringTier
                val ringSlot = SoulRingSlots.all.firstOrNull { sl ->
                    sl.index !in s.soulRings && sl.index < RealmData.maxSoulRings(s.level)
                }
                if (ringSlot != null) {
                    val affixes = SoulRingGenerator.generateRandomAffixes(qt)
                    val (y, q) = SoulRingGenerator.splitTier(qt)
                    val pct = SoulRingSystem.randomPercentage()
                    s.soulRings[ringSlot.index] = SoulRingInstance(y, q, pct, affixes, null)
                    addLog("💍 获得${RingQuality.fullDisplayName(qt)}")
                }
            }
            checkAchievements()
        } else {
            addLog("💀 副本挑战失败！${tier.bossName}太过强大")
            s.totalBattleLosses++
        }
        s.save()
        notifyUI()
        return result.won
    }

    /** V3: 每日副本剩余时间 */
    fun dungeonResetTimeRemaining(): Long {
        val now = System.currentTimeMillis()
        val lastDay = state.lastDungeonTime / DungeonData.DAILY_RESET_MS
        val today = now / DungeonData.DAILY_RESET_MS
        if (lastDay >= today) {
            // 已打，返回明天重置时间
            return (today + 1) * DungeonData.DAILY_RESET_MS - now
        }
        return 0  // 可进入
    }

    /** 生成塔怪物 */
    private fun generateTowerMonster(floor: Int): Monster {
        val seg = TowerData.getSegment(floor)
        val isBoss = TowerData.isBossFloor(floor)
        val hpMult = TowerData.floorHpMultiplier(floor)
        val atkMult = TowerData.floorAtkMultiplier(floor)

        val name = if (isBoss) seg.bossName
                   else "${seg.name}守卫"

        val basePower = TowerData.monsterPower(floor)
        val hp = (basePower * hpMult).toLong()
        val atk = (basePower * atkMult / 3.0).coerceAtLeast(1.0).toInt()
        val matk = (atk * 0.8).toInt()
        val pdef = (atk * 0.3).toInt()
        val mdef = (atk * 0.3).toInt()

        // Boss额外词缀
        val affixes = mutableListOf<MonsterAffix>()
        if (isBoss) {
            affixes.add(MonsterAffix.BOSS)
            if (floor >= 70) affixes.add(MonsterAffix.REGENERATING)
            if (floor >= 50) affixes.add(MonsterAffix.BERSERK)
        } else if (floor >= 30) {
            if (kotlin.random.Random.nextFloat() < 0.3f) affixes.add(MonsterAffix.SWIFT)
        }

        return Monster(
            name = name,
            affixes = affixes,
            maxHp = hp,
            hp = hp,
            atk = atk,
            matk = matk,
            critRate = 50,
            critDmg = 200,
            pdef = pdef,
            mdef = mdef,
            expReward = 100,
            goldReward = TowerData.normalReward(floor),
            canDropEquip = isBoss
        )
    }

    // ======== 天赋树系统 ========

    /** 消耗1点天赋点升级指定分支 */
    fun doSpendTalentPoint(branch: TalentBranch): Boolean {
        val s = state
        if (s.talentPoints <= 0) {
            addLog("没有可用的天赋点数！转生可获得天赋点"); notifyUI(); return false
        }
        val currentLevel = s.getTalentLevel(branch)
        if (currentLevel >= branch.maxLevel) {
            addLog("${branch.displayName} 已达最高等级 ${branch.maxLevel}"); notifyUI(); return false
        }
        s.talentPoints--
        s.setTalentLevel(branch, currentLevel + 1)
        addLog("🌳 ${branch.icon} ${branch.displayName} 提升至 Lv.${currentLevel + 1}/${branch.maxLevel}！")
        addLog("  效果: ${TalentBranch.effectDescription(branch, currentLevel + 1)}")
        // 财富之道Lv3：获得1次免费扩容
        if (branch == TalentBranch.WEALTH && currentLevel + 1 == 3) {
            s.freeExpandRemaining++
            addLog("🎁 获得1次免费背包扩容机会！（剩余${s.freeExpandRemaining}次）")
        }
        notifyUI()
        // 检查成就
        checkAchievements()
        return true
    }

    /** 获取已消耗的天赋点数总和 */
    fun getSpentTalentPoints(): Int = TalentBranch.entries.sumOf { state.getTalentLevel(it) }

    fun doPrestige(): Boolean {
        if (state.level < RealmData.PRESTIGE_MIN_LEVEL) {
            addLog("需要达到 ${RealmData.PRESTIGE_MIN_LEVEL} 级才能进行神位传承"); notifyUI(); return false
        }
        val s = state
        val wealthLv = s.getTalentLevel(TalentBranch.WEALTH)
        val soulMasterLv = s.getTalentLevel(TalentBranch.SOUL_MASTER)
        val warGodLv = s.getTalentLevel(TalentBranch.WAR_GOD)
        val divineLv = s.getTalentLevel(TalentBranch.DIVINE)

        s.prestigeCount++; s.level = 1

        // ====== 清理超出槽位的已装备魂骨/魂环 ======
        val maxRingsAfter = RealmData.maxSoulRings(s.level)
        s.soulRings.keys.toList().filter { it >= maxRingsAfter }.forEach { s.soulRings.remove(it) }
        val maxBonesAfter = RealmData.maxSoulBones(s.level)
        s.soulBones.keys.toList().filter { it >= maxBonesAfter }.forEach { s.soulBones.remove(it) }

        // ====== 保留前快照(用于日志) ======
        val oldGold = s.gold
        val oldMapId = s.currentMapId
        val oldTower = s.towerFloor
        val oldTowerKills = s.towerBossKills

        // ====== 1. 🪙 财富之道 → 金币保留率 ======
        val goldRetention = when (wealthLv) { 0 -> 0.0; 1 -> 0.10; 2 -> 0.25; 3 -> 0.50; else -> 0.0 }

        s.gold = (s.gold * goldRetention).toLong()

        // ====== 2. 💠 魂师之道 → 背包/武魂传承 + 流派重选 ======
        when (soulMasterLv) {
            0 -> {
                s.martialSoul = null
                s.chosenSchool = null  // 武魂改革：未点魂师之道需重新选流派
                s.backpackRings.clear()
                s.backpackBones.clear()
                s.backpackSoulCores.clear()
                s.equippedSoulCores.replaceAll { _, _ -> null }
            }
            1 -> {
                s.martialSoul = null
                // chosenSchool 保留，转生后可重选流派
                s.backpackBones.clear()
                s.backpackSoulCores.clear()
                s.equippedSoulCores.replaceAll { _, _ -> null }
                // 背包魂环保留
            }
            2 -> {
                s.martialSoul = null
                // chosenSchool 保留
                s.backpackSoulCores.clear()
                s.equippedSoulCores.replaceAll { _, _ -> null }
                // 背包魂环+魂骨保留
            }
            3 -> { /* 全部保留 */ }
        }

        // ====== 3. ⚔️ 战神之道 → 地图/塔进度保留 ======
        when (warGodLv) {
            0 -> { s.currentMapId = 0; s.towerFloor = 0; s.towerBossKills = 0 }
            1 -> { s.towerFloor = 0; s.towerBossKills = 0 }
            2 -> { s.towerBossKills = 0 /* 保留塔层数但Boss击杀重置 */ }
            3 -> { /* 全部保留 */ }
        }

        // ====== 4. 🛡️ 神祇之道 → 背包容量保留率 ======
        // 商店等级系统已移除，神祇之道改为战斗减伤
        // 保留原注释以说明该天赋在战斗中生效

        // ====== V3: 塔内状态与每日副本重置 ======
        s.towerInRun = false
        s.towerTempHp = 0
        s.dungeonTierCompleted = -1

        // ====== 始终重置的字段 ======
        s.soulPower = 0
        s.currentStage = 1
        s.currentHp = calcMaxHp()
        s.inBattle = false
        s.currentMonster = generateCurrentMonster()
        s.battleSoulPower = 100  // 重置战斗魂力
        s.activeSkillCooldowns.clear()  // 重置所有技能冷却
        s.skillCooldown = 0  // 兼容旧冷却字段

        // ====== 获得天赋点 ======
        s.talentPoints++

        // ====== 转生简报 ======
        addLog("=== 神位传承 第${s.prestigeCount}世 ===")
        addLog("🏅 天赋 +1 (共${s.talentPoints}点)")
        when (wealthLv) {
            0 -> addLog("🪙 财富Lv0: 金币全部清空")
            1 -> addLog("🪙 财富Lv1: 保留10%金币 (${formatNum(s.gold)})")
            2 -> addLog("🪙 财富Lv2: 保留25%金币 (${formatNum(s.gold)})")
            3 -> addLog("🪙 财富Lv3: 保留50%金币 (${formatNum(s.gold)})")
        }
        when (soulMasterLv) {
            0 -> addLog("💠 魂师Lv0: 背包/武魂全部清空")
            1 -> addLog("💠 魂师Lv1: 保留魂环×${s.backpackRings.size}, 魂骨/武魂/魂核已清空")
            2 -> addLog("💠 魂师Lv2: 保留魂环×${s.backpackRings.size} 魂骨×${s.backpackBones.size}, 武魂/魂核已清空")
            3 -> addLog("💠 魂师Lv3: 全部保留(魂环×${s.backpackRings.size} 魂骨×${s.backpackBones.size} 武魂:${s.martialSoul?.name ?: "无"})")
        }
        when (warGodLv) {
            0 -> addLog("⚔️ 战神Lv0: 杀戮之都/地图全部重置")
            1 -> addLog("⚔️ 战神Lv1: 保留地图ID(${if (s.currentMapId > 0) MapData.getMap(s.currentMapId)?.name ?: "有" else "无"}), 塔层重置")
            2 -> addLog("⚔️ 战神Lv2: 保留地图+${s.towerFloor}层塔, Boss击杀重置")
            3 -> addLog("⚔️ 战神Lv3: 全部保留(塔${s.towerFloor}层 Boss击杀${s.towerBossKills})")
        }
        addLog("🛡️ 神祇Lv${divineLv}: 战斗中${if (divineLv >= 3) "减伤5%" else "HP加成"}")
        addLog("📈 永久: 全属性 ×${String.format("%.1f", prestigeMultiplier())}")
        val newFeatures = checkForNewFeatures()
        if (newFeatures.isNotEmpty()) {
            pendingFeatureUnlocks.addAll(newFeatures)
            val names = newFeatures.joinToString("、") { "${it.icon} ${it.displayName}" }
            pushNotifyEvent(NotifyType.FEATURE_UNLOCK, "🎉 新功能已解锁！", names)
        }
        checkAchievements(); notifyUI(); return true
    }

    fun toggleAutoBattle(): Boolean {
        state.autoBattle = !state.autoBattle
        if (state.autoBattle && state.currentHp <= 0) {
            state.autoBattle = false
            addLog("已阵亡，无法开启自动战斗")
        } else {
            addLog(if (state.autoBattle) "🟢 自动战斗已开启" else "🔴 自动战斗已关闭")
        }
        notifyUI(); return state.autoBattle
    }

    // ======== Tick ========

    fun tick() {
        // 新tick，清除属性缓存
        tickCachedAttr = null
        
        tickCount++
        if (tickCount >= 60) {
            tickCount = 0
            state.save()
        }
        autoBreakthroughTask()

        val maxHp = calcMaxHp()

        // HP自动恢复（战斗中不恢复，每tick 10%）
        if (!state.inBattle && state.currentHp < maxHp) {
            val regen = (maxHp * 0.10).toLong().coerceAtLeast(1L)
            state.currentHp = (state.currentHp + regen).coerceAtMost(maxHp)
        }

        // 再生之核：每秒额外HP恢复（战斗中也能生效）
        for (core in state.equippedSoulCores.values) {
            if (core != null && core.passiveSkill.type == PassiveSkillType.REGEN && state.currentHp < maxHp) {
                val regenPct = core.effectiveValue
                val heal = (maxHp * regenPct / 100).coerceAtLeast(1L)
                state.currentHp = (state.currentHp + heal).coerceAtMost(maxHp)
            }
        }

        // 自动战斗：战斗中持续执行；非战斗时满血才开启
        // 使用批量UI模式，只在整个tick结束时刷一次UI
        if (state.autoBattle && state.currentHp > 0 && (state.inBattle || state.currentHp >= maxHp)) {
            batchNotify = true
            autoBattleTask()
            batchNotify = false
            // 如果有延期的notifyUI，现在执行
            if (batchNotifyPending) {
                batchNotifyPending = false
                notifyUI()
            }
        }
        notifyUI()
    }

    private fun autoBreakthroughTask() {
        if (!state.autoBreakthrough) return  // 关闭自动突破时跳过
        if (state.isLevelCapped) return  // 卡级中跳过自动突破
        // 在门槛级(10/20/30...)停止自动突破，让玩家可开启卡级或手动突破
        if (state.level % 10 == 0) return
        // 魂环卡级：即将解锁新槽位时，必须先填满当前槽位
        val currentSlots = RealmData.maxSoulRings(state.level)
        val nextSlots = RealmData.maxSoulRings(state.level + 1)
        if (nextSlots > currentSlots && state.soulRings.size < currentSlots) return
        val cost = RealmData.breakthroughCost(state.level)
        if (state.soulPower >= cost) {
            state.soulPower -= cost; state.level++
            state.currentHp = calcMaxHp()
            addLog("自动突破 → ${RealmData.name(state.level)} (${state.level}级)")
            if (state.level % 10 == 0) {
                addLog("🌟 已到达境界门槛 Lv.${state.level}，自动突破暂停")
                addLog("   💡 可在修炼页开启【卡级修炼】积累超额魂力")
            }
            val newFeatures = checkForNewFeatures()
            if (newFeatures.isNotEmpty()) {
                pendingFeatureUnlocks.addAll(newFeatures)
                val names = newFeatures.joinToString("、") { "${it.icon} ${it.displayName}" }
                pushNotifyEvent(NotifyType.FEATURE_UNLOCK, "🎉 新功能已解锁！", names)
            }
            checkAchievements()
            state.save()
        }
    }

    private fun autoBattleTask() {
        // 固定2倍速执行（每tick执行2回合）
        doBattle()
        if (state.autoBattle && state.currentHp > 0 && state.inBattle) {
            doBattle()
        }
    }

    fun calculateOfflineReward(): Long {
        if (state.lastExitTime == 0L) return 0
        val now = System.currentTimeMillis()
        var sec = (now - state.lastExitTime) / 1000
        if (sec > state.maxOfflineSec) sec = state.maxOfflineSec
        if (sec < 60) return 0
        // 离线修炼：每5秒修炼一次，获得魂力
        val trains = sec / 5
        val soulGain = trains * (state.level * 28L * prestigeMultiplier()).toLong()
        if (state.isLevelCapped) {
            state.excessSoulPower += soulGain
        } else {
            state.soulPower += soulGain
        }
        state.currentHp = calcMaxHp()
        return soulGain
    }

    fun checkAchievements(): List<AchievementDef> {
        val snapshot = state.getSnapshot()
        val newlyUnlocked = mutableListOf<AchievementDef>()
        AchievementDefs.all.forEach { ach ->
            if (!state.unlockedAchievements.contains(ach.id) && ach.condition(snapshot)) {
                state.unlockedAchievements.add(ach.id); newlyUnlocked.add(ach)
                addLog("🏆 成就解锁：${ach.name} (${ach.rewards.description()})")
            }
        }
        return newlyUnlocked
    }

    fun formatNum(n: Long): String = when {
        n >= 1_000_000_000_000L -> "${String.format("%.1f", n / 1_000_000_000_000.0)}兆"
        n >= 100_000_000L -> "${String.format("%.1f", n / 100_000_000.0)}亿"
        n >= 10_000L -> "${String.format("%.1f", n / 10_000.0)}万"
        else -> n.toString()
    }

    fun formatNum(n: Int): String = formatNum(n.toLong())

    // ======== 新手引导系统 ========

    /** 引导步骤定义 */
    enum class TutorialStep(val step: Int, val title: String, val desc: String) {
        WELCOME(1, "欢迎来到斗罗大陆！", "选择你的修炼流派，决定武魂方向"),
        CULTIVATE(2, "修炼获取魂力", "点击【修炼】按钮积攒魂力"),
        FIGHT(3, "挑战怪物", "点击【⚔️战斗】与怪物战斗"),
        SOUL_RING(4, "吸收魂环", "在【装备】页面吸收魂环增强实力"),
        ADVENTURE(5, "探索地图", "前往【冒险】页面进入地图"),
        DONE(7, "引导完成", "你已准备就绪！");

        companion object {
            fun fromStep(s: Int): TutorialStep? = entries.find { it.step == s }
        }
    }

    fun isTutorialActive(): Boolean = state.tutorialStep in 1..5
    fun isTutorialCompleted(): Boolean = state.tutorialStep >= 7

    fun advanceTutorial() {
        if (!isTutorialActive()) return
        val next = state.tutorialStep + 1
        state.tutorialStep = if (next >= 6) {
            addLog("🌟 新手引导完成！获得 500 金魂币奖励！")
            state.gold += 500
            7
        } else {
            next
        }
        state.save()
        notifyUI()
    }

    fun skipTutorial() {
        state.tutorialStep = 7
        state.save()
        notifyUI()
    }
}
