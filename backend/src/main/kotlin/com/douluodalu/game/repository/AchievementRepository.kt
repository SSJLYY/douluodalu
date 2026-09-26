package com.douluodalu.game.repository

import com.douluodalu.game.entity.AchievementEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AchievementRepository : JpaRepository<AchievementEntity, Long> {

    /** 该玩家全部已解锁成就（解锁判定 / 属性加成求和 / 状态合成一次取回） */
    fun findByUserId(userId: Long): List<AchievementEntity>
}
