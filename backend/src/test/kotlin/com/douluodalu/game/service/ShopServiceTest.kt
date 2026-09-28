package com.douluodalu.game.service

import com.douluodalu.game.controller.ShopResult
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.ShopPurchaseRecord
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.model.*
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.ShopPurchaseRecordRepository
import com.douluodalu.game.repository.UserRepository
import com.douluodalu.game.repository.UserTitleRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Spy
import org.mockito.kotlin.*
import com.douluodalu.game.entity.BackpackItemEntity
import java.util.Optional

class ShopServiceTest {
    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var gameService: GameService

    @Mock
    private lateinit var backpackItemRepository: BackpackItemRepository

    @Mock
    private lateinit var purchaseRecordRepository: ShopPurchaseRecordRepository

    @Mock
    private lateinit var dailyQuestService: DailyQuestService

    /** 第二十九轮杀气商店：@InjectMocks 补齐新构造依赖（既有用例不触碰，默认空表/空注册表） */
    @Mock
    private lateinit var userTitleRepository: UserTitleRepository

    @Spy
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry()

    @Captor
    private lateinit var itemCaptor: ArgumentCaptor<BackpackItemEntity>

    @InjectMocks
    private lateinit var shopService: ShopService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doAnswer { inv -> (inv.arguments[0] as BackpackItemEntity) }
            .whenever(backpackItemRepository).save(any())
    }

    private fun userWith(gold: Long = 0, bossCoin: Long = 0, level: Int = 10, soulPower: Long = 0, capacity: Int = 20): Pair<UserEntity, PlayerProfileEntity> {
        val profile = PlayerProfileEntity(userId = 1L, level = level)
        profile.gold = gold
        profile.bossCoin = bossCoin
        profile.soulPower = soulPower
        profile.backpackCapacity = capacity
        val user = UserEntity(id = 1, username = "u", nickname = "n", passwordHash = "h")
        user.player = profile
        doReturn(Optional.of(user)).whenever(userRepository).findById(1L)
        return user to profile
    }

    @Test
    fun `buyItem should reject insufficient gold without deducting or saving`() {
        val (user, profile) = userWith(gold = 100)

        val result = shopService.buyItem(1L, ShopItem(204, "魂力精华(小)", "", 300, "GOLD", "SOUL_POWER", "500"))

        assertFalse(result.success)
        assertEquals("金币不足", result.error)
        assertEquals(100L, profile.gold)
        verify(userRepository, never()).save(any())
        verify(purchaseRecordRepository, never()).save(any())
        verify(backpackItemRepository, never()).save(any())
    }

    @Test
    fun `buyItem SOUL_POWER should deduct gold and grant soul power`() {
        val (user, profile) = userWith(gold = 1000, soulPower = 50)
        doReturn(null).whenever(purchaseRecordRepository).findByUserIdAndItemId(1L, 204L)

        val result = shopService.buyItem(1L, ShopItem(204, "魂力精华(小)", "", 300, "GOLD", "SOUL_POWER", "500"))

        assertTrue(result.success)
        assertEquals(700L, profile.gold)
        assertEquals(550L, profile.soulPower)
        verify(userRepository).save(user)
        // 首次购买：新记录 purchaseCount=1
        val captor = ArgumentCaptor.forClass(ShopPurchaseRecord::class.java)
        verify(purchaseRecordRepository).save(captor.capture())
        assertEquals(1, captor.value.purchaseCount)
        assertEquals(204L, captor.value.itemId)
    }

    @Test
    fun `buyItem BACKPACK_EXPAND should increase capacity and accumulate purchase count`() {
        val (user, profile) = userWith(gold = 5000, capacity = 20)
        val existing = ShopPurchaseRecord(userId = 1L, itemId = 205L, purchaseCount = 3)
        doReturn(existing).whenever(purchaseRecordRepository).findByUserIdAndItemId(1L, 205L)

        val result = shopService.buyItem(1L, ShopItem(205, "背包扩展券", "", 3000, "GOLD", "BACKPACK_EXPAND", "5"))

        assertTrue(result.success)
        assertEquals(25, profile.backpackCapacity)
        assertEquals(2000L, profile.gold)
        verify(purchaseRecordRepository).save(existing)
        assertEquals(4, existing.purchaseCount)
    }

    @Test
    fun `buyItem RING_BOX should persist a backpack ring item via repository save`() {
        val (user, profile) = userWith(bossCoin = 100)
        doReturn(null).whenever(purchaseRecordRepository).findByUserIdAndItemId(1L, 1L)

        val result = shopService.buyItem(1L, ShopItem(1, "万年魂环箱", "", 50, "BOSS_COIN", "RING_BOX", "TEN_THOUSAND"))

        assertTrue(result.success)
        assertEquals(50L, profile.bossCoin)
        verify(backpackItemRepository).save(itemCaptor.capture())
        val saved = itemCaptor.value
        assertEquals("RING", saved.itemType)
        assertEquals(1L, saved.userId)
        assertEquals(2, saved.yearOrdinal) // TEN_THOUSAND → ordinal 2
        // 品质保底随机 [1,5)，年分数随机 [100,1000)：只断言区间，不断言具体值
        assertTrue(saved.qualityOrdinal in 1..4, "quality=${saved.qualityOrdinal} 应在 1..4")
        assertTrue(saved.percentage in 100..999, "percentage=${saved.percentage} 应在 100..999")
    }

    @Test
    fun `buyItem RING_BOX should fail whole purchase without deduction when backpack is full`() {
        val (user, profile) = userWith(bossCoin = 100, capacity = 20)
        doReturn(20L).whenever(backpackItemRepository).countByUserId(1L)

        val result = shopService.buyItem(1L, ShopItem(1, "万年魂环箱", "", 50, "BOSS_COIN", "RING_BOX", "TEN_THOUSAND"))

        assertFalse(result.success)
        assertEquals("背包已满，请先整理背包", result.error)
        // 扣款前拦截：Boss 币不减少，不落任何写
        assertEquals(100L, profile.bossCoin)
        verify(backpackItemRepository, never()).save(any())
        verify(userRepository, never()).save(any())
        verify(purchaseRecordRepository, never()).save(any())
    }

    @Test
    fun `buyItem BONE_BOX should fail when backpack is full and pass when space remains`() {
        val (user, profile) = userWith(gold = 1000, capacity = 10)
        doReturn(10L).whenever(backpackItemRepository).countByUserId(1L)

        val full = shopService.buyItem(1L, ShopItem(203, "百年魂骨箱", "", 800, "GOLD", "BONE_BOX", "HUNDRED"))
        assertFalse(full.success)
        assertEquals("背包已满，请先整理背包", full.error)
        assertEquals(1000L, profile.gold)
        verify(backpackItemRepository, never()).save(any())

        // 腾出空间后同一商品可正常购买
        doReturn(3L).whenever(backpackItemRepository).countByUserId(1L)
        doReturn(null).whenever(purchaseRecordRepository).findByUserIdAndItemId(1L, 203L)
        val ok = shopService.buyItem(1L, ShopItem(203, "百年魂骨箱", "", 800, "GOLD", "BONE_BOX", "HUNDRED"))
        assertTrue(ok.success)
        assertEquals(200L, profile.gold)
        verify(backpackItemRepository).save(itemCaptor.capture())
        assertEquals("BONE", itemCaptor.value.itemType)
    }

    @Test
    fun `buyItem should reject invalid currency type without any deduction`() {
        val (user, profile) = userWith(gold = 10000)

        val result = shopService.buyItem(1L, ShopItem(999, "奇怪商品", "", 100, "DIAMOND", "SOUL_POWER", "500"))

        assertFalse(result.success)
        assertEquals("无效的货币类型", result.error)
        assertEquals(10000L, profile.gold)
        verify(userRepository, never()).save(any())
        verify(purchaseRecordRepository, never()).save(any())
    }

    @Test
    fun `buyItem should reject insufficient level without deducting currency`() {
        val (user, profile) = userWith(bossCoin = 3000, level = 10)

        val result = shopService.buyItem(1L, ShopItem(3, "神赐礼包", "", 3000, "BOSS_COIN", "GIFT_PACK", "DIVINE", stock = 1, requiresLevel = 100))

        assertFalse(result.success)
        assertEquals("等级不足，需要100级", result.error)
        assertEquals(3000L, profile.bossCoin)
        verify(userRepository, never()).save(any())
        verify(purchaseRecordRepository, never()).save(any())
    }

    @Test
    fun `buyLimitedItem should reject when purchase count reached stock`() {
        val (user, profile) = userWith(bossCoin = 5000, level = 100)
        val record = ShopPurchaseRecord(userId = 1L, itemId = 1L, purchaseCount = 1)
        doReturn(record).whenever(purchaseRecordRepository).findByUserIdAndItemId(1L, 1L)

        val result = shopService.buyLimitedItem(1L, ShopItem(1, "传说魂核", "", 2000, "BOSS_COIN", "CORE_BOX", "MYTHIC", stock = 1))

        assertFalse(result.success)
        assertEquals("该商品已售罄", result.error)
        assertEquals(5000L, profile.bossCoin)
        verify(userRepository, never()).save(any())
        verify(backpackItemRepository, never()).save(any())
        // 售罄路径连余额都不该去查（未进入 buyItem 的扣款逻辑）
        assertEquals(1, record.purchaseCount)
    }

    // ==================== 任务#24 补盲 ====================

    @Test
    fun `buyItem should reject unsupported reward type BEFORE deducting currency (神赐礼包 GIFT_PACK)`() {
        val (user, profile) = userWith(bossCoin = 3000, level = 100)

        // 3000 Boss 币"神赐礼包"：itemType=GIFT_PACK 在发放分支里根本未实现。
        // 若扣款后才落到 else 早退，@Transactional 托管实体的扣款照样提交 → 钱货两空。
        // （任务#26 已把该商品从 LimitedShopData 下架，此处保留同一定义的实例继续锁住预检行为，
        //  防止未来把 GIFT_PACK 挂回商店时回归。）
        val result = shopService.buyItem(
            1L, ShopItem(903, "神赐礼包", "包含大量稀有材料", 3000, "BOSS_COIN", "GIFT_PACK", "DIVINE", stock = 1, requiresLevel = 100)
        )

        assertFalse(result.success)
        assertEquals(3000L, profile.bossCoin)
        verify(userRepository, never()).save(any())
        verify(purchaseRecordRepository, never()).save(any())
        verify(backpackItemRepository, never()).save(any())
    }

    @Test
    fun `GIFT_PACK divine gift pack should be delisted from LimitedShopData until grant is implemented`() {
        // 预检只能"干净拒绝"，但商品仍展示 = 用户看得见买不了；下架才是最小合理方案。
        // 恢复上架的前提：ShopService 实现 GIFT_PACK 发放分支（见 LimitedShopData 注释）。
        assertTrue(LimitedShopData.items.none { it.itemType == "GIFT_PACK" })
        assertNotNull(ShopService.rewardValidationError(
            ShopItem(903, "神赐礼包", "", 3000, "BOSS_COIN", "GIFT_PACK", "DIVINE")
        ), "rewardValidationError 须继续拒绝 GIFT_PACK，防止未实现发放就重新上架")
    }

    @Test
    fun `buyItem should reject box with unknown tier before deduction instead of reporting fake success`() {
        val (user, profile) = userWith(bossCoin = 100)

        // 未知档位当前会走 grantXxxBox 的 else：钱已扣、背包不落物品，却返回 success=true "异常数据"
        val result = shopService.buyItem(1L, ShopItem(1, "异常魂环箱", "", 50, "BOSS_COIN", "RING_BOX", "BILLION"))

        assertFalse(result.success)
        assertEquals(100L, profile.bossCoin)
        verify(backpackItemRepository, never()).save(any())
        verify(userRepository, never()).save(any())
        verify(purchaseRecordRepository, never()).save(any())
    }

    @Test
    fun `buyItem should reject malformed or negative GOLD_BAG itemData without deducting`() {
        val (user, profile) = userWith(bossCoin = 30, gold = 5000)

        val malformed = shopService.buyItem(1L, ShopItem(8, "坏金币袋", "", 30, "BOSS_COIN", "GOLD_BAG", "abc"))
        assertFalse(malformed.success)
        assertEquals(30L, profile.bossCoin)
        assertEquals(5000L, profile.gold)

        // 负数额若放行：gold += (-10000) 反向吞金 / 配合低价还能套取
        val negative = shopService.buyItem(1L, ShopItem(8, "倒贴金币袋", "", 1, "BOSS_COIN", "GOLD_BAG", "-10000"))
        assertFalse(negative.success)
        assertEquals(30L, profile.bossCoin)
        assertEquals(5000L, profile.gold)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `all shop catalogs must use globally unique item ids because purchase records key on itemId only`() {
        // shop_purchase_record 唯一键是 (user_id, item_id)，不带商店来源。
        // 任何两个商店间复用同一 id，购买记录就会互相串用限购计数。
        val ids = (NormalShopData.items + BossShopData.items + LimitedShopData.items + GuildShopData.items).map { it.id }

        assertEquals(ids.size, ids.toSet().size, "跨商店商品 id 冲突：$ids")
    }

    @Test
    fun `buying boss shop ring box must not consume limited shop 传说魂核 allowance`() {
        // 现实场景复现：玩家先买了 Boss 商店"万年魂环箱"(id=1)，记录已存在 (userId=1,itemId=1)。
        // 若限量商店的"传说魂核"与之 id 冲突，会被这条记录误判"已售罄"。
        val (user, profile) = userWith(bossCoin = 5000, level = 100)
        val limitedLegendCore = LimitedShopData.items.first { it.name == "传说魂核" }
        val bossRecord = ShopPurchaseRecord(userId = 1L, itemId = 1L, purchaseCount = 1)
        // 精确 stub：历史购买记录只挂在 Boss 商店的 itemId=1 上
        doAnswer { inv ->
            val args = inv.arguments
            if (args[0] as Long == 1L && args[1] as Long == 1L) bossRecord else null
        }.whenever(purchaseRecordRepository).findByUserIdAndItemId(any(), any())

        val result = shopService.buyLimitedItem(1L, limitedLegendCore)

        assertTrue(result.success, "传说魂核购买被 Boss 商店记录误拦：${result.error}")
        assertEquals(3000L, profile.bossCoin)
        verify(backpackItemRepository).save(any())
    }

    @Test
    fun `purchase record increment should refresh lastPurchaseAt`() {
        val (user, profile) = userWith(gold = 1000)
        val existing = ShopPurchaseRecord(userId = 1L, itemId = 204L, purchaseCount = 3)
        existing.lastPurchaseAt = java.time.LocalDateTime.now().minusDays(7)
        val before = existing.lastPurchaseAt
        doReturn(existing).whenever(purchaseRecordRepository).findByUserIdAndItemId(1L, 204L)

        val result = shopService.buyItem(1L, ShopItem(204, "魂力精华(小)", "", 300, "GOLD", "SOUL_POWER", "500"))

        assertTrue(result.success)
        assertEquals(4, existing.purchaseCount)
        assertTrue(existing.lastPurchaseAt.isAfter(before), "lastPurchaseAt 应随本次购买刷新")
    }

    // ==================== 每日任务挂点 ====================

    @Test
    fun `buyItem should record shop_buy daily quest exactly once on success path only`() {
        val (user, profile) = userWith(gold = 1000)
        doReturn(null).whenever(purchaseRecordRepository).findByUserIdAndItemId(1L, 204L)

        val ok = shopService.buyItem(1L, ShopItem(204, "魂力精华(小)", "", 300, "GOLD", "SOUL_POWER", "500"))
        assertTrue(ok.success)
        verify(dailyQuestService).recordShopBuy(1L)

        // 失败路径（无效货币早退）不计数：仍只有成功那 1 次
        val failed = shopService.buyItem(1L, ShopItem(999, "奇怪商品", "", 100, "DIAMOND", "SOUL_POWER", "500"))
        assertFalse(failed.success)
        verify(dailyQuestService).recordShopBuy(1L)
    }
}
