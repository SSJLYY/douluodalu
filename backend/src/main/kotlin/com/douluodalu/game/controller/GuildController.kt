package com.douluodalu.game.controller

import com.douluodalu.game.entity.Guild
import com.douluodalu.game.service.GuildService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/guild")
class GuildController(private val guildService: GuildService) {
    
    @GetMapping("/list")
    fun getGuildList(auth: Authentication): ResponseEntity<List<Guild>> {
        return ResponseEntity.ok(guildService.getGuildList())
    }

    @GetMapping("/my")
    fun getMyGuild(auth: Authentication): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val guild = guildService.getMyGuild(userId)
        return if (guild != null) {
            ResponseEntity.ok(guild)
        } else {
            ResponseEntity.ok(mapOf("message" to "未加入宗门"))
        }
    }

    @PostMapping("/create")
    fun createGuild(
        auth: Authentication,
        @RequestBody request: CreateGuildRequest
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val result = guildService.createGuild(userId, request.name, request.description)
        return if (result != null) {
            ResponseEntity.ok(mapOf("message" to "宗门创建成功", "guild" to result))
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
        val success = guildService.leaveGuild(userId)
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "已退出宗门"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "退出失败"))
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
}

data class CreateGuildRequest(val name: String, val description: String)
data class DonateRequest(val amount: Long)
