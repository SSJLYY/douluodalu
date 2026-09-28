'use client';

import { useState } from 'react';
import api, { BattleResult, UpdateSettingsRequest } from '@/lib/api';
import { PRESTIGE_MIN_LEVEL, prestigeHint } from '@/lib/prestige';
import { breakthroughCostFor, useAutoBattle } from '@/lib/hooks';
import { MAP_NAMES } from '@/lib/maps';
import BattleReplay from '@/components/BattleReplay';
import { useGameData } from '@/contexts/GameDataContext';

/** 挂机设置三开关 key（= PUT /api/game/settings 请求体的三个布尔字段，由类型推导保持同步） */
type SettingKey = keyof UpdateSettingsRequest;

/** 主页挂机设置 chips 元数据（顺序即渲染顺序） */
const SETTING_CHIPS: { key: SettingKey; testid: string; label: string; title: string }[] = [
    { key: 'autoBattle', testid: 'settings-toggle-autobattle', label: '🤖 自动战斗', title: '开启后每 2 秒自动战斗一次（页面隐藏时自动暂停）' },
    { key: 'autoAdvanceMap', testid: 'settings-toggle-autoadvancemap', label: '🗺️ 自动推图', title: '战斗胜利后自动推进地图进度（后端结算）' },
    { key: 'autoBreakthrough', testid: 'settings-toggle-autobreakthrough', label: '⬆️ 自动突破', title: '战斗后魂力足够时自动突破（消耗 120·Lv^1.55 魂力）' },
];

/**
 * 主页战斗区域（消息条/战斗结果/四操作钮/挂机设置/突破与转生提示），自 game/page.tsx 拆出，
 * DOM 与拆分前逐字节一致。战斗结果卡与自动战斗循环内聚在本组件；转生弹窗开合经回调上抛。
 */
export default function BattlePanel({ onOpenPrestige }: { onOpenPrestige: () => void }) {
    const { gameState, setGameState, message, setMessage, actionLoading, runAction, runMessageAction } = useGameData();
    const [battleResult, setBattleResult] = useState<BattleResult | null>(null);
    // 挂机设置保存请求飞行中标记：chips 短暂禁用防连点乱序（乐观更新已让 UI 即时反馈）
    const [settingsSaving, setSettingsSaving] = useState(false);

    // 自动战斗循环（profile.autoBattle 开启即跑）：不走 runAction——省每 tick 的 actionLoading
    // 翻转与整页 refresh，状态数值交给已订阅的本人 /topic/battle 广播 → useGameData debouncedRefresh；
    // onResult 只更新战斗结果卡（不刷 message，避免 2s 一条 toast 刷屏盖掉成就/公告提示）。
    useAutoBattle(
        gameState?.profile.autoBattle ?? false,
        gameState?.profile.autoBreakthrough ?? false,
        (result) => setBattleResult(result),
    );

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

    const handleBreakthrough = () => runMessageAction(
        () => api.breakthrough(),
        '突破失败',
    );

    // 挂机设置 toggle：后端契约三布尔必须全量显式回传（缺字段/null → 400 VALIDATION_ERROR），
    // 目标项取反、另两项按当前 profile 原值带上；乐观更新本地 profile（飞行中 UI 即时反馈，
    // WS/轮询刷新后续以服务端值为准纠偏），失败回滚 + message。
    // 不走 runAction：不触发整页 actionLoading，也无需立即 refresh（响应本身就是最新 Profile）。
    const handleToggleSetting = async (key: SettingKey) => {
        if (!gameState || settingsSaving) return;
        const before = gameState.profile;
        const req: UpdateSettingsRequest = {
            autoBattle: key === 'autoBattle' ? !before.autoBattle : before.autoBattle,
            autoAdvanceMap: key === 'autoAdvanceMap' ? !before.autoAdvanceMap : before.autoAdvanceMap,
            autoBreakthrough: key === 'autoBreakthrough' ? !before.autoBreakthrough : before.autoBreakthrough,
        };
        setSettingsSaving(true);
        setGameState((prev) => (prev ? { ...prev, profile: { ...prev.profile, ...req } } : prev));
        try {
            const updated = await api.updateSettings(req);
            setGameState((prev) => (prev ? { ...prev, profile: updated } : prev));
        } catch (err) {
            setGameState((prev) => (prev ? { ...prev, profile: { ...prev.profile, ...before } } : prev));
            setMessage(err instanceof Error && err.message ? err.message : '保存设置失败');
        } finally {
            setSettingsSaving(false);
        }
    };

    if (!gameState) return null;

    const p = gameState.profile;
    const mapName = MAP_NAMES[p.currentMapId] || '未知';
    // 突破消耗公式镜像收敛到 hooks.ts breakthroughCostFor（与自动突破阈值同源，改需与后端同步）
    const breakthroughCost = breakthroughCostFor(p.level);

    return (
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
                    data-testid={
                        message.startsWith('🏆 成就解锁')
                            ? 'achievement-toast'
                            : message.startsWith('📢')
                                ? 'announcement-toast'
                                : undefined
                    }
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
                    onClick={onOpenPrestige}
                    disabled={actionLoading || p.level < PRESTIGE_MIN_LEVEL}
                    title={prestigeHint(p.level, p.prestigeCount)}
                    aria-label={`转生（${prestigeHint(p.level, p.prestigeCount)}）`}
                    className="py-3 bg-gradient-to-r from-red-600 to-yellow-600 hover:from-red-500 hover:to-yellow-500 rounded-lg font-bold transition disabled:opacity-50"
                >
                    🔄 转生
                </button>
            </div>

            {/* 挂机设置三开关：切换即 PUT /api/game/settings（三布尔全量回传）+ 乐观更新本地 profile。
                autoBattle 开启驱动 useAutoBattle 循环；375px flex-wrap 换行、chips min-h-11 触达高度，零溢出 */}
            <div className="flex flex-wrap items-center gap-2 mt-3" role="group" aria-label="挂机设置">
                {SETTING_CHIPS.map((chip) => {
                    const active = p[chip.key];
                    return (
                        <button
                            key={chip.key}
                            type="button"
                            data-testid={chip.testid}
                            onClick={() => handleToggleSetting(chip.key)}
                            disabled={settingsSaving}
                            title={chip.title}
                            aria-pressed={active}
                            className={`min-h-11 px-3 rounded-full border text-xs font-semibold inline-flex items-center transition disabled:opacity-50 ${
                                active
                                    ? 'border-green-500/60 bg-green-500/15 text-green-300 hover:bg-green-500/25'
                                    : 'border-line bg-gray-800/60 text-gray-400 hover:text-gray-200 hover:border-gray-500'
                            }`}
                        >
                            {chip.label}
                            {active && <span className="ml-1" aria-hidden>✓</span>}
                        </button>
                    );
                })}
            </div>

            <div className="text-center text-xs text-gray-500 mt-2 space-y-0.5">
                <div>突破需要 {breakthroughCost} 魂力 (当前: {p.soulPower})</div>
                <div data-testid="prestige-hint">{prestigeHint(p.level, p.prestigeCount)}</div>
            </div>
        </div>
    );
}
