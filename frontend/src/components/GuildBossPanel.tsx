'use client';

import type { GuildBossRank, GuildBossResult, GuildBossStatus } from '@/lib/api';
import BossRankPanel from '@/components/BossRankPanel';

/**
 * 宗门 Boss 卡内容（照 BossRankPanel 提取先例从宗门页抽出便于测试；页面仅保留 state/拉取/挑战回调）：
 * - 血条：bossHp/bossMaxHp 为全宗门共享的周血池（每周一重置），progressbar 三 aria 值 +
 *   aria-label「宗门Boss血量」，红色系 bar，宽度过渡复用 .dl-hp-bar（reduced-motion 下 CSS 侧即时切换）；
 * - killed=true：血条归零态（aria/数字/宽度一致强制归零，防御后端不一致数据）+ 覆盖横幅
 *   「☠ 本周 Boss 已被击杀」（☠ aria-hidden），挑战按钮禁用并以 title 说明下周一重生；
 * - 挑战结果行：伤害/Boss生命；killed=true 时追加「🎉 全员协力击杀！」高亮（message 已含说明，二次强调）；
 *   bossResult.killed 缺失（旧后端）→ 不渲染高亮；
 * - 降级容错：bossStatus=null（旧后端无 /boss/status 端点 404 或拉取失败）→ 血条区整块隐藏，挑战照旧。
 * 375px：血条/横幅/按钮均 w-full 单列，数字行 tabular-nums，无固定宽度零溢出。
 */
export default function GuildBossPanel({
    bossStatus,
    bossResult,
    rank,
    myUserId,
    loading,
    onChallenge,
}: {
    /** GET /api/guild/boss/status 结果；null = 未就绪/旧后端失败 → 血条区隐藏（静默降级） */
    bossStatus: GuildBossStatus | null;
    /** 最近一次挑战响应（页面级 state，页面切换不丢） */
    bossResult: GuildBossResult | null;
    /** 本周伤害榜数据；null = 未就绪/失败 → 榜块整块隐藏（与 BossRankPanel 调用方同口径） */
    rank: GuildBossRank | null;
    /** 当前登录玩家 id（榜单「我」高亮）；未就绪传 null 不高亮 */
    myUserId: number | null;
    /** 页面级 actionLoading：挑战请求进行中禁用按钮 */
    loading: boolean;
    /** 挑战回调：页面负责调 API、错误提示（含 400 已被击杀 message）与血池/周榜刷新 */
    onChallenge: () => void;
}) {
    const killed = bossStatus?.killed ?? false;
    // killed 归零态：展示值（aria/数字/宽度）统一按 0 渲染——契约后端 killed=true 时本就返回
    // bossHp=0，此处仅防御不一致数据导致「横幅说已击杀、数字却还有血」的矛盾展示
    const shownHp = killed ? 0 : (bossStatus?.bossHp ?? 0);
    const bossMaxHp = bossStatus?.bossMaxHp ?? 0;
    // 除零/越界防护：maxHp≤0 视为 0%（不出 NaN），百分比夹取到 [0,100]
    const pct = killed || bossMaxHp <= 0 ? 0 : Math.min(100, Math.max(0, (shownHp / bossMaxHp) * 100));

    return (
        <>
            <h2 className="text-lg font-semibold mb-3 text-red-400">宗门 Boss</h2>
            <p className="text-sm text-gray-200 mb-4">挑战宗门 Boss，可获得金币、Boss币和装备掉落。</p>

            {bossStatus && (
                <div className="mb-4">
                    <div className="relative">
                        <div
                            data-testid="boss-hp-bar"
                            role="progressbar"
                            aria-valuemin={0}
                            aria-valuemax={bossMaxHp}
                            aria-valuenow={shownHp}
                            aria-label="宗门Boss血量"
                            className="h-3 bg-gray-700 rounded-full overflow-hidden"
                        >
                            {/* dl-hp-bar：宽度过渡复用既有类（globals.css 已登记 reduced-motion 关闭） */}
                            <div className="dl-hp-bar h-full rounded-full bg-red-500" style={{ width: `${pct}%` }} />
                        </div>
                        <div data-testid="boss-hp-text" className="mt-1.5 text-xs text-gray-200 tabular-nums">
                            血量 {shownHp.toLocaleString()} / {bossMaxHp.toLocaleString()}
                        </div>
                        {killed && (
                            <div
                                data-testid="boss-killed-banner"
                                className="absolute inset-0 flex items-center justify-center gap-1 rounded text-xs font-semibold text-red-300 bg-gray-900/70"
                            >
                                <span aria-hidden>☠</span> 本周 Boss 已被击杀
                            </div>
                        )}
                    </div>
                </div>
            )}

            <button
                onClick={onChallenge}
                disabled={loading || killed}
                title={killed ? '本周 Boss 已被击杀，下周一重置重生后可再挑战' : undefined}
                className="w-full px-4 min-h-11 bg-red-600 hover:bg-red-700 disabled:bg-gray-600 rounded font-medium"
            >
                挑战 Boss
            </button>

            {bossResult && (
                <div className="mt-3 text-sm text-gray-100">
                    伤害 {bossResult.damage} / Boss生命 {bossResult.bossHp}
                    {bossResult.killed && (
                        <div data-testid="boss-kill-highlight" className="mt-1 font-semibold text-yellow-400">
                            <span aria-hidden>🎉</span> 全员协力击杀！
                        </div>
                    )}
                </div>
            )}

            {/* 本周伤害榜：数据未就绪/拉取失败静默整块隐藏 */}
            {rank && <BossRankPanel rank={rank} myUserId={myUserId} />}
        </>
    );
}
