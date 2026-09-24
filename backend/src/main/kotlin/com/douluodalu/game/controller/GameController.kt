package com.douluodalu.game.controller

import com.douluodalu.game.dto.*
import com.douluodalu.game.service.GameService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/game")
@Tag(name = "游戏核心", description = "游戏状态、离线奖励")
class GameController(
    private val gameService: GameService
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
}
