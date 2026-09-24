package com.douluodalu.game.repository

import com.douluodalu.game.entity.EquippedRing
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.stereotype.Repository

@Repository
interface EquippedRingRepository : JpaRepository<EquippedRing, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    fun findByUserIdAndSlotIndex(userId: Long, slotIndex: Int): EquippedRing?

    fun findByUserId(userId: Long): List<EquippedRing>
}
