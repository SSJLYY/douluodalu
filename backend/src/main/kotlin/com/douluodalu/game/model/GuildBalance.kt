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
