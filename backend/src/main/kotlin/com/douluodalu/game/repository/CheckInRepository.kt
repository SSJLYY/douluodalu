package com.douluodalu.game.repository

import com.douluodalu.game.entity.CheckInEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface CheckInRepository : JpaRepository<CheckInEntity, Long> {
    /** 最近一次签到行：判定连续/断签与「今日已签」都用它，无需聚合历史 */
    fun findFirstByUserIdOrderByCheckDateDesc(userId: Long): CheckInEntity?

    /** 当日预检（save 之外另有 uk_user_date 唯一键兜底并发双击） */
    fun existsByUserIdAndCheckDate(userId: Long, checkDate: LocalDate): Boolean
}
