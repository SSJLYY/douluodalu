'use client';

import { useCallback, useEffect, useState } from 'react';
import api, { DungeonFightResult, DungeonState, DungeonSweepResult } from '@/lib/api';
import { useGameData } from '@/contexts/GameDataContext';
import { BootState, ErrorPanel, SkeletonCards } from '@/components/StateViews';
import BattleReplay from '@/components/BattleReplay';
import { dungeonDifficultyClassName } from '@/lib/dungeon';

/**
 * 每日副本页（第二十九轮，设计文档 §8）：五大难度每天一次挑战机会 + 通关后扫荡。
 *  - 首屏门控照 achievements/tower 页三态模式：gameState 未落地 → BootState（错误重试/骨架）；
 *    副本状态走独立端点 /api/dungeon/state，在其后拉取（loadError 与副本错误分开展示）；
 *  - 卡片四态：锁定（显示解锁转数）/ 可挑战 / 可挑战+可扫荡 / 今日已挑战（胜局另显「已通关」）；
 *    奖励预览的金币为后端已乘转生倍率的值，直接展示（前端不镜像倍率公式）；
 *  - 战斗结果复用 BattleReplay 回放（battleLog 与 battle/tower 同构）；扫荡结果走紧凑面板；
 *  - 375px：单列卡片 + 徽章 shrink-0 + 按钮行 flex（每钮 flex-1），无水平溢出。
 */
export default function DungeonPage() {
    const { gameState, message, setMessage, actionLoading: loading, loadError, runAction, refresh } = useGameData();
    const [dungeon, setDungeon] = useState<DungeonState | null>(null);
    const [dungeonError, setDungeonError] = useState('');
    const [fightResult, setFightResult] = useState<DungeonFightResult | null>(null);
    const [sweepResult, setSweepResult] = useState<DungeonSweepResult | null>(null);

    const loadDungeon = useCallback(async () => {
        try {
            setDungeon(await api.getDungeonState());
            setDungeonError('');
        } catch (err) {
            setDungeonError(err instanceof Error && err.message ? err.message : '加载副本状态失败');
        }
    }, []);

    // gameState 就绪（= 已登录且顶栏数据可用）后再拉副本状态；轮询/WS 刷新 gameState 时同步校准
    useEffect(() => {
        if (gameState) void loadDungeon();
    }, [gameState, loadDungeon]);

    const handleFight = (tier: number) => runAction(
        () => api.fightDungeon(tier),
        (r) => {
            setFightResult(r);
            setSweepResult(null);
            if (r.won) {
                setMessage(`挑战成功！击败了${r.monsterName}，获得${r.goldGained.toLocaleString()}金币、${r.killingGained}杀气`);
            } else {
                setMessage(`挑战失败！被${r.monsterName}击败，今日机会已用`);
            }
            void loadDungeon();
        },
        '挑战失败',
    );

    const handleSweep = (tier: number) => runAction(
        () => api.sweepDungeon(tier),
        (r) => {
            setSweepResult(r);
            setFightResult(null);
            setMessage(`扫荡成功！获得${r.goldGained.toLocaleString()}金币、${r.killingGained}杀气，消耗${r.soulPowerSpent}魂力`);
            void loadDungeon();
        },
        '扫荡失败',
    );

    if (!gameState) {
        return (
            <div className="space-y-6">
                <h1 className="text-2xl font-bold text-yellow-400">每日副本</h1>
                <BootState error={loadError} onRetry={refresh} rows={5} />
            </div>
        );
    }

    const soulPower = gameState.profile.soulPower;
    const prestigeCount = gameState.profile.prestigeCount;

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">每日副本</h1>

            {/* 副本状态加载失败（gameState 正常时）：错误重试面板 */}
            {dungeonError ? (
                <ErrorPanel message={dungeonError} onRetry={() => void loadDungeon()} testId="dungeon-error" />
            ) : !dungeon ? (
                <SkeletonCards count={5} testId="dungeon-skeleton" />
            ) : (
                <div className="space-y-4">
                    {/* 当日节奏横幅：一天一次机会（战斗胜/败、扫荡都算已挑战） */}
                    <div className="bg-gray-800 rounded-lg p-4 flex items-center justify-between gap-2" data-testid="dungeon-daily-banner">
                        <div className="text-sm text-gray-300 min-w-0">
                            今日机会：{dungeon.challengedToday ? '已用（明天 0 点重置）' : '未用'} · 当前 {prestigeCount} 转
                        </div>
                        {dungeon.tierCompleted >= 0 && (
                            <span className="shrink-0 bg-yellow-600/90 px-2 py-1 rounded text-xs whitespace-nowrap">
                                今日已通关：{dungeon.tiers[dungeon.tierCompleted]?.name}
                            </span>
                        )}
                    </div>

                    {/* 五难度卡（单列，375px 零溢出） */}
                    {dungeon.tiers.map((t) => {
                        const canSweep = t.sweepable && soulPower >= t.sweepSoulPowerCost;
                        return (
                            <div key={t.tier} className="bg-gray-800 rounded-lg p-4 border border-line">
                                <div className="flex items-center justify-between gap-2">
                                    <h3 className="font-semibold text-lg min-w-0 truncate">
                                        {t.name}
                                        <span className="text-gray-400 text-sm font-normal ml-1">· {t.bossName}</span>
                                    </h3>
                                    <span className={`${dungeonDifficultyClassName(t.difficultyName)} px-2 py-1 rounded text-xs text-white shrink-0`}>
                                        {t.difficultyName}
                                    </span>
                                </div>

                                {/* Boss 强度与奖励预览（金币为后端已乘转生倍率的值） */}
                                <div className="text-sm text-gray-400 mt-2 break-words">
                                    Boss 强度：HP×{t.hpMult} · 攻击×{t.atkMult}
                                </div>
                                <div className="text-sm mt-1 flex flex-wrap gap-x-4 gap-y-1">
                                    <span className="text-yellow-500">💰 {t.goldReward.toLocaleString()} 金币</span>
                                    <span className="text-red-400">🩸 +{t.killingReward} 杀气</span>
                                    <span className="text-purple-400">📦 tier {t.dropTier} 掉落</span>
                                </div>

                                {/* 状态与操作：锁定 / 可挑战(+扫荡) / 今日已挑战 */}
                                <div className="mt-3 flex flex-wrap items-center gap-2">
                                    {!t.unlocked ? (
                                        <div className="flex-1 min-w-0 flex items-center justify-center gap-2 bg-gray-700/60 text-gray-400 rounded-lg min-h-11 text-sm">
                                            🔒 需要 {t.unlockPrestige} 转解锁
                                        </div>
                                    ) : t.challengedToday ? (
                                        <div className="flex-1 min-w-0 flex items-center justify-center gap-2 bg-gray-700/60 text-gray-400 rounded-lg min-h-11 text-sm">
                                            {t.clearedToday ? '✅ 今日已通关' : '今日已挑战'}
                                        </div>
                                    ) : (
                                        <>
                                            <button
                                                type="button"
                                                data-testid={`dungeon-fight-${t.tier}`}
                                                onClick={() => handleFight(t.tier)}
                                                disabled={loading}
                                                className="flex-1 min-h-11 bg-red-600 hover:bg-red-700 disabled:bg-gray-600 rounded-lg text-sm font-semibold transition-colors"
                                            >
                                                {loading ? '挑战中...' : '挑战'}
                                            </button>
                                            {t.sweepable && (
                                                <button
                                                    type="button"
                                                    data-testid={`dungeon-sweep-${t.tier}`}
                                                    onClick={() => handleSweep(t.tier)}
                                                    disabled={loading || !canSweep}
                                                    title={canSweep ? '' : `魂力不足（需 ${t.sweepSoulPowerCost}，当前 ${soulPower}）`}
                                                    className="flex-1 min-h-11 bg-indigo-600 hover:bg-indigo-500 disabled:bg-gray-600 rounded-lg text-sm font-semibold transition-colors"
                                                >
                                                    扫荡（{t.sweepSoulPowerCost}魂力）
                                                </button>
                                            )}
                                        </>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {/* 挑战结果：胜败横幅 + 奖励 + 回放（battleLog 与 battle/tower 同构，复用 BattleReplay） */}
            {fightResult && (
                <div className="bg-gray-800 rounded-lg p-4" data-testid="dungeon-fight-result">
                    <div className={`rounded-lg p-3 text-center font-semibold ${fightResult.won ? 'bg-green-600/20 text-green-400' : 'bg-red-600/20 text-red-400'}`}>
                        {fightResult.won ? '🏆 挑战成功！' : '💀 挑战失败（失败无奖励）'}
                    </div>
                    <div className="space-y-1 mt-3 text-sm">
                        <div>对手：{fightResult.monsterName}（Boss HP {fightResult.monsterMaxHp.toLocaleString()}）</div>
                        <div>回合数：{fightResult.rounds}</div>
                        {fightResult.won && (
                            <>
                                <div>获得金币：{fightResult.goldGained.toLocaleString()}</div>
                                <div>获得杀气：+{fightResult.killingGained}</div>
                                <div>掉落：{fightResult.drops.length > 0 ? `${fightResult.drops.length} 件装备（详见背包）` : '无'}</div>
                            </>
                        )}
                        {fightResult.message && <div className="text-orange-400">{fightResult.message}</div>}
                    </div>
                    {fightResult.battleLog && fightResult.battleLog.length > 0 ? (
                        <BattleReplay battleLog={fightResult.battleLog} monsterName={fightResult.monsterName} />
                    ) : null}
                </div>
            )}

            {/* 扫荡结果（紧凑面板：不战斗直接拿奖励） */}
            {sweepResult && (
                <div className="bg-gray-800 rounded-lg p-4" data-testid="dungeon-sweep-result">
                    <div className="rounded-lg p-3 text-center font-semibold bg-indigo-600/20 text-indigo-300">
                        ⚡ 扫荡完成（消耗 {sweepResult.soulPowerSpent} 魂力）
                    </div>
                    <div className="space-y-1 mt-3 text-sm">
                        <div>获得金币：{sweepResult.goldGained.toLocaleString()}</div>
                        <div>获得杀气：+{sweepResult.killingGained}</div>
                        <div>掉落：{sweepResult.drops.length > 0 ? `${sweepResult.drops.length} 件装备（详见背包）` : '无'}</div>
                        {sweepResult.message && <div className="text-orange-400">{sweepResult.message}</div>}
                    </div>
                </div>
            )}

            {/* 消息提示 */}
            {message && (
                <div className="bg-gray-700 rounded-lg p-4 text-center break-words">
                    {message}
                </div>
            )}

            {/* 每日副本说明 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">每日副本说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• 每天一次机会：任选一个已解锁难度挑战，胜或败都算已挑战，次日凌晨重置</li>
                    <li>• Boss 强度取你当前推图怪的数值 × 难度倍率，换图/推关后副本难度同步成长</li>
                    <li>• 胜局奖励：金币随转生倍率放大、杀气直加、掉落 tier 档装备（背包满则丢失）</li>
                    <li>• 通关后可扫荡：不战斗直接拿该难度奖励，消耗魂力 50 + 等级×5（与当日机会共用名额）</li>
                    <li>• 难度越高奖励越丰厚，按转数逐级解锁（1~5 转）</li>
                </ul>
            </div>
        </div>
    );
}
