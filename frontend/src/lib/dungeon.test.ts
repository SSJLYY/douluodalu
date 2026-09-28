import { describe, expect, it } from 'vitest';
import {
    DUNGEON_DEFS,
    DUNGEON_SWEEP_SOUL_POWER_BASE,
    DUNGEON_SWEEP_SOUL_POWER_PER_LEVEL,
    dungeonDifficultyClassName,
    dungeonSweepCost,
} from '@/lib/dungeon';

/**
 * 每日副本契约钉（第二十九轮，设计文档 §8）：
 *  - 五难度定义表逐位钉文档（与后端 GameBalance.DUNGEON_DEFS 同源，漂移即测试红）；
 *  - 扫荡费用公式 = 50 + 等级 × 5（镜像后端 dungeonSweepSoulPowerCost）。
 */

/** 扫荡费用公式 */
describe('dungeonSweepCost（扫荡魂力费用镜像）', () => {
    it(`费用 = ${DUNGEON_SWEEP_SOUL_POWER_BASE} + 等级 × ${DUNGEON_SWEEP_SOUL_POWER_PER_LEVEL}`, () => {
        expect(dungeonSweepCost(1)).toBe(55);
        expect(dungeonSweepCost(5)).toBe(75);
        expect(dungeonSweepCost(50)).toBe(300);
        expect(dungeonSweepCost(100)).toBe(550);
    });
});

/** 五难度定义表（逐位照设计文档 §8） */
describe('DUNGEON_DEFS（五难度定义表，逐位照 §8 表）', () => {
    it('固定 5 个难度，解锁转数 1~5 递增', () => {
        expect(DUNGEON_DEFS).toHaveLength(5);
        expect(DUNGEON_DEFS.map((d) => d.unlockPrestige)).toEqual([1, 2, 3, 4, 5]);
    });

    it('名称/难度/Boss/倍率/奖励/掉落层级逐位照文档表', () => {
        expect(DUNGEON_DEFS.map((d) => d.name)).toEqual(['魂兽森林', '暗影峡谷', '龙墓禁地', '神之遗迹', '深渊之门']);
        expect(DUNGEON_DEFS.map((d) => d.difficulty)).toEqual(['简单', '普通', '困难', '噩梦', '地狱']);
        expect(DUNGEON_DEFS.map((d) => d.boss)).toEqual([
            '千年魂兽·泰坦巨猿', '暗影君王·鬼魅', '远古龙皇·赤王', '堕落天使·路西法', '深渊之主·阿萨谢尔',
        ]);
        expect(DUNGEON_DEFS.map((d) => d.hpMult)).toEqual([3.0, 5.0, 8.0, 12.0, 20.0]);
        expect(DUNGEON_DEFS.map((d) => d.atkMult)).toEqual([1.8, 2.5, 3.5, 5.0, 8.0]);
        expect(DUNGEON_DEFS.map((d) => d.gold)).toEqual([5000, 15000, 40000, 100000, 300000]);
        expect(DUNGEON_DEFS.map((d) => d.killing)).toEqual([10, 25, 50, 100, 200]);
        expect(DUNGEON_DEFS.map((d) => d.dropTier)).toEqual([7, 12, 17, 22, 24]);
    });

    it('奖励随难度单调递增（难度选择的预期管理）', () => {
        const golds = DUNGEON_DEFS.map((d) => d.gold);
        const killings = DUNGEON_DEFS.map((d) => d.killing);
        expect([...golds].sort((a, b) => a - b)).toEqual(golds);
        expect([...killings].sort((a, b) => a - b)).toEqual(killings);
    });
});

/** 难度徽章色板 */
describe('dungeonDifficultyClassName（难度徽章色板）', () => {
    it('五难度各有色板，未知难度降级灰色', () => {
        expect(dungeonDifficultyClassName('简单')).toBe('bg-green-600');
        expect(dungeonDifficultyClassName('地狱')).toBe('bg-red-600');
        expect(dungeonDifficultyClassName('不存在')).toBe('bg-gray-600');
    });
});
