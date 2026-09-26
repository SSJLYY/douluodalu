package com.douluodalu.game.controller

import jakarta.validation.Validation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 安全审计回归（输入校验）：宗门写端点此前无 @Valid/校验注解——
 * 超长 name/description 直达 DB 触发 Data too long（500），负数捐献金额
 * 依赖服务层单点防御。注解上限与 V1 迁移列宽严格对齐：
 * guild.name VARCHAR(64)、guild.notice VARCHAR(500)。
 */
class GuildRequestValidationTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    private fun violations(request: Any) =
        validator.validate(request).map { it.propertyPath.toString() to it.message }.toSet()

    @Test
    fun `createGuild name must be 1-64 chars matching column width`() {
        assertTrue(violations(CreateGuildRequest(name = "", description = "")).isNotEmpty())
        assertTrue(violations(CreateGuildRequest(name = "a".repeat(65), description = "")).isNotEmpty())
        assertTrue(violations(CreateGuildRequest(name = "a".repeat(64), description = "")).isEmpty())
    }

    @Test
    fun `createGuild description must be at most 500 chars matching column width`() {
        assertTrue(violations(CreateGuildRequest(name = "宗门", description = "d".repeat(501))).isNotEmpty())
        assertTrue(violations(CreateGuildRequest(name = "宗门", description = "d".repeat(500))).isEmpty())
    }

    @Test
    fun `donate amount must be positive at the parameter layer`() {
        assertTrue(violations(DonateRequest(amount = 0)).isNotEmpty())
        assertTrue(violations(DonateRequest(amount = -100)).isNotEmpty())
        assertTrue(violations(DonateRequest(amount = 1)).isEmpty())
    }

    @Test
    fun `kick and transfer target user id must be positive`() {
        assertTrue(violations(KickMemberRequest(targetUserId = 0)).isNotEmpty())
        assertTrue(violations(KickMemberRequest(targetUserId = -5)).isNotEmpty())
        assertTrue(violations(KickMemberRequest(targetUserId = 1)).isEmpty())
        assertTrue(violations(TransferLeaderRequest(targetUserId = 0)).isNotEmpty())
        assertTrue(violations(TransferLeaderRequest(targetUserId = 1)).isEmpty())
    }
}
