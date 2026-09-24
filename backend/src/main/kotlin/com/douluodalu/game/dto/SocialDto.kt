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

/**
 * 宗门成员行（GET /api/guild/members）。joinedAt 走 Spring Boot 默认 Jackson 配置
 * 输出 ISO-8601 字符串（write-dates-as-timestamps 默认关闭）。
 * isLeader 必须显式钉 JSON 字段名：Kotlin 的 isXxx 属性 getter 会被 Jackson
 * 传统命名规则剥成 "leader"，与前端契约不符。
 */
data class GuildMemberResponse(
    val userId: Long,
    val nickname: String,
    val joinedAt: java.time.LocalDateTime,
    val contribution: Long,
    @get:com.fasterxml.jackson.annotation.JsonProperty("isLeader")
    val isLeader: Boolean
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
