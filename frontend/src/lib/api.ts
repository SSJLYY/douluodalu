// 后端地址：Spring Boot（端口 8080，无 context-path，Controller 自带 /api 前缀）。
// 通过 frontend/.env.local 的 NEXT_PUBLIC_API_URL 注入；兜底值与本地开发约定一致。
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/** token 失效时派发的全局事件，AuthContext 监听后做软跳转（避免 window.location 硬刷新） */
export const UNAUTHORIZED_EVENT = 'douluodalu:unauthorized';

/** 409 重试前派发的全局事件，useGameData 监听后立即重取 game/state */
export const STATE_REFRESH_EVENT = 'douluodalu:state-refresh';

/**
 * 携带 HTTP status 的 API 错误：调用方按状态码分支（如自动战斗循环捕获 429 按 Retry-After 退避）。
 * extends Error → 既有 `err instanceof Error` / `err.message` 的 catch 路径全部向后兼容。
 */
export class ApiError extends Error {
    constructor(message: string, public status: number) {
        super(message);
        this.name = 'ApiError';
    }
}

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
            // ApiError 挂载 HTTP status：调用方可按状态码分支（429 退避等），message 消费方不受影响。
            const err = await res.json().catch(() => ({ message: '请求失败' })) as { message?: string; error?: string };
            throw new ApiError(err.message || err.error || '请求失败', res.status);
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

    // Check-in（每日签到）
    async checkin() {
        return this.request<CheckInResult>('/api/game/checkin', { method: 'POST' });
    }

    /**
     * 补签（只能补昨天这一天）：花费 500 金币（MAKEUP_COST_GOLD 镜像常量）修复连续签到，
     * 只修复连签、不补发当日奖励；补签后今日仍可正常签到。
     * 失败（无历史签到/昨日已签/金币不足）也是 200 + success:false + message
     * （照 prestige/awaken 惯例，前端只透传 message）。
     */
    async makeupCheckin() {
        return this.request<MakeupResult>('/api/game/checkin/makeup', { method: 'POST' });
    }

    // Daily Quests（每日任务）：未达标/已领取由后端 400 → request 统一抛 message
    async claimQuest(questId: string) {
        return this.request<ClaimQuestResult>('/api/game/quests/claim', {
            method: 'POST',
            body: JSON.stringify({ questId }),
        });
    }

    /**
     * 挂机设置（全后端第一个 PUT）：三布尔必须全量显式回传——缺字段/null → 后端 400 VALIDATION_ERROR，
     * 即「只想改一项也要把另外两项按当前值原样带上」；成功返回完整 Profile（三布尔已更新）。
     */
    async updateSettings(req: UpdateSettingsRequest) {
        return this.request<Profile>('/api/game/settings', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(req),
        });
    }

    // Actions
    async cultivate() {
        return this.request<CultivateResult>('/api/action/cultivate', { method: 'POST' });
    }

    async breakthrough() {
        return this.request<BreakthroughResult>('/api/action/breakthrough', { method: 'POST' });
    }

    /**
     * 转生（神位传承）：门槛 Lv.50（后端 GameBalance.PRESTIGE_MIN_LEVEL）。
     * 等级不足时也是 200 + success:false（照 breakthrough 惯例，message 说明门槛）；
     * 成功：等级回 1、金币/魂力清零、已装备魂环/魂骨卸回背包，其余（装备背包/天赋/成就/塔/推图/图鉴/Boss币）保留。
     */
    async prestige() {
        return this.request<PrestigeResult>('/api/action/prestige', { method: 'POST' });
    }

    /**
     * 觉醒/重醒武魂：首醒免费，重醒 5000 金（前端镜像常量见 lib/soul.ts REAWAKEN_COST_GOLD）。
     * 失败（如重醒金币不足）也是 200 + success:false + message（照 prestige 惯例，前端只透传 message）；
     * 成功返回本次觉醒/重醒的武魂名与稀有度；转生不清武魂。
     */
    async awaken() {
        return this.request<AwakenResult>('/api/action/awaken', { method: 'POST' });
    }

    /**
     * 选择/重选流派：首选免费，重选 5000 金（前端镜像常量见 lib/school.ts RESCHOOL_COST_GOLD）。
     * 失败（未知流派/门槛不足/已选择该流派/重选金币不足）也是 200 + success:false + message
     * （照 prestige/awaken 惯例，前端只透传 message）；门槛按选择时校验；转生不清流派。
     */
    async chooseSchool(school: string) {
        return this.request<ChooseSchoolResult>('/api/action/school', {
            method: 'POST',
            body: JSON.stringify({ school }),
        });
    }

    async battle() {
        return this.request<BattleResult>('/api/action/battle', { method: 'POST' });
    }

    async towerBattle() {
        return this.request<TowerBattleResult>('/api/action/tower', { method: 'POST' });
    }

    // Dungeon（每日副本，第二十九轮）：资格类前置不足（未解锁/今日已挑战/未通关/魂力不足）
    // 由后端统一 400 → request 抛 ApiError，调用方落 message 即可
    async getDungeonState() {
        return this.request<DungeonState>('/api/dungeon/state');
    }

    /** 挑战难度 tier（0~4）：每天一次机会（任选已解锁难度，胜败都算已挑战） */
    async fightDungeon(tier: number) {
        return this.request<DungeonFightResult>(`/api/dungeon/fight/${tier}`, { method: 'POST' });
    }

    /** 扫荡难度 tier：历史通关后可用，扣魂力 dungeonSweepCost(level) 直接拿奖励（同当日唯一一次名额） */
    async sweepDungeon(tier: number) {
        return this.request<DungeonSweepResult>(`/api/dungeon/sweep/${tier}`, { method: 'POST' });
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

    /** 本会成员列表（后端按 joinedAt 升序）；未入宗门 400 → request 统一抛错 */
    async getGuildMembers() {
        return this.request<GuildMemberInfo[]>('/api/guild/members');
    }

    async kickGuildMember(targetUserId: number) {
        return this.request<{ message: string }>('/api/guild/kick', {
            method: 'POST',
            body: JSON.stringify({ targetUserId }),
        });
    }

    async transferGuildLeader(targetUserId: number) {
        return this.request<{ message: string }>('/api/guild/transfer', {
            method: 'POST',
            body: JSON.stringify({ targetUserId }),
        });
    }

    async disbandGuild() {
        return this.request<{ message: string }>('/api/guild/disband', { method: 'POST' });
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
        return this.request<LeaveGuildResult>('/api/guild/leave', {
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

    /** 宗门 Boss 周榜（GET 照现有无 body 方法惯例）；调用方对失败静默降级，不阻塞挑战主功能 */
    async guildBossRank() {
        return this.request<GuildBossRank>('/api/guild/boss/rank');
    }

    /**
     * 宗门 Boss 周血池状态（GET 照现有无 body 方法惯例）。
     * 旧后端无此端点（404）：调用方须静默降级（隐藏血条区），不阻塞挑战主功能。
     */
    async guildBossStatus() {
        return this.request<GuildBossStatus>('/api/guild/boss/status');
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

    /**
     * 魂骨强化：itemIndex 与 slotIndex 二选一——
     * - itemIndex：背包 BONE 子列表下标（同 equipBone 的 boneIndex 口径）；
     * - slotIndex：已装备骨槽位 0-5（同 unequipBone 口径）。
     * 都空/都非空 → 后端 400「参数无效…」。成功 200 { message }（含当前强化等级与花费）；
     * 失败（金币不足/已达上限）也是 400 → request 统一抛 error 文案，调用方落到 message 条。
     * 费用预览镜像见 lib/equipment.ts boneEnhanceCost（与后端 GameBalance 同源）。
     */
    async enhanceBone(req: { itemIndex?: number; slotIndex?: number }) {
        return this.request<{ message: string }>('/api/equipment/bone/enhance', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(req),
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
    /** 成就（后端 15 条全量定义，含未解锁进度）。旧后端返回 string[] → 消费方须先过 normalizeAchievements 形状守卫降级 */
    achievements: Achievement[];
    /** 任务#21：总战斗力（含装备加成，与战斗/爬塔响应同源） */
    power: number;
    /** 当前已装备魂环总负荷 */
    ringLoad: number;
    /** 魂环吸收容量（=根骨×6）；装环超容量后端返回 400 */
    capacity: number;
    /** 任务#23：战力明细（来源拆分；basePower+ringPower+bonePower+corePower(+achievement) == power） */
    powerDetail: PowerDetail;
    /** 每日签到状态（7 日循环）。后端响应升级前该字段可能缺失（undefined），消费方须判空降级 */
    checkIn: CheckInStatus;
    /** 每日任务（固定 5 条，按日重置）。后端升级前字段缺失 → 消费方整卡隐藏（纯增量功能） */
    dailyQuests?: DailyQuests;
    /**
     * 战斗属性摘要（战力面板底部一行展示）。旧后端缺失（undefined）→ 消费方整行隐藏；
     * critRate/critDmg 为百分点（5 = 5%、150 = 150%）。
     */
    combatStats?: CombatStats;
}

/** 战斗属性摘要（GameState.combatStats，后端 CombatStatsDto 同源）。物攻已含在 powerDetail.baseAtk 内，不单独下发 */
export interface CombatStats {
    matk: number;      // 魔攻
    pdef: number;      // 物防
    mdef: number;      // 魔防
    critRate: number;  // 暴击率（百分点，如 5 = 5%）
    critDmg: number;   // 暴击伤害（百分点，如 150 = 150%）
}

    /**
     * 对应后端 PowerDetailDto（任务#23）。
     * 后端保证：ringAtk+boneAtk+coreAtk == 攻击加成总值、ringHp+boneHp == 生命加成总值、
     * 四行 power* 求和 == power（后端补 achievement/prestige/soul/school/enhance 字段后为五行~九行：
     * + achievement + prestige + soul + school + enhance）。
     * 魂核只加攻击（coreHp=0），玩家模型无基础生命（baseHp=0）。
     */
export interface PowerDetail {
    baseAtk: number;
    baseHp: number;
    basePower: number;
    ringAtk: number;
    ringHp: number;
    ringPower: number;
    boneAtk: number;
    boneHp: number;
    bonePower: number;
    coreAtk: number;
    coreHp: number;
    corePower: number;
    /** 成就属性加成折算战力（第 5 行）。旧后端无此字段 → undefined 按 0 处理、行隐藏；求和不变量：base+ring+core+bone+achievement == power */
    achievement?: number;
    /** 转生加成折算战力（第 6 行，每转全属性+10% 的折算）。旧后端无此字段 → undefined 按 0 处理、行隐藏；求和不变量扩为：base+ring+core+bone+achievement+prestige == power */
    prestige?: number;
    /** 武魂加成折算战力（第 7 行，武魂七属性折算）。旧后端无此字段 → undefined 按 0 处理、行隐藏；求和不变量扩为：base+ring+core+bone+achievement+prestige+soul == power */
    soul?: number;
    /** 流派加成折算战力（第 8 行）。旧后端无此字段 → undefined 按 0 处理、行隐藏；求和不变量扩为：base+ring+core+bone+achievement+prestige+soul+school == power */
    school?: number;
    /** 魂骨强化加成折算战力（第 9 行「🔨 强化」）。旧后端无此字段 → undefined 按 0 处理、行隐藏；求和不变量扩为：base+ring+core+bone+achievement+prestige+soul+school+enhance == power */
    enhance?: number;
}

/** 成就属性奖励（Achievement.rewards）。matk/pdef/mdef/critRate/critDmg 后端暂未生效，前端只展示 hp/atk */
export interface AchievementReward {
    hp: number;
    atk: number;
    matk: number;
    pdef: number;
    mdef: number;
    critRate: number;
    critDmg: number;
}

/** 成就单条定义（GameState.achievements 元素，后端 15 条全量下发含未解锁进度）。unlockedAt 为 yyyy-MM-dd 或 null */
export interface Achievement {
    id: string;
    name: string;
    description: string;
    /** CULTIVATION | SOUL_RING | BATTLE | TOWER | PRESTIGE */
    category: string;
    target: number;
    progress: number;
    unlocked: boolean;
    unlockedAt?: string | null;
    rewards: AchievementReward;
}

/**
 * 成就运行时形状守卫：旧后端 achievements 返回 string[]（每项是 string），与新形状不符。
 * 规则：非数组 → 空数组；数组元素不是对象或缺 id 字段 → 过滤掉
 * （string[] 每项都会被过滤 → 结果为空数组，调用方据此走 EmptyPanel 降级）；合法新形状原样透传。
 */
export function normalizeAchievements(value: unknown): Achievement[] {
    if (!Array.isArray(value)) return [];
    return value.filter((item): item is Achievement =>
        typeof item === 'object' && item !== null && 'id' in item,
    );
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
    /** 当前武魂稀有度（COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC）。旧后端缺失按 null → 前端只显武魂名不显徽章 */
    soulRarity?: string | null;
    /** 当前武魂专属技能名（如「天使圣光」）。旧后端缺失/未觉醒按 null → 主页武魂区不显技能行 */
    soulSkillName?: string | null;
}

/** PUT /api/game/settings 请求体：三布尔缺一不可（缺字段/null → 后端 400 VALIDATION_ERROR） */
export interface UpdateSettingsRequest {
    autoBattle: boolean;
    autoAdvanceMap: boolean;
    autoBreakthrough: boolean;
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
    /**
     * 技能回合为武魂技能名（如「天使圣光」），普通回合 null/缺失。
     * 技能攻击回合 playerDamage 即技能伤害（多段为等价合并值）；
     * 治疗回合 playerDamage=0 且 playerHpAfter > playerHpBefore。
     * 旧后端缺失（undefined）→ 回放无技能高亮，渲染与旧版一致。
     */
    skillName?: string | null;
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
    /** 战斗回合日志：后端升级前可能缺失（undefined），消费方走无回放的原展示 */
    battleLog?: BattleRound[];
}

// ======== 每日副本（第二十九轮，设计文档 §8） ========

/** 单难度状态（DungeonState.tiers 固定 5 条，tier = 数组下标 0~4） */
export interface DungeonTierState {
    tier: number;
    name: string;
    difficultyName: string;
    bossName: string;
    hpMult: number;
    atkMult: number;
    /** 奖励预览：金币（后端已乘转生倍率，直接展示） */
    goldReward: number;
    killingReward: number;
    /** 掉落层级（§8 表 tier 列；背包有空间必掉，满则丢失） */
    dropTier: number;
    /** 解锁条件：转生次数下限 */
    unlockPrestige: number;
    unlocked: boolean;
    /** 今日机会是否已用（一天一次机会全体难度共享——战斗胜/败、扫荡都算） */
    challengedToday: boolean;
    /** 该难度今日是否已通关 */
    clearedToday: boolean;
    /** 可否扫荡：已解锁 且 历史通关过该难度 且 今日机会未用 */
    sweepable: boolean;
    /** 扫荡魂力消耗（后端按当前等级现算：50 + 等级 × 5） */
    sweepSoulPowerCost: number;
}

/** GET /api/dungeon/state 响应（date=yyyy-MM-dd，与签到/每日任务同一时区口径） */
export interface DungeonState {
    date: string;
    challengedToday: boolean;
    /** 当日最高已通关难度（-1=未通关，0~4=难度层级） */
    tierCompleted: number;
    tiers: DungeonTierState[];
}

/** POST /api/dungeon/fight/{tier} 响应：结构与 TowerBattleResult 同族，battleLog 复用 BattleReplay 回放 */
export interface DungeonFightResult {
    won: boolean;
    rounds: number;
    tier: number;
    monsterName: string;
    monsterMaxHp: number;
    /** 失败无奖励：金币/杀气/掉落为 0/空 */
    goldGained: number;
    killingGained: number;
    drops: BackpackItem[];
    playerHp: number;
    playerLevel: number;
    playerGold: number;
    playerKillingIntent: number;
    battleLog?: BattleRound[];
    /** 背包满导致掉落丢失时的提示 */
    message?: string | null;
    power: number;
}

/** POST /api/dungeon/sweep/{tier} 响应：不战斗直接拿该难度奖励（同当日唯一一次名额） */
export interface DungeonSweepResult {
    tier: number;
    soulPowerSpent: number;
    goldGained: number;
    killingGained: number;
    drops: BackpackItem[];
    playerGold: number;
    playerSoulPower: number;
    message?: string | null;
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

/** POST /api/action/prestige 响应：等级不足时也是 200 + success:false（message 说明门槛），前端只透传 message */
export interface PrestigeResult {
    success: boolean;
    prestigeCount: number;
    message: string;
}

/**
 * POST /api/action/awaken 响应：失败（重醒金币不足等）也是 200 + success:false + message。
 * 稀有度枚举：COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC（中文名/色板映射见 lib/soul.ts）。
 */
export interface AwakenResult {
    success: boolean;
    /** 本次觉醒/重醒结果武魂名 */
    martialSoulName: string;
    /** 六档稀有度枚举之一；未知值前端按「只显名字不显徽章」降级 */
    rarity: string;
    /** 首醒 0 / 重醒 5000 */
    goldSpent: number;
    /** false=首次觉醒 / true=重醒 */
    reawakened: boolean;
    message: string;
}

/**
 * POST /api/action/school 响应：失败（未知流派/门槛不足/已选择该流派/重选金币不足）也是
 * 200 + success:false + message（照 prestige/awaken 惯例，前端只透传 message）；
 * 失败时 chosenSchool=""，成功为所选流派枚举名（BALANCED|PHYSICAL|MAGIC|SUPPORT|CONTROL|ASSASSIN）。
 */
export interface ChooseSchoolResult {
    success: boolean;
    chosenSchool: string;
    message: string;
}

export interface OfflineReward {
    offlineSeconds: number;
    goldGained: number;
    expGained: number;
    battleWins: number;
}

/** 每日签到：单日奖励（7 日一循环）。数值表由后端下发，前端不得硬编码 */
export interface CheckInReward {
    day: number;
    gold: number;
    bossCoin: number;
    soulPower: number;
}

/** 每日签到状态（GameState.checkIn）。后端升级前字段可能 undefined，消费方判空降级 */
export interface CheckInStatus {
    /** 今日是否已签 */
    signedToday: boolean;
    /** 当前连续签到天数 */
    streak: number;
    /** 累计签到天数 */
    totalDays: number;
    /** 下次签到落在 7 日循环第几天（1-7） */
    nextCycleDay: number;
    /** 7 天奖励全表（后端下发） */
    rewards: CheckInReward[];
    /** 昨日漏签且可补签（花费 MAKEUP_COST_GOLD 金币，只修复连签不补发当日奖励）。旧后端缺失按 undefined → 补签条不渲染（纯增量功能） */
    makeupAvailable?: boolean;
}

/** POST /api/game/checkin 响应；已签返回 400（message=「今日已签到，明天再来吧」） */
export interface CheckInResult {
    goldGained: number;
    bossCoinGained: number;
    soulPowerGained: number;
    streak: number;
    totalDays: number;
    cycleDay: number;
}

/** 补签花费金币（与后端 MAKEUP_COST_GOLD 同源，改需双向同步——照 REAWAKEN_COST_GOLD/RESCHOOL_COST_GOLD 镜像惯例） */
export const MAKEUP_COST_GOLD = 500;

/**
 * POST /api/game/checkin/makeup 响应：失败（无历史签到/昨日已签/金币不足）也是 200 + success:false + message
 * （照 prestige/awaken 惯例，前端只透传 message）。只修复连签、不补发当日奖励；补签后今日仍可正常签到。
 */
export interface MakeupResult {
    success: boolean;
    /** 补签后的连续签到天数 */
    streak: number;
    /** 补签后的累计签到天数 */
    totalDays: number;
    /** 本次补签实际花费金币（= MAKEUP_COST_GOLD） */
    goldSpent: number;
    message: string;
}

/** 每日任务单条：固定 5 条（battle_wins/cultivate/tower/checkin/shop_buy），后端按日重置 */
export interface DailyQuest {
    id: string;
    description: string;
    target: number;
    progress: number;
    claimed: boolean;
    rewardGold: number;
    rewardBossCoin: number;
    rewardSoulPower: number;
}

/** 每日任务集合（GameState.dailyQuests）。后端升级前字段可能 undefined，消费方判空隐藏整卡 */
export interface DailyQuests {
    date: string;
    quests: DailyQuest[];
}

/** POST /api/game/quests/claim 响应；未达标/今日已领取返回 400（message 由 request 统一抛出） */
export interface ClaimQuestResult {
    questId: string;
    goldGained: number;
    bossCoinGained: number;
    soulPowerGained: number;
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

/** GET /api/guild/members 行：joinedAt 为后端 Jackson 默认 ISO-8601 字符串 */
export interface GuildMemberInfo {
    userId: number;
    nickname: string;
    joinedAt: string;
    contribution: number;
    isLeader: boolean;
}

/**
 * POST /api/guild/leave 响应。后端升级后附带退出结局标记：
 * - disbanded=true：退出即宗门解散（如只剩自己时退出）；
 * - transferredTo：退出时宗主已自动转让给该成员（继任者昵称）。
 * 旧后端仅返回 {message}，两字段均可选，消费方按存在性容错。
 */
export interface LeaveGuildResult {
    message: string;
    disbanded?: boolean;
    transferredTo?: string;
}

export interface GuildBossResult {
    won: boolean;
    damage: number;
    bossHp: number;
    goldGained: number;
    bossCoinGained: number;
    item: BackpackItem;
    message: string;
    /**
     * 响应尾部血池三字段（新后端）：挑战扣的是全宗门共享周血池，bossHp 为挑战后剩余值，
     * bossMaxHp 为池容量，killed=true 表示本次挑战击杀了 Boss（击杀者有额外奖励）。
     * 旧后端缺失（undefined）→ 消费方按存在性容错（不渲染击杀高亮）。
     */
    bossMaxHp?: number;
    killed?: boolean;
}

/** GET /api/guild/boss/status 响应（JWT，需在宗门，不在宗门 400 由 request 统一抛错）：
 * bossHp/bossMaxHp 为全宗门共享的周血池（每周一重置重生）；killed=true 本周已被击杀，
 * 击杀后至下周一期间挑战返回 400「本周 Boss 已被击杀，下周一再来」。 */
export interface GuildBossStatus {
    bossHp: number;
    bossMaxHp: number;
    killed: boolean;
}

/** GET /api/guild/boss/rank 单行：weeklyDamage 为该成员本周累计 Boss 伤害（仅 >0 入榜） */
export interface GuildBossRankEntry {
    userId: number;
    nickname: string;
    weeklyDamage: number;
}

/**
 * GET /api/guild/boss/rank 响应（JWT，需在宗门内，不在宗门由 request 统一抛错）：
 * entries 按 weeklyDamage 降序、最多 10 条；myRank 为本人本周排名（本周无伤害按 ≤0 视为未上榜）。
 */
export interface GuildBossRank {
    entries: GuildBossRankEntry[];
    myRank: number;
}

export const api = new ApiClient();
export default api;
