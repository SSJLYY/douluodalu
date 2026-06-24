package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "equipped_core")
class EquippedCoreEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "slot_type", nullable = false, length = 16)
    var slotType: String = "",  // LEFT, RIGHT

    @Column(name = "core_id", nullable = false)
    var coreId: Long = 0,

    @Column(name = "rarity_ordinal", nullable = false)
    var rarityOrdinal: Int = 0,

    @Column(name = "core_name", nullable = false, length = 50)
    var coreName: String = "",

    @Column(name = "core_value", nullable = false)
    var coreValue: Int = 0,

    @Column(name = "core_level", nullable = false)
    var coreLevel: Int = 0,

    @Column(name = "equip_at")
    var equipAt: LocalDateTime = LocalDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    var user: UserEntity? = null
) {
    override fun toString() = "EquippedCore[id=$id,user=$userId,slot=$slotType,core=$coreId,rarity=$rarityOrdinal,name=$coreName,val=$coreValue,level=$coreLevel]"
}
