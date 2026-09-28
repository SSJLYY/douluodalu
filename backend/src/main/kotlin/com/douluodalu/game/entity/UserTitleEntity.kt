package com.douluodalu.game.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

/** user_title 联合主键（user_id + title_id），照 SocialEntities 的 IdClass 惯例 */
data class UserTitleId(
    var userId: Long = 0,
    var titleId: String = ""
) : Serializable

/**
 * 杀气商店称号拥有记录：一人一称号至多一行（V14 联合 PK 兜底，角色同 achievement_record
 * 的 uk_user_achievement——并发双买时后提交者触发约束冲突，ShopService.buyKillingTitle
 * 捕获 DataIntegrityViolationException 重查后按业务错误拒绝）。
 * 购买即永久拥有（无领取/佩戴状态列）：属性口径按已拥有集合求和（第二十九轮），
 * 与成就「解锁即生效、天然幂等」模型一致；转生不清本表（称号永久）。
 */
@Entity
@IdClass(UserTitleId::class)
@Table(name = "user_title")
class UserTitleEntity(
    @Id
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Id
    @Column(name = "title_id", nullable = false, length = 40)
    var titleId: String = "",

    @Column(name = "purchased_at")
    var purchasedAt: LocalDateTime = LocalDateTime.now()
) {
    override fun toString() = "UserTitleEntity[user=$userId,title=$titleId,purchasedAt=$purchasedAt]"
}
