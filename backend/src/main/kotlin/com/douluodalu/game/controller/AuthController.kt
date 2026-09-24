package com.douluodalu.game.controller

import com.douluodalu.game.dto.*
import com.douluodalu.game.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户注册、登录、信息查询")
class AuthController(
    private val authService: AuthService
) {
    @Operation(summary = "用户注册", description = "创建新用户账号")
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.register(request))
    }

    @Operation(summary = "用户登录", description = "使用用户名和密码登录")
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.login(request))
    }

    @Operation(summary = "获取当前用户信息", description = "通过 JWT Token 获取用户信息")
    @GetMapping("/me")
    fun getMe(auth: Authentication): ResponseEntity<UserInfoResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(authService.getUserInfo(userId))
    }

    @Operation(summary = "用户登出", description = "拉黑当前 Token 并记录登出时间（用于离线收益）")
    @PostMapping("/logout")
    fun logout(
        @RequestHeader(value = "Authorization", required = false) authorization: String?
    ): ResponseEntity<SimpleResponse> {
        val token = authorization?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }
            ?: return ResponseEntity.badRequest().body(SimpleResponse(false, "缺少 Authorization 头"))
        authService.logout(token)
        return ResponseEntity.ok(SimpleResponse(true, "登出成功"))
    }
}
