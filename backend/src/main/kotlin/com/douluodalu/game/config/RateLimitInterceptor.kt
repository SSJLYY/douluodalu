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
import java.net.InetAddress
import java.time.Duration

/**
 * 基于 Bucket4j 的速率限制拦截器。
 * - 通过 IP 限流（XFF 仅在可信代理背后采信，见 clientIp 注释——防伪造头绕过限流）。
 * - 不同路径前缀可用不同的桶配置（auth 路径更严格）。
 * - 限流分级（第二十六轮）：auth 前缀路径中仅 GET /api/auth/me 例外走 global 桶
 *   （读端点高频，见 preHandle 注释），其余 auth 写端点维持严格桶。
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
        // 限流分级（第二十六轮）：/api/auth/** 走严格 auth 桶（10 RPM）防爆破/防撞库；
        // 唯一例外是 GET /api/auth/me——它是读端点，SPA 启动与路由切换都会高频调用，
        // 挂在 auth 桶极易在正常使用中先触顶（第二十四轮遗留），故降级走 global 桶
        // （300 RPM，与其他读端点同池）；登录/注册等写端点保持 auth 桶严格限流不变。
        val isAuthPath = path.startsWith("/api/auth") &&
                !(request.method == "GET" && path == "/api/auth/me")

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

    /**
     * 取限流键用的客户端 IP。安全审计修复：不能无条件信任 X-Forwarded-For /
     * X-Real-IP——请求方（或中间任何一跳）都可以自行携带伪造的 XFF 头，逐请求轮换
     * 即可让「按 IP 限流」完全失效（登录接口的 10 RPM 防爆破随之被绕过）。
     *
     * 现在仅当 TCP 对端（remoteAddr）是可信代理（本机回环 / 内网地址，覆盖
     * DEPLOY.md 部署形态：nginx 与应用同机、proxy_pass http://127.0.0.1:8080）
     * 时才采信转发头；且取 XFF 的【最后一段】——nginx 的 $proxy_add_x_forwarded_for
     * 是「追加」语义，最后一段才是代理实际看到的直连客户端地址，客户端可伪造的
     * 前几段全部弃用。对端不可信（公网直连）时一律用 remoteAddr，转发头仅作参考。
     */
    private fun clientIp(req: HttpServletRequest): String {
        val remote = req.remoteAddr ?: "unknown"
        if (!isTrustedProxy(remote)) return remote

        val xff = req.getHeader("X-Forwarded-For")
        if (!xff.isNullOrBlank()) {
            val last = xff.split(",").map { it.trim() }.lastOrNull { it.isNotEmpty() }
            if (!last.isNullOrBlank()) return last
        }
        val real = req.getHeader("X-Real-IP")
        if (!real.isNullOrBlank()) return real
        return remote
    }

    /** 可信代理：本机回环、全零地址或 RFC1918/站点内网（nginx 同机部署即覆盖） */
    private fun isTrustedProxy(remoteAddr: String): Boolean {
        return try {
            val addr = InetAddress.getByName(remoteAddr)
            addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isSiteLocalAddress
        } catch (e: Exception) {
            // 无法解析的地址按不可信处理，回落到 remoteAddr 本身
            false
        }
    }
}
