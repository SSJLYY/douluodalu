'use client';

import type { Achievement } from '@/lib/api';
import { EmptyPanel } from '@/components/StateViews';

/**
 * 成就面板（纯展示组件，数据由页面经 normalizeAchievements 形状守卫后传入，便于测试）。
 * 按类别分组（category→中文映射写死在前端），组头带「已解锁 x/y」徽标；
 * 组内排序：已解锁沉底（保持后端原序），未解锁按进度比降序在前。
 * 奖励只展示 hp/atk（生命/攻击），matk/pdef/mdef/critRate/critDmg 后端未生效不展示。
 * achievements 为空（含旧后端 string[] 降级）→ EmptyPanel 空态。
 */

/** 分组展示顺序：修炼 → 魂环 → 战斗 → 杀戮之都 → 转生；未知类别兜底排在末尾 */
const CATEGORY_ORDER = ['CULTIVATION', 'SOUL_RING', 'BATTLE', 'TOWER', 'PRESTIGE'] as const;

const CATEGORY_LABELS: Record<string, string> = {
    CULTIVATION: '修炼',
    SOUL_RING: '魂环',
    BATTLE: '战斗',
    TOWER: '杀戮之都',
    PRESTIGE: '转生',
};

export function categoryLabel(category: string): string {
    return CATEGORY_LABELS[category] ?? category;
}

/** 组内排序：未解锁按进度比（progress/target）降序在前，已解锁沉底（稳定排序保持原相对顺序） */
function sortWithinGroup(rows: Achievement[]): Achievement[] {
    const locked = rows.filter((a) => !a.unlocked);
    const unlocked = rows.filter((a) => a.unlocked);
    locked.sort(
        (a, b) => b.progress / Math.max(1, b.target) - a.progress / Math.max(1, a.target),
    );
    return [...locked, ...unlocked];
}

/** 单条成就行：名称 + 描述 + 进度条（progressbar 三 aria 值）+ 奖励（仅 hp/atk）；未解锁整行灰态 */
function AchievementRow({ achievement: a }: { achievement: Achievement }) {
    const pct = Math.min(100, Math.round((a.progress / Math.max(1, a.target)) * 100));
    const hp = a.rewards?.hp ?? 0;
    const atk = a.rewards?.atk ?? 0;
    return (
        <div
            data-testid={`achievement-row-${a.id}`}
            className={`rounded-lg border p-3 min-w-0 ${
                a.unlocked
                    ? 'border-yellow-600/50 bg-yellow-900/20'
                    : 'border-line bg-gray-700/40 opacity-75'
            }`}
        >
            <div className="flex items-start justify-between gap-2 mb-1">
                <div className="min-w-0">
                    <div className="text-sm font-semibold text-gray-200 truncate" title={a.name}>
                        {a.name}
                    </div>
                    <div className="text-xs text-gray-400 leading-snug">{a.description}</div>
                </div>
                {a.unlocked ? (
                    <span
                        data-testid={`achievement-unlocked-${a.id}`}
                        className="shrink-0 px-2 py-0.5 rounded border border-yellow-500 bg-yellow-500/15 text-yellow-400 text-xs whitespace-nowrap"
                    >
                        <span aria-hidden>✓</span> 已解锁{a.unlockedAt ? ` ${a.unlockedAt}` : ''}
                    </span>
                ) : (
                    <span className="shrink-0 text-xs text-gray-400 tabular-nums">
                        {a.progress}/{a.target}
                    </span>
                )}
            </div>
            <div
                className="h-2 bg-gray-700 rounded-full overflow-hidden"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={a.target}
                aria-valuenow={a.progress}
                aria-label={`${a.name}进度`}
            >
                <div
                    className={`dl-hp-bar h-full rounded-full ${a.unlocked ? 'bg-yellow-500' : 'bg-blue-500'}`}
                    style={{ width: `${pct}%` }}
                />
            </div>
            {hp > 0 || atk > 0 ? (
                <div className="mt-1 text-[11px] text-gray-400 tabular-nums">
                    生命 +{hp.toLocaleString()} · 攻击 +{atk.toLocaleString()}
                </div>
            ) : null}
        </div>
    );
}

export default function AchievementsPanel({ achievements }: { achievements: Achievement[] }) {
    if (achievements.length === 0) {
        // 降级路径：后端未升级（string[] → normalize 后为空）/ 无数据 → 空态灰字
        return (
            <div className="bg-surface/80 rounded-xl p-4 border border-line" data-testid="achievements-panel">
                <EmptyPanel message="暂无成就数据" testId="achievements-empty" />
            </div>
        );
    }

    // 按类别分组（保持首次出现顺序），再按 CATEGORY_ORDER 重排，未知类别追加在末尾
    const groups = new Map<string, Achievement[]>();
    for (const a of achievements) {
        const list = groups.get(a.category) ?? [];
        list.push(a);
        groups.set(a.category, list);
    }
    const known = CATEGORY_ORDER.filter((c) => groups.has(c));
    const unknown = [...groups.keys()].filter((c) => !CATEGORY_ORDER.includes(c as typeof CATEGORY_ORDER[number]));

    return (
        <div className="dl-fade-up bg-surface/80 rounded-xl p-4 border border-line" data-testid="achievements-panel">
            <div className="space-y-5">
                {[...known, ...unknown].map((category) => {
                    const rows = sortWithinGroup(groups.get(category) ?? []);
                    const unlockedCount = rows.filter((a) => a.unlocked).length;
                    return (
                        <section key={category} data-testid={`achievement-group-${category}`}>
                            <div className="flex items-center justify-between gap-2 mb-2">
                                <h2 className="text-sm font-semibold text-orange-400">{categoryLabel(category)}</h2>
                                <span
                                    data-testid={`achievement-group-count-${category}`}
                                    className="px-2 py-0.5 rounded border border-yellow-600/50 bg-yellow-600/10 text-yellow-400 text-xs tabular-nums shrink-0"
                                >
                                    已解锁 {unlockedCount}/{rows.length}
                                </span>
                            </div>
                            <div className="space-y-3">
                                {rows.map((a) => (
                                    <AchievementRow key={a.id} achievement={a} />
                                ))}
                            </div>
                        </section>
                    );
                })}
            </div>
        </div>
    );
}
