import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AffixChips from '@/components/AffixChips';

/** 后端契约样例：稀有骨词缀 JSON（CRIT_RATE/CRIT_DMG 百分点 + 三维平数值） */
const VALID_JSON = '[{"type":"CRIT_RATE","value":5},{"type":"CRIT_DMG","value":150},{"type":"PDEF","value":30}]';

describe('AffixChips 词缀 chips 渲染与配色', () => {
    it('affixesJson 非空 → 按 testid affix-chips-item-{index} 渲染全称 chips，类型色逐条对应', () => {
        render(<AffixChips affixesJson={VALID_JSON} variant="item" index={3} />);

        const root = screen.getByTestId('affix-chips-item-3');
        expect(root.className).toContain('text-[11px]');
        const chips = Array.from(root.children) as HTMLElement[];
        expect(chips).toHaveLength(3);
        expect(chips[0].textContent).toBe('暴击率 +5%');
        expect(chips[0].className).toContain('text-yellow-400');
        expect(chips[1].textContent).toBe('暴击伤害 +150%');
        expect(chips[1].className).toContain('text-orange-400');
        expect(chips[2].textContent).toBe('物防 +30');
        expect(chips[2].className).toContain('text-sky-400');
    });

    it('variant="slot" → 缩写 + text-[10px]（槽位窄卡），testid affix-chips-slot-{index}', () => {
        render(<AffixChips affixesJson={VALID_JSON} variant="slot" index={2} />);

        const root = screen.getByTestId('affix-chips-slot-2');
        expect(root.className).toContain('text-[10px]');
        const chips = Array.from(root.children).map((c) => c.textContent);
        expect(chips).toEqual(['暴 +5%', '爆伤 +150%', '物防 +30']);
        // item 全称 testid 不存在（variant 决定 testid 前缀）
        expect(screen.queryByTestId('affix-chips-item-2')).toBeNull();
    });

    it('未知类型 → 灰字显原名（text-gray-400 容错降级，不崩溃）', () => {
        render(<AffixChips affixesJson='[{"type":"SPEED","value":7}]' variant="item" index={0} />);

        const chip = screen.getByTestId('affix-chips-item-0').firstElementChild as HTMLElement;
        expect(chip.textContent).toBe('SPEED +7');
        expect(chip.className).toContain('text-gray-400');
    });
});

describe('AffixChips 零噪音降级与点击隔离', () => {
    it.each([
        ['null', null],
        ['undefined', undefined],
        ['空串', ''],
        ['非法 JSON', 'not-json{{'],
        ['非数组 JSON', '{"type":"CRIT_RATE","value":5}'],
        ['全无效元素', '[{"value":5},"junk"]'],
    ])('affixesJson 为 %s → 不渲染任何 DOM', (_name, json) => {
        const { container } = render(<AffixChips affixesJson={json} variant="item" index={0} />);
        expect(container.childElementCount).toBe(0);
    });

    it('根节点 stopPropagation：点击 chips 不冒泡到宿主（背包卡=装备/骨槽=卸下）', () => {
        let bubbled = 0;
        const { getByTestId } = render(
            <div onClick={() => { bubbled += 1; }}>
                <AffixChips affixesJson={VALID_JSON} variant="item" index={0} />
            </div>,
        );
        fireEvent.click(getByTestId('affix-chips-item-0'));
        expect(bubbled).toBe(0);
    });
});
