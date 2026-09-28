'use client';

import { useRef } from 'react';
import api from '@/lib/api';
import { useModalEscape } from '@/lib/hooks';
import { RESCHOOL_COST_GOLD, SCHOOL_META, schoolUnlockHint, schoolUnlocked } from '@/lib/school';
import { useGameData } from '@/contexts/GameDataContext';

/**
 * 流派选择弹窗，自 game/page.tsx 拆出（DOM 与拆分前逐字节一致）：照重醒弹窗模板
 * （role=dialog/aria-modal、Escape/遮罩关闭、打开聚焦取消钮）。六流派单列卡片：
 * 门槛未达标 → 确认禁用+门槛文案；当前流派 → 标「当前」不可再选；其余项确认为重选（文案注明 5000 金）。
 * 375px：max-h-[85vh] overflow-y-auto、按钮 min-h-11。可达性经 useModalEscape 收敛。
 */
export default function SchoolDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
    const { gameState, actionLoading, runAction, setMessage } = useGameData();
    const cancelRef = useRef<HTMLButtonElement>(null);
    useModalEscape(open, onClose, cancelRef);

    // 选择/重选流派：后端失败（未知流派/门槛不足/已选择该流派/重选金币不足）也是 200 + success:false
    // → message 照显且弹窗保持打开（门槛/金币不足时玩家可直接改选其他项）；仅 success=true 关闭弹窗，
    // runAction 自动 refresh 拉新 chosenSchool 与战力明细第 8 行。
    const handleChooseSchool = (school: string) => runAction(
        () => api.chooseSchool(school),
        (r) => {
            setMessage(r.message);
            if (r.success) onClose();
        },
        '选择流派失败',
    );

    if (!open || !gameState) return null;

    const p = gameState.profile;
    // 当前流派完整元数据（弹窗头文案与「选择/重选」措辞共用；未知枚举/未选 → null）
    const currentSchoolMeta = p.chosenSchool ? SCHOOL_META[p.chosenSchool] ?? null : null;

    return (
        <div
            className="dl-fade-in fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
            role="dialog"
            aria-modal="true"
            aria-label="流派选择"
            data-testid="school-dialog"
            onClick={onClose}
        >
            <div
                className="dl-pop bg-surface border border-indigo-500/50 rounded-2xl p-6 max-w-sm w-full shadow-2xl max-h-[85vh] overflow-y-auto"
                onClick={(e) => e.stopPropagation()}
            >
                <h3 className="text-lg font-bold text-indigo-400 mb-1">
                    <span aria-hidden>🎓</span> 流派
                </h3>
                <p className="text-xs text-gray-400 mb-3">
                    {currentSchoolMeta
                        ? `当前 ${currentSchoolMeta.icon}${currentSchoolMeta.label}；重选花费 ${RESCHOOL_COST_GOLD} 金币，系数立即切换`
                        : '首选免费，流派系数立即作用于全部战斗属性（含武魂）'}
                </p>
                {/* 六流派卡片：375px 单列堆叠（space-y 纵向排布，无横向挤压） */}
                <div className="space-y-2 mb-4">
                    {Object.entries(SCHOOL_META).map(([key, meta]) => {
                        const unlocked = schoolUnlocked(key, p.level, p.prestigeCount);
                        const isCurrent = p.chosenSchool === key;
                        const hint = schoolUnlockHint(key, p.level, p.prestigeCount);
                        return (
                            <div
                                key={key}
                                data-testid={`school-option-${key}`}
                                className={`rounded-lg border p-3 ${isCurrent ? 'border-indigo-500/70 bg-indigo-500/10' : 'border-line bg-gray-900/30'} ${!unlocked && !isCurrent ? 'opacity-60' : ''}`}
                            >
                                <div className="flex items-center justify-between gap-2">
                                    <div className="min-w-0">
                                        <div className="flex items-center gap-1.5 flex-wrap">
                                            <span className="text-sm font-bold text-gray-200">
                                                <span aria-hidden>{meta.icon}</span> {meta.label}
                                            </span>
                                            {isCurrent && (
                                                <span className="shrink-0 rounded border border-indigo-500/60 bg-indigo-500/30 px-1 py-0.5 text-[10px] font-semibold text-indigo-300 leading-none">
                                                    当前
                                                </span>
                                            )}
                                        </div>
                                        <p className="text-[11px] text-gray-400 mt-0.5 leading-relaxed">{meta.description}</p>
                                        <p className="text-[11px] text-gray-300 mt-0.5 leading-relaxed">系数：{meta.modsSummary}</p>
                                        <p className={`text-[11px] mt-0.5 leading-relaxed ${unlocked ? 'text-gray-500' : 'text-red-300'}`}>{hint}</p>
                                    </div>
                                    <button
                                        type="button"
                                        data-testid={`school-confirm-${key}`}
                                        onClick={() => handleChooseSchool(key)}
                                        disabled={actionLoading || isCurrent || !unlocked}
                                        title={isCurrent
                                            ? '已是当前流派'
                                            : !unlocked
                                                ? hint
                                                : currentSchoolMeta
                                                    ? `重选花费 ${RESCHOOL_COST_GOLD} 金币`
                                                    : '首选免费'}
                                        className="shrink-0 self-center min-h-11 px-3 rounded-lg text-xs font-bold text-white bg-gradient-to-r from-indigo-600 to-blue-600 hover:from-indigo-500 hover:to-blue-500 transition disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:from-indigo-600 disabled:hover:to-blue-600"
                                    >
                                        {isCurrent ? '当前' : currentSchoolMeta ? '重选' : '选择'}
                                    </button>
                                </div>
                            </div>
                        );
                    })}
                </div>
                <button
                    type="button"
                    ref={cancelRef}
                    data-testid="school-cancel"
                    onClick={onClose}
                    disabled={actionLoading}
                    className="w-full min-h-11 bg-gray-700 hover:bg-gray-600 rounded-lg font-bold transition disabled:opacity-50"
                >
                    取消
                </button>
            </div>
        </div>
    );
}
