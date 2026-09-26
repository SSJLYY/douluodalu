package com.douluodalu.game.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

/**
 * 安全审计回归（JWT 与会话）：
 * 1) 黑名单 TTL 必须覆盖 token 整个生命周期——旧配置 blacklist.expiration(1h) <
 *    token expiration(24h) 时，登出拉黑的 token 在条目驱逐后会「复活」；
 * 2) 畸形/伪造签名的 token 校验必须返回 false（JwtAuthFilter 依赖它不抛异常，
 *    保证恶意 token 走 401/403 而非 500）；
 * 3) secret 强度不足必须拒绝启动。
 */
class JwtUtilTest {

    private val secret = "unit-test-secret-key-with-at-least-32-bytes!!"
    private val env = MockEnvironment()

    @Test
    fun `blacklist ttl must cover full token lifetime when configured ttl is shorter`() {
        // 配置 100ms、token 有效期 2000ms → 生效 TTL 必须取较大值 2000
        val u = JwtUtil(secret, expiration = 2000, blacklistExpirationMs = 100, environment = env)
        assertEquals(2000L, u.effectiveBlacklistTtlMs)
    }

    @Test
    fun `blacklist ttl keeps configured value when it is the larger one`() {
        val u = JwtUtil(secret, expiration = 1000, blacklistExpirationMs = 5000, environment = env)
        assertEquals(5000L, u.effectiveBlacklistTtlMs)
    }

    @Test
    fun `revoked token must fail validation immediately`() {
        val u = JwtUtil(secret, expiration = 60_000, blacklistExpirationMs = 60_000, environment = env)
        val token = u.generateToken(1L, "alice")
        assertTrue(u.validateToken(token))

        u.revokeToken(token)
        assertFalse(u.validateToken(token), "登出后的 token 必须立即失效")
    }

    @Test
    fun `malformed or garbage token must return false instead of throwing`() {
        val u = JwtUtil(secret, expiration = 60_000, blacklistExpirationMs = 60_000, environment = env)
        assertFalse(u.validateToken(""))
        assertFalse(u.validateToken("garbage-token"))
        assertFalse(u.validateToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.not-a-valid-signature"))
    }

    @Test
    fun `token signed with a different secret must be rejected`() {
        val otherSecret = "another-unit-test-secret-with-32-bytes-exactly!!!"
        val u1 = JwtUtil(secret, expiration = 60_000, blacklistExpirationMs = 60_000, environment = env)
        val u2 = JwtUtil(otherSecret, expiration = 60_000, blacklistExpirationMs = 60_000, environment = env)

        val forged = u2.generateToken(1L, "alice")
        assertFalse(u1.validateToken(forged), "他人密钥签发的 token 必须被拒绝（防伪造）")
    }

    @Test
    fun `expired token must be rejected`() {
        val u = JwtUtil(secret, expiration = 1, blacklistExpirationMs = 60_000, environment = env)
        val token = u.generateToken(1L, "alice")
        Thread.sleep(20)
        assertFalse(u.validateToken(token))
    }

    @Test
    fun `secret shorter than 32 bytes must be rejected at startup`() {
        val u = JwtUtil("too-short", expiration = 60_000, blacklistExpirationMs = 60_000, environment = env)
        assertThrows<IllegalArgumentException> { u.validateSecret() }
    }
}
