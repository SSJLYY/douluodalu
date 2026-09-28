package com.douluodalu.game.repository

import com.douluodalu.game.entity.UserTitleEntity
import com.douluodalu.game.entity.UserTitleId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserTitleRepository : JpaRepository<UserTitleEntity, UserTitleId> {

    /** 该玩家全部已拥有称号（商店面板拥有态 / 属性加成求和一次取回） */
    fun findByUserId(userId: Long): List<UserTitleEntity>

    /** 单称号拥有判定（回购拒绝的预检与并发撞键后的重查确认共用） */
    fun existsByUserIdAndTitleId(userId: Long, titleId: String): Boolean
}
