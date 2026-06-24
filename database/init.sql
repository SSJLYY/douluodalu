-- =====================================================
-- 斗罗大陆·放置传说 Web版 数据库初始化脚本
-- MySQL 8.0
-- =====================================================

CREATE DATABASE IF NOT EXISTS douluodalu
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE douluodalu;

-- 1. 账号系统
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(32) UNIQUE NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(255) NOT NULL,
    `nickname` VARCHAR(32) NOT NULL COMMENT '游戏昵称',
    `avatar_url` VARCHAR(255),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `last_login_at` DATETIME,
    INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 玩家存档
CREATE TABLE IF NOT EXISTS `player_profile` (
    `user_id` BIGINT PRIMARY KEY,
    `level` INT DEFAULT 1,
    `gold` BIGINT DEFAULT 0,
    `soul_power` BIGINT DEFAULT 0,
    `boss_coin` BIGINT DEFAULT 0,
    `martial_soul_name` VARCHAR(32),
    `chosen_school` VARCHAR(32),
    `current_map_id` INT DEFAULT 0,
    `current_stage` INT DEFAULT 1,
    `total_battle_wins` BIGINT DEFAULT 0,
    `total_battle_losses` BIGINT DEFAULT 0,
    `tower_floor` INT DEFAULT 0,
    `tower_boss_kills` INT DEFAULT 0,
    `killing_intent` INT DEFAULT 0,
    `prestige_count` INT DEFAULT 0,
    `talent_points` INT DEFAULT 0,
    `codex_kills` BIGINT DEFAULT 0,
    `last_logout_time` DATETIME,
    `auto_battle` BOOLEAN DEFAULT FALSE,
    `auto_advance_map` BOOLEAN DEFAULT TRUE,
    `auto_breakthrough` BOOLEAN DEFAULT TRUE,
    `tutorial_step` INT DEFAULT 1,
    `current_hp` BIGINT DEFAULT 100,
    `battle_soul_power` INT DEFAULT 100,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 魂环装备
CREATE TABLE IF NOT EXISTS `equipped_ring` (
    `user_id` BIGINT NOT NULL,
    `slot_index` TINYINT NOT NULL COMMENT '0-8',
    `year_ordinal` TINYINT NOT NULL,
    `quality_ordinal` TINYINT NOT NULL,
    `percentage` INT DEFAULT 0,
    `affixes_json` JSON,
    `skill_name` VARCHAR(32),
    PRIMARY KEY (`user_id`, `slot_index`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 魂骨装备
CREATE TABLE IF NOT EXISTS `equipped_bone` (
    `user_id` BIGINT NOT NULL,
    `slot_index` TINYINT NOT NULL COMMENT '0-5',
    `year_ordinal` TINYINT NOT NULL,
    `rarity_ordinal` TINYINT NOT NULL,
    `enhance_level` INT DEFAULT 0,
    `affixes_json` JSON,
    `passive_skill_name` VARCHAR(32),
    PRIMARY KEY (`user_id`, `slot_index`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 魂核装备
CREATE TABLE IF NOT EXISTS `equipped_core` (
    `user_id` BIGINT NOT NULL,
    `slot_type` VARCHAR(16) NOT NULL COMMENT 'ATTACK/DEFENSE/UTILITY',
    `core_name` VARCHAR(32) NOT NULL,
    `rarity_ordinal` TINYINT NOT NULL,
    `passive_skill_name` VARCHAR(32),
    `value` INT NOT NULL,
    `level` INT DEFAULT 0,
    PRIMARY KEY (`user_id`, `slot_type`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 背包物品
CREATE TABLE IF NOT EXISTS `backpack_item` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `item_type` VARCHAR(8) NOT NULL COMMENT 'RING/BONE/CORE',
    `year_ordinal` TINYINT NOT NULL,
    `quality_ordinal` TINYINT NOT NULL,
    `affixes_json` JSON,
    `locked` BOOLEAN DEFAULT FALSE,
    `percentage` INT DEFAULT 0,
    `skill_name` VARCHAR(32),
    `bone_type_ordinal` TINYINT,
    `enhance_level` INT DEFAULT 0,
    `passive_skill_name` VARCHAR(32),
    `core_name` VARCHAR(32),
    `core_value` INT,
    `core_level` INT DEFAULT 0,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_type` (`user_id`, `item_type`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 天赋
CREATE TABLE IF NOT EXISTS `player_talent` (
    `user_id` BIGINT NOT NULL,
    `branch` VARCHAR(20) NOT NULL,
    `level` INT DEFAULT 0,
    PRIMARY KEY (`user_id`, `branch`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. 成就
CREATE TABLE IF NOT EXISTS `player_achievement` (
    `user_id` BIGINT NOT NULL,
    `achievement_id` VARCHAR(50) NOT NULL,
    `unlocked_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `achievement_id`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. 宗门
CREATE TABLE IF NOT EXISTS `guild` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(64) UNIQUE NOT NULL,
    `leader_id` BIGINT NOT NULL,
    `level` INT DEFAULT 1,
    `exp` BIGINT DEFAULT 0,
    `member_count` INT DEFAULT 1,
    `max_members` INT DEFAULT 20,
    `notice` VARCHAR(500),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`leader_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `guild_member` (
    `guild_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `role` VARCHAR(16) DEFAULT 'MEMBER' COMMENT 'LEADER/ELDER/MEMBER',
    `contribution` BIGINT DEFAULT 0,
    `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`guild_id`, `user_id`),
    FOREIGN KEY (`guild_id`) REFERENCES `guild`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. 排行榜
CREATE TABLE IF NOT EXISTS `ranking_entry` (
    `rank_type` VARCHAR(16) NOT NULL COMMENT 'LEVEL/TOWER/POWER/GUILD',
    `user_id` BIGINT NOT NULL,
    `score` BIGINT NOT NULL,
    `extra_data` JSON,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`rank_type`, `user_id`),
    INDEX `idx_rank_score` (`rank_type`, `score` DESC),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. 商店状态
CREATE TABLE IF NOT EXISTS `player_shop_state` (
    `user_id` BIGINT PRIMARY KEY,
    `limited_shop_refresh_time` DATETIME,
    `limited_shop_rings_json` JSON,
    `limited_shop_bones_json` JSON,
    `dungeon_tier_completed` INT DEFAULT -1,
    `last_dungeon_time` DATETIME,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
