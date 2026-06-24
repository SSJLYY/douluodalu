'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/contexts/AuthContext';
import api, { GameState } from '@/lib/api';

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
    const [gameState, setGameState] = useState<GameState | null>(null);

    useEffect(() => {
        if (!isLoading && !user) {
            router.push('/');
        }
        if (user) {
            loadGameState();
        }
    }, [user, isLoading]);

    const loadGameState = async () => {
        try {
            const state = await api.getGameState();
            setGameState(state);
        } catch (err) {
            console.error('加载游戏状态失败:', err);
        }
    };

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
            <header className="bg-gray-800 border-b border-gray-700 p-3">
                <div className="max-w-7xl mx-auto flex justify-between items-center">
                    <div className="flex items-center gap-4">
                        <h1 className="text-xl font-bold text-yellow-400">斗罗大陆</h1>
                        <span className="text-gray-400">|</span>
                        <span className="text-gray-300">{user.nickname}</span>
                    </div>
                    
                    {gameState && (
                        <div className="flex items-center gap-4 text-sm">
                            <div className="flex items-center gap-2">
                                <span className="text-yellow-500">💰</span>
                                <span>{gameState.profile.gold.toLocaleString()}</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-purple-500">⚡</span>
                                <span>{gameState.profile.soulPower.toLocaleString()}</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-blue-500">💎</span>
                                <span>{gameState.profile.bossCoin.toLocaleString()}</span>
                            </div>
                        </div>
                    )}

                    <button
                        onClick={() => {
                            logout();
                            router.push('/');
                        }}
                        className="px-4 py-2 bg-red-600 hover:bg-red-700 rounded text-sm"
                    >
                        退出
                    </button>
                </div>
            </header>

            {/* 主内容区域 */}
            <main className="flex-1 max-w-7xl w-full mx-auto p-4">
                {children}
            </main>

            {/* 底部导航栏 */}
            <nav className="bg-gray-800 border-t border-gray-700">
                <div className="max-w-7xl mx-auto">
                    <div className="flex overflow-x-auto">
                        {NAV_ITEMS.map((item) => (
                            <Link
                                key={item.href}
                                href={item.href}
                                className={`flex flex-col items-center py-3 px-4 min-w-[80px] transition-colors ${
                                    pathname === item.href
                                        ? 'text-yellow-400 bg-gray-700'
                                        : 'text-gray-400 hover:text-white hover:bg-gray-700'
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