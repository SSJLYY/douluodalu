package com.example.garygame.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.garygame.R
import com.example.garygame.engine.GameEngine
import com.example.garygame.model.*

class CultivationFragment : Fragment() {

    private lateinit var tvRealm: TextView
    private lateinit var tvWuhun: TextView
    private lateinit var tvHpLabel: TextView
    private lateinit var progressPlayerHp: ProgressBar
    private lateinit var tvAttack: TextView
    private lateinit var tvMatk: TextView
    private lateinit var tvCrit: TextView
    private lateinit var tvSoulPower: TextView
    private lateinit var tvPdef: TextView
    private lateinit var tvMdef: TextView
    private lateinit var tvRootBone: TextView

    private lateinit var layoutSkill: LinearLayout
    private lateinit var tvSkillName: TextView
    private lateinit var tvSkillCooldown: TextView

    // V2: 战斗魂力 + 自动突破
    private lateinit var tvBattleSoulPower: TextView
    private lateinit var cbAutoBreakthrough: CheckBox
    private lateinit var activeSkillContainer: LinearLayout

    private lateinit var tvBattlefieldTitle: TextView
    private lateinit var battlefieldContainer: LinearLayout

    private lateinit var btnBattle: Button
    private lateinit var btnAutoBattle: Button
    private lateinit var btnSpeed: Button

    private lateinit var tvProgressInfo: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCultivate: Button
    private lateinit var btnBreakthrough: Button
    private lateinit var btnAwaken: Button
    private lateinit var btnPrestige: Button
    private lateinit var logContainer: LinearLayout

    // 死亡遮罩
    private lateinit var deathOverlay: LinearLayout
    private lateinit var tvDeathTitle: TextView
    private lateinit var tvDeathInfo: TextView
    private lateinit var progressDeathRegen: ProgressBar

    // 卡级修炼
    private lateinit var cappingSection: LinearLayout
    private lateinit var tvCapTitle: TextView
    private lateinit var tvCapStatus: TextView
    private lateinit var tvCapExcess: TextView
    private lateinit var capStatContainer: LinearLayout
    private lateinit var btnCapToggle: Button

    // 天赋树
    private lateinit var talentSection: LinearLayout
    private lateinit var tvTalentPoints: TextView
    private lateinit var tvTalentProgress: TextView
    private lateinit var tvTalentSummary: TextView
    private lateinit var tvPrestigeHint: TextView
    private lateinit var talentBranchesContainer: LinearLayout

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateUI()
            refreshHandler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_cultivation, container, false)

        tvRealm = view.findViewById(R.id.tv_realm)
        tvWuhun = view.findViewById(R.id.tv_wuhun)
        tvHpLabel = view.findViewById(R.id.tv_hp_label)
        progressPlayerHp = view.findViewById(R.id.progress_player_hp)
        tvAttack = view.findViewById(R.id.tv_attack)
        tvMatk = view.findViewById(R.id.tv_matk)
        tvCrit = view.findViewById(R.id.tv_crit)
        tvSoulPower = view.findViewById(R.id.tv_soul_power)
        tvPdef = view.findViewById(R.id.tv_pdef)
        tvMdef = view.findViewById(R.id.tv_mdef)
        tvRootBone = view.findViewById(R.id.tv_root_bone)

        layoutSkill = view.findViewById(R.id.layout_skill)
        tvSkillName = view.findViewById(R.id.tv_skill_name)
        tvSkillCooldown = view.findViewById(R.id.tv_skill_cooldown)

        tvBattleSoulPower = view.findViewById(R.id.tv_battle_soul_power)
        cbAutoBreakthrough = view.findViewById(R.id.cb_auto_breakthrough)
        activeSkillContainer = view.findViewById(R.id.active_skill_container)

        tvBattlefieldTitle = view.findViewById(R.id.tv_battlefield_title)
        battlefieldContainer = view.findViewById(R.id.battlefield_container)

        btnBattle = view.findViewById(R.id.btn_battle)
        btnAutoBattle = view.findViewById(R.id.btn_auto_battle)
        btnSpeed = view.findViewById(R.id.btn_speed)

        tvProgressInfo = view.findViewById(R.id.tv_progress_info)
        progressBar = view.findViewById(R.id.progress_soul_power)
        btnCultivate = view.findViewById(R.id.btn_cultivate)
        btnBreakthrough = view.findViewById(R.id.btn_breakthrough)
        btnAwaken = view.findViewById(R.id.btn_awaken)
        btnPrestige = view.findViewById(R.id.btn_prestige)
        logContainer = view.findViewById(R.id.log_container)

        // 死亡遮罩
        deathOverlay = view.findViewById(R.id.death_overlay)
        tvDeathTitle = view.findViewById(R.id.tv_death_title)
        tvDeathInfo = view.findViewById(R.id.tv_death_info)
        progressDeathRegen = view.findViewById(R.id.progress_death_regen)

        // 卡级修炼
        cappingSection = view.findViewById(R.id.capping_section)
        tvCapTitle = view.findViewById(R.id.tv_cap_title)
        tvCapStatus = view.findViewById(R.id.tv_cap_status)
        tvCapExcess = view.findViewById(R.id.tv_cap_excess)
        capStatContainer = view.findViewById(R.id.cap_stat_container)
        btnCapToggle = view.findViewById(R.id.btn_cap_toggle)

        // 天赋树
        talentSection = view.findViewById(R.id.talent_section)
        tvTalentPoints = view.findViewById(R.id.tv_talent_points)
        tvTalentProgress = view.findViewById(R.id.tv_talent_progress)
        tvTalentSummary = view.findViewById(R.id.tv_talent_summary)
        tvPrestigeHint = view.findViewById(R.id.tv_prestige_hint)
        talentBranchesContainer = view.findViewById(R.id.talent_branches_container)

        btnBattle.setOnClickListener {
            GameEngine.doBattle()
            updateUI()
        }
        btnCultivate.setOnClickListener {
            val st = GameEngine.state
            if (st.skipCultivateConfirm) { GameEngine.doCultivate(); updateUI() }
            else showActionConfirm("修炼",
                "消耗 ${GameEngine.formatNum(GameEngine.cultivationCost())} 金魂币\n预计获得 ${GameEngine.formatNum((GameEngine.cultivationCost() * 0.8 * GameEngine.soulPowerMultiplier()).toLong())} 魂力",
            ) { GameEngine.doCultivate(); updateUI() }
        }
        btnBreakthrough.setOnClickListener {
            val st = GameEngine.state
            if (st.skipBreakthroughConfirm) { GameEngine.doBreakthrough(); updateUI() }
            else showActionConfirm("突破",
                "消耗 ${GameEngine.formatNum(RealmData.breakthroughCost(st.level))} 魂力\n突破至 ${RealmData.name(st.level + 1)} (${st.level + 1}级)，HP回满",
            ) { GameEngine.doBreakthrough(); updateUI() }
        }
        btnAwaken.setOnClickListener {
            val st = GameEngine.state
            // 检查是否已选择流派
            if (st.chosenSchool == null) {
                if (st.tutorialStep <= 3) {
                    showSchoolSelectionDialog()
                } else {
                    showActionConfirm("流派选择",
                        "请先选择修炼流派：\n${SoulSchool.entries.joinToString("\n") { "${it.icon} ${it.displayName} — ${it.description.split("\n").first()}" }}",
                    ) { showSchoolSelectionDialog() }
                }
                return@setOnClickListener
            }
            if (st.skipAwakenConfirm) { GameEngine.doAwaken(); updateUI() }
            else showActionConfirm("武魂觉醒",
                "随机觉醒一个武魂，获得专属技能和属性加成\n觉醒后覆盖当前武魂（如有）",
            ) { GameEngine.doAwaken(); updateUI() }
        }
        btnPrestige.setOnClickListener {
            val st = GameEngine.state
            if (st.skipPrestigeConfirm) { GameEngine.doPrestige(); updateUI() }
            else showActionConfirm("⚠️ 神位传承",
                buildPrestigeConfirmText(),
            ) { GameEngine.doPrestige(); updateUI() }
        }
        btnAutoBattle.setOnClickListener {
            GameEngine.toggleAutoBattle()
            updateUI()
        }
        btnSpeed.setOnClickListener {
            GameEngine.toggleBattleSpeed()
            updateUI()
        }
        cbAutoBreakthrough.setOnCheckedChangeListener { _, isChecked ->
            GameEngine.state.autoBreakthrough = isChecked
        }
        // 卡级修炼开关
        btnCapToggle.setOnClickListener {
            GameEngine.toggleLevelCap()
            updateUI()
        }

        // 战场点击查看怪物详情
        view.findViewById<View>(R.id.battlefield_card).setOnClickListener { showMonsterDetail() }
        // 武魂点击查看详情
        tvWuhun.setOnClickListener { showWuhunDetail() }

        GameEngine.addListener(updateListener)
        updateUI()
        refreshHandler.post(refreshRunnable)

        return view
    }

    private val updateListener = { updateUI() }

    private fun updateUI() {
        val s = GameEngine.state
        val attr = GameEngine.calcAttributes()
        val maxHp = attr.maxHp
        val currentHp = s.currentHp.coerceIn(0, maxHp)

        // 境界/武魂
        tvRealm.text = "${RealmData.name(s.level)} Lv.${s.level}"
        val schoolPrefix = s.chosenSchool?.let { "${it.icon} " } ?: ""
        tvWuhun.text = "$schoolPrefix${s.martialSoul?.name ?: "未觉醒"} (${s.martialSoul?.rarity?.displayName ?: "无"})"

        // 根骨/吸收容量
        val rootBone = GameEngine.calcRootBone()
        val capacity = GameEngine.calcAbsorptionCapacity()
        val totalLoad = GameEngine.calcTotalRingLoad()
        tvRootBone.text = "根骨: ${"%.1f".format(rootBone)} | 容量: ${GameEngine.formatNum(totalLoad)}/${GameEngine.formatNum(capacity)}"
        tvRootBone.setTextColor(if (totalLoad > capacity * 0.9) resources.getColor(R.color.rarity_mythic) else resources.getColor(R.color.rarity_uncommon))

        // HP条
        val isDead = currentHp <= 0
        if (isDead) {
            tvHpLabel.text = "💀 阵亡 - HP: 0 / ${GameEngine.formatNum(maxHp)}"
            tvHpLabel.setTextColor(resources.getColor(R.color.rarity_mythic))
        } else {
            tvHpLabel.text = "HP: ${GameEngine.formatNum(currentHp)} / ${GameEngine.formatNum(maxHp)}"
            tvHpLabel.setTextColor(resources.getColor(R.color.rarity_legendary))
        }
        progressPlayerHp.max = maxHp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        progressPlayerHp.progress = currentHp.coerceAtMost(maxHp).toInt()

        // 死亡遮罩
        if (isDead) {
            deathOverlay.visibility = View.VISIBLE
            val regenPct = if (maxHp > 0) ((currentHp.toDouble() / maxHp.toDouble()) * 100).toInt() else 0
            tvDeathInfo.text = "HP 恢复中 ${regenPct}%"
            progressDeathRegen.progress = regenPct
        } else {
            deathOverlay.visibility = View.GONE
        }

        // 属性
        tvAttack.text = "物攻: ${GameEngine.formatNum(attr.atk.toLong())}"
        tvMatk.text = "魔攻: ${GameEngine.formatNum(attr.matk.toLong())}"
        tvCrit.text = "暴击: ${attr.critRate}%/${attr.critDmg}%"
        tvPdef.text = "物防: ${GameEngine.formatNum(attr.pdef.toLong())}"
        tvMdef.text = "魔防: ${GameEngine.formatNum(attr.mdef.toLong())}"

        // 武魂技能
        s.martialSoul?.let { soul ->
            val skill = soul.skill
            layoutSkill.visibility = View.VISIBLE
            tvSkillName.text = "${skill.name}: ${skill.description}"
            tvSkillCooldown.text = "冷却: ${maxOf(s.skillCooldown, 0)}/${skill.cooldown}"
            tvSkillCooldown.setTextColor(if (s.skillCooldown <= 0) resources.getColor(R.color.rarity_uncommon) else resources.getColor(R.color.text_secondary))
        } ?: run { layoutSkill.visibility = View.GONE }

        // 战斗魂力
        val maxBp = RealmData.battleSoulPowerMax(s.level)
        tvBattleSoulPower.text = "魂力: ${s.battleSoulPower}/${maxBp} ⚡"
        tvBattleSoulPower.setTextColor(if (s.battleSoulPower >= 20) resources.getColor(R.color.rarity_uncommon) else resources.getColor(R.color.text_secondary))
        cbAutoBreakthrough.isChecked = s.autoBreakthrough

        // 修炼魂力
        tvSoulPower.text = "修炼魂力: ${GameEngine.formatNum(s.soulPower)}"
        val cost = RealmData.breakthroughCost(s.level)
        tvProgressInfo.text = "魂力 ${GameEngine.formatNum(s.soulPower)} / ${GameEngine.formatNum(cost)}"
        progressBar.max = cost.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        progressBar.progress = s.soulPower.coerceAtMost(cost).toInt()

        // 怪物UI
        updateMonsterUI()

        // 战斗按钮
        if (s.autoBattle) {
            btnAutoBattle.text = "🤖 自动战斗: 开启中"
            btnAutoBattle.setBackgroundColor(resources.getColor(R.color.btn_hunt))
            btnAutoBattle.setTextColor(android.graphics.Color.WHITE)
        } else {
            btnAutoBattle.text = "🤖 自动战斗: 关闭"
            btnAutoBattle.setBackgroundColor(resources.getColor(R.color.card_bg))
            btnAutoBattle.setTextColor(resources.getColor(R.color.text_secondary))
        }

        // 倍速按钮
        if (s.battleSpeed == 2) {
            btnSpeed.text = "⚡ 二倍速"
            btnSpeed.setBackgroundColor(resources.getColor(R.color.btn_hunt))
            btnSpeed.setTextColor(android.graphics.Color.WHITE)
        } else {
            btnSpeed.text = "🐢 一倍速"
            btnSpeed.setBackgroundColor(resources.getColor(R.color.card_bg))
            btnSpeed.setTextColor(resources.getColor(R.color.text_secondary))
        }

        // 战斗按钮状态
        btnBattle.isEnabled = currentHp > 0 && !s.autoBattle
        val canAct = currentHp > 0 && !s.inBattle
        btnCultivate.isEnabled = canAct
        btnBreakthrough.isEnabled = canAct
        btnAwaken.isEnabled = currentHp > 0
        btnPrestige.isEnabled = currentHp > 0
        btnAutoBattle.isEnabled = currentHp > 0

        // 战斗按钮文字
        when {
            isDead -> {
                val regenPct = if (maxHp > 0) ((currentHp.toDouble() / maxHp.toDouble()) * 100).toInt() else 0
                btnBattle.text = "💀 恢复中 ${regenPct}%"
                btnBattle.setBackgroundColor(resources.getColor(R.color.rarity_mythic))
            }
            s.inBattle -> {
                btnBattle.text = "⚔️ 攻击 (第${s.battleRound + 1}回合)"
                btnBattle.setBackgroundColor(resources.getColor(R.color.btn_hunt))
            }
            else -> {
                btnBattle.text = "⚔️ 攻击"
                btnBattle.setBackgroundColor(resources.getColor(R.color.btn_hunt))
            }
        }

        // 主动技能按钮
        updateActiveSkillButtons()

        // 卡级修炼
        updateCappingUI()

        // 天赋树
        updateTalentUI()

        // 战斗日志
        val logs = GameEngine.getLog()
        logContainer.removeAllViews()
        for (log in logs) {
            if (logContainer.childCount >= 15) break
            val tv = TextView(requireContext())
            tv.text = log
            tv.setTextColor(requireContext().getColor(
                when {
                    log.contains("击败") || log.contains("通关") -> R.color.text_gold
                    log.contains("被") || log.contains("失败") -> R.color.rarity_mythic
                    log.contains("解锁") || log.contains("成功") -> R.color.rarity_uncommon
                    else -> R.color.text_secondary
                }
            ))
            tv.textSize = 11f
            tv.setPadding(0, 2, 0, 2)
            logContainer.addView(tv)
        }
    }

    /** 战场UI：对阵式布局 玩家 vs 怪物组(每个怪物独立血条) */
    private fun updateMonsterUI() {
        val s = GameEngine.state
        val monsters = s.currentMonsters
        if (monsters.isEmpty()) return

        val currentTarget = s.currentMonsterTarget.coerceIn(0, monsters.size - 1)
        val aliveCount = monsters.count { it.hp > 0 }
        val totalTargets = monsters.size
        val stageType = StageData.getStageType(s.currentStage)

        tvBattlefieldTitle.text = "⚔️ 战斗 (${aliveCount}/${totalTargets}存活) | 关卡${s.currentStage}(${stageType.name})"

        battlefieldContainer.removeAllViews()

        val vsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // 左侧：玩家简况
        val playerCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            background = resources.getDrawable(R.drawable.card_bg, null)
        }

        val attr = GameEngine.calcAttributes()
        val isDead = s.currentHp <= 0
        val hpPct = if (attr.maxHp > 0) (s.currentHp * 100 / attr.maxHp).coerceIn(0, 100) else 0

        playerCol.addView(TextView(requireContext()).apply {
            text = if (isDead) "💀 阵亡" else "🦸 ${RealmData.name(s.level)}"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (isDead) resources.getColor(R.color.rarity_mythic) else resources.getColor(R.color.accent))
        })

        playerCol.addView(ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = hpPct.toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8))
            progressDrawable = resources.getDrawable(R.drawable.progress_hp_bg, null)
        })

        playerCol.addView(TextView(requireContext()).apply {
            text = "HP ${GameEngine.formatNum(s.currentHp)} ($hpPct%)"
            textSize = 9f
            setTextColor(resources.getColor(R.color.text_secondary))
        })

        vsRow.addView(playerCol)

        // VS 分隔
        vsRow.addView(TextView(requireContext()).apply {
            text = " ⚔️ "
            textSize = 18f
            setTextColor(resources.getColor(R.color.rarity_legendary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dpToPx(4), 0, dpToPx(4), 0)
        })

        // 右侧：怪物组
        val monsterCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        for ((i, monster) in monsters.withIndex()) {
            val isTarget = i == currentTarget && monster.hp > 0
            val monsterDead = monster.hp <= 0
            val mHpPct = if (monster.maxHp > 0) (monster.hp * 100 / monster.maxHp).coerceIn(0, 100) else 0

            val monsterCard = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(4), dpToPx(3), dpToPx(4), dpToPx(3))
                if (isTarget) {
                    setBackgroundResource(R.drawable.card_bg_gold)
                } else if (!monsterDead) {
                    setBackgroundResource(R.drawable.card_bg)
                }
                setOnClickListener { showMonsterDetail() }
            }

            val nameRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val prefix = when {
                monsterDead -> "💀"
                isTarget -> "🎯"
                else -> "👾"
            }
            val bestAffix = monster.affixes.maxByOrNull { it.dropMult }
            val nameColor = if (monsterDead) resources.getColor(R.color.rarity_common)
            else (bestAffix?.colorInt ?: resources.getColor(R.color.text_primary))

            nameRow.addView(TextView(requireContext()).apply {
                text = "$prefix "
                textSize = 11f
            })

            nameRow.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${monster.name}${if (isTarget) " [目标]" else ""}"
                textSize = 11f
                setTextColor(nameColor)
                if (isTarget) setTypeface(null, android.graphics.Typeface.BOLD)
            })

            nameRow.addView(TextView(requireContext()).apply {
                text = "HP: $mHpPct%"
                textSize = 10f
                setTextColor(if (monsterDead) resources.getColor(R.color.rarity_common) else resources.getColor(R.color.rarity_legendary))
            })

            monsterCard.addView(nameRow)

            if (!monsterDead) {
                monsterCard.addView(ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100; progress = mHpPct.toInt()
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6))
                    progressDrawable = resources.getDrawable(R.drawable.progress_monster_bg, null)
                })
            }

            monsterCol.addView(monsterCard)
        }

        vsRow.addView(monsterCol)
        battlefieldContainer.addView(vsRow)

        // 当前目标详情行
        val curMonster = s.currentMonster ?: monsters.getOrNull(currentTarget)
        if (curMonster != null && curMonster.hp > 0) {
            val affixStr = curMonster.affixes.joinToString(" ") { "[${it.displayName}]" }
            if (affixStr.isNotEmpty()) {
                battlefieldContainer.addView(TextView(requireContext()).apply {
                    text = "当前目标: ${curMonster.name} | $affixStr | ⚔${curMonster.atk} 🛡${curMonster.pdef}"
                    textSize = 10f
                    setTextColor(resources.getColor(R.color.rarity_epic))
                    setPadding(0, dpToPx(4), 0, 0)
                })
            }
        }
    }

    /** 主动技能按钮（依据可用魂力、冷却状态） */
    private fun updateActiveSkillButtons() {
        val s = GameEngine.state
        activeSkillContainer.removeAllViews()

        val skills = GameEngine.getAvailableRingSkills()
        if (skills.isNotEmpty()) {
            val header = TextView(requireContext()).apply {
                text = "⚡ 魂环主动技能 (可释放):"
                textSize = 12f
                setTextColor(resources.getColor(R.color.rarity_legendary))
                setPadding(0, 2, 0, 2)
            }
            activeSkillContainer.addView(header)

            for ((slotIdx, skill) in skills) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 2, 0, 2)
                }
                val slotName = SoulRingSlots.get(slotIdx)?.displayName ?: "魂环$slotIdx"
                val info = TextView(requireContext()).apply {
                    text = "${skill.name} (${skill.soulCost}⚡) ${skill.type.displayName}"
                    setTextColor(resources.getColor(R.color.rarity_uncommon))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(info)
                val useBtn = Button(requireContext()).apply {
                    text = "释放"
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(resources.getColor(R.color.btn_hunt))
                    textSize = 11f
                    setPadding(8, 0, 8, 0)
                    val _tv = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, _tv, true)
                    foreground = context.getDrawable(_tv.resourceId)
                    setOnClickListener {
                        GameEngine.applyActiveSkillDamage(slotIdx, skill, GameEngine.calcAttributes(),
                            s.currentMonster ?: return@setOnClickListener)
                        s.activeSkillCooldowns[slotIdx] = skill.cooldown
                        GameEngine.addLog("⚡ 手动释放【${skill.name}】")
                        updateUI()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(28)
                    )
                }
                row.addView(useBtn)
                activeSkillContainer.addView(row)
            }
        }

        // 冷却信息
        val cdInfo = GameEngine.getRingCooldownInfo()
        if (cdInfo.isNotEmpty()) {
            val cdHeader = TextView(requireContext()).apply {
                val availableCount = skills.size
                text = "⏳ 魂环冷却 ($availableCount 可用):"
                textSize = 11f
                setTextColor(resources.getColor(R.color.text_secondary))
                setPadding(0, 2, 0, 2)
            }
            activeSkillContainer.addView(cdHeader)

            for ((slotIdx, pair) in cdInfo) {
                val (skill, cd) = pair
                val slotName = SoulRingSlots.get(slotIdx)?.displayName ?: "魂环$slotIdx"
                val cdText = if (cd <= 0) "✅ 就绪" else "⏳ $cd 回合"
                val cdTv = TextView(requireContext()).apply {
                    text = "  $slotName: ${skill.name} | $cdText"
                    setTextColor(if (cd <= 0) resources.getColor(R.color.rarity_uncommon) else resources.getColor(R.color.text_secondary))
                    textSize = 10f
                }
                activeSkillContainer.addView(cdTv)
            }
        }
    }

    /** 卡级修炼UI */
    private fun updateCappingUI() {
        val s = GameEngine.state
        val isCapLevel = GameEngine.isAtCapLevel()

        cappingSection.visibility = if (GameEngine.isFeatureUnlocked(GameEngine.FeatureType.LEVEL_CAP)) View.VISIBLE else View.GONE

        if (s.isLevelCapped) {
            tvCapTitle.text = "🔒 卡级修炼 (已开启)"
            tvCapStatus.text = "超额魂力: ${GameEngine.formatNum(s.excessSoulPower)}"
            tvCapExcess.text = "修炼收益进入超额储备，突破已锁定"
            btnCapToggle.text = "🔓 解除卡级"

            // 属性凝练按钮
            capStatContainer.removeAllViews()
            val stats = listOf("hp" to "生命", "atk" to "攻击", "matk" to "魔攻", "pdef" to "物防", "mdef" to "魔防")
            for ((key, name) in stats) {
                val curLv = s.capStatLevels.getOrDefault(key, 0)
                val cost = (100L + curLv * 80L).coerceAtMost(50000L)
                val maxBatch = GameEngine.maxBatchForStat(key)
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 2, 0, 2)
                }
                row.addView(TextView(requireContext()).apply {
                    text = "$name Lv.$curLv (${GameEngine.formatNum(cost)}/次)"
                    textSize = 11f
                    setTextColor(resources.getColor(R.color.rarity_uncommon))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                val btnBatch = Button(requireContext()).apply {
                    text = if (maxBatch > 0) "×${maxBatch.coerceAtMost(10)}" else "—"
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(if (maxBatch > 0) resources.getColor(R.color.btn_cultivate) else resources.getColor(R.color.card_bg))
                    textSize = 10f
                    setPadding(6, 0, 6, 0)
                    isEnabled = maxBatch > 0
                    setOnClickListener {
                        if (maxBatch > 0) {
                            val batch = maxBatch.coerceAtMost(10)
                            GameEngine.batchConsumeExcessForStat(key, batch)
                            updateUI()
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(26))
                }
                row.addView(btnBatch)
                capStatContainer.addView(row)
            }
            // 魂力转化金币
            val goldRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 2)
            }
            goldRow.addView(TextView(requireContext()).apply {
                text = "💰 魂力→金币"
                textSize = 11f
                setTextColor(resources.getColor(R.color.text_gold))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val btnGold = Button(requireContext()).apply {
                text = if (s.excessSoulPower >= 100) "转化" else "—"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(if (s.excessSoulPower >= 100) resources.getColor(R.color.btn_normal) else resources.getColor(R.color.card_bg))
                textSize = 10f
                setPadding(6, 0, 6, 0)
                isEnabled = s.excessSoulPower >= 100
                setOnClickListener {
                    GameEngine.batchConsumeExcessForGold(5)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(26))
            }
            goldRow.addView(btnGold)
            capStatContainer.addView(goldRow)
        } else {
            tvCapTitle.text = if (isCapLevel) "🔓 卡级模式（可开启）" else "🔒 卡级模式"
            tvCapStatus.text = if (isCapLevel) "当前为境界门槛，可开启卡级修炼积累超额魂力"
                else "仅在境界门槛（Lv.10/20/30...）可开启卡级修炼"
            tvCapExcess.text = ""
            capStatContainer.removeAllViews()
            btnCapToggle.text = if (s.isLevelCapped) "🔓 解除卡级"
                else if (isCapLevel) "🔒 开启卡级"
                else "—"
            btnCapToggle.isEnabled = isCapLevel
        }
    }

    /** 天赋树UI */
    private fun updateTalentUI() {
        val s = GameEngine.state
        val unlocked = GameEngine.isFeatureUnlocked(GameEngine.FeatureType.TALENT)
        talentSection.visibility = if (unlocked) View.VISIBLE else View.GONE
        if (!unlocked) return

        tvTalentPoints.text = "${s.talentPoints}"
        val spent = GameEngine.getSpentTalentPoints()
        val totalMax = TalentBranch.entries.sumOf { it.maxLevel }
        tvTalentProgress.text = "已消耗: $spent / $totalMax"

        // 天赋摘要
        val summaryLines = TalentBranch.entries.map { branch ->
            val lv = s.getTalentLevel(branch)
            "${branch.icon} ${branch.displayName} Lv.$lv/${branch.maxLevel}"
        }
        tvTalentSummary.text = summaryLines.joinToString(" | ")

        tvPrestigeHint.visibility = if (s.talentPoints <= 0 && spent == 0) View.VISIBLE else View.GONE

        // 天赋分支按钮
        talentBranchesContainer.removeAllViews()
        for (branch in TalentBranch.entries) {
            val lv = s.getTalentLevel(branch)
            val isMaxed = lv >= branch.maxLevel
            val canUpgrade = s.talentPoints > 0 && !isMaxed

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 3, 0, 3)
            }

            row.addView(TextView(requireContext()).apply {
                text = "${branch.icon} ${branch.displayName} Lv.$lv/${branch.maxLevel}"
                textSize = 12f
                setTextColor(if (isMaxed) resources.getColor(R.color.text_gold) else resources.getColor(R.color.rarity_legendary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(TextView(requireContext()).apply {
                text = TalentBranch.effectDescription(branch, lv)
                textSize = 10f
                setTextColor(resources.getColor(R.color.rarity_uncommon))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            val btn = Button(requireContext()).apply {
                text = if (isMaxed) "MAX" else "升级"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(if (canUpgrade) resources.getColor(R.color.btn_breakthrough) else resources.getColor(R.color.card_bg))
                textSize = 10f
                setPadding(8, 0, 8, 0)
                isEnabled = canUpgrade
                setOnClickListener {
                    GameEngine.doSpendTalentPoint(branch)
                    updateUI()
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(28))
            }
            row.addView(btn)
            talentBranchesContainer.addView(row)
        }
    }

    /** 流派选择对话框（开局/重要时刻调用） */
    private fun showSchoolSelectionDialog() {
        val s = GameEngine.state
        if (s.chosenSchool != null) {
            showDetailDialog("流派已选", "当前流派：${s.chosenSchool!!.icon} ${s.chosenSchool!!.displayName}\n转生后可重新选择")
            return
        }

        val schools = SoulSchool.entries.toList()
        val items = schools.map { school ->
            val unlocked = GameEngine.isSchoolUnlocked(school)
            val req = if (!unlocked) " (🔒 ${GameEngine.schoolUnlockRequirement(school)})" else ""
            "${school.icon} ${school.displayName}${req}\n  ${school.description.split("\n").first()}"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("🎯 选择修炼流派")
            .setItems(items) { _, which ->
                val school = schools[which]
                if (GameEngine.isSchoolUnlocked(school)) {
                    GameEngine.doChooseSchool(school)
                    updateUI()
                } else {
                    showDetailDialog("未解锁", "${school.displayName} 需要 ${GameEngine.schoolUnlockRequirement(school)}")
                }
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        GameEngine.removeListener(updateListener)
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    // ======== 详情弹窗 ========

    private fun showMonsterDetail() {
        val monster = GameEngine.state.currentMonster ?: return
        val sb = StringBuilder()
        sb.appendLine("👾 ${monster.name}")
        sb.appendLine("━━━━━━━━━━━━━━━")
        sb.appendLine("HP: ${GameEngine.formatNum(monster.hp)} / ${GameEngine.formatNum(monster.maxHp)}")
        sb.appendLine("物攻: ${monster.atk}  魔攻: ${monster.matk}")
        sb.appendLine("物防: ${monster.pdef}  魔防: ${monster.mdef}")
        sb.appendLine("暴击: ${monster.critRate}%  爆伤: ${monster.critDmg}%")
        sb.appendLine("奖励: ${GameEngine.formatNum(monster.goldReward)}💰 ${GameEngine.formatNum(monster.expReward)}经验")

        if (monster.affixes.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("🏷️ 词缀详情:")
            for (affix in monster.affixes) {
                sb.appendLine("  [${affix.displayName}] ${affix.desc}")
            }
        } else {
            sb.appendLine()
            sb.appendLine("🏷️ 词缀: 无")
        }

        val s = GameEngine.state
        sb.appendLine()
        sb.appendLine("📊 关卡信息:")
        sb.appendLine("  当前关卡: ${s.currentStage}/15")
        sb.appendLine("  关卡类型: ${StageData.getStageType(s.currentStage).name}")
        sb.appendLine("  怪物组: ${s.currentMonsters.size}个")
        if (s.currentMonsters.size > 1) {
            val alive = s.currentMonsters.count { it.hp > 0 }
            sb.appendLine("  剩余: ${alive}个")
        }

        showDetailDialog("怪物详情", sb.toString())
    }

    private fun showWuhunDetail() {
        val soul = GameEngine.state.martialSoul ?: run {
            showDetailDialog("武魂详情", "武魂未觉醒\n\n前往修炼页点击【✨觉醒】来觉醒你的武魂！")
            return
        }
        val sb = StringBuilder()
        sb.appendLine("✨ ${soul.name}")
        sb.appendLine("品质: ${soul.rarity.displayName}")
        sb.appendLine("━━━━━━━━━━━━━━━")
        sb.appendLine("基础属性加成:")
        sb.appendLine("  HP +${soul.baseHp}")
        sb.appendLine("  物攻 +${soul.baseAtk}  魔攻 +${soul.baseMatk}")
        sb.appendLine("  物防 +${soul.pdef}  魔防 +${soul.mdef}")
        sb.appendLine("  暴击率 +${soul.critRate}%  爆伤 +${soul.critDmg}%")
        sb.appendLine("  自动金币 +${soul.autoGoldBonus}")
        sb.appendLine()
        sb.appendLine("⚡ 武魂技能: ${soul.skill.name}")
        sb.appendLine("  类型: ${soul.skill.type.displayName}")
        sb.appendLine("  描述: ${soul.skill.description}")
        sb.appendLine("  威力: ${soul.skill.power}%  冷却: ${soul.skill.cooldown}回合")
        if (soul.description.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📖 ${soul.description}")
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

    private fun buildPrestigeConfirmText(): String {
        val s = GameEngine.state
        val sb = StringBuilder()
        sb.append("📄 转生后会重置等级、魂力、关卡进度\n")
        sb.append("已装备的魂环+魂骨将永久保留\n\n")
        sb.append("━━━ 天赋保留加成 ━━━\n")
        TalentBranch.entries.forEach { branch ->
            val lv = s.getTalentLevel(branch)
            sb.append("${branch.icon} ${branch.displayName} Lv.$lv: ")
            sb.append(TalentBranch.retentionDescription(branch, lv))
            if (lv < branch.maxLevel) {
                sb.append("\n   👉 升至Lv.${lv + 1}: ")
                sb.append(TalentBranch.retentionDescription(branch, lv + 1).replace("\n", "/"))
            }
            sb.append("\n")
        }
        sb.append("\n🏅 转生后获得 1 天赋点 (当前: ${s.talentPoints}点)")
        sb.append("\n📈 永久: 全属性 ×${String.format("%.1f", GameEngine.prestigeMultiplier() + 0.5)}")
        return sb.toString()
    }

    private fun showActionConfirm(title: String, message: String, onConfirm: () -> Unit) {
        val s = GameEngine.state
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 8)
        }
        val msgView = TextView(requireContext()).apply {
            text = message
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary))
            setLineSpacing(4f, 1.2f)
        }
        container.addView(msgView)
        val cb = CheckBox(requireContext()).apply {
            text = "不再提示"
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 13f
            setPadding(0, dpToPx(8), 0, 0)
        }
        container.addView(cb)

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                if (cb.isChecked) {
                    when (title.substringBefore(" ").trim()) {
                        "修炼" -> s.skipCultivateConfirm = true
                        "突破" -> s.skipBreakthroughConfirm = true
                        "武魂觉醒" -> s.skipAwakenConfirm = true
                        "⚠️" -> s.skipPrestigeConfirm = true
                    }
                }
                onConfirm()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
