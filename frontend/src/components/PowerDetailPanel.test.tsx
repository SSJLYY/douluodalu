import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import PowerDetailPanel from '@/components/PowerDetailPanel';
import type { PowerDetail } from '@/lib/api';

/**
 * 战力明细面板行显隐单测（直接渲染组件，不渲染整页）：
 * prestige 为后端增量字段（第 6 行「🔄 转生」），旧后端缺失 → undefined 按 0 处理、行隐藏、脚注回退。
 */

const BASE_DETAIL: PowerDetail = {
    baseAtk: 10,
    baseHp: 0,
    basePower: 100,
    ringAtk: 20,
    ringHp: 200,
    ringPower: 40,
    boneAtk: 5,
    boneHp: 100,
    bonePower: 15,
    coreAtk: 3,
    coreHp: 0,
    corePower: 5,
};
/** 四固定行之和（100+40+15+5），成就/转生为 0 时总战力即此值 */
const BASE_POWER = 160;

/** 渲染面板并展开明细（默认收起） */
function openPanel(power: number, detail: PowerDetail) {
    const { container } = render(<PowerDetailPanel power={power} detail={detail} />);
    fireEvent.click(screen.getByRole('button', { name: /战力明细/ }));
    return container;
}

describe('PowerDetailPanel 转生第 6 行', () => {
    it('prestige>0 追加第 6 行「转生」（aria-label=转生加成），脚注升级为六行 + 转生加成', () => {
        openPanel(BASE_POWER + 25 + 10, {
            ...BASE_DETAIL,
            achievement: 25,
            prestige: 10,
        });

        expect(screen.getByRole('progressbar', { name: '转生加成' }).getAttribute('aria-valuenow')).toBe('10');
        // 六行齐全：base/ring/bone/core/achievement/prestige
        expect(document.querySelectorAll('[data-power-row]').length).toBe(6);

        const footnote = screen.getByText(/行求和恒等于总战力/).textContent ?? '';
        expect(footnote).toContain('六行');
        expect(footnote).toContain('转生加成');
        expect(footnote).toContain('成就加成');
    });

    it('prestige=0 隐藏转生行，脚注回退五行（成就行仍显示）', () => {
        openPanel(BASE_POWER + 25, { ...BASE_DETAIL, achievement: 25, prestige: 0 });

        expect(screen.queryByRole('progressbar', { name: '转生加成' })).toBeNull();
        expect(screen.getByRole('progressbar', { name: '成就加成' })).toBeTruthy();
        expect(document.querySelectorAll('[data-power-row]').length).toBe(5);
        expect(screen.getByText(/行求和恒等于总战力/).textContent).toContain('五行');
    });

    it('旧后端 achievement/prestige 缺失（undefined）→ 两可选行都隐藏，脚注回退四行', () => {
        openPanel(BASE_POWER, BASE_DETAIL);

        expect(screen.queryByRole('progressbar', { name: '成就加成' })).toBeNull();
        expect(screen.queryByRole('progressbar', { name: '转生加成' })).toBeNull();
        expect(document.querySelectorAll('[data-power-row]').length).toBe(4);
        expect(screen.getByText(/行求和恒等于总战力/).textContent).toContain('四行');
    });

    it('六行 share 求和恒等于总战力（DOM aria-valuenow 逐行累加断言）', () => {
        const power = BASE_POWER + 25 + 10;
        const container = openPanel(power, { ...BASE_DETAIL, achievement: 25, prestige: 10 });

        const sum = Array.from(container.querySelectorAll('[data-power-row]'))
            .map((row) => row.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow') ?? '0')
            .reduce((acc, v) => acc + Number(v), 0);
        expect(sum).toBe(power);
    });
});
