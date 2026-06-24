package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "backpack_item")
class BackpackItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "item_type", nullable = false, length = 8)
    var itemType: String = "",

    @Column(name = "year_ordinal")
    var yearOrdinal: Int = 0,
    @Column(name = "quality_ordinal")
    var qualityOrdinal: Int = 0,

    @Column(name = "affixes_json", columnDefinition = "JSON")
    var affixesJson: String? = null,

    var locked: Boolean = false,

    var percentage: Int = 0,
    @Column(name = "skill_name")
    var skillName: String? = null,

    @Column(name = "bone_type_ordinal")
    var boneTypeOrdinal: Int? = null,
    @Column(name = "enhance_level")
    var enhanceLevel: Int = 0,
    @Column(name = "passive_skill_name")
    var passiveSkillName: String? = null,

    @Column(name = "core_name")
    var coreName: String? = null,
    @Column(name = "core_value")
    var coreValue: Int? = null,
    @Column(name = "core_level")
    var coreLevel: Int = 0,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()
)
