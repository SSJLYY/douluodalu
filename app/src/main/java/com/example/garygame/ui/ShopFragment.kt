package com.example.garygame.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class ShopFragment : Fragment() {

    private lateinit var tvBossCoin: TextView
    private lateinit var tvExpandDesc: TextView
    private lateinit var tvExpandCost: TextView
    private lateinit var shopExpand: LinearLayout
    private lateinit var tvSoulCoreDesc: TextView
    private lateinit var tvSoulCoreCost: TextView
    private lateinit var shopSoulCore: LinearLayout
    private lateinit var tvRefreshDesc: TextView
    private lateinit var tvRefreshCost: TextView
    private lateinit var shopRefresh: LinearLayout

    // Boss商店
    private lateinit var bossShopContainer: LinearLayout
    private lateinit var tvGachaCost: TextView
    private lateinit var tvSoulCrystalCost: TextView
    private lateinit var btnGacha: Button
    private lateinit var btnGachaDetail: Button
    private lateinit var btnSoulCrystal: Button
    private lateinit var bossRingSection: LinearLayout
    private lateinit var bossBoneSection: LinearLayout
    private lateinit var tvSoulCoreEquipped: TextView
    private lateinit var soulCoreInventoryContainer: LinearLayout
    // 限时珍品
    private lateinit var tvLimitedTimer: TextView
    private lateinit var limitedShopItems: LinearLayout
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateLimitedShopTimer()
            timerHandler.postDelayed(this, 1000L)
        }
    }

    private val updateListener = { updateUI() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_shop, container, false)

        tvBossCoin = view.findViewById(R.id.tv_shop_boss_coin)

        // 金币消耗
        tvExpandDesc = view.findViewById(R.id.tv_shop_auto_desc)
        tvExpandCost = view.findViewById(R.id.tv_shop_auto_cost)
        shopExpand = view.findViewById(R.id.shop_item_auto)

        // 金币抽卡
        tvSoulCoreDesc = view.findViewById(R.id.tv_shop_soulcore_desc)
        tvSoulCoreCost = view.findViewById(R.id.tv_shop_soulcore_cost)
        shopSoulCore = view.findViewById(R.id.shop_item_soulcore)
        // 限时刷新
        tvRefreshDesc = view.findViewById(R.id.tv_shop_refresh_desc)
        tvRefreshCost = view.findViewById(R.id.tv_shop_refresh_cost)
        shopRefresh = view.findViewById(R.id.shop_item_refresh)

        // Boss商店
        bossShopContainer = view.findViewById(R.id.boss_shop_container)
        tvGachaCost = view.findViewById(R.id.tv_boss_gacha_cost)
        tvSoulCrystalCost = view.findViewById(R.id.tv_boss_crystal_cost)
        btnGacha = view.findViewById(R.id.btn_boss_gacha)
        btnGachaDetail = view.findViewById(R.id.btn_boss_gacha_detail)
        btnSoulCrystal = view.findViewById(R.id.btn_boss_crystal)
        bossRingSection = view.findViewById(R.id.boss_ring_section)
        bossBoneSection = view.findViewById(R.id.boss_bone_section)
        tvSoulCoreEquipped = view.findViewById(R.id.tv_soul_core_equipped)
        soulCoreInventoryContainer = view.findViewById(R.id.soul_core_inventory)

        // 限时珍品
        tvLimitedTimer = view.findViewById(R.id.tv_limited_timer)
        limitedShopItems = view.findViewById(R.id.limited_shop_items)

        // 金币消耗监听
        shopExpand.setOnClickListener { GameEngine.expandBackpack(); updateUI() }
        shopSoulCore.setOnClickListener { GameEngine.doGoldSoulCoreDraw(); updateUI() }
        shopRefresh.setOnClickListener { GameEngine.doGoldRefreshShop(); updateUI() }

        // Boss商店监听
        btnGacha.setOnClickListener {
            GameEngine.doSoulCoreGacha()
            updateUI()
        }
        btnGachaDetail.setOnClickListener {
            showSoulCorePoolDialog()
        }
        btnSoulCrystal.setOnClickListener {
            GameEngine.doBuySoulCrystal()
            updateUI()
        }

        GameEngine.addListener(updateListener)
        updateUI()
        timerHandler.post(timerRunnable)
        return view
    }

    private fun updateUI() {
        val s = GameEngine.state
        tvBossCoin.text = "Boss币: ${GameEngine.formatNum(s.bossCoin)}"

        // 金币消耗
        updateGoldSinks()

        // Boss商店
        updateBossShop()
        // 限时珍品
        updateLimitedShop()
    }

    private fun updateBossShop() {
        val s = GameEngine.state

        // Boss商店功能锁定
        if (!GameEngine.isFeatureUnlocked(GameEngine.FeatureType.BOSS_SHOP)) {
            bossShopContainer.visibility = View.GONE
            return
        }
        bossShopContainer.visibility = View.VISIBLE
        val mapId = s.currentMapId

        // 价格标签
        val gachaCost = BossShopData.SOUL_CORE_GACHA_COST
        tvGachaCost.text = "$gachaCost Boss币"
        tvSoulCrystalCost.text = "${BossShopData.SOUL_CRYSTAL_COST} Boss币"

        btnGacha.isEnabled = s.bossCoin >= gachaCost
        btnSoulCrystal.isEnabled = s.bossCoin >= BossShopData.SOUL_CRYSTAL_COST

        // ======== 魂核过滤系统 ========
        // 稀有度过滤
        val filterSection = bossShopContainer.findViewWithTag<LinearLayout>("soulCoreFilter")
        if (filterSection != null) bossShopContainer.removeView(filterSection)

        val filterLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            tag = "soulCoreFilter"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dpToPx(6), 0, 0) }
        }

        // 过滤标题
        filterLayout.addView(TextView(requireContext()).apply {
            text = "🎯 魂核过滤（被过滤的抽取后自动分解）"
            setTextColor(resources.getColor(R.color.accent, null))
            textSize = 11f
        })

        // 稀有度过滤行
        val rarityRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dpToPx(4), 0, 0) }
        }
        rarityRow.addView(TextView(requireContext()).apply {
            text = "稀有度:"
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dpToPx(4), 0) }
        })
        for (rarity in 0..5) {
            val isFiltered = s.soulCoreFilterRarities.contains(rarity)
            val rarityName = Rarity.entries.getOrNull(rarity)?.displayName ?: ""
            val btn = Button(requireContext()).apply {
                text = if (isFiltered) "✅${rarityName}" else rarityName
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(
                    if (isFiltered) R.color.rarity_mythic
                    else R.color.text_secondary
                ))
                textSize = 10f
                setPadding(dpToPx(6), 0, dpToPx(6), 0)
                minHeight = dpToPx(30)
                minWidth = dpToPx(40)
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.toggleSoulCoreRarityFilter(rarity)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30)
                ).apply { setMargins(dpToPx(2), 0, dpToPx(2), 0) }
            }
            rarityRow.addView(btn)
        }
        filterLayout.addView(rarityRow)

        // 分类过滤行
        val allCategories = SoulCoreCategory.entries.toList()
        // 按compatibleSlots分组显示
        val slotGroups = mapOf(
            "⚔️攻击" to allCategories.filter { it.compatibleSlots == listOf(SoulCoreSlotType.ATTACK) },
            "🛡️防御" to allCategories.filter { it.compatibleSlots.size == 2 && SoulCoreSlotType.DEFENSE in it.compatibleSlots },
            "✨通用" to allCategories.filter { it.compatibleSlots.size >= 3 }
        )
        for ((groupName, cats) in slotGroups) {
            if (cats.isEmpty()) continue
            val catRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(2), 0, 0) }
            }
            catRow.addView(TextView(requireContext()).apply {
                text = "$groupName:"
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dpToPx(2), 0) }
            })
            for (cat in cats) {
                val isFiltered = s.soulCoreFilterCategories.contains(cat)
                val btn = Button(requireContext()).apply {
                    text = if (isFiltered) "✅${cat.displayName}" else cat.displayName
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(resources.getColor(
                        if (isFiltered) R.color.rarity_mythic
                        else R.color.text_secondary
                    ))
                    textSize = 10f
                    setPadding(dpToPx(4), 0, dpToPx(4), 0)
                    minHeight = dpToPx(28)
                    addTouchFeedback()
                    setOnClickListener {
                        GameEngine.toggleSoulCoreCategoryFilter(cat)
                        updateUI()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(28)
                    ).apply { setMargins(dpToPx(1), 0, dpToPx(1), 0) }
                }
                catRow.addView(btn)
            }
            filterLayout.addView(catRow)
        }

        // 将过滤区插入到bossShopContainer中（魂环商品前）
        val ringSectionIndex = bossShopContainer.indexOfChild(bossRingSection)
        bossShopContainer.addView(filterLayout, ringSectionIndex)

        // ======== 动态构建阶梯魂环商品 ========
        bossRingSection.removeAllViews()
        val allRings = BossShopData.getAllRingItems()
        var lastTier: BossShopData.BossShopTier? = null
        for (item in allRings) {
            val unlocked = BossShopData.isTierUnlocked(item.tier, mapId)
            val canBuy = unlocked && s.bossCoin >= item.cost

            if (item.tier != lastTier) {
                lastTier = item.tier
                bossRingSection.addView(TextView(requireContext()).apply {
                    text = if (unlocked) "━━ ${item.tier.label}阶梯（已解锁）━━"
                           else "🔒 ${item.tier.label}阶梯（${item.tier.description}）"
                    setTextColor(if (unlocked) resources.getColor(R.color.accent, null)
                                 else resources.getColor(R.color.text_secondary, null))
                    textSize = 11f
                    setPadding(0, dpToPx(6), 0, dpToPx(2))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                background = resources.getDrawable(R.drawable.card_bg, null)
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                alpha = if (unlocked) 1f else 0.55f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(2), 0, 0) }
            }
            row.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val year = RingQuality.entries.getOrNull(item.yearOrdinal)
                text = "${item.icon} ${item.label}"
                setTextColor(if (unlocked) year?.colorHex?.toInt() ?: resources.getColor(R.color.text_primary, null)
                             else resources.getColor(R.color.text_secondary, null))
                textSize = 13f
            })

            row.addView(TextView(requireContext()).apply {
                text = "${item.cost} Boss币"
                setTextColor(if (canBuy) resources.getColor(R.color.text_gold, null) else resources.getColor(R.color.rarity_mythic, null))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dpToPx(6), 0) }
            })
            val buyBtn = Button(requireContext()).apply {
                text = if (unlocked) "购买" else "🔒"
                isEnabled = canBuy
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(if (canBuy) R.color.btn_cultivate else R.color.text_secondary))
                textSize = 12f
                setPadding(dpToPx(14), 0, dpToPx(14), 0)
                minHeight = dpToPx(40)
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.doBuyBossRing(item.yearOrdinal)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(40)
                )
            }
            row.addView(buyBtn)
            bossRingSection.addView(row)
        }

        // ======== 动态构建阶梯魂骨商品 ========
        bossBoneSection.removeAllViews()
        val allBones = BossShopData.getAllBoneItems()
        lastTier = null
        for (item in allBones) {
            val unlocked = BossShopData.isTierUnlocked(item.tier, mapId)
            val canBuy = unlocked && s.bossCoin >= item.cost

            if (item.tier != lastTier) {
                lastTier = item.tier
                bossBoneSection.addView(TextView(requireContext()).apply {
                    text = if (unlocked) "━━ ${item.tier.label}阶梯（已解锁）━━"
                           else "🔒 ${item.tier.label}阶梯（${item.tier.description}）"
                    setTextColor(if (unlocked) resources.getColor(R.color.accent, null)
                                 else resources.getColor(R.color.text_secondary, null))
                    textSize = 11f
                    setPadding(0, dpToPx(6), 0, dpToPx(2))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                background = resources.getDrawable(R.drawable.card_bg, null)
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                alpha = if (unlocked) 1f else 0.55f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(2), 0, 0) }
            }
            row.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${item.icon} ${item.label}"
                setTextColor(if (unlocked) resources.getColor(R.color.text_primary, null)
                             else resources.getColor(R.color.text_secondary, null))
                textSize = 13f
            })

            row.addView(TextView(requireContext()).apply {
                text = "${item.cost} Boss币"
                setTextColor(if (canBuy) resources.getColor(R.color.text_gold, null) else resources.getColor(R.color.rarity_mythic, null))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dpToPx(6), 0) }
            })
            val buyBtn = Button(requireContext()).apply {
                text = if (unlocked) "购买" else "🔒"
                isEnabled = canBuy
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(if (canBuy) R.color.btn_prestige else R.color.text_secondary))
                textSize = 12f
                setPadding(dpToPx(14), 0, dpToPx(14), 0)
                minHeight = dpToPx(40)
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.doBuyBossBone(item.rarityOrdinal)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(40)
                )
            }
            row.addView(buyBtn)
            bossBoneSection.addView(row)
        }

        // 多槽位已装备魂核显示
        soulCoreInventoryContainer.removeAllViews()

        // 标题：魂核装备系统
        soulCoreInventoryContainer.addView(TextView(requireContext()).apply {
            text = "━━━ 魂核装备系统（多槽位）━━━"
            setTextColor(resources.getColor(R.color.accent, null))
            textSize = 12f
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        })

        // 显示各槽位
        for (slot in SoulCoreSlotType.entries) {
            val isUnlocked = SoulCoreSlotType.isUnlocked(slot, s.prestigeCount)
            val equipped = s.equippedSoulCores[slot]
            val slotRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                background = resources.getDrawable(R.drawable.card_bg, null)
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                alpha = if (isUnlocked) 1f else 0.45f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(3), 0, 0) }
            }

            val slotText = if (!isUnlocked) {
                "🔒 ${slot.icon} ${slot.displayName}（需转生${slot.minPrestige}次）"
            } else if (equipped != null) {
                val rarity = Rarity.entries.getOrNull(equipped.rarityOrdinal) ?: Rarity.COMMON
                val valStr = equipped.passiveSkill.description.replace("%", "") + " " + equipped.effectiveValue
                "${slot.icon} ${slot.displayName}: ${equipped.name} (${rarity.displayName}) Lv.${equipped.level}\n${equipped.passiveSkill.description}: ${equipped.effectiveValue}"
            } else {
                "${slot.icon} ${slot.displayName}: 空"
            }

            slotRow.addView(TextView(requireContext()).apply {
                text = slotText
                setTextColor(
                    if (!isUnlocked) resources.getColor(R.color.text_secondary, null)
                    else if (equipped != null) {
                        val rarity = Rarity.entries.getOrNull(equipped.rarityOrdinal) ?: Rarity.COMMON
                        rarity.colorInt
                    } else resources.getColor(R.color.text_primary, null)
                )
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            // 卸下按钮
            if (isUnlocked && equipped != null) {
                val unequipBtn = Button(requireContext()).apply {
                    text = "卸下"
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(resources.getColor(R.color.rarity_mythic, null))
                    textSize = 11f
                    setPadding(dpToPx(8), 0, dpToPx(8), 0)
                    minHeight = dpToPx(36)
                    addTouchFeedback()
                    setOnClickListener {
                        GameEngine.doUnequipSoulCore(slot)
                        updateUI()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36)
                    )
                }
                slotRow.addView(unequipBtn)
            }
            soulCoreInventoryContainer.addView(slotRow)
        }

        // 分隔
        soulCoreInventoryContainer.addView(TextView(requireContext()).apply {
            text = "── 魂核背包 ──"
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 11f
            setPadding(0, dpToPx(6), 0, dpToPx(2))
        })

        // 背包魂核列表
        if (s.backpackSoulCores.isEmpty()) {
            soulCoreInventoryContainer.addView(TextView(requireContext()).apply {
                text = "背包无魂核，请在上方抽取"
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 12f
            })
        } else {
            for ((idx, core) in s.backpackSoulCores.withIndex()) {
                val rarity = Rarity.entries.getOrNull(core.rarityOrdinal) ?: Rarity.COMMON
                val isMaxLevel = core.level >= SoulCoreLevelData.MAX_LEVEL
                val canUpgrade = !isMaxLevel
                val slotNames = core.category.compatibleSlots.joinToString("/") { it.icon }

                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dpToPx(2), 0, dpToPx(2))
                }
                row.addView(TextView(requireContext()).apply {
                    text = "${core.name} (${rarity.displayName}) Lv.${core.level} [${slotNames}]\n${core.passiveSkill.description}: ${core.effectiveValue}"
                    setTextColor(rarity.colorInt)
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                // 选择装备槽位对话框
                val equipBtn = Button(requireContext()).apply {
                    text = "装备"
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(resources.getColor(R.color.btn_normal, null))
                    textSize = 11f
                    setPadding(dpToPx(8), 0, dpToPx(8), 0)
                    minHeight = dpToPx(36)
                    addTouchFeedback()
                    setOnClickListener {
                        showSoulCoreEquipDialog(idx, core)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36)
                    ).apply { setMargins(2, 0, 2, 0) }
                }
                row.addView(equipBtn)

                // 升级按钮
                if (canUpgrade) {
                    val need = SoulCoreLevelData.requiredCopies(core.level)
                    val upgBtn = Button(requireContext()).apply {
                        text = "升级"
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(resources.getColor(R.color.btn_prestige, null))
                        textSize = 11f
                        setPadding(dpToPx(8), 0, dpToPx(8), 0)
                        minHeight = dpToPx(36)
                        addTouchFeedback()
                        setOnClickListener {
                            GameEngine.doUpgradeSoulCore(idx)
                            updateUI()
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36)
                        )
                    }
                    row.addView(upgBtn)
                }
                soulCoreInventoryContainer.addView(row)
            }
        }
    }

    private fun updateGoldSinks() {
        val s = GameEngine.state

        // 商店功能锁定（Lv.3解锁基础购买）
        val shopLocked = !GameEngine.isFeatureUnlocked(GameEngine.FeatureType.SHOP)
        if (shopLocked) {
            tvExpandDesc.text = "🏪 商店 · 🔒 Lv.3解锁"
            tvExpandCost.text = "突破至Lv.3后可购买基础物资"
            tvSoulCoreDesc.text = ""
            tvSoulCoreCost.text = ""
            tvRefreshDesc.text = ""
            tvRefreshCost.text = ""
            shopExpand.isEnabled = false
            shopSoulCore.isEnabled = false
            shopRefresh.isEnabled = false
            return
        }
        val gold = s.gold

        // 背包扩容
        val expandCost = GameEngine.expandBackpackCost()
        val wealthLv = s.getTalentLevel(TalentBranch.WEALTH)
        val discount = when (wealthLv) { 1 -> 0.20; 2 -> 0.40; 3 -> 0.60; else -> 0.0 }
        val actualCost = (expandCost * (1.0 - discount)).toLong().coerceAtLeast(1L)
        val freeHint = if (s.freeExpandRemaining > 0) " (免费x${s.freeExpandRemaining})" else ""
        tvExpandDesc.text = "📦 当前容量: ${s.backpackTotalItems}/${s.backpackCapacity}${freeHint}"
        tvExpandCost.text = if (gold >= actualCost || s.freeExpandRemaining > 0) "${GameEngine.formatNum(actualCost)}💰" else "⛔${GameEngine.formatNum(actualCost)}🔥"

        // 魂核抽卡
        val scCost = GoldSinkData.SOUL_CORE_DRAW_COST
        tvSoulCoreDesc.text = "花费💰随机抽取魂核（受地图影响）"
        tvSoulCoreCost.text = if (gold >= scCost) "${GameEngine.formatNum(scCost)}💰" else "⛔${GameEngine.formatNum(scCost)}🔥"

        // 限时刷新
        val refreshCost = GoldSinkData.REFRESH_SHOP_COST
        tvRefreshDesc.text = "花费💰立即刷新限时珍品"
        tvRefreshCost.text = if (gold >= refreshCost) "${GameEngine.formatNum(refreshCost)}💰" else "⛔${GameEngine.formatNum(refreshCost)}🔥"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacks(timerRunnable)
        GameEngine.removeListener(updateListener)
    }

    private fun updateLimitedShop() {
        // 限时珍品功能锁定
        if (!GameEngine.isFeatureUnlocked(GameEngine.FeatureType.LIMITED_SHOP)) {
            tvLimitedTimer.text = "⏳限时珍品 · 🔒 Lv.65解锁"
            limitedShopItems.removeAllViews()
            return
        }
        GameEngine.refreshLimitedShop()
        val s = GameEngine.state
        limitedShopItems.removeAllViews()

        // 魂环商品
        for ((idx, ring) in s.limitedShopRings.withIndex()) {
            val ringTier = ring.combinedTier
            val price = LimitedShopData.ringBuyPrice(ringTier)
            val skillName = ring.skill?.name ?: "无技能"
            val affixStr = ring.affixes.joinToString(" ") { "${it.type.displayName}+${it.value}" }
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                background = resources.getDrawable(R.drawable.card_bg, null)
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(4), 0, 0) }
            }
            row.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "💍 ${RingQuality.fullDisplayName(ringTier)}\n🎯 ${skillName} | $affixStr"
                setTextColor(RingQuality.yearColorInt(ring.yearOrdinal))
                textSize = 12f
            })

            val canBuy = s.gold >= price
            row.addView(TextView(requireContext()).apply {
                text = GameEngine.formatNum(price)
                setTextColor(if (canBuy) resources.getColor(R.color.text_gold, null) else resources.getColor(R.color.rarity_mythic, null))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dpToPx(6), 0) }
            })
            val buyBtn = Button(requireContext()).apply {
                text = "购买"
                isEnabled = canBuy
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(if (canBuy) R.color.btn_normal else R.color.text_secondary))
                textSize = 12f
                setPadding(dpToPx(14), 0, dpToPx(14), 0)
                minHeight = dpToPx(40)
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.buyLimitedShopRing(idx)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(40)
                )
            }
            row.addView(buyBtn)
            limitedShopItems.addView(row)
        }

        // 魂骨商品
        for ((idx, bone) in s.limitedShopBones.withIndex()) {
            val boneTier = bone.combinedTier
            val price = LimitedShopData.boneBuyPrice(boneTier)
            val typeName = BoneType.entries.getOrNull(bone.boneTypeOrdinal)?.displayName ?: "魂骨"
            val passiveName = bone.passiveSkill?.name ?: "无被动"
            val affixStr = bone.affixes.joinToString(" ") { "${it.type.displayName}+${it.value}" }
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                background = resources.getDrawable(R.drawable.card_bg, null)
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(4), 0, 0) }
            }
            row.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "🦴 ${BoneRarity.fullDisplayName(boneTier)}($typeName)\n🛡 $passiveName | $affixStr"
                setTextColor(BoneRarity.yearColorInt(bone.yearOrdinal))
                textSize = 12f
            })

            val canBuy = s.gold >= price
            row.addView(TextView(requireContext()).apply {
                text = GameEngine.formatNum(price)
                setTextColor(if (canBuy) resources.getColor(R.color.text_gold, null) else resources.getColor(R.color.rarity_mythic, null))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dpToPx(6), 0) }
            })
            val buyBtn = Button(requireContext()).apply {
                text = "购买"
                isEnabled = canBuy
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(if (canBuy) R.color.btn_normal else R.color.text_secondary))
                textSize = 12f
                setPadding(dpToPx(14), 0, dpToPx(14), 0)
                minHeight = dpToPx(40)
                addTouchFeedback()
                setOnClickListener {
                    GameEngine.buyLimitedShopBone(idx)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(40)
                )
            }
            row.addView(buyBtn)
            limitedShopItems.addView(row)
        }

        // 无商品时提示
        if (s.limitedShopRings.isEmpty() && s.limitedShopBones.isEmpty()) {
            limitedShopItems.addView(TextView(requireContext()).apply {
                text = "暂无商品，等待刷新中..."
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 13f
                setPadding(0, dpToPx(8), 0, dpToPx(8))
            })
        }

        updateLimitedShopTimer()
    }

    private fun updateLimitedShopTimer() {
        val sec = GameEngine.limitedShopRemainSec()
        val min = sec / 60
        val s = sec % 60
        tvLimitedTimer.text = String.format("%d:%02d", min, s)
        if (sec <= 0) {
            tvLimitedTimer.text = "刷新中..."
        }
    }

    /** 魂核奖池详情弹窗 */
    private fun showSoulCorePoolDialog() {
        val s = GameEngine.state
        val maxRarity = (s.currentMapId / 2).coerceIn(0, 5)
        val pool = SoulCorePool.all.filter { it.rarityOrdinal <= maxRarity }
        val mapName = MapData.getMap(s.currentMapId)?.name ?: "未知"

        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        // 头部信息
        dialogLayout.addView(TextView(requireContext()).apply {
            text = "📍 当前地图: $mapName | 最大稀有度: ${Rarity.entries.getOrNull(maxRarity)?.displayName ?: ""}"
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 12f
            setPadding(0, 0, 0, dpToPx(6))
        })
        dialogLayout.addView(TextView(requireContext()).apply {
            text = "💰 每次抽取: ${BossShopData.SOUL_CORE_GACHA_COST} Boss币 | 稀有度随机+0~2"
            setTextColor(resources.getColor(R.color.text_gold, null))
            textSize = 11f
            setPadding(0, 0, 0, dpToPx(8))
        })

        // 按稀有度分组显示奖池
        val grouped = pool.groupBy { it.rarityOrdinal }.toSortedMap()
        for ((rarity, cores) in grouped) {
            val rarityName = Rarity.entries.getOrNull(rarity)?.displayName ?: "未知"
            val rarityColor = Rarity.entries.getOrNull(rarity)?.colorInt ?: android.graphics.Color.WHITE

            // 分组标题
            val sectionBg = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                background = resources.getDrawable(R.drawable.card_bg, null)
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(4), 0, 0) }
            }

            val poolSize = pool.size
            val probEach = String.format("%.1f", 100.0 / poolSize)
            sectionBg.addView(TextView(requireContext()).apply {
                text = "${rarityName} (${cores.size}种) 各${probEach}%概率"
                setTextColor(rarityColor)
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            // 该稀有度下的每个魂核
            for (core in cores) {
                val coreRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                    setOnClickListener {
                        showSoulCoreDetailDialog(core)
                    }
                }
                coreRow.addView(TextView(requireContext()).apply {
                    text = "  🞄 ${core.name}"
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                coreRow.addView(TextView(requireContext()).apply {
                    text = "${core.category.displayName}"
                    setTextColor(resources.getColor(R.color.text_secondary, null))
                    textSize = 10f
                })
                coreRow.addView(TextView(requireContext()).apply {
                    text = " 🔍"
                    setTextColor(resources.getColor(R.color.accent, null))
                    textSize = 10f
                })
                sectionBg.addView(coreRow)
            }
            dialogLayout.addView(sectionBg)
        }

        // 说明文字
        dialogLayout.addView(TextView(requireContext()).apply {
            text = "\n💡 实际稀有度 = 基础稀有度 + 随机(0~2)"
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 11f
        })
        dialogLayout.addView(TextView(requireContext()).apply {
            text = "💡 点击魂核名称查看各稀有度效果值"
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 11f
        })

        // 滚动容器
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(480)
            )
            addView(dialogLayout)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("💠 魂核奖池详情")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    /** 魂核详细信息弹窗 */
    private fun showSoulCoreDetailDialog(core: SoulCoreDef) {
        val detailLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        // 基础信息
        detailLayout.addView(TextView(requireContext()).apply {
            text = "${core.name}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val rarityColor = Rarity.entries.getOrNull(core.rarityOrdinal)?.colorInt ?: android.graphics.Color.WHITE
            setTextColor(rarityColor)
        })
        detailLayout.addView(TextView(requireContext()).apply {
            text = "${core.description}"
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 12f
            setPadding(0, dpToPx(4), 0, 0)
        })
        detailLayout.addView(TextView(requireContext()).apply {
            text = "分类: ${core.category.displayName} | 可装: ${core.category.compatibleSlots.joinToString("/") { it.displayName }}"
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 12f
            setPadding(0, dpToPx(4), 0, dpToPx(8))
        })

        // 分割线
        detailLayout.addView(View(requireContext()).apply {
            setBackgroundColor(resources.getColor(R.color.text_secondary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
            )
        })

        // 各稀有度效果值表格
        detailLayout.addView(TextView(requireContext()).apply {
            text = "\n各稀有度效果值"
            setTextColor(resources.getColor(R.color.accent, null))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        for (rarity in 0..5) {
            val value = core.passiveSkill.getSoulCoreValue(rarity)
            val rarityName = Rarity.entries.getOrNull(rarity)?.displayName ?: ""
            val rarityColor = Rarity.entries.getOrNull(rarity)?.colorInt ?: android.graphics.Color.WHITE
            val desc = core.passiveSkill.description.replace("%", "") + " $value"

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(3), 0, dpToPx(3))
            }
            row.addView(TextView(requireContext()).apply {
                text = "$rarityName"
                setTextColor(rarityColor)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(60), LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            row.addView(TextView(requireContext()).apply {
                text = desc
                setTextColor(resources.getColor(R.color.text_primary, null))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            row.addView(TextView(requireContext()).apply {
                text = "💰${core.bossCoinCost}币"
                setTextColor(resources.getColor(R.color.text_gold, null))
                textSize = 11f
            })
            detailLayout.addView(row)
        }

        // 升级效果说明
        detailLayout.addView(TextView(requireContext()).apply {
            text = "\n升级效果: 每级效果×${String.format("%.0f", SoulCoreLevelData.effectMultiplier(1) * 100)}%"
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 11f
        })
        detailLayout.addView(TextView(requireContext()).apply {
            text = "满级(Lv.5): 效果×${String.format("%.0f", SoulCoreLevelData.effectMultiplier(5) * 100)}%"
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 11f
        })

        AlertDialog.Builder(requireContext())
            .setTitle("${core.name} 详情")
            .setView(detailLayout)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /** 魂核装备槽位选择弹窗 */
    private fun showSoulCoreEquipDialog(backpackIndex: Int, core: DroppedSoulCore) {
        val s = GameEngine.state
        val availableSlots = core.category.compatibleSlots.filter {
            SoulCoreSlotType.isUnlocked(it, s.prestigeCount)
        }
        if (availableSlots.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("❌ 无可装备槽位")
                .setMessage("${core.name}需要转生解锁对应槽位")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        // 生成带状态描述的选项列表（不单独设setMessage，避免与setItems冲突）
        val items = availableSlots.map { slot ->
            val equipped = s.equippedSoulCores[slot]
            val status = if (equipped != null) "（已装备: ${equipped.name}）" else "（空）"
            "${slot.icon} ${slot.displayName} $status"
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("${core.name} (Lv.${core.level}) - 选择槽位")
            .setItems(items) { _, which ->
                try {
                    val slot = availableSlots[which]
                    GameEngine.addLog("🔍 装备魂核: ${core.name} → ${slot.displayName}")
                    GameEngine.doEquipSoulCore(backpackIndex, slot)
                } catch (e: Exception) {
                    GameEngine.addLog("❌ 装备异常: ${e.message}")
                }
                updateUI()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 为按钮添加涟漪触摸反馈 */
    private fun Button.addTouchFeedback() {
        val _tv = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, _tv, true)
        try { foreground = context.getDrawable(_tv.resourceId) } catch (_: Exception) {}
    }
}
