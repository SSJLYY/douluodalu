/**
 * 每日副本（第二十九轮，设计文档 §8）前端纯函数/常量镜像。
 * 与后端 GameBalance「每日副本」区块同源，改需双向同步（照 REAWAKEN_COST_GOLD 等镜像惯例）。
 */

/** 扫荡魂力费用镜像（后端 GameBalance.dungeonSweepSoulPowerCost 同式：50 + 玩家等级 × 5）。
 *  定价依据见后端 GameBalance 注释：文档「战斗魂力×3」语义不明（battle 不耗魂力，无量纲可乘），
 *  取与等级线性挂钩的温和定价。 */
export const DUNGEON_SWEEP_SOUL_POWER_BASE = 50;
export const DUNGEON_SWEEP_SOUL_POWER_PER_LEVEL = 5;

/** 扫荡魂力费用（与后端结算/状态预览同源单点；页面按钮与确认提示共用） */
export function dungeonSweepCost(level: number): number {
    return DUNGEON_SWEEP_SOUL_POWER_BASE + level * DUNGEON_SWEEP_SOUL_POWER_PER_LEVEL;
}

/** 五难度定义行（逐位镜像后端 GameBalance.DUNGEON_DEFS / 设计文档 §8 表；wiki 与副本页共用） */
export interface DungeonDefRow {
    name: string;
    difficulty: string;
    boss: string;
    hpMult: number;
    atkMult: number;
    gold: number;
    killing: number;
    dropTier: number;
    unlockPrestige: number;
}

export const DUNGEON_DEFS: DungeonDefRow[] = [
    { name: '魂兽森林', difficulty: '简单', boss: '千年魂兽·泰坦巨猿', hpMult: 3.0, atkMult: 1.8, gold: 5000, killing: 10, dropTier: 7, unlockPrestige: 1 },
    { name: '暗影峡谷', difficulty: '普通', boss: '暗影君王·鬼魅', hpMult: 5.0, atkMult: 2.5, gold: 15000, killing: 25, dropTier: 12, unlockPrestige: 2 },
    { name: '龙墓禁地', difficulty: '困难', boss: '远古龙皇·赤王', hpMult: 8.0, atkMult: 3.5, gold: 40000, killing: 50, dropTier: 17, unlockPrestige: 3 },
    { name: '神之遗迹', difficulty: '噩梦', boss: '堕落天使·路西法', hpMult: 12.0, atkMult: 5.0, gold: 100000, killing: 100, dropTier: 22, unlockPrestige: 4 },
    { name: '深渊之门', difficulty: '地狱', boss: '深渊之主·阿萨谢尔', hpMult: 20.0, atkMult: 8.0, gold: 300000, killing: 200, dropTier: 24, unlockPrestige: 5 },
];

/** 难度徽章色板（简单→地狱 递进；className 供页面/百科徽章着色，未知名降级灰色） */
const DUNGEON_DIFFICULTY_META: Record<string, string> = {
    '简单': 'bg-green-600',
    '普通': 'bg-blue-600',
    '困难': 'bg-purple-600',
    '噩梦': 'bg-orange-600',
    '地狱': 'bg-red-600',
};

export function dungeonDifficultyClassName(difficulty: string): string {
    return DUNGEON_DIFFICULTY_META[difficulty] ?? 'bg-gray-600';
}
