-- ============================================================
-- V11: guild_boss 共享血量宗门 Boss 表（weekly raid 化）
--
-- 背景：宗门 Boss 此前血量按 (1800 + 宗门等级×650) 每次挑战即时计算、
-- 各成员独立结算，成员之间无协作感。改为「每周共享血池」：
--   max_hp = (1800 + 宗门等级×650) × GUILD_BOSS_WEEK_HP_MULT(10)
-- 全员伤害共同扣减同一血池，扣到 0 即全宗击杀（击杀者拿额外奖励，
-- 全员仍按周榜分周奖）。宗门端点经惰性初始化建行/跨周重生，
-- GuildWeeklyResetService 每周一 03:03 全表重生兜底。
--
-- 每周一行：week_start 记录本行所属周（周一日期），挑战时发现
-- week_start 非本周即按当前宗门等级重算 max_hp 并复活；
-- 每周一由周重置服务遍历重置（killed=0、current_hp=max_hp、week_start=本周一）。
-- guild_id 作主键（一宗一行），FK ON DELETE CASCADE：解散宗门时血池行随之删除。
-- ============================================================

CREATE TABLE guild_boss (
    guild_id BIGINT NOT NULL PRIMARY KEY COMMENT '宗门ID（一宗一行，每周随周重置重生）',
    current_hp BIGINT NOT NULL COMMENT '当前共享血量（全员挑战共同扣减，扣到 0 即击杀）',
    max_hp BIGINT NOT NULL COMMENT '本周血池上限（(1800+宗门等级×650)×10，随周按当前宗门等级重算）',
    killed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '本周是否已被击杀（1=已击杀，挑战拒绝至下周一重生）',
    week_start DATE NOT NULL COMMENT '本行所属周的周一日期（惰性重生判据：非本周即重生）',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_guild_boss_guild FOREIGN KEY (guild_id) REFERENCES guild(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宗门Boss每周共享血池表';
