package com.douluodalu.game.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * 安全审计回归（限流完整性）：
 * 1) 伪造 X-Forwarded-For 头不得绕过按 IP 限流（旧实现无条件取 XFF 第一段，
 *    攻击者逐请求轮换即可让登录 10 RPM 防爆破失效）；
 * 2) 可信代理（nginx 同机反代，remoteAddr=回环/内网）背后取 XFF 的【最后一段】——
 *    nginx $proxy_add_x_forwarded_for 是追加语义，最后一段才是代理看到的真实客户端；
 * 3) auth 桶与 global 桶相互独立；429 响应带 Retry-After。
 *
 * 桶容量取 1（auth=1/global=1）：refillGreedy 是按时间连续滴注式回填，容量 2 会让
 * 背靠背两次请求都在容量内通过；容量 1 时两次相邻请求间回填量远不足 1 个令牌，
 * 结果确定（下一次请求必然 429）。
 */
class RateLimitInterceptorTest {

    /** global=1 / auth=1：单次请求即耗尽当前桶，断言最简且确定 */
    private lateinit var interceptor: RateLimitInterceptor

    @BeforeEach
    fun setUp() {
        interceptor = RateLimitInterceptor(authPerMinute = 1, globalPerMinute = 1)
    }

    private fun request(
        remote: String,
        uri: String = "/api/game/state",
        method: String = "GET",
        headers: Map<String, String> = emptyMap()
    ): HttpServletRequest {
        val req = mock(HttpServletRequest::class.java)
        `when`(req.remoteAddr).thenReturn(remote)
        `when`(req.requestURI).thenReturn(uri)
        `when`(req.method).thenReturn(method)
        whenever(req.getHeader(any())).thenAnswer { inv -> headers[inv.getArgument(0)] }
        return req
    }

    /** mock 响应并返回状态码读取器（拦截器 setStatus 的捕获） */
    private fun response(): Pair<HttpServletResponse, () -> Int> {
        val resp = mock(HttpServletResponse::class.java)
        val status = AtomicInteger(200)
        doAnswer { status.set(it.getArgument(0)) }.whenever(resp).setStatus(any())
        `when`(resp.writer).thenReturn(PrintWriter(StringWriter()))
        return resp to { status.get() }
    }

    private fun consume(req: HttpServletRequest): Int {
        val (resp, status) = response()
        val pass = interceptor.preHandle(req, resp, Any())
        return if (pass) 200 else status()
    }

    @Test
    fun `forged XFF from untrusted public remoteAddr must not bypass rate limit`() {
        // 公网直连（非可信代理）：XFF 全部弃用，按 TCP 对端地址算桶
        val r1 = consume(request("203.0.113.7", headers = mapOf("X-Forwarded-For" to "9.9.9.1")))
        val r2 = consume(request("203.0.113.7", headers = mapOf("X-Forwarded-For" to "9.9.9.2")))
        val r3 = consume(request("203.0.113.7", headers = mapOf("X-Forwarded-For" to "9.9.9.3")))
        assertEquals(200, r1)
        // 旧实现下 r2/r3 会因 XFF 轮换拿到新桶而 200；现在必须与 r1 同桶
        assertEquals(429, r2)
        assertEquals(429, r3)
    }

    @Test
    fun `untrusted remoteAddr must not be fooled by spoofed X-Real-IP`() {
        val r1 = consume(request("198.51.100.4", headers = mapOf("X-Real-IP" to "9.9.9.9")))
        val r2 = consume(request("198.51.100.4", headers = mapOf("X-Real-IP" to "8.8.8.8")))
        assertEquals(200, r1)
        assertEquals(429, r2)
    }

    @Test
    fun `trusted proxy uses last XFF segment as the client key`() {
        // 回环 = 可信代理（DEPLOY.md：nginx 同机 proxy_pass 127.0.0.1:8080）。
        // 客户端可伪造的 XFF 第一段弃用，取代理追加的真实客户端（最后一段）。
        val r1 = consume(request("127.0.0.1", headers = mapOf("X-Forwarded-For" to "8.8.8.1, 6.6.6.6")))
        val r2 = consume(request("127.0.0.1", headers = mapOf("X-Forwarded-For" to "8.8.8.2, 6.6.6.6")))
        // 换了真实客户端（最后一段 7.7.7.7）应拿到独立桶
        val r3 = consume(request("127.0.0.1", headers = mapOf("X-Forwarded-For" to "8.8.8.3, 7.7.7.7")))
        assertEquals(200, r1)
        assertEquals(429, r2)
        assertEquals(200, r3)
    }

    @Test
    fun `trusted proxy without XFF falls back to X-Real-IP then remoteAddr`() {
        val r1 = consume(request("192.168.1.10", headers = mapOf("X-Real-IP" to "6.6.6.9")))
        val r2 = consume(request("192.168.1.10", headers = mapOf("X-Real-IP" to "6.6.6.9")))
        // 无转发头时回落 remoteAddr：与 X-Real-IP 桶互不相干，是全新桶
        val r3 = consume(request("192.168.1.10"))
        assertEquals(200, r1)
        assertEquals(429, r2)
        assertEquals(200, r3)
    }

    @Test
    fun `auth bucket is independent from global bucket`() {
        val auth1 = consume(request("10.1.1.1", uri = "/api/auth/login"))
        val auth2 = consume(request("10.1.1.1", uri = "/api/auth/login"))
        // auth 桶耗尽不影响 global 桶
        val global = consume(request("10.1.1.1", uri = "/api/game/state"))
        assertEquals(200, auth1)
        assertEquals(429, auth2)
        assertEquals(200, global)
    }

    @Test
    fun `429 response carries Retry-After and json body`() {
        val resp = mock(HttpServletResponse::class.java)
        val body = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(body))

        val passed = interceptor.preHandle(
            request("10.9.9.9", uri = "/api/auth/login"), resp, Any()
        )
        val passed2 = interceptor.preHandle(
            request("10.9.9.9", uri = "/api/auth/login"), resp, Any()
        )
        assertTrue(passed)
        assertFalse(passed2)
        org.mockito.Mockito.verify(resp).setHeader("Retry-After", "60")
        assertTrue(body.toString().contains("TOO_MANY_REQUESTS"))
    }

    // ==================== 第二十六轮 限流分级：GET /api/auth/me 走 global 桶 ====================

    @Test
    fun `auth me reads ride the global bucket while login stays strictly limited`() {
        // 生产同参：auth=10 RPM、global=300 RPM；同一 IP 背靠背 11 次请求（间隔回填可忽略）
        val rl = RateLimitInterceptor(authPerMinute = 10, globalPerMinute = 300)

        // GET /api/auth/me 若仍在 auth 桶，第 11 次必然 429；走 global 桶（300）则全部放行
        val meStatuses = (1..11).map { consumeWith(rl, request("10.1.2.3", uri = "/api/auth/me")) }
        assertEquals(11, meStatuses.count { it == 200 }, "11 次 me 读请求不得 429：$meStatuses")
        assertTrue(meStatuses.all { it == 200 })

        // 同一 IP 的 login（写端点）仍在 auth 桶：10 RPM 触顶，第 11 次 429——防爆破能力不变
        val loginStatuses = (1..11).map { consumeWith(rl, request("10.1.2.3", uri = "/api/auth/login")) }
        assertEquals(200, loginStatuses[0])
        assertEquals(429, loginStatuses[10], "第 11 次 login 必须 429（auth 桶 10 RPM）")
    }

    @Test
    fun `auth me shares the global bucket with other global endpoints`() {
        // me 与 /api/game/state 共用同一 global 桶：其一耗尽，另一也 429（同 IP）
        val g1 = consume(request("10.5.5.5", uri = "/api/game/state"))
        val me = consume(request("10.5.5.5", uri = "/api/auth/me"))
        // global 桶容量 1 已被 state 耗尽 → me 也被限（证明不是独立桶）；login 不受影响（独立 auth 桶）
        val login = consume(request("10.5.5.5", uri = "/api/auth/login"))
        assertEquals(200, g1)
        assertEquals(429, me, "me 必须与 global 桶共享配额（否则为独立桶）")
        assertEquals(200, login, "auth 桶独立于 global：global 耗尽不得影响 login")
    }

    /** 用指定拦截器消费一次请求（setUp 的 interceptor 是 1/1 桶，分级测试需要生产参数） */
    private fun consumeWith(rl: RateLimitInterceptor, req: HttpServletRequest): Int {
        val (resp, status) = response()
        return if (rl.preHandle(req, resp, Any())) 200 else status()
    }
}
