package com.douluodalu.game.repository

import com.douluodalu.game.entity.EquippedRing
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EquippedRingRepository : JpaRepository<EquippedRing, Long> {
    fun findByUserIdAndSlotIndex(userId: Long, slotIndex: Int): EquippedRing?
    fun findByUserId(userId: Long): List<EquippedRing>
}
