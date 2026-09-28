-- ============================================================
-- V14: 杀气商店（第二十九轮）
--
-- 背景：塔（杀戮之都）战斗胜利产出杀气 killing_intent
-- （GameService.towerBattle：1 + towerFloor/10），此前无任何消耗出口。
-- 设计文档 §7.4 定义杀气商店：8 个永久称号兑换 + HP/ATK 属性购买（无限次，
-- 价格 = 100 × 2^已购次数）。
--
-- 1) user_title：称号拥有记录表。
--    - 无代理 id 列：联合 PK (user_id, title_id) 一人一称号至多一行——该主键
--      同时充当 achievement_record uk_user_achievement 的并发兜底角色：
--      并发双买时后提交者触发约束冲突整单回滚（ShopService.buyKillingTitle
--      捕获 DataIntegrityViolationException 重查后按业务错误拒绝）。
--    - 无 claimed/佩戴列：称号购买即永久拥有，全部已拥有称号属性叠加生效
--      （不做「佩戴唯一」，与成就「解锁即生效、按集合求和」模型一致）。
--    - purchased_at 默认 CURRENT_TIMESTAMP：兑换时间由 DB 落定。
--    - 转生（GameService.prestige）不清本表：称号永久，区别于 level/gold/soulPower
--      的清零范围（文档 §15.2 重置项不含称号）。
--
-- 2) player_profile 加杀气属性购买计数两列：HP/ATK 两商品各自独立计数，
--    价格 = KILLING_ATTR_BASE_COST(100) × 2^购买次数（GameBalance.killingAttrCost
--    唯一写点，前后端镜像同源）。效果 +100 HP / +10 ATK 每次，基值固定不随
--    等级缩放（照设计文档 §7.4）。DEFAULT 0：历史玩家零漂移。
-- ============================================================

CREATE TABLE user_title (
    user_id BIGINT NOT NULL COMMENT '用户ID（联合PK成员）',
    title_id VARCHAR(40) NOT NULL COMMENT '称号定义ID（GameBalance KILLING_TITLES，契约固定）',
    purchased_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '兑换时间（购买即永久拥有，无领取/佩戴状态）',
    PRIMARY KEY (user_id, title_id),
    CONSTRAINT fk_user_title_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='杀气商店称号拥有记录表';

ALTER TABLE player_profile
    ADD COLUMN killing_hp_buys INT NOT NULL DEFAULT 0 COMMENT '杀气商店：HP提升已购次数（价格 100×2^n）',
    ADD COLUMN killing_atk_buys INT NOT NULL DEFAULT 0 COMMENT '杀气商店：攻击提升已购次数（价格 100×2^n）';
