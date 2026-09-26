import { describe, expect, it } from 'vitest';
import type { AwakenResult } from '@/lib/api';
import {
    awakenToast,
    REAWAKEN_COST_GOLD,
    soulPoolHint,
    soulRarityMeta,
    SOUL_RARITY_META,
    SOUL_RARITY_WEIGHTS,
} from '@/lib/soul';

/**
 * 武魂纯函数单测：稀有度中文名/色板映射、未知/缺失容错、品质池随转数扩展文案、
 * 觉醒/重醒 toast 文案（传说/神话加前缀、失败照透传）。
 */

/** 成功结果样板（按用例覆盖 rarity/success） */
const RESULT: AwakenResult = {
    success: true,
    martialSoulName: '六翼天使',
    rarity: 'COMMON',
    goldSpent: 0,
    reawakened: false,
    message: '觉醒成功：六翼天使（普通）！',
};

describe('soulRarityMeta（稀有度中文名/色板映射）', () => {
    it('六档枚举逐一映射中文标签：普通/精良/稀有/史诗/传说/神话', () => {
        expect(Object.keys(SOUL_RARITY_META)).toEqual([
            'COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC',
        ]);
        const expectLabels: Record<string, string> = {
            COMMON: '普通',
            UNCOMMON: '精良',
            RARE: '稀有',
            EPIC: '史诗',
            LEGENDARY: '传说',
            MYTHIC: '神话',
        };
        for (const [key, label] of Object.entries(expectLabels)) {
            const meta = soulRarityMeta(key);
            expect(meta).not.toBeNull();
            expect(meta?.label).toBe(label);
        }
    });

    it('每档配色都是主题调色板类（text-* + border-*，不写死 hex）', () => {
        for (const meta of Object.values(SOUL_RARITY_META)) {
            expect(meta.className).toMatch(/text-\S+/);
            expect(meta.className).toMatch(/border-\S+/);
            expect(meta.className).not.toMatch(/#[0-9a-fA-F]{3,8}/);
        }
        // 契约示例档位：EPIC 紫、MYTHIC 红橙（红）
        expect(SOUL_RARITY_META.EPIC.className).toContain('text-purple-400');
        expect(SOUL_RARITY_META.MYTHIC.className).toContain('text-red-400');
        expect(SOUL_RARITY_META.LEGENDARY.className).toContain('text-yellow-400');
    });

    it('未知/缺失容错：undefined、null、空串、未注册枚举 → null（消费方降级只显名字）', () => {
        expect(soulRarityMeta(undefined)).toBeNull();
        expect(soulRarityMeta(null)).toBeNull();
        expect(soulRarityMeta('')).toBeNull();
        expect(soulRarityMeta('SSR')).toBeNull();
        expect(soulRarityMeta('common')).toBeNull(); // 大小写敏感，防止静默错配
    });
});

describe('soulPoolHint（品质池随转数扩展文案）', () => {
    it('0/1/2/3/5 转门槛逐档切换：精良及以下 → 神话及以下', () => {
        expect(soulPoolHint(0)).toBe('当前品质池：精良及以下');
        expect(soulPoolHint(1)).toBe('当前品质池：稀有及以下');
        expect(soulPoolHint(2)).toBe('当前品质池：史诗及以下');
        expect(soulPoolHint(3)).toBe('当前品质池：传说及以下');
        expect(soulPoolHint(5)).toBe('当前品质池：神话及以下');
    });

    it('档间/超额/异常转数取已达的最高档：4 转=传说、9 转=神话、负数兜底 0 转', () => {
        expect(soulPoolHint(4)).toBe('当前品质池：传说及以下');
        expect(soulPoolHint(9)).toBe('当前品质池：神话及以下');
        expect(soulPoolHint(-1)).toBe('当前品质池：精良及以下');
    });
});

describe('awakenToast（觉醒/重醒结果文案）', () => {
    it('成功：后端 message 照用；传说/神话加 ✨🎉 前缀', () => {
        expect(awakenToast(RESULT)).toBe('觉醒成功：六翼天使（普通）！');
        expect(awakenToast({ ...RESULT, rarity: 'LEGENDARY', message: '觉醒成功：六翼天使（传说）！' }))
            .toBe('✨🎉 觉醒成功：六翼天使（传说）！');
        expect(awakenToast({ ...RESULT, rarity: 'MYTHIC', message: '重醒成功：昊天锤（神话）！' }))
            .toBe('✨🎉 重醒成功：昊天锤（神话）！');
    });

    it('失败（重醒金币不足 success=false）→ message 照显不加前缀', () => {
        expect(awakenToast({
            ...RESULT,
            success: false,
            rarity: 'MYTHIC',
            message: '金币不足，重醒需要 5000 金币',
        })).toBe('金币不足，重醒需要 5000 金币');
    });
});

describe('soul 规则镜像常量（与后端 GameBalance 同源）', () => {
    it('重醒花费 5000 金、六档权重与设计文档 §2.2 一致', () => {
        expect(REAWAKEN_COST_GOLD).toBe(5000);
        expect(SOUL_RARITY_WEIGHTS).toEqual({
            COMMON: 40,
            UNCOMMON: 25,
            RARE: 15,
            EPIC: 8,
            LEGENDARY: 2,
            MYTHIC: 0.5,
        });
    });
});
