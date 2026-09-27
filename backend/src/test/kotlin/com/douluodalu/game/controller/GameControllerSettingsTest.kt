package com.douluodalu.game.controller

import com.douluodalu.game.config.GlobalExceptionHandler
import com.douluodalu.game.dto.ProfileDto
import com.douluodalu.game.service.CheckInService
import com.douluodalu.game.service.DailyQuestService
import com.douluodalu.game.service.GameService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * 第二十六轮设置端点（PUT /api/game/settings）控制器层回归：
 *  - 空 body（三字段全缺 → Jackson 反序列化为全 null）必须被 @Valid/@NotNull 拒为 400
 *    （「想保持不动也要显式原值回传」，防前端漏传被静默当成不改），且不得触达服务层；
 *  - 显式传值时走 PUT 分发到 GameService.updateSettings，响应复用 ProfileDto。
 * standalone MockMvc + GlobalExceptionHandler：校验异常 → 400 VALIDATION_ERROR 的真实链路。
 */
class GameControllerSettingsTest {

    @Mock
    private lateinit var gameService: GameService

    @Mock
    private lateinit var checkInService: CheckInService

    @Mock
    private lateinit var dailyQuestService: DailyQuestService

    private lateinit var mockMvc: MockMvc

    private val auth: Authentication = UsernamePasswordAuthenticationToken(1L, null, emptyList())

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        mockMvc = MockMvcBuilders.standaloneSetup(GameController(gameService, checkInService, dailyQuestService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun profileDto(autoBattle: Boolean, autoAdvanceMap: Boolean, autoBreakthrough: Boolean) = ProfileDto(
        level = 5, gold = 0, soulPower = 0, bossCoin = 0,
        martialSoulName = null, chosenSchool = null,
        currentMapId = 0, currentStage = 1, currentHp = 100, battleSoulPower = 100,
        totalBattleWins = 0, totalBattleLosses = 0, towerFloor = 0, killingIntent = 0,
        prestigeCount = 0, talentPoints = 0, codexKills = 0,
        autoBattle = autoBattle, autoAdvanceMap = autoAdvanceMap, autoBreakthrough = autoBreakthrough,
        tutorialStep = 1
    )

    @Test
    fun `updateSettings with empty body must be rejected 400 without touching the service`() {
        mockMvc.perform(
            put("/api/game/settings").principal(auth).contentType(MediaType.APPLICATION_JSON).content("{}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("autoBattle")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("autoAdvanceMap")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("autoBreakthrough")))

        // 400 拦在参数层：服务层不得被调用，存档零改动
        verify(gameService, never()).updateSettings(any(), any())
    }

    @Test
    fun `updateSettings with explicit values must delegate to service and return updated profile`() {
        val resp = profileDto(autoBattle = true, autoAdvanceMap = false, autoBreakthrough = false)
        whenever(gameService.updateSettings(1L, UpdateSettingsRequest(true, false, false))).thenReturn(resp)

        mockMvc.perform(
            put("/api/game/settings").principal(auth).contentType(MediaType.APPLICATION_JSON)
                .content("""{"autoBattle":true,"autoAdvanceMap":false,"autoBreakthrough":false}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.autoBattle").value(true))
            .andExpect(jsonPath("$.autoAdvanceMap").value(false))
            .andExpect(jsonPath("$.autoBreakthrough").value(false))

        // 委托校验：userId 取自 Authentication，请求体逐字段透传
        verify(gameService).updateSettings(1L, UpdateSettingsRequest(true, false, false))
    }
}
