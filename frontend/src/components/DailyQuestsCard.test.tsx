import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import DailyQuestsCard from '@/components/DailyQuestsCard';
import type { DailyQuest } from '@/lib/api';

/** 固定 5 任务契约：覆盖 可领 / 未达标 / 已领 / 零进度 四种行状态 */
const QUESTS: DailyQuest[] = [
    { id: 'battle_wins', description: '战斗胜利3次', target: 3, progress: 3, claimed: false, rewardGold: 500, rewardBossCoin: 0, rewardSoulPower: 0 },
    { id: 'cultivate', description: '修炼5次', target: 5, progress: 2, claimed: false, rewardGold: 0, rewardBossCoin: 0, rewardSoulPower: 50 },
    { id: 'tower', description: '挑战魂塔2次', target: 2, progress: 0, claimed: false, rewardGold: 300, rewardBossCoin: 1, rewardSoulPower: 0 },
    { id: 'checkin', description: '完成今日签到', target: 1, progress: 1, claimed: true, rewardGold: 200, rewardBossCoin: 0, rewardSoulPower: 20 },
    { id: 'shop_buy', description: '商店购物1次', target: 1, progress: 0, claimed: false, rewardGold: 100, rewardBossCoin: 0, rewardSoulPower: 0 },
];

const questRows = (container: HTMLElement) => container.querySelectorAll('[data-testid^="quest-row-"]');

describe('DailyQuestsCard', () => {
    it('渲染 5 行任务与全清进度徽标（已领取 1/5）', () => {
        const { container } = render(<DailyQuestsCard quests={QUESTS} onClaim={vi.fn()} />);

        expect(screen.getByTestId('daily-quests-card')).toBeTruthy();
        expect(questRows(container).length).toBe(5);
        expect(screen.getByTestId('daily-quests-progress').textContent).toBe('已领取 1/5');
    });

    it('奖励摘要：0 值币种不显示', () => {
        render(<DailyQuestsCard quests={QUESTS} onClaim={vi.fn()} />);

        expect(screen.getByText('+500金币')).toBeTruthy(); // battle_wins 只有金币
        expect(screen.getByText('+50魂力')).toBeTruthy(); // cultivate 只有魂力
        // tower 金币+Boss币 → join(' ') 合并为一个文本节点
        expect(screen.getByText('+300金币 +1Boss币')).toBeTruthy();
        expect(screen.queryByText('+500金币 +0Boss币')).toBeNull();
    });

    it('三态按钮：可领启用「领取」/ 已领禁用「✓ 已领取」/ 未达标禁用「未完成」', () => {
        render(<DailyQuestsCard quests={QUESTS} onClaim={vi.fn()} />);

        const claimable = screen.getByTestId('quest-claim-battle_wins') as HTMLButtonElement;
        expect(claimable.disabled).toBe(false);
        expect(claimable.textContent).toBe('领取');

        const claimed = screen.getByTestId('quest-claim-checkin') as HTMLButtonElement;
        expect(claimed.disabled).toBe(true);
        expect(claimed.textContent).toBe('✓ 已领取');

        const behind = screen.getByTestId('quest-claim-cultivate') as HTMLButtonElement;
        expect(behind.disabled).toBe(true);
        expect(behind.textContent).toBe('未完成');

        const zeroProgress = screen.getByTestId('quest-claim-shop_buy') as HTMLButtonElement;
        expect(zeroProgress.disabled).toBe(true);
        expect(zeroProgress.textContent).toBe('未完成');
    });

    it('点击领取触发 onClaim(questId)；禁用行点击不触发', () => {
        const onClaim = vi.fn();
        render(<DailyQuestsCard quests={QUESTS} onClaim={onClaim} />);

        fireEvent.click(screen.getByTestId('quest-claim-battle_wins'));
        expect(onClaim).toHaveBeenCalledTimes(1);
        expect(onClaim).toHaveBeenCalledWith('battle_wins');

        fireEvent.click(screen.getByTestId('quest-claim-cultivate')); // 未达标 disabled
        expect(onClaim).toHaveBeenCalledTimes(1);
    });

    it('actionLoading 时全部领取按钮禁用', () => {
        render(<DailyQuestsCard quests={QUESTS} actionLoading onClaim={vi.fn()} />);

        expect((screen.getByTestId('quest-claim-battle_wins') as HTMLButtonElement).disabled).toBe(true);
        expect((screen.getByTestId('quest-claim-tower') as HTMLButtonElement).disabled).toBe(true);
    });

    it('进度条带 progressbar 角色与 aria-valuemin/max/now', () => {
        render(<DailyQuestsCard quests={QUESTS} onClaim={vi.fn()} />);

        const bar = screen.getByRole('progressbar', { name: '修炼5次进度' });
        expect(bar.getAttribute('aria-valuemin')).toBe('0');
        expect(bar.getAttribute('aria-valuemax')).toBe('5');
        expect(bar.getAttribute('aria-valuenow')).toBe('2');
    });

    it('quests 缺失或为空 → return null（纯增量功能，整卡隐藏）', () => {
        const missing = render(<DailyQuestsCard onClaim={vi.fn()} />);
        expect(missing.container.firstChild).toBeNull();

        const empty = render(<DailyQuestsCard quests={[]} onClaim={vi.fn()} />);
        expect(empty.container.firstChild).toBeNull();
    });
});
