-- ============================================================
-- V4: player_profile 增加乐观锁版本号列（防并发双花）
-- ============================================================

ALTER TABLE player_profile
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA乐观锁版本号';
