'use client';

import api from '@/lib/api';
import { awakenToast, REAWAKEN_COST_GOLD, soulRarityMeta } from '@/lib/soul';
import { RESCHOOL_COST_GOLD, schoolBadgeMeta } from '@/lib/school';
import { useGameData } from '@/contexts/GameDataContext';

const REALM_NAMES = ['魂士', '魂师', '大魂师', '魂尊', '魂宗', '魂王', '魂帝', '魂圣', '魂斗罗', '封号斗罗', '极限斗罗', '半神', '神祇', '神王', '至高神王', '创世神'];

/**
 * 主页玩家状态栏（境界/战力/武魂/流派/HP/资源），自 game/page.tsx 拆出，DOM 与拆分前逐字节一致。
 * 数据经 useGameData Context 消费（与布局顶栏同一实例）；觉醒请求内聚在本组件，弹窗开合经回调上抛。
 */
export default function PlayerStatusBar({
    onOpenSchool,
    onOpenReawaken,
}: {
    onOpenSchool: () => void;
    onOpenReawaken: () => void;
}) {
    const { gameState, actionLoading, runAction, setMessage } = useGameData();
    if (!gameState) return null;

    const p = gameState.profile;
    // 武魂稀有度徽章元数据：旧后端 soulRarity 缺失/未知 → null（只显名字不显徽章）
    const soulMeta = soulRarityMeta(p.soulRarity);
    // 流派徽章元数据：未选/旧后端 null/未知枚举 → null（降级为「未选流派」灰字）
    const schoolMeta = schoolBadgeMeta(p.chosenSchool);
    const realmName = REALM_NAMES[Math.min(Math.floor((p.level - 1) / 10), REALM_NAMES.length - 1)];
    const maxHp = 50 * p.level + 100;
    const hpPercent = Math.round((p.currentHp / maxHp) * 100);

    // 觉醒/重醒：后端失败（重醒金币不足）也是 200 + success:false → message 照显；
    // 传说/神话成功由 awakenToast 加 ✨🎉 前缀。首醒免费且不可逆性弱 → 不弹窗直接觉醒，结果走大号 message toast。
    const handleAwaken = () => runAction(
        () => api.awaken(),
        (r) => setMessage(awakenToast(r)),
        '觉醒失败',
    );

    return (
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
                {/* 武魂状态：已觉醒 → 名字+稀有度徽章+重醒入口；未觉醒 → 名字占位+觉醒按钮（首醒免费，不弹窗）。
                    同一 flex 行尾追加流派徽章（已选：icon+中文名，点击可重选）或「未选流派」灰字+选流派入口；
                    375px：徽章 text-[11px] shrink-0、名字 truncate、行 flex-wrap 兜底换行、按钮 min-h-11 触达高度，零溢出 */}
                <div className="text-sm text-gray-400 min-w-0 max-w-full flex flex-col items-start sm:items-end gap-1">
                    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 min-w-0 max-w-full">
                        {p.martialSoulName ? (
                            <>
                                <span className="truncate">武魂: {p.martialSoulName}</span>
                                {soulMeta && (
                                    <span
                                        data-testid="soul-badge"
                                        className={`shrink-0 inline-flex items-center rounded border px-1.5 py-0.5 text-[11px] font-semibold leading-none ${soulMeta.className}`}
                                    >
                                        {soulMeta.label}
                                    </span>
                                )}
                            </>
                        ) : (
                            <span>未觉醒武魂</span>
                        )}
                        {schoolMeta ? (
                            <button
                                type="button"
                                data-testid="school-badge"
                                onClick={onOpenSchool}
                                disabled={actionLoading}
                                title={`当前流派：${schoolMeta.label}（点击重选，花费 ${RESCHOOL_COST_GOLD} 金币）`}
                                className={`shrink-0 min-h-11 inline-flex items-center gap-1 rounded border px-1.5 py-0.5 text-[11px] font-semibold leading-none transition hover:brightness-110 disabled:opacity-50 ${schoolMeta.className}`}
                            >
                                <span aria-hidden>{schoolMeta.icon}</span>{schoolMeta.label}
                            </button>
                        ) : (
                            <>
                                <span className="shrink-0 text-gray-500">未选流派</span>
                                <button
                                    type="button"
                                    data-testid="school-btn"
                                    onClick={onOpenSchool}
                                    disabled={actionLoading}
                                    title="选择流派（首选免费）"
                                    className="shrink-0 min-h-11 px-1 text-xs text-yellow-500 hover:text-yellow-400 underline underline-offset-2 transition disabled:opacity-50"
                                >
                                    选流派
                                </button>
                            </>
                        )}
                    </div>
                    {/* 武魂专属技能名：徽章行下方小字展示（页内可见优先于 title）。
                        后端升级前 soulSkillName 缺失/未觉醒 null → 不渲染；truncate + max-w-full 防 375px 溢出 */}
                    {p.soulSkillName && (
                        <span
                            data-testid="soul-skill-name"
                            title={`武魂专属技能：${p.soulSkillName}（冷却制自动释放）`}
                            className="max-w-full truncate text-[11px] text-muted"
                        >
                            技能：{p.soulSkillName}
                        </span>
                    )}
                    {p.martialSoulName ? (
                        <button
                            data-testid="reawaken-btn"
                            onClick={onOpenReawaken}
                            disabled={actionLoading}
                            title={`重醒花费 ${REAWAKEN_COST_GOLD} 金币，随机获得当前品质池内的新武魂`}
                            className="min-h-11 px-1 text-xs text-gray-500 hover:text-yellow-400 underline underline-offset-2 transition disabled:opacity-50"
                        >
                            重醒（{REAWAKEN_COST_GOLD}金币）
                        </button>
                    ) : (
                        <button
                            data-testid="awaken-btn"
                            onClick={handleAwaken}
                            disabled={actionLoading}
                            title="首次觉醒免费"
                            aria-label="觉醒武魂（首次免费）"
                            className="min-h-11 px-4 rounded-lg bg-gradient-to-r from-yellow-600 to-amber-600 hover:from-yellow-500 hover:to-amber-500 text-white text-sm font-bold transition disabled:opacity-50"
                        >
                            {actionLoading ? '觉醒中...' : '觉醒'}
                        </button>
                    )}
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
    );
}
