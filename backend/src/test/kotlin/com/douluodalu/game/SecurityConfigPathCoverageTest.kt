package com.douluodalu.game

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 安全审计回归（端点认证矩阵）：securityFilterChain 白名单之外的每一个端点
 * 都必须经过 JWT 认证链——匿名请求一律 4xx（本工程未注册 EntryPoint，落到
 * Spring Security 默认 Http403ForbiddenEntryPoint → 403），绝不放行业务数据，
 * 也不能因匿名 principal 转成 500。
 *
 * 覆盖重点：
 *  - 第二十三轮新增的 /api/guild/boss/status、/api/guild/boss/rank（两段路径，
 *    必须落在 anyRequest().authenticated() 内，无任何 permitAll 通配误覆盖）；
 *  - 畸形 Bearer token 必须走 403（JwtAuthFilter 吞掉解析异常），不是 500；
 *  - 白名单 /api/health、/api/rank/level、/actuator/health 匿名可达；
 *  - /api/auth/me 匿名可达但返回 401（白名单内由控制器自行拒绝匿名 principal）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigPathCoverageTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun expectRejected(uri: String, method: String = "GET") {
        val builder = if (method == "POST") {
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(uri)
        } else {
            get(uri)
        }
        val result = mockMvc.perform(builder).andReturn()
        val s = result.response.status
        assertTrue(
            s == 401 || s == 403,
            "匿名请求必须被拒绝（401/403），实际 $s：$method $uri"
        )
        assertTrue(s < 500, "匿名请求不得 500：$method $uri")
    }

    @Test
    fun `every protected endpoint must reject anonymous access`() {
        // 核心游戏（GET/POST 各一）
        expectRejected("/api/game/state")
        expectRejected("/api/game/checkin", method = "POST")
        expectRejected("/api/action/cultivate")
        expectRejected("/api/equipment")
        expectRejected("/api/talent")
        expectRejected("/api/shop/normal")
        expectRejected("/api/guild/list")
        expectRejected("/api/guild/my")
        expectRejected("/api/guild/members")

        // 第二十三轮新增：宗门 Boss 共享血池状态 + 周榜（无 permitAll 通配覆盖的两段路径）
        expectRejected("/api/guild/boss/status")
        expectRejected("/api/guild/boss/rank")
        expectRejected("/api/guild/boss/nonsense")

        // 写端点（POST）
        expectRejected("/api/guild/donate", method = "POST")
        expectRejected("/api/guild/kick", method = "POST")
        expectRejected("/api/guild/boss/challenge", method = "POST")
    }

    @Test
    fun `malformed bearer token must be rejected without 500`() {
        for (token in listOf("not-a-real-jwt", "a.b.c", "!!!")) {
            val result = mockMvc.perform(get("/api/game/state").header("Authorization", "Bearer $token")).andReturn()
            val s = result.response.status
            assertTrue(
                (s == 401 || s == 403) && s < 500,
                "畸形 token 必须被拒（401/403，不得 500），实际 $s：Bearer $token"
            )
        }
    }

    @Test
    fun `anonymous auth me endpoint must be 401 not 500`() {
        // /api/auth 通配白名单可达，但匿名 principal 由控制器显式拒绝为 401（修复前 500）
        expectRejected("/api/auth/me")
    }

    @Test
    fun `whitelist endpoints stay anonymous-accessible`() {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk)
        mockMvc.perform(get("/api/rank/level")).andExpect(status().isOk)
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
    }
}
