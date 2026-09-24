package com.douluodalu.game.service

import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.repository.PlayerProfileRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cache.CacheManager
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * 任务#29：排行榜 Caffeine 读缓存行为测试。
 *
 * 只测「命中路径 + 失效路径」，不真实等待 TTL（TTL 正确性由
 * Caffeine expireAfterWrite 语义保证，配置见 CacheConfig）。
 * Mock 掉 PlayerProfileRepository，两次调用间用 verify 次数判断是否穿透到数据层。
 * 同一 Spring 上下文跨用例复用，@BeforeEach 清空 "rank" 缓存保证穿透计数干净。
 */
@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = ["cache.rank.enabled=true"])
class RankCacheEnabledTest {

    @Autowired
    private lateinit var rankService: RankService

    @Autowired
    private lateinit var gameService: GameService

    @Autowired
    private lateinit var cacheManager: CacheManager

    @MockBean
    private lateinit var profileRepo: PlayerProfileRepository

    @BeforeEach
    fun clearRankCache() {
        cacheManager.getCache("rank")?.clear()
    }

    private fun profile(userId: Long, nickname: String, level: Int, towerFloor: Int) =
        PlayerProfileEntity(userId = userId, level = level, towerFloor = towerFloor)
            .apply { user = UserEntity(id = userId, username = "u$userId", passwordHash = "x", nickname = nickname) }

    @Test
    fun `第二次同参数调用命中缓存，不再穿透 repository`() {
        whenever(profileRepo.findLevelRankTopN(any(), any<Pageable>()))
            .thenReturn(listOf(profile(1, "卷王", 99, 0), profile(2, "咸鱼", 50, 0)))

        val first = rankService.getLevelRank(50)
        val second = rankService.getLevelRank(50)

        verify(profileRepo, times(1)).findLevelRankTopN(any(), any<Pageable>())
        assertEquals(first, second)
        assertEquals("卷王", first[0].nickname)
        assertEquals(1, first[0].rank)
    }

    @Test
    fun `缓存键含榜单类型与limit：不同维度各自回源`() {
        whenever(profileRepo.findLevelRankTopN(any(), any<Pageable>()))
            .thenReturn(listOf(profile(1, "a", 9, 0)))
        whenever(profileRepo.findTowerRankTopN(any(), any<Pageable>()))
            .thenReturn(listOf(profile(1, "a", 9, 7)))

        rankService.getLevelRank(50)
        rankService.getLevelRank(20)   // 不同 limit → 回源
        rankService.getTowerRank(50)   // 不同榜单 → 回源

        verify(profileRepo, times(2)).findLevelRankTopN(any(), any<Pageable>())
        verify(profileRepo, times(1)).findTowerRankTopN(any(), any<Pageable>())
    }

    @Test
    fun `突破等级后写路径失效，榜单重新回源`() {
        val p = profile(1, "a", 10, 0).apply { soulPower = 9_999_999L }
        whenever(profileRepo.findLevelRankTopN(any(), any<Pageable>())).thenReturn(listOf(p))
        whenever(profileRepo.findByUserId(1L)).thenReturn(p)
        doAnswer { it.arguments[0] }.whenever(profileRepo).save(any())

        rankService.getLevelRank(50)
        val bt = gameService.breakthrough(1L)
        assertTrue(bt.success)
        rankService.getLevelRank(50)

        verify(profileRepo, times(2)).findLevelRankTopN(any(), any<Pageable>())
    }
}

/**
 * 开关关闭（application-test.yml 默认 cache.rank.enabled=false）：
 * CacheConfig 不注册 → 无缓存代理 → 每次请求都直查 repository。
 */
@ActiveProfiles("test")
@SpringBootTest
class RankCacheDisabledTest {

    @Autowired
    private lateinit var rankService: RankService

    @MockBean
    private lateinit var profileRepo: PlayerProfileRepository

    @Test
    fun `cache_rank_disabled 时每次调用都穿透到 repository`() {
        whenever(profileRepo.findLevelRankTopN(any(), any<Pageable>()))
            .thenReturn(listOf(
                PlayerProfileEntity(userId = 1, level = 9).apply {
                    user = UserEntity(id = 1, username = "u", passwordHash = "x", nickname = "a")
                }
            ))

        rankService.getLevelRank(50)
        rankService.getLevelRank(50)

        verify(profileRepo, times(2)).findLevelRankTopN(any(), any<Pageable>())
    }
}
