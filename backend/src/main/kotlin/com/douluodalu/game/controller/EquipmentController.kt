package com.douluodalu.game.controller

import com.douluodalu.game.service.GameService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/equipment")
@Tag(name = "装备系统", description = "魂环、魂骨、魂核的装备与卸下")
class EquipmentController(private val gameService: GameService) {

    @Operation(summary = "获取装备列表", description = "获取玩家已装备的魂环、魂骨、魂核")
    @GetMapping("")
    fun getEquipment(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val gameState = gameService.getGameState(userId)
        return ResponseEntity.ok(gameState)
    }

    @Operation(summary = "装备魂环", description = "将背包中的魂环装备到指定槽位")
    @PostMapping("/ring/equip")
    fun equipRing(
        auth: Authentication,
        @Valid @RequestBody request: EquipRingRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.equipRing(userId, request.slotIndex, request.ringIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂环装备成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "装备失败"))
        }
    }

    @Operation(summary = "卸下魂环", description = "从指定槽位卸下魂环")
    @PostMapping("/ring/unequip")
    fun unequipRing(
        auth: Authentication,
        @Valid @RequestBody request: UnequipRingRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.unequipRing(userId, request.slotIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂环卸下成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "卸下失败"))
        }
    }

    @Operation(summary = "装备魂骨", description = "将背包中的魂骨装备到指定槽位")
    @PostMapping("/bone/equip")
    fun equipBone(
        auth: Authentication,
        @Valid @RequestBody request: EquipBoneRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.equipBone(userId, request.slotIndex, request.boneIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂骨装备成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "装备失败"))
        }
    }

    @Operation(summary = "卸下魂骨", description = "从指定槽位卸下魂骨")
    @PostMapping("/bone/unequip")
    fun unequipBone(
        auth: Authentication,
        @Valid @RequestBody request: UnequipBoneRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.unequipBone(userId, request.slotIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂骨卸下成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "卸下失败"))
        }
    }

    @Operation(summary = "强化魂骨", description = "消耗金币提升魂骨强化等级（背包骨 itemIndex 或已装备骨 slotIndex 二选一，上限 +15）")
    @PostMapping("/bone/enhance")
    fun enhanceBone(
        auth: Authentication,
        @Valid @RequestBody request: EnhanceBoneRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        // 服务层返回消息对（成功带实际强化等级与花费）：照既有 Boolean→200/400 惯例转 HTTP
        val result = gameService.enhanceBone(userId, request.itemIndex, request.slotIndex)
        return if (result.success) {
            ResponseEntity.ok(mapOf("message" to result.message))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to result.message))
        }
    }

    @Operation(summary = "装备魂核", description = "将背包中的魂核装备到指定槽位")
    @PostMapping("/core/equip")
    fun equipCore(
        auth: Authentication,
        @Valid @RequestBody request: EquipCoreRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.equipCore(userId, if (request.slotIndex == 0) "LEFT" else "RIGHT", request.coreIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂核装备成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "装备失败"))
        }
    }

    @Operation(summary = "卸下魂核", description = "从指定槽位卸下魂核")
    @PostMapping("/core/unequip")
    fun unequipCore(
        auth: Authentication,
        @Valid @RequestBody request: UnequipCoreRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.unequipCore(userId, if (request.slotIndex == 0) "LEFT" else "RIGHT")
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂核卸下成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "卸下失败"))
        }
    }

    @Operation(summary = "出售物品", description = "出售背包中的物品换取金币")
    @PostMapping("/backpack/sell")
    fun sellBackpackItem(
        auth: Authentication,
        @Valid @RequestBody request: SellItemRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.sellBackpackItem(userId, request.itemIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "出售成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "出售失败"))
        }
    }

    @Operation(summary = "扩展背包", description = "消耗金币增加背包容量")
    @PostMapping("/backpack/expand")
    fun expandBackpack(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.expandBackpack(userId)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "背包扩展成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "扩展失败，金币不足"))
        }
    }
}

// ======== Equipment Request DTOs with @Valid ========
data class EquipRingRequest(
    @field:Min(0, message = "槽位索引不能为负")
    @field:Max(8, message = "魂环槽位最大为 8")
    val slotIndex: Int,

    @field:Min(0, message = "魂环索引不能为负")
    val ringIndex: Int
)

data class UnequipRingRequest(
    @field:Min(0, message = "槽位索引不能为负")
    @field:Max(8, message = "魂环槽位最大为 8")
    val slotIndex: Int
)

data class EquipBoneRequest(
    @field:Min(0, message = "槽位索引不能为负")
    @field:Max(5, message = "魂骨槽位最大为 5")
    val slotIndex: Int,

    @field:Min(0, message = "魂骨索引不能为负")
    val boneIndex: Int
)

data class UnequipBoneRequest(
    @field:Min(0, message = "槽位索引不能为负")
    @field:Max(5, message = "魂骨槽位最大为 5")
    val slotIndex: Int
)

/**
 * POST /api/equipment/bone/enhance 请求体：itemIndex（背包 BONE 列表索引，与 sell/equip 同口径）
 * 或 slotIndex（已装备骨槽位 0-5）恰好一个非空——都空/都非空由服务层校验并返回
 * 400 {"error": "参数无效：itemIndex 与 slotIndex 二选一"}（JSR-303 无跨字段非空约束，二层校验惯例）。
 * 两者均可空故只挂 @Min(0)（slotIndex 上限 0-5 由服务层范围检查兜底）。
 */
data class EnhanceBoneRequest(
    @field:Min(0, message = "魂骨索引不能为负")
    val itemIndex: Int? = null,

    @field:Min(0, message = "槽位索引不能为负")
    val slotIndex: Int? = null
)

data class EquipCoreRequest(
    @field:Min(0, message = "槽位索引不能为负")
    @field:Max(1, message = "魂核槽位最大为 1")
    val slotIndex: Int,

    @field:Min(0, message = "魂核索引不能为负")
    val coreIndex: Int
)

data class UnequipCoreRequest(
    @field:Min(0, message = "槽位索引不能为负")
    @field:Max(1, message = "魂核槽位最大为 1")
    val slotIndex: Int
)

data class SellItemRequest(
    @field:Min(0, message = "物品索引不能为负")
    val itemIndex: Int
)
