package com.douluodalu.game.repository

import com.douluodalu.game.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GuildRepository : JpaRepository<Guild, Long> {
    fun findByName(name: String): Guild?
    fun existsByName(name: String): Boolean

    /**
     * 原子占位：仅当未满员时 +1，返回受影响行数（0 = 满员/不存在）。
     * Guild 无 @Version 乐观锁列，加入宗门的"读-改-写"在并发下会突破人数上限，
     * 故名额占用必须走带条件的 UPDATE，而不是先 findById 再 save。
     */
    @Modifying
    @Query("UPDATE Guild g SET g.currentMembers = g.currentMembers + 1 WHERE g.id = :guildId AND g.currentMembers < g.maxMembers")
    fun tryJoinMemberCount(@Param("guildId") guildId: Long): Int

    /** 原子释放名额：仅当计数 > 0 时 -1，防止并发退出把人数减成负数 */
    @Modifying
    @Query("UPDATE Guild g SET g.currentMembers = g.currentMembers - 1 WHERE g.id = :guildId AND g.currentMembers > 0")
    fun tryLeaveMemberCount(@Param("guildId") guildId: Long): Int

    /**
     * 宗主转让走条件原子 UPDATE（WHERE 带上现任宗主做 compare-and-set）：
     * Guild 无 @Version 乐观锁列，整行 save 回写会把并发者刚改过的 member_count 覆盖回旧值；
     * 而"读 leaderId → save"在并发双转让下会两边都"成功"。条件 UPDATE 让后提交者影响 0 行、明确失败。
     */
    @Modifying
    @Query("UPDATE Guild g SET g.leaderId = :newLeaderId WHERE g.id = :guildId AND g.leaderId = :expectedLeaderId")
    fun transferLeaderId(
        @Param("guildId") guildId: Long,
        @Param("newLeaderId") newLeaderId: Long,
        @Param("expectedLeaderId") expectedLeaderId: Long
    ): Int
}

@Repository
interface GuildMemberRepository : JpaRepository<GuildMember, GuildMemberId> {
    fun findByUserId(userId: Long): GuildMember?
    fun findByGuildId(guildId: Long): List<GuildMember>
    fun countByGuildId(guildId: Long): Long
    fun deleteByUserId(userId: Long)
    fun deleteByGuildId(guildId: Long)
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