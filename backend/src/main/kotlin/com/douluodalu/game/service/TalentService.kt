package com.douluodalu.game.service

import com.douluodalu.game.entity.Talent
import com.douluodalu.game.model.TalentBranch
import com.douluodalu.game.repository.TalentRepository
import com.douluodalu.game.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TalentService(
    private val talentRepository: TalentRepository,
    private val userRepository: UserRepository
) {
    fun getTalents(userId: Long): Map<String, Int> {
        val talents = talentRepository.findByUserId(userId)
        return talents.associate { it.branch to it.level }
    }

    @Transactional
    fun upgradeTalent(userId: Long, branch: TalentBranch): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val player = user.player ?: return false

        // 检查天赋点
        if (player.talentPoints <= 0) return false

        // 获取或创建天赋
        val talent = talentRepository.findByUserIdAndBranch(userId, branch.name)
            ?: Talent(
                userId = userId,
                branch = branch.name,
                level = 0
            )

        // 检查等级上限
        if (talent.level >= branch.maxLevel) return false

        // 升级
        talent.level += 1
        player.talentPoints -= 1
        talentRepository.save(talent)
        userRepository.save(user)

        return true
    }

    @Transactional
    fun resetTalents(userId: Long): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val player = user.player ?: return false

        // 检查金币
        if (player.gold < 5000) return false

        // 获取所有天赋
        val talents = talentRepository.findByUserId(userId)
        val totalPoints = talents.sumOf { it.level }

        // 重置
        talentRepository.deleteAll(talents)
        player.talentPoints += totalPoints
        player.gold -= 5000
        userRepository.save(user)

        return true
    }
}