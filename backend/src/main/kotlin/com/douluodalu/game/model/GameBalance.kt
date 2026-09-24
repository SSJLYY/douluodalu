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

    // ======== 离线收益 ========
    const val OFFLINE_MAX_HOURS = 12L
    const val OFFLINE_MAX_SECONDS = OFFLINE_MAX_HOURS * 3600L
    const val OFFLINE_EFFICIENCY = 0.8
    const val OFFLINE_GOLD_BASE = 10L
    const val OFFLINE_GOLD_PER_LEVEL = 2L
    const val OFFLINE_EXP_BASE = 5L
    const val OFFLINE_EXP_PER_LEVEL = 1L
    const val OFFLINE_SECONDS_PER_BATTLE_WIN = 5L

    // ======== 杀戮之都（爬塔） ========
    val TOWER_MONSTERS = listOf("血色统领", "暗影猎手", "地狱魔蛛", "杀戮守卫")
    const val TOWER_BASE_LOSE_CHANCE = 0.25
    const val TOWER_FLOOR_DIFFICULTY = 0.02
    const val TOWER_MAX_FLOOR = 100
    const val TOWER_LEVEL_PER_FLOOR = 3
    const val TOWER_GOLD_BASE = 30L
    const val TOWER_GOLD_PER_LEVEL = 12L
    const val TOWER_EXP_BASE = 25L
    const val TOWER_EXP_PER_LEVEL = 10L
    const val TOWER_BOSS_COIN_CHANCE = 0.6
    const val TOWER_DROP_CHANCE = 0.65
    const val TOWER_KILLING_PER_FLOORS = 10
}
