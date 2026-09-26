import type { AwakenResult } from '@/lib/api';

/** 重醒花费金币（与后端 GameBalance 同源，改需同步——同 PRESTIGE_MIN_LEVEL 的既有镜像惯例；首醒免费） */
export const REAWAKEN_COST_GOLD = 5000;

/**
 * 武魂稀有度 → 中文标签 + 徽章配色（Tailwind 调色板类，不写死 hex，亮暗主题均可读）。
 * 与后端稀有度枚举同源：COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC。
 */
export const SOUL_RARITY_META: Record<string, { label: string; className: string }> = {
    COMMON: { label: '普通', className: 'text-gray-300 border-gray-500/60' },
    UNCOMMON: { label: '精良', className: 'text-green-400 border-green-500/60' },
    RARE: { label: '稀有', className: 'text-blue-400 border-blue-500/60' },
    EPIC: { label: '史诗', className: 'text-purple-400 border-purple-500/60' },
    LEGENDARY: { label: '传说', className: 'text-yellow-400 border-yellow-500/60' },
    MYTHIC: { label: '神话', className: 'text-red-400 border-red-500/60' },
};

/**
 * 觉醒/重醒品质权重表（数值来自设计文档 §2.2，与后端 GameBalance 同源，改需同步）。
 * 权重越高越常见：普通 40 → 神话 0.5。
 */
export const SOUL_RARITY_WEIGHTS: Record<string, number> = {
    COMMON: 40,
    UNCOMMON: 25,
    RARE: 15,
    EPIC: 8,
    LEGENDARY: 2,
    MYTHIC: 0.5,
};

/**
 * 品质池随转数扩展门槛表（与后端 GameBalance 同源，改需同步）：
 * 0 转精良及以下 / 1 转稀有 / 2 转史诗 / 3 转传说 / 5 转神话。
 */
export const SOUL_POOL_TABLE: ReadonlyArray<{ minPrestige: number; pool: string }> = [
    { minPrestige: 0, pool: '精良及以下' },
    { minPrestige: 1, pool: '稀有及以下' },
    { minPrestige: 2, pool: '史诗及以下' },
    { minPrestige: 3, pool: '传说及以下' },
    { minPrestige: 5, pool: '神话及以下' },
];

/**
 * 容错取稀有度元数据：未知/缺失 → null（旧后端 soulRarity 缺失或后端扩枚举时，
 * 消费方降级为「只显武魂名不显徽章」）。
 */
export function soulRarityMeta(rarity?: string | null): { label: string; className: string } | null {
    if (!rarity) return null;
    return SOUL_RARITY_META[rarity] ?? null;
}

/**
 * 当前转数下的品质池文案（重醒弹窗/wiki 共用）：
 * 从最高门槛往下取第一个已达转数档；负数等异常输入按 0 转兜底。
 */
export function soulPoolHint(prestigeCount: number): string {
    const current = SOUL_POOL_TABLE.reduce(
        (acc, row) => (prestigeCount >= row.minPrestige ? row : acc),
        SOUL_POOL_TABLE[0],
    );
    return `当前品质池：${current.pool}`;
}

/**
 * 觉醒/重醒结果 toast 文案：后端 message 照用；失败（金币不足等，success=false）原样透传；
 * 成功且稀有度达传说/神话时加 ✨🎉 前缀突出稀有结果（纯函数便于单测，page.tsx 禁止任意导出）。
 */
export function awakenToast(result: AwakenResult): string {
    if (!result.success) return result.message;
    if (result.rarity === 'LEGENDARY' || result.rarity === 'MYTHIC') {
        return `✨🎉 ${result.message}`;
    }
    return result.message;
}
