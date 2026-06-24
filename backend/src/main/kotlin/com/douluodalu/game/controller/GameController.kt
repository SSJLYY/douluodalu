package com.douluodalu.game.controller

import com.douluodalu.game.dto.*
import com.douluodalu.game.service.GameService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/game")
class GameController(
    private val gameService: GameService
) {
    @GetMapping("/state")
    fun getState(auth: Authentication): ResponseEntity<GameStateResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.getGameState(userId))
    }

    @PostMapping("/offline-claim")
    fun claimOffline(auth: Authentication): ResponseEntity<OfflineRewardResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.claimOfflineReward(userId))
    }
}
