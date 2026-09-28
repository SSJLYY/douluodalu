import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AchievementsPanel, { categoryLabel } from '@/components/AchievementsPanel';
import type { Achievement } from '@/lib/api';

/** 固定奖励契约：hp/atk 生效展示，matk/pdef/mdef/critRate/critDmg 后端未生效不展示 */
const REWARDS = { hp: 100, atk: 5, matk: 3, pdef: 2, mdef: 1, critRate: 0.1, critDmg: 0.5 };

function makeAch(overrides: Partial<Achievement> & Pick<Achievement, 'id' | 'category'>): Achievement {
    return {
        name: `成就-${overrides.id}`,
        description: `${overrides.id} 的描述文案`,
        target: 10,
        progress: 0,
        unlocked: false,
        unlockedAt: null,
        rewards: REWARDS,
        ...overrides,
    };
}

const GROUP_ROWS = (container: HTMLElement, category: string) =>
    [...container.querySelectorAll<HTMLElement>(`[data-testid="achievement-group-${category}"] [data-testid^="achievement-row-"]`)]
        .map((el) => el.getAttribute('data-testid'));

describe('AchievementsPanel', () => {
    it('按类别分组渲染，category→中文映射写死（修炼/魂环/战斗/杀戮之都/转生）', () => {
        const achievements = (['CULTIVATION', 'SOUL_RING', 'BATTLE', 'TOWER', 'PRESTIGE'] as const)
            .map((category) => makeAch({ id: `a-${category}`, category }));
        const { container } = render(<AchievementsPanel achievements={achievements} />);

        expect(screen.getByTestId('achievements-panel')).toBeTruthy();
        for (const category of ['CULTIVATION', 'SOUL_RING', 'BATTLE', 'TOWER', 'PRESTIGE']) {
            expect(screen.getByTestId(`achievement-group-${category}`)).toBeTruthy();
        }
        expect(screen.getByText('修炼')).toBeTruthy();
        expect(screen.getByText('魂环')).toBeTruthy();
        expect(screen.getByText('战斗')).toBeTruthy();
        expect(screen.getByText('杀戮之都')).toBeTruthy();
        expect(screen.getByText('转生')).toBeTruthy();
        // 分组顺序：修炼 → 魂环 → 战斗 → 杀戮之都 → 转生（排除组头 count 徽标）
        const groups = [...container.querySelectorAll('[data-testid^="achievement-group-"]')] as HTMLElement[];
        expect(
            groups.map((g) => g.getAttribute('data-testid')).filter((id) => !id?.startsWith('achievement-group-count-')),
        ).toEqual([
            'achievement-group-CULTIVATION',
            'achievement-group-SOUL_RING',
            'achievement-group-BATTLE',
            'achievement-group-TOWER',
            'achievement-group-PRESTIGE',
        ]);
    });

    it('未知 category 兜底显示原值并追加在已知分组之后', () => {
        const achievements = [
            makeAch({ id: 'x1', category: 'NEW_TYPE' }),
            makeAch({ id: 'x2', category: 'BATTLE' }),
        ];
        render(<AchievementsPanel achievements={achievements} />);

        expect(categoryLabel('NEW_TYPE')).toBe('NEW_TYPE');
        expect(screen.getByTestId('achievement-group-NEW_TYPE')).toBeTruthy();
        expect(screen.getByText('NEW_TYPE')).toBeTruthy();
    });

    it('组头带「已解锁 x/y」小徽标', () => {
        const achievements = [
            makeAch({ id: 'b1', category: 'BATTLE', unlocked: true, progress: 10 }),
            makeAch({ id: 'b2', category: 'BATTLE', unlocked: true, progress: 10 }),
            makeAch({ id: 'b3', category: 'BATTLE' }),
        ];
        render(<AchievementsPanel achievements={achievements} />);

        expect(screen.getByTestId('achievement-group-count-BATTLE').textContent).toBe('已解锁 2/3');
    });

    it('进度条带 progressbar 角色与 aria-valuemin/max/now 三值', () => {
        render(<AchievementsPanel achievements={[makeAch({ id: 'cul', category: 'CULTIVATION', name: '修炼大师', progress: 8 })]} />);

        const bar = screen.getByRole('progressbar', { name: '修炼大师进度' });
        expect(bar.getAttribute('aria-valuemin')).toBe('0');
        expect(bar.getAttribute('aria-valuemax')).toBe('10');
        expect(bar.getAttribute('aria-valuenow')).toBe('8');
    });

    it('已解锁金色 ✓ 徽标 + unlockedAt 日期；unlockedAt 为 null 时只显示徽标', () => {
        const achievements = [
            makeAch({ id: 'with-date', category: 'TOWER', unlocked: true, progress: 10, unlockedAt: '2026-09-01' }),
            makeAch({ id: 'no-date', category: 'TOWER', unlocked: true, progress: 10, unlockedAt: null }),
        ];
        render(<AchievementsPanel achievements={achievements} />);

        expect(screen.getByTestId('achievement-unlocked-with-date').textContent).toBe('✓ 已解锁 2026-09-01');
        expect(screen.getByTestId('achievement-unlocked-no-date').textContent).toBe('✓ 已解锁');
        // 已解锁行不再展示 progress/target 计数
        expect(screen.queryByText('10/10')).toBeNull();
    });

    it('奖励只展示 hp/atk：`生命 +100 · 攻击 +5`；未生效四字段（魔攻/物防/魔防/暴击）不出现', () => {
        const { container } = render(<AchievementsPanel achievements={[makeAch({ id: 'r1', category: 'BATTLE' })]} />);

        expect(screen.getByText('生命 +100 · 攻击 +5')).toBeTruthy();
        const text = container.textContent ?? '';
        expect(text).not.toContain('魔攻');
        expect(text).not.toContain('物防');
        expect(text).not.toContain('魔防');
        expect(text).not.toContain('暴击');
    });

    it('组内排序：未解锁按进度比降序在前，已解锁沉底（保持原相对顺序）', () => {
        const achievements = [
            makeAch({ id: 'done', category: 'CULTIVATION', unlocked: true, progress: 10 }),
            makeAch({ id: 'low', category: 'CULTIVATION', progress: 2 }),
            makeAch({ id: 'high', category: 'CULTIVATION', progress: 8 }),
        ];
        const { container } = render(<AchievementsPanel achievements={achievements} />);

        expect(GROUP_ROWS(container, 'CULTIVATION')).toEqual([
            'achievement-row-high',
            'achievement-row-low',
            'achievement-row-done',
        ]);
    });

    it('未解锁行灰态渲染（无金色 ✓ 徽标），已解锁行高亮边框', () => {
        const achievements = [
            makeAch({ id: 'locked', category: 'PRESTIGE', progress: 4 }),
            makeAch({ id: 'open', category: 'PRESTIGE', unlocked: true, progress: 10, unlockedAt: '2026-09-02' }),
        ];
        const { container } = render(<AchievementsPanel achievements={achievements} />);

        const locked = screen.getByTestId('achievement-row-locked');
        expect(locked.className).toContain('opacity-75');
        expect(container.querySelector('[data-testid="achievement-unlocked-locked"]')).toBeNull();

        const open = screen.getByTestId('achievement-row-open');
        expect(open.className).not.toContain('opacity-75');
        expect(open.className).toContain('border-yellow-600/50');
    });

    it('空数组（旧后端 string[] 降级后）→ EmptyPanel 空态', () => {
        render(<AchievementsPanel achievements={[]} />);

        expect(screen.getByTestId('achievements-panel')).toBeTruthy();
        expect(screen.getByTestId('achievements-empty').textContent).toBe('暂无成就数据');
    });
});
