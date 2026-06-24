'use client';

import { useEffect, useState } from 'react';
import api, { GameState, TowerBattleResult } from '@/lib/api';

export default function TowerPage() {
    const [gameState, setGameState] = useState<GameState | null>(null);
    const [battleResult, setBattleResult] = useState<TowerBattleResult | null>(null);
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

    const handleChallenge = async () => {
        setLoading(true);
        try {
            const result = await api.towerBattle();
            setBattleResult(result);
            if (result.won) {
                setMessage(`挑战成功！击败了${result.monsterName}，登上第${result.towerFloor}层，获得${result.goldGained}金币、${result.bossCoinGained}Boss币`);
            } else {
                setMessage(`挑战失败！被${result.monsterName}击败`);
            }
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '挑战失败');
        } finally {
            setLoading(false);
        }
    };

    if (!gameState) {
        return <div className="text-center py-8">加载中...</div>;
    }

    const currentFloor = gameState.profile.towerFloor;
    const killingIntent = gameState.profile.killingIntent;

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-red-400">杀戮之都</h1>

            {/* 当前进度 */}
            <div className="bg-gray-800 rounded-lg p-6">
                <h2 className="text-lg font-semibold mb-4">当前进度</h2>
                <div className="grid grid-cols-2 gap-4">
                    <div className="text-center">
                        <div className="text-4xl font-bold text-red-500">{currentFloor}</div>
                        <div className="text-gray-400">当前层数</div>
                    </div>
                    <div className="text-center">
                        <div className="text-4xl font-bold text-orange-500">{killingIntent}</div>
                        <div className="text-gray-400">杀戮值</div>
                    </div>
                </div>
            </div>

            {/* 挑战区域 */}
            <div className="bg-gray-800 rounded-lg p-6">
                <h2 className="text-lg font-semibold mb-4">挑战第 {currentFloor + 1} 层</h2>
                <div className="text-center">
                    <div className="text-6xl mb-4">🏰</div>
                    <p className="text-gray-400 mb-4">
                        击败守卫可获得丰厚奖励，层数越高奖励越丰富
                    </p>
                    <button
                        onClick={handleChallenge}
                        disabled={loading}
                        className="px-8 py-4 bg-red-600 hover:bg-red-700 disabled:bg-gray-600 rounded-lg text-lg font-semibold transition-colors"
                    >
                        {loading ? '挑战中...' : '开始挑战'}
                    </button>
                </div>
            </div>

            {/* 战斗结果 */}
            {battleResult && (
                <div className="bg-gray-800 rounded-lg p-4">
                    <h3 className="font-semibold mb-2">战斗结果</h3>
                    <div className="space-y-2">
                        <div>对手: {battleResult.monsterName}</div>
                        <div>回合数: {battleResult.rounds}</div>
                        <div>结果: {battleResult.won ? '胜利' : '失败'}</div>
                        {battleResult.won && (
                            <>
                                <div>获得金币: {battleResult.goldGained}</div>
                                <div>获得魂力: {battleResult.expGained}</div>
                            </>
                        )}
                    </div>
                </div>
            )}

            {/* 消息提示 */}
            {message && (
                <div className="bg-gray-700 rounded-lg p-4 text-center">
                    {message}
                </div>
            )}

            {/* 杀戮之都说明 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">杀戮之都说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• 逐层挑战，难度递增</li>
                    <li>• 每层击败守卫可获得金币和魂力</li>
                    <li>• 杀戮值影响掉落品质</li>
                    <li>• 挑战失败不会损失进度</li>
                    <li>• 层数越高，奖励越丰厚</li>
                </ul>
            </div>
        </div>
    );
}
