package com.douluodalu.game.service

import com.douluodalu.game.dto.*
import com.douluodalu.game.entity.BackpackItemEntity
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.entity.EquippedBone
import com.douluodalu.game.entity.EquippedCore
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.BackpackItemRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import com.douluodalu.game.repository.TalentRepository
import com.douluodalu.game.repository.EquippedRingRepository
import com.douluodalu.game.repository.EquippedBoneRepository
import com.douluodalu.game.repository.EquippedCoreRepository
import com.douluodalu.game.repository.UserRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@Service
class GameService(
    private val profileRepo: PlayerProfileRepository,
    private val backpackRepo: BackpackItemRepository,
    private val talentRepo: TalentRepository,
    private val equippedRingRepo: EquippedRingRepository,
    private val equippedBoneRepo: EquippedBoneRepository,
    private val equippedCoreRepo: EquippedCoreRepository,
    private val userRepository: UserRepository,
    private val webSocketService: WebSocketService,
    private val checkInService: CheckInService,
    private val dailyQuestService: DailyQuestService,
    private val equipmentPowerService: EquipmentPowerService,
    private val achievementService: AchievementService,
    /** 业务计数器（Micrometer，Spring Boot 自动配置 bean）；测试注入 SimpleMeterRegistry */
    private val meterRegistry: MeterRegistry
) {
    /**
     * 玩家有效战斗属性包（第十七轮战斗模型扩展）。resolveBattle 新签名入参：
     * 参数对象而非 7 个散参——battle/塔日志/getGameState/仿真四处同源组装（playerCombatStats），
     * 后续加属性只扩对象不改签名。hp 不入包：传参的是运行时 currentHp（可低于上限），非静态属性。
     * （挂在 GameService 而非 companion：companion 内嵌类无法以 GameService.X 短名从测试解析）
     */
    data class CombatStats(
        val atk: Long,
        val matk: Long = 0,
        val pdef: Long = 0,
        val mdef: Long = 0,
        /** 暴击率（百分点：1 = 1%）与暴击伤害（百分点：150 = 1.5 倍） */
        val critRate: Int = 0,
        val critDmg: Int = 0,
        /**
         * 武魂技能（第二十轮，尾部默认 null 零漂移）：null = 无技能路径，与旧数学逐位一致。
         * battle/towerBattle/getGameState/仿真四处经 soulSkillOf 同源反查传入；塔日志复用
         * CombatStats 携带（buildTowerBattleLog 直接消费 player.skill，无需独立反查）。
         */
        val skill: GameBalance.SkillDef? = null
    )

    /**
     * 怪物战斗属性（monsterStats 产出，由 Pair 升级为数据类；component1..component5 顺序
     * hp/atk/matk/pdef/mdef，旧 `val (hp, atk) = monsterStats(...)` 解构仍编译通过）。
     * 怪 matk = atk（魔法普攻同量级，只影响吃玩家哪项防御）；怪不暴击（v1）。
     */
    data class MonsterStats(
        val hp: Long,
        val atk: Int,
        val matk: Int,
        val pdef: Int,
        val mdef: Int
    )

    companion object {
        // ===== 业务计数器名（ops Grafana 面板按名建面板，逐字契约，勿改） =====
        const val METRIC_BATTLE_TOTAL = "douluo.battle.total"
        const val METRIC_AWAKEN_TOTAL = "douluo.awaken.total"
        const val METRIC_PRESTIGE_TOTAL = "douluo.prestige.total"
        const val METRIC_SCHOOL_CHOOSE_TOTAL = "douluo.school.choose.total"

        val REALM_NAMES = listOf(
            "魂士", "魂师", "大魂师", "魂尊", "魂宗",
            "魂王", "魂帝", "魂圣", "魂斗罗", "封号斗罗",
            "极限斗罗", "半神", "神祇", "神王", "至高神王", "创世神"
        )
        val MAP_NAMES = listOf(
            "圣魂村", "诺丁城外", "星斗外围", "落日森林",
            "极北之地", "海神岛", "杀戮之都外域", "神界废墟",
            // 第二十二轮地图扩展（第十九轮地图提案续神界线，索引 8~10）
            "神王殿", "至高神庭", "创世之巅"
        )

        /** 回合制战斗结算结果（纯函数产出，LongRunSimulationTest 直接复用保证镜像不漂移） */
        data class BattleOutcome(
            val won: Boolean,
            val rounds: Int,
            val playerHpLeft: Long,
            val log: List<BattleRoundLog>
        )

        /** 按地图/关卡生成怪物 (hp/atk/matk/pdef/mdef)。镜像约定：仿真端调用同一函数。 */
        fun monsterStats(mapId: Int, stage: Int): MonsterStats {
            val monsterHp = ((GameBalance.MONSTER_HP_BASE + mapId * GameBalance.MONSTER_HP_PER_MAP) *
                    (1.0 + stage * GameBalance.MONSTER_STAGE_GROWTH)).toLong()
            val monsterAtk = ((GameBalance.MONSTER_ATK_BASE + mapId * GameBalance.MONSTER_ATK_PER_MAP) *
                    (1.0 + stage * GameBalance.MONSTER_STAGE_GROWTH)).toInt()
            // 第十七轮扩展：怪 matk = atk；怪双防 = atk × MONSTER_DEF_FACTOR（校准杠杆，随图/关单调）
            val def = (monsterAtk * GameBalance.MONSTER_DEF_FACTOR).toInt()
            return MonsterStats(monsterHp, monsterAtk, monsterAtk, def, def)
        }

        /**
         * 玩家有效战斗属性组装（battle/塔日志/getGameState 与 LongRunSimulationTest 镜像共用纯函数）：
         *  - atk = 基础(×转生倍率) + 加成；matk 镜像物攻基础 ×PLAYER_MATK_BASE_FACTOR（加成另计）；
         *  - pdef/mdef 基础 = level × PLAYER_DEF_PER_LEVEL（基础部分同样乘转生倍率——「全属性+10%/转」
         *    双口径，与 scaledBaseAtk/scaledBaseMaxHp 一致）；加成部分已由 bonusFor/applyPrestige 乘过倍率；
         *  - critRate/critDmg = 常数基础 + 加成（基础不乘转生倍率：非常量成长项，无等级维度）。
         *  签名不变（第十八轮武魂集成走加成通道）：调用方把武魂加成经 soulBonusOf（已乘转生倍率）
         *  并入 equip 后传入——武魂 hp/atk 由此进 maxHp/atk 基础战斗口径（hp 不入 CombatStats 包，
         *  由 maxHp 侧消费），五属性随包进结算；未觉醒并入零加成，数值零漂移。
         *  第十九轮流派集成：school 尾参（默认 null 恒等）——atk/matk/pdef/mdef 在「基础(已乘转生)
         *  + 加成包(已含转生倍率)」相加【之后】逐属性乘流派系数（GameBalance 流派区块注释写死的
         *  系数口径，与 shared「基础+武魂之后乘流派」对齐——我们的武魂已折进加成包）；critRate/
         *  critDmg 走加数（百分点直加）。school=null → 乘 1.0/加 0 逐位恒等（零漂移）。
         *  第二十轮武魂技能：skill 尾参（默认 null 零漂移）随包进 CombatStats——调用方以
         *  soulSkillOf(martialSoulName) 同源反查传入（battle/towerBattle/getGameState/仿真四处）。
         */
        fun playerCombatStats(
            level: Int,
            prestigeCount: Int,
            equip: EquipmentBonus,
            school: GameBalance.SchoolMods? = null,
            skill: GameBalance.SkillDef? = null
        ): CombatStats {
            fun scale(base: Long): Long =
                if (prestigeCount <= 0) base
                else (base * GameBalance.prestigeMultiplier(prestigeCount)).toLong()
            // 流派乘区：只在「基础+加成」相加后整体乘（不在基础/加成两侧分别乘）；school=null 跳过乘法
            fun schoolMul(v: Long, mod: Double): Long = if (school == null) v else (v * mod).toLong()
            val baseMatk = (GameBalance.PLAYER_ATK_BASE * GameBalance.PLAYER_MATK_BASE_FACTOR).toLong() +
                    (level * GameBalance.PLAYER_ATK_PER_LEVEL * GameBalance.PLAYER_MATK_BASE_FACTOR).toLong()
            val baseDef = level.toLong() * GameBalance.PLAYER_DEF_PER_LEVEL
            return CombatStats(
                atk = schoolMul(scale(GameBalance.PLAYER_ATK_BASE + level * GameBalance.PLAYER_ATK_PER_LEVEL) + equip.atkBonus,
                    school?.atk ?: 1.0),
                matk = schoolMul(scale(baseMatk) + equip.matkBonus, school?.matk ?: 1.0),
                pdef = schoolMul(scale(baseDef) + equip.pdefBonus, school?.pdef ?: 1.0),
                mdef = schoolMul(scale(baseDef) + equip.mdefBonus, school?.mdef ?: 1.0),
                critRate = GameBalance.PLAYER_CRIT_RATE_BASE + equip.critRateBonus.toInt() + (school?.critRateBonus ?: 0),
                critDmg = GameBalance.PLAYER_CRIT_DMG_BASE + equip.critDmgBonus.toInt() + (school?.critDmgBonus ?: 0),
                skill = skill
            )
        }

        /**
         * 武魂加成（第十八轮武魂觉醒，纯函数四处同源：battle / towerBattle / getGameState / 仿真镜像）：
         *  由 martialSoulName 反查武魂池折成七字段 EquipmentBonus，并乘转生倍率（与装备/成就加成
         *  同通道——基础与加成两侧都吃「+10%/转」，powerOf 的 atk 1:1、hp/10 权重因此与战斗口径一致）。
         *  未觉醒/名字无法反查（历史脏数据）返回零加成。hp/atk 虽「进基础」字段（maxHp/atk 的基础
         *  战斗口径），但倍率取整统一走 applyPrestige 加成侧，避免 playerCombatStats 出现两套倍率路径。
         */
        fun soulBonusOf(martialSoulName: String?, prestigeCount: Int): EquipmentBonus {
            val soul = martialSoulName?.let { GameBalance.soulByName(it) } ?: return EquipmentBonus(0, 0)
            return EquipmentPowerService.applyPrestige(EquipmentPowerService.soulBonus(soul), prestigeCount)
        }

        /**
         * 流派系数查表（第十九轮，纯函数四处同源：battle / towerBattle / getGameState / 仿真镜像）：
         * 由 profile.chosenSchool（枚举名落库）反查流派 mods；未选/历史脏数据返回 null（恒等口径，
         * 与 soulBonusOf 的反查失败兜底同款）。名字反查失败不抛异常——战斗组装对脏数据宽容。
         */
        fun schoolModsOf(chosenSchool: String?): GameBalance.SchoolMods? =
            chosenSchool?.let { GameBalance.schoolByName(it)?.mods }

        /**
         * 武魂技能反查（第二十轮，纯函数四处同源：battle / towerBattle / getGameState / 仿真镜像）：
         * 由 martialSoulName 反查武魂池的技能定义（soulBonusOf 同款反查兜底——未觉醒/历史脏数据
         * 返回 null，战斗组装对脏数据宽容）。塔日志经 CombatStats.skill 携带（buildTowerBattleLog
         * 消费 player.skill），与主路径天然同口径。
         */
        fun soulSkillOf(martialSoulName: String?): GameBalance.SkillDef? =
            martialSoulName?.let { GameBalance.soulByName(it)?.skill }

        /**
         * 流派 hp 乘区（maxHp 组装专用，battle/towerBattle/仿真镜像同源）：
         * maxHp = (scaledBaseMaxHp + equip.hpBonus) × school.hp——乘在【加总后】而非只乘基础侧，
         * 加成包里的装备/成就/武魂 hp 全部吃到系数（GameBalance 流派区块注释写死的口径，
         * 与 shared「基础+武魂之后乘流派」对齐）；school=null 恒等返回（零漂移）。
         */
        fun schoolScaledMaxHp(basePlusBonus: Long, school: GameBalance.SchoolMods?): Long =
            if (school == null) basePlusBonus else (basePlusBonus * school.hp).toLong()

        /**
         * shared 引擎同源减伤因子（GameEngine.kt defFactor）：def=0 → 1.0（无减免），
         * def→∞ → 0.1 下限夹取。防御的边际收益递减（DEF_K=200 时每 +200 防翻倍难度）。
         */
        private fun defFactor(def: Long): Double =
            (1.0 - def.toDouble() / (def + GameBalance.DEF_K)).coerceIn(0.1, 1.0)

        /**
         * 纯函数回合结算（第十七轮战斗模型扩展：防御 defFactor / 暴击 / 魔物理混合接入）：
         *  - 玩家先手，30 回合（maxRounds）内击杀即胜；rng 显式注入便于固定种子断言；
         *  - 每回合掷点次序【写死，勿动】——玩家三掷：①攻击类型（nextInt(100) < ATTACK_MAGIC_SHARE
         *    → 魔法，用 matk vs 怪 mdef；否则物理，用 atk vs 怪 pdef）→ ②暴击（nextInt(100) <
         *    critRate → 伤害 ×critDmg/100；怪物 v1 不暴击、不掷）→ ③浮动（nextLong(攻击力/5)，
         *    保留既有 +0~20%）；
         *  - 怪物回合两掷：①攻击类型（85/15 同构，怪 matk = atk → 玩家 pdef/mdef 都有消费）
         *    → ②浮动（对怪 atk 取浮动，与旧版逐位同源）；
         *  - 伤害算式：dmg = (攻击力 + 浮动) × defFactor(对方对应防御)，暴击再乘 critDmg/100，
         *    最后截断取整、下限 1（defFactor 命中 0.1 下限的极端防御也保底 1 点伤害）；
         *  - 内部掷点自洽契约：塔日志回放用独立种子重模拟，只要求本函数内部次序一致。
         *
         * 第二十轮武魂技能（触发模型 = 冷却制，零新增掷点；定义见 GameBalance 技能区块）：
         *  - 触发判定：player.skill != null 且第 r 回合（1-based）满足 (r-1) % skill.cooldown == 0
         *    （cooldown 3~5 → 首回合即放，之后每 N 回合一次）；技能回合的玩家三掷【照常发生】，
         *    技能只改写该回合玩家攻击的伤害结算或改为治疗——RNG 流与无技能路径逐位一致；
         *  - SINGLE_DAMAGE：dmg = (typeAtk+浮动) × power/100 × defFactor(def) × 暴击倍率
         *    （typeAtk/def 按本回合①掷的物/魔分流，3 掷结果全部复用）；
         *  - MULTI_HIT：hitCount = (power/50).coerceIn(2,5)，总倍率 = power×hitCount/100
         *    （v1 单发等价结算，不做逐段动画），其余同 SINGLE；
         *  - IGNORE_DEFENSE：defFactor 用 def×SKILL_DEF_IGNORE_FACTOR 计算（软化标定依据见
         *    GameBalance 注释），其余同 SINGLE；
         *  - HEAL：该回合玩家不攻击（playerDamage=0），改为回血 min(maxHp, hp + maxHp×power/100)
         *    （playerMaxHp 与调用方 maxHp 完全同口径传入），怪物照常攻击；
         *  - BERSERK：本轮不实现结算，回落普通攻击（GameBalance 注释留档）；
         *  - 技能回合的 log 行 skillName = 技能名，普通回合 null；skill=null 时与旧实现逐位一致。
         */
        fun resolveBattle(
            player: CombatStats,
            playerHp: Long,
            monster: MonsterStats,
            maxRounds: Int,
            rng: Random,
            playerMaxHp: Long = playerHp
        ): BattleOutcome {
            var pHp = playerHp
            var mHp = monster.hp
            var rounds = 0
            val log = mutableListOf<BattleRoundLog>()
            while (rounds < maxRounds && pHp > 0 && mHp > 0) {
                rounds++
                val playerHpBefore = pHp
                val monsterHpBefore = mHp

                // —— 玩家回合（三掷：类型 → 暴击 → 浮动；次序与掷点数【逐位不变】，技能回合照掷）——
                val pMagic = rng.nextInt(100) < GameBalance.ATTACK_MAGIC_SHARE
                val pCrit = rng.nextInt(100) < player.critRate
                val pBase = if (pMagic) player.matk else player.atk
                val pVariance = rng.nextLong((pBase / 5).coerceAtLeast(1))
                // 冷却制触发：r=1 首放，之后每 cooldown 回合一次；BERSERK 未实现结算 → 回落普通攻击
                val activeSkill = player.skill?.takeIf {
                    (rounds - 1) % it.cooldown == 0 && it.type != GameBalance.SkillType.BERSERK
                }
                val pDmg: Long
                if (activeSkill != null && activeSkill.type == GameBalance.SkillType.HEAL) {
                    // HEAL：玩家不攻击，回血 maxHp×power/100（上限封顶；Long 整除口径）
                    pHp = min(playerMaxHp, pHp + playerMaxHp * activeSkill.power / 100)
                    pDmg = 0
                } else if (activeSkill != null) {
                    // 伤害型技能：typeAtk/def 按本回合①掷分流，倍率按技能类型折算（见方法注释）
                    val hits = if (activeSkill.type == GameBalance.SkillType.MULTI_HIT) {
                        (activeSkill.power / 50).coerceIn(2, 5)
                    } else 1
                    val powerMult = activeSkill.power * hits / 100.0
                    val rawDef = (if (pMagic) monster.mdef else monster.pdef).toLong()
                    val effDef = if (activeSkill.type == GameBalance.SkillType.IGNORE_DEFENSE) {
                        (rawDef * GameBalance.SKILL_DEF_IGNORE_FACTOR).toLong()
                    } else rawDef
                    var skillDmg = (pBase + pVariance) * powerMult * defFactor(effDef)
                    if (pCrit) skillDmg *= player.critDmg / 100.0
                    pDmg = skillDmg.toLong().coerceAtLeast(1)
                } else {
                    var pDmgD = (pBase + pVariance) * defFactor((if (pMagic) monster.mdef else monster.pdef).toLong())
                    if (pCrit) pDmgD *= player.critDmg / 100.0
                    pDmg = pDmgD.toLong().coerceAtLeast(1)
                }
                mHp -= pDmg
                if (mHp <= 0) {
                    log.add(BattleRoundLog(rounds, playerHpBefore, monsterHpBefore, pDmg, 0, pHp, 0,
                        skillName = activeSkill?.name))
                    break
                }

                // —— 怪物回合（两掷：类型 → 浮动；v1 怪物不暴击，技能不影响怪物行动）——
                val mMagic = rng.nextInt(100) < GameBalance.ATTACK_MAGIC_SHARE
                val mBase = if (mMagic) monster.matk.toLong() else monster.atk.toLong()
                val mVariance = rng.nextLong((monster.atk / 5).coerceAtLeast(1).toLong())
                val mDmg = ((mBase + mVariance) * defFactor(if (mMagic) player.mdef else player.pdef))
                    .toLong().coerceAtLeast(1)
                pHp -= mDmg
                log.add(BattleRoundLog(rounds, playerHpBefore, monsterHpBefore, pDmg, mDmg, pHp, mHp,
                    skillName = activeSkill?.name))
            }
            return BattleOutcome(won = mHp <= 0, rounds = rounds, playerHpLeft = pHp, log = log)
        }

        /** 塔战日志的独立模拟种子：只由 (userId, 挑战时楼层) 决定，同楼层回放天然可复现 */
        fun towerLogSeed(userId: Long, floor: Int): Long = userId * 1_000_003L + floor

        /**
         * 塔战逐回合日志（呈现层）：胜负已由概率一锤定音（won 入参），本函数只负责把结果
         * 渲染成与普通战斗同构的回合日志（前端塔页复用 BattleReplay）。
         *  - 独立种子 Random(towerLogSeed(userId, floor))：绝不复用主 Random、绝不增删既有掷点，
         *    LongRunSimulationTest 的 towerWinChance/resolveBattle 镜像契约不受影响；
         *  - 胜负服从入参 won（概率语义与呈现解耦）：模拟结果不一致时按 ×1.5 梯度调整
         *    塔怪 HP/ATK 重模拟（最多 8 次），仍不一致按最后一次输出（呈现层可接受）；
         *  - 塔怪属性从楼层推导（推导依据见 GameBalance.TOWER_LOG_* 注释；双防随 atk 同步
         *    ×MONSTER_DEF_FACTOR，与 monsterStats 同构）；
         *  - 玩家口径与 battle() 调 resolveBattle 时一致：完整 CombatStats（第十七轮扩展，
         *    含五属性——塔日志重模拟必须与主路径入参同口径）；生命用满血（塔挑战以满血状态
         *    进行，与战败后回满的既有语义一致）。
         *  - 第二十轮武魂技能：技能随 CombatStats.skill 携带（调用方经 soulSkillOf 同源反查，
         *    与主路径天然同口径）；playerMaxHp 透传 resolveBattle 供 HEAL 回血（与满血口径一致）。
         */
        fun buildTowerBattleLog(
            userId: Long,
            floor: Int,
            won: Boolean,
            player: CombatStats,
            playerMaxHp: Long
        ): List<BattleRoundLog> {
            val baseHp = GameBalance.MONSTER_HP_BASE + floor * GameBalance.TOWER_LOG_MONSTER_HP_PER_FLOOR
            val baseAtk = GameBalance.MONSTER_ATK_BASE + floor * GameBalance.TOWER_LOG_MONSTER_ATK_PER_FLOOR
            val baseDef = (baseAtk * GameBalance.MONSTER_DEF_FACTOR).toInt()
            fun towerMonster(factor: Double) = MonsterStats(
                hp = (baseHp * factor).toLong().coerceAtLeast(1),
                atk = (baseAtk * factor).toInt().coerceAtLeast(1),
                matk = (baseAtk * factor).toInt().coerceAtLeast(1),
                pdef = (baseDef * factor).toInt(),
                mdef = (baseDef * factor).toInt()
            )
            val rng = Random(towerLogSeed(userId, floor))
            var factor = 1.0
            var outcome = resolveBattle(
                player = player,
                playerHp = playerMaxHp,
                monster = towerMonster(factor),
                maxRounds = GameBalance.MAX_BATTLE_ROUNDS,
                rng = rng,
                playerMaxHp = playerMaxHp
            )
            var attempts = 0
            while (outcome.won != won && attempts < 8) {
                attempts++
                // 期望胜 → 削怪（/1.5 递进）；期望败 → 强怪（×1.5 递进）；每轮换一批新掷点（同一种子流）
                factor = if (won) factor / 1.5 else factor * 1.5
                outcome = resolveBattle(
                    player = player,
                    playerHp = playerMaxHp,
                    monster = towerMonster(factor),
                    maxRounds = GameBalance.MAX_BATTLE_ROUNDS,
                    rng = rng,
                    playerMaxHp = playerMaxHp
                )
            }
            return outcome.log
        }
    }

    @Transactional(readOnly = true)
    fun getGameState(userId: Long): GameStateResponse {
        val profile = profileRepo.findByUserId(userId)
            ?: throw IllegalStateException("玩家存档不存在")
        val talents = talentRepo.findByUserId(userId).associate { it.branch to it.level }
        val rings = equippedRingRepo.findByUserId(userId)
        val bones = equippedBoneRepo.findByUserId(userId)
        val cores = equippedCoreRepo.findByUserId(userId)
        val equippedRings = rings.map {
            EquippedRingDto(it.slotIndex, it.yearOrdinal, it.qualityOrdinal, it.percentage, null, null,
                RingLoadCalculator.ringLoad(it))
        }
        val equippedBones = bones.map { EquippedBoneDto(it.slotIndex, it.yearOrdinal, it.qualityOrdinal, it.enhanceLevel, null, null) }
        val equippedCores = cores.map { EquippedCoreDto(it.slotType, it.coreName, it.rarityOrdinal, null, it.coreValue, it.coreLevel) }
        // 任务#21：战力 + 魂环负荷/容量（公式同源：EquipmentPowerService / RingLoadCalculator）
        // 成就系统集成：已解锁成就的 hp/atk 加成并入 bonus（power/容量/明细同口径即时生效）
        // 转生集成：power/明细吃「装备+成就加成 ×转生倍率」；容量口径维持不随倍率放大——
        // 与 equipRing 校验同源（校验本就按不含成就加成的装备口径，保守方向），契约范围仅
        // 收入/战斗属性/bonusFor，负荷体系不动
        val achBonus = achievementService.unlockedBonus(userId)
        val rawBonus = EquipmentPowerService.bonus(profile.level, rings, bones, cores, achBonus)
        // 第十八轮武魂集成：武魂加成（已乘转生倍率）并入战斗/战力口径；容量口径【有意】不并入
        // （负荷体系按装备校准，武魂非装备、不入根骨，与五属性不入容量的既有决定一致）
        val soulBonus = soulBonusOf(profile.martialSoulName, profile.prestigeCount)
        val bonus = EquipmentPowerService.applyPrestige(rawBonus, profile.prestigeCount)
        val combatBonus = EquipmentPowerService.plus(bonus, soulBonus)
        // 第十九轮流派集成：chosenSchool 反查流派系数（未选 → null 恒等，零漂移）；power/powerDetail
        // 与战斗属性同一含流派口径（detail 第 8 行差值法依赖此一致性，否则恒等破）
        val school = schoolModsOf(profile.chosenSchool)
        // 第十七轮战斗模型扩展：玩家有效战斗属性（与 battle() 结算入参同源，含转生倍率与流派系数）
        // 第二十轮武魂技能：soulSkillOf 同源反查随包（四处同源之三；skill 不进 CombatStatsDto，仅口径统一）
        val combat = playerCombatStats(profile.level, profile.prestigeCount, combatBonus, school,
            soulSkillOf(profile.martialSoulName))
        return GameStateResponse(
            profile = toProfileDto(profile),
            equippedRings = equippedRings,
            equippedBones = equippedBones,
            equippedCores = equippedCores,
            backpackItems = backpackRepo.findByUserIdOrderByCreatedAtAsc(userId).map { toBackpackItemDto(it) },
            talents = talents,
            achievements = achievementService.getStatus(userId),
            // 第十九轮流派：power 用同一含流派口径（applySchool 把流派乘区折进加成包，与 detail
            // 第 8 行的 powerOf(applySchool(...)) 逐位同式）；未选流派 applySchool 恒等返回
            power = EquipmentPowerService.powerOf(
                profile.level,
                EquipmentPowerService.applySchool(combatBonus, school)
            ),
            ringLoad = RingLoadCalculator.totalRingLoad(rings),
            capacity = absorptionCapacityFor(profile, rawBonus),
            // 任务#23：战力明细（复用同一 rings/bones/cores 列表与公式，纯内存拆分，不再查库；
            // 八行含成就行、转生倍率增量行、武魂行与流派行，八行求和 == power）
            powerDetail = EquipmentPowerService.detail(
                profile.level, rings, bones, cores, achBonus, profile.prestigeCount,
                profile.martialSoulName?.let { GameBalance.soulByName(it) },
                school
            ),
            // 第十七轮战斗模型扩展：玩家有效战斗属性（含成就/装备五属性加成与转生倍率，与 battle
            // 结算入参同源 playerCombatStats）；尾部新增带默认值，向后兼容
            combatStats = CombatStatsDto(
                matk = combat.matk, pdef = combat.pdef, mdef = combat.mdef,
                critRate = combat.critRate, critDmg = combat.critDmg
            ),
            // 每日签到状态（CheckInService 只读查询，无循环依赖：CheckInService 不反向依赖本类）
            checkIn = checkInService.getCheckInStatus(userId),
            // 每日任务面板（DailyQuestService 只读查询不建行，无循环依赖：它不反向依赖本类）
            dailyQuests = dailyQuestService.getTodayStatus(userId)
        )
    }

    @Transactional
    fun cultivate(userId: Long): CultivateResponse {
        val profile = getProfile(userId)
        val baseGain = GameBalance.CULTIVATE_BASE_GAIN + profile.level * GameBalance.CULTIVATE_LEVEL_GAIN_FACTOR
        // 转生倍率（收入口径①）：修炼产出 ×(1+转数×0.1)；RNG 掷点次序不变，倍率只在掷点后缩放
        // （prestigeCount=0 时倍率 1.0，取整逐位不变）
        val gain = ((baseGain + Random.nextLong(baseGain / GameBalance.CULTIVATE_RANDOM_DIVISOR)) *
                GameBalance.prestigeMultiplier(profile.prestigeCount)).toLong()
        profile.soulPower += gain
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        // 每日任务挂点（副路径）：修炼成功即计数，失败不击穿主流程（见 DailyQuestService）
        dailyQuestService.recordCultivate(userId)
        // 成就挂点（副路径）：等级达标即自动解锁，失败不击穿主流程（见 AchievementService）
        achievementService.sync(userId)
        return CultivateResponse(gain, profile.soulPower, profile.level)
    }

    // 任务#29：level 是等级榜分数，突破成功后清空榜单缓存（allEntries，键含 limit 无法定点失效）；
    // cache.rank.enabled=false 时注解惰化，直连数据库
    @CacheEvict(cacheNames = ["rank"], allEntries = true)
    @Transactional
    fun breakthrough(userId: Long): BreakthroughResponse {
        val profile = getProfile(userId)
        val cost = getBreakthroughCost(profile.level)
        if (profile.soulPower < cost) {
            return BreakthroughResponse(false, profile.level, "魂力不足，需要${cost}，当前${profile.soulPower}")
        }
        profile.soulPower -= cost
        profile.level += 1
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        // 每日任务挂点（副路径）：仅突破成功出口计数（breakthrough），失败不击穿主流程；
        // 突破消耗魂力故任务奖励返魂力，形成小额回流（见 GameBalance.DAILY_QUESTS 注释）
        dailyQuestService.recordBreakthrough(userId)
        // 成就挂点（副路径）：仅突破成功出口触发（等级达标即自动解锁，失败不击穿主流程）
        achievementService.sync(userId)
        return BreakthroughResponse(true, profile.level, "突破成功！当前境界：${getRealmName(profile.level)} Lv.${profile.level}")
    }

    // ======== 转生（神位传承） ========
    /**
     * 转生：达到 GameBalance.PRESTIGE_MIN_LEVEL（=50）后把 level 重置为 1，
     * 换取永久属性/收入倍率（+10%/转）与 1 天赋点。
     * 与 breakthrough 同款失败语义：门槛不足返回 success=false（HTTP 200），不抛异常。
     */
    // 转生重置 level（等级榜分数）→ 清空榜单缓存（同 breakthrough，任务#29：allEntries，键含 limit 无法定点失效）
    @CacheEvict(cacheNames = ["rank"], allEntries = true)
    @Transactional
    fun prestige(userId: Long): PrestigeResponse {
        val profile = getProfile(userId)
        if (profile.level < GameBalance.PRESTIGE_MIN_LEVEL) {
            return PrestigeResponse(
                false, profile.prestigeCount,
                "等级不足，转生需要 Lv.${GameBalance.PRESTIGE_MIN_LEVEL}（当前 Lv.${profile.level}）"
            )
        }
        // 卸装（数据操作与 unequipRing/unequipBone 完全同源）：equipped 表是独立行——装备时
        // backpack 行被删除、属性拷贝进 equipped 行，卸下 = 按属性拷贝新建 backpack 行 + 删除
        // equipped 行（ringId 引用的原背包行不复活，与现有 unequip 行为一致）。
        // unequip 路径本就无背包容量校验（只有「获得新掉落」路径才校验 hasBackpackSpace），
        // 转生卸回的物件本就来自背包，无需容量兜底（背包可能临时超容，与玩家手动全部卸下一致）。
        // 魂核保留已装备——设计文档重置项只列魂环魂骨。
        for (ring in equippedRingRepo.findByUserId(userId)) {
            backpackRepo.save(
                BackpackItemEntity(
                    userId = userId,
                    itemType = "RING",
                    yearOrdinal = ring.yearOrdinal,
                    qualityOrdinal = ring.qualityOrdinal,
                    percentage = ring.percentage
                )
            )
            equippedRingRepo.delete(ring)
        }
        for (bone in equippedBoneRepo.findByUserId(userId)) {
            backpackRepo.save(
                BackpackItemEntity(
                    userId = userId,
                    itemType = "BONE",
                    yearOrdinal = bone.yearOrdinal,
                    qualityOrdinal = bone.qualityOrdinal,
                    boneTypeOrdinal = bone.boneTypeOrdinal,
                    enhanceLevel = bone.enhanceLevel
                )
            )
            equippedBoneRepo.delete(bone)
        }
        // 重置范围（文档 §15.2 + 防刷修正）：level/gold/soulPower 清零——soulPower 不清零可拿
        // 存量魂力秒升回原等级（零成本刷属性漏洞），shared doPrestige 同款清零；
        // currentStage 回 1（shared「始终重置」字段：保留地图、从该图第 1 关重新推，与战败退回
        // 第 1 关的既有语义自洽）；currentHp 按重置后等级回满。
        // 保留：prestigeCount/talentPoints（+1）、towerFloor、currentMapId（推图进度）、
        // codexKills、bossCoin、成就、天赋等级、背包、totalBattleWins/Losses。
        profile.level = 1
        profile.gold = 0
        profile.soulPower = 0
        profile.currentStage = 1
        profile.currentHp = getMaxHp(1)
        profile.prestigeCount += 1
        profile.talentPoints += 1
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        // 成就挂点（副路径）：prestigeCount 跳变 → prestige_1/prestige_3 解锁
        achievementService.sync(userId)
        // 业务计数器：转生成功（Micrometer 计数是内存操作不抛业务异常，无需 try/catch，不影响主流程）
        counter(METRIC_PRESTIGE_TOTAL)
        return PrestigeResponse(
            true, profile.prestigeCount,
            "转生成功！现为 ${profile.prestigeCount}转，全属性+${profile.prestigeCount * 10}%，天赋点+1"
        )
    }

    // ======== 武魂觉醒（第十八轮） ========
    /**
     * 觉醒/重醒：未觉醒免费从品质池 roll（池按转数过滤，§2.3）；已觉醒再醒（重醒）花
     * GameBalance.REAWAKEN_COST_GOLD（文档未定价，实现决策防无限免费刷池，注释留档；
     * 重醒不限次数——品质上限由转数门槛兜住）。
     * 写点：martialSoulName + battleSoulPower（= GameBalance.martialSoulPower 公式值，
     * 宗门 Boss 伤害公式的死值由此活化；重醒时重算）。转生不清武魂（文档 §15.2 重置项
     * 不含武魂，§15.1 重醒是可选动作），故 prestige() 无需改动。
     * 失败语义与 breakthrough 一致：HTTP 200 + success=false + message，不抛异常、零改动。
     * 注：不调 achievementService.sync——成就口径（level/胜场/塔层/已装备环数/转数）没有
     * 武魂类成就，觉醒/重醒不改变任何成就进度维度。
     */
    @Transactional
    fun awaken(userId: Long): AwakenResponse {
        val profile = getProfile(userId)
        val reawakened = profile.martialSoulName != null
        if (reawakened) {
            val cost = GameBalance.REAWAKEN_COST_GOLD
            if (profile.gold < cost) {
                return AwakenResponse(
                    success = false, goldSpent = 0, reawakened = true,
                    message = "重醒需要${cost}金币（当前${profile.gold}）"
                )
            }
            profile.gold -= cost
        }
        // roll 用全局 Random：awaken 端点无既有掷点次序契约（GameBalance.rollMartialSoul 注释）
        val soul = GameBalance.rollMartialSoul(profile.prestigeCount)
        profile.martialSoulName = soul.name
        profile.battleSoulPower = GameBalance.martialSoulPower(soul).toInt()
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        // 业务计数器：觉醒成功（kind 区分首醒/重醒；Micrometer 不抛业务异常）
        counter(METRIC_AWAKEN_TOTAL, "kind", if (reawakened) "reawaken" else "first")
        return AwakenResponse(
            success = true,
            martialSoulName = soul.name,
            rarity = soul.rarity.name,
            goldSpent = if (reawakened) GameBalance.REAWAKEN_COST_GOLD else 0L,
            reawakened = reawakened,
            message = (if (reawakened) "重醒成功！" else "觉醒成功！") +
                    "获得${soul.rarity.displayName}武魂「${soul.name}」"
        )
    }

    // ======== 流派选择（第十九轮） ========
    /**
     * 选择流派：六流派（GameBalance.SCHOOLS）择一，系数经 schoolModsOf 进战斗/战力组装四处
     * （battle/towerBattle/getGameState/仿真镜像）。
     *  - 校验顺序：未知流派 → 已选择该流派 → 门槛（level+prestige，message 给出要求）→
     *    重选扣金（不足 success=false）→ 写 chosenSchool → save；
     *  - 首选免费；重选（已有流派改选）花 GameBalance.RESCHOOL_COST_GOLD（对齐 REAWAKEN_COST_GOLD
     *    惯例，防零成本来回切换）；门槛按「选时」校验（已选流派转生后不回撤——level 重置不影响
     *    已生效系数，与「转生不清武魂」同一保留语义），转生不清 chosenSchool，prestige() 无需改动；
     *  - 失败语义照 breakthrough：HTTP 200 + success=false + message，不抛异常、零改动。
     *  注 1：不重 roll 武魂——shared chooseSchool 会 roll，实现分歧照 awaken 惯例留档
     *  （GameBalance 流派区块注释）：后端流派选择与武魂觉醒解耦，零新增掷点。
     *  注 2：不调 achievementService.sync——成就口径（level/胜场/塔层/已装备环数/转数）没有
     *  流派类成就，选择流派不改变任何成就进度维度。
     */
    @Transactional
    fun chooseSchool(userId: Long, schoolName: String): ChooseSchoolResponse {
        val profile = getProfile(userId)
        val def = GameBalance.schoolByName(schoolName)
        if (def == null) {
            return ChooseSchoolResponse(success = false, chosenSchool = "", message = "未知流派")
        }
        if (profile.chosenSchool == def.name) {
            return ChooseSchoolResponse(
                success = false, chosenSchool = "",
                message = "已选择${def.displayName}，无需重复选择"
            )
        }
        if (!GameBalance.isSchoolUnlocked(def, profile.level, profile.prestigeCount)) {
            val requirement = buildString {
                append("需要 Lv.").append(def.requiredLevel)
                if (def.requiredPrestige > 0) append(" 且转生≥").append(def.requiredPrestige)
            }
            return ChooseSchoolResponse(
                success = false, chosenSchool = "",
                message = "门槛不足：$requirement（当前 Lv.${profile.level}/${profile.prestigeCount}转）"
            )
        }
        val reschooling = profile.chosenSchool != null
        if (reschooling) {
            val cost = GameBalance.RESCHOOL_COST_GOLD
            if (profile.gold < cost) {
                return ChooseSchoolResponse(
                    success = false, chosenSchool = "",
                    message = "改选流派需要${cost}金币（当前${profile.gold}）"
                )
            }
            profile.gold -= cost
        }
        profile.chosenSchool = def.name
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        // 业务计数器：流派选择成功（Micrometer 不抛业务异常）
        counter(METRIC_SCHOOL_CHOOSE_TOTAL)
        return ChooseSchoolResponse(
            success = true,
            chosenSchool = def.name,
            message = (if (reschooling) "改选成功！" else "选择成功！") +
                    "${def.icon} 已加入${def.displayName}${if (reschooling) "（花费${GameBalance.RESCHOOL_COST_GOLD}金币）" else ""}"
        )
    }

    @Transactional
    fun battle(userId: Long): BattleResponse {
        val profile = getProfile(userId)
        val mapId = profile.currentMapId
        val stage = profile.currentStage

        // 装备战力接入（P7 修复）+ 成就属性加成（bonusFor 唯一 choke point，即时生效）
        // 转生集成：bonusFor 返回的装备+成就合计加成已乘转生倍率（转数入参）
        // 第十八轮武魂集成：武魂加成（已乘转生倍率）并入同一加成包——atk/hp 进基础战斗口径
        // （maxHp/atk），五属性随 CombatStats 进结算，power 与战斗强度同口径
        val equip = EquipmentPowerService.plus(
            equipmentPowerService.bonusFor(userId, profile.level, profile.prestigeCount),
            soulBonusOf(profile.martialSoulName, profile.prestigeCount)
        )
        // 第十九轮流派集成：chosenSchool 反查系数（未选 → null 恒等）；power/maxHp/结算同口径
        val school = schoolModsOf(profile.chosenSchool)
        val power = EquipmentPowerService.powerOf(profile.level, EquipmentPowerService.applySchool(equip, school))

        // 生成怪物（第十七轮扩展：数据类含 matk/pdef/mdef）
        val monster = monsterStats(mapId, stage)
        val monsterName = "${MAP_NAMES.getOrElse(mapId) { "未知" }}·${stage}层怪物"

        // 战斗计算：完整战斗属性 = 等级基础(×转生倍率) + 装备/成就/武魂加成(已含倍率，五属性全量)
        // ×流派系数；maxHp 乘在加总后（schoolScaledMaxHp 注释：加成包里的武魂 hp 也吃到系数）
        // 第二十轮武魂技能：soulSkillOf 从 martialSoulName 反查技能随 CombatStats 进结算（四处同源之一）
        val maxHp = schoolScaledMaxHp(scaledBaseMaxHp(profile) + equip.hpBonus, school)
        val player = playerCombatStats(profile.level, profile.prestigeCount, equip, school,
            soulSkillOf(profile.martialSoulName))
        val outcome = resolveBattle(
            player = player,
            playerHp = profile.currentHp.coerceAtMost(maxHp),
            monster = monster,
            maxRounds = GameBalance.MAX_BATTLE_ROUNDS,
            rng = Random,
            playerMaxHp = maxHp
        )
        val won = outcome.won
        val playerHp = outcome.playerHpLeft
        val rounds = outcome.rounds
        val battleLog = outcome.log
        // 转生倍率（收入口径②）：战斗胜利产出 ×(1+转数×0.1)（prestigeCount=0 时取整逐位不变）
        val mult = GameBalance.prestigeMultiplier(profile.prestigeCount)
        val expGained = if (won)
            ((GameBalance.WIN_EXP_BASE + mapId * GameBalance.WIN_EXP_PER_MAP + stage * GameBalance.WIN_EXP_PER_STAGE) * mult).toLong()
        else 0L
        val goldGained = if (won)
            ((GameBalance.WIN_GOLD_BASE + mapId * GameBalance.WIN_GOLD_PER_MAP + stage * GameBalance.WIN_GOLD_PER_STAGE) * mult).toLong()
        else 0L

        if (won) {
            profile.totalBattleWins++
            profile.gold += goldGained
            profile.soulPower += expGained
            profile.currentHp = playerHp.coerceAtLeast(1)
            // 推进关卡
            if (stage >= GameBalance.STAGES_PER_MAP) {
                if (profile.autoAdvanceMap && mapId < GameBalance.MAX_MAP_ID) {
                    profile.currentMapId = mapId + 1
                    profile.currentStage = 1
                } else {
                    profile.currentStage = 15
                }
            } else {
                profile.currentStage = stage + 1
            }
            // 每日任务挂点（副路径）：仅战斗胜利计数，战败不计数（失败不击穿主流程，见 DailyQuestService）
            dailyQuestService.recordBattleWin(userId)
        } else {
            profile.totalBattleLosses++
            profile.currentHp = maxHp // 死亡回满血（含装备生命加成）
            profile.currentStage = 1 // 退回第1关
        }
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)

        // 战斗掉落（背包已满则掉落丢失，并在响应中提示）
        val drops = mutableListOf<BackpackItemDto>()
        var dropLostMessage: String? = null
        if (won) {
            val dropChance = GameBalance.BASE_DROP_CHANCE + mapId * GameBalance.DROP_CHANCE_PER_MAP +
                    stage * GameBalance.DROP_CHANCE_PER_STAGE
            if (Random.nextDouble() < dropChance) {
                if (hasBackpackSpace(profile)) {
                    val dropType = when (Random.nextInt(3)) {
                        0 -> "RING"
                        1 -> "BONE"
                        else -> "CORE"
                    }
                    // 战斗掉落环年份档位随地图推进（8-10 图封顶 tier-3，依据见 BATTLE_RING_DROP_YEAR_CAP 注释）
                    val yearOrdinal = (mapId / 2).coerceIn(0, GameBalance.BATTLE_RING_DROP_YEAR_CAP)
                    val qualityOrdinal = Random.nextInt(0, 5)
                    val item = BackpackItemEntity(
                        userId = userId,
                        itemType = dropType,
                        yearOrdinal = yearOrdinal,
                        qualityOrdinal = qualityOrdinal,
                        percentage = Random.nextInt(100, 1000)
                    )
                    backpackRepo.save(item)
                    drops.add(toBackpackItemDto(item))
                } else {
                    dropLostMessage = "背包已满，掉落物品丢失，请及时整理背包"
                }
            }
            // Boss额外掉落
            if (stage % 5 == 0 && Random.nextDouble() < GameBalance.BOSS_EXTRA_DROP_CHANCE) {
                profile.bossCoin += 1 + mapId
            }
        }

        // 广播战斗结果到 WebSocket
        val user = userRepository.findById(userId).orElse(null)
        if (user != null) {
            webSocketService.broadcastBattleResult(userId, user.username, monsterName, won)
        }

        // 成就挂点（副路径）：胜败都可触发——totalBattleWins/等级在两侧都有跳变点，
        // sync 便宜且统一（失败不击穿主流程，见 AchievementService）
        achievementService.sync(userId)

        // 业务计数器：战斗结算（胜败都计；Micrometer 不抛业务异常，不影响主流程）
        counter(METRIC_BATTLE_TOTAL, "outcome", if (won) "win" else "lose")

        return BattleResponse(
            won = won, rounds = rounds, monsterName = monsterName, monsterMaxHp = monster.hp,
            expGained = expGained, goldGained = goldGained,
            drops = drops, playerHp = profile.currentHp,
            playerLevel = profile.level, playerGold = profile.gold,
            playerSoulPower = profile.soulPower, battleLog = battleLog,
            message = dropLostMessage, power = power
        )
    }

    // 任务#29：towerFloor 是爬塔榜分数，唯一写点，结算后清空榜单缓存
    @CacheEvict(cacheNames = ["rank"], allEntries = true)
    @Transactional
    fun towerBattle(userId: Long): TowerResponse {
        val profile = getProfile(userId)
        val towerLevel = profile.towerFloor * GameBalance.TOWER_LEVEL_PER_FLOOR
        val foughtFloor = profile.towerFloor // 塔战日志种子取挑战时楼层（胜利分支随后会 +1）
        // P2+P7 修复：胜率 = 1-(0.25+floor×0.005) 基础值 + 装备战力加成（floor=99 仍 >0，换装可登顶）
        // 成就属性加成经 bonusFor 并入（唯一 choke point，即时生效）；转生集成：加成已乘转生倍率
        // 第十八轮武魂集成：武魂加成（已乘转生倍率）并入，塔胜率/塔日志/战败回满与 battle 同口径
        // 第十九轮流派集成：chosenSchool 反查系数（未选 → null 恒等）；power（含塔胜率）/maxHp/
        // 塔日志玩家属性与 battle 同一含流派口径
        val equip = EquipmentPowerService.plus(
            equipmentPowerService.bonusFor(userId, profile.level, profile.prestigeCount),
            soulBonusOf(profile.martialSoulName, profile.prestigeCount)
        )
        val school = schoolModsOf(profile.chosenSchool)
        val power = EquipmentPowerService.powerOf(profile.level, EquipmentPowerService.applySchool(equip, school))
        val won = Random.nextDouble() < EquipmentPowerService.towerWinChance(profile.towerFloor, power)
        val monsterName = GameBalance.TOWER_MONSTERS[Random.nextInt(GameBalance.TOWER_MONSTERS.size)]
        val rounds = 4 + Random.nextInt(6)
        // 转生倍率（收入口径③）：塔胜产出 ×(1+转数×0.1)（prestigeCount=0 时取整逐位不变）
        val mult = GameBalance.prestigeMultiplier(profile.prestigeCount)
        val expGained = if (won) ((GameBalance.TOWER_EXP_BASE + towerLevel * GameBalance.TOWER_EXP_PER_LEVEL) * mult).toLong() else 0L
        val goldGained = if (won) ((GameBalance.TOWER_GOLD_BASE + towerLevel * GameBalance.TOWER_GOLD_PER_LEVEL) * mult).toLong() else 0L
        val bossCoinGained = if (won && Random.nextDouble() < GameBalance.TOWER_BOSS_COIN_CHANCE) 1L else 0L
        val killingGained = if (won) 1 + profile.towerFloor / GameBalance.TOWER_KILLING_PER_FLOORS else 0
        val drops = if (won && Random.nextDouble() < GameBalance.TOWER_DROP_CHANCE) {
            listOfNotNull(rollBackpackDrop(userId, towerLevel))
        } else {
            emptyList()
        }

        if (won) {
            profile.gold += goldGained
            profile.soulPower += expGained
            profile.bossCoin += bossCoinGained
            profile.towerFloor = min(GameBalance.TOWER_MAX_FLOOR, profile.towerFloor + 1)
            profile.killingIntent += killingGained
            profile.totalBattleWins += 1
            profile.codexKills += 1
        } else {
            profile.totalBattleLosses += 1
            // 战败回满血：与 battle 同款含流派口径（schoolScaledMaxHp，乘在基础+加成加总后）
            profile.currentHp = schoolScaledMaxHp(scaledBaseMaxHp(profile) + equip.hpBonus, school)
        }
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)

        // 每日任务挂点（副路径）：挑战即计数，不论胜负（失败不击穿主流程，见 DailyQuestService）
        dailyQuestService.recordTower(userId)
        // 成就挂点（副路径）：塔层/胜场跳变点，胜败都可触发（失败不击穿主流程，见 AchievementService）
        achievementService.sync(userId)

        // 塔战逐回合日志：插在全部既有掷点之后、用独立种子模拟（不改胜负判定、不动 RNG 次序，
        // LongRunSimulationTest 的 towerWinChance/resolveBattle 镜像契约不受影响）。
        // 玩家属性口径与 battle() 调 resolveBattle 时一致（含转生倍率）；生命用满血
        // （塔挑战以满血进行，与战败回满语义一致）。
        // 第十七轮扩展：塔日志重模拟的玩家属性与 battle() 完全同口径（playerCombatStats，
        // 含五属性与转生倍率——独立种子只复现掷点，属性入参必须与主路径一致）
        // 第十九轮流派：塔日志玩家属性/生命与 battle() 同一含流派口径
        // 第二十轮武魂技能：soulSkillOf 同源反查随 CombatStats 进塔日志重模拟（四处同源之二）
        val towerPlayerMaxHp = schoolScaledMaxHp(scaledBaseMaxHp(profile) + equip.hpBonus, school)
        val battleLog = buildTowerBattleLog(
            userId, foughtFloor, won,
            playerCombatStats(profile.level, profile.prestigeCount, equip, school,
                soulSkillOf(profile.martialSoulName)), towerPlayerMaxHp
        )

        return TowerResponse(
            won = won,
            // battleLog 非空时与回放对齐（旧行为是 4+rand(6) 假值）；空时维持旧值兜底
            rounds = if (battleLog.isNotEmpty()) battleLog.size else rounds,
            monsterName = monsterName,
            expGained = expGained, goldGained = goldGained, bossCoinGained = bossCoinGained,
            towerFloor = profile.towerFloor, killingIntent = profile.killingIntent,
            drops = drops, playerLevel = profile.level, power = power,
            battleLog = battleLog
        )
    }

    /**
     * 生成一件随机装备并放入背包，返回其 DTO；背包已满时拒绝发放并返回 null（掉落丢失）。
     * 掉落权重参照数值框架：魂骨 > 魂核 > 魂环，品质/年份随等级提升。
     */
    @Transactional
    fun rollBackpackDrop(userId: Long, level: Int): BackpackItemDto? {
        val profile = getProfile(userId)
        if (!hasBackpackSpace(profile)) return null
        val itemType = when {
            Random.nextDouble() > 0.72 -> "BONE"
            Random.nextDouble() > 0.45 -> "CORE"
            else -> "RING"
        }
        val qualityOrdinal = min(4, level / 8 + Random.nextInt(3))
        // 任务#22：魂环年份封顶 TOWER_RING_DROP_YEAR_CAP（塔环死掉落治理，见 GameBalance 注释）；
        // 魂骨/魂核不占负荷、战力档位保留原曲线。RNG 消耗次序与原实现一致（先抽后截断）。
        val yearRoll = level / 12 + Random.nextInt(2)
        val yearOrdinal = if (itemType == "RING") min(GameBalance.TOWER_RING_DROP_YEAR_CAP, yearRoll) else min(4, yearRoll)
        val item = BackpackItemEntity(
            userId = userId,
            itemType = itemType,
            yearOrdinal = yearOrdinal,
            qualityOrdinal = qualityOrdinal,
            percentage = 100 + level * 12 + Random.nextInt(80),
            skillName = if (itemType == "RING") listOf("蓝银缠绕", "昊天重击", "疾风突刺")[Random.nextInt(3)] else null,
            boneTypeOrdinal = if (itemType == "BONE") Random.nextInt(6) else null,
            enhanceLevel = if (itemType == "BONE") max(1, level / 10) else 0,
            passiveSkillName = if (itemType != "RING") listOf("坚韧", "破甲", "凝神")[Random.nextInt(3)] else null,
            coreName = if (itemType == "CORE") listOf("攻击魂核", "防御魂核", "辅助魂核")[Random.nextInt(3)] else null,
            coreValue = if (itemType == "CORE") 10 + level * 3 else null,
            coreLevel = if (itemType == "CORE") max(1, level / 5) else 0
        )
        backpackRepo.save(item)
        return toBackpackItemDto(item)
    }

    @Transactional
    fun claimOfflineReward(userId: Long): OfflineRewardResponse {
        val profile = getProfile(userId)
        // 离线起算时间：优先登出时间；从未登出过（老数据/首次）时回退最后登录时间或最后更新时间
        val lastExit = profile.lastLogoutTime
            ?: profile.user?.lastLoginAt
            ?: profile.updatedAt
        val offlineSeconds = Duration.between(lastExit, LocalDateTime.now()).seconds
        val effectiveSeconds = min(offlineSeconds, GameBalance.OFFLINE_MAX_SECONDS)
        if (effectiveSeconds < 60) return OfflineRewardResponse(effectiveSeconds, 0, 0, 0)

        // P1 修复：OFFLINE_*_BASE/PER_LEVEL 语义为「每小时」，按小时计费（不足 1h 按比例折算）。
        // 旧实现按秒计费导致 12h=43,200 秒直接日入数十万~数千万金币（见仿真报告 P1）。
        // 转生倍率（收入口径④）：离线 gold/exp 产出 ×(1+转数×0.1)（prestigeCount=0 时逐位不变）
        val mult = GameBalance.prestigeMultiplier(profile.prestigeCount)
        val effectiveHours = effectiveSeconds / 3600.0
        val goldPerHour = (GameBalance.OFFLINE_GOLD_BASE + profile.level * GameBalance.OFFLINE_GOLD_PER_LEVEL) *
                GameBalance.OFFLINE_EFFICIENCY * mult
        val expPerHour = (GameBalance.OFFLINE_EXP_BASE + profile.level * GameBalance.OFFLINE_EXP_PER_LEVEL) *
                GameBalance.OFFLINE_EFFICIENCY * mult
        val goldGained = (goldPerHour * effectiveHours).toLong()
        val expGained = (expPerHour * effectiveHours).toLong()
        val battleWins = effectiveSeconds / GameBalance.OFFLINE_SECONDS_PER_BATTLE_WIN

        profile.gold += goldGained
        profile.soulPower += expGained
        profile.totalBattleWins += battleWins
        profile.lastLogoutTime = LocalDateTime.now()
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        // 成就挂点（副路径）：totalBattleWins 跳变点（离线胜场折算可能一次跨过 battle_10/battle_50）
        achievementService.sync(userId)

        return OfflineRewardResponse(effectiveSeconds, goldGained, expGained, battleWins)
    }

    private fun getProfile(userId: Long): PlayerProfileEntity {
        return profileRepo.findByUserId(userId)
            ?: throw IllegalStateException("玩家存档不存在")
    }

    /** 业务计数器薄封装：Micrometer 计数为内存操作、不抛业务异常，直接增量即可（不影响主流程） */
    private fun counter(name: String, vararg tags: String) {
        meterRegistry.counter(name, *tags).increment()
    }

    /**
     * 背包是否还有空间放入一件新物品。所有获得新背包物品的路径
     * （战斗掉落、魂塔掉落、宗门 Boss 掉落）发放前必须校验。
     */
    private fun hasBackpackSpace(profile: PlayerProfileEntity): Boolean {
        return backpackRepo.countByUserId(profile.userId) < profile.backpackCapacity
    }

    private fun getBreakthroughCost(level: Int): Long {
        return (120.0 * Math.pow(level.toDouble(), 1.55)).toLong()
    }

    private fun getRealmName(level: Int): String {
        val idx = ((level - 1) / 10).coerceIn(0, REALM_NAMES.size - 1)
        return REALM_NAMES[idx]
    }

    private fun getMaxHp(level: Int): Long = 50L * level + 100L

    // 注：基础攻击的转生倍率缩放已并入 playerCombatStats（第十七轮战斗模型扩展，battle/塔/状态
    // 组装三处同源）；scaledBaseMaxHp 仍独立保留（prestige/战败回满等非战斗路径使用）。

    /** 转生倍率下的基础生命上限（等级部分），口径同 playerCombatStats 的基础属性缩放 */
    private fun scaledBaseMaxHp(profile: PlayerProfileEntity): Long {
        val base = getMaxHp(profile.level)
        return if (profile.prestigeCount <= 0) base
        else (base * GameBalance.prestigeMultiplier(profile.prestigeCount)).toLong()
    }

    private fun toProfileDto(p: PlayerProfileEntity) = ProfileDto(
        level = p.level, gold = p.gold, soulPower = p.soulPower, bossCoin = p.bossCoin,
        martialSoulName = p.martialSoulName, chosenSchool = p.chosenSchool,
        currentMapId = p.currentMapId, currentStage = p.currentStage,
        currentHp = p.currentHp, battleSoulPower = p.battleSoulPower,
        totalBattleWins = p.totalBattleWins, totalBattleLosses = p.totalBattleLosses,
        towerFloor = p.towerFloor, killingIntent = p.killingIntent,
        prestigeCount = p.prestigeCount, talentPoints = p.talentPoints,
        codexKills = p.codexKills, autoBattle = p.autoBattle,
        autoAdvanceMap = p.autoAdvanceMap, autoBreakthrough = p.autoBreakthrough,
        tutorialStep = p.tutorialStep,
        // 第十八轮武魂集成：品质徽章由名字反查武魂池，不落库不加列（避免迁移）；未觉醒/脏数据为 null
        soulRarity = p.martialSoulName?.let { GameBalance.soulByName(it)?.rarity?.name },
        // 第二十轮武魂技能：技能名由名字反查武魂池（与 soulRarity 同款不落库口径）；未觉醒/脏数据为 null
        soulSkillName = p.martialSoulName?.let { GameBalance.soulByName(it)?.skill?.name }
    )

    private fun toBackpackItemDto(e: com.douluodalu.game.entity.BackpackItemEntity) = BackpackItemDto(
        id = e.id, itemType = e.itemType, yearOrdinal = e.yearOrdinal,
        qualityOrdinal = e.qualityOrdinal, affixesJson = e.affixesJson, locked = e.locked,
        percentage = e.percentage, skillName = e.skillName, boneTypeOrdinal = e.boneTypeOrdinal,
        enhanceLevel = e.enhanceLevel, passiveSkillName = e.passiveSkillName,
        coreName = e.coreName, coreValue = e.coreValue, coreLevel = e.coreLevel,
        // 任务#21：魂环条目带负荷，供前端"装得下/装不下"预览（其他装备不占负荷，为 0）
        load = if (e.itemType == "RING") RingLoadCalculator.ringLoad(e.yearOrdinal, e.qualityOrdinal, e.percentage) else 0
    )

    /** 玩家魂环吸收容量：根骨×GameBalance.RING_CAPACITY_ROOT_MULT。第十七轮战斗模型扩展后
     *  matk/pdef/mdef 已进战斗结算，但容量口径【有意】维持 atk/hp 两维（负荷公式不动，避免容量带
     *  漂移扰动任务#22 校准好的 40~92% 利用率带）——matk/pdef/mdef 传 0 并在此留档 */
    private fun absorptionCapacityFor(profile: PlayerProfileEntity, bonus: EquipmentBonus): Long =
        RingLoadCalculator.absorptionCapacity(
            RingLoadCalculator.calcRootBone(
                maxHp = getMaxHp(profile.level) + bonus.hpBonus,
                atk = GameBalance.PLAYER_ATK_BASE + profile.level * GameBalance.PLAYER_ATK_PER_LEVEL + bonus.atkBonus,
                matk = 0, pdef = 0, mdef = 0
            )
        )

    // ======== 装备操作 ========
    /**
     * 装备魂环（任务#21：接入 shared 同源的负荷校验）。
     * 超负荷时抛 IllegalArgumentException → GlobalExceptionHandler 输出 400 {error,message}。
     */
    @Transactional
    fun equipRing(userId: Long, slotIndex: Int, ringIndex: Int): Boolean {
        if (slotIndex < 0 || slotIndex > 8) return false // 9个槽位

        // 获取背包中所有魂环（按创建时间排序）
        val rings = backpackRepo.findByUserIdAndItemType(userId, "RING").sortedBy { it.createdAt }
        if (ringIndex < 0 || ringIndex >= rings.size) return false
        val ring = rings[ringIndex]

        // 检查槽位是否已被占用，如果有则卸下原有魂环
        val existing = equippedRingRepo.findByUserIdAndSlotIndex(userId, slotIndex)

        // 吸收容量检测（shared GameEngine.kt:1851~1861 同构）：换装时旧环负荷先释放，再叠加新环
        val profile = getProfile(userId)
        val equipped = equippedRingRepo.findByUserId(userId)
        val newLoad = RingLoadCalculator.ringLoad(ring.yearOrdinal, ring.qualityOrdinal, ring.percentage)
        val loadAfterEquip = RingLoadCalculator.totalRingLoad(equipped.filterNot { it.slotIndex == slotIndex }) + newLoad
        val totalLoadBefore = RingLoadCalculator.totalRingLoad(equipped)
        val bonus = EquipmentPowerService.bonus(
            profile.level, equipped,
            equippedBoneRepo.findByUserId(userId), equippedCoreRepo.findByUserId(userId)
        )
        val capacity = absorptionCapacityFor(profile, bonus)
        if (loadAfterEquip > capacity) {
            val overload = loadAfterEquip - capacity
            throw IllegalArgumentException(
                "负荷不足！当前负荷 $totalLoadBefore/$capacity，该魂环需负荷 $newLoad，还需 $overload 才可吸收"
            )
        }

        if (existing != null) {
            // 卸下已有魂环
            backpackRepo.save(
                BackpackItemEntity(
                    userId = userId,
                    itemType = "RING",
                    yearOrdinal = existing.yearOrdinal,
                    qualityOrdinal = existing.qualityOrdinal,
                    percentage = existing.percentage
                )
            )
            equippedRingRepo.delete(existing)
        }

        // 装备新魂环
        equippedRingRepo.save(
            EquippedRing(
                userId = userId,
                slotIndex = slotIndex,
                ringId = ring.id,
                yearOrdinal = ring.yearOrdinal,
                qualityOrdinal = ring.qualityOrdinal,
                percentage = ring.percentage
            )
        )
        backpackRepo.delete(ring)
        // 成就挂点（副路径）：SOUL_RING 口径 = 已装备魂环数，成功装环后同步（失败不击穿主流程）。
        // 注：上方容量校验按装备口径（companion bonus，不含成就加成）——校验保守方向，
        // 差异带 ≤ 成就 hp/atk 折算的容量增量。
        achievementService.sync(userId)
        return true
    }

    @Transactional
    fun unequipRing(userId: Long, slotIndex: Int): Boolean {
        if (slotIndex < 0 || slotIndex > 8) return false
        val equipped = equippedRingRepo.findByUserIdAndSlotIndex(userId, slotIndex) ?: return false

        // 移回背包
        backpackRepo.save(
            BackpackItemEntity(
                userId = userId,
                itemType = "RING",
                yearOrdinal = equipped.yearOrdinal,
                qualityOrdinal = equipped.qualityOrdinal,
                percentage = equipped.percentage
            )
        )
        equippedRingRepo.delete(equipped)
        return true
    }

    @Transactional
    fun equipBone(userId: Long, slotIndex: Int, boneIndex: Int): Boolean {
        if (slotIndex < 0 || slotIndex > 5) return false // 6个槽位

        val bones = backpackRepo.findByUserIdAndItemType(userId, "BONE").sortedBy { it.createdAt }
        if (boneIndex < 0 || boneIndex >= bones.size) return false
        val bone = bones[boneIndex]

        val existing = equippedBoneRepo.findByUserIdAndSlotIndex(userId, slotIndex)
        if (existing != null) {
            backpackRepo.save(
                BackpackItemEntity(
                    userId = userId,
                    itemType = "BONE",
                    yearOrdinal = existing.yearOrdinal,
                    qualityOrdinal = existing.qualityOrdinal,
                    boneTypeOrdinal = existing.boneTypeOrdinal,
                    enhanceLevel = existing.enhanceLevel
                )
            )
            equippedBoneRepo.delete(existing)
        }

        equippedBoneRepo.save(
            EquippedBone(
                userId = userId,
                slotIndex = slotIndex,
                boneId = bone.id,
                yearOrdinal = bone.yearOrdinal,
                qualityOrdinal = bone.qualityOrdinal,
                boneTypeOrdinal = bone.boneTypeOrdinal ?: 0,
                enhanceLevel = bone.enhanceLevel
            )
        )
        backpackRepo.delete(bone)
        return true
    }

    @Transactional
    fun unequipBone(userId: Long, slotIndex: Int): Boolean {
        if (slotIndex < 0 || slotIndex > 5) return false
        val equipped = equippedBoneRepo.findByUserIdAndSlotIndex(userId, slotIndex) ?: return false

        backpackRepo.save(
            BackpackItemEntity(
                userId = userId,
                itemType = "BONE",
                yearOrdinal = equipped.yearOrdinal,
                qualityOrdinal = equipped.qualityOrdinal,
                boneTypeOrdinal = equipped.boneTypeOrdinal,
                enhanceLevel = equipped.enhanceLevel
            )
        )
        equippedBoneRepo.delete(equipped)
        return true
    }

    @Transactional
    fun equipCore(userId: Long, slotType: String, coreIndex: Int): Boolean {
        val validSlots = setOf("LEFT", "RIGHT")
        if (!validSlots.contains(slotType.uppercase())) return false

        val cores = backpackRepo.findByUserIdAndItemType(userId, "CORE").sortedBy { it.createdAt }
        if (coreIndex < 0 || coreIndex >= cores.size) return false
        val core = cores[coreIndex]

        val existing = equippedCoreRepo.findByUserIdAndSlotType(userId, slotType.uppercase())
        if (existing != null) {
            backpackRepo.save(
                BackpackItemEntity(
                    userId = userId,
                    itemType = "CORE",
                    qualityOrdinal = existing.rarityOrdinal,
                    coreName = existing.coreName,
                    coreValue = existing.coreValue,
                    coreLevel = existing.coreLevel
                )
            )
            equippedCoreRepo.delete(existing)
        }

        equippedCoreRepo.save(
            EquippedCore(
                userId = userId,
                slotType = slotType.uppercase(),
                coreId = core.id,
                rarityOrdinal = core.qualityOrdinal,
                coreName = core.coreName ?: "",
                coreValue = core.coreValue ?: 0,
                coreLevel = core.coreLevel
            )
        )
        backpackRepo.delete(core)
        return true
    }

    @Transactional
    fun unequipCore(userId: Long, slotType: String): Boolean {
        if (!setOf("LEFT", "RIGHT").contains(slotType.uppercase())) return false
        val equipped = equippedCoreRepo.findByUserIdAndSlotType(userId, slotType.uppercase()) ?: return false

        backpackRepo.save(
            BackpackItemEntity(
                userId = userId,
                itemType = "CORE",
                qualityOrdinal = equipped.rarityOrdinal,
                coreName = equipped.coreName,
                coreValue = equipped.coreValue,
                coreLevel = equipped.coreLevel
            )
        )
        equippedCoreRepo.delete(equipped)
        return true
    }

    @Transactional
    fun sellBackpackItem(userId: Long, itemIndex: Int): Boolean {
        val profile = getProfile(userId)
        val items = backpackRepo.findByUserId(userId).sortedBy { it.createdAt }
        if (itemIndex < 0 || itemIndex >= items.size) return false
        val item = items[itemIndex]
        if (item.locked) return false
        // 计算售价
        val sellPrice = 100L + item.qualityOrdinal * 50L
        profile.gold += sellPrice
        backpackRepo.delete(item)
        profileRepo.save(profile)
        return true
    }

    @Transactional
    fun expandBackpack(userId: Long): Boolean {
        val profile = getProfile(userId)
        val cost = 1000L + profile.backpackCapacity * 100L
        if (profile.gold < cost) return false
        profile.gold -= cost
        profile.backpackCapacity += 5
        profileRepo.save(profile)
        return true
    }
}
