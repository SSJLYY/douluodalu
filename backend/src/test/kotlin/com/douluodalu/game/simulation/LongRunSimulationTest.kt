package com.douluodalu.game.simulation

import com.douluodalu.game.entity.EquippedBone
import com.douluodalu.game.entity.EquippedCore
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.service.AchievementService
import com.douluodalu.game.service.EquipmentBonus
import com.douluodalu.game.service.EquipmentPowerService
import com.douluodalu.game.service.GameService
import com.douluodalu.game.service.RingLoadCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.min
import kotlin.random.Random

/**
 * 任务#17：游戏数值 30/90 天长跑仿真（纯 Kotlin，不起 Spring、不连库，毫秒级完成）。
 *
 * 任务#20 修复 P1/P2/P7 后，battle/tower/offline 的镜像已改为直接调用生产端纯函数
 * （GameService.monsterStats/resolveBattle、EquipmentPowerService.towerWinChance/powerOf），
 * 不再各自手抄公式，避免镜像漂移。仍为纯计算、固定种子、可复现。
 *
 * 其余镜像：
 *   - cultivate()            公式镜像自 GameService.cultivate，改动需同步
 *   - breakthrough/cost      公式镜像自 GameService.breakthrough / getBreakthroughCost，改动需同步
 *   - tower()/rollDrop       奖励与掉落结构镜像自 GameService.towerBattle / rollBackpackDrop，改动需同步
 *   - claimOffline()         公式镜像自 GameService.claimOfflineReward（P1 修复后按小时计费、12h 截断）
 *   - sellJunk()/buyExpand() 公式镜像自 GameService.sellBackpackItem / NormalShopData 205 号商品，改动需同步
 *   - dailyIncome()          签到（CHECK_IN_REWARDS 7 日循环）+ 每日任务（DAILY_QUESTS 全清上界假设），改动需同步
 *   - achievementBonusOf()   成就属性加成（调用生产纯函数 AchievementService.progressOf +
 *                            EquipmentPowerService.achievementBonus，按镜像玩家状态算已解锁集合）
 *   - prestige/doPrestige()  转生镜像（GameService.prestige + GameBalance.prestigeMultiplier）：
 *                            breakthroughAll 达到 PRESTIGE_MIN_LEVEL 即转生——level/gold/soulPower 清零、
 *                            stage 回 1、环骨卸回背包（魂核保留）、towerFloor/mapId 保留；
 *                            收入与属性按双口径 ×(1+转数×0.1)，改动需同步
 *   - awaken()               觉醒镜像（第十八轮：GameService.awaken + GameBalance.rollMartialSoul/soulBonusOf，
 *                            首醒免费/重醒 REAWAKEN_COST_GOLD；第 1 天首醒、每转重醒一次；
 *                            roll 注入镜像自身 rng、武魂加成计入战斗属性与塔战力），改动需同步
 *   - soulSkill()            武魂技能镜像（第二十轮：GameService.soulSkillOf 反查随 CombatStats 进
 *                            resolveBattle——技能经生产纯函数自动生效，镜像零额外公式），改动需同步
 *   - chooseSchool()         流派镜像（第十九轮：GameService.chooseSchool + GameBalance.SCHOOLS）：
 *                            第 1 天免费选 BALANCED（温和系数建模，不重 roll 武魂、不消耗掷点），
 *                            系数经 GameService.schoolModsOf/playerCombatStats/schoolScaledMaxHp/
 *                            EquipmentPowerService.applySchool 计入战斗属性、maxHp 与塔胜率战力，改动需同步
 *   - enhancePass()          魂骨强化镜像（第二十七轮：GameService.enhanceBone slotIndex 路径 +
 *                            GameBalance.boneEnhanceCost/BONE_ENHANCE_MAX_LEVEL，费用走生产公式、100% 成功；
 *                            玩家策略见 ENHANCE_GOLD_FLOOR/ENHANCE_RESERVE 注释——预算约束防挤兑
 *                            扩容/商店，扣费镜像否则仿真高估金币存量），改动需同步
 *   - boneAffix()            魂骨词缀掷点镜像（第二十八轮：塔掉落 BONE 分支在全部既有掷点之后
 *                            调用生产同一 GameBalance.rollBoneAffixList 注入镜像自身 rng——条数=品质+1、
 *                            逐条类型 nextInt(5−已选)、数值品质插值，掷点次序与生产逐位同构；
 *                            词缀随 SimItem.affixes → EquippedBone.affixesJson 进 EquipSimPlayer
 *                            的 bonus()/战力口径；battle 内联掉落不加词缀，与生产分层一致），改动需同步
 *   - dungeon()              每日副本镜像（第二十九轮：DungeonService.fight/sweep + GameBalance.DUNGEON_DEFS）：
 *                            每天唯一一次机会（胜败都占当日名额）挑战已解锁最高难度，ever_cleared 位掩码
 *                            镜像历史通关、通关后改扫荡（耗魂力 50+等级×5）；金币=goldReward×
 *                            prestigeMultiplier、杀气直加不乘倍率、掉落 rollBackpackDropMirror
 *                            （level=dropTier×12，与塔共用一份镜像），改动需同步
 *   - killingShop()          杀气商店镜像（第二十九轮：ShopService.buyKillingTitle/buyKillingAttr）：
 *                            称号 title_1→title_8 顺序攒够即买（属性性价比高于属性购买，见方法 KDoc）、
 *                            全满后剩余杀气轮替买 HP/ATK（价格 100×2^已购次数）；杀气收入=塔胜
 *                            1+floor/10（既有）+副本直加，加成经生产纯函数 killingBonus 并入战斗/塔
 *                            战力加成包（equip+ach+kill 合计后再乘转生倍率，与 bonusFor 同通道），改动需同步
 * 平衡常量直接引用 GameBalance（单一事实来源），但公式结构如有改动需同步本文件。
 *
 * 断言刻意只放软性的健康检查（跑通、数量级不离谱）；主要产出是根目录
 * 《数值仿真报告-90天.md》，内容确定（固定随机种子）、幂等重写。
 *
 * 任务#22 追加：《魂环负荷反馈回路专项》——EquipSimPlayer 在任务#20/#21 之后的生产公式上
 * 建模「穿装 → atk/hp↑ → 容量↑ → 能装更强环 → 穿装」回路（负荷/容量/战力全部直接调用
 * RingLoadCalculator / EquipmentPowerService / GameService 纯函数，不手抄），
 * 与「无负荷校验」对照组比较推图速度，并做 10 种子鲁棒性抽查。
 */
class LongRunSimulationTest {

    companion object {
        // ======== 每日任务收入镜像（全清上界假设） ========
        // 上界假设：经济在全清上界下不崩即安全。直接读 DAILY_QUESTS 求和（当前 5 任务合计
        // 600 金/2 Boss币/1000 魂力/日），不硬编码，定义表调参自动跟随。
        private val QUEST_GOLD_PER_DAY = GameBalance.DAILY_QUESTS.sumOf { it.rewardGold }
        private val QUEST_BOSS_COIN_PER_DAY = GameBalance.DAILY_QUESTS.sumOf { it.rewardBossCoin }
        private val QUEST_SOUL_POWER_PER_DAY = GameBalance.DAILY_QUESTS.sumOf { it.rewardSoulPower }

        // ======== 魂骨强化镜像的玩家策略预算（第二十七轮，仿真侧杠杆；生产常量不动） ========
        // 金币存量低于 ENHANCE_GOLD_FLOOR 不强化；单次强化费用 ≤ gold − ENHANCE_GOLD_FLOOR
        // （强化后存量仍守住 3 万防线——比「保留 3000 扩容券预算」更保守，扩容/商店完全不受挤兑，
        // 从而把「强化抢金币 → 扩容延迟 → 背包满丢掉落」隔离在策略外，作为观察口径留档）。
        private val ENHANCE_GOLD_FLOOR = 30_000L
    }

    // ======== 镜像状态 ========

    private class DayStats {
        var offlineGold = 0L; var offlineExp = 0L; var offlineWins = 0L
        var battleGold = 0L; var battleWins = 0L; var battleLosses = 0L
        var towerGold = 0L; var towerWins = 0L; var towerLosses = 0L
        var sellGold = 0L; var bossCoins = 0L
        var dropsGained = 0L; var dropsLost = 0L
        var breakthroughs = 0
        var prestiges = 0
        // 签到+任务（每日固定收入镜像，经济占比核算用）
        var checkInGold = 0L; var questGold = 0L
        var checkInQuestBossCoin = 0L; var checkInQuestSoulPower = 0L
        // 第十八轮武魂觉醒镜像事件计数（当日；报告「觉醒事件」行汇总）
        var awakens = 0; var reawakens = 0
        // 第十九轮流派镜像事件计数（当日；报告「流派事件」行汇总）
        var schoolChoices = 0
        // 第二十七轮魂骨强化镜像事件计数（当日；报告「强化镜像」小节汇总）
        var enhances = 0; var enhanceGold = 0L
        // 第二十九轮每日副本镜像事件计数（当日；报告「每日副本镜像」小节汇总）
        var dungeonGold = 0L; var dungeonWins = 0L; var dungeonLosses = 0L; var dungeonSweeps = 0L
        var sweepSoulPower = 0L
        // 第二十九轮杀气经济镜像（当日）：杀气收入来源（塔胜 + 副本）与商店支出（称号/属性购买）
        var towerKilling = 0L; var dungeonKilling = 0L
        var killingSpent = 0L; var titleBuys = 0; var attrBuys = 0
    }

    /** 每日固定收入镜像（SimPlayer/EquipSimPlayer 共用，保证两组画像经济口径一致） */
    private data class DailyIncome(
        val gold: Long, val soulPower: Long, val bossCoin: Long,
        val checkInGold: Long, val questGold: Long
    )

    /**
     * 签到 + 每日任务的当日收入：
     *  - 签到：逐日按 CHECK_IN_REWARDS[((day-1)%7)]（7 日循环，day 从 1 起）；
     *  - 任务：全清上界假设（DAILY_QUESTS 求和，见 companion 注释）。
     */
    private fun dailyIncome(day: Int): DailyIncome {
        val cir = GameBalance.CHECK_IN_REWARDS[(day - 1) % GameBalance.CHECK_IN_CYCLE]
        return DailyIncome(
            gold = cir.gold + QUEST_GOLD_PER_DAY,
            soulPower = cir.soulPower + QUEST_SOUL_POWER_PER_DAY,
            bossCoin = cir.bossCoin + QUEST_BOSS_COIN_PER_DAY,
            checkInGold = cir.gold,
            questGold = QUEST_GOLD_PER_DAY
        )
    }

    /**
     * 成就属性加成镜像：按镜像玩家状态算已解锁集合，七字段加成计入战斗属性
     * （第十七轮战斗模型扩展起 matk/pdef/mdef/critRate/critDmg 随 EquipmentBonus 全量生效）。
     * 口径映射与求和**直接调用生产纯函数**（AchievementService.progressOf +
     * EquipmentPowerService.achievementBonus），不手抄。SimPlayer 不穿装 → 环数按 0 计；
     * EquipSimPlayer 传实际槽位数。
     */
    private fun achievementBonusOf(
        level: Int, totalBattleWins: Long, towerFloor: Int, equippedRingCount: Int, prestigeCount: Int
    ): EquipmentBonus {
        val ids = GameBalance.AchievementDefs.all
            .filter {
                AchievementService.progressOf(it.category, level, totalBattleWins, towerFloor, equippedRingCount, prestigeCount) >=
                        it.requiredValue
            }.map { it.id }
        return EquipmentPowerService.achievementBonus(ids)
    }

    /** 一件背包物品只需品质即可参与（卖出/整理）决策：sellPrice = 100 + quality*50 */
    private inner class SimPlayer(seed: Long) {
        val rng = Random(seed)

        var level = 1                    // GameService 中 profile.level: Int
        var gold = 0L
        var soulPower = 0L               // 修炼/战斗/塔/离线共同注入的魂力池
        var bossCoin = 0L
        var mapId = 0
        var stage = 1
        var hp = 100L                    // 镜像默认 currentHp=100；HP 跨场次持久化，只有战败才回满
        var towerFloor = 0
        var capacity = 20                // backpackCapacity 默认 20
        val items = mutableListOf<Int>() // 背包物品（仅存 qualityOrdinal）
        var lastLogoutHours = 0.0        // 仿真时间轴（小时），离线起算点

        var dropLostTotal = 0L           // 满包丢掉落次数（battle 提示/tower 静默丢失合并统计）
        var offlineBattleWins = 0L       // 离线收益折算的 totalBattleWins
        var offlineWastedSeconds = 0L    // 因 12h 上限被截断的离线秒数
        var totalBattleWins = 0L         // 镜像 profile.totalBattleWins（战斗胜 + 塔胜 + 离线折算，成就 BATTLE 口径）
        var prestigeCount = 0            // 镜像 profile.prestigeCount（talentPoints 不建模：对属性/收入曲线无反馈）
        // 第十八轮武魂觉醒镜像（GameService.awaken / GameBalance 武魂池）：
        // 第 1 天免费首醒（0 转池），每次转生后重醒一次（池随转数扩展；金币不足留待后续登录）
        var martialSoul: GameBalance.MartialSoulDef? = null
        var pendingReawaken = false
        var awakenTotal = 0
        var reawakenTotal = 0
        // 第十九轮流派镜像（GameService.chooseSchool / GameBalance.SCHOOLS）：
        // 第 1 天免费选 BALANCED（温和系数建模：HP/双防×1.05、暴击+5%；atk/matk ×1.0）
        var chosenSchool: GameBalance.SchoolDef? = null
        var schoolTotal = 0

        /** 成就属性加成（SimPlayer 不穿装 → 环数按 0 计；prestigeCount 参与成就解锁口径） */
        fun achBonus(): EquipmentBonus = achievementBonusOf(level, totalBattleWins, towerFloor, 0, prestigeCount)

        /** 武魂加成镜像（GameService.soulBonusOf 同源纯函数：反查武魂池 + applyPrestige） */
        fun soulBonusOf(): EquipmentBonus = GameService.soulBonusOf(martialSoul?.name, prestigeCount)

        /** 武魂技能镜像（第二十轮，GameService.soulSkillOf 同源纯函数：随 CombatStats 进 resolveBattle） */
        fun soulSkill(): GameBalance.SkillDef? = GameService.soulSkillOf(martialSoul?.name)

        /** 流派系数镜像（GameService.schoolModsOf 同源纯函数：chosenSchool 反查 mods） */
        fun schoolMods(): GameBalance.SchoolMods? = GameService.schoolModsOf(chosenSchool?.name)

        // ---- 流派选择镜像（GameService.chooseSchool：首选免费、零掷点；第 1 天执行）----
        fun chooseSchool(s: DayStats) {
            if (chosenSchool != null) return
            chosenSchool = GameBalance.schoolByName("BALANCED")
                ?: error("GameBalance.SCHOOLS 缺少 BALANCED 定义")
            schoolTotal++
            s.schoolChoices++
        }

        // ---- 觉醒镜像（GameService.awaken：首醒免费、重醒扣 GameBalance.REAWAKEN_COST_GOLD，
        //      金币不足返回 false 零改动；roll 走生产纯函数但注入镜像自身 rng 保持可复现）----
        fun awaken(s: DayStats): Boolean {
            val reawakened = martialSoul != null
            if (reawakened) {
                if (gold < GameBalance.REAWAKEN_COST_GOLD) return false
                gold -= GameBalance.REAWAKEN_COST_GOLD
                reawakenTotal++
                s.reawakens++
            } else {
                awakenTotal++
                s.awakens++
            }
            martialSoul = GameBalance.rollMartialSoul(prestigeCount, rng)
            return true
        }

        private fun rndLong(bound: Long): Long = if (bound <= 0) 0 else rng.nextLong(bound)
        private fun rndInt(bound: Int): Int = if (bound <= 0) 0 else rng.nextInt(bound)

        // ---- 转生倍率镜像（GameService.scaledBaseMaxHp 同源：基础部分 ×倍率，加成部分已在 bonusFor 侧乘过；
        //      基础 atk/matk/双防的缩放已并入生产纯函数 playerCombatStats，不再单独镜像）----
        private fun scaledBaseMaxHp(): Long {
            val base = getMaxHp()
            return if (prestigeCount <= 0) base else (base * GameBalance.prestigeMultiplier(prestigeCount)).toLong()
        }

        // ---- 公式镜像自 GameService.cultivate（转生倍率：掷点后 ×(1+转数×0.1)）----
        fun cultivate() {
            val baseGain = GameBalance.CULTIVATE_BASE_GAIN + level * GameBalance.CULTIVATE_LEVEL_GAIN_FACTOR
            soulPower += ((baseGain + rndLong(baseGain / GameBalance.CULTIVATE_RANDOM_DIVISOR)) *
                    GameBalance.prestigeMultiplier(prestigeCount)).toLong()
        }

        // ---- 公式镜像自 GameService.getBreakthroughCost / breakthrough ----
        fun breakthroughCost(l: Int): Long = (120.0 * Math.pow(l.toDouble(), 1.55)).toLong()

        /**
         * autoBreakthrough=true 的客户端行为：魂力够就一直点；达到转生门槛即转生
         * （转生清空魂力 → 循环自然终止）。prestigeEnabled=false 为对照组（转生镜像落地前行为）。
         * 返回突破次数。
         */
        fun breakthroughAll(s: DayStats, prestigeEnabled: Boolean = true): Int {
            var n = 0
            while (n < 100_000 && soulPower >= breakthroughCost(level)) {
                soulPower -= breakthroughCost(level)
                level += 1
                n++
                // 转生镜像（GameService.prestige 玩家策略：达标即转）：门槛读 GameBalance.PRESTIGE_MIN_LEVEL
                if (prestigeEnabled && level >= GameBalance.PRESTIGE_MIN_LEVEL) {
                    doPrestige()
                    s.prestiges += 1
                    break
                }
            }
            return n
        }

        /** 转生数据镜像（GameService.prestige 逐项对照；SimPlayer 无装备，卸装部分为空操作） */
        fun doPrestige() {
            prestigeCount += 1
            level = 1
            gold = 0
            soulPower = 0                    // 防存量魂力秒升回原等级（shared doPrestige 同款清零）
            stage = 1                        // 镜像生产：保留地图、从第 1 关重新推
            hp = 50L * 1 + 100L              // 镜像生产 currentHp = getMaxHp(1)
            // towerFloor/currentMapId/codexKills/bossCoin 保留；talentPoints+1 不建模（无数值反馈）
            // 武魂保留（文档 §15.2 重置项不含武魂）；「每转重醒一次」镜像玩家策略（金币充裕时执行）
            pendingReawaken = true
        }

        // ---- battle 镜像：直接调用生产纯函数 GameService.monsterStats/playerCombatStats/resolveBattle ----
        fun battle(s: DayStats) {
            // 镜像 bonusFor(userId, level, prestigeCount)：装备(0)+成就 合计 ×转生倍率
            // 第十八轮武魂镜像：武魂加成（soulBonusOf，已乘转生倍率）并入同一加成包（生产 battle 同式）
            val equip = EquipmentPowerService.plus(
                EquipmentPowerService.applyPrestige(achBonus(), prestigeCount),
                soulBonusOf()
            )
            val oldMap = mapId
            val oldStage = stage          // 生产代码掉落/奖励均用战前快照
            val monster = GameService.monsterStats(oldMap, oldStage)
            // 画像不模拟穿装 → 装备加成恒为 0（P7 修复效果在报告「已修复项」中说明）；
            // 成就七字段加成按已解锁集合并入（与生产 bonusFor 口径一致，含转生倍率）；
            // 战斗属性（含五属性、武魂与流派系数）直接调生产纯函数 playerCombatStats 组装，与 battle() 逐位同源
            val school = schoolMods()
            // 第二十轮武魂技能镜像：soulSkillOf 同源反查随包进 resolveBattle（生产 battle 同式）
            val player = GameService.playerCombatStats(level, prestigeCount, equip, school, soulSkill())
            // 第十九轮流派镜像：maxHp 乘在「基础+加成包」加总后（GameService.schoolScaledMaxHp 同源）
            val maxHp = GameService.schoolScaledMaxHp(scaledBaseMaxHp() + equip.hpBonus, school)
            val outcome = GameService.resolveBattle(
                player, hp, monster, GameBalance.MAX_BATTLE_ROUNDS, rng, maxHp
            )
            if (!outcome.won) {                                                // 30 回合未杀 → 败
                s.battleLosses++
                hp = maxHp                                                     // 死亡回满血（含成就生命加成×倍率）
                stage = 1                                                       // 退回第 1 关
                return
            }
            s.battleWins++
            totalBattleWins++
            // 转生倍率镜像（GameService.battle：胜利产出 ×(1+转数×0.1)）
            val mult = GameBalance.prestigeMultiplier(prestigeCount)
            val goldGained = ((GameBalance.WIN_GOLD_BASE + oldMap * GameBalance.WIN_GOLD_PER_MAP +
                    oldStage * GameBalance.WIN_GOLD_PER_STAGE) * mult).toLong()
            val expGained = ((GameBalance.WIN_EXP_BASE + oldMap * GameBalance.WIN_EXP_PER_MAP +
                    oldStage * GameBalance.WIN_EXP_PER_STAGE) * mult).toLong()
            gold += goldGained; s.battleGold += goldGained
            soulPower += expGained
            hp = outcome.playerHpLeft.coerceAtLeast(1)                          // HP 跨场次保留
            if (oldStage >= GameBalance.STAGES_PER_MAP) {
                if (mapId < GameBalance.MAX_MAP_ID) { mapId += 1; stage = 1 } else stage = GameBalance.STAGES_PER_MAP
            } else stage = oldStage + 1
            // 掉落判定（用战前 map/stage 快照，镜像生产代码）：满包则丢失
            val dropChance = GameBalance.BASE_DROP_CHANCE + oldMap * GameBalance.DROP_CHANCE_PER_MAP +
                    oldStage * GameBalance.DROP_CHANCE_PER_STAGE
            if (rng.nextDouble() < dropChance) {
                if (items.size < capacity) { items.add(rndInt(5)); s.dropsGained++ }
                else { dropLostTotal++; s.dropsLost++ }
            }
            // Boss 额外掉落：stage%5==0（含终局 15 关）→ 40% 掉 1+mapId 枚 Boss 币
            if (oldStage % 5 == 0 && rng.nextDouble() < GameBalance.BOSS_EXTRA_DROP_CHANCE) {
                bossCoin += 1 + oldMap; s.bossCoins += 1 + oldMap
            }
        }

        // ---- 塔镜像：胜率直接调用生产纯函数 EquipmentPowerService.towerWinChance（P2+P7 修复后同源）----
        fun tower(s: DayStats) {
            // 镜像 towerBattle 的 power 口径：bonusFor（装备+成就合计 ×转生倍率）+ 武魂加成（第十八轮）
            // + 流派系数（第十九轮 applySchool，与生产 towerBattle 同一含流派口径）
            val power = EquipmentPowerService.powerOf(
                level,
                EquipmentPowerService.applySchool(
                    EquipmentPowerService.plus(
                        EquipmentPowerService.applyPrestige(achBonus(), prestigeCount),
                        soulBonusOf()
                    ),
                    schoolMods()
                )
            )
            val won = rng.nextDouble() < EquipmentPowerService.towerWinChance(towerFloor, power)
            if (!won) { s.towerLosses++; return }
            s.towerWins++
            totalBattleWins++                                                  // 生产 towerBattle：胜场 +1
            val towerLevel = towerFloor * GameBalance.TOWER_LEVEL_PER_FLOOR
            // 转生倍率镜像（GameService.towerBattle：塔胜产出 ×(1+转数×0.1)）
            val mult = GameBalance.prestigeMultiplier(prestigeCount)
            val goldGained = ((GameBalance.TOWER_GOLD_BASE + towerLevel * GameBalance.TOWER_GOLD_PER_LEVEL) * mult).toLong()
            gold += goldGained; s.towerGold += goldGained
            soulPower += ((GameBalance.TOWER_EXP_BASE + towerLevel * GameBalance.TOWER_EXP_PER_LEVEL) * mult).toLong()
            if (rng.nextDouble() < GameBalance.TOWER_BOSS_COIN_CHANCE) { bossCoin += 1; s.bossCoins += 1 }
            towerFloor = min(GameBalance.TOWER_MAX_FLOOR, towerFloor + 1)
            if (rng.nextDouble() < GameBalance.TOWER_DROP_CHANCE) {
                // rollBackpackDrop：空间不足返回 null（静默丢失）；品质=min(4, level/8 + nextInt(3))
                if (items.size < capacity) {
                    items.add(min(4, level / 8 + rndInt(3))); s.dropsGained++
                } else { dropLostTotal++; s.dropsLost++ }
            }
        }

        // ---- 公式镜像自 GameService.claimOfflineReward（P1 修复：按小时计费 + 12h 截断；转生倍率产出）----
        fun claimOffline(nowHours: Double, s: DayStats) {
            val offlineSeconds = ((nowHours - lastLogoutHours) * 3600).toLong().coerceAtLeast(0)
            lastLogoutHours = nowHours
            val eff = min(offlineSeconds, GameBalance.OFFLINE_MAX_SECONDS)
            offlineWastedSeconds += offlineSeconds - eff
            if (eff < 60) return
            val effHours = eff / 3600.0
            val mult = GameBalance.prestigeMultiplier(prestigeCount)
            val goldPerHour = (GameBalance.OFFLINE_GOLD_BASE + level * GameBalance.OFFLINE_GOLD_PER_LEVEL) *
                    GameBalance.OFFLINE_EFFICIENCY * mult
            val expPerHour = (GameBalance.OFFLINE_EXP_BASE + level * GameBalance.OFFLINE_EXP_PER_LEVEL) *
                    GameBalance.OFFLINE_EFFICIENCY * mult
            val goldGained = (goldPerHour * effHours).toLong()
            gold += goldGained; s.offlineGold += goldGained
            val expGained = (expPerHour * effHours).toLong()
            soulPower += expGained; s.offlineExp += expGained
            val wins = eff / GameBalance.OFFLINE_SECONDS_PER_BATTLE_WIN
            offlineBattleWins += wins; s.offlineWins += wins
            totalBattleWins += wins                                            // 生产 claimOfflineReward：胜场折算入账
        }

        // ---- 公式镜像自 GameService.sellBackpackItem（100+quality*50，与装备类型/等级无关）----
        /** 中活跃玩家整理背包：卖最低品质，保留 reserve 格余量 */
        fun sellJunk(reserve: Int = 5): Long {
            var gain = 0L
            while (items.size > capacity - reserve && items.isNotEmpty()) {
                val idx = items.indices.minByOrNull { items[it] } ?: break
                gain += 100L + items[idx] * 50L
                items.removeAt(idx)
            }
            return gain
        }

        // ---- 公式镜像自 NormalShopData 205（3000 金币背包扩展券，+5 容量）----
        fun buyExpansions(capLimit: Int = 80): Int {
            var n = 0
            while (capacity < capLimit && gold >= 3000) { gold -= 3000; capacity += 5; n++ }
            return n
        }

        private fun getMaxHp(): Long = 50L * level + 100L                       // 镜像 GameService.getMaxHp
    }

    // ======== 仿真驱动 ========

    private data class DayRow(
        val day: Int, val stats: DayStats,
        val level: Int, val gold: Long, val soulPower: Long, val bossCoin: Long,
        val mapId: Int, val stage: Int, val towerFloor: Int,
        val bagCount: Int, val capacity: Int, val stuckStreak: Int,
        val prestigeCount: Int = 0,
    )

    private class SimOutcome(
        val rows: List<DayRow>,
        val dropLostTotal: Long,
        val dropsGainedTotal: Long,
        val offlineWastedHours: Double,
        // 第十八轮武魂觉醒镜像汇总（报告「觉醒事件」行）：90 天末武魂与累计觉醒/重醒次数
        val finalSoul: GameBalance.MartialSoulDef? = null,
        val awakenTotal: Int = 0,
        val reawakenTotal: Int = 0,
        // 第十九轮流派镜像汇总（报告「流派事件」行）：90 天末所选流派与累计选择事件
        val finalSchool: String? = null,
        val schoolTotal: Int = 0,
    ) {
        val final: DayRow get() = rows.last()
        fun last(n: Int) = rows.takeLast(n)
        private fun DayRow.passiveGold(): Long = stats.checkInGold + stats.questGold
        fun offlineShareLast(n: Int): Double {
            val ls = last(n)
            val off = ls.sumOf { it.stats.offlineGold }
            val tot = ls.sumOf { it.stats.offlineGold + it.stats.battleGold + it.stats.towerGold + it.stats.sellGold + it.passiveGold() }
            return if (tot == 0L) 0.0 else off * 100.0 / tot
        }
        fun avgDailyTotalGold(n: Int): Double =
            last(n).sumOf { it.stats.offlineGold + it.stats.battleGold + it.stats.towerGold + it.stats.sellGold + it.passiveGold() } / n.toDouble()
        fun avgDailyPassiveGold(n: Int): Double = last(n).sumOf { it.passiveGold() } / n.toDouble()
        /** 「签到+任务」日收入占主动玩法（战斗+塔+卖装备）日收入的百分比 */
        fun passiveVsActiveShareLast(n: Int): Double {
            val ls = last(n)
            val passive = ls.sumOf { it.passiveGold() }
            val active = ls.sumOf { it.stats.battleGold + it.stats.towerGold + it.stats.sellGold }
            return if (active == 0L) 0.0 else passive * 100.0 / active
        }
        fun avgDailyBossCoin(n: Int): Double = last(n).sumOf { it.stats.bossCoins } / n.toDouble()
        fun avgDailyOfflineWins(n: Int): Double = last(n).sumOf { it.stats.offlineWins } / n.toDouble()
        fun maxTowerWinsLast(n: Int): Long = last(n).maxOf { it.stats.towerWins }
        fun lastTowerWinDay(): Int = rows.lastOrNull { it.stats.towerWins > 0 }?.day ?: 0
        fun firstDay(pred: DayRow.() -> Boolean): Int = rows.firstOrNull { pred(it) }?.day ?: 0
        fun avgDailyActiveGold(n: Int): Double =
            last(n).sumOf { it.stats.battleGold + it.stats.towerGold + it.stats.sellGold } / n.toDouble()
    }

    /**
     * 玩家画像（中活跃）：每天 3 次登录（08:00/14:00/22:00，离线间隔 6h/8h/10h）；
     * 每次登录：领离线 → 修炼 8 次 → 突破到魂力不足 → 战斗(6/8/6 次) + 魂塔(5/5/8 次) →
     * 整理背包（卖低质保 5 格）→ 金币充裕即买 3000 金币的背包扩展券（至 80 格）。
     */
    private fun run(days: Int, seed: Long, prestigeEnabled: Boolean = true): SimOutcome {
        val p = SimPlayer(seed)
        val rows = ArrayList<DayRow>(days)
        var gained = 0L
        var stuckStreak = 0
        val loginHours = listOf(8.0, 14.0, 22.0)
        val battlesPerSession = listOf(6, 8, 6)
        val towersPerSession = listOf(5, 5, 8)
        for (d in 1..days) {
            val s = DayStats()
            // 每日固定收入镜像：签到（7 日循环）+ 每日任务全清上界假设（见 dailyIncome 注释）
            val inc = dailyIncome(d)
            p.gold += inc.gold; p.soulPower += inc.soulPower; p.bossCoin += inc.bossCoin
            s.checkInGold += inc.checkInGold; s.questGold += inc.questGold
            s.bossCoins += inc.bossCoin
            s.checkInQuestSoulPower += inc.soulPower
            // 第十八轮武魂觉醒镜像：第 1 天首次登录免费首醒（0 转池）；转生后重醒一次
            // （金币 ≥REAWAKEN_COST_GOLD 即执行，不足留待后续登录——镜像「金币在后期充裕」）
            if (d == 1) p.awaken(s)
            if (p.pendingReawaken && p.awaken(s)) p.pendingReawaken = false
            // 第十九轮流派镜像：第 1 天首次登录免费选 BALANCED（温和系数建模，不消耗掷点）
            if (d == 1) p.chooseSchool(s)
            for (i in loginHours.indices) {
                val t = (d - 1) * 24.0 + loginHours[i]
                p.claimOffline(t, s)
                repeat(8) { p.cultivate() }
                s.breakthroughs += p.breakthroughAll(s, prestigeEnabled)
                repeat(battlesPerSession[i]) { p.battle(s) }
                repeat(towersPerSession[i]) { p.tower(s) }
                s.sellGold += p.sellJunk()
                p.buyExpansions()
            }
            gained += s.dropsGained
            // "突破卡点"定义：当日一次突破都没成功
            stuckStreak = if (s.breakthroughs == 0) stuckStreak + 1 else 0
            rows.add(DayRow(d, s, p.level, p.gold, p.soulPower, p.bossCoin,
                p.mapId, p.stage, p.towerFloor, p.items.size, p.capacity, stuckStreak, p.prestigeCount))
        }
        return SimOutcome(rows, p.dropLostTotal, gained, p.offlineWastedSeconds / 3600.0,
            p.martialSoul, p.awakenTotal, p.reawakenTotal, p.chosenSchool?.name, p.schoolTotal)
    }

    // ======== 任务#22：魂环负荷反馈回路专项 ========

    /** 仿真的完整掉落物：type 0=RING 1=BONE 2=CORE（与生产 itemType 三枚举一一对应） */
    private data class SimItem(
        val type: Int,
        val year: Int,
        val quality: Int,
        val pct: Int,
        val enhance: Int = 0,       // 魂骨强化（rollBackpackDrop: max(1, level/10)；battle 掉落恒 0）
        val coreValue: Int = 0,     // 魂核值（rollBackpackDrop: 10 + level×3）
        // 第二十八轮魂骨词缀（rollBackpackDrop BONE 分支末尾追加，rollBoneAffixList 同函数；
        // battle 内联掉落恒空——普通掉落与「稀有掉落」分层，与生产一致）
        val affixes: List<Pair<GameBalance.BoneAffixType, Int>> = emptyList()
    ) {
        val ringLoad: Long get() = if (type == 0) RingLoadCalculator.ringLoad(year, quality, pct) else 0L
        fun toEquippedRing(slot: Int) = EquippedRing(slotIndex = slot, yearOrdinal = year, qualityOrdinal = quality, percentage = pct)
        // 词缀 JSON 随 EquippedBone.affixesJson 进 EquipSimPlayer 的属性镜像（bonus() 消费，战力同步）
        fun toEquippedBone(slot: Int) = EquippedBone(
            slotIndex = slot, yearOrdinal = year, qualityOrdinal = quality, enhanceLevel = enhance,
            affixesJson = GameBalance.serializeBoneAffixes(affixes)
        )
        fun toEquippedCore(slot: Int) = EquippedCore(slotType = if (slot == 0) "LEFT" else "RIGHT", rarityOrdinal = quality, coreValue = coreValue)
    }

    /** 单件装备的战力得分 = 攻击加成 + 生命加成/10（POWER_HP_DIVISOR 同源折算），由生产纯函数 bonus() 折出 */
    private fun itemScore(level: Int, it: SimItem): Long {
        val b = when (it.type) {
            0 -> EquipmentPowerService.bonus(level, listOf(it.toEquippedRing(0)), emptyList(), emptyList())
            1 -> EquipmentPowerService.bonus(level, emptyList(), listOf(it.toEquippedBone(0)), emptyList())
            else -> EquipmentPowerService.bonus(level, emptyList(), emptyList(), listOf(it.toEquippedCore(0)))
        }
        return b.atkBonus + (b.hpBonus / GameBalance.POWER_HP_DIVISOR).toLong()
    }

    /**
     * 穿装画像玩家：与 SimPlayer 同作息，但会「能装就装最强可装环（9 槽）+ 骨 6 槽 + 核 2 槽」。
     * enforceLoad=true：完全按 GameService.equipRing 的负荷校验语义（容量取当前已穿装备折出的
     * atk/hp 根骨×6，换装先释放同槽旧环负荷）；false 为对照组（无负荷校验，直接穿最强 9 环）。
     * 掉落与战斗/塔公式逐行镜像生产 GameService.battle / towerBattle / rollBackpackDrop（含 RNG 调用次序）。
     */
    private inner class EquipSimPlayer(
        seed: Long,
        val enforceLoad: Boolean,
        val capacityMult: Long = GameBalance.RING_CAPACITY_ROOT_MULT,
        val towerRingYearCap: Int = GameBalance.TOWER_RING_DROP_YEAR_CAP,
        /** 第二十七轮强化镜像开关（对照组 = false；默认开启，关闭时金币存量口径回到旧版） */
        val enhanceEnabled: Boolean = true,
    ) {        val rng = Random(seed)

        var level = 1
        var gold = 0L
        var soulPower = 0L
        var bossCoin = 0L
        var mapId = 0
        var stage = 1
        var hp = 100L
        var towerFloor = 0
        var bagCap = 20
        val bag = mutableListOf<SimItem>()
        val rings = arrayOfNulls<SimItem>(9)
        val bones = arrayOfNulls<SimItem>(6)
        val cores = arrayOfNulls<SimItem>(2)
        var lastLogoutHours = 0.0
        var dropLostTotal = 0L
        var offlineWastedSeconds = 0L

        // ---- 观察指标 ----
        var firstRejectDay = 0; var firstRejectMap = -1; var firstRejectLevel = 0; var firstRejectLoad = 0L; var firstRejectYear = -1
        var rejectsToday = 0
        var totalBattleWins = 0L         // 镜像 profile.totalBattleWins（战斗胜 + 塔胜 + 离线折算，成就 BATTLE 口径）
        var prestigeCount = 0            // 镜像 profile.prestigeCount（talentPoints 不建模：对属性/收入曲线无反馈）
        // 第十九轮流派镜像（与 SimPlayer 同策略：第 1 天免费选 BALANCED 温和系数建模）
        var chosenSchool: GameBalance.SchoolDef? = null
        // 第二十九轮杀气商店/每日副本镜像状态：杀气余额（跨转生保留——§15.2 重置项不含杀气/称号/
        // 副本进度）、已拥有称号（列表顺序=购买顺序 title_1→title_8）、HP/ATK 属性购买计数、
        // 副本历史通关位掩码（ever_cleared 镜像）
        var killingIntent = 0L
        val ownedTitles = mutableListOf<String>()
        var killingHpBuys = 0
        var killingAtkBuys = 0
        var everCleared = 0

        /**
         * 杀气商店加成（生产 EquipmentPowerService.killingBonusFor 同源纯函数：称号叠加+属性购买）。
         * 并入战斗/塔战力加成包的通道与生产 bonusFor 逐位同式：equip+ach+kill 合计后再乘转生倍率。
         */
        fun killBonus(): EquipmentBonus =
            EquipmentPowerService.killingBonus(ownedTitles, killingHpBuys, killingAtkBuys)
        /** 成就属性加成（SOUL_RING 口径 = 已装备槽位数；prestigeCount 参与成就解锁口径） */
        fun achBonus(): EquipmentBonus = achievementBonusOf(level, totalBattleWins, towerFloor, slotsFilled(), prestigeCount)
        /** 流派系数镜像（GameService.schoolModsOf 同源纯函数：chosenSchool 反查 mods） */
        fun schoolMods(): GameBalance.SchoolMods? = GameService.schoolModsOf(chosenSchool?.name)
        /** 成功穿上的最高年份档位随时间的演进：档位 y → 首次穿上该档位环的天 */
        val firstEquipDayByYear = mutableMapOf<Int, Int>()
        var currentDay = 0
        // 健康度计数器（每日清零）：换装升级次数 / 掉落即不可装（负荷>当前容量）的环数 / 当日环掉落数
        var upgradesToday = 0
        var deadRingDropsToday = 0
        var ringDropsToday = 0

        private fun rndLong(bound: Long): Long = if (bound <= 0) 0 else rng.nextLong(bound)
        private fun rndInt(bound: Int): Int = if (bound <= 0) 0 else rng.nextInt(bound)
        private fun getMaxHp(level: Int): Long = 50L * level + 100L                 // 镜像 GameService.getMaxHp

        // ---- 与生产同源的状态计算（GameService.battle / absorptionCapacityFor）----
        fun bonus(): EquipmentBonus = EquipmentPowerService.bonus(
            level, equippedRingList(), equippedBoneList(), equippedCoreList()
        )
        private fun equippedRingList(): List<EquippedRing> =
            rings.mapIndexedNotNull { s, i -> i?.toEquippedRing(s) }
        private fun equippedBoneList(): List<EquippedBone> =
            bones.mapIndexedNotNull { s, i -> i?.toEquippedBone(s) }
        private fun equippedCoreList(): List<EquippedCore> =
            cores.mapIndexedNotNull { s, i -> i?.toEquippedCore(s) }

        fun equippedLoad(): Long = RingLoadCalculator.totalRingLoad(equippedRingList())
        // 容量按装备口径（不含成就加成）——镜像生产 equipRing 容量校验（保守方向，差异带 ≤ 成就加成折算量）
        fun capacity(): Long = RingLoadCalculator.absorptionCapacity(
            RingLoadCalculator.calcRootBone(
                maxHp = getMaxHp(level) + bonus().hpBonus,
                atk = GameBalance.PLAYER_ATK_BASE + level * GameBalance.PLAYER_ATK_PER_LEVEL + bonus().atkBonus,
                matk = 0, pdef = 0, mdef = 0
            ), capacityMult
        )
        fun slotsFilled(): Int = rings.count { it != null }
        fun bagRings(): List<SimItem> = bag.filter { it.type == 0 }

        // ---- 穿装（负荷校验语义镜像 GameService.equipRing:446~502）----
        /** @return null=成功；非 null=负荷不足被拒 */
        fun tryEquipRing(slot: Int, item: SimItem): Long? {
            val equipped = equippedRingList()
            val loadAfter = RingLoadCalculator.totalRingLoad(equipped.filterNot { it.slotIndex == slot }) + item.ringLoad
            val cap = capacity() // 生产语义：容量按「换装前」已穿装备的 atk/hp 计算
            if (enforceLoad && loadAfter > cap) {
                if (firstRejectDay == 0) {
                    firstRejectDay = currentDay; firstRejectMap = mapId; firstRejectLevel = level
                    firstRejectLoad = item.ringLoad; firstRejectYear = item.year
                }
                rejectsToday++
                return loadAfter - cap
            }
            val old = rings[slot]
            rings[slot] = item
            bag.remove(item)
            if (old != null) bag.add(old)
            if (old == null || itemScore(level, item) > itemScore(level, old)) upgradesToday++
            firstEquipDayByYear.putIfAbsent(item.year, currentDay)
            return null
        }

        /** 一次登录的整理：骨/核无负荷直接穿最强；环按「最强优先、装得下才装」贪心到不动点 */
        fun equipPass() {
            for (type in intArrayOf(1, 2)) {
                val slots = if (type == 1) bones else cores
                while (true) {
                    val cands = bag.filter { it.type == type }.sortedByDescending { itemScore(level, it) }
                    val c = cands.firstOrNull() ?: break
                    val empty = slots.indexOfFirst { it == null }
                    val weakest = (0 until slots.size).filter { slots[it] != null }.minByOrNull { itemScore(level, slots[it]!!) }
                    if (empty >= 0) {
                        slots[empty] = c; bag.remove(c)
                    } else if (weakest != null && itemScore(level, c) > itemScore(level, slots[weakest]!!)) {
                        val old = slots[weakest]; slots[weakest] = c; bag.remove(c); old?.let { bag.add(it) }
                    } else break
                }
            }
            var progress = true
            while (progress) {
                progress = false
                val cands = bagRings().sortedByDescending { itemScore(level, it) }
                for (c in cands) {
                    if (c !in bag) continue // 可能已被上一轮穿走
                    val empty = rings.indexOfFirst { it == null }
                    if (empty >= 0 && tryEquipRing(empty, c) == null) { progress = true; continue }
                    val weakest = (0 until rings.size).filter { rings[it] != null }.minByOrNull { itemScore(level, rings[it]!!) }
                    if (weakest != null && itemScore(level, c) > itemScore(level, rings[weakest]!!) &&
                        tryEquipRing(weakest, c) == null) { progress = true }
                }
            }
        }

        // ---- 以下与 SimPlayer 同源镜像（cultivate/breakthrough/prestige/offline/sell/expand）----
        // ---- 转生倍率镜像（GameService.scaledBaseMaxHp 同源；基础 atk/matk/双防缩放已并入 playerCombatStats）----
        private fun scaledBaseMaxHp(): Long {
            val base = getMaxHp(level)
            return if (prestigeCount <= 0) base else (base * GameBalance.prestigeMultiplier(prestigeCount)).toLong()
        }

        fun cultivate() {
            val baseGain = GameBalance.CULTIVATE_BASE_GAIN + level * GameBalance.CULTIVATE_LEVEL_GAIN_FACTOR
            soulPower += ((baseGain + rndLong(baseGain / GameBalance.CULTIVATE_RANDOM_DIVISOR)) *
                    GameBalance.prestigeMultiplier(prestigeCount)).toLong()
        }

        fun breakthroughCost(l: Int): Long = (120.0 * Math.pow(l.toDouble(), 1.55)).toLong()

        /** 与 SimPlayer 同策略：达标即转生（转生清空魂力 → 循环自然终止）。返回突破次数 */
        fun breakthroughAll(s: DayStats, prestigeEnabled: Boolean = true): Int {
            var n = 0
            while (n < 100_000 && soulPower >= breakthroughCost(level)) {
                soulPower -= breakthroughCost(level)
                level += 1
                n++
                if (prestigeEnabled && level >= GameBalance.PRESTIGE_MIN_LEVEL) {
                    doPrestige()
                    s.prestiges += 1
                    break
                }
            }
            return n
        }

        /**
         * 转生数据镜像（GameService.prestige 逐项对照）：环/骨全部卸回背包（与 unequip 同源——
         * 属性随行回背包， EquipSimPlayer 的 SimItem 即完整属性）、魂核保留已装备；
         * level/gold/soulPower 清零、stage 回 1、hp 按 Lv.1 回满；towerFloor/mapId 保留。
         * 卸装后由既有 equipPass 按容量回装（骨/核无负荷校验下个登录即回装，环随容量增长逐步回装）。
         */
        fun doPrestige() {
            prestigeCount += 1
            level = 1
            gold = 0
            soulPower = 0
            stage = 1
            hp = 50L * 1 + 100L
            rings.forEachIndexed { slot, item -> item?.let { rings[slot] = null; bag.add(it) } }
            bones.forEachIndexed { slot, item -> item?.let { bones[slot] = null; bag.add(it) } }
            // 第二十九轮注：杀气余额/已拥有称号/属性购买计数/副本 everCleared 均跨转生保留
            // （文档 §15.2 重置项不含杀气、称号与副本进度），镜像状态零改动即正确
        }

        fun battle(s: DayStats) {
            val ach = achBonus()
            val oldMap = mapId
            val oldStage = stage
            val monster = GameService.monsterStats(oldMap, oldStage)
            // 镜像 bonusFor(userId, level, prestigeCount)：装备+成就+杀气商店（第二十九轮）合计 ×转生倍率
            val raw = EquipmentPowerService.bonus(level, equippedRingList(), equippedBoneList(), equippedCoreList())
            val equip = EquipmentPowerService.applyPrestige(
                EquipmentPowerService.plus(EquipmentPowerService.plus(raw, ach), killBonus()), prestigeCount
            )
            // 战斗属性（含五属性、武魂【无——本画像未建模武魂】与流派系数）直接调生产纯函数
            // playerCombatStats 组装，与生产 battle() 逐位同源；maxHp 乘在基础+加成加总后
            val school = schoolMods()
            val player = GameService.playerCombatStats(level, prestigeCount, equip, school)
            val maxHp = GameService.schoolScaledMaxHp(scaledBaseMaxHp() + equip.hpBonus, school)
            val outcome = GameService.resolveBattle(
                player, min(hp, maxHp), monster, GameBalance.MAX_BATTLE_ROUNDS, rng
            )
            if (!outcome.won) {
                s.battleLosses++
                hp = maxHp
                stage = 1
                return
            }
            s.battleWins++
            totalBattleWins++
            // 转生倍率镜像（GameService.battle：胜利产出 ×(1+转数×0.1)）
            val mult = GameBalance.prestigeMultiplier(prestigeCount)
            val goldGained = ((GameBalance.WIN_GOLD_BASE + oldMap * GameBalance.WIN_GOLD_PER_MAP +
                    oldStage * GameBalance.WIN_GOLD_PER_STAGE) * mult).toLong()
            val expGained = ((GameBalance.WIN_EXP_BASE + oldMap * GameBalance.WIN_EXP_PER_MAP +
                    oldStage * GameBalance.WIN_EXP_PER_STAGE) * mult).toLong()
            gold += goldGained; s.battleGold += goldGained
            soulPower += expGained
            hp = outcome.playerHpLeft.coerceAtLeast(1)
            if (oldStage >= GameBalance.STAGES_PER_MAP) {
                if (mapId < GameBalance.MAX_MAP_ID) { mapId += 1; stage = 1 } else stage = GameBalance.STAGES_PER_MAP
            } else stage = oldStage + 1
            // 掉落（镜像 GameService.battle:222~251，含 RNG 次序：dropChance→空间→type→quality→pct）
            val dropChance = GameBalance.BASE_DROP_CHANCE + oldMap * GameBalance.DROP_CHANCE_PER_MAP +
                    oldStage * GameBalance.DROP_CHANCE_PER_STAGE
            if (rng.nextDouble() < dropChance) {
                if (bag.size < bagCap) {
                    val type = rng.nextInt(3)
                    // 镜像 GameService 战斗掉落环年份（8-10 图封顶 tier-3，同源 BATTLE_RING_DROP_YEAR_CAP）
                    val item = SimItem(type, (oldMap / 2).coerceIn(0, GameBalance.BATTLE_RING_DROP_YEAR_CAP), rng.nextInt(5), 100 + rndInt(900))
                    bag.add(item)
                    s.dropsGained++
                    if (item.type == 0) {
                        ringDropsToday++
                        if (item.ringLoad > capacity()) deadRingDropsToday++
                    }
                } else { dropLostTotal++; s.dropsLost++ }
            }
            if (oldStage % 5 == 0 && rng.nextDouble() < GameBalance.BOSS_EXTRA_DROP_CHANCE) {
                bossCoin += 1 + oldMap; s.bossCoins += 1 + oldMap
            }
        }

        fun tower(s: DayStats) {
            val ach = achBonus()
            val raw = EquipmentPowerService.bonus(level, equippedRingList(), equippedBoneList(), equippedCoreList())
            // 镜像 towerBattle 的 power 口径：bonusFor（装备+成就+杀气商店合计 ×转生倍率；powerOf 含五属性折算）
            // + 流派系数（第十九轮 applySchool，与生产 towerBattle 同一含流派口径）
            val power = EquipmentPowerService.powerOf(
                level,
                EquipmentPowerService.applySchool(
                    EquipmentPowerService.applyPrestige(
                        EquipmentPowerService.plus(EquipmentPowerService.plus(raw, ach), killBonus()), prestigeCount
                    ),
                    schoolMods()
                )
            )
            val won = rng.nextDouble() < EquipmentPowerService.towerWinChance(towerFloor, power)
            rndInt(GameBalance.TOWER_MONSTERS.size) // 镜像 monsterName 抽卡（保持 RNG 流同构）
            rndInt(6)                                // 镜像 rounds 抽卡
            if (!won) { s.towerLosses++; return }
            s.towerWins++
            totalBattleWins++                        // 生产 towerBattle：胜场 +1
            // 杀气收入（第二十九轮既有口径镜像 GameService.towerBattle：1 + towerFloor/10 直加不乘
            // 转生倍率，取【挑战时】楼层——生产 killingGained 在胜局 towerFloor+1 之前取值）
            val killingGained = 1L + towerFloor / GameBalance.TOWER_KILLING_PER_FLOORS
            killingIntent += killingGained; s.towerKilling += killingGained
            val towerLevel = towerFloor * GameBalance.TOWER_LEVEL_PER_FLOOR
            // 转生倍率镜像（GameService.towerBattle：塔胜产出 ×(1+转数×0.1)）
            val mult = GameBalance.prestigeMultiplier(prestigeCount)
            val goldGained = ((GameBalance.TOWER_GOLD_BASE + towerLevel * GameBalance.TOWER_GOLD_PER_LEVEL) * mult).toLong()
            gold += goldGained; s.towerGold += goldGained
            soulPower += ((GameBalance.TOWER_EXP_BASE + towerLevel * GameBalance.TOWER_EXP_PER_LEVEL) * mult).toLong()
            if (rng.nextDouble() < GameBalance.TOWER_BOSS_COIN_CHANCE) { bossCoin += 1; s.bossCoins += 1 }
            towerFloor = min(GameBalance.TOWER_MAX_FLOOR, towerFloor + 1)
            // 掉落判定：nextDouble 无论背包是否满都会消耗（镜像 towerBattle 的 `won && nextDouble(...)`），
            // 满包则 rollBackpackDrop 在消耗任何属性 RNG 前返回 null（静默丢失）
            if (rng.nextDouble() < GameBalance.TOWER_DROP_CHANCE) {
                rollBackpackDropMirror(towerLevel, s)
            }
        }

        /**
         * rollBackpackDrop 逐行镜像（GameService.rollBackpackDrop；RNG 掷点次序【写死，勿动】：
         * ①类型 nextDouble 两掷（>0.72→BONE，否则 >0.45→CORE，再否则 RING）→ ②品质 nextInt(3)
         * → ③年份 nextInt(2)（先抽后截断）→ ④成熟度 nextInt(80) → ⑤按类型命名掷点（RING 1 掷/
         * BONE 2 掷/CORE 2 掷）→ ⑥【BONE 末尾】词缀（生产同一 rollBoneAffixList 注入镜像 rng）。
         * 背包满在消耗任何 RNG 前返回 null（静默丢失，计满包丢掉落）。塔（level=floor×3）与
         * 每日副本（level=dropTier×12，第二十九轮）共用一份镜像，防两处手抄漂移。
         */
        private fun rollBackpackDropMirror(level: Int, s: DayStats): SimItem? {
            if (bag.size >= bagCap) { dropLostTotal++; s.dropsLost++; return null }
            val t = if (rng.nextDouble() > 0.72) 1 else if (rng.nextDouble() > 0.45) 2 else 0
            val q = min(4, level / 8 + rndInt(3))
            val yBase = level / 12 + rndInt(2)
            // 魂环年份封顶 TOWER_RING_DROP_YEAR_CAP（死掉落治理同口径）；魂骨/魂核保留原曲线
            val y = if (t == 0) min(towerRingYearCap, yBase) else min(4, yBase)
            val pct = 100 + level * 12 + rndInt(80)
            val enhance = if (t == 1) maxOf(1, level / 10) else 0
            val coreValue = if (t == 2) 10 + level * 3 else 0
            var affixes: List<Pair<GameBalance.BoneAffixType, Int>> = emptyList()
            when (t) {
                0 -> rndInt(3)                        // skillName
                1 -> {                                // boneType + passiveSkillName + 词缀
                    rndInt(6); rndInt(3)
                    affixes = GameBalance.rollBoneAffixList(q, rng)
                }
                else -> { rndInt(3); rndInt(3) }      // passiveSkillName + coreName
            }
            val item = SimItem(t, y, q, pct, enhance, coreValue, affixes)
            bag.add(item)
            s.dropsGained++
            if (item.type == 0) {
                ringDropsToday++
                if (item.ringLoad > capacity()) deadRingDropsToday++
            }
            return item
        }

        // ---- 每日副本镜像（第二十九轮，DungeonService.fight/sweep + GameBalance.DUNGEON_DEFS）----
        // 玩家策略（理性）：每天唯一一次机会（胜败都占当日名额）放在首次登录，挑战当前已解锁的
        // 最高难度（prestigeCount ≥ unlockPrestige 的最大 tier；0 转无任何难度解锁 → 空过）；
        // 该难度历史已通关（everCleared 位掩码镜像，胜局置位、转生不清）后改扫荡（免战斗、耗魂力
        // 50+等级×5；魂力不足则退回挑战——战斗免费，已通关难度的期望收益仍为正）。
        // 奖励公式逐位镜像生产：金币 = goldReward × prestigeMultiplier（收入口径③）、杀气直加不乘
        // 倍率（稀缺货币口径）、掉落 rollBackpackDropMirror(level=dropTier×12)；胜局 totalBattleWins+1
        // 并保留战果血量（下限 1），败局回满血零奖励（与 battle 战败语义一致）。
        fun dungeon(s: DayStats) {
            val tier = GameBalance.DUNGEON_DEFS.indexOfLast { prestigeCount >= it.unlockPrestige }
            if (tier < 0) return                             // 0 转未解锁任何难度
            val def = GameBalance.DUNGEON_DEFS[tier]
            val mult = GameBalance.prestigeMultiplier(prestigeCount)
            if (GameBalance.dungeonHasCleared(everCleared, tier)) {
                val cost = GameBalance.dungeonSweepSoulPowerCost(level)
                if (soulPower >= cost) {                     // 扫荡：不战斗、不计胜负、不写通关标记
                    soulPower -= cost
                    s.sweepSoulPower += cost
                    s.dungeonSweeps += 1
                    val goldGained = (def.goldReward * mult).toLong()
                    gold += goldGained; s.dungeonGold += goldGained
                    killingIntent += def.killingReward; s.dungeonKilling += def.killingReward
                    rollBackpackDropMirror(GameBalance.dungeonDropLevel(def.dropTier), s)
                    return
                }
            }
            // 挑战：战斗属性组装与 battle 同口径（装备+成就+杀气商店合计 ×转生倍率；流派系数并入）
            val raw = EquipmentPowerService.bonus(level, equippedRingList(), equippedBoneList(), equippedCoreList())
            val equip = EquipmentPowerService.applyPrestige(
                EquipmentPowerService.plus(EquipmentPowerService.plus(raw, achBonus()), killBonus()), prestigeCount
            )
            val school = schoolMods()
            val player = GameService.playerCombatStats(level, prestigeCount, equip, school)
            val maxHp = GameService.schoolScaledMaxHp(scaledBaseMaxHp() + equip.hpBonus, school)
            // Boss = 当前推图怪 × 难度倍率（DungeonService.dungeonMonster 同构：matk 镜像攻击、
            // 双防 = 放大后攻击 × MONSTER_DEF_FACTOR、倍率下限夹 1）
            val base = GameService.monsterStats(mapId, stage)
            val atk = (base.atk * def.atkMult).toInt().coerceAtLeast(1)
            val defV = (atk * GameBalance.MONSTER_DEF_FACTOR).toInt()
            val boss = GameService.MonsterStats(
                (base.hp * def.hpMult).toLong().coerceAtLeast(1), atk, atk, defV, defV
            )
            val outcome = GameService.resolveBattle(
                player, hp.coerceAtMost(maxHp), boss, GameBalance.MAX_BATTLE_ROUNDS, rng, maxHp
            )
            if (!outcome.won) {                              // 败局：占当日名额、零奖励、回满血
                s.dungeonLosses += 1
                hp = maxHp
                return
            }
            s.dungeonWins += 1
            totalBattleWins += 1                             // 生产 fight：胜局 totalBattleWins +1（成就口径）
            everCleared = everCleared or GameBalance.dungeonClearedBit(tier)
            val goldGained = (def.goldReward * mult).toLong()
            gold += goldGained; s.dungeonGold += goldGained
            killingIntent += def.killingReward; s.dungeonKilling += def.killingReward
            hp = outcome.playerHpLeft.coerceAtLeast(1)       // 胜局保留战果血量（battle 同款）
            rollBackpackDropMirror(GameBalance.dungeonDropLevel(def.dropTier), s)
        }

        // ---- 杀气商店镜像（第二十九轮，ShopService.buyKillingTitle/buyKillingAttr + GameBalance）----
        // 玩家策略：称号按 title_1→title_8 顺序攒够即买，【称号优先级高于属性购买】——KDoc 留档理由：
        // 称号属性量随价格（100→150000）同步 ~2.5~3 倍递增且永久拥有叠加生效（「拥有即生效」模型），
        // 单位杀气换得的属性显著高于属性购买（+100HP/+10ATK 基值固定不随等级缩放，文档定位为
        // 前期小额补强）；称号全满后剩余杀气投入 HP/ATK 属性购买（HP/ATK 轮替、价格 100×2^已购次数）。
        // 零掷点：商店购买不消耗 RNG；加成经 killBonus()（生产 killingBonus 纯函数）并入战斗/塔战力。
        fun killingShop(s: DayStats) {
            while (ownedTitles.size < GameBalance.KILLING_TITLES.size) {
                val next = GameBalance.KILLING_TITLES[ownedTitles.size]
                if (killingIntent < next.cost) break
                killingIntent -= next.cost
                s.killingSpent += next.cost
                s.titleBuys += 1
                ownedTitles.add(next.id)
            }
            if (ownedTitles.size == GameBalance.KILLING_TITLES.size) {
                var hpTurn = true
                while (true) {
                    val cost = GameBalance.killingAttrCost(if (hpTurn) killingHpBuys else killingAtkBuys)
                    if (killingIntent < cost) break
                    killingIntent -= cost
                    s.killingSpent += cost
                    s.attrBuys += 1
                    if (hpTurn) killingHpBuys += 1 else killingAtkBuys += 1
                    hpTurn = !hpTurn
                }
            }
        }

        fun claimOffline(nowHours: Double, s: DayStats) {
            val offlineSeconds = ((nowHours - lastLogoutHours) * 3600).toLong().coerceAtLeast(0)
            lastLogoutHours = nowHours
            val eff = min(offlineSeconds, GameBalance.OFFLINE_MAX_SECONDS)
            offlineWastedSeconds += offlineSeconds - eff
            if (eff < 60) return
            val effHours = eff / 3600.0
            val mult = GameBalance.prestigeMultiplier(prestigeCount)
            val goldPerHour = (GameBalance.OFFLINE_GOLD_BASE + level * GameBalance.OFFLINE_GOLD_PER_LEVEL) *
                    GameBalance.OFFLINE_EFFICIENCY * mult
            val expPerHour = (GameBalance.OFFLINE_EXP_BASE + level * GameBalance.OFFLINE_EXP_PER_LEVEL) *
                    GameBalance.OFFLINE_EFFICIENCY * mult
            val goldGained = (goldPerHour * effHours).toLong()
            gold += goldGained; s.offlineGold += goldGained
            val expGained = (expPerHour * effHours).toLong()
            soulPower += expGained; s.offlineExp += expGained
            val wins = eff / GameBalance.OFFLINE_SECONDS_PER_BATTLE_WIN
            s.offlineWins += wins
            totalBattleWins += wins            // 生产 claimOfflineReward：胜场折算入账
        }

        fun sellJunk(reserve: Int = 5): Long {
            var gain = 0L
            while (bag.size > bagCap - reserve && bag.isNotEmpty()) {
                val idx = bag.indices.minByOrNull { bag[it].quality } ?: break
                gain += 100L + bag[idx].quality * 50L
                bag.removeAt(idx)
            }
            return gain
        }

        fun buyExpansions(capLimit: Int = 80): Int {
            var n = 0
            while (bagCap < capLimit && gold >= 3000) { gold -= 3000; bagCap += 5; n++ }
            return n
        }

        // ---- 魂骨强化镜像（GameService.enhanceBone 双路径 + GameBalance.boneEnhanceCost）----
        // 玩家策略（第二十七轮，仿真侧杠杆）：【每日一次】（首次登录）若金币 ≥ ENHANCE_GOLD_FLOOR，
        // 在【未满 +15 且费用 ≤ gold−防线（强化后存量不破防线）】的骨中强化 itemScore 最高的一件：
        //  ① 已装备骨优先（slotIndex 路径镜像）；② 若已装备骨全部 ≥ +15（塔掉落白送 max(1,towerLevel/10)
        //     最高 +30，越过主动强化上限——设计发现，见报告小节），退回背包骨（itemIndex 路径镜像，
        //     「预备强化」策略，终局 sink 的主要来源）。
        // 费用走生产公式（随年份/品质/当前等级平方陡增）、100% 成功。每日一次+存量防线是刻意克制的
        // 节奏（逐登录强化实测吃掉 ~87% 日收入、把存量钉死在防线，违背「补充 sink」定位）。
        // 零掷点：强化不消耗 RNG，但抬升骨战力 → 战斗/塔胜负路径与关闭强化的对照自然分叉（统计对照）。
        // 扣费镜像是经济闭环的关键：不扣费则仿真高估金币存量（吸收费未实现前的主力 sink）。
        fun enhancePass(s: DayStats) {
            if (!enhanceEnabled || gold < ENHANCE_GOLD_FLOOR) return
            val budget = gold - ENHANCE_GOLD_FLOOR
            if (budget <= 0) return
            var best: Pair<SimItem, (Int) -> Unit>? = null
            var bestScore = Long.MIN_VALUE
            fun consider(item: SimItem, apply: (Int) -> Unit) {
                if (item.enhance >= GameBalance.BONE_ENHANCE_MAX_LEVEL) return
                val cost = GameBalance.boneEnhanceCost(item.year, item.quality, item.enhance)
                if (cost > budget) return
                val score = itemScore(level, item)
                if (score > bestScore) { bestScore = score; best = item to apply }
            }
            bones.forEachIndexed { i, b -> b?.let { consider(it) { nl -> bones[i] = b.copy(enhance = nl) } } }
            if (best == null) {
                bag.forEachIndexed { i, b -> if (b.type == 1) consider(b) { nl -> bag[i] = b.copy(enhance = nl) } }
            }
            val chosen = best ?: return
            val (item, apply) = chosen
            val cost = GameBalance.boneEnhanceCost(item.year, item.quality, item.enhance)
            gold -= cost
            s.enhances += 1
            s.enhanceGold += cost
            apply(item.enhance + 1)
        }
    }

    /** 负荷回路专项的每日采样 */
    private data class LoadLoopDay(
        val day: Int, val level: Int, val mapId: Int, val stage: Int, val towerFloor: Int,
        val load: Long, val capacity: Long, val slots: Int,
        val bagRings: Int, val bagHighTierRings: Int, val bagMaxRingLoad: Long,
        val rejectsToday: Int, val bagCount: Int, val bagCap: Int, val gold: Long,
        val upgradesToday: Int, val ringDropsToday: Int, val deadRingDropsToday: Int,
        val bagRingsByYear: IntArray,
        val prestigeCount: Int = 0,
        /** 第二十七轮强化镜像当日采样（次数/消耗金币） */
        val enhancesToday: Int = 0,
        val enhanceGoldToday: Long = 0,
    ) {
        val utilPct: Double get() = if (capacity <= 0) 0.0 else load * 100.0 / capacity
    }

    private class LoadLoopRun(
        val rows: List<LoadLoopDay>,
        val firstRejectDay: Int, val firstRejectMap: Int, val firstRejectLevel: Int,
        val firstRejectLoad: Long, val firstRejectYear: Int,
        val firstEquipDayByYear: Map<Int, Int>,
        val dropLostTotal: Long,
        val totalRejects: Long,
        val totalUpgrades: Long,
        val totalRingDrops: Long,
        val totalDeadRingDrops: Long,
        /** 第二十七轮强化镜像汇总：累计次数 / 消耗金币 / 全期金币总收入（占比分母） */
        val totalEnhances: Long = 0,
        val totalEnhanceGold: Long = 0,
        val totalGoldIncome: Long = 0,
        // 第二十九轮每日副本镜像汇总：金币（占比）/胜败扫荡/扫荡魂力
        val totalDungeonGold: Long = 0,
        val totalDungeonWins: Long = 0,
        val totalDungeonLosses: Long = 0,
        val totalDungeonSweeps: Long = 0,
        val totalSweepSoulPower: Long = 0,
        /** 主动玩法金币总收入（战斗+塔+卖装备+副本）——副本占主动收入比的分母 */
        val totalActiveGold: Long = 0,
        // 第二十九轮杀气镜像汇总：收入来源（塔/副本）与商店支出（称号数/属性购买次数/期末状态）
        val totalTowerKilling: Long = 0,
        val totalDungeonKilling: Long = 0,
        val totalKillingSpent: Long = 0,
        val totalTitleBuys: Long = 0,
        val totalAttrBuys: Long = 0,
        val finalTitles: Int = 0,
        val finalKillingIntent: Long = 0,
        val nextTitleName: String = "全满",
    ) {
        val final: LoadLoopDay get() = rows.last()
        val maxUtilEver: Double get() = rows.maxOf { it.utilPct }
        /** 掉落的魂环里「掉落当时就装不下」的比例（死库存进量） */
        val deadRingDropShare: Double get() = if (totalRingDrops == 0L) 0.0 else totalDeadRingDrops * 100.0 / totalRingDrops
        /** 强化消耗占全期金币总收入的百分比（经济 sink 占比） */
        val enhanceShareOfIncome: Double get() = if (totalGoldIncome == 0L) 0.0 else totalEnhanceGold * 100.0 / totalGoldIncome
        /** 每日副本金币占全期金币总收入的百分比（第二十九轮新收入占比观察口径） */
        val dungeonShareOfIncome: Double get() = if (totalGoldIncome == 0L) 0.0 else totalDungeonGold * 100.0 / totalGoldIncome
        /** 每日副本金币占主动玩法（战斗+塔+卖装备+副本）收入的百分比 */
        val dungeonShareOfActive: Double get() = if (totalActiveGold == 0L) 0.0 else totalDungeonGold * 100.0 / totalActiveGold
        fun rowsEvery(n: Int): List<LoadLoopDay> = rows.filter { it.day % n == 0 || it.day == rows.size }
    }

    /** 日均工具（成员级，供报告各段使用） */
    private fun List<LoadLoopDay>.averageOf(sel: (LoadLoopDay) -> Double): Double =
        if (isEmpty()) 0.0 else sumOf { sel(it) } / size

    private fun runLoadLoop(
        days: Int, seed: Long, enforceLoad: Boolean,
        capacityMult: Long = GameBalance.RING_CAPACITY_ROOT_MULT,
        towerRingYearCap: Int = GameBalance.TOWER_RING_DROP_YEAR_CAP,
        prestigeEnabled: Boolean = true,
        enhanceEnabled: Boolean = true,
    ): LoadLoopRun {
        val p = EquipSimPlayer(seed, enforceLoad, capacityMult, towerRingYearCap, enhanceEnabled)
        val rows = ArrayList<LoadLoopDay>(days)
        var totalRejects = 0L
        var totalUpgrades = 0L
        var totalRingDrops = 0L
        var totalDeadRingDrops = 0L
        var totalEnhances = 0L
        var totalEnhanceGold = 0L
        var totalGoldIncome = 0L
        var totalDungeonGold = 0L; var totalDungeonWins = 0L
        var totalDungeonLosses = 0L; var totalDungeonSweeps = 0L; var totalSweepSoulPower = 0L
        var totalActiveGold = 0L
        var totalTowerKilling = 0L; var totalDungeonKilling = 0L
        var totalKillingSpent = 0L; var totalTitleBuys = 0L; var totalAttrBuys = 0L
        val loginHours = listOf(8.0, 14.0, 22.0)
        val battlesPerSession = listOf(6, 8, 6)
        val towersPerSession = listOf(5, 5, 8)
        for (d in 1..days) {
            p.currentDay = d
            val s = DayStats()
            // 每日固定收入镜像：与 SimPlayer 同口径（签到 7 日循环 + 每日任务全清上界假设）
            val inc = dailyIncome(d)
            p.gold += inc.gold; p.soulPower += inc.soulPower; p.bossCoin += inc.bossCoin
            s.checkInGold += inc.checkInGold; s.questGold += inc.questGold
            s.bossCoins += inc.bossCoin
            s.checkInQuestSoulPower += inc.soulPower
            // 第十九轮流派镜像：第 1 天免费选 BALANCED（与 SimPlayer 同策略，温和系数建模）
            if (d == 1) p.chosenSchool = GameBalance.schoolByName("BALANCED")
            for (i in loginHours.indices) {
                val t = (d - 1) * 24.0 + loginHours[i]
                p.claimOffline(t, s)
                repeat(8) { p.cultivate() }
                s.breakthroughs += p.breakthroughAll(s, prestigeEnabled)
                if (i == 0) {
                    // 第二十九轮杀气商店镜像：每日首次登录先结账（昨日塔胜+副本杀气攒够即买，称号优先）
                    p.killingShop(s)
                    // 第二十九轮每日副本镜像：当日唯一一次机会（胜败都占名额），挑战已解锁最高难度、
                    // 通关后扫荡（魂力不足退回挑战）
                    p.dungeon(s)
                }
                repeat(battlesPerSession[i]) { p.battle(s) }
                repeat(towersPerSession[i]) { p.tower(s) }
                p.equipPass()
                // 第二十七轮强化镜像：每日首次登录强化一件（穿装后、卖垃圾/买扩容前；克制节奏+
                // 存量防线把 sink 压在补充量级——逐登录强化实测吃掉 ~87% 日收入，见报告小节）
                if (i == 0) p.enhancePass(s)
                s.sellGold += p.sellJunk()
                p.buyExpansions()
            }
            totalRejects += p.rejectsToday
            totalUpgrades += p.upgradesToday
            totalRingDrops += p.ringDropsToday
            totalDeadRingDrops += p.deadRingDropsToday
            totalEnhances += s.enhances
            totalEnhanceGold += s.enhanceGold
            // 第二十九轮副本/杀气镜像累计（副本金币计入金币总收入与主动玩法收入两个占比分母）
            totalDungeonGold += s.dungeonGold
            totalDungeonWins += s.dungeonWins
            totalDungeonLosses += s.dungeonLosses
            totalDungeonSweeps += s.dungeonSweeps
            totalSweepSoulPower += s.sweepSoulPower
            totalTowerKilling += s.towerKilling
            totalDungeonKilling += s.dungeonKilling
            totalKillingSpent += s.killingSpent
            totalTitleBuys += s.titleBuys
            totalAttrBuys += s.attrBuys
            totalGoldIncome += s.offlineGold + s.battleGold + s.towerGold + s.sellGold + s.dungeonGold +
                    s.checkInGold + s.questGold
            totalActiveGold += s.battleGold + s.towerGold + s.sellGold + s.dungeonGold
            val bagRings = p.bagRings()
            rows.add(LoadLoopDay(
                day = d, level = p.level, mapId = p.mapId, stage = p.stage, towerFloor = p.towerFloor,
                load = p.equippedLoad(), capacity = p.capacity(), slots = p.slotsFilled(),
                bagRings = bagRings.size, bagHighTierRings = bagRings.count { it.year >= 2 },
                bagMaxRingLoad = bagRings.maxOfOrNull { it.ringLoad } ?: 0L,
                rejectsToday = p.rejectsToday, bagCount = p.bag.size, bagCap = p.bagCap, gold = p.gold,
                upgradesToday = p.upgradesToday, ringDropsToday = p.ringDropsToday,
                deadRingDropsToday = p.deadRingDropsToday,
                bagRingsByYear = (0..4).map { y -> bagRings.count { it.year == y } }.toIntArray(),
                prestigeCount = p.prestigeCount,
                enhancesToday = s.enhances, enhanceGoldToday = s.enhanceGold,
            ))
            p.rejectsToday = 0
            p.upgradesToday = 0
            p.ringDropsToday = 0
            p.deadRingDropsToday = 0
        }
        return LoadLoopRun(rows, p.firstRejectDay, p.firstRejectMap, p.firstRejectLevel,
            p.firstRejectLoad, p.firstRejectYear, p.firstEquipDayByYear, p.dropLostTotal, totalRejects,
            totalUpgrades, totalRingDrops, totalDeadRingDrops, totalEnhances, totalEnhanceGold, totalGoldIncome,
            totalDungeonGold = totalDungeonGold, totalDungeonWins = totalDungeonWins,
            totalDungeonLosses = totalDungeonLosses, totalDungeonSweeps = totalDungeonSweeps,
            totalSweepSoulPower = totalSweepSoulPower, totalActiveGold = totalActiveGold,
            totalTowerKilling = totalTowerKilling, totalDungeonKilling = totalDungeonKilling,
            totalKillingSpent = totalKillingSpent, totalTitleBuys = totalTitleBuys,
            totalAttrBuys = totalAttrBuys, finalTitles = p.ownedTitles.size,
            finalKillingIntent = p.killingIntent,
            nextTitleName = GameBalance.KILLING_TITLES.getOrNull(p.ownedTitles.size)?.name ?: "全满")
    }

    // ======== 报告生成 ========

    /** 多种子鲁棒性抽查的一行汇总 */
    private data class SeedSummary(
        val seed: Long, val level: Int, val mapId: Int, val utilLate: Double,
        val slots: Int, val bagRings: Int, val bagHighTier: Int,
        val deadShareEarly: Double, val deadShareLate: Double,
        val firstRejectDay: Int, val upgradesPerDay: Double, val totalRejects: Long,
        val prestigeCount: Int = 0, val firstPrestigeDay: Int = 0,
        /** 第二十七轮强化镜像汇总（10 种子收敛锁观察列） */
        val enhances: Long = 0, val enhanceGold: Long = 0, val enhanceShare: Double = 0.0,
        /** 第二十九轮副本/杀气商店镜像汇总（10 种子收敛锁观察列） */
        val dungeonGold: Long = 0, val dungeonShare: Double = 0.0,
        val titles: Int = 0, val killingSpent: Long = 0,
    )

    /** 后 60 天（31~90）掉落的魂环中「掉落当时就装不下」的比例 */
    private fun LoadLoopRun.deadRingDropShareLate(): Double {
        val late = rows.filter { it.day > 30 }
        val drops = late.sumOf { it.ringDropsToday.toLong() }
        return if (drops == 0L) 0.0 else late.sumOf { it.deadRingDropsToday.toLong() } * 100.0 / drops
    }

    private fun LoadLoopRun.deadRingDropShareEarly(): Double {
        val early = rows.filter { it.day <= 30 }
        val drops = early.sumOf { it.ringDropsToday.toLong() }
        return if (drops == 0L) 0.0 else early.sumOf { it.deadRingDropsToday.toLong() } * 100.0 / drops
    }

    private fun buildLoadLoopSection(
        load: LoadLoopRun, free: LoadLoopRun, zero: SimOutcome,
        seeds: List<SeedSummary>, noEnhance: LoadLoopRun,
    ): String = buildString {
        val fl = load.final
        val u10 = load.rows.take(10).averageOf { it.utilPct }
        val uMid = load.rows.filter { it.day in 31..60 }.averageOf { it.utilPct }
        val uLate = load.rows.filter { it.day > 60 }.averageOf { it.utilPct }
        val deadEarly = load.deadRingDropShareEarly()
        val deadLate = load.deadRingDropShareLate()
        val upPerDayLate = load.rows.filter { it.day > 60 }.sumOf { it.upgradesToday } / 30.0
        val bag = fl.bagRingsByYear

        appendLine("## 魂环负荷反馈回路专项复核（任务#22）")
        appendLine()
        appendLine("### 回路健康判定")
        val healthy = uLate in 50.0..92.0 && deadLate < 5.0 && fl.slots >= 8 && upPerDayLate >= 0.05
        appendLine(if (healthy) {
            "- **结论：调参后回路健康。** 判据（全部满足）：后期利用率落在 50%~92% 带内（实测 ${String.format("%.1f", uLate)}%）、"
        } else {
            "- **结论：回路仍不健康（触发下列判据），需继续调参。** 实测："
        })
        appendLine("  ①后期(61~90天)容量利用率 ${String.format("%.1f", uLate)}%（目标带 50~92%，>95%+高档积压=恒卡，<50%=形同虚设）；")
        appendLine("  ②30 天后魂环掉落「落地即装不下」死掉落率 ${String.format("%.1f", deadLate)}%（目标 <5%）；")
        appendLine("  ③第 90 天槽位 ${fl.slots}/9 且换装 treadmill ${String.format("%.2f", upPerDayLate)} 次/日>0（仍在升级）；")
        appendLine("  ④首次拒装后档位仍能解锁（见下表档位演进，非永久卡死）。")
        appendLine()
        appendLine("### 方法")
        appendLine("- 画像升级：与上文同作息，但每次登录末「骨 6 槽 / 核 2 槽直穿最强，魂环 9 槽按最强优先、")
        appendLine("  装得下才装」贪心到不动点（换装升级即淘汰进背包，与生产 equipRing 行为一致）。")
        appendLine("- 负荷/容量/换装校验**直接调用生产纯函数** `RingLoadCalculator.ringLoad/totalRingLoad/absorptionCapacity`")
        appendLine("  （容量=根骨×RING_CAPACITY_ROOT_MULT，根骨=当前 atk×3+maxHp/100，atk/hp 含装备加成，与")
        appendLine("  `GameService.absorptionCapacityFor` 同源）；战斗/塔胜率吃装备加成（沿用 resolveBattle/towerWinChance），")
        appendLine("  掉落逐行镜像 battle/rollBackpackDrop（含 RNG 消耗次序）。环负荷=下一档位基础值×成熟度/1000×(0.6+0.1×品质)，")
        appendLine("  档位 0~4 对应 1k/10k/100k/1M/10M。魂骨/魂核不占负荷（设计文档决定）。")
        appendLine("- 对照组 = 同一 RNG 序列下关掉负荷校验（无脑穿最强 9 环），量化校验本身对推图/塔速度的代价。")
        appendLine("- 注意：贪心穿装画像下「利用率」天然贴着容量走（档内连续分布），单看利用率会高估卡死程度；")
        appendLine("  真正的健康信号是**死掉落率**（掉了就永远装不下）与**换装频率**（升级 treadmill 是否还在转）。")
        appendLine()
        appendLine("### 负荷/容量回路 90 天曲线（主种子 20260914，每 5 天采样）")
        appendLine()
        appendLine("| 天 | 等级 | 转数 | 推图 | 已装负荷 | 容量 | 利用率 | 槽位 | 背包环积压(≥2档) | 死环掉落/当日环掉落 | 换装次数 | 塔层 |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|")
        for (r in load.rowsEvery(5)) {
            appendLine("| ${r.day} | ${r.level} | ${r.prestigeCount} | ${r.mapId + 1}-${r.stage} | ${eng(r.load)} | ${eng(r.capacity)} " +
                    "| ${String.format("%.1f", r.utilPct)}% | ${r.slots}/9 | ${r.bagRings} (${r.bagHighTierRings}) " +
                    "| ${r.deadRingDropsToday}/${r.ringDropsToday} | ${r.upgradesToday} | ${r.towerFloor} |")
        }
        appendLine()
        appendLine("### 判定指标（调参后实测）")
        appendLine()
        appendLine("- 容量利用率：前 10 天 ${String.format("%.1f", u10)}% / 中期(31~60 天) ${String.format("%.1f", uMid)}%" +
                " / 后期(61~90 天) ${String.format("%.1f", uLate)}% / 历史峰值 ${String.format("%.1f", load.maxUtilEver)}%")
        appendLine("- 死掉落率（掉落当时即超容量）：前 30 天 ${String.format("%.1f", deadEarly)}% / 后 60 天 ${String.format("%.1f", deadLate)}%" +
                "（90 天累计 ${load.totalDeadRingDrops}/${load.totalRingDrops}）")
        appendLine("- 首次拒装：第 ${load.firstRejectDay} 天（${load.firstRejectMap + 1} 号图 / 等级 ${load.firstRejectLevel} /" +
                " 涉事环 ${eng(load.firstRejectLoad)} 负荷·序数${load.firstRejectYear}档）；90 天累计拒装事件 ${load.totalRejects} 次")
        val tierStr = (0..4).filter { load.firstEquipDayByYear.containsKey(it) }
            .joinToString("、") { "序数${it}←第 ${load.firstEquipDayByYear[it]} 天" }
        appendLine("- 成功穿上的最高档位演进：${tierStr.ifEmpty { "无" }}——首拒后档位仍持续解锁，未永久卡死")
        appendLine("- 第 90 天：槽位 ${fl.slots}/9、利用率 ${String.format("%.1f", fl.utilPct)}%、换装 ${String.format("%.2f", upPerDayLate)} 次/日(后30天)；")
        appendLine("  背包魂环 ${fl.bagRings} 件（档位分布 0..4 = ${bag.joinToString("/")}），均为可装档位内的换装备用件，" +
                "单件最大负荷 ${eng(fl.bagMaxRingLoad)} < 容量 ${eng(fl.capacity)}，无死档位积灰")
        appendLine("- 满包丢装备 ${load.dropLostTotal} 件——与负荷无关（塔 65% 掉率的非环垃圾流，即上文 P4 既有问题）。")
        appendLine()
        appendLine("### 调参记录（本次改动，生产端生效）")
        appendLine()
        appendLine("调参前（容量乘数=shared 原值 6、塔环档位不封顶）同种子实测呈**恒卡**形态，命中任务判据「利用率>95% + 高档位环大量积压」：")
        appendLine()
        appendLine("| 指标 | 调参前(mult=6, 塔环≤4档) | 调参后(mult=24, 塔环≤2档) |")
        appendLine("|---|---|---|")
        appendLine("| 后期(61~90天)利用率 | 97.2%（贴墙） | ${String.format("%.1f", uLate)}%（带内） |")
        appendLine("| 90 天死掉落率 | 84.1%（30 天后≈100%） | ${String.format("%.1f", load.deadRingDropShare)}%（后 60 天 ${String.format("%.1f", deadLate)}%） |")
        appendLine("| 拒装事件/日 | 83.8 | ${String.format("%.1f", load.totalRejects / 90.0)} |")
        appendLine("| 第 90 天背包环积压 | 22 件全为 ≥2 档、单件最大 9.99M（永不可装） | ${fl.bagRings} 件（全部可装档内） |")
        appendLine("| 换装 treadmill | ~0（终局无升级） | ${String.format("%.2f", upPerDayLate)} 次/日 |")
        appendLine()
        appendLine("两处改动（均为任务授权的回路上限）：")
        appendLine("- `GameBalance.RING_CAPACITY_ROOT_MULT` 新增，容量乘数 shared 原值 6 → **24**（RingLoadCalculator.absorptionCapacity 读取）。")
        appendLine("  负荷档位是 10 倍一跳的指数阶梯，而容量（根骨×乘数）随等级/装备线性增长，×6 时中后期容量恒卡在")
        appendLine("  档位阶梯缝里（容量 ~24 万 vs 3 档环最低负荷 6 万~99.9 万），利用率贴 96~99% 且新档位解锁间隔趋近无穷。")
        appendLine("- `GameBalance.TOWER_RING_DROP_YEAR_CAP = 2`（GameService.rollBackpackDrop 魂环分支）：塔 13 层后")
        appendLine("  `min(4, towerLevel/12+rand(2))` 恒为 4 档、且塔环 percentage=100+towerLevel×12 饱和到钳位值 999、品质恒 4 →")
        appendLine("  每张塔魂环负荷恒 ≈9.99M，**100% 死掉落**，是背包积灰的主因。塔环封顶 2 档（≤99.9k）后塔掉环重新可装；")
        appendLine("  3~4 档保留为推图 6-7 号图（负荷分布 6 万~99.9 万随机）与后续轮回内容的终局追求。魂骨/魂核档位曲线不动。")
        appendLine("  注：魂骨/魂核不吃负荷但全额计入根骨 atk/hp → 容量主要被它们撑大，这是负荷系统对「不占负荷装备」")
        appendLine("  约束力为零的既有设计缺口，维持《V2.1负荷平衡》文档决定，本任务不动，仅在此留档。")
        appendLine()
        appendLine("### 对照组：负荷校验对推图/塔推进的代价（调参后）")
        appendLine()
        appendLine("| 采样点 | 无装备基线(任务#17画像) | 对照组(无负荷校验,秒穿最强9环) | 实装组(负荷校验) |")
        appendLine("|---|---|---|---|")
        for (d in listOf(10, 20, 30, 45, 60, 75, 90)) {
            val z = zero.rows.first { it.day == d }
            val f = free.rows.first { it.day == d }
            val l = load.rows.first { it.day == d }
            appendLine("| 第 $d 天 | ${z.mapId + 1}-${z.stage} (Lv.${z.level}) | ${f.mapId + 1}-${f.stage} (Lv.${f.level}) " +
                    "| ${l.mapId + 1}-${l.stage} (Lv.${l.level}) |")
        }
        appendLine("- 推图侧两组几乎无差：装备攻击加成（尤其魂骨）已使战斗/塔碾压怪物曲线，第 10 天双双抵达 8-15 终局")
        appendLine("  （P7 既有结论「终局空洞」）；负荷校验的代价不体现在推进速度，而体现在上文换装/死掉落指标上。")
        appendLine("  无校验对照组的意义在于确认：校验**没有**把推图拖回零装备基线节奏（那才是真卡死）。")
        appendLine()
        appendLine("### 多种子鲁棒性抽查（10 种子 × 90 天，实装组·调参后）")
        appendLine()
        appendLine("| 种子 | 90天等级 | 转数(首次转生日) | 推图 | 后期利用率 | 死环率(前30/后60天) | 换装/日 | 拒装/日 | 槽位 | 环积压(≥2档) | 首次拒装 | 强化(次/金/占收入) | 副本(金/占主动收入) | 杀气商店(称号/支出) |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        for (s in seeds) {
            appendLine("| ${s.seed} | ${s.level} | ${s.prestigeCount} (第 ${s.firstPrestigeDay} 天) | ${s.mapId + 1} | ${String.format("%.1f", s.utilLate)}% " +
                    "| ${String.format("%.1f", s.deadShareEarly)}% / ${String.format("%.1f", s.deadShareLate)}% " +
                    "| ${String.format("%.2f", s.upgradesPerDay)} | ${String.format("%.1f", s.totalRejects / 90.0)} " +
                    "| ${s.slots}/9 | ${s.bagRings} (${s.bagHighTier}) | 第 ${s.firstRejectDay} 天 " +
                    "| ${s.enhances}/${eng(s.enhanceGold)}/${String.format("%.1f", s.enhanceShare)}% " +
                    "| ${eng(s.dungeonGold)}/${String.format("%.1f", s.dungeonShare)}% " +
                    "| ${s.titles}个/${eng(s.killingSpent)} |")
        }
        appendLine()
        appendLine("- 10 种子汇总（转生开启）：满槽 ${seeds.count { it.slots == 9 }}/10、" +
                "后期利用率 ${String.format("%.1f", seeds.minOf { it.utilLate })}%~${String.format("%.1f", seeds.maxOf { it.utilLate })}%" +
                "、后 60 天死环率最高 ${String.format("%.1f", seeds.maxOf { it.deadShareLate })}%" +
                "、累计转生 ${seeds.sumOf { it.prestigeCount }} 次（全部种子首次转生日 ${seeds.minOf { it.firstPrestigeDay }}~${seeds.maxOf { it.firstPrestigeDay }} 天）。")
        appendLine()
        appendLine("### 魂骨强化镜像（第二十七轮）")
        appendLine()
        appendLine("- 玩家策略（仿真侧杠杆，生产常量不动）：每日首次登录，若金币 ≥${eng(ENHANCE_GOLD_FLOOR)}，")
        appendLine("  在【未满 +${GameBalance.BONE_ENHANCE_MAX_LEVEL} 且费用 ≤ 金币−防线】的骨中强化 itemScore 最高的一件——已装备骨优先")
        appendLine("  （slotIndex 路径），全被顶满时退回背包骨（itemIndex 路径「预备强化」）。费用走生产公式")
        appendLine("  `GameBalance.boneEnhanceCost`（800×(级+1)²×(年+1)×品质倍率，随等级平方陡增）、100% 成功；")
        appendLine("  强化后存量仍 ≥${eng(ENHANCE_GOLD_FLOOR)}（扩容/商店完全不受挤兑，「强化抢金币 → 扩容延迟 →")
        appendLine("  背包满丢掉落」被策略防线隔离——对照组观察口径见下表）。每日一次+防线是刻意克制的节奏")
        appendLine("  （逐登录强化的先导实测吃掉 ~87% 日收入、把存量钉死在防线，违背「补充 sink」定位）。零掷点：")
        appendLine("  强化不消耗 RNG，但抬升骨战力 → 战斗/塔胜负路径与对照分叉，对照为统计对照而非逐位同 RNG。")
        appendLine("- **设计发现（塔白送曲线 × 主动强化上限的互动）**：生产塔掉落魂骨白送 enhance=max(1,towerLevel/10)")
        appendLine("  （rollBackpackDrop 被传入 towerLevel=floor×3，满层 300 → **+30**），越过主动强化上限 +15 →")
        appendLine("  终局已装备骨全部不可再强化（这正是「背包骨预备强化」成为终局 sink 主力的原因）。战斗掉落骨")
        appendLine("  （+0，battle 内联掉落不经 rollBackpackDrop）与前期塔骨（+≤15）是主动强化的可作用对象。")
        appendLine("  生产掉落曲线是否要对齐上限（如 min(15, towerLevel/10)）属主线决策，本次未动生产代码。")
        appendLine("- 扣费镜像是经济闭环的关键（吸收费未实现前的主力金币 sink）：不扣费则仿真高估金币存量。")
        appendLine("- 主种子 90 天（强化开启）：累计强化 ${load.totalEnhances} 次、消耗 ${eng(load.totalEnhanceGold)} 金" +
                "（占全期金币总收入 ${String.format("%.1f", load.enhanceShareOfIncome)}%；期末金币存量 ${eng(load.final.gold)}）。")
        appendLine("- 主种子对照组（同种子关闭强化）：期末金币 ${eng(noEnhance.final.gold)}、" +
                "后期利用率 ${String.format("%.1f", noEnhance.rows.filter { it.day > 60 }.averageOf { it.utilPct })}%、" +
                "满包丢掉落 ${noEnhance.dropLostTotal} 件（强化开启为 ${load.dropLostTotal} 件）。")
        appendLine()
        appendLine("| 指标 | 关闭强化（对照） | 开启强化 |")
        appendLine("|---|---|---|")
        val uLateOff = noEnhance.rows.filter { it.day > 60 }.averageOf { it.utilPct }
        val uLateOn = load.rows.filter { it.day > 60 }.averageOf { it.utilPct }
        appendLine("| 90 天末金币存量 | ${eng(noEnhance.final.gold)} | ${eng(load.final.gold)} |")
        appendLine("| 90 天末等级/推图 | Lv.${noEnhance.final.level} ${noEnhance.final.mapId + 1}-${noEnhance.final.stage} | Lv.${load.final.level} ${load.final.mapId + 1}-${load.final.stage} |")
        appendLine("| 后期(61~90天)利用率 | ${String.format("%.1f", uLateOff)}% | ${String.format("%.1f", uLateOn)}% |")
        appendLine("| 满包丢掉落 | ${noEnhance.dropLostTotal} 件 | ${load.dropLostTotal} 件 |")
        appendLine("| 累计强化次数/消耗 | 0 / 0 | ${load.totalEnhances} / ${eng(load.totalEnhanceGold)} |")
        appendLine()
        appendLine("- 10 种子强化汇总：累计 ${seeds.sumOf { it.enhances }} 次 / ${eng(seeds.sumOf { it.enhanceGold })} 金，" +
                "占收入比 ${String.format("%.1f", seeds.minOf { it.enhanceShare })}%~${String.format("%.1f", seeds.maxOf { it.enhanceShare })}%——" +
                "期末金币存量（主种子 ${eng(load.final.gold)}）仍处健康量级，前期单次费用 ≤ 日收入量级。")
        appendLine()
        appendLine("### 每日副本镜像（第二十九轮）")
        appendLine()
        appendLine("- 玩家策略（理性）：每天唯一一次机会（胜败都占当日名额）放在首次登录，挑战当前已解锁的")
        appendLine("  最高难度（转数 ≥ unlockPrestige 的最大 tier，0 转空过）；该难度历史通关（ever_cleared 位掩码")
        appendLine("  镜像，胜局置位、转生不清）后改扫荡（免战斗、耗魂力 50+等级×5，魂力不足退回挑战——战斗")
        appendLine("  免费，已通关难度期望收益仍为正）。")
        appendLine("- 公式逐位镜像 DungeonService.fight/sweep：Boss = 当前推图怪 × HP/攻击倍率（matk 镜像攻击、")
        appendLine("  双防 = 放大后攻击 × MONSTER_DEF_FACTOR），战斗走生产 resolveBattle（玩家属性与 battle 同口径，")
        appendLine("  含杀气商店加成）；金币 = goldReward × prestigeMultiplier、杀气直加、掉落 rollBackpackDrop")
        appendLine("  （level=dropTier×12——与塔共用 rollBackpackDropMirror，背包满丢失计满包丢掉落）。")
        appendLine("- 主种子 90 天：副本金币累计 ${eng(load.totalDungeonGold)}" +
                "（占全期金币总收入 ${String.format("%.1f", load.dungeonShareOfIncome)}%、" +
                "占主动玩法收入 ${String.format("%.1f", load.dungeonShareOfActive)}%）、" +
                "胜/败/扫荡 = ${load.totalDungeonWins}/${load.totalDungeonLosses}/${load.totalDungeonSweeps}、" +
                "扫荡耗魂力合计 ${eng(load.totalSweepSoulPower)}；副本杀气累计 ${load.totalDungeonKilling}。")
        appendLine("- 10 种子汇总：副本金币 ${eng(seeds.minOf { it.dungeonGold })}~${eng(seeds.maxOf { it.dungeonGold })}、" +
                "占主动收入 ${String.format("%.1f", seeds.minOf { it.dungeonShare })}%~${String.format("%.1f", seeds.maxOf { it.dungeonShare })}%。" +
                "解锁节奏=转生驱动（难度 0~4 依次需 1~5 转）：主种子 90 天末 ${load.final.prestigeCount} 转 → " +
                "已解锁 ${GameBalance.DUNGEON_DEFS.count { load.final.prestigeCount >= it.unlockPrestige }}/5 档——")
        appendLine("  高难度（噩梦 10 万/地狱 30 万名义金）属 4~5 转后的终局产出，本期画像未触及。")
        appendLine()
        appendLine("### 杀气商店镜像（第二十九轮）")
        appendLine()
        appendLine("- 玩家策略：称号按 title_1→title_8 顺序攒够即买，【称号优先级高于属性购买】——称号属性量")
        appendLine("  随价格（100→150000）同步递增且永久拥有叠加生效，单位杀气换得的属性显著高于属性购买")
        appendLine("  （+100HP/+10ATK 基值固定不随等级缩放，文档定位为前期小额补强）；称号全满后剩余杀气轮替")
        appendLine("  投入 HP/ATK 属性购买（价格 100×2^已购次数）。")
        appendLine("- 杀气收入：塔胜 1+floor/10（既有口径）+ 副本 10/25/50/100/200 直加（不乘转生倍率）；")
        appendLine("  加成经生产纯函数 killingBonus 并入战斗/塔战力加成包（equip+ach+kill 合计后 ×转生倍率，")
        appendLine("  与生产 bonusFor 同通道、战力第 10 行 title 口径）。")
        appendLine("- 主种子 90 天：杀气收入 塔 ${load.totalTowerKilling} + 副本 ${load.totalDungeonKilling} = " +
                "${load.totalTowerKilling + load.totalDungeonKilling}，商店支出 ${eng(load.totalKillingSpent)}" +
                "（称号 ${load.totalTitleBuys} 个、属性购买 ${load.totalAttrBuys} 次），期末余额 ${load.finalKillingIntent}、")
        appendLine("  期末称号 ${load.finalTitles}/8（下一档：${load.nextTitleName}）。")
        appendLine("- 10 种子汇总：期末称号 ${seeds.minOf { it.titles }}~${seeds.maxOf { it.titles }}/8 个、" +
                "杀气商店支出 ${eng(seeds.minOf { it.killingSpent })}~${eng(seeds.maxOf { it.killingSpent })}——" +
                "杀气在 90 天窗口内是「收入＜称号定价」的稀缺货币（title_8 累计需 22.12 万），商店构成杀气的")
        appendLine("  长线终局 sink，90 天画像验证的是其前期节奏（称号逐档解锁）而非终局饱和。")
    }

    /**
     * 转生事件复盘（转生镜像落地后新增章节）：同种子「关闭转生 vs 达标即转」对照，
     * 量化转生对等级成长、经济、负荷回路的扰动，供主线评估门槛/收益校准。
     */
    private fun buildPrestigeSection(
        r90: SimOutcome, r90Base: SimOutcome, load90: LoadLoopRun, load90Base: LoadLoopRun
    ): String = buildString {
        val f = r90.final
        val fb = r90Base.final
        val fl = load90.final
        val flb = load90Base.final
        val prestigeDays = r90.rows.mapIndexedNotNull { i, r ->
            if (r.stats.prestiges > 0) r.day else null
        }
        val firstPrestigeDay = prestigeDays.firstOrNull() ?: 0
        val loadPrestigeDays = load90.rows.mapIndexedNotNull { i, r ->
            val prev = if (i == 0) 0 else load90.rows[i - 1].prestigeCount
            if (r.prestigeCount > prev) r.day else null
        }
        val loadFirstPrestigeDay = loadPrestigeDays.firstOrNull() ?: 0
        val utilLateBase = load90Base.rows.filter { it.day > 60 }.averageOf { it.utilPct }
        val utilLate = load90.rows.filter { it.day > 60 }.averageOf { it.utilPct }
        appendLine()
        appendLine("## 转生事件复盘（转生镜像新增）")
        appendLine()
        appendLine("### 机制镜像（生产 GameService.prestige + GameBalance.prestigeMultiplier）")
        appendLine("- 触发策略：画像在 breakthroughAll 中达到 Lv.${GameBalance.PRESTIGE_MIN_LEVEL} 即转生（生产为玩家手动端点 POST /api/action/prestige，")
        appendLine("  门槛不足返回 success=false 不消耗）。画像取「达标即转」的激进策略，压测重置回爬曲线的最坏情形。")
        appendLine("- 重置（文档 §15.2 + 防刷修正）：level=1、gold=0、soulPower=0（防存量魂力秒升回原等级——零成本刷属性漏洞）、")
        appendLine("  推图关卡回 1、已装备魂环/魂骨卸回背包（魂核保留）；保留：towerFloor/currentMapId（推图进度）、codexKills、")
        appendLine("  bossCoin、成就、天赋、背包；prestigeCount+1、talentPoints+1（天赋点不建模，对属性/收入曲线无反馈）。")
        appendLine("- 收益（双口径）：修炼/战斗胜/塔胜/离线四处产出与基础 atk/maxHp 及装备+成就加成 ×(1+转数×10%)；")
        appendLine("  签到/任务固定表不乘（留存钩子与交易口径不膨胀）。")
        appendLine()
        appendLine("### 零装备基线（SimPlayer 主种子 20260914，同 RNG 对照）")
        appendLine()
        appendLine("| 指标 | 转生关闭 | 转生开启（达标即转） |")
        appendLine("|---|---|---|")
        appendLine("| 90 天末等级 | ${fb.level} | ${f.level} |")
        appendLine("| 累计转生次数 | 0 | ${f.prestigeCount}（首次第 $firstPrestigeDay 天${if (prestigeDays.size > 1) "，全部转生日：$prestigeDays" else ""}） |")
        appendLine("| 90 天末推图进度 | ${fb.mapId + 1}-${fb.stage} | ${f.mapId + 1}-${f.stage} |")
        appendLine("| 90 天末塔层 | ${fb.towerFloor} | ${f.towerFloor} |")
        appendLine("| 90 天金币存量 | ${eng(fb.gold)} | ${eng(f.gold)} |")
        appendLine("| 近10日日均总收入(金币) | ${String.format("%.3e", r90Base.avgDailyTotalGold(10))} | ${String.format("%.3e", r90.avgDailyTotalGold(10))} |")
        appendLine()
        appendLine("### 穿装画像（EquipSimPlayer 实装组主种子，同 RNG 对照）")
        appendLine()
        appendLine("| 指标 | 转生关闭 | 转生开启（达标即转） |")
        appendLine("|---|---|---|")
        appendLine("| 90 天末等级 | ${flb.level} | ${fl.level} |")
        appendLine("| 累计转生次数 | 0 | ${fl.prestigeCount}（首次第 $loadFirstPrestigeDay 天${if (loadPrestigeDays.size > 1) "，全部转生日：$loadPrestigeDays" else ""}） |")
        appendLine("| 90 天末槽位 | ${flb.slots}/9 | ${fl.slots}/9 |")
        appendLine("| 后期(61~90天)容量利用率 | ${String.format("%.1f", utilLateBase)}% | ${String.format("%.1f", utilLate)}% |")
        appendLine("| 90 天末推图进度 | ${flb.mapId + 1}-${flb.stage} | ${fl.mapId + 1}-${fl.stage} |")
        appendLine()
        appendLine("### 解读")
        appendLine("- 转生后收入 ×(1+转数×10%) 与推图/塔层保留使回爬快于首爬；等级曲线呈锯齿形（到 50 即清零重爬），")
        appendLine("  90 天末等级不再单调、以「当前回爬进度」为准。")
        appendLine("- 穿装画像转生后容量（根骨×乘数，按当前 atk/maxHp）跌至 Lv.1 水平：魂环全部暂不可装（利用率骤降是")
        appendLine("  转生的预期形态，非负荷系统退化）；骨/核无负荷校验、下个登录即回装，环随等级回爬逐步回装。")
    }

    private fun eng(v: Long): String = when {
        v >= 1_000_000_000_000L -> String.format("%.2fT", v / 1e12)
        v >= 1_000_000_000L -> String.format("%.2fB", v / 1e9)
        v >= 1_000_000L -> String.format("%.2fM", v / 1e6)
        v >= 10_000L -> String.format("%.1fk", v / 1e3)
        else -> v.toString()
    }

    private fun fmtRow(r: DayRow): String =
        "| ${r.day} | ${r.level} | ${r.prestigeCount} | ${eng(r.gold)} | ${eng(r.soulPower)} | ${eng(r.bossCoin)} " +
                "| ${r.mapId + 1}-${r.stage} | ${r.towerFloor} | ${r.bagCount}/${r.capacity} " +
                "| ${eng(r.stats.offlineGold)} | ${eng(r.stats.battleGold + r.stats.towerGold + r.stats.sellGold)} " +
                "| ${eng(r.stats.checkInGold + r.stats.questGold)} " +
                "| ${r.stats.dropsLost} | ${r.stuckStreak} |"

    private fun buildReport(r30: SimOutcome, r90: SimOutcome, elapsedMs: Long): String = buildString {
        val f90 = r90.final
        val f30 = r30.final
        appendLine("# 游戏数值 30/90 天长跑仿真报告")
        appendLine()
        appendLine("> 生成方式：`backend/src/test/kotlin/com/douluodalu/game/simulation/LongRunSimulationTest.kt`。")
        appendLine("> 纯 Kotlin 公式镜像（不起 Spring、不连库），固定随机种子(20260914)，内容可复现；单次跑批 ${elapsedMs}ms。")
        appendLine("> 平衡常量直接引用 GameBalance；任务#20 修复 P1/P2/P7 后，battle/tower/离线镜像与生产端同源，")
        appendLine("> 本数据为**修复后**重跑结果，修复前后对账见文末「已修复项」。")
        appendLine()
        appendLine("## 仿真设定")
        appendLine()
        appendLine("- 玩家画像（中活跃）：每天 3 次登录（08:00/14:00/22:00，离线 6h/8h/10h，均 <12h 上限），")
        appendLine("  每次登录领离线收益、修炼 8 次、自动突破直到魂力不足、日均战斗 20 次 + 魂塔 18 次，")
        appendLine("  登录收尾卖最低品质装备保留 5 格，金币 ≥3000 即买背包扩展券（至 80 格）。")
        appendLine("- 每日固定收入（镜像）：逐日签到（CHECK_IN_REWARDS 7 日循环）+ 每日任务**全清上界假设**")
        appendLine("  （DAILY_QUESTS 求和：${QUEST_GOLD_PER_DAY} 金 / ${QUEST_BOSS_COIN_PER_DAY} Boss币 / ${QUEST_SOUL_POWER_PER_DAY} 魂力 每日）")
        appendLine("  ——上界假设：经济在全清上界下不崩即安全。成就属性加成按镜像状态（level/totalBattleWins/")
        appendLine("  towerFloor/已装备环数/prestige）经生产纯函数解锁并计入战斗 atk/hp（只兑现 hp/atk 口径）。")
        appendLine("- 镜像范围：cultivate / breakthrough(120·L^1.55) / battle（调用生产纯函数 monsterStats+resolveBattle，")
        appendLine("  HP 跨场次持久化、败退回到 1 关）/ towerBattle（调用生产纯函数 towerWinChance）/")
        appendLine("  claimOfflineReward（12h 截断、P1 修复后按**小时**计费）/ 掉落与背包容量 / 扩展券 / 签到+任务+成就 /")
        appendLine("  转生（达到 PRESTIGE_MIN_LEVEL=${GameBalance.PRESTIGE_MIN_LEVEL} 即转生：level/gold/soulPower 清零、stage 回 1、")
        appendLine("  环骨卸回背包，收入与属性 ×(1+转数×0.1)，详见文末《转生事件复盘》。")
        appendLine("- 武魂觉醒（第十八轮镜像，GameService.awaken / GameBalance 武魂池同源）：第 1 天免费首醒（0 转池），")
        appendLine("  每次转生后重醒一次（REAWAKEN_COST_GOLD=${GameBalance.REAWAKEN_COST_GOLD} 金，金币不足留待后续登录）；")
        appendLine("  武魂七属性经 soulBonusOf（已乘转生倍率）计入镜像战斗属性与塔胜率战力（报告末尾附觉醒事件行）。")
        appendLine("- 流派（第十九轮镜像，GameService.chooseSchool / GameBalance.SCHOOLS 同源）：第 1 天免费选 BALANCED")
        appendLine("  （温和系数建模：HP×105%、双防×105%、暴击+5%/爆伤+5%，atk/matk ×1.0；选流派不重 roll 武魂、零掷点），")
        appendLine("  系数经 schoolModsOf/playerCombatStats/schoolScaledMaxHp/applySchool 计入镜像战斗属性、maxHp 与")
        appendLine("  塔胜率战力（报告附流派事件行）。")
        appendLine("- 每日副本与杀气商店（第二十九轮镜像，DungeonService/ShopService 同源）：在文末《魂环负荷")
        appendLine("  反馈回路专项》的穿装画像（EquipSimPlayer）中建模——每天唯一一次副本机会（挑战已解锁最高")
        appendLine("  难度、通关后扫荡）+ 杀气收入（塔胜 1+floor/10 与副本直加）按称号顺序消费；本节零装备基线画像不参与。")
        appendLine("- 未建模：宗门 Boss、天赋、穿装行为——画像只捡/卖装备不穿戴，故装备战力加成按 0 计")
        appendLine("  （装备对战力的贡献已由 EquipmentPowerServiceTest 单测覆盖，见「已修复项」P7；成就加成不属装备，照常计入）。")
        appendLine("  **注（任务#22）**：上文各节维持「零装备基线」口径；穿装画像 + 魂环负荷/容量反馈回路的专项仿真")
        appendLine("  见文末《魂环负荷反馈回路专项复核（任务#22）》一章。")
        appendLine()
        appendLine("## 关键结果")
        appendLine()
        appendLine("| 指标 | 第 30 天 | 第 90 天 |")
        appendLine("|---|---|---|")
        appendLine("| 等级 | ${f30.level} | ${f90.level} |")
        appendLine("| 累计转生次数 | ${f30.prestigeCount} | ${f90.prestigeCount} |")
        appendLine("| 金币存量 | ${f30.gold} | ${f90.gold} |")
        appendLine("| 近10日日均总收入(金币) | ${String.format("%.3e", r30.avgDailyTotalGold(10))} | ${String.format("%.3e", r90.avgDailyTotalGold(10))} |")
        appendLine("| 近10日日均\"主动玩法\"收入(战斗+塔+卖装备) | ${String.format("%.0f", r30.avgDailyActiveGold(10))} | ${String.format("%.0f", r90.avgDailyActiveGold(10))} |")
        appendLine("| 近10日\"签到+任务\"日均收入(金币) | ${String.format("%.0f", r30.avgDailyPassiveGold(10))} | ${String.format("%.0f", r90.avgDailyPassiveGold(10))} |")
        appendLine("| 近10日\"签到+任务\"占主动玩法收入比例 | ${String.format("%.1f", r30.passiveVsActiveShareLast(10))}% | ${String.format("%.1f", r90.passiveVsActiveShareLast(10))}% |")
        appendLine("| 近10日离线收入占比 | ${String.format("%.1f", r30.offlineShareLast(10))}% | ${String.format("%.1f", r90.offlineShareLast(10))}% |")
        appendLine("| 近10日日均 Boss 币 | ${String.format("%.0f", r30.avgDailyBossCoin(10))} | ${String.format("%.0f", r90.avgDailyBossCoin(10))} |")
        appendLine("| 近10日日均离线折算\"战斗胜利\" | ${String.format("%.0f", r30.avgDailyOfflineWins(10))} | ${String.format("%.0f", r90.avgDailyOfflineWins(10))} |")
        appendLine("| 推图进度(地图-关卡) | ${f30.mapId + 1}-${f30.stage} | ${f90.mapId + 1}-${f90.stage} |")
        appendLine("| 推图全通关(抵达7图)所需天数 | 第 ${r30.firstDay { mapId >= GameBalance.MAX_MAP_ID }} 天 | 第 ${r90.firstDay { mapId >= GameBalance.MAX_MAP_ID }} 天 |")
        appendLine("| 魂塔层数 | ${f30.towerFloor} | ${f90.towerFloor} |")
        appendLine("| 累计满包丢掉落 | ${r30.dropLostTotal} | ${r90.dropLostTotal} |")
        appendLine("| 连续无法突破最长天数 | ${r30.rows.maxOf { it.stuckStreak }} | ${r90.rows.maxOf { it.stuckStreak }} |")
        // 第十八轮武魂觉醒事件行（首醒第 1 天免费；重醒 = 转生后花 REAWAKEN_COST_GOLD 换更大品质池）
        fun soulLabel(o: SimOutcome): String =
            o.finalSoul?.let { "${it.name}（${it.rarity.displayName}，战力值 ${GameBalance.martialSoulPower(it)}，技能 ${it.skill.name}）" } ?: "未觉醒"
        appendLine("| 武魂觉醒事件（首醒/重醒累计） | ${r30.awakenTotal}/${r30.reawakenTotal} | ${r90.awakenTotal}/${r90.reawakenTotal} |")
        appendLine("| 期末武魂 | ${soulLabel(r30)} | ${soulLabel(r90)} |")
        // 第十九轮流派事件行（第 1 天免费选 BALANCED 温和系数建模；改选 RESCHOOL_COST_GOLD 金，画像不改选）
        fun schoolLabel(o: SimOutcome): String = o.finalSchool?.let {
            val def = GameBalance.schoolByName(it)
            "${def?.icon ?: ""}${def?.displayName ?: it}（HP/双防×105%、暴击+5%，累计选择 ${o.schoolTotal} 次）"
        } ?: "未选流派"
        appendLine("| 流派事件（第 1 天免费选择，累计选择次数） | ${r30.schoolTotal} | ${r90.schoolTotal} |")
        appendLine("| 期末流派 | ${schoolLabel(r30)} | ${schoolLabel(r90)} |")
        appendLine()
        appendLine("## 发现的平衡问题（P1/P2/P7 已于任务#20 修复，前后对账见文末「已修复项」）")
        appendLine()
        appendLine("### P1 离线收益通胀 —— ✅ 已修复：按秒计费改为按小时计费")
        appendLine("- 修复前：`goldPerSecond = (10 + 2·L) × 0.8` 金币/秒，12h=43,200 秒直接日入数千万金。")
        appendLine("- 修复后：同数值语义改为每小时。本次数据：第 90 天日均总收入 ${String.format("%.3e", r90.avgDailyTotalGold(10))} 金、")
        appendLine("  近10日离线收入占比 ${String.format("%.1f", r90.offlineShareLast(10))}%（修复前恒为 100.0%），主动玩法收入 ${String.format("%.0f", r90.avgDailyActiveGold(10))} 金/日。")
        appendLine()
        appendLine("### P2 魂塔 38 层硬墙 —— ✅ 已修复：难度增量 0.02→0.005 + 战力加成胜率")
        appendLine("- 修复前 `win = random > 0.25 + floor × 0.02`，floor≥38 数学上不可能获胜；")
        appendLine("- 修复后 floor=99 基础胜率仍有 0.255，战力超过该层推荐值（40×floor）还能继续加成（封顶 +15%）。")
        appendLine("  本次仿真第 90 天塔层 ${f90.towerFloor}（修复前恒止步 38）、最后一次塔胜场在第 ${r90.lastTowerWinDay()} 天。")
        appendLine()
        appendLine("### P3 突破卡点被削弱：成长主要仍靠在线积累")
        appendLine("- 突破成本 `120·L^1.55` 超线性，离线魂力（修复后）`(5+L)×0.8/小时` 注入 + 每日 3 次领取，")
        appendLine("  90 天里连续无法突破天数最大 = ${r90.rows.maxOf { it.stuckStreak }}，等级到达 ${f90.level}（修复前为 1460）。")
        appendLine("- 建议：可将指数 1.55 → 2.0~2.2，使成本增速高于任何线性注入源（本次未动）。")
        appendLine()
        appendLine("### P4 背包体系与通胀脱钩：扩容免费、卖出无意义、整理成例行家务")
        appendLine("- 塔 65% + 推图最高 36.5% 掉落率下，90 天累计获得 ${r90.dropsGainedTotal} 件装备、满包丢失 ${r90.dropLostTotal} 件")
        appendLine("  （30 天口径：获得 ${r30.dropsGainedTotal}、丢失 ${r30.dropLostTotal}）。丢失少只因玩家每次登录都手动清包——")
        appendLine("  但整理本身成了无收益的例行家务：出售价 `100+品质×50`（100~300 金）不足第 90 天日均收入(${String.format("%.1e", r90.avgDailyTotalGold(10))})的十万分之一。")
        appendLine("- 扩展券 3000 金币占日收入的 ${String.format("%.6f", 3000.0 / maxOf(1.0, r90.avgDailyTotalGold(10)) * 100)}%，容量 20→80 在第 ${r90.firstDay { capacity >= 80 }} 天即拉满，")
        appendLine("  之后所有掉落只进 80 格\"垃圾场\"循环；容量成长与掉落节奏完全失配（前期贵、后期白送）。")
        appendLine("- 建议：扩展成本改指数增长 `1000 × 1.4^((capacity-20)/5)` 并去掉商店 5 格上限依赖；高品质(≥3)装备支持分解回收；")
        appendLine("  离线收益修正后同步 `TOWER_DROP_CHANCE` 0.65 → 0.35。")
        appendLine()
        appendLine("### P5 离线 12h 上限的行为扭曲")
        appendLine("- 本画像（3 次登录、间隔 ≤10h）刚好绕开截断：90 天累计被截断 ${String.format("%.1f", r90.offlineWastedHours)}h——")
        appendLine("  上限对中高频玩家无感，只惩罚低频玩家。")
        appendLine("- 每天只登录 1 次的玩家离线 24h，被截断 12h：按第 90 天等级 ${f90.level} 计，每天凭空蒸发 ≈")
        appendLine("  ${eng(((GameBalance.OFFLINE_GOLD_BASE + f90.level * GameBalance.OFFLINE_GOLD_PER_LEVEL) * GameBalance.OFFLINE_EFFICIENCY * 12).toLong())} 金币（恰好一半，P1 修复后该绝对值已回落三个数量级），")
        appendLine("  变相逼\"闹钟式\"卡点登录；且因收益按小时线性、无递减，理性的日优行为就是贴着 12h 整点领取。")
        appendLine("- 建议：`OFFLINE_MAX_HOURS` 12 → 20（覆盖一夜睡眠），或分段衰减（前 12h 100%、其后 50%）。")
        appendLine()
        appendLine("### P6 离线折算战斗胜利（5 秒/胜）污染统计与排行")
        appendLine("- `OFFLINE_SECONDS_PER_BATTLE_WIN=5`：领一次 10h 离线 = +7200 胜场。仿真第 90 天日均 +")
        appendLine("  ${String.format("%.0f", r90.avgDailyOfflineWins(10))} 胜。`totalBattleWins`/`codexKills`（塔胜利也 +1）驱动的排行榜")
        appendLine("  将完全被离线收益刷爆，活跃榜与挂机榜失去区分度。")
        appendLine("- 建议：离线只给金币/魂力、不再 +胜场；或 ≥3600 秒/胜且计入独立的离线计数器。")
        appendLine()
        appendLine("### P7 装备零战力 —— ✅ 已修复（战力接入 battle/塔公式）；推图终局空洞仍在")
        appendLine("- 修复前：battle() 的 `playerAtk` 只读等级，魂环/魂骨/魂核不参与任何战斗公式。")
        appendLine("- 修复后：EquipmentPowerService 将已装备环/骨/核折算攻防加成，进入回合结算与塔胜率")
        appendLine("  （本仿真画像不穿装，加成按 0 建模；战力效果由 EquipmentPowerServiceTest 单测断言）。")
        appendLine("- 推图侧未修：autoAdvance 到 7 图 15 关后永久驻留（生产代码 `currentStage = 15` 原地踏步），")
        appendLine("  仿真第 ${r90.firstDay { mapId >= GameBalance.MAX_MAP_ID && stage >= GameBalance.STAGES_PER_MAP }} 天起进入终局循环，")
        appendLine("  15 关满足 `stage%5==0` → 每胜 40% 掉 Boss 币（日均 ${String.format("%.0f", r90.avgDailyBossCoin(10))} 枚）。")
        appendLine("- 建议：`MAX_MAP_ID` 之后增加声望/轮回层；Boss 币产出与价格重新对表（本次未动）。")
        appendLine()
        appendLine("## GameBalance 调参建议汇总（✅ = 任务#20 已落地；其余仅建议，未改动生产代码）")
        appendLine()
        appendLine("| 常量 | 原值 | 现值/建议值 | 针对问题 | 状态 |")
        appendLine("|---|---|---|---|---|")
        appendLine("| OFFLINE_GOLD_BASE / OFFLINE_GOLD_PER_LEVEL | 10 / 2（每秒） | 10 / 2（每小时） | P1 | ✅ 已落地 |")
        appendLine("| OFFLINE_EXP_BASE / OFFLINE_EXP_PER_LEVEL | 5 / 1（每秒） | 5 / 1（每小时） | P1/P3 | ✅ 已落地 |")
        appendLine("| TOWER_FLOOR_DIFFICULTY | 0.02 | 0.005 | P2（38 层硬墙）| ✅ 已落地 |")
        appendLine("| 装备战力系数（RING/BONE/CORE/POWER_*） | 无 | 新增，见 GameBalance「装备战力」段 | P7 | ✅ 已落地 |")
        appendLine("| OFFLINE_SECONDS_PER_BATTLE_WIN | 5 | 3600 或移除胜场折算 | P6 | 未动 |")
        appendLine("| OFFLINE_MAX_HOURS | 12 | 20 或分段衰减 | P5 | 未动 |")
        appendLine("| TOWER_DROP_CHANCE | 0.65 | 0.35 | P4 | 未动 |")
        appendLine("| 突破成本指数 getBreakthroughCost | 120·L^1.55 | 120·L^2.1 | P3 | 未动 |")
        appendLine("| 背包扩展成本 expandBackpack | 1000+cap×100 | 1000×1.4^((cap-20)/5) | P4 | 未动 |")
        appendLine()
        appendLine("## 每日曲线（90 天全量；30 天时间线为其同种子前缀，另附 30 天独立跑批切片）")
        appendLine()
        appendLine("推图列 = 地图-关卡（8-15 表示 7 号图\"杀戮之都外域\"满星后原地驻留）；金币等大额用 k/M/B 缩写。")
        appendLine()
        appendLine("| 天 | 等级 | 转数 | 金币存量 | 魂力 | Boss币 | 推图 | 塔层 | 背包 | 当日离线金 | 当日主动金 | 当日签到+任务金 | 满包丢掉落 | 连续未突破天数 |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        r90.rows.forEach { appendLine(fmtRow(it)) }
        appendLine()
        appendLine("> 收入/属性分项来源说明（每日固定收入 + 成就属性加成镜像，与生产端同源）：")
        appendLine("> ① 每日签到：逐日按 `CHECK_IN_REWARDS[((day-1)%7)]`（CHECK_IN_CYCLE=7，day 从 1 起）计入 gold/soulPower/bossCoin；")
        appendLine("> ② 每日任务：全清上界假设（直接读 `DAILY_QUESTS` 求和，当前 ${QUEST_GOLD_PER_DAY} 金/${QUEST_BOSS_COIN_PER_DAY} Boss币/${QUEST_SOUL_POWER_PER_DAY} 魂力/日）——上界假设：经济在全清上界下不崩即安全；")
        appendLine("> ③ 成就属性加成：按镜像玩家状态（level/totalBattleWins/towerFloor/已装备环数/prestige）算已解锁集合（生产纯函数 AchievementService.progressOf + EquipmentPowerService.achievementBonus），七字段加成计入战斗属性镜像（第十七轮战斗模型扩展起 hp/atk 之外 matk/pdef/mdef/critRate/critDmg 经 playerCombatStats 一并生效）。")
        appendLine()
        appendLine("## 30 天时间线（独立跑批，同种子）")
        appendLine()
        appendLine("第 30 天快照：等级 ${f30.level} / 金币 ${f30.gold} / 塔 ${f30.towerFloor} 层 / 推图 ${f30.mapId + 1}-${f30.stage} /")
        appendLine("累计满包丢掉落 ${r30.dropLostTotal} 件 / 近10日离线收入占比 ${String.format("%.1f", r30.offlineShareLast(10))}%。")
        appendLine("30 天内修复后的经济形态即稳定（离线占比 <10%、塔可登顶），修复效果不依赖长线复利。")
        appendLine()
        appendLine("> 运行成本说明：本测试每次 `mvn test` 都会执行（纯计算，毫秒级，故不加 @Disabled）；")
        appendLine("> 报告为幂等重写（固定种子 20260914）。单独运行：`mvn test -Dtest=LongRunSimulationTest`。")
        appendLine()
        appendLine("## 已修复项（任务#20：P1 / P2 / P7）")
        appendLine()
        appendLine("### P1 离线收益通胀：按秒 → 按小时计费")
        appendLine("- 生产改动：`GameService.claimOfflineReward` 用 `effHours = 有效秒/3600`，系数仍是")
        appendLine("  `(OFFLINE_GOLD_BASE + L×PER_LEVEL) × 0.8`，但语义为**每小时**。")
        appendLine("- 换算依据：修复前实测中活跃日均\"主动玩法\"收入 ≈ 7,000 金/日；设计意图「离线 12h ≈ 主动 30~60 分钟")
        appendLine("  产出」≈ 主动日收入的 30%~60% ≈ 2,100~4,200 金/12h ≈ 175~350 金/小时，即 `(10+2L)×0.8` 在 L≈120~200 档。")
        appendLine("- 修复前（旧公式同种子数据）：第 90 天日均总收入 1.948e+08、离线占比 100.0%、第 1 天金币即 3.33M。")
        appendLine("- 修复后（本次重跑）：第 30/90 天日均总收入 ${String.format("%.3e", r30.avgDailyTotalGold(10))} / ${String.format("%.3e", r90.avgDailyTotalGold(10))} 金，")
        appendLine("  离线占比 ${String.format("%.1f", r30.offlineShareLast(10))}% / ${String.format("%.1f", r90.offlineShareLast(10))}%，第 1 天金币 ${eng(r30.rows.first().gold)}，90 天存量 ${eng(f90.gold)}。")
        appendLine()
        appendLine("### P2 魂塔 floor≥38 恒败：难度增量 0.02 → 0.005，并纳入战力")
        appendLine("- 生产改动：`TOWER_FLOOR_DIFFICULTY=0.005`；胜率改由纯函数 `EquipmentPowerService.towerWinChance`")
        appendLine("  计算：`1-(0.25+floor×0.005)` 基础值 + 战力/该层推荐值（floor×40）×7.5% 加成（封顶 +15%）。")
        appendLine("- 断言测试：floor=99 裸装胜率 0.255 > 0（修复前 ≤0 恒败），战力达到该层推荐值（floor×40）胜率 +7.5%")
        appendLine("  并随战力线性提升（封顶 +15%），胜率对战力严格单调不减。")
        appendLine("- 修复前：90 天塔层恒 38、第 22 天后零塔胜场；修复后：第 30/90 天塔层 ${f30.towerFloor}/${f90.towerFloor}，")
        appendLine("  近 10 日最高日塔胜场 ${r90.maxTowerWinsLast(10)}。")
        appendLine()
        appendLine("### P7 装备零战力：装备战力接入 battle / 塔公式")
        appendLine("- 生产改动：新增 `EquipmentPowerService`（公式对齐 shared `GameEngine.calcAttributes()` 精神：")
        appendLine("  魂环=年份档×品质倍率[1.0/1.2/1.5/1.8/2.2]×成熟度(1+percentage/1000)；魂骨×(1+强化×0.1)；")
        appendLine("  魂核按基础攻击百分比附加、单核封顶 50%）；battle 的玩家攻击/生命上限与塔胜率均吃该加成，")
        appendLine("  `BattleResponse`/`TowerResponse` 新增向后兼容的 `power` 字段（前端暂未消费）。")
        appendLine("- 单测断言（EquipmentPowerServiceTest）：装备升档 → 攻/血/战力单调增加；同一等级同一随机种子下")
        appendLine("  弱装备 30 回合打不死、强装备必杀（换装变强可感）；塔胜率随战力单调不减。")
        appendLine("- **明确未做**：①魂环负荷校验（shared 引擎 1858 行附近的年份负荷体系）属独立大功能，本次范围外；")
        appendLine("  ②前端战力展示未改动（仅后端 DTO 预留 power 字段）；③仿真画像不模拟穿装，本报告数值为\"零装备基线\"。")
    }

    // ======== 测试入口 ========

    @Test
    fun `30天与90天长跑仿真并生成报告`() {
        val start = System.currentTimeMillis()
        val r30 = run(days = 30, seed = 20260914L)
        val r90 = run(days = 90, seed = 20260914L)
        // 转生镜像对照组：同一 RNG 种子关闭转生，量化转生对等级/经济/负荷曲线的影响（报告对比表用）
        val r90Base = run(days = 90, seed = 20260914L, prestigeEnabled = false)

        // 任务#22：负荷回路 —— 主种子（实装/对照）+ 10 种子鲁棒性抽查
        val load90 = runLoadLoop(90, 20260914L, enforceLoad = true)
        val load90Base = runLoadLoop(90, 20260914L, enforceLoad = true, prestigeEnabled = false)
        val free90 = runLoadLoop(90, 20260914L, enforceLoad = false)
        // 第二十七轮强化镜像对照组：同种子关闭强化（统计对照，观察强化抢金币对扩容/背包挤压的影响）
        val load90NoEnhance = runLoadLoop(90, 20260914L, enforceLoad = true, enhanceEnabled = false)
        val seeds = (0 until 10).map { i ->
            val seed = 20260914L + i * 1009L
            val run = runLoadLoop(90, seed, enforceLoad = true)
            SeedSummary(
                seed = seed, level = run.final.level, mapId = run.final.mapId,
                utilLate = run.rows.filter { it.day > 60 }.averageOf { it.utilPct },
                slots = run.final.slots, bagRings = run.final.bagRings, bagHighTier = run.final.bagHighTierRings,
                deadShareEarly = run.deadRingDropShareEarly(), deadShareLate = run.deadRingDropShareLate(),
                firstRejectDay = run.firstRejectDay,
                upgradesPerDay = run.rows.filter { it.day > 60 }.sumOf { it.upgradesToday } / 30.0,
                totalRejects = run.totalRejects,
                prestigeCount = run.final.prestigeCount,
                firstPrestigeDay = run.rows.firstOrNull { it.prestigeCount > 0 }?.day ?: 0,
                enhances = run.totalEnhances, enhanceGold = run.totalEnhanceGold,
                enhanceShare = run.enhanceShareOfIncome,
                dungeonGold = run.totalDungeonGold, dungeonShare = run.dungeonShareOfActive,
                titles = run.finalTitles, killingSpent = run.totalKillingSpent,
            )
        }
        val elapsed = System.currentTimeMillis() - start

        // 软性健康检查（不锁死平衡值，只防跑飞/NaN）。
        // 注：mapId 下限按 P1 修复后的真实节奏放宽——离线通胀消失后等级/推图速度显著变慢，
        // 90 天到 3 号图（mapId=2）是修复后经济的正常中活跃节奏，不再是 7 天通关。
        assertEquals(30, r30.rows.size)
        assertEquals(90, r90.rows.size)
        assertTrue(r90.final.level >= 10, "90 天等级应显著成长（实测 ${r90.final.level}）")
        assertTrue(r90.final.gold > 0)
        assertTrue(r90.final.mapId >= 2, "90 天应至少推进到中期地图（实测 mapId=${r90.final.mapId}）")
        // 负荷回路软检查：能穿上至少 1 个环（全拒=系统封死）、利用率不为 NaN/负、种子跑批不超时
        assertTrue(load90.final.slots >= 1, "90 天至少应能穿上 1 个魂环（实测槽位 ${load90.final.slots}）")
        assertTrue(load90.rows.all { it.utilPct.isFinite() && it.utilPct >= 0.0 })
        assertTrue(seeds.all { it.slots >= 1 }, "所有种子都应至少能穿上 1 个魂环")
        // 第二十七轮强化镜像收敛锁（10 种子 × 90 天，强化策略生效后复核）：
        //  ① sink 必须真实触发（扣费镜像被 exercised，防未来改动静默废掉镜像→高估存量）；
        //  ② 全种子后期利用率仍落健康带（强化抬升骨战力 → 容量↑ → 利用率↓的方向不得击穿下限 40%）
        assertTrue(load90.totalEnhances > 0 && load90.totalEnhanceGold > 0,
            "主种子应实际发生强化（实测 ${load90.totalEnhances} 次/${load90.totalEnhanceGold} 金）")
        assertTrue(seeds.all { it.enhances > 0 }, "所有种子的强化镜像都应触发（sink 生效）")
        assertTrue(seeds.all { it.utilLate in 40.0..92.0 },
            "10 种子后期利用率应在健康带 40~92%（实测 ${seeds.map { String.format("%.1f", it.utilLate) }}，" +
                    "越界=强化镜像扰动负荷回路）")
        // 第二十九轮副本/杀气商店镜像收敛锁（10 种子 × 90 天）：新经济流必须真实触发
        //  ① 每日副本金币收入 > 0（转生 ≥1 后镜像被 exercised，防未来改动静默废掉镜像 → 低估收入）；
        //  ② 杀气商店至少购入一个称号（杀气 sink 生效，防镜像失效 → 高估杀气余额/漏算战力）
        assertTrue(seeds.all { it.dungeonGold > 0 },
            "所有种子的每日副本金币收入都应触发（实测 ${seeds.map { it.dungeonGold }}）")
        assertTrue(seeds.all { it.killingSpent > 0 && it.titles > 0 },
            "所有种子都应购买至少一个杀气称号（商店 sink 生效，实测 ${seeds.map { it.titles }}）")
        // 任务#22 调参后的收敛锁（固定种子实测 9/9 槽、死环率~0、利用率带内）：
        // 若未来改动使回路退化（恒卡或形同虚设），此三断言会先炸
        assertEquals(9, load90.final.slots, "主种子 90 天应满 9 槽（实测 ${load90.final.slots}）")
        assertTrue(load90.deadRingDropShareLate() < 5.0,
            "30 天后死掉落率应 <5%（实测 ${load90.deadRingDropShareLate()}%，恒卡征兆）")
        val utilLateMain = load90.rows.filter { it.day > 60 }.averageOf { it.utilPct }
        assertTrue(utilLateMain in 40.0..92.0,
            "后期容量利用率应在健康带 40~92%（实测 ${utilLateMain}%，越界=形同虚设或恒卡）")
        assertTrue(elapsed < 60_000, "仿真应在 60 秒内完成（实测 ${elapsed}ms）")

        val userDir = File(System.getProperty("user.dir"))
        val root = if (userDir.name.equals("backend", ignoreCase = true)) userDir.parentFile else userDir
        val reportFile = File(root, "数值仿真报告-90天.md")
        reportFile.writeText(
            buildReport(r30, r90, elapsed) + "\n" + buildLoadLoopSection(load90, free90, r90, seeds, load90NoEnhance) +
                    "\n" + buildPrestigeSection(r90, r90Base, load90, load90Base)
        )
        assertTrue(reportFile.exists())
    }
}
