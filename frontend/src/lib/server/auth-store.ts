import { pbkdf2Sync, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

export interface StoredUser {
    userId: number;
    username: string;
    normalizedUsername: string;
    nickname: string;
    passwordHash: string;
    passwordSalt: string;
}

interface ProfileRecord {
    level: number;
    gold: number;
    soulPower: number;
    bossCoin: number;
    martialSoulName: string | null;
    chosenSchool: string | null;
    currentMapId: number;
    currentStage: number;
    currentHp: number;
    battleSoulPower: number;
    totalBattleWins: number;
    totalBattleLosses: number;
    towerFloor: number;
    killingIntent: number;
    prestigeCount: number;
    talentPoints: number;
    codexKills: number;
    autoBattle: boolean;
    autoAdvanceMap: boolean;
    autoBreakthrough: boolean;
    tutorialStep: number;
}

interface GuildRecord {
    id: number;
    name: string;
    leaderId: number;
    level: number;
    exp: number;
    memberCount: number;
    maxMembers: number;
    notice: string | null;
    createdAt: string;
}

interface ShopItemRecord {
    id: number;
    name: string;
    description: string;
    price: number;
    currency: 'GOLD' | 'BOSS_COIN';
    itemType: string;
    itemData: Record<string, unknown>;
    refreshTime?: string;
}

type DbRow = Record<string, unknown>;

declare global {
    var __douluoDb: DatabaseSync | undefined;
}

const db = getDb();

export function normalizeUsername(username: string) {
    return username.trim().toLowerCase();
}

export function hashPassword(password: string, salt = randomBytes(16).toString('hex')) {
    const hash = pbkdf2Sync(password, salt, 100_000, 32, 'sha256').toString('hex');
    return { hash, salt };
}

export function verifyPassword(password: string, user: StoredUser) {
    const { hash } = hashPassword(password, user.passwordSalt);
    const expected = Buffer.from(user.passwordHash, 'hex');
    const actual = Buffer.from(hash, 'hex');
    return expected.length === actual.length && timingSafeEqual(expected, actual);
}

export function createUser(username: string, password: string, nickname: string) {
    const normalizedUsername = normalizeUsername(username);
    if (findUserByUsername(normalizedUsername)) return null;

    const { hash, salt } = hashPassword(password);
    const result = db.prepare(`
        INSERT INTO users (username, normalized_username, nickname, password_hash, password_salt)
        VALUES (?, ?, ?, ?, ?)
    `).run(username.trim(), normalizedUsername, nickname.trim(), hash, salt);
    const userId = Number(result.lastInsertRowid);
    createInitialGameState(userId);
    return getUserById(userId);
}

export function findUserByUsername(username: string) {
    return mapUser(db.prepare('SELECT * FROM users WHERE normalized_username = ?').get(normalizeUsername(username)) as DbRow | undefined);
}

export function createSession(userId: number) {
    const token = randomUUID();
    db.prepare('INSERT INTO sessions (token, user_id, created_at) VALUES (?, ?, ?)').run(token, userId, new Date().toISOString());
    return token;
}

export function getUserByToken(token: string | null) {
    if (!token) return null;
    const row = db.prepare(`
        SELECT u.* FROM users u
        INNER JOIN sessions s ON s.user_id = u.id
        WHERE s.token = ?
    `).get(token) as DbRow | undefined;
    return mapUser(row);
}

export function getBearerToken(request: Request) {
    const authHeader = request.headers.get('authorization');
    if (!authHeader?.startsWith('Bearer ')) return null;
    return authHeader.slice('Bearer '.length).trim();
}

export function toAuthResponse(user: StoredUser, token: string) {
    return {
        token,
        userId: user.userId,
        username: user.username,
        nickname: user.nickname,
    };
}

export function toUserInfo(user: StoredUser) {
    return {
        userId: user.userId,
        username: user.username,
        nickname: user.nickname,
        avatarUrl: null,
    };
}

export function getGameState(userId: number) {
    const profile = getProfile(userId) ?? createInitialGameState(userId);
    const backpackItems = selectJsonList('backpack_items', userId);
    const equippedRings = selectJsonList('equipped_rings', userId);
    const equippedBones = selectJsonList('equipped_bones', userId);
    const equippedCores = selectJsonList('equipped_cores', userId);
    if (backpackItems.length === 0 && equippedRings.length === 0 && equippedBones.length === 0 && equippedCores.length === 0) {
        backpackItems.push(addBackpackItem(userId, {
            itemType: 'RING',
            yearOrdinal: 0,
            qualityOrdinal: 1,
            affixesJson: JSON.stringify({ source: 'starter', attack: 8 }),
            locked: false,
            percentage: 120,
            skillName: '新手魂环',
            boneTypeOrdinal: null,
            enhanceLevel: 0,
            passiveSkillName: null,
            coreName: null,
            coreValue: null,
            coreLevel: 0,
        }));
    }
    return {
        profile,
        equippedRings,
        equippedBones,
        equippedCores,
        backpackItems,
        talents: getJsonObject<Record<string, number>>('talents', userId, {}),
        achievements: getJsonObject<string[]>('achievements', userId, []),
    };
}

export function cultivate(userId: number) {
    const state = getGameState(userId);
    const soulPowerGained = Math.max(10, Math.floor(state.profile.level * 12 + Math.random() * 18));
    const totalSoulPower = state.profile.soulPower + soulPowerGained;
    db.prepare('UPDATE profiles SET soul_power = ? WHERE user_id = ?').run(totalSoulPower, userId);
    return {
        soulPowerGained,
        totalSoulPower,
        level: state.profile.level,
    };
}

export function breakthrough(userId: number) {
    const state = getGameState(userId);
    const cost = Math.floor(120 * Math.pow(state.profile.level, 1.55));
    if (state.profile.soulPower < cost) {
        return { success: false, newLevel: state.profile.level, message: '魂力不足，无法突破' };
    }

    const newLevel = state.profile.level + 1;
    db.prepare(`
        UPDATE profiles
        SET soul_power = ?, level = ?, current_hp = ?, battle_soul_power = ?
        WHERE user_id = ?
    `).run(state.profile.soulPower - cost, newLevel, 50 * newLevel + 100, state.profile.battleSoulPower + 25, userId);
    return { success: true, newLevel, message: `突破成功，当前等级 ${newLevel}` };
}

export function towerBattle(userId: number) {
    const state = getGameState(userId);
    const p = state.profile;
    const towerLevel = p.towerFloor * 3;
    const won = Math.random() > 0.25 + p.towerFloor * 0.02;
    const monsterName = ['血色统领', '暗影猎手', '地狱魔蛛', '杀戮守卫'][Math.floor(Math.random() * 4)];
    const rounds = 4 + Math.floor(Math.random() * 6);
    const goldGained = won ? 30 + towerLevel * 12 : 0;
    const expGained = won ? 25 + towerLevel * 10 : 0;
    const bossCoinGained = won && Math.random() > 0.4 ? 1 : 0;
    const drops = won && Math.random() > 0.35 ? [addBackpackItem(userId, createRandomEquipment(towerLevel, 'tower'))] : [];
    const killingGained = won ? 1 + Math.floor(p.towerFloor / 10) : 0;

    let nextProfile = { ...p };
    if (won) {
        nextProfile = {
            ...nextProfile,
            gold: nextProfile.gold + goldGained,
            soulPower: nextProfile.soulPower + expGained,
            bossCoin: nextProfile.bossCoin + bossCoinGained,
            towerFloor: Math.min(100, nextProfile.towerFloor + 1),
            killingIntent: nextProfile.killingIntent + killingGained,
            totalBattleWins: nextProfile.totalBattleWins + 1,
            codexKills: nextProfile.codexKills + 1,
        };
    } else {
        nextProfile = {
            ...nextProfile,
            totalBattleLosses: nextProfile.totalBattleLosses + 1,
            currentHp: 50 * p.level + 100,
        };
    }
    updateProfile(userId, nextProfile);

    return {
        won,
        rounds,
        monsterName,
        expGained,
        goldGained,
        bossCoinGained,
        towerFloor: nextProfile.towerFloor,
        killingIntent: nextProfile.killingIntent,
        drops,
        playerLevel: nextProfile.level,
    };
}

export function upgradeTalent(userId: number, branch: string) {
    const state = getGameState(userId);
    if (state.profile.talentPoints <= 0) return { error: '天赋点数不足' };
    const talents = state.talents as Record<string, number>;
    const currentLevel = talents[branch] ?? 0;
    if (currentLevel >= 10) return { error: '该天赋已满级' };

    talents[branch] = currentLevel + 1;
    db.prepare('INSERT OR REPLACE INTO talents (user_id, data) VALUES (?, ?)').run(userId, JSON.stringify(talents));
    db.prepare('UPDATE profiles SET talent_points = talent_points - 1 WHERE user_id = ?').run(userId);
    return { message: `${branch} 天赋升级至 ${currentLevel + 1} 级` };
}

export function sellBackpackItem(userId: number, backpackItemId: number) {
    const backpackItem = getBackpackItemById(userId, backpackItemId);
    if (!backpackItem) return { error: '背包物品不存在' };
    const soldGold = 10 + (Number(backpackItem.qualityOrdinal) + 1) * 15 + (Number(backpackItem.yearOrdinal) + 1) * 8;
    db.prepare('DELETE FROM backpack_items WHERE id = ?').run(backpackItem.dbId);
    db.prepare('UPDATE profiles SET gold = gold + ? WHERE user_id = ?').run(soldGold, userId);
    return { message: `出售成功，获得 ${soldGold} 金币`, gold: soldGold };
}

export function claimOfflineReward(userId: number) {
    const state = getGameState(userId);
    const now = Date.now();
    const key = `offline_${userId}`;
    const row = db.prepare('SELECT value FROM kv_store WHERE key = ?').get(key) as { value: string } | undefined;
    const lastTime = row ? Number(row.value) : now - 60 * 1000;
    const offlineSeconds = Math.min(60 * 60 * 8, Math.floor((now - lastTime) / 1000));
    const battleWins = Math.floor(offlineSeconds / 15);
    const goldGained = Math.floor(battleWins * (10 + state.profile.level * 3));
    const expGained = Math.floor(battleWins * (15 + state.profile.level * 5));
    db.prepare('INSERT OR REPLACE INTO kv_store (key, value) VALUES (?, ?)').run(key, String(now));
    db.prepare('UPDATE profiles SET gold = gold + ?, soul_power = soul_power + ? WHERE user_id = ?').run(goldGained, expGained, userId);
    return { offlineSeconds, goldGained, expGained, battleWins };
}

export function getRankByLevel(limit = 50) {
    const rows = db.prepare('SELECT u.id as user_id, u.nickname, p.level, p.prestige_count FROM profiles p JOIN users u ON u.id = p.user_id ORDER BY p.level DESC, p.prestige_count DESC LIMIT ?').all(limit) as DbRow[];
    return rows.map((row, i) => ({
        rank: i + 1,
        userId: Number(row.user_id),
        nickname: String(row.nickname),
        score: Number(row.level),
        extraData: `转生${String(row.prestige_count)}`,
    }));
}

export function getRankByTower(limit = 50) {
    const rows = db.prepare('SELECT u.id as user_id, u.nickname, p.tower_floor, p.killing_intent FROM profiles p JOIN users u ON u.id = p.user_id ORDER BY p.tower_floor DESC, p.killing_intent DESC LIMIT ?').all(limit) as DbRow[];
    return rows.map((row, i) => ({
        rank: i + 1,
        userId: Number(row.user_id),
        nickname: String(row.nickname),
        score: Number(row.tower_floor),
        extraData: `杀戮${String(row.killing_intent)}`,
    }));
}

export function battle(userId: number) {
    const state = getGameState(userId);
    const won = Math.random() > 0.25;
    const monsterName = ['柔骨兔', '曼陀罗蛇', '鬼藤', '人面魔蛛'][Math.floor(Math.random() * 4)];
    const rounds = 3 + Math.floor(Math.random() * 5);
    const expGained = won ? 20 + state.profile.level * 8 : 0;
    const goldGained = won ? 15 + state.profile.level * 5 : 0;
    const bossCoinGained = won && Math.random() > 0.55 ? 1 : 0;
    const drops = won && Math.random() > 0.45 ? [addBackpackItem(userId, createRandomEquipment(state.profile.level, 'battle'))] : [];

    let nextProfile = { ...state.profile };
    if (won) {
        nextProfile = {
            ...nextProfile,
            gold: nextProfile.gold + goldGained,
            soulPower: nextProfile.soulPower + expGained,
            bossCoin: nextProfile.bossCoin + bossCoinGained,
            totalBattleWins: nextProfile.totalBattleWins + 1,
            codexKills: nextProfile.codexKills + 1,
            currentStage: Math.min(15, nextProfile.currentStage + 1),
        };
    } else {
        nextProfile = {
            ...nextProfile,
            totalBattleLosses: nextProfile.totalBattleLosses + 1,
            currentHp: 50 * nextProfile.level + 100,
        };
    }
    updateProfile(userId, nextProfile);

    return {
        won,
        rounds,
        monsterName,
        expGained,
        goldGained,
        drops,
        playerHp: nextProfile.currentHp,
        playerLevel: nextProfile.level,
        playerGold: nextProfile.gold,
        playerSoulPower: nextProfile.soulPower,
    };
}

export function getBossShopItems() {
    return BOSS_SHOP_ITEMS;
}

export function getLimitedShopItems() {
    const refreshTime = new Date(Date.now() + 1000 * 60 * 60 * 6).toISOString();
    return LIMITED_SHOP_ITEMS.map((item) => ({ ...item, refreshTime }));
}

export function getGuildShopItems() {
    return GUILD_SHOP_ITEMS;
}

export function buyShopItem(userId: number, itemId: number, shopType: 'boss' | 'limited' | 'guild') {
    if (shopType === 'guild' && !getUserGuild(userId)) return { error: '请先加入宗门' };
    const item = getShopCatalog(shopType).find((shopItem) => shopItem.id === itemId);
    if (!item) return { error: '商品不存在' };

    const profile = getProfile(userId) ?? createInitialGameState(userId);
    const balance = item.currency === 'GOLD' ? profile.gold : profile.bossCoin;
    if (balance < item.price) return { error: `${item.currency === 'GOLD' ? '金币' : 'Boss币'}不足` };

    const nextProfile = { ...profile };
    if (item.currency === 'GOLD') nextProfile.gold -= item.price;
    if (item.currency === 'BOSS_COIN') nextProfile.bossCoin -= item.price;
    updateProfile(userId, nextProfile);

    const backpackItem = addBackpackItem(userId, createEquipmentFromShopItem(item));
    return { message: '购买成功，物品已放入背包', item: backpackItem };
}

export function listGuilds() {
    return (db.prepare(`
        SELECT g.*, COUNT(gm.user_id) AS member_count
        FROM guilds g
        LEFT JOIN guild_members gm ON gm.guild_id = g.id
        GROUP BY g.id
        ORDER BY g.level DESC, g.id ASC
    `).all() as DbRow[]).map(mapGuild);
}

export function getUserGuild(userId: number) {
    const row = db.prepare(`
        SELECT g.*, COUNT(all_members.user_id) AS member_count
        FROM guild_members mine
        INNER JOIN guilds g ON g.id = mine.guild_id
        LEFT JOIN guild_members all_members ON all_members.guild_id = g.id
        WHERE mine.user_id = ?
        GROUP BY g.id
    `).get(userId) as DbRow | undefined;
    return row ? mapGuild(row) : null;
}

export function createGuild(userId: number, name: string) {
    const trimmedName = name.trim();
    if (trimmedName.length < 2) return { error: '宗门名称至少需要 2 个字符' };
    if (getUserGuild(userId)) return { error: '你已经加入宗门，退出当前宗门后才能创建或加入其他宗门' };
    if (db.prepare('SELECT id FROM guilds WHERE name = ?').get(trimmedName)) return { error: '宗门名称已存在' };

    const now = new Date().toISOString();
    const result = db.prepare(`
        INSERT INTO guilds (name, leader_id, level, exp, max_members, notice, created_at)
        VALUES (?, ?, 1, 0, 20, ?, ?)
    `).run(trimmedName, userId, '欢迎加入宗门。', now);
    const guildId = Number(result.lastInsertRowid);
    db.prepare('INSERT INTO guild_members (guild_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)').run(guildId, userId, 'LEADER', now);
    return { guild: getGuildById(guildId) };
}

export function joinGuild(userId: number, guildId: number) {
    if (!Number.isInteger(guildId) || guildId < 1) return { error: '宗门不存在' };
    if (getUserGuild(userId)) return { error: '你已经加入宗门，退出当前宗门后才能加入其他宗门' };

    const guild = getGuildById(guildId);
    if (!guild) return { error: '宗门不存在' };
    if (guild.memberCount >= guild.maxMembers) return { error: '宗门人数已满' };

    db.prepare('INSERT INTO guild_members (guild_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)')
        .run(guildId, userId, 'MEMBER', new Date().toISOString());
    return { guild: getGuildById(guildId) };
}

export function leaveGuild(userId: number) {
    const guild = getUserGuild(userId);
    if (!guild) return { error: '你还没有加入宗门' };
    if (guild.leaderId === userId) {
        db.prepare('DELETE FROM guild_members WHERE guild_id = ?').run(guild.id);
        db.prepare('DELETE FROM guilds WHERE id = ?').run(guild.id);
        return { message: '宗门已解散' };
    }

    db.prepare('DELETE FROM guild_members WHERE user_id = ?').run(userId);
    return { message: '已退出宗门' };
}

export function donateToGuild(userId: number, amount: number) {
    const guild = getUserGuild(userId);
    if (!guild) return { error: '请先加入宗门' };
    const donation = Math.max(100, Math.min(10000, Math.floor(amount || 0)));
    const profile = getProfile(userId) ?? createInitialGameState(userId);
    if (profile.gold < donation) return { error: '金币不足' };

    const nextProfile = { ...profile, gold: profile.gold - donation };
    updateProfile(userId, nextProfile);

    const expGain = Math.floor(donation / 10);
    const newExp = guild.exp + expGain;
    const newLevel = Math.max(guild.level, Math.floor(newExp / 500) + 1);
    const newMaxMembers = 20 + (newLevel - 1) * 5;
    db.prepare('UPDATE guilds SET exp = ?, level = ?, max_members = ? WHERE id = ?').run(newExp, newLevel, newMaxMembers, guild.id);

    return { message: `捐献成功，宗门经验 +${expGain}`, guild: getGuildById(guild.id), gold: nextProfile.gold };
}

export function challengeGuildBoss(userId: number) {
    const guild = getUserGuild(userId);
    if (!guild) return { error: '请先加入宗门' };
    const profile = getProfile(userId) ?? createInitialGameState(userId);
    const damage = profile.level * 120 + profile.battleSoulPower + Math.floor(Math.random() * 160);
    const bossHp = 1800 + guild.level * 650;
    const won = damage >= bossHp * 0.35;
    const goldGained = Math.floor(damage / 12);
    const bossCoinGained = won ? 6 + guild.level : 2;
    const item = addBackpackItem(userId, createRandomEquipment(profile.level + guild.level, 'guildBoss'));

    updateProfile(userId, {
        ...profile,
        gold: profile.gold + goldGained,
        bossCoin: profile.bossCoin + bossCoinGained,
    });
    db.prepare('UPDATE guilds SET exp = exp + ? WHERE id = ?').run(won ? 30 : 12, guild.id);

    return {
        won,
        damage,
        bossHp,
        goldGained,
        bossCoinGained,
        item,
        message: won ? '宗门 Boss 挑战成功' : '造成了有效伤害，获得参与奖励',
    };
}

function getDb() {
    if (globalThis.__douluoDb) return globalThis.__douluoDb;
    const dataDir = path.join(process.cwd(), 'data');
    mkdirSync(dataDir, { recursive: true });
    const database = new DatabaseSync(path.join(dataDir, 'app.db'));
    database.exec('PRAGMA foreign_keys = ON');
    migrate(database);
    globalThis.__douluoDb = database;
    return database;
}

function migrate(database: DatabaseSync) {
    database.exec(`
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            normalized_username TEXT NOT NULL UNIQUE,
            nickname TEXT NOT NULL,
            password_hash TEXT NOT NULL,
            password_salt TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS sessions (
            token TEXT PRIMARY KEY,
            user_id INTEGER NOT NULL,
            created_at TEXT NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS profiles (
            user_id INTEGER PRIMARY KEY,
            level INTEGER NOT NULL,
            gold INTEGER NOT NULL,
            soul_power INTEGER NOT NULL,
            boss_coin INTEGER NOT NULL,
            martial_soul_name TEXT,
            chosen_school TEXT,
            current_map_id INTEGER NOT NULL,
            current_stage INTEGER NOT NULL,
            current_hp INTEGER NOT NULL,
            battle_soul_power INTEGER NOT NULL,
            total_battle_wins INTEGER NOT NULL,
            total_battle_losses INTEGER NOT NULL,
            tower_floor INTEGER NOT NULL,
            killing_intent INTEGER NOT NULL,
            prestige_count INTEGER NOT NULL,
            talent_points INTEGER NOT NULL,
            codex_kills INTEGER NOT NULL,
            auto_battle INTEGER NOT NULL,
            auto_advance_map INTEGER NOT NULL,
            auto_breakthrough INTEGER NOT NULL,
            tutorial_step INTEGER NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS guilds (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            leader_id INTEGER NOT NULL,
            level INTEGER NOT NULL,
            exp INTEGER NOT NULL,
            max_members INTEGER NOT NULL,
            notice TEXT,
            created_at TEXT NOT NULL,
            FOREIGN KEY (leader_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS guild_members (
            guild_id INTEGER NOT NULL,
            user_id INTEGER NOT NULL UNIQUE,
            role TEXT NOT NULL,
            joined_at TEXT NOT NULL,
            PRIMARY KEY (guild_id, user_id),
            FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS equipped_rings (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, data TEXT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS equipped_bones (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, data TEXT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS equipped_cores (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, data TEXT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS backpack_items (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, data TEXT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS talents (user_id INTEGER PRIMARY KEY, data TEXT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS achievements (user_id INTEGER PRIMARY KEY, data TEXT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
        CREATE TABLE IF NOT EXISTS kv_store (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    `);
}

function createInitialGameState(userId: number) {
    const profile = createDefaultProfile();
    db.prepare(`
        INSERT OR IGNORE INTO profiles (
            user_id, level, gold, soul_power, boss_coin, martial_soul_name, chosen_school, current_map_id, current_stage,
            current_hp, battle_soul_power, total_battle_wins, total_battle_losses, tower_floor, killing_intent, prestige_count,
            talent_points, codex_kills, auto_battle, auto_advance_map, auto_breakthrough, tutorial_step
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
        userId,
        profile.level,
        profile.gold,
        profile.soulPower,
        profile.bossCoin,
        profile.martialSoulName,
        profile.chosenSchool,
        profile.currentMapId,
        profile.currentStage,
        profile.currentHp,
        profile.battleSoulPower,
        profile.totalBattleWins,
        profile.totalBattleLosses,
        profile.towerFloor,
        profile.killingIntent,
        profile.prestigeCount,
        profile.talentPoints,
        profile.codexKills,
        Number(profile.autoBattle),
        Number(profile.autoAdvanceMap),
        Number(profile.autoBreakthrough),
        profile.tutorialStep,
    );
    db.prepare('INSERT OR IGNORE INTO talents (user_id, data) VALUES (?, ?)').run(userId, '{}');
    db.prepare('INSERT OR IGNORE INTO achievements (user_id, data) VALUES (?, ?)').run(userId, '[]');
    return getProfile(userId) ?? profile;
}

function createDefaultProfile(): ProfileRecord {
    return {
        level: 1,
        gold: 500,
        soulPower: 0,
        bossCoin: 0,
        martialSoulName: null,
        chosenSchool: null,
        currentMapId: 0,
        currentStage: 1,
        currentHp: 150,
        battleSoulPower: 100,
        totalBattleWins: 0,
        totalBattleLosses: 0,
        towerFloor: 1,
        killingIntent: 0,
        prestigeCount: 0,
        talentPoints: 0,
        codexKills: 0,
        autoBattle: false,
        autoAdvanceMap: false,
        autoBreakthrough: false,
        tutorialStep: 0,
    };
}

function getUserById(userId: number) {
    return mapUser(db.prepare('SELECT * FROM users WHERE id = ?').get(userId) as DbRow | undefined);
}

function getProfile(userId: number) {
    return mapProfile(db.prepare('SELECT * FROM profiles WHERE user_id = ?').get(userId) as DbRow | undefined);
}

function updateProfile(userId: number, profile: ProfileRecord) {
    db.prepare(`
        UPDATE profiles SET
            level = ?, gold = ?, soul_power = ?, boss_coin = ?, martial_soul_name = ?, chosen_school = ?, current_map_id = ?,
            current_stage = ?, current_hp = ?, battle_soul_power = ?, total_battle_wins = ?, total_battle_losses = ?,
            tower_floor = ?, killing_intent = ?, prestige_count = ?, talent_points = ?, codex_kills = ?, auto_battle = ?,
            auto_advance_map = ?, auto_breakthrough = ?, tutorial_step = ?
        WHERE user_id = ?
    `).run(
        profile.level,
        profile.gold,
        profile.soulPower,
        profile.bossCoin,
        profile.martialSoulName,
        profile.chosenSchool,
        profile.currentMapId,
        profile.currentStage,
        profile.currentHp,
        profile.battleSoulPower,
        profile.totalBattleWins,
        profile.totalBattleLosses,
        profile.towerFloor,
        profile.killingIntent,
        profile.prestigeCount,
        profile.talentPoints,
        profile.codexKills,
        Number(profile.autoBattle),
        Number(profile.autoAdvanceMap),
        Number(profile.autoBreakthrough),
        profile.tutorialStep,
        userId,
    );
}

function addBackpackItem(userId: number, item: Omit<Record<string, unknown>, 'id'>) {
    const result = db.prepare('INSERT INTO backpack_items (user_id, data) VALUES (?, ?)').run(userId, '{}');
    const id = Number(result.lastInsertRowid);
    const data = { id, ...item };
    db.prepare('UPDATE backpack_items SET data = ? WHERE id = ?').run(JSON.stringify(data), id);
    return data;
}

function createRandomEquipment(level: number, source: string) {
    const itemType = Math.random() > 0.72 ? 'BONE' : Math.random() > 0.45 ? 'CORE' : 'RING';
    const qualityOrdinal = Math.min(4, Math.floor(level / 8) + Math.floor(Math.random() * 3));
    const yearOrdinal = Math.min(4, Math.floor(level / 12) + Math.floor(Math.random() * 2));
    const base = {
        itemType,
        yearOrdinal,
        qualityOrdinal,
        affixesJson: JSON.stringify({ source, attack: 5 + level * 2, hp: 20 + level * 5 }),
        locked: false,
        percentage: 100 + level * 12 + Math.floor(Math.random() * 80),
        skillName: itemType === 'RING' ? ['蓝银缠绕', '昊天重击', '疾风突刺'][Math.floor(Math.random() * 3)] : null,
        boneTypeOrdinal: itemType === 'BONE' ? Math.floor(Math.random() * 6) : null,
        enhanceLevel: itemType === 'BONE' ? Math.max(1, Math.floor(level / 10)) : 0,
        passiveSkillName: itemType !== 'RING' ? ['坚韧', '破甲', '凝神'][Math.floor(Math.random() * 3)] : null,
        coreName: itemType === 'CORE' ? ['攻击魂核', '防御魂核', '辅助魂核'][Math.floor(Math.random() * 3)] : null,
        coreValue: itemType === 'CORE' ? 10 + level * 3 : null,
        coreLevel: itemType === 'CORE' ? Math.max(1, Math.floor(level / 5)) : 0,
    };
    return base;
}

function createEquipmentFromShopItem(item: ShopItemRecord) {
    return {
        itemType: item.itemType,
        yearOrdinal: Number(item.itemData.yearOrdinal ?? 1),
        qualityOrdinal: Number(item.itemData.qualityOrdinal ?? 2),
        affixesJson: JSON.stringify(item.itemData.affixes ?? { source: 'shop' }),
        locked: false,
        percentage: Number(item.itemData.percentage ?? 300),
        skillName: item.itemType === 'RING' ? String(item.itemData.skillName ?? item.name) : null,
        boneTypeOrdinal: item.itemType === 'BONE' ? Number(item.itemData.boneTypeOrdinal ?? 0) : null,
        enhanceLevel: item.itemType === 'BONE' ? Number(item.itemData.enhanceLevel ?? 1) : 0,
        passiveSkillName: item.itemType !== 'RING' ? String(item.itemData.passiveSkillName ?? item.name) : null,
        coreName: item.itemType === 'CORE' ? String(item.itemData.coreName ?? item.name) : null,
        coreValue: item.itemType === 'CORE' ? Number(item.itemData.coreValue ?? 30) : null,
        coreLevel: item.itemType === 'CORE' ? Number(item.itemData.coreLevel ?? 1) : 0,
    };
}

function getShopCatalog(shopType: 'boss' | 'limited' | 'guild') {
    if (shopType === 'boss') return BOSS_SHOP_ITEMS;
    if (shopType === 'limited') return LIMITED_SHOP_ITEMS;
    return GUILD_SHOP_ITEMS;
}

const BOSS_SHOP_ITEMS: ShopItemRecord[] = [
    { id: 101, name: '千年强攻魂环', description: '稳定提升攻击能力的魂环。', price: 3, currency: 'BOSS_COIN', itemType: 'RING', itemData: { yearOrdinal: 1, qualityOrdinal: 2, percentage: 420, skillName: '强攻爆发' } },
    { id: 102, name: '万年疾影魂骨', description: '提供破甲被动的稀有魂骨。', price: 8, currency: 'BOSS_COIN', itemType: 'BONE', itemData: { yearOrdinal: 2, qualityOrdinal: 3, boneTypeOrdinal: 1, enhanceLevel: 2, passiveSkillName: '疾影破甲' } },
    { id: 103, name: '攻击魂核', description: '增加战斗魂力的魂核。', price: 6, currency: 'BOSS_COIN', itemType: 'CORE', itemData: { qualityOrdinal: 3, coreName: '攻击魂核', coreValue: 48, coreLevel: 2, passiveSkillName: '魂力增幅' } },
];

const LIMITED_SHOP_ITEMS: ShopItemRecord[] = [
    { id: 201, name: '完美十万年魂环', description: '限时出现的高品质魂环。', price: 18, currency: 'BOSS_COIN', itemType: 'RING', itemData: { yearOrdinal: 3, qualityOrdinal: 4, percentage: 1200, skillName: '十万年威压' } },
    { id: 202, name: '神赐魂核', description: '拥有高额属性的限时魂核。', price: 25, currency: 'BOSS_COIN', itemType: 'CORE', itemData: { qualityOrdinal: 4, coreName: '神赐魂核', coreValue: 120, coreLevel: 5, passiveSkillName: '神赐共鸣' } },
];

const GUILD_SHOP_ITEMS: ShopItemRecord[] = [
    { id: 301, name: '宗门护体魂骨', description: '宗门传承魂骨，适合前期过渡。', price: 200, currency: 'GOLD', itemType: 'BONE', itemData: { yearOrdinal: 1, qualityOrdinal: 2, boneTypeOrdinal: 2, enhanceLevel: 1, passiveSkillName: '宗门护体' } },
    { id: 302, name: '宗门凝魂核心', description: '宗门商店专属魂核。', price: 350, currency: 'GOLD', itemType: 'CORE', itemData: { qualityOrdinal: 2, coreName: '宗门凝魂核心', coreValue: 55, coreLevel: 2, passiveSkillName: '凝魂' } },
];

export function equipRing(userId: number, backpackItemId: number, slotIndex: number) {
    const backpackItem = getBackpackItemById(userId, backpackItemId);
    if (!backpackItem) return { error: '背包物品不存在' };
    if (backpackItem.itemType !== 'RING') return { error: '只能装备魂环到魂环槽位' };
    const existingRing = getEquippedBySlot('equipped_rings', userId, slotIndex);
    if (existingRing) {
        addBackpackItem(userId, existingRing);
        db.prepare('DELETE FROM equipped_rings WHERE id = ?').run(existingRing.dbId);
    }
    db.prepare('DELETE FROM backpack_items WHERE id = ?').run(backpackItemId);
    const data = { ...backpackItem, slotIndex };
    delete (data as Record<string, unknown>).dbId;
    db.prepare('INSERT INTO equipped_rings (user_id, data) VALUES (?, ?)').run(userId, JSON.stringify(data));
    return { message: `魂环已装备到槽位 ${slotIndex + 1}` };
}

export function equipBone(userId: number, backpackItemId: number, slotIndex: number) {
    const backpackItem = getBackpackItemById(userId, backpackItemId);
    if (!backpackItem) return { error: '背包物品不存在' };
    if (backpackItem.itemType !== 'BONE') return { error: '只能装备魂骨到魂骨槽位' };
    const existingBone = getEquippedBySlot('equipped_bones', userId, slotIndex);
    if (existingBone) {
        addBackpackItem(userId, existingBone);
        db.prepare('DELETE FROM equipped_bones WHERE id = ?').run(existingBone.dbId);
    }
    db.prepare('DELETE FROM backpack_items WHERE id = ?').run(backpackItemId);
    const data = { ...backpackItem, slotIndex };
    delete (data as Record<string, unknown>).dbId;
    db.prepare('INSERT INTO equipped_bones (user_id, data) VALUES (?, ?)').run(userId, JSON.stringify(data));
    return { message: `魂骨已装备到槽位 ${slotIndex + 1}` };
}

export function equipCore(userId: number, backpackItemId: number, slotType: string) {
    const backpackItem = getBackpackItemById(userId, backpackItemId);
    if (!backpackItem) return { error: '背包物品不存在' };
    if (backpackItem.itemType !== 'CORE') return { error: '只能装备魂核到魂核槽位' };
    const existingCore = getEquippedBySlotType('equipped_cores', userId, slotType);
    if (existingCore) {
        addBackpackItem(userId, existingCore);
        db.prepare('DELETE FROM equipped_cores WHERE id = ?').run(existingCore.dbId);
    }
    db.prepare('DELETE FROM backpack_items WHERE id = ?').run(backpackItemId);
    const data = { ...backpackItem, slotType };
    delete (data as Record<string, unknown>).dbId;
    db.prepare('INSERT INTO equipped_cores (user_id, data) VALUES (?, ?)').run(userId, JSON.stringify(data));
    return { message: `魂核已装备到${slotType}槽位` };
}

export function unequipRing(userId: number, slotIndex: number) {
    const existingRing = getEquippedBySlot('equipped_rings', userId, slotIndex);
    if (!existingRing) return { error: '该槽位没有装备魂环' };
    db.prepare('DELETE FROM equipped_rings WHERE id = ?').run(existingRing.dbId);
    addBackpackItem(userId, existingRing);
    return { message: '魂环已卸下，放回背包' };
}

export function unequipBone(userId: number, slotIndex: number) {
    const existingBone = getEquippedBySlot('equipped_bones', userId, slotIndex);
    if (!existingBone) return { error: '该槽位没有装备魂骨' };
    db.prepare('DELETE FROM equipped_bones WHERE id = ?').run(existingBone.dbId);
    addBackpackItem(userId, existingBone);
    return { message: '魂骨已卸下，放回背包' };
}

export function unequipCore(userId: number, slotType: string) {
    const existingCore = getEquippedBySlotType('equipped_cores', userId, slotType);
    if (!existingCore) return { error: '该槽位没有装备魂核' };
    db.prepare('DELETE FROM equipped_cores WHERE id = ?').run(existingCore.dbId);
    addBackpackItem(userId, existingCore);
    return { message: '魂核已卸下，放回背包' };
}

function getBackpackItemById(userId: number, backpackItemId: number) {
    const row = db.prepare('SELECT id, data FROM backpack_items WHERE user_id = ? AND id = ?').get(userId, backpackItemId) as { id: number; data: string } | undefined;
    if (!row) return null;
    return { ...JSON.parse(row.data), dbId: row.id };
}

function getEquippedBySlot(tableName: string, userId: number, slotIndex: number) {
    const rows = db.prepare(`SELECT id, data FROM ${tableName} WHERE user_id = ?`).all(userId) as { id: number; data: string }[];
    for (const row of rows) {
        const data = JSON.parse(row.data);
        if (data.slotIndex === slotIndex) return { ...data, dbId: row.id };
    }
    return null;
}

function getEquippedBySlotType(tableName: string, userId: number, slotType: string) {
    const rows = db.prepare(`SELECT id, data FROM ${tableName} WHERE user_id = ?`).all(userId) as { id: number; data: string }[];
    for (const row of rows) {
        const data = JSON.parse(row.data);
        if (data.slotType === slotType) return { ...data, dbId: row.id };
    }
    return null;
}

function getGuildById(guildId: number) {
    const row = db.prepare(`
        SELECT g.*, COUNT(gm.user_id) AS member_count
        FROM guilds g
        LEFT JOIN guild_members gm ON gm.guild_id = g.id
        WHERE g.id = ?
        GROUP BY g.id
    `).get(guildId) as DbRow | undefined;
    return row ? mapGuild(row) : null;
}

function selectJsonList(tableName: string, userId: number) {
    return (db.prepare(`SELECT data FROM ${tableName} WHERE user_id = ? ORDER BY id ASC`).all(userId) as DbRow[])
        .map((row) => JSON.parse(String(row.data)) as unknown);
}

function getJsonObject<T>(tableName: string, userId: number, fallback: T) {
    const row = db.prepare(`SELECT data FROM ${tableName} WHERE user_id = ?`).get(userId) as DbRow | undefined;
    return row ? JSON.parse(String(row.data)) as T : fallback;
}

function mapUser(row: DbRow | undefined): StoredUser | null {
    if (!row) return null;
    return {
        userId: Number(row.id),
        username: String(row.username),
        normalizedUsername: String(row.normalized_username),
        nickname: String(row.nickname),
        passwordHash: String(row.password_hash),
        passwordSalt: String(row.password_salt),
    };
}

function mapProfile(row: DbRow | undefined): ProfileRecord | null {
    if (!row) return null;
    return {
        level: Number(row.level),
        gold: Number(row.gold),
        soulPower: Number(row.soul_power),
        bossCoin: Number(row.boss_coin),
        martialSoulName: row.martial_soul_name === null ? null : String(row.martial_soul_name),
        chosenSchool: row.chosen_school === null ? null : String(row.chosen_school),
        currentMapId: Number(row.current_map_id),
        currentStage: Number(row.current_stage),
        currentHp: Number(row.current_hp),
        battleSoulPower: Number(row.battle_soul_power),
        totalBattleWins: Number(row.total_battle_wins),
        totalBattleLosses: Number(row.total_battle_losses),
        towerFloor: Number(row.tower_floor),
        killingIntent: Number(row.killing_intent),
        prestigeCount: Number(row.prestige_count),
        talentPoints: Number(row.talent_points),
        codexKills: Number(row.codex_kills),
        autoBattle: Boolean(row.auto_battle),
        autoAdvanceMap: Boolean(row.auto_advance_map),
        autoBreakthrough: Boolean(row.auto_breakthrough),
        tutorialStep: Number(row.tutorial_step),
    };
}

function mapGuild(row: DbRow): GuildRecord {
    return {
        id: Number(row.id),
        name: String(row.name),
        leaderId: Number(row.leader_id),
        level: Number(row.level),
        exp: Number(row.exp),
        memberCount: Number(row.member_count),
        maxMembers: Number(row.max_members),
        notice: row.notice === null ? null : String(row.notice),
        createdAt: String(row.created_at),
    };
}
