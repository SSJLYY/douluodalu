'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/contexts/AuthContext';
import { useGameData } from '@/lib/hooks';
import ThemeToggle from '@/components/ThemeToggle';
import { SkeletonRows } from '@/components/StateViews';

const NAV_ITEMS = [
    { href: '/game', label: '战斗', icon: '⚔️' },
    { href: '/game/cultivation', label: '修炼', icon: '🧘' },
    { href: '/game/equipment', label: '装备', icon: '🛡️' },
    { href: '/game/shop', label: '商店', icon: '🏪' },
    { href: '/game/tower', label: '杀戮之都', icon: '🏰' },
    { href: '/game/talent', label: '天赋', icon: '✨' },
    { href: '/game/wiki', label: '百科', icon: '📖' },
    { href: '/game/social/rank', label: '排行榜', icon: '🏆' },
    { href: '/game/social/guild', label: '宗门', icon: '🏛️' },
];

export default function GameLayout({ children }: { children: React.ReactNode }) {
    const { user, isLoading, authChecked, logout } = useAuth();
    const router = useRouter();
    const pathname = usePathname();
    const { gameState, refresh } = useGameData();

    // 只在 token 校验完成后才判定「未登录」：authChecked 之前一律骨架屏，
    // 防止已登录用户在 getMe 返回前被误踢回登录页（见 AuthContext authChecked 注释）。
    useEffect(() => {
        if (authChecked && !isLoading && !user) {
            router.push('/');
        }
    }, [authChecked, user, isLoading, router]);

    // 切换子页面时刷新顶部资源栏（金币/魂力/Boss币）
    useEffect(() => {
        queueMicrotask(refresh);
    }, [pathname, refresh]);

    if (!authChecked || isLoading || !user) {
        // 鉴权回源期间给整页框架骨架，替代纯文本「加载中...」
        return (
            <div className="min-h-screen flex flex-col">
                <header className="bg-surface border-b border-line px-3 py-2.5">
                    <div className="dl-skeleton h-7 w-32" />
                </header>
                <main className="flex-1 max-w-7xl w-full mx-auto p-4">
                    <SkeletonRows rows={6} testId="auth-boot-skeleton" />
                </main>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex flex-col">
            {/* 顶部状态栏：任务#27 窄屏收纳——<md 时货币行折到第二行整行横滚（dl-scroll-x），不再挤压换行破版 */}
            <header className="bg-surface border-b border-line px-3 py-2.5">
                <div className="max-w-7xl mx-auto flex flex-wrap items-center gap-x-3 gap-y-1.5">
                    <div className="flex items-center gap-2 min-w-0">
                        {/* 全称：主页内置 header 已移除（避免双顶栏），此处补偿完整标题；溢出由外层 flex-wrap + 昵称 truncate 兜底 */}
                        <h1 className="text-lg sm:text-xl font-bold text-accent shrink-0">斗罗大陆·放置传说</h1>
                        <span className="text-gray-400 hidden sm:inline">|</span>
                        <span className="text-gray-300 truncate max-w-[9rem] sm:max-w-none">{user.nickname}</span>
                    </div>

                    {gameState && (
                        <div className="dl-scroll-x order-3 w-full md:order-none md:w-auto md:flex-1 md:min-w-0" aria-label="玩家资源">
                            <div className="inline-flex items-center gap-4 text-sm whitespace-nowrap md:justify-end md:w-full">
                                <div className="flex items-center gap-2 shrink-0">
                                    <span className="text-yellow-500">💰</span>
                                    {/* key=数值：变化时重挂载触发 dl-value-flash 过渡动画 */}
                                    <span key={gameState.profile.gold} className="dl-value-flash tabular-nums">{gameState.profile.gold.toLocaleString()}</span>
                                </div>
                                <div className="flex items-center gap-2 shrink-0">
                                    <span className="text-purple-500">⚡</span>
                                    <span key={gameState.profile.soulPower} className="dl-value-flash tabular-nums">{gameState.profile.soulPower.toLocaleString()}</span>
                                </div>
                                <div className="flex items-center gap-2 shrink-0">
                                    <span className="text-blue-500">💎</span>
                                    <span key={gameState.profile.bossCoin} className="dl-value-flash tabular-nums">{gameState.profile.bossCoin.toLocaleString()}</span>
                                </div>
                                {/* 任务#21：总战力（GameState.power），变化沿用 flash 动画 */}
                                <div className="flex items-center gap-2 shrink-0" title="战斗力（含装备加成）">
                                    <span className="text-orange-500">⚔️</span>
                                    <span key={gameState.power} className="dl-value-flash font-semibold text-orange-300 tabular-nums">{gameState.power.toLocaleString()}</span>
                                    <span className="text-xs text-gray-400">战力</span>
                                </div>
                            </div>
                        </div>
                    )}

                    <div className="ml-auto shrink-0 flex items-center gap-2">
                        <ThemeToggle className="max-sm:min-h-11" />
                        <button
                            onClick={logout}
                            className="px-4 min-h-11 bg-red-600 hover:bg-red-700 rounded text-sm"
                        >
                            退出
                        </button>
                    </div>
                </div>
            </header>

            {/* 主内容区域 */}
            <main className="flex-1 max-w-7xl w-full mx-auto p-4">
                {children}
            </main>

            {/* 底部导航栏 */}
            <nav className="bg-surface border-t border-line">
                <div className="max-w-7xl mx-auto">
                    <div className="dl-scroll-x flex">
                        {NAV_ITEMS.map((item) => (
                            <Link
                                key={item.href}
                                href={item.href}
                                className={`flex flex-col items-center justify-center py-3 px-4 min-w-[80px] min-h-[56px] shrink-0 transition-colors ${
                                    pathname === item.href
                                        ? 'text-accent bg-surface-soft'
                                        : 'text-gray-400 hover:text-foreground hover:bg-surface-soft'
                                }`}
                            >
                                <span className="text-xl">{item.icon}</span>
                                <span className="text-xs mt-1 whitespace-nowrap">{item.label}</span>
                            </Link>
                        ))}
                    </div>
                </div>
            </nav>
        </div>
    );
}
