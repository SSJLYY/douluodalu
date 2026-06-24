package com.example.garygame.web

import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit

@Serializable
data class AuthResponse(val token: String, val username: String, val userId: Int)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class LoginRegisterRequest(val username: String, val password: String)

@Serializable
data class SaveGameRequest(val gameData: String)

@Serializable
data class GameDataResponse(val gameData: String?, val updatedAt: Long)

object ApiClient {
    private var token: String? = null
    private var baseUrl: String = ""
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun init() {
        baseUrl = window.location.origin
        token = localStorage.getItem("auth_token")
    }

    fun isLoggedIn(): Boolean = token != null
    fun getToken(): String? = token

    fun logout() {
        // 通知后端撤销 token
        val currentToken = token
        if (currentToken != null) {
            kotlinx.coroutines.MainScope().launch {
                try {
                    window.fetch("$baseUrl/api/logout", RequestInit(
                        method = "POST",
                        headers = Headers().apply { append("Authorization", "Bearer $currentToken") }
                    )).await()
                } catch (_: Exception) {}
            }
        }
        token = null
        localStorage.removeItem("auth_token")
    }

    suspend fun register(username: String, password: String): Result<AuthResponse> {
        return try {
            val body = json.encodeToString(LoginRegisterRequest.serializer(), LoginRegisterRequest(username, password))
            val response = window.fetch("$baseUrl/api/register", RequestInit(
                method = "POST",
                headers = Headers().apply { append("Content-Type", "application/json") },
                body = body
            )).await()

            if (response.ok) {
                val text = response.text().await()
                val auth = json.decodeFromString<AuthResponse>(text)
                token = auth.token
                localStorage.setItem("auth_token", auth.token)
                Result.success(auth)
            } else {
                val text = response.text().await()
                val error = try { json.decodeFromString<ErrorResponse>(text).error } catch (_: Exception) { "注册失败" }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    suspend fun login(username: String, password: String): Result<AuthResponse> {
        return try {
            val body = json.encodeToString(LoginRegisterRequest.serializer(), LoginRegisterRequest(username, password))
            val response = window.fetch("$baseUrl/api/login", RequestInit(
                method = "POST",
                headers = Headers().apply { append("Content-Type", "application/json") },
                body = body
            )).await()

            if (response.ok) {
                val text = response.text().await()
                val auth = json.decodeFromString<AuthResponse>(text)
                token = auth.token
                localStorage.setItem("auth_token", auth.token)
                Result.success(auth)
            } else {
                val text = response.text().await()
                val error = try { json.decodeFromString<ErrorResponse>(text).error } catch (_: Exception) { "登录失败" }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    suspend fun saveGame(gameData: String): Boolean {
        return try {
            val body = json.encodeToString(SaveGameRequest.serializer(), SaveGameRequest(gameData))
            val response = window.fetch("$baseUrl/api/game/save", RequestInit(
                method = "POST",
                headers = Headers().apply {
                    append("Content-Type", "application/json")
                    append("Authorization", "Bearer $token")
                },
                body = body
            )).await()
            response.ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loadGame(): String? {
        return try {
            val response = window.fetch("$baseUrl/api/game/load", RequestInit(
                method = "GET",
                headers = Headers().apply { append("Authorization", "Bearer $token") }
            )).await()

            if (response.ok) {
                val text = response.text().await()
                json.decodeFromString<GameDataResponse>(text).gameData
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
