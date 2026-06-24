package com.example.garygame.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.garygame.R
import com.example.garygame.engine.GameEngine
import com.example.garygame.model.*

class AdventureFragment : Fragment() {

    private lateinit var tvCurrentMap: TextView
    private lateinit var tvMapFloor: TextView
    private lateinit var tvMapEffect: TextView
    private lateinit var btnResetMap: Button
    private lateinit var mapContainer: LinearLayout
    private lateinit var tvTowerInfo: TextView
    private lateinit var tvTowerPower: TextView
    private lateinit var btnTower: Button
    private lateinit var achievementContainer: LinearLayout
    private lateinit var codexContainer: LinearLayout

    private val updateListener = { updateUI() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_adventure, container, false)

        tvCurrentMap = view.findViewById(R.id.tv_current_map)
        tvMapFloor = view.findViewById(R.id.tv_map_floor)
        tvMapEffect = view.findViewById(R.id.tv_map_effect)
        btnResetMap = view.findViewById(R.id.btn_reset_map)
        mapContainer = view.findViewById(R.id.map_container)
        tvTowerInfo = view.findViewById(R.id.tv_tower_info)
        tvTowerPower = view.findViewById(R.id.tv_tower_power)
        btnTower = view.findViewById(R.id.btn_tower_challenge)
        achievementContainer = view.findViewById(R.id.achievement_container)
        codexContainer = view.findViewById(R.id.codex_container)

        btnTower.setOnClickListener {
            GameEngine.doTowerChallenge()
            updateUI()
        }

        btnResetMap.setOnClickListener {
            GameEngine.doResetMap()
            updateUI()
        }

        // 地图自动跳转开关
        view.findViewById<Button>(R.id.btn_auto_advance).setOnClickListener {
            GameEngine.state.autoAdvanceMap = !GameEngine.state.autoAdvanceMap
            updateUI()
        }

        GameEngine.addListener(updateListener)
        updateUI()
        return view
    }

    private fun updateUI() {
        val s = GameEngine.state

        // 当前地图信息
        val currentMap = MapData.getMap(s.currentMapId)
        val stageType = StageData.getStageType(s.currentStage)
        val stageTypeName = when (stageType) {
            StageType.NORMAL -> "普通"
            StageType.ELITE -> "精英"
            StageType.BOSS -> "Boss"
        }
        tvCurrentMap.text = currentMap?.name ?: "未知"
        tvMapFloor.text = "关卡 ${s.currentStage}/${StageData.STAGES_PER_MAP} (${stageTypeName}) | ${s.currentMonsters.size}v${s.currentMonsters.size}波"

        // 地图自动跳转开关
        val autoAdvBtn = view?.findViewById<Button>(R.id.btn_auto_advance)
        if (autoAdvBtn != null) {
            autoAdvBtn.text = if (s.autoAdvanceMap) "🗺️ 自动跳图: 开" else "🗺️ 自动跳图: 关"
            autoAdvBtn.setBackgroundColor(if (s.autoAdvanceMap) resources.getColor(R.color.btn_hunt) else resources.getColor(R.color.card_bg))
            autoAdvBtn.setTextColor(if (s.autoAdvanceMap) android.graphics.Color.WHITE else resources.getColor(R.color.text_secondary))
        }

        // 显示地图效果
        if (currentMap?.effect != null && currentMap.effect.description != "无特殊效果") {
            tvMapEffect.text = "📍 ${currentMap.effect.description}"
            tvMapEffect.visibility = View.VISIBLE
        } else {
            tvMapEffect.visibility = View.GONE
        }

        // 地图列表
        mapContainer.removeAllViews()
        for (mapDef in MapData.all) {
            val isCurrent = mapDef.id == s.currentMapId
            val isUnlocked = GameEngine.isMapUnlocked(mapDef.id)

            val item = LinearLayout(requireContext())
            item.orientation = LinearLayout.HORIZONTAL
            item.setPadding(0, 8, 0, 8)

            val nameView = TextView(requireContext()).apply {
                text = "${mapDef.name} (Lv.${mapDef.unlockLevel})"
                setTextColor(when {
                    isCurrent -> resources.getColor(R.color.text_gold)
                    isUnlocked -> resources.getColor(R.color.text_primary)
                    else -> resources.getColor(R.color.rarity_common)
                })
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val infoView = TextView(requireContext()).apply {
                text = if (isCurrent) "当前"
                       else if (isUnlocked) "可进入"
                       else "🔒${GameEngine.formatNum(mapDef.unlockCost.toLong())}💰"
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 0) }
            }

            item.addView(nameView)
            item.addView(infoView)

            if (!isCurrent && isUnlocked) {
                val enterBtn = Button(requireContext()).apply {
                    text = "进入"
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(resources.getColor(R.color.btn_normal))
                    textSize = 12f
                    setOnClickListener {
                        GameEngine.doChangeMap(mapDef.id)
                        updateUI()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(60), dpToPx(36)
                    )
                }
                item.addView(enterBtn)
            }

            mapContainer.addView(item)

            if (mapDef.id < MapData.all.size - 1) {
                val divider = View(requireContext()).apply {
                    setBackgroundColor(resources.getColor(R.color.divider))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                }
                mapContainer.addView(divider)
            }
        }

        // 杀戮之都
        val nextFloor = s.towerFloor + 1
        tvTowerInfo.text = "当前层：${s.towerFloor} / ${TowerData.MAX_FLOOR}"
        if (nextFloor <= TowerData.MAX_FLOOR) {
            val recPower = TowerData.monsterPower(nextFloor)
            tvTowerPower.text = "下一层推荐战力：${GameEngine.formatNum(recPower)}"
        } else {
            tvTowerPower.text = "已通关所有层！"
        }
        btnTower.isEnabled = nextFloor <= TowerData.MAX_FLOOR

        // 成就
        achievementContainer.removeAllViews()
        var hasAchievement = false
        for (ach in AchievementDefs.all) {
            val isUnlocked = s.unlockedAchievements.contains(ach.id)
            val tv = TextView(requireContext()).apply {
                val prefix = if (isUnlocked) "\u2705 " else "\uD83D\uDD12 "
                text = prefix + ach.name + " (" + ach.rewards.description() + ")"
                setTextColor(if (isUnlocked) resources.getColor(R.color.text_gold) else resources.getColor(R.color.rarity_common))
                textSize = 13f
                setPadding(0, 3, 0, 3)
            }
            achievementContainer.addView(tv)
            hasAchievement = true
        }
        if (!hasAchievement) {
            achievementContainer.addView(TextView(requireContext()).apply {
                text = "暂无成就"
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 13f
            })
        }

        // 图鉴系统
        codexContainer.removeAllViews()
        val kills = s.codexKills
        val title = CodexData.getTitle(kills)
        val atkBonus = CodexData.codexAtkBonus(kills)
        val nextMilestone = CodexData.nextMilestone(kills)

        codexContainer.addView(TextView(requireContext()).apply {
            text = "总击杀: ${GameEngine.formatNum(kills)}"
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 14f
            setPadding(0, 2, 0, 2)
        })
        codexContainer.addView(TextView(requireContext()).apply {
            text = "称号: $title"
            setTextColor(resources.getColor(R.color.text_gold))
            textSize = 14f
            setPadding(0, 2, 0, 2)
        })
        codexContainer.addView(TextView(requireContext()).apply {
            text = "图鉴攻击加成: +$atkBonus"
            setTextColor(resources.getColor(R.color.rarity_uncommon))
            textSize = 13f
            setPadding(0, 2, 0, 2)
        })
        if (nextMilestone != null) {
            codexContainer.addView(TextView(requireContext()).apply {
                text = "下一称号: ${nextMilestone.second} (${GameEngine.formatNum(nextMilestone.first)}击杀)"
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 11f
                setPadding(0, 2, 0, 2)
            })
        } else {
            codexContainer.addView(TextView(requireContext()).apply {
                text = "🏆 已达最高称号「征服者」!"
                setTextColor(resources.getColor(R.color.rarity_mythic))
                textSize = 12f
                setPadding(0, 2, 0, 2)
            })
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        GameEngine.removeListener(updateListener)
    }
}
