-- ============================================================
-- V7: 每日签到记录表
--
-- 设计要点：
-- 1) 签到判定完全基于 check_date（DATE，自然日）。日期边界取 JVM 本地时区的
--    LocalDate.now()，与 JDBC 连接串 serverTimezone=Asia/Shanghai 一致，
--    避免 UTC 漂移把跨天时刻的签到记到错误的日期上。
-- 2) 连续/累计天数随行快照（streak_at_sign/total_days_at_sign）：
--    状态查询只需读最近一行，无需聚合历史记录。
-- 3) uk_user_date 唯一键是并发双击的 DB 层兜底：Service 层存在性预检 +
--    捕获 DataIntegrityViolationException 归一为「今日已签到」（400），
--    防止竞态下同一自然日双发奖励。
-- ============================================================

CREATE TABLE check_in_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    check_date DATE NOT NULL COMMENT '签到日期（本地时区自然日）',
    streak_at_sign BIGINT NOT NULL COMMENT '签到时的连续签到天数',
    total_days_at_sign BIGINT NOT NULL COMMENT '签到时的累计签到天数',
    cycle_day INT NOT NULL COMMENT '本次签到落在7日循环的第几天(1-7)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_date (user_id, check_date),
    CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日签到记录表';
