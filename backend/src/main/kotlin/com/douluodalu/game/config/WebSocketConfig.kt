package com.douluodalu.game.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        // 启用简单的内存消息代理，用于广播消息给订阅了这些目的地的客户端
        config.enableSimpleBroker("/topic", "/queue")
        // 客户端发送消息到服务器的目的地前缀
        config.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // 注册 STOMP 端点，客户端将通过此端点连接到 WebSocket 服务器
        // SockJS 端点（提供 /ws/info 探测与各降级传输，保留兼容）
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()
        // 原生 WebSocket 端点：前端 @stomp/stompjs 直连（ws://host/ws-native），无需 sockjs-client
        registry.addEndpoint("/ws-native")
            .setAllowedOriginPatterns("*")
    }
}
