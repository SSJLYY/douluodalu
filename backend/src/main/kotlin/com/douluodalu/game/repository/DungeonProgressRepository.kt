package com.douluodalu.game.repository

import com.douluodalu.game.entity.DungeonProgressEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 每日副本进度仓库（第二十九轮）：一人一行（user_id 主键），findById(userId) 即按玩家取行。
 * 无派生查询方法：读路径 findById、写路径 save 已覆盖（行不存在 = 从未参与副本，
 * 服务层读路径不建行、写路径惰性建行，与 V8 每日任务同口径）。
 */
@Repository
interface DungeonProgressRepository : JpaRepository<DungeonProgressEntity, Long>
