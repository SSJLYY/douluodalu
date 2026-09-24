package com.douluodalu.game.service

import com.douluodalu.game.config.CacheConfig
import com.douluodalu.game.dto.RankEntryResponse
import com.douluodalu.game.repository.PlayerProfileRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 排行榜读服务（任务#29：从 RankController 下沉，便于挂接 Spring Cache）。
 *
 * 缓存键 = 榜单类型 + 已归一化的 limit，两个维度即「榜单类型+limit」。
 * limit 由控制器先 coerce 到 1..1000，键空间有上界，配合 maximumSize 可控。
 * 缓存值为 DTO 列表（不可变数据快照），不存在懒加载实体逃逸出事务的问题。
 *
 * 失效策略：写路径 @CacheEvict（GameService.breakthrough / towerBattle）
 * + TTL 30s 兜底；cache.rank.enabled=false 时注解惰化、直查数据库。
 */
@Service
class RankService(
    private val playerProfileRepo: PlayerProfileRepository
) {
    // #p0 位置引用：本 pom 的 kotlin 插件 args 可能覆盖父 POM 的 -java.parameters，
    // 参数名不可用时位置引用最稳妥
    @Cacheable(cacheNames = [CacheConfig.CACHE_RANK], key = "'level:' + #p0")
    @Transactional(readOnly = true)
    fun getLevelRank(limit: Int): List<RankEntryResponse> {
        val profiles = playerProfileRepo.findLevelRankTopN(1, PageRequest.of(0, limit))
        return profiles.mapIndexed { idx, p ->
            RankEntryResponse(
                idx + 1,
                p.userId,
                p.user?.nickname ?: "",
                p.level.toLong(),
                "转生${p.prestigeCount}次"
            )
        }
    }

    @Cacheable(cacheNames = [CacheConfig.CACHE_RANK], key = "'tower:' + #p0")
    @Transactional(readOnly = true)
    fun getTowerRank(limit: Int): List<RankEntryResponse> {
        val profiles = playerProfileRepo.findTowerRankTopN(1, PageRequest.of(0, limit))
        return profiles.mapIndexed { idx, p ->
            RankEntryResponse(idx + 1, p.userId, p.user?.nickname ?: "", p.towerFloor.toLong(), null)
        }
    }
}
