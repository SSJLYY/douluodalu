package com.douluodalu.game.controller

import com.douluodalu.game.dto.GuildListResponse
import com.douluodalu.game.dto.GuildMyResponse
import com.douluodalu.game.entity.Guild
import com.douluodalu.game.model.GuildShopData
import com.douluodalu.game.service.GuildService
import com.douluodalu.game.service.ShopService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * Guild 实体 → 裁剪 DTO：memberCount 取实体 currentMembers，notice 取实体 description，
 * 其余内部字段（leaderId/createdAt/exp 等）不下发给前端。
 */
private fun Guild.toListDto() = GuildListResponse(
    id = id,
    name = name,
    level = level,
    memberCount = currentMembers,
    maxMembers = maxMembers,
    notice = description
)

@RestController
@RequestMapping("/api/guild")
class GuildController(
    private val guildService: GuildService,
    private val shopService: ShopService
) {

    @GetMapping("/list")
    fun getGuildList(auth: Authentication): ResponseEntity<List<GuildListResponse>> {
        return ResponseEntity.ok(guildService.getGuildList().map { it.toListDto() })
    }

    @GetMapping("/my")
    fun getMyGuild(auth: Authentication): ResponseEntity<GuildMyResponse> {
        val userId = auth.principal as Long
        val guild = guildService.getMyGuild(userId)
        return ResponseEntity.ok(
            if (guild != null) GuildMyResponse(joined = true, guild = guild.toListDto())
            else GuildMyResponse(joined = false)
        )
    }

    /** 本会成员列表（按加入时间升序）：仅成员可查，未入宗门返回 400 {error} */
    @GetMapping("/members")
    fun getGuildMembers(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val members = guildService.getGuildMembers(userId)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "请先加入宗门"))
        return ResponseEntity.ok(members)
    }

    @PostMapping("/create")
    fun createGuild(
        auth: Authentication,
        @RequestBody request: CreateGuildRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = guildService.createGuild(userId, request.name, request.description)
        return if (result != null) {
            ResponseEntity.ok(mapOf("message" to "宗门创建成功", "guild" to result.toListDto()))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "创建失败，可能已在其他宗门"))
        }
    }

    @PostMapping("/join/{guildId}")
    fun joinGuild(
        auth: Authentication,
        @PathVariable guildId: Long
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val success = guildService.joinGuild(userId, guildId)
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "加入宗门成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "加入失败"))
        }
    }

    @PostMapping("/leave")
    fun leaveGuild(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = guildService.leaveGuild(userId)
        return if (result != null) {
            // 宗主退出时服务端已自动处理：有成员→转让给加入最早者（transferredTo=继任昵称）；
            // 只剩自己→解散（disbanded=true）；message 沿用旧契约固定文案
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "退出失败"))
        }
    }

    @PostMapping("/kick")
    fun kickMember(
        auth: Authentication,
        @RequestBody request: KickMemberRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val success = guildService.kickMember(userId, request.targetUserId)
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "已踢出成员"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "踢人失败：仅宗主可踢其他成员，且不能踢自己"))
        }
    }

    @PostMapping("/transfer")
    fun transferLeadership(
        auth: Authentication,
        @RequestBody request: TransferLeaderRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val success = guildService.transferLeadership(userId, request.targetUserId)
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "宗主已转让"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "转让失败：仅宗主可转让，目标须为本宗门成员"))
        }
    }

    @PostMapping("/disband")
    fun disbandGuild(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val success = guildService.disbandGuild(userId)
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "宗门已解散"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "解散失败：仅宗主可解散，且需宗门内只剩自己"))
        }
    }

    @PostMapping("/donate")
    fun donateGuild(
        auth: Authentication,
        @RequestBody request: DonateRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = guildService.donate(userId, request.amount)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "捐献成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "捐献失败"))
        }
    }

    @PostMapping("/boss/challenge")
    fun challengeBoss(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = guildService.challengeBoss(userId)
        return if (result != null) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "请先加入宗门"))
        }
    }

    @GetMapping("/shop")
    fun getGuildShopItems(auth: Authentication): ResponseEntity<Any> {
        return ResponseEntity.ok(GuildShopData.items)
    }

    @PostMapping("/shop/buy/{itemId}")
    fun buyGuildShopItem(
        auth: Authentication,
        @PathVariable itemId: Long
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        if (guildService.getMyGuild(userId) == null) {
            return ResponseEntity.badRequest().body(mapOf("error" to "请先加入宗门"))
        }
        val item = GuildShopData.items.find { it.id == itemId }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "商品不存在"))

        val result = shopService.buyItem(userId, item)
        return if (result.success) {
            ResponseEntity.ok(mapOf("message" to "购买成功", "item" to result.item))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to result.error))
        }
    }
}

data class CreateGuildRequest(val name: String, val description: String)
data class DonateRequest(val amount: Long)
data class KickMemberRequest(val targetUserId: Long)
data class TransferLeaderRequest(val targetUserId: Long)
