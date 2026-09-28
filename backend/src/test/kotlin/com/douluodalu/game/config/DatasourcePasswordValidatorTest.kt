package com.douluodalu.game.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

/**
 * DB 密码 prod 拦测（安全补强，复用 JwtUtil.validateSecret 的 prod 校验机制）：
 * prod profile 下默认占位符/空密码必须拒绝启动；其他 profile 仅告警放行，
 * 保证本地开发与 H2 测试（password 为空串）不受影响。
 */
class DatasourcePasswordValidatorTest {

    private fun validator(password: String, vararg profiles: String): DatasourcePasswordValidator {
        val env = MockEnvironment()
        if (profiles.isNotEmpty()) env.setActiveProfiles(*profiles)
        return DatasourcePasswordValidator(password, env)
    }

    @Test
    fun `prod profile with default placeholder must fail startup`() {
        assertThrows(IllegalStateException::class.java) {
            validator("change-me", "prod").validatePassword()
        }
        assertThrows(IllegalStateException::class.java) {
            validator("change-me", "production").validatePassword()
        }
    }

    @Test
    fun `prod profile with blank password must fail startup`() {
        assertThrows(IllegalStateException::class.java) {
            validator("", "prod").validatePassword()
        }
    }

    @Test
    fun `prod profile with real password must pass`() {
        assertDoesNotThrow {
            validator("s3cr3t-from-DB_PASSWORD", "prod").validatePassword()
        }
    }

    @Test
    fun `non-prod profile keeps default placeholder or blank (local and H2 tests)`() {
        assertDoesNotThrow { validator("change-me").validatePassword() }
        assertDoesNotThrow { validator("change-me", "dev").validatePassword() }
        // application-test.yml 的 H2 连接密码为空串，测试 profile 启动不受拦截
        assertDoesNotThrow { validator("", "test").validatePassword() }
    }
}
