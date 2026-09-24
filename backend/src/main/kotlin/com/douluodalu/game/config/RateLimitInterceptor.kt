package com.douluodalu.game.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration

/**
 * 基于 Bucket4j 的速率限制拦截器。
 * - 通过 IP 限流。
 * - 不同路径前缀可用不同的桶配置（auth 路径更严格）。
 */
@Component
@ConditionalOnProperty(prefix = "ratelimit", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class RateLimitInterceptor(
    @Value("\${ratelimit.auth.requests-per-minute:10}") private val authPerMinute: Long,
    @Value("\${ratelimit.global.requests-per-minute:300}") private val globalPerMinute: Long
) : HandlerInterceptor {

    /**
     * 使用 Caffeine 缓存限流桶：maximumSize 兜底 + 5 分钟无访问自动过期，
     * 避免 ConcurrentHashMap 只增不减导致的内存泄漏。
     */
    private val buckets: Cache<String, Bucket> = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterAccess(Duration.ofMinutes(5))
        .build()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val ip = clientIp(request)
        val path = request.requestURI
        val isAuthPath = path.startsWith("/api/auth")

        val key = "${if (isAuthPath) "auth" else "global"}:$ip"
        val bucket = buckets.get(key) { createBucket(isAuthPath) }

        if (bucket.tryConsume(1)) {
            return true
        }

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", "60")
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("""{"error":"TOO_MANY_REQUESTS","message":"请求过于频繁，请稍后再试"}""")
        return false
    }

    private fun createBucket(isAuthPath: Boolean): Bucket {
        val limit = if (isAuthPath) authPerMinute else globalPerMinute
        return Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(limit).refillGreedy(limit, Duration.ofMinutes(1)).build())
            .build()
    }

    private fun clientIp(req: HttpServletRequest): String {
        val xff = req.getHeader("X-Forwarded-For")
        if (!xff.isNullOrBlank()) return xff.split(",").first().trim()
        val real = req.getHeader("X-Real-IP")
        if (!real.isNullOrBlank()) return real
        return req.remoteAddr ?: "unknown"
    }
}
