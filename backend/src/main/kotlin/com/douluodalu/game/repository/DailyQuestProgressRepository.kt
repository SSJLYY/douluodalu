package com.douluodalu.game.repository

import com.douluodalu.game.entity.DailyQuestProgressEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface DailyQuestProgressRepository : JpaRepository<DailyQuestProgressEntity, Long> {

    /** 当天单任务进度行（claim 失败归因用） */
    fun findByUserIdAndQuestDateAndQuestId(userId: Long, questDate: LocalDate, questId: String): DailyQuestProgressEntity?

    /** 当天全部任务进度行（状态合成一次取回，缺行 progress=0） */
    fun findByUserIdAndQuestDate(userId: Long, questDate: LocalDate): List<DailyQuestProgressEntity>

    /**
     * 条件原子计数：DB 侧串行累加，返回受影响行数（0 = 当天还没有该任务的行）。
     * 避免「读-改-写」丢失更新；重复记录天然幂等（防抖无需额外判断）。
     */
    @Modifying
    @Query(
        "UPDATE DailyQuestProgressEntity p SET p.progress = p.progress + 1, p.updatedAt = CURRENT_TIMESTAMP " +
                "WHERE p.userId = :userId AND p.questDate = :questDate AND p.questId = :questId"
    )
    fun incrementProgress(
        @Param("userId") userId: Long,
        @Param("questDate") questDate: LocalDate,
        @Param("questId") questId: String
    ): Int

    /**
     * 条件原子领取：仅当「未领取且进度达标」时置 claimed=1，返回行数区分成败。
     * 并发双击只有一方命中，防重复领取/超额领取双花。
     */
    @Modifying
    @Query(
        "UPDATE DailyQuestProgressEntity p SET p.claimed = true, p.updatedAt = CURRENT_TIMESTAMP " +
                "WHERE p.userId = :userId AND p.questDate = :questDate AND p.questId = :questId " +
                "AND p.claimed = false AND p.progress >= :target"
    )
    fun claimIfEligible(
        @Param("userId") userId: Long,
        @Param("questDate") questDate: LocalDate,
        @Param("questId") questId: String,
        @Param("target") target: Int
    ): Int
}
