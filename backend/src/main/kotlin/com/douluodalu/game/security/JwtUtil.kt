package com.douluodalu.game.security

import com.github.benmanes.caffeine.cache.Caffeine
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentMap
import javax.crypto.SecretKey

@Component
class JwtUtil(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration}") private val expiration: Long,
    @Value("\${jwt.blacklist.expiration:3600000}") private val blacklistExpirationMs: Long,
    private val environment: Environment
) {
    private val log = LoggerFactory.getLogger(JwtUtil::class.java)

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    }

    /**
     * 安全审计修复：黑名单条目必须存活到 token 自然失效为止。若配置的
     * jwt.blacklist.expiration 短于 token 有效期（默认 1h < 24h），登出被拉黑的
     * token 在条目过期驱逐后会「复活」——validateToken 查不到 jti 又恢复有效。
     * 因此取两者较大值：黑名单至少覆盖整个 token 生命周期。
     */
    val effectiveBlacklistTtlMs: Long = maxOf(blacklistExpirationMs, expiration)

    /**
     * 黑名单：使用 Caffeine 内存缓存，TTL 到期自动驱逐，避免无限增长。
     * 后续如需多实例同步，可替换为 Redis 实现同接口。
     */
    private val revokedTokens: ConcurrentMap<String, Long> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMillis(effectiveBlacklistTtlMs))
        .maximumSize(100_000)
        .build<String, Long>()
        .asMap()

    @PostConstruct
    fun validateSecret() {
        // HS256 要求 key ≥ 32 字节 (256 bit)
        val bytes = secret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= 32) {
            "jwt.secret 强度不足：当前 ${bytes.size} 字节，至少需要 32 字节。请通过 JWT_SECRET 环境变量注入。"
        }
        // 默认占位符：生产 profile 直接启动失败，开发/默认 profile 仅告警放行
        if (secret.startsWith("change-me")) {
            if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod", "production"))) {
                throw IllegalStateException(
                    "生产环境禁止使用默认 jwt.secret 占位符，请通过 JWT_SECRET 环境变量注入真实密钥。"
                )
            }
            log.warn("[WARN] jwt.secret 仍为默认值（未设置 JWT_SECRET 环境变量）。仅适合本地开发，生产环境将拒绝启动。")
        }
    }

    fun generateToken(userId: Long, username: String): String {
        val jti = UUID.randomUUID().toString()
        return Jwts.builder()
            .id(jti)
            .subject(userId.toString())
            .claim("username", username)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(key)
            .compact()
    }

    fun getUserIdFromToken(token: String): Long {
        val claims = parseClaims(token)
        return claims.subject.toLong()
    }

    fun getUsernameFromToken(token: String): String {
        val claims = parseClaims(token)
        return claims["username"] as String
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = parseClaims(token)
            val jti = claims.id ?: return true
            revokedTokens.containsKey(jti).not()
        } catch (e: Exception) {
            false
        }
    }

    fun revokeToken(token: String) {
        try {
            val claims = parseClaims(token)
            val jti = claims.id ?: return
            revokedTokens[jti] = System.currentTimeMillis()
        } catch (e: Exception) {
            // token 已过期或无效，无需加入黑名单
        }
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
