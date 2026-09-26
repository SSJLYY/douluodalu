package com.douluodalu.game.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 宗门 Boss 每周共享血池（V11，一宗一行）：全员挑战共同扣减 currentHp，
 * 扣到 0 即全宗击杀（killed=true），每周一由 GuildWeeklyResetService 重生
 * （killed=false、currentHp=maxHp 按当前宗门等级重算、weekStart=本周一）；
 * challengeBoss 惰性初始化兜底：无行或 weekStart 非本周时建行/重生。
 */
@Entity
@Table(name = "guild_boss")
class GuildBossEntity(
    /** 一宗一行：guild_id 即主键（FK guild ON DELETE CASCADE，解散宗门随之删行） */
    @Id
    @Column(name = "guild_id")
    var guildId: Long = 0,

    /** 当前共享血量（伤害超出剩余血按剩余算，coerceAtLeast(0)） */
    @Column(name = "current_hp", nullable = false)
    var currentHp: Long = 0,

    /** 本周血池上限：(1800 + 宗门等级×650) × GUILD_BOSS_WEEK_HP_MULT */
    @Column(name = "max_hp", nullable = false)
    var maxHp: Long = 0,

    /** 本周是否已被击杀（击杀后挑战拒绝，至下周一重生） */
    @Column(nullable = false)
    var killed: Boolean = false,

    /** 本行所属周的周一日期（惰性重生判据：非本周即重生） */
    @Column(name = "week_start", nullable = false)
    var weekStart: LocalDate = LocalDate.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
