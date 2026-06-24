package com.douluodalu.game.controller

import com.douluodalu.game.service.GameService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/equipment")
class EquipmentController(private val gameService: GameService) {
    
    @GetMapping("")
    fun getEquipment(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val gameState = gameService.getGameState(userId)
        return ResponseEntity.ok(gameState)
    }

    @PostMapping("/ring/equip")
    fun equipRing(
        auth: Authentication,
        @RequestBody request: EquipRingRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.equipRing(userId, request.slotIndex, request.ringIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂环装备成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "装备失败"))
        }
    }

    @PostMapping("/ring/unequip")
    fun unequipRing(
        auth: Authentication,
        @RequestBody request: UnequipRingRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.unequipRing(userId, request.slotIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂环卸下成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "卸下失败"))
        }
    }

    @PostMapping("/bone/equip")
    fun equipBone(
        auth: Authentication,
        @RequestBody request: EquipBoneRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.equipBone(userId, request.slotIndex, request.boneIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂骨装备成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "装备失败"))
        }
    }

    @PostMapping("/bone/unequip")
    fun unequipBone(
        auth: Authentication,
        @RequestBody request: UnequipBoneRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.unequipBone(userId, request.slotIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂骨卸下成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "卸下失败"))
        }
    }

    @PostMapping("/core/equip")
    fun equipCore(
        auth: Authentication,
        @RequestBody request: EquipCoreRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.equipCore(userId, request.slotIndex, request.coreIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂核装备成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "装备失败"))
        }
    }

    @PostMapping("/core/unequip")
    fun unequipCore(
        auth: Authentication,
        @RequestBody request: UnequipCoreRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.unequipCore(userId, request.slotIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "魂核卸下成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "卸下失败"))
        }
    }

    @PostMapping("/backpack/sell")
    fun sellBackpackItem(
        auth: Authentication,
        @RequestBody request: SellItemRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = gameService.sellBackpackItem(userId, request.itemIndex)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "出售成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "出售失败"))
        }
    }

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

data class EquipRingRequest(val slotIndex: Int, val ringIndex: Int)
data class UnequipRingRequest(val slotIndex: Int)
data class EquipBoneRequest(val slotIndex: Int, val boneIndex: Int)
data class UnequipBoneRequest(val slotIndex: Int)
data class EquipCoreRequest(val slotIndex: Int, val coreIndex: Int)
data class UnequipCoreRequest(val slotIndex: Int)
data class SellItemRequest(val itemIndex: Int)
