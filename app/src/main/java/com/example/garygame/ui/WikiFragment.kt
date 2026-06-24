package com.example.garygame.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.garygame.R
import com.example.garygame.engine.GameEngine
import com.example.garygame.model.*

class WikiFragment : Fragment() {

    private var containerView: View? = null
    private var viewIds = mutableListOf<Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_wiki, container, false)
        val root = view.findViewById<LinearLayout>(R.id.wiki_container)
        val ctx = requireContext()
        val res = resources
        containerView = view

        // ===== 标题 =====
        root.addView(tv(ctx, res, "📖 斗罗大陆·放置传说 V5.0 百科", R.color.accent, 18f, true, 0, 4))
        root.addView(tv(ctx, res, "涵盖全部核心系统与数值公式  |  点击目录条目跳转至对应章节", R.color.text_secondary, 11f, false, 0, 8))

        // ===================================================================
        // 📋 目录
        // ===================================================================
        root.addView(tv(ctx, res, "📋 目录", R.color.text_gold, 16f, true, 4, 4))
        val tocItems = listOf(
            "1.武魂流派" to 1001, "2.境界突破" to 1002, "3.修炼与突破" to 1003,
            "4.魂环系统(三维度)" to 1004, "5.魂骨系统" to 1005, "6.魂骨套装" to 1006,
            "7.战斗系统(含技能)" to 1007, "8.地图系统" to 1008, "9.怪物词缀" to 1009,
            "10.杀戮之都" to 1010, "11.Boss商店" to 1011, "12.限时珍品" to 1012,
            "13.魂核系统" to 1013, "14.天赋树" to 1014, "15.转生系统" to 1015,
            "16.卡级修炼" to 1016, "17.背包系统" to 1017, "18.功能解锁" to 1018,
            "19.附属系统" to 1019, "20.数值公式" to 1020
        )
        val tocContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), 0, dpToPx(8), dpToPx(4))
        }
        for ((label, id) in tocItems) {
            tocContainer.addView(clickableTocItem(ctx, res, label, id))
        }
        root.addView(tocContainer)
        addDivider(root, ctx, res)

        // ===================================================================
        // 1. 武魂流派
        // ===================================================================
        val s1 = sectionTitle(root, ctx, res, "1. ✨ 武魂流派系统", 1001)
        root.addView(tv(ctx, res, "开局三选一流派，决定武魂池与属性偏重方向。流派选定后觉醒初始武魂，转生后可重新选择。", R.color.text_primary, 13f, false, 0, 2))
        for (school in SoulSchool.entries.filter { it.category == SchoolCategory.BASIC }) {
            val mod = SchoolStatMods.get(school)
            root.addView(tv(ctx, res, schoolDesc(school, mod), schoolColor(school), 12f, false, 0, 1))
        }
        root.addView(tv(ctx, res, "武魂品质：普通(灰)→精良(绿)→稀有(蓝)→史诗(紫)→传说(金)→神话(红)", R.color.text_secondary, 11f, false, 0, 2))

        root.addView(tv(ctx, res, "🌟 特殊流派（需满足解锁条件）", R.color.text_gold, 14f, true, 6, 4))
        for (school in SoulSchool.entries.filter { it.category == SchoolCategory.SPECIAL }) {
            val mod = SchoolStatMods.get(school)
            val unlockNote = when (school) {
                SoulSchool.SUPPORT -> "（Lv.50 + 转生1次）"
                SoulSchool.CONTROL -> "（Lv.70 + 转生2次）"
                SoulSchool.ASSASSIN -> "（Lv.90 + 转生3次）"
                else -> ""
            }
            root.addView(tv(ctx, res, schoolDesc(school, mod, unlockNote), schoolColor(school), 12f, false, 0, 2))
        }

        root.addView(tv(ctx, res, "12种武魂覆盖全流派，品质由转生次数决定最高可觉醒等级（转生5次可觉醒神话级武魂）", R.color.text_secondary, 11f, false, 0, 2))
        root.addView(crossRef(ctx, res, "→ 参见 §7 战斗系统（武魂本命魂技）", 1007), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 2. 境界突破
        // ===================================================================
        val s2 = sectionTitle(root, ctx, res, "2. ⬆️ 境界突破系统", 1002)
        sectionBody(root, ctx, res,
            "每10级跨越一个境界，基础属性获得大幅加成",
            "加成公式：境界加成 = 1.0 + (等级/10) × 0.25",
            "Lv1~9 魂士 1.00× → Lv10~19 魂师 1.25× → Lv20~29 大魂师 1.50×",
            "Lv30~39 魂尊 1.75× → Lv40~49 魂宗 2.00× → Lv50~59 魂王 2.25×",
            "Lv60~69 魂帝 2.50× → Lv70~79 魂圣 2.75× → Lv80~89 魂斗罗 3.00×",
            "Lv90~99 封号斗罗 3.25× → Lv100+ 极限/半神/神祇/神王 3.50×+",
            "基础属性：HP=50×等级+100  ATK=10×等级  MATK=8×等级  PDEF=5×等级  MDEF=4×等级",
            "加成在流派修正后、装备附加前应用，仅放大基础属性"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §3 修炼与突破 | §16 卡级修炼", 1003), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 3. 修炼与突破
        // ===================================================================
        val s3 = sectionTitle(root, ctx, res, "3. 🧘 修炼与突破", 1003)
        sectionBody(root, ctx, res,
            "手动修炼：每次获得「等级×28×转生倍率」魂力，冷却5秒",
            "离线修炼：每5秒自动修炼一次，上线时结算（最多12小时）",
            "突破消耗：150×等级^1.65 魂力（指数增长），突破后重置HP",
            "",
            "自动突破：Tick每0.5秒检测，达到境界门槛(10/20/30…)自动暂停",
            "自动暂停条件：等级是10的倍数（便于玩家开启卡级或手动突破）",
            "魂环卡级：即将解锁新槽位时，需先填满当前槽位才能突破",
            "开启卡级后自动突破暂停，超额魂力存入储备池",
            "",
            "基础成长：每级HP+50  ATK+10  MATK+8  PDEF+5  MDEF+4",
            "HP恢复：非战斗中每秒回复最大生命10%（再生之核战斗中也可生效）",
            "离线收益自动计算，自动突破/自动存档同步运行"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §16 卡级修炼 | §15 转生系统（转生倍率）", 1016), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 4. 魂环系统
        // ===================================================================
        val s4 = sectionTitle(root, ctx, res, "4. 💍 魂环系统 V5（三维度模型）", 1004)
        root.addView(tv(ctx, res, "★ 三维度：年份(5档)×品质(5档)=25合并层级 + 成熟度百分比(100~999)", R.color.accent, 13f, true, 2, 2))
        sectionBody(root, ctx, res,
            "年份档位：百年(0) 千年(1) 万年(2) 十万年(3) 百万年(4)",
            "品质档位：劣等(0) 普通(1) 精良(2) 优秀(3) 完美(4)",
            "合并层级 = 年份×5 + 品质（范围0~24）",
            "成熟度百分比：10.0%~99.9%（掉落随机），越高属性越强",
            "",
            "★ 等效年份（核心公式）",
            "  nextBaseYear = 年份档位下一级的基础值（百年→1000，千年→10000...）",
            "  最后一档(百万年)用当前值×10 = 10000000",
            "  等效年份 = nextBaseYear × (percentage/1000)",
            "  示例：千年(72.0%) = 10000 × 0.72 = 7200年",
            "",
            "★ 魂环负荷 = 等效年份（直接相等）",
            "  所有已装备魂环的负荷总和不得超出吸收容量",
            "",
            "★ 根骨与吸收容量",
            "  根骨 = 物攻×3 + 魔攻×3 + 物防×2 + 魔防×2 + 生命/100",
            "  吸收容量 = 根骨 × 6（最低100）",
            "",
            "★ 属性加成公式",
            "  成熟度倍率 ringPctMult = 1.0 + percentage/1000.0（范围1.1×~2.0×）",
            "  词缀效果 = baseValue(合并层级) × ringPctMult × (1.0 + 魂师等级×0.08)",
            "  基础值锚点(合并层级0~24插值): [8, 18, 40, 85, 180]",
            "  词缀数量：百年2→千年2→万年3→十万年4→百万年5",
            "",
            "★ 品质与百分比互相隔离：品质决定基础值，百分比决定成熟度倍率",
            "★ 年份档次主导: 百年基础值 << 千年基础值，成熟度不会反超年份差距",
            "★ 显示格式：精良·千年(79.8%)",
            "★ 魂环不可卸下/更换，仅可吸收到空槽位",
            "★ 槽位：Lv1→1槽，每20级+1槽（Lv21/41/61/81/101/121/141/161），最多9槽",
            "★ 吸收费用：年份基数×（1+已有环数×0.5）—— 随环数递增",
            "★ 掉落自动过滤：装备页可设置不想要的年份/品质组合，自动卖出"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §7 战斗系统（魂环技能释放）| §5 魂骨系统", 1007), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 5. 魂骨系统
        // ===================================================================
        val s5 = sectionTitle(root, ctx, res, "5. 🦴 魂骨系统", 1005)
        sectionBody(root, ctx, res,
            "5档品质（同魂环体系）：劣等→普通→精良→优秀→完美",
            "6大部位：头骨/左臂骨/右臂骨/躯干骨/左腿骨/右腿骨",
            "每部位含独特被动技能（属性增幅/减伤/反伤/吸血/闪避/斩杀/狂暴等）",
            "合并层级=年份档×5+品质档（同魂环体系），共用baseValue/affixCount",
            "槽位：Lv1→1槽，每30级+1槽（Lv31/61/91/121/151），最多6槽",
            "",
            "★ 强化系统（Max=20级）",
            "  强化效果 = 1.0 + 等级×0.15（最高4.0倍）",
            "  强化消耗 = 1000 × (1.0 + 等级×0.8)",
            "",
            "★ 属性加成 = baseValue × 强化效果 × (1.0 + 魂师等级×0.08)",
            "★ 魂骨可卸下/替换（与魂环不同）",
            "★ Boss固定掉落魂骨，限时珍品也可购买"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §6 魂骨套装 | §10 杀戮之都（BOSS掉落）", 1006), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 6. 魂骨套装
        // ===================================================================
        val s6 = sectionTitle(root, ctx, res, "6. 🔥 魂骨套装效果", 1006)
        sectionBody(root, ctx, res,
            "同时装备任意2件可激活2件套效果，集齐全部部件激活全套效果",
            "⚪ 基础套（全6件）：2件-物攻+15 双防+10 HP+100 | 全套-物攻+40 双防+30 HP+250 暴击+3%",
            "🔵 精英套（躯干+腿3件）：2件-双防+20 生命+250 | 全套-双防+30 生命+400",
            "👑 神装套（全6件）：2件-物攻+60 双防+30 生命+300 | 全套-物攻+150 双防+80 生命+1000 暴击+10%",
            "",
            "套装效果与强化效果叠加，不影响被动技能",
            "注意：旧版「攻击套/防御套」命名已更新为基础套/精英套/神装套"
        )
        addDivider(root, ctx, res)

        // ===================================================================
        // 7. 战斗系统
        // ===================================================================
        val s7 = sectionTitle(root, ctx, res, "7. ⚔️ 战斗系统（含技能系统）", 1007)
        sectionBody(root, ctx, res,
            "★ 回合制1vN：每回合攻击当前目标，击杀后自动切换下一目标",
            "  物伤 = ATK × (1 - 对方物防/(物防+200))，最低10%穿透",
            "  魔伤 = MATK × (1 - 对方魔防/(魔防+200))，最低10%穿透",
            "  暴击伤害 = 基础伤害 × 爆伤%，暴击率上限95%",
            "",
            "★ 战斗魂力系统：上限100+等级×5，每回合回复(3+等级/20)点",
            "  魂环技能消耗魂力释放，武魂本命技能不耗魂力",
            "  自动择优释放（优先高威力魂环技能）",
            "  50回合上限，超时战斗自动结束",
            "",
            "★ 主动技能池（137个技能，8系别，5个Tier）",
            "  技能类型：单体/多段/治疗/吸血/狂怒/增幅/护盾/斩杀/吸魂",
            "  魂环概率携带技能，随合并层级提高技能品质提升",
            "  魂力消耗随技能Tier增加，冷却3~5回合",
            "  手动/自动切换：可关闭自动释放，战斗中手动点击技能按钮",
            "",
            "★ 武魂本命魂技：觉醒时自带，不占魂环槽且不耗魂力",
            "★ 多目标战斗自动追踪击杀进度",
            "★ 自动战斗：每Tick自动执行2回合，HP归零停止"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §4 魂环系统（技能来源）| §13 魂核系统（战斗特效）", 1004), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 8. 地图系统
        // ===================================================================
        val s8 = sectionTitle(root, ctx, res, "8. 🗺️ 地图系统", 1008)
        sectionBody(root, ctx, res,
            "共8张地图，每张15关（普通/精英/Boss交替，分别占3/2/1关模式）",
            "① 圣魂村(Lv1)  → ② 诺丁城外(Lv10)  → ③ 星斗外围(Lv25)  → ④ 落日森林(Lv40)",
            "⑤ 极北之地(Lv55) → ⑥ 海神岛(Lv70) → ⑦ 杀戮外域(Lv85) → ⑧ 神界之门(Lv100)",
            "关卡缩放：怪物属性×(1+关卡×0.12)",
            "地图解锁：需达到指定等级+支付解锁费用（地图越高级费用越高）",
            "自动跳转（autoAdvanceMap）：关底自动进入下一地图（需检测金币充足）",
            "每关怪物数量：普通1个，精英(1~2个)，Boss(1~2个)"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §9 怪物词缀 | §11 Boss商店（阶梯解锁）", 1009), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 9. 怪物词缀
        // ===================================================================
        val s9 = sectionTitle(root, ctx, res, "9. 👹 怪物词缀", 1009)
        root.addView(tv(ctx, res, "怪物词缀影响战斗策略，部分词缀提升掉落倍率：", R.color.text_primary, 12f, false, 0, 4))
        for (affix in MonsterAffix.entries) {
            val note = if (affix.dropMult != 1.0) "（掉落×${String.format("%.1f", affix.dropMult)}）" else ""
            root.addView(affixLine(ctx, res, affix.displayName, "${affix.desc} $note", affix.colorInt))
        }
        root.addView(tv(ctx, res, "词缀数量：Boss固定2~3个，精英(每3关)1~2个，普通30%概率1个", R.color.text_secondary, 11f, false, 4, 2))
        addDivider(root, ctx, res)

        // ===================================================================
        // 10. 杀戮之都
        // ===================================================================
        val s10 = sectionTitle(root, ctx, res, "10. 🗼 杀戮之都", 1010)
        sectionBody(root, ctx, res,
            "独立爬塔100层，8段主题（鲜血荒原→终焉之地）",
            "每层消耗HP: 8%(1~20层)→9%(21~40)→10%(41~60)→12%(61~80)→15%(81~100)",
            "休息层(每5层非Boss)回满HP，Boss层(每10层)掉落魂骨",
            "扫荡：战力≥怪物3倍时可跳过战斗连跳多层",
            "每击杀Boss永久ATK+5×层数（Boss击杀数保留）",
            "杀气货币：爬塔获得，可在杀气商店兑换称号/属性",
            "5个称号(杀戮者→弑神者)、4档属性强化",
            "塔内HP独立计算，死亡退出不扣金币"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §11 Boss商店（货币互通）", 1011), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 11. Boss商店
        // ===================================================================
        val s11 = sectionTitle(root, ctx, res, "11. 🏪 Boss商店", 1011)
        sectionBody(root, ctx, res,
            "Boss币仅Boss掉落：基础(1~3枚/地图)，Boss怪×3倍",
            "魂环商品4阶（按地图解锁）：百年·精良(20币)→百万年·精良(1500币)",
            "魂骨商品4阶：百年·精良(50币)→百万年·精良(3500币)",
            "魂核抽取：50Boss币随机获取13种魂核之一",
            "魂力结晶：20Boss币+500魂力（用于修炼池消耗）",
            "优秀魂环：300币 | 完美魂环：800币",
            "优秀魂骨：600币 | 完美魂骨：1500币",
            "解锁条件：进阶商品需打到达对应地图（地图3/5/7解锁各阶）"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §12 限时珍品（金币商店）| §13 魂核系统", 1012), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 12. 限时珍品
        // ===================================================================
        val s12 = sectionTitle(root, ctx, res, "12. ⏳ 限时珍品（金币刷新）", 1012)
        sectionBody(root, ctx, res,
            "每10分钟自动刷新（Lv.65解锁），金币购买稀有魂环/魂骨",
            "每次2个魂环+1个魂骨，按当前地图生成3±3合并层级品级",
            "魂环价格（阶梯递增）：百年5000💰 ~ 百万年2000万💰+",
            "魂骨价格（阶梯递增）：百年5万💰 ~ 百万年2000万💰+",
            "可强制刷新：20000💰立即重新生成一批",
            "含三维度随机成熟度(10%~99.9%)，品质优秀的概率较低",
            "限时珍品是获取高年份高成熟度魂环的重要渠道"
        )
        addDivider(root, ctx, res)

        // ===================================================================
        // 13. 魂核系统
        // ===================================================================
        val s13 = sectionTitle(root, ctx, res, "13. 💠 魂核系统", 1013)
        sectionBody(root, ctx, res,
            "3个槽位（攻击/防御/通用），随转生逐步解锁",
            "13种魂核类别，分为两系：",
            "  基础系：生命·力量·守护·暴击·毁灭·起源（6种）",
            "  特效系：疾风(闪避)·嗜血(吸血)·龙鳞(减伤)·荆棘(反伤)·再生(回血)·虚空(减CD)·不朽(复活)",
            "可升级：消耗同名魂核提升等级，效果随等级增长",
            "获取方式：Boss币抽取(50币) / 金币抽取(50000💰)",
            "装备后永久生效，战斗中按类型触发效果",
            "特效说明：闪避→概率免疫伤害 | 减伤→固定比例减免 | 反伤→反弹伤害",
            " 吸血→攻击回血 | 减CD→降低技能冷却 | 复活→HP归零时复活",
            " 再生→每秒恢复HP | 起源→全属性%加成"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §7 战斗系统（魂核战斗效果）| §11 Boss商店（抽取）", 1007), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 14. 天赋树
        // ===================================================================
        val s14 = sectionTitle(root, ctx, res, "14. 🌳 天赋树系统", 1014)
        sectionBody(root, ctx, res,
            "每次转生获得1点天赋点（转生次数=天赋点数）",
            "4个天赋分支，每分支最多3级：",
            "⚔️ 战神之道：ATK/MATK+6%/+12%/+18%  PDEF/MDEF+3%/+6%/+9%  暴击+5%(Lv3)",
            "💠 魂师之道：装备词缀效果+8%/+16%/+24%（影响魂环+魂骨）",
            "🪙 财富之道：背包扩容费-20%/-40%/-60%  免费扩容1次(Lv3)",
            "🛡️ 神祇之道：生命+10%/+20%/+30%  减伤+5%(Lv3)",
            "天赋点不可重置，请谨慎选择"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §15 转生系统（天赋点来源）", 1015), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 15. 转生系统
        // ===================================================================
        val s15 = sectionTitle(root, ctx, res, "15. 🔄 转生系统（神位传承）", 1015)
        sectionBody(root, ctx, res,
            "100级后开放转生，重置等级至1，保留装备/成就/图鉴/限时珍品",
            "每次转生全属性+10%（倍率=1.0 + 转生次数×0.1）",
            "修炼魂力、离线收益均享受转生倍率加成",
            "",
            "★ 4个天赋分支影响转生保留内容：",
            "  财富LV0:金币全清 / LV1:保留10% / LV2:25% / LV3:50%",
            "  魂师LV0:背包全清 / LV1:保留魂环 / LV2:保留魂环+魂骨 / LV3:全部保留",
            "  战神LV0:地图+塔全清 / LV1:保留地图 / LV2:保留地图+塔层 / LV3:全部保留",
            "  神祇:战斗中减伤（非背包保留型天赋）",
            "",
            "每次转生获得1天赋点，塔内状态/每日副本重置"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §14 天赋树 | §18 功能解锁（流派解锁条件）", 1014), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 16. 卡级修炼
        // ===================================================================
        val s16 = sectionTitle(root, ctx, res, "16. 🔒 卡级修炼系统", 1016)
        sectionBody(root, ctx, res,
            "Lv.10解锁卡级功能，达到10的倍数等级时由玩家决定是否卡级",
            "卡级期间修炼的魂力存入超额储备，自动突破暂停",
            "消耗超额储备可凝练属性（HP+50/级 ATK+10/级 MATK+8/级等）",
            "卡级设计目的：在境界门槛前积累更多属性，为高难度挑战做准备",
            "关闭卡级后超额魂力一次性计入当前境界",
            "注意：卡级不会阻止探索/战斗/转生，仅阻止自动突破"
        )
        root.addView(crossRef(ctx, res, "→ 参见 §3 修炼与突破（自动突破暂停逻辑）", 1003), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addDivider(root, ctx, res)

        // ===================================================================
        // 17. 背包系统
        // ===================================================================
        val s17 = sectionTitle(root, ctx, res, "17. 📦 背包系统", 1017)
        sectionBody(root, ctx, res,
            "基础容量20格，可金币扩容(+5格/次)，价格阶梯递增",
            "魂环/魂骨/魂核共用背包容量（装备中的不计入）",
            "物品可锁定（防误卖），一键卖出全部未锁定物品",
            "掉落过滤：装备页设置不想要的年份/品质组合，自动分解为金币",
            "包满时掉落物品自动分解为金币",
            "扩容消耗类型：GoldSink — 随扩容次数递增",
            "25个背包格是常规玩家的舒适区间"
        )
        addDivider(root, ctx, res)

        // ===================================================================
        // 18. 功能解锁列表
        // ===================================================================
        val s18 = sectionTitle(root, ctx, res, "18. 📋 功能解锁总览", 1018)
        root.addView(tv(ctx, res, "共18项功能，按等级逐步解锁，部分需要转生条件：", R.color.text_primary, 12f, false, 0, 4))
        for (ft in GameEngine.FeatureType.entries) {
            val extra = if (ft.extraPrestigeRequired > 0) "（需转生${ft.extraPrestigeRequired}次）" else ""
            root.addView(tv(ctx, res, "  Lv.${ft.unlockLevel} ${ft.icon} ${ft.displayName}${extra}：${ft.description}", R.color.text_secondary, 12f, false, 0, 1))
        }
        addDivider(root, ctx, res)

        // ===================================================================
        // 19. 附属系统
        // ===================================================================
        val s19 = sectionTitle(root, ctx, res, "19. 📦 附属系统一览", 1019)
        sectionBody(root, ctx, res,
            "🏆 成就系统（Lv.10解锁）：5类20个成就（修炼/魂环/战斗/爬塔/转生），永久属性奖励",
            "📖 图鉴系统：击杀数里程碑（8档）解锁称号+永久攻击加成",
            "  100→初出茅庐  |  500→猎魂勇士  |  2000→百战精英  |  5000→屠戮者",
            "  15000→魂兽克星  |  50000→传奇猎手  |  150000→万人斩  |  500000→征服者",
            "💀 每日副本（Lv.75+转生1次解锁）：5阶难度(简单→地狱)，每天1次，丰厚金币+材料",
            "💡 操作确认可跳过：修炼/突破/觉醒/转生均可设置跳过确认",
            "🔄 技能开关（Lv.55解锁）：手动控制各魂环技能的自动释放"
        )
        addDivider(root, ctx, res)

        // ===================================================================
        // 20. 数值公式总览
        // ===================================================================
        val s20 = sectionTitle(root, ctx, res, "20. 🔢 数值公式总览", 1020)
        sectionBody(root, ctx, res,
            "【基础属性】HP=50×等级+100  |  ATK=10×等级  |  MATK=8×等级",
            "  PDEF=5×等级  |  MDEF=4×等级",
            "【境界加成】1.0 + (等级/10) × 0.25（流派修正后/装备附加前）",
            "【突破消耗】150 × 等级^1.65",
            "【修炼魂力】等级 × 28 × 转生倍率（每次手动修炼）",
            "【转生倍率】1.0 + 转生次数 × 0.1",
            "",
            "【伤害公式】物伤 = ATK × (1 - 物防/(物防+200)) 最低10%穿透",
            "  魔伤 = MATK × (1 - 魔防/(魔防+200)) 最低10%穿透",
            "  暴击伤害 = 基础伤害 × 爆伤%",
            "",
            "【魂环·等效年份】nextBaseYear × (percentage/1000)",
            "  nextBaseYear: 百年→1000  千年→10000  万年→100000  十万年→1000000  百万年→10000000",
            "【魂环·成熟度倍率】1.0 + percentage/1000.0（10.0%~99.9% → 1.1×~2.0×）",
            "【魂环·词缀效果】baseValue(合并层级0~24) × 成熟度倍率 × (1.0 + 魂师Lv×0.08)",
            "【魂环·吸收容量】根骨 × 6（最低100）",
            "【魂环·负荷】直接等于等效年份",
            "【根骨】ATK×3 + MATK×3 + PDEF×2 + MDEF×2 + HP/100",
            "",
            "【魂骨·强化效果】1.0 + 等级×0.15（最高20级=4.0倍）",
            "【魂骨·强化消耗】1000 × (1.0 + 等级×0.8)",
            "【魂骨·套装】2件激活Tier2效果，全件激活Full效果",
            "",
            "【关卡缩放】1.0 + 关卡×0.12",
            "【战斗魂力上限】100 + 等级×5  |  【魂力回复】(3+等级/20)/回合",
            "【HP恢复】非战斗中每秒10%最大生命",
            "【离线时长】最多12小时（43200秒），低于60秒不触发"
        )
        addDivider(root, ctx, res)

        // ===== 底部 =====
        root.addView(tv(ctx, res, "📖 百科生成于 2026.06 · V5.0 定型版本", R.color.text_secondary, 11f, false, 8, 12))

        return view
    }

    // ===================== 辅助函数 =====================

    private var _nextId = 9000
    private fun nextViewId(): Int = _nextId++

    /** 可点击的章节标题（注册View ID用于跳转） */
    private fun sectionTitle(container: LinearLayout, ctx: android.content.Context, res: android.content.res.Resources,
                             title: String, sectionId: Int): TextView {
        val id = nextViewId()
        viewIds.add(id)
        return tv(ctx, res, title, R.color.text_gold, 16f, true, 10, 4).also {
            it.id = id
            container.addView(it)
        }
    }

    /** 章节正文（列表） */
    private fun sectionBody(container: LinearLayout, ctx: android.content.Context, res: android.content.res.Resources,
                            vararg items: String) {
        for (item in items) {
            if (item.isEmpty()) {
                container.addView(tv(ctx, res, "", R.color.text_secondary, 8f, false, 0, 2))
            } else {
                container.addView(tv(ctx, res, "  • $item", R.color.text_secondary, 12f, false, 1, 1))
            }
        }
    }

    /** 可点击的目录项 */
    private fun clickableTocItem(ctx: android.content.Context, res: android.content.res.Resources,
                                 text: String, targetId: Int): TextView {
        val sp = SpannableString("↪  $text")
        sp.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                val target = containerView?.findViewById<View>(targetId) ?: return
                containerView?.post { containerView?.scrollTo(0, target.top) }
            }
        }, 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(ForegroundColorSpan(res.getColor(R.color.accent)), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return TextView(ctx).apply {
            setText(sp)
            movementMethod = LinkMovementMethod.getInstance()
            textSize = 13f
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
            setLineSpacing(2f, 1.15f)
        }
    }

    /** 交叉引用文本（可点击跳转到其他章节） */
    private fun crossRef(ctx: android.content.Context, res: android.content.res.Resources,
                         text: String, targetId: Int): TextView {
        val sp = SpannableString(text)
        sp.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                val target = containerView?.findViewById<View>(targetId) ?: return
                containerView?.post { containerView?.scrollTo(0, target.top) }
            }
        }, 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(ForegroundColorSpan(res.getColor(R.color.rarity_rare)), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(StyleSpan(Typeface.ITALIC), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return TextView(ctx).apply {
            setText(sp)
            movementMethod = LinkMovementMethod.getInstance()
            textSize = 12f
            setPadding(dpToPx(12), dpToPx(4), dpToPx(8), dpToPx(4))
            setLineSpacing(2f, 1.15f)
        }
    }

    private fun addDivider(container: LinearLayout, ctx: android.content.Context, res: android.content.res.Resources) {
        val div = View(ctx).apply {
            setBackgroundColor(res.getColor(R.color.divider))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, dpToPx(8), 0, 0) }
        }
        container.addView(div)
    }

    private fun tv(ctx: android.content.Context, res: android.content.res.Resources,
                   text: String, colorRes: Int, size: Float, bold: Boolean, padTop: Int, padBot: Int): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(res.getColor(colorRes))
            textSize = size
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(8), dpToPx(padTop), dpToPx(8), dpToPx(padBot))
            setLineSpacing(2f, 1.15f)
        }
    }

    /** 词缀行：彩色粗体名称 + 灰色描述 */
    private fun affixLine(ctx: android.content.Context, res: android.content.res.Resources,
                          name: String, desc: String, color: Int): TextView {
        val full = "  [$name] $desc"
        val sp = SpannableString(full)
        val nameStart = full.indexOf(name)
        val nameEnd = nameStart + name.length
        sp.setSpan(ForegroundColorSpan(color), nameStart, nameEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(StyleSpan(Typeface.BOLD), nameStart, nameEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return TextView(ctx).apply {
            text = sp
            setTextColor(res.getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
            setLineSpacing(2f, 1.15f)
        }
    }

    private fun schoolDesc(school: SoulSchool, mod: SchoolStatMods, extra: String = ""): String {
        val sb = StringBuilder()
        sb.append("${school.icon} ${school.displayName}${extra}")
        sb.append("：HP×${String.format("%.0f", mod.hp * 100)}% ATK×${String.format("%.0f", mod.atk * 100)}%")
        sb.append(" MATK×${String.format("%.0f", mod.matk * 100)}%")
        sb.append(" PDEF×${String.format("%.0f", mod.pdef * 100)}% MDEF×${String.format("%.0f", mod.mdef * 100)}%")
        if (mod.critRateBonus != 0) sb.append(" 暴击+${mod.critRateBonus}%")
        if (mod.critDmgBonus != 0) sb.append(" 爆伤+${mod.critDmgBonus}%")
        return sb.toString()
    }

    private fun schoolColor(school: SoulSchool): Int = when (school) {
        SoulSchool.BALANCED -> R.color.rarity_rare
        SoulSchool.PHYSICAL -> R.color.rarity_epic
        SoulSchool.MAGIC -> R.color.rarity_legendary
        SoulSchool.SUPPORT -> R.color.rarity_uncommon
        SoulSchool.CONTROL -> R.color.rarity_rare
        SoulSchool.ASSASSIN -> R.color.rarity_epic
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
