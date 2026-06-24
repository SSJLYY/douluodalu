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
@Table(name = "guild_member")
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
    var joinedAt: LocalDateTime = LocalDateTime.now()
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
