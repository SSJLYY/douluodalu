package com.douluodalu.game.config

import com.douluodalu.game.entity.AuditLog as AuditLogRecord
import com.douluodalu.game.repository.AuditLogRepository
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Aspect
@Component
class AuditLogAspect(
    private val auditLogRepository: AuditLogRepository
) {
    private val log = LoggerFactory.getLogger(AuditLogAspect::class.java)

    /**
     * 敏感字段脱敏：参数 toString 中形如 password=xxx / token: xxx / secretKey=xxx 的值替换为 ******。
     * 保留参数记录能力，仅遮蔽敏感值。
     */
    private val sensitivePattern =
        Regex("(?i)\\b(\\w*(?:password|passwd|secret|token|credential)\\w*\\s*[=:]\\s*)([^,;)\\s}\\]]+)")

    private fun sanitizeArgs(args: Array<out Any?>): String = args.joinToString { arg ->
        when (arg) {
            null -> "null"
            is ByteArray -> "ByteArray(${arg.size})"
            else -> sensitivePattern.replace(arg.toString()) { "${it.groupValues[1]}******" }
        }
    }

    @Around("@annotation(auditLog)")
    fun around(joinPoint: ProceedingJoinPoint, auditLog: AuditLog): Any? {
        val startTime = System.currentTimeMillis()
        val auth: Authentication? = SecurityContextHolder.getContext().authentication
        val userId = (auth?.principal as? Long) ?: 0L

        val request: HttpServletRequest? =
            (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val ip = request?.remoteAddr ?: "unknown"
        val traceId = request?.getHeader("X-Trace-Id") ?: "unknown"

        val method = (joinPoint.signature as MethodSignature).method
        val methodName = method.name

        try {
            val result = joinPoint.proceed()
            val duration = System.currentTimeMillis() - startTime

            val detail = buildString {
                append("耗时: ${duration}ms")
                if (auditLog.logParams) {
                    append(" 参数: ${sanitizeArgs(joinPoint.args)}")
                }
                if (auditLog.logResult && result != null) {
                    append(" 结果: $result")
                }
            }

            // 审计写入是同步且发生在请求线程：失败只降级告警，绝不阻断业务
            saveQuietly(
                AuditLogRecord(
                    userId = userId,
                    action = auditLog.action,
                    target = if (auditLog.target.isNotBlank()) auditLog.target else methodName,
                    detail = detail,
                    ip = ip,
                    traceId = traceId,
                    result = "SUCCESS"
                )
            )

            log.info("[AUDIT] userId={} action={} method={} ip={} traceId={} duration={}ms",
                userId, auditLog.action, methodName, ip, traceId, duration)

            return result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime

            // 同上：审计落库失败不得吞掉/替换业务原始异常
            saveQuietly(
                AuditLogRecord(
                    userId = userId,
                    action = auditLog.action,
                    target = if (auditLog.target.isNotBlank()) auditLog.target else methodName,
                    detail = "耗时: ${duration}ms" +
                            if (auditLog.logParams) " 参数: ${sanitizeArgs(joinPoint.args)}" else "",
                    ip = ip,
                    traceId = traceId,
                    result = "FAILURE",
                    errorMessage = e.message
                )
            )

            log.error("[AUDIT-FAIL] userId={} action={} method={} ip={} traceId={} duration={}ms error={}",
                userId, auditLog.action, methodName, ip, traceId, duration, e.message)

            throw e
        }
    }

    /**
     * 同步 save 的降级包装：audit_log 写入异常（库故障、字段超长等）仅记 warn，
     * 不影响业务方法的返回值或异常传播。
     */
    private fun saveQuietly(record: AuditLogRecord) {
        try {
            auditLogRepository.save(record)
        } catch (e: Exception) {
            log.warn("[AUDIT] 审计日志写入失败已降级跳过 action={} userId={} error={}",
                record.action, record.userId, e.message)
        }
    }
}
