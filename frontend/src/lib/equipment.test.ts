import { describe, expect, it } from 'vitest';
import {
    BONE_ENHANCE_BASE_COST,
    BONE_ENHANCE_MAX_LEVEL,
    BONE_QUALITY_MULT,
    boneEnhanceCost,
    boneEnhanceMaxed,
} from '@/lib/equipment';

/**
 * 魂骨强化费用工具单测：公式锚点值（与后端 GameBalance 同源公式的实算结果）、
 * Math.floor 截断对齐后端 toLong、负数/越界容错、+15 上限判断、镜像常量锁定。
 * 锚点按契约公式 cost = 800 × (currentLevel+1)² × (yearOrdinal+1) × QUALITY_MULT[qualityOrdinal]。
 */
describe('boneEnhanceCost（强化费用公式，与后端 GameBalance 同源）', () => {
    it('锚点 1：yearOrdinal=0 / qualityOrdinal=0 / 0→1 级 = 800×1×1×1.0 = 800', () => {
        expect(boneEnhanceCost(0, 0, 0)).toBe(800);
    });

    it('锚点 2：yearOrdinal=1 / qualityOrdinal=2 / 3→4 级 = 800×16×2×1.5 = 38400', () => {
        expect(boneEnhanceCost(1, 2, 3)).toBe(38400);
    });

    it('锚点 3：yearOrdinal=4 / qualityOrdinal=4 / 9→10 级 = 800×100×5×2.2 = 880000', () => {
        expect(boneEnhanceCost(4, 4, 9)).toBe(880000);
    });

    it('公式实算抽检 1.2/1.8 档，Math.floor 对齐后端 toLong 截断', () => {
        expect(boneEnhanceCost(0, 1, 0)).toBe(960); // 800×1×1×1.2
        expect(boneEnhanceCost(0, 3, 2)).toBe(12960); // 800×9×1×1.8
        expect(boneEnhanceCost(2, 2, 4)).toBe(90000); // 800×25×3×1.5
    });

    it('容错：负数年份/等级按 0 兜底、qualityOrdinal 越界/负数按 1.0 兜底（UI 不出 NaN/负数）', () => {
        expect(boneEnhanceCost(-1, 0, 0)).toBe(800);
        expect(boneEnhanceCost(0, 0, -3)).toBe(800);
        expect(boneEnhanceCost(0, 9, 0)).toBe(800);
        expect(boneEnhanceCost(0, -1, 0)).toBe(800);
        expect(Number.isNaN(boneEnhanceCost(0, 0, 0))).toBe(false);
    });
});

describe('boneEnhanceMaxed（+15 上限判断）与镜像常量', () => {
    it('上限判断：14 未满 / 15 已满 / 越界值也按已满兜底', () => {
        expect(boneEnhanceMaxed(0)).toBe(false);
        expect(boneEnhanceMaxed(14)).toBe(false);
        expect(boneEnhanceMaxed(15)).toBe(true);
        expect(boneEnhanceMaxed(16)).toBe(true);
    });

    it('镜像常量与后端 GameBalance 同源：基数 800、上限 +15、五档品质系数', () => {
        expect(BONE_ENHANCE_BASE_COST).toBe(800);
        expect(BONE_ENHANCE_MAX_LEVEL).toBe(15);
        expect(BONE_QUALITY_MULT).toEqual([1.0, 1.2, 1.5, 1.8, 2.2]);
    });
});
