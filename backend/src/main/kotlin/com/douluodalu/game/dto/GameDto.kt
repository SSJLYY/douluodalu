package com.douluodalu.game.dto

// ======== 游戏状态DTO ========
data class GameStateResponse(
    val profile: ProfileDto,
    val equippedRings: List<EquippedRingDto>,
    val equippedBones: List<EquippedBoneDto>,
    val equippedCores: List<EquippedCoreDto>,
    val backpackItems: List<BackpackItemDto>,
    val talents: Map<String, Int>,
    val achievements: List<String>
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
    val skillName: String?
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
    val coreLevel: Int
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
