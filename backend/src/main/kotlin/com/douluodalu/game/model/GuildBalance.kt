package com.douluodalu.game.model

/**
 * 宗门 Boss 与宗门商店数值常量。
 * 移植自已删除的前端内嵌假后端（auth-store.ts），以后端为准。
 */
object GuildBossBalance {
    const val BOSS_HP_BASE = 1800L
    const val BOSS_HP_PER_GUILD_LEVEL = 650L
    const val WIN_DAMAGE_RATIO = 0.35
    const val BASE_DAMAGE_PER_LEVEL = 120L
    const val DAMAGE_RANDOM_RANGE = 160L
    const val GOLD_PER_DAMAGE_DIVISOR = 12L
    const val WIN_BOSS_COIN_BASE = 6L
    const val LOSE_BOSS_COIN = 2L
    const val WIN_GUILD_EXP = 30L
    const val LOSE_GUILD_EXP = 12L

    /** 挑战 Boss 的贡献口径：每 CONTRIBUTION_PER_DAMAGE 点伤害记 1 点贡献（与金币奖励同为"按伤害"） */
    const val CONTRIBUTION_PER_DAMAGE = 1000L

    // ======== 宗门Boss周榜（第二十二轮）========
    // 每周一 03:03（GuildWeeklyResetService）按本周伤害（guild_member.weekly_boss_damage）降序
    // 给每宗门前 3 名发奖后全量清零。数值依据：对齐宗门 Boss 单胜 bossCoin 量级
    // （WIN_BOSS_COIN_BASE=6 + guildLevel → 1 级宗门单胜 7 币，20 级上限 26 币）：
    //  - 冠军 50 币 ≈ 活跃玩家一周挑战（20~40 次）单胜产币（~140-280 币/周）的顶部溢价，
    //    与签到第 7 天大奖 10 币、爬塔任务 2 币/日同量级，不打穿 bossCoin 定价（商店最低档 50 币）；
    //  - 金币 5000 ≈ 主动日收入（~7000 金/日，见《数值仿真报告-90天.md》）的 0.7 天，
    //    按周一次性发放不冲击日经济；2/3 名按 60%/40% 递减保持榜单梯度。
    data class WeeklyBossRankReward(val bossCoin: Long, val gold: Long)

    val WEEKLY_BOSS_RANK_REWARDS = listOf(
        WeeklyBossRankReward(bossCoin = 50, gold = 5000), // 第 1 名
        WeeklyBossRankReward(bossCoin = 30, gold = 3000), // 第 2 名
        WeeklyBossRankReward(bossCoin = 20, gold = 2000)  // 第 3 名
    )
}

/**
 * 宗门商店（金币结算，需已加入宗门）。
 * 商品类型复用 ShopService 支持的 BONE_BOX / CORE_BOX。
 */
object GuildShopData {
    val items = listOf(
        ShopItem(301, "宗门护体魂骨", "宗门传承魂骨，适合前期过渡。", 200, "GOLD", "BONE_BOX", "THOUSAND"),
        ShopItem(302, "宗门凝魂核心", "宗门商店专属魂核。", 350, "GOLD", "CORE_BOX", "RARE")
    )
}
