package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "audit_log")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(nullable = false, length = 64)
    var action: String = "",

    @Column(length = 128)
    var target: String? = null,

    @Column(columnDefinition = "TEXT")
    var detail: String? = null,

    @Column(length = 64)
    var ip: String? = null,

    @Column(name = "trace_id", length = 64)
    var traceId: String? = null,

    @Column(nullable = false, length = 16)
    var result: String = "SUCCESS",

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()
)
