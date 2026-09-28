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
 * 控制器路由冒烟（ActionController / EquipmentController / ShopController 各两条）：
 *  - 匿名（缺鉴权）请求必须被安全链拒绝且绝不 500——本工程未注册 AuthenticationEntryPoint，
 *    匿名落到 Spring Security 默认 Http403ForbiddenEntryPoint → 403（与
 *    SecurityConfigPathCoverageTest 同口径，断言 401/403 皆可，改 401 会变更既有状态码契约）；
 *  - 携带真实 JWT（注册接口签发）走正常路径必须 200，证明鉴权注入与控制器分发链路完整。
 * 全量 @SpringBootTest + H2（application-test.yml），风格对齐 AuthControllerTest 的
 * 「匿名拒绝 / 认证通过」成对断言。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControllerSmokeTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    /** 注册新用户并取回 JWT（/api/auth/register 在白名单内，测试 profile 关闭限流） */
    private fun registerToken(): String {
        val username = "smoke_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"password123","nickname":"冒烟用户"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andReturn()
        return JsonPath.read(result.response.contentAsString, "\$.token")
    }

    /** 匿名请求必须被拒绝（401/403），且绝不能 500 */
    private fun expectAnonymousRejected(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, label: String) {
        val status = mockMvc.perform(builder).andReturn().response.status
        assertTrue(status == 401 || status == 403, "$label：匿名请求应 401/403，实际 $status")
        assertTrue(status < 500, "$label：匿名请求不得 500，实际 $status")
    }

    // ==================== ActionController ====================

    @Test
    fun `action cultivate without auth must be rejected`() {
        expectAnonymousRejected(post("/api/action/cultivate"), "POST /api/action/cultivate")
    }

    @Test
    fun `action cultivate with jwt must return 200`() {
        val token = registerToken()
        mockMvc.perform(post("/api/action/cultivate").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.soulPowerGained").exists())
    }

    // ==================== EquipmentController ====================

    @Test
    fun `equipment list without auth must be rejected`() {
        expectAnonymousRejected(get("/api/equipment"), "GET /api/equipment")
    }

    @Test
    fun `equipment list with jwt must return 200`() {
        val token = registerToken()
        mockMvc.perform(get("/api/equipment").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.profile").exists())
            .andExpect(jsonPath("$.equippedRings").isArray)
    }

    // ==================== ShopController ====================

    @Test
    fun `shop normal list without auth must be rejected`() {
        expectAnonymousRejected(get("/api/shop/normal"), "GET /api/shop/normal")
    }

    @Test
    fun `shop normal list with jwt must return 200`() {
        val token = registerToken()
        mockMvc.perform(get("/api/shop/normal").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }
}
