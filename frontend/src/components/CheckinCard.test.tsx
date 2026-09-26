import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import CheckinCard from '@/components/CheckinCard';
import type { CheckInReward, CheckInStatus } from '@/lib/api';

const REWARDS: CheckInReward[] = Array.from({ length: 7 }, (_, i) => ({
    day: i + 1,
    gold: 100 * (i + 1),
    bossCoin: (i + 1) % 2,
    soulPower: 10 * (i + 1),
}));

const BASE: CheckInStatus = {
    signedToday: false,
    streak: 3,
    totalDays: 10,
    nextCycleDay: 4,
    rewards: REWARDS,
};

const dayCells = (container: HTMLElement) => container.querySelectorAll('[data-testid^="checkin-day-"]');

describe('CheckinCard', () => {
    it('正常状态渲染 7 格奖励与连续/累计徽标', () => {
        const { container } = render(<CheckinCard checkIn={BASE} actionLoading={false} onCheckin={vi.fn()} />);

        expect(dayCells(container).length).toBe(7);
        expect(screen.getByText(/连续 3 天/)).toBeTruthy();
        expect(screen.getByText(/累计 10 天/)).toBeTruthy();
        // 奖励摘要：0 值币种不显示（第 2 天 bossCoin=0 → 格内无 Boss币行）
        expect(screen.getByTestId('checkin-day-2').textContent).not.toContain('Boss币');
        expect(screen.getByTestId('checkin-day-2').textContent).toContain('+200金币');
        // bossCoin 奇数天为 1（第 1/3/5/7 天）
        expect(screen.getAllByText('+1Boss币').length).toBe(4);
    });

    it('未签时 nextCycleDay 格高亮并标记 aria-current，按钮可点', () => {
        const onCheckin = vi.fn();
        render(<CheckinCard checkIn={BASE} actionLoading={false} onCheckin={onCheckin} />);

        const today = screen.getByTestId('checkin-day-4');
        expect(today.getAttribute('aria-current')).toBe('date');
        expect(screen.getByText('今日')).toBeTruthy();
        // 未签：day 4 之前的格子算已领取（✓）
        expect(screen.getByTestId('checkin-day-1').textContent).toContain('✓');
        expect(screen.getByTestId('checkin-day-4').textContent).not.toContain('✓');

        const btn = screen.getByTestId('checkin-btn') as HTMLButtonElement;
        expect(btn.disabled).toBe(false);
        fireEvent.click(btn);
        expect(onCheckin).toHaveBeenCalledTimes(1);
    });

    it('signedToday 按钮禁用且文案为「今日已签 ✓」，不再高亮今日格', () => {
        const onCheckin = vi.fn();
        render(
            <CheckinCard
                checkIn={{ ...BASE, signedToday: true, nextCycleDay: 5 }}
                actionLoading={false}
                onCheckin={onCheckin}
            />,
        );

        const btn = screen.getByTestId('checkin-btn') as HTMLButtonElement;
        expect(btn.disabled).toBe(true);
        expect(btn.textContent).toBe('今日已签 ✓');
        expect(screen.queryByText('今日')).toBeNull();
        // 已签：day <= 本次 cycleDay(4) 的格子都已领取
        expect(screen.getByTestId('checkin-day-4').textContent).toContain('✓');
        expect(screen.getByTestId('checkin-day-5').textContent).not.toContain('✓');

        fireEvent.click(btn);
        expect(onCheckin).not.toHaveBeenCalled();
    });

    it('actionLoading 时签到按钮禁用并显示「签到中...」', () => {
        render(<CheckinCard checkIn={BASE} actionLoading onCheckin={vi.fn()} />);

        const btn = screen.getByTestId('checkin-btn') as HTMLButtonElement;
        expect(btn.disabled).toBe(true);
        expect(btn.textContent).toBe('签到中...');
    });

    it('checkIn 缺失 → EmptyPanel 降级，不渲染网格与按钮', () => {
        render(<CheckinCard actionLoading={false} onCheckin={vi.fn()} />);

        expect(screen.getByTestId('checkin-empty').textContent).toBe('签到功能暂不可用');
        expect(screen.queryByTestId('checkin-btn')).toBeNull();
        expect(dayCells(screen.getByTestId('checkin-card')).length).toBe(0);
    });

    it('rewards 为空数组 → 同样 EmptyPanel 降级', () => {
        render(<CheckinCard checkIn={{ ...BASE, rewards: [] }} actionLoading={false} onCheckin={vi.fn()} />);

        expect(screen.getByTestId('checkin-empty')).toBeTruthy();
        expect(screen.queryByTestId('checkin-btn')).toBeNull();
    });
});
