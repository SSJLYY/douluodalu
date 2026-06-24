package com.douluodalu.game.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

@Component
class JwtUtil(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration}") private val expiration: Long,
    @Value("\${jwt.blacklist.expiration:3600000}") private val blacklistExpiration: Long
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    // 黑名单存储（生产环境可替换为 Redis）
    private val revokedTokens = ConcurrentHashMap<String, Long>()

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
            val jti = claims.id
            if (jti != null) {
                val revokedTime = revokedTokens[jti]
                if (revokedTime != null) {
                    // 检查黑名单是否过期
                    if (System.currentTimeMillis() - revokedTime > blacklistExpiration) {
                        revokedTokens.remove(jti)
                        return false
                    }
                    return false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun revokeToken(token: String) {
        try {
            val claims = parseClaims(token)
            val jti = claims.id
            if (jti != null) {
                revokedTokens[jti] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // Token 已过期或无效，无需操作
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
