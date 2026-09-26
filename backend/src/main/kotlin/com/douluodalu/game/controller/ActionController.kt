package com.douluodalu.game.controller

import com.douluodalu.game.dto.*
import com.douluodalu.game.service.GameService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/action")
@Tag(name = "游戏动作", description = "修炼、突破、战斗")
class ActionController(
    private val gameService: GameService
) {
    @Operation(summary = "修炼", description = "获取魂力")
    @PostMapping("/cultivate")
    fun cultivate(auth: Authentication): ResponseEntity<CultivateResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.cultivate(userId))
    }

    @Operation(summary = "突破", description = "消耗魂力提升境界")
    @PostMapping("/breakthrough")
    fun breakthrough(auth: Authentication): ResponseEntity<BreakthroughResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.breakthrough(userId))
    }

    @Operation(summary = "转生", description = "Lv.50 达标后重置等级，换取永久属性倍率（+10%/转）与 1 天赋点")
    @PostMapping("/prestige")
    fun prestige(auth: Authentication): ResponseEntity<PrestigeResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.prestige(userId))
    }

    @Operation(summary = "战斗", description = "与当前关卡怪物战斗")
    @PostMapping("/battle")
    fun battle(auth: Authentication): ResponseEntity<BattleResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.battle(userId))
    }

    @Operation(summary = "杀戮之都挑战", description = "挑战杀戮之都当前层数，胜利后上升一层")
    @PostMapping("/tower")
    fun towerBattle(auth: Authentication): ResponseEntity<TowerResponse> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.towerBattle(userId))
    }
}
