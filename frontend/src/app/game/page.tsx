'use client';

import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import api, { OfflineReward, normalizeAchievements } from '@/lib/api';
import { useGameData } from '@/contexts/GameDataContext';
import { useLiveAnnouncement } from '@/lib/live';
import { BootState } from '@/components/StateViews';
import CheckinCard from '@/components/CheckinCard';
import DailyQuestsCard from '@/components/DailyQuestsCard';
import PowerDetailPanel from '@/components/PowerDetailPanel';
import PlayerStatusBar from '@/components/game/PlayerStatusBar';
import BattlePanel from '@/components/game/BattlePanel';
import QuickNav from '@/components/game/QuickNav';
import OfflineRewardModal from '@/components/game/OfflineRewardModal';
import PrestigeDialog from '@/components/game/PrestigeDialog';
import ReawakenDialog from '@/components/game/ReawakenDialog';
import SchoolDialog from '@/components/game/SchoolDialog';

// 模块级标记：跨组件StrictMode双挂载/客户端导航只领取一次离线收益（刷新页面会重新领取，符合放置游戏惯例）
let offlineClaimAttempted = false;

/**
 * 战斗主页：状态/行为经 GameDataContext 消费（与布局顶栏同一实例，轮询/WS 只有一份）。
 * 玩家状态栏、战斗区域、快捷导航与三类弹窗拆至 src/components/game/*（DOM 与拆分前逐字节一致），
 * 本文件只保留页面级状态（弹窗开合/离线收益）、成就 toast 与签到/任务回调。
 */
export default function GamePage() {
    // 顶部标题/昵称/退出由 layout 顶栏承载（此处不再渲染内置 header，避免双顶栏）
    const { isLoading } = useAuth();
    const { gameState, setMessage, actionLoading, loadError, runAction, runMessageAction, refresh } = useGameData();
    // 转生/重醒/流派选择弹窗：按钮只负责打开，真正请求在弹窗内确认触发
    const [prestigeOpen, setPrestigeOpen] = useState(false);
    const [reawakenOpen, setReawakenOpen] = useState(false);
    const [schoolOpen, setSchoolOpen] = useState(false);
    const [offline, setOffline] = useState<OfflineReward | null>(null);

    // 进入主页领取一次离线收益：有实际产出才弹窗，0 收益静默关闭
    useEffect(() => {
        if (offlineClaimAttempted) return;
        offlineClaimAttempted = true;
        api.claimOfflineReward()
            .then((reward) => {
                if (reward.goldGained > 0 || reward.expGained > 0) setOffline(reward);
            })
            .catch(() => undefined);
    }, []);

    // 成就解锁 toast：每次 refresh 落地后 diff 新解锁的 id。
    // 首拉（seenUnlockedRef 为 null）只建档不提示；已见过的 id 不重复提示；
    // achievements 缺失/旧后端 string[] 形状 → normalizeAchievements 返回空数组，静默跳过。
    // 多条同时解锁：取第一条 + 数量（`X 等 N 项`），不逐条拼接以免 message 条过长溢出。
    const seenUnlockedRef = useRef<Set<string> | null>(null);
    useEffect(() => {
        if (!gameState) return;
        const unlocked = normalizeAchievements(gameState.achievements).filter((a) => a.unlocked);
        const seen = seenUnlockedRef.current;
        if (seen === null) {
            seenUnlockedRef.current = new Set(unlocked.map((a) => a.id));
            return;
        }
        const fresh = unlocked.filter((a) => !seen.has(a.id));
        if (fresh.length === 0) return;
        for (const a of unlocked) seen.add(a.id);
        setMessage(fresh.length === 1
            ? `🏆 成就解锁：${fresh[0].name}！属性已生效`
            : `🏆 成就解锁：${fresh[0].name} 等${fresh.length}项！属性已生效`);
    }, [gameState, setMessage]);

    // 全服公告广播（/topic/announcement）：走现有 message 条展示（📢 前缀 + announcement-toast
    // testid，照 achievement-toast 先例）。WS 不可用时不触发（live.ts 静默重连），页面功能不受影响。
    useLiveAnnouncement((msg) => setMessage(`📢 ${msg}`));

    const dismissOffline = () => {
        setOffline(null);
        void refresh();
    };

    // 每日签到：runAction 成功回调 + 自动 refresh 拉新签到状态；已签由后端 400 → message 提示
    const handleCheckin = () => runAction(
        () => api.checkin(),
        (r) => setMessage(`签到成功！第${r.cycleDay}天：+${r.goldGained}金币${r.bossCoinGained > 0 ? ` +${r.bossCoinGained}Boss币` : ''}`),
        '签到失败',
    );

    // 补签昨日（花费 500 金币，MAKEUP_COST_GOLD）：后端失败（无历史签到/昨日已签/金币不足）也是
    // 200 + success:false → 透传 message（照 prestige/awaken 惯例）；只修复连签、不补发当日奖励。
    // runAction 自动 refresh 拉新 makeupAvailable/streak（成功后补签条消失、连续徽标更新）。
    const handleMakeupCheckin = () => runMessageAction(
        () => api.makeupCheckin(),
        '补签失败',
    );

    // 每日任务领奖：runAction 成功回调 + 自动 refresh 拉新任务进度；未达标/重复领取由后端 400 → message 提示
    const handleClaimQuest = (questId: string) => runAction(
        () => api.claimQuest(questId),
        (r) => setMessage(`任务奖励已领取！+${r.goldGained}金币${r.bossCoinGained > 0 ? ` +${r.bossCoinGained}Boss币` : ''}${r.soulPowerGained > 0 ? ` +${r.soulPowerGained}魂力` : ''}`),
        '领取失败',
    );

    if (isLoading || !gameState) {
        // 首载骨架 / 加载失败错误重试（统一三态，复用 StateViews）
        return (
            <div className="min-h-screen bg-gradient-to-b from-gray-900 via-gray-800 to-gray-900">
                <div className="max-w-4xl mx-auto p-4">
                    <BootState error={isLoading ? '' : loadError} onRetry={refresh} rows={7} />
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-b from-gray-900 via-gray-800 to-gray-900">
            <div className="max-w-4xl mx-auto p-4 space-y-4">
                {/* 玩家状态栏（境界/武魂/流派/HP/资源） */}
                <PlayerStatusBar onOpenSchool={() => setSchoolOpen(true)} onOpenReawaken={() => setReawakenOpen(true)} />

                {/* 任务#23：战力明细（可折叠，默认收起；放在状态栏下方，与 ⚔️ 战力 同一视觉区）。
                    战斗属性摘要行：后端升级前 combatStats 缺失 → 面板内整行隐藏 */}
                <PowerDetailPanel power={gameState.power} detail={gameState.powerDetail} combatStats={gameState.combatStats} />

                {/* 每日签到（后端未升级时 checkIn 缺失 → 卡内降级为空态） */}
                <CheckinCard checkIn={gameState.checkIn} actionLoading={actionLoading} onCheckin={handleCheckin} onMakeup={handleMakeupCheckin} />

                {/* 每日任务（纯增量功能：后端未升级时 dailyQuests 缺失 → 整卡隐藏不渲染） */}
                <DailyQuestsCard quests={gameState.dailyQuests?.quests} actionLoading={actionLoading} onClaim={handleClaimQuest} />

                {/* 战斗区域（消息条/结果/四钮/挂机设置） */}
                <BattlePanel onOpenPrestige={() => setPrestigeOpen(true)} />

                {/* 快捷导航 */}
                <QuickNav />
            </div>

            {/* 离线收益弹窗（领取请求在页级 effect，挂载即拉取） */}
            <OfflineRewardModal offline={offline} onDismiss={dismissOffline} />

            {/* 转生/重醒/流派选择确认弹窗（Escape+聚焦可达性收敛在 useModalEscape） */}
            <PrestigeDialog open={prestigeOpen} onClose={() => setPrestigeOpen(false)} />
            <ReawakenDialog open={reawakenOpen} onClose={() => setReawakenOpen(false)} />
            <SchoolDialog open={schoolOpen} onClose={() => setSchoolOpen(false)} />
        </div>
    );
}
