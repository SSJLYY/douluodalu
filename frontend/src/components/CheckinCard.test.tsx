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

describe('CheckinCard 补签条', () => {
    /** 补签可用态：BASE + makeupAvailable（后端判定昨日漏签可补） */
    const MAKEUP: CheckInStatus = { ...BASE, makeupAvailable: true };

    it('makeupAvailable=true 渲染补签条、说明文案与补签按钮（文案写明不补发当日奖励）', () => {
        render(<CheckinCard checkIn={MAKEUP} actionLoading={false} onCheckin={vi.fn()} onMakeup={vi.fn()} />);

        expect(screen.getByTestId('makeup-row')).toBeTruthy();
        expect(screen.getByText('昨日漏签了！花费 500 金币补签，修复连续签到（不补发当日奖励）')).toBeTruthy();
        const btn = screen.getByTestId('makeup-btn') as HTMLButtonElement;
        expect(btn.textContent).toBe('补签昨日（500金币）');
        expect(btn.disabled).toBe(false);
    });

    it('makeupAvailable=false → 不渲染补签条与补签按钮', () => {
        render(<CheckinCard checkIn={{ ...BASE, makeupAvailable: false }} actionLoading={false} onCheckin={vi.fn()} onMakeup={vi.fn()} />);

        expect(screen.queryByTestId('makeup-row')).toBeNull();
        expect(screen.queryByTestId('makeup-btn')).toBeNull();
    });

    it('makeupAvailable 缺失（旧后端）→ 同样不渲染补签条', () => {
        render(<CheckinCard checkIn={BASE} actionLoading={false} onCheckin={vi.fn()} onMakeup={vi.fn()} />);

        expect(screen.queryByTestId('makeup-row')).toBeNull();
        expect(screen.queryByTestId('makeup-btn')).toBeNull();
        // 主签到流程不受影响
        expect(screen.getByTestId('checkin-btn')).toBeTruthy();
    });

    it('点击补签按钮触发 onMakeup 回调（不触发 onCheckin）', () => {
        const onMakeup = vi.fn();
        const onCheckin = vi.fn();
        render(<CheckinCard checkIn={MAKEUP} actionLoading={false} onCheckin={onCheckin} onMakeup={onMakeup} />);

        fireEvent.click(screen.getByTestId('makeup-btn'));

        expect(onMakeup).toHaveBeenCalledTimes(1);
        expect(onCheckin).not.toHaveBeenCalled();
    });

    it('actionLoading 时补签按钮禁用', () => {
        render(<CheckinCard checkIn={MAKEUP} actionLoading onCheckin={vi.fn()} onMakeup={vi.fn()} />);

        const btn = screen.getByTestId('makeup-btn') as HTMLButtonElement;
        expect(btn.disabled).toBe(true);
        fireEvent.click(btn);
    });
});
