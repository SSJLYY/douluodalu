package com.douluodalu.game.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ======== 认证请求 ========
data class RegisterRequest(
    @field:NotBlank @field:Size(min = 3, max = 32)
    val username: String,
    @field:NotBlank @field:Size(min = 6, max = 64)
    val password: String,
    @field:NotBlank @field:Size(min = 2, max = 32)
    val nickname: String
)

data class LoginRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String
)

// ======== 认证响应 ========
data class AuthResponse(
    val token: String,
    val userId: Long,
    val username: String,
    val nickname: String
)

data class UserInfoResponse(
    val userId: Long,
    val username: String,
    val nickname: String,
    val avatarUrl: String?
)
