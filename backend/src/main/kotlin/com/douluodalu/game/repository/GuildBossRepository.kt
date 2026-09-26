package com.douluodalu.game.repository

import com.douluodalu.game.entity.GuildBossEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GuildBossRepository : JpaRepository<GuildBossEntity, Long> {
    /** 本宗当前周血池行（无行 = 本周尚无任何成员挑战过，由调用方惰性初始化） */
    fun findByGuildId(guildId: Long): GuildBossEntity?
}
