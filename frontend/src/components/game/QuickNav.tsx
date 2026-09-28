'use client';

import { useRouter } from 'next/navigation';

/**
 * 主页快捷导航 6 格，自 game/page.tsx 拆出（第二十九轮 5 格 → 6 格：追加「副本」格）：
 * grid-cols-3（375px 下 3+3 两行，每格约 106px 宽裕容纳图标+四字标签，无孤行格且零水平溢出）。
 */
export default function QuickNav() {
    const router = useRouter();

    return (
        <div className="dl-fade-up [animation-delay:320ms] grid grid-cols-3 gap-3">
            <button onClick={() => router.push('/game/equipment')} className="bg-surface/80 rounded-xl p-4 border border-line hover:border-yellow-500 hover:-translate-y-0.5 transition text-center">
                <div className="text-2xl mb-1">⚔️</div>
                <div className="text-sm text-gray-300">装备</div>
            </button>
            <button onClick={() => router.push('/game/shop')} className="bg-surface/80 rounded-xl p-4 border border-line hover:border-yellow-500 hover:-translate-y-0.5 transition text-center">
                <div className="text-2xl mb-1">🏪</div>
                <div className="text-sm text-gray-300">商店</div>
            </button>
            <button onClick={() => router.push('/game/tower')} className="bg-surface/80 rounded-xl p-4 border border-line hover:border-yellow-500 hover:-translate-y-0.5 transition text-center">
                <div className="text-2xl mb-1">🗼</div>
                <div className="text-sm text-gray-300">杀戮之都</div>
            </button>
            <button data-testid="nav-dungeon" onClick={() => router.push('/game/dungeon')} className="bg-surface/80 rounded-xl p-4 border border-line hover:border-yellow-500 hover:-translate-y-0.5 transition text-center">
                <div className="text-2xl mb-1">🏯</div>
                <div className="text-sm text-gray-300">副本</div>
            </button>
            <button onClick={() => router.push('/game/social/rank')} className="bg-surface/80 rounded-xl p-4 border border-line hover:border-yellow-500 hover:-translate-y-0.5 transition text-center">
                <div className="text-2xl mb-1">🏆</div>
                <div className="text-sm text-gray-300">排行榜</div>
            </button>
            <button data-testid="nav-achievements" onClick={() => router.push('/game/achievements')} className="bg-surface/80 rounded-xl p-4 border border-line hover:border-yellow-500 hover:-translate-y-0.5 transition text-center">
                <div className="text-2xl mb-1" aria-hidden>🏅</div>
                <div className="text-sm text-gray-300">成就</div>
            </button>
        </div>
    );
}
