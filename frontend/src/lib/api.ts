const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

class ApiClient {
    private token: string | null = null;

    setToken(token: string | null) {
        this.token = token;
        if (token) {
            localStorage.setItem('token', token);
        } else {
            localStorage.removeItem('token');
        }
    }

    getToken(): string | null {
        if (!this.token) {
            this.token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
        }
        return this.token;
    }

    private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
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
            window.location.href = '/';
            throw new Error('认证失败，请重新登录');
        }

        if (!res.ok) {
            const err = await res.json().catch(() => ({ message: '请求失败' }));
            throw new Error(err.message || '请求失败');
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

    // Shop
    async getBossShopItems() {
        return this.request<ShopItem[]>('/api/shop/boss');
    }

    async buyBossShopItem(itemId: number) {
        return this.request<{ message: string; item: unknown }>('/api/shop/boss/buy/' + itemId, {
            method: 'POST',
        });
    }

    async getLimitedShopItems() {
        return this.request<LimitedShopItem[]>('/api/shop/limited');
    }

    async buyLimitedShopItem(itemId: number) {
        return this.request<{ message: string; item: unknown }>('/api/shop/limited/buy/' + itemId, {
            method: 'POST',
        });
    }

    // Guild
    async getGuildList() {
        return this.request<Guild[]>('/api/guild/list');
    }

    async getMyGuild() {
        return this.request<Guild | null>('/api/guild/my');
    }

    async createGuild(name: string) {
        return this.request<{ message: string; guild: Guild }>('/api/guild/create', {
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
        return this.request<{ message: string; guild: Guild; gold: number }>('/api/guild/donate', {
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

    async sellBackpackItem(backpackItemId: number) {
        return this.request<{ message: string }>('/api/equipment/backpack/sell', {
            method: 'POST',
            body: JSON.stringify({ backpackItemId }),
        });
    }

    async expandBackpack() {
        return this.request<{ message: string }>('/api/equipment/backpack/expand', {
            method: 'POST',
        });
    }

    // Equip / Unequip
    async equipRing(backpackItemId: number, slotIndex: number) {
        return this.request<{ message: string }>('/api/equip/ring', {
            method: 'POST',
            body: JSON.stringify({ backpackItemId, slotIndex }),
        });
    }

    async equipBone(backpackItemId: number, slotIndex: number) {
        return this.request<{ message: string }>('/api/equip/bone', {
            method: 'POST',
            body: JSON.stringify({ backpackItemId, slotIndex }),
        });
    }

    async equipCore(backpackItemId: number, slotType: string) {
        return this.request<{ message: string }>('/api/equip/core', {
            method: 'POST',
            body: JSON.stringify({ backpackItemId, slotType }),
        });
    }

    async unequipRing(slotIndex: number) {
        return this.request<{ message: string }>('/api/unequip/ring', {
            method: 'POST',
            body: JSON.stringify({ slotIndex }),
        });
    }

    async unequipBone(slotIndex: number) {
        return this.request<{ message: string }>('/api/unequip/bone', {
            method: 'POST',
            body: JSON.stringify({ slotIndex }),
        });
    }

    async unequipCore(slotType: string) {
        return this.request<{ message: string }>('/api/unequip/core', {
            method: 'POST',
            body: JSON.stringify({ slotType }),
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

export interface GameState {
    profile: Profile;
    equippedRings: EquippedRing[];
    equippedBones: EquippedBone[];
    equippedCores: EquippedCore[];
    backpackItems: BackpackItem[];
    talents: Record<string, number>;
    achievements: string[];
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
}

export interface EquippedBone {
    slotIndex: number;
    yearOrdinal: number;
    rarityOrdinal: number;
    enhanceLevel: number;
    affixesJson: string | null;
    passiveSkillName: string | null;
}

export interface EquippedCore {
    slotType: string;
    coreName: string;
    qualityOrdinal: number;
    passiveSkillName: string | null;
    coreValue: number;
    coreLevel: number;
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

export interface ShopItem {
    id: number;
    name: string;
    description: string;
    price: number;
    currency: 'GOLD' | 'BOSS_COIN';
    itemType: string;
    itemData: unknown;
}

export interface LimitedShopItem extends ShopItem {
    refreshTime: string;
}

export interface Guild {
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
