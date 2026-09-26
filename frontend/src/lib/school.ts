/**
 * 重选流派花费金币（与后端 GameBalance.RESCHOOL_COST_GOLD 同源，改需同步——同 REAWAKEN_COST_GOLD 的既有镜像惯例；首选免费）。
 */
export const RESCHOOL_COST_GOLD = 5000;

/**
 * 流派枚举元数据（与后端 School 枚举同源：BALANCED|PHYSICAL|MAGIC|SUPPORT|CONTROL|ASSASSIN，键序即弹窗/wiki 展示序）。
 * - requiredLevel/requiredPrestige：选择门槛（与后端 GameBalance 同源，改需同步），0 表示无要求（开局可选）；
 * - modsSummary：系数文案，与后端实际系数逐字对应（选择弹窗/wiki 共用，勿改措辞）；
 * - description：流派定位一句话说明。
 */
export const SCHOOL_META: Record<string, {
    icon: string;
    label: string;
    description: string;
    modsSummary: string;
    requiredLevel: number;
    requiredPrestige: number;
}> = {
    BALANCED: {
        icon: '⚖️',
        label: '均衡流派',
        description: '攻防均衡的稳健路线，无明显短板',
        modsSummary: '生命/双防+5%，暴击爆伤+5%',
        requiredLevel: 0,
        requiredPrestige: 0,
    },
    PHYSICAL: {
        icon: '⚔️',
        label: '物理流派',
        description: '极限物理输出，牺牲魔法攻击与魔防',
        modsSummary: '物攻+30%，魔攻-55%，物防+15%，魔防-30%，暴击+8%',
        requiredLevel: 0,
        requiredPrestige: 0,
    },
    MAGIC: {
        icon: '🔮',
        label: '法系流派',
        description: '极限魔法输出，牺牲物理攻击与物防',
        modsSummary: '魔攻+30%，物攻-55%，魔防+15%，物防-30%，爆伤+8%',
        requiredLevel: 0,
        requiredPrestige: 0,
    },
    SUPPORT: {
        icon: '🛡️',
        label: '辅助流派',
        description: '高生命高双防的坦克路线，牺牲部分输出',
        modsSummary: '生命+15%/双防+20%，物攻-20%，暴击+3%',
        requiredLevel: 50,
        requiredPrestige: 1,
    },
    CONTROL: {
        icon: '🌿',
        label: '控制流派',
        description: '魔攻与生命兼顾的消耗控制路线',
        modsSummary: '魔攻+10%/生命+5%，暴击爆伤+6%',
        requiredLevel: 70,
        requiredPrestige: 2,
    },
    ASSASSIN: {
        icon: '🗡️',
        label: '暗杀流派',
        description: '极限暴击爆发路线，以生命与双防为代价',
        modsSummary: '物攻+40%，生命-10%/双防-40%，暴击+12% 爆伤+15%',
        requiredLevel: 90,
        requiredPrestige: 3,
    },
};

/** 流派徽章配色（Tailwind 调色板类，不写死 hex，亮暗主题均可读；主题色与战力明细「🎓 流派」行的 bg-indigo-500 同族） */
const SCHOOL_BADGE_CLASS: Record<string, string> = {
    BALANCED: 'text-yellow-400 border-yellow-500/60',
    PHYSICAL: 'text-red-400 border-red-500/60',
    MAGIC: 'text-indigo-400 border-indigo-500/60',
    SUPPORT: 'text-green-400 border-green-500/60',
    CONTROL: 'text-teal-400 border-teal-500/60',
    ASSASSIN: 'text-purple-400 border-purple-500/60',
};

/**
 * 容错取流派徽章元数据：未知/缺失 → null（旧后端 chosenSchool 为 null=未选，或后端扩枚举时，
 * 消费方降级为「未选流派」灰字）。
 */
export function schoolBadgeMeta(chosenSchool?: string | null): { icon: string; label: string; className: string } | null {
    if (!chosenSchool) return null;
    const meta = SCHOOL_META[chosenSchool];
    if (!meta) return null;
    return {
        icon: meta.icon,
        label: meta.label,
        className: SCHOOL_BADGE_CLASS[chosenSchool] ?? 'text-gray-300 border-gray-500/60',
    };
}

/**
 * 流派门槛纯文案（wiki 静态表用，不掺玩家当前进度）：
 * 开局可选 → 「开局可选」；有门槛 → 「需要 Lv.50 且转生≥1」形式；未知流派 → ''。
 */
export function schoolRequirementText(school: string): string {
    const meta = SCHOOL_META[school];
    if (!meta) return '';
    if (meta.requiredLevel === 0 && meta.requiredPrestige === 0) return '开局可选';
    return `需要 Lv.${meta.requiredLevel}${meta.requiredPrestige > 0 ? ` 且转生≥${meta.requiredPrestige}` : ''}`;
}

/**
 * 是否满足流派选择门槛（与后端「按选时校验」同口径：等级与转数同时达标；未知流派一律 false）。
 * 转生后等级回 1 但流派保留、转数只增不减，故已选流派可能在转生后暂不满足自家门槛（弹窗按「当前」优先展示）。
 */
export function schoolUnlocked(school: string, level: number, prestigeCount: number): boolean {
    const meta = SCHOOL_META[school];
    if (!meta) return false;
    return level >= meta.requiredLevel && prestigeCount >= meta.requiredPrestige;
}

/**
 * 流派门槛提示文案（选择弹窗共用）：
 * - 开局可选流派 → 「开局可选」；
 * - 有门槛且已达标 → 「需要 Lv.50 且转生≥1」（保留门槛说明本身）；
 * - 有门槛未达标 → 追加当前进度，帮助玩家规划（「需要 Lv.70 且转生≥2（当前 Lv.70/1转）」）。
 */
export function schoolUnlockHint(school: string, level: number, prestigeCount: number): string {
    const base = schoolRequirementText(school);
    if (!base || base === '开局可选' || schoolUnlocked(school, level, prestigeCount)) return base;
    return `${base}（当前 Lv.${level}${prestigeCount > 0 ? `/${prestigeCount}转` : ''}）`;
}
