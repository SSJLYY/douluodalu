package com.douluodalu.game.service

import com.douluodalu.game.controller.ShopResult
import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.model.*
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

@Service
class ShopService(
    private val userRepository: UserRepository,
    private val gameService: GameService,
    private val backpackItemRepository: BackpackItemRepository
) {
    @Transactional
    fun buyItem(userId: Long, item: ShopItem): ShopResult {
        val user = userRepository.findById(userId).orElse(null)
            ?: return ShopResult(false, error = "用户不存在", item = mapOf("id" to item.id, "name" to item.name))

        val player = user.player
            ?: return ShopResult(false, error = "玩家数据不存在", item = mapOf("id" to item.id, "name" to item.name))

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

        // 检查等级
        if (player.level < item.requiresLevel) {
            return ShopResult(false, error = "等级不足，需要${item.requiresLevel}级", item = mapOf("id" to item.id, "name" to item.name))
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
        val existingRecord = backpackItemRepository.findShopPurchaseRecordByUserIdAndItemId(userId, item.id)
        if (existingRecord != null) {
            existingRecord.purchaseCount += 1
        } else {
            val newRecord = com.douluodalu.game.entity.ShopPurchaseRecord()
            newRecord.userId = userId
            newRecord.itemId = item.id
            newRecord.purchaseCount = 1
            backpackItemRepository.save(newRecord)
        }

        userRepository.save(user)
        return ShopResult(true, reward, item = mapOf("id" to item.id, "name" to item.name, "reward" to reward))
    }

    @Transactional
    fun buyLimitedItem(userId: Long, item: ShopItem): ShopResult {
        // 检查限购
        if (item.stock > 0) {
            val record = backpackItemRepository.findShopPurchaseRecordByUserIdAndItemId(userId, item.id)
            val purchased = record?.purchaseCount ?: 0
            if (purchased >= item.stock) {
                return ShopResult(false, error = "该商品已售罄")
            }
        }
        return buyItem(userId, item)
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
