'use client';

import { useEffect, useState } from 'react';
import api, { GameState } from '@/lib/api';

const TALENT_BRANCHES = [
    { id: 'WAR_GOD', name: '战神', icon: '⚔️', description: '提升战斗能力' },
    { id: 'SOUL_MASTER', name: '魂师', icon: '🔮', description: '提升魂力修炼' },
    { id: 'WEALTH', name: '财富', icon: '💰', description: '提升金币获取' },
    { id: 'DIVINE', name: '神圣', icon: '✨', description: '提升特殊能力' },
];

export default function TalentPage() {
    const [gameState, setGameState] = useState<GameState | null>(null);
    const [message, setMessage] = useState('');
    const [loading, setLoading] = useState(false);

    async function loadGameState() {
        try {
            const state = await api.getGameState();
            setGameState(state);
        } catch (err) {
            console.error('加载游戏状态失败:', err);
        }
    }

    useEffect(() => {
        queueMicrotask(loadGameState);
    }, []);

    const handleUpgradeTalent = async (branch: string) => {
        setLoading(true);
        try {
            await api.upgradeTalent(branch);
            setMessage('天赋升级成功！');
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '升级失败');
        } finally {
            setLoading(false);
        }
    };

    if (!gameState) {
        return <div className="text-center py-8">加载中...</div>;
    }

    const talentPoints = gameState.profile.talentPoints;
    const talents = gameState.talents;

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">天赋</h1>

            {/* 天赋点数 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <div className="flex justify-between items-center">
                    <h2 className="text-lg font-semibold">天赋点数</h2>
                    <div className="text-2xl font-bold text-yellow-500">{talentPoints}</div>
                </div>
                <p className="text-sm text-gray-400 mt-2">
                    通过转生获得天赋点数，用于强化角色能力
                </p>
            </div>

            {/* 天赋分支 */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {TALENT_BRANCHES.map((branch) => {
                    const currentLevel = talents[branch.id] || 0;
                    const maxLevel = 10;
                    const canUpgrade = talentPoints > 0 && currentLevel < maxLevel;

                    return (
                        <div key={branch.id} className="bg-gray-800 rounded-lg p-4">
                            <div className="flex items-center gap-3 mb-3">
                                <span className="text-3xl">{branch.icon}</span>
                                <div>
                                    <h3 className="font-semibold text-lg">{branch.name}</h3>
                                    <p className="text-sm text-gray-400">{branch.description}</p>
                                </div>
                            </div>
                            
                            <div className="mb-3">
                                <div className="flex justify-between text-sm mb-1">
                                    <span>等级</span>
                                    <span>{currentLevel} / {maxLevel}</span>
                                </div>
                                <div className="w-full bg-gray-700 rounded-full h-2">
                                    <div 
                                        className="bg-yellow-500 h-2 rounded-full transition-all"
                                        style={{ width: `${(currentLevel / maxLevel) * 100}%` }}
                                    />
                                </div>
                            </div>

                            <button
                                onClick={() => handleUpgradeTalent(branch.id)}
                                disabled={loading || !canUpgrade}
                                className="w-full py-2 bg-yellow-600 hover:bg-yellow-700 disabled:bg-gray-600 rounded text-sm transition-colors"
                            >
                                {loading ? '升级中...' : 
                                 currentLevel >= maxLevel ? '已满级' :
                                 talentPoints <= 0 ? '无天赋点' : '升级'}
                            </button>
                        </div>
                    );
                })}
            </div>

            {/* 消息提示 */}
            {message && (
                <div className="bg-gray-700 rounded-lg p-4 text-center">
                    {message}
                </div>
            )}

            {/* 天赋说明 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">天赋说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• 天赋点数通过转生获得</li>
                    <li>• 每次转生获得1点天赋点</li>
                    <li>• 天赋等级越高，效果越强</li>
                    <li>• 天赋点数不会消耗，可自由分配</li>
                </ul>
            </div>
        </div>
    );
}
