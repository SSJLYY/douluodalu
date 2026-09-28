import { describe, expect, it } from 'vitest';
import { KILLING_ATTR_BASE_COST, killingAttrCost, killingTitleAttrs } from '@/lib/killing';

/**
 * 杀气商店纯函数契约钉（第二十九轮）：killingAttrCost 与后端 GameBalance.killingAttrCost
 * 同源镜像（100 × 2^已购次数，杀气计价），锚点 0/1/2 次 → 100/200/400 钉死前后端契约；
 * killingTitleAttrs 钉称号卡属性预览的格式化口径（零字段省略、列序同设计文档 §7.4）。
 */
describe('killingAttrCost（属性购买价格 100×2^n，后端 GameBalance 镜像）', () => {
    it('锚点：0/1/2 次购买 → 100/200/400（设计文档 §7.4 价格递增序列）', () => {
        expect(killingAttrCost(0)).toBe(100);
        expect(killingAttrCost(1)).toBe(200);
        expect(killingAttrCost(2)).toBe(400);
    });

    it('继续递增：3 次 → 800，10 次 → 102400（2 的幂逐位）', () => {
        expect(killingAttrCost(3)).toBe(800);
        expect(killingAttrCost(10)).toBe(KILLING_ATTR_BASE_COST * 1024);
    });

    it('脏数据兜底：负数购买次数按 0 档计价（与后端 coerceIn(0, 56) 同口径）', () => {
        expect(killingAttrCost(-1)).toBe(100);
        expect(killingAttrCost(-99)).toBe(100);
    });

    it('封顶防御：>56 次夹取到 56 档（2^56×100 ≈ 7.2e18，产出速率下不可达）', () => {
        expect(killingAttrCost(57)).toBe(killingAttrCost(56));
        expect(killingAttrCost(1000)).toBe(killingAttrCost(56));
    });
});

describe('killingTitleAttrs（称号卡属性预览格式化）', () => {
    it('零字段省略：title_1（HP+200 攻+10）不出现「防/暴击/爆伤 +0」噪音', () => {
        expect(killingTitleAttrs({ hp: 200, atk: 10, pdef: 0, critRate: 0, critDmg: 0 }))
            .toEqual(['HP+200', '攻+10']);
    });

    it('全字段按 HP→攻→防→暴击→爆伤 列序（同设计文档 §7.4 属性表）', () => {
        // 千分位走同一运行时的 toLocaleString（与实现同调用，断言不随测试环境语区漂移）
        const n = (v: number) => v.toLocaleString();
        expect(killingTitleAttrs({ hp: 8000, atk: 200, pdef: 40, critRate: 5, critDmg: 15 }))
            .toEqual([`HP+${n(8000)}`, `攻+${n(200)}`, `防+${n(40)}`, '暴击+5%', '爆伤+15%']);
    });

    it('全部为零 → 空数组（调用方渲染兜底文案，不留空行）', () => {
        expect(killingTitleAttrs({ hp: 0, atk: 0, pdef: 0, critRate: 0, critDmg: 0 })).toEqual([]);
    });
});
