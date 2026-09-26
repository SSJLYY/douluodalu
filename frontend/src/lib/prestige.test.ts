import { describe, expect, it } from 'vitest';
import { PRESTIGE_MIN_LEVEL, prestigeHint } from '@/lib/prestige';

/** 转生门槛判断/提示文案（纯函数）：禁用态与就绪态共用，信息不依赖 hover */
describe('prestigeHint（转生门槛/提示文案）', () => {
    it(`未达门槛：提示需要 Lv.${PRESTIGE_MIN_LEVEL} 与当前等级（Lv.49 边界）`, () => {
        expect(prestigeHint(PRESTIGE_MIN_LEVEL - 1, 0)).toBe(`转生需要 Lv.${PRESTIGE_MIN_LEVEL}（当前 Lv.49）`);
    });

    it(`恰好达门槛（Lv.${PRESTIGE_MIN_LEVEL}）：0 转预告转生后全属性+10%`, () => {
        expect(prestigeHint(PRESTIGE_MIN_LEVEL, 0)).toBe('转生后全属性+10%');
    });

    it('已多次转生：倍率按新转数（转数+1）×10% 计', () => {
        expect(prestigeHint(80, 2)).toBe('转生后全属性+30%');
    });
});
