package com.douluodalu.game.repository

import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.entity.ShopPurchaseRecord
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BackpackItemRepository : JpaRepository<BackpackItemEntity, Long> {
    fun findByUserId(userId: Long): List<BackpackItemEntity>
    fun findByUserIdOrderByCreatedAtAsc(userId: Long): List<BackpackItemEntity>
    fun findByUserId(userId: Long, pageable: Pageable): Page<BackpackItemEntity>
    fun findByUserIdAndItemType(userId: Long, itemType: String): List<BackpackItemEntity>
    fun findByUserIdAndItemType(userId: Long, itemType: String, pageable: Pageable): Page<BackpackItemEntity>
    fun countByUserId(userId: Long): Long
    fun countByUserIdAndItemType(userId: Long, itemType: String): Long

    @Query("SELECT r FROM ShopPurchaseRecord r WHERE r.userId = :userId AND r.itemId = :itemId")
    fun findShopPurchaseRecordByUserIdAndItemId(@Param("userId") userId: Long, @Param("itemId") itemId: Long): ShopPurchaseRecord?
}
