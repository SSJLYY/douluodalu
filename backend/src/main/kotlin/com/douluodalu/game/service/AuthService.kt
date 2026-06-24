package com.douluodalu.game.service

import com.douluodalu.game.dto.*
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.UserRepository
import com.douluodalu.game.security.JwtUtil
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val user = UserEntity(
            username = request.username,
            passwordHash = passwordEncoder.encode(request.password),
            nickname = request.nickname
        )
        try {
            val savedUser = userRepository.save(user)

            val profile = PlayerProfileEntity()
            profile.user = savedUser
            val savedProfile = playerProfileRepository.save(profile)
            savedUser.player = savedProfile

            val token = jwtUtil.generateToken(savedUser.id, savedUser.username)
            return AuthResponse(token, savedUser.id, savedUser.username, savedUser.nickname)
        } catch (e: DataIntegrityViolationException) {
            val rootMsg = if (e.cause != null) e.cause?.message else e.message
            if (rootMsg?.contains("duplicate", ignoreCase = true) == true ||
                rootMsg?.contains("unique", ignoreCase = true) == true) {
                throw IllegalArgumentException("用户名已存在")
            }
            throw e
        }
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        // 统一错误消息：用户不存在和密码错误返回相同消息，防止枚举攻击
        val user = userRepository.findByUsername(request.username)
            ?: throw IllegalArgumentException("用户名或密码错误")
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw IllegalArgumentException("用户名或密码错误")
        }
        user.lastLoginAt = LocalDateTime.now()
        userRepository.save(user)

        val token = jwtUtil.generateToken(user.id, user.username)
        return AuthResponse(token, user.id, user.username, user.nickname)
    }

    @Transactional(readOnly = true)
    fun getUserInfo(userId: Long): UserInfoResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("用户不存在") }
        return UserInfoResponse(user.id, user.username, user.nickname, user.avatarUrl)
    }
}
