package com.douluodalu.game.repository

import com.douluodalu.game.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GuildRepository : JpaRepository<Guild, Long> {
    fun findByName(name: String): Guild?
    fun existsByName(name: String): Boolean
}

@Repository
interface GuildMemberRepository : JpaRepository<GuildMember, GuildMemberId> {
    fun findByUserId(userId: Long): GuildMember?
    fun findByGuildId(guildId: Long): List<GuildMember>
    fun countByGuildId(guildId: Long): Long
    fun deleteByUserId(userId: Long)
}

@Repository
interface TalentRepository : JpaRepository<Talent, TalentId> {
    fun findByUserId(userId: Long): List<Talent>
    fun findByUserIdAndBranch(userId: Long, branch: String): Talent?
}

@Repository
interface ShopPurchaseRecordRepository : JpaRepository<ShopPurchaseRecord, Long> {
    fun findByUserIdAndItemId(userId: Long, itemId: Long): ShopPurchaseRecord?
}