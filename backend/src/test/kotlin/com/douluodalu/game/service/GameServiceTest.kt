package com.douluodalu.game.service

import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import com.douluodalu.game.repository.EquippedRingRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.TalentRepository
import com.douluodalu.game.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.math.abs

class GameServiceTest {
    @Mock
    private lateinit var profileRepo: PlayerProfileRepository

    @Mock
    private lateinit var backpackRepo: BackpackItemRepository

    @Mock
    private lateinit var talentRepo: TalentRepository

    @Mock
    private lateinit var equippedRingRepo: EquippedRingRepository

    @Mock
    private lateinit var equippedBoneRepo: EquippedBoneRepository

    @Mock
    private lateinit var equippedCoreRepo: EquippedCoreRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var webSocketService: WebSocketService

    @InjectMocks
    private lateinit var gameService: GameService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doAnswer { it.arguments[0] }.whenever(profileRepo).save(any())
    }

    private fun profile(userId: Long = 1L) = PlayerProfileEntity(userId = userId, level = 5)

    @Test
    fun `cultivate should gain soul power within expected range`() {
        val p = profile()
        p.soulPower = 100L
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val response = gameService.cultivate(1L)

        // level=5 → baseGain = 10 + 5*2 = 20，随机加成为 [0, baseGain/4) = [0, 5)
        val baseGain = 10L + 5L * 2L
        assertTrue(response.soulPowerGained in baseGain until (baseGain + baseGain / 4),
            "gain=${response.soulPowerGained} 应在 [$baseGain, ${baseGain + baseGain / 4}) 区间")
        assertEquals(100L + response.soulPowerGained, response.totalSoulPower)
        assertEquals(p.soulPower, response.totalSoulPower)
        assertEquals(5, response.level)
    }

    @Test
    fun `claimOfflineReward should cap offline time at 12 hours`() {
        val p = profile()
        p.lastLogoutTime = LocalDateTime.now().minusHours(20)
        p.gold = 0
        p.soulPower = 0
        p.totalBattleWins = 0
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val response = gameService.claimOfflineReward(1L)

        val maxSeconds = 12 * 3600L
        assertEquals(maxSeconds, response.offlineSeconds)

        // level=5：gold/s = (10 + 5*2) * 0.8 = 16.0，exp/s = (5 + 5) * 0.8 = 8.0
        assertEquals((16.0 * maxSeconds).toLong(), response.goldGained)
        assertEquals((8.0 * maxSeconds).toLong(), response.expGained)
        assertEquals(maxSeconds / 5, response.battleWins)

        assertEquals(response.goldGained, p.gold)
        assertEquals(response.expGained, p.soulPower)
        // 领取后登出时间基准被刷新，重复领取不应再次发奖
        assertNotNull(p.lastLogoutTime)
        assertTrue(java.time.Duration.between(p.lastLogoutTime, LocalDateTime.now()).seconds < 60)
    }

    @Test
    fun `claimOfflineReward should fall back to lastLoginAt when lastLogoutTime is null`() {
        val user = UserEntity(id = 1, username = "u", nickname = "n", passwordHash = "h")
        user.lastLoginAt = LocalDateTime.now().minusHours(2)
        val p = profile()
        p.user = user
        p.lastLogoutTime = null
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)

        val response = gameService.claimOfflineReward(1L)

        // 回退到最后登录时间（约 2 小时前），允许秒级误差
        assertTrue(abs(response.offlineSeconds - 2 * 3600L) < 5,
            "offlineSeconds=${response.offlineSeconds} 应约为 7200")
        assertTrue(response.goldGained > 0)
        assertTrue(response.expGained > 0)
    }
}
