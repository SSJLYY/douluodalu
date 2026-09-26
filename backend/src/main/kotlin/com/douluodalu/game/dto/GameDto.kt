package com.douluodalu.game.dto

// ======== 游戏状态DTO ========
data class GameStateResponse(
    val profile: ProfileDto,
    val equippedRings: List<EquippedRingDto>,
    val equippedBones: List<EquippedBoneDto>,
    val equippedCores: List<EquippedCoreDto>,
    val backpackItems: List<BackpackItemDto>,
    val talents: Map<String, Int>,
    val achievements: List<String>,
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
    val checkIn: CheckInStatusDto = CheckInStatusDto()
)

/**
 * 战力明细（任务#23）。全部字段带默认值 0，向后兼容。
 * 拆分不变量（EquipmentPowerService.detail 保证，测试断言）：
 *  - ringAtk + boneAtk + coreAtk == EquipmentBonus.atkBonus
 *  - ringHp + boneHp == EquipmentBonus.hpBonus（魂核只加攻击百分比，coreHp 恒为 0；
 *    玩家模型无基础生命维度，baseHp 恒为 0，均为真实来源语义）
 *  - basePower + ringPower + bonePower + corePower == power（powerOf 同式）
 */
data class PowerDetailDto(
    /** 基础攻击 = PLAYER_ATK_BASE + level × PLAYER_ATK_PER_LEVEL */
    val baseAtk: Long = 0,
    val baseHp: Long = 0,
    /** 基础行战力 = POWER_BASE + POWER_LEVEL_WEIGHT × level + baseAtk */
    val basePower: Long = 0,
    val ringAtk: Long = 0,
    val ringHp: Long = 0,
    /** 魂环战力贡献 = ringAtk + 魂环生命折算（余数按最大余数法分配，保证四行求和==power） */
    val ringPower: Long = 0,
    val boneAtk: Long = 0,
    val boneHp: Long = 0,
    val bonePower: Long = 0,
    val coreAtk: Long = 0,
    val coreHp: Long = 0,
    val corePower: Long = 0
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
    val tutorialStep: Int
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
    val rewards: List<CheckInRewardDto> = emptyList()
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
    val monsterHpAfter: Long
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
    val power: Long = 0 // 战斗力（含装备加成），字段带默认值向后兼容，前端暂未消费
)
