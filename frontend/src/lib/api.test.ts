import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import api, { normalizeAchievements, STATE_REFRESH_EVENT, UNAUTHORIZED_EVENT } from '@/lib/api';
import type { Achievement } from '@/lib/api';

/** 与 vitest.config.mts 固定的 NEXT_PUBLIC_API_URL 一致（api.ts 的 API_BASE 在模块加载时固化） */
const API_BASE = 'http://localhost:8080';

/** 构造 fetch 响应桩：api.ts 的 request 只消费 ok / status / json */
function jsonResponse(status: number, body: unknown) {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: async () => body,
    } as unknown as Response;
}

describe('ApiClient.request', () => {
    let fetchMock: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        localStorage.clear();
        api.setToken('tok');
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('默认带 Content-Type 与 Authorization 头，URL = API_BASE + path', async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse(200, { ok: true }));

        await expect(api.getGameState()).resolves.toEqual({ ok: true });

        expect(fetchMock).toHaveBeenCalledTimes(1);
        const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        const headers = init.headers as Record<string, string>;
        expect(url).toBe(`${API_BASE}/api/game/state`);
        expect(headers['Content-Type']).toBe('application/json');
        expect(headers['Authorization']).toBe('Bearer tok');
    });

    it('401 清除 token（内存+localStorage）并派发 UNAUTHORIZED_EVENT', async () => {
        const onUnauthorized = vi.fn();
        window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
        try {
            fetchMock.mockResolvedValueOnce(jsonResponse(401, {}));

            await expect(api.getGameState()).rejects.toThrow('认证失败，请重新登录');

            expect(api.getToken()).toBeNull();
            expect(localStorage.getItem('token')).toBeNull();
            expect(onUnauthorized).toHaveBeenCalledTimes(1);
        } finally {
            window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
        }
    });

    it('409 非 auth 路径等待 400ms 后重放一次，并派发 STATE_REFRESH_EVENT', async () => {
        vi.useFakeTimers();
        const onStateRefresh = vi.fn();
        window.addEventListener(STATE_REFRESH_EVENT, onStateRefresh);
        try {
            fetchMock.mockResolvedValueOnce(jsonResponse(409, {}));
            fetchMock.mockResolvedValueOnce(jsonResponse(200, { retried: true }));

            const pending = api.getGameState();
            await vi.advanceTimersByTimeAsync(400);

            await expect(pending).resolves.toEqual({ retried: true });
            expect(fetchMock).toHaveBeenCalledTimes(2);
            // 重放与首次请求同 URL（同 path/options，仅 attempt+1）
            expect(fetchMock.mock.calls[1][0]).toBe(fetchMock.mock.calls[0][0]);
            expect(onStateRefresh).toHaveBeenCalledTimes(1);
        } finally {
            window.removeEventListener(STATE_REFRESH_EVENT, onStateRefresh);
            vi.useRealTimers();
        }
    });

    it('400 兼容 {message} 与 {error} 两种错误体，均缺失时兜底「请求失败」', async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse(400, { message: '任务未达标' }));
        await expect(api.claimQuest('tower')).rejects.toThrow('任务未达标');

        fetchMock.mockResolvedValueOnce(jsonResponse(400, { error: '今日已领取' }));
        await expect(api.claimQuest('tower')).rejects.toThrow('今日已领取');

        fetchMock.mockResolvedValueOnce(jsonResponse(400, {}));
        await expect(api.claimQuest('tower')).rejects.toThrow('请求失败');
    });

    it('checkin 走 POST /api/game/checkin（无 body）', async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse(200, { goldGained: 100 }));

        await expect(api.checkin()).resolves.toEqual({ goldGained: 100 });

        const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        expect(url).toBe(`${API_BASE}/api/game/checkin`);
        expect(init.method).toBe('POST');
        expect(init.body).toBeUndefined();
    });

    it('claimQuest 走 POST /api/game/quests/claim，body 为 JSON {questId}', async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse(200, { questId: 'battle_wins', goldGained: 500, bossCoinGained: 0, soulPowerGained: 20 }));

        await expect(api.claimQuest('battle_wins')).resolves.toMatchObject({ questId: 'battle_wins' });

        const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
        expect(url).toBe(`${API_BASE}/api/game/quests/claim`);
        expect(init.method).toBe('POST');
        expect(JSON.parse(init.body as string)).toEqual({ questId: 'battle_wins' });
    });
});

/** 新形状合法单条：GameState.achievements 升级后的元素 */
const ACH: Achievement = {
    id: 'cultivate_100',
    name: '修炼百次',
    description: '累计修炼100次',
    category: 'CULTIVATION',
    target: 100,
    progress: 40,
    unlocked: false,
    unlockedAt: null,
    rewards: { hp: 100, atk: 5, matk: 0, pdef: 0, mdef: 0, critRate: 0, critDmg: 0 },
};

describe('normalizeAchievements（成就运行时形状守卫）', () => {
    it('合法新形状数组原样透传（元素引用不变）', () => {
        const list: Achievement[] = [ACH, { ...ACH, id: 'battle_1', unlocked: true }];
        expect(normalizeAchievements(list)).toEqual(list);
    });

    it('元素非对象或缺 id 字段被过滤，合法项保留', () => {
        const ok = { ...ACH, id: 'ok' };
        const dirty: unknown[] = [ok, null, undefined, 'str', 42, true, { name: '缺id字段' }, []];
        expect(normalizeAchievements(dirty)).toEqual([ok]);
    });

    it('旧后端 string[]（每项是 string）→ 全部过滤返回空数组', () => {
        expect(normalizeAchievements(['cultivate_100', 'battle_1'])).toEqual([]);
    });

    it('非数组输入（undefined/null/普通对象）→ 空数组', () => {
        expect(normalizeAchievements(undefined)).toEqual([]);
        expect(normalizeAchievements(null)).toEqual([]);
        expect(normalizeAchievements({ id: 'x' })).toEqual([]);
    });
});
