package com.douluodalu.game.service

import com.douluodalu.game.entity.GuildMember
import com.douluodalu.game.model.GuildBossBalance
import com.douluodalu.game.repository.GuildMemberRepository
import com.douluodalu.game.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 宗门 Boss 周榜重置（第二十二轮）：每周一凌晨按本周伤害（guild_member.weekly_boss_damage）
 * 给每个宗门前 3 名发奖（直接加到 profile，经 user.player 托管，照 GuildService 惯例），
 * 随后全量清零，下一周从 0 重新累计。
 *
 * 开关 guild.weekly-reset-enabled 默认开（matchIfMissing = true）；application-test.yml 显式
 * 置 false，保证测试 profile 下整个 Bean 不注册（同 AuditLogCleanupService 口径），杜绝
 * 定时任务与断言抢跑造成的测试抖动。
 * cron 默认每周一 03:03，错开审计清理任务的 03:00 整点。
 * 前提：@EnableScheduling 已由 SchedulingConfig 无条件开启（第二十二轮放宽，各任务
 * 用自己类上的 @ConditionalOnProperty 独立开关）。
 */
@Service
@ConditionalOnProperty(name = ["guild.weekly-reset-enabled"], havingValue = "true", matchIfMissing = true)
class GuildWeeklyResetService(
    private val guildMemberRepository: GuildMemberRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(GuildWeeklyResetService::class.java)

    @Scheduled(cron = "\${guild.weekly-reset-cron:0 0 3 ? * MON}")
    fun weeklyReset() {
        val guilds = resetWeeklyBossDamage()
        log.info("guild boss weekly reset done: guilds={}", guilds)
    }

    /**
     * 执行一次周结算，返回处理的宗门数。遍历「有成员行」的宗门（guild_member 全表
     * groupBy guildId，一次查询，无逐宗门 N+1）；前 3 名按伤害降序（并列按 userId 升序
     * 稳定排序）、weeklyBossDamage>0 才有奖（0 伤害不占名次）；随后全部成员行清零。
     * 历史行 user/player 缺失（数据漂移）时跳过该名发奖但不清零失败，整体单事务回滚兜底。
     */
    @Transactional
    fun resetWeeklyBossDamage(): Int {
        val members = guildMemberRepository.findAll()
        if (members.isEmpty()) return 0
        val rewards = GuildBossBalance.WEEKLY_BOSS_RANK_REWARDS
        var guildCount = 0
        for ((guildId, rows) in members.groupBy { it.guildId }) {
            guildCount++
            val top = rows.filter { it.weeklyBossDamage > 0 }
                .sortedWith(compareByDescending<GuildMember> { it.weeklyBossDamage }.thenBy { it.userId })
                .take(rewards.size)
            for ((idx, member) in top.withIndex()) {
                val reward = rewards[idx]
                val user = userRepository.findById(member.userId).orElse(null) ?: continue
                val player = user.player ?: continue
                player.bossCoin += reward.bossCoin
                player.gold += reward.gold
                userRepository.save(user)
                log.info(
                    "guild boss weekly rank: guild={} rank={} user={} damage={} bossCoin+= {} gold+={}",
                    guildId, idx + 1, member.userId, member.weeklyBossDamage, reward.bossCoin, reward.gold
                )
            }
            // 全量清零（含未上榜与零伤害成员）：本周口径随发奖一并归零
            rows.forEach { it.weeklyBossDamage = 0 }
        }
        guildMemberRepository.saveAll(members)
        return guildCount
    }
}
