-- ============================================================
-- 装备系统 - 魂环/魂骨/魂核 装备表
-- 版本: v1.0.0
-- ============================================================

USE douluo_game;

-- ============================================================
-- 魂环装备表 (equipped_ring) - 每个槽位固定9个
-- ============================================================
DROP TABLE IF EXISTS equipped_ring;
CREATE TABLE equipped_ring (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '拥有者ID',
    slot_index INT NOT NULL COMMENT '槽位索引(0-8)',
    ring_id BIGINT NOT NULL COMMENT '背包物品ID',
    year_ordinal INT NOT NULL COMMENT '年份档次(0-4)',
    quality_ordinal INT NOT NULL COMMENT '品质档次(0-4)',
    percentage INT NOT NULL COMMENT '年分数(100-999)',
    equip_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '装备时间',
    UNIQUE KEY uk_user_slot (user_id, slot_index),
    CONSTRAINT fk_ring_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_ring_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='魂环装备表';

-- ============================================================
-- 魂骨装备表 (equipped_bone) - 每个槽位固定6个
-- ============================================================
DROP TABLE IF EXISTS equipped_bone;
CREATE TABLE equipped_bone (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '拥有者ID',
    slot_index INT NOT NULL COMMENT '槽位索引(0-5)',
    bone_id BIGINT NOT NULL COMMENT '背包物品ID',
    year_ordinal INT NOT NULL COMMENT '年份档次(0-4)',
    quality_ordinal INT NOT NULL COMMENT '品质档次(0-4)',
    bone_type_ordinal INT NOT NULL COMMENT '魂骨类型(0-5: 头骨/左臂/右臂/躯干/左腿/右腿)',
    enhance_level INT NOT NULL DEFAULT 0 COMMENT '强化等级',
    equip_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '装备时间',
    UNIQUE KEY uk_bone_user_slot (user_id, slot_index),
    CONSTRAINT fk_bone_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_bone_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='魂骨装备表';

-- ============================================================
-- 魂核装备表 (equipped_core) - 左右两个魂核槽
-- ============================================================
DROP TABLE IF EXISTS equipped_core;
CREATE TABLE equipped_core (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '拥有者ID',
    slot_type VARCHAR(16) NOT NULL COMMENT '槽位类型(LEFT/RIGHT)',
    core_id BIGINT NOT NULL COMMENT '背包物品ID',
    rarity_ordinal INT NOT NULL COMMENT '稀有度档次(0-6)',
    core_name VARCHAR(50) NOT NULL COMMENT '魂核名称',
    core_value INT NOT NULL COMMENT '魂核值',
    core_level INT NOT NULL DEFAULT 0 COMMENT '魂核等级',
    equip_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '装备时间',
    UNIQUE KEY uk_core_user_slot (user_id, slot_type),
    CONSTRAINT fk_core_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_core_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='魂核装备表';

-- ============================================================
-- 验证表结构
-- ============================================================
SHOW TABLES;
DESCRIBE equipped_ring;
DESCRIBE equipped_bone;
DESCRIBE equipped_core;
