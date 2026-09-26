package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 成就解锁记录：一人一成就至多一行（V9 uk_user_achievement 兜底）。
 * 解锁即生效（奖励是永久属性加成，口径按已解锁集合求和），无领取状态列。
 */
@Entity
@Table(name = "achievement_record")
class AchievementEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "achievement_id", nullable = false, length = 40)
    var achievementId: String = "",

    @Column(name = "unlocked_at")
    var unlockedAt: LocalDateTime = LocalDateTime.now()
)
