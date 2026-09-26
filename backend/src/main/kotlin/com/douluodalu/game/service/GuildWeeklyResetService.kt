package com.douluodalu.game.service

import com.douluodalu.game.entity.GuildMember
import com.douluodalu.game.model.GuildBossBalance
import com.douluodalu.game.repository.GuildBossRepository
import com.douluodalu.game.repository.GuildMemberRepository
import com.douluodalu.game.repository.GuildRepository
import com.douluodalu.game.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 宗门 Boss 周榜重置（第二十二轮）+ 共享血池周重生（第二十三轮）：每周一凌晨
 *  1) 共享血池重生：遍历 guild_boss 全表置 killed=false、current_hp=max_hp
 *     （max_hp 按各宗门当前等级重算）、week_start=本周一；
 *  2) 周榜结算：按本周伤害（guild_member.weekly_boss_damage）给每个宗门前 3 名发奖
 *     （直接加到 profile，经 user.player 托管，照 GuildService 惯例）；
 *  3) 全量清零：weekly_boss_damage 归零，下一周从 0 重新累计。
 *
 * 顺序（单事务）：Boss 重生 → 周榜结算+清零。理由：
 *  - 锁序一致防死锁：challengeBoss 的持锁序是 guild → boss → member，本任务按
 *    boss → member 顺序拿锁，保持全局「boss 先于 member」的一致序，避免与在途
 *    挑战形成循环等待；
 *  - 业务无差：血池与成员周伤两表独立，重生先行使周一 03:03 后第一批挑战直接打到
 *    满血新 Boss（challengeBoss 侧的惰性重生仅为漏网兜底）。
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
    private val guildBossRepository: GuildBossRepository,
    private val guildRepository: GuildRepository,
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
        // 1) 共享血池周重生（先于成员表操作，锁序理由见类注释）
        respawnGuildBosses()

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

    /**
     * 共享血池全表重生：killed=false、current_hp=max_hp、week_start=本周一。
     * max_hp 按各宗门当前等级重算（上周挑战期间宗门可能升级）——宗门数量少
     * （几十量级）且每周只跑一次，逐行 findById 取 guild level 可接受，不做 join 优化。
     * guild 行已消失（解散后 FK CASCADE 删 boss 行前的漂移窗口）时跳过该行不失败。
     * 返回重生行数（供测试断言）。
     */
    private fun respawnGuildBosses(): Int {
        val bosses = guildBossRepository.findAll()
        if (bosses.isEmpty()) return 0
        val monday = GuildBossBalance.currentWeekMonday()
        for (boss in bosses) {
            val guild = guildRepository.findById(boss.guildId).orElse(null) ?: continue
            val maxHp = GuildBossBalance.weeklyBossMaxHp(guild.level)
            boss.currentHp = maxHp
            boss.maxHp = maxHp
            boss.killed = false
            boss.weekStart = monday
            log.info(
                "guild boss weekly respawn: guild={} maxHp={} weekStart={}", guild.id, maxHp, monday
            )
        }
        guildBossRepository.saveAll(bosses)
        return bosses.size
    }
}
