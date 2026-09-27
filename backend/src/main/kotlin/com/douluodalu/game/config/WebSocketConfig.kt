package com.douluodalu.game.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * 第二十四轮遗留生产收敛（第二十八轮落地）：/ws 与 /ws-native 两个端点的允许来源不再写死
 * "*"——读环境变量 WS_ALLOWED_ORIGINS（逗号分隔，解析规则见 resolveAllowedOrigins），
 * 缺省仍为 "*" 保持向后兼容（照 SecurityConfig 的 CORS_ORIGINS 先例；生产用环境变量
 * 覆盖真实域名，application-prod.yml 有示例注释）。
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    companion object {
        /** 环境变量名（application-prod.yml 示例注释与部署清单按此配置） */
        const val ENV_WS_ALLOWED_ORIGINS = "WS_ALLOWED_ORIGINS"

        /**
         * 纯函数解析（单测锚点）：null/空白段 → 缺省 "*"（向后兼容）；逗号分隔、
         * 逐段 trim、空段丢弃。返回数组直接喂 setAllowedOriginPatterns。
         */
        fun resolveAllowedOrigins(envValue: String?): Array<String> {
            val parsed = envValue?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            return if (parsed.isNullOrEmpty()) arrayOf("*") else parsed.toTypedArray()
        }
    }

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        // 启用简单的内存消息代理，用于广播消息给订阅了这些目的地的客户端
        config.enableSimpleBroker("/topic", "/queue")
        // 客户端发送消息到服务器的目的地前缀
        config.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registerEndpoints(registry, resolveAllowedOrigins(System.getenv(ENV_WS_ALLOWED_ORIGINS)))
    }

    /** 端点注册（origins 显式注入：env 解析与注册解耦，单测可直接断言 allowedOriginPatterns 生效） */
    internal fun registerEndpoints(registry: StompEndpointRegistry, origins: Array<String>) {
        // SockJS 端点（提供 /ws/info 探测与各降级传输，保留兼容）
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(*origins)
            .withSockJS()
        // 原生 WebSocket 端点：前端 @stomp/stompjs 直连（ws://host/ws-native），无需 sockjs-client
        registry.addEndpoint("/ws-native")
            .setAllowedOriginPatterns(*origins)
    }
}
