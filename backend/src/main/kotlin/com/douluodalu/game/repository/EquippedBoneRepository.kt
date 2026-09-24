package com.douluodalu.game.repository

import com.douluodalu.game.entity.EquippedBone
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.stereotype.Repository

@Repository
interface EquippedBoneRepository : JpaRepository<EquippedBone, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    fun findByUserIdAndSlotIndex(userId: Long, slotIndex: Int): EquippedBone?

    fun findByUserId(userId: Long): List<EquippedBone>
}
