'use client';

import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import api, { RankEntry } from '@/lib/api';
import { useAsync } from '@/lib/hooks';
import { SkeletonRows, ErrorPanel } from '@/components/StateViews';

const RANK_TYPES = [
    { id: 'level', name: '等级排行', icon: '⚡' },
    { id: 'tower', name: '爬塔排行', icon: '🏰' },
];

/** 前三名领奖台配置：金/银/铜依次对应 rank 1/2/3，底色走 globals.css 调色板变量 */
const PODIUM = [
    { rank: 1, medal: '🥇', label: '冠军', cls: 'dl-podium-gold text-yellow-400', height: 'h-24 sm:h-28' },
    { rank: 2, medal: '🥈', label: '亚军', cls: 'dl-podium-silver text-gray-300', height: 'h-16 sm:h-20' },
    { rank: 3, medal: '🥉', label: '季军', cls: 'dl-podium-bronze text-orange-400', height: 'h-12 sm:h-14' },
];

export default function RankPage() {
    const { user } = useAuth();
    const [activeRank, setActiveRank] = useState('level');
    const [updatedAt, setUpdatedAt] = useState<number | null>(null);
    const { data: rankData, loading, error, refresh } = useAsync(
        () => activeRank === 'level' ? api.getLevelRank(50) : api.getTowerRank(50),
        // 首帧不发请求，统一交给下方按 activeRank 去重的 effect，避免 StrictMode 双挂载重复拉取
        { immediate: false }
    );

    // 数据成功落地时记录「更新于」时间戳（error 清零时不更新）
    const prevDataRef = useRef<RankEntry[] | null>(null);
    useEffect(() => {
        if (rankData && rankData !== prevDataRef.current) {
            prevDataRef.current = rankData;
            setUpdatedAt(Date.now());
        }
    }, [rankData]);

    // 进入时拉一次 + 切换榜单类型再拉；prevRankRef 记住已拉取的榜单，StrictMode 双挂载时值不变则跳过
    const prevRankRef = useRef<string | null>(null);
    useEffect(() => {
        if (prevRankRef.current === activeRank) return;
        prevRankRef.current = activeRank;
        queueMicrotask(refresh);
    }, [activeRank, refresh]);

    // 榜单进入时拉一次 + 手动刷新即可（permitAll 静态数据，省流量：不做轮询/WS 自动刷新）
    const handleRefresh = () => {
        void refresh();
    };

    const getRankIcon = (rank: number) => {
        if (rank === 1) return '🥇';
        if (rank === 2) return '🥈';
        if (rank === 3) return '🥉';
        return `#${rank}`;
    };

    const list = rankData ?? [];
    const top3 = PODIUM.map((p) => ({ ...p, entry: list.find((e) => e.rank === p.rank) }));
    const rest = list.filter((e) => e.rank > 3);

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h1 className="text-2xl font-bold text-yellow-400">排行榜</h1>
                <div className="flex items-center gap-3">
                    {updatedAt != null && (
                        <span data-testid="rank-updated" className="text-xs text-gray-400">
                            更新于 {new Date(updatedAt).toLocaleTimeString()}
                        </span>
                    )}
                    <button
                        type="button"
                        data-testid="rank-refresh"
                        onClick={handleRefresh}
                        disabled={loading}
                        className="px-3 py-1.5 max-sm:min-h-11 rounded-lg text-sm border border-line bg-surface-soft/80 hover:bg-surface-soft transition-colors inline-flex items-center gap-2"
                    >
                        <span aria-hidden className={loading ? 'dl-spinner' : ''}>{loading ? '' : '↻'}</span>
                        {loading ? '刷新中...' : '刷新'}
                    </button>
                </div>
            </div>

            {/* 排行榜类型选择 */}
            <div className="flex border-b border-gray-700" role="tablist">
                {RANK_TYPES.map((rankType) => (
                    <button
                        key={rankType.id}
                        role="tab"
                        aria-selected={activeRank === rankType.id}
                        className={`min-h-11 py-2 px-4 font-semibold flex items-center gap-2 transition-colors ${
                            activeRank === rankType.id
                                ? 'text-yellow-400 border-b-2 border-yellow-400'
                                : 'text-gray-400 hover:text-foreground'
                        }`}
                        onClick={() => setActiveRank(rankType.id)}
                    >
                        <span>{rankType.icon}</span>
                        {rankType.name}
                    </button>
                ))}
            </div>

            {/* 排行榜主体：骨架 / 错误重试 / 空态 / 领奖台+列表 */}
            <div className="bg-gray-800 rounded-lg overflow-hidden">
                {loading && list.length === 0 ? (
                    <SkeletonRows rows={8} testId="rank-skeleton" />
                ) : error && list.length === 0 ? (
                    <ErrorPanel message={error} onRetry={handleRefresh} testId="rank-error" retryTestId="rank-retry" />
                ) : !error && list.length === 0 && !loading ? (
                    <div className="text-center py-8 text-gray-500">暂无数据</div>
                ) : (
                    <>
                        {/* 前三名领奖台（≥3 条数据才展示；刷新失败保留旧数据 + 顶部提示） */}
                        {error && (
                            <div role="alert" className="dl-shake px-4 py-2 bg-red-500/20 border-b border-red-500/50 text-red-300 text-sm">
                                {error}，正在显示上次数据
                            </div>
                        )}
                        {list.length >= 3 && (
                            <div data-testid="rank-podium" className="dl-pop grid grid-cols-3 items-end gap-2 sm:gap-4 p-4 sm:p-6">
                                {[top3[1], top3[0], top3[2]].map(({ rank, medal, label, cls, height, entry }) => (
                                    <div key={rank} data-testid={`rank-podium-${rank}`} className={`rounded-lg p-3 text-center ${cls}`}>
                                        <div className="text-2xl sm:text-3xl" aria-hidden>{medal}</div>
                                        <div className="font-bold truncate mt-1">{entry?.nickname ?? label}</div>
                                        {entry && (
                                            <div className="text-sm text-gray-300 mt-0.5">{entry.score.toLocaleString()}</div>
                                        )}
                                        <div className={`mt-2 rounded-t-md ${height} flex items-start justify-center pt-1`}>
                                            <span className="text-xs opacity-80">{label}</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        <div data-testid="rank-list" className="divide-y divide-gray-700">
                            {(list.length >= 3 ? rest : list).map((entry, i) => (
                                <div
                                    key={entry.userId}
                                    data-testid={`rank-row-${entry.rank}`}
                                    className={`dl-fade-up p-4 flex items-center justify-between ${
                                        entry.userId === user?.userId ? 'bg-yellow-900/30' : ''
                                    }`}
                                    style={i < 10 ? { animationDelay: `${i * 40}ms` } : undefined}
                                >
                                    <div className="flex items-center gap-4 min-w-0">
                                        <div className="w-12 shrink-0 text-center font-bold text-lg">
                                            {getRankIcon(entry.rank)}
                                        </div>
                                        <div className="min-w-0">
                                            <div className="font-semibold truncate">
                                                {entry.nickname}
                                                {entry.userId === user?.userId && (
                                                    <span className="ml-2 text-yellow-400 text-sm">(我)</span>
                                                )}
                                            </div>
                                            {entry.extraData && (
                                                <div className="text-sm text-gray-400">
                                                    {entry.extraData}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                    <div className="text-right shrink-0">
                                        <div className="font-semibold text-yellow-400">
                                            {entry.score.toLocaleString()}
                                        </div>
                                        <div className="text-sm text-gray-400">
                                            {activeRank === 'level' ? '等级' : '层数'}
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                        {/* 刷新中：旧数据之上叠一层半透明蒙层，不闪骨架 */}
                        {loading && (
                            <div aria-hidden className="h-1 dl-skeleton rounded-none" />
                        )}
                    </>
                )}
            </div>

            {/* 排行榜说明 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">排行榜说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• 等级排行：按玩家等级排序</li>
                    <li>• 爬塔排行：按杀戮之都层数排序</li>
                    <li>• 点击「刷新」获取最新榜单（每小时更新一次）</li>
                    <li>• 提升实力，争取更高排名</li>
                </ul>
            </div>
        </div>
    );
}
