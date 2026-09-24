-- ============================================================
-- V2: 装备系统表
-- ============================================================

-- 魂环装备表 (9个槽位)
CREATE TABLE equipped_ring (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    slot_index INT NOT NULL COMMENT '槽位 0-8',
    ring_id BIGINT NOT NULL COMMENT '原背包物品ID',
    year_ordinal INT NOT NULL COMMENT '年份档次',
    quality_ordinal INT NOT NULL COMMENT '品质档次',
    percentage INT NOT NULL COMMENT '年分数',
    equip_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '装备时间',
    UNIQUE KEY uk_user_slot (user_id, slot_index),
    INDEX idx_user (user_id),
    CONSTRAINT fk_equipped_ring_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='魂环装备表';

-- 魂骨装备表 (6个槽位)
CREATE TABLE equipped_bone (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    slot_index INT NOT NULL COMMENT '槽位 0-5',
    bone_id BIGINT NOT NULL COMMENT '原背包物品ID',
    year_ordinal INT NOT NULL COMMENT '年份档次',
    quality_ordinal INT NOT NULL COMMENT '品质档次',
    bone_type_ordinal INT NOT NULL COMMENT '魂骨类型(0-5)',
    enhance_level INT NOT NULL DEFAULT 0 COMMENT '强化等级',
    equip_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '装备时间',
    UNIQUE KEY uk_user_slot (user_id, slot_index),
    INDEX idx_user (user_id),
    CONSTRAINT fk_equipped_bone_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='魂骨装备表';

-- 魂核装备表 (左右2个槽位)
CREATE TABLE equipped_core (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    slot_type VARCHAR(8) NOT NULL COMMENT '槽位类型 LEFT/RIGHT',
    core_id BIGINT NOT NULL COMMENT '原背包物品ID',
    rarity_ordinal INT NOT NULL COMMENT '稀有度',
    core_name VARCHAR(50) NOT NULL COMMENT '魂核名称',
    core_value INT NOT NULL DEFAULT 0 COMMENT '魂核值',
    core_level INT NOT NULL DEFAULT 0 COMMENT '魂核等级',
    equip_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '装备时间',
    UNIQUE KEY uk_user_slot (user_id, slot_type),
    INDEX idx_user (user_id),
    CONSTRAINT fk_equipped_core_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='魂核装备表';
