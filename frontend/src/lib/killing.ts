/**
 * 杀气商店工具（第二十九轮，照 lib/prestige.ts 的「纯函数 + 后端同源镜像」模式）。
 *
 * 后端契约（GameBalance.killingAttrCost 为唯一数值源）：属性购买价格 = 100 × 2^已购次数
 * （杀气计价，HP/ATK 两商品各自独立计数，无限次购买）。前端镜像仅用于展示层（余额预判
 * 禁用态 / 下一档价预告），实际扣费与校验以后端为准；改后端公式需双向同步。
 */

/** 属性购买基础价格（杀气）：第 n 次购买价格 = 100 × 2^已购次数（100/200/400/800...） */
export const KILLING_ATTR_BASE_COST = 100;

/** 价格封顶档位（与后端 GameBalance 一致夹取 [0, 56]）：2^56 × 100 ≈ 7.2e18，封顶仅数值防御 */
const MAX_BUYS_SHIFT = 56;

/**
 * 属性购买当前价格（杀气计价）：100 × 2^buys。
 * buys 夹取 [0, 56]（与后端同口径）：负数（脏数据）按 0 兜底；56 次购买在杀气产出速率
 * （1 + towerFloor/10 每胜）下不可达。注：JS 双精度下 2^n 恒精确、100×2^n 在 n>46 后
 * 超出 Number.MAX_SAFE_INTEGER 有浮点舍入——同样不可达，展示层不作处理。
 */
export function killingAttrCost(buys: number): number {
    const n = Math.min(Math.max(Math.trunc(buys), 0), MAX_BUYS_SHIFT);
    return KILLING_ATTR_BASE_COST * 2 ** n;
}

/** 称号属性预览的最小形状（对应后端 KillingTitleDto 的属性字段，owned/cost 不参与格式化） */
export interface KillingTitleAttrs {
    hp: number;
    atk: number;
    pdef: number;
    /** 暴击率加成（百分点：3 = 3%），与成就/词缀同量纲 */
    critRate: number;
    /** 暴击伤害加成（百分点） */
    critDmg: number;
}

/**
 * 称号卡属性预览行（纯函数，便于单测）：非零字段格式化为「HP+200」「攻+10」「防+10」
 * 「暴击+3%」「爆伤+15%」，零字段省略（title_1/2/3 无暴击系加成，不留「+0%」噪音）；
 * 全零返回空数组（调用方渲染兜底文案）。字段顺序固定 HP→攻→防→暴击→爆伤，与设计文档
 * §7.4 属性表列序一致。
 */
export function killingTitleAttrs(title: KillingTitleAttrs): string[] {
    const parts: string[] = [];
    if (title.hp !== 0) parts.push(`HP+${title.hp.toLocaleString()}`);
    if (title.atk !== 0) parts.push(`攻+${title.atk.toLocaleString()}`);
    if (title.pdef !== 0) parts.push(`防+${title.pdef.toLocaleString()}`);
    if (title.critRate !== 0) parts.push(`暴击+${title.critRate}%`);
    if (title.critDmg !== 0) parts.push(`爆伤+${title.critDmg}%`);
    return parts;
}
