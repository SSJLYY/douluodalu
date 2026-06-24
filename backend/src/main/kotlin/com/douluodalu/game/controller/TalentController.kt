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
        val userId = auth.principal as Long
        val talents = talentService.getTalents(userId)
        return ResponseEntity.ok(talents)
    }

    @PostMapping("/upgrade/{branch}")
    fun upgradeTalent(
        auth: Authentication,
        @PathVariable branch: String
    ): ResponseEntity<Any> {
        val userId = auth.principal as Long
        val talentBranch = try {
            TalentBranch.valueOf(branch.uppercase())
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf("error" to "无效的天赋分支"))
        }
        
        val result = talentService.upgradeTalent(userId, talentBranch)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "天赋升级成功"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "天赋升级失败"))
        }
    }

    @GetMapping("/info")
    fun getTalentInfo(auth: Authentication): ResponseEntity<List<TalentInfo>> {
        val userId = auth.principal as Long
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
