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

            auditLogRepository.save(
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

            auditLogRepository.save(
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
}
