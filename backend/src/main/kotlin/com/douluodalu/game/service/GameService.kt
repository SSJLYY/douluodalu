package com.douluodalu.game.service

import com.douluodalu.game.dto.*
import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.entity.EquippedBone
import com.douluodalu.game.entity.EquippedCore
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.TalentRepository
import com.douluodalu.game.repository.EquippedRingRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.random.Random

@Service
class GameService(
    private val profileRepo: PlayerProfileRepository,
    private val backpackRepo: BackpackItemRepository,
    private val talentRepo: TalentRepository,
    private val equippedRingRepo: EquippedRingRepository,
    private val equippedBoneRepo: EquippedBoneRepository,
    private val equippedCoreRepo: EquippedCoreRepository
) {
    companion object {
        val REALM_NAMES = listOf(
            "魂士", "魂师", "大魂师", "魂尊", "魂宗",
            "魂王", "魂帝", "魂圣", "魂斗罗", "封号斗罗",
            "极限斗罗", "半神", "神祇", "神王", "至高神王", "创世神"
        )
        val MAP_NAMES = listOf(
            "圣魂村", "诺丁城外", "星斗外围", "落日森林",
            "极北之地", "海神岛", "杀戮之都外域", "神界废墟"
        )
    }

    @Transactional(readOnly = true)
    fun getGameState(userId: Long): GameStateResponse {
        val profile = profileRepo.findByUserId(userId)
            ?: throw IllegalStateException("玩家存档不存在")
        val talents = talentRepo.findByUserId(userId).associate { it.branch to it.level }
        val equippedRings = equippedRingRepo.findByUserId(userId)
            .map { EquippedRingDto(it.slotIndex, it.yearOrdinal, it.qualityOrdinal, it.percentage, null, null) }
        val equippedBones = equippedBoneRepo.findByUserId(userId)
            .map { EquippedBoneDto(it.slotIndex, it.yearOrdinal, it.qualityOrdinal, it.enhanceLevel, null, null) }
        val equippedCores = equippedCoreRepo.findByUserId(userId)
            .map { EquippedCoreDto(it.slotType, it.coreName, it.rarityOrdinal, null, it.coreValue, it.coreLevel) }
        return GameStateResponse(
            profile = toProfileDto(profile),
            equippedRings = equippedRings,
            equippedBones = equippedBones,
            equippedCores = equippedCores,
            backpackItems = backpackRepo.findByUserId(userId).map { toBackpackItemDto(it) },
            talents = talents,
            achievements = emptyList()
        )
    }

    @Transactional
    fun cultivate(userId: Long): CultivateResponse {
        val profile = getProfile(userId)
        val baseGain = 10L + profile.level * 2L
        val gain = baseGain + Random.nextLong(baseGain / 4)
        profile.soulPower += gain
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        return CultivateResponse(gain, profile.soulPower, profile.level)
    }

    @Transactional
    fun breakthrough(userId: Long): BreakthroughResponse {
        val profile = getProfile(userId)
        val cost = getBreakthroughCost(profile.level)
        if (profile.soulPower < cost) {
            return BreakthroughResponse(false, profile.level, "魂力不足，需要${cost}，当前${profile.soulPower}")
        }
        profile.soulPower -= cost
        profile.level += 1
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        return BreakthroughResponse(true, profile.level, "突破成功！当前境界：${getRealmName(profile.level)} Lv.${profile.level}")
    }

    @Transactional
    fun battle(userId: Long): BattleResponse {
        val profile = getProfile(userId)
        val mapId = profile.currentMapId
        val stage = profile.currentStage

        // 生成怪物（使用浮点数避免精度丢失）
        val monsterHp = ((200L + mapId * 300L) * (1.0 + stage * 0.12)).toLong()
        val monsterAtk = ((15 + mapId * 25) * (1.0 + stage * 0.12)).toInt()
        val monsterName = "${MAP_NAMES.getOrElse(mapId) { "未知" }}·${stage}层怪物"

        // 简化战斗计算
        val playerAtk = 50L + profile.level * 10L
        var playerHp = profile.currentHp
        var mHp = monsterHp
        var rounds = 0
        val maxRounds = 30

        while (rounds < maxRounds && playerHp > 0 && mHp > 0) {
            rounds++
            // 玩家攻击
            val pDmg = playerAtk + Random.nextLong(playerAtk / 5)
            mHp -= pDmg
            if (mHp <= 0) break
            // 怪物攻击
            val mDmg = monsterAtk.toLong() + Random.nextLong((monsterAtk / 5).toLong())
            playerHp -= mDmg
        }

        val won = mHp <= 0
        val expGained = if (won) (50L + mapId * 30L + stage * 10L) else 0L
        val goldGained = if (won) (30L + mapId * 20L + stage * 8L) else 0L

        if (won) {
            profile.totalBattleWins++
            profile.gold += goldGained
            profile.soulPower += expGained
            profile.currentHp = playerHp.coerceAtLeast(1)
            // 推进关卡
            if (stage >= 15) {
                if (profile.autoAdvanceMap && mapId < 7) {
                    profile.currentMapId = mapId + 1
                    profile.currentStage = 1
                } else {
                    profile.currentStage = 15
                }
            } else {
                profile.currentStage = stage + 1
            }
        } else {
            profile.totalBattleLosses++
            profile.currentHp = getMaxHp(profile.level) // 死亡回满血
            profile.currentStage = 1 // 退回第1关
        }
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)

        // 战斗掉落
        val drops = mutableListOf<BackpackItemDto>()
        if (won) {
            val dropChance = 0.15 + mapId * 0.02 + stage * 0.005
            if (Random.nextDouble() < dropChance) {
                val dropType = when (Random.nextInt(3)) {
                    0 -> "RING"
                    1 -> "BONE"
                    else -> "CORE"
                }
                val yearOrdinal = (mapId / 2).coerceIn(0, 4)
                val qualityOrdinal = Random.nextInt(0, 5)
                val item = BackpackItemEntity(
                    userId = userId,
                    itemType = dropType,
                    yearOrdinal = yearOrdinal,
                    qualityOrdinal = qualityOrdinal,
                    percentage = Random.nextInt(100, 1000)
                )
                backpackRepo.save(item)
                drops.add(toBackpackItemDto(item))
            }
            // Boss额外掉落
            if (stage % 5 == 0 && Random.nextDouble() < 0.4) {
                profile.bossCoin += 1 + mapId
            }
        }

        return BattleResponse(
            won = won, rounds = rounds, monsterName = monsterName,
            expGained = expGained, goldGained = goldGained,
            drops = drops, playerHp = profile.currentHp,
            playerLevel = profile.level, playerGold = profile.gold,
            playerSoulPower = profile.soulPower
        )
    }

    @Transactional
    fun claimOfflineReward(userId: Long): OfflineRewardResponse {
        val profile = getProfile(userId)
        val lastLogout = profile.lastLogoutTime ?: return OfflineRewardResponse(0, 0, 0, 0)
        val offlineSeconds = Duration.between(lastLogout, LocalDateTime.now()).seconds
        val maxSeconds = 12 * 3600L
        val effectiveSeconds = min(offlineSeconds, maxSeconds)
        if (effectiveSeconds < 60) return OfflineRewardResponse(effectiveSeconds, 0, 0, 0)

        val efficiency = 0.8
        val goldPerSecond = (10L + profile.level * 2L) * efficiency
        val expPerSecond = (5L + profile.level) * efficiency
        val goldGained = (goldPerSecond * effectiveSeconds).toLong()
        val expGained = (expPerSecond * effectiveSeconds).toLong()
        val battleWins = effectiveSeconds / 5

        profile.gold += goldGained
        profile.soulPower += expGained
        profile.totalBattleWins += battleWins
        profile.lastLogoutTime = LocalDateTime.now()
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)

        return OfflineRewardResponse(effectiveSeconds, goldGained, expGained, battleWins)
    }

    private fun getProfile(userId: Long): PlayerProfileEntity {
        return profileRepo.findByUserId(userId)
            ?: throw IllegalStateException("玩家存档不存在")
    }

    private fun getBreakthroughCost(level: Int): Long {
        return (120.0 * Math.pow(level.toDouble(), 1.55)).toLong()
    }

    private fun getRealmName(level: Int): String {
        val idx = ((level - 1) / 10).coerceIn(0, REALM_NAMES.size - 1)
        return REALM_NAMES[idx]
    }

    private fun getMaxHp(level: Int): Long = 50L * level + 100L

    private fun toProfileDto(p: PlayerProfileEntity) = ProfileDto(
        level = p.level, gold = p.gold, soulPower = p.soulPower, bossCoin = p.bossCoin,
        martialSoulName = p.martialSoulName, chosenSchool = p.chosenSchool,
        currentMapId = p.currentMapId, currentStage = p.currentStage,
        currentHp = p.currentHp, battleSoulPower = p.battleSoulPower,
        totalBattleWins = p.totalBattleWins, totalBattleLosses = p.totalBattleLosses,
        towerFloor = p.towerFloor, killingIntent = p.killingIntent,
        prestigeCount = p.prestigeCount, talentPoints = p.talentPoints,
        codexKills = p.codexKills, autoBattle = p.autoBattle,
        autoAdvanceMap = p.autoAdvanceMap, autoBreakthrough = p.autoBreakthrough,
        tutorialStep = p.tutorialStep
    )

    private fun toBackpackItemDto(e: com.douluodalu.game.entity.BackpackItemEntity) = BackpackItemDto(
        id = e.id, itemType = e.itemType, yearOrdinal = e.yearOrdinal,
        qualityOrdinal = e.qualityOrdinal, affixesJson = e.affixesJson, locked = e.locked,
        percentage = e.percentage, skillName = e.skillName, boneTypeOrdinal = e.boneTypeOrdinal,
        enhanceLevel = e.enhanceLevel, passiveSkillName = e.passiveSkillName,
        coreName = e.coreName, coreValue = e.coreValue, coreLevel = e.coreLevel
    )

    // ======== 装备操作 ========
    @Transactional
    fun equipRing(userId: Long, slotIndex: Int, ringIndex: Int): Boolean {
        if (slotIndex < 0 || slotIndex > 8) return false // 9个槽位

        // 获取背包中所有魂环（按创建时间排序）
        val rings = backpackRepo.findByUserIdAndItemType(userId, "RING").sortedBy { it.createdAt }
        if (ringIndex < 0 || ringIndex >= rings.size) return false
        val ring = rings[ringIndex]

        // 检查槽位是否已被占用，如果有则卸下原有魂环
        val existing = equippedRingRepo.findByUserIdAndSlotIndex(userId, slotIndex)
        if (existing != null) {
            // 卸下已有魂环
            backpackRepo.save(
                BackpackItemEntity(
                    userId = userId,
                    itemType = "RING",
                    yearOrdinal = existing.yearOrdinal,
                    qualityOrdinal = existing.qualityOrdinal,
                    percentage = existing.percentage
                )
            )
            equippedRingRepo.delete(existing)
        }

        // 装备新魂环
        equippedRingRepo.save(
            EquippedRing(
                userId = userId,
                slotIndex = slotIndex,
                ringId = ring.id,
                yearOrdinal = ring.yearOrdinal,
                qualityOrdinal = ring.qualityOrdinal,
                percentage = ring.percentage
            )
        )
        backpackRepo.delete(ring)
        return true
    }

    @Transactional
    fun unequipRing(userId: Long, slotIndex: Int): Boolean {
        if (slotIndex < 0 || slotIndex > 8) return false
        val equipped = equippedRingRepo.findByUserIdAndSlotIndex(userId, slotIndex) ?: return false

        // 移回背包
        backpackRepo.save(
            BackpackItemEntity(
                userId = userId,
                itemType = "RING",
                yearOrdinal = equipped.yearOrdinal,
                qualityOrdinal = equipped.qualityOrdinal,
                percentage = equipped.percentage
            )
        )
        equippedRingRepo.delete(equipped)
        return true
    }

    @Transactional
    fun equipBone(userId: Long, slotIndex: Int, boneIndex: Int): Boolean {
        if (slotIndex < 0 || slotIndex > 5) return false // 6个槽位

        val bones = backpackRepo.findByUserIdAndItemType(userId, "BONE").sortedBy { it.createdAt }
        if (boneIndex < 0 || boneIndex >= bones.size) return false
        val bone = bones[boneIndex]

        val existing = equippedBoneRepo.findByUserIdAndSlotIndex(userId, slotIndex)
        if (existing != null) {
            backpackRepo.save(
                BackpackItemEntity(
                    userId = userId,
                    itemType = "BONE",
                    yearOrdinal = existing.yearOrdinal,
                    qualityOrdinal = existing.qualityOrdinal,
                    boneTypeOrdinal = existing.boneTypeOrdinal,
                    enhanceLevel = existing.enhanceLevel
                )
            )
            equippedBoneRepo.delete(existing)
        }

        equippedBoneRepo.save(
            EquippedBone(
                userId = userId,
                slotIndex = slotIndex,
                boneId = bone.id,
                yearOrdinal = bone.yearOrdinal,
                qualityOrdinal = bone.qualityOrdinal,
                boneTypeOrdinal = bone.boneTypeOrdinal ?: 0,
                enhanceLevel = bone.enhanceLevel
            )
        )
        backpackRepo.delete(bone)
        return true
    }

    @Transactional
    fun unequipBone(userId: Long, slotIndex: Int): Boolean {
        if (slotIndex < 0 || slotIndex > 5) return false
        val equipped = equippedBoneRepo.findByUserIdAndSlotIndex(userId, slotIndex) ?: return false

        backpackRepo.save(
            BackpackItemEntity(
                userId = userId,
                itemType = "BONE",
                yearOrdinal = equipped.yearOrdinal,
                qualityOrdinal = equipped.qualityOrdinal,
                boneTypeOrdinal = equipped.boneTypeOrdinal,
                enhanceLevel = equipped.enhanceLevel
            )
        )
        equippedBoneRepo.delete(equipped)
        return true
    }

    @Transactional
    fun equipCore(userId: Long, slotType: String, coreIndex: Int): Boolean {
        val validSlots = setOf("LEFT", "RIGHT")
        if (!validSlots.contains(slotType.uppercase())) return false

        val cores = backpackRepo.findByUserIdAndItemType(userId, "CORE").sortedBy { it.createdAt }
        if (coreIndex < 0 || coreIndex >= cores.size) return false
        val core = cores[coreIndex]

        val existing = equippedCoreRepo.findByUserIdAndSlotType(userId, slotType.uppercase())
        if (existing != null) {
            backpackRepo.save(
                BackpackItemEntity(
                    userId = userId,
                    itemType = "CORE",
                    qualityOrdinal = existing.rarityOrdinal,
                    coreName = existing.coreName,
                    coreValue = existing.coreValue,
                    coreLevel = existing.coreLevel
                )
            )
            equippedCoreRepo.delete(existing)
        }

        equippedCoreRepo.save(
            EquippedCore(
                userId = userId,
                slotType = slotType.uppercase(),
                coreId = core.id,
                rarityOrdinal = core.qualityOrdinal,
                coreName = core.coreName ?: "",
                coreValue = core.coreValue ?: 0,
                coreLevel = core.coreLevel
            )
        )
        backpackRepo.delete(core)
        return true
    }

    @Transactional
    fun unequipCore(userId: Long, slotType: String): Boolean {
        if (!setOf("LEFT", "RIGHT").contains(slotType.uppercase())) return false
        val equipped = equippedCoreRepo.findByUserIdAndSlotType(userId, slotType.uppercase()) ?: return false

        backpackRepo.save(
            BackpackItemEntity(
                userId = userId,
                itemType = "CORE",
                qualityOrdinal = equipped.rarityOrdinal,
                coreName = equipped.coreName,
                coreValue = equipped.coreValue,
                coreLevel = equipped.coreLevel
            )
        )
        equippedCoreRepo.delete(equipped)
        return true
    }

    @Transactional
    fun sellBackpackItem(userId: Long, itemIndex: Int): Boolean {
        val profile = getProfile(userId)
        val items = backpackRepo.findByUserId(userId).sortedBy { it.createdAt }
        if (itemIndex < 0 || itemIndex >= items.size) return false
        val item = items[itemIndex]
        if (item.locked) return false
        // 计算售价
        val sellPrice = 100L + item.qualityOrdinal * 50L
        profile.gold += sellPrice
        backpackRepo.delete(item)
        profileRepo.save(profile)
        return true
    }

    @Transactional
    fun expandBackpack(userId: Long): Boolean {
        val profile = getProfile(userId)
        val cost = 1000L + profile.backpackCapacity * 100L
        if (profile.gold < cost) return false
        profile.gold -= cost
        profile.backpackCapacity += 5
        profileRepo.save(profile)
        return true
    }
}
