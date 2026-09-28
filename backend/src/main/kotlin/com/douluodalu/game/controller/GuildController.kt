package com.douluodalu.game.controller

import com.douluodalu.game.dto.GuildBossRankResponse
import com.douluodalu.game.dto.GuildBossStatusResponse
import com.douluodalu.game.dto.GuildListResponse
import com.douluodalu.game.dto.GuildMyResponse
import com.douluodalu.game.entity.Guild
import com.douluodalu.game.model.GuildShopData
import com.douluodalu.game.service.GuildService
import com.douluodalu.game.service.ShopService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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
        val userId = auth.userId()
        val guild = guildService.getMyGuild(userId)
        return ResponseEntity.ok(
            if (guild != null) GuildMyResponse(joined = true, guild = guild.toListDto())
            else GuildMyResponse(joined = false)
        )
    }

    /** 本会成员列表（按加入时间升序）：仅成员可查，未入宗门返回 400 {error} */
    @GetMapping("/members")
    fun getGuildMembers(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.userId()
        val members = guildService.getGuildMembers(userId)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "请先加入宗门"))
        return ResponseEntity.ok(members)
    }

    @PostMapping("/create")
    fun createGuild(
        auth: Authentication,
        @Valid @RequestBody request: CreateGuildRequest
    ): ResponseEntity<Any> {
        val userId = auth.userId()
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
        val userId = auth.userId()
        val success = guildService.joinGuild(userId, guildId)
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "加入宗门成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "加入失败"))
        }
    }

    @PostMapping("/leave")
    fun leaveGuild(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.userId()
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
        @Valid @RequestBody request: KickMemberRequest
    ): ResponseEntity<Any> {
        val userId = auth.userId()
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
        @Valid @RequestBody request: TransferLeaderRequest
    ): ResponseEntity<Any> {
        val userId = auth.userId()
        val success = guildService.transferLeadership(userId, request.targetUserId)
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "宗主已转让"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "转让失败：仅宗主可转让，目标须为本宗门成员"))
        }
    }

    @PostMapping("/disband")
    fun disbandGuild(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.userId()
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
        @Valid @RequestBody request: DonateRequest
    ): ResponseEntity<Any> {
        val userId = auth.userId()
        val result = guildService.donate(userId, request.amount)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "捐献成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "捐献失败"))
        }
    }

    @PostMapping("/boss/challenge")
    fun challengeBoss(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.userId()
        val result = guildService.challengeBoss(userId)
        return if (result != null) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "请先加入宗门"))
        }
    }

    /**
     * 共享血池状态（GET /api/guild/boss/status）：当前血量/本周上限/击杀标记，供前端血条。
     * 无行/跨周时惰性初始化；未入宗门由服务层抛业务异常（消息含「宗门」），统一转 400。
     */
    @GetMapping("/boss/status")
    fun getGuildBossStatus(auth: Authentication): ResponseEntity<GuildBossStatusResponse> {
        val userId = auth.userId()
        return ResponseEntity.ok(guildService.getGuildBossStatus(userId))
    }

    /**
     * 宗门 Boss 周榜（按本周伤害降序，只含 >0 成员，至多 10 条；myRank=自己名次，无伤害记录 0）。
     * 未入宗门由服务层抛业务异常（消息含「宗门」），GlobalExceptionHandler 统一转 400。
     */
    @GetMapping("/boss/rank")
    fun getGuildBossRank(auth: Authentication): ResponseEntity<GuildBossRankResponse> {
        val userId = auth.userId()
        return ResponseEntity.ok(guildService.getGuildBossRank(userId))
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
        val userId = auth.userId()
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

/**
 * 安全审计修复：此前无任何校验注解且缺 @Valid——超长 name/description 直接落库
 * 触发 Data too long（500 + DB 细节日志）。@Size 上限与 V1 迁移的列宽严格对齐：
 * guild.name VARCHAR(64)、guild.notice VARCHAR(500)。
 */
data class CreateGuildRequest(
    @field:NotBlank(message = "宗门名称不能为空")
    @field:Size(max = 64, message = "宗门名称最长 64 字符")
    val name: String,

    @field:Size(max = 500, message = "宗门公告最长 500 字符")
    val description: String
)

/** @Min(1) 把「负数/零金额」在参数层挡成 400；服务层 amount<=0 校验保留作纵深防御（第十轮修复仍在） */
data class DonateRequest(
    @field:Min(value = 1, message = "捐献金额必须为正数")
    val amount: Long
)

data class KickMemberRequest(
    @field:Min(value = 1, message = "目标用户 ID 非法")
    val targetUserId: Long
)

data class TransferLeaderRequest(
    @field:Min(value = 1, message = "目标用户 ID 非法")
    val targetUserId: Long
)
