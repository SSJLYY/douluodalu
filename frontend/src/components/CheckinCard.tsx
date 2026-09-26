'use client';

import { MAKEUP_COST_GOLD } from '@/lib/api';
import type { CheckInStatus } from '@/lib/api';
import { EmptyPanel } from '@/components/StateViews';

/**
 * 每日签到卡（7 日循环）：奖励表由后端下发（rewards），前端不硬编码数值。
 * 后端响应升级前 gameState.checkIn 可能缺失/为空 → 整卡降级为标题+「暂不可用」灰字，不留破图。
 * 补签条：makeupAvailable=true 才渲染（后端判定昨日漏签可补）；旧后端缺失 → 纯增量不显示。
 */
export default function CheckinCard({
    checkIn,
    actionLoading,
    onCheckin,
    onMakeup,
}: {
    checkIn?: CheckInStatus;
    actionLoading: boolean;
    onCheckin: () => void;
    /** 补签回调（页面用 runAction 包装 api.makeupCheckin）；仅补签条渲染时可触达 */
    onMakeup?: () => void;
}) {
    const rewards = checkIn?.rewards ? [...checkIn.rewards].sort((a, b) => a.day - b.day) : [];
    const usable = rewards.length > 0;
    const signed = Boolean(checkIn?.signedToday);
    const nextDay = checkIn?.nextCycleDay ?? 1;
    // 已领取格判定：未签时 day < nextCycleDay；已签时 day <= 本次 cycleDay
    // （本次 cycleDay = nextCycleDay 的前一天，7→1 环绕时取 7）
    const signedCycleDay = signed ? (nextDay === 1 ? 7 : nextDay - 1) : 0;
    const isClaimed = (day: number) => (signed ? day <= signedCycleDay : day < nextDay);

    return (
        <div className="dl-fade-up [animation-delay:80ms] bg-surface/80 rounded-xl p-4 border border-line" data-testid="checkin-card">
            <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-3">
                <h2 className="text-sm font-semibold text-orange-400">
                    <span aria-hidden>📅</span> 每日签到
                </h2>
                {usable && (
                    <div className="flex items-center gap-2 text-xs">
                        <span className="px-2 py-0.5 rounded bg-yellow-600/20 border border-yellow-600 text-yellow-400">
                            连续 {checkIn?.streak ?? 0} 天
                        </span>
                        <span className="text-gray-400">累计 {checkIn?.totalDays ?? 0} 天</span>
                    </div>
                )}
            </div>

            {!usable ? (
                // 降级路径：后端未升级（checkIn 缺失 / rewards 空）→ 空态灰字，不渲染按钮与破图
                <EmptyPanel message="签到功能暂不可用" testId="checkin-empty" />
            ) : (
                <>
                    {/* 7 天奖励网格：窄屏 4 列（4+3 两行）避免 7 列过挤，sm 起单行 7 列；零水平溢出 */}
                    <div className="grid grid-cols-4 sm:grid-cols-7 gap-2">
                        {rewards.map((r) => {
                            const isToday = !signed && r.day === nextDay;
                            const claimed = isClaimed(r.day);
                            return (
                                <div
                                    key={r.day}
                                    data-testid={`checkin-day-${r.day}`}
                                    aria-current={isToday ? 'date' : undefined}
                                    className={`rounded-lg p-2 border text-center min-w-0 ${
                                        isToday
                                            ? 'border-yellow-500 bg-yellow-500/15'
                                            : claimed
                                                ? 'border-line bg-gray-700/40 opacity-60'
                                                : 'border-line bg-gray-700/50'
                                    }`}
                                >
                                    <div className="text-xs font-medium flex items-center justify-center gap-1">
                                        {claimed && <span className="text-green-400" aria-hidden>✓</span>}
                                        <span className={isToday ? 'text-yellow-400' : 'text-gray-300'}>第{r.day}天</span>
                                    </div>
                                    {isToday && <div className="text-[10px] font-bold text-yellow-400">今日</div>}
                                    {/* 奖励摘要：数值为 0 的币种不显示；CJK 可自然换行，窄屏不溢出 */}
                                    <div className="mt-1 space-y-0.5 text-[11px] leading-tight">
                                        {r.gold > 0 && <div className="text-yellow-400 tabular-nums">+{r.gold.toLocaleString()}金币</div>}
                                        {r.bossCoin > 0 && <div className="text-purple-400 tabular-nums">+{r.bossCoin.toLocaleString()}Boss币</div>}
                                        {r.soulPower > 0 && <div className="text-blue-400 tabular-nums">+{r.soulPower.toLocaleString()}魂力</div>}
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <button
                        type="button"
                        data-testid="checkin-btn"
                        onClick={onCheckin}
                        disabled={signed || actionLoading}
                        className={`w-full mt-3 px-4 min-h-11 rounded-lg font-bold transition disabled:opacity-50 ${
                            signed
                                ? 'bg-gray-700'
                                : 'bg-gradient-to-r from-yellow-600 to-orange-600 hover:from-yellow-500 hover:to-orange-500 dl-btn-sheen'
                        }`}
                    >
                        {signed ? '今日已签 ✓' : actionLoading ? '签到中...' : '签到'}
                    </button>

                    {/* 补签条：只修复连签、不补发当日奖励（文案写明）；补签成功后 runAction 自动 refresh
                        → makeupAvailable 变 false → 整条消失、连续/累计徽标更新。金币不足等失败由后端 message 提示。
                        按钮为 secondary 风格（border-line），弱于主签到按钮；窄屏文案与按钮单列堆叠，375px 零溢出。 */}
                    {checkIn?.makeupAvailable && (
                        <div
                            data-testid="makeup-row"
                            className="mt-2 flex flex-col sm:flex-row sm:items-center gap-2 rounded-lg border border-line bg-gray-700/30 p-2.5"
                        >
                            <p className="min-w-0 text-xs leading-relaxed text-gray-400">
                                昨日漏签了！花费 {MAKEUP_COST_GOLD} 金币补签，修复连续签到（不补发当日奖励）
                            </p>
                            <button
                                type="button"
                                data-testid="makeup-btn"
                                onClick={onMakeup}
                                disabled={actionLoading}
                                className="shrink-0 w-full sm:w-auto min-h-11 px-3 rounded-lg text-xs sm:text-sm font-bold text-gray-200 border border-line bg-gray-700/50 hover:bg-gray-700 transition-colors disabled:opacity-50"
                            >
                                补签昨日（{MAKEUP_COST_GOLD}金币）
                            </button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
