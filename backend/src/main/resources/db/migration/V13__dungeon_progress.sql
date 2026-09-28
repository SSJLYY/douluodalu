-- ============================================================
-- V13: 每日副本进度表（第二十九轮，设计文档 §8）
--
-- 设计要点：
-- 1) 一人一行（user_id 主键），四个业务列合起来表达「当天 + 历史」两维进度：
--    - tier_completed：当日最高已通关难度（-1=未通关，0~4=难度层级）。
--      每天一次副本机会（§8「重置时间：每日凌晨（每天一次机会）」），胜局写入该难度，
--      败局保持 -1；跨天靠 challenge_date 惰性重置（服务层读到旧日期即视为 -1，
--      当日首次写路径落库归位，与 V8 每日任务「日期列惰性重置」同模式，无需定时任务）。
--    - challenge_date：当日已用机会的日期（战斗胜/败、扫荡都写今天——「胜或败都算当日
--      已挑战，防刷」；扫荡与战斗共用当日唯一一次奖励名额，见 DungeonService 注释）。
--      可空：从未参与过副本的老玩家无当日概念。
--    - ever_cleared：历史已通关记录位掩码（bit t = 难度 t 曾通关，0=从未通关）。
--      TINYINT(1) 实际取值范围 0~255，5 个难度位（0~31）容量充足；扫荡前置
--      「历史已通关该难度」按位精确判定（不过关的难度不出扫荡按钮，§8 扫荡功能）。
--      注：列名沿用任务书的 boolean 惯例命名（DEFAULT 0 = 从未通关），语义为位掩码。
-- 2) 日期边界取 JVM 本地时区 LocalDate.now()，与 JDBC 连接串
--    serverTimezone=Asia/Shanghai 一致（同 V7/V8 口径），避免 UTC 漂移把
--    跨天时刻的挑战记到错误的日期上。
-- 3) 无行 = 该玩家从未参与每日副本（读路径不建行，与 V8 每日任务读路径不建行同口径）。
-- ============================================================

CREATE TABLE dungeon_progress (
    user_id BIGINT PRIMARY KEY COMMENT '用户ID（一人一行）',
    tier_completed TINYINT NOT NULL DEFAULT -1 COMMENT '当日最高已通关难度（-1未通关，0~4难度层级，跨天惰性重置）',
    challenge_date DATE DEFAULT NULL COMMENT '当日已用机会日期（战斗胜/败、扫荡都写今天；跨天惰性重置锚点）',
    ever_cleared TINYINT(1) NOT NULL DEFAULT 0 COMMENT '历史已通关位掩码（bit t = 难度t曾通关；0=从未通关）',
    CONSTRAINT fk_dungeon_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日副本进度表';
