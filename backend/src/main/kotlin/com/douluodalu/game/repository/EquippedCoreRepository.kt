package com.douluodalu.game.repository

import com.douluodalu.game.entity.EquippedCore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EquippedCoreRepository : JpaRepository<EquippedCore, Long> {
    fun findByUserIdAndSlotType(userId: Long, slotType: String): EquippedCore?
    fun findByUserId(userId: Long): List<EquippedCore>
}
