package com.douluodalu.game.config

import com.douluodalu.game.security.JwtAuthFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val rateLimitInterceptor: RateLimitInterceptor,
    // 文档端点是否公开，与 springdoc.api-docs.enabled 联动：
    // 非 prod 默认 true（放行 Swagger）；prod profile 在 application-prod.yml 里置 false，
    // 既关闭 springdoc 本身，也不再放行文档路径（届时 /v3/api-docs 等需认证，且实际返回 404）
    @Value("\${springdoc.api-docs.enabled:true}") private val docsEnabled: Boolean
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfig()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/rank/**").permitAll()
                    .requestMatchers("/api/health").permitAll()
                    // Actuator 存活探针：仅放行 health（与自定义 /api/health 并存，
                    // 前者供容器/负载均衡探活，后者供业务侧查看依赖状态）；
                    // 其余端点（env/beans/configprops 等）不在暴露清单且不放行
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    // WebSocket/STOMP：浏览器原生 WS 握手与 SockJS 各传输（/ws/info、
                    // /ws/{server}/{session}/xhr 等）无法携带 Authorization 头，
                    // 握手一律放行（SockJS 子路径 + 原生端点 /ws-native），鉴权在应用层消息处理中做
                    .requestMatchers("/ws", "/ws/**", "/ws-native").permitAll()
                    .anyRequest().authenticated()
                if (docsEnabled) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                }
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun webMvc(): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
        }
    }

    private fun corsConfig(): CorsConfigurationSource {
        val allowedOrigins = System.getenv("CORS_ORIGINS")
            ?.split(",")
            ?.map { it.trim() }
            ?: listOf("http://localhost:3000", "http://localhost:8080")

        val config = CorsConfiguration().apply {
            this.allowedOrigins = allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
