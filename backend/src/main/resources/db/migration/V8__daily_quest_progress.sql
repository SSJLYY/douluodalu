-- ============================================================
-- V8: 每日任务进度表
--
-- 设计要点：
-- 1) 跨天重置靠 quest_date 天然隔离：进度与领取状态都按 (user_id, quest_date)
--    维度记录，新的一天写新行（旧行留存为历史），无需任何定时清理任务。
--    行数上限 = 用户数 × 5 任务/天，增长可预期。
-- 2) uk_user_date_quest 唯一键是一人一天一任务至多一行，同时是并发首记竞态的
--    DB 层兜底：Service 层先做条件原子 UPDATE（progress+1），返回 0 行才 INSERT，
--    并发撞唯一键后捕获 DataIntegrityViolationException 重试一次原子 UPDATE。
-- 3) 计数与领取都用条件原子 UPDATE 在 DB 侧串行化：
--    - 计数：SET progress = progress + 1，避免「读-改-写」丢失更新，重复记录天然幂等；
--    - 领取：SET claimed = 1 WHERE claimed = 0 AND progress >= target，
--      并发双击领取只有一方命中，防重复领取/超额领取双花。
-- 4) 日期边界取 JVM 本地时区 LocalDate.now()，与 JDBC 连接串
--    serverTimezone=Asia/Shanghai 一致（同 V7 签到口径），避免 UTC 漂移
--    把跨天时刻的计数记到错误的日期上。
-- ============================================================

CREATE TABLE daily_quest_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    quest_date DATE NOT NULL COMMENT '任务日期（本地时区自然日，跨天重置靠它天然隔离）',
    quest_id VARCHAR(32) NOT NULL COMMENT '任务定义ID（battle_wins/cultivate/tower/checkin/shop_buy，契约固定）',
    progress INT NOT NULL DEFAULT 0 COMMENT '当前进度计数（条件原子UPDATE累加）',
    claimed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已领取奖励（0未领 1已领）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_date_quest (user_id, quest_date, quest_id),
    CONSTRAINT fk_daily_quest_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日任务进度表';
