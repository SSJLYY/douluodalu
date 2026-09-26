package com.douluodalu.game.service

import com.douluodalu.game.controller.ShopResult
import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.model.*
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.ShopPurchaseRecordRepository
import com.douluodalu.game.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

@Service
class ShopService(
    private val userRepository: UserRepository,
    private val gameService: GameService,
    private val backpackItemRepository: BackpackItemRepository,
    private val purchaseRecordRepository: ShopPurchaseRecordRepository,
    private val dailyQuestService: DailyQuestService
) {
    companion object {
        /** 会发放新背包物品的商品类型，购买/掉落前需校验背包容量 */
        val BACKPACK_GRANT_TYPES = setOf("RING_BOX", "BONE_BOX", "CORE_BOX")

        /** grantRingBox / grantBoneBox 支持的年份档位 */
        val BOX_TIERS = setOf("HUNDRED", "THOUSAND", "TEN_THOUSAND", "HUNDRED_THOUSAND", "MILLION")

        /**
         * 奖励能否真正发放的预检（扣款前调用）。
         * 历史上校验发生在扣款之后：未实现的 itemType（如限量商店在售的
         * GIFT_PACK"神赐礼包"）、未知箱类档位、非法/负数 itemData 都会走
         * `return ShopResult(false)` 早退——但托管实体的扣款没有异常触发
         * 回滚，照样随事务提交，造成"扣钱不发货"甚至"扣钱报成功"。
         */
        fun rewardValidationError(item: ShopItem): String? = when (item.itemType) {
            "RING_BOX", "BONE_BOX" ->
                if (item.itemData in BOX_TIERS) null else "商品数据格式错误"
            "CORE_BOX" ->
                if (SoulCoreTier.entries.any { it.name == item.itemData }) null else "商品数据格式错误"
            "GOLD_BAG", "SOUL_POWER" ->
                if ((item.itemData.toLongOrNull() ?: -1L) >= 0L) null else "商品数据格式错误"
            "BACKPACK_EXPAND" ->
                if ((item.itemData.toIntOrNull() ?: -1) >= 0) null else "商品数据格式错误"
            else -> "商品暂不可用：${item.itemType} 奖励尚未实现"
        }
    }

    @Transactional
    fun buyItem(userId: Long, item: ShopItem): ShopResult {
        val user = userRepository.findById(userId).orElse(null)
            ?: return ShopResult(false, error = "用户不存在", item = mapOf("id" to item.id, "name" to item.name))

        val player = user.player
            ?: return ShopResult(false, error = "玩家数据不存在", item = mapOf("id" to item.id, "name" to item.name))

        // 先检查等级，避免等级不足时已扣款（早退不触发回滚，托管实体的扣款会被提交）
        if (player.level < item.requiresLevel) {
            return ShopResult(false, error = "等级不足，需要${item.requiresLevel}级", item = mapOf("id" to item.id, "name" to item.name))
        }

        // 奖励可发放性同样必须在扣款前预检（同"先扣钱后校验"历史教训）
        rewardValidationError(item)?.let { err ->
            return ShopResult(false, error = err, item = mapOf("id" to item.id, "name" to item.name))
        }

        // 开箱类商品会发放背包物品：在扣款前校验背包容量，满包整单失败（不扣款、无需退款）
        if (item.itemType in BACKPACK_GRANT_TYPES && !hasBackpackSpace(player)) {
            return ShopResult(false, error = "背包已满，请先整理背包", item = mapOf("id" to item.id, "name" to item.name))
        }

        // 检查货币
        when (item.currencyType) {
            "BOSS_COIN" -> {
                if (player.bossCoin < item.price) {
                    return ShopResult(false, error = "Boss币不足", item = mapOf("id" to item.id, "name" to item.name))
                }
                player.bossCoin -= item.price
                if (player.bossCoin < 0) return ShopResult(false, error = "余额不足", item = mapOf("id" to item.id, "name" to item.name))
            }
            "GOLD" -> {
                if (player.gold < item.price) {
                    return ShopResult(false, error = "金币不足", item = mapOf("id" to item.id, "name" to item.name))
                }
                player.gold -= item.price
                if (player.gold < 0) return ShopResult(false, error = "余额不足", item = mapOf("id" to item.id, "name" to item.name))
            }
            else -> return ShopResult(false, error = "无效的货币类型", item = mapOf("id" to item.id, "name" to item.name))
        }

        // 发放物品
        val reward = when (item.itemType) {
            "RING_BOX" -> grantRingBox(player, item.itemData)
            "BONE_BOX" -> grantBoneBox(player, item.itemData)
            "CORE_BOX" -> grantCoreBox(player, item.itemData)
            "GOLD_BAG" -> {
                val amount = item.itemData.toLongOrNull()
                    ?: return ShopResult(false, error = "商品数据格式错误", item = mapOf("id" to item.id, "name" to item.name))
                player.gold += amount
                "获得${amount}金币"
            }
            "SOUL_POWER" -> {
                val amount = item.itemData.toLongOrNull()
                    ?: return ShopResult(false, error = "商品数据格式错误", item = mapOf("id" to item.id, "name" to item.name))
                player.soulPower += amount
                "获得${amount}魂力"
            }
            "BACKPACK_EXPAND" -> {
                val amount = item.itemData.toIntOrNull()
                    ?: return ShopResult(false, error = "商品数据格式错误", item = mapOf("id" to item.id, "name" to item.name))
                player.backpackCapacity += amount
                "背包容量+${amount}"
            }
            else -> return ShopResult(false, error = "无效的商品类型", item = mapOf("id" to item.id, "name" to item.name))
        }

        // 更新购买记录
        val existingRecord = purchaseRecordRepository.findByUserIdAndItemId(userId, item.id)
        if (existingRecord != null) {
            existingRecord.purchaseCount += 1
            existingRecord.lastPurchaseAt = java.time.LocalDateTime.now()
            purchaseRecordRepository.save(existingRecord)
        } else {
            val newRecord = com.douluodalu.game.entity.ShopPurchaseRecord()
            newRecord.userId = userId
            newRecord.itemId = item.id
            newRecord.purchaseCount = 1
            purchaseRecordRepository.save(newRecord)
        }

        userRepository.save(user)
        // 每日任务挂点（副路径）：唯一成功出口，一处覆盖普通/Boss/限量/宗门 4 个商店入口
        // （buyLimitedItem 也委托 buyItem）；失败不击穿购物主流程（见 DailyQuestService）
        dailyQuestService.recordShopBuy(userId)
        return ShopResult(true, item = mapOf("id" to item.id, "name" to item.name, "reward" to reward))
    }

    @Transactional
    fun buyLimitedItem(userId: Long, item: ShopItem): ShopResult {
        // 检查限购
        if (item.stock > 0) {
            val record = purchaseRecordRepository.findByUserIdAndItemId(userId, item.id)
            val purchased = record?.purchaseCount ?: 0
            if (purchased >= item.stock) {
                return ShopResult(false, error = "该商品已售罄")
            }
        }
        return buyItem(userId, item)
    }

    /**
     * 背包是否还有空间放入一件新物品。所有发放背包物品的商品
     * （魂环箱/魂骨箱/魂核箱）在扣款前统一用此方法校验。
     */
    private fun hasBackpackSpace(player: com.douluodalu.game.entity.PlayerProfileEntity): Boolean {
        return backpackItemRepository.countByUserId(player.userId) < player.backpackCapacity
    }

    private fun grantRingBox(player: com.douluodalu.game.entity.PlayerProfileEntity, tier: String): String {
        val yearOrdinal = when (tier) {
            "HUNDRED" -> 0
            "THOUSAND" -> 1
            "TEN_THOUSAND" -> 2
            "HUNDRED_THOUSAND" -> 3
            "MILLION" -> 4
            else -> return "获得百年魂环(异常数据)"
        }
        // 品质保底：至少精良
        val qualityOrdinal = Random.nextInt(1, 5)
        val percentage = Random.nextInt(100, 1000)
        val ringName = RingQuality.fullName(yearOrdinal, qualityOrdinal)

        val item = BackpackItemEntity(
            userId = player.userId,
            itemType = "RING",
            yearOrdinal = yearOrdinal,
            qualityOrdinal = qualityOrdinal,
            percentage = percentage
        )
        backpackItemRepository.save(item)
        return "获得${ringName}(年分数${percentage / 10}.${percentage % 10}%)"
    }

    private fun grantBoneBox(player: com.douluodalu.game.entity.PlayerProfileEntity, tier: String): String {
        val yearOrdinal = when (tier) {
            "HUNDRED" -> 0
            "THOUSAND" -> 1
            "TEN_THOUSAND" -> 2
            "HUNDRED_THOUSAND" -> 3
            "MILLION" -> 4
            else -> return "获得百年魂骨(异常数据)"
        }
        // 品质保底：至少精良
        val qualityOrdinal = Random.nextInt(1, 5)
        val boneTypeOrdinal = Random.nextInt(0, 6)
        val boneName = BoneRarity.fullName(yearOrdinal, qualityOrdinal)
        val typeName = BoneType.entries[boneTypeOrdinal].displayName

        val item = BackpackItemEntity(
            userId = player.userId,
            itemType = "BONE",
            yearOrdinal = yearOrdinal,
            qualityOrdinal = qualityOrdinal,
            boneTypeOrdinal = boneTypeOrdinal
        )
        backpackItemRepository.save(item)
        return "获得${typeName}${boneName}"
    }

    private fun grantCoreBox(player: com.douluodalu.game.entity.PlayerProfileEntity, tier: String): String {
        val coreTier = try {
            SoulCoreTier.valueOf(tier)
        } catch (e: Exception) {
            return "获得普通魂核(异常数据)"
        }

        val rarityOrdinal = coreTier.ordinal
        val coreName = coreTier.displayName + "魂核"
        val value = 50 + rarityOrdinal * 30 + Random.nextInt(5, 20)

        val item = BackpackItemEntity(
            userId = player.userId,
            itemType = "CORE",
            yearOrdinal = 0,
            qualityOrdinal = rarityOrdinal,
            coreName = coreName,
            coreValue = value
        )
        backpackItemRepository.save(item)
        return "获得${coreTier.displayName}魂核"
    }
}
