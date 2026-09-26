-- ============================================================
-- V10: guild_member 增加本周宗门 Boss 伤害字段（周榜排行与周一重置）
--
-- 背景：宗门 Boss 挑战此前只累计总贡献（contribution），没有「周」维度的
-- 伤害口径，无法支撑宗门 Boss 周榜玩法。新增 weekly_boss_damage 记录本周
-- 累计伤害，由 GuildWeeklyResetService 每周一 03:03 按降序给前 3 名发奖后
-- 全量清零（下一周从 0 重新累计）。
-- NOT NULL DEFAULT 0：历史成员行与新建行无需回填即参与周榜口径（0 不入榜）。
-- ============================================================

ALTER TABLE guild_member
    ADD COLUMN weekly_boss_damage BIGINT NOT NULL DEFAULT 0 COMMENT '本周宗门Boss伤害';
