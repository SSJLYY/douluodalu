package com.douluodalu.game.model

import kotlin.random.Random

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

    // ======== 战斗：防御与暴击（第十七轮战斗模型扩展）========
    // 公式出处（shared 引擎只读参照，GameEngine.kt:1216~1267 defFactor/暴击/爆伤）：
    //   defFactor = (1.0 - def/(def+DEF_K)).coerceIn(0.1, 1.0)：def=0 → 1.0（无减免）、def→∞ → 0.1 下限；
    //   暴击判定 Random.nextInt(100) < critRate（critRate 为百分点数，18 = 18%）；爆伤 dmg × critDmg/100；
    //   普攻物理/魔法混合 85%/15%（ATTACK_MAGIC_SHARE=15）。
    // 数值锚点：玩家 matk 镜像物攻基础的一半（PLAYER_MATK_BASE_FACTOR）；玩家双防 = level × PLAYER_DEF_PER_LEVEL
    // （校准杠杆）；怪 pdef/mdef = 怪 atk × MONSTER_DEF_FACTOR（校准杠杆）。90 天仿真校准记录见各常量注释。
    //
    // 【第十七轮校准记录】仿真第 1 次跑（LongRunSimulationTest，主种子 20260914，初值
    // PLAYER_DEF_PER_LEVEL=2 / MONSTER_DEF_FACTOR=0.35 / 怪物 hp·atk 系数未动）即全部收敛，未再调参：
    //  - 穿装画像（转生关闭口径）90 天末等级 78（基线 77，允许带 ≥60）、推图 8-15（mapId=7，目标 ≥5）；
    //  - 每日胜率量级不塌方：零装备基线第 10/90 天推图 3-2 / 5-9（基线 4-4 / 5-6 同量级），
    //    穿装组第 10 天仍抵达 8-15 终局（装备攻击加成使 defFactor 减免被碾压）；
    //  - 收敛锁全过：主种子 9/9 槽、后 60 天死环率 0.0%、后期利用率 72.6%（带 40~92 内）、单跑 2.9s<60s；
    //  - 结构性原因：玩家双防（2/级）与怪 atk（+25/图）同量级线性，且 pdef 减伤只作用于怪的
    //    +0~20% 浮动基础伤，攻防两端减免大致对冲，胜率曲线无系统性偏移。
    const val DEF_K = 200.0                // 减伤曲线常数（shared GameEngine defFactor 同源）
    const val ATTACK_MAGIC_SHARE = 15      // 普攻魔法占比（百分点）：15 = 85% 物理 / 15% 魔法
    const val PLAYER_MATK_BASE_FACTOR = 0.5 // 玩家魔攻 = 物攻基础 × 0.5（25 + level×5）
    const val PLAYER_DEF_PER_LEVEL = 2     // 玩家物防/魔防每级成长（校准杠杆；仿真第 1 跑初值即收敛，见上区块校准记录）
    const val PLAYER_CRIT_RATE_BASE = 0    // 玩家基础暴击率（百分点；零基础保证无暴击加成时与旧数学零漂移）
    const val PLAYER_CRIT_DMG_BASE = 150   // 玩家基础暴击伤害（百分点，150 = 1.5 倍）
    const val MONSTER_DEF_FACTOR = 0.35    // 怪物双防 = 怪 atk × 0.35（校准杠杆；仿真第 1 跑初值即收敛，见上区块校准记录）

    // 五属性战力折算权重（powerOf）：matk×0.5、pdef/mdef×0.2、critRate×5、critDmg×0.2，逐项截断取整后求和。
    // 量级标定（与 atk 1 点 = 1 战力对齐感知）：critRate 1 点 ≈ 5 战力（1% 暴击 ≈ 期望 +0.5%×1.5 倍伤），
    // 防御 10 点 ≈ 2 战力（defFactor 边际收益在 200~400 防区间 ≈ 每点 0.2% 减伤），matk 折半（魔攻占比 15%）。
    const val POWER_MATK_WEIGHT = 0.5
    const val POWER_DEF_WEIGHT = 0.2
    const val POWER_CRIT_RATE_WEIGHT = 5.0
    const val POWER_CRIT_DMG_WEIGHT = 0.2

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

    // ======== 签到补签（第二十一轮） ========
    // 实现决策定价（设计文档未定价）：500 金 ≈ 0~5 层塔单次胜场量级（TOWER_GOLD_BASE +
    // towerLevel×TOWER_GOLD_PER_LEVEL），断签玩家的止损选项而非奖励套利入口。
    // 语义边界：补签只修复连签（把「昨天」按正常签到口径接回链上），【不补发】当日
    // 循环奖励（金币/Boss币/魂力一律不发，也不触发每日任务的签到计数）。
    const val CHECKIN_MAKEUP_COST_GOLD = 500L

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

    // ======== 转生（神位传承） ========
    // 门槛校准依据：设计文档原文 Lv.100、策略指南 Lv.90，但后端无经验系统、level 唯一提升途径是
    // breakthrough（魂力消耗 120·L^1.55，与文档加速曲线不同源），90 天中活跃仿真
    // （LongRunSimulationTest）纯修画像仅到 Lv.77、穿装画像更高但重置后回爬仍需数周。
    // 照搬文档门槛会让本玩法上线后 ~5 个月不可达，故按后端节奏校准为 Lv.50
    // （90 天内可完成 1~2 转，与成就 prestige_1/prestige_3 的节奏对齐）。
    // 收益 = 双口径（shared GameEngine.prestigeMultiplier = 1.0 + count×0.1 先例）：
    //  1) 属性：基础 atk/maxHp（GameService battle/tower 结算处）与 EquipmentPowerService.bonusFor
    //     返回的装备+成就加成部分都乘倍率（等效于 (基础+装备+成就)×倍率，符合文档 §15.2）；
    //  2) 收入：cultivate 产出、battle 胜 gold/exp、towerBattle 胜 gold/exp、claimOfflineReward
    //     gold/exp 四处乘倍率（依据 shared GameEngine.kt:2607 修为产出 ×prestigeMultiplier——
    //     转生后收入更快回爬，否则重置纯亏）。签到/任务/宗门/商店固定表**不乘**（留存钩子与
    //     交易口径不膨胀）；Boss 币口径不乘。
    const val PRESTIGE_MIN_LEVEL = 50
    const val PRESTIGE_STAT_BONUS = 0.10

    /** 转生倍率：全属性/主动收入 ×(1 + 转数×PRESTIGE_STAT_BONUS)。count=0 恒为 1.0，既有数值零漂移 */
    fun prestigeMultiplier(count: Int): Double = 1.0 + count * PRESTIGE_STAT_BONUS

    // ======== 成就 ========
    // 定义表与设计文档 §10 全表对齐（21 条：cult_10~150 / ring_1~9 / battle_10~1000 /
    // tower_10~100 / prestige_1~5，第二十一轮补齐缺口 cult_150/battle_200/battle_1000/
    // tower_100/prestige_5）。文档「DEF+n」按既有多数口径落为 pdef+mdef 双防各 n
    // （battle_50 历史上只落 pdef，属孤例，不回改）。
    // 奖励兑现口径：第十七轮战斗模型扩展起七字段全消费（hp/atk 之外，matk/pdef/mdef/critRate/critDmg
    // 经 EquipmentBonus 五属性字段进 resolveBattle 与 powerOf）；装备侧暂无五属性数据（affixesJson
    // 未生成），转生武魂下轮接入。
    // 进度口径：CULTIVATION→level、BATTLE→totalBattleWins、TOWER→towerFloor、
    // SOUL_RING→已装备魂环数（equippedRingRepo.findByUserId(userId).size，描述用「装备」而非
    // 「获得」——背包里的环不算）、PRESTIGE→prestigeCount（写点：GameService.prestige）。
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
            AchievementDef("cult_150", "神王降临", "达到150级", "CULTIVATION", 150, AchievementRewards(hp = 8000, atk = 300, pdef = 80, mdef = 80, critRate = 12, critDmg = 30)),
            AchievementDef("ring_1", "初获魂环", "装备第一个魂环", "SOUL_RING", 1, AchievementRewards(matk = 10, critRate = 1)),
            AchievementDef("ring_3", "三环齐聚", "装备3个魂环", "SOUL_RING", 3, AchievementRewards(matk = 35, critRate = 3, critDmg = 10)),
            AchievementDef("ring_5", "五环辉煌", "装备5个魂环", "SOUL_RING", 5, AchievementRewards(matk = 70, critRate = 5, critDmg = 20, hp = 300)),
            AchievementDef("ring_9", "九环圆满", "装备9个魂环", "SOUL_RING", 9, AchievementRewards(hp = 500, matk = 200, critRate = 10, critDmg = 40)),
            AchievementDef("battle_10", "十战勇士", "赢得10场战斗", "BATTLE", 10, AchievementRewards(hp = 100, atk = 10)),
            AchievementDef("battle_50", "百战老兵", "赢得50场战斗", "BATTLE", 50, AchievementRewards(hp = 400, atk = 30, pdef = 5)),
            AchievementDef("battle_200", "千战精英", "赢得200场战斗", "BATTLE", 200, AchievementRewards(hp = 1000, atk = 80, pdef = 15, mdef = 15)),
            AchievementDef("battle_1000", "万战传说", "赢得1000场战斗", "BATTLE", 1000, AchievementRewards(hp = 3000, atk = 250, pdef = 50, mdef = 50, critRate = 5)),
            AchievementDef("tower_10", "塔十层", "通关杀戮之都第10层", "TOWER", 10, AchievementRewards(hp = 200, atk = 15, matk = 10)),
            AchievementDef("tower_30", "塔三十层", "通关杀戮之都第30层", "TOWER", 30, AchievementRewards(hp = 600, atk = 50, matk = 40, pdef = 10, mdef = 10, critRate = 3)),
            AchievementDef("tower_50", "塔五十层", "通关杀戮之都第50层", "TOWER", 50, AchievementRewards(hp = 1500, atk = 120, matk = 100, pdef = 25, mdef = 25, critRate = 5, critDmg = 15)),
            AchievementDef("tower_100", "杀戮之王", "通关杀戮之都第100层", "TOWER", 100, AchievementRewards(hp = 8000, atk = 500, matk = 400, pdef = 100, mdef = 100, critRate = 15, critDmg = 50)),
            AchievementDef("prestige_1", "初次转生", "完成第一次神位传承", "PRESTIGE", 1, AchievementRewards(hp = 500, pdef = 20, mdef = 20)),
            AchievementDef("prestige_3", "三生三世", "完成3次神位传承", "PRESTIGE", 3, AchievementRewards(hp = 2000, pdef = 60, mdef = 60, critDmg = 20)),
            AchievementDef("prestige_5", "五世轮回", "完成5次神位传承", "PRESTIGE", 5, AchievementRewards(hp = 5000, pdef = 150, mdef = 150, critRate = 5, critDmg = 50))
        )
    }

    /** id → 定义（解锁判定 / 属性加成求和 / 状态合成三处同源，防止数值漂移） */
    val ACHIEVEMENT_BY_ID = AchievementDefs.all.associateBy { it.id }

    // ======== 武魂技能（第二十轮） ========
    // 数值锚点：shared 引擎 Models.kt SkillType/Skill **逐字移植**（shared 为唯一权威，若调参需
    // 两侧同步）。SkillDef 取 shared Skill 的 name/type/power/cooldown 四字段（description 为
    // 展示文案，后端无消费场景不移植）。触发模型为冷却制、零新增掷点：技能在第 r 回合（1-based）
    // 释放 ⟺ (r-1) % cooldown == 0（cooldown 3~5 → 首回合即放，之后每 N 回合一次）；技能回合的
    // 玩家三掷照常发生（RNG 流逐位不变），技能只改写该回合玩家攻击的伤害结算或改为治疗
    // （结算语义见 GameService.resolveBattle 注释）。BERSERK（狂暴打击）shared 无武魂挂载、
    // 本轮不实现结算——resolveBattle 回落普通攻击（注释留档，接入时同步 shared 狂暴语义）。
    enum class SkillType(val displayName: String) {
        SINGLE_DAMAGE("单体攻击"), MULTI_HIT("多段攻击"), HEAL("治疗回复"),
        IGNORE_DEFENSE("无视防御"), BERSERK("狂暴打击")
    }

    data class SkillDef(val name: String, val type: SkillType, val power: Int, val cooldown: Int)

    /**
     * IGNORE_DEFENSE 破甲系数：defFactor 用 def×0.5 计算（软化而非完全无视）。
     * 标定依据：shared「破甲击」系（天使圣光/修罗斩）文案为「无视防御」，若按字面完全无视
     * （defFactor 恒 1.0），技能相对普攻的增量会随怪物防御无限放大——def→∞ 时普攻被 0.1 下限
     * 夹死而技能不衰减，高防图收益失控；def×0.5 下破甲收益与防御成长同阶（对任何 def 恒等于
     * 「减半对方防御」），可被仿真收敛锁约束，故按 shared 破甲击系先例软化并在此留档。
     */
    const val SKILL_DEF_IGNORE_FACTOR = 0.5

    // ======== 武魂池（觉醒） ========
    // 数值锚点：设计文档 §2.1（12 武魂 × 七属性表）、§2.2（觉醒概率权重）、§2.3（品质池随转数扩展），
    // 定义从 shared 引擎 Models.kt MartialSoulPool **逐字移植**（shared 为唯一权威，若调参需两侧同步；
    // 流派/自动金币加成字段后端无消费场景，仍不移植；技能字段已由第二十轮逐字移植，见上方技能区块）。
    // rarity 复用本模块既有 Rarity 枚举（枚举名与 shared 逐字一致：
    // COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC）。
    data class MartialSoulDef(
        val name: String,
        val rarity: Rarity,
        val baseHp: Long,
        val baseAtk: Int,
        val baseMatk: Int,
        val critRate: Int,
        val critDmg: Int,
        val pdef: Int,
        val mdef: Int,
        /** 武魂技能（shared MartialSoul.skill 逐字移植；12 条全部非空） */
        val skill: SkillDef
    )

    // §2.1 十二武魂（字段序：name, rarity, baseHp, baseAtk, baseMatk, critRate, critDmg, pdef, mdef, skill；
    // 与 shared MartialSoul 逐条对照核验——九属性逐字一致，技能 (type, power, cooldown) 取 shared Skill 实参）
    val MARTIAL_SOULS = listOf(
        MartialSoulDef("蓝银草", Rarity.COMMON, 50, 20, 10, 5, 150, 5, 5, SkillDef("缠绕", SkillType.SINGLE_DAMAGE, 140, 3)),
        MartialSoulDef("幽冥灵猫", Rarity.COMMON, 40, 25, 5, 10, 150, 3, 3, SkillDef("幽冥突刺", SkillType.MULTI_HIT, 100, 4)),
        MartialSoulDef("柔骨兔", Rarity.UNCOMMON, 80, 30, 15, 8, 160, 8, 8, SkillDef("腰弓", SkillType.SINGLE_DAMAGE, 180, 4)),
        MartialSoulDef("蓝银皇", Rarity.UNCOMMON, 100, 40, 30, 8, 160, 10, 12, SkillDef("蓝银囚笼", SkillType.SINGLE_DAMAGE, 160, 3)),
        MartialSoulDef("七宝琉璃塔", Rarity.RARE, 120, 50, 60, 12, 180, 15, 20, SkillDef("七宝转出", SkillType.HEAL, 30, 5)),
        MartialSoulDef("邪火凤凰", Rarity.RARE, 110, 55, 70, 15, 200, 12, 10, SkillDef("凤凰啸天击", SkillType.SINGLE_DAMAGE, 220, 4)),
        MartialSoulDef("白虎", Rarity.RARE, 200, 60, 20, 10, 170, 25, 15, SkillDef("白虎烈光波", SkillType.MULTI_HIT, 120, 4)),
        MartialSoulDef("昊天锤", Rarity.EPIC, 250, 80, 30, 12, 200, 30, 20, SkillDef("乱披风锤法", SkillType.MULTI_HIT, 150, 5)),
        MartialSoulDef("九宝琉璃塔", Rarity.EPIC, 180, 75, 90, 15, 200, 20, 25, SkillDef("九宝护体", SkillType.HEAL, 50, 5)),
        MartialSoulDef("六翼天使", Rarity.LEGENDARY, 350, 120, 100, 18, 220, 35, 30, SkillDef("天使圣光", SkillType.IGNORE_DEFENSE, 180, 4)),
        MartialSoulDef("海神三叉戟", Rarity.LEGENDARY, 300, 130, 120, 15, 250, 30, 35, SkillDef("海神之怒", SkillType.SINGLE_DAMAGE, 250, 5)),
        MartialSoulDef("修罗魔剑", Rarity.MYTHIC, 500, 200, 150, 25, 300, 40, 35, SkillDef("修罗斩", SkillType.IGNORE_DEFENSE, 300, 5))
    )

    private val MARTIAL_SOUL_BY_NAME = MARTIAL_SOULS.associateBy { it.name }

    /** 名字反查（ProfileDto.soulRarity 徽章 / 战斗属性并入共用）；未知名字（历史脏数据）返回 null */
    fun soulByName(name: String): MartialSoulDef? = MARTIAL_SOUL_BY_NAME[name]

    /** §2.3 品质池门槛：0转 ≤精良 / 1转 ≤稀有 / 2转 ≤史诗 / 3~4转 ≤传说 / ≥5转 全量（shared getAvailablePool 同款） */
    fun availableSoulPool(prestigeCount: Int): List<MartialSoulDef> {
        val maxRarity = when {
            prestigeCount >= 5 -> Rarity.MYTHIC
            prestigeCount >= 3 -> Rarity.LEGENDARY
            prestigeCount >= 2 -> Rarity.EPIC
            prestigeCount >= 1 -> Rarity.RARE
            else -> Rarity.UNCOMMON
        }
        return MARTIAL_SOULS.filter { it.rarity.ordinal <= maxRarity.ordinal }
    }

    /** §2.2 觉醒概率权重（逐字）：COMMON 40 / UNCOMMON 25 / RARE 15 / EPIC 8 / LEGENDARY 2 / MYTHIC 0.5 */
    private fun soulRollWeight(rarity: Rarity): Double = when (rarity) {
        Rarity.COMMON -> 40.0
        Rarity.UNCOMMON -> 25.0
        Rarity.RARE -> 15.0
        Rarity.EPIC -> 8.0
        Rarity.LEGENDARY -> 2.0
        Rarity.MYTHIC -> 0.5
    }

    /**
     * 觉醒 roll（shared MartialSoulPool.randomAwaken 同款算法）：nextDouble × 权重总和后按池序逐项扣减。
     * rng 默认全局 Random（awaken 端点无既有掷点次序契约）；仿真镜像/测试可注入自身 rng 保持可复现。
     */
    fun rollMartialSoul(prestigeCount: Int, rng: Random = Random): MartialSoulDef {
        val pool = availableSoulPool(prestigeCount)
        var rand = rng.nextDouble() * pool.sumOf { soulRollWeight(it.rarity) }
        for (soul in pool) {
            rand -= soulRollWeight(soul.rarity)
            if (rand <= 0) return soul
        }
        return pool.first()
    }

    /**
     * 武魂战力值（battleSoulPower 唯一写点公式）：让宗门 Boss 伤害公式（GuildService.challengeBoss）
     * 里的恒 100 死值变成随武魂品质成长的活值（COMMON 蓝银草 60 → MYTHIC 修罗魔剑 455）；重醒时重算。
     */
    fun martialSoulPower(def: MartialSoulDef): Long =
        def.baseHp / 10 + def.baseAtk + def.baseMatk / 2 + def.pdef + def.mdef + def.critRate + def.critDmg / 10

    /**
     * 重醒定价：文档未定价，实现决策——首醒免费、重醒 5000 金，防无限免费刷池
     * （重醒不限次数，品质上限由 §2.3 转数门槛兜住）。
     */
    const val REAWAKEN_COST_GOLD = 5000L

    // ======== 流派（灵武流派，第十九轮） ========
    // 数值锚点：shared 引擎 ModelsExt.kt:615-641（SoulSchool 枚举 + SchoolStatMods 表）**逐字移植**
    // （shared 为唯一权威，若调参需两侧同步；shared 的「减伤+5%」「自动金币加成」等后端无对应
    // 乘区的字段不移植）。选流派不重 roll 武魂——shared chooseSchool 会 roll，实现分歧照 awaken
    // 惯例留档：后端武魂觉醒/流派选择解耦，零新增掷点。
    // 系数口径（写死，四处组装同源遵守）：流派系数作用于「基础(已乘转生) + 装备/成就/武魂加成
    // (已乘转生)」之和——即 atk/matk/pdef/mdef 与 hp 逐属性乘对应系数（GameService.playerCombatStats
    // 的 scale+equip 相加后乘、maxHp 的 scaledBaseMaxHp+equip.hpBonus 加总后乘——加成包里的武魂 hp
    // 也吃到系数，与 shared「基础+武魂之后乘流派」口径对齐）；暴击率/爆伤走加数（百分点直加）。
    // chosenSchool=null → 全部系数恒等（乘 1.0/加 0），既有数值零漂移。
    data class SchoolMods(
        val hp: Double, val atk: Double, val matk: Double,
        val pdef: Double, val mdef: Double,
        /** 暴击率/暴击伤害加成（百分点数：5 = 5%/+5% 爆伤），与装备/成就加成同量纲走加数 */
        val critRateBonus: Int, val critDmgBonus: Int
    )

    data class SchoolDef(
        val name: String, val icon: String, val displayName: String, val description: String,
        /** BASIC = 开局可选 / SPECIAL = 有等级+转数门槛（与 shared SchoolCategory 枚举名一致） */
        val category: String,
        val mods: SchoolMods,
        /** 门槛按「选时」校验（已选流派转生后不回撤）；基础流派 requiredLevel=1/requiredPrestige=0 */
        val requiredLevel: Int,
        val requiredPrestige: Int
    )

    // 六流派（系数/名称/图标/描述与 shared ModelsExt.kt:615-641 逐字对照核验；门槛：后端节奏校准——
    // SUPPORT Lv.50+1转 / CONTROL Lv.70+2转 / ASSASSIN Lv.90+3转，与 PRESTIGE_MIN_LEVEL=50 的
    // 转生节奏和成就 prestige_1/prestige_3 对齐）
    val SCHOOLS = listOf(
        SchoolDef("BALANCED", "⚖️", "均衡流派", "全面发展，物法双修。\nHP×105% ATK×100% MATK×100% PDEF×105% MDEF×105% 暴击+5% 爆伤+5%",
            "BASIC", SchoolMods(1.05, 1.00, 1.00, 1.05, 1.05, 5, 5), 1, 0),
        SchoolDef("PHYSICAL", "⚔️", "物理流派", "近战强攻，重视物攻物防。\nHP×100% ATK×130% MATK×45% PDEF×115% MDEF×70% 暴击+8%",
            "BASIC", SchoolMods(1.00, 1.30, 0.45, 1.15, 0.70, 8, 0), 1, 0),
        SchoolDef("MAGIC", "🔮", "法系流派", "远程法术，重视魔攻魔防。\nHP×95% ATK×45% MATK×130% PDEF×70% MDEF×115% 爆伤+8%",
            "BASIC", SchoolMods(0.95, 0.45, 1.30, 0.70, 1.15, 0, 8), 1, 0),
        SchoolDef("SUPPORT", "🛡️", "辅助流派", "治疗增益，提升团队生存。\nHP×115% ATK×80% MATK×90% PDEF×120% MDEF×120% 暴击+3% 减伤+5%",
            "SPECIAL", SchoolMods(1.15, 0.80, 0.90, 1.20, 1.20, 3, 0), 50, 1),
        SchoolDef("CONTROL", "🌿", "控制流派", "控制敌人，限制行动。\nHP×105% ATK×95% MATK×110% PDEF×100% MDEF×105% 暴击+6% 爆伤+6%",
            "SPECIAL", SchoolMods(1.05, 0.95, 1.10, 1.00, 1.05, 6, 6), 70, 2),
        SchoolDef("ASSASSIN", "🗡️", "暗杀流派", "高暴发低防御，一击必杀。\nHP×90% ATK×140% MATK×50% PDEF×60% MDEF×60% 暴击+12% 爆伤+15%",
            "SPECIAL", SchoolMods(0.90, 1.40, 0.50, 0.60, 0.60, 12, 15), 90, 3)
    )

    private val SCHOOL_BY_NAME = SCHOOLS.associateBy { it.name }

    /** 枚举名反查（选流派校验 / 战斗组装查表共用）；未知名字（历史脏数据/非法入参）返回 null */
    fun schoolByName(name: String): SchoolDef? = SCHOOL_BY_NAME[name]

    /** 流派解锁判定（选时校验唯一写点）：等级与转数双门槛，与转生校验同款「选时」语义 */
    fun isSchoolUnlocked(school: SchoolDef, level: Int, prestigeCount: Int): Boolean =
        level >= school.requiredLevel && prestigeCount >= school.requiredPrestige

    /**
     * 改选流派定价（对齐 REAWAKEN_COST_GOLD 惯例）：首选免费、已有流派再选（重选）5000 金，
     * 防零成本来回切换刷门槛差值；重选不限次数。
     */
    const val RESCHOOL_COST_GOLD = 5000L
}
