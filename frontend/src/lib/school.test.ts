import { describe, expect, it } from 'vitest';
import {
    RESCHOOL_COST_GOLD,
    schoolBadgeMeta,
    schoolRequirementText,
    schoolUnlocked,
    schoolUnlockHint,
    SCHOOL_META,
} from '@/lib/school';

/**
 * 流派纯函数单测：六档枚举中文名/icon/系数文案映射、门槛表、未知/缺失容错、
 * 门槛文案（开局可选/需要 Lv+转生/未达标附当前进度）、门槛判定边界。
 */

describe('SCHOOL_META（六档流派映射，与后端契约逐字同源）', () => {
    it('六档枚举逐一映射中文标签与 icon，键序即展示序', () => {
        expect(Object.keys(SCHOOL_META)).toEqual([
            'BALANCED', 'PHYSICAL', 'MAGIC', 'SUPPORT', 'CONTROL', 'ASSASSIN',
        ]);
        const expectIcons: Record<string, string> = {
            BALANCED: '⚖️',
            PHYSICAL: '⚔️',
            MAGIC: '🔮',
            SUPPORT: '🛡️',
            CONTROL: '🌿',
            ASSASSIN: '🗡️',
        };
        const expectLabels: Record<string, string> = {
            BALANCED: '均衡流派',
            PHYSICAL: '物理流派',
            MAGIC: '法系流派',
            SUPPORT: '辅助流派',
            CONTROL: '控制流派',
            ASSASSIN: '暗杀流派',
        };
        for (const key of Object.keys(SCHOOL_META)) {
            expect(SCHOOL_META[key].icon).toBe(expectIcons[key]);
            expect(SCHOOL_META[key].label).toBe(expectLabels[key]);
        }
    });

    it('门槛表：3 基础流派开局可选，SUPPORT/CONTROL/ASSASSIN 为 Lv.50+1转 / Lv.70+2转 / Lv.90+3转', () => {
        expect(SCHOOL_META.BALANCED.requiredLevel).toBe(0);
        expect(SCHOOL_META.BALANCED.requiredPrestige).toBe(0);
        expect(SCHOOL_META.PHYSICAL.requiredLevel).toBe(0);
        expect(SCHOOL_META.PHYSICAL.requiredPrestige).toBe(0);
        expect(SCHOOL_META.MAGIC.requiredLevel).toBe(0);
        expect(SCHOOL_META.MAGIC.requiredPrestige).toBe(0);
        expect(SCHOOL_META.SUPPORT).toMatchObject({ requiredLevel: 50, requiredPrestige: 1 });
        expect(SCHOOL_META.CONTROL).toMatchObject({ requiredLevel: 70, requiredPrestige: 2 });
        expect(SCHOOL_META.ASSASSIN).toMatchObject({ requiredLevel: 90, requiredPrestige: 3 });
    });

    it('系数文案与后端契约逐字一致（弹窗/wiki 直接展示，勿改措辞）', () => {
        expect(SCHOOL_META.BALANCED.modsSummary).toBe('生命/双防+5%，暴击爆伤+5%');
        expect(SCHOOL_META.PHYSICAL.modsSummary).toBe('物攻+30%，魔攻-55%，物防+15%，魔防-30%，暴击+8%');
        expect(SCHOOL_META.MAGIC.modsSummary).toBe('魔攻+30%，物攻-55%，魔防+15%，物防-30%，爆伤+8%');
        expect(SCHOOL_META.SUPPORT.modsSummary).toBe('生命+15%/双防+20%，物攻-20%，暴击+3%');
        expect(SCHOOL_META.CONTROL.modsSummary).toBe('魔攻+10%/生命+5%，暴击爆伤+6%');
        expect(SCHOOL_META.ASSASSIN.modsSummary).toBe('物攻+40%，生命-10%/双防-40%，暴击+12% 爆伤+15%');
    });
});

describe('schoolBadgeMeta（流派徽章映射与容错）', () => {
    it('已知流派返回 icon+中文标签+色板类；未选（null/undefined/空串）与未知枚举 → null', () => {
        const meta = schoolBadgeMeta('PHYSICAL');
        expect(meta).toEqual({ icon: '⚔️', label: '物理流派', className: 'text-red-400 border-red-500/60' });
        expect(schoolBadgeMeta('CONTROL')?.label).toBe('控制流派');

        expect(schoolBadgeMeta(undefined)).toBeNull();
        expect(schoolBadgeMeta(null)).toBeNull();
        expect(schoolBadgeMeta('')).toBeNull();
        expect(schoolBadgeMeta('SSS')).toBeNull();
        expect(schoolBadgeMeta('balanced')).toBeNull(); // 大小写敏感，防止静默错配
    });

    it('每档徽章配色都是主题调色板类（text-* + border-*，不写死 hex）', () => {
        for (const key of Object.keys(SCHOOL_META)) {
            const meta = schoolBadgeMeta(key);
            expect(meta?.className).toMatch(/text-\S+/);
            expect(meta?.className).toMatch(/border-\S+/);
            expect(meta?.className).not.toMatch(/#[0-9a-fA-F]{3,8}/);
        }
    });
});

describe('schoolRequirementText / schoolUnlockHint（门槛文案）', () => {
    it('开局可选三流派 → 「开局可选」；有门槛 → 「需要 Lv.X 且转生≥Y」', () => {
        expect(schoolRequirementText('BALANCED')).toBe('开局可选');
        expect(schoolRequirementText('PHYSICAL')).toBe('开局可选');
        expect(schoolRequirementText('MAGIC')).toBe('开局可选');
        expect(schoolRequirementText('SUPPORT')).toBe('需要 Lv.50 且转生≥1');
        expect(schoolRequirementText('CONTROL')).toBe('需要 Lv.70 且转生≥2');
        expect(schoolRequirementText('ASSASSIN')).toBe('需要 Lv.90 且转生≥3');
        expect(schoolRequirementText('UNKNOWN')).toBe('');
    });

    it('已达标保留门槛说明；未达标追加当前进度（帮助规划）', () => {
        expect(schoolUnlockHint('SUPPORT', 60, 2)).toBe('需要 Lv.50 且转生≥1');
        expect(schoolUnlockHint('SUPPORT', 30, 0)).toBe('需要 Lv.50 且转生≥1（当前 Lv.30）');
        expect(schoolUnlockHint('CONTROL', 70, 1)).toBe('需要 Lv.70 且转生≥2（当前 Lv.70/1转）');
        expect(schoolUnlockHint('ASSASSIN', 90, 3)).toBe('需要 Lv.90 且转生≥3');
        expect(schoolUnlockHint('BALANCED', 1, 0)).toBe('开局可选');
        expect(schoolUnlockHint('UNKNOWN', 1, 0)).toBe('');
    });
});

describe('schoolUnlocked（门槛判定边界）', () => {
    it('等级与转数需同时达标：边界值达标注 / 差一档不达', () => {
        expect(schoolUnlocked('SUPPORT', 50, 1)).toBe(true);
        expect(schoolUnlocked('SUPPORT', 49, 1)).toBe(false);
        expect(schoolUnlocked('SUPPORT', 50, 0)).toBe(false);
        expect(schoolUnlocked('CONTROL', 70, 2)).toBe(true);
        expect(schoolUnlocked('CONTROL', 80, 1)).toBe(false);
        expect(schoolUnlocked('ASSASSIN', 90, 3)).toBe(true);
        expect(schoolUnlocked('ASSASSIN', 150, 2)).toBe(false);
        // 开局可选流派任意等级/转数均可选
        expect(schoolUnlocked('BALANCED', 1, 0)).toBe(true);
        expect(schoolUnlocked('MAGIC', 1, 0)).toBe(true);
        // 未知流派一律不可选
        expect(schoolUnlocked('UNKNOWN', 99, 9)).toBe(false);
    });
});

describe('school 规则镜像常量（与后端 GameBalance 同源）', () => {
    it('重选流派花费 5000 金（首选免费）', () => {
        expect(RESCHOOL_COST_GOLD).toBe(5000);
    });
});
