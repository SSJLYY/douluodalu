package com.douluodalu.game.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * 排行榜读缓存（任务#29）。
 *
 * 设计要点：
 * 1. @EnableCaching 随本配置类一起被 @ConditionalOnProperty 守护：
 *    cache.rank.enabled=false 时本类不注册 → 全应用没有缓存代理，
 *    @Cacheable/@CacheEvict 注解全部惰化（no-op），实现「一键关闭」，
 *    与 ratelimit.enabled 先例同款开关风格。
 * 2. 只声明命名缓存 "rank"（isDynamic=false），防止其他代码误挂无 TTL 缓存。
 * 3. TTL 到期自动驱逐，不做主动失效以外的兜底：放置游戏排行榜可接受
 *    ttl-seconds 内的短暂陈旧（默认 30s）。写路径（GameService.breakthrough /
 *    towerBattle）另有 @CacheEvict 提前失效，TTL 只是防漏网写路径的保险丝。
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = ["cache.rank.enabled"], havingValue = "true", matchIfMissing = true)
class CacheConfig(
    @Value("\${cache.rank.ttl-seconds:30}") private val ttlSeconds: Long
) {
    @Bean
    fun cacheManager(): CaffeineCacheManager {
        val manager = CaffeineCacheManager(CACHE_RANK)
        manager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(256) // key = 榜单类型(2) × limit(≤1000)，容量远大于工作集
        )
        return manager
    }

    companion object {
        const val CACHE_RANK = "rank"
    }
}
