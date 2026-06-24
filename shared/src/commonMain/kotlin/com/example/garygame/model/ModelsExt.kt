package com.example.garygame.model

import kotlin.math.*
import kotlin.random.Random

// ============================================================
// V5: 魂环主动技能系统（双维度适配）
// ============================================================

enum class ActiveSkillType(val displayName: String) {
    SINGLE_STRIKE("单体打击"), AOE_STRIKE("范围打击"), MULTI_HIT("多段连击"),
    LIFE_STEAL("吸血"), SHIELD("护盾"), FURY("狂怒Buff"),
    POISON("毒素"), STUN("眩晕"), BLEED("流血"), AMPLIFY("增幅Buff"),
    CHAIN("连锁"), EXECUTE("斩杀"), SOUL_DRAIN("魂力汲取"),
    HEAL("治疗"), ICE("冰冻"), CURSE("诅咒"), DOMAIN("领域")
}

data class ActiveSkill(
    val name: String, val type: ActiveSkillType,
    val power: Int, val soulCost: Int, val cooldown: Int,
    val description: String, val tier: Int = 0  // 0=百年 1=千年 2=万年 3=十万年 4=百万年
)

object ActiveSkillPool {
    val all = listOf(
        // 强攻系-单体爆发 Tier 0~4
        ActiveSkill("重击", ActiveSkillType.SINGLE_STRIKE, 150, 5, 3, "凝聚魂力全力一击，造成150%伤害", 0),
        ActiveSkill("破甲击", ActiveSkillType.SINGLE_STRIKE, 130, 5, 3, "无视20%防御，造成130%伤害", 0),
        ActiveSkill("猛击", ActiveSkillType.SINGLE_STRIKE, 170, 7, 3, "蓄力猛击，造成170%伤害", 1),
        ActiveSkill("崩山击", ActiveSkillType.SINGLE_STRIKE, 180, 7, 3, "崩山裂地，造成180%伤害，无视30%防御", 1),
        ActiveSkill("破军", ActiveSkillType.SINGLE_STRIKE, 200, 9, 4, "势如破竹，造成200%伤害，对Boss额外+30%", 1),
        ActiveSkill("裂地斩", ActiveSkillType.AOE_STRIKE, 140, 8, 3, "裂地横扫，对全体敌人造成140%伤害", 1),
        ActiveSkill("雷霆一击", ActiveSkillType.SINGLE_STRIKE, 220, 10, 4, "雷霆万钧，造成220%伤害，20%麻痹1回合", 2),
        ActiveSkill("断岳", ActiveSkillType.SINGLE_STRIKE, 260, 12, 4, "断岳之威，消耗5%HP增伤50%，造成260%+50%伤害", 2),
        ActiveSkill("开天辟地", ActiveSkillType.SINGLE_STRIKE, 300, 15, 5, "开天之力，造成300%伤害，暴击率+20%", 3),
        ActiveSkill("大须弥锤", ActiveSkillType.SINGLE_STRIKE, 500, 20, 6, "昊天宗秘传！损失30%HP，造成500%毁灭伤害", 3),
        ActiveSkill("神魔破", ActiveSkillType.SINGLE_STRIKE, 400, 18, 5, "神魔之力，无视50%防御造成400%伤害", 3),
        ActiveSkill("终极毁灭", ActiveSkillType.SINGLE_STRIKE, 600, 30, 8, "修罗神终极一击！造成600%伤害，但冷却+2", 4),
        ActiveSkill("混沌之击", ActiveSkillType.SINGLE_STRIKE, 550, 25, 7, "混沌破灭，造成550%伤害，无视所有防御", 4),
        // 多段连击系 Tier 0~4
        ActiveSkill("二连击", ActiveSkillType.MULTI_HIT, 80, 4, 2, "快速二连击，每段80%伤害", 0),
        ActiveSkill("三连斩", ActiveSkillType.MULTI_HIT, 75, 6, 3, "三连快斩，每段75%伤害", 1),
        ActiveSkill("幽冥突刺", ActiveSkillType.MULTI_HIT, 70, 6, 3, "幽冥灵猫突刺！3段每段70%伤害", 1),
        ActiveSkill("乱披风", ActiveSkillType.MULTI_HIT, 65, 8, 4, "乱披风锤法！4段每段65%伤害，每击+5%", 2),
        ActiveSkill("幽冥百爪", ActiveSkillType.MULTI_HIT, 60, 9, 4, "幽冥百爪！4段每段60%伤害，暴击率+15%", 2),
        ActiveSkill("凤凰流星", ActiveSkillType.MULTI_HIT, 55, 10, 4, "凤凰流星雨！5段每段55%火伤", 2),
        ActiveSkill("虎爪裂空", ActiveSkillType.MULTI_HIT, 80, 11, 4, "白虎裂空爪！3段每段80%伤害，末段暴击+50%", 2),
        ActiveSkill("千手连击", ActiveSkillType.MULTI_HIT, 50, 12, 5, "千手连击！6段每段50%伤害，暴击触发追击", 3),
        ActiveSkill("风暴连斩", ActiveSkillType.MULTI_HIT, 90, 14, 5, "风暴连斩！4段每段90%伤害(需前4击命中)", 3),
        ActiveSkill("无限连击", ActiveSkillType.MULTI_HIT, 100, 18, 6, "无尽连击！4段每段100%伤害，击杀续1段", 4),
        // 法伤系-元素 Tier 0~4
        ActiveSkill("火球", ActiveSkillType.SINGLE_STRIKE, 140, 5, 3, "基础火球术，造成140%魔攻伤害", 0),
        ActiveSkill("冰锥", ActiveSkillType.ICE, 130, 5, 3, "冰锥穿刺，造成130%冰伤+20%冰冻1回合", 0),
        ActiveSkill("闪电链", ActiveSkillType.CHAIN, 120, 8, 4, "连锁闪电！对主目标120%后弹射2个80%", 1),
        ActiveSkill("凤凰啸天", ActiveSkillType.SINGLE_STRIKE, 180, 9, 3, "凤凰啸天击！180%火伤+灼烧3回合每回8%", 1),
        ActiveSkill("绝对零度", ActiveSkillType.ICE, 200, 12, 4, "极致之冰！200%冰伤+冰冻1回合(50%)", 2),
        ActiveSkill("地狱烈焰", ActiveSkillType.AOE_STRIKE, 160, 12, 4, "地狱之火！全体160%火伤+灼烧3回每回10%", 2),
        ActiveSkill("天雷降世", ActiveSkillType.SINGLE_STRIKE, 240, 14, 4, "天雷降世！240%雷伤+暴击率+20%", 3),
        ActiveSkill("天使圣光", ActiveSkillType.SINGLE_STRIKE, 280, 16, 5, "天使圣光！280%神圣伤害，对邪魔+50%", 3),
        ActiveSkill("海神之怒", ActiveSkillType.SINGLE_STRIKE, 350, 22, 6, "海神之怒！350%水伤，无视30%魔防", 4),
        ActiveSkill("审判之光", ActiveSkillType.AOE_STRIKE, 300, 25, 6, "神圣审判！全体300%神圣伤害，清除目标增益", 4),
        // 嗜血续航系 Tier 0~4
        ActiveSkill("生命汲取", ActiveSkillType.LIFE_STEAL, 130, 5, 3, "吸取生命！130%伤害回复20%伤害量", 0),
        ActiveSkill("噬血", ActiveSkillType.LIFE_STEAL, 150, 7, 3, "噬血攻击！150%伤害回复30%伤害量", 1),
        ActiveSkill("血气唤醒", ActiveSkillType.LIFE_STEAL, 200, 8, 3, "血气唤醒！损失10%HP造成200%伤害恢复50%", 1),
        ActiveSkill("血祭", ActiveSkillType.LIFE_STEAL, 180, 9, 4, "血之献祭！180%伤害回复40%伤害量", 2),
        ActiveSkill("暗影噬魂", ActiveSkillType.LIFE_STEAL, 200, 11, 4, "暗影噬魂！200%伤害回复50%+暴击+10%", 2),
        ActiveSkill("生命献祭", ActiveSkillType.HEAL, 0, 8, 4, "生命献祭！回复30%HP，下回合伤害+60%", 2),
        ActiveSkill("血月", ActiveSkillType.LIFE_STEAL, 250, 14, 5, "血月斩！250%伤害回复60%伤害量", 3),
        ActiveSkill("不死之血", ActiveSkillType.LIFE_STEAL, 200, 15, 5, "不死之血！若击杀目标回复30%最大HP", 3),
        ActiveSkill("嗜血神术", ActiveSkillType.LIFE_STEAL, 300, 22, 6, "嗜血神术！300%伤害回复100%(过量转护盾)", 4),
        // 护盾防御系 Tier 0~4
        ActiveSkill("魂力护体", ActiveSkillType.SHIELD, 10, 5, 3, "魂力护体！生成护盾吸收10%HP伤害2回合", 0),
        ActiveSkill("铁壁", ActiveSkillType.SHIELD, 20, 5, 2, "铁壁防御！2回合减伤20%", 0),
        ActiveSkill("蓝银囚笼", ActiveSkillType.SHIELD, 15, 7, 3, "蓝银囚笼！护盾15%HP+禁锢敌人1回合", 1),
        ActiveSkill("天使守护", ActiveSkillType.SHIELD, 20, 8, 3, "天使守护！护盾20%HP，存在时反伤15%", 1),
        ActiveSkill("冰铠", ActiveSkillType.SHIELD, 18, 9, 4, "寒冰铠甲！护盾18%HP，近战攻击者30%冰冻", 2),
        ActiveSkill("海神庇护", ActiveSkillType.SHIELD, 35, 12, 4, "海神庇护！2回合减伤35%+每回恢复5%HP", 2),
        ActiveSkill("金刚不坏", ActiveSkillType.SHIELD, 50, 15, 5, "金刚不坏！3回合减伤50%但攻击-30%", 3),
        ActiveSkill("绝对防御", ActiveSkillType.SHIELD, 100, 18, 6, "绝对防御！1回合免疫所有伤害但无法行动", 3),
        ActiveSkill("神圣壁垒", ActiveSkillType.SHIELD, 40, 22, 6, "神圣壁垒！护盾40%HP+破盾时50%反伤", 4),
        // 治疗回复系 Tier 0~4
        ActiveSkill("治愈", ActiveSkillType.HEAL, 15, 4, 2, "基础治愈术，回复15%HP", 0),
        ActiveSkill("生命绽放", ActiveSkillType.HEAL, 12, 5, 3, "生命绽放！回复12%HP+后续2回每回6%", 0),
        ActiveSkill("七宝回复", ActiveSkillType.HEAL, 20, 6, 3, "七宝转出！回复20%HP", 1),
        ActiveSkill("生命之泉", ActiveSkillType.HEAL, 15, 7, 3, "生命之泉！回复15%HP并清除1个负面", 1),
        ActiveSkill("九宝护体", ActiveSkillType.HEAL, 25, 9, 4, "九宝护体！回复25%HP+护盾10%HP", 2),
        ActiveSkill("群体治愈", ActiveSkillType.HEAL, 18, 8, 3, "群体治愈！回复18%HP", 2),
        ActiveSkill("生命之歌", ActiveSkillType.HEAL, 10, 10, 4, "生命之歌！3回合持续恢复每回10%HP", 2),
        ActiveSkill("涅槃重生", ActiveSkillType.HEAL, 50, 15, 5, "凤凰涅槃！HP<30%回复50%HP否则回复20%", 3),
        ActiveSkill("神愈", ActiveSkillType.HEAL, 50, 18, 6, "神之治愈！回复50%HP清除所有负面", 4),
        // 增益Buff系 Tier 0~4
        ActiveSkill("力量强化", ActiveSkillType.AMPLIFY, 20, 5, 3, "2回合攻击+20%", 0),
        ActiveSkill("铁骨", ActiveSkillType.AMPLIFY, 25, 5, 3, "2回合双防+25%", 0),
        ActiveSkill("七宝增幅", ActiveSkillType.AMPLIFY, 30, 7, 3, "2回合攻击+30%、防御+20%", 1),
        ActiveSkill("战斗狂热", ActiveSkillType.AMPLIFY, 25, 7, 3, "3回合攻击+25%、暴击+10%", 1),
        ActiveSkill("武魂觉醒", ActiveSkillType.AMPLIFY, 20, 9, 4, "3回合全属性+20%", 2),
        ActiveSkill("嗜血狂化", ActiveSkillType.FURY, 50, 10, 4, "2回合攻击+50%但防御-30%", 2),
        ActiveSkill("海神祝福", ActiveSkillType.AMPLIFY, 30, 12, 4, "3回合全属性+30%+每回恢复3%HP", 2),
        ActiveSkill("武魂真身", ActiveSkillType.AMPLIFY, 60, 16, 5, "3回合攻击+60%暴击+30%暴伤+50%", 3),
        ActiveSkill("天使降临", ActiveSkillType.AMPLIFY, 50, 18, 5, "3回合全属性+50%且免疫控制", 3),
        ActiveSkill("神王降临", ActiveSkillType.AMPLIFY, 80, 28, 7, "5回合全属性+80%但冷却+3", 4),
        // 异常DOT系 Tier 0~4
        ActiveSkill("毒刺", ActiveSkillType.POISON, 100, 4, 2, "淬毒一击！100%伤害+中毒3回每回5%HP", 0),
        ActiveSkill("灼烧", ActiveSkillType.POISON, 110, 5, 2, "火焰灼烧！110%火伤+灼烧2回每回6%", 0),
        ActiveSkill("撕裂", ActiveSkillType.BLEED, 120, 6, 3, "撕裂伤口！120%伤害+流血3回每回8%", 1),
        ActiveSkill("碧磷毒雾", ActiveSkillType.POISON, 90, 8, 3, "碧磷蛇毒！全体90%毒伤+中毒3回每回8%", 1),
        ActiveSkill("腐蚀之种", ActiveSkillType.CURSE, 100, 8, 3, "腐蚀之种！100%伤害+腐蚀3回每回-5%防御", 2),
        ActiveSkill("剧毒新星", ActiveSkillType.POISON, 140, 10, 4, "剧毒爆发！140%毒伤，中毒目标引爆+200%", 2),
        ActiveSkill("暗影诅咒", ActiveSkillType.CURSE, 130, 9, 3, "暗影诅咒！130%伤害+诅咒2回受伤害+20%", 2),
        ActiveSkill("万毒噬体", ActiveSkillType.POISON, 100, 14, 5, "万毒噬体！中毒5回每回10%HP且受治疗-50%", 3),
        ActiveSkill("死神低语", ActiveSkillType.CURSE, 100, 15, 5, "死神标记！标记3回结束造成期间总伤30%", 3),
        ActiveSkill("湮灭", ActiveSkillType.POISON, 150, 22, 6, "湮灭之力！每回15%HP持续3回无视防御", 4),
        // 控制系 Tier 0~4
        ActiveSkill("震荡", ActiveSkillType.STUN, 80, 5, 3, "震荡冲击！80%伤害+20%眩晕1回合", 0),
        ActiveSkill("藤蔓缠绕", ActiveSkillType.STUN, 60, 5, 3, "藤蔓缠绕！60%伤害+40%定身1回合", 0),
        ActiveSkill("精神冲击", ActiveSkillType.STUN, 100, 7, 3, "精神冲击！100%伤害+30%眩晕1回合", 1),
        ActiveSkill("冰封", ActiveSkillType.ICE, 120, 8, 3, "寒冰封冻！120%冰伤+冰冻1回合", 1),
        ActiveSkill("威压", ActiveSkillType.CURSE, 0, 7, 4, "龙族威压！降低目标攻击30%+防御20%持续2回", 1),
        ActiveSkill("灵魂震荡", ActiveSkillType.STUN, 150, 10, 4, "灵魂震荡！眩晕50%否则伤害+50%", 2),
        ActiveSkill("蓝银霸皇枪", ActiveSkillType.STUN, 180, 12, 4, "蓝银霸皇枪！180%伤害+定身2回(30%)", 2),
        ActiveSkill("精神混乱", ActiveSkillType.STUN, 60, 12, 5, "精神混乱！60%伤害+混乱1回合", 3),
        ActiveSkill("时空锁定", ActiveSkillType.STUN, 100, 18, 6, "时空锁定！定身2回(80%)不可驱散", 4),
        // 特殊机制系 Tier 0~4
        ActiveSkill("魂力汲取", ActiveSkillType.SOUL_DRAIN, 100, 0, 3, "魂力汲取！100%伤害+偷取10魂力", 0),
        ActiveSkill("反击姿态", ActiveSkillType.SHIELD, 60, 7, 4, "反击姿态！2回合受到攻击时反击60%伤害", 1),
        ActiveSkill("蓄力", ActiveSkillType.AMPLIFY, 100, 6, 3, "蓄力待发！本回合不攻击，下回合伤害+100%", 1),
        ActiveSkill("标记打击", ActiveSkillType.CURSE, 20, 5, 3, "标记目标！3回合内攻击标记目标额外+20%", 1),
        ActiveSkill("暗器百解", ActiveSkillType.POISON, 130, 8, 3, "唐门暗器！130%伤害+30%随机异常", 2),
        ActiveSkill("八蛛矛", ActiveSkillType.LIFE_STEAL, 150, 9, 4, "八蛛矛！150%伤害+击杀永久+1攻击", 2),
        ActiveSkill("伤害转移", ActiveSkillType.SHIELD, 40, 10, 4, "伤害转移！1回合内40%受伤转给敌人", 2),
        ActiveSkill("杀戮领域", ActiveSkillType.DOMAIN, 20, 14, 5, "杀戮领域！3回合攻击+20%击杀续1回", 3),
        ActiveSkill("蓝银领域", ActiveSkillType.DOMAIN, 5, 14, 5, "蓝银领域！3回合每回回5%HP+缠绕攻击者", 3),
        ActiveSkill("海神领域", ActiveSkillType.DOMAIN, 25, 18, 5, "海神领域！3回合全属性+25%+免疫控制", 4),
        ActiveSkill("修罗领域", ActiveSkillType.DOMAIN, 40, 22, 6, "修罗领域！3回合攻击+40%击杀回20%HP", 4),
        ActiveSkill("吞噬进化", ActiveSkillType.LIFE_STEAL, 200, 20, 6, "八蛛矛吞噬！击杀永久+5HP(每场最多5次)", 4)
    )

    /** V5: 基于合并层级(0-24)筛选技能，按年份tier过滤 */
    fun getSkillsForTier(combinedTier: Int): List<ActiveSkill> {
        val yearTier = combinedTier / RingQuality.size  // 0-4
        return all.filter { it.tier <= yearTier }
    }

    fun randomSkill(combinedTier: Int): ActiveSkill {
        val pool = getSkillsForTier(combinedTier)
        return if (pool.isNotEmpty()) pool.random() else all.first()
    }
}

// ============================================================
// V5: 魂骨被动技能系统（双维度适配）
// ============================================================

enum class PassiveSkillType(val displayName: String) {
    STAT_BOOST("属性增幅"), DMG_REDUCE("减伤"), THORNS("反伤"),
    LIFESTEAL("吸血"), DODGE("闪避"), COUNTER("反击"),
    EXECUTE("斩杀"), REGEN("再生"), BARRIER("护盾"),
    CRIT_BOOST("暴击"), ARMOR_BREAK("破甲"), REVIVE("复活"),
    RAGE("狂怒"), CHAIN_HIT("连击"), SOUL_STEAL("魂力窃取"), IMMUNE("免疫")
}

data class PassiveSkill(
    val name: String, val type: PassiveSkillType,
    val values: List<Int>,  // V5: 25值 [百年劣等...百万年完美]
    val description: String
) {
    /** V5: 基于合并层级(0-24)取值，有效品质插值（用于魂骨） */
    fun getValue(combinedTier: Int): Int {
        val idx = combinedTier.coerceIn(0, values.size - 1)
        val base = values[idx]
        val eq = RingYear.effectiveQuality(combinedTier)
        val curveMult = when { eq < 0.6 -> 0.6; eq < 1.2 -> 0.8; eq < 1.8 -> 1.0; eq < 2.6 -> 1.3; eq < 3.4 -> 1.6; else -> 2.0 }
        return (base * curveMult).toInt().coerceAtLeast(1)
    }
    /** 魂核专用取值：直接按稀有度取原生值，不经过combinedTier曲线（魂核values数组只有6个元素0-5稀有度） */
    fun getSoulCoreValue(rarityOrdinal: Int): Int {
        val idx = rarityOrdinal.coerceIn(0, values.size - 1)
        return values[idx]
    }
}

object PassiveSkillPool {
    // 头部魂骨 — 精神/感知（V5: 25值 = 5年×5品质阶梯）
    val head = listOf(
        PassiveSkill("精神探测", PassiveSkillType.CRIT_BOOST, listOf(2,2,3,4,5, 3,4,5,7,10, 5,6,8,11,16, 7,9,13,18,25, 10,14,20,28,38), "暴击率+"),
        PassiveSkill("精神凝聚", PassiveSkillType.SOUL_STEAL, listOf(1,1,1,1,2, 1,1,1,2,3, 1,1,2,2,4, 1,2,2,3,5, 2,2,3,4,6), "每回合额外恢复魂力+"),
        PassiveSkill("预警", PassiveSkillType.DODGE, listOf(3,4,5,8,12, 4,5,7,11,17, 5,7,10,15,22, 7,10,16,22,32, 10,15,22,30,45), "闪避率+"),
        PassiveSkill("灵魂锁链", PassiveSkillType.BARRIER, listOf(6,8,12,18,28, 8,11,16,24,36, 11,15,22,32,48, 16,22,32,45,65, 22,32,45,65,95), "战斗开始获得护盾=最大HP的%"),
        PassiveSkill("洞察", PassiveSkillType.ARMOR_BREAK, listOf(4,5,7,10,18, 5,7,10,15,25, 7,10,15,22,35, 10,15,22,32,50, 15,22,32,48,72), "无视目标%防御"),
    )
    // 左臂骨 — 防御/反击（V5: 25值）
    val leftArm = listOf(
        PassiveSkill("铁壁", PassiveSkillType.STAT_BOOST, listOf(4,6,8,12,18, 6,8,12,16,24, 8,12,16,22,32, 12,16,24,32,45, 16,24,32,45,65), "物防+%"),
        PassiveSkill("魔抗", PassiveSkillType.STAT_BOOST, listOf(4,6,8,12,18, 6,8,12,16,24, 8,12,16,22,32, 12,16,24,32,45, 16,24,32,45,65), "魔防+%"),
        PassiveSkill("荆棘之甲", PassiveSkillType.THORNS, listOf(4,5,6,8,15, 5,7,8,11,20, 7,8,11,16,28, 8,12,16,22,40, 12,16,24,32,55), "反弹%伤害给攻击者"),
        PassiveSkill("坚定意志", PassiveSkillType.IMMUNE, listOf(1,1,1,1,1, 1,1,1,1,2, 1,1,1,2,2, 1,1,2,2,3, 1,2,2,3,5), "控制效果持续时间-%"),
        PassiveSkill("格挡", PassiveSkillType.DMG_REDUCE, listOf(4,5,8,12,22, 5,7,12,16,30, 8,12,18,24,40, 12,18,28,38,60, 18,28,38,55,85), "%几率格挡50%伤害"),
        PassiveSkill("不屈", PassiveSkillType.DMG_REDUCE, listOf(7,10,14,20,32, 10,14,20,28,42, 14,20,28,38,58, 20,28,38,55,80, 28,42,55,80,115), "HP<30%时减伤%"),
        PassiveSkill("复仇", PassiveSkillType.COUNTER, listOf(7,10,14,20,32, 10,14,20,28,42, 14,20,28,38,58, 20,28,38,55,80, 28,42,55,80,115), "受到攻击后%几率反击80%伤害"),
        PassiveSkill("伤害分摊", PassiveSkillType.DMG_REDUCE, listOf(21,30,28,25,22, 30,28,25,22,20, 28,25,22,20,18, 25,22,20,18,16, 22,20,18,16,14), "单次受伤不超过最大HP%"),
        PassiveSkill("绝对壁垒", PassiveSkillType.BARRIER, listOf(28,35,45,55,70, 35,45,55,65,85, 45,55,65,80,100, 55,65,80,100,130, 65,85,100,130,170), "首击减伤%+免疫眩晕"),
    )
    // 右臂骨 — 攻击/破防（V5: 25值）
    val rightArm = listOf(
        PassiveSkill("力量增幅", PassiveSkillType.STAT_BOOST, listOf(4,5,7,10,16, 5,7,10,14,22, 7,10,14,20,30, 10,14,22,30,45, 16,22,30,45,65), "物攻+%"),
        PassiveSkill("破甲", PassiveSkillType.ARMOR_BREAK, listOf(2,3,4,6,10, 3,4,6,8,14, 4,6,8,12,20, 6,8,14,20,30, 10,14,20,30,45), "无视%防御"),
        PassiveSkill("嗜血", PassiveSkillType.LIFESTEAL, listOf(1,2,3,4,8, 2,3,4,6,12, 3,4,6,8,16, 4,6,8,12,22, 8,12,16,22,35), "攻击回复%伤害量HP"),
        PassiveSkill("会心一击", PassiveSkillType.CRIT_BOOST, listOf(2,3,4,6,10, 3,4,6,8,14, 4,6,8,12,20, 6,8,14,20,30, 10,14,20,30,45), "暴击率+%"),
        PassiveSkill("致命节奏", PassiveSkillType.CHAIN_HIT, listOf(2,3,4,6,10, 3,4,6,8,14, 4,6,8,12,20, 6,8,14,20,30, 10,14,20,30,45), "连续攻击同目标每击+%(最高25%)"),
        PassiveSkill("毁灭打击", PassiveSkillType.CRIT_BOOST, listOf(7,10,14,20,32, 10,14,20,28,42, 14,20,28,38,58, 20,28,38,55,80, 28,42,55,80,115), "暴击伤害+%"),
        PassiveSkill("处决者", PassiveSkillType.EXECUTE, listOf(11,15,22,28,45, 15,22,28,38,60, 22,28,38,55,80, 28,38,55,80,115, 45,60,80,115,170), "对HP<30%目标伤害+%"),
        PassiveSkill("狂热", PassiveSkillType.RAGE, listOf(2,3,3,4,5, 3,3,4,5,6, 3,4,5,6,7, 4,5,6,7,9, 5,6,7,9,12), "击杀+%攻击(可叠5层，战斗重置)"),
        PassiveSkill("血之狂暴", PassiveSkillType.RAGE, listOf(1,2,3,4,8, 2,3,4,6,11, 3,4,6,8,15, 4,6,8,12,22, 8,11,15,22,35), "每损失10%HP攻击+%(上限45%)"),
        PassiveSkill("毁灭之力", PassiveSkillType.CRIT_BOOST, listOf(14,20,28,38,55, 20,28,38,55,75, 28,38,55,75,100, 38,55,75,100,140, 55,75,100,140,200), "攻击+%且暴击时额外+50%伤害"),
    )
    // 躯干骨 — 生命/恢复（V5: 25值）
    val torso = listOf(
        PassiveSkill("生命源泉", PassiveSkillType.STAT_BOOST, listOf(4,5,7,10,16, 5,7,10,14,22, 7,10,14,20,30, 10,14,22,30,45, 16,22,30,45,65), "最大HP+%"),
        PassiveSkill("快速再生", PassiveSkillType.REGEN, listOf(21,30,42,60,90, 30,42,60,80,120, 42,60,80,110,160, 60,80,110,150,220, 90,120,160,220,320), "每秒HP恢复+%"),
        PassiveSkill("血肉之躯", PassiveSkillType.LIFESTEAL, listOf(7,10,14,20,32, 10,14,20,28,42, 14,20,28,38,58, 20,28,38,55,80, 28,42,55,80,115), "受到的治疗效果+%"),
        PassiveSkill("护体罡气", PassiveSkillType.BARRIER, listOf(4,5,7,10,16, 5,7,10,14,22, 7,10,14,20,30, 10,14,22,30,45, 16,22,30,45,65), "每5回自动获护盾=%HP"),
        PassiveSkill("活力", PassiveSkillType.REGEN, listOf(1,1,2,3,5, 1,2,3,4,7, 2,3,4,6,10, 3,4,6,9,14, 5,7,10,14,22), "每回合结束恢复%最大HP"),
        PassiveSkill("痛苦转化", PassiveSkillType.LIFESTEAL, listOf(2,3,4,6,10, 3,4,6,8,14, 4,6,8,12,20, 6,8,14,20,30, 10,14,20,30,45), "受伤时恢复%伤害量HP(每回1次)"),
        PassiveSkill("生命链接", PassiveSkillType.REGEN, listOf(1,1,3,4,8, 1,3,4,6,11, 3,4,6,8,15, 4,6,8,12,22, 8,11,15,22,35), "使用技能时恢复%HP"),
        PassiveSkill("不死之身", PassiveSkillType.REVIVE, listOf(1,1,1,1,1, 1,1,1,1,1, 1,1,1,1,1, 1,1,1,1,2, 1,1,1,2,3), "致命伤保留1HP，触发%次"),
        PassiveSkill("生命赞歌", PassiveSkillType.REGEN, listOf(14,20,30,45,70, 20,30,45,60,96, 30,45,60,85,130, 45,60,85,120,180, 70,96,130,180,260), "最大HP+%每秒恢复+%"),
    )
    // 左腿骨 — 敏捷/闪避（V5: 25值）
    val leftLeg = listOf(
        PassiveSkill("迅捷", PassiveSkillType.DODGE, listOf(2,3,4,6,10, 3,4,6,8,14, 4,6,8,12,20, 6,8,14,20,30, 10,14,20,30,45), "闪避率+%"),
        PassiveSkill("轻灵", PassiveSkillType.DODGE, listOf(4,5,7,10,16, 5,7,10,14,22, 7,10,14,20,30, 10,14,22,30,45, 16,22,30,45,65), "%几率完全闪避"),
        PassiveSkill("灵巧", PassiveSkillType.CRIT_BOOST, listOf(2,3,4,6,10, 3,4,6,8,14, 4,6,8,12,20, 6,8,14,20,30, 10,14,20,30,45), "暴击率+%"),
        PassiveSkill("残影", PassiveSkillType.COUNTER, listOf(11,15,22,28,42, 15,22,28,38,54, 22,28,38,50,72, 28,38,50,68,100, 42,54,72,100,145), "闪避后下次攻击必暴击且伤害+%"),
        PassiveSkill("幻影步", PassiveSkillType.DODGE, listOf(6,8,12,16,24, 8,12,16,22,30, 12,16,22,30,42, 16,22,30,42,60, 24,30,42,60,85), "%几率完全闪避攻击"),
        PassiveSkill("如影随形", PassiveSkillType.CHAIN_HIT, listOf(14,20,30,40,55, 20,30,40,55,66, 30,40,55,72,100, 40,55,72,100,140, 55,66,100,140,200), "暴击后%几率再攻击(50%伤害)"),
        PassiveSkill("舞空术", PassiveSkillType.DODGE, listOf(6,8,12,16,24, 8,12,16,22,30, 12,16,22,30,42, 16,22,30,42,60, 24,30,42,60,85), "闪避+%且闪避后回%HP"),
        PassiveSkill("神行太保", PassiveSkillType.DODGE, listOf(8,12,16,22,32, 12,16,22,30,42, 16,22,30,40,58, 22,30,40,55,80, 32,42,58,80,115), "闪避+%闪避后必暴击且暴伤+%"),
    )
    // 右腿骨 — 爆发/终结（V5: 25值）
    val rightLeg = listOf(
        PassiveSkill("强攻", PassiveSkillType.STAT_BOOST, listOf(3,4,6,8,14, 4,6,8,12,18, 6,8,12,16,26, 8,12,18,24,38, 14,18,26,38,55), "物攻+%"),
        PassiveSkill("弱点打击", PassiveSkillType.CRIT_BOOST, listOf(6,8,12,16,26, 8,12,16,22,34, 12,16,22,30,46, 16,22,30,42,62, 26,34,46,62,90), "%几率造成1.5倍伤害"),
        PassiveSkill("蓄势待发", PassiveSkillType.RAGE, listOf(11,15,22,28,48, 15,22,28,38,66, 22,28,38,50,88, 28,38,50,68,115, 48,66,88,115,170), "战斗开始首击伤害+%"),
        PassiveSkill("横扫", PassiveSkillType.CHAIN_HIT, listOf(7,10,14,20,32, 10,14,20,28,42, 14,20,28,38,58, 20,28,38,55,80, 28,42,55,80,115), "普攻%几率50%溅射"),
        PassiveSkill("狂暴", PassiveSkillType.RAGE, listOf(14,20,28,38,58, 20,28,38,50,72, 28,38,50,68,98, 38,50,68,90,135, 58,72,98,135,200), "击杀后下回攻击+%"),
        PassiveSkill("终结", PassiveSkillType.EXECUTE, listOf(2,3,4,6,10, 3,4,6,8,14, 4,6,8,12,20, 6,8,14,20,30, 10,14,20,30,45), "HP<30%额外受%最大HP伤害"),
        PassiveSkill("蓄力爆发", PassiveSkillType.RAGE, listOf(4,5,7,10,16, 5,7,10,14,22, 7,10,14,20,30, 10,14,22,30,45, 16,22,30,45,65), "连续普攻每次加%技能伤(叠4次)"),
        PassiveSkill("毁灭践踏", PassiveSkillType.CHAIN_HIT, listOf(4,5,4,4,4, 5,4,4,4,4, 4,4,4,4,4, 4,4,4,4,4, 4,4,4,4,4), "每%回自动触发90%范围伤害"),
        PassiveSkill("战神降临", PassiveSkillType.RAGE, listOf(1,2,3,3,5, 2,3,3,4,6, 3,3,4,5,8, 3,4,5,6,10, 5,6,8,10,15), "每损10%HP暴击+%暴伤+%(限15%/+50%)"),
    )

    fun getPoolForBone(boneTypeOrdinal: Int): List<PassiveSkill> = when (boneTypeOrdinal) {
        0 -> head; 1 -> leftArm; 2 -> rightArm; 3 -> torso; 4 -> leftLeg; 5 -> rightLeg; else -> head
    }

    /** V5: 基于合并层级(0-24)随机被动，质量越高池越深 */
    fun randomPassive(boneTypeOrdinal: Int, combinedTier: Int): PassiveSkill {
        val pool = getPoolForBone(boneTypeOrdinal)
        val eq = RingYear.effectiveQuality(combinedTier)
        val minValueIdx = (eq * 5.0).toInt().coerceIn(0, 24)
        val filtered = pool.filter { it.values.size > minValueIdx }
        return (filtered.ifEmpty { pool }).random()
    }

    val all: List<PassiveSkill> get() = head + leftArm + rightArm + torso + leftLeg + rightLeg
}

// ============================================================
// V5: 魂核系统
// ============================================================

// V5: 魂核槽位类型
enum class SoulCoreSlotType(val displayName: String, val icon: String, val minPrestige: Int) {
    ATTACK("攻击槽", "⚔️", 0),
    DEFENSE("防御槽", "🛡️", 0),
    UTILITY("辅助槽", "💠", 3);
    companion object {
        val unlockedSlots: List<SoulCoreSlotType> get() = entries
        fun isUnlocked(slot: SoulCoreSlotType, prestigeCount: Int): Boolean =
            prestigeCount >= slot.minPrestige
    }
}

// V5: 魂核分类（决定可装备槽位）
enum class SoulCoreCategory(val displayName: String, val compatibleSlots: List<SoulCoreSlotType>) {
    POWER("力量", listOf(SoulCoreSlotType.ATTACK)),
    CRIT("暴击", listOf(SoulCoreSlotType.ATTACK)),
    DESTRUCTION("毁灭", listOf(SoulCoreSlotType.ATTACK)),
    GUARD("守护", listOf(SoulCoreSlotType.ATTACK, SoulCoreSlotType.DEFENSE)),
    THORNS("荆棘", listOf(SoulCoreSlotType.ATTACK, SoulCoreSlotType.DEFENSE)),
    DRAGON("龙鳞", listOf(SoulCoreSlotType.ATTACK, SoulCoreSlotType.DEFENSE)),
    VAMP("嗜血", listOf(SoulCoreSlotType.ATTACK, SoulCoreSlotType.UTILITY)),
    REGEN("再生", listOf(SoulCoreSlotType.ATTACK, SoulCoreSlotType.UTILITY)),
    DODGE("疾风", listOf(SoulCoreSlotType.ATTACK, SoulCoreSlotType.UTILITY)),
    IMMORTAL("不朽", listOf(SoulCoreSlotType.UTILITY)),
    ORIGIN("起源", SoulCoreSlotType.entries),
    VOID("虚空", SoulCoreSlotType.entries),
    UNIVERSAL("通用", SoulCoreSlotType.entries);
}

// V5: 魂核升级配置
object SoulCoreLevelData {
    const val MAX_LEVEL = 5
    /** 升级到指定等级所需同名魂核数量 */
    fun requiredCopies(level: Int): Int = when (level) {
        0 -> 1  // 0→1
        1 -> 2  // 1→2
        2 -> 3  // 2→3
        3 -> 5  // 3→4
        4 -> 8  // 4→5
        else -> Int.MAX_VALUE
    }
    /** 升级后效果倍率 */
    fun effectMultiplier(level: Int): Double = 1.0 + level * 0.15
}

data class SoulCoreInstance(
    val name: String,
    val rarityOrdinal: Int,
    val passiveSkill: PassiveSkill,
    val value: Int,
    val level: Int = 0,
    val slotType: SoulCoreSlotType
) {
    /** 当前等级下技能实际值 */
    val effectiveValue: Int get() = (value * SoulCoreLevelData.effectMultiplier(level)).toInt()
    /** 所属分类 */
    val category: SoulCoreCategory get() = name.toSoulCoreCategory()
    /** 是否与另一个魂核同名（用于升级消耗判断） */
    fun isSameName(other: DroppedSoulCore): Boolean = name == other.name
}

/** 魂核名称 → 分类映射（公共扩展函数，消除SoulCoreInstance和DroppedSoulCore的重复） */
fun String.toSoulCoreCategory(): SoulCoreCategory = when (this) {
    "生命结晶" -> SoulCoreCategory.UNIVERSAL
    "力量之核" -> SoulCoreCategory.POWER
    "暴击之核" -> SoulCoreCategory.CRIT
    "毁灭之核" -> SoulCoreCategory.DESTRUCTION
    "守护之核" -> SoulCoreCategory.GUARD
    "荆棘之核" -> SoulCoreCategory.THORNS
    "龙鳞之核" -> SoulCoreCategory.DRAGON
    "嗜血之核" -> SoulCoreCategory.VAMP
    "再生之核" -> SoulCoreCategory.REGEN
    "疾风之核" -> SoulCoreCategory.DODGE
    "不朽之核" -> SoulCoreCategory.IMMORTAL
    "起源之核" -> SoulCoreCategory.ORIGIN
    "虚空之核" -> SoulCoreCategory.VOID
    else -> SoulCoreCategory.UNIVERSAL
}

data class SoulCoreDef(
    val name: String, val description: String,
    val rarityOrdinal: Int, val passiveSkill: PassiveSkill,
    val bossCoinCost: Int, val category: SoulCoreCategory
)

object SoulCorePool {
    val all = listOf(
        SoulCoreDef("生命结晶", "提升最大生命值", 0, PassiveSkill("生命", PassiveSkillType.STAT_BOOST, listOf(3,5,8,12,18,25), "HP+%"), 50, SoulCoreCategory.UNIVERSAL),
        SoulCoreDef("力量之核", "提升物理攻击", 0, PassiveSkill("力量", PassiveSkillType.STAT_BOOST, listOf(3,5,8,12,18,25), "物攻+%"), 50, SoulCoreCategory.POWER),
        SoulCoreDef("守护之核", "提升双防", 1, PassiveSkill("守护", PassiveSkillType.STAT_BOOST, listOf(4,7,10,15,22,30), "双防+%"), 100, SoulCoreCategory.GUARD),
        SoulCoreDef("暴击之核", "提升暴击率", 1, PassiveSkill("暴击", PassiveSkillType.CRIT_BOOST, listOf(2,4,6,8,12,16), "暴击率+%"), 100, SoulCoreCategory.CRIT),
        SoulCoreDef("嗜血之核", "攻击回复生命", 2, PassiveSkill("嗜血", PassiveSkillType.LIFESTEAL, listOf(1,2,3,5,8,12), "攻击回复%伤害量HP"), 200, SoulCoreCategory.VAMP),
        SoulCoreDef("荆棘之核", "反弹伤害", 2, PassiveSkill("荆棘", PassiveSkillType.THORNS, listOf(3,5,8,12,18,25), "反弹%伤害"), 200, SoulCoreCategory.THORNS),
        SoulCoreDef("再生之核", "提升每秒恢复", 2, PassiveSkill("再生", PassiveSkillType.REGEN, listOf(15,25,40,60,80,120), "每秒恢复+%"), 200, SoulCoreCategory.REGEN),
        SoulCoreDef("疾风之核", "提升闪避率", 3, PassiveSkill("疾风", PassiveSkillType.DODGE, listOf(3,5,8,12,16,22), "闪避+%"), 400, SoulCoreCategory.DODGE),
        SoulCoreDef("毁灭之核", "提升爆伤", 3, PassiveSkill("毁灭", PassiveSkillType.CRIT_BOOST, listOf(8,15,22,30,45,60), "暴伤+%"), 400, SoulCoreCategory.DESTRUCTION),
        SoulCoreDef("龙鳞之核", "减伤效果", 4, PassiveSkill("龙鳞", PassiveSkillType.DMG_REDUCE, listOf(3,5,8,12,16,22), "减伤+%"), 800, SoulCoreCategory.DRAGON),
        SoulCoreDef("不朽之核", "致命保命", 4, PassiveSkill("不朽", PassiveSkillType.REVIVE, listOf(1,1,1,1,2,2), "致命伤保留1HP触发%次"), 1000, SoulCoreCategory.IMMORTAL),
        SoulCoreDef("起源之核", "全属性提升", 5, PassiveSkill("起源", PassiveSkillType.STAT_BOOST, listOf(5,8,10,15,20,30), "全属性+%"), 2000, SoulCoreCategory.ORIGIN),
        SoulCoreDef("虚空之核", "技能冷却缩减", 5, PassiveSkill("虚空", PassiveSkillType.IMMUNE, listOf(1,1,1,2,2,3), "技能冷却-%且魂力-20%"), 2500, SoulCoreCategory.VOID),
    )
    fun randomSoulCore(maxRarity: Int): SoulCoreDef {
        val pool = all.filter { it.rarityOrdinal <= maxRarity }
        return pool.random()
    }
}

// ============================================================
// V5: 关卡/多目标战斗类型
// ============================================================

enum class StageType { NORMAL, ELITE, BOSS }

data class StageInfo(
    val stage: Int, val type: StageType, val monsterCount: Int, val bossName: String = ""
)

object StageData {
    const val STAGES_PER_MAP = 15
    fun getStageType(stage: Int): StageType = when {
        stage % 5 == 0 -> StageType.BOSS; stage % 3 == 0 -> StageType.ELITE; else -> StageType.NORMAL
    }
    fun getMonsterCount(stage: Int): Int = when (getStageType(stage)) {
        StageType.NORMAL -> 1; StageType.ELITE -> (1..2).random()
        StageType.BOSS -> if (stage >= 5 && Random.nextFloat() < 0.4f) 2 else 1
    }
    fun stageScale(stage: Int): Double = 1.0 + stage * 0.12
    fun bossNames(mapId: Int): List<String> {
        val names = listOf(
            listOf("野猪王", "巨蟒", "千年魂兽"), listOf("凶狼王", "暗影豹", "千年地穴魔蛛"),
            listOf("凤尾鸡冠蛇", "泰坦巨猿幼体", "万年魂兽"), listOf("暗金恐爪熊", "地穴魔蛛", "万年蚁皇"),
            listOf("冰碧帝皇蝎", "雪魔怪", "十万年魂兽"), listOf("海魂兽王", "魔魂大白鲨", "十万年海神兽"),
            listOf("深渊领主", "黑暗邪魔", "百万年深渊兽"), listOf("天使守卫", "修罗使者", "神级守卫")
        )
        return names.getOrElse(mapId) { listOf("Boss", "领主", "魔王") }
    }
}

// ============================================================
// V5: 正态分布爆率（适配0-24合并层级）
// ============================================================

object DropDistribution {
    fun normalSample(mean: Double, stdDev: Double): Double {
        val u1 = Random.nextDouble(); val u2 = Random.nextDouble()
        val z = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        return mean + z * stdDev
    }

    fun rollTier(mean: Double, stdDev: Double, maxTier: Int): Int {
        val raw = normalSample(mean, stdDev)
        return raw.roundToInt().coerceIn(0, maxTier)
    }

    /** 地图品质均值：map0≈2, map7≈22 */
    fun tierMean(mapId: Int): Double = mapId * 2.4 + 2.5

    const val TIER_STD_DEV = 5.0

    const val BOSS_MEAN_OFFSET = 3.0

    /** 地图最大可掉落合并层级 */
    fun maxDropTier(mapId: Int): Int = when (mapId) {
        0 -> 1    // 圣魂村：百年·普通
        1 -> 5    // 诺丁城：千年·劣等
        2 -> 7    // 星斗：千年·精良
        3 -> 11   // 落日：万年·普通
        4 -> 14   // 极北：万年·完美
        5 -> 17   // 海神岛：十万年·精良
        6 -> 19   // 杀戮外域：十万年·完美
        else -> 24 // 神界：不限
    }

    fun maxBoneDropTier(mapId: Int): Int = maxDropTier(mapId)

    // ==== 向后兼容(V4旧API) ====
    @Deprecated("使用 tierMean", ReplaceWith("tierMean(mapId)"))
    fun qualityMean(mapId: Int): Double = tierMean(mapId)
    @Deprecated("使用 maxDropTier", ReplaceWith("maxDropTier(mapId)"))
    fun maxDropQuality(mapId: Int): Int = maxDropTier(mapId)
    @Deprecated("使用 maxBoneDropTier", ReplaceWith("maxBoneDropTier(mapId)"))
    fun maxBoneDropQuality(mapId: Int): Int = maxBoneDropTier(mapId)
    @Deprecated("使用 rollTier", ReplaceWith("rollTier(mean, stdDev, maxTier)"))
    fun rollQuality(mean: Double, stdDev: Double, maxQ: Int): Int = rollTier(mean, stdDev, maxQ)
    @Deprecated("使用 TIER_STD_DEV", ReplaceWith("TIER_STD_DEV"))
    const val QUALITY_STD_DEV: Double = TIER_STD_DEV

}

// ============================================================
// V5: Boss商店（双维度阶梯）
// ============================================================

object BossShopData {
    enum class BossShopTier(val label: String, val description: String, val unlockMapId: Int) {
        TIER_1("第一", "诺丁城外(地图1)", 1),
        TIER_2("第二", "星斗大森林外围(地图3)", 3),
        TIER_3("第三", "落日森林(地图5)", 5),
        TIER_4("第四", "极北之地(地图7)", 7)
    }

    data class ShopRingItem(
        val tier: BossShopTier,
        val combinedTier: Int,  // 0-24
        val cost: Int, val icon: String, val label: String
    ) {
        /** 向后兼容：yearOrdinal = combinedTier */
        val yearOrdinal: Int get() = combinedTier
    }

    data class ShopBoneItem(
        val tier: BossShopTier,
        val combinedTier: Int,
        val cost: Int, val icon: String, val label: String
    ) {
        /** 向后兼容：rarityOrdinal = combinedTier */
        val rarityOrdinal: Int get() = combinedTier
    }

    val ringItems = listOf(
        ShopRingItem(BossShopTier.TIER_1, 2, 20, "⚪", "百年·精良魂环"),
        ShopRingItem(BossShopTier.TIER_1, 7, 60, "🟢", "千年·精良魂环"),
        ShopRingItem(BossShopTier.TIER_2, 12, 180, "🔵", "万年·精良魂环"),
        ShopRingItem(BossShopTier.TIER_3, 17, 500, "🟣", "十万年·精良魂环"),
        ShopRingItem(BossShopTier.TIER_4, 22, 1500, "🟡", "百万年·精良魂环")
    )

    val boneItems = listOf(
        ShopBoneItem(BossShopTier.TIER_1, 2, 50, "⚪", "百年·精良魂骨"),
        ShopBoneItem(BossShopTier.TIER_1, 7, 150, "🟢", "千年·精良魂骨"),
        ShopBoneItem(BossShopTier.TIER_2, 12, 450, "🔵", "万年·精良魂骨"),
        ShopBoneItem(BossShopTier.TIER_3, 17, 1200, "🟣", "十万年·精良魂骨"),
        ShopBoneItem(BossShopTier.TIER_4, 22, 3500, "🟡", "百万年·精良魂骨")
    )

    fun getAllRingItems() = ringItems
    fun getAllBoneItems() = boneItems

    fun isTierUnlocked(tier: BossShopTier, currentMapId: Int): Boolean =
        currentMapId >= tier.unlockMapId

    fun bossCoinReward(mapId: Int, isBoss: Boolean): Int {
        // 设计原则：仅Boss掉落，根据地图难度动态调整
        // 目标：每小时获取约720个Boss币（支持每5分钟抽1次商店，50币/次）
        // 计算：360个Boss/小时 × 2个/Boss = 720币/小时 → 14.4次抽取/小时
        val baseDrop = when (mapId) {
            0 -> 1   // 圣魂村：新手期，较低产出
            1 -> 2   // 诺丁城
            2 -> 2   // 星斗外围
            3 -> 2   // 落日森林
            4 -> 2   // 极北之地：平衡点
            5 -> 2   // 海神岛
            6 -> 2   // 杀戮外域
            else -> 3 // 神界废墟：高回报
        }
        return if (isBoss) baseDrop else 0
    }

    const val SOUL_CORE_GACHA_COST = 50
    fun soulCoreBuyCost(def: SoulCoreDef): Int = def.bossCoinCost

    const val RING_EXCELLENT_COST = 300
    const val RING_PERFECT_COST = 800
    const val BONE_EXCELLENT_COST = 600
    const val BONE_PERFECT_COST = 1500
    const val SOUL_CRYSTAL_COST = 20
    const val SOUL_CRYSTAL_VALUE = 500L
}

// ============================================================
// V5: 限时珍品商店（双维度生成）
// ============================================================

object LimitedShopData {
    const val REFRESH_INTERVAL_MS = 600_000L
    const val RING_COUNT = 2
    const val BONE_COUNT = 1

    /** 基于地图计算基础合并层级 */
    fun calcRingTier(mapId: Int): Int = ((mapId + 1) * 3).coerceIn(0, 24)
    fun calcBoneTier(mapId: Int): Int = ((mapId + 1) * 2).coerceIn(0, 24)

    fun generateRings(mapId: Int): List<DroppedRing> {
        val baseTier = calcRingTier(mapId)
        return (1..RING_COUNT).map {
            val tier = (baseTier + Random.nextInt(-3, 4)).coerceIn(0, 24)
            val (year, quality) = SoulRingGenerator.splitTier(tier)
            val affixes = SoulRingGenerator.generateRandomAffixes(tier)
            val skill = ActiveSkillPool.randomSkill(tier)
            val pct = SoulRingSystem.randomPercentage()
            DroppedRing(year, quality, pct, affixes, skill)
        }
    }

    fun generateBones(mapId: Int): List<DroppedBone> {
        val baseTier = calcBoneTier(mapId)
        return (1..BONE_COUNT).map {
            val tier = (baseTier + Random.nextInt(-3, 4)).coerceIn(0, 24)
            val (year, rarity) = SoulRingGenerator.splitTier(tier)
            val boneType = Random.nextInt(BoneType.entries.size)
            val affixes = SoulBoneGenerator.generateRandomAffix(tier)
            val passive = PassiveSkillPool.randomPassive(boneType, tier)
            DroppedBone(boneType, year, rarity, affixes, passive)
        }
    }

    /** 魂环购买价格（基于合并层级）- 提高至合理水平 */
    fun ringBuyPrice(combinedTier: Int): Long = when {
        combinedTier < 5 -> 5000L + combinedTier * 3000L           // 5000-20000
        combinedTier < 10 -> 20000L + (combinedTier - 5) * 15000L  // 20000-95000
        combinedTier < 15 -> 100000L + (combinedTier - 10) * 50000L // 100000-350000
        combinedTier < 20 -> 400000L + (combinedTier - 15) * 200000L // 400000-1400000
        else -> 2000000L + (combinedTier - 20) * 800000L  // 200万+
    }

    /** 魂骨购买价格（基于合并层级）- 提高至合理水平 */
    fun boneBuyPrice(combinedTier: Int): Long = when {
        combinedTier < 5 -> 50000L + combinedTier * 30000L          // 50000-200000
        combinedTier < 10 -> 200000L + (combinedTier - 5) * 100000L // 200000-700000
        combinedTier < 15 -> 800000L + (combinedTier - 10) * 300000L // 800000-2300000
        combinedTier < 20 -> 3000000L + (combinedTier - 15) * 1000000L // 300万-800万
        else -> 10000000L + (combinedTier - 20) * 3000000L  // 1000万+
    }
}

// 背包魂核物品
 data class DroppedSoulCore(
     val name: String, val rarityOrdinal: Int, val passiveSkill: PassiveSkill, val value: Int,
     val level: Int = 0
 ) {
     /** 归属分类 */
     val category: SoulCoreCategory get() = name.toSoulCoreCategory()
     /** 当前等级下技能实际值 */
     val effectiveValue: Int get() = (value * SoulCoreLevelData.effectMultiplier(level)).toInt()
 }

// ============================================================
// V5: 灵魂学院 & 武魂流派系统
// ============================================================

enum class SchoolCategory { BASIC, SPECIAL }

enum class SoulSchool(
    val icon: String, val displayName: String, val description: String, val category: SchoolCategory
) {
    BALANCED("⚖️", "均衡流派", "全面发展，物法双修。\nHP×105% ATK×100% MATK×100% PDEF×105% MDEF×105% 暴击+5% 爆伤+5%", SchoolCategory.BASIC),
    PHYSICAL("⚔️", "物理流派", "近战强攻，重视物攻物防。\nHP×100% ATK×130% MATK×45% PDEF×115% MDEF×70% 暴击+8%", SchoolCategory.BASIC),
    MAGIC("🔮", "法系流派", "远程法术，重视魔攻魔防。\nHP×95% ATK×45% MATK×130% PDEF×70% MDEF×115% 爆伤+8%", SchoolCategory.BASIC),
    SUPPORT("🛡️", "辅助流派", "治疗增益，提升团队生存。\nHP×115% ATK×80% MATK×90% PDEF×120% MDEF×120% 暴击+3% 减伤+5%", SchoolCategory.SPECIAL),
    CONTROL("🌿", "控制流派", "控制敌人，限制行动。\nHP×105% ATK×95% MATK×110% PDEF×100% MDEF×105% 暴击+6% 爆伤+6%", SchoolCategory.SPECIAL),
    ASSASSIN("🗡️", "暗杀流派", "高暴发低防御，一击必杀。\nHP×90% ATK×140% MATK×50% PDEF×60% MDEF×60% 暴击+12% 爆伤+15%", SchoolCategory.SPECIAL)
}

data class SchoolStatMods(
    val hp: Float, val atk: Float, val matk: Float,
    val pdef: Float, val mdef: Float, val critRateBonus: Int, val critDmgBonus: Int
) {
    companion object {
        private val map = mapOf(
            SoulSchool.BALANCED to SchoolStatMods(1.05f, 1.00f, 1.00f, 1.05f, 1.05f, 5, 5),
            SoulSchool.PHYSICAL to SchoolStatMods(1.00f, 1.30f, 0.45f, 1.15f, 0.70f, 8, 0),
            SoulSchool.MAGIC to SchoolStatMods(0.95f, 0.45f, 1.30f, 0.70f, 1.15f, 0, 8),
            SoulSchool.SUPPORT to SchoolStatMods(1.15f, 0.80f, 0.90f, 1.20f, 1.20f, 3, 0),
            SoulSchool.CONTROL to SchoolStatMods(1.05f, 0.95f, 1.10f, 1.00f, 1.05f, 6, 6),
            SoulSchool.ASSASSIN to SchoolStatMods(0.90f, 1.40f, 0.50f, 0.60f, 0.60f, 12, 15)
        )
        fun get(school: SoulSchool): SchoolStatMods = map[school] ?: map[SoulSchool.BALANCED]!!
    }
}

// ============================================================
// V5: 杀气兑换商店
// ============================================================

data class TowerTitleItem(
    val id: String, val name: String, val cost: Int, val desc: String,
    val hp: Long, val atk: Int, val matk: Int, val pdef: Int, val mdef: Int,
    val critRate: Int, val critDmg: Int
)

data class TowerStatItem(val id: String, val name: String, val cost: Int, val hp: Long, val atk: Int)

object TowerShopData {
    val titleItems = listOf(
        TowerTitleItem("slayer", "杀戮者", 50, "HP+500 ATK+30 MATK+20", 500, 30, 20, 10, 10, 2, 5),
        TowerTitleItem("executioner", "处刑人", 150, "HP+1500 ATK+80 MATK+50 双防+25", 1500, 80, 50, 25, 25, 5, 10),
        TowerTitleItem("reaper", "死神使者", 400, "HP+4000 ATK+200 MATK+150 双防+80", 4000, 200, 150, 80, 80, 8, 20),
        TowerTitleItem("asura", "修罗", 1000, "HP+10000 ATK+500 MATK+400 双防+200 暴击+15% 爆伤+40%", 10000, 500, 400, 200, 200, 15, 40),
        TowerTitleItem("godslayer", "弑神者", 3000, "HP+30000 ATK+1500 MATK+1200 双防+600 暴击+25% 爆伤+80%", 30000, 1500, 1200, 600, 600, 25, 80)
    )
    val statItems = listOf(
        TowerStatItem("hp1", "生命强化I", 30, 200, 0),
        TowerStatItem("atk1", "攻击强化I", 30, 0, 15),
        TowerStatItem("hp2", "生命强化II", 80, 600, 0),
        TowerStatItem("atk2", "攻击强化II", 80, 0, 45),
        TowerStatItem("hp3", "生命强化III", 200, 1800, 0),
        TowerStatItem("atk3", "攻击强化III", 200, 0, 135),
        TowerStatItem("hp4", "生命强化IV", 500, 5000, 0),
        TowerStatItem("atk4", "攻击强化IV", 500, 0, 400)
    )
    fun normalKillingIntent(floor: Int): Int = when { floor <= 20 -> 1; floor <= 40 -> 2; floor <= 60 -> 3; floor <= 80 -> 5; else -> 8 }
    fun bossKillingIntent(floor: Int): Int = when { floor <= 20 -> 5; floor <= 40 -> 10; floor <= 60 -> 20; floor <= 80 -> 35; else -> 60 }
}

// ============================================================
// V5: 金币消耗类型
// ============================================================

enum class GoldSinkType(val displayName: String, val icon: String) {
    EXPAND_BACKPACK("背包扩容", "📦"),
    SOUL_CORE_DRAW("魂核抽卡", "💠"),
    REFRESH_SHOP("限时刷新", "🔄");
}

// ============================================================
// V5: 金币消耗数据（非战斗功能型消耗）
// ============================================================

object GoldSinkData {
    /** 魂核抽卡：花费固定金币抽取随机魂核 */
    const val SOUL_CORE_DRAW_COST: Long = 50000L

    /** 强制刷新限时商店：花费固定金币 */
    const val REFRESH_SHOP_COST: Long = 20000L
}

// ============================================================
// V5: 魂骨强化数据
// ============================================================

object EnhancementData {
    const val MAX_ENHANCE = 20
    fun enhanceEffect(level: Int): Double = 1.0 + level * 0.15
    fun enhanceCost(level: Int): Long = (1000L * (1.0 + level * 0.8)).toLong()
}

// ============================================================
// V5: 怪物生成器
// ============================================================

object MonsterGenerator {
    private val monsterNamePrefixes = listOf(
        listOf("野狼", "毒蛇", "野猪", "麻雀", "老鼠"),
        listOf("凶狼", "暗影蛇", "狂暴猪", "血鹰", "巨鼠"),
        listOf("幽冥狼", "凤尾蛇", "泰坦猿", "暗金熊", "蚁皇"),
        listOf("冰碧蝎", "雪魔", "冰霜巨人", "极地熊", "雪狼"),
        listOf("海魂兽", "魔魂鲨", "深海章鱼", "海龙", "水元素"),
        listOf("深渊恶魔", "黑暗骑士", "熔岩巨人", "暗影刺客", "噬魂者"),
        listOf("天使护卫", "神界守卫", "圣光骑士", "神罚者", "天罚之刃"),
        listOf("创世守卫", "混沌兽", "虚空行者", "时空裂隙兽", "终焉之龙")
    )

    fun generateMonster(mapId: Int, stage: Int): Monster {
        val map = MapData.getMap(mapId) ?: MapData.all[0]
        val stats = map.baseStats
        val namePrefix = monsterNamePrefixes.getOrElse(mapId) { monsterNamePrefixes[0] }
        val name = "${namePrefix[stage % namePrefix.size]}·${map.name}"
        val scale = 1.0 + stage * 0.12
        val hp = (stats.hp * scale).toLong().coerceAtLeast(10)
        val atk = (stats.atk * scale).toInt().coerceAtLeast(1)
        val matk = (stats.matk * scale).toInt().coerceAtLeast(1)
        val pdef = (stats.pdef * scale).toInt().coerceAtLeast(0)
        val mdef = (stats.mdef * scale).toInt().coerceAtLeast(0)
        val affixes = rollMonsterAffixes(mapId, stage, isBoss = false)
        return Monster(
            name = name, affixes = affixes,
            maxHp = hp, hp = hp, atk = atk, matk = matk,
            critRate = stats.critRate, critDmg = stats.critDmg,
            pdef = pdef, mdef = mdef,
            expReward = (stats.expReward * scale).toLong().coerceAtLeast(1),
            goldReward = (stats.goldReward * scale).toLong().coerceAtLeast(1),
            canDropEquip = Random.nextFloat() < 0.15f
        )
    }

    fun generateBoss(mapId: Int, stage: Int): Monster {
        val base = generateMonster(mapId, stage)
        val bossAffixes = base.affixes.toMutableList()
        if (!bossAffixes.contains(MonsterAffix.BOSS)) bossAffixes.add(0, MonsterAffix.BOSS)
        return base.copy(
            name = "【领主】${base.name}",
            maxHp = (base.maxHp * 3).toLong(),
            hp = (base.hp * 3).toLong(),
            atk = (base.atk * 1.5).toInt(),
            matk = (base.matk * 1.5).toInt(),
            pdef = (base.pdef * 1.3).toInt(),
            mdef = (base.mdef * 1.3).toInt(),
            expReward = (base.expReward * 3).toLong(),
            goldReward = (base.goldReward * 3).toLong(),
            affixes = bossAffixes,
            canDropEquip = true
        )
    }

    private fun rollMonsterAffixes(mapId: Int, stage: Int, isBoss: Boolean): List<MonsterAffix> {
        val affixes = mutableListOf<MonsterAffix>()
        val allAffixes = MonsterAffix.entries.filter { it != MonsterAffix.BOSS && it != MonsterAffix.TUTOR }
        val affixCount = when {
            isBoss -> Random.nextInt(2, 4)
            stage % 3 == 0 -> Random.nextInt(1, 3)
            else -> if (Random.nextFloat() < 0.3f) 1 else 0
        }
        val selected = allAffixes.shuffled().take(affixCount)
        affixes.addAll(selected)
        if (mapId == 0 && stage <= 2) affixes.add(MonsterAffix.TUTOR)
        return affixes
    }
}

/** 判断怪物是否为Boss */
fun Monster.isBoss(): Boolean = affixes.contains(MonsterAffix.BOSS)
