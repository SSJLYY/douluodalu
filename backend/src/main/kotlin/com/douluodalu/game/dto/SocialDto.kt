package com.douluodalu.game.dto

// 注：旧 GuildCreateRequest / GuildJoinRequest / GuildDonateRequest / GuildInfoResponse /
// GuildMemberDto 已删除——Controller 实际使用文件底部的内联请求类与下方裁剪 DTO，
// 旧定义零引用属死代码（任务#26 收敛）。

data class GuildListResponse(
    val id: Long,
    val name: String,
    val level: Int,
    val memberCount: Int,
    val maxMembers: Int,
    val notice: String?
)

data class GuildMyResponse(
    val joined: Boolean,
    val guild: GuildListResponse? = null
)

data class GuildBossResponse(
    val won: Boolean,
    val damage: Long,
    val bossHp: Long,
    val goldGained: Long,
    val bossCoinGained: Long,
    val item: BackpackItemDto? = null, // 背包已满时为 null（掉落丢失）
    val message: String
)

data class RankEntryResponse(
    val rank: Int,
    val userId: Long,
    val nickname: String,
    val score: Long,
    val extraData: String?
)
