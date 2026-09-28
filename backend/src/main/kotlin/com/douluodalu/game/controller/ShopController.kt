package com.douluodalu.game.controller

import com.douluodalu.game.dto.KillingBuyResponse
import com.douluodalu.game.dto.KillingShopDto
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
    @GetMapping("/normal")
    fun getNormalShopItems(auth: Authentication): ResponseEntity<List<ShopItem>> {
        return ResponseEntity.ok(NormalShopData.items)
    }

    @PostMapping("/normal/buy/{itemId}")
    fun buyNormalShopItem(
        auth: Authentication,
        @PathVariable itemId: Long
    ): ResponseEntity<Any> {
        val userId = auth.userId()
        val item = NormalShopData.items.find { it.id == itemId }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "商品不存在"))

        val result = shopService.buyItem(userId, item)
        return if (result.success) {
            ResponseEntity.ok(mapOf("message" to "购买成功", "item" to result.item))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to result.error))
        }
    }

    @GetMapping("/boss")
    fun getBossShopItems(auth: Authentication): ResponseEntity<List<ShopItem>> {
        return ResponseEntity.ok(BossShopData.items)
    }

    @PostMapping("/boss/buy/{itemId}")
    fun buyBossShopItem(
        auth: Authentication,
        @PathVariable itemId: Long
    ): ResponseEntity<Any> {
        val userId = auth.userId()
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
        val userId = auth.userId()
        val item = LimitedShopData.items.find { it.id == itemId }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "商品不存在"))

        val result = shopService.buyLimitedItem(userId, item)
        return if (result.success) {
            ResponseEntity.ok(mapOf("message" to "购买成功", "item" to result.item))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to result.error))
        }
    }

    // ==================== 杀气商店（第二十九轮） ====================
    // 业务错误（杀气不足/已拥有/未知称号/非法 stat）由 ShopService 抛
    // IllegalArgumentException → GlobalExceptionHandler 统一 400（其余商店的
    // ShopResult(success=false) 400 口径在杀气商店以异常等价实现）。

    /** GET /api/shop/killing：8 称号拥有/价格/属性预览 + HP/ATK 两商品当前价/已购次数 + 杀气余额 */
    @GetMapping("/killing")
    fun getKillingShop(auth: Authentication): ResponseEntity<KillingShopDto> {
        return ResponseEntity.ok(shopService.getKillingShop(auth.userId()))
    }

    /** POST /api/shop/killing/title/{titleId}：称号兑换（购买即永久拥有，已拥有不可回购） */
    @PostMapping("/killing/title/{titleId}")
    fun buyKillingTitle(auth: Authentication, @PathVariable titleId: String): ResponseEntity<KillingBuyResponse> {
        return ResponseEntity.ok(shopService.buyKillingTitle(auth.userId(), titleId))
    }

    /** POST /api/shop/killing/attribute/{stat}：属性购买（stat ∈ hp|atk，价格 100×2^已购次数） */
    @PostMapping("/killing/attribute/{stat}")
    fun buyKillingAttr(auth: Authentication, @PathVariable stat: String): ResponseEntity<KillingBuyResponse> {
        return ResponseEntity.ok(shopService.buyKillingAttr(auth.userId(), stat))
    }
}

data class ShopResult(val success: Boolean, val item: Any? = null, val error: String? = null)
