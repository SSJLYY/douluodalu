package com.douluodalu.game.service

import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import com.douluodalu.game.repository.EquippedRingRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.TalentRepository
import com.douluodalu.game.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class EquipmentFlowTest {
    @Mock
    private lateinit var profileRepo: PlayerProfileRepository

    @Mock
    private lateinit var backpackRepo: BackpackItemRepository

    @Mock
    private lateinit var talentRepo: TalentRepository

    @Mock
    private lateinit var equippedRingRepo: EquippedRingRepository

    @Mock
    private lateinit var equippedBoneRepo: EquippedBoneRepository

    @Mock
    private lateinit var equippedCoreRepo: EquippedCoreRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var webSocketService: WebSocketService

    @Captor
    private lateinit var savedRingCaptor: ArgumentCaptor<EquippedRing>

    @Captor
    private lateinit var bagItemCaptor: ArgumentCaptor<BackpackItemEntity>

    @InjectMocks
    private lateinit var gameService: GameService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        doAnswer { it.arguments[0] }.whenever(profileRepo).save(any())
        doAnswer { it.arguments[0] }.whenever(backpackRepo).save(any())
        doAnswer { it.arguments[0] }.whenever(equippedRingRepo).save(any())
    }

    private fun ring(id: Long, year: Int = 1, quality: Int = 2, percentage: Int = 350) = BackpackItemEntity(
        id = id, userId = 1L, itemType = "RING", yearOrdinal = year, qualityOrdinal = quality, percentage = percentage
    )

    @Test
    fun `equipRing should move item from backpack to slot`() {
        val r = ring(id = 11L)
        doReturn(listOf(r)).whenever(backpackRepo).findByUserIdAndItemType(1L, "RING")
        doReturn(null).whenever(equippedRingRepo).findByUserIdAndSlotIndex(1L, 0)

        assertTrue(gameService.equipRing(1L, 0, 0))

        verify(equippedRingRepo).save(savedRingCaptor.capture())
        val equipped = savedRingCaptor.value
        assertEquals(0, equipped.slotIndex)
        assertEquals(11L, equipped.ringId)
        assertEquals(1, equipped.yearOrdinal)
        assertEquals(2, equipped.qualityOrdinal)
        assertEquals(350, equipped.percentage)
        verify(backpackRepo).delete(r)
    }

    @Test
    fun `equipRing on occupied slot should return old ring to backpack and replace it`() {
        val newRing = ring(id = 22L, year = 2, quality = 3, percentage = 600)
        val oldEquipped = EquippedRing(id = 5L, userId = 1L, slotIndex = 3, ringId = 9L, yearOrdinal = 0, qualityOrdinal = 1, percentage = 120)
        doReturn(listOf(newRing)).whenever(backpackRepo).findByUserIdAndItemType(1L, "RING")
        doReturn(oldEquipped).whenever(equippedRingRepo).findByUserIdAndSlotIndex(1L, 3)

        assertTrue(gameService.equipRing(1L, 3, 0))

        // 旧环回背包
        verify(backpackRepo).save(bagItemCaptor.capture())
        val returned = bagItemCaptor.value
        assertEquals("RING", returned.itemType)
        assertEquals(0, returned.yearOrdinal)
        assertEquals(1, returned.qualityOrdinal)
        assertEquals(120, returned.percentage)
        // 旧记录删除、新环占位
        verify(equippedRingRepo).delete(oldEquipped)
        verify(equippedRingRepo).save(savedRingCaptor.capture())
        assertEquals(22L, savedRingCaptor.value.ringId)
        verify(backpackRepo).delete(newRing)
    }

    @Test
    fun `unequipRing should restore ring to backpack and free the slot`() {
        val equipped = EquippedRing(id = 7L, userId = 1L, slotIndex = 2, ringId = 33L, yearOrdinal = 3, qualityOrdinal = 4, percentage = 888)
        doReturn(equipped).whenever(equippedRingRepo).findByUserIdAndSlotIndex(1L, 2)

        assertTrue(gameService.unequipRing(1L, 2))

        verify(backpackRepo).save(bagItemCaptor.capture())
        val restored = bagItemCaptor.value
        assertEquals("RING", restored.itemType)
        assertEquals(3, restored.yearOrdinal)
        assertEquals(4, restored.qualityOrdinal)
        assertEquals(888, restored.percentage)
        verify(equippedRingRepo).delete(equipped)
    }

    @Test
    fun `equip and unequip should reject invalid slot index and empty slot without writes`() {
        // 槽位越界
        assertFalse(gameService.equipRing(1L, 9, 0))
        assertFalse(gameService.unequipRing(1L, -1))
        // 空槽卸下 / 背包无环
        val r = ring(id = 44L)
        doReturn(listOf(r)).whenever(backpackRepo).findByUserIdAndItemType(1L, "RING")
        doReturn(null).whenever(equippedRingRepo).findByUserIdAndSlotIndex(1L, 4)
        assertFalse(gameService.unequipRing(1L, 4))
        assertFalse(gameService.equipRing(1L, 0, 5)) // ringIndex 越界

        verify(equippedRingRepo, never()).save(any())
        verify(equippedRingRepo, never()).delete(any())
        verify(backpackRepo, never()).save(any())
        verify(backpackRepo, never()).delete(any())
    }

    @Test
    fun `sellBackpackItem should add gold by quality and remove item`() {
        val profile = PlayerProfileEntity(userId = 1L, level = 5)
        profile.gold = 1000
        doReturn(profile).whenever(profileRepo).findByUserId(1L)
        val item = ring(id = 55L, quality = 3)
        doReturn(listOf(item)).whenever(backpackRepo).findByUserId(1L)

        assertTrue(gameService.sellBackpackItem(1L, 0))

        // 售价 = 100 + quality*50 = 250
        assertEquals(1250L, profile.gold)
        verify(backpackRepo).delete(item)
        verify(profileRepo).save(profile)
    }

    @Test
    fun `sellBackpackItem should reject locked item and out-of-range index without writes`() {
        val profile = PlayerProfileEntity(userId = 1L, level = 5)
        profile.gold = 1000
        doReturn(profile).whenever(profileRepo).findByUserId(1L)
        val locked = ring(id = 56L).apply { this.locked = true }
        doReturn(listOf(locked)).whenever(backpackRepo).findByUserId(1L)

        assertFalse(gameService.sellBackpackItem(1L, 0))  // 已锁定
        assertFalse(gameService.sellBackpackItem(1L, 3))  // 索引越界

        assertEquals(1000L, profile.gold)
        verify(backpackRepo, never()).delete(any())
        verify(profileRepo, never()).save(any())
    }

    @Test
    fun `expandBackpack should charge by formula and add capacity only with enough gold`() {
        val profile = PlayerProfileEntity(userId = 1L, level = 5)
        profile.backpackCapacity = 20
        doReturn(profile).whenever(profileRepo).findByUserId(1L)

        // 费用 = 1000 + 20*100 = 3000，金币不足时拒绝且不写入
        profile.gold = 2999
        assertFalse(gameService.expandBackpack(1L))
        assertEquals(2999L, profile.gold)
        assertEquals(20, profile.backpackCapacity)
        verify(profileRepo, never()).save(any())

        // 足额时扣款 + 容量 +5
        profile.gold = 3000
        assertTrue(gameService.expandBackpack(1L))
        assertEquals(0L, profile.gold)
        assertEquals(25, profile.backpackCapacity)
        verify(profileRepo).save(profile)
    }
}
