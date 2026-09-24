-- ============================================================
-- V5: 补齐高频查询缺失索引（生产就绪加固）
--
-- 核查方式：对真库 douluo_game 逐表 SHOW INDEX，并对照各 Repository 的
-- @Query / 派生查询。结论：
--   * users.username            -- 已有唯一索引 username + idx_username，覆盖登录   OK
--   * player_profile.user_id    -- 即 PRIMARY KEY，findByUserId/JOIN 均走主键        OK
--   * backpack_item.user_id     -- idx_user_item / idx_user_created 最左前缀覆盖
--                                 findByUserId / countByUserId（EXPLAIN type=ref）    OK
--   * guild_member.user_id      -- 外键索引 fk_member_user 已建                       OK
--   * shop_purchase_record      -- 唯一键 uk_user_item(user_id,item_id) 覆盖复合查询  OK
--   * player_talent             -- PRIMARY KEY (user_id, branch)                      OK
-- 仅有 audit_log 两处缺口，本迁移补齐（普通非唯一索引，直接创建，无重复数据风险）：
-- ============================================================

-- 1) AuditLogRepository.findRecentLogs:
--      WHERE created_at >= :startTime ORDER BY created_at DESC
--    建前 EXPLAIN: type=ALL, Using where; Using filesort（全表扫+文件排序）
CREATE INDEX idx_audit_created ON audit_log (created_at);

-- 2) AuditLogRepository.findByUserIdAndAction:
--      WHERE user_id = :userId AND action = :action ORDER BY created_at DESC
--    原先仅能用 idx_user_time(user_id, created_at) 过滤 user_id 后再逐行筛 action
--    （EXPLAIN filtered=5%），补复合索引后三列一次命中且免排序
CREATE INDEX idx_audit_user_action ON audit_log (user_id, action, created_at);
