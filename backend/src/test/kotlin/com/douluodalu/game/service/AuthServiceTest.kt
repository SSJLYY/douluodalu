package com.douluodalu.game.service

import com.douluodalu.game.dto.LoginRequest
import com.douluodalu.game.dto.RegisterRequest
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.UserRepository
import com.douluodalu.game.security.JwtUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

class AuthServiceTest {
    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var playerProfileRepository: PlayerProfileRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var jwtUtil: JwtUtil

    @InjectMocks
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `register should create user and return auth response`() {
        val request = RegisterRequest("testuser", "password123", "测试用户")
        val savedUser = UserEntity(id = 1, username = "testuser", nickname = "测试用户", passwordHash = "hashed")
        val savedProfile = PlayerProfileEntity(userId = 1)

        whenever(passwordEncoder.encode("password123")).thenReturn("hashed")
        doReturn(savedUser).whenever(userRepository).save(any())
        doReturn(savedProfile).whenever(playerProfileRepository).save(any())
        doReturn("test-token").whenever(jwtUtil).generateToken(1, "testuser")

        val response = authService.register(request)

        assertNotNull(response)
        assertEquals("test-token", response.token)
        assertEquals(1, response.userId)
        assertEquals("testuser", response.username)
        assertEquals("测试用户", response.nickname)
    }

    @Test
    fun `login should return auth response for valid credentials`() {
        val request = LoginRequest("testuser", "password123")
        val user = UserEntity(id = 1, username = "testuser", nickname = "测试用户", passwordHash = "hashed")

        whenever(userRepository.findByUsername("testuser")).thenReturn(user)
        whenever(passwordEncoder.matches("password123", "hashed")).thenReturn(true)
        doReturn(user).whenever(userRepository).save(any())
        doReturn("test-token").whenever(jwtUtil).generateToken(1, "testuser")

        val response = authService.login(request)

        assertNotNull(response)
        assertEquals("test-token", response.token)
        assertEquals(1, response.userId)
    }

    @Test
    fun `login should throw exception for invalid credentials`() {
        val request = LoginRequest("testuser", "wrongpassword")
        val user = UserEntity(id = 1, username = "testuser", nickname = "测试用户", passwordHash = "hashed")

        whenever(userRepository.findByUsername("testuser")).thenReturn(user)
        whenever(passwordEncoder.matches("wrongpassword", "hashed")).thenReturn(false)

        assertThrows<IllegalArgumentException> {
            authService.login(request)
        }
    }

    @Test
    fun `login should throw exception for non-existent user`() {
        val request = LoginRequest("nonexistent", "password123")

        whenever(userRepository.findByUsername("nonexistent")).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            authService.login(request)
        }
    }
}
