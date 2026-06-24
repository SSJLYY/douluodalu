package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "equipped_ring")
class EquippedRing(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "slot_index", nullable = false)
    var slotIndex: Int = 0,

    @Column(name = "ring_id", nullable = false)
    var ringId: Long = 0,

    @Column(name = "year_ordinal", nullable = false)
    var yearOrdinal: Int = 0,

    @Column(name = "quality_ordinal", nullable = false)
    var qualityOrdinal: Int = 0,

    @Column(name = "percentage", nullable = false)
    var percentage: Int = 0,

    @Column(name = "equip_at")
    var equipAt: LocalDateTime = LocalDateTime.now()
) {
    override fun toString() = "EquippedRing[id=$id,user=$userId,slot=$slotIndex,ring=$ringId,year=$yearOrdinal,qual=$qualityOrdinal,perc=$percentage]"
}
