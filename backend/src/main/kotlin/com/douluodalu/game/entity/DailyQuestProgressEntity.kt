package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 每日任务进度：一人一天一任务至多一行（V8 uk_user_date_quest 兜底）。
 * 跨天重置靠 questDate 自然日隔离（新一天写新行），无需定时清理。
 */
@Entity
@Table(name = "daily_quest_progress")
class DailyQuestProgressEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "quest_date", nullable = false)
    var questDate: LocalDate = LocalDate.now(),

    @Column(name = "quest_id", nullable = false, length = 32)
    var questId: String = "",

    @Column(name = "progress", nullable = false)
    var progress: Int = 0,

    @Column(name = "claimed", nullable = false)
    var claimed: Boolean = false,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
