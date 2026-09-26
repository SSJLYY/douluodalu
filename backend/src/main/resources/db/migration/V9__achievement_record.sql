-- ============================================================
-- V9: 成就解锁记录表
--
-- 设计要点：
-- 1) 无 claimed 列：成就奖励是永久属性加成（hp/atk），属性口径按已解锁集合
--    求和，「解锁记录行本身就是事实源」——解锁即生效，不存在「领取」动作，
--    天然幂等无双花，故不需要领取状态列。
-- 2) uk_user_achievement 唯一键 = 一人一成就至多一行，同时是并发双解锁的
--    DB 层兜底：Service 层 save 撞唯一键时捕获 DataIntegrityViolationException
--    幂等吞掉（记录行已存在即视为解锁成功）。
-- 3) unlocked_at 默认 CURRENT_TIMESTAMP：解锁时间由 DB 落定，状态接口以
--    yyyy-MM-dd 透出（LocalDate.toString() 口径）。
-- ============================================================

CREATE TABLE achievement_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    achievement_id VARCHAR(40) NOT NULL COMMENT '成就定义ID（GameBalance AchievementDefs，契约固定）',
    unlocked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '解锁时间（解锁即生效，无领取动作）',
    UNIQUE KEY uk_user_achievement (user_id, achievement_id),
    CONSTRAINT fk_achievement_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成就解锁记录表';
