package com.douluodalu.game.repository

import com.douluodalu.game.entity.PlayerProfileEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PlayerProfileRepository : JpaRepository<PlayerProfileEntity, Long> {
    fun findByUserId(userId: Long): PlayerProfileEntity?
    fun findByLevelGreaterThanEqualOrderByLevelDesc(level: Int): List<PlayerProfileEntity>
    fun findByTowerFloorGreaterThanEqualOrderByTowerFloorDesc(towerFloor: Int): List<PlayerProfileEntity>

    @Query("SELECT p FROM PlayerProfileEntity p JOIN FETCH p.user u WHERE p.level >= :level ORDER BY p.level DESC")
    fun findWithUserByLevelGreaterThanEqualOrderByLevelDesc(level: Int): List<PlayerProfileEntity>

    @Query("SELECT p FROM PlayerProfileEntity p JOIN FETCH p.user u WHERE p.towerFloor >= :floor ORDER BY p.towerFloor DESC")
    fun findWithUserByTowerFloorGreaterThanEqualOrderByTowerFloorDesc(floor: Int): List<PlayerProfileEntity>
}
