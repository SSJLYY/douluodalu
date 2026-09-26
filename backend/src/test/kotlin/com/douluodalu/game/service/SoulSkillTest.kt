package com.douluodalu.game.service

import com.douluodalu.game.model.GameBalance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * 第二十轮武魂技能断言（纯函数，不起 Spring，全部固定种子）：
 *  - 触发模型：冷却制 (r-1) % cooldown == 0（第 1 回合首放，之后每 N 回合一次）；
 *  - 四类结算：SINGLE 倍率 / MULTI 总倍率（hitCount 折算） / IGNORE def×0.5 软化 / HEAL 回血与封顶；
 *  - 掷点不变性：技能回合三掷照常、无技能路径逐位一致（BattleMathTest 未动的互补证明）；
 *  - 12 条技能数据 shared 逐字移植锚定（shared Models.kt MartialSoulPool 为唯一权威）。
 */
class SoulSkillTest {

    private fun player(
        atk: Long = 100, matk: Long = 100, pdef: Long = 0, mdef: Long = 0,
        critRate: Int = 0, critDmg: Int = 150,
        skill: GameBalance.SkillDef? = null
    ) = GameService.CombatStats(atk, matk, pdef, mdef, critRate, critDmg, skill)

    private fun monster(
        hp: Long = 1_000_000, atk: Int = 10, matk: Int = atk, pdef: Int = 0, mdef: Int = 0
    ) = GameService.MonsterStats(hp, atk, matk, pdef, mdef)

    /** shared 引擎同源减伤公式（GameService.defFactor 镜像，锚定公式不漂移） */
    private fun defFactor(def: Long): Double =
        (1.0 - def.toDouble() / (def + GameBalance.DEF_K)).coerceIn(0.1, 1.0)

    /** 同种子复刻第 1 回合玩家三掷（类型→暴击→浮动；resolveBattle 掷点次序契约同源） */
    private data class FirstRolls(val magic: Boolean, val crit: Boolean, val base: Long, val variance: Long)

    private fun firstRolls(p: GameService.CombatStats, seed: Long): FirstRolls {
        val r = Random(seed)
        val magic = r.nextInt(100) < GameBalance.ATTACK_MAGIC_SHARE
        val crit = r.nextInt(100) < p.critRate
        val base = if (magic) p.matk else p.atk
        val variance = r.nextLong((base / 5).coerceAtLeast(1))
        return FirstRolls(magic, crit, base, variance)
    }

    // ======== 触发模型：冷却制时点 ========

    @Test
    fun `cooldown 3 fires skill on rounds 1 4 7 and not on 2 3 5 6`() {
        val p = player(atk = 100, skill = GameBalance.SkillDef("测试技", GameBalance.SkillType.SINGLE_DAMAGE, 100, 3))
        // power=100（倍率 1.0）+ 零防：技能回合伤害与普攻相同 → 7 回合全部完整留痕
        val outcome = GameService.resolveBattle(p, 100_000, monster(), 7, Random(7), playerMaxHp = 100_000)
        assertEquals(7, outcome.log.size)
        assertEquals(listOf(1, 4, 7), outcome.log.filter { it.skillName != null }.map { it.round },
            "cooldown=3 应在第 1/4/7 回合释放（(r-1)%3==0）")
        assertEquals(listOf(2, 3, 5, 6), outcome.log.filter { it.skillName == null }.map { it.round },
            "第 2/3/5/6 回合不应释放")
    }

    @Test
    fun `cooldown 4 and 5 shift the firing cadence accordingly`() {
        val p4 = player(atk = 100, skill = GameBalance.SkillDef("测试技", GameBalance.SkillType.SINGLE_DAMAGE, 100, 4))
        val o4 = GameService.resolveBattle(p4, 100_000, monster(), 7, Random(7), playerMaxHp = 100_000)
        assertEquals(listOf(1, 5), o4.log.filter { it.skillName != null }.map { it.round }, "cooldown=4 → 第 1/5 回合")
        val p5 = player(atk = 100, skill = GameBalance.SkillDef("测试技", GameBalance.SkillType.SINGLE_DAMAGE, 100, 5))
        val o5 = GameService.resolveBattle(p5, 100_000, monster(), 7, Random(7), playerMaxHp = 100_000)
        assertEquals(listOf(1, 6), o5.log.filter { it.skillName != null }.map { it.round }, "cooldown=5 → 第 1/6 回合")
    }

    // ======== 掷点不变性：无技能路径零漂移 + 技能回合不增删掷点 ========

    @Test
    fun `skill round consumes the same three rolls and keeps the rng stream bitwise stable`() {
        val seed = 20260926L
        val base = GameService.resolveBattle(
            player(atk = 150, matk = 60, critRate = 18, critDmg = 160), 10_000,
            monster(pdef = 25, mdef = 25), 30, Random(seed)
        )
        // cooldown=100_000 → 仅第 1 回合触发；power=100 → 倍率 1.0：除 skillName 外与无技能流逐位一致
        val withSkill = GameService.resolveBattle(
            player(atk = 150, matk = 60, critRate = 18, critDmg = 160,
                skill = GameBalance.SkillDef("仅一回合", GameBalance.SkillType.SINGLE_DAMAGE, 100, 100_000)),
            10_000, monster(pdef = 25, mdef = 25), 30, Random(seed)
        )
        assertEquals(base.log[0].playerDamage, withSkill.log[0].playerDamage,
            "倍率 1.0 的技能回合伤害必须与普攻逐位一致（三掷照常、同一算式）")
        assertEquals(base.log[0].monsterDamage, withSkill.log[0].monsterDamage)
        assertEquals(base.log.drop(1), withSkill.log.drop(1),
            "第 2 回合起整行逐位一致——若技能回合多掷/少掷/换序，RNG 流移位必在此炸")
        assertEquals(base.playerHpLeft, withSkill.playerHpLeft)
        assertEquals(base.rounds, withSkill.rounds)
    }

    // ======== SINGLE_DAMAGE：倍率精确 ========

    @Test
    fun `single damage skill applies power multiplier exactly`() {
        // 蓝银草·缠绕（SINGLE 140/3），零防零暴击：第 1 回合伤害 = (typeAtk+浮动)×1.4（截断）
        val skill = GameBalance.soulByName("蓝银草")!!.skill
        assertEquals(GameBalance.SkillType.SINGLE_DAMAGE, skill.type)
        val seed = 20260926L
        val p = player(atk = 200, skill = skill)
        val outcome = GameService.resolveBattle(p, 10_000, monster(), 1, Random(seed), playerMaxHp = 10_000)
        val rolls = firstRolls(p, seed)
        val expected = ((rolls.base + rolls.variance) * (skill.power / 100.0)).toLong().coerceAtLeast(1)
        assertEquals(expected, outcome.log[0].playerDamage,
            "SINGLE 伤害 = (typeAtk+浮动)×power/100×defFactor（零防下 defFactor=1.0）")
        assertEquals("缠绕", outcome.log[0].skillName)

        // 暴击同样作用于技能伤害：critDmg=200（×2.0 双精度精确缩放）→ 技能暴击伤害 = 非暴击 ×2
        val allCrit = GameService.resolveBattle(
            player(atk = 200, critRate = 100, critDmg = 200, skill = skill),
            10_000, monster(), 1, Random(seed), playerMaxHp = 10_000
        )
        assertEquals(outcome.log[0].playerDamage * 2, allCrit.log[0].playerDamage)
    }

    // ======== MULTI_HIT：hitCount 折算总倍率 ========

    @Test
    fun `multi hit skill folds hit count into total multiplier`() {
        val seed = 20260926L
        // 幽冥灵猫·幽冥突刺（MULTI 100/4）：hitCount=(100/50)=2 → 总倍率 2.0，零防零暴击下恰为普攻 2 倍
        val stab = GameBalance.soulByName("幽冥灵猫")!!.skill
        assertEquals(GameBalance.SkillType.MULTI_HIT, stab.type)
        val normal = GameService.resolveBattle(player(atk = 150), 100_000, monster(), 1, Random(seed), playerMaxHp = 100_000)
        val hit2 = GameService.resolveBattle(player(atk = 150, skill = stab), 100_000, monster(), 1, Random(seed), playerMaxHp = 100_000)
        assertEquals(normal.log[0].playerDamage * 2, hit2.log[0].playerDamage,
            "2 段 ×100% = 2.0 总倍率（v1 单发等价结算）")

        // 昊天锤·乱披风锤法（150/5）：hitCount=3 → 总倍率 4.5（复刻掷点断言精确值）
        val hammer = GameBalance.soulByName("昊天锤")!!.skill
        val p = player(atk = 150, skill = hammer)
        val hit3 = GameService.resolveBattle(p, 100_000, monster(), 1, Random(seed), playerMaxHp = 100_000)
        val rolls = firstRolls(p, seed)
        val expected = ((rolls.base + rolls.variance) * (hammer.power * 3 / 100.0)).toLong().coerceAtLeast(1)
        assertEquals(expected, hit3.log[0].playerDamage, "3 段 ×150% = 4.5 总倍率")

        // hitCount 夹取边界：(power/50).coerceIn(2,5) —— power=250 → 5 段（12.5 倍）；power=40 → 下限 2 段（0.8 倍）
        val capped = GameBalance.SkillDef("上限", GameBalance.SkillType.MULTI_HIT, 250, 3)
        val pCap = player(atk = 150, skill = capped)
        val outCap = GameService.resolveBattle(pCap, 100_000, monster(), 1, Random(seed), playerMaxHp = 100_000)
        assertEquals(((firstRolls(pCap, seed).base + firstRolls(pCap, seed).variance) * (250 * 5 / 100.0)).toLong(),
            outCap.log[0].playerDamage)
        val floored = GameBalance.SkillDef("下限", GameBalance.SkillType.MULTI_HIT, 40, 3)
        val pFloor = player(atk = 150, skill = floored)
        val outFloor = GameService.resolveBattle(pFloor, 100_000, monster(), 1, Random(seed), playerMaxHp = 100_000)
        assertEquals(((firstRolls(pFloor, seed).base + firstRolls(pFloor, seed).variance) * (40 * 2 / 100.0)).toLong(),
            outFloor.log[0].playerDamage, "power<100 时 hitCount 下限 2（总倍率 0.8）")
    }

    // ======== IGNORE_DEFENSE：def×0.5 软化 ========

    @Test
    fun `ignore defense skill halves the effective defense`() {
        // 六翼天使·天使圣光（IGNORE 180/4）vs 同 power 同面板的 SINGLE：def>0 时 IGNORE 严格更高
        // （差异只来自 defFactor(25×0.5) > defFactor(25)），def=0 时两者逐位相等（乘区归一）
        val ignore = GameBalance.soulByName("六翼天使")!!.skill
        assertEquals(GameBalance.SkillType.IGNORE_DEFENSE, ignore.type)
        val singleLike = GameBalance.SkillDef("同倍率对照", GameBalance.SkillType.SINGLE_DAMAGE, 180, 4)
        val seed = 20260926L
        val m = monster(atk = 10, pdef = 25, mdef = 25)
        val outSingle = GameService.resolveBattle(player(atk = 300, skill = singleLike), 10_000, m, 1, Random(seed), playerMaxHp = 10_000)
        val outIgnore = GameService.resolveBattle(player(atk = 300, skill = ignore), 10_000, m, 1, Random(seed), playerMaxHp = 10_000)
        val rolls = firstRolls(player(atk = 300, skill = ignore), seed)
        // 25×0.5=12.5 → Long 截断 12（实现为 (rawDef×SKILL_DEF_IGNORE_FACTOR).toLong()）
        val expected = ((rolls.base + rolls.variance) * (180 / 100.0) * defFactor((25 * GameBalance.SKILL_DEF_IGNORE_FACTOR).toLong()))
            .toLong().coerceAtLeast(1)
        assertEquals(expected, outIgnore.log[0].playerDamage,
            "IGNORE 伤害 = (typeAtk+浮动)×power/100×defFactor(def×0.5)")
        assertTrue(outIgnore.log[0].playerDamage > outSingle.log[0].playerDamage,
            "def=25 时破甲（def×0.5）必须严格高于同倍率普通结算（实测 ${outIgnore.log[0].playerDamage} vs ${outSingle.log[0].playerDamage}）")
        // def=0：SKILL_DEF_IGNORE_FACTOR 无感，两流逐位一致
        val m0 = monster(atk = 10)
        val zeroSingle = GameService.resolveBattle(player(atk = 300, skill = singleLike), 10_000, m0, 1, Random(seed), playerMaxHp = 10_000)
        val zeroIgnore = GameService.resolveBattle(player(atk = 300, skill = ignore), 10_000, m0, 1, Random(seed), playerMaxHp = 10_000)
        assertEquals(zeroSingle.log[0].playerDamage, zeroIgnore.log[0].playerDamage)
    }

    // ======== HEAL：回血量、上限封顶、玩家不攻击 ========

    @Test
    fun `heal skill restores fraction of maxHp caps at maxHp and skips player damage`() {
        val heal = GameBalance.soulByName("七宝琉璃塔")!!.skill // 七宝转出 HEAL 30/5
        assertEquals(GameBalance.SkillType.HEAL, heal.type)
        val maxHp = 1000L
        val m = monster(atk = 10)
        val seed = 99L

        // 怪物回合复刻（玩家三掷照常先行消耗，HEAL 不消费掷点结果；pdef=0 → defFactor=1.0）
        val r = Random(seed)
        r.nextInt(100)                       // 玩家类型掷
        r.nextInt(100)                       // 玩家暴击掷
        r.nextLong((100L / 5).coerceAtLeast(1)) // 玩家浮动掷（atk=100）
        val mMagic = r.nextInt(100) < GameBalance.ATTACK_MAGIC_SHARE
        val mVariance = r.nextLong((m.atk / 5).coerceAtLeast(1).toLong())
        val mDmg = (((if (mMagic) m.matk.toLong() else m.atk.toLong()) + mVariance) * defFactor(0))
            .toLong().coerceAtLeast(1)

        // 回血 = maxHp×30% = 300：400 → 700，再挨怪一刀
        val outcome = GameService.resolveBattle(player(atk = 100, skill = heal), 400, m, 1, Random(seed), playerMaxHp = maxHp)
        assertEquals(0L, outcome.log[0].playerDamage, "HEAL 回合玩家不攻击（playerDamage=0）")
        assertEquals(700L - mDmg, outcome.log[0].playerHpAfter, "回血量 = maxHp×power/100（400+300=700）")
        assertEquals(outcome.log[0].monsterHpBefore, outcome.log[0].monsterHpAfter, "HEAL 回合怪物不掉血")
        assertEquals("七宝转出", outcome.log[0].skillName)
        assertEquals(1, outcome.log[0].round)

        // 上限封顶：950+300 > 1000 → 回到满血 1000 再挨刀
        val capped = GameService.resolveBattle(player(atk = 100, skill = heal), 950, m, 1, Random(seed), playerMaxHp = maxHp)
        assertEquals(1000L - mDmg, capped.log[0].playerHpAfter, "回血不得溢出 maxHp（950+300 封顶 1000）")

        // 九宝琉璃塔·九宝护体（HEAL 50/5）：400+500=900
        val heal50 = GameBalance.soulByName("九宝琉璃塔")!!.skill
        val out50 = GameService.resolveBattle(player(atk = 100, skill = heal50), 400, m, 1, Random(seed), playerMaxHp = maxHp)
        assertEquals(900L - mDmg, out50.log[0].playerHpAfter, "50% 档回血量 = maxHp×50%")
    }

    // ======== BERSERK：本轮不实现结算，回落普通攻击 ========

    @Test
    fun `berserk skill type falls back to normal attack until implemented`() {
        val berserk = GameBalance.SkillDef("狂暴测试", GameBalance.SkillType.BERSERK, 999, 3)
        val seed = 5L
        val normal = GameService.resolveBattle(player(atk = 200), 10_000, monster(pdef = 25, mdef = 25), 2, Random(seed))
        val withB = GameService.resolveBattle(player(atk = 200, skill = berserk), 10_000, monster(pdef = 25, mdef = 25), 2, Random(seed))
        assertEquals(normal.log[0].playerDamage, withB.log[0].playerDamage, "BERSERK 回合回落普通攻击结算")
        assertNull(withB.log[0].skillName, "BERSERK 未实现结算 → 该回合不视为技能回合（skillName=null）")
        assertEquals(normal.log.drop(1), withB.log.drop(1), "后续回合 RNG 流不受影响")
    }

    // ======== 数据移植锚定：12 条逐字对照 shared Models.kt MartialSoulPool ========

    private data class SkillAnchor(
        val soul: String, val name: String, val type: GameBalance.SkillType, val power: Int, val cooldown: Int
    )

    @Test
    fun `twelve soul skills are transplanted verbatim from shared`() {
        val anchors = listOf(
            SkillAnchor("蓝银草", "缠绕", GameBalance.SkillType.SINGLE_DAMAGE, 140, 3),
            SkillAnchor("幽冥灵猫", "幽冥突刺", GameBalance.SkillType.MULTI_HIT, 100, 4),
            SkillAnchor("柔骨兔", "腰弓", GameBalance.SkillType.SINGLE_DAMAGE, 180, 4),
            SkillAnchor("蓝银皇", "蓝银囚笼", GameBalance.SkillType.SINGLE_DAMAGE, 160, 3),
            SkillAnchor("七宝琉璃塔", "七宝转出", GameBalance.SkillType.HEAL, 30, 5),
            SkillAnchor("邪火凤凰", "凤凰啸天击", GameBalance.SkillType.SINGLE_DAMAGE, 220, 4),
            SkillAnchor("白虎", "白虎烈光波", GameBalance.SkillType.MULTI_HIT, 120, 4),
            SkillAnchor("昊天锤", "乱披风锤法", GameBalance.SkillType.MULTI_HIT, 150, 5),
            SkillAnchor("九宝琉璃塔", "九宝护体", GameBalance.SkillType.HEAL, 50, 5),
            SkillAnchor("六翼天使", "天使圣光", GameBalance.SkillType.IGNORE_DEFENSE, 180, 4),
            SkillAnchor("海神三叉戟", "海神之怒", GameBalance.SkillType.SINGLE_DAMAGE, 250, 5),
            SkillAnchor("修罗魔剑", "修罗斩", GameBalance.SkillType.IGNORE_DEFENSE, 300, 5)
        )
        assertEquals(12, GameBalance.MARTIAL_SOULS.size)
        for (a in anchors) {
            val soul = GameBalance.soulByName(a.soul) ?: fail("武魂池缺少 ${a.soul}")
            assertEquals(a.name, soul.skill.name, "${a.soul} 技能名应与 shared 逐字一致")
            assertEquals(a.type, soul.skill.type, "${a.soul} 技能类型应与 shared 逐字一致")
            assertEquals(a.power, soul.skill.power, "${a.soul} 技能威力应与 shared 逐字一致")
            assertEquals(a.cooldown, soul.skill.cooldown, "${a.soul} 技能冷却应与 shared 逐字一致")
        }
        assertEquals(anchors.map { it.soul }, GameBalance.MARTIAL_SOULS.map { it.name },
            "锚定表必须覆盖全部 12 条武魂且顺序一致")
    }

    // ======== 反查兜底 ========

    @Test
    fun `soulSkillOf resolves by name and tolerates dirty data`() {
        assertNull(GameService.soulSkillOf(null), "未觉醒 → null")
        assertNull(GameService.soulSkillOf("历史脏数据名字"), "反查失败兜底 null（战斗组装宽容）")
        assertEquals("白虎烈光波", GameService.soulSkillOf("白虎")!!.name)
    }

    // ======== 塔日志：技能随 CombatStats 携带且同种子可复现 ========

    @Test
    fun `tower battle log carries skill and stays seed reproducible`() {
        val p = GameService.CombatStats(
            atk = 500, matk = 300, critRate = 10,
            skill = GameBalance.soulByName("蓝银草")!!.skill
        )
        val a = GameService.buildTowerBattleLog(42L, 7, true, p, 2000)
        val b = GameService.buildTowerBattleLog(42L, 7, true, p, 2000)
        assertEquals(a, b, "同 (userId, floor, won) 两次调用日志逐字段一致（技能不破坏独立种子回放）")
        assertTrue(a.isNotEmpty())
        assertEquals("缠绕", a.first().skillName, "第 1 回合应首放武魂技能")
    }
}
