package com.douluodalu.game.controller

import com.douluodalu.game.model.*
import com.douluodalu.game.service.GameService
import com.douluodalu.game.service.ShopService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/shop")
class ShopController(
    private val shopService: ShopService,
    private val gameService: GameService
) {
    @GetMapping("/boss")
    fun getBossShopItems(auth: Authentication): ResponseEntity<List<ShopItem>> {
        return ResponseEntity.ok(BossShopData.items)
    }

    @PostMapping("/boss/buy/{itemId}")
    fun buyBossShopItem(
        auth: Authentication,
        @PathVariable itemId: Long
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val item = BossShopData.items.find { it.id == itemId }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "商品不存在"))
        
        val result = shopService.buyItem(userId, item)
        return if (result.success) {
            ResponseEntity.ok(mapOf("message" to "购买成功", "item" to result.item))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to result.error))
        }
    }

    @GetMapping("/limited")
    fun getLimitedShopItems(auth: Authentication): ResponseEntity<List<ShopItem>> {
        return ResponseEntity.ok(LimitedShopData.items)
    }

    @PostMapping("/limited/buy/{itemId}")
    fun buyLimitedShopItem(
        auth: Authentication,
        @PathVariable itemId: Long
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val item = LimitedShopData.items.find { it.id == itemId }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "商品不存在"))
        
        val result = shopService.buyLimitedItem(userId, item)
        return if (result.success) {
            ResponseEntity.ok(mapOf("message" to "购买成功", "item" to result.item))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to result.error))
        }
    }
}

data class ShopResult(val success: Boolean, val item: Any? = null, val error: String? = null)
