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
    // 注：魂环年份负荷校验属独立大功能，本次未实现（见仿真报告「已修复项」说明）。
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
}
