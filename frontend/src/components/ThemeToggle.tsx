'use client';

import { useCallback, useEffect, useSyncExternalStore } from 'react';

export const THEME_STORAGE_KEY = 'dl-theme';
export const THEME_CHANGE_EVENT = 'douluodalu:theme-change';
export type ThemeMode = 'dark' | 'light' | 'system';

const ORDER: ThemeMode[] = ['dark', 'light', 'system'];
const LABEL: Record<ThemeMode, string> = { dark: '🌙 暗色', light: '☀️ 亮色', system: '🖥️ 系统' };

function isMode(v: string | null | undefined): v is ThemeMode {
    return v === 'dark' || v === 'light' || v === 'system';
}

/** 读取当前主题模式：?theme= 临时覆盖（截图/调试用，不持久化）> localStorage > 默认暗色 */
export function readThemeMode(): ThemeMode {
    if (typeof window === 'undefined') return 'dark';
    const q = new URLSearchParams(window.location.search).get('theme');
    if (isMode(q)) return q;
    const v = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isMode(v) ? v : 'dark';
}

/** 把主题模式落到 <html> 的 .dark class（与 layout.tsx 内联初始化脚本逻辑一致） */
export function applyThemeMode(mode: ThemeMode) {
    const dark = mode === 'dark'
        || (mode === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.classList.toggle('dark', dark);
    document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
}

function subscribe(onChange: () => void) {
    window.addEventListener(THEME_CHANGE_EVENT, onChange);
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    mq.addEventListener('change', onChange);
    return () => {
        window.removeEventListener(THEME_CHANGE_EVENT, onChange);
        mq.removeEventListener('change', onChange);
    };
}

/**
 * 主题切换按钮：点击循环 暗 → 亮 → 跟随系统。
 * localStorage 持久化；useSyncExternalStore 保证 SSR 快照(暗色)与客户端实际值在 hydration 后自动同步，
 * 且 system 模式下系统偏好变化时即时重应用。
 */
export default function ThemeToggle({ className = '' }: { className?: string }) {
    const mode = useSyncExternalStore(subscribe, readThemeMode, () => 'dark' as ThemeMode);

    // 与 DOM 外部状态同步：mode（含系统偏好变化）变化时重应用到 <html>
    useEffect(() => {
        applyThemeMode(mode);
    }, [mode]);

    const cycle = useCallback(() => {
        const next = ORDER[(ORDER.indexOf(readThemeMode()) + 1) % ORDER.length];
        window.localStorage.setItem(THEME_STORAGE_KEY, next);
        applyThemeMode(next);
        window.dispatchEvent(new Event(THEME_CHANGE_EVENT));
    }, []);

    return (
        <button
            type="button"
            onClick={cycle}
            aria-label={`当前主题：${LABEL[mode]}，点击切换`}
            title="切换主题：暗色 → 亮色 → 跟随系统"
            suppressHydrationWarning
            className={`px-3 py-1.5 rounded-lg text-sm border border-line bg-surface-soft/80 hover:bg-surface-soft transition-colors ${className}`}
        >
            {LABEL[mode]}
        </button>
    );
}
