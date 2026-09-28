package com.douluodalu.game.controller

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * 每日副本路由冒烟（第二十九轮，参照 ControllerSmokeTest 的「匿名拒绝 / 认证通过」成对断言）：
 *  - 匿名（缺鉴权）请求必须被安全链拒绝且绝不 500（401/403 皆可，本工程未注册
 *    AuthenticationEntryPoint → Spring Security 默认 Http403ForbiddenEntryPoint → 403）；
 *  - 携带真实 JWT（注册接口签发）走正常路径必须 200，证明鉴权注入与控制器分发链路完整。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DungeonControllerSmokeTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    /** 注册新用户并取回 JWT（/api/auth/register 在白名单内，测试 profile 关闭限流） */
    private fun registerToken(): String {
        val username = "dungeon_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"password123","nickname":"副本用户"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andReturn()
        return JsonPath.read(result.response.contentAsString, "\$.token")
    }

    @Test
    fun `dungeon fight without auth must be rejected`() {
        val status = mockMvc.perform(post("/api/dungeon/fight/0")).andReturn().response.status
        assertTrue(status == 401 || status == 403, "匿名请求应 401/403，实际 $status")
        assertTrue(status < 500, "匿名请求不得 500，实际 $status")
    }

    @Test
    fun `dungeon state with jwt must return 200 with five tiers`() {
        val token = registerToken()
        mockMvc.perform(get("/api/dungeon/state").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tierCompleted").value(-1))
            .andExpect(jsonPath("$.tiers.length()").value(5))
    }
}
