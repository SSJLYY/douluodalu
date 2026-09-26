package com.douluodalu.game.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "guild")
class Guild(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(unique = true, nullable = false, length = 64)
    var name: String = "",

    @Column(name = "notice", length = 500)
    var description: String = "",

    var level: Int = 1,
    var exp: Long = 0,

    @Column(name = "max_members")
    var maxMembers: Int = 20,

    @Column(name = "member_count")
    var currentMembers: Int = 1,

    @Column(name = "leader_id")
    var leaderId: Long = 0,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()
)

data class GuildMemberId(
    var guildId: Long = 0,
    var userId: Long = 0
) : Serializable

@Entity
@IdClass(GuildMemberId::class)
@Table(
    name = "guild_member",
    // 复合主键 (guild_id, user_id) 拦不住"同一人并发加入两个不同宗门"（两行主键互不冲突），
    // 唯一索引由 DB 兜底：并发双加入时后提交者触发约束冲突整单回滚（见 V6 迁移）
    uniqueConstraints = [UniqueConstraint(name = "uk_member_user", columnNames = ["user_id"])]
)
class GuildMember(
    @Id
    @Column(name = "guild_id")
    var guildId: Long = 0,

    @Id
    @Column(name = "user_id")
    var userId: Long = 0,

    @Column(length = 16)
    var role: String = "MEMBER", // LEADER, ELDER, MEMBER

    @Column(name = "contribution")
    var contribution: Long = 0,

    @Column(name = "joined_at")
    var joinedAt: LocalDateTime = LocalDateTime.now(),

    /** 本周宗门 Boss 伤害（challengeBoss 逐次累加；每周一由 GuildWeeklyResetService 结算前 3 名后清零） */
    @Column(name = "weekly_boss_damage", nullable = false)
    var weeklyBossDamage: Long = 0
)

data class TalentId(
    var userId: Long = 0,
    var branch: String = ""
) : Serializable

@Entity
@IdClass(TalentId::class)
@Table(name = "player_talent")
class Talent(
    @Id
    @Column(name = "user_id")
    var userId: Long = 0,

    @Id
    @Column(length = 20)
    var branch: String = "",

    var level: Int = 0
)

@Entity
@Table(name = "shop_purchase_record")
class ShopPurchaseRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id")
    var userId: Long = 0,

    @Column(name = "item_id")
    var itemId: Long = 0,

    @Column(name = "purchase_count")
    var purchaseCount: Int = 0,

    @Column(name = "last_purchase_at")
    var lastPurchaseAt: LocalDateTime = LocalDateTime.now()
)
