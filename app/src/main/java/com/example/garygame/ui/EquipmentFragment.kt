package com.example.garygame.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.garygame.R
import com.example.garygame.engine.GameEngine
import com.example.garygame.model.*

class EquipmentFragment : Fragment() {

    private lateinit var tvWuhunDisplay: TextView
    private lateinit var tvSoulRingInfo: TextView
    private lateinit var tvSoulBoneInfo: TextView
    private lateinit var ringsContainer: LinearLayout
    private lateinit var bonesContainer: LinearLayout
    private lateinit var setBonusContainer: LinearLayout
    // 魂核
    private lateinit var tvSoulCoreEquipInfo: TextView
    private lateinit var soulCoreContainer: LinearLayout
    private lateinit var mainLayout: LinearLayout
    private var backpackContainer: LinearLayout? = null

    private val updateListener = { updateUI() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_equipment, container, false)

        tvWuhunDisplay = view.findViewById(R.id.tv_wuhun_display)
        tvSoulRingInfo = view.findViewById(R.id.tv_soul_ring_info)
        tvSoulBoneInfo = view.findViewById(R.id.tv_soul_bone_info)
        ringsContainer = view.findViewById(R.id.soul_rings_container)
        bonesContainer = view.findViewById(R.id.soul_bones_container)
        setBonusContainer = view.findViewById(R.id.set_bonus_container)
        tvSoulCoreEquipInfo = view.findViewById(R.id.tv_soul_core_equip_info)
        soulCoreContainer = view.findViewById(R.id.soul_core_container)
        mainLayout = (view as ViewGroup).getChildAt(0) as LinearLayout

        GameEngine.addListener(updateListener)
        // 武魂点击查看详情
        tvWuhunDisplay.setOnClickListener { showWuhunDetail() }
        updateUI()
        return view
    }

    private fun updateUI() {
        val s = GameEngine.state
        val attr = GameEngine.calcAttributes()

        // 武魂
        val soul = s.martialSoul
        if (soul != null) {
            tvWuhunDisplay.text = "${soul.name} (${soul.rarity.displayName})"
            tvWuhunDisplay.setTextColor(soul.rarity.colorInt)
        } else {
            tvWuhunDisplay.text = "未觉醒- 前往修炼页觉醒"
            tvWuhunDisplay.setTextColor(resources.getColor(R.color.rarity_common))
        }

        // 魂环 (槽位制显示，含解锁提示)
        val maxRings = RealmData.maxSoulRings(s.level)
        tvSoulRingInfo.text = "魂环槽位: ${s.soulRings.size} / $maxRings"
        ringsContainer.removeAllViews()

        for (slot in SoulRingSlots.all) {
            val isUnlocked = slot.index < maxRings
            val line = LinearLayout(requireContext())
            line.orientation = LinearLayout.VERTICAL
            line.setPadding(0, 2, 0, 2)

            if (!isUnlocked) {
                // 未解锁槽位：显示解锁等级提示
                val unlockLv = RealmData.ringSlotUnlockLevel(slot.index)
                line.addView(TextView(requireContext()).apply {
                    text = "${slot.displayName}: 🔀 Lv.${unlockLv} 解锁"
                    setTextColor(resources.getColor(R.color.text_secondary))
                    textSize = 12f
                })
                ringsContainer.addView(line)
                continue
            }

            val ring = s.soulRings[slot.index]

            val ringView = TextView(requireContext()).apply {
                if (ring != null && ring.yearOrdinal in RingYear.entries.indices) {
                    val year = RingYear.entries[ring.yearOrdinal]
                    val affixStr = ring.affixes.joinToString("  ") { "${it.type.displayName}+${it.value}" }
                    text = "${slot.displayName}: ${year.displayName} | $affixStr"
                    setTextColor(year.colorHex.toInt())
                    setOnClickListener { showRingDetail(slot.index, ring) }
                } else {
                    val minYear = RingYear.entries[RingYear.yearOf(slot.minCombinedTier)]
                    text = "${slot.displayName}: 空(需${minYear.displayName}+)"
                    setTextColor(resources.getColor(R.color.text_secondary))
                }
                textSize = 13f
            }
            line.addView(ringView)

            // V2: 显示主动技能
            if (ring?.skill != null) {
                val skill = ring.skill
                val cd = s.activeSkillCooldowns[slot.index] ?: 0
                val cdText = if (cd <= 0) "✅就绪" else "⏳${cd}回合"
                val soulEnough = if (s.battleSoulPower >= skill.soulCost) "🔋" else "⚡魂力"
                line.addView(TextView(requireContext()).apply {
                    text = "    🎆 ${skill.name}(${skill.type.displayName}) 消耗${skill.soulCost}🔋${cdText} ${if (cd<=0) soulEnough else ""}"
                    setTextColor(if (cd <= 0) resources.getColor(R.color.rarity_uncommon) else resources.getColor(R.color.text_secondary))
                    textSize = 11f
                })
            }

            // 卸下按钮
            if (ring != null) {
                val unequipBtn = Button(requireContext()).apply {
                    text = "卸下"
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(resources.getColor(R.color.rarity_mythic))
                    textSize = 11f
                    setPadding(8, 0, 8, 0)
                    addTouchFeedback()
                    setOnClickListener {
                        GameEngine.unequipRing(slot.index)
                        updateUI()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(28)
                    ).apply { setMargins(0, 2, 0, 0) }
                }
                line.addView(unequipBtn)
            }

            ringsContainer.addView(line)
        }

        // 可吸收魂环按钮
        if (s.soulRings.size < maxRings) {
            val nextGrade = GameEngine.getNextSoulRingGrade()
            val year = RingYear.entries[nextGrade]
            val cost = RingYear.cost(nextGrade, s.soulRings.size)
            val btn = Button(requireContext()).apply {
                text = "吸收${year.displayName} (${GameEngine.formatNum(cost)}💰)"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(R.color.btn_normal))
                textSize = 12f
                isEnabled = s.gold >= cost
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.doAbsorbSoulRing(nextGrade)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(36)
                ).apply { setMargins(0, 6, 0, 2) }
            }
            ringsContainer.addView(btn)
        } else {
            ringsContainer.addView(TextView(requireContext()).apply {
                text = "魂环已满"
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(0, 3, 0, 3)
            })
        }

        // 魂骨 (含解锁提示)
        val maxBones = RealmData.maxSoulBones(s.level)
        tvSoulBoneInfo.text = "魂骨槽位: ${s.soulBones.size} / $maxBones"
        bonesContainer.removeAllViews()

        for (boneType in BoneType.entries) {
            val slotIdx = boneType.ordinal
            val isUnlocked = slotIdx < maxBones
            val line = LinearLayout(requireContext())
            line.orientation = LinearLayout.VERTICAL
            line.setPadding(0, 4, 0, 4)

            if (!isUnlocked) {
                val unlockLv = RealmData.boneSlotUnlockLevel(slotIdx)
                line.addView(TextView(requireContext()).apply {
                    text = "${boneType.displayName}: 🔀 Lv.${unlockLv} 解锁"
                    setTextColor(resources.getColor(R.color.text_secondary))
                    textSize = 12f
                })
                bonesContainer.addView(line)
                continue
            }

            val currentBone = s.soulBones[boneType.ordinal]

            val nameView = TextView(requireContext()).apply {
                if (currentBone != null && currentBone.rarityOrdinal in BoneRarity.entries.indices) {
                    val rarity = BoneRarity.entries[currentBone.rarityOrdinal]
                    val enhMult = EnhancementData.enhanceEffect(currentBone.enhanceLevel)
                    val affixStr = currentBone.affixes.joinToString("  ") {
                        val enhancedValue = (it.value * enhMult).toInt()
                        "${it.type.displayName}+${enhancedValue}"
                    }
                    val enhanceInfo = if (currentBone.enhanceLevel > 0) " +${currentBone.enhanceLevel}" else ""
                    text = "${boneType.displayName}: ${rarity.displayName}$enhanceInfo [$affixStr]"
                } else {
                    text = "${boneType.displayName}: 空"
                }
                setTextColor(if (currentBone != null) resources.getColor(R.color.text_gold) else resources.getColor(R.color.text_secondary))
                textSize = 13f
                setOnClickListener { showBoneDetail(boneType, currentBone) }
            }
            line.addView(nameView)

            // V2: 显示被动技能
            if (currentBone?.passiveSkill != null) {
                val ps = currentBone.passiveSkill
                val valAtRarity = ps.getValue(currentBone.combinedTier)
                line.addView(TextView(requireContext()).apply {
                    text = "    被动: ${ps.name} (${ps.type.displayName}) ${ps.description}: $valAtRarity"
                    setTextColor(resources.getColor(R.color.rarity_epic))
                    textSize = 11f
                })
            }

            // 操作按钮行（强化 + 卸下）
            val hasEnhance = currentBone != null && s.gold >= EnhancementData.enhanceCost(currentBone.enhanceLevel)
                && currentBone.enhanceLevel < EnhancementData.MAX_ENHANCE
            if (hasEnhance || currentBone != null) {
                val btnRow = LinearLayout(requireContext())
                btnRow.orientation = LinearLayout.HORIZONTAL
                btnRow.setPadding(0, 2, 0, 0)

                // 强化按钮
                if (hasEnhance) {
                    val nextCost = EnhancementData.enhanceCost(currentBone!!.enhanceLevel)
                    val enhBtn = Button(requireContext()).apply {
                        text = "🔤 强化 (${GameEngine.formatNum(nextCost)}💰)"
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(resources.getColor(R.color.btn_normal))
                        textSize = 12f
                        setPadding(10, 0, 10, 0)
                        addTouchFeedback()
                        setOnClickListener {
                            GameEngine.doEnhanceBone(boneType.ordinal)
                            updateUI()
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)
                        )
                    }
                    btnRow.addView(enhBtn)

                    val effectMult = EnhancementData.enhanceEffect(currentBone!!.enhanceLevel + 1)
                    val effectLabel = TextView(requireContext()).apply {
                        text = "→ ×${String.format("%.1f", effectMult)}"
                        setTextColor(resources.getColor(R.color.rarity_uncommon))
                        textSize = 11f
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(6, 0, 0, 0) }
                    }
                    btnRow.addView(effectLabel)
                }

                // 卸下按钮
                if (currentBone != null) {
                    val unequipBtn = Button(requireContext()).apply {
                        text = "卸下"
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(resources.getColor(R.color.rarity_mythic))
                        textSize = 11f
                        setPadding(8, 0, 8, 0)
                        addTouchFeedback()
                        setOnClickListener {
                            GameEngine.unequipBone(boneType.ordinal)
                            updateUI()
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)
                        ).apply { setMargins(6, 0, 0, 0) }
                    }
                    btnRow.addView(unequipBtn)
                }

                line.addView(btnRow)
            }

            if (currentBone == null && s.soulBones.size < maxBones) {
                val cheapTier = BoneYear.combinedTier(1, 0) // 千年·劣等 = tier 5
                val cheapCost = BoneYear.cost(cheapTier)
                if (s.gold >= cheapCost) {
                    val btn = Button(requireContext()).apply {
                        text = "购买千年魂骨(${GameEngine.formatNum(cheapCost)}💰)"
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(resources.getColor(R.color.btn_normal))
                        textSize = 11f
                        setPadding(8, 0, 8, 0)
                        addTouchFeedback()
                        setOnClickListener {
                            GameEngine.doEquipBone(boneType.ordinal, cheapTier)
                            updateUI()
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30)
                        )
                    }
                    line.addView(btn, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30)))
                }
            }
            bonesContainer.addView(line)
        }

        // 套装效果显示（含进度提示）
        setBonusContainer.removeAllViews()
        val equippedTypes = s.soulBones.keys.map { BoneType.entries[it] }.toSet()
        val allSetInfos = SoulBoneSetData.sets.map { set ->
            val matched = set.requiredSlots.count { it in equippedTypes }
            ActiveSetInfo(set, matched, set.requiredSlots.size)
        }.filter { it.matchedCount > 0 || it.totalCount <= maxBones }

        if (allSetInfos.isNotEmpty()) {
            val titleTv = TextView(requireContext()).apply {
                text = "🔟 套装效果"
                setTextColor(resources.getColor(R.color.rarity_legendary))
                textSize = 14f
                setPadding(0, 6, 0, 2)
            }
            setBonusContainer.addView(titleTv)

            for (info in allSetInfos) {
                val st = info.set
                val progressText = "${info.matchedCount}/${info.totalCount}"
                val lineTv = TextView(requireContext()).apply {
                    text = buildString {
                        append("${st.icon} ${st.name} [$progressText]")
                        if (info.hasTier2) {
                            append(" ✅ 2件 ${st.tier2Desc}")
                            if (info.isFullSet) {
                                append("\n       👑全套: ${st.fullDesc}")
                            }
                        } else if (info.matchedCount == 1) {
                            append(" → 再装1件激活2件效果")
                        } else {
                            append(" → 装备2件激活效果")
                        }
                    }
                    setTextColor(
                        if (info.isFullSet) resources.getColor(R.color.rarity_legendary)
                        else if (info.hasTier2) resources.getColor(R.color.accent)
                        else resources.getColor(R.color.text_secondary)
                    )
                    textSize = 12f
                    setPadding(0, 2, 0, 2)
                }
                setBonusContainer.addView(lineTv)
            }
        } else {
            setBonusContainer.addView(TextView(requireContext()).apply {
                text = "装备特定部位魂骨可激活套装效果"
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 11f
                setPadding(0, 4, 0, 0)
            })
        }

        // ======== 魂核显示 ========
        val firstCore = s.equippedSoulCores.values.firstOrNull { it != null }
        if (firstCore != null) {
            val rarity = Rarity.entries.getOrNull(firstCore.rarityOrdinal) ?: Rarity.COMMON
            val valStr = firstCore.passiveSkill.description.replace("%", "") + " " + firstCore.passiveSkill.getSoulCoreValue(firstCore.rarityOrdinal)
            tvSoulCoreEquipInfo.text = "💘 ${firstCore.name} (${rarity.displayName}) | ${firstCore.passiveSkill.description}: ${firstCore.passiveSkill.getSoulCoreValue(firstCore.rarityOrdinal)}"
            tvSoulCoreEquipInfo.setTextColor(rarity.colorInt)
        } else {
            tvSoulCoreEquipInfo.text = "未装备魂核（Boss商店抽取）"
            tvSoulCoreEquipInfo.setTextColor(resources.getColor(R.color.text_secondary))
        }
        soulCoreContainer.removeAllViews()
        if (s.backpackSoulCores.isNotEmpty()) {
            soulCoreContainer.addView(TextView(requireContext()).apply {
                text = "背包(${s.backpackSoulCores.size}):"
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_secondary))
                setPadding(0, 2, 0, 2)
            })
            for ((idx, sc) in s.backpackSoulCores.withIndex()) {
                val scRarity = Rarity.entries.getOrNull(sc.rarityOrdinal) ?: Rarity.COMMON
                soulCoreContainer.addView(TextView(requireContext()).apply {
                    text = "  ${sc.name} (${scRarity.displayName}) ${sc.passiveSkill.description}: ${sc.passiveSkill.getSoulCoreValue(sc.rarityOrdinal)}"
                    setTextColor(scRarity.colorInt)
                    textSize = 11f
                })
            }
        }

        // ======== 掉落背包 ========
        backpackContainer?.let { mainLayout.removeView(it) }
        val bpContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dpToPx(10), 0, dpToPx(16)) }
        }
        backpackContainer = bpContainer

        // 标题行：容量 + 扩容按钮
        val titleRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 6)
        }
        val bpTitle = TextView(requireContext()).apply {
            val cap = s.backpackCapacity
            val total = s.backpackRings.size + s.backpackBones.size
            text = "🎓 掉落背包 ($total/$cap)"
            setTextColor(resources.getColor(R.color.accent))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(bpTitle)
        val expandBtn = Button(requireContext()).apply {
            val cost = GameEngine.expandBackpackCost()
            text = "扩容 ${GameEngine.formatNum(cost)}💰"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(resources.getColor(R.color.btn_hunt))
            textSize = 11f
            setPadding(8, 0, 8, 0)
            addTouchFeedback()
            setOnClickListener {
                if (GameEngine.expandBackpack()) updateUI()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30)
            )
        }
        titleRow.addView(expandBtn)
        bpContainer.addView(titleRow)

        // 管理按钮行：整理 + 一键卖出
        val mgmtRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 4)
        }
        val sortRingBtn = Button(requireContext()).apply {
            text = "🔧 整理魂环"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(resources.getColor(R.color.btn_normal))
            textSize = 11f
            setPadding(6, 0, 6, 0)
            addTouchFeedback()
            setOnClickListener { GameEngine.sortBackpackRings(); updateUI() }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(28), 1f)
        }
        mgmtRow.addView(sortRingBtn)
        val sellAllRingsBtn = Button(requireContext()).apply {
            text = "💰 全卖魂环"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(resources.getColor(R.color.btn_hunt))
            textSize = 11f
            setPadding(6, 0, 6, 0)
            addTouchFeedback()
            setOnClickListener { GameEngine.sellAllBackpackRings(); updateUI() }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply { setMargins(4, 0, 0, 0) }
        }
        mgmtRow.addView(sellAllRingsBtn)
        bpContainer.addView(mgmtRow)

        val mgmtRow2 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 4)
        }
        val sortBoneBtn = Button(requireContext()).apply {
            text = "🔧 整理魂骨"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(resources.getColor(R.color.btn_normal))
            textSize = 11f
            setPadding(6, 0, 6, 0)
            addTouchFeedback()
            setOnClickListener { GameEngine.sortBackpackBones(); updateUI() }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(28), 1f)
        }
        mgmtRow2.addView(sortBoneBtn)
        val sellAllBonesBtn = Button(requireContext()).apply {
            text = "💰 全卖魂骨"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(resources.getColor(R.color.btn_hunt))
            textSize = 11f
            setPadding(6, 0, 6, 0)
            addTouchFeedback()
            setOnClickListener { GameEngine.sellAllBackpackBones(); updateUI() }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply { setMargins(4, 0, 0, 0) }
        }
        mgmtRow2.addView(sellAllBonesBtn)
        bpContainer.addView(mgmtRow2)

        if (s.backpackRings.isEmpty() && s.backpackBones.isEmpty()) {
            bpContainer.addView(TextView(requireContext()).apply {
                text = "暂无掉落物品\n击杀Boss有概率掉落魂环魂骨"
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(0, 6, 0, 0)
            })
        }

        // 魂环背包
        for ((idx, ring) in s.backpackRings.withIndex()) {
            if (ring.yearOrdinal !in RingYear.entries.indices) continue
            val year = RingYear.entries[ring.yearOrdinal]
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 0)
            }
            val infoTv = TextView(requireContext()).apply {
                val affixStr = ring.affixes.joinToString(" ") { "${it.type.displayName}+${it.value}" }
                text = "💍 ${year.displayName}: $affixStr"
                setTextColor(year.colorHex.toInt())
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(infoTv)
            val equipBtn = Button(requireContext()).apply {
                text = "装备"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(R.color.btn_normal))
                textSize = 12f
                setPadding(8, 0, 8, 0)
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.doEquipBackpackRing(idx)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)
                )
            }
            row.addView(equipBtn)
            val sellBtn = Button(requireContext()).apply {
                val price = GameEngine.calcRingSellPrice(ring.combinedTier)
                text = "卖出 ${GameEngine.formatNum(price)}"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(R.color.btn_hunt))
                textSize = 11f
                setPadding(8, 0, 8, 0)
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.sellBackpackRing(idx)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)
                ).apply { setMargins(4, 0, 0, 0) }
            }
            row.addView(sellBtn)
            bpContainer.addView(row)
        }

        // 魂骨背包
        for ((idx, bone) in s.backpackBones.withIndex()) {
            if (bone.rarityOrdinal !in BoneRarity.entries.indices) continue
            if (bone.boneTypeOrdinal !in BoneType.entries.indices) continue
            val rarity = BoneRarity.entries[bone.rarityOrdinal]
            val boneType = BoneType.entries[bone.boneTypeOrdinal]
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 0)
            }
            val infoTv = TextView(requireContext()).apply {
                val affixStr = bone.affixes.joinToString(" ") { "${it.type.displayName}+${it.value}" }
                text = "🦴 ${rarity.displayName}(${boneType.displayName}): $affixStr"
                setTextColor(resources.getColor(R.color.text_gold))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(infoTv)
            val equipBtn = Button(requireContext()).apply {
                text = "装备"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(R.color.btn_hunt))
                textSize = 12f
                setPadding(8, 0, 8, 0)
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.doEquipBackpackBone(idx)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)
                )
            }
            row.addView(equipBtn)
            val sellBtn = Button(requireContext()).apply {
                val price = GameEngine.calcBoneSellPrice(bone.combinedTier)
                text = "卖出 ${GameEngine.formatNum(price)}"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(R.color.btn_normal))
                textSize = 11f
                setPadding(8, 0, 8, 0)
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.sellBackpackBone(idx)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32)
                ).apply { setMargins(4, 0, 0, 0) }
            }
            row.addView(sellBtn)
            bpContainer.addView(row)
        }

        mainLayout.addView(bpContainer)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    /** 为按钮添加涟漪触摸反馈 */
    private fun Button.addTouchFeedback() {
        val _tv = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, _tv, true)
        try { foreground = context.getDrawable(_tv.resourceId) } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        GameEngine.removeListener(updateListener)
    }

    // ======== 详情弹窗 ========

    /** 魂环详情 */
    private fun showRingDetail(slotIdx: Int, ring: SoulRingInstance) {
        if (ring.yearOrdinal !in RingYear.entries.indices) return
        val year = RingYear.entries[ring.yearOrdinal]
        val slotName = SoulRingSlots.get(slotIdx)?.displayName ?: "魂环${slotIdx + 1}"
        val sb = StringBuilder()
        sb.appendLine("🔥 $slotName: ${year.displayName}")
        sb.appendLine("─────────────────────")
        sb.appendLine("词缀数量: ${ring.affixes.size}")
        sb.appendLine()
        sb.appendLine("📳 词缀详情:")
        for (affix in ring.affixes) {
            sb.appendLine("  ${affix.type.displayName} +${affix.value}")
        }

        // 显示总加成
        if (ring.affixes.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📱 属性汇总")
            val summary = mutableMapOf<String, Int>()
            for (affix in ring.affixes) {
                summary[affix.type.displayName] = (summary[affix.type.displayName] ?: 0) + affix.value
            }
            for ((type, value) in summary) {
                sb.appendLine("  $type +$value")
            }
        }

        showDetailDialog("魂环详情", sb.toString())
    }

    /** 魂骨详情 */
    private fun showBoneDetail(boneType: BoneType, bone: SoulBoneInstance?) {
        if (bone == null) {
            showDetailDialog("魂骨详情", "${boneType.displayName}: 空\n\n前往商店购买或杀戮之都获取魂骨！")
            return
        }
        if (bone.rarityOrdinal !in BoneRarity.entries.indices) return
        val rarity = BoneRarity.entries[bone.rarityOrdinal]
        val sb = StringBuilder()
        sb.appendLine("🦴 ${boneType.displayName}")
        sb.appendLine("品质: ${rarity.displayName}")
        sb.appendLine("强化等级: +${bone.enhanceLevel} / +${EnhancementData.MAX_ENHANCE}")
        sb.appendLine("─────────────────────")
        sb.appendLine("词缀数量: ${bone.affixes.size}")
        sb.appendLine()

        val enhMult = EnhancementData.enhanceEffect(bone.enhanceLevel)
        sb.appendLine("📳 词缀详情 (强化加成 ×${String.format("%.1f", enhMult)}):")
        for (affix in bone.affixes) {
            val enhancedValue = (affix.value * enhMult).toInt()
            sb.appendLine("  ${affix.type.displayName} +${affix.value} → +${enhancedValue}")
        }

        // 显示总加成
        if (bone.affixes.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📱 当前总加成")
            val summary = mutableMapOf<String, Int>()
            for (affix in bone.affixes) {
                val enhancedValue = (affix.value * enhMult).toInt()
                summary[affix.type.displayName] = (summary[affix.type.displayName] ?: 0) + enhancedValue
            }
            for ((type, value) in summary) {
                sb.appendLine("  $type +$value")
            }
        }

        // 下次强化预览
        if (bone.enhanceLevel < EnhancementData.MAX_ENHANCE) {
            val nextCost = EnhancementData.enhanceCost(bone.enhanceLevel)
            val nextMult = EnhancementData.enhanceEffect(bone.enhanceLevel + 1)
            sb.appendLine()
            sb.appendLine("🔤 下次强化: ${GameEngine.formatNum(nextCost)}💰 → ×${String.format("%.1f", nextMult)}")
        }

        showDetailDialog("魂骨详情", sb.toString())
    }

    /** 武魂详情 */
    private fun showWuhunDetail() {
        val soul = GameEngine.state.martialSoul ?: run {
            showDetailDialog("武魂详情", "武魂未觉醒\n\n前往修炼页点击【觉醒】来觉醒你的武魂！")
            return
        }
        val sb = StringBuilder()
        sb.appendLine("✨${soul.name}")
        sb.appendLine("品质: ${soul.rarity.displayName}")
        sb.appendLine("─────────────────────")
        sb.appendLine("基础属性加成")
        sb.appendLine("  HP +${soul.baseHp}")
        sb.appendLine("  物攻 +${soul.baseAtk}  魔攻 +${soul.baseMatk}")
        sb.appendLine("  物防 +${soul.pdef}  魔防 +${soul.mdef}")
        sb.appendLine("  暴击率 +${soul.critRate}%  爆伤 +${soul.critDmg}%")
        sb.appendLine("  自动金币 +${soul.autoGoldBonus}")
        sb.appendLine()
        sb.appendLine("⚡ 武魂技能 ${soul.skill.name}")
        sb.appendLine("  类型: ${soul.skill.type.displayName}")
        sb.appendLine("  描述: ${soul.skill.description}")
        sb.appendLine("  威力: ${soul.skill.power}%  冷却: ${soul.skill.cooldown}回合")
        if (soul.description.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📉 ${soul.description}")
        }

        showDetailDialog("武魂详情", sb.toString())
    }

    private fun showDetailDialog(title: String, content: String) {
        val textView = TextView(requireContext()).apply {
            text = content
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary))
            setPadding(24, 16, 24, 16)
            setLineSpacing(4f, 1.2f)
        }
        val scrollView = ScrollView(requireContext()).apply {
            addView(textView)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .show()
    }
}
