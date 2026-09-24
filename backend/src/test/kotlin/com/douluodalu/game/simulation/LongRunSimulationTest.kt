package com.douluodalu.game.simulation

import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.service.EquipmentBonus
import com.douluodalu.game.service.EquipmentPowerService
import com.douluodalu.game.service.GameService
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
 * 平衡常量直接引用 GameBalance（单一事实来源），但公式结构如有改动需同步本文件。
 *
 * 断言刻意只放软性的健康检查（跑通、数量级不离谱）；主要产出是根目录
 * 《数值仿真报告-90天.md》，内容确定（固定随机种子）、幂等重写。
 */
class LongRunSimulationTest {

    // ======== 镜像状态 ========

    private class DayStats {
        var offlineGold = 0L; var offlineExp = 0L; var offlineWins = 0L
        var battleGold = 0L; var battleWins = 0L; var battleLosses = 0L
        var towerGold = 0L; var towerWins = 0L; var towerLosses = 0L
        var sellGold = 0L; var bossCoins = 0L
        var dropsGained = 0L; var dropsLost = 0L
        var breakthroughs = 0
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

        private fun rndLong(bound: Long): Long = if (bound <= 0) 0 else rng.nextLong(bound)
        private fun rndInt(bound: Int): Int = if (bound <= 0) 0 else rng.nextInt(bound)

        // ---- 公式镜像自 GameService.cultivate ----
        fun cultivate() {
            val baseGain = GameBalance.CULTIVATE_BASE_GAIN + level * GameBalance.CULTIVATE_LEVEL_GAIN_FACTOR
            soulPower += baseGain + rndLong(baseGain / GameBalance.CULTIVATE_RANDOM_DIVISOR)
        }

        // ---- 公式镜像自 GameService.getBreakthroughCost / breakthrough ----
        fun breakthroughCost(l: Int): Long = (120.0 * Math.pow(l.toDouble(), 1.55)).toLong()

        /** autoBreakthrough=true 的客户端行为：魂力够就一直点。返回突破次数 */
        fun breakthroughAll(): Int {
            var n = 0
            while (n < 100_000 && soulPower >= breakthroughCost(level)) {
                soulPower -= breakthroughCost(level)
                level += 1
                n++
            }
            return n
        }

        // ---- battle 镜像：直接调用生产纯函数 GameService.monsterStats/resolveBattle（P7 修复后同源）----
        fun battle(s: DayStats) {
            val oldMap = mapId
            val oldStage = stage          // 生产代码掉落/奖励均用战前快照
            val (monsterHp, monsterAtk) = GameService.monsterStats(oldMap, oldStage)
            // 画像不模拟穿装 → 装备攻击加成恒为 0（P7 修复效果在报告「已修复项」中说明）
            val playerAtk = GameBalance.PLAYER_ATK_BASE + level * GameBalance.PLAYER_ATK_PER_LEVEL
            val outcome = GameService.resolveBattle(
                playerAtk, hp, monsterHp, monsterAtk, GameBalance.MAX_BATTLE_ROUNDS, rng
            )
            if (!outcome.won) {                                                // 30 回合未杀 → 败
                s.battleLosses++
                hp = getMaxHp()                                                 // 死亡回满血
                stage = 1                                                       // 退回第 1 关
                return
            }
            s.battleWins++
            val goldGained = GameBalance.WIN_GOLD_BASE + oldMap * GameBalance.WIN_GOLD_PER_MAP +
                    oldStage * GameBalance.WIN_GOLD_PER_STAGE
            val expGained = GameBalance.WIN_EXP_BASE + oldMap * GameBalance.WIN_EXP_PER_MAP +
                    oldStage * GameBalance.WIN_EXP_PER_STAGE
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
            val power = EquipmentPowerService.powerOf(level, EquipmentBonus(0, 0))
            val won = rng.nextDouble() < EquipmentPowerService.towerWinChance(towerFloor, power)
            if (!won) { s.towerLosses++; return }
            s.towerWins++
            val towerLevel = towerFloor * GameBalance.TOWER_LEVEL_PER_FLOOR
            val goldGained = GameBalance.TOWER_GOLD_BASE + towerLevel * GameBalance.TOWER_GOLD_PER_LEVEL
            gold += goldGained; s.towerGold += goldGained
            soulPower += GameBalance.TOWER_EXP_BASE + towerLevel * GameBalance.TOWER_EXP_PER_LEVEL
            if (rng.nextDouble() < GameBalance.TOWER_BOSS_COIN_CHANCE) { bossCoin += 1; s.bossCoins += 1 }
            towerFloor = min(GameBalance.TOWER_MAX_FLOOR, towerFloor + 1)
            if (rng.nextDouble() < GameBalance.TOWER_DROP_CHANCE) {
                // rollBackpackDrop：空间不足返回 null（静默丢失）；品质=min(4, level/8 + nextInt(3))
                if (items.size < capacity) {
                    items.add(min(4, level / 8 + rndInt(3))); s.dropsGained++
                } else { dropLostTotal++; s.dropsLost++ }
            }
        }

        // ---- 公式镜像自 GameService.claimOfflineReward（P1 修复：按小时计费 + 12h 截断）----
        fun claimOffline(nowHours: Double, s: DayStats) {
            val offlineSeconds = ((nowHours - lastLogoutHours) * 3600).toLong().coerceAtLeast(0)
            lastLogoutHours = nowHours
            val eff = min(offlineSeconds, GameBalance.OFFLINE_MAX_SECONDS)
            offlineWastedSeconds += offlineSeconds - eff
            if (eff < 60) return
            val effHours = eff / 3600.0
            val goldPerHour = (GameBalance.OFFLINE_GOLD_BASE + level * GameBalance.OFFLINE_GOLD_PER_LEVEL) *
                    GameBalance.OFFLINE_EFFICIENCY
            val expPerHour = (GameBalance.OFFLINE_EXP_BASE + level * GameBalance.OFFLINE_EXP_PER_LEVEL) *
                    GameBalance.OFFLINE_EFFICIENCY
            val goldGained = (goldPerHour * effHours).toLong()
            gold += goldGained; s.offlineGold += goldGained
            val expGained = (expPerHour * effHours).toLong()
            soulPower += expGained; s.offlineExp += expGained
            val wins = eff / GameBalance.OFFLINE_SECONDS_PER_BATTLE_WIN
            offlineBattleWins += wins; s.offlineWins += wins
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
    )

    private class SimOutcome(
        val rows: List<DayRow>,
        val dropLostTotal: Long,
        val dropsGainedTotal: Long,
        val offlineWastedHours: Double,
    ) {
        val final: DayRow get() = rows.last()
        fun last(n: Int) = rows.takeLast(n)
        fun offlineShareLast(n: Int): Double {
            val ls = last(n)
            val off = ls.sumOf { it.stats.offlineGold }
            val tot = ls.sumOf { it.stats.offlineGold + it.stats.battleGold + it.stats.towerGold + it.stats.sellGold }
            return if (tot == 0L) 0.0 else off * 100.0 / tot
        }
        fun avgDailyTotalGold(n: Int): Double =
            last(n).sumOf { it.stats.offlineGold + it.stats.battleGold + it.stats.towerGold + it.stats.sellGold } / n.toDouble()
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
    private fun run(days: Int, seed: Long): SimOutcome {
        val p = SimPlayer(seed)
        val rows = ArrayList<DayRow>(days)
        var gained = 0L
        var stuckStreak = 0
        val loginHours = listOf(8.0, 14.0, 22.0)
        val battlesPerSession = listOf(6, 8, 6)
        val towersPerSession = listOf(5, 5, 8)
        for (d in 1..days) {
            val s = DayStats()
            for (i in loginHours.indices) {
                val t = (d - 1) * 24.0 + loginHours[i]
                p.claimOffline(t, s)
                repeat(8) { p.cultivate() }
                s.breakthroughs += p.breakthroughAll()
                repeat(battlesPerSession[i]) { p.battle(s) }
                repeat(towersPerSession[i]) { p.tower(s) }
                s.sellGold += p.sellJunk()
                p.buyExpansions()
            }
            gained += s.dropsGained
            // "突破卡点"定义：当日一次突破都没成功
            stuckStreak = if (s.breakthroughs == 0) stuckStreak + 1 else 0
            rows.add(DayRow(d, s, p.level, p.gold, p.soulPower, p.bossCoin,
                p.mapId, p.stage, p.towerFloor, p.items.size, p.capacity, stuckStreak))
        }
        return SimOutcome(rows, p.dropLostTotal, gained, p.offlineWastedSeconds / 3600.0)
    }

    // ======== 报告生成 ========

    private fun eng(v: Long): String = when {
        v >= 1_000_000_000_000L -> String.format("%.2fT", v / 1e12)
        v >= 1_000_000_000L -> String.format("%.2fB", v / 1e9)
        v >= 1_000_000L -> String.format("%.2fM", v / 1e6)
        v >= 10_000L -> String.format("%.1fk", v / 1e3)
        else -> v.toString()
    }

    private fun fmtRow(r: DayRow): String =
        "| ${r.day} | ${r.level} | ${eng(r.gold)} | ${eng(r.soulPower)} | ${eng(r.bossCoin)} " +
                "| ${r.mapId + 1}-${r.stage} | ${r.towerFloor} | ${r.bagCount}/${r.capacity} " +
                "| ${eng(r.stats.offlineGold)} | ${eng(r.stats.battleGold + r.stats.towerGold + r.stats.sellGold)} " +
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
        appendLine("- 镜像范围：cultivate / breakthrough(120·L^1.55) / battle（调用生产纯函数 monsterStats+resolveBattle，")
        appendLine("  HP 跨场次持久化、败退回到 1 关）/ towerBattle（调用生产纯函数 towerWinChance）/")
        appendLine("  claimOfflineReward（12h 截断、P1 修复后按**小时**计费）/ 掉落与背包容量 / 扩展券。")
        appendLine("- 未建模：宗门 Boss、天赋、穿装行为——画像只捡/卖装备不穿戴，故装备战力加成按 0 计")
        appendLine("  （装备对战力的贡献已由 EquipmentPowerServiceTest 单测覆盖，见「已修复项」P7）。")
        appendLine()
        appendLine("## 关键结果")
        appendLine()
        appendLine("| 指标 | 第 30 天 | 第 90 天 |")
        appendLine("|---|---|---|")
        appendLine("| 等级 | ${f30.level} | ${f90.level} |")
        appendLine("| 金币存量 | ${f30.gold} | ${f90.gold} |")
        appendLine("| 近10日日均总收入(金币) | ${String.format("%.3e", r30.avgDailyTotalGold(10))} | ${String.format("%.3e", r90.avgDailyTotalGold(10))} |")
        appendLine("| 近10日日均\"主动玩法\"收入(战斗+塔+卖装备) | ${String.format("%.0f", r30.avgDailyActiveGold(10))} | ${String.format("%.0f", r90.avgDailyActiveGold(10))} |")
        appendLine("| 近10日离线收入占比 | ${String.format("%.1f", r30.offlineShareLast(10))}% | ${String.format("%.1f", r90.offlineShareLast(10))}% |")
        appendLine("| 近10日日均 Boss 币 | ${String.format("%.0f", r30.avgDailyBossCoin(10))} | ${String.format("%.0f", r90.avgDailyBossCoin(10))} |")
        appendLine("| 近10日日均离线折算\"战斗胜利\" | ${String.format("%.0f", r30.avgDailyOfflineWins(10))} | ${String.format("%.0f", r90.avgDailyOfflineWins(10))} |")
        appendLine("| 推图进度(地图-关卡) | ${f30.mapId + 1}-${f30.stage} | ${f90.mapId + 1}-${f90.stage} |")
        appendLine("| 推图全通关(抵达7图)所需天数 | 第 ${r30.firstDay { mapId >= GameBalance.MAX_MAP_ID }} 天 | 第 ${r90.firstDay { mapId >= GameBalance.MAX_MAP_ID }} 天 |")
        appendLine("| 魂塔层数 | ${f30.towerFloor} | ${f90.towerFloor} |")
        appendLine("| 累计满包丢掉落 | ${r30.dropLostTotal} | ${r90.dropLostTotal} |")
        appendLine("| 连续无法突破最长天数 | ${r30.rows.maxOf { it.stuckStreak }} | ${r90.rows.maxOf { it.stuckStreak }} |")
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
        appendLine("| 天 | 等级 | 金币存量 | 魂力 | Boss币 | 推图 | 塔层 | 背包 | 当日离线金 | 当日主动金 | 满包丢掉落 | 连续未突破天数 |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|")
        r90.rows.forEach { appendLine(fmtRow(it)) }
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
        val elapsed = System.currentTimeMillis() - start

        // 软性健康检查（不锁死平衡值，只防跑飞/NaN）。
        // 注：mapId 下限按 P1 修复后的真实节奏放宽——离线通胀消失后等级/推图速度显著变慢，
        // 90 天到 3 号图（mapId=2）是修复后经济的正常中活跃节奏，不再是 7 天通关。
        assertEquals(30, r30.rows.size)
        assertEquals(90, r90.rows.size)
        assertTrue(r90.final.level >= 10, "90 天等级应显著成长（实测 ${r90.final.level}）")
        assertTrue(r90.final.gold > 0)
        assertTrue(r90.final.mapId >= 2, "90 天应至少推进到中期地图（实测 mapId=${r90.final.mapId}）")
        assertTrue(elapsed < 10_000, "仿真应在 10 秒内完成（实测 ${elapsed}ms）")

        val userDir = File(System.getProperty("user.dir"))
        val root = if (userDir.name.equals("backend", ignoreCase = true)) userDir.parentFile else userDir
        val reportFile = File(root, "数值仿真报告-90天.md")
        reportFile.writeText(buildReport(r30, r90, elapsed))
        assertTrue(reportFile.exists())
    }
}
