import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import BossRankPanel from '@/components/BossRankPanel';
import type { GuildBossRank, GuildBossRankEntry } from '@/lib/api';

const entry = (userId: number, nickname: string, weeklyDamage: number): GuildBossRankEntry => ({
    userId,
    nickname,
    weeklyDamage,
});

/** 满 10 条的完整榜（后端契约：按 weeklyDamage 降序、最多 10 条）；11 号是「我本人」排第 6 */
const FULL: GuildBossRank = {
    myRank: 6,
    entries: [
        entry(1, '唐三', 1200000),
        entry(2, '小舞', 900000),
        entry(3, '戴沐白', 800000),
        entry(4, '奥斯卡', 700000),
        entry(5, '马红俊', 600000),
        entry(11, '我本人', 500000),
        entry(7, '宁荣荣', 400000),
        entry(8, '朱竹清', 300000),
        entry(9, '比比东', 200000),
        entry(10, '千仞雪', 100000),
    ],
};

describe('BossRankPanel 领奖台', () => {
    it('满 3 条渲染领奖台：金银铜台位对应 1/2/3 名，展示顺序为亚军/冠军/季军', () => {
        render(<BossRankPanel rank={FULL} myUserId={11} />);

        const podium = screen.getByTestId('boss-rank-podium');
        expect(podium).toBeTruthy();
        // 三个台位 testid 与奖牌
        expect(screen.getByTestId('boss-rank-podium-1').textContent).toContain('🥇');
        expect(screen.getByTestId('boss-rank-podium-2').textContent).toContain('🥈');
        expect(screen.getByTestId('boss-rank-podium-3').textContent).toContain('🥉');
        // DOM 顺序（children 顺序）：亚军 → 冠军 → 季军（冠军居中）
        const order = Array.from(podium.children).map((el) => el.getAttribute('data-testid'));
        expect(order).toEqual(['boss-rank-podium-2', 'boss-rank-podium-1', 'boss-rank-podium-3']);
        // 冠军台位：昵称 + 千分位伤害
        const first = screen.getByTestId('boss-rank-podium-1');
        expect(first.textContent).toContain('唐三');
        expect(first.textContent).toContain('1,200,000');
    });

    it('4-10 名走列表行 boss-rank-row-{n}，副行显示 weeklyDamage 千分位，Top3 不重复进列表', () => {
        render(<BossRankPanel rank={FULL} myUserId={11} />);

        expect(screen.getByTestId('boss-rank-row-4').textContent).toContain('奥斯卡');
        expect(screen.getByTestId('boss-rank-row-4').textContent).toContain('700,000');
        expect(screen.getByTestId('boss-rank-row-10').textContent).toContain('100,000');
        // Top3 只在领奖台，不在列表
        expect(screen.queryByTestId('boss-rank-row-1')).toBeNull();
        expect(screen.queryByTestId('boss-rank-row-2')).toBeNull();
        expect(screen.queryByTestId('boss-rank-row-3')).toBeNull();
        // 列表行数 = 10 - 3 = 7（第 4~10 名）
        const list = screen.getByTestId('boss-rank-list');
        expect(list.querySelectorAll('[data-testid^="boss-rank-row-"]').length).toBe(7);
    });
});

describe('BossRankPanel 自己高亮与我的排名', () => {
    it('myUserId 命中的行高亮 + 「(我)」标记，其他行不高亮', () => {
        render(<BossRankPanel rank={FULL} myUserId={11} />);

        const mine = screen.getByTestId('boss-rank-row-6');
        expect(mine.textContent).toContain('我本人');
        expect(mine.textContent).toContain('(我)');
        expect(mine.className).toContain('bg-yellow-900/30');
        expect(screen.getByTestId('boss-rank-row-4').className).not.toContain('bg-yellow-900/30');
        expect(screen.getByTestId('boss-rank-row-4').textContent).not.toContain('(我)');
    });

    it('myRank > 0 显示「我的排名」徽标；≤ 0（未上榜）不显示', () => {
        const first = render(<BossRankPanel rank={FULL} myUserId={11} />);
        expect(screen.getByTestId('boss-rank-my').textContent).toBe('我的排名 #6');
        first.unmount();

        render(<BossRankPanel rank={{ myRank: 0, entries: FULL.entries }} myUserId={99} />);
        expect(screen.queryByTestId('boss-rank-my')).toBeNull();
    });

    it('myUserId 未就绪（null）时不高亮任何行', () => {
        render(<BossRankPanel rank={FULL} />);

        expect(screen.queryByText('(我)')).toBeNull();
        expect(screen.queryByTestId('boss-rank-row-6')?.className).not.toContain('bg-yellow-900/30');
    });
});

describe('BossRankPanel 空榜与不足 3 条', () => {
    it('entries 为空 → 「本周暂无 Boss 伤害记录」灰字，不渲染领奖台与列表', () => {
        render(<BossRankPanel rank={{ myRank: 0, entries: [] }} myUserId={11} />);

        const empty = screen.getByTestId('boss-rank-empty');
        expect(empty.textContent).toBe('本周暂无 Boss 伤害记录');
        expect(empty.className).toContain('text-gray-400');
        expect(screen.queryByTestId('boss-rank-podium')).toBeNull();
        expect(screen.queryByTestId('boss-rank-list')).toBeNull();
    });

    it('不足 3 条（2 条）→ 不立领奖台，全部走列表（名次按下标 1/2）', () => {
        render(<BossRankPanel rank={{ myRank: 2, entries: [entry(1, '唐三', 500), entry(2, '小舞', 300)] }} />);

        expect(screen.queryByTestId('boss-rank-podium')).toBeNull();
        expect(screen.getByTestId('boss-rank-row-1').textContent).toContain('唐三');
        expect(screen.getByTestId('boss-rank-row-1').textContent).toContain('500');
        expect(screen.getByTestId('boss-rank-row-2').textContent).toContain('小舞');
    });
});
