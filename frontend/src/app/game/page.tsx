'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import api, { BattleResult, OfflineReward, normalizeAchievements } from '@/lib/api';
import { PRESTIGE_MIN_LEVEL, prestigeHint } from '@/lib/prestige';
import { useGameData } from '@/lib/hooks';
import { BootState } from '@/components/StateViews';
import BattleReplay from '@/components/BattleReplay';
import CheckinCard from '@/components/CheckinCard';
import DailyQuestsCard from '@/components/DailyQuestsCard';
import PowerDetailPanel from '@/components/PowerDetailPanel';

const MAP_NAMES = ['圣魂村', '诺丁城外', '星斗外围', '落日森林', '极北之地', '海神岛', '杀戮之都外域', '神界废墟'];
const REALM_NAMES = ['魂士', '魂师', '大魂师', '魂尊', '魂宗', '魂王', '魂帝', '魂圣', '魂斗罗', '封号斗罗', '极限斗罗', '半神', '神祇', '神王', '至高神王', '创世神'];

// 模块级标记：跨组件StrictMode双挂载/客户端导航只领取一次离线收益（刷新页面会重新领取，符合放置游戏惯例）
let offlineClaimAttempted = false;

export default function GamePage() {
    // 顶部标题/昵称/退出由 layout 顶栏承载（此处不再渲染内置 header，避免双顶栏）
    const { isLoading } = useAuth();
    const router = useRouter();
    const { gameState, message, setMessage, actionLoading, loadError, runAction, refresh } = useGameData();
    const [battleResult, setBattleResult] = useState<BattleResult | null>(null);
    const [offline, setOffline] = useState<OfflineReward | null>(null);
    // 转生确认弹窗：按钮只负责打开，真正请求在弹窗内「确认转生」触发
    const [prestigeOpen, setPrestigeOpen] = useState(false);
    const prestigeCancelRef = useRef<HTMLButtonElement>(null);

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

    // 成就解锁 toast：每次 refresh 落地后 diff 新解锁的 id。
    // 首拉（seenUnlockedRef 为 null）只建档不提示；已见过的 id 不重复提示；
    // achievements 缺失/旧后端 string[] 形状 → normalizeAchievements 返回空数组，静默跳过。
    // 多条同时解锁：取第一条 + 数量（`X 等 N 项`），不逐条拼接以免 message 条过长溢出。
    const seenUnlockedRef = useRef<Set<string> | null>(null);
    useEffect(() => {
        if (!gameState) return;
        const unlocked = normalizeAchievements(gameState.achievements).filter((a) => a.unlocked);
        const seen = seenUnlockedRef.current;
        if (seen === null) {
            seenUnlockedRef.current = new Set(unlocked.map((a) => a.id));
            return;
        }
        const fresh = unlocked.filter((a) => !seen.has(a.id));
        if (fresh.length === 0) return;
        for (const a of unlocked) seen.add(a.id);
        setMessage(fresh.length === 1
            ? `🏆 成就解锁：${fresh[0].name}！属性已生效`
            : `🏆 成就解锁：${fresh[0].name} 等${fresh.length}项！属性已生效`);
    }, [gameState, setMessage]);

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

    // 转生（神位传承）：等级不足时后端也是 200 + success:false（照 breakthrough 惯例）→ 透传 message。
    // 成功/失败路径都关闭弹窗（message 在战斗卡内展示），失败不打断用户修正后重试。
    const handlePrestige = async () => {
        await runAction(
            () => api.prestige(),
            (result) => setMessage(result.message),
            '转生失败',
        );
        setPrestigeOpen(false);
    };

    // 转生确认弹窗可达性：打开时聚焦「取消」（破坏性操作的安全默认），Escape 关闭；
    // 未做完整焦点圈禁（Tab 仍可离开弹窗），与离线收益弹窗同级、不强拦截键盘路径。
    useEffect(() => {
        if (!prestigeOpen) return;
        prestigeCancelRef.current?.focus();
        const onKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setPrestigeOpen(false);
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [prestigeOpen]);

    // 每日签到：runAction 成功回调 + 自动 refresh 拉新签到状态；已签由后端 400 → message 提示
    const handleCheckin = () => runAction(
        () => api.checkin(),
        (r) => setMessage(`签到成功！第${r.cycleDay}天：+${r.goldGained}金币${r.bossCoinGained > 0 ? ` +${r.bossCoinGained}Boss币` : ''}`),
        '签到失败',
    );

    // 每日任务领奖：runAction 成功回调 + 自动 refresh 拉新任务进度；未达标/重复领取由后端 400 → message 提示
    const handleClaimQuest = (questId: string) => runAction(
        () => api.claimQuest(questId),
        (r) => setMessage(`任务奖励已领取！+${r.goldGained}金币${r.bossCoinGained > 0 ? ` +${r.bossCoinGained}Boss币` : ''}${r.soulPowerGained > 0 ? ` +${r.soulPowerGained}魂力` : ''}`),
        '领取失败',
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

                {/* 任务#23：战力明细（可折叠，默认收起；放在状态栏下方，与 ⚔️ 战力 同一视觉区）。
                    战斗属性摘要行：后端升级前 combatStats 缺失 → 面板内整行隐藏 */}
                <PowerDetailPanel power={gameState.power} detail={gameState.powerDetail} combatStats={gameState.combatStats} />

                {/* 每日签到（后端未升级时 checkIn 缺失 → 卡内降级为空态） */}
                <CheckinCard checkIn={gameState.checkIn} actionLoading={actionLoading} onCheckin={handleCheckin} />

                {/* 每日任务（纯增量功能：后端未升级时 dailyQuests 缺失 → 整卡隐藏不渲染） */}
                <DailyQuestsCard quests={gameState.dailyQuests?.quests} actionLoading={actionLoading} onClaim={handleClaimQuest} />

                {/* 战斗区域 */}
                <div className="dl-fade-up [animation-delay:240ms] bg-surface/80 rounded-xl p-4 border border-line">
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
                        }`}
                            data-testid={message.startsWith('🏆 成就解锁') ? 'achievement-toast' : undefined}
                        >
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
                            {battleResult.battleLog && battleResult.battleLog.length > 0 ? (
                                // 回放组件：battleLog 非空时替代静态日志（新结果自动重置回放）
                                <BattleReplay battleLog={battleResult.battleLog} monsterName={battleResult.monsterName} />
                            ) : (
                                // 降级路径：battleLog 缺失/为空（旧后端）→ 保留静态回合日志，信息不丢失
                                <details className="mt-2 text-xs text-gray-400">
                                    <summary className="cursor-pointer select-none">回合日志</summary>
                                    <ul className="mt-1 space-y-0.5">
                                        {battleResult.battleLog?.map((r) => (
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

                    {/* 操作按钮（第 4 钮转生：375px 下 2×2 两行、sm 起一行四钮，零水平溢出） */}
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
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
                        <button
                            data-testid="prestige-btn"
                            onClick={() => setPrestigeOpen(true)}
                            disabled={actionLoading || p.level < PRESTIGE_MIN_LEVEL}
                            title={prestigeHint(p.level, p.prestigeCount)}
                            aria-label={`转生（${prestigeHint(p.level, p.prestigeCount)}）`}
                            className="py-3 bg-gradient-to-r from-red-600 to-yellow-600 hover:from-red-500 hover:to-yellow-500 rounded-lg font-bold transition disabled:opacity-50"
                        >
                            🔄 转生
                        </button>
                    </div>
                    <div className="text-center text-xs text-gray-500 mt-2 space-y-0.5">
                        <div>突破需要 {breakthroughCost} 魂力 (当前: {p.soulPower})</div>
                        <div data-testid="prestige-hint">{prestigeHint(p.level, p.prestigeCount)}</div>
                    </div>
                </div>

                {/* 快捷导航：5 格 → grid-cols-3（375px 下 3+2 两行，每格约 106px 宽裕容纳图标+四字标签，无孤行格且零水平溢出） */}
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
                    <button onClick={() => router.push('/game/social/rank')} className="bg-surface/80 rounded-xl p-4 border border-line hover:border-yellow-500 hover:-translate-y-0.5 transition text-center">
                        <div className="text-2xl mb-1">🏆</div>
                        <div className="text-sm text-gray-300">排行榜</div>
                    </button>
                    <button data-testid="nav-achievements" onClick={() => router.push('/game/achievements')} className="bg-surface/80 rounded-xl p-4 border border-line hover:border-yellow-500 hover:-translate-y-0.5 transition text-center">
                        <div className="text-2xl mb-1" aria-hidden>🏅</div>
                        <div className="text-sm text-gray-300">成就</div>
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

            {/* 转生确认弹窗（神位传承）：不直接执行，先列明重置项/保留项；点遮罩或取消关闭，确认才发请求 */}
            {prestigeOpen && (
                <div
                    className="dl-fade-in fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
                    role="dialog"
                    aria-modal="true"
                    aria-label="神位传承确认"
                    data-testid="prestige-dialog"
                    onClick={() => setPrestigeOpen(false)}
                >
                    <div
                        className="dl-pop bg-surface border border-yellow-500/50 rounded-2xl p-6 max-w-sm w-full shadow-2xl max-h-[85vh] overflow-y-auto"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <h3 className="text-lg font-bold text-yellow-400 mb-3">
                            <span aria-hidden>⚠️</span> 神位传承
                        </h3>
                        <p className="text-sm text-gray-400 mb-3">
                            转生是不可逆的重大抉择（当前 {p.prestigeCount} 转 → 转生后 {p.prestigeCount + 1} 转），请确认以下变化：
                        </p>
                        {/* 两栏清单：375px 下单列堆叠保证可读，sm 起并排 */}
                        <div className="grid sm:grid-cols-2 gap-3 text-xs mb-3">
                            <div className="bg-red-900/30 border border-red-600/50 rounded-lg p-3">
                                <div className="font-semibold text-red-300 mb-1">重置项</div>
                                <ul className="space-y-0.5 text-gray-300">
                                    <li>• 等级回到 Lv.1</li>
                                    <li>• 金币清零</li>
                                    <li>• 魂力清零</li>
                                    <li>• 已装备魂环/魂骨卸回背包</li>
                                </ul>
                            </div>
                            <div className="bg-green-900/30 border border-green-600/50 rounded-lg p-3">
                                <div className="font-semibold text-green-300 mb-1">保留项</div>
                                <ul className="space-y-0.5 text-gray-300">
                                    <li>• 装备与背包</li>
                                    <li>• 天赋等级并 +1 天赋点</li>
                                    <li>• 成就</li>
                                    <li>• 杀戮之都进度</li>
                                    <li>• 推图进度</li>
                                    <li>• 图鉴</li>
                                    <li>• Boss币</li>
                                </ul>
                            </div>
                        </div>
                        <p className="text-xs text-gray-400 mb-4">
                            转生后每转全属性+10%、收入+10%；本次转生后全属性+{(p.prestigeCount + 1) * 10}%。
                        </p>
                        <div className="grid grid-cols-2 gap-3">
                            <button
                                ref={prestigeCancelRef}
                                data-testid="prestige-cancel"
                                onClick={() => setPrestigeOpen(false)}
                                disabled={actionLoading}
                                className="py-2 bg-gray-700 hover:bg-gray-600 rounded-lg font-bold transition disabled:opacity-50"
                            >
                                取消
                            </button>
                            <button
                                data-testid="prestige-confirm"
                                onClick={handlePrestige}
                                disabled={actionLoading}
                                className="py-2 bg-gradient-to-r from-red-600 to-yellow-600 hover:from-red-500 hover:to-yellow-500 rounded-lg font-bold transition disabled:opacity-50"
                            >
                                {actionLoading ? '转生中...' : '确认转生'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
