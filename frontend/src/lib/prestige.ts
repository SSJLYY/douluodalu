/** 转生门槛等级（与后端 GameBalance.PRESTIGE_MIN_LEVEL 同源，改需同步——同 breakthroughCost 的既有镜像惯例） */
export const PRESTIGE_MIN_LEVEL = 50;

/**
 * 转生按钮提示行 / title 文案（纯函数，便于单测；禁用态与就绪态共用，信息不依赖 hover）：
 * - 未达门槛：说明门槛与当前等级（按钮同时禁用）
 * - 已达标：预告本次转生的全属性倍率（每转 +10%，按转生后的新转数计）
 */
export function prestigeHint(level: number, prestigeCount: number): string {
    if (level < PRESTIGE_MIN_LEVEL) {
        return `转生需要 Lv.${PRESTIGE_MIN_LEVEL}（当前 Lv.${level}）`;
    }
    return `转生后全属性+${(prestigeCount + 1) * 10}%`;
}
