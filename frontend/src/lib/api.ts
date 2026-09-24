// 后端地址：Spring Boot（端口 8080，无 context-path，Controller 自带 /api 前缀）。
// 通过 frontend/.env.local 的 NEXT_PUBLIC_API_URL 注入；兜底值与本地开发约定一致。
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/** token 失效时派发的全局事件，AuthContext 监听后做软跳转（避免 window.location 硬刷新） */
export const UNAUTHORIZED_EVENT = 'douluodalu:unauthorized';

/** 409 重试前派发的全局事件，useGameData 监听后立即重取 game/state */
export const STATE_REFRESH_EVENT = 'douluodalu:state-refresh';

class ApiClient {
    private token: string | null = null;

    setToken(token: string | null) {
        this.token = token;
        if (typeof window === 'undefined') return;
        if (token) {
            localStorage.setItem('token', token);
        } else {
            localStorage.removeItem('token');
        }
    }

    getToken(): string | null {
        if (!this.token && typeof window !== 'undefined') {
            this.token = localStorage.getItem('token');
        }
        return this.token;
    }

    /** 409 乐观锁冲突（player_profile @Version）自动重试：等待后端事务收尾后重放一次 */
    private static readonly RETRY_DELAY_MS = 400;

    private async request<T>(path: string, options: RequestInit = {}, attempt = 0): Promise<T> {
        const token = this.getToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json',
            ...(options.headers as Record<string, string>),
        };
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const res = await fetch(`${API_BASE}${path}`, {
            ...options,
            headers,
        });

        if (res.status === 401 || res.status === 403) {
            this.setToken(null);
            if (typeof window !== 'undefined') {
                window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
            }
            throw new Error('认证失败，请重新登录');
        }

        // 409 乐观锁冲突（player_profile @Version）：后端每次请求都会重新加载最新 version，
        // 短暂延迟后重放一次即可自愈；auth 路径的 409（用户名已存在）不重试。
        // 同时派发 STATE_REFRESH_EVENT 通知 useGameData 重取最新 state。
        if (res.status === 409 && attempt === 0 && !path.startsWith('/api/auth')) {
            await new Promise((r) => setTimeout(r, ApiClient.RETRY_DELAY_MS));
            if (typeof window !== 'undefined') {
                window.dispatchEvent(new Event(STATE_REFRESH_EVENT));
            }
            return this.request<T>(path, options, attempt + 1);
        }

        if (!res.ok) {
            // 后端错误体不完全统一：GlobalExceptionHandler 输出 {error,message}，
            // 部分 Controller 业务失败只输出 {error}，这里两者都兼容。
            const err = await res.json().catch(() => ({ message: '请求失败' })) as { message?: string; error?: string };
            throw new Error(err.message || err.error || '请求失败');
        }

        return res.json();
    }

    // Auth
    async register(username: string, password: string, nickname: string) {
        return this.request<AuthResponse>('/api/auth/register', {
            method: 'POST',
            body: JSON.stringify({ username, password, nickname }),
        });
    }

    async login(username: string, password: string) {
        return this.request<AuthResponse>('/api/auth/login', {
            method: 'POST',
            body: JSON.stringify({ username, password }),
        });
    }

    async getMe() {
        return this.request<UserInfo>('/api/auth/me');
    }

    async logout() {
        return this.request<SimpleResponse>('/api/auth/logout', { method: 'POST' });
    }

    // Game State
    async getGameState() {
        return this.request<GameState>('/api/game/state');
    }

    async claimOfflineReward() {
        return this.request<OfflineReward>('/api/game/offline-claim', { method: 'POST' });
    }

    // Actions
    async cultivate() {
        return this.request<CultivateResult>('/api/action/cultivate', { method: 'POST' });
    }

    async breakthrough() {
        return this.request<BreakthroughResult>('/api/action/breakthrough', { method: 'POST' });
    }

    async battle() {
        return this.request<BattleResult>('/api/action/battle', { method: 'POST' });
    }

    async towerBattle() {
        return this.request<TowerBattleResult>('/api/action/tower', { method: 'POST' });
    }

    // Rank
    async getLevelRank(limit = 50) {
        return this.request<RankEntry[]>(`/api/rank/level?limit=${limit}`);
    }

    async getTowerRank(limit = 50) {
        return this.request<RankEntry[]>(`/api/rank/tower?limit=${limit}`);
    }

    // Shop（后端 ShopItem：currencyType 为字符串，itemData 为编码字符串）
    async getNormalShopItems() {
        return this.request<ShopItem[]>('/api/shop/normal');
    }

    async buyNormalShopItem(itemId: number) {
        return this.request<{ message: string; item: unknown }>('/api/shop/normal/buy/' + itemId, {
            method: 'POST',
        });
    }

    async getBossShopItems() {
        return this.request<ShopItem[]>('/api/shop/boss');
    }

    async buyBossShopItem(itemId: number) {
        return this.request<{ message: string; item: unknown }>('/api/shop/boss/buy/' + itemId, {
            method: 'POST',
        });
    }

    async getLimitedShopItems() {
        return this.request<ShopItem[]>('/api/shop/limited');
    }

    async buyLimitedShopItem(itemId: number) {
        return this.request<{ message: string; item: unknown }>('/api/shop/limited/buy/' + itemId, {
            method: 'POST',
        });
    }

    // Guild
    async getGuildList() {
        return this.request<GuildSummary[]>('/api/guild/list');
    }

    async getMyGuild() {
        return this.request<GuildMyResponse>('/api/guild/my');
    }

    async createGuild(name: string) {
        return this.request<{ message: string; guild: GuildSummary }>('/api/guild/create', {
            method: 'POST',
            body: JSON.stringify({ name, description: '' }),
        });
    }

    async joinGuild(guildId: number) {
        return this.request<{ message: string }>('/api/guild/join/' + guildId, {
            method: 'POST',
        });
    }

    async leaveGuild() {
        return this.request<{ message: string }>('/api/guild/leave', {
            method: 'POST',
        });
    }

    async donateGuild(amount: number) {
        return this.request<{ message: string }>('/api/guild/donate', {
            method: 'POST',
            body: JSON.stringify({ amount }),
        });
    }

    async challengeGuildBoss() {
        return this.request<GuildBossResult>('/api/guild/boss/challenge', {
            method: 'POST',
        });
    }

    async getGuildShopItems() {
        return this.request<ShopItem[]>('/api/guild/shop');
    }

    async buyGuildShopItem(itemId: number) {
        return this.request<{ message: string; item: unknown }>('/api/guild/shop/buy/' + itemId, {
            method: 'POST',
        });
    }

    // Talent
    async upgradeTalent(branch: string) {
        return this.request<{ message: string }>('/api/talent/upgrade/' + branch, {
            method: 'POST',
        });
    }

    // Equipment
    async getEquipment() {
        return this.request<GameState>('/api/equipment');
    }

    /**
     * 后端索引语义（GameService）：
     * - itemIndex：背包全列表（按创建时间排序，与 /api/game/state 返回顺序一致）中的下标；
     * - ringIndex/boneIndex/coreIndex：背包中同类型物品（RING/BONE/CORE）子列表中的下标。
     * 页面里用 backpackItems 计算下标后调用本组接口。
     */
    async sellBackpackItem(itemIndex: number) {
        return this.request<{ message: string }>('/api/equipment/backpack/sell', {
            method: 'POST',
            body: JSON.stringify({ itemIndex }),
        });
    }

    async expandBackpack() {
        return this.request<{ message: string }>('/api/equipment/backpack/expand', {
            method: 'POST',
        });
    }

    async equipRing(ringIndex: number, slotIndex: number) {
        return this.request<{ message: string }>('/api/equipment/ring/equip', {
            method: 'POST',
            body: JSON.stringify({ slotIndex, ringIndex }),
        });
    }

    async equipBone(boneIndex: number, slotIndex: number) {
        return this.request<{ message: string }>('/api/equipment/bone/equip', {
            method: 'POST',
            body: JSON.stringify({ slotIndex, boneIndex }),
        });
    }

    /** 魂核只有两个槽位：slotIndex 0 = 左（LEFT），1 = 右（RIGHT） */
    async equipCore(coreIndex: number, slotIndex: number) {
        return this.request<{ message: string }>('/api/equipment/core/equip', {
            method: 'POST',
            body: JSON.stringify({ slotIndex, coreIndex }),
        });
    }

    async unequipRing(slotIndex: number) {
        return this.request<{ message: string }>('/api/equipment/ring/unequip', {
            method: 'POST',
            body: JSON.stringify({ slotIndex }),
        });
    }

    async unequipBone(slotIndex: number) {
        return this.request<{ message: string }>('/api/equipment/bone/unequip', {
            method: 'POST',
            body: JSON.stringify({ slotIndex }),
        });
    }

    async unequipCore(slotIndex: number) {
        return this.request<{ message: string }>('/api/equipment/core/unequip', {
            method: 'POST',
            body: JSON.stringify({ slotIndex }),
        });
    }
}

// Types
export interface AuthResponse {
    token: string;
    userId: number;
    username: string;
    nickname: string;
}

export interface UserInfo {
    userId: number;
    username: string;
    nickname: string;
    avatarUrl: string | null;
}

export interface SimpleResponse {
    success: boolean;
    message: string;
}

export interface GameState {
    profile: Profile;
    equippedRings: EquippedRing[];
    equippedBones: EquippedBone[];
    equippedCores: EquippedCore[];
    backpackItems: BackpackItem[];
    talents: Record<string, number>;
    achievements: string[];
    /** 任务#21：总战斗力（含装备加成，与战斗/爬塔响应同源） */
    power: number;
    /** 当前已装备魂环总负荷 */
    ringLoad: number;
    /** 魂环吸收容量（=根骨×6）；装环超容量后端返回 400 */
    capacity: number;
}

export interface Profile {
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

export interface EquippedRing {
    slotIndex: number;
    yearOrdinal: number;
    qualityOrdinal: number;
    percentage: number;
    affixesJson: string | null;
    skillName: string | null;
    /** 该魂环负荷（后端按 shared SoulRingSystem 计算，=等效年份） */
    load: number;
}

export interface EquippedBone {
    slotIndex: number;
    yearOrdinal: number;
    rarityOrdinal: number;
    enhanceLevel: number;
    affixesJson: string | null;
    passiveSkillName: string | null;
}

/** 对应后端 EquippedCoreDto：rarityOrdinal / value / level（无 qualityOrdinal/coreValue/coreLevel） */
export interface EquippedCore {
    slotType: string;
    coreName: string;
    rarityOrdinal: number;
    passiveSkillName: string | null;
    value: number;
    level: number;
}

export interface BackpackItem {
    id: number;
    itemType: string;
    yearOrdinal: number;
    qualityOrdinal: number;
    affixesJson: string | null;
    locked: boolean;
    percentage: number;
    skillName: string | null;
    boneTypeOrdinal: number | null;
    enhanceLevel: number;
    passiveSkillName: string | null;
    coreName: string | null;
    coreValue: number | null;
    coreLevel: number;
    /** 若为魂环：该环负荷（装得下/装不下预览用）；其他类型为 0 */
    load: number;
}

export interface BattleRound {
    round: number;
    playerHpBefore: number;
    monsterHpBefore: number;
    playerDamage: number;
    monsterDamage: number;
    playerHpAfter: number;
    monsterHpAfter: number;
}

export interface BattleResult {
    won: boolean;
    rounds: number;
    monsterName: string;
    expGained: number;
    goldGained: number;
    drops: BackpackItem[];
    playerHp: number;
    playerLevel: number;
    playerGold: number;
    playerSoulPower: number;
    battleLog?: BattleRound[];
}

export interface TowerBattleResult {
    won: boolean;
    rounds: number;
    monsterName: string;
    expGained: number;
    goldGained: number;
    bossCoinGained: number;
    towerFloor: number;
    killingIntent: number;
    drops: BackpackItem[];
    playerLevel: number;
}

export interface CultivateResult {
    soulPowerGained: number;
    totalSoulPower: number;
    level: number;
}

export interface BreakthroughResult {
    success: boolean;
    newLevel: number;
    message: string;
}

export interface OfflineReward {
    offlineSeconds: number;
    goldGained: number;
    expGained: number;
    battleWins: number;
}

export interface RankEntry {
    rank: number;
    userId: number;
    nickname: string;
    score: number;
    extraData: string | null;
}

/** 对应后端 model/ShopItem：currencyType 字符串、itemData 编码字符串、stock=-1 无限 */
export interface ShopItem {
    id: number;
    name: string;
    description: string;
    price: number;
    currencyType: 'GOLD' | 'BOSS_COIN' | string;
    itemType: string;
    itemData: string;
    stock: number;
    requiresLevel: number;
}

/** 后端裁剪 DTO：memberCount 为当前人数，notice 即宗门公告 */
export interface GuildSummary {
    id: number;
    name: string;
    level: number;
    memberCount: number;
    maxMembers: number;
    notice: string | null;
}

export interface GuildMyResponse {
    joined: boolean;
    guild: GuildSummary | null;
}

export interface GuildBossResult {
    won: boolean;
    damage: number;
    bossHp: number;
    goldGained: number;
    bossCoinGained: number;
    item: BackpackItem;
    message: string;
}

export const api = new ApiClient();
export default api;
