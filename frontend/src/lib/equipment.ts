/**
 * 魂骨强化费用工具（与后端 GameBalance 强化常量同源，改需双向同步——
 * 照 REAWAKEN_COST_GOLD / RESCHOOL_COST_GOLD 的既有镜像惯例）。
 * 前端用途：装备页强化按钮的费用预览（后端金额以 /api/equipment/bone/enhance 的实际响应文案为准）。
 */

/** 魂骨强化费用基数（与后端 GameBalance 同源）：cost = 800 × (currentLevel+1)² × (yearOrdinal+1) × 品质系数 */
export const BONE_ENHANCE_BASE_COST = 800;

/** 魂骨强化等级上限（与后端 GameBalance 同源）：+15 后不可继续强化 */
export const BONE_ENHANCE_MAX_LEVEL = 15;

/**
 * 魂骨品质系数表（下标 = qualityOrdinal，与后端 GameBalance 同源，改需同步）：
 * 五档依次 1.0 / 1.2 / 1.5 / 1.8 / 2.2。
 */
export const BONE_QUALITY_MULT: readonly number[] = [1.0, 1.2, 1.5, 1.8, 2.2];

/**
 * 魂骨强化到下一级的金币费用（费用预览镜像，公式与后端 GameBalance 同源）：
 *   cost = 800 × (currentLevel+1)² × (yearOrdinal+1) × BONE_QUALITY_MULT[qualityOrdinal]
 * Math.floor 对齐后端 toLong 截断（同序 double 乘法 → 同值同截断）。
 * 容错（后端本有参数校验，这里只为 UI 不出 NaN/负数）：
 * - yearOrdinal/currentLevel 为负 → 按 0 兜底；
 * - qualityOrdinal 越界/非法 → 系数按 1.0 兜底。
 */
export function boneEnhanceCost(yearOrdinal: number, qualityOrdinal: number, currentLevel: number): number {
    const yearFactor = Math.max(0, Math.floor(yearOrdinal)) + 1;
    const levelFactor = Math.max(0, Math.floor(currentLevel)) + 1;
    const qualityMult = BONE_QUALITY_MULT[qualityOrdinal] ?? 1.0;
    return Math.floor(BONE_ENHANCE_BASE_COST * levelFactor * levelFactor * yearFactor * qualityMult);
}

/**
 * 魂骨是否已达强化上限（+15）：强化按钮「已满级」禁用判断。
 * 超过上限的异常值也按已满处理（等级只增不减，宁可少显示一次按钮也不误报价）。
 */
export function boneEnhanceMaxed(level: number): boolean {
    return level >= BONE_ENHANCE_MAX_LEVEL;
}
