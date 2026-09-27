package com.douluodalu.game.config

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.socket.config.annotation.SockJsServiceRegistration
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration

/**
 * 第二十八轮 WS origin 收敛（第二十四轮遗留）：
 *  - resolveAllowedOrigins：env 解析纯函数（缺省 "*" 向后兼容、逗号分隔/trim/空段丢弃）；
 *  - registerStompEndpoints：env 解析结果应用到 /ws 与 /ws-native 两端点的
 *    allowedOriginPatterns（mock 注册器 + 精确变参验证；origins 计算与注册经
 *    registerEndpoints 解耦，env→origins 的映射由纯函数用例覆盖）。
 */
class WebSocketConfigTest {

    // ======== resolveAllowedOrigins：env 解析纯函数 ========

    @Test
    fun `resolveAllowedOrigins falls back to wildcard when env unset or blank`() {
        assertArrayEquals(arrayOf("*"), WebSocketConfig.resolveAllowedOrigins(null), "未设置应缺省 *（向后兼容）")
        assertArrayEquals(arrayOf("*"), WebSocketConfig.resolveAllowedOrigins(""), "空串应缺省 *")
        assertArrayEquals(arrayOf("*"), WebSocketConfig.resolveAllowedOrigins(" , , "), "全空段应缺省 *")
    }

    @Test
    fun `resolveAllowedOrigins splits on comma trims and drops empty segments`() {
        assertArrayEquals(
            arrayOf("http://localhost:3000", "http://127.0.0.1:3000"),
            WebSocketConfig.resolveAllowedOrigins(" http://localhost:3000 , , http://127.0.0.1:3000 ,")
        )
    }

    // ======== 端点注册：allowedOriginPatterns 生效 ========

    @Test
    fun `registerStompEndpoints applies env-resolved origins to both endpoints`() {
        val config = WebSocketConfig()
        // 测试进程未设置 WS_ALLOWED_ORIGINS → 期望即缺省 "*"（与 SecurityConfig CORS_ORIGINS 先例同款 env 读取）
        val expected = WebSocketConfig.resolveAllowedOrigins(System.getenv(WebSocketConfig.ENV_WS_ALLOWED_ORIGINS))
        val sockJs = mockRegistration()
        val native = mock<StompWebSocketEndpointRegistration>()
        whenever(native.setAllowedOriginPatterns(anyVararg<String>())).thenReturn(native)
        val registry = mock<StompEndpointRegistry>()
        whenever(registry.addEndpoint("/ws")).thenReturn(sockJs)
        whenever(registry.addEndpoint("/ws-native")).thenReturn(native)

        config.registerStompEndpoints(registry)

        verify(sockJs).setAllowedOriginPatterns(*expected)
        verify(native).setAllowedOriginPatterns(*expected)
        verify(sockJs).withSockJS()
    }

    @Test
    fun `registerEndpoints registers both endpoints with injected origins`() {
        val config = WebSocketConfig()
        val sockJs = mockRegistration()
        val native = mock<StompWebSocketEndpointRegistration>()
        whenever(native.setAllowedOriginPatterns(anyVararg<String>())).thenReturn(native)
        val registry = mock<StompEndpointRegistry>()
        whenever(registry.addEndpoint("/ws")).thenReturn(sockJs)
        whenever(registry.addEndpoint("/ws-native")).thenReturn(native)

        config.registerEndpoints(registry, arrayOf("http://a:3000", "https://game.example.com"))

        verify(sockJs).setAllowedOriginPatterns("http://a:3000", "https://game.example.com")
        verify(native).setAllowedOriginPatterns("http://a:3000", "https://game.example.com")
    }

    private fun mockRegistration(): StompWebSocketEndpointRegistration {
        val reg = mock<StompWebSocketEndpointRegistration>()
        whenever(reg.setAllowedOriginPatterns(anyVararg<String>())).thenReturn(reg)
        whenever(reg.withSockJS()).thenReturn(mock<SockJsServiceRegistration>())
        return reg
    }
}
