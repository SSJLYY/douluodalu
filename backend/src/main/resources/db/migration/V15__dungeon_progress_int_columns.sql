-- ============================================================
-- V15: dungeon_progress 数值列对齐实体类型（第二十九轮集成期修复）
--
-- 背景：V13 的 tier_completed(TINYINT) / ever_cleared(TINYINT(1)) 在 MySQL 下
-- 分别映射 JDBC TINYINT / BIT，而 DungeonProgressEntity 对应字段是 Int（期望
-- INTEGER）——ddl-auto=validate 的真库启动直接拒绝（Schema-managementException）。
-- 单元/集成测试用的是 H2 + create-drop（不校验列型），故测试全绿仍漏过；本迁移
-- 由真库冒烟（mvn spring-boot:run 连本地 MySQL）抓出。
--
-- 修法：DROP + ADD 重建两列为 INT——该写法 MySQL 与 H2 方言通用（MODIFY 是
-- MySQL 专属、ALTER COLUMN 改型是 H2 专属，均无法跨库）。数据代价：ever_cleared
-- 位掩码与 tier_completed 当日进度归零——本功能发布窗口内无存量玩家（V13 刚落），
-- 归零等价于「重新通关一次」，可接受；KDoc 留档。
--
-- 教训留档：新表迁移的数值列一律 INT 起步（与实体 Int 对齐），TINYINT(1) 在
-- MySQL 下是 BIT 语义（boolean 惯例），不得用于非布尔列。
-- ============================================================

ALTER TABLE dungeon_progress DROP COLUMN tier_completed;
ALTER TABLE dungeon_progress ADD COLUMN tier_completed INT NOT NULL DEFAULT -1 COMMENT '当日最高已通关难度（-1未通关，0~4难度层级，跨天惰性重置）';
ALTER TABLE dungeon_progress DROP COLUMN ever_cleared;
ALTER TABLE dungeon_progress ADD COLUMN ever_cleared INT NOT NULL DEFAULT 0 COMMENT '历史已通关位掩码（bit t = 难度t曾通关；0=从未通关）';
