package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "equipped_bone")
class EquippedBoneEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "slot_index", nullable = false)
    var slotIndex: Int = 0,

    @Column(name = "bone_id", nullable = false)
    var boneId: Long = 0,

    @Column(name = "year_ordinal", nullable = false)
    var yearOrdinal: Int = 0,

    @Column(name = "quality_ordinal", nullable = false)
    var qualityOrdinal: Int = 0,

    @Column(name = "bone_type_ordinal", nullable = false)
    var boneTypeOrdinal: Int = 0,

    @Column(name = "enhance_level", nullable = false)
    var enhanceLevel: Int = 0,

    @Column(name = "equip_at")
    var equipAt: LocalDateTime = LocalDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    var user: UserEntity? = null
) {
    override fun toString() = "EquippedBone[id=$id,user=$userId,slot=$slotIndex,bone=$boneId,year=$yearOrdinal,qual=$qualityOrdinal,type=$boneTypeOrdinal,enh=$enhanceLevel]"
}
