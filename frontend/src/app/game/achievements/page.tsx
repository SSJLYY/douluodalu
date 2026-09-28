'use client';

import { normalizeAchievements } from '@/lib/api';
import { useGameData } from '@/contexts/GameDataContext';
import AchievementsPanel from '@/components/AchievementsPanel';
import { BootState } from '@/components/StateViews';

/**
 * 成就页：useGameData 首屏门控（照 rank 页三态模式）+ AchievementsPanel 纯展示。
 * gameState.achievements 经 normalizeAchievements 形状守卫：
 * 旧后端 string[] / 字段缺失 → 降级空数组，面板内走 EmptyPanel，不留破图。
 */
export default function AchievementsPage() {
    const { gameState, loadError, refresh } = useGameData();

    if (!gameState) {
        return (
            <div className="space-y-6">
                <h1 className="text-2xl font-bold text-yellow-400">成就</h1>
                <BootState error={loadError} onRetry={refresh} rows={6} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">成就</h1>
            <AchievementsPanel achievements={normalizeAchievements(gameState.achievements)} />
        </div>
    );
}
