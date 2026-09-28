'use client';

import { useCallback, useEffect, useRef, useState, type RefObject } from 'react';
import api, { ApiError, BattleResult, GameState, STATE_REFRESH_EVENT } from '@/lib/api';
import { useAuth } from '@/contexts/AuthContext';
import { useLiveBattle } from '@/lib/live';

function errorMessage(err: unknown, fallback: string) {
    return err instanceof Error && err.message ? err.message : fallback;
}

/**
 * 通用异步数据加载：loading/error/refresh + setData（本地乐观更新用）。
 * 项目未引入 React Query，这里做最小实现收敛各页面的重复模式。
 */
export function useAsync<T>(fetcher: () => Promise<T>, options: { immediate?: boolean } = {}) {
    const { immediate = true } = options;
    const [data, setData] = useState<T | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const fetcherRef = useRef(fetcher);
    useEffect(() => {
        fetcherRef.current = fetcher;
    });

    const refresh = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            setData(await fetcherRef.current());
        } catch (err) {
            setError(errorMessage(err, '加载失败'));
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (immediate) queueMicrotask(refresh);
    }, [immediate, refresh]);

    return { data, setData, loading, error, refresh };
}

/** 放置类游戏默认轮询间隔（毫秒）：刷新离线挂机产生的数值；WS 断开时的兜底节奏 */
const POLL_INTERVAL_MS = 12000;
/** WS 连接就绪时的放宽轮询间隔：实时性交给 /topic/battle 推送，轮询仅兜底 */
const LIVE_POLL_INTERVAL_MS = 30000;

/**
 * 游戏主数据 hook：收敛各页面「loadGameState + try/catch + setMessage + 操作后刷新」模式。
 * runAction 包装一次 API 动作：成功回调 + 自动刷新状态，失败落到 message。
 * 内置定时轮询刷新放置收益：WS(/ws-native) 在线时放宽到 30s 并订阅本人战斗广播即时刷新，
 * 断开时回退 12s 纯轮询；409 重试事件（STATE_REFRESH_EVENT）触发即时重取；卸载时清理定时器。
 */
export function useGameData(pollMs: number | false = POLL_INTERVAL_MS) {
    const { user } = useAuth();
    const [gameState, setGameState] = useState<GameState | null>(null);
    const [message, setMessage] = useState('');
    const [actionLoading, setActionLoading] = useState(false);
    // 任务#27：/api/game/state 最近一次加载失败的原因（成功即清空）。
    // 首屏未拿到数据时页面据此渲染统一错误重试面板；已拿到数据时静默兜底旧值，不打扰。
    const [loadError, setLoadError] = useState('');

    const refresh = useCallback(async () => {
        // 未登录（无 token）时不发 /api/game/state：匿名直接访问 /game/* 时布局骨架屏阶段
        // 就会被 authChecked 门控重定向回登录页，无需数据；跳过可避免控制台出现 403 资源报错。
        if (!api.getToken()) return;
        try {
            setGameState(await api.getGameState());
            setLoadError('');
        } catch (err) {
            // 轮询失败属于常态（后端重启/网络抖动），不再 console.error 刷屏，交给页面门控展示
            setLoadError(errorMessage(err, '加载游戏状态失败'));
        }
    }, []);

    useEffect(() => {
        queueMicrotask(refresh);
    }, [refresh]);

    // WS 推送与轮询/多事件可能同帧触发：1s 窗口内只重取一次
    const lastRefreshAtRef = useRef(0);
    const debouncedRefresh = useCallback(() => {
        const now = Date.now();
        if (now - lastRefreshAtRef.current < 1000) return;
        lastRefreshAtRef.current = now;
        void refresh();
    }, [refresh]);

    // 本人战斗广播（后端把所有人的战斗都发到 /topic/battle，必须按 userId 过滤）→ 即时刷新
    const userIdRef = useRef(user?.userId);
    useEffect(() => {
        userIdRef.current = user?.userId;
    });
    const liveConnected = useLiveBattle((ev) => {
        if (userIdRef.current != null && ev.userId === userIdRef.current) debouncedRefresh();
    });

    // 连接刚就绪/断开时校准一次，避免状态在切换间隔时停留在旧数据
    useEffect(() => {
        lastRefreshAtRef.current = Date.now();
        queueMicrotask(refresh);
    }, [liveConnected, refresh]);

    // 放置体验：定时轮询刷新数值；页面隐藏时跳过，卸载时清理定时器
    const effectivePollMs = pollMs === false ? false : (liveConnected ? Math.max(pollMs, LIVE_POLL_INTERVAL_MS) : pollMs);
    useEffect(() => {
        if (!effectivePollMs) return;
        const timer = setInterval(() => {
            if (typeof document !== 'undefined' && document.hidden) return;
            void refresh();
        }, effectivePollMs);
        return () => clearInterval(timer);
    }, [effectivePollMs, refresh]);

    // 409 乐观锁重试时 api.ts 派发该事件，立即重取最新 state
    useEffect(() => {
        window.addEventListener(STATE_REFRESH_EVENT, refresh);
        return () => window.removeEventListener(STATE_REFRESH_EVENT, refresh);
    }, [refresh]);

    const runAction = useCallback(async <T,>(
        action: () => Promise<T>,
        onSuccess: (result: T) => void,
        fallbackError = '操作失败',
    ) => {
        setActionLoading(true);
        try {
            const result = await action();
            onSuccess(result);
            await refresh();
        } catch (err) {
            setMessage(errorMessage(err, fallbackError));
            await refresh();
        } finally {
            setActionLoading(false);
        }
    }, [refresh]);

    return { gameState, setGameState, message, setMessage, actionLoading, loadError, refresh, runAction };
}

/**
 * 确认/选择类弹窗的键盘可达性收敛（Escape 关闭 + 打开时聚焦「取消」钮），
 * 替换各弹窗复制的「focus(preventScroll)+keydown」effect：
 * - preventScroll：内容超高的弹窗（流派 6 卡）focus 时不把容器滚到取消钮处，避免开弹窗看不到标题；
 * - close 经 ref 中转、effect 只依赖 open——与原先 [open] 单依赖等价，
 *   actionLoading 翻转等重渲染不会重复 focus 抢走「确认」钮焦点；
 * - 未做完整焦点圈禁（Tab 仍可离开弹窗），与既有弹窗同级、不强拦截键盘路径。
 */
export function useModalEscape<T extends HTMLElement>(
    open: boolean,
    close: () => void,
    cancelRef?: RefObject<T | null>,
) {
    const closeRef = useRef(close);
    useEffect(() => {
        closeRef.current = close;
    });
    useEffect(() => {
        if (!open) return;
        cancelRef?.current?.focus({ preventScroll: true });
        const onKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') closeRef.current();
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [open, cancelRef]);
}

/** useGameData.runAction 的函数型签名：抽出来供 makeRunMessageAction 注入测试替身 */
export type RunActionFn = <T,>(
    action: () => Promise<T>,
    onSuccess: (result: T) => void,
    fallbackError?: string,
) => Promise<void>;

/**
 * runAction 简写：成功路径固定为「把后端响应的 message 透传到提示条」，
 * 收敛各页面 runAction(() => api.x(), (r) => setMessage(r.message), 'xx失败') 的重复三参写法。
 * 失败落 message / 成功后自动刷新仍由注入的 runAction 承担，行为与手写调用完全一致。
 */
export function makeRunMessageAction(
    runAction: RunActionFn,
    setMessage: (msg: string) => void,
) {
    return async <T extends { message: string }>(
        action: () => Promise<T>,
        fallbackError = '操作失败',
    ): Promise<void> => {
        await runAction(action, (r) => setMessage(r.message), fallbackError);
    };
}

// ---- 自动战斗（任务：挂机设置） ----

/** 自动战斗 tick 间隔（后端不参与循环，客户端每 2s 打一次 /api/action/battle） */
export const AUTO_BATTLE_INTERVAL_MS = 2000;
/** 429 退避暂停时长（后端全局 300 RPM/IP 限流命中后按 Retry-After: 60 暂停） */
export const AUTO_BATTLE_RETRY_PAUSE_MS = 60000;

/**
 * 突破魂力消耗（镜像后端 GameService.getBreakthroughCost：120·L^1.55 截断取整）。
 * 前端唯一镜像点：主页突破提示与自动突破阈值都从这里取，改需与后端双向同步。
 */
export function breakthroughCostFor(level: number): number {
    return Math.floor(120 * Math.pow(level, 1.55));
}

/**
 * 自动战斗循环：enabled 时每 AUTO_BATTLE_INTERVAL_MS 调一次 api.battle()。
 * 设计要点：
 * - 不走 runAction：省去每 tick 的整页 actionLoading 翻转与 state refresh——状态刷新交给
 *   已订阅的本人 /topic/battle 广播 → useGameData 的 debouncedRefresh（1s 去重），数值自然跟上；
 * - document.hidden 跳过 tick（与 useGameData 轮询同款先例）；上一场未返回跳过下一 tick（防堆积）；
 * - 429（ApiError.status）退避：暂停 AUTO_BATTLE_RETRY_PAUSE_MS 后自动恢复，普通错误不打断循环；
 * - autoBreakthrough 开启且战斗后魂力 ≥ breakthroughCostFor(level) → 顺手调一次 breakthrough
 *   （失败 success=false 或抛错均静默，不影响下一 tick）；
 * - 结果经 onResult 交给调用方（主页只更新战斗结果卡，不刷 message 免 toast 刷屏）；
 *   卸载 / enabled 翻转清理定时器。
 */
export function useAutoBattle(
    enabled: boolean,
    autoBreakthrough: boolean,
    onResult: (r: BattleResult) => void,
) {
    // 回调/开关走 ref：循环 effect 只依赖 enabled，回调换引用不重启定时器
    const onResultRef = useRef(onResult);
    useEffect(() => {
        onResultRef.current = onResult;
    });
    const autoBreakthroughRef = useRef(autoBreakthrough);
    useEffect(() => {
        autoBreakthroughRef.current = autoBreakthrough;
    });

    // 上一场 battle 未返回标记（防 tick 堆积）与 429 退避截止时间（标志位跳过，不重设 interval）
    const inFlightRef = useRef(false);
    const pausedUntilRef = useRef(0);

    useEffect(() => {
        if (!enabled) return;
        const timer = setInterval(() => {
            if (typeof document !== 'undefined' && document.hidden) return;
            if (inFlightRef.current) return;
            if (Date.now() < pausedUntilRef.current) return;
            inFlightRef.current = true;
            void (async () => {
                try {
                    const result = await api.battle();
                    onResultRef.current(result);
                    // 顺手自动突破：以战斗结果里的最新魂力/等级判阈值，每 tick 至多补一次
                    if (autoBreakthroughRef.current
                        && result.playerSoulPower >= breakthroughCostFor(result.playerLevel)) {
                        await api.breakthrough().catch(() => undefined);
                    }
                } catch (err) {
                    // 限流退避：暂停后由后续 tick 自然恢复；其余错误（网络抖动等）静默，不打断循环
                    if (err instanceof ApiError && err.status === 429) {
                        pausedUntilRef.current = Date.now() + AUTO_BATTLE_RETRY_PAUSE_MS;
                    }
                } finally {
                    inFlightRef.current = false;
                }
            })();
        }, AUTO_BATTLE_INTERVAL_MS);
        return () => clearInterval(timer);
    }, [enabled]);
}
