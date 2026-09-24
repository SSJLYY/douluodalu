package com.douluodalu.game.repository

import com.douluodalu.game.entity.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<AuditLog>

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.action = :action ORDER BY a.createdAt DESC")
    fun findByUserIdAndAction(
        @Param("userId") userId: Long,
        @Param("action") action: String
    ): List<AuditLog>

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt >= :startTime ORDER BY a.createdAt DESC")
    fun findRecentLogs(@Param("startTime") startTime: java.time.LocalDateTime): List<AuditLog>
}
