package com.example.garygame.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.garygame.R
import com.example.garygame.engine.GameEngine
import com.example.garygame.model.*

class TalentFragment : Fragment() {

    private lateinit var tvTalentPoints: TextView
    private lateinit var tvTalentProgress: TextView
    private lateinit var tvTalentSummary: TextView
    private lateinit var tvPrestigeHint: TextView
    private lateinit var branchesContainer: LinearLayout

    private val updateListener = { updateUI() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_talent, container, false)

        tvTalentPoints = view.findViewById(R.id.tv_talent_points)
        tvTalentProgress = view.findViewById(R.id.tv_talent_progress)
        tvTalentSummary = view.findViewById(R.id.tv_talent_summary)
        tvPrestigeHint = view.findViewById(R.id.tv_prestige_hint)
        branchesContainer = view.findViewById(R.id.talent_branches_container)

        GameEngine.addListener(updateListener)
        updateUI()
        return view
    }

    private fun updateUI() {
        val s = GameEngine.state
        val canSpend = s.talentPoints > 0
        val spent = GameEngine.getSpentTalentPoints()
        val totalMax = TalentBranch.entries.sumOf { it.maxLevel }
        val allMaxed = spent >= totalMax

        // 可用点数
        tvTalentPoints.text = s.talentPoints.toString()
        tvTalentPoints.setTextColor(resources.getColor(
            if (canSpend) R.color.rarity_legendary else R.color.text_secondary
        ))

        // 总进度
        tvTalentProgress.text = "已消耗: $spent / $totalMax 天赋点"
        tvTalentProgress.setTextColor(resources.getColor(
            if (allMaxed) R.color.accent else R.color.text_secondary
        ))

        // 数值加成总结
        updateSummary(s)

        // 转生提示
        if (s.prestigeCount == 0) {
            tvPrestigeHint.visibility = View.VISIBLE
        } else {
            tvPrestigeHint.text = "🏅 已传承 ${s.prestigeCount} 世 | 累计获得 ${s.prestigeCount} 天赋点"
            tvPrestigeHint.visibility = View.VISIBLE
        }

        // 分支列表
        branchesContainer.removeAllViews()
        for (branch in TalentBranch.entries) {
            val level = s.getTalentLevel(branch)
            val isMaxed = level >= branch.maxLevel
            val canUpgrade = canSpend && !isMaxed

            // 分支卡片
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                background = resources.getDrawable(R.drawable.card_bg, null)
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(4), 0, 0) }
            }

            // == 第一行：图标 + 名称 + 等级 + 升级按钮 ==
            val headerRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // 图标
            headerRow.addView(TextView(requireContext()).apply {
                text = branch.icon
                textSize = 22f
                setPadding(0, 0, dpToPx(8), 0)
            })

            // 名称 + 等级
            headerRow.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${branch.displayName}  Lv.$level/${branch.maxLevel}"
                setTextColor(if (isMaxed) resources.getColor(R.color.accent, null) else resources.getColor(R.color.text_primary, null))
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            // 升级按钮
            val upgradeBtn = Button(requireContext()).apply {
                text = when {
                    isMaxed -> "已满"
                    canUpgrade -> "+升级"
                    else -> "加点"
                }
                isEnabled = canUpgrade
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(resources.getColor(
                    when {
                        isMaxed -> R.color.text_secondary
                        canUpgrade -> R.color.btn_hunt
                        else -> R.color.btn_disabled
                    }
                ))
                textSize = 13f
                setPadding(dpToPx(16), 0, dpToPx(16), 0)
                minHeight = dpToPx(40)
                addTouchFeedback()
                if (canUpgrade) {
                    setOnClickListener {
                        GameEngine.doSpendTalentPoint(branch)
                        updateUI()
                    }
                } else if (!isMaxed && !canSpend) {
                    // 没有可用点数时点击显示提�?
                    setOnClickListener {
                        GameEngine.addLog("⚠️ 天赋点不足！完成神位传承获得天赋点")
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(40)
                )
            }
            headerRow.addView(upgradeBtn)
            card.addView(headerRow)

            // == 进度�?==
            val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = branch.maxLevel
                progress = level
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8)
                ).apply { setMargins(0, dpToPx(6), 0, dpToPx(4)) }
                progressDrawable = resources.getDrawable(R.drawable.progress_soul_bg, null)
            }
            card.addView(progressBar)

            // == 当前效果 ==
            val effectDesc = TalentBranch.effectDescription(branch, level)
            card.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = if (level > 0) "�?$effectDesc" else "未激�?(暂无效果)"
                setTextColor(if (level > 0) resources.getColor(R.color.rarity_uncommon, null) else resources.getColor(R.color.text_secondary, null))
                textSize = 12f
            })

            // == 转生保留效果 ==
            val retentionDesc = TalentBranch.retentionDescription(branch, level)
            card.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(2), 0, 0) }
                text = if (level > 0) "🔄 $retentionDesc" else "🔄 $retentionDesc"
                setTextColor(resources.getColor(R.color.rarity_uncommon, null))
                textSize = 10f
            })

            // == 下一级预�?==
            if (!isMaxed) {
                val nextDesc = TalentBranch.nextLevelDescription(branch, level)
                card.addView(TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dpToPx(2), 0, 0) }
                    text = "下一�?�?$nextDesc"
                    setTextColor(resources.getColor(R.color.text_secondary, null))
                    textSize = 11f
                })
            }

            // == 分支描述 ==
            card.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(4), 0, 0) }
                text = "📖 ${branch.desc}"
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 11f
            })

            branchesContainer.addView(card)
        }
    }

    /** 计算所有天赋的汇总加成描�?*/
    private fun updateSummary(s: com.example.garygame.engine.GameState) {
        val warLv = s.getTalentLevel(TalentBranch.WAR_GOD)
        val soulLv = s.getTalentLevel(TalentBranch.SOUL_MASTER)
        val wealthLv = s.getTalentLevel(TalentBranch.WEALTH)
        val divineLv = s.getTalentLevel(TalentBranch.DIVINE)
        val spent = warLv + soulLv + wealthLv + divineLv

        if (spent == 0) {
            tvTalentSummary.visibility = View.GONE
            return
        }

        val parts = mutableListOf<String>()
        if (warLv > 0) {
            val atkPct = warLv * 6
            val defPct = (warLv * 3).coerceAtMost(9)
            parts.add("攻击+${atkPct}% 防御+${defPct}%")
            if (warLv >= 3) parts.add("暴击+5%")
        }
        if (soulLv > 0) {
            parts.add("词缀效果+${soulLv * 8}%")
        }
        if (wealthLv > 0) {
            parts.add(TalentBranch.effectDescription(TalentBranch.WEALTH, wealthLv))
        }
        if (divineLv > 0) {
            parts.add("生命+${divineLv * 10}%")
            if (divineLv >= 3) parts.add("减伤5%")
        }

        tvTalentSummary.text = "📊 当前总加�? ${parts.joinToString(" | ")}"
        tvTalentSummary.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        GameEngine.removeListener(updateListener)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /** 为按钮添加涟漪触摸反�?*/
    private fun Button.addTouchFeedback() {
        val _tv = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, _tv, true)
        try { foreground = context.getDrawable(_tv.resourceId) } catch (_: Exception) {}
    }
}
