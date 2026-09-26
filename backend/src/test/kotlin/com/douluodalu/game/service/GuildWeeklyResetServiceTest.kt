package com.douluodalu.game.service

import com.douluodalu.game.entity.GuildMember
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.model.GuildBossBalance
import com.douluodalu.game.repository.GuildMemberRepository
import com.douluodalu.game.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * 宗门 Boss 周榜重置任务单测（纯 Mockito，不触库）：
 * Top3 发奖精确、第 4 名无奖、零伤害跳过、发奖后全量清零。
 */
class GuildWeeklyResetServiceTest {

    private lateinit var guildMemberRepository: GuildMemberRepository
    private lateinit var userRepository: UserRepository
    private lateinit var service: GuildWeeklyResetService

    /** userId → profile，断言奖励金额用 */
    private val profiles = mutableMapOf<Long, PlayerProfileEntity>()

    @BeforeEach
    fun setUp() {
        guildMemberRepository = mock()
        userRepository = mock()
        service = GuildWeeklyResetService(guildMemberRepository, userRepository)
        profiles.clear()
    }

    private fun stubUser(id: Long): UserEntity {
        val profile = PlayerProfileEntity(userId = id, level = 20)
        profile.gold = 0
        profile.bossCoin = 0
        profiles[id] = profile
        val user = UserEntity(id = id, username = "u$id", nickname = "n$id", passwordHash = "h")
        user.player = profile
        whenever(userRepository.findById(id)).thenReturn(Optional.of(user))
        return user
    }

    private fun stubMembers(vararg members: GuildMember) {
        whenever(guildMemberRepository.findAll()).thenReturn(members.toList())
    }

    private fun member(guildId: Long, userId: Long, damage: Long) =
        GuildMember(guildId = guildId, userId = userId, weeklyBossDamage = damage)

    @Test
    fun `reset should reward exactly top 3 by weekly damage and clear every row`() {
        // 同一宗门 5 名成员：伤害 500/400/300/200/100 → 前 3 名获奖，第 4/5 名无奖
        val m1 = member(9L, 1L, 500)
        val m2 = member(9L, 2L, 400)
        val m3 = member(9L, 3L, 300)
        val m4 = member(9L, 4L, 200)
        val m5 = member(9L, 5L, 100)
        stubMembers(m1, m2, m3, m4, m5)
        (1L..5L).forEach { stubUser(it) }

        val guilds = service.resetWeeklyBossDamage()

        assertEquals(1, guilds)
        val rewards = GuildBossBalance.WEEKLY_BOSS_RANK_REWARDS
        assertEquals(rewards[0].bossCoin, profiles[1L]!!.bossCoin)
        assertEquals(rewards[0].gold, profiles[1L]!!.gold)
        assertEquals(rewards[1].bossCoin, profiles[2L]!!.bossCoin)
        assertEquals(rewards[1].gold, profiles[2L]!!.gold)
        assertEquals(rewards[2].bossCoin, profiles[3L]!!.bossCoin)
        assertEquals(rewards[2].gold, profiles[3L]!!.gold)
        // 第 4/5 名无奖
        assertEquals(0L, profiles[4L]!!.bossCoin)
        assertEquals(0L, profiles[4L]!!.gold)
        assertEquals(0L, profiles[5L]!!.bossCoin)
        assertEquals(0L, profiles[5L]!!.gold)
        // 奖励恰好发放 3 次（每个上榜名一次）
        verify(userRepository, times(3)).save(any())
        // 全量清零回写（下方逐行断言的是传给 saveAll 的同一批实体实例）
        verify(guildMemberRepository).saveAll(any<Iterable<GuildMember>>())
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), listOf(m1, m2, m3, m4, m5).map { it.weeklyBossDamage })
    }

    @Test
    fun `reset should skip zero damage members and guilds with no positive damage`() {
        // 两个宗门：9 号全员零伤害（无人获奖），10 号只有 2 名有伤害（不足 3 人也全获）
        val zeroA = member(9L, 1L, 0)
        val zeroB = member(9L, 2L, 0)
        stubMembers(zeroA, zeroB, member(10L, 3L, 70), member(10L, 4L, 30))
        (1L..4L).forEach { stubUser(it) }

        val guilds = service.resetWeeklyBossDamage()

        assertEquals(2, guilds)
        // 零伤害成员不占名次、不发奖（save 恰好 = 10 号宗门 2 名获奖者）
        verify(userRepository, times(2)).save(any())
        assertEquals(GuildBossBalance.WEEKLY_BOSS_RANK_REWARDS[0].bossCoin, profiles[3L]!!.bossCoin)
        assertEquals(GuildBossBalance.WEEKLY_BOSS_RANK_REWARDS[1].bossCoin, profiles[4L]!!.bossCoin)
        assertEquals(0L, profiles[1L]!!.bossCoin)
        assertEquals(0L, profiles[2L]!!.bossCoin)
        // 零伤害行同样清零（本就是 0，回写不漂移）
        assertEquals(0L, zeroA.weeklyBossDamage)
        assertEquals(0L, zeroB.weeklyBossDamage)
    }

    @Test
    fun `reset should keep order deterministic for tied damage by ascending userId`() {
        // 并列伤害按 userId 升序稳定排序：只有前 3 获奖，并列尾部的 1 号（低伤害）与并列外的空位不获奖
        stubMembers(
            member(9L, 4L, 100), member(9L, 3L, 100), member(9L, 2L, 100),
            member(9L, 1L, 50)
        )
        (1L..4L).forEach { stubUser(it) }

        service.resetWeeklyBossDamage()

        assertEquals(GuildBossBalance.WEEKLY_BOSS_RANK_REWARDS[0].bossCoin, profiles[2L]!!.bossCoin)
        assertEquals(GuildBossBalance.WEEKLY_BOSS_RANK_REWARDS[1].bossCoin, profiles[3L]!!.bossCoin)
        assertEquals(GuildBossBalance.WEEKLY_BOSS_RANK_REWARDS[2].bossCoin, profiles[4L]!!.bossCoin)
        assertEquals(0L, profiles[1L]!!.bossCoin)
    }

    @Test
    fun `reset should tolerate missing user rows and still clear the board`() {
        // 成员行有伤害但 user 缺失（历史数据漂移）：跳过该名发奖，其余照常获奖、清零不失败
        val orphan = member(9L, 1L, 500)
        val normal = member(9L, 2L, 100)
        stubMembers(orphan, normal)
        whenever(userRepository.findById(1L)).thenReturn(Optional.empty())
        stubUser(2L)

        val guilds = service.resetWeeklyBossDamage()

        assertEquals(1, guilds)
        // 只有 2 号拿到奖励：名次按榜位固定（1 号被跳过不顶替），2 号是榜上第 2 名 → 第 2 档奖励
        verify(userRepository, times(1)).save(any())
        assertEquals(GuildBossBalance.WEEKLY_BOSS_RANK_REWARDS[1].bossCoin, profiles[2L]!!.bossCoin)
        assertEquals(GuildBossBalance.WEEKLY_BOSS_RANK_REWARDS[1].gold, profiles[2L]!!.gold)
        // 清零不受缺行影响
        assertEquals(0L, orphan.weeklyBossDamage)
        assertEquals(0L, normal.weeklyBossDamage)
    }

    @Test
    fun `reset with no members at all should be a no-op`() {
        whenever(guildMemberRepository.findAll()).thenReturn(emptyList())

        assertEquals(0, service.resetWeeklyBossDamage())

        verify(userRepository, never()).save(any())
        verify(guildMemberRepository, never()).saveAll(any<Iterable<GuildMember>>())
    }
}
