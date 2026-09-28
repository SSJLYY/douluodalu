package com.douluodalu.game.dto

import jakarta.validation.constraints.NotBlank

// ======== 游戏状态DTO ========
data class GameStateResponse(
    val profile: ProfileDto,
    val equippedRings: List<EquippedRingDto>,
    val equippedBones: List<EquippedBoneDto>,
    val equippedCores: List<EquippedCoreDto>,
    val backpackItems: List<BackpackItemDto>,
    val talents: Map<String, Int>,
    /** 成就面板（AchievementService.getStatus 合成；解锁在写事务内自动发生，无领取端点） */
    val achievements: List<AchievementDto> = emptyList(),
    // ======== 任务#21：战力 + 魂环负荷体系（全部带默认值，向后兼容） ========
    /** 总战斗力（EquipmentPowerService.powerOf，与战斗/爬塔响应同源） */
    val power: Long = 0,
    /** 当前已装备魂环总负荷（shared SoulRingSystem.calcRingLoad 逐件求和） */
    val ringLoad: Long = 0,
    /** 魂环吸收容量 = 根骨×6（超负荷装环会被 400 拒绝） */
    val capacity: Long = 0,
    /** 任务#23：战力明细分解（攻击/生命按来源拆分，拆分求和与 power 严格一致） */
    val powerDetail: PowerDetailDto = PowerDetailDto(),
    /** 每日签到状态（尾部新增带默认值，向后兼容） */
    val checkIn: CheckInStatusDto = CheckInStatusDto(),
    /** 每日任务面板：date=yyyy-MM-dd（与签到同一时区口径）；quests 固定返回全部任务定义（含未开始 progress=0，只读当天不建行） */
    val dailyQuests: DailyQuestsDto = DailyQuestsDto(),
    /** 第十七轮战斗模型扩展：玩家有效战斗属性（matk/pdef/mdef/critRate/critDmg，与 battle 结算
     *  入参同源 playerCombatStats，含成就/装备加成与转生倍率）。尾部新增全默认值，向后兼容 */
    val combatStats: CombatStatsDto = CombatStatsDto()
)

/** 玩家有效战斗属性（GameStateResponse.combatStats）。全默认值向后兼容；
 *  critRate/critDmg 为百分点数（critRate 5 = 5%、critDmg 150 = 1.5 倍），atk/hp 不在此重复
 *  （已由 profile.currentHp / powerDetail.baseAtk 承载） */
data class CombatStatsDto(
    val matk: Long = 0,
    val pdef: Long = 0,
    val mdef: Long = 0,
    val critRate: Int = 0,
    val critDmg: Int = 0
)

/**
 * 战力明细（任务#23）。全部字段带默认值 0，向后兼容。
 * 拆分不变量（EquipmentPowerService.detail 保证，测试断言）：
 *  - ringAtk + boneAtk + coreAtk == EquipmentBonus.atkBonus（不含成就加成）
 *  - ringHp + boneHp == EquipmentBonus.hpBonus（魂核只加攻击百分比，coreHp 恒为 0；
 *    玩家模型无基础生命维度，baseHp 恒为 0，均为真实来源语义）
 *  - basePower + ringPower + bonePower + corePower + achievement + prestige == power（powerOf 同式，
 *    成就加成单列一行：achievement = 成就 atk + 成就 hp/POWER_HP_DIVISOR 折算；
 *    转生倍率增量单列第 6 行 prestige，转数 0 时恒为 0）
 *  - 上式六行 + soul == power（第十八轮武魂觉醒：武魂贡献单列第 7 行，soul 行 = 含武魂战力 −
 *    六行之和；未觉醒恒为 0，退化为原六行恒等）
 *  - 上式七行 + school == power（第十九轮流派：流派系数战力贡献单列第 8 行，school 行 =
 *    含流派战力 − 七行之和（差值法，prestige/soul 行同款）；未选流派恒为 0，退化为原七行恒等）
 *  - 上式八行 + enhance == power（第二十七轮魂骨强化：强化乘区战力增量单列第 9 行，enhance 行 =
 *    含强化全量战力 − 全骨 enhanceLevel 归零口径战力（差值法收尾）；骨行 boneAtk/boneHp/bonePower
 *    按归零口径重算，避免与强化行双重计入。无强化骨时该行恒为 0、骨行与旧口径逐位一致，退化为原八行恒等）
 *  - 上式九行 + title == power（第二十九轮杀气商店：已拥有称号 + HP/ATK 属性购买的加成折算
 *    单列第 10 行（差值法，prestige/soul/school 行同款，先乘转生倍率后并入）；无称号且无购买
 *    恒为 0，退化为原九行恒等）
 */
data class PowerDetailDto(
    /** 基础攻击 = PLAYER_ATK_BASE + level × PLAYER_ATK_PER_LEVEL */
    val baseAtk: Long = 0,
    val baseHp: Long = 0,
    /** 基础行战力 = POWER_BASE + POWER_LEVEL_WEIGHT × level + baseAtk */
    val basePower: Long = 0,
    val ringAtk: Long = 0,
    val ringHp: Long = 0,
    /** 魂环战力贡献 = ringAtk + 魂环生命折算（余数按最大余数法分配，保证求和==power） */
    val ringPower: Long = 0,
    val boneAtk: Long = 0,
    val boneHp: Long = 0,
    val bonePower: Long = 0,
    val coreAtk: Long = 0,
    val coreHp: Long = 0,
    val corePower: Long = 0,
    /** 成就加成战力行（已解锁成就 hp/atk 求和折算；未解锁任何成就时为 0） */
    val achievement: Long = 0,
    /** 转生倍率战力增量行（装备+成就加成 ×(1+转数×0.1) 相对 1.0 倍的增量；未转生恒为 0） */
    val prestige: Long = 0,
    /** 武魂战力行（第十八轮武魂觉醒：= 含武魂战力 − 六行之和；未觉醒恒为 0。尾部新增，向后兼容） */
    val soul: Long = 0,
    /** 流派战力行（第十九轮流派：= 含流派战力 − 七行之和；未选流派恒为 0。尾部新增，向后兼容） */
    val school: Long = 0,
    /** 魂骨强化战力行（第二十七轮：= 含强化全量战力 − 全骨归零口径战力；骨行为归零口径。
     *  无强化骨恒为 0。尾部新增，向后兼容） */
    val enhance: Long = 0,
    /** 杀气商店战力行（第二十九轮：已拥有称号 + HP/ATK 属性购买的加成折算，差值法第 10 行；
     *  无称号且无购买恒为 0。尾部新增，向后兼容） */
    val title: Long = 0
)

// ======== 成就 ========
/** 成就奖励（全字段照带；第一版战斗模型只消费 hp/atk，其余待属性系统扩展后生效） */
data class AchievementRewardDto(
    val hp: Long = 0,
    val atk: Int = 0,
    val matk: Int = 0,
    val pdef: Int = 0,
    val mdef: Int = 0,
    val critRate: Int = 0,
    val critDmg: Int = 0
)

/** 单条成就状态（GameStateResponse.achievements；category：CULTIVATION/SOUL_RING/BATTLE/TOWER/PRESTIGE） */
data class AchievementDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val target: Long = 0,
    val progress: Long = 0,
    val unlocked: Boolean = false,
    /** 解锁日期 yyyy-MM-dd（LocalDate.toString()）；未解锁为 null */
    val unlockedAt: String? = null,
    val rewards: AchievementRewardDto = AchievementRewardDto()
)

// ======== 杀气商店（第二十九轮） ========
// 放置说明：需求为「Shop 相关 DTO 类之后」——GameDto.kt 内无 Shop DTO（ShopItem 在
// model/GameModels.kt，GameBalance 挂商店定义），故插在成就区块之后、不放文件末尾
// （避免与并行分支的尾部追加冲突）。
/** GET /api/shop/killing 响应：8 称号的拥有/价格/属性预览 + HP/ATK 两商品当前价/已购次数 */
data class KillingShopDto(
    /** 当前杀气余额（profile.killingIntent 同源，塔页展示同一字段） */
    val killingIntent: Int,
    val titles: List<KillingTitleDto>,
    val attrs: List<KillingAttrDto>
)

/** 杀气称号条目（GameBalance.KILLING_TITLES 全表下发，owned 为已拥有态——购买即永久拥有不可回购） */
data class KillingTitleDto(
    val id: String,
    val name: String,
    val cost: Long,
    /** 属性预览（全部已拥有称号叠加生效，此处为单张的加成） */
    val hp: Long,
    val atk: Long,
    val pdef: Long,
    val critRate: Long,
    val critDmg: Long,
    val owned: Boolean
)

/** 杀气属性购买条目（stat ∈ hp|atk；价格 = 100×2^buys，GameBalance.killingAttrCost 前后端镜像同源） */
data class KillingAttrDto(
    val stat: String,
    val displayName: String,
    /** 单次购买效果：+100 HP / +10 ATK（基值固定不随等级缩放，照文档 §7.4） */
    val effectPerBuy: Long,
    /** 已购次数（HP/ATK 各自独立计数） */
    val buys: Int,
    /** 下一次购买价格（当前价） */
    val nextCost: Long
)

/** POST /api/shop/killing/title/{titleId} 与 /attribute/{stat} 成功响应（失败抛 IllegalArgumentException → 统一 400） */
data class KillingBuyResponse(
    val message: String,
    /** 扣减后杀气余额 */
    val killingIntent: Int,
    /** 称号兑换成功时回传称号 id（属性购买为 null） */
    val titleId: String? = null,
    /** 属性购买成功时回传 stat（称号兑换为 null） */
    val stat: String? = null,
    /** 属性购买成功时回传累计次数（称号兑换为 null） */
    val buys: Int? = null
)

data class ProfileDto(
    val level: Int,
    val gold: Long,
    val soulPower: Long,
    val bossCoin: Long,
    val martialSoulName: String?,
    val chosenSchool: String?,
    val currentMapId: Int,
    val currentStage: Int,
    val currentHp: Long,
    val battleSoulPower: Int,
    val totalBattleWins: Long,
    val totalBattleLosses: Long,
    val towerFloor: Int,
    val killingIntent: Int,
    val prestigeCount: Int,
    val talentPoints: Int,
    val codexKills: Long,
    val autoBattle: Boolean,
    val autoAdvanceMap: Boolean,
    val autoBreakthrough: Boolean,
    val tutorialStep: Int,
    /** 武魂品质徽章（第十八轮）：由 martialSoulName 反查武魂池得到，不落库（尾部新增，向后兼容）；
     *  取值 COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC，未觉醒为 null */
    val soulRarity: String? = null,
    /** 武魂技能名（第二十轮）：由 martialSoulName 反查武魂池得到，不落库（尾部新增，向后兼容）；
     *  未觉醒/无技能（历史脏数据）为 null */
    val soulSkillName: String? = null
)

data class EquippedRingDto(
    val slotIndex: Int,
    val yearOrdinal: Int,
    val qualityOrdinal: Int,
    val percentage: Int,
    val affixesJson: String?,
    val skillName: String?,
    /** 该魂环负荷（任务#21，= 等效年份；带默认值向后兼容） */
    val load: Long = 0
)

data class EquippedBoneDto(
    val slotIndex: Int,
    val yearOrdinal: Int,
    val rarityOrdinal: Int,
    val enhanceLevel: Int,
    val affixesJson: String?,
    val passiveSkillName: String?
)

data class EquippedCoreDto(
    val slotType: String,
    val coreName: String,
    val rarityOrdinal: Int,
    val passiveSkillName: String?,
    val value: Int,
    val level: Int
)

data class BackpackItemDto(
    val id: Long,
    val itemType: String,
    val yearOrdinal: Int,
    val qualityOrdinal: Int,
    val affixesJson: String?,
    val locked: Boolean,
    val percentage: Int,
    val skillName: String?,
    val boneTypeOrdinal: Int?,
    val enhanceLevel: Int,
    val passiveSkillName: String?,
    val coreName: String?,
    val coreValue: Int?,
    val coreLevel: Int,
    /** 若为魂环：该环负荷（前端"装得下/装不下"预览用）；其他类型为 0。任务#21，向后兼容 */
    val load: Long = 0
)

// ======== 每日签到 ========
/** POST /api/game/checkin 的签到结果 */
data class CheckInResult(
    val goldGained: Long,
    val bossCoinGained: Long,
    val soulPowerGained: Long,
    val streak: Long,
    val totalDays: Long,
    /** 本次签到落在 7 日循环的第几天（1-7） */
    val cycleDay: Int
)

/** 签到状态（GameStateResponse.checkIn）。nextCycleDay = (streak % 7) + 1：已签时代表明天，未签时代表今天 */
data class CheckInStatusDto(
    val signedToday: Boolean = false,
    val streak: Long = 0,
    val totalDays: Long = 0,
    val nextCycleDay: Int = 1,
    /** 固定返回 7 天循环全表（含数值），前端据此渲染预览格，避免前后端数值表漂移 */
    val rewards: List<CheckInRewardDto> = emptyList(),
    /** 是否可补签（第二十一轮，尾部新增带默认值向后兼容）：昨日无签到记录 且 用户有任意历史签到。
     *  已签今日不影响——签了今天仍可补昨日 */
    val makeupAvailable: Boolean = false
)

/** POST /api/game/checkin/makeup 的补签结果（失败返回 success=false + message，HTTP 200，照 breakthrough 惯例）。
 *  补签只修复连签、不补发当日奖励；失败时除 success/message 外均为默认值 */
data class MakeupResponse(
    val success: Boolean,
    val streak: Long = 0,
    val totalDays: Long = 0,
    val goldSpent: Long = 0,
    val message: String = ""
)

data class CheckInRewardDto(
    val day: Int = 0,
    val gold: Long = 0,
    val bossCoin: Long = 0,
    val soulPower: Long = 0
)

// ======== 操作响应 ========
data class BattleRoundLog(
    val round: Int,
    val playerHpBefore: Long,
    val monsterHpBefore: Long,
    val playerDamage: Long,
    val monsterDamage: Long,
    val playerHpAfter: Long,
    val monsterHpAfter: Long,
    /** 武魂技能名（第二十轮，尾部新增带默认值向后兼容）：技能释放回合为技能名，普通回合 null。
     *  HEAL 技能回合 playerDamage=0、playerHpAfter 为回血并挨打后的余量（before-damage 恒等式在该类回合不成立） */
    val skillName: String? = null
)

data class BattleResponse(
    val won: Boolean,
    val rounds: Int,
    val monsterName: String,
    val monsterMaxHp: Long,
    val expGained: Long,
    val goldGained: Long,
    val drops: List<BackpackItemDto>,
    val playerHp: Long,
    val playerLevel: Int,
    val playerGold: Long,
    val playerSoulPower: Long,
    val battleLog: List<BattleRoundLog>,
    val message: String? = null, // 背包满导致掉落丢失时的提示
    val power: Long = 0 // 战斗力（含装备加成），字段带默认值向后兼容，前端暂未消费
)

data class CultivateResponse(
    val soulPowerGained: Long,
    val totalSoulPower: Long,
    val level: Int
)

data class BreakthroughResponse(
    val success: Boolean,
    val newLevel: Int,
    val message: String
)

/** POST /api/action/prestige 的转生结果（失败返回 success=false + 原因，HTTP 200，与 breakthrough 一致） */
data class PrestigeResponse(
    val success: Boolean,
    val prestigeCount: Int,
    val message: String
)

/** POST /api/action/awaken 的觉醒/重醒结果（失败返回 success=false + message，HTTP 200，与 breakthrough 一致）。
 *  rarity 取值 COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC；失败时除 success/message 外均为默认值 */
data class AwakenResponse(
    val success: Boolean,
    val martialSoulName: String = "",
    val rarity: String = "",        // COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC
    val goldSpent: Long = 0,        // 首醒 0 / 重醒 5000
    val reawakened: Boolean = false,
    val message: String = ""
)

/** POST /api/action/school 的选择流派结果（失败返回 success=false + message，HTTP 200，与 breakthrough 一致）。
 *  chosenSchool 为流派枚举名（BALANCED|PHYSICAL|MAGIC|SUPPORT|CONTROL|ASSASSIN）；失败时为空串 */
data class ChooseSchoolResponse(
    val success: Boolean,
    val chosenSchool: String = "",
    val message: String = ""
)

/** POST /api/action/school 请求体：school 为流派枚举名；非法枚举名走业务校验返回 success=false「未知流派」 */
data class ChooseSchoolRequest(
    @field:NotBlank(message = "school 不能为空")
    val school: String = ""
)

data class OfflineRewardResponse(
    val offlineSeconds: Long,
    val goldGained: Long,
    val expGained: Long,
    val battleWins: Long
)

data class SimpleResponse(
    val success: Boolean,
    val message: String
)

data class TowerResponse(
    val won: Boolean,
    val rounds: Int,
    val monsterName: String,
    val expGained: Long,
    val goldGained: Long,
    val bossCoinGained: Long,
    val towerFloor: Int,
    val killingIntent: Int,
    val drops: List<BackpackItemDto>,
    val playerLevel: Int,
    val power: Long = 0, // 战斗力（含装备加成），字段带默认值向后兼容，前端暂未消费
    /** 塔战逐回合日志（呈现层，独立种子可复现回放，前端塔页复用 BattleReplay）；空列表=旧版本行为 */
    val battleLog: List<BattleRoundLog> = emptyList()
)

// ======== 每日任务 ========
/** 单条每日任务状态（GameStateResponse.dailyQuests.quests 固定返回全部任务定义，缺行 progress=0） */
data class DailyQuestDto(
    val id: String = "",
    val description: String = "",
    val target: Int = 0,
    val progress: Int = 0,
    val claimed: Boolean = false,
    val rewardGold: Long = 0,
    val rewardBossCoin: Long = 0,
    val rewardSoulPower: Long = 0
)

/** 每日任务面板（GameStateResponse 尾部新增带默认值，向后兼容）。date=yyyy-MM-dd（LocalDate.now()，与签到同一时区口径） */
data class DailyQuestsDto(
    val date: String = "",
    val quests: List<DailyQuestDto> = emptyList()
)

/** POST /api/game/quests/claim 成功响应 */
data class ClaimQuestResponse(
    val questId: String,
    val goldGained: Long,
    val bossCoinGained: Long,
    val soulPowerGained: Long
)
