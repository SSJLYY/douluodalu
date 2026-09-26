'use client';

import { useEffect, useMemo, useState } from 'react';
import type { BattleRound } from '@/lib/api';

/** 自动播放步进间隔（毫秒/回合） */
const STEP_MS = 900;

/** 为每次战斗的 battleLog 数组引用铸造稳定 key：引用变化 → Inner 重挂载 → 回放状态重置 */
let logTokenSeq = 0;
const logTokens = new WeakMap<BattleRound[], string>();
function tokenFor(log: BattleRound[]): string {
    let token = logTokens.get(log);
    if (!token) {
        token = `battle-${++logTokenSeq}`;
        logTokens.set(log, token);
    }
    return token;
}

/**
 * 回放内核（由外层按 battleLog 引用 key 重挂载）：
 * - 初始状态即「重置」：reduced-motion 直接定位末回合且不自动播放，否则从第 1 回合自动播放；
 * - 自动播放 900ms/回合；是否在播 = playing && 未到末回合（派生值，到末回合自然停，无 effect 内 setState）；
 * - timeout 句柄随 effect 清理，暂停/卸载/重挂载均无泄漏。
 */
function ReplayInner({ log, monsterName }: { log: BattleRound[]; monsterName?: string }) {
    const total = log.length;

    // reduced-motion 快照：仅决定初始定位/是否自动播放；偏好变化后下次战斗重挂载时重新读取
    const [reducedMotion] = useState(
        () => typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    );
    const [index, setIndex] = useState(() => (reducedMotion ? total - 1 : 0));
    const [playing, setPlaying] = useState(() => !reducedMotion && total > 1);

    const atEnd = index >= total - 1;
    const effectivePlaying = playing && !atEnd;

    // 自动播放：到末回合 effectivePlaying 变 false，effect 自然不再排程（暂停/卸载清理句柄）
    useEffect(() => {
        if (!effectivePlaying) return;
        const timer = setTimeout(() => setIndex((i) => Math.min(i + 1, total - 1)), STEP_MS);
        return () => clearTimeout(timer);
    }, [effectivePlaying, index, total]);

    // 双侧最大 HP：round1 的 before 与所有 after 的最大者（下限 1 防除零）
    const maxPlayerHp = useMemo(
        () => Math.max(1, ...log.map((r) => Math.max(r.playerHpBefore, r.playerHpAfter))),
        [log],
    );
    const maxMonsterHp = useMemo(
        () => Math.max(1, ...log.map((r) => Math.max(r.monsterHpBefore, r.monsterHpAfter))),
        [log],
    );

    const current = log[Math.min(index, total - 1)];
    const playerPct = Math.max(0, Math.min(100, (current.playerHpAfter / maxPlayerHp) * 100));
    const monsterPct = Math.max(0, Math.min(100, (current.monsterHpAfter / maxMonsterHp) * 100));
    // HP 归零的那回合：该侧标签行抖动（key 重挂载触发；HP 条本体稳定挂载，宽度过渡不被破坏）
    const playerDown = current.playerHpAfter <= 0;
    const monsterDown = current.monsterHpAfter <= 0;
    // 技能回合：skillName 非空（旧后端缺失/普通回合 null → 无高亮，渲染与旧版一致）
    const skillName = current.skillName ?? null;
    // 治疗回合签名：本回合我方零输出且 HP 回升（治疗技能该回合不攻击；普攻数据不可能出现此组合）
    const isHeal = current.playerDamage === 0 && current.playerHpAfter > current.playerHpBefore;

    const stepPrev = () => {
        setPlaying(false);
        setIndex((i) => Math.max(0, i - 1));
    };
    const stepNext = () => {
        setPlaying(false);
        setIndex((i) => Math.min(total - 1, i + 1));
    };
    const togglePlay = () => {
        if (effectivePlaying) {
            setPlaying(false);
        } else {
            // 末回合再按播放 → 从第 1 回合重放
            if (atEnd) setIndex(0);
            setPlaying(true);
        }
    };

    return (
        <div data-testid="battle-replay" className="mt-3 pt-3 border-t border-line/60">
            {/* 回合指示：文字 + 圆点列（圆点纯装饰 aria-hidden，进度由文字承载） */}
            <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-3">
                <span className="text-xs sm:text-sm text-gray-300 tabular-nums">
                    第 {index + 1} / {total} 回合
                </span>
                <div className="flex flex-wrap gap-1 justify-end max-w-full" aria-hidden>
                    {log.map((r) => (
                        <span
                            key={r.round}
                            className={`w-1.5 h-1.5 rounded-full ${r.round <= index + 1 ? 'bg-accent' : 'bg-gray-600'}`}
                        />
                    ))}
                </div>
            </div>

            {/* 我方 HP */}
            <div className="mb-3">
                <div
                    key={`p-label-${index}`}
                    className={`flex justify-between items-center gap-2 text-xs mb-1 ${playerDown ? 'dl-shake' : ''}`}
                >
                    <span className="text-green-400 font-medium shrink-0">我方</span>
                    <span className="text-gray-400 tabular-nums">
                        {current.playerHpAfter.toLocaleString()} / {maxPlayerHp.toLocaleString()}
                    </span>
                </div>
                <div
                    className="h-3 bg-gray-700 rounded-full overflow-hidden"
                    role="progressbar"
                    aria-valuemin={0}
                    aria-valuemax={maxPlayerHp}
                    aria-valuenow={current.playerHpAfter}
                    aria-label="我方生命值"
                >
                    <div className="dl-hp-bar h-full bg-green-500 rounded-full" style={{ width: `${playerPct}%` }} />
                </div>
            </div>

            {/* 敌方 HP */}
            <div className="mb-3">
                <div
                    key={`m-label-${index}`}
                    className={`flex justify-between items-center gap-2 text-xs mb-1 ${monsterDown ? 'dl-shake' : ''}`}
                >
                    <span className="text-red-400 font-medium min-w-0 truncate">
                        敌方{monsterName ? ` · ${monsterName}` : ''}
                    </span>
                    <span className="text-gray-400 tabular-nums shrink-0">
                        {current.monsterHpAfter.toLocaleString()} / {maxMonsterHp.toLocaleString()}
                    </span>
                </div>
                <div
                    className="h-3 bg-gray-700 rounded-full overflow-hidden"
                    role="progressbar"
                    aria-valuemin={0}
                    aria-valuemax={maxMonsterHp}
                    aria-valuenow={current.monsterHpAfter}
                    aria-label="敌方生命值"
                >
                    <div className="dl-hp-bar h-full bg-red-500 rounded-full" style={{ width: `${monsterPct}%` }} />
                </div>
            </div>

            {/* 当前回合伤害数字：key 重挂载触发 dl-value-flash；窄屏 flex-wrap 换行不溢出。
                技能回合（skillName 非空）行首追加 ✨ 技能名高亮（text-accent，随回合切换重挂载闪烁）；
                治疗回合（playerDamage=0 且 HP 回升）以 💚 回复标记替代「我方输出 0」，避免零伤数字误导；
                旧数据（skillName 缺失且非治疗）两分支均不触发，渲染与旧版逐像素一致 */}
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs sm:text-sm mb-3">
                {skillName && (
                    <span
                        key={`skill-${index}`}
                        data-testid="battle-skill-highlight"
                        className="dl-value-flash max-w-full text-accent font-semibold"
                    >
                        <span aria-hidden>✨</span> 技能「{skillName}」
                    </span>
                )}
                {isHeal ? (
                    <span
                        key={`p-heal-${index}`}
                        data-testid="battle-heal-marker"
                        className="dl-value-flash font-bold text-green-400"
                    >
                        <span aria-hidden>💚</span> 回复
                    </span>
                ) : (
                    <span className="text-gray-400">
                        我方输出{' '}
                        <span
                            key={`p-dmg-${index}`}
                            className={`dl-value-flash font-bold tabular-nums ${current.playerDamage > 0 ? 'text-green-400' : 'text-gray-500'}`}
                        >
                            {current.playerDamage.toLocaleString()}
                        </span>
                    </span>
                )}
                <span className="text-gray-400">
                    敌方输出{' '}
                    <span
                        key={`m-dmg-${index}`}
                        className={`dl-value-flash font-bold tabular-nums ${current.monsterDamage > 0 ? 'text-red-400' : 'text-gray-500'}`}
                    >
                        {current.monsterDamage.toLocaleString()}
                    </span>
                </span>
            </div>

            {/* 控制：步进/播放三钮，min-h-11 触控友好；手动步进即暂停自动播放 */}
            <div className="grid grid-cols-3 gap-2">
                <button
                    type="button"
                    data-testid="battle-replay-prev"
                    onClick={stepPrev}
                    disabled={index === 0}
                    className="min-h-11 px-2 rounded-lg border border-line bg-surface-soft/60 hover:bg-surface-soft text-xs sm:text-sm transition-colors disabled:opacity-50"
                >
                    上一回合
                </button>
                <button
                    type="button"
                    data-testid="battle-replay-toggle"
                    aria-label={effectivePlaying ? '暂停回放' : '播放回放'}
                    onClick={togglePlay}
                    className="min-h-11 px-2 rounded-lg bg-gradient-to-r from-orange-600 to-red-600 hover:from-orange-500 hover:to-red-500 text-xs sm:text-sm font-bold transition-colors"
                >
                    {effectivePlaying ? '⏸ 暂停' : '▶ 播放'}
                </button>
                <button
                    type="button"
                    data-testid="battle-replay-next"
                    onClick={stepNext}
                    disabled={atEnd}
                    className="min-h-11 px-2 rounded-lg border border-line bg-surface-soft/60 hover:bg-surface-soft text-xs sm:text-sm transition-colors disabled:opacity-50"
                >
                    下一回合
                </button>
            </div>
        </div>
    );
}

/**
 * 战斗回合回放：消费 battleLog 逐回合回放双侧 HP 与伤害。
 * - HP 条宽度按「该侧全程最大 HP」归一化，宽度过渡走 .dl-hp-bar（reduced-motion 下 CSS 侧即时切换）；
 * - 技能回合（skillName 非空）在伤害数字行首高亮技能名（text-accent）；治疗回合（零输出且 HP 回升）
 *   以 💚 回复标记替代「我方输出 0」；旧数据无 skillName → 无高亮，渲染与旧版一致；
 * - 每次 battleLog 引用变化（新战斗结果）→ key 重挂载 → 重置到第 1 回合并自动播放；
 * - battleLog 缺失/为空返回 null，由调用方保留静态 <details> 兜底，信息不丢失。
 */
export default function BattleReplay({ battleLog, monsterName }: { battleLog?: BattleRound[]; monsterName?: string }) {
    const log = battleLog && battleLog.length > 0 ? battleLog : null;
    if (!log) return null;
    return <ReplayInner key={tokenFor(log)} log={log} monsterName={monsterName} />;
}
