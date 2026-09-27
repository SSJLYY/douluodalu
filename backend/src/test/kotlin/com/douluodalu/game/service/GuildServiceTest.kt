package com.douluodalu.game.service

import com.douluodalu.game.entity.Guild
import com.douluodalu.game.entity.GuildBossEntity
import com.douluodalu.game.entity.GuildMember
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.repository.GuildBossRepository
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
import org.mockito.Spy
import org.mockito.kotlin.*
import com.douluodalu.game.model.GuildBossBalance
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.LocalDateTime
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
    private lateinit var guildBossRepository: GuildBossRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var gameService: GameService

    @Mock
    private lateinit var dailyQuestService: DailyQuestService

    @Mock
    private lateinit var webSocketService: WebSocketService

    /** 真实 Micrometer 注册表（Spy 包装保留真实计数行为；计数器断言用） */
    @Spy
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry()

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

        val result = guildService.leaveGuild(1L)

        assertNotNull(result)
        assertEquals(3, guild.currentMembers)
        // 普通成员退出：message 兼容旧契约，无解散/转让分歧
        assertEquals("已退出宗门", result!!.message)
        assertFalse(result.disbanded)
        assertNull(result.transferredTo)
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

        assertNotNull(guildService.leaveGuild(1L))

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
        doReturn(guild).whenever(guildRepository).findByIdForUpdate(9L)
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

    // ==================== 任务#26 公会管理玩法 ====================

    private fun stubOtherUser(id: Long, guildId: Long?): UserEntity {
        val profile = PlayerProfileEntity(userId = id, level = 20)
        profile.guildId = guildId
        profile.gold = 1000
        val user = UserEntity(id = id, username = "u$id", nickname = "n$id", passwordHash = "h")
        user.player = profile
        doReturn(Optional.of(user)).whenever(userRepository).findById(id)
        return user
    }

    private fun leaderGuild(guildId: Long = 9L, leaderId: Long = 1L, members: Int = 3): Guild {
        val guild = Guild(id = guildId, name = "唐门", level = 1, currentMembers = members, maxMembers = 20, leaderId = leaderId)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(guildId)
        // 共享血量 Boss 的行锁读（challengeBoss/getGuildBossStatus 走 FOR UPDATE 锚 guild 行）
        doReturn(guild).whenever(guildRepository).findByIdForUpdate(guildId)
        return guild
    }

    // ---- kickMember 权限矩阵 ----

    @Test
    fun `kickMember by leader should remove row, clear target guildId and release slot atomically`() {
        userWith(gold = 100, guildId = 9L) // 操作者 = 宗主 userId 1
        val target = stubOtherUser(2L, guildId = 9L)
        val guild = leaderGuild()
        val targetMember = GuildMember(guildId = 9L, userId = 2L, role = "MEMBER")
        doReturn(targetMember).whenever(guildMemberRepository).findByUserId(2L)
        doAnswer { guild.currentMembers -= 1; 1 }.whenever(guildRepository).tryLeaveMemberCount(9L)

        assertTrue(guildService.kickMember(1L, 2L))

        assertNull(target.player?.guildId)
        verify(guildMemberRepository).delete(targetMember)
        verify(guildRepository).tryLeaveMemberCount(9L)
        assertEquals(2, guild.currentMembers)
        // 计数走原子 UPDATE，不整行 save 回写
        verify(guildRepository, never()).save(any())
    }

    @Test
    fun `kickMember should be rejected for non-leader operator`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild(leaderId = 99L) // 宗主是别人

        assertFalse(guildService.kickMember(1L, 2L))

        verify(guildMemberRepository, never()).delete(any())
        verify(guildRepository, never()).tryLeaveMemberCount(any())
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `kickMember should refuse self-kick`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild()

        assertFalse(guildService.kickMember(1L, 1L))

        verify(guildMemberRepository, never()).delete(any())
        verify(guildRepository, never()).tryLeaveMemberCount(any())
    }

    @Test
    fun `kickMember should reject target outside own guild without touching counts`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild()
        // 目标在别的宗门（甚至无宗门）：成员行 guildId 不匹配
        doReturn(GuildMember(guildId = 5L, userId = 2L, role = "MEMBER"))
            .whenever(guildMemberRepository).findByUserId(2L)
        assertFalse(guildService.kickMember(1L, 2L))

        // 目标完全不在成员表
        doReturn(null).whenever(guildMemberRepository).findByUserId(3L)
        assertFalse(guildService.kickMember(1L, 3L))

        verify(guildMemberRepository, never()).delete(any())
        verify(guildRepository, never()).tryLeaveMemberCount(any())
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `kickMember should abort without half-applied writes when target user row is missing`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild()
        doReturn(GuildMember(guildId = 9L, userId = 2L, role = "MEMBER"))
            .whenever(guildMemberRepository).findByUserId(2L)
        // userRepository.findById(2L) 未打桩 → Optional.empty()：成员行成孤儿前的三处一致要求

        assertFalse(guildService.kickMember(1L, 2L))

        verify(guildMemberRepository, never()).delete(any())
        verify(guildRepository, never()).tryLeaveMemberCount(any())
    }

    // ---- transferLeadership 权限矩阵 ----

    @Test
    fun `transferLeadership should CAS leaderId and swap member roles without whole-row save`() {
        userWith(gold = 100, guildId = 9L)
        val guild = leaderGuild()
        val leaderRow = GuildMember(guildId = 9L, userId = 1L, role = "LEADER")
        val targetRow = GuildMember(guildId = 9L, userId = 2L, role = "MEMBER")
        doReturn(listOf(leaderRow, targetRow)).whenever(guildMemberRepository).findByGuildId(9L)
        doReturn(1).whenever(guildRepository).transferLeaderId(9L, 2L, 1L)

        assertTrue(guildService.transferLeadership(1L, 2L))

        // leaderId 更新走条件原子 UPDATE（compare-and-set），不整行 save 覆盖并发计数
        verify(guildRepository).transferLeaderId(9L, 2L, 1L)
        verify(guildRepository, never()).save(any())
        assertEquals("LEADER", targetRow.role)
        assertEquals("MEMBER", leaderRow.role)
        verify(guildMemberRepository).save(targetRow)
        verify(guildMemberRepository).save(leaderRow)
        assertEquals(9L, guild.id)
    }

    @Test
    fun `transferLeadership should be rejected for non-leader and for unknown target`() {
        // 非宗主
        userWith(gold = 100, guildId = 9L)
        leaderGuild(leaderId = 99L)
        assertFalse(guildService.transferLeadership(1L, 2L))

        // 宗主但目标不在成员表
        userWith(gold = 100, guildId = 9L)
        leaderGuild(leaderId = 1L)
        doReturn(listOf(GuildMember(guildId = 9L, userId = 1L, role = "LEADER")))
            .whenever(guildMemberRepository).findByGuildId(9L)
        assertFalse(guildService.transferLeadership(1L, 77L))

        verify(guildRepository, never()).transferLeaderId(any(), any(), any())
    }

    @Test
    fun `transferLeadership should fail cleanly when concurrent transfer already replaced the leader`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild()
        doReturn(listOf(
            GuildMember(guildId = 9L, userId = 1L, role = "LEADER"),
            GuildMember(guildId = 9L, userId = 2L, role = "MEMBER")
        )).whenever(guildMemberRepository).findByGuildId(9L)
        // CAS 失败：读 leader 与 UPDATE 之间已被另一笔转让换主
        doReturn(0).whenever(guildRepository).transferLeaderId(9L, 2L, 1L)

        assertFalse(guildService.transferLeadership(1L, 2L))

        verify(guildMemberRepository, never()).save(any())
    }

    @Test
    fun `transferLeadership to self should be rejected without any lookups`() {
        assertFalse(guildService.transferLeadership(1L, 1L))
        verify(guildRepository, never()).transferLeaderId(any(), any(), any())
    }

    // ---- disbandGuild 权限矩阵 ----

    @Test
    fun `disbandGuild by solo leader should delete guild and member rows, detach player and keep gold burnt`() {
        val (user, profile) = userWith(gold = 100, guildId = 9L)
        val guild = leaderGuild()
        doReturn(1L).whenever(guildMemberRepository).countByGuildId(9L)

        assertTrue(guildService.disbandGuild(1L))

        verify(guildMemberRepository).deleteByGuildId(9L)
        verify(guildRepository).delete(guild)
        assertNull(profile.guildId)
        verify(userRepository).save(user)
        // 创建费不退（有意取舍）：解散后金币仍只有原来的 100
        assertEquals(100L, profile.gold)
    }

    @Test
    fun `disbandGuild should be rejected while other members remain`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild()
        doReturn(2L).whenever(guildMemberRepository).countByGuildId(9L)

        assertFalse(guildService.disbandGuild(1L))

        verify(guildRepository, never()).delete(any())
        verify(guildMemberRepository, never()).deleteByGuildId(any())
    }

    @Test
    fun `disbandGuild should be rejected for plain member`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild(leaderId = 99L)

        assertFalse(guildService.disbandGuild(1L))

        verify(guildMemberRepository, never()).countByGuildId(any())
        verify(guildRepository, never()).delete(any())
    }

    // ---- leaveGuild 宗主新语义 ----

    @Test
    fun `leaveGuild as leader with members should auto-transfer to earliest joined member then leave`() {
        userWith(gold = 100, guildId = 9L)
        val guild = leaderGuild(members = 3)
        val early = GuildMember(guildId = 9L, userId = 2L, role = "MEMBER", joinedAt = LocalDateTime.of(2026, 1, 1, 0, 0))
        val late = GuildMember(guildId = 9L, userId = 3L, role = "MEMBER", joinedAt = LocalDateTime.of(2026, 2, 1, 0, 0))
        // 乱序返回，验证按 joinedAt 取最早
        doReturn(listOf(late, early, GuildMember(guildId = 9L, userId = 1L, role = "LEADER")))
            .whenever(guildMemberRepository).findByGuildId(9L)
        doReturn(1).whenever(guildRepository).transferLeaderId(9L, 2L, 1L)
        doAnswer { guild.currentMembers -= 1; 1 }.whenever(guildRepository).tryLeaveMemberCount(9L)
        // 继任者昵称走 findById（单 id 无 N+1 场景）
        val successor = stubOtherUser(2L, guildId = 9L)

        val result = guildService.leaveGuild(1L)

        assertNotNull(result)
        verify(guildRepository).transferLeaderId(9L, 2L, 1L)
        assertEquals("LEADER", early.role)
        verify(guildMemberRepository).save(early)
        verify(guildMemberRepository).deleteByUserId(1L)
        assertEquals(2, guild.currentMembers)
        // 转让分歧：disbanded=false 且 transferredTo 带继任者昵称
        assertFalse(result!!.disbanded)
        assertEquals(successor.nickname, result.transferredTo)
    }

    @Test
    fun `leaveGuild as the last remaining member of the guild should disband it`() {
        val (user, profile) = userWith(gold = 100, guildId = 9L)
        val guild = leaderGuild(members = 1)
        doReturn(listOf(GuildMember(guildId = 9L, userId = 1L, role = "LEADER")))
            .whenever(guildMemberRepository).findByGuildId(9L)

        val result = guildService.leaveGuild(1L)

        assertNotNull(result)
        verify(guildMemberRepository).deleteByGuildId(9L)
        verify(guildRepository).delete(guild)
        assertNull(profile.guildId)
        // 走解散路径：不再需要递减人数
        verify(guildRepository, never()).tryLeaveMemberCount(any())
        // 解散分歧：disbanded=true，message 兼容字段保留
        assertTrue(result!!.disbanded)
        assertNull(result.transferredTo)
        assertEquals("已退出宗门", result.message)
    }

    @Test
    fun `leaveGuild as leader should abort when concurrent transfer already changed leader`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild(members = 2)
        doReturn(listOf(
            GuildMember(guildId = 9L, userId = 1L, role = "LEADER"),
            GuildMember(guildId = 9L, userId = 2L, role = "MEMBER")
        )).whenever(guildMemberRepository).findByGuildId(9L)
        doReturn(0).whenever(guildRepository).transferLeaderId(9L, 2L, 1L)

        assertNull(guildService.leaveGuild(1L))

        verify(guildMemberRepository, never()).deleteByUserId(any())
        verify(guildRepository, never()).tryLeaveMemberCount(any())
        verify(userRepository, never()).save(any())
    }

    // ---- contribution 累计 ----

    @Test
    fun `donate should accumulate contribution on the member row by donated amount`() {
        userWith(gold = 5000, guildId = 9L)
        val guild = leaderGuild()
        val member = GuildMember(guildId = 9L, userId = 1L, role = "MEMBER", contribution = 40L)
        doReturn(member).whenever(guildMemberRepository).findByUserId(1L)

        assertTrue(guildService.donate(1L, 600L))

        assertEquals(640L, member.contribution)
        verify(guildMemberRepository).save(member)
    }

    @Test
    fun `challengeBoss should accumulate contribution proportional to damage`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        val guild = leaderGuild()
        val member = GuildMember(guildId = 9L, userId = 1L, role = "MEMBER")
        doReturn(member).whenever(guildMemberRepository).findByUserId(1L)
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        assertNotNull(resp)
        assertEquals(resp!!.damage / GuildBossBalance.CONTRIBUTION_PER_DAMAGE, member.contribution)
        verify(guildMemberRepository).save(member)
    }

    // ==================== 任务#28 getGuildMembers ====================

    private fun stubBatchUsers(vararg users: UserEntity) {
        doReturn(users.toList()).whenever(userRepository).findAllById(any())
    }

    @Test
    fun `getGuildMembers should return null for player without guild`() {
        userWith(gold = 100, guildId = null)

        assertNull(guildService.getGuildMembers(1L))

        // 无宗门是业务拒绝而非查询：不应触达 guild/member 表
        verify(guildRepository, never()).findById(any())
        verify(guildMemberRepository, never()).findByGuildId(any())
    }

    @Test
    fun `getGuildMembers should sort members by joinedAt ascending`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild()
        val leader = GuildMember(guildId = 9L, userId = 1L, role = "LEADER", joinedAt = LocalDateTime.of(2026, 1, 1, 0, 0))
        val early = GuildMember(guildId = 9L, userId = 2L, joinedAt = LocalDateTime.of(2026, 3, 1, 0, 0), contribution = 5L)
        val late = GuildMember(guildId = 9L, userId = 3L, joinedAt = LocalDateTime.of(2026, 2, 1, 0, 0), contribution = 7L)
        // 乱序返回，验证服务端排序而非依赖 DB 顺序
        doReturn(listOf(late, early, leader)).whenever(guildMemberRepository).findByGuildId(9L)
        doReturn(listOf(
            UserEntity(id = 1L, username = "a", nickname = "宗主甲", passwordHash = "h"),
            UserEntity(id = 2L, username = "b", nickname = "乙", passwordHash = "h"),
            UserEntity(id = 3L, username = "c", nickname = "丙", passwordHash = "h")
        )).whenever(userRepository).findAllById(any())

        val members = guildService.getGuildMembers(1L)

        assertNotNull(members)
        assertEquals(listOf(1L, 3L, 2L), members!!.map { it.userId })
        assertEquals(listOf("宗主甲", "丙", "乙"), members.map { it.nickname })
        assertEquals(listOf(0L, 7L, 5L), members.map { it.contribution })
    }

    @Test
    fun `getGuildMembers should mark isLeader from guild leaderId even if member role row is stale`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild(leaderId = 2L) // 宗主是 2 号，操作者 1 号只是成员
        val staleLeaderRole = GuildMember(guildId = 9L, userId = 1L, role = "LEADER") // 转让后 role 副本漂移
        val realLeader = GuildMember(guildId = 9L, userId = 2L, role = "MEMBER")
        doReturn(listOf(staleLeaderRole, realLeader)).whenever(guildMemberRepository).findByGuildId(9L)
        doReturn(listOf(
            UserEntity(id = 1L, username = "a", nickname = "甲", passwordHash = "h"),
            UserEntity(id = 2L, username = "b", nickname = "乙", passwordHash = "h")
        )).whenever(userRepository).findAllById(any())

        val members = guildService.getGuildMembers(1L)

        assertNotNull(members)
        // isLeader 以 guild.leader_id 为唯一事实源，不信任成员行 role 副本
        assertEquals(listOf(false, true), members!!.map { it.isLeader })
    }

    @Test
    fun `getGuildMembers should batch-fetch nicknames with one findAllById instead of per-row findById`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild(members = 4)
        doReturn(listOf(
            GuildMember(guildId = 9L, userId = 1L, role = "LEADER"),
            GuildMember(guildId = 9L, userId = 2L),
            GuildMember(guildId = 9L, userId = 3L),
            GuildMember(guildId = 9L, userId = 4L)
        )).whenever(guildMemberRepository).findByGuildId(9L)
        stubBatchUsers(
            UserEntity(id = 1L, username = "a", nickname = "甲", passwordHash = "h"),
            UserEntity(id = 2L, username = "b", nickname = "乙", passwordHash = "h"),
            UserEntity(id = 3L, username = "c", nickname = "丙", passwordHash = "h"),
            UserEntity(id = 4L, username = "d", nickname = "丁", passwordHash = "h")
        )

        val members = guildService.getGuildMembers(1L)

        assertEquals(4, members!!.size)
        // 4 名成员昵称 = 恰好 1 次 findAllById；findById 全链只允许查操作者这 1 次
        verify(userRepository, times(1)).findAllById(any())
        verify(userRepository, times(1)).findById(any())
    }

    @Test
    fun `getGuildMembers should degrade to placeholder nickname when a user row is missing`() {
        userWith(gold = 100, guildId = 9L)
        leaderGuild(members = 2)
        doReturn(listOf(
            GuildMember(guildId = 9L, userId = 1L, role = "LEADER"),
            GuildMember(guildId = 9L, userId = 2L)
        )).whenever(guildMemberRepository).findByGuildId(9L)
        // users 表缺 2 号行（数据漂移）：列表接口不应整页 500，降级为占位昵称
        doReturn(listOf(UserEntity(id = 1L, username = "a", nickname = "甲", passwordHash = "h")))
            .whenever(userRepository).findAllById(any())

        val members = guildService.getGuildMembers(1L)

        assertEquals(listOf("甲", "未知用户"), members!!.map { it.nickname })
    }

    // ==================== 第二十二轮 宗门Boss周榜 ====================

    /** 周榜用：造带 guildId 的用户并打桩 findById（userWith 固定 userId=1，不满足多成员场景） */
    private fun stubGuildUser(id: Long, guildId: Long?, nickname: String = "n$id"): UserEntity {
        val profile = PlayerProfileEntity(userId = id, level = 20)
        profile.guildId = guildId
        val user = UserEntity(id = id, username = "u$id", nickname = nickname, passwordHash = "h")
        user.player = profile
        doReturn(Optional.of(user)).whenever(userRepository).findById(id)
        return user
    }

    @Test
    fun `challengeBoss should accumulate weekly boss damage on the member row`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        val member = GuildMember(guildId = 9L, userId = 1L, role = "MEMBER")
        doReturn(member).whenever(guildMemberRepository).findByUserId(1L)
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        assertNotNull(resp)
        // 周榜口径：本周伤害逐次累加（与总贡献两条独立累计线），单次挑战正好 +damage
        assertEquals(resp!!.damage, member.weeklyBossDamage)
        assertEquals(resp.damage / GuildBossBalance.CONTRIBUTION_PER_DAMAGE, member.contribution)
        verify(guildMemberRepository).save(member)
    }

    @Test
    fun `challengeBoss should increment douluo_guild_boss_total counter with outcome tag`() {
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        // 胜：level 50 伤害下限 50*120=6000 >> 0.35*(1800+650)=857，恒胜
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        guildService.challengeBoss(1L)

        assertEquals(1.0, meterRegistry.get(GuildService.METRIC_GUILD_BOSS_TOTAL)
            .tag("outcome", "win").counter().count(),
            "douluo.guild.boss.total{outcome=win} 应 +1（ops 指标契约名逐字）")

        // 败：level 1 伤害上限 120+100(battleSoulPower)+159 < 857，恒败
        userWith(gold = 0, level = 1, guildId = 9L)
        guildService.challengeBoss(1L)

        assertEquals(1.0, meterRegistry.get(GuildService.METRIC_GUILD_BOSS_TOTAL)
            .tag("outcome", "lose").counter().count(),
            "douluo.guild.boss.total{outcome=lose} 应 +1（同一计数器按 tag 分列）")

        // 未入宗门的拒绝路径不计入计数器（仍只有 win/lose 两列）
        userWith(gold = 0, level = 50, guildId = null)
        assertNull(guildService.challengeBoss(1L))
        assertEquals(2, meterRegistry.meters
            .count { it.id.name == GuildService.METRIC_GUILD_BOSS_TOTAL })
    }

    @Test
    fun `getGuildBossRank should sort descending include only positive damage and batch nicknames`() {
        stubGuildUser(1L, 9L, "甲")
        stubGuildUser(2L, 9L, "乙")
        stubGuildUser(3L, 9L, "丙")
        leaderGuild()
        doReturn(listOf(
            GuildMember(guildId = 9L, userId = 1L, weeklyBossDamage = 100L),
            GuildMember(guildId = 9L, userId = 2L, weeklyBossDamage = 300L),
            GuildMember(guildId = 9L, userId = 3L, weeklyBossDamage = 0L),
            GuildMember(guildId = 9L, userId = 4L, weeklyBossDamage = 200L)
        )).whenever(guildMemberRepository).findByGuildId(9L)
        // 4 号用户行缺失（数据漂移）：列表接口降级为占位昵称，不整页 500
        doReturn(listOf(
            UserEntity(id = 1L, username = "a", nickname = "甲", passwordHash = "h"),
            UserEntity(id = 2L, username = "b", nickname = "乙", passwordHash = "h"),
            UserEntity(id = 3L, username = "c", nickname = "丙", passwordHash = "h")
        )).whenever(userRepository).findAllById(any())

        val rank = guildService.getGuildBossRank(1L)

        // 降序、只含 >0 成员（3 号 0 伤害不入榜）
        assertEquals(listOf(2L, 4L, 1L), rank.entries.map { it.userId })
        assertEquals(listOf(300L, 200L, 100L), rank.entries.map { it.weeklyDamage })
        assertEquals(listOf("乙", "未知用户", "甲"), rank.entries.map { it.nickname })
        assertFalse(rank.entries.any { it.userId == 3L })
        // myRank = 操作者全量降序名次（1-based）
        assertEquals(3, rank.myRank)
        // 昵称批查防 N+1：恰好 1 次 findAllById；findById 全链只有操作者这 1 次
        verify(userRepository, times(1)).findAllById(any())
        verify(userRepository, times(1)).findById(any())
    }

    @Test
    fun `getGuildBossRank should return myRank 0 for a caller with no weekly damage`() {
        stubGuildUser(1L, 9L)
        stubGuildUser(2L, 9L)
        leaderGuild()
        doReturn(listOf(
            GuildMember(guildId = 9L, userId = 1L, weeklyBossDamage = 0L),
            GuildMember(guildId = 9L, userId = 2L, weeklyBossDamage = 50L)
        )).whenever(guildMemberRepository).findByGuildId(9L)
        doReturn(listOf(UserEntity(id = 2L, username = "b", nickname = "乙", passwordHash = "h")))
            .whenever(userRepository).findAllById(any())

        val rank = guildService.getGuildBossRank(1L)

        // 自己 0 伤害不入榜（entries 只有他人），myRank=0
        assertEquals(listOf(2L), rank.entries.map { it.userId })
        assertEquals(0, rank.myRank)
    }

    @Test
    fun `getGuildBossRank should cap entries at 10 while myRank reflects the full ranking`() {
        stubGuildUser(1L, 9L)
        leaderGuild(members = 12)
        // 12 名成员伤害 = userId×10（1 号 10 分最低）→ 降序前 10 名为 userId 12..3
        doReturn((1L..12L).map { GuildMember(guildId = 9L, userId = it, weeklyBossDamage = it * 10) })
            .whenever(guildMemberRepository).findByGuildId(9L)
        doReturn((1L..10L).map { UserEntity(id = it, username = "u$it", nickname = "n$it", passwordHash = "h") })
            .whenever(userRepository).findAllById(any())

        val rank = guildService.getGuildBossRank(1L)

        assertEquals(10, rank.entries.size, "entries 最多 10 条（契约上限）")
        assertEquals((12L downTo 3L).toList(), rank.entries.map { it.userId })
        // 昵称只查上榜的 10 人（1 号不在内，不浪费批查）
        verify(userRepository).findAllById((12L downTo 3L).toList())
        // myRank 按全量降序算：1 号伤害垫底 → 第 12 名（与「无伤害记录 0」语义分离）
        assertEquals(12, rank.myRank)
    }

    @Test
    fun `getGuildBossRank should throw business exception for player without guild`() {
        stubGuildUser(1L, null)

        val ex = assertThrows(IllegalArgumentException::class.java) { guildService.getGuildBossRank(1L) }

        // 消息含「宗门」：GlobalExceptionHandler 转 400 的既有语义
        assertTrue(ex.message!!.contains("宗门"))
        // 业务拒绝：不应触达 guild/member 查询
        verify(guildRepository, never()).findById(any())
        verify(guildMemberRepository, never()).findByGuildId(any())
    }

    // ==================== 第二十三轮 共享血量宗门 Boss（weekly raid 化） ====================

    /** 血池测试辅助：preset 一行本周血池 */
    private fun bossPool(
        guildId: Long = 9L,
        currentHp: Long,
        maxHp: Long = GuildBossBalance.weeklyBossMaxHp(1),
        killed: Boolean = false,
        weekStart: java.time.LocalDate = GuildBossBalance.currentWeekMonday()
    ) = GuildBossEntity(guildId = guildId, currentHp = currentHp, maxHp = maxHp, killed = killed, weekStart = weekStart)

    /** 血池测试辅助：打桩成员行（击杀/扣血路径会累计贡献与周伤） */
    private fun stubBossMember() {
        doReturn(GuildMember(guildId = 9L, userId = 1L, role = "MEMBER"))
            .whenever(guildMemberRepository).findByUserId(1L)
    }

    @Test
    fun `challengeBoss should lazily create the shared weekly boss pool on first challenge`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        doReturn(null).whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        // 两次 save：第 1 次 = 惰性建行（满血新池），第 2 次 = 本次伤害扣减落库。
        // captor 捕获的是同一实体引用（建行后被扣减），故按最终池状态断言：
        val captor = ArgumentCaptor.forClass(GuildBossEntity::class.java)
        verify(guildBossRepository, times(2)).save(captor.capture())
        val row = captor.allValues[0]
        val maxHp = GuildBossBalance.weeklyBossMaxHp(1) // (1800 + 1×650) × 10 = 24500
        assertEquals(maxHp, row.maxHp)
        assertFalse(row.killed)
        assertEquals(GuildBossBalance.currentWeekMonday(), row.weekStart)
        // 建行满血 → 扣一次伤害：剩余 = maxHp - damage（建行时 currentHp=maxHp 的间接证明）
        assertEquals(maxHp - resp!!.damage, resp.bossHp)
        assertEquals(maxHp - resp.damage, row.currentHp)
        assertFalse(resp.killed)
    }

    @Test
    fun `challengeBoss should deduct damage from the shared pool exactly once`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        val maxHp = GuildBossBalance.weeklyBossMaxHp(1)
        val pool = bossPool(currentHp = maxHp)
        doReturn(pool).whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        // 精确扣血：pool -= damage（level=50 伤害 ∈ [6100, 6259]，远小于 24500，不击杀）
        assertEquals(maxHp - resp!!.damage, pool.currentHp)
        assertEquals(maxHp - resp.damage, resp.bossHp)
        assertEquals(maxHp, resp.bossMaxHp)
        assertFalse(resp.killed)
        assertFalse(pool.killed)
    }

    @Test
    fun `challengeBoss should clamp overkill damage at remaining hp and complete the kill`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        val pool = bossPool(currentHp = 500L) // 剩余 500 << 单次伤害 6100+
        doReturn(pool).whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        // 伤害超剩余血按剩余算：血池归零而非负数；扣减后 == 0 → killed
        assertEquals(0L, pool.currentHp)
        assertTrue(pool.killed)
        assertEquals(0L, resp!!.bossHp)
        assertEquals(GuildBossBalance.weeklyBossMaxHp(1), resp.bossMaxHp)
        assertTrue(resp.killed)
        // 响应 damage 仍按完整伤害口径（既有逻辑不变），血条按剩余
        assertTrue(resp.damage > 500L)
    }

    @Test
    fun `challengeBoss kill should grant killer bonus boss coin gold message and count the kill`() {
        val (user, profile) = userWith(gold = 0, level = 50, guildId = 9L)
        profile.bossCoin = 0
        leaderGuild()
        doReturn(bossPool(currentHp = 500L)).whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        // 击杀奖励：常规胜奖（金币 damage/12、Boss币 6+guildLevel=7）+ 击杀加成（30 币 + 3000 金）
        assertEquals(resp!!.damage / GuildBossBalance.GOLD_PER_DAMAGE_DIVISOR + GuildBossBalance.GUILD_BOSS_KILL_GOLD, resp.goldGained)
        assertEquals(GuildBossBalance.WIN_BOSS_COIN_BASE + 1 + GuildBossBalance.GUILD_BOSS_KILL_BOSS_COIN, resp.bossCoinGained)
        assertEquals(resp.goldGained, profile.gold)
        assertEquals(resp.bossCoinGained, profile.bossCoin)
        assertTrue(resp.message.contains("全员协力击杀"))
        // 第 9 个业务计数器：击杀 +1（无 tag）
        assertEquals(1.0, meterRegistry.get(GuildService.METRIC_GUILD_BOSS_KILL_TOTAL).counter().count())
        // 击杀挂点：全服公告同步触发（文案含宗门名，详见下方第二十六轮专测）
        verify(webSocketService).broadcastAnnouncement(argThat { contains("唐门") })
        verify(userRepository).save(user)
    }

    // ==================== 第二十六轮 击杀全服公告（激活 WS 公告通道） ====================

    @Test
    fun `challengeBoss kill should broadcast a server-wide announcement containing the guild name`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        doReturn(bossPool(currentHp = 500L)).whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        guildService.challengeBoss(1L)

        // 击杀 → /topic/announcement 全服公告，文案必须含宗门名（「唐门」）供订阅者识别事件主体
        verify(webSocketService).broadcastAnnouncement(argThat { contains("唐门") })
    }

    @Test
    fun `challengeBoss non-kill challenge must not broadcast any announcement`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        // 满血池：本次伤害 6100+ << 24500，正常扣血不击杀（胜利分支也不广播）
        doReturn(bossPool(currentHp = GuildBossBalance.weeklyBossMaxHp(1)))
            .whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        assertNotNull(resp)
        assertFalse(resp!!.killed)
        // 普通挑战（无论胜负）不走公告通道：公告是「全服事件」，单次扣血不值得打扰全服
        verify(webSocketService, never()).broadcastAnnouncement(any())
    }

    @Test
    fun `challengeBoss mid pool hit should not grant kill rewards or count the kill`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        doReturn(bossPool(currentHp = GuildBossBalance.weeklyBossMaxHp(1)))
            .whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        // 非击杀：无击杀加成、无击杀计数（registry 里没有该 meter）
        assertEquals(resp!!.damage / GuildBossBalance.GOLD_PER_DAMAGE_DIVISOR, resp.goldGained)
        assertEquals(GuildBossBalance.WIN_BOSS_COIN_BASE + 1, resp.bossCoinGained)
        assertFalse(resp.message.contains("全员协力击杀"))
        assertEquals(0, meterRegistry.meters.count { it.id.name == GuildService.METRIC_GUILD_BOSS_KILL_TOTAL })
    }

    @Test
    fun `challengeBoss after kill should reject with business exception and grant nothing`() {
        val (_, profile) = userWith(gold = 5000, level = 50, guildId = 9L)
        profile.bossCoin = 9L
        leaderGuild()
        doReturn(bossPool(currentHp = 0L, killed = true)).whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()

        val ex = assertThrows(IllegalArgumentException::class.java) { guildService.challengeBoss(1L) }

        assertTrue(ex.message!!.contains("下周一"), "已击杀拒绝文案应指引下周再战：${ex.message}")
        // 业务拒绝零改动：不扣血不发奖不 roll 装备不累计贡献
        assertEquals(5000L, profile.gold)
        assertEquals(9L, profile.bossCoin)
        verify(guildMemberRepository, never()).save(any())
        verify(gameService, never()).rollBackpackDrop(any(), any())
        verify(guildBossRepository, never()).save(any())
        assertEquals(0, meterRegistry.meters.count { it.id.name == GuildService.METRIC_GUILD_BOSS_KILL_TOTAL })
    }

    @Test
    fun `challengeBoss should serialize double challenge into one kill and one rejection`() {
        val (_, profile) = userWith(gold = 0, level = 50, guildId = 9L)
        profile.bossCoin = 0
        leaderGuild()
        val pool = bossPool(currentHp = 500L)
        doReturn(pool).whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val attempts = AtomicInteger(0)
        val successes = AtomicInteger(0)
        val rejections = AtomicInteger(0)
        var killDamage = 0L
        // 行锁语义单测化（真实并发由 DB 对 guild 行 FOR UPDATE 串行化）：两挑战串行执行，
        // 第一击打空血池完成击杀，第二挑战者必须见到 killed=true 被拒——伤害/奖励只入账一次
        repeat(2) {
            attempts.incrementAndGet()
            try {
                val resp = guildService.challengeBoss(1L)!!
                successes.incrementAndGet()
                killDamage = resp.damage
            } catch (e: IllegalArgumentException) {
                rejections.incrementAndGet()
                assertTrue(e.message!!.contains("击杀"))
            }
        }

        assertEquals(2, attempts.get())
        assertEquals(1, successes.get())
        assertEquals(1, rejections.get())
        assertEquals(0L, pool.currentHp)
        assertTrue(pool.killed)
        // 击杀奖励恰好一次：gold = killDamage/12 + 3000，bossCoin = 7 + 30
        assertEquals(killDamage / GuildBossBalance.GOLD_PER_DAMAGE_DIVISOR + GuildBossBalance.GUILD_BOSS_KILL_GOLD, profile.gold)
        assertEquals(GuildBossBalance.WIN_BOSS_COIN_BASE + 1 + GuildBossBalance.GUILD_BOSS_KILL_BOSS_COIN, profile.bossCoin)
        assertEquals(1.0, meterRegistry.get(GuildService.METRIC_GUILD_BOSS_KILL_TOTAL).counter().count())
    }

    @Test
    fun `challengeBoss should lazily respawn a stale week pool with maxHp recalculated for the current guild level`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        val guild = Guild(id = 9L, name = "唐门", level = 3, exp = 0, maxMembers = 30, leaderId = 2L)
        doReturn(Optional.of(guild)).whenever(guildRepository).findById(9L)
        doReturn(guild).whenever(guildRepository).findByIdForUpdate(9L)
        val pool = bossPool(currentHp = 0L, maxHp = GuildBossBalance.weeklyBossMaxHp(1), killed = true,
            weekStart = GuildBossBalance.currentWeekMonday().minusWeeks(1)) // 上周已击杀的残行
        doReturn(pool).whenever(guildBossRepository).findByGuildId(9L)
        stubBossMember()
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        // 跨周惰性重生：killed 翻回、week_start 对齐本周一、maxHp 按当前宗门等级 3 重算
        val maxHp = GuildBossBalance.weeklyBossMaxHp(3) // (1800 + 3×650) × 10 = 37500
        assertFalse(pool.killed)
        assertEquals(GuildBossBalance.currentWeekMonday(), pool.weekStart)
        assertEquals(maxHp, pool.maxHp)
        assertEquals(maxHp - resp!!.damage, pool.currentHp)
        assertEquals(maxHp, resp.bossMaxHp)
        assertFalse(resp.killed)
    }

    @Test
    fun `challengeBoss should keep win rewards contribution and weekly damage accrual unchanged under the shared pool`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        val member = GuildMember(guildId = 9L, userId = 1L, role = "MEMBER")
        doReturn(member).whenever(guildMemberRepository).findByUserId(1L)
        doReturn(null).whenever(gameService).rollBackpackDrop(any(), any())

        val resp = guildService.challengeBoss(1L)

        // 既有口径零漂移（单次伤害/胜负/金币/Boss币/贡献/周伤）：
        // level=50 伤害下限 6100 ≥ 0.35×2450=857 → 恒胜
        assertTrue(resp!!.won)
        assertEquals(resp.damage / GuildBossBalance.GOLD_PER_DAMAGE_DIVISOR, resp.goldGained)
        assertEquals(GuildBossBalance.WIN_BOSS_COIN_BASE + 1, resp.bossCoinGained)
        assertEquals(resp.damage / GuildBossBalance.CONTRIBUTION_PER_DAMAGE, member.contribution)
        assertEquals(resp.damage, member.weeklyBossDamage)
    }

    @Test
    fun `getGuildBossStatus should lazily init the pool and return full state`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        doReturn(null).whenever(guildBossRepository).findByGuildId(9L)

        val status = guildService.getGuildBossStatus(1L)

        val maxHp = GuildBossBalance.weeklyBossMaxHp(1)
        assertEquals(maxHp, status.bossHp)
        assertEquals(maxHp, status.bossMaxHp)
        assertFalse(status.killed)
        verify(guildBossRepository).save(any())
    }

    @Test
    fun `getGuildBossStatus should report the killed pool without touching it`() {
        userWith(gold = 0, level = 50, guildId = 9L)
        leaderGuild()
        doReturn(bossPool(currentHp = 0L, killed = true)).whenever(guildBossRepository).findByGuildId(9L)

        val status = guildService.getGuildBossStatus(1L)

        assertEquals(0L, status.bossHp)
        assertEquals(GuildBossBalance.weeklyBossMaxHp(1), status.bossMaxHp)
        assertTrue(status.killed)
        verify(guildBossRepository, never()).save(any())
    }

    @Test
    fun `getGuildBossStatus should throw business exception for player without guild`() {
        userWith(gold = 100, guildId = null)

        val ex = assertThrows(IllegalArgumentException::class.java) { guildService.getGuildBossStatus(1L) }

        assertTrue(ex.message!!.contains("宗门"))
        verify(guildRepository, never()).findByIdForUpdate(any())
        verify(guildBossRepository, never()).findByGuildId(any())
    }

    @Test
    fun `donate success should record the guild_donate daily quest progress`() {
        userWith(gold = 5000, guildId = 9L)
        leaderGuild()
        doReturn(GuildMember(guildId = 9L, userId = 1L, role = "MEMBER"))
            .whenever(guildMemberRepository).findByUserId(1L)

        assertTrue(guildService.donate(1L, 600L))

        verify(dailyQuestService).recordGuildDonate(1L)
    }

    @Test
    fun `donate failure should not record any daily quest progress`() {
        // 金币不足（副路径只在成功出口计数）
        userWith(gold = 50, guildId = 9L)
        leaderGuild()

        assertFalse(guildService.donate(1L, 100L))

        verify(dailyQuestService, never()).recordGuildDonate(any())
    }
}
