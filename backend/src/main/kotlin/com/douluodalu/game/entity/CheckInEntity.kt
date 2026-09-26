package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

/** 每日签到记录：一人一天至多一行（V7 uk_user_date 兜底），天数随签到快照 */
@Entity
@Table(name = "check_in_record")
class CheckInEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "check_date", nullable = false)
    var checkDate: LocalDate = LocalDate.now(),

    @Column(name = "streak_at_sign", nullable = false)
    var streakAtSign: Long = 1,

    @Column(name = "total_days_at_sign", nullable = false)
    var totalDaysAtSign: Long = 1,

    @Column(name = "cycle_day", nullable = false)
    var cycleDay: Int = 1,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()
)
