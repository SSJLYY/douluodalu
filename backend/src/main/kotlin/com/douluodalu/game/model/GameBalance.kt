package com.douluodalu.game.model

/**
 * 游戏数值平衡常量：GameService 中的魔法数字统一收敛到这里。
 * 与 application.yml 中 game.offline.* 配置保持一致（当前代码以本对象为准）。
 */
object GameBalance {

    // ======== 修炼 ========
    const val CULTIVATE_BASE_GAIN = 10L
    const val CULTIVATE_LEVEL_GAIN_FACTOR = 2L
    const val CULTIVATE_RANDOM_DIVISOR = 4L

    // ======== 战斗：怪物生成 ========
    const val MONSTER_HP_BASE = 200L
    const val MONSTER_HP_PER_MAP = 300L
    const val MONSTER_ATK_BASE = 15
    const val MONSTER_ATK_PER_MAP = 25
    const val MONSTER_STAGE_GROWTH = 0.12

    // ======== 战斗：玩家属性与结算 ========
    const val PLAYER_ATK_BASE = 50L
    const val PLAYER_ATK_PER_LEVEL = 10L
    const val MAX_BATTLE_ROUNDS = 30

    // ======== 装备战力（P7 修复，任务#20）========
    // 公式精神对齐 shared GameEngine.calcAttributes()：
    //   魂环 = 年份档位 × 品质倍率 × 成熟度倍率(1 + percentage/1000，对应 shared 的 1.1~2.0 区间)
    //   魂骨 = 年份档位 × 品质倍率 × (1 + 强化等级 × 0.10)（shared 的 enhanceEffect 简化版）
    //   魂核 = 按基础攻击的百分比附加（shared「力量」魂核 atk += atk*value/100 的加权版）
    // 注：魂环年份负荷校验已由 RingLoadCalculator 实现（任务#21，公式逐行移植 shared SoulRingSystem）。
    val EQUIP_QUALITY_MULT = listOf(1.0, 1.2, 1.5, 1.8, 2.2) // 取自《魂环负荷与年份对应关系.md》qualityMult
    const val RING_ATK_WEIGHT = 20L        // 单魂环攻击基础值（再乘年份/品质/成熟度倍率）
    const val RING_HP_WEIGHT = 80L         // 单魂环生命基础值
    const val BONE_ATK_WEIGHT = 40L        // 单魂骨攻击基础值
    const val BONE_HP_WEIGHT = 150L        // 单魂骨生命基础值
    const val BONE_ENHANCE_PER_LEVEL = 0.10
    const val CORE_ATK_PCT_WEIGHT = 0.30   // 魂核：coreValue 每 100 点折算为基础攻击的 30%（再乘稀有度倍率）
    const val CORE_ATK_PCT_CAP = 0.50      // 单魂核攻击加成上限：基础攻击的 50%（防高等级 coreValue 线性膨胀失控）
    const val POWER_BASE = 50L             // 战斗力常数项
    const val POWER_LEVEL_WEIGHT = 5L      // 战斗力：每级贡献
    const val POWER_HP_DIVISOR = 10.0      // 战斗力：每 10 点生命折算 1 点攻击
    const val WIN_EXP_BASE = 50L
    const val WIN_EXP_PER_MAP = 30L
    const val WIN_EXP_PER_STAGE = 10L
    const val WIN_GOLD_BASE = 30L
    const val WIN_GOLD_PER_MAP = 20L
    const val WIN_GOLD_PER_STAGE = 8L
    const val STAGES_PER_MAP = 15
    const val MAX_MAP_ID = 7

    // ======== 掉落 ========
    const val BASE_DROP_CHANCE = 0.15
    const val DROP_CHANCE_PER_MAP = 0.02
    const val DROP_CHANCE_PER_STAGE = 0.005
    const val BOSS_EXTRA_DROP_CHANCE = 0.4

    // ======== 魂环负荷（任务#21/#22）========
    // 吸收容量 = 根骨 × RING_CAPACITY_ROOT_MULT（RingLoadCalculator.absorptionCapacity）。
    // 任务#22 负荷回路 90 天仿真（LongRunSimulationTest 专项）：shared 原始值 6 下第 45 天起
    // 容量利用率恒 96~99%、约 80 次/日拒装、高档位环 100% 死掉落（背包永久积压）→ 恒卡形态。
    // 6 → 24 后：利用率全程 ~70-82% 带内、30 天后死掉落率 0%、换装 treadmil 正常（10 种子复核见报告）。
    const val RING_CAPACITY_ROOT_MULT = 24L
    // 魂塔掉落魂环的年份档位上限（rollBackpackDrop）：塔满层后 towerLevel=300 使
    // min(4, towerLevel/12+rand(2)) 恒为 4 档、percentage 饱和 999 → 每张塔魂环负荷 ~9.99M，
    // 在 90 天容量规模（~1e6）下是 100% 不可装死掉落。封顶 2 档（负荷 ≤99.9k）后塔环重新可装，
    // 3~4 档保留为推图终局(6-7 号图)/后续轮回内容的专属追求。
    const val TOWER_RING_DROP_YEAR_CAP = 2

    // ======== 离线收益（P1 修复：语义由「每秒」改为「每小时」）========
    // 换算依据（《数值仿真报告-90天.md》实测）：中活跃玩家日均"主动玩法"收入约 7,000 金币/日
    // （第 9~90 天区间 5.6k~8.4k），设计意图「离线 12h ≈ 主动游玩 30~60 分钟产出」≈ 主动日收入的
    // 30%~60% ≈ 2,100~4,200 金/12h ≈ 175~350 金/小时。修复后 (10 + 2L)×0.8 金/小时在 L≈120~200
    // 档正好落在该区间；旧「每秒」语义下 12h=43,200 秒直接日入数千万，主动玩法彻底失去意义。
    const val OFFLINE_MAX_HOURS = 12L
    const val OFFLINE_MAX_SECONDS = OFFLINE_MAX_HOURS * 3600L
    const val OFFLINE_EFFICIENCY = 0.8
    const val OFFLINE_GOLD_BASE = 10L          // 每小时基础金币
    const val OFFLINE_GOLD_PER_LEVEL = 2L      // 每小时每级追加金币
    const val OFFLINE_EXP_BASE = 5L            // 每小时基础魂力
    const val OFFLINE_EXP_PER_LEVEL = 1L       // 每小时每级追加魂力
    const val OFFLINE_SECONDS_PER_BATTLE_WIN = 5L  // P6（胜场折算通胀）不在本次修复范围，维持原值

    // ======== 杀戮之都（爬塔） ========
    val TOWER_MONSTERS = listOf("血色统领", "暗影猎手", "地狱魔蛛", "杀戮守卫")
    const val TOWER_BASE_LOSE_CHANCE = 0.25
    // P2 修复：0.02 → 0.005。旧值 floor≥38 时失败率 ≥1.01 恒败（数学上不可能获胜）；
    // 新值 floor=100 失败率 0.75（基础胜率 0.25），100 层理论可登顶。
    const val TOWER_FLOOR_DIFFICULTY = 0.005
    const val TOWER_MAX_FLOOR = 100
    const val TOWER_LEVEL_PER_FLOOR = 3
    const val TOWER_GOLD_BASE = 30L
    const val TOWER_GOLD_PER_LEVEL = 12L
    const val TOWER_EXP_BASE = 25L
    const val TOWER_EXP_PER_LEVEL = 10L
    const val TOWER_BOSS_COIN_CHANCE = 0.6
    const val TOWER_DROP_CHANCE = 0.65
    const val TOWER_KILLING_PER_FLOORS = 10

    // ======== 爬塔战力加成（P7 修复配套：装备战力参与塔胜率）========
    const val TOWER_POWER_PER_FLOOR = 40L      // 每层推荐战力
    const val TOWER_POWER_WIN_FACTOR = 0.075   // 战力达到该层推荐值即胜率 +7.5%（按 power/推荐值 线性提升）
    const val TOWER_POWER_WIN_BONUS_CAP = 0.15
    const val TOWER_WIN_CHANCE_MIN = 0.05
    const val TOWER_WIN_CHANCE_MAX = 0.95

    // ======== 每日签到（7 日循环）========
    // 量级依据（《数值仿真报告-90天.md》）：主动日收入约 7,000 金，离线 12h ≈ 2,100~4,200 金。
    // 单日签到金币控制在 100~500 ≈ 挂机 1~2 小时量级，全循环合计 1,900 金 ≈ 挂机半天，
    // 只作留存钩子、不冲击主动玩法；soulPower 固定 100/日（商店 300 金 = 500 魂力的比价下
    // 约 60 金/日，纯小额补贴）；第 7 天发 bossCoin 大奖 10 枚——参照宗门 Boss 单次胜利
    // 6~26 枚、爬塔期望 ~0.6 枚/场、商店最低 bossCoin 商品 50 币档，攒一周才够摸到门槛，
    // 给循环一个可期待的终点又不破坏 bossCoin 定价。
    data class CheckInReward(val day: Int, val gold: Long, val bossCoin: Long, val soulPower: Long)

    val CHECK_IN_REWARDS = listOf(
        CheckInReward(1, gold = 100, bossCoin = 0, soulPower = 100),
        CheckInReward(2, gold = 150, bossCoin = 0, soulPower = 100),
        CheckInReward(3, gold = 200, bossCoin = 0, soulPower = 100),
        CheckInReward(4, gold = 250, bossCoin = 0, soulPower = 100),
        CheckInReward(5, gold = 300, bossCoin = 0, soulPower = 100),
        CheckInReward(6, gold = 400, bossCoin = 0, soulPower = 100),
        CheckInReward(7, gold = 500, bossCoin = 10, soulPower = 100)
    )

    /** 7 日循环天数上限：cycleDay = ((streak - 1) % CHECK_IN_CYCLE) + 1 */
    const val CHECK_IN_CYCLE = 7

    // ======== 每日任务 ========
    // 任务 id 是对外契约（前端按 id 渲染与领奖），不得改名；target/description/奖励可按经济锚点微调。
    const val QUEST_BATTLE_WINS = "battle_wins"
    const val QUEST_CULTIVATE = "cultivate"
    const val QUEST_TOWER = "tower"
    const val QUEST_CHECKIN = "checkin"
    const val QUEST_SHOP_BUY = "shop_buy"

    data class DailyQuestDef(
        val id: String,
        val description: String,
        val target: Int,
        val rewardGold: Long,
        val rewardBossCoin: Long,
        val rewardSoulPower: Long
    )

    // 数值锚点（《数值仿真报告-90天.md》：主动日收入约 7,000 金，离线 12h ≈ 2,100~4,200 金）：
    //  - 单任务金币 60~200 ≈ 0.5~1 场塔胜（塔胜金 TOWER_GOLD_BASE + towerLevel×12：
    //    floor=0 时 30 金、floor≈5 时 210 金），只作留存钩子、不冲击主动玩法；
    //  - 全清合计 600 金 ≈ 签到 7 日循环（1,900 金）的 1/3，落在 500~1000 金设计区间；
    //  - bossCoin 仅爬塔任务发 2 枚 ≤ 签到第 7 天大奖 10 枚（爬塔期望 ~0.6 枚/场、
    //    2 次挑战 ≈ 1.2 枚，任务给 2 枚作小额提速），不破坏 bossCoin 定价；
    //  - 魂力按比价 300 金 ≈ 500 魂力（商店魂力精华）折算，≈ 金币 × 5/3。
    val DAILY_QUESTS = listOf(
        DailyQuestDef(QUEST_BATTLE_WINS, "今日战斗胜利 3 次", target = 3, rewardGold = 200, rewardBossCoin = 0, rewardSoulPower = 330),
        DailyQuestDef(QUEST_CULTIVATE, "修炼 5 次", target = 5, rewardGold = 60, rewardBossCoin = 0, rewardSoulPower = 100),
        DailyQuestDef(QUEST_TOWER, "挑战魂塔 2 次", target = 2, rewardGold = 120, rewardBossCoin = 2, rewardSoulPower = 200),
        DailyQuestDef(QUEST_CHECKIN, "完成今日签到", target = 1, rewardGold = 120, rewardBossCoin = 0, rewardSoulPower = 200),
        DailyQuestDef(QUEST_SHOP_BUY, "商店购物 1 次", target = 1, rewardGold = 100, rewardBossCoin = 0, rewardSoulPower = 170)
    )

    /** id → 定义（claim 校验/发奖与 status 合成同源，防止两处数值漂移） */
    val DAILY_QUEST_BY_ID = DAILY_QUESTS.associateBy { it.id }

    // ======== 塔战日志模拟（呈现层，不参与胜负判定）========
    // 塔怪属性从楼层推导：塔是「每层一关」的连续难度带，取推图曲线均摊值——
    //   HP: MONSTER_HP_PER_MAP(300) / STAGES_PER_MAP(15) = 20/层 → 取 24（略收紧，战斗走廊更短）
    //   ATK: MONSTER_ATK_PER_MAP(25) / STAGES_PER_MAP(15) ≈ 1.67 → 取 2
    // 基数复用 MONSTER_HP_BASE(200) / MONSTER_ATK_BASE(15)：0 层 ≈ 第 1 图第 1 关量级。
    // 仅供 GameService.buildTowerBattleLog 渲染逐回合日志；胜负仍由 towerWinChance 概率判定。
    const val TOWER_LOG_MONSTER_HP_PER_FLOOR = 24L
    const val TOWER_LOG_MONSTER_ATK_PER_FLOOR = 2

    // ======== 成就 ========
    // 奖励兑现口径：第一版战斗模型只消费 hp/atk（resolveBattle 仅吃 atk/hp）；
    // matk/pdef/mdef/critRate/critDmg 数据保留但口径暂不消费，属性系统扩展后生效
    // （DTO 照带全字段，前端只展示 hp/atk）。
    // 进度口径：CULTIVATION→level、BATTLE→totalBattleWins、TOWER→towerFloor、
    // SOUL_RING→已装备魂环数（equippedRingRepo.findByUserId(userId).size，描述用「装备」而非
    // 「获得」——背包里的环不算）、PRESTIGE→prestigeCount（当前无写点，待转生玩法，进度恒 0 不解锁）。
    data class AchievementRewards(
        val hp: Long = 0, val atk: Int = 0, val matk: Int = 0,
        val pdef: Int = 0, val mdef: Int = 0, val critRate: Int = 0, val critDmg: Int = 0
    )

    data class AchievementDef(
        val id: String, val name: String, val description: String,
        val category: String, val requiredValue: Long, val rewards: AchievementRewards
    )

    object AchievementDefs {
        val all = listOf(
            AchievementDef("cult_10", "初出茅庐", "达到10级", "CULTIVATION", 10, AchievementRewards(hp = 100, atk = 5)),
            AchievementDef("cult_30", "魂尊之路", "达到30级", "CULTIVATION", 30, AchievementRewards(hp = 300, atk = 15, pdef = 5, mdef = 5)),
            AchievementDef("cult_50", "魂宗威名", "达到50级", "CULTIVATION", 50, AchievementRewards(hp = 600, atk = 30, pdef = 10, mdef = 10, critRate = 2)),
            AchievementDef("cult_80", "封号斗罗", "达到80级", "CULTIVATION", 80, AchievementRewards(hp = 1500, atk = 60, pdef = 20, mdef = 20, critRate = 5)),
            AchievementDef("cult_100", "极限斗罗", "达到100级", "CULTIVATION", 100, AchievementRewards(hp = 3000, atk = 120, pdef = 40, mdef = 40, critRate = 8, critDmg = 15)),
            AchievementDef("ring_1", "初获魂环", "装备第一个魂环", "SOUL_RING", 1, AchievementRewards(matk = 10, critRate = 1)),
            AchievementDef("ring_3", "三环齐聚", "装备3个魂环", "SOUL_RING", 3, AchievementRewards(matk = 35, critRate = 3, critDmg = 10)),
            AchievementDef("ring_5", "五环辉煌", "装备5个魂环", "SOUL_RING", 5, AchievementRewards(matk = 70, critRate = 5, critDmg = 20, hp = 300)),
            AchievementDef("ring_9", "九环圆满", "装备9个魂环", "SOUL_RING", 9, AchievementRewards(hp = 500, matk = 200, critRate = 10, critDmg = 40)),
            AchievementDef("battle_10", "十战勇士", "赢得10场战斗", "BATTLE", 10, AchievementRewards(hp = 100, atk = 10)),
            AchievementDef("battle_50", "百战老兵", "赢得50场战斗", "BATTLE", 50, AchievementRewards(hp = 400, atk = 30, pdef = 5)),
            AchievementDef("tower_10", "塔十层", "通关杀戮之都第10层", "TOWER", 10, AchievementRewards(hp = 200, atk = 15, matk = 10)),
            AchievementDef("tower_30", "塔三十层", "通关杀戮之都第30层", "TOWER", 30, AchievementRewards(hp = 600, atk = 50, matk = 40, pdef = 10, mdef = 10, critRate = 3)),
            AchievementDef("tower_50", "塔五十层", "通关杀戮之都第50层", "TOWER", 50, AchievementRewards(hp = 1500, atk = 120, matk = 100, pdef = 25, mdef = 25, critRate = 5, critDmg = 15)),
            AchievementDef("prestige_1", "初次转生", "完成第一次神位传承", "PRESTIGE", 1, AchievementRewards(hp = 500, pdef = 20, mdef = 20)),
            AchievementDef("prestige_3", "三生三世", "完成3次神位传承", "PRESTIGE", 3, AchievementRewards(hp = 2000, pdef = 60, mdef = 60, critDmg = 20))
        )
    }

    /** id → 定义（解锁判定 / 属性加成求和 / 状态合成三处同源，防止数值漂移） */
    val ACHIEVEMENT_BY_ID = AchievementDefs.all.associateBy { it.id }
}
