'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import api, { GameState, BattleResult } from '@/lib/api';

const MAP_NAMES = ['圣魂村', '诺丁城外', '星斗外围', '落日森林', '极北之地', '海神岛', '杀戮之都外域', '神界废墟'];
const REALM_NAMES = ['魂士', '魂师', '大魂师', '魂尊', '魂宗', '魂王', '魂帝', '魂圣', '魂斗罗', '封号斗罗', '极限斗罗', '半神', '神祇', '神王', '至高神王', '创世神'];

export default function GamePage() {
    const { user, isLoading, logout } = useAuth();
    const router = useRouter();
    const [gameState, setGameState] = useState<GameState | null>(null);
    const [battleResult, setBattleResult] = useState<BattleResult | null>(null);
    const [message, setMessage] = useState('');
    const [actionLoading, setActionLoading] = useState(false);

    async function loadGameState() {
        try {
            const state = await api.getGameState();
            setGameState(state);
        } catch (err) {
            console.error('加载游戏状态失败:', err);
        }
    }

    useEffect(() => {
        if (!isLoading && !user) {
            router.push('/');
        }
        if (user) {
            queueMicrotask(loadGameState);
        }
    }, [user, isLoading, router]);

    const handleBattle = async () => {
        setActionLoading(true);
        try {
            const result = await api.battle();
            setBattleResult(result);
            const dropText = result.drops.length > 0 ? `，掉落${result.drops.length}件装备` : '';
            setMessage(result.won
                ? `胜利！击败了${result.monsterName}，获得${result.goldGained}金币、${result.expGained}魂力${dropText}`
                : `战败！被${result.monsterName}击败，回城恢复...`
            );
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '战斗失败');
        } finally {
            setActionLoading(false);
        }
    };

    const handleCultivate = async () => {
        setActionLoading(true);
        try {
            const result = await api.cultivate();
            setMessage(`修炼成功！获得${result.soulPowerGained}魂力，总计${result.totalSoulPower}`);
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '修炼失败');
        } finally {
            setActionLoading(false);
        }
    };

    const handleBreakthrough = async () => {
        setActionLoading(true);
        try {
            const result = await api.breakthrough();
            setMessage(result.message);
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '突破失败');
        } finally {
            setActionLoading(false);
        }
    };

    if (isLoading || !gameState) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <div className="text-xl animate-pulse">加载游戏数据...</div>
            </div>
        );
    }

    const p = gameState.profile;
    const realmName = REALM_NAMES[Math.min(Math.floor((p.level - 1) / 10), REALM_NAMES.length - 1)];
    const mapName = MAP_NAMES[p.currentMapId] || '未知';
    const maxHp = 50 * p.level + 100;
    const hpPercent = Math.round((p.currentHp / maxHp) * 100);
    const breakthroughCost = Math.floor(120 * Math.pow(p.level, 1.55));

    return (
        <div className="min-h-screen bg-gradient-to-b from-gray-900 via-gray-800 to-gray-900">
            {/* 顶部导航 */}
            <header className="bg-gray-800/90 backdrop-blur border-b border-gray-700 px-4 py-3 flex items-center justify-between">
                <h1 className="text-lg font-bold text-yellow-400">斗罗大陆·放置传说</h1>
                <div className="flex items-center gap-4">
                    <span className="text-sm text-gray-300">{user?.nickname}</span>
                    <button onClick={logout} className="text-sm text-red-400 hover:text-red-300">退出</button>
                </div>
            </header>

            <div className="max-w-4xl mx-auto p-4 space-y-4">
                {/* 玩家状态栏 */}
                <div className="bg-gray-800/80 rounded-xl p-4 border border-gray-700">
                    <div className="flex items-center justify-between mb-3">
                        <div>
                            <span className="text-yellow-400 font-bold text-lg">{realmName}</span>
                            <span className="text-gray-400 ml-2">Lv.{p.level}</span>
                            {p.prestigeCount > 0 && <span className="text-purple-400 ml-2">{p.prestigeCount}转</span>}
                        </div>
                        <div className="text-sm text-gray-400">
                            {p.martialSoulName ? `武魂: ${p.martialSoulName}` : '未觉醒武魂'}
                        </div>
                    </div>

                    {/* HP条 */}
                    <div className="mb-2">
                        <div className="flex justify-between text-xs text-gray-400 mb-1">
                            <span>HP</span>
                            <span>{p.currentHp}/{maxHp}</span>
                        </div>
                        <div className="h-3 bg-gray-700 rounded-full overflow-hidden">
                            <div className="h-full bg-gradient-to-r from-red-600 to-red-400 rounded-full transition-all" style={{ width: `${hpPercent}%` }} />
                        </div>
                    </div>

                    {/* 资源 */}
                    <div className="grid grid-cols-3 gap-4 text-center text-sm">
                        <div className="bg-gray-700/50 rounded-lg p-2">
                            <div className="text-yellow-400 font-bold">{p.gold.toLocaleString()}</div>
                            <div className="text-xs text-gray-400">金币</div>
                        </div>
                        <div className="bg-gray-700/50 rounded-lg p-2">
                            <div className="text-blue-400 font-bold">{p.soulPower.toLocaleString()}</div>
                            <div className="text-xs text-gray-400">魂力</div>
                        </div>
                        <div className="bg-gray-700/50 rounded-lg p-2">
                            <div className="text-purple-400 font-bold">{p.bossCoin.toLocaleString()}</div>
                            <div className="text-xs text-gray-400">Boss币</div>
                        </div>
                    </div>
                </div>

                {/* 战斗区域 */}
                <div className="bg-gray-800/80 rounded-xl p-4 border border-gray-700">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <h2 className="text-lg font-bold text-orange-400">{mapName}</h2>
                            <p className="text-sm text-gray-400">第 {p.currentStage}/15 层</p>
                        </div>
                        <div className="text-right text-sm text-gray-400">
                            <div>胜场: {p.totalBattleWins} | 败场: {p.totalBattleLosses}</div>
                            <div>图鉴击杀: {p.codexKills}</div>
                        </div>
                    </div>

                    {/* 战斗消息 */}
                    {message && (
                        <div className={`mb-4 p-3 rounded-lg text-sm ${
                            message.includes('胜利') || message.includes('成功')
                                ? 'bg-green-500/20 border border-green-500/50 text-green-300'
                                : message.includes('战败') || message.includes('失败')
                                    ? 'bg-red-500/20 border border-red-500/50 text-red-300'
                                    : 'bg-blue-500/20 border border-blue-500/50 text-blue-300'
                        }`}>
                            {message}
                        </div>
                    )}

                    {/* 战斗结果 */}
                    {battleResult && (
                        <div className={`mb-4 p-4 rounded-lg border ${
                            battleResult.won ? 'bg-green-900/30 border-green-600' : 'bg-red-900/30 border-red-600'
                        }`}>
                            <div className="font-bold mb-2">
                                {battleResult.won ? '胜利！' : '战败！'} vs {battleResult.monsterName}
                            </div>
                            <div className="text-sm text-gray-300">
                                回合数: {battleResult.rounds} |
                                {battleResult.won && ` +${battleResult.goldGained}金币 +${battleResult.expGained}魂力`}
                                {battleResult.drops.length > 0 && ` 掉落${battleResult.drops.length}件装备`}
                            </div>
                        </div>
                    )}

                    {/* 操作按钮 */}
                    <div className="grid grid-cols-3 gap-3">
                        <button
                            onClick={handleBattle}
                            disabled={actionLoading}
                            className="py-3 bg-gradient-to-r from-red-600 to-orange-600 hover:from-red-500 hover:to-orange-500 rounded-lg font-bold transition disabled:opacity-50"
                        >
                            {actionLoading ? '战斗中...' : '战斗'}
                        </button>
                        <button
                            onClick={handleCultivate}
                            disabled={actionLoading}
                            className="py-3 bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-500 hover:to-cyan-500 rounded-lg font-bold transition disabled:opacity-50"
                        >
                            修炼
                        </button>
                        <button
                            onClick={handleBreakthrough}
                            disabled={actionLoading}
                            className="py-3 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 rounded-lg font-bold transition disabled:opacity-50"
                        >
                            突破
                        </button>
                    </div>
                    <div className="text-center text-xs text-gray-500 mt-2">
                        突破需要 {breakthroughCost} 魂力 (当前: {p.soulPower})
                    </div>
                </div>

                {/* 快捷导航 */}
                <div className="grid grid-cols-4 gap-3">
                    <button onClick={() => router.push('/game/equipment')} className="bg-gray-800/80 rounded-xl p-4 border border-gray-700 hover:border-yellow-500 transition text-center">
                        <div className="text-2xl mb-1">⚔️</div>
                        <div className="text-sm text-gray-300">装备</div>
                    </button>
                    <button onClick={() => router.push('/game/shop')} className="bg-gray-800/80 rounded-xl p-4 border border-gray-700 hover:border-yellow-500 transition text-center">
                        <div className="text-2xl mb-1">🏪</div>
                        <div className="text-sm text-gray-300">商店</div>
                    </button>
                    <button onClick={() => router.push('/game/tower')} className="bg-gray-800/80 rounded-xl p-4 border border-gray-700 hover:border-yellow-500 transition text-center">
                        <div className="text-2xl mb-1">🗼</div>
                        <div className="text-sm text-gray-300">杀戮之都</div>
                    </button>
                    <button onClick={() => router.push('/game/social/rank')} className="bg-gray-800/80 rounded-xl p-4 border border-gray-700 hover:border-yellow-500 transition text-center">
                        <div className="text-2xl mb-1">🏆</div>
                        <div className="text-sm text-gray-300">排行榜</div>
                    </button>
                </div>
            </div>
        </div>
    );
}
