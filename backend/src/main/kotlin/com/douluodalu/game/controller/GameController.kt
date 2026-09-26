package com.douluodalu.game.controller

import com.douluodalu.game.dto.*
import com.douluodalu.game.service.CheckInService
import com.douluodalu.game.service.DailyQuestService
import com.douluodalu.game.service.GameService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/game")
@Tag(name = "游戏核心", description = "游戏状态、离线奖励、每日签到、每日任务")
class GameController(
    private val gameService: GameService,
    private val checkInService: CheckInService,
    private val dailyQuestService: DailyQuestService
) {
    @Operation(summary = "获取游戏状态", description = "获取玩家完整游戏数据")
    @GetMapping("/state")
    fun getState(auth: Authentication): ResponseEntity<GameStateResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.getGameState(userId))
    }

    @Operation(summary = "领取离线奖励", description = "领取离线期间积累的金币和魂力")
    @PostMapping("/offline-claim")
    fun claimOffline(auth: Authentication): ResponseEntity<OfflineRewardResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.claimOfflineReward(userId))
    }

    @Operation(summary = "每日签到", description = "7 日循环奖励；当日已签返回 400")
    @PostMapping("/checkin")
    fun checkIn(auth: Authentication): ResponseEntity<CheckInResult> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(checkInService.checkIn(userId))
    }

    @Operation(
        summary = "补签", description = "只能补签昨天：花费 500 金币修复连签，不补发当日奖励；" +
                "无历史签到/昨日已签/金币不足返回 200 + success=false"
    )
    @PostMapping("/checkin/makeup")
    fun makeupCheckIn(auth: Authentication): ResponseEntity<MakeupResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(checkInService.makeup(userId))
    }

    @Operation(summary = "领取每日任务奖励", description = "进度未达标/今日已领取/未知任务返回 400")
    @PostMapping("/quests/claim")
    fun claimQuest(auth: Authentication, @Valid @RequestBody request: ClaimQuestRequest): ResponseEntity<ClaimQuestResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(dailyQuestService.claim(userId, request.questId))
    }
}

/** 领取每日任务奖励请求体 */
data class ClaimQuestRequest(
    @field:NotBlank(message = "questId 不能为空")
    val questId: String
)
