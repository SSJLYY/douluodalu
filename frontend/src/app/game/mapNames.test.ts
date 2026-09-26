import { describe, expect, it } from 'vitest';
import { MAP_NAMES } from '@/lib/maps';

/**
 * 地图名契约（与后端 MAP_NAMES / MAX_MAP_ID 7→10 扩展同步）：
 * game/page.tsx 经 lib/maps.ts 消费、game/wiki/page.tsx 地图分类为独立条目，
 * 此测试钉住数组形状防漂移。
 */
describe('MAP_NAMES（地图名契约，与后端同步）', () => {
    it('共 11 张地图：原 8 张在前，神王殿/至高神庭/创世之巅落在索引 8/9/10', () => {
        expect(MAP_NAMES).toHaveLength(11);
        expect(MAP_NAMES.slice(0, 8)).toEqual([
            '圣魂村', '诺丁城外', '星斗外围', '落日森林', '极北之地', '海神岛', '杀戮之都外域', '神界废墟',
        ]);
        expect(MAP_NAMES[8]).toBe('神王殿');
        expect(MAP_NAMES[9]).toBe('至高神庭');
        expect(MAP_NAMES[10]).toBe('创世之巅');
    });
});
