'use client';

import { affixChipClass, affixDisplay, parseBoneAffixes } from '@/lib/affix';

/**
 * 魂骨词缀 chips（纯展示组件，照 BossRankPanel 从页面抽出的先例，便于单测与两处复用）。
 *
 * - variant="item"：背包 BONE 卡 → 全称 + text-[11px]（「暴击率 +5%」）；
 * - variant="slot"：已装备骨槽 → 缩写 + text-[10px]（「暴 +5%」，375px 窄槽 flex-wrap 不溢出；
 *   缩写映射见 lib/affix.ts BONE_AFFIX_META.short 注释）；
 * - affixesJson 为 null/空/非法/全无效 → 返回 null 不渲染任何 DOM（零噪音降级）；
 * - 根节点 stopPropagation：chips 纯展示不承载点击语义，两处宿主都可点
 *   （背包卡=装备、骨槽=卸下），避免点词缀误触（同槽内强化按钮 stopPropagation 惯例）。
 */
export default function AffixChips({
    affixesJson,
    variant = 'item',
    index = 0,
}: {
    affixesJson: string | null | undefined;
    /** item=背包 BONE 卡（全称 11px）/ slot=已装备骨槽（缩写 10px） */
    variant?: 'item' | 'slot';
    /** testid 下标：item 用背包 BONE 子列表下标（同 enhance-btn-item-* 口径），slot 用槽位 0-5 */
    index?: number;
}) {
    const affixes = parseBoneAffixes(affixesJson);
    if (affixes.length === 0) return null;
    const compact = variant === 'slot';
    return (
        <div
            data-testid={`affix-chips-${variant}-${index}`}
            onClick={(e) => e.stopPropagation()}
            className={`${compact ? 'text-[10px]' : 'text-[11px]'} mt-1 flex flex-wrap gap-1`}
        >
            {affixes.map((a, i) => (
                <span
                    key={`${a.type}-${i}`}
                    className={`rounded bg-black/25 px-1 ${affixChipClass(a.type)}`}
                >
                    {affixDisplay(a, compact)}
                </span>
            ))}
        </div>
    );
}
