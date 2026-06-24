package com.douluodalu.game.controller

import com.douluodalu.game.dto.*
import com.douluodalu.game.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.register(request))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.login(request))
    }

    @GetMapping("/me")
    fun getMe(auth: Authentication): ResponseEntity<UserInfoResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(authService.getUserInfo(userId))
    }
}
