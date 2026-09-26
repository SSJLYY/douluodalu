'use client';

import type { DailyQuest } from '@/lib/api';

/**
 * 每日任务卡（任务固定 5 条，后端按日重置）：纯展示组件，领奖回调由页面传入（页内接 runAction）。
 * 后端响应升级前 gameState.dailyQuests 缺失 → 整卡 return null 不渲染
 * （与签到卡的 EmptyPanel 降级不同：任务是纯增量功能，缺失即隐藏，不留空卡）。
 */
export default function DailyQuestsCard({
    quests,
    actionLoading = false,
    onClaim,
}: {
    quests?: DailyQuest[];
    actionLoading?: boolean;
    onClaim: (questId: string) => void;
}) {
    if (!quests || quests.length === 0) return null;

    const claimedCount = quests.filter((q) => q.claimed).length;
    const allClaimed = claimedCount === quests.length;

    return (
        <div className="dl-fade-up [animation-delay:160ms] bg-surface/80 rounded-xl p-4 border border-line" data-testid="daily-quests-card">
            <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-3">
                <h2 className="text-sm font-semibold text-orange-400">
                    <span aria-hidden>📋</span> 每日任务
                </h2>
                {/* 全清进度徽标：全部领取后转绿提示今日已清 */}
                <span
                    data-testid="daily-quests-progress"
                    className={`px-2 py-0.5 rounded border text-xs ${
                        allClaimed
                            ? 'bg-green-500/20 border-green-500 text-green-400'
                            : 'bg-yellow-600/20 border-yellow-600 text-yellow-400'
                    }`}
                >
                    已领取 {claimedCount}/{quests.length}
                </span>
            </div>

            <div className="space-y-3">
                {quests.map((q) => {
                    const claimable = !q.claimed && q.progress >= q.target;
                    const pct = Math.min(100, Math.round((q.progress / Math.max(1, q.target)) * 100));
                    // 奖励摘要：0 值币种不显示；窄屏隐藏文字、由 title 承接（行内只留描述/进度/按钮）
                    const rewardText = [
                        q.rewardGold > 0 ? `+${q.rewardGold.toLocaleString()}金币` : '',
                        q.rewardBossCoin > 0 ? `+${q.rewardBossCoin.toLocaleString()}Boss币` : '',
                        q.rewardSoulPower > 0 ? `+${q.rewardSoulPower.toLocaleString()}魂力` : '',
                    ].filter(Boolean).join(' ');

                    return (
                        <div key={q.id} data-testid={`quest-row-${q.id}`} className="flex items-center gap-3">
                            {/* 左侧：描述 + 进度（min-w-0 可收缩，描述超长截断，窄屏不挤出容器） */}
                            <div className="flex-1 min-w-0">
                                <div className="flex items-baseline justify-between gap-2 mb-1">
                                    <span className="text-sm text-gray-300 truncate min-w-0" title={q.description}>
                                        {q.description}
                                    </span>
                                    <span className="text-xs text-gray-400 tabular-nums shrink-0">
                                        {q.progress}/{q.target}
                                    </span>
                                </div>
                                <div
                                    className="h-2 bg-gray-700 rounded-full overflow-hidden"
                                    role="progressbar"
                                    aria-valuemin={0}
                                    aria-valuemax={q.target}
                                    aria-valuenow={q.progress}
                                    aria-label={`${q.description}进度`}
                                >
                                    {/* dl-hp-bar：宽度过渡复用既有类（globals.css 已登记 reduced-motion 关闭） */}
                                    <div
                                        className={`dl-hp-bar h-full rounded-full ${claimable || q.claimed ? 'bg-green-500' : 'bg-blue-500'}`}
                                        style={{ width: `${pct}%` }}
                                    />
                                </div>
                                {rewardText && (
                                    <div className="mt-1 text-[11px] text-gray-400 tabular-nums truncate" title={rewardText}>
                                        <span className="hidden sm:inline">{rewardText}</span>
                                    </div>
                                )}
                            </div>

                            {/* 右侧：领取按钮（shrink-0，三态：可领高亮 / 未完成 / 已领取） */}
                            <button
                                type="button"
                                data-testid={`quest-claim-${q.id}`}
                                onClick={() => onClaim(q.id)}
                                disabled={q.claimed || !claimable || actionLoading}
                                className={`shrink-0 self-center min-h-11 px-3 rounded-lg text-xs sm:text-sm font-bold transition-colors disabled:opacity-50 ${
                                    claimable
                                        ? 'bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-500 hover:to-emerald-500 text-white dl-btn-sheen'
                                        : 'bg-gray-700'
                                }`}
                            >
                                {q.claimed ? '✓ 已领取' : claimable ? '领取' : '未完成'}
                            </button>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
