-- ============================================================
-- V1: 基础表结构
-- ============================================================

-- 用户表
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    nickname VARCHAR(32) NOT NULL COMMENT '玩家昵称',
    avatar_url VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    last_login_at DATETIME DEFAULT NULL COMMENT '最后登录时间',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号表';

-- 玩家资料表
CREATE TABLE player_profile (
    user_id BIGINT NOT NULL PRIMARY KEY COMMENT '关联用户ID',
    level INT NOT NULL DEFAULT 1 COMMENT '境界等级',
    gold BIGINT NOT NULL DEFAULT 0 COMMENT '金币',
    soul_power BIGINT NOT NULL DEFAULT 0 COMMENT '魂力',
    boss_coin BIGINT NOT NULL DEFAULT 0 COMMENT 'Boss币',
    martial_soul_name VARCHAR(50) DEFAULT NULL COMMENT '武魂名称',
    chosen_school VARCHAR(50) DEFAULT NULL COMMENT '选择流派',
    current_map_id INT NOT NULL DEFAULT 0 COMMENT '当前地图ID',
    current_stage INT NOT NULL DEFAULT 1 COMMENT '当前关卡',
    current_hp BIGINT NOT NULL DEFAULT 100 COMMENT '当前血量',
    battle_soul_power INT NOT NULL DEFAULT 100 COMMENT '战斗魂力',
    total_battle_wins BIGINT NOT NULL DEFAULT 0 COMMENT '总战斗胜利数',
    total_battle_losses BIGINT NOT NULL DEFAULT 0 COMMENT '总战斗失败数',
    tower_floor INT NOT NULL DEFAULT 0 COMMENT '杀戮之都层数',
    tower_boss_kills INT NOT NULL DEFAULT 0 COMMENT '塔Boss击杀数',
    killing_intent INT NOT NULL DEFAULT 0 COMMENT '杀意值',
    prestige_count INT NOT NULL DEFAULT 0 COMMENT '转生次数',
    talent_points INT NOT NULL DEFAULT 0 COMMENT '天赋点数',
    codex_kills BIGINT NOT NULL DEFAULT 0 COMMENT '图鉴击杀数',
    last_logout_time DATETIME DEFAULT NULL COMMENT '最后登出时间',
    auto_battle BOOLEAN NOT NULL DEFAULT FALSE COMMENT '自动战斗',
    auto_advance_map BOOLEAN NOT NULL DEFAULT TRUE COMMENT '自动推进地图',
    auto_breakthrough BOOLEAN NOT NULL DEFAULT TRUE COMMENT '自动突破',
    tutorial_step INT NOT NULL DEFAULT 1 COMMENT '教程进度',
    guild_id BIGINT DEFAULT NULL COMMENT '所属宗门ID',
    backpack_capacity INT NOT NULL DEFAULT 20 COMMENT '背包容量',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_level (level),
    INDEX idx_tower_floor (tower_floor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家资料表';

-- 背包物品表
CREATE TABLE backpack_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '拥有者ID',
    item_type VARCHAR(8) NOT NULL COMMENT '物品类型: RING, BONE, CORE',
    year_ordinal INT DEFAULT 0 COMMENT '年份档次(0-4: 百年到百万年)',
    quality_ordinal INT DEFAULT 0 COMMENT '品质档次(0-4: 劣等到完美)',
    affixes_json JSON DEFAULT NULL COMMENT '词缀JSON',
    locked BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否锁定',
    percentage INT NOT NULL DEFAULT 0 COMMENT '年分数(100-999)',
    skill_name VARCHAR(50) DEFAULT NULL COMMENT '技能名',
    bone_type_ordinal INT DEFAULT NULL COMMENT '魂骨类型索引(0-5)',
    enhance_level INT NOT NULL DEFAULT 0 COMMENT '强化等级',
    passive_skill_name VARCHAR(50) DEFAULT NULL COMMENT '被动技能名',
    core_name VARCHAR(50) DEFAULT NULL COMMENT '魂核名称',
    core_value INT DEFAULT NULL COMMENT '魂核值',
    core_level INT NOT NULL DEFAULT 0 COMMENT '魂核等级',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
    INDEX idx_user_item (user_id, item_type),
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='背包物品表';

-- 宗门表
CREATE TABLE guild (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE COMMENT '宗门名称',
    notice VARCHAR(500) DEFAULT '' COMMENT '公告/描述',
    level INT NOT NULL DEFAULT 1 COMMENT '宗门等级',
    exp BIGINT NOT NULL DEFAULT 0 COMMENT '宗门经验',
    max_members INT NOT NULL DEFAULT 20 COMMENT '最大成员数',
    member_count INT NOT NULL DEFAULT 1 COMMENT '当前成员数',
    leader_id BIGINT NOT NULL COMMENT '宗主ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_leader (leader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宗门表';

-- 宗门成员表
CREATE TABLE guild_member (
    guild_id BIGINT NOT NULL COMMENT '宗门ID',
    user_id BIGINT NOT NULL COMMENT '成员ID',
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT '角色: LEADER, ELDER, MEMBER',
    contribution BIGINT NOT NULL DEFAULT 0 COMMENT '贡献值',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (guild_id, user_id),
    CONSTRAINT fk_member_guild FOREIGN KEY (guild_id) REFERENCES guild(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宗门成员表';

-- 天赋表
CREATE TABLE player_talent (
    user_id BIGINT NOT NULL COMMENT '玩家ID',
    branch VARCHAR(20) NOT NULL COMMENT '天赋分支: WAR_GOD, SOUL_MASTER, WEALTH, DIVINE',
    level INT NOT NULL DEFAULT 0 COMMENT '天赋等级(0-3)',
    PRIMARY KEY (user_id, branch),
    CONSTRAINT fk_talent_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='天赋表';

-- 商店购买记录表
CREATE TABLE shop_purchase_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '购买者ID',
    item_id BIGINT NOT NULL COMMENT '商品ID',
    purchase_count INT NOT NULL DEFAULT 0 COMMENT '累计购买次数',
    last_purchase_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后购买时间',
    UNIQUE KEY uk_user_item (user_id, item_id),
    INDEX idx_user_item (user_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商店购买记录表';
