'use client';

import { useRef } from 'react';
import api from '@/lib/api';
import { useModalEscape } from '@/lib/hooks';
import { awakenToast, REAWAKEN_COST_GOLD, soulPoolHint } from '@/lib/soul';
import { useGameData } from '@/contexts/GameDataContext';

/**
 * 重醒确认弹窗（5000 金），自 game/page.tsx 拆出（DOM 与拆分前逐字节一致）：照转生弹窗模板
 * （role=dialog/aria-modal、Escape/遮罩关闭、打开聚焦取消钮），列明花费与当前品质池范围
 * （随转数扩展，见 lib/soul.ts）；确认才发 /api/action/awaken。可达性经 useModalEscape 收敛。
 */
export default function ReawakenDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
    const { gameState, actionLoading, runAction, setMessage } = useGameData();
    const cancelRef = useRef<HTMLButtonElement>(null);
    useModalEscape(open, onClose, cancelRef);

    // 重醒（5000 金）：确认弹窗内才发请求，成功/失败路径都关弹窗（照转生弹窗惯例，失败不打断重试）。
    const handleReawaken = async () => {
        await runAction(
            () => api.awaken(),
            (r) => setMessage(awakenToast(r)),
            '重醒失败',
        );
        onClose();
    };

    if (!open || !gameState) return null;

    const p = gameState.profile;

    return (
        <div
            className="dl-fade-in fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
            role="dialog"
            aria-modal="true"
            aria-label="武魂重醒确认"
            data-testid="reawaken-dialog"
            onClick={onClose}
        >
            <div
                className="dl-pop bg-surface border border-yellow-500/50 rounded-2xl p-6 max-w-sm w-full shadow-2xl max-h-[85vh] overflow-y-auto"
                onClick={(e) => e.stopPropagation()}
            >
                <h3 className="text-lg font-bold text-yellow-400 mb-3">
                    <span aria-hidden>♻️</span> 武魂重醒
                </h3>
                <p className="text-sm text-gray-400 mb-3">
                    重醒将随机获得当前品质池内的新武魂（{soulPoolHint(p.prestigeCount)}），武魂名与属性随之改变，请确认：
                </p>
                <ul className="text-xs space-y-1 mb-4 text-gray-300 bg-gray-900/30 border border-line rounded-lg p-3">
                    <li>• 花费 <span className="text-yellow-400">{REAWAKEN_COST_GOLD} 金币</span>（当前拥有 {p.gold.toLocaleString()}）</li>
                    <li>• 新武魂品质在当前品质池内随机，可能低于当前品质</li>
                    <li>• 转生不会清除武魂；转数越高品质池越好</li>
                </ul>
                <div className="grid grid-cols-2 gap-3">
                    <button
                        ref={cancelRef}
                        data-testid="reawaken-cancel"
                        onClick={onClose}
                        disabled={actionLoading}
                        className="py-2 bg-gray-700 hover:bg-gray-600 rounded-lg font-bold transition disabled:opacity-50"
                    >
                        取消
                    </button>
                    <button
                        data-testid="reawaken-confirm"
                        onClick={handleReawaken}
                        disabled={actionLoading}
                        className="py-2 bg-gradient-to-r from-yellow-600 to-amber-600 hover:from-yellow-500 hover:to-amber-500 rounded-lg font-bold transition disabled:opacity-50"
                    >
                        {actionLoading ? '重醒中...' : '确认重醒'}
                    </button>
                </div>
            </div>
        </div>
    );
}
