package com.douluodalu.game.service

import com.douluodalu.game.dto.GuildBossResponse
import com.douluodalu.game.entity.Guild
import com.douluodalu.game.entity.GuildMember
import com.douluodalu.game.model.GuildBossBalance
import com.douluodalu.game.repository.GuildRepository
import com.douluodalu.game.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.random.Random

@Service
class GuildService(
    private val guildRepository: GuildRepository,
    private val userRepository: UserRepository,
    private val gameService: GameService
) {
    fun getGuildList(): List<Guild> {
        return guildRepository.findAll()
    }

    fun getMyGuild(userId: Long): Guild? {
        val user = userRepository.findById(userId).orElse(null) ?: return null
        val player = user.player ?: return null
        val guildId = player.guildId ?: return null
        return guildRepository.findById(guildId).orElse(null)
    }

    @Transactional
    fun createGuild(userId: Long, name: String, description: String): Guild? {
        val user = userRepository.findById(userId).orElse(null) ?: return null
        val player = user.player ?: return null

        // 检查是否已在宗门
        if (player.guildId != null) return null

        // 检查等级
        if (player.level < 30) return null

        // 检查金币
        if (player.gold < 10000) return null

        // 创建宗门
        val guild = Guild(
            name = name,
            description = description,
            level = 1,
            exp = 0,
            maxMembers = 20,
            leaderId = userId,
            createdAt = LocalDateTime.now()
        )
        val savedGuild = guildRepository.save(guild)

        // 创建宗主
        val member = GuildMember(
            guildId = savedGuild.id,
            userId = userId,
            role = "LEADER",
            joinedAt = LocalDateTime.now()
        )

        // 更新玩家
        player.guildId = savedGuild.id
        player.gold -= 10000
        userRepository.save(user)

        return savedGuild
    }

    @Transactional
    fun joinGuild(userId: Long, guildId: Long): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val player = user.player ?: return false

        // 检查是否已在宗门
        if (player.guildId != null) return false

        // 检查宗门是否存在
        val guild = guildRepository.findById(guildId).orElse(null) ?: return false

        // 检查宗门人数
        if (guild.currentMembers >= guild.maxMembers) return false

        // 加入宗门
        player.guildId = guildId
        guild.currentMembers += 1
        guildRepository.save(guild)
        userRepository.save(user)

        return true
    }

    @Transactional
    fun leaveGuild(userId: Long): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val player = user.player ?: return false
        val guildId = player.guildId ?: return false

        val guild = guildRepository.findById(guildId).orElse(null) ?: return false

        // 如果是宗主，不能退出
        if (guild.leaderId == userId) return false

        // 退出宗门
        player.guildId = null
        guild.currentMembers -= 1
        guildRepository.save(guild)
        userRepository.save(user)

        return true
    }

    @Transactional
    fun donate(userId: Long, amount: Long): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val player = user.player ?: return false
        val guildId = player.guildId ?: return false

        if (player.gold < amount) return false

        val guild = guildRepository.findById(guildId).orElse(null) ?: return false

        // 捐献
        player.gold -= amount
        guild.exp += (amount / 100).toInt()

        // 升级检查
        val expNeeded = guild.level * 1000
        if (guild.exp >= expNeeded) {
            guild.level += 1
            guild.exp -= expNeeded
            guild.maxMembers += 5
        }

        guildRepository.save(guild)
        userRepository.save(user)

        return true
    }

    /**
     * 挑战宗门 Boss：伤害与玩家等级/战斗魂力挂钩，胜利判定按伤害占 Boss 生命比例。
     * 无论胜负均获得金币、Boss 币与一件随机装备，并为宗门积累经验。
     */
    @Transactional
    fun challengeBoss(userId: Long): GuildBossResponse? {
        val user = userRepository.findById(userId).orElse(null) ?: return null
        val player = user.player ?: return null
        val guildId = player.guildId ?: return null
        val guild = guildRepository.findById(guildId).orElse(null) ?: return null

        val damage = player.level * GuildBossBalance.BASE_DAMAGE_PER_LEVEL +
                player.battleSoulPower +
                Random.nextLong(GuildBossBalance.DAMAGE_RANDOM_RANGE)
        val bossHp = GuildBossBalance.BOSS_HP_BASE + guild.level * GuildBossBalance.BOSS_HP_PER_GUILD_LEVEL
        val won = damage >= (bossHp * GuildBossBalance.WIN_DAMAGE_RATIO).toLong()
        val goldGained = damage / GuildBossBalance.GOLD_PER_DAMAGE_DIVISOR
        val bossCoinGained = if (won) GuildBossBalance.WIN_BOSS_COIN_BASE + guild.level else GuildBossBalance.LOSE_BOSS_COIN
        val item = gameService.rollBackpackDrop(userId, player.level + guild.level)

        player.gold += goldGained
        player.bossCoin += bossCoinGained
        player.updatedAt = LocalDateTime.now()
        guild.exp += if (won) GuildBossBalance.WIN_GUILD_EXP else GuildBossBalance.LOSE_GUILD_EXP

        guildRepository.save(guild)
        userRepository.save(user)

        return GuildBossResponse(
            won = won,
            damage = damage,
            bossHp = bossHp,
            goldGained = goldGained,
            bossCoinGained = bossCoinGained,
            item = item,
            message = if (won) "宗门 Boss 挑战成功" else "造成了有效伤害，获得参与奖励"
        )
    }
}