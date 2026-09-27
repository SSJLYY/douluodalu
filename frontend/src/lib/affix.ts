/**
 * 魂骨词缀解析与展示工具（照 lib/equipment.ts / lib/soul.ts 的纯函数+同位测试模式）。
 *
 * 后端契约（BackpackItem.affixesJson / EquippedBone.affixesJson 开始携带真实数据）：
 *   [{"type":"CRIT_RATE","value":5},{"type":"MATK","value":30}]
 * - 枚举 5 种，条数随品质 1~5；掉落来源为塔/宗门 Boss 的稀有骨；
 * - 普通战斗掉落骨与既有骨 affixesJson=null（无词缀，展示层不渲染任何 chips）。
 * 容错原则（照 api.ts normalizeAchievements 先例）：非法 JSON / 非数组 / 元素缺字段
 * 一律过滤或空数组，展示层永不为词缀数据崩溃。
 */

/** 骨词缀类型枚举（与后端 BoneAffixType 同源，改需双向同步） */
export type BoneAffixType = 'CRIT_RATE' | 'CRIT_DMG' | 'PDEF' | 'MDEF' | 'MATK';

/**
 * 词缀元数据：中文标签 / 槽位缩写 / Tailwind 类型色 / 是否百分点。
 * - label：背包 BONE 卡全称展示（如「暴击率 +5%」）；
 * - short：已装备骨槽缩写展示（槽位窄，如「暴 +5%」）。缩写映射（全文唯一出处）：
 *     暴击率→「暴」  暴击伤害→「爆伤」  物防→「物防」  魔防→「魔防」  魔攻→「魔攻」
 *   （两条暴击系最易混，故 暴/爆伤 区分；三维本就两字，保持全称不损失可读性）
 * - className：类型色（Tailwind 调色板类，照 SOUL_RARITY_META 惯例不写死 hex）；
 * - percent：true 时数值按百分点追加 %（CRIT_RATE/CRIT_DMG，与 CombatStats 同口径）。
 */
export const BONE_AFFIX_META: Record<
    BoneAffixType,
    { label: string; short: string; className: string; percent: boolean }
> = {
    CRIT_RATE: { label: '暴击率', short: '暴', className: 'text-yellow-400', percent: true },
    CRIT_DMG: { label: '暴击伤害', short: '爆伤', className: 'text-orange-400', percent: true },
    PDEF: { label: '物防', short: '物防', className: 'text-sky-400', percent: false },
    MDEF: { label: '魔防', short: '魔防', className: 'text-indigo-400', percent: false },
    MATK: { label: '魔攻', short: '魔攻', className: 'text-fuchsia-400', percent: false },
};

/** 解析后的单条词缀（display 为背包卡全称格式，如「暴击率 +5%」「物防 +30」） */
export interface ParsedBoneAffix {
    type: string;
    label: string;
    value: number;
    display: string;
}

/**
 * 单条词缀展示文案：compact=true 用槽位缩写（「暴 +5%」「物防 +30」），否则全称（「暴击率 +5%」）。
 * 未知类型（后端扩枚举前端未跟）→ 原名兜底（消费方配色走 affixChipClass 灰字降级）。
 */
export function affixDisplay(affix: ParsedBoneAffix, compact = false): string {
    const meta = BONE_AFFIX_META[affix.type as BoneAffixType];
    const name = meta ? (compact ? meta.short : meta.label) : affix.type;
    return `${name} +${affix.value}${meta?.percent ? '%' : ''}`;
}

/**
 * 词缀 chip 配色：已知类型走 BONE_AFFIX_META 类型色，未知类型灰字显原名（容错降级，
 * 照 soulRarityMeta「未知 → 消费方降级」的既有惯例）。
 */
export function affixChipClass(type: string): string {
    return BONE_AFFIX_META[type as BoneAffixType]?.className ?? 'text-gray-400';
}

/**
 * 解析 affixesJson 为词缀数组。容错矩阵（任何异常 → 空数组，正常元素缺失则逐条过滤）：
 * - null / undefined / 空串 → []（普通骨与旧数据零噪音）；
 * - 非法 JSON（JSON.parse 抛错）→ []；
 * - 合法 JSON 但不是数组（对象/字符串/数字）→ []；
 * - 元素非对象，或缺 type/value，或 type 非非空字符串、value 非有限数字 → 该条过滤；
 * - type 不在 5 枚举内 → 保留，label=原名（展示层灰字降级）。
 */
export function parseBoneAffixes(json: string | null | undefined): ParsedBoneAffix[] {
    if (!json) return [];
    let parsed: unknown;
    try {
        parsed = JSON.parse(json);
    } catch {
        return [];
    }
    if (!Array.isArray(parsed)) return [];
    const out: ParsedBoneAffix[] = [];
    for (const raw of parsed) {
        if (typeof raw !== 'object' || raw === null) continue;
        const { type, value } = raw as Record<string, unknown>;
        if (typeof type !== 'string' || type === '') continue;
        if (typeof value !== 'number' || !Number.isFinite(value)) continue;
        const meta = BONE_AFFIX_META[type as BoneAffixType];
        const affix: ParsedBoneAffix = {
            type,
            label: meta?.label ?? type,
            value,
            display: '',
        };
        affix.display = affixDisplay(affix);
        out.push(affix);
    }
    return out;
}
