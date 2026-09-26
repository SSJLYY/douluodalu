import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BattleReplay from '@/components/BattleReplay';
import type { BattleRound } from '@/lib/api';

/** 3 回合固定日志：我方 HP 90→80→70，敌方 80→50→0 */
const LOG: BattleRound[] = [
    { round: 1, playerHpBefore: 100, monsterHpBefore: 100, playerDamage: 20, monsterDamage: 10, playerHpAfter: 90, monsterHpAfter: 80 },
    { round: 2, playerHpBefore: 90, monsterHpBefore: 80, playerDamage: 30, monsterDamage: 10, playerHpAfter: 80, monsterHpAfter: 50 },
    { round: 3, playerHpBefore: 80, monsterHpBefore: 50, playerDamage: 50, monsterDamage: 10, playerHpAfter: 70, monsterHpAfter: 0 },
];

const playerHpBar = () => screen.getByRole('progressbar', { name: '我方生命值' });
const roundText = () => screen.getByText(/第 \d+ \/ 3 回合/);

describe('BattleReplay', () => {
    beforeEach(() => {
        // BattleReplay 仅惰性读 .matches（reduced-motion 快照），桩恒返回不减速
        vi.stubGlobal('matchMedia', () => ({ matches: false }));
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.useRealTimers();
    });

    it('初始渲染第 1 回合，advanceTimersByTime(900) 自动步进到第 2 回合（HP aria-valuenow 变化）', () => {
        render(<BattleReplay battleLog={LOG} monsterName="人面魔蛛" />);

        expect(roundText().textContent).toBe('第 1 / 3 回合');
        expect(playerHpBar().getAttribute('aria-valuenow')).toBe('90');
        // 怪物名与「敌方」同 span 渲染
        expect(screen.getByText('敌方 · 人面魔蛛')).toBeTruthy();

        act(() => {
            vi.advanceTimersByTime(900);
        });

        expect(roundText().textContent).toBe('第 2 / 3 回合');
        expect(playerHpBar().getAttribute('aria-valuenow')).toBe('80');
    });

    it('自动播到末回合自停：toggle 回到「▶ 播放」，再推进不越界', () => {
        render(<BattleReplay battleLog={LOG} />);

        // 每步 act 单独包一层：状态更新触发的 effect（排下一个 900ms 定时器）在 act 收尾时才 flush
        act(() => {
            vi.advanceTimersByTime(900);
        });
        act(() => {
            vi.advanceTimersByTime(900);
        });

        expect(roundText().textContent).toBe('第 3 / 3 回合');
        expect((screen.getByTestId('battle-replay-next') as HTMLButtonElement).disabled).toBe(true);
        expect(screen.getByText('▶ 播放')).toBeTruthy();

        // 到末回合后 effectivePlaying=false，effect 不再排程定时器
        act(() => {
            vi.advanceTimersByTime(900);
        });
        expect(roundText().textContent).toBe('第 3 / 3 回合');
    });

    it('toggle 暂停后续播：暂停期不步进，续播后按时步进', () => {
        render(<BattleReplay battleLog={LOG} />);

        // 播放中暂停
        fireEvent.click(screen.getByTestId('battle-replay-toggle'));
        expect(screen.getByText('▶ 播放')).toBeTruthy();
        act(() => {
            vi.advanceTimersByTime(2000);
        });
        expect(roundText().textContent).toBe('第 1 / 3 回合');

        // 再按续播 → 900ms 后步进
        fireEvent.click(screen.getByTestId('battle-replay-toggle'));
        expect(screen.getByText('⏸ 暂停')).toBeTruthy();
        act(() => {
            vi.advanceTimersByTime(900);
        });
        expect(roundText().textContent).toBe('第 2 / 3 回合');
    });

    it('prev 步进回退并暂停自动播放，首回合时 prev 禁用', () => {
        render(<BattleReplay battleLog={LOG} />);

        act(() => {
            vi.advanceTimersByTime(900); // → 第 2 回合
        });
        fireEvent.click(screen.getByTestId('battle-replay-prev'));

        expect(roundText().textContent).toBe('第 1 / 3 回合');
        expect(playerHpBar().getAttribute('aria-valuenow')).toBe('90');
        expect((screen.getByTestId('battle-replay-prev') as HTMLButtonElement).disabled).toBe(true);

        // 手动步进即暂停：时间推进不再自动前进
        act(() => {
            vi.advanceTimersByTime(900);
        });
        expect(roundText().textContent).toBe('第 1 / 3 回合');
    });

    it('battleLog 缺失/为空返回 null（调用方保留静态兜底）', () => {
        const missing = render(<BattleReplay />);
        expect(missing.container.firstChild).toBeNull();

        const empty = render(<BattleReplay battleLog={[]} />);
        expect(empty.container.firstChild).toBeNull();
    });
});
