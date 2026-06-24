package com.example.garygame.engine

import android.content.Context
import org.json.JSONObject

/**
 * 《斗罗大陆·放置传说 V5》全局数值统筹配置库
 * 
 * 核心设计理念：
 * 1. 纯静态预配置，无实时数据采集
 * 2. 加速版生命周期：6天首次转生、140天13转
 * 3. 所有子系统参数引用总控系数，禁止单独修改零散数值
 * 4. 本地离线闭环，不联网、不上传
 */
object GlobalValueConfig {
    
    // ==================== 基础生命周期参数 ====================
    object Lifecycle {
        /** 首次转生目标天数（加速版：6天） */
        const val FIRST_PRESTIGE_DAYS = 6
        
        /** 首次转生等级门槛（折中方案：90级） */
        const val FIRST_PRESTIGE_LEVEL = 90
        
        /** 转生固定等级门槛 */
        const val PRESTIGE_LEVEL_THRESHOLD = 90
        
        /** 每转全属性永久加成（10%） */
        const val PRESTIGE_STAT_BONUS = 0.10f
        
        /** 经验产出倍率（按转生次数梯度下调） */
        val EXP_MULTIPLIER_BY_PRESTIGE = mapOf(
            0 to 1.2f,    // 新手期×1.2（从1.5下调，避免过快）
            1 to 1.1f,    // 1转后×1.1
            2 to 1.0f,    // 2转后×1.0
            3 to 0.9f,    // 3转后×0.9
            4 to 0.8f,    // 4转后×0.8
            5 to 0.75f,   // 5转后×0.75
            6 to 0.7f,
            7 to 0.68f,
            8 to 0.65f,
            9 to 0.63f,
            10 to 0.6f,   // 10转后稳定在×0.6
            11 to 0.58f,
            12 to 0.55f,
            13 to 0.52f   // 13转后×0.52
        )
        
        /** 新手保护期经验加成（前3天额外+30%） */
        const val NEWBIE_EXP_BOOST_DAYS = 3
        const val NEWBIE_EXP_BOOST_MULT = 1.3f
        
        /** 每日活跃度奖励（在线>2小时触发） */
        const val DAILY_ACTIVE_BONUS_MINUTES = 120
        const val DAILY_ACTIVE_EXP_MULT = 1.2f      // 经验+20%
        const val DAILY_ACTIVE_GOLD_MULT = 1.15f    // 金币+15%
        
        /** 预期生命周期（用于仿真验证） */
        const val EXPECTED_5_PRESTIGE_DAYS = 35     // 5转预计35天
        const val EXPECTED_10_PRESTIGE_DAYS = 75    // 10转预计75天
        const val EXPECTED_13_PRESTIGE_DAYS = 140   // 13转预计140天
    }
    
    // ==================== 境界系统系数 ====================
    object Realm {
        /** 突破消耗公式：soulPower = BASE × level^EXPONENT */
        const val SOUL_POWER_BASE = 120.0           // 从150降至120（加速升级）
        const val SOUL_POWER_EXPONENT = 1.55        // 从1.65降至1.55（曲线更平缓）
        
        /** 每级基础属性成长 */
        const val HP_PER_LEVEL = 50L
        const val ATK_PER_LEVEL = 10
        const val MATK_PER_LEVEL = 8
        const val PDEF_PER_LEVEL = 5
        const val MDEF_PER_LEVEL = 4
        
        /** 境界倍率（每完成1境+25%） */
        const val REALM_BONUS_PER_TIER = 0.25f
        
        /** 魂环槽位解锁等级 */
        fun soulRingSlotUnlockLevel(slotIndex: Int): Int = slotIndex * 20 + 1
        
        /** 魂骨槽位解锁等级 */
        fun soulBoneSlotUnlockLevel(slotIndex: Int): Int = slotIndex * 30 + 1
        
        /** 最大魂环数 */
        fun maxSoulRings(level: Int): Int = ((level - 1) / 20 + 1).coerceIn(1, 9)
        
        /** 最大魂骨数 */
        fun maxSoulBones(level: Int): Int = ((level - 1) / 30 + 1).coerceIn(1, 6)
    }
    
    // ==================== 推图节奏控制 ====================
    object Progression {
        /** 前3天顺滑期：怪物属性系数（降低难度） */
        const val EARLY_GAME_MONSTER_MULT = 0.90f
        
        /** 第6-7天难度抬升（适度挑战） */
        const val MID_GAME_MONSTER_MULT = 1.10f
        
        /** 动态难度上限（防止无限膨胀） */
        const val MAX_MONSTER_MULT_CAP = 50.0f
        
        /** 分段静态膨胀系数（优化版，更平滑） */
        val MAP_DIFFICULTY_CURVE = mapOf(
            0 to 1.0f,    // 圣魂村
            1 to 1.15f,   // 诺丁城外
            2 to 1.35f,   // 星斗外围
            3 to 1.6f,    // 落日森林
            4 to 2.0f,    // 极北之地
            5 to 2.5f,    // 海神岛
            6 to 3.2f,    // 杀戮之都外域
            7 to 4.0f     // 神界废墟
        )
        
        /** 自动跳转开关（默认开启） */
        const val AUTO_ADVANCE_MAP_DEFAULT = true
    }
    
    // ==================== 资源消耗渠道 ====================
    object ResourceSink {
        /** 金币自动消耗优先级 */
        const val AUTO_ABSORB_RING_PRIORITY = 1     // 魂环吸收
        const val AUTO_ENHANCE_BONE_PRIORITY = 2    // 魂骨强化
        const val AUTO_SYNTH_CORE_PRIORITY = 3      // 魂核合成
        
        /** 魂骨强化消耗公式：BASE × (L+1)^EXPONENT（优化版：指数从2.8降至2.5） */
        const val BONE_ENHANCE_BASE_COST = 5000L
        const val BONE_ENHANCE_EXPONENT = 2.5f      // 从2.8降至2.5（降低43%总消耗）
        const val BONE_ENHANCE_MAX_LEVEL = 10
        const val BONE_ENHANCE_STAT_BONUS = 0.05f   // 每级+5%
        
        /** 魂环吸收费用基数调整（加速版降低20%） */
        const val RING_ABSORB_COST_MULT = 0.8f
    }
    
    // ==================== 掉落与产出系数 ====================
    object DropRate {
        /** 基础掉落倍率（优化版：+30%金币掉落） */
        const val BASE_GOLD_DROP_MULT = 1.3f      // 从1.0提升至1.3
        const val BASE_EXP_DROP_MULT = 1.0f
        
        /** 魂环/魂骨掉落几率 */
        const val BASE_RING_DROP_CHANCE = 0.15f     // 15%掉魂环
        const val BASE_BONE_DROP_CHANCE = 0.08f     // 8%掉魂骨
        
        /** 高阶资源锁死阈值（防止过快毕业） */
        const val HIGH_TIER_RING_LOCK_PRESTIGE = 3  // 3转前限制百万年魂环掉落
        const val HIGH_TIER_BONE_LOCK_PRESTIGE = 2  // 2转前限制十万年魂骨掉落
        
        /** Boss掉落加成 */
        const val BOSS_DROP_MULT = 3.0f             // Boss掉落×3
        const val BOSS_GOLD_MULT = 5.0f             // Boss金币×5
    }
    
    // ==================== 战斗系统系数 ====================
    object Combat {
        /** 自动战斗速度选项 */
        val BATTLE_SPEED_OPTIONS = listOf(1, 2)     // 1倍速、2倍速
        const val DEFAULT_BATTLE_SPEED = 2          // 默认2倍速
        
        /** 战斗魂力恢复 */
        const val BATTLE_SOUL_POWER_INITIAL = 100
        const val BATTLE_SOUL_POWER_REGEN_PER_TURN = 10
        
        /** HP自动恢复（非战斗中，每秒25%） */
        const val HP_REGEN_RATE_OUT_OF_BATTLE = 0.25f
        
        /** 死亡惩罚（仅进度回退，不扣资源） */
        const val DEATH_PENALTY_MAP_RESET = true    // 退回第1关
        const val DEATH_PENALTY_HP_RESTORE = true   // HP回满
        const val DEATH_PENALTY_GOLD_LOSS = 0.0f    // 金币损失0%
    }
    
    // ==================== 留存机制配置 ====================
    object Retention {
        /** 连续登录奖励 */
        val CONSECUTIVE_LOGIN_REWARDS = mapOf(
            1 to "金币×5000",
            3 to "千年魂环×1",
            7 to "史诗武魂觉醒券×1",
            14 to "十万年魂骨×1",
            30 to "神话魂核自选箱×1"
        )
        
        /** 每日随机事件概率 */
        const val DAILY_EVENT_CHANCE = 0.3f         // 30%几率触发
        
        /** 离线收益效率 */
        const val OFFLINE_GAIN_EFFICIENCY = 0.8f    // 离线80%效率
        const val MAX_OFFLINE_HOURS = 12            // 最大离线12小时
        
        /** 成就可见性阈值（完成70%时高亮提示） */
        const val ACHIEVEMENT_VISIBILITY_THRESHOLD = 0.7f
    }
    
    // ==================== 三套预设玩家模板 ====================
    object PlayerTemplates {
        
        data class PlayerProfile(
            val name: String,
            val dailyOnlineMinutes: Int,        // 每日在线时长
            val autoBattleEfficiency: Float,    // 自动战斗效率(0-1)
            val resourceUtilization: Float,     // 资源利用率(0-1)
            val skipLimitedShop: Boolean,       // 是否错过限时商店
            val manualOptimization: Boolean     // 是否手动优化操作
        )
        
        /** 零氪慢速 */
        val ZERO_SPEND = PlayerProfile(
            name = "零氪慢速",
            dailyOnlineMinutes = 30,
            autoBattleEfficiency = 0.6f,
            resourceUtilization = 0.6f,
            skipLimitedShop = true,
            manualOptimization = false
        )
        
        /** 均衡养成（标准锚点） */
        val BALANCED = PlayerProfile(
            name = "均衡养成",
            dailyOnlineMinutes = 120,
            autoBattleEfficiency = 0.8f,
            resourceUtilization = 0.8f,
            skipLimitedShop = false,
            manualOptimization = true
        )
        
        /** 资源充裕 */
        val WHALE = PlayerProfile(
            name = "资源充裕",
            dailyOnlineMinutes = 240,
            autoBattleEfficiency = 0.95f,
            resourceUtilization = 0.95f,
            skipLimitedShop = false,
            manualOptimization = true
        )
    }
    
    // ==================== 活动临时倍率（自动回滚）====================
    object EventMultipliers {
        data class TemporaryBoost(
            val multiplier: Float,
            val startTime: Long,
            val durationMs: Long
        ) {
            fun isActive(): Boolean = System.currentTimeMillis() < startTime + durationMs
            fun getCurrentMultiplier(): Float = if (isActive()) multiplier else 1.0f
        }
        
        var currentEvent: TemporaryBoost? = null
        
        /** 激活临时活动倍率 */
        fun activateEvent(mult: Float, durationDays: Int) {
            currentEvent = TemporaryBoost(
                multiplier = mult,
                startTime = System.currentTimeMillis(),
                durationMs = durationDays * 24 * 3600 * 1000L
            )
        }
        
        /** 获取当前活动倍率 */
        fun getCurrentEventMult(): Float = currentEvent?.getCurrentMultiplier() ?: 1.0f
    }
    
    // ==================== 仿真结果存储 ====================
    object SimulationResults {
        var lastSimulationDate: Long = 0L
        var zeroSpendProgress: Map<Int, Pair<Int, Int>> = emptyMap()  // day -> (level, prestige)
        var balancedProgress: Map<Int, Pair<Int, Int>> = emptyMap()
        var whaleProgress: Map<Int, Pair<Int, Int>> = emptyMap()
        
        /** 标准节奏锚点（基于均衡模板） */
        val STANDARD_ANCHORS = mapOf(
            6 to 1,     // Day 6 → 1转
            35 to 5,    // Day 35 → 5转
            75 to 10,   // Day 75 → 10转
            140 to 13   // Day 140 → 13转
        )
    }
    
    // ==================== 配置持久化 ====================
    private const val PREFS_NAME = "GlobalValueConfig"
    private const val KEY_CONFIG_JSON = "config_json"
    
    /** 保存配置到SharedPreferences */
    fun saveToPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("first_prestige_days", Lifecycle.FIRST_PRESTIGE_DAYS)
            put("first_prestige_level", Lifecycle.FIRST_PRESTIGE_LEVEL)
            put("soul_power_base", Realm.SOUL_POWER_BASE)
            put("soul_power_exponent", Realm.SOUL_POWER_EXPONENT)
            put("early_game_monster_mult", Progression.EARLY_GAME_MONSTER_MULT)
            put("mid_game_monster_mult", Progression.MID_GAME_MONSTER_MULT)
            put("base_gold_drop_mult", DropRate.BASE_GOLD_DROP_MULT)
            put("offline_gain_efficiency", Retention.OFFLINE_GAIN_EFFICIENCY)
        }
        prefs.edit().putString(KEY_CONFIG_JSON, json.toString()).apply()
    }
    
    /** 从SharedPreferences加载配置 */
    fun loadFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CONFIG_JSON, null)
        if (jsonStr != null) {
            val json = JSONObject(jsonStr)
            // 可在运行时覆盖默认值（目前仅读取，暂不动态修改）
        }
    }
    
    /** 重置为默认配置 */
    fun resetToDefaults(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CONFIG_JSON).apply()
    }
    
    // ==================== 辅助方法 ====================
    
    /** 获取指定转生次数的经验倍率 */
    fun getExpMultiplier(prestige: Int): Float {
        return Lifecycle.EXP_MULTIPLIER_BY_PRESTIGE.getOrElse(prestige) { 0.58f }
    }
    
    /** 计算突破所需魂力 */
    fun getBreakthroughCost(level: Int): Long {
        return (Realm.SOUL_POWER_BASE * Math.pow(level.toDouble(), Realm.SOUL_POWER_EXPONENT)).toLong()
    }
    
    /** 获取地图难度系数 */
    fun getMapDifficultyMultiplier(mapId: Int): Float {
        return Progression.MAP_DIFFICULTY_CURVE.getOrElse(mapId) { 1.0f }
    }
    
    /** 检查是否在新手保护期 */
    fun isInNewbieProtection(day: Int): Boolean {
        return day <= Lifecycle.NEWBIE_EXP_BOOST_DAYS
    }
    
    /** 获取新手保护期经验倍率 */
    fun getNewbieExpMult(day: Int): Float {
        return if (isInNewbieProtection(day)) Lifecycle.NEWBIE_EXP_BOOST_MULT else 1.0f
    }
}
