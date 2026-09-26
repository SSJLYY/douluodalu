package com.douluodalu.game.controller

import com.douluodalu.game.dto.UserInfoResponse
import com.douluodalu.game.service.AuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication

/**
 * 安全审计回归：/api/auth 通配白名单内，GET /api/auth/me 无有效 JWT 时
 * principal 是 "anonymousUser"（String）。旧实现 `as Long` 直接 ClassCastException
 * → 500 UNKNOWN_ERROR；修复后必须返回 401。
 */
class AuthControllerTest {

    @Mock
    private lateinit var authService: AuthService

    @InjectMocks
    private lateinit var controller: AuthController

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `getMe with anonymous principal must return 401 not 500`() {
        // 模拟 Spring Security 匿名认证（principal 为 String，非 Long）
        val anonymous: Authentication = TestingAuthenticationToken("anonymousUser", null, "ROLE_ANONYMOUS")

        val resp = controller.getMe(anonymous)

        assertEquals(HttpStatus.UNAUTHORIZED, resp.statusCode)
        verify(authService, never()).getUserInfo(any())
    }

    @Test
    fun `getMe with null authentication must return 401 not 500`() {
        // 真实链路：匿名请求经 Security 的 getUserPrincipal() 对匿名 token 返回 null，
        // 控制器收到的 Authentication 参数为 null（非空参数声明下旧代码直接 NPE → 500）
        val resp = controller.getMe(null)

        assertEquals(HttpStatus.UNAUTHORIZED, resp.statusCode)
        verify(authService, never()).getUserInfo(any())
    }

    @Test
    fun `getMe with authenticated Long principal returns user info`() {
        whenever(authService.getUserInfo(1L)).thenReturn(UserInfoResponse(1L, "u", "n", null))
        val auth: Authentication = UsernamePasswordAuthenticationToken(1L, null, emptyList())

        val resp = controller.getMe(auth)

        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(1L, resp.body?.userId)
    }
}
