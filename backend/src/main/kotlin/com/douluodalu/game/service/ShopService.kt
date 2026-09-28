package com.douluodalu.game.service

import com.douluodalu.game.controller.ShopResult
import com.douluodalu.game.dto.KillingAttrDto
import com.douluodalu.game.dto.KillingBuyResponse
import com.douluodalu.game.dto.KillingShopDto
import com.douluodalu.game.dto.KillingTitleDto
import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.entity.UserTitleEntity
import com.douluodalu.game.model.*
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.ShopPurchaseRecordRepository
import com.douluodalu.game.repository.UserRepository
import com.douluodalu.game.repository.UserTitleRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 商店服务：金币/Boss币商品购买（buyItem/buyLimitedItem）+ 杀气商店（第二十九轮）。
 *
 * 杀气商店（设计文档 §7.4）：塔胜利产出的杀气 killingIntent 的消耗出口——
 *  - 称号兑换（buyKillingTitle）：8 个永久称号，购买即永久拥有（user_title 落库），
 *    全部已拥有称号属性叠加生效（不做「佩戴唯一」，与成就自动生效模型一致）；已拥有
 *    不可回购。加成经 EquipmentPowerService.killingBonus 并入 bonusFor/getGameState 自动生效。
 *  - 属性购买（buyKillingAttr）：HP/ATK 两商品各自独立计数（player_profile.killing_hp_buys/
 *    killing_atk_buys），价格 = 100×2^已购次数（GameBalance.killingAttrCost），无限次购买。
 * 业务错误（杀气不足/已拥有/未知称号/非法 stat）抛 IllegalArgumentException → 全局 400；
 * 扣杀气、加计数、写 user_title 同一事务（@Transactional）。
 */
@Service
class ShopService(
    private val userRepository: UserRepository,
    private val gameService: GameService,
    private val backpackItemRepository: BackpackItemRepository,
    private val purchaseRecordRepository: ShopPurchaseRecordRepository,
    private val dailyQuestService: DailyQuestService,
    /** 第二十九轮杀气商店：称号拥有记录（回购判定 / 并发唯一键兜底 / 面板拥有态） */
    private val userTitleRepository: UserTitleRepository,
    /** 业务计数器（Micrometer，Spring Boot 自动配置 bean）；测试注入 SimpleMeterRegistry */
    private val meterRegistry: MeterRegistry
) {
    companion object {
        /** 会发放新背包物品的商品类型，购买/掉落前需校验背包容量 */
        val BACKPACK_GRANT_TYPES = setOf("RING_BOX", "BONE_BOX", "CORE_BOX")

        /** grantRingBox / grantBoneBox 支持的年份档位 */
        val BOX_TIERS = setOf("HUNDRED", "THOUSAND", "TEN_THOUSAND", "HUNDRED_THOUSAND", "MILLION")

        // ===== 业务计数器名（第二十九轮，照 round 21 GameService METRIC_* 先例；逐字契约，勿改）=====
        const val METRIC_KILLING_TITLE_TOTAL = "douluo.killing.title.total"
        const val METRIC_KILLING_ATTR_TOTAL = "douluo.killing.attr.total"

        /** 杀气属性购买的合法 stat 集合（非法 stat 抛 IllegalArgumentException → 400） */
        val KILLING_ATTR_STATS = setOf("hp", "atk")

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

    // ==================== 杀气商店（第二十九轮） ====================

    /**
     * 杀气商店面板（只读）：8 称号的拥有/价格/属性预览 + HP/ATK 两商品当前价/已购次数。
     * 定义表全量下发（GameBalance.KILLING_TITLES 唯一数值源），owned 由 user_title 反查；
     * nextCost = GameBalance.killingAttrCost(buys)（前后端镜像同源，前端 lib/killing.ts 钉契约）。
     */
    @Transactional(readOnly = true)
    fun getKillingShop(userId: Long): KillingShopDto {
        val (_, player) = killingPlayer(userId)
        val owned = userTitleRepository.findByUserId(userId).map { it.titleId }.toSet()
        return KillingShopDto(
            killingIntent = player.killingIntent,
            titles = GameBalance.KILLING_TITLES.map {
                KillingTitleDto(
                    id = it.id, name = it.name, cost = it.cost,
                    hp = it.hp, atk = it.atk, pdef = it.pdef, critRate = it.critRate, critDmg = it.critDmg,
                    owned = it.id in owned
                )
            },
            attrs = listOf(
                KillingAttrDto(
                    stat = "hp", displayName = "生命提升",
                    effectPerBuy = GameBalance.KILLING_ATTR_HP_PER_BUY,
                    buys = player.killingHpBuys, nextCost = GameBalance.killingAttrCost(player.killingHpBuys)
                ),
                KillingAttrDto(
                    stat = "atk", displayName = "攻击提升",
                    effectPerBuy = GameBalance.KILLING_ATTR_ATK_PER_BUY,
                    buys = player.killingAtkBuys, nextCost = GameBalance.killingAttrCost(player.killingAtkBuys)
                )
            )
        )
    }

    /**
     * 称号兑换：扣杀气 + 写 user_title 同一事务。购买即永久拥有（无佩戴列），
     * 属性经 EquipmentPowerService.killingBonus 并入 bonusFor/getGameState 自动生效。
     * 校验顺序：未知称号 → 已拥有（回购拒绝）→ 杀气不足；业务错误抛 IllegalArgumentException
     * → 全局 400。并发双买由 V14 联合 PK 兜底：saveAndFlush 撞约束捕获后重查确认，
     * 按业务错误拒绝（已扣杀气随事务回滚，玩家不损失货币）——照 achievement_record
     * uk_user_achievement 的并发写法先例。
     * 注：不计入 shop_buy 每日任务（recordShopBuy 只挂 buyItem 主路径，杀气商店非金币/Boss币
     * 商品，任务口径不变）。
     */
    @Transactional
    fun buyKillingTitle(userId: Long, titleId: String): KillingBuyResponse {
        val def = GameBalance.KILLING_TITLE_BY_ID[titleId]
            ?: throw IllegalArgumentException("未知称号：$titleId")
        val (user, player) = killingPlayer(userId)
        if (userTitleRepository.existsByUserIdAndTitleId(userId, def.id)) {
            throw IllegalArgumentException("已拥有称号「${def.name}」，无需重复兑换")
        }
        if (player.killingIntent < def.cost) {
            throw IllegalArgumentException("杀气不足，需要${def.cost}（当前${player.killingIntent}）")
        }
        // 余额已校验 ≥ cost（cost ≤ 150000 恒在 Int 域内），killingIntent 为 Int 列故截回 Int 安全
        player.killingIntent = (player.killingIntent - def.cost).toInt()
        try {
            // saveAndFlush：把唯一约束冲突逼到本方法内抛出，捕获路径才能给出业务语义的 400
            userTitleRepository.saveAndFlush(UserTitleEntity(userId = userId, titleId = def.id))
        } catch (e: DataIntegrityViolationException) {
            // 并发双买竞态：联合 PK 兜底——重查确认已拥有则按业务错误拒绝（事务回滚扣款）；
            // 非重复键冲突（真脏数据）原样上抛走 500 兜底
            if (userTitleRepository.existsByUserIdAndTitleId(userId, def.id)) {
                throw IllegalArgumentException("已拥有称号「${def.name}」，无需重复兑换")
            }
            throw e
        }
        player.updatedAt = LocalDateTime.now()
        userRepository.save(user)
        // 业务计数器：称号兑换成功（Micrometer 不抛业务异常）
        counter(METRIC_KILLING_TITLE_TOTAL)
        return KillingBuyResponse(
            message = "称号「${def.name}」兑换成功！属性已永久生效",
            killingIntent = player.killingIntent,
            titleId = def.id
        )
    }

    /**
     * 属性购买：HP/ATK 两商品各自独立计数，价格 = 100×2^已购次数（GameBalance.killingAttrCost），
     * 效果 +100 HP / +10 ATK 每次（基值固定不随等级缩放，照文档 §7.4）。无限次购买；
     * 加成经 killingAttrBonus 并入 bonusFor 自动生效。扣杀气与加计数同一事务：
     * player_profile 的 @Version 乐观锁兜并发读改写（撞锁 409 重试）。
     */
    @Transactional
    fun buyKillingAttr(userId: Long, stat: String): KillingBuyResponse {
        val (user, player) = killingPlayer(userId)
        if (stat !in KILLING_ATTR_STATS) {
            throw IllegalArgumentException("无效的属性类型：$stat（须为 hp 或 atk）")
        }
        val buys = if (stat == "hp") player.killingHpBuys else player.killingAtkBuys
        val effect = if (stat == "hp") GameBalance.KILLING_ATTR_HP_PER_BUY else GameBalance.KILLING_ATTR_ATK_PER_BUY
        val cost = GameBalance.killingAttrCost(buys)
        if (player.killingIntent < cost) {
            throw IllegalArgumentException("杀气不足，需要$cost（当前${player.killingIntent}）")
        }
        // 余额已校验 ≥ cost（killingIntent 为 Int 列故差值截回 Int 安全）
        player.killingIntent = (player.killingIntent - cost).toInt()
        if (stat == "hp") player.killingHpBuys += 1 else player.killingAtkBuys += 1
        player.updatedAt = LocalDateTime.now()
        userRepository.save(user)
        // 业务计数器：属性购买成功（stat 区分 hp/atk，照 round 21 tag 先例）
        counter(METRIC_KILLING_ATTR_TOTAL, "stat", stat)
        return KillingBuyResponse(
            message = "${if (stat == "hp") "生命" else "攻击"}提升成功！+$effect（已购 ${buys + 1} 次）",
            killingIntent = player.killingIntent,
            stat = stat,
            buys = buys + 1
        )
    }

    /**
     * 杀气商店共用的存档取值（user.player 与 buyItem 同款读写路径；用户/玩家缺失属于
     * 鉴权内的脏状态，抛 IllegalArgumentException → 400）。
     */
    private fun killingPlayer(userId: Long): Pair<UserEntity, com.douluodalu.game.entity.PlayerProfileEntity> {
        val user = userRepository.findById(userId).orElse(null)
            ?: throw IllegalArgumentException("用户不存在")
        val player = user.player ?: throw IllegalArgumentException("玩家数据不存在")
        return user to player
    }

    /** 业务计数器（照 GameService.counter 先例：Micrometer 计数是内存操作，不影响主流程） */
    private fun counter(name: String, vararg tags: String) {
        meterRegistry.counter(name, *tags).increment()
    }
}
