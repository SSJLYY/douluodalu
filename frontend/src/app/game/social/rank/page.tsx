'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import api, { RankEntry } from '@/lib/api';

const RANK_TYPES = [
    { id: 'level', name: '等级排行', icon: '⚡', api: 'getLevelRank' },
    { id: 'tower', name: '爬塔排行', icon: '🏰', api: 'getTowerRank' },
];

export default function RankPage() {
    const { user } = useAuth();
    const [activeRank, setActiveRank] = useState('level');
    const [rankData, setRankData] = useState<RankEntry[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        loadRankData();
    }, [activeRank]);

    const loadRankData = async () => {
        setLoading(true);
        try {
            const rankType = RANK_TYPES.find(r => r.id === activeRank);
            if (!rankType) return;

            let data: RankEntry[];
            if (rankType.api === 'getLevelRank') {
                data = await api.getLevelRank(50);
            } else if (rankType.api === 'getTowerRank') {
                data = await api.getTowerRank(50);
            } else {
                data = [];
            }
            setRankData(data);
        } catch (err) {
            console.error('加载排行榜失败:', err);
        } finally {
            setLoading(false);
        }
    };

    const getRankIcon = (rank: number) => {
        if (rank === 1) return '🥇';
        if (rank === 2) return '🥈';
        if (rank === 3) return '🥉';
        return `#${rank}`;
    };

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">排行榜</h1>

            {/* 排行榜类型选择 */}
            <div className="flex border-b border-gray-700">
                {RANK_TYPES.map((rankType) => (
                    <button
                        key={rankType.id}
                        className={`py-2 px-4 font-semibold flex items-center gap-2 ${
                            activeRank === rankType.id
                                ? 'text-yellow-400 border-b-2 border-yellow-400'
                                : 'text-gray-400 hover:text-white'
                        }`}
                        onClick={() => setActiveRank(rankType.id)}
                    >
                        <span>{rankType.icon}</span>
                        {rankType.name}
                    </button>
                ))}
            </div>

            {/* 排行榜列表 */}
            <div className="bg-gray-800 rounded-lg overflow-hidden">
                {loading ? (
                    <div className="text-center py-8">加载中...</div>
                ) : rankData.length === 0 ? (
                    <div className="text-center py-8 text-gray-500">暂无数据</div>
                ) : (
                    <div className="divide-y divide-gray-700">
                        {rankData.map((entry, index) => (
                            <div 
                                key={entry.userId}
                                className={`p-4 flex items-center justify-between ${
                                    entry.userId === user?.userId ? 'bg-yellow-900/30' : ''
                                }`}
                            >
                                <div className="flex items-center gap-4">
                                    <div className="w-12 text-center font-bold text-lg">
                                        {getRankIcon(entry.rank)}
                                    </div>
                                    <div>
                                        <div className="font-semibold">
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
                                <div className="text-right">
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
                )}
            </div>

            {/* 排行榜说明 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">排行榜说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• 等级排行：按玩家等级排序</li>
                    <li>• 爬塔排行：按杀戮之都层数排序</li>
                    <li>• 排行榜每小时更新一次</li>
                    <li>• 提升实力，争取更高排名</li>
                </ul>
            </div>
        </div>
    );
}