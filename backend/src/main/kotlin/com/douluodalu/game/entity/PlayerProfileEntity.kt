package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "player_profile")
class PlayerProfileEntity(
    @Id
    @Column(name = "user_id")
    var userId: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    var user: UserEntity? = null,

    var level: Int = 1,
    var gold: Long = 0,
    @Column(name = "soul_power")
    var soulPower: Long = 0,
    @Column(name = "boss_coin")
    var bossCoin: Long = 0,

    @Column(name = "martial_soul_name")
    var martialSoulName: String? = null,
    @Column(name = "chosen_school")
    var chosenSchool: String? = null,

    @Column(name = "current_map_id")
    var currentMapId: Int = 0,
    @Column(name = "current_stage")
    var currentStage: Int = 1,
    @Column(name = "current_hp")
    var currentHp: Long = 100,
    @Column(name = "battle_soul_power")
    var battleSoulPower: Int = 100,

    @Column(name = "total_battle_wins")
    var totalBattleWins: Long = 0,
    @Column(name = "total_battle_losses")
    var totalBattleLosses: Long = 0,

    @Column(name = "tower_floor")
    var towerFloor: Int = 0,
    @Column(name = "tower_boss_kills")
    var towerBossKills: Int = 0,
    @Column(name = "killing_intent")
    var killingIntent: Int = 0,

    @Column(name = "prestige_count")
    var prestigeCount: Int = 0,
    @Column(name = "talent_points")
    var talentPoints: Int = 0,

    @Column(name = "codex_kills")
    var codexKills: Long = 0,

    @Column(name = "last_logout_time")
    var lastLogoutTime: LocalDateTime? = null,

    @Column(name = "auto_battle")
    var autoBattle: Boolean = false,
    @Column(name = "auto_advance_map")
    var autoAdvanceMap: Boolean = true,
    @Column(name = "auto_breakthrough")
    var autoBreakthrough: Boolean = true,

    @Column(name = "tutorial_step")
    var tutorialStep: Int = 1,

    @Column(name = "guild_id")
    var guildId: Long? = null,

    @Column(name = "backpack_capacity")
    var backpackCapacity: Int = 20,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
