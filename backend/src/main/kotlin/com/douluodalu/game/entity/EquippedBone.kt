package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "equipped_bone")
class EquippedBone(
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

    // 第二十八轮魂骨词缀（V12 迁移）：equip/unequip/prestige 的「属性拷贝」模式必须随件搬运
    // （JSON 形状与数值表见 GameBalance「魂骨词缀」区块；V12 前的行恒 null，消费侧宽容处理）
    @Column(name = "affixes_json", columnDefinition = "JSON")
    var affixesJson: String? = null,

    @Column(name = "equip_at")
    var equipAt: LocalDateTime = LocalDateTime.now()
) {
    override fun toString() = "EquippedBone[id=$id,user=$userId,slot=$slotIndex,bone=$boneId,year=$yearOrdinal,quality=$qualityOrdinal,type=$boneTypeOrdinal,enh=$enhanceLevel,affixes=${affixesJson != null}]"
}
