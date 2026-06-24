package com.douluodalu.game.repository

import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.entity.ShopPurchaseRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BackpackItemRepository : JpaRepository<BackpackItemEntity, Long> {
    fun findByUserId(userId: Long): List<BackpackItemEntity>
    fun findByUserIdAndItemType(userId: Long, itemType: String): List<BackpackItemEntity>
    fun countByUserId(userId: Long): Long

    @Query("SELECT r FROM ShopPurchaseRecord r WHERE r.userId = :userId AND r.itemId = :itemId")
    fun findShopPurchaseRecordByUserIdAndItemId(@Param("userId") userId: Long, @Param("itemId") itemId: Long): ShopPurchaseRecord?
}
