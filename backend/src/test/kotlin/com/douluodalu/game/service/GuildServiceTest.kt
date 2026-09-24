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
import com.douluodalu.game.model.GuildBossBalance
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
        // 模拟 DB 端条件原子自增
        doAnswer { guild.currentMembers += 1; 1 }.whenever(guildRepository).tryJoinMemberCount(9L)

        val ok = guildService.joinGuild(1L, 9L)

        assertTrue(ok)
        assertEquals(4, guild.currentMembers)
        // 名额走原子 UPDATE，不再整行 save 回写（避免覆盖并发者的计数）
        verify(guildRepository, never()).save(any())
        val captor = ArgumentCaptor.forClass(GuildMember::class.java)
        verify(guildMemberRepository).save(captor.capture())
        assertEquals(9L, captor.value.guildId)
        assertEquals(1L, captor.value.userId)
        assertEquals("MEMBER", captor.value.role)
    }

    @Test
    fun `joinGuild should enforce capacity atomically when concurrent joins race for the last slot`() {
        userWith(gold = 100, level = 30, guildId = null)
        val guild = Guild(id = 9L, name = "唐门", level = 1, currentMembers = 19, maxMembers = 20, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)
        doReturn(null).whenever(guildMemberRepository).findByUserId(1L)
        // 模拟 DB 行锁语义：只剩 1 个名额，条件 UPDATE 只会成功一次
        val slotsLeft = AtomicInteger(1)
        doAnswer { if (slotsLeft.getAndDecrement() > 0) { guild.currentMembers += 1; 1 } else 0 }
            .whenever(guildRepository).tryJoinMemberCount(9L)

        val threads = 2
        val start = CountDownLatch(1)
        val results = java.util.concurrent.ConcurrentLinkedQueue<Boolean>()
        val pool = Executors.newFixedThreadPool(threads)
        val errors = AtomicReference<Throwable?>(null)
        repeat(threads) {
            pool.submit {
                try {
                    start.await()
                    results.add(guildService.joinGuild(1L, 9L))
                } catch (t: Throwable) {
                    errors.compareAndSet(null, t)
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        errors.get()?.let { throw it }

        // 两个并发 join：只有原子占位成功者入场，人数上限不被击穿
        assertEquals(1, results.count { it }, "并发双加入只应成功一次: $results")
        assertEquals(20, guild.currentMembers)
        verify(guildMemberRepository, times(1)).save(any())
    }

    @Test
    fun `joinGuild should reject when a stale guild_member row already exists`() {
        userWith(gold = 100, level = 30, guildId = null)
        val guild = Guild(id = 9L, name = "唐门", level = 1, currentMembers = 3, maxMembers = 20, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)
        // player.guildId 为空但成员表仍挂着旧宗门行（历史并发残留/数据漂移）
        doReturn(GuildMember(guildId = 5L, userId = 1L, role = "MEMBER"))
            .whenever(guildMemberRepository).findByUserId(1L)

        assertFalse(guildService.joinGuild(1L, 9L))

        verify(guildRepository, never()).tryJoinMemberCount(any())
        verify(guildMemberRepository, never()).save(any())
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `leaveGuild should delete member record from guild_member table`() {
        userWith(gold = 100, level = 30, guildId = 9L)
        val guild = Guild(id = 9L, name = "唐门", level = 1, currentMembers = 4, maxMembers = 20, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)
        doAnswer { guild.currentMembers -= 1; 1 }.whenever(guildRepository).tryLeaveMemberCount(9L)

        val ok = guildService.leaveGuild(1L)

        assertTrue(ok)
        assertEquals(3, guild.currentMembers)
        verify(guildMemberRepository).deleteByUserId(1L)
        verify(guildRepository, never()).save(any())
    }

    @Test
    fun `leaveGuild should still detach player when member count is already desynced at zero`() {
        userWith(gold = 100, level = 30, guildId = 9L)
        val guild = Guild(id = 9L, name = "唐门", level = 1, currentMembers = 0, maxMembers = 20, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)
        // 计数已被历史竞态压到 0：条件 UPDATE 影响 0 行，但玩家退出本身不能被阻塞
        doReturn(0).whenever(guildRepository).tryLeaveMemberCount(9L)

        assertTrue(guildService.leaveGuild(1L))

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

    // ==================== 任务#24 补盲 ====================

    @Test
    fun `donate should reject non-positive amount instead of minting gold`() {
        val (user, profile) = userWith(gold = 1000, guildId = 9L)
        val guild = Guild(id = 9L, name = "唐门", level = 1, exp = 500, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)

        // 负数捐献若放行：gold -= (-500000) 直接凭空造币，exp 还会被扣成负数
        assertFalse(guildService.donate(1L, -500_000L))
        assertFalse(guildService.donate(1L, 0L))

        assertEquals(1000L, profile.gold)
        assertEquals(500L, guild.exp)
        verify(guildRepository, never()).save(any())
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `donate with one huge amount should level up across multiple thresholds`() {
        val (user, profile) = userWith(gold = 1_100_000, guildId = 9L)
        val guild = Guild(id = 9L, name = "唐门", level = 1, exp = 100, maxMembers = 20, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)

        assertTrue(guildService.donate(1L, 1_000_000L))

        // exp: 100 + 10000 = 10100，依次跨过 1000/2000/3000/4000 四道门槛 → 5 级余 100
        assertEquals(5, guild.level)
        assertEquals(100L, guild.exp)
        assertEquals(40, guild.maxMembers)
        assertEquals(100_000L, profile.gold)
    }

    @Test
    fun `challengeBoss guild exp should also trigger level up like donate does`() {
        val (user, profile) = userWith(gold = 0, level = 50, guildId = 9L)
        profile.bossCoin = 0
        val guild = Guild(id = 9L, name = "唐门", level = 1, exp = 990, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        assertNotNull(resp)
        // 伤害下限 50*120=6000，远超 0.35*(1800+650)=857 → 必胜
        assertTrue(resp!!.won)
        assertEquals(GuildBossBalance.WIN_BOSS_COIN_BASE + 1, profile.bossCoin)
        // 胜利 +30 经验：990+30=1020 ≥ 1*1000 → 应升到 2 级余 20（与 donate 的升级判定一致）
        assertEquals(2, guild.level)
        assertEquals(20L, guild.exp)
    }

    @Test
    fun `createGuild should reject blank or duplicate name without deducting gold`() {
        val (user, profile) = userWith(gold = 25000, level = 35)
        doReturn(true).whenever(guildRepository).existsByName("唐门")

        // guild.name 带 UNIQUE 约束，重名直接落库会抛 DataIntegrityViolation（500 + 泄漏 DB 细节）
        assertNull(guildService.createGuild(1L, "唐门", "重名"))
        assertNull(guildService.createGuild(1L, "   ", "空白名"))

        assertEquals(25000L, profile.gold)
        verify(guildRepository, never()).save(any())
        verify(guildMemberRepository, never()).save(any())
        verify(userRepository, never()).save(any())
    }
}
