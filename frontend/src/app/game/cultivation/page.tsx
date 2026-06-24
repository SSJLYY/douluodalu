'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import api, { GameState, CultivateResult, BreakthroughResult } from '@/lib/api';

const REALM_NAMES = [
    '魂士', '魂师', '大魂师', '魂尊', '魂宗', '魂王', '魂帝', '魂圣',
    '魂斗罗', '封号斗罗', '极限斗罗', '半神', '神祇', '神王', '至高神王', '创世神'
];

export default function CultivationPage() {
    const { user } = useAuth();
    const [gameState, setGameState] = useState<GameState | null>(null);
    const [cultivateResult, setCultivateResult] = useState<CultivateResult | null>(null);
    const [breakthroughResult, setBreakthroughResult] = useState<BreakthroughResult | null>(null);
    const [message, setMessage] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        loadGameState();
    }, []);

    const loadGameState = async () => {
        try {
            const state = await api.getGameState();
            setGameState(state);
        } catch (err) {
            console.error('加载游戏状态失败:', err);
        }
    };

    const handleCultivate = async () => {
        setLoading(true);
        try {
            const result = await api.cultivate();
            setCultivateResult(result);
            setMessage(`修炼成功！获得 ${result.soulPowerGained} 魂力`);
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '修炼失败');
        } finally {
            setLoading(false);
        }
    };

    const handleBreakthrough = async () => {
        setLoading(true);
        try {
            const result = await api.breakthrough();
            setBreakthroughResult(result);
            if (result.success) {
                setMessage(`突破成功！提升至 ${REALM_NAMES[result.newLevel - 1] || '未知境界'}`);
            } else {
                setMessage('突破失败，魂力不足');
            }
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '突破失败');
        } finally {
            setLoading(false);
        }
    };

    if (!gameState) {
        return <div className="text-center py-8">加载中...</div>;
    }

    const currentRealm = REALM_NAMES[gameState.profile.level - 1] || '未知境界';
    const nextRealm = REALM_NAMES[gameState.profile.level] || '已至巅峰';
    const soulPowerNeeded = gameState.profile.level * 1000; // 简化的突破需求
    const progress = Math.min(100, (gameState.profile.soulPower / soulPowerNeeded) * 100);

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">修炼</h1>

            {/* 当前境界 */}
            <div className="bg-gray-800 rounded-lg p-6">
                <h2 className="text-lg font-semibold mb-4">当前境界</h2>
                <div className="text-center">
                    <div className="text-4xl font-bold text-yellow-500 mb-2">{currentRealm}</div>
                    <div className="text-gray-400">等级 {gameState.profile.level}</div>
                </div>
            </div>

            {/* 修炼进度 */}
            <div className="bg-gray-800 rounded-lg p-6">
                <h2 className="text-lg font-semibold mb-4">修炼进度</h2>
                <div className="space-y-4">
                    <div>
                        <div className="flex justify-between text-sm mb-1">
                            <span>魂力</span>
                            <span>{gameState.profile.soulPower.toLocaleString()} / {soulPowerNeeded.toLocaleString()}</span>
                        </div>
                        <div className="w-full bg-gray-700 rounded-full h-4">
                            <div 
                                className="bg-purple-600 h-4 rounded-full transition-all"
                                style={{ width: `${progress}%` }}
                            />
                        </div>
                    </div>
                    <div className="text-sm text-gray-400">
                        下一境界: {nextRealm}
                    </div>
                </div>
            </div>

            {/* 操作按钮 */}
            <div className="grid grid-cols-2 gap-4">
                <button
                    onClick={handleCultivate}
                    disabled={loading}
                    className="py-4 bg-purple-600 hover:bg-purple-700 disabled:bg-gray-600 rounded-lg text-lg font-semibold transition-colors"
                >
                    {loading ? '修炼中...' : '修炼'}
                </button>
                <button
                    onClick={handleBreakthrough}
                    disabled={loading || gameState.profile.soulPower < soulPowerNeeded}
                    className="py-4 bg-yellow-600 hover:bg-yellow-700 disabled:bg-gray-600 rounded-lg text-lg font-semibold transition-colors"
                >
                    {loading ? '突破中...' : '突破'}
                </button>
            </div>

            {/* 消息提示 */}
            {message && (
                <div className="bg-gray-700 rounded-lg p-4 text-center">
                    {message}
                </div>
            )}

            {/* 修炼结果 */}
            {cultivateResult && (
                <div className="bg-gray-800 rounded-lg p-4">
                    <h3 className="font-semibold mb-2">修炼结果</h3>
                    <div>获得魂力: {cultivateResult.soulPowerGained}</div>
                </div>
            )}

            {/* 突破结果 */}
            {breakthroughResult && (
                <div className="bg-gray-800 rounded-lg p-4">
                    <h3 className="font-semibold mb-2">突破结果</h3>
                    <div>结果: {breakthroughResult.success ? '成功' : '失败'}</div>
                    {breakthroughResult.success && (
                        <div>新境界: {REALM_NAMES[breakthroughResult.newLevel - 1] || '未知'}</div>
                    )}
                </div>
            )}

            {/* 修炼提示 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">修炼提示</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• 修炼可获得魂力，用于突破境界</li>
                    <li>• 突破需要足够的魂力</li>
                    <li>• 境界越高，修炼获得的魂力越多</li>
                    <li>• 突破失败不会损失魂力</li>
                </ul>
            </div>
        </div>
    );
}