import { describe, expect, it } from 'vitest';
import {
    BONE_AFFIX_META,
    affixChipClass,
    affixDisplay,
    parseBoneAffixes,
} from '@/lib/affix';

/**
 * 魂骨词缀解析工具单测：合法 JSON 解析与 display 格式（CRIT_RATE/CRIT_DMG 带百分点）、
 * 容错矩阵（非法 JSON / 非数组 / 元素缺字段逐条过滤 / 未知类型原名兜底 / null 零噪音）、
 * 缩写展示与配色映射锁定（与后端 BoneAffixType 五枚举同源）。
 */
describe('parseBoneAffixes（合法解析与 display 格式）', () => {
    it('合法 JSON：type/label/value 逐条解析，CRIT_RATE/CRIT_DMG 带百分点、三维不带', () => {
        const affixes = parseBoneAffixes('[{"type":"CRIT_RATE","value":5},{"type":"MATK","value":30}]');
        expect(affixes).toHaveLength(2);
        expect(affixes[0]).toEqual({ type: 'CRIT_RATE', label: '暴击率', value: 5, display: '暴击率 +5%' });
        expect(affixes[1]).toEqual({ type: 'MATK', label: '魔攻', value: 30, display: '魔攻 +30' });
    });

    it('五枚举全覆盖：CRIT_DMG/PDEF/MDEF 的 label 与 display 格式正确', () => {
        const affixes = parseBoneAffixes(
            '[{"type":"CRIT_DMG","value":150},{"type":"PDEF","value":40},{"type":"MDEF","value":0.5}]',
        );
        expect(affixes.map((a) => a.display)).toEqual(['暴击伤害 +150%', '物防 +40', '魔防 +0.5']);
    });
});

describe('parseBoneAffixes（容错矩阵：任何异常 → 空数组/逐条过滤）', () => {
    it('非法 JSON（parse 抛错）→ 空数组', () => {
        expect(parseBoneAffixes('not-json{{')).toEqual([]);
    });

    it('合法 JSON 但非数组（对象/字符串/数字/布尔）→ 空数组', () => {
        expect(parseBoneAffixes('{"type":"CRIT_RATE","value":5}')).toEqual([]);
        expect(parseBoneAffixes('"CRIT_RATE"')).toEqual([]);
        expect(parseBoneAffixes('42')).toEqual([]);
        expect(parseBoneAffixes('true')).toEqual([]);
    });

    it('元素缺字段/坏值逐条过滤：type 缺失、value 缺失/非数字均跳过，合法条保留', () => {
        const affixes = parseBoneAffixes(
            '[{"type":"CRIT_RATE"},{"value":5},{"type":"PDEF","value":"30"},' +
            '"junk",null,{"type":"MATK","value":30}]',
        );
        expect(affixes).toHaveLength(1);
        expect(affixes[0].type).toBe('MATK');
        // 全部无效 → 空数组（展示层零噪音）
        expect(parseBoneAffixes('[{"type":"CRIT_RATE"},{"value":5}]')).toEqual([]);
    });

    it('未知类型容错：label/display 显原名、数值不带百分点（展示层灰字降级）', () => {
        const affixes = parseBoneAffixes('[{"type":"SPEED","value":7}]');
        expect(affixes).toHaveLength(1);
        expect(affixes[0].label).toBe('SPEED');
        expect(affixes[0].display).toBe('SPEED +7');
        expect(affixChipClass('SPEED')).toBe('text-gray-400');
    });

    it('null / undefined / 空串 → 空数组（普通骨与旧数据零噪音）', () => {
        expect(parseBoneAffixes(null)).toEqual([]);
        expect(parseBoneAffixes(undefined)).toEqual([]);
        expect(parseBoneAffixes('')).toEqual([]);
    });
});

describe('affixDisplay 缩写与 BONE_AFFIX_META 映射锁定', () => {
    it('compact=true 走槽位缩写：暴 +5% / 爆伤 +150% / 物防 +30；未知类型原名兜底', () => {
        const crit = parseBoneAffixes('[{"type":"CRIT_RATE","value":5}]')[0];
        const dmg = parseBoneAffixes('[{"type":"CRIT_DMG","value":150}]')[0];
        const pdef = parseBoneAffixes('[{"type":"PDEF","value":30}]')[0];
        const unknown = parseBoneAffixes('[{"type":"SPEED","value":7}]')[0];
        expect(affixDisplay(crit, true)).toBe('暴 +5%');
        expect(affixDisplay(dmg, true)).toBe('爆伤 +150%');
        expect(affixDisplay(pdef, true)).toBe('物防 +30');
        expect(affixDisplay(unknown, true)).toBe('SPEED +7');
    });

    it('五枚举配色与缩写表锁定（Tailwind 类型色，改需与组件视觉同步）', () => {
        expect(BONE_AFFIX_META.CRIT_RATE).toMatchObject({ label: '暴击率', short: '暴', className: 'text-yellow-400', percent: true });
        expect(BONE_AFFIX_META.CRIT_DMG).toMatchObject({ label: '暴击伤害', short: '爆伤', className: 'text-orange-400', percent: true });
        expect(BONE_AFFIX_META.PDEF).toMatchObject({ label: '物防', short: '物防', className: 'text-sky-400', percent: false });
        expect(BONE_AFFIX_META.MDEF).toMatchObject({ label: '魔防', short: '魔防', className: 'text-indigo-400', percent: false });
        expect(BONE_AFFIX_META.MATK).toMatchObject({ label: '魔攻', short: '魔攻', className: 'text-fuchsia-400', percent: false });
    });
});
