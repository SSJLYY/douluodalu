import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import GuildBossPanel from '@/components/GuildBossPanel';
import type { BackpackItem, GuildBossResult, GuildBossStatus } from '@/lib/api';

const hpBar = () => screen.getByTestId('boss-hp-bar');
const challengeBtn = () => screen.getByRole('button', { name: '挑战 Boss' });

/** BackpackItem 字段全部必填但结果行不消费，测试桩留空即可 */
const stubItem = {} as BackpackItem;

const bossResult = (overrides: Partial<GuildBossResult> = {}): GuildBossResult => ({
    won: true,
    damage: 123456,
    bossHp: 876544,
    goldGained: 500,
    bossCoinGained: 3,
    item: stubItem,
    message: '挑战成功',
    ...overrides,
});

const baseProps = {
    bossStatus: null,
    bossResult: null,
    rank: null,
    myUserId: null,
    loading: false,
    onChallenge: () => {},
};

describe('GuildBossPanel 血条渲染', () => {
    it('半血：progressbar 三 aria 值 + aria-label + 红色 bar 宽 50%，数字行千分位', () => {
        const status: GuildBossStatus = { bossHp: 500000, bossMaxHp: 1000000, killed: false };
        render(<GuildBossPanel {...baseProps} bossStatus={status} />);

        const bar = hpBar();
        expect(bar.getAttribute('role')).toBe('progressbar');
        expect(bar.getAttribute('aria-label')).toBe('宗门Boss血量');
        expect(bar.getAttribute('aria-valuemin')).toBe('0');
        expect(bar.getAttribute('aria-valuemax')).toBe('1000000');
        expect(bar.getAttribute('aria-valuenow')).toBe('500000');
        const fill = bar.firstElementChild as HTMLElement;
        expect(fill.className).toContain('dl-hp-bar');
        expect(fill.className).toContain('bg-red-500');
        expect(fill.style.width).toBe('50%');
        expect(screen.getByTestId('boss-hp-text').textContent).toBe('血量 500,000 / 1,000,000');
        // 未击杀：无横幅，按钮可用
        expect(screen.queryByTestId('boss-killed-banner')).toBeNull();
        expect(challengeBtn().hasAttribute('disabled')).toBe(false);
    });

    it('bossHp=0 且 killed=false（池已扣空但未标记击杀的中间态）：now=0、无 NaN、无横幅、按钮仍可用', () => {
        const status: GuildBossStatus = { bossHp: 0, bossMaxHp: 1000000, killed: false };
        render(<GuildBossPanel {...baseProps} bossStatus={status} />);

        expect(hpBar().getAttribute('aria-valuenow')).toBe('0');
        expect((hpBar().firstElementChild as HTMLElement).style.width).toBe('0%');
        expect(screen.getByTestId('boss-hp-text').textContent).toBe('血量 0 / 1,000,000');
        expect(screen.queryByTestId('boss-killed-banner')).toBeNull();
        const btn = challengeBtn();
        expect(btn.hasAttribute('disabled')).toBe(false);
        expect(btn.getAttribute('title')).toBeNull();
    });
});

describe('GuildBossPanel 击杀态', () => {
    it('killed=true：横幅覆盖（☠ aria-hidden）+ 血条/数字归零 + 按钮禁用且 title 说明下周一重生', () => {
        const status: GuildBossStatus = { bossHp: 0, bossMaxHp: 1000000, killed: true };
        render(<GuildBossPanel {...baseProps} bossStatus={status} />);

        const banner = screen.getByTestId('boss-killed-banner');
        expect(banner.textContent).toContain('本周 Boss 已被击杀');
        const skull = banner.querySelector('span[aria-hidden="true"]');
        expect(skull?.textContent).toBe('☠');
        // 归零态：aria/宽度/数字一致为 0（防御后端 killed=true 仍带血的不一致数据）
        expect(hpBar().getAttribute('aria-valuenow')).toBe('0');
        expect((hpBar().firstElementChild as HTMLElement).style.width).toBe('0%');
        expect(screen.getByTestId('boss-hp-text').textContent).toBe('血量 0 / 1,000,000');
        const btn = challengeBtn();
        expect(btn.hasAttribute('disabled')).toBe(true);
        expect(btn.getAttribute('title')).toContain('下周一');
    });

    it('挑战结果 killed=true：结果行追加「全员协力击杀！」高亮；旧后端无 killed 字段则不渲染', () => {
        const first = render(
            <GuildBossPanel {...baseProps} bossResult={bossResult({ killed: true })} />,
        );
        const highlight = screen.getByTestId('boss-kill-highlight');
        expect(highlight.textContent).toContain('全员协力击杀！');
        first.unmount();

        // 旧后端：响应缺失 killed → 不渲染高亮，结果行照旧
        render(<GuildBossPanel {...baseProps} bossResult={bossResult()} />);
        expect(screen.queryByTestId('boss-kill-highlight')).toBeNull();
        expect(screen.getByText(/伤害 123456 \/ Boss生命 876544/)).toBeTruthy();
    });
});

describe('GuildBossPanel 降级容错与组合', () => {
    it('bossStatus=null（旧后端 404/失败）：血条区整块隐藏，挑战按钮照旧可用', () => {
        render(<GuildBossPanel {...baseProps} />);

        expect(screen.queryByTestId('boss-hp-bar')).toBeNull();
        expect(screen.queryByTestId('boss-hp-text')).toBeNull();
        expect(screen.queryByTestId('boss-killed-banner')).toBeNull();
        expect(challengeBtn().hasAttribute('disabled')).toBe(false);
    });

    it('loading=true 禁用挑战按钮；rank 传入时透传渲染 BossRankPanel，null 时不渲染', () => {
        const first = render(<GuildBossPanel {...baseProps} loading />);
        expect(challengeBtn().hasAttribute('disabled')).toBe(true);
        expect(screen.queryByTestId('boss-rank')).toBeNull();
        first.unmount();

        render(
            <GuildBossPanel
                {...baseProps}
                rank={{ myRank: 1, entries: [{ userId: 11, nickname: '唐三', weeklyDamage: 1200000 }] }}
                myUserId={11}
            />,
        );
        expect(screen.getByTestId('boss-rank')).toBeTruthy();
        expect(screen.getByTestId('boss-rank-row-1').textContent).toContain('唐三');
    });
});
