package com.douluodalu.game.controller

import com.douluodalu.game.dto.*
import com.douluodalu.game.service.CheckInService
import com.douluodalu.game.service.DailyQuestService
import com.douluodalu.game.service.GameService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
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

    /**
     * 更新玩家设置三开关（autoBattle / autoAdvanceMap / autoBreakthrough）。
     *
     * 全后端第一个 PUT（有意破除「写端点一律 POST」的历史惯例）：本端点是对既有
     * 玩家资源（profile 设置项）的幂等字段级替换——重复提交同一组值结果相同、
     * 不产生新资源、无副作用累积，REST 语义上属 PUT；其余端点（签到/战斗/领奖
     * 等「执行一次动作」）保持 POST 惯例不动。
     *
     * 校验语义：三字段 @NotNull + 可空默认 null 的组合——HTTP 侧字段必须显式传值
     * （缺字段被 Jackson 反序列化为 null → @Valid 校验失败 400），杜绝「前端漏传
     * 被静默当成不改」；服务层保留 null 跳过分支支持部分更新（见 GameService.updateSettings）。
     * 响应复用 ProfileDto（toProfileDto 重跑），前端可直接局部替换 profile。
     */
    @Operation(summary = "更新玩家设置", description = "三开关显式传值（缺字段 400），返回更新后的完整 profile")
    @PutMapping("/settings")
    fun updateSettings(
        auth: Authentication,
        @Valid @RequestBody request: UpdateSettingsRequest
    ): ResponseEntity<ProfileDto> {
        val userId = auth.principal as Long
        return ResponseEntity.ok(gameService.updateSettings(userId, request))
    }
}

/** 领取每日任务奖励请求体 */
data class ClaimQuestRequest(
    @field:NotBlank(message = "questId 不能为空")
    val questId: String
)

/**
 * 更新玩家设置请求体（部分更新）：三字段可空、默认 null = 该项不更新（服务层跳过）；
 * @NotNull 使「缺字段/显式 null」在 @Valid 层即被拒绝为 400，见 GameController.updateSettings。
 */
data class UpdateSettingsRequest(
    @field:NotNull(message = "autoBattle 必须显式传值")
    val autoBattle: Boolean? = null,
    @field:NotNull(message = "autoAdvanceMap 必须显式传值")
    val autoAdvanceMap: Boolean? = null,
    @field:NotNull(message = "autoBreakthrough 必须显式传值")
    val autoBreakthrough: Boolean? = null
)
