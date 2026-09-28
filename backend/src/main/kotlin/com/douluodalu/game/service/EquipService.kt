package com.douluodalu.game.service

import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.entity.EquippedBone
import com.douluodalu.game.entity.EquippedCore
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.exception.PlayerSaveNotFoundException
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.EquippedRingRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 装备域服务（原 GameService 装备区块拆出）：魂环/魂骨/魂核的装备、卸下与魂骨强化。
 *
 * 拆分口径：方法体自 GameService 逐行搬移，事务边界不变——每个方法仍挂独立
 * `@Transactional`（REQUIRED，与拆分前在同一 Spring 代理链路上的语义一致：
 * GameService 门面方法先开事务 → 本服务方法加入同一事务；直连本服务则由本服务开事务）。
 * 乐观锁 409、扣费、日志、成就挂点等外部行为与拆分前完全一致。
 * GameService 保留同名门面方法委托至此，保证 EquipmentController 与既有测试的调用面不变。
 */
@Service
class EquipService(
    private val profileRepo: PlayerProfileRepository,
    private val backpackRepo: BackpackItemRepository,
    private val equippedRingRepo: EquippedRingRepository,
    private val equippedBoneRepo: EquippedBoneRepository,
    private val equippedCoreRepo: EquippedCoreRepository,
    private val achievementService: AchievementService
) {

    /**
     * 魂骨强化结果（EquipService.enhanceBone，第二十七轮）。success=true → Controller 200
     * {"message": message}；false → 400 {"error": message}（照 EquipmentController 既有
     * Boolean→200/400 惯例升级为消息对）。enhanceLevel/goldSpent 为成功后的实际值（失败恒 0），
     * 供 Controller 组装消息与测试断言。
     */
    data class EnhanceResult(
        val success: Boolean,
        val enhanceLevel: Int = 0,
        val goldSpent: Long = 0,
        val message: String = ""
    )

    private fun getProfile(userId: Long): PlayerProfileEntity {
        return profileRepo.findByUserId(userId)
            ?: throw PlayerSaveNotFoundException()
    }

    // ======== 装备操作 ========
    /**
     * 装备魂环（任务#21：接入 shared 同源的负荷校验）。
     * 超负荷时抛 IllegalArgumentException → GlobalExceptionHandler 输出 400 {error,message}。
     */
    @Transactional
    fun equipRing(userId: Long, slotIndex: Int, ringIndex: Int): Boolean {
        if (slotIndex < 0 || slotIndex > 8) return false // 9个槽位

        // 获取背包中所有魂环（按创建时间排序）
        val rings = backpackRepo.findByUserIdAndItemType(userId, "RING").sortedBy { it.createdAt }
        if (ringIndex < 0 || ringIndex >= rings.size) return false
        val ring = rings[ringIndex]

        // 检查槽位是否已被占用，如果有则卸下原有魂环
        val existing = equippedRingRepo.findByUserIdAndSlotIndex(userId, slotIndex)

        // 吸收容量检测（shared GameEngine.kt:1851~1861 同构）：换装时旧环负荷先释放，再叠加新环
        val profile = getProfile(userId)
        val equipped = equippedRingRepo.findByUserId(userId)
        val newLoad = RingLoadCalculator.ringLoad(ring.yearOrdinal, ring.qualityOrdinal, ring.percentage)
        val loadAfterEquip = RingLoadCalculator.totalRingLoad(equipped.filterNot { it.slotIndex == slotIndex }) + newLoad
        val totalLoadBefore = RingLoadCalculator.totalRingLoad(equipped)
        val bonus = EquipmentPowerService.bonus(
            profile.level, equipped,
            equippedBoneRepo.findByUserId(userId), equippedCoreRepo.findByUserId(userId)
        )
        val capacity = RingLoadCalculator.absorptionCapacityFor(profile, bonus)
        if (loadAfterEquip > capacity) {
            val overload = loadAfterEquip - capacity
            throw IllegalArgumentException(
                "负荷不足！当前负荷 $totalLoadBefore/$capacity，该魂环需负荷 $newLoad，还需 $overload 才可吸收"
            )
        }

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
        // 成就挂点（副路径）：SOUL_RING 口径 = 已装备魂环数，成功装环后同步（失败不击穿主流程）。
        // 注：上方容量校验按装备口径（companion bonus，不含成就加成）——校验保守方向，
        // 差异带 ≤ 成就 hp/atk 折算的容量增量。
        achievementService.sync(userId)
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
                    enhanceLevel = existing.enhanceLevel,
                    // 第二十八轮：被换下的骨词缀随件回背包（换装不丢词缀）
                    affixesJson = existing.affixesJson
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
                enhanceLevel = bone.enhanceLevel,
                // 第二十八轮：equip 属性拷贝模式随件搬运——背包行词缀拷入 equipped 行（V12 注释）
                affixesJson = bone.affixesJson
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
                enhanceLevel = equipped.enhanceLevel,
                // 第二十八轮：unequip 重建背包行时词缀带回（卸装不丢词缀）
                affixesJson = equipped.affixesJson
            )
        )
        equippedBoneRepo.delete(equipped)
        return true
    }

    // ======== 魂骨强化（第二十七轮：主动强化端点） ========
    /**
     * 强化魂骨（双路径二选一）：itemIndex = 背包 BONE 列表索引（与 sell/equip 同口径：按创建时间
     * 排序）、slotIndex = 已装备骨槽位 0-5。恰好一个非空（都空/都非空 → 失败）。
     * 校验顺序：路径参数二选一 → 索引存在 → 上限（≥ BONE_ENHANCE_MAX_LEVEL 拒绝）→ 金币 →
     * 扣金 → enhanceLevel+1 → save（@Transactional；失败出口零写库）。
     * 费用走 GameBalance.boneEnhanceCost（公式与经济锚点见该函数注释）；100% 成功无失败机制；
     * 属性/战力经 EquipmentPowerService.boneMult 的强化乘区全自动生效（本方法只改 enhanceLevel）。
     * ring/core 明确不做强化（GameBalance 魂骨强化区块注释留档）；背包路径允许强化 locked 件——
     * 强化不消耗/不移动物品，锁只保护「不被卖出」（sellBackpackItem）语义。
     */
    @Transactional
    fun enhanceBone(userId: Long, itemIndex: Int?, slotIndex: Int?): EnhanceResult {
        if ((itemIndex == null) == (slotIndex == null)) {
            return EnhanceResult(success = false, message = "参数无效：itemIndex 与 slotIndex 二选一")
        }
        val profile = getProfile(userId)
        // 双路径解析：恰好一个分支命中。snapshot = (yearOrdinal, qualityOrdinal, currentLevel)，
        // persist = 强化 +1 后的持久化动作（两实体无公共接口，用闭包收敛到同一校验/扣费尾部）
        val snapshot: Triple<Int, Int, Int>
        val persist: (Int) -> Unit
        if (itemIndex != null) {
            val bones = backpackRepo.findByUserIdAndItemType(userId, "BONE").sortedBy { it.createdAt }
            if (itemIndex < 0 || itemIndex >= bones.size) {
                return EnhanceResult(success = false, message = "魂骨索引越界")
            }
            val item = bones[itemIndex]
            snapshot = Triple(item.yearOrdinal, item.qualityOrdinal, item.enhanceLevel)
            persist = { newLevel ->
                item.enhanceLevel = newLevel
                backpackRepo.save(item)
            }
        } else {
            val slot = slotIndex!!
            if (slot < 0 || slot > 5) {
                return EnhanceResult(success = false, message = "魂骨槽位越界（0-5）")
            }
            val equipped = equippedBoneRepo.findByUserIdAndSlotIndex(userId, slot)
                ?: return EnhanceResult(success = false, message = "该槽位没有装备魂骨")
            snapshot = Triple(equipped.yearOrdinal, equipped.qualityOrdinal, equipped.enhanceLevel)
            persist = { newLevel ->
                equipped.enhanceLevel = newLevel
                equippedBoneRepo.save(equipped)
            }
        }
        val (yearOrdinal, qualityOrdinal, currentLevel) = snapshot
        if (currentLevel >= GameBalance.BONE_ENHANCE_MAX_LEVEL) {
            return EnhanceResult(
                success = false,
                message = "已达强化上限（+${GameBalance.BONE_ENHANCE_MAX_LEVEL}）"
            )
        }
        val cost = GameBalance.boneEnhanceCost(yearOrdinal, qualityOrdinal, currentLevel)
        if (profile.gold < cost) {
            return EnhanceResult(success = false, message = "金币不足（需要 $cost）")
        }
        profile.gold -= cost
        val newLevel = currentLevel + 1
        persist(newLevel)
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        return EnhanceResult(
            success = true, enhanceLevel = newLevel, goldSpent = cost,
            message = "强化成功！魂骨强化等级 +1（当前 +$newLevel，花费 $cost 金币）"
        )
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
}
