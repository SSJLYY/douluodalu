-- 斗罗大陆·放置传说 - 数据库初始化脚本
-- 使用方法: mysql -u root -p < init_database.sql

CREATE DATABASE IF NOT EXISTS douluo_game
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE douluo_game;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 游戏存档表
CREATE TABLE IF NOT EXISTS game_states (
    user_id INT NOT NULL UNIQUE,
    game_data LONGTEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
