-- ============================================================
-- V6: guild_member 增加 user_id 唯一约束（防"一人同时挂多个宗门"）
--
-- 背景：guild_member 主键为复合 (guild_id, user_id)，同一用户并发 join
-- 两个不同宗门时两行主键互不冲突，会产生"幽灵成员"行并虚增 member_count。
-- Service 层已补 joinGuild 前置校验 + 条件原子占位（tryJoinMemberCount），
-- 此迁移在 DB 层兜底：一名玩家至多一行成员记录。
-- ============================================================

-- 1) 清理历史脏数据：成员行与 player_profile.guild_id 不再一致的孤儿/幽灵行
--    （正常流程 leave/join 均双边同步，只有竞态残留会被此句命中）
DELETE gm FROM guild_member gm
    LEFT JOIN player_profile p
        ON p.user_id = gm.user_id AND p.guild_id = gm.guild_id
    WHERE p.user_id IS NULL;

-- 2) 唯一约束（同时天然为按 user_id 查询提供索引）
ALTER TABLE guild_member
    ADD UNIQUE KEY uk_member_user (user_id);
