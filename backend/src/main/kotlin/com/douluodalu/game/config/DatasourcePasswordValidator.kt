package com.douluodalu.game.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

/**
 * 数据库密码 prod 拦截（安全补强）：application.yml 里 spring.datasource.password 的默认
 * 占位符是 `change-me`（本地/H2 测试依赖该默认值，不能删除），若带着默认值上生产，
 * 等于把 DB 口令公之于众。复用 JwtUtil.validateSecret 的同一套 prod 校验机制
 * （@PostConstruct + Environment.acceptsProfiles(Profiles.of("prod","production"))）：
 *  - prod/production profile + 默认占位符（或空密码）→ 启动直接失败；
 *  - 其他 profile → 默认值仅告警放行，本地开发与 H2 测试不受影响。
 * JWT secret 的同类拦截已在 JwtUtil 中存在，此处不重复。
 */
@Component
class DatasourcePasswordValidator(
    @Value("\${spring.datasource.password:}") private val password: String,
    private val environment: Environment
) {
    private val log = LoggerFactory.getLogger(DatasourcePasswordValidator::class.java)

    @PostConstruct
    fun validatePassword() {
        val isProd = environment.acceptsProfiles(Profiles.of("prod", "production"))
        if (password == DEFAULT_PASSWORD) {
            if (isProd) {
                throw IllegalStateException(
                    "生产环境禁止使用默认 spring.datasource.password 占位符，请通过 DB_PASSWORD 环境变量注入真实密码。"
                )
            }
            log.warn("[WARN] spring.datasource.password 仍为默认值（未设置 DB_PASSWORD 环境变量）。仅适合本地开发，生产环境将拒绝启动。")
        } else if (isProd && password.isBlank()) {
            throw IllegalStateException(
                "生产环境 spring.datasource.password 不得为空，请通过 DB_PASSWORD 环境变量注入真实密码。"
            )
        }
    }

    companion object {
        /** application.yml 中 spring.datasource.password 的默认占位符（与 JWT 的 change-me 同风格） */
        const val DEFAULT_PASSWORD = "change-me"
    }
}
