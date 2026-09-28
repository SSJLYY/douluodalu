package com.douluodalu.game.controller

import com.douluodalu.game.model.TalentBranch
import com.douluodalu.game.service.TalentService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/talent")
class TalentController(private val talentService: TalentService) {
    
    @GetMapping("")
    fun getTalents(auth: Authentication): ResponseEntity<Map<String, Int>> {
        val userId = auth.userId()
        val talents = talentService.getTalents(userId)
        return ResponseEntity.ok(talents)
    }

    @PostMapping("/upgrade/{branch}")
    fun upgradeTalent(
        auth: Authentication,
        @PathVariable branch: String
    ): ResponseEntity<Any> {
        val userId = auth.userId()
        // 无效分支名不再手写 try/catch 自造 {"error": "..."} 响应体：显式抛
        // IllegalArgumentException 交回 GlobalExceptionHandler，输出与其他端点一致的
        // 400 {error:"BAD_REQUEST", message}（状态码 400 与文案「无效的天赋分支」不变）
        val talentBranch = TalentBranch.entries.firstOrNull { it.name.equals(branch, ignoreCase = true) }
            ?: throw IllegalArgumentException("无效的天赋分支")

        val result = talentService.upgradeTalent(userId, talentBranch)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "天赋升级成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "天赋升级失败"))
        }
    }

    @GetMapping("/info")
    fun getTalentInfo(auth: Authentication): ResponseEntity<List<TalentInfo>> {
        val userId = auth.userId()
        val talents = talentService.getTalents(userId)
        val info = TalentBranch.entries.map { branch ->
            val level = talents[branch.name] ?: 0
            TalentInfo(
                branch = branch.name,
                displayName = branch.displayName,
                currentLevel = level,
                maxLevel = branch.maxLevel,
                effect = TalentBranch.effectDescription(branch, level)
            )
        }
        return ResponseEntity.ok(info)
    }
}

data class TalentInfo(
    val branch: String,
    val displayName: String,
    val currentLevel: Int,
    val maxLevel: Int,
    val effect: String
)
