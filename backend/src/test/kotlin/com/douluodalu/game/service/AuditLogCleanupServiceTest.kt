package com.douluodalu.game.service

import com.douluodalu.game.repository.AuditLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.LocalDateTime

/**
 * 审计日志保留清理任务单测（纯 Mockito，不触库）：
 * 核心验证删除 cutoff = now - retentionDays，保留期内记录不进入删除范围。
 * 注：ArgumentCaptor.capture() 返回 null 哨兵会触发 Kotlin 非空参数检查，
 * 因此用 thenAnswer 在桩内直接捕获入参。
 */
class AuditLogCleanupServiceTest {

    private lateinit var repo: AuditLogRepository
    private lateinit var service: AuditLogCleanupService
    private var capturedCutoff: LocalDateTime? = null

    @BeforeEach
    fun setUp() {
        repo = mock()
        service = AuditLogCleanupService(repo)
        capturedCutoff = null
    }

    private fun stubDelete(deletedCount: Long) {
        whenever(repo.deleteByCreatedAtBefore(any())).thenAnswer { inv ->
            capturedCutoff = inv.getArgument(0)
            deletedCount
        }
    }

    @Test
    fun `过期记录被删除，cutoff 为当前时间减保留天数`() {
        service.retentionDays = 30
        stubDelete(42L)

        val deleted = service.cleanup()

        assertEquals(42L, deleted)
        verify(repo).deleteByCreatedAtBefore(any())
        val cutoff = capturedCutoff
        assertNotNull(cutoff)
        // cutoff ≈ now-30d（容差 5 分钟）：31 天前的记录落入删除范围，保留期内(29 天)的不落
        val drift = Duration.between(cutoff, LocalDateTime.now().minusDays(30)).abs()
        assertTrue(drift.toMinutes() < 5, "cutoff 偏差过大: $cutoff")
        assertTrue(LocalDateTime.now().minusDays(31).isBefore(cutoff))
        assertTrue(LocalDateTime.now().minusDays(29).isAfter(cutoff))
    }

    @Test
    fun `保留期内无过期记录时删除数为 0 且不误删`() {
        service.retentionDays = 30
        stubDelete(0L)

        val deleted = service.cleanup()

        assertEquals(0L, deleted)
        verify(repo).deleteByCreatedAtBefore(any())
        val cutoff = capturedCutoff
        assertNotNull(cutoff)
        // 保留期内（如 10 天前）的 created_at 晚于 cutoff，不满足删除条件
        assertTrue(LocalDateTime.now().minusDays(10).isAfter(cutoff))
    }

    @Test
    fun `保留天数非正数视为永久保留，不触发删除`() {
        service.retentionDays = 0

        val deleted = service.cleanup()

        assertEquals(0L, deleted)
        verifyNoInteractions(repo)
    }
}
