'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import api, { BattleResult, OfflineReward, PowerDetail } from '@/lib/api';
import { useGameData } from '@/lib/hooks';
import { BootState } from '@/components/StateViews';

const MAP_NAMES = ['圣魂村', '诺丁城外', '星斗外围', '落日森林', '极北之地', '海神岛', '杀戮之都外域', '神界废墟'];
const REALM_NAMES = ['魂士', '魂师', '大魂师', '魂尊', '魂宗', '魂王', '魂帝', '魂圣', '魂斗罗', '封号斗罗', '极限斗罗', '半神', '神祇', '神王', '至高神王', '创世神'];

/**
 * 任务#23：战力明细可折叠面板（默认收起）。
 * 后端 PowerDetailDto 保证 basePower+ringPower+bonePower+corePower == 总战力（拆分求和恒等），
 * 每行显示攻击/生命贡献与占总战力百分比；颜色全部走主题调色板变量，亮暗两色主题均可读。
 */
function PowerDetailPanel({ power, detail }: { power: number; detail: PowerDetail }) {
    const [open, setOpen] = useState(false);
    const rows = [
        { key: 'base', icon: '🛡️', label: '基础（等级+攻击）', atk: detail.baseAtk, hp: detail.baseHp, share: detail.basePower, bar: 'bg-gray-500' },
        { key: 'ring', icon: '💜', label: '魂环', atk: detail.ringAtk, hp: detail.ringHp, share: detail.ringPower, bar: 'bg-blue-500' },
        { key: 'bone', icon: '🦴', label: '魂骨', atk: detail.boneAtk, hp: detail.boneHp, share: detail.bonePower, bar: 'bg-purple-500' },
        { key: 'core', icon: '🔮', label: '魂核', atk: detail.coreAtk, hp: detail.coreHp, share: detail.corePower, bar: 'bg-yellow-500' },
    ];
    return (
        <div className="dl-fade-up bg-surface/80 rounded-xl border border-line" data-testid="power-detail-panel">
            <button
                type="button"
                aria-expanded={open}
                onClick={() => setOpen((v) => !v)}
                className="w-full px-4 py-3 flex items-center justify-between text-left hover:bg-surface-soft/60 transition rounded-xl"
            >
                <span className="text-sm font-semibold text-orange-400">⚔️ 战力明细</span>
                <span className="flex items-center gap-2 text-sm text-gray-400 min-w-0">
                    <span className="hidden sm:inline">总战力</span>
                    <span key={power} className="dl-value-flash font-bold text-orange-400 tabular-nums">{power.toLocaleString()}</span>
                    <span className={`inline-block transition-transform duration-300 ${open ? 'rotate-180' : ''}`} aria-hidden>▾</span>
                </span>
            </button>
            {open && (
                <div className="px-4 pb-4 space-y-3">
                    {rows.map((r) => {
                        const pct = power > 0 ? Math.min(100, (r.share / power) * 100) : 0;
                        return (
                            <div key={r.key} data-power-row={r.key}>
                                <div className="flex flex-wrap items-baseline justify-between gap-x-2 text-xs mb-1">
                                    <span className="text-gray-300 min-w-0">
                                        {r.icon} {r.label}
                                        <span className="text-gray-500 ml-2">攻 +{r.atk.toLocaleString()} · 生 +{r.hp.toLocaleString()}</span>
                                    </span>
                                    <span className="text-gray-400 tabular-nums shrink-0">
                                        {r.share.toLocaleString()}（{pct.toFixed(1)}%）
                                    </span>
                                </div>
                                <div
                                    className="h-2 bg-gray-700 rounded-full overflow-hidden"
                                    role="progressbar"
                                    aria-valuemin={0}
                                    aria-valuemax={Math.max(power, 1)}
                                    aria-valuenow={r.share}
                                    aria-label={`${r.label}战力贡献`}
                                >
                                    <div className={`h-full ${r.bar} rounded-full transition-all duration-700 ease-out`} style={{ width: `${pct}%` }} />
                                </div>
                            </div>
                        );
                    })}
                    <p className="text-[11px] text-gray-500 leading-relaxed">
                        战力 = 基础 + 装备攻击加成 + 装备生命÷10；四行求和恒等于总战力（后端同源拆分）。
                    </p>
                </div>
            )}
        </div>
    );
}

// 模块级标记：跨组件StrictMode双挂载/客户端导航只领取一次离线收益（刷新页面会重新领取，符合放置游戏惯例）
let offlineClaimAttempted = false;

export default function GamePage() {
    const { user, isLoading, logout } = useAuth();
    const router = useRouter();
    const { gameState, message, setMessage, actionLoading, loadError, runAction, refresh } = useGameData();
    const [battleResult, setBattleResult] = useState<BattleResult | null>(null);
    const [offline, setOffline] = useState<OfflineReward | null>(null);

    // 进入主页领取一次离线收益：有实际产出才弹窗，0 收益静默关闭
    useEffect(() => {
        if (offlineClaimAttempted) return;
        offlineClaimAttempted = true;
        api.claimOfflineReward()
            .then((reward) => {
                if (reward.goldGained > 0 || reward.expGained > 0) setOffline(reward);
            })
            .catch(() => undefined);
    }, []);

    const dismissOffline = () => {
        setOffline(null);
        void refresh();
    };

    const handleBattle = () => runAction(
        () => api.battle(),
        (result) => {
            setBattleResult(result);
            const dropText = result.drops.length > 0 ? `，掉落${result.drops.length}件装备` : '';
            setMessage(result.won
                ? `胜利！击败了${result.monsterName}，获得${result.goldGained}金币、${result.expGained}魂力${dropText}`
                : `战败！被${result.monsterName}击败，回城恢复...`
            );
        },
        '战斗失败',
    );

    const handleCultivate = () => runAction(
        () => api.cultivate(),
        (result) => setMessage(`修炼成功！获得${result.soulPowerGained}魂力，总计${result.totalSoulPower}`),
        '修炼失败',
    );

    const handleBreakthrough = () => runAction(
        () => api.breakthrough(),
        (result) => setMessage(result.message),
        '突破失败',
    );

    if (isLoading || !gameState) {
        // 首载骨架 / 加载失败错误重试（统一三态，复用 StateViews）
        return (
            <div className="min-h-screen bg-gradient-to-b from-gray-900 via-gray-800 to-gray-900">
                <div className="max-w-4xl mx-auto p-4">
                    <BootState error={isLoading ? '' : loadError} onRetry={refresh} rows={7} />
                </div>
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
            {/* 顶部导航：窄屏下标题截断、按钮触控高度 */}
            <header className="bg-surface/90 backdrop-blur border-b border-line px-4 py-3 flex items-center justify-between gap-3">
                <h1 className="text-base sm:text-lg font-bold text-accent truncate">斗罗大陆·放置传说</h1>
                <div className="flex items-center gap-3 shrink-0">
                    <span className="text-sm text-gray-300 truncate max-w-[8rem]">{user?.nickname}</span>
                    <button onClick={logout} className="text-sm min-h-11 px-2 text-red-400 hover:text-red-300">退出</button>
                </div>
            </header>

            <div className="max-w-4xl mx-auto p-4 space-y-4">
                {/* 玩家状态栏 */}
                <div className="dl-fade-up bg-surface/80 rounded-xl p-4 border border-line">
                    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-3">
                        <div className="min-w-0">
                            <span className="text-yellow-400 font-bold text-lg">{realmName}</span>
                            <span className="text-gray-400 ml-2">Lv.{p.level}</span>
                            {p.prestigeCount > 0 && <span className="text-purple-400 ml-2">{p.prestigeCount}转</span>}
                            {/* 任务#21：主页头部战力（变化时重挂载触发 flash 动画） */}
                            <span className="ml-2 text-sm font-semibold text-orange-400">
                                ⚔️ 战力 <span key={gameState.power} className="dl-value-flash inline-block tabular-nums">{gameState.power.toLocaleString()}</span>
                            </span>
                        </div>
                        <div className="text-sm text-gray-400 min-w-0 truncate max-w-full">
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
                            <div className="h-full bg-gradient-to-r from-red-600 to-red-400 rounded-full transition-all duration-700 ease-out" style={{ width: `${hpPercent}%` }} />
                        </div>
                    </div>

                    {/* 资源 */}
                    <div className="grid grid-cols-3 gap-4 text-center text-sm">
                        <div className="bg-gray-700/50 rounded-lg p-2">
                            <div key={p.gold} className="dl-value-flash text-yellow-400 font-bold">{p.gold.toLocaleString()}</div>
                            <div className="text-xs text-gray-400">金币</div>
                        </div>
                        <div className="bg-gray-700/50 rounded-lg p-2">
                            <div key={p.soulPower} className="dl-value-flash text-blue-400 font-bold">{p.soulPower.toLocaleString()}</div>
                            <div className="text-xs text-gray-400">魂力</div>
                        </div>
                        <div className="bg-gray-700/50 rounded-lg p-2">
                            <div key={p.bossCoin} className="dl-value-flash text-purple-400 font-bold">{p.bossCoin.toLocaleString()}</div>
                            <div className="text-xs text-gray-400">Boss币</div>
                        </div>
                    </div>
                </div>

                {/* 任务#23：战力明细（可折叠，默认收起；放在状态栏下方，与 ⚔️ 战力 同一视觉区） */}
                <PowerDetailPanel power={gameState.power} detail={gameState.powerDetail} />

                {/* 战斗区域 */}
                <div className="dl-fade-up [animation-delay:80ms] bg-surface/80 rounded-xl p-4 border border-line">
                    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-4">
                        <div className="min-w-0">
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
                        <div key={message} className={`dl-slide-in mb-4 p-3 rounded-lg text-sm ${
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
                        <div key={`${battleResult.monsterName}-${battleResult.rounds}`} className={`dl-fade-up mb-4 p-4 rounded-lg border ${
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
                            {battleResult.battleLog && battleResult.battleLog.length > 0 && (
                                <details className="mt-2 text-xs text-gray-400">
                                    <summary className="cursor-pointer select-none">回合日志</summary>
                                    <ul className="mt-1 space-y-0.5">
                                        {battleResult.battleLog.map((r) => (
                                            <li key={r.round}>
                                                第{r.round}回合: 我输出{r.playerDamage}，受{r.monsterDamage} |
                                                我HP {r.playerHpBefore}→{r.playerHpAfter}，敌HP {r.monsterHpBefore}→{r.monsterHpAfter}
                                            </li>
                                        ))}
                                    </ul>
                                </details>
                            )}
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
                <div className="dl-fade-up [animation-delay:160ms] grid grid-cols-4 gap-3">
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
                    <button onClick={() => router.push('/game/social/rank')} className="bg-surface/80 rounded-xl p-4 border border-line hover:border-yellow-500 hover:-translate-y-0.5 transition text-center">
                        <div className="text-2xl mb-1">🏆</div>
                        <div className="text-sm text-gray-300">排行榜</div>
                    </button>
                </div>
            </div>

            {/* 离线收益弹窗 */}
            {offline && (
                <div className="dl-fade-in fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true">
                    <div className="dl-pop bg-surface border border-yellow-500/50 rounded-2xl p-6 max-w-sm w-full shadow-2xl">
                        <h3 className="text-lg font-bold text-yellow-400 mb-3">欢迎回来！</h3>
                        <p className="text-sm text-gray-400 mb-4">
                            你离开了 {Math.floor(offline.offlineSeconds / 60)} 分钟，放置收益已入账：
                        </p>
                        <ul className="text-sm space-y-1 mb-4">
                            <li className="text-yellow-300">金币 +{offline.goldGained.toLocaleString()}</li>
                            <li className="text-blue-300">魂力 +{offline.expGained.toLocaleString()}</li>
                            {offline.battleWins > 0 && <li className="text-green-300">自动胜利 {offline.battleWins} 场</li>}
                        </ul>
                        <button
                            onClick={dismissOffline}
                            className="w-full py-2 bg-gradient-to-r from-yellow-600 to-orange-600 hover:from-yellow-500 hover:to-orange-500 rounded-lg font-bold transition"
                        >
                            收下
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
