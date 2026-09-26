'use client';

import type { GuildBossRank, GuildBossRankEntry } from '@/lib/api';

/**
 * 前三名领奖台配置：照排行榜页（social/rank）PODIUM——金/银/铜依次对应 rank 1/2/3，
 * 底色走 globals.css 调色板变量（dl-podium-*）。
 */
const PODIUM = [
    { rank: 1, medal: '🥇', label: '冠军', cls: 'dl-podium-gold text-yellow-400', height: 'h-24 sm:h-28' },
    { rank: 2, medal: '🥈', label: '亚军', cls: 'dl-podium-silver text-gray-300', height: 'h-16 sm:h-20' },
    { rank: 3, medal: '🥉', label: '季军', cls: 'dl-podium-bronze text-orange-400', height: 'h-12 sm:h-14' },
];

/** 后端 entries 只带 userId/nickname/weeklyDamage（降序），名次按数组下标推导 */
type RankedEntry = GuildBossRankEntry & { rank: number };

/**
 * 宗门 Boss 周榜（纯展示组件，照 CheckinCard 提取先例从宗门页抽出便于测试）：
 * - 满 3 条才立领奖台（照排行榜页惯例，避免空台位），不足 3 条全部走列表；
 * - 4-10 名列表副行展示 weeklyDamage.toLocaleString()，自己的行按 userId 高亮；
 * - 空榜灰字「本周暂无 Boss 伤害记录」；拉取失败由调用方置空整块不渲染（静默降级）。
 * 375px：领奖台 grid-cols-3 同排行榜页缩放，列表行 truncate + shrink-0 零溢出。
 */
export default function BossRankPanel({
    rank,
    myUserId = null,
}: {
    rank: GuildBossRank;
    /** 当前登录玩家 id（useAuth().user?.userId），用于「我」高亮；未就绪时不高亮 */
    myUserId?: number | null;
}) {
    const entries: RankedEntry[] = rank.entries.map((e, i) => ({ ...e, rank: i + 1 }));
    const showPodium = entries.length >= 3;
    const top3 = PODIUM.map((p) => ({ ...p, entry: entries.find((e) => e.rank === p.rank) }));
    const rest = showPodium ? entries.filter((e) => e.rank > 3) : entries;

    return (
        <div data-testid="boss-rank" className="mt-4 border-t border-gray-600 pt-4">
            <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-2">
                <h3 className="text-base font-semibold text-orange-400">
                    <span aria-hidden>📊</span> 本周伤害榜
                </h3>
                {rank.myRank > 0 && (
                    <span data-testid="boss-rank-my" className="text-xs text-gray-300 tabular-nums shrink-0">
                        我的排名 #{rank.myRank}
                    </span>
                )}
            </div>

            {entries.length === 0 ? (
                <p data-testid="boss-rank-empty" className="text-center py-4 text-sm text-gray-400">
                    本周暂无 Boss 伤害记录
                </p>
            ) : (
                <>
                    {showPodium && (
                        <div data-testid="boss-rank-podium" className="dl-pop grid grid-cols-3 items-end gap-2 p-2 mb-1">
                            {/* 展示顺序照排行榜页：亚军/冠军/季军（冠军居中最高） */}
                            {[top3[1], top3[0], top3[2]].map(({ rank: r, medal, label, cls, height, entry }) => (
                                <div key={r} data-testid={`boss-rank-podium-${r}`} className={`rounded-lg p-2 text-center min-w-0 ${cls}`}>
                                    <div className="text-xl sm:text-2xl" aria-hidden>{medal}</div>
                                    <div className="font-bold truncate mt-1 text-sm">{entry?.nickname ?? label}</div>
                                    {entry && (
                                        <div className="text-xs text-gray-300 mt-0.5 tabular-nums">{entry.weeklyDamage.toLocaleString()}</div>
                                    )}
                                    <div className={`mt-2 rounded-t-md ${height} flex items-start justify-center pt-1`}>
                                        <span className="text-[10px] opacity-80">{label}</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                    <div data-testid="boss-rank-list" className="divide-y divide-gray-700">
                        {rest.map((entry) => {
                            const isSelf = entry.userId === myUserId;
                            return (
                                <div
                                    key={entry.userId}
                                    data-testid={`boss-rank-row-${entry.rank}`}
                                    className={`py-2.5 flex items-center gap-3 min-w-0 ${isSelf ? 'bg-yellow-900/30' : ''}`}
                                >
                                    <div className="w-8 shrink-0 text-center font-bold text-sm text-gray-300">
                                        #{entry.rank}
                                    </div>
                                    <div className="min-w-0">
                                        <div className="font-semibold truncate">
                                            {entry.nickname}
                                            {isSelf && <span className="ml-2 text-yellow-400 text-xs">(我)</span>}
                                        </div>
                                        {/* 副行：本周伤害（千分位） */}
                                        <div className="text-sm text-gray-400 tabular-nums">
                                            {entry.weeklyDamage.toLocaleString()}
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </>
            )}
        </div>
    );
}
