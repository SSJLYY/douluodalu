package com.douluodalu.game.service

import com.douluodalu.game.entity.Guild
import com.douluodalu.game.entity.GuildMember
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.repository.GuildMemberRepository
import com.douluodalu.game.repository.GuildRepository
import com.douluodalu.game.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.util.Optional

class GuildServiceTest {
    @Mock
    private lateinit var guildRepository: GuildRepository

    @Mock
    private lateinit var guildMemberRepository: GuildMemberRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var gameService: GameService

    @InjectMocks
    private lateinit var guildService: GuildService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    private fun userWith(gold: Long, level: Int = 30, guildId: Long? = null): Pair<UserEntity, PlayerProfileEntity> {
        val profile = PlayerProfileEntity(userId = 1L, level = level)
        profile.gold = gold
        profile.guildId = guildId
        val user = UserEntity(id = 1, username = "u", nickname = "n", passwordHash = "h")
        user.player = profile
        doReturn(Optional.of(user)).whenever(userRepository).findById(1L)
        return user to profile
    }

    @Test
    fun `createGuild should deduct 10000 gold, save guild and bind player`() {
        val (user, profile) = userWith(gold = 25000, level = 35)
        doAnswer { inv ->
            val g = inv.arguments[0] as Guild
            g.id = 77L
            g
        }.whenever(guildRepository).save(any())

        val guild = guildService.createGuild(1L, "唐门", "紫极魔瞳")

        assertNotNull(guild)
        assertEquals(77L, guild!!.id)
        assertEquals("唐门", guild.name)
        assertEquals(1L, guild.leaderId)
        assertEquals(15000L, profile.gold)
        assertEquals(77L, profile.guildId)
        verify(guildRepository).save(any())
        verify(userRepository).save(user)
    }

    @Test
    fun `createGuild should persist leader record in guild_member table`() {
        userWith(gold = 25000, level = 35)
        doAnswer { inv ->
            val g = inv.arguments[0] as Guild
            g.id = 77L
            g
        }.whenever(guildRepository).save(any())

        val guild = guildService.createGuild(1L, "唐门", "紫极魔瞳")

        assertNotNull(guild)
        val captor = ArgumentCaptor.forClass(GuildMember::class.java)
        verify(guildMemberRepository).save(captor.capture())
        assertEquals(77L, captor.value.guildId)
        assertEquals(1L, captor.value.userId)
        assertEquals("LEADER", captor.value.role)
    }

    @Test
    fun `joinGuild should persist member record in guild_member table`() {
        userWith(gold = 100, level = 30, guildId = null)
        val guild = Guild(id = 9L, name = "唐门", level = 1, currentMembers = 3, maxMembers = 20, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)

        val ok = guildService.joinGuild(1L, 9L)

        assertTrue(ok)
        assertEquals(4, guild.currentMembers)
        val captor = ArgumentCaptor.forClass(GuildMember::class.java)
        verify(guildMemberRepository).save(captor.capture())
        assertEquals(9L, captor.value.guildId)
        assertEquals(1L, captor.value.userId)
        assertEquals("MEMBER", captor.value.role)
    }

    @Test
    fun `leaveGuild should delete member record from guild_member table`() {
        userWith(gold = 100, level = 30, guildId = 9L)
        val guild = Guild(id = 9L, name = "唐门", level = 1, currentMembers = 4, maxMembers = 20, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)

        val ok = guildService.leaveGuild(1L)

        assertTrue(ok)
        assertEquals(3, guild.currentMembers)
        verify(guildMemberRepository).deleteByUserId(1L)
    }

    @Test
    fun `createGuild should be rejected when player already in a guild`() {
        userWith(gold = 25000, level = 35, guildId = 5L)

        val guild = guildService.createGuild(1L, "重开一门", "")

        assertNull(guild)
        verify(guildRepository, never()).save(any())
        verify(guildMemberRepository, never()).save(any())
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `createGuild should be rejected when level or gold insufficient`() {
        // 等级不足（<30）
        userWith(gold = 25000, level = 20)
        assertNull(guildService.createGuild(1L, "早门", ""))

        // 金币不足（<10000）
        userWith(gold = 9999, level = 30)
        assertNull(guildService.createGuild(1L, "穷门", ""))

        verify(guildRepository, never()).save(any())
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `donate should deduct gold, add guild exp and trigger level up`() {
        val (user, profile) = userWith(gold = 5000, guildId = 9L)
        val guild = Guild(id = 9L, name = "唐门", level = 1, exp = 950, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)

        val ok = guildService.donate(1L, 2000L)

        assertTrue(ok)
        assertEquals(3000L, profile.gold)
        // exp: 950 + 2000/100 = 970 → 未达 1000？ 970 < 1000 不升级
        assertEquals(970L, guild.exp)
        assertEquals(1, guild.level)
        verify(guildRepository).save(guild)
        verify(userRepository).save(user)
    }

    @Test
    fun `donate should level up guild when exp threshold reached`() {
        val (user, profile) = userWith(gold = 120000, guildId = 9L)
        val guild = Guild(id = 9L, name = "唐门", level = 1, exp = 100, maxMembers = 20, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)

        val ok = guildService.donate(1L, 100000L)

        assertTrue(ok)
        assertEquals(20000L, profile.gold)
        // exp: 100 + 100000/100 = 1100 ≥ 1*1000 → 升到 2 级，余 100 经验，人数上限 +5
        assertEquals(2, guild.level)
        assertEquals(100L, guild.exp)
        assertEquals(25, guild.maxMembers)
        verify(guildRepository).save(guild)
    }

    @Test
    fun `donate should be rejected when not in guild or gold insufficient, no writes`() {
        // 未入宗门
        userWith(gold = 50000, guildId = null)
        assertFalse(guildService.donate(1L, 100L))

        // 金币不足
        userWith(gold = 50, guildId = 9L)
        assertFalse(guildService.donate(1L, 100L))

        verify(guildRepository, never()).save(any())
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `challengeBoss should be rejected for player without guild and cause no writes`() {
        val (user, profile) = userWith(gold = 1000, guildId = null)

        val response = guildService.challengeBoss(1L)

        assertNull(response)
        verify(gameService, never()).rollBackpackDrop(any(), any())
        verify(guildRepository, never()).save(any())
        verify(userRepository, never()).save(any())
    }
}
