'use client';

import { useState } from 'react';
import type { PowerDetail } from '@/lib/api';

/**
 * 任务#23：战力明细可折叠面板（默认收起），从 game/page.tsx 抽出以便单测
 * （Next App Router 的路由文件不允许任意导出，组件放 components/ 目录）。
 * 后端 PowerDetailDto 保证 basePower+ringPower+bonePower+corePower == 总战力（拆分求和恒等），
 * 每行显示攻击/生命贡献与占总战力百分比；颜色全部走主题调色板变量，亮暗两色主题均可读。
 * 后端补 achievement 字段后追加第 5 行「🏆 成就」、补 prestige 字段后追加第 6 行「🔄 转生」（五行/六行求和 == 总战力）；
 * 旧后端缺字段 → undefined 按 0 处理、行隐藏，脚注在「四行/五行/六行」间动态回退。
 */
interface PowerRow {
    key: string;
    icon: string;
    label: string;
    /** 成就行无独立攻/生拆分（契约只有折算战力）→ null，行内不渲染攻/生子标签 */
    atk: number | null;
    hp: number | null;
    share: number;
    bar: string;
    ariaLabel: string;
}

export default function PowerDetailPanel({ power, detail }: { power: number; detail: PowerDetail }) {
    const [open, setOpen] = useState(false);
    const achPower = detail.achievement ?? 0;
    const prestigePower = detail.prestige ?? 0;
    const rows: PowerRow[] = [
        { key: 'base', icon: '🛡️', label: '基础（等级+攻击）', atk: detail.baseAtk, hp: detail.baseHp, share: detail.basePower, bar: 'bg-gray-500', ariaLabel: '基础（等级+攻击）战力贡献' },
        { key: 'ring', icon: '💜', label: '魂环', atk: detail.ringAtk, hp: detail.ringHp, share: detail.ringPower, bar: 'bg-blue-500', ariaLabel: '魂环战力贡献' },
        { key: 'bone', icon: '🦴', label: '魂骨', atk: detail.boneAtk, hp: detail.boneHp, share: detail.bonePower, bar: 'bg-purple-500', ariaLabel: '魂骨战力贡献' },
        { key: 'core', icon: '🔮', label: '魂核', atk: detail.coreAtk, hp: detail.coreHp, share: detail.corePower, bar: 'bg-yellow-500', ariaLabel: '魂核战力贡献' },
        ...(achPower > 0 ? [{
            key: 'achievement', icon: '🏆', label: '成就', atk: null, hp: null,
            share: achPower, bar: 'bg-amber-500', ariaLabel: '成就加成',
        }] : []),
        ...(prestigePower > 0 ? [{
            key: 'prestige', icon: '🔄', label: '转生', atk: null, hp: null,
            share: prestigePower, bar: 'bg-fuchsia-500', ariaLabel: '转生加成',
        }] : []),
    ];
    // 脚注行数文案随缺失字段动态回退：4 固定行 + 成就/转生可选行 → 四/五/六行
    const rowCountLabel = ['四', '五', '六'][rows.length - 4] ?? String(rows.length);
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
                                        {r.atk != null && r.hp != null && (
                                            <span className="text-gray-500 ml-2">攻 +{r.atk.toLocaleString()} · 生 +{r.hp.toLocaleString()}</span>
                                        )}
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
                                    aria-label={r.ariaLabel}
                                >
                                    <div className={`h-full ${r.bar} rounded-full transition-all duration-700 ease-out`} style={{ width: `${pct}%` }} />
                                </div>
                            </div>
                        );
                    })}
                    <p className="text-[11px] text-gray-500 leading-relaxed">
                        战力 = 基础 + 装备攻击加成 + 装备生命÷10{achPower > 0 ? ' + 成就加成' : ''}{prestigePower > 0 ? ' + 转生加成' : ''}；{rowCountLabel}行求和恒等于总战力（后端同源拆分）。
                    </p>
                </div>
            )}
        </div>
    );
}
