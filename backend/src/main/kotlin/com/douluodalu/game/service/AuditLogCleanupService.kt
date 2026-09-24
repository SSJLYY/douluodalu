package com.douluodalu.game.service

import com.douluodalu.game.repository.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 审计日志保留策略：audit_log 表此前只进不出，按保留期滚动清理。
 *
 * 保留天数由 audit.retention-days 配置（默认 30，环境变量 AUDIT_RETENTION_DAYS 覆盖），
 * 每日凌晨（cron 默认 03:00）删除 created_at 早于 cutoff 的记录。
 * 走 V5 已建的 idx_audit_created 索引范围扫描。
 *
 * Bean 本身也挂在 audit.cleanup-enabled 开关下（默认开，test profile 关）：
 * 关闭时不仅 @Scheduled 不触发，整个 Bean 都不注册，彻底杜绝测试抖动。
 */
@Service
@ConditionalOnProperty(name = ["audit.cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class AuditLogCleanupService(
    private val auditLogRepository: AuditLogRepository
) {
    private val log = LoggerFactory.getLogger(AuditLogCleanupService::class.java)

    @Value("\${audit.retention-days:30}")
    var retentionDays: Long = 30

    @Scheduled(cron = "\${audit.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    fun cleanupExpiredAuditLogs() {
        cleanup()
    }

    /** 执行一次清理，返回删除条数。retentionDays<=0 视为禁用清理（保留全部历史）。 */
    @Transactional
    fun cleanup(): Long {
        if (retentionDays <= 0) {
            log.warn("audit cleanup skipped: retention-days={} (<=0 means keep all)", retentionDays)
            return 0
        }
        val cutoff = LocalDateTime.now().minusDays(retentionDays)
        val deleted = auditLogRepository.deleteByCreatedAtBefore(cutoff)
        log.info("audit log cleanup done: retentionDays={} cutoff={} deleted={}", retentionDays, cutoff, deleted)
        return deleted
    }
}
