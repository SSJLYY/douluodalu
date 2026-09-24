'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/contexts/AuthContext';
import { useGameData } from '@/lib/hooks';
import ThemeToggle from '@/components/ThemeToggle';

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
    const { user, isLoading, logout } = useAuth();
    const router = useRouter();
    const pathname = usePathname();
    const { gameState, refresh } = useGameData();

    useEffect(() => {
        if (!isLoading && !user) {
            router.push('/');
        }
    }, [user, isLoading, router]);

    // 切换子页面时刷新顶部资源栏（金币/魂力/Boss币）
    useEffect(() => {
        queueMicrotask(refresh);
    }, [pathname, refresh]);

    if (isLoading || !user) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <div className="text-xl">加载中...</div>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex flex-col">
            {/* 顶部状态栏 */}
            <header className="bg-surface border-b border-line p-3">
                <div className="max-w-7xl mx-auto flex justify-between items-center">
                    <div className="flex items-center gap-4">
                        <h1 className="text-xl font-bold text-accent">斗罗大陆</h1>
                        <span className="text-gray-400">|</span>
                        <span className="text-gray-300">{user.nickname}</span>
                    </div>
                    
                    {gameState && (
                        <div className="flex items-center gap-4 text-sm">
                            <div className="flex items-center gap-2">
                                <span className="text-yellow-500">💰</span>
                                {/* key=数值：变化时重挂载触发 dl-value-flash 过渡动画 */}
                                <span key={gameState.profile.gold} className="dl-value-flash">{gameState.profile.gold.toLocaleString()}</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-purple-500">⚡</span>
                                <span key={gameState.profile.soulPower} className="dl-value-flash">{gameState.profile.soulPower.toLocaleString()}</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-blue-500">💎</span>
                                <span key={gameState.profile.bossCoin} className="dl-value-flash">{gameState.profile.bossCoin.toLocaleString()}</span>
                            </div>
                            {/* 任务#21：总战力（GameState.power），变化沿用 flash 动画 */}
                            <div className="flex items-center gap-2" title="战斗力（含装备加成）">
                                <span className="text-orange-500">⚔️</span>
                                <span key={gameState.power} className="dl-value-flash font-semibold text-orange-300">{gameState.power.toLocaleString()}</span>
                                <span className="text-xs text-gray-400">战力</span>
                            </div>
                        </div>
                    )}

                    <div className="flex items-center gap-2">
                        <ThemeToggle />
                        <button
                            onClick={logout}
                            className="px-4 py-2 bg-red-600 hover:bg-red-700 rounded text-sm"
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
                    <div className="flex overflow-x-auto">
                        {NAV_ITEMS.map((item) => (
                            <Link
                                key={item.href}
                                href={item.href}
                                className={`flex flex-col items-center py-3 px-4 min-w-[80px] transition-colors ${
                                    pathname === item.href
                                        ? 'text-accent bg-surface-soft'
                                        : 'text-gray-400 hover:text-foreground hover:bg-surface-soft'
                                }`}
                            >
                                <span className="text-xl">{item.icon}</span>
                                <span className="text-xs mt-1">{item.label}</span>
                            </Link>
                        ))}
                    </div>
                </div>
            </nav>
        </div>
    );
}
