'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import api, { GameState, STATE_REFRESH_EVENT } from '@/lib/api';
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

    const refresh = useCallback(async () => {
        try {
            setGameState(await api.getGameState());
        } catch (err) {
            console.error('加载游戏状态失败:', err);
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

    return { gameState, setGameState, message, setMessage, actionLoading, refresh, runAction };
}
