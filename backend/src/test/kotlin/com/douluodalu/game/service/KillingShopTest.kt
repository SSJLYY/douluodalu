package com.douluodalu.game.service

import com.douluodalu.game.dto.KillingBuyResponse
import com.douluodalu.game.dto.KillingShopDto
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.entity.UserEntity
import com.douluodalu.game.entity.UserTitleEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.UserRepository
import com.douluodalu.game.repository.UserTitleRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.util.Optional

/**
 * 第二十九轮杀气商店（设计文档 §7.4）：ShopService.getKillingShop / buyKillingTitle /
 * buyKillingAttr 的单测（ShopServiceTest mock 风格同款）+ GameBalance 数值逐位契约钉。
 * 覆盖：8 称号购买逐位（扣杀气/写库/重复拒绝/余额不足拒绝/未知称号/并发撞键兜底）、
 * 属性购买 2^n 价格逐位（0/1/2 次 → 100/200/400）与 HP/ATK 独立计数、业务计数器。
 * 战力管道（bonusFor/title 行/十行恒等/转生保留）见 EquipmentPowerServiceTest 与 GameServiceTest。
 */
class KillingShopTest {
    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userTitleRepository: UserTitleRepository

    private lateinit var shopService: ShopService

    /** 真实 Micrometer 注册表（计数器断言用；测试间 clear 防串扰） */
    private val meterRegistry = SimpleMeterRegistry()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        meterRegistry.clear()
        // user / gameService / backpack / purchaseRecord / dailyQuest 仅 killingPlayer 之外的
        // buyItem 路径消费；杀气商店只用 userRepository + userTitleRepository
        shopService = ShopService(
            userRepository,
            org.mockito.kotlin.mock(),
            org.mockito.kotlin.mock(),
            org.mockito.kotlin.mock(),
            org.mockito.kotlin.mock(),
            userTitleRepository,
            meterRegistry
        )
    }

    private fun userWith(killingIntent: Int = 0, hpBuys: Int = 0, atkBuys: Int = 0): Pair<UserEntity, PlayerProfileEntity> {
        val profile = PlayerProfileEntity(userId = 1L, level = 10)
        profile.killingIntent = killingIntent
        profile.killingHpBuys = hpBuys
        profile.killingAtkBuys = atkBuys
        val user = UserEntity(id = 1, username = "u", nickname = "n", passwordHash = "h")
        user.player = profile
        doReturn(Optional.of(user)).whenever(userRepository).findById(1L)
        return user to profile
    }

    // ==================== GameBalance 数值契约（文档 §7.4 逐位） ====================

    @Test
    fun `killing title table must match design doc values bit for bit`() {
        val expected = listOf(
            listOf("title_1", "初出茅庐", 100L, 200L, 10L, 0L, 0L, 0L),
            listOf("title_2", "猎魂勇士", 300L, 500L, 25L, 0L, 0L, 0L),
            listOf("title_3", "百战精英", 800L, 1200L, 50L, 10L, 0L, 0L),
            listOf("title_4", "屠戮者", 2000L, 3000L, 100L, 20L, 3L, 0L),
            listOf("title_5", "魂兽克星", 5000L, 8000L, 200L, 40L, 5L, 15L),
            listOf("title_6", "传奇猎手", 15000L, 20000L, 500L, 80L, 8L, 30L),
            listOf("title_7", "万人斩", 50000L, 50000L, 1200L, 150L, 12L, 50L),
            listOf("title_8", "征服者", 150000L, 150000L, 3000L, 300L, 15L, 80L)
        )
        assertEquals(8, GameBalance.KILLING_TITLES.size, "设计文档固定 8 个称号")
        expected.forEachIndexed { i, row ->
            val def = GameBalance.KILLING_TITLES[i]
            assertEquals(row[0], def.id, "[$i] id")
            assertEquals(row[1], def.name, "[$i] name")
            assertEquals(row[2], def.cost, "[$i] cost")
            assertEquals(row[3], def.hp, "[$i] hp")
            assertEquals(row[4], def.atk, "[$i] atk")
            assertEquals(row[5], def.pdef, "[$i] pdef（文档 DEF+n 落 pdef）")
            assertEquals(row[6], def.critRate, "[$i] critRate（title_1/2/3 无 CRIT 字段补 0）")
            assertEquals(row[7], def.critDmg, "[$i] critDmg")
        }
        assertEquals(GameBalance.KILLING_TITLES.associateBy { it.id }, GameBalance.KILLING_TITLE_BY_ID)
    }

    @Test
    fun `killing attr cost must double from 100 (100 200 400 at 0 1 2 buys)`() {
        assertEquals(100L, GameBalance.killingAttrCost(0))
        assertEquals(200L, GameBalance.killingAttrCost(1))
        assertEquals(400L, GameBalance.killingAttrCost(2))
        assertEquals(800L, GameBalance.killingAttrCost(3))
        // 负数按 0 兜底、超大购买次数封顶防 Long 溢出（GameBalance KDoc 的数值防御）
        assertEquals(100L, GameBalance.killingAttrCost(-1))
        assertEquals(GameBalance.KILLING_ATTR_BASE_COST shl 56, GameBalance.killingAttrCost(100))
        assertEquals(GameBalance.KILLING_ATTR_HP_PER_BUY, 100L)
        assertEquals(GameBalance.KILLING_ATTR_ATK_PER_BUY, 10L)
    }

    // ==================== 面板 ====================

    @Test
    fun `getKillingShop returns 8 titles with owned flags and 2-power-n attr pricing`() {
        val (_, player) = userWith(killingIntent = 999, hpBuys = 2, atkBuys = 1)
        whenever(userTitleRepository.findByUserId(1L)).thenReturn(
            listOf(UserTitleEntity(userId = 1L, titleId = "title_1"), UserTitleEntity(userId = 1L, titleId = "title_8"))
        )

        val shop = shopService.getKillingShop(1L)

        assertEquals(999, shop.killingIntent)
        assertEquals(8, shop.titles.size)
        assertEquals(listOf("title_1", "title_8"), shop.titles.filter { it.owned }.map { it.id }, "已拥有称号按 user_title 反查")
        shop.titles.forEach { dto ->
            val def = GameBalance.KILLING_TITLE_BY_ID.getValue(dto.id)
            assertEquals(def.cost, dto.cost)
            assertEquals(def.hp, dto.hp)
            assertEquals(def.atk, dto.atk)
            assertEquals(def.pdef, dto.pdef)
            assertEquals(def.critRate, dto.critRate)
            assertEquals(def.critDmg, dto.critDmg)
        }
        // HP/ATK 两商品当前价 = 100×2^已购次数（2 次 → 400、1 次 → 200）
        val hp = shop.attrs.first { it.stat == "hp" }
        val atk = shop.attrs.first { it.stat == "atk" }
        assertEquals(2, hp.buys)
        assertEquals(400L, hp.nextCost)
        assertEquals(100L, hp.effectPerBuy)
        assertEquals(1, atk.buys)
        assertEquals(200L, atk.nextCost)
        assertEquals(10L, atk.effectPerBuy)
    }

    // ==================== 称号兑换 ====================

    @Test
    fun `buyKillingTitle should deduct exact cost and persist user_title for all 8 titles`() {
        for (def in GameBalance.KILLING_TITLES) {
            val (user, player) = userWith(killingIntent = def.cost.toInt())
            whenever(userTitleRepository.existsByUserIdAndTitleId(1L, def.id)).thenReturn(false)

            val response = shopService.buyKillingTitle(1L, def.id)

            assertTrue(response.message.contains(def.name))
            assertEquals(def.id, response.titleId)
            assertEquals(0, player.killingIntent, "${def.id} 应恰好扣完全额 ${def.cost}")
            assertEquals(0, response.killingIntent)
            val captor = ArgumentCaptor.forClass(UserTitleEntity::class.java)
            verify(userTitleRepository).saveAndFlush(captor.capture())
            assertEquals(1L, captor.value.userId)
            assertEquals(def.id, captor.value.titleId)
            verify(userRepository).save(user)
            // 每个称号验证完重置调用计数
            org.mockito.kotlin.clearInvocations(userTitleRepository, userRepository)
        }
    }

    @Test
    fun `buyKillingTitle should reject repurchase of owned title without deduction or write`() {
        val (_, player) = userWith(killingIntent = 100)
        whenever(userTitleRepository.existsByUserIdAndTitleId(1L, "title_1")).thenReturn(true)

        val e = assertThrows<IllegalArgumentException> { shopService.buyKillingTitle(1L, "title_1") }

        assertTrue(e.message!!.contains("已拥有"), "回购拒绝应给业务语义：${e.message}")
        assertEquals(100, player.killingIntent, "拒绝路径不得扣杀气")
        verify(userTitleRepository, never()).saveAndFlush(any())
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `buyKillingTitle should reject insufficient killing intent without deduction or write`() {
        val (_, player) = userWith(killingIntent = 299) // title_2 需要 300
        whenever(userTitleRepository.existsByUserIdAndTitleId(1L, "title_2")).thenReturn(false)

        val e = assertThrows<IllegalArgumentException> { shopService.buyKillingTitle(1L, "title_2") }

        assertTrue(e.message!!.contains("杀气不足"), "${e.message}")
        assertEquals(299, player.killingIntent)
        verify(userTitleRepository, never()).saveAndFlush(any())
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `buyKillingTitle should reject unknown title id`() {
        userWith(killingIntent = 1_000_000)

        val e = assertThrows<IllegalArgumentException> { shopService.buyKillingTitle(1L, "title_9") }

        assertTrue(e.message!!.contains("未知称号"), "${e.message}")
    }

    @Test
    fun `buyKillingTitle concurrent duplicate should fall back to unique constraint then reject as business error`() {
        // 并发双买兜底（achievement_record uk 先例）：预检时未拥有、saveAndFlush 撞 V14 联合 PK、
        // 重查确认已拥有 → 按业务错误拒绝；扣款不落库（userRepository.save 不被调用，事务回滚扣杀气）
        val (_, player) = userWith(killingIntent = 100)
        var existsCalls = 0
        whenever(userTitleRepository.existsByUserIdAndTitleId(1L, "title_1")).thenAnswer { existsCalls++ == 0 }
        doThrow(DataIntegrityViolationException("duplicate entry"))
            .whenever(userTitleRepository).saveAndFlush(any())

        val e = assertThrows<IllegalArgumentException> { shopService.buyKillingTitle(1L, "title_1") }

        assertTrue(e.message!!.contains("已拥有"), "撞键后重查确认应转业务 400：${e.message}")
        verify(userRepository, never()).save(any())
        // 非重复键冲突（重查未确认）应原样上抛走 500 兜底
        existsCalls = 5
        val raw = assertThrows<DataIntegrityViolationException> { shopService.buyKillingTitle(1L, "title_1") }
        assertEquals("duplicate entry", raw.message)
    }

    // ==================== 属性购买 ====================

    @Test
    fun `buyKillingAttr hp prices follow 2-power-n (100-200-400) with independent counter`() {
        val (_, player) = userWith(killingIntent = 10_000)

        val first = shopService.buyKillingAttr(1L, "hp")
        val second = shopService.buyKillingAttr(1L, "hp")
        val third = shopService.buyKillingAttr(1L, "hp")

        assertEquals(100, 10_000 - first.killingIntent, "第 1 次 100")
        assertEquals(200, first.killingIntent - second.killingIntent, "第 2 次 200")
        assertEquals(400, second.killingIntent - third.killingIntent, "第 3 次 400")
        assertEquals(9_300, player.killingIntent)
        assertEquals(3, player.killingHpBuys)
        assertEquals(0, player.killingAtkBuys, "HP/ATK 计数各自独立")
        assertEquals(3, third.buys)
        verify(userRepository, org.mockito.kotlin.times(3)).save(any())
    }

    @Test
    fun `buyKillingAttr atk path counts separately and grants 10 atk per buy`() {
        val (_, player) = userWith(killingIntent = 5_000)

        val first = shopService.buyKillingAttr(1L, "atk")
        val second = shopService.buyKillingAttr(1L, "atk")

        assertEquals(100, 5_000 - first.killingIntent)
        assertEquals(200, first.killingIntent - second.killingIntent)
        assertEquals(2, second.buys)
        assertEquals(2, player.killingAtkBuys)
        assertEquals(0, player.killingHpBuys)
        assertTrue(first.message.contains("+10"), "效果文案应含 +10 ATK：${first.message}")
    }

    @Test
    fun `buyKillingAttr should reject invalid stat without deduction`() {
        val (_, player) = userWith(killingIntent = 100)

        val e = assertThrows<IllegalArgumentException> { shopService.buyKillingAttr(1L, "mp") }

        assertTrue(e.message!!.contains("无效的属性类型"), "${e.message}")
        assertEquals(100, player.killingIntent)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `buyKillingAttr should reject insufficient killing intent without deduction`() {
        val (_, player) = userWith(killingIntent = 99)

        val e = assertThrows<IllegalArgumentException> { shopService.buyKillingAttr(1L, "hp") }

        assertTrue(e.message!!.contains("杀气不足"), "${e.message}")
        assertEquals(99, player.killingIntent)
        assertEquals(0, player.killingHpBuys)
        verify(userRepository, never()).save(any())
    }

    // ==================== 业务计数器（round 21 先例） ====================

    @Test
    fun `killing shop purchases should increment business counters`() {
        userWith(killingIntent = 1_000)
        whenever(userTitleRepository.existsByUserIdAndTitleId(1L, "title_1")).thenReturn(false)

        shopService.buyKillingTitle(1L, "title_1")
        shopService.buyKillingAttr(1L, "hp")
        shopService.buyKillingAttr(1L, "atk")

        assertEquals(1.0, meterRegistry.counter(ShopService.METRIC_KILLING_TITLE_TOTAL).count(), 1e-9)
        assertEquals(1.0, meterRegistry.counter(ShopService.METRIC_KILLING_ATTR_TOTAL, "stat", "hp").count(), 1e-9)
        assertEquals(1.0, meterRegistry.counter(ShopService.METRIC_KILLING_ATTR_TOTAL, "stat", "atk").count(), 1e-9)
    }
}
