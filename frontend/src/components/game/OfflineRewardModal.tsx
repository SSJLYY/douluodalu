'use client';

import type { OfflineReward } from '@/lib/api';

/**
 * 离线收益弹窗（自 game/page.tsx 拆出，DOM 与拆分前逐字节一致）：
 * 纯展示组件——领取请求仍留在页面 effect 里（保证挂载即领取的时机不变），关闭经 onDismiss 上抛。
 */
export default function OfflineRewardModal({
    offline,
    onDismiss,
}: {
    offline: OfflineReward | null;
    onDismiss: () => void;
}) {
    if (!offline) return null;

    return (
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
                    onClick={onDismiss}
                    className="w-full py-2 bg-gradient-to-r from-yellow-600 to-orange-600 hover:from-yellow-500 hover:to-orange-500 rounded-lg font-bold transition"
                >
                    收下
                </button>
            </div>
        </div>
    );
}
