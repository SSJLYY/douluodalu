'use client';

import { useRef } from 'react';
import api from '@/lib/api';
import { useModalEscape } from '@/lib/hooks';
import { useGameData } from '@/contexts/GameDataContext';

/**
 * 转生确认弹窗（神位传承），自 game/page.tsx 拆出（DOM 与拆分前逐字节一致）：
 * 不直接执行，先列明重置项/保留项；点遮罩或取消关闭，确认才发请求。
 * 可达性经 useModalEscape 收敛（打开聚焦「取消」、Escape 关闭），原三处复制 effect 由此替换。
 */
export default function PrestigeDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
    const { gameState, actionLoading, runMessageAction } = useGameData();
    const cancelRef = useRef<HTMLButtonElement>(null);
    useModalEscape(open, onClose, cancelRef);

    // 转生（神位传承）：等级不足时后端也是 200 + success:false（照 breakthrough 惯例）→ 透传 message。
    // 成功/失败路径都关闭弹窗（message 在战斗卡内展示），失败不打断用户修正后重试。
    const handlePrestige = async () => {
        await runMessageAction(
            () => api.prestige(),
            '转生失败',
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
            aria-label="神位传承确认"
            data-testid="prestige-dialog"
            onClick={onClose}
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
                        ref={cancelRef}
                        data-testid="prestige-cancel"
                        onClick={onClose}
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
    );
}
