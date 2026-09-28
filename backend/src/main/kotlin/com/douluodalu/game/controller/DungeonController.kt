package com.douluodalu.game.controller

import com.douluodalu.game.dto.DungeonFightResponse
import com.douluodalu.game.dto.DungeonStateDto
import com.douluodalu.game.dto.DungeonSweepResponse
import com.douluodalu.game.service.DungeonService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 每日副本控制器（第二十九轮，设计文档 §8）：
 *  - GET  /api/dungeon/state      五难度状态（解锁/今日已挑战/今日已通关/可扫荡/奖励预览）
 *  - POST /api/dungeon/fight/{tier}   挑战难度 tier（0~4，一天一次机会胜败都算已挑战）
 *  - POST /api/dungeon/sweep/{tier}   扫荡难度 tier（历史通关后可用，扣魂力直接拿奖励）
 * tier 非数字路径参数由 GlobalExceptionHandler 的 TYPE_MISMATCH 统一 400；越界层级由
 * 服务层 IllegalArgumentException → 统一 400。认证用 authentication.userId()（既有惯例）。
 */
@RestController
@RequestMapping("/api/dungeon")
@Tag(name = "每日副本", description = "五大难度每日副本：状态、挑战、扫荡")
class DungeonController(
    private val dungeonService: DungeonService
) {
    @Operation(summary = "副本状态", description = "五难度的解锁/今日已挑战/今日已通关/可扫荡/奖励预览")
    @GetMapping("/state")
    fun state(auth: Authentication): ResponseEntity<DungeonStateDto> {
        val userId = auth.userId()
        return ResponseEntity.ok(dungeonService.getState(userId))
    }

    @Operation(summary = "挑战副本", description = "每天一次机会（任选已解锁难度，胜败都算已挑战）；胜局发奖并写通关标记")
    @PostMapping("/fight/{tier}")
    fun fight(auth: Authentication, @PathVariable tier: Int): ResponseEntity<DungeonFightResponse> {
        val userId = auth.userId()
        return ResponseEntity.ok(dungeonService.fight(userId, tier))
    }

    @Operation(summary = "扫荡副本", description = "历史通关该难度后可用：扣魂力（50+等级×5）直接拿金币+杀气+掉落，占用当日名额")
    @PostMapping("/sweep/{tier}")
    fun sweep(auth: Authentication, @PathVariable tier: Int): ResponseEntity<DungeonSweepResponse> {
        val userId = auth.userId()
        return ResponseEntity.ok(dungeonService.sweep(userId, tier))
    }
}
