package com.douluodalu.game.repository

import com.douluodalu.game.entity.EquippedBone
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EquippedBoneRepository : JpaRepository<EquippedBone, Long> {
    fun findByUserIdAndSlotIndex(userId: Long, slotIndex: Int): EquippedBone?
    fun findByUserId(userId: Long): List<EquippedBone>
}
