package com.douluodalu.game.dto

data class GuildCreateRequest(
    val name: String,
    val notice: String = ""
)

data class GuildJoinRequest(
    val guildId: Long
)

data class GuildDonateRequest(
    val gold: Long
)

data class GuildInfoResponse(
    val id: Long,
    val name: String,
    val leaderName: String,
    val level: Int,
    val exp: Long,
    val memberCount: Int,
    val maxMembers: Int,
    val notice: String?,
    val members: List<GuildMemberDto>
)

data class GuildMemberDto(
    val userId: Long,
    val nickname: String,
    val role: String,
    val contribution: Long,
    val level: Int
)

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
