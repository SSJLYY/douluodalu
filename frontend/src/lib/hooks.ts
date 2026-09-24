'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import api, { GameState } from '@/lib/api';

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

/**
 * 游戏主数据 hook：收敛各页面「loadGameState + try/catch + setMessage + 操作后刷新」模式。
 * runAction 包装一次 API 动作：成功回调 + 自动刷新状态，失败落到 message。
 */
export function useGameData() {
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
