package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDate

/**
 * 每日副本进度：一人一行（V13 user_id 主键，无自增 id）。
 *
 * 四列语义（第二十九轮，设计文档 §8，详见迁移注释与 DungeonService KDoc）：
 *  - tierCompleted：当日最高已通关难度（-1=未通关，0~4=难度层级）；跨天靠 challengeDate
 *    惰性重置（服务层读旧日期视为 -1，当日首次写路径落库归位）。
 *  - challengeDate：当日已用机会日期（战斗胜/败、扫荡都写——「胜或败都算当日已挑战，防刷」）；
 *    可空 = 从未参与过副本。
 *  - everCleared：历史已通关位掩码（bit t = 难度 t 曾通关，0=从未通关）——扫荡前置
 *    「历史已通关该难度」按位精确判定；列名沿用任务书惯例（DEFAULT 0），语义为位掩码。
 *
 * 主键为业务键 user_id（无 @GeneratedValue）：与 PlayerProfileEntity 同款「赋值主键 +
 * save 走 merge（SELECT 后 INSERT/UPDATE）」模式，首次保存多一次 SELECT 属既有惯例。
 */
@Entity
@Table(name = "dungeon_progress")
class DungeonProgressEntity(
    @Id
    @Column(name = "user_id")
    var userId: Long = 0,

    /** 当日最高已通关难度（-1=未通关；跨天惰性重置，见类注释） */
    @Column(name = "tier_completed", nullable = false)
    var tierCompleted: Int = -1,

    /** 当日已用机会日期（战斗/扫荡共用；null=从未参与） */
    @Column(name = "challenge_date")
    var challengeDate: LocalDate? = null,

    /** 历史已通关位掩码（bit t = 难度 t 曾通关；判定走 GameBalance.dungeonHasCleared 单点） */
    @Column(name = "ever_cleared", nullable = false)
    var everCleared: Int = 0
)
