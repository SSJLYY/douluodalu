package com.douluodalu.game.controller

import com.douluodalu.game.dto.*
import com.douluodalu.game.service.GameService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/action")
class ActionController(
    private val gameService: GameService
) {
    @PostMapping("/cultivate")
    fun cultivate(auth: Authentication): ResponseEntity<CultivateResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.cultivate(userId))
    }

    @PostMapping("/breakthrough")
    fun breakthrough(auth: Authentication): ResponseEntity<BreakthroughResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.breakthrough(userId))
    }

    @PostMapping("/battle")
    fun battle(auth: Authentication): ResponseEntity<BattleResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.battle(userId))
    }
}
