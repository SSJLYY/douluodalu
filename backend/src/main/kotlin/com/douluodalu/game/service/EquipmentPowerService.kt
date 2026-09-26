package com.douluodalu.game.service

import com.douluodalu.game.dto.PowerDetailDto
import com.douluodalu.game.entity.EquippedBone
import com.douluodalu.game.entity.EquippedCore
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.AchievementRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import com.douluodalu.game.repository.EquippedRingRepository
import org.springframework.stereotype.Service

/**
 * 装备战力计算（任务#20 / 仿真报告 P7 修复）：把已装备的魂环/魂骨/魂核折算成攻防加成与战斗力。
 *
 * 公式移植自 shared 引擎 GameEngine.calcAttributes() 的精神（不照搬整个引擎）：
 *  - 魂环：基础值 × (年份档位+1) × 品质倍率 × 成熟度倍率(1 + percentage/1000)。
 *    shared 版中年份差距由词缀基础值体现、成熟度乘 1.1~2.0 倍，这里用 (yearOrdinal+1) 做年份档位加权。
 *  - 魂骨：基础值 × (年份档位+1) × 品质倍率 × (1 + 强化等级 × BONE_ENHANCE_PER_LEVEL)。
 *  - 魂核：shared「力量」魂核按 atk += atk × value/100 百分比附加，这里折算为基础攻击的
 *    CORE_ATK_PCT_WEIGHT × coreValue/100 × 稀有度倍率，并用 CORE_ATK_PCT_CAP 封顶防失控。
 *
 * 品质倍率表取自《魂环负荷与年份对应关系.md》的 qualityMult [1.0, 1.2, 1.5, 1.8, 2.2]。
 * 注：魂环年份负荷校验在任务#21 由 RingLoadCalculator 实现（逐行移植 shared SoulRingSystem）。
 * 成就系统集成：bonusFor（实例方法，战斗/塔/状态组装的唯一 choke point）把已解锁成就的
 * hp/atk 加成并入返回值；只依赖 AchievementRepository（查已解锁 id 集合→按定义求和），
 * 不依赖 AchievementService（防循环）。
 * 转生系统集成：bonusFor 增加转数参数（默认 0），装备+成就合计加成乘转生倍率（applyPrestige）；
 * detail 同步增加第 6 行 prestige（倍率增量单列），求和恒等从五行升级为六行。
 * 武魂集成（第十八轮）：bonusFor/bonus 本体不并入武魂（武魂单列 detail 第 7 行 soul，
 * 经 soulBonus 换算 + applyPrestige 乘倍率后由调用方并入战斗/战力口径），七行求和恒等。
 * 流派集成（第十九轮）：流派系数经 applySchool 折进战力加成包（school=null 恒等零漂移），
 * detail 增加第 8 行 school（差值法，prestige/soul 行同款），八行求和恒等。
 */
/**
 * 单件装备折出的战斗加成（成就加成复用同一形状）。
 * 第十七轮战斗模型扩展：五属性（matk/pdef/mdef/critRate/critDmg）加成接入战斗结算与战力折算；
 * 装备侧本轮无五属性数据（affixesJson 未生成，见 bonus() 注释），五属性加成当前只来自成就。
 */
data class EquipmentBonus(
    val atkBonus: Long,
    val hpBonus: Long,
    /** 魔攻加成（长整，与 atkBonus 同量纲） */
    val matkBonus: Long = 0,
    /** 物防/魔防加成 */
    val pdefBonus: Long = 0,
    val mdefBonus: Long = 0,
    /** 暴击率加成（百分点数：1 = 1%）与暴击伤害加成（百分点数：10 = +10% 爆伤） */
    val critRateBonus: Long = 0,
    val critDmgBonus: Long = 0
)

@Service
class EquipmentPowerService(
    private val equippedRingRepo: EquippedRingRepository,
    private val equippedBoneRepo: EquippedBoneRepository,
    private val equippedCoreRepo: EquippedCoreRepository,
    private val achievementRepo: AchievementRepository
) {

    /** 读取该玩家已装备的魂环/魂骨/魂核 + 已解锁成就，计算战斗加成与战斗力（成就加成即时生效）。
     *  转生集成：装备+成就合计加成乘转生倍率（prestigeCount=0 时倍率恒 1.0，与旧版行为逐位一致）。
     *  第十七轮扩展：五属性加成随 EquipmentBonus 七字段全量透传（装备侧恒 0，成就侧带值）。 */
    fun bonusFor(userId: Long, level: Int, prestigeCount: Int = 0): EquipmentBonus {
        val equip = bonus(
            level,
            equippedRingRepo.findByUserId(userId),
            equippedBoneRepo.findByUserId(userId),
            equippedCoreRepo.findByUserId(userId)
        )
        val ach = achievementBonus(achievementRepo.findByUserId(userId).map { it.achievementId })
        return applyPrestige(plus(equip, ach), prestigeCount)
    }

    companion object {
        private fun qualityMult(ordinal: Int): Double =
            GameBalance.EQUIP_QUALITY_MULT.getOrElse(ordinal.coerceIn(0, GameBalance.EQUIP_QUALITY_MULT.size - 1)) { 1.0 }

        /** 单魂环攻击加成（Double，未取整）：基础值 × 年份档位 × 品质倍率 × 成熟度倍率 */
        private fun ringAtkOf(r: EquippedRing): Double =
            GameBalance.RING_ATK_WEIGHT * ringMult(r)

        /** 单魂环生命加成（Double，未取整） */
        private fun ringHpOf(r: EquippedRing): Double =
            GameBalance.RING_HP_WEIGHT * ringMult(r)

        private fun ringMult(r: EquippedRing): Double =
            (1.0 + r.yearOrdinal.coerceIn(0, 4)) *
                    qualityMult(r.qualityOrdinal) *
                    (1.0 + r.percentage / 1000.0)

        /** 单魂骨攻击加成（Double，未取整）：基础值 × 年份档位 × 品质倍率 × 强化倍率 */
        private fun boneAtkOf(b: EquippedBone): Double =
            GameBalance.BONE_ATK_WEIGHT * boneMult(b)

        /** 单魂骨生命加成（Double，未取整） */
        private fun boneHpOf(b: EquippedBone): Double =
            GameBalance.BONE_HP_WEIGHT * boneMult(b)

        private fun boneMult(b: EquippedBone): Double =
            (1.0 + b.yearOrdinal.coerceIn(0, 4)) *
                    qualityMult(b.qualityOrdinal) *
                    (1.0 + b.enhanceLevel * GameBalance.BONE_ENHANCE_PER_LEVEL)

        /** 单魂核攻击加成（Double，未取整）：基础攻击 × 折算百分比（受 CORE_ATK_PCT_CAP 封顶） */
        private fun coreAtkOf(c: EquippedCore, baseAtk: Long): Double =
            baseAtk * corePct(c)

        private fun corePct(c: EquippedCore): Double =
            (GameBalance.CORE_ATK_PCT_WEIGHT * c.coreValue / 100.0 *
                    qualityMult(c.rarityOrdinal)).coerceAtMost(GameBalance.CORE_ATK_PCT_CAP)

        /**
         * 纯函数：已解锁成就集合的七字段加成求和（AchievementService.unlockedBonus 与
         * bonusFor 同源，防止两处口径漂移）。第十七轮战斗模型扩展起七字段全兑现：
         * matk/pdef/mdef/critRate/critDmg 经 EquipmentBonus 进 resolveBattle 与 powerOf
         * （cult_30 的 pdef=5 等数据自此实际生效）。
         * 未知 id（定义表已下线的历史记录行）静默忽略。
         */
        fun achievementBonus(unlockedIds: Collection<String>): EquipmentBonus {
            var atk = 0L
            var hp = 0L
            var matk = 0L
            var pdef = 0L
            var mdef = 0L
            var critRate = 0L
            var critDmg = 0L
            for (id in unlockedIds) {
                GameBalance.ACHIEVEMENT_BY_ID[id]?.let {
                    atk += it.rewards.atk
                    hp += it.rewards.hp
                    matk += it.rewards.matk
                    pdef += it.rewards.pdef
                    mdef += it.rewards.mdef
                    critRate += it.rewards.critRate
                    critDmg += it.rewards.critDmg
                }
            }
            return EquipmentBonus(atk, hp, matk, pdef, mdef, critRate, critDmg)
        }

        /** 纯函数：由装备列表与等级计算加成（不依赖 Spring，便于测试与仿真镜像复用）。
         *  achievementBonus：成就加成并入返回值（默认零 = 既有四参调用行为不变）。
         *  装备侧五属性：装备实体仅有 affixesJson 透传字段且掉落侧从未生成（恒 null），
         *  魂环/魂骨/魂核本轮无可折算的 matk/pdef/mdef/crit 数据 → 五属性加成恒 0，
         *  只透传成就部分；转生武魂下轮接入时在此补充单件折算公式。 */
        fun bonus(
            level: Int,
            rings: List<EquippedRing>,
            bones: List<EquippedBone>,
            cores: List<EquippedCore>,
            achievementBonus: EquipmentBonus = EquipmentBonus(0, 0)
        ): EquipmentBonus {
            var atk = 0.0
            var hp = 0.0
            for (r in rings) {
                atk += ringAtkOf(r)
                hp += ringHpOf(r)
            }
            for (b in bones) {
                atk += boneAtkOf(b)
                hp += boneHpOf(b)
            }
            val baseAtk = baseAttack(level)
            for (c in cores) {
                atk += coreAtkOf(c, baseAtk)
            }
            return EquipmentBonus(
                atk.toLong() + achievementBonus.atkBonus, hp.toLong() + achievementBonus.hpBonus,
                achievementBonus.matkBonus, achievementBonus.pdefBonus, achievementBonus.mdefBonus,
                achievementBonus.critRateBonus, achievementBonus.critDmgBonus
            )
        }

        /** 七字段逐项相加（bonusFor / detail 合并装备与成就加成共用，防止漏字段） */
        fun plus(a: EquipmentBonus, b: EquipmentBonus): EquipmentBonus = EquipmentBonus(
            a.atkBonus + b.atkBonus, a.hpBonus + b.hpBonus, a.matkBonus + b.matkBonus,
            a.pdefBonus + b.pdefBonus, a.mdefBonus + b.mdefBonus,
            a.critRateBonus + b.critRateBonus, a.critDmgBonus + b.critDmgBonus
        )

        /**
         * 武魂七属性 → EquipmentBonus 形状（第十八轮武魂觉醒）：atk 1:1、hp 1:1、
         * matk/pdef/mdef/critRate/critDmg 直映。调用方（GameService.battle/塔/状态组装与
         * detail 的 soul 行）再统一走 applyPrestige 乘转生倍率，与装备/成就加成同通道，
         * 保证 powerOf 的战力口径与实际战斗强度不脱节。bonusFor/bonus 本体【不】并入武魂
         * （武魂单列 detail 第 7 行，不混入装备拆分行）。
         */
        fun soulBonus(soul: GameBalance.MartialSoulDef): EquipmentBonus = EquipmentBonus(
            soul.baseAtk.toLong(), soul.baseHp, soul.baseMatk.toLong(),
            soul.pdef.toLong(), soul.mdef.toLong(), soul.critRate.toLong(), soul.critDmg.toLong()
        )

        /** 基础攻击（等级部分），powerOf / detail 同源 */
        fun baseAttack(level: Int): Long =
            GameBalance.PLAYER_ATK_BASE + level * GameBalance.PLAYER_ATK_PER_LEVEL

        /**
         * 转生倍率作用于「装备+成就」加成部分（shared GameEngine.prestigeMultiplier 双口径之属性侧；
         * 基础 atk/maxHp 部分由 GameService 缩放，两处合计等效于总和乘倍率）。
         * 取整沿用既有 .toLong() 截断风格（第十七轮扩展：七字段逐项缩放）；prestigeCount<=0 时
         * 原样返回，保证既有数值零漂移。
         */
        fun applyPrestige(b: EquipmentBonus, prestigeCount: Int): EquipmentBonus {
            if (prestigeCount <= 0) return b
            val mult = GameBalance.prestigeMultiplier(prestigeCount)
            return EquipmentBonus(
                (b.atkBonus * mult).toLong(), (b.hpBonus * mult).toLong(),
                (b.matkBonus * mult).toLong(), (b.pdefBonus * mult).toLong(), (b.mdefBonus * mult).toLong(),
                (b.critRateBonus * mult).toLong(), (b.critDmgBonus * mult).toLong()
            )
        }

        /**
         * 流派系数 → 战力口径（第十九轮流派）：把流派乘区折进加成包（powerOf 签名不变）。
         * 战斗口径中流派乘「基础(已乘转生)+加成(已乘转生)」之和（GameService.playerCombatStats /
         * schoolScaledMaxHp）；战力口径沿用 prestige 行先例——系数只作用于加成包（powerOf 的基础项
         * 是固定等级函数，基础侧增量折入第 8 行 school 差值行），五属性逐项乘系数、暴击走加数。
         * school=null 原样返回（未选流派零漂移）；getGameState 的 power 与 detail 第 8 行
         * 必须用同一本函数口径，八行求和恒等才成立。
         */
        fun applySchool(b: EquipmentBonus, school: GameBalance.SchoolMods?): EquipmentBonus {
            if (school == null) return b
            return EquipmentBonus(
                (b.atkBonus * school.atk).toLong(), (b.hpBonus * school.hp).toLong(),
                (b.matkBonus * school.matk).toLong(),
                (b.pdefBonus * school.pdef).toLong(), (b.mdefBonus * school.mdef).toLong(),
                b.critRateBonus + school.critRateBonus, b.critDmgBonus + school.critDmgBonus
            )
        }

        /**
         * 纯函数：战力明细拆分（任务#23；成就系统集成后五行不变量；转生集成后六行不变量；
         * 第十八轮武魂觉醒后七行不变量；第十九轮流派后八行不变量）。复用与 bonus() 完全相同的
         * 单件公式，取整用最大余数法（largest remainder）保证拆分求和与 EquipmentBonus / powerOf
         * 严格相等：
         *  - ringAtk + boneAtk + coreAtk == 装备部分的 atkBonus（不含成就加成）
         *  - ringHp + boneHp == 装备部分的 hpBonus（魂核只加攻击、玩家无基础生命 → coreHp/baseHp 恒 0）
         *  - basePower + ringPower + bonePower + corePower + achievement ==
         *    powerOf(level, bonus(level, rings, bones, cores, achievementBonus))（1.0 倍口径）
         *  - 上式五行 + prestige == powerOf(level, applyPrestige(装备+成就合计, prestigeCount))
         *    （转生倍率产生的全部增量单列第 6 行；prestigeCount=0 时该行恒 0，退化为原五行拆分）
         *  - 上式六行 + soul == powerOf(level, plus(applyPrestige(装备+成就合计, p), applyPrestige(soulBonus, p)))
         *    （武魂贡献单列第 7 行 = 含武魂战力 − 不含武魂六行之和，最大余数法口径延续——武魂不经
         *    bonus() 单件拆分，无自然来源行，故用差值法与第 6 行同款手法；soul=null 时该行恒 0，
         *    退化为原六行拆分。倍率取整式与 GameService 战斗组装（applyPrestige(soulBonus, p) 后
         *    并入）逐位一致，防止 power 展示与战斗强度脱节）
         *  - 上式七行 + school == powerOf(level, applySchool(含武魂合计, school))
         *    （流派贡献单列第 8 行 = 含流派战力 − 七行之和，差值法延续；流派不经 bonus() 单件拆分、
         *    又乘在「基础+加成」加总后（基础侧 powerOf 无法直接表达），全部增量归此行；school=null
         *    时该行恒 0，退化为原七行拆分。applySchool 取整式与 getGameState 的 power 组装逐位一致）
         *  achievementBonus / prestigeCount / soul / school 均为默认零/空时与既有调用行为完全一致。
         */
        fun detail(
            level: Int,
            rings: List<EquippedRing>,
            bones: List<EquippedBone>,
            cores: List<EquippedCore>,
            achievementBonus: EquipmentBonus = EquipmentBonus(0, 0),
            prestigeCount: Int = 0,
            soul: GameBalance.MartialSoulDef? = null,
            school: GameBalance.SchoolMods? = null
        ): PowerDetailDto {
            val b = bonus(level, rings, bones, cores)
            val baseAtk = baseAttack(level)

            // 攻击拆分：与 bonus() 相同的累加顺序（环→骨→核），逐件最大余数法分配后按来源归并
            val atkParts = rings.map { ringAtkOf(it) } + bones.map { boneAtkOf(it) } + cores.map { coreAtkOf(it, baseAtk) }
            val atkAlloc = allocate(atkParts, b.atkBonus)
            val ringAtk = atkAlloc.take(rings.size).sum()
            val boneAtk = atkAlloc.slice(rings.size until rings.size + bones.size).sum()
            val coreAtk = atkAlloc.takeLast(cores.size).sum()

            // 生命拆分：环+骨
            val hpParts = rings.map { ringHpOf(it) } + bones.map { boneHpOf(it) }
            val hpAlloc = allocate(hpParts, b.hpBonus)
            val ringHp = hpAlloc.take(rings.size).sum()
            val boneHp = hpAlloc.takeLast(bones.size).sum()

            // 战力拆分：生命折算（/POWER_HP_DIVISOR）的取整余数按「环/骨/成就生命总量」最大余数法分配，
            // 保证五行战力求和 == powerOf（powerOf = 常数 + 等级 + 基础攻击 + atkBonus(装备+成就) +
            // hpBonus(装备+成就)/D + newAttrPower(五属性，成就行承载)）
            val basePower = GameBalance.POWER_BASE + GameBalance.POWER_LEVEL_WEIGHT * level + baseAtk
            val hpPowerTotal = ((b.hpBonus + achievementBonus.hpBonus) / GameBalance.POWER_HP_DIVISOR).toLong()
            val hpPowerAlloc = allocate(
                listOf(hpParts.take(rings.size).sum() / GameBalance.POWER_HP_DIVISOR,
                        hpParts.takeLast(bones.size).sum() / GameBalance.POWER_HP_DIVISOR,
                        achievementBonus.hpBonus / GameBalance.POWER_HP_DIVISOR),
                hpPowerTotal
            )
            val achievementRow = achievementBonus.atkBonus + hpPowerAlloc[2] +
                    // 第十七轮扩展：五属性折算并入成就行（装备侧五属性恒 0 → combined 的五属性 == 成就的，
                    // newAttrPower(ach) == newAttrPower(combined)，恒等严格保持；装备侧接入（转生武魂）
                    // 后需同步把该折算拆分到对应装备来源行）
                    newAttrPower(achievementBonus)
            // 第 6 行 prestige = 含倍率战力 − 五行（1.0 倍口径）战力：倍率产生的增量全部归此行，
            // 六行求和与 getGameState 展示的 power（powerOf(applyPrestige(合计加成))）严格一致
            val fiveRowSum = basePower + ringAtk + hpPowerAlloc[0] + boneAtk + hpPowerAlloc[1] + coreAtk + achievementRow
            val combined = plus(b, achievementBonus)
            val prestigeRow = powerOf(level, applyPrestige(combined, prestigeCount)) - fiveRowSum
            // 第 7 行 soul = 含武魂战力 − 六行之和（差值法，与第 6 行同款最大余数口径延续）；
            // 倍率取整式 applyPrestige(soulBonus, p) 与 GameService 战斗组装逐位一致（soulBonus 注释）
            val sixRowSum = fiveRowSum + prestigeRow
            // 含武魂合计（soul=null 时即不含武魂口径，与 GameService getGameState 的 combatBonus 同构）
            val withSoul = if (soul == null) applyPrestige(combined, prestigeCount)
            else plus(applyPrestige(combined, prestigeCount), applyPrestige(soulBonus(soul), prestigeCount))
            val soulRow = if (soul == null) 0L else powerOf(level, withSoul) - sixRowSum
            // 第 8 行 school = 含流派战力 − 七行之和（差值法延续）；applySchool 取整式与 getGameState
            // 的 power 组装逐位一致（applySchool 注释）——未选流派该行恒 0，退化为原七行恒等
            val sevenRowSum = sixRowSum + soulRow
            val schoolRow = if (school == null) 0L else
                powerOf(level, applySchool(withSoul, school)) - sevenRowSum
            return PowerDetailDto(
                baseAtk = baseAtk,
                baseHp = 0,
                basePower = basePower,
                ringAtk = ringAtk,
                ringHp = ringHp,
                ringPower = ringAtk + hpPowerAlloc[0],
                boneAtk = boneAtk,
                boneHp = boneHp,
                bonePower = boneAtk + hpPowerAlloc[1],
                coreAtk = coreAtk,
                coreHp = 0,
                corePower = coreAtk,
                achievement = achievementRow,
                prestige = prestigeRow,
                soul = soulRow,
                school = schoolRow
            )
        }

        /**
         * 最大余数法：把非负 Double 列表取整分配成总和恰为 total 的 Long 列表
         * （total 必须是 parts 之和的下取整，调用方均满足；余数按小数部分从大到小补 1）。
         */
        private fun allocate(parts: List<Double>, total: Long): List<Long> {
            if (parts.isEmpty()) return emptyList()
            val floors = parts.map { it.toLong() }
            val result = floors.toMutableList()
            var remainder = (total - floors.sum()).coerceAtLeast(0L).toInt()
            val byFractionDesc = parts.indices.sortedByDescending { parts[it] - floors[it] }
            var i = 0
            while (remainder > 0) {
                val idx = byFractionDesc[i % byFractionDesc.size]
                result[idx] = result[idx] + 1L
                remainder--
                i++
            }
            return result
        }

        /**
         * 五属性战力折算（第十七轮扩展）：matk×0.5 + pdef×0.2 + mdef×0.2 + critRate×5 + critDmg×0.2，
         * 逐项截断取整后求和（权重依据见 GameBalance POWER_*_WEIGHT 注释）。
         * 独立成纯函数：powerOf 与 detail 的成就行共用，保证六行求和恒等不漂移。
         */
        fun newAttrPower(b: EquipmentBonus): Long =
            (b.matkBonus * GameBalance.POWER_MATK_WEIGHT).toLong() +
                    (b.pdefBonus * GameBalance.POWER_DEF_WEIGHT).toLong() +
                    (b.mdefBonus * GameBalance.POWER_DEF_WEIGHT).toLong() +
                    (b.critRateBonus * GameBalance.POWER_CRIT_RATE_WEIGHT).toLong() +
                    (b.critDmgBonus * GameBalance.POWER_CRIT_DMG_WEIGHT).toLong()

        /**
         * 战斗力 = 常数项 + 等级贡献 + 基础攻击 + 装备攻击加成 + 装备生命折算（POWER_HP_DIVISOR:1）
         * + 五属性折算（newAttrPower；装备侧五属性恒 0，当前只来自成就加成）。
         */
        fun powerOf(level: Int, b: EquipmentBonus): Long =
            GameBalance.POWER_BASE + GameBalance.POWER_LEVEL_WEIGHT * level +
                    GameBalance.PLAYER_ATK_BASE + GameBalance.PLAYER_ATK_PER_LEVEL * level +
                    b.atkBonus + (b.hpBonus / GameBalance.POWER_HP_DIVISOR).toLong() +
                    newAttrPower(b)

        /**
         * 魂塔胜率（P2 修复后公式）：基础胜率 1 - (0.25 + floor×0.005)，floor=99 时仍有 0.255；
         * 战力达到该层推荐值（floor×TOWER_POWER_PER_FLOOR）即 +7.5%，按 power/推荐值 比例线性提升，
         * 封顶 TOWER_POWER_WIN_BONUS_CAP（推荐值 2 倍时触顶）。胜率随战力严格单调不减。
         */
        fun towerWinChance(floor: Int, power: Long): Double {
            val base = 1.0 - GameBalance.TOWER_BASE_LOSE_CHANCE - floor * GameBalance.TOWER_FLOOR_DIFFICULTY
            val required = floor * GameBalance.TOWER_POWER_PER_FLOOR
            val ratio = if (required <= 0L) {
                if (power > 0L) 1.0 else 0.0
            } else {
                power.toDouble() / required
            }
            val bonus = (ratio * GameBalance.TOWER_POWER_WIN_FACTOR)
                .coerceAtMost(GameBalance.TOWER_POWER_WIN_BONUS_CAP)
            return (base + bonus).coerceIn(GameBalance.TOWER_WIN_CHANCE_MIN, GameBalance.TOWER_WIN_CHANCE_MAX)
        }
    }
}
