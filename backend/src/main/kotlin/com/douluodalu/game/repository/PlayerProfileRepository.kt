package com.douluodalu.game.repository

import com.douluodalu.game.entity.PlayerProfileEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PlayerProfileRepository : JpaRepository<PlayerProfileEntity, Long> {

    /**
     * 任务#29：@OneToOne @MapsId 的 user 关联在 JPA 语义下无法真正懒加载，
     * 每次加载 profile 都会补发一条 users SELECT（/api/game/state 实测多 1 条 SQL）。
     * 用 @Query JOIN FETCH 覆盖派生查询：调用方（GameService/AuthService/GuildService…）
     * 与测试桩完全不改，加载即带回 user，多条路径各省 1 条 SQL。
     */
    @Query("SELECT p FROM PlayerProfileEntity p JOIN FETCH p.user WHERE p.userId = :userId")
    fun findByUserId(userId: Long): PlayerProfileEntity?

    // ===== 排行榜（任务#29）=====
    // 旧实现无 LIMIT：全表取回 + 内存 take()。Pageable 下推为 SQL LIMIT 后，
    // EXPLAIN 实证走 idx_level / idx_tower_floor 反向扫描（Backward index scan，
    // 无 filesort），users 侧 eq_ref 主键命中。昵称经 JOIN FETCH 一并带回，无逐行 N+1。

    @Query("SELECT p FROM PlayerProfileEntity p JOIN FETCH p.user WHERE p.level >= :level ORDER BY p.level DESC")
    fun findLevelRankTopN(level: Int, pageable: Pageable): List<PlayerProfileEntity>

    @Query("SELECT p FROM PlayerProfileEntity p JOIN FETCH p.user WHERE p.towerFloor >= :floor ORDER BY p.towerFloor DESC")
    fun findTowerRankTopN(floor: Int, pageable: Pageable): List<PlayerProfileEntity>
}
