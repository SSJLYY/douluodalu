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
    private val achievementService: AchievementService
) {
    companion object {
        val REALM_NAMES = listOf(
            "魂士", "魂师", "大魂师", "魂尊", "魂宗",
            "魂王", "魂帝", "魂圣", "魂斗罗", "封号斗罗",
            "极限斗罗", "半神", "神祇", "神王", "至高神王", "创世神"
        )
        val MAP_NAMES = listOf(
            "圣魂村", "诺丁城外", "星斗外围", "落日森林",
            "极北之地", "海神岛", "杀戮之都外域", "神界废墟"
        )

        /** 回合制战斗结算结果（纯函数产出，LongRunSimulationTest 直接复用保证镜像不漂移） */
        data class BattleOutcome(
            val won: Boolean,
            val rounds: Int,
            val playerHpLeft: Long,
            val log: List<BattleRoundLog>
        )

        /** 按地图/关卡生成怪物 (hp, atk)。镜像约定：仿真端调用同一函数。 */
        fun monsterStats(mapId: Int, stage: Int): Pair<Long, Int> {
            val monsterHp = ((GameBalance.MONSTER_HP_BASE + mapId * GameBalance.MONSTER_HP_PER_MAP) *
                    (1.0 + stage * GameBalance.MONSTER_STAGE_GROWTH)).toLong()
            val monsterAtk = ((GameBalance.MONSTER_ATK_BASE + mapId * GameBalance.MONSTER_ATK_PER_MAP) *
                    (1.0 + stage * GameBalance.MONSTER_STAGE_GROWTH)).toInt()
            return monsterHp to monsterAtk
        }

        /**
         * 纯函数回合结算：玩家先手，攻击浮动 ±20%（atk + rng[0, atk/5)），30 回合内击杀即胜。
         * rng 显式注入，便于测试用固定种子断言「装备更好 → 结果单调不减」。
         */
        fun resolveBattle(
            playerAtk: Long,
            playerHp: Long,
            monsterHp: Long,
            monsterAtk: Int,
            maxRounds: Int,
            rng: Random
        ): BattleOutcome {
            var pHp = playerHp
            var mHp = monsterHp
            var rounds = 0
            val log = mutableListOf<BattleRoundLog>()
            while (rounds < maxRounds && pHp > 0 && mHp > 0) {
                rounds++
                val playerHpBefore = pHp
                val monsterHpBefore = mHp

                val pDmg = playerAtk + rng.nextLong(playerAtk / 5)
                mHp -= pDmg
                if (mHp <= 0) {
                    log.add(BattleRoundLog(rounds, playerHpBefore, monsterHpBefore, pDmg, 0, pHp, 0))
                    break
                }

                val mDmg = monsterAtk.toLong() + rng.nextLong((monsterAtk / 5).coerceAtLeast(1).toLong())
                pHp -= mDmg
                log.add(BattleRoundLog(rounds, playerHpBefore, monsterHpBefore, pDmg, mDmg, pHp, mHp))
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
         *  - 塔怪属性从楼层推导（推导依据见 GameBalance.TOWER_LOG_* 注释）；
         *  - 玩家口径与 battle() 调 resolveBattle 时一致：攻击 = 基础 + 等级 + 装备加成；
         *    生命用满血（塔挑战以满血状态进行，与战败后回满的既有语义一致）。
         */
        fun buildTowerBattleLog(
            userId: Long,
            floor: Int,
            won: Boolean,
            playerAtk: Long,
            playerMaxHp: Long
        ): List<BattleRoundLog> {
            val baseHp = GameBalance.MONSTER_HP_BASE + floor * GameBalance.TOWER_LOG_MONSTER_HP_PER_FLOOR
            val baseAtk = GameBalance.MONSTER_ATK_BASE + floor * GameBalance.TOWER_LOG_MONSTER_ATK_PER_FLOOR
            val rng = Random(towerLogSeed(userId, floor))
            var factor = 1.0
            var outcome = resolveBattle(
                playerAtk = playerAtk,
                playerHp = playerMaxHp,
                monsterHp = (baseHp * factor).toLong().coerceAtLeast(1),
                monsterAtk = (baseAtk * factor).toInt().coerceAtLeast(1),
                maxRounds = GameBalance.MAX_BATTLE_ROUNDS,
                rng = rng
            )
            var attempts = 0
            while (outcome.won != won && attempts < 8) {
                attempts++
                // 期望胜 → 削怪（/1.5 递进）；期望败 → 强怪（×1.5 递进）；每轮换一批新掷点（同一种子流）
                factor = if (won) factor / 1.5 else factor * 1.5
                outcome = resolveBattle(
                    playerAtk = playerAtk,
                    playerHp = playerMaxHp,
                    monsterHp = (baseHp * factor).toLong().coerceAtLeast(1),
                    monsterAtk = (baseAtk * factor).toInt().coerceAtLeast(1),
                    maxRounds = GameBalance.MAX_BATTLE_ROUNDS,
                    rng = rng
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
        // 成就系统集成：已解锁成就的 hp/atk 加成并入 bonus（power/容量/明细五行同口径即时生效）
        val achBonus = achievementService.unlockedBonus(userId)
        val bonus = EquipmentPowerService.bonus(profile.level, rings, bones, cores, achBonus)
        return GameStateResponse(
            profile = toProfileDto(profile),
            equippedRings = equippedRings,
            equippedBones = equippedBones,
            equippedCores = equippedCores,
            backpackItems = backpackRepo.findByUserIdOrderByCreatedAtAsc(userId).map { toBackpackItemDto(it) },
            talents = talents,
            achievements = achievementService.getStatus(userId),
            power = EquipmentPowerService.powerOf(profile.level, bonus),
            ringLoad = RingLoadCalculator.totalRingLoad(rings),
            capacity = absorptionCapacityFor(profile, bonus),
            // 任务#23：战力明细（复用同一 rings/bones/cores 列表与公式，纯内存拆分，不再查库；五行含成就行）
            powerDetail = EquipmentPowerService.detail(profile.level, rings, bones, cores, achBonus),
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
        val gain = baseGain + Random.nextLong(baseGain / GameBalance.CULTIVATE_RANDOM_DIVISOR)
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
        // 成就挂点（副路径）：仅突破成功出口触发（等级达标即自动解锁，失败不击穿主流程）
        achievementService.sync(userId)
        return BreakthroughResponse(true, profile.level, "突破成功！当前境界：${getRealmName(profile.level)} Lv.${profile.level}")
    }

    @Transactional
    fun battle(userId: Long): BattleResponse {
        val profile = getProfile(userId)
        val mapId = profile.currentMapId
        val stage = profile.currentStage

        // 装备战力接入（P7 修复）+ 成就属性加成（bonusFor 唯一 choke point，即时生效）
        val equip = equipmentPowerService.bonusFor(userId, profile.level)
        val power = EquipmentPowerService.powerOf(profile.level, equip)

        // 生成怪物
        val (monsterHp, monsterAtk) = monsterStats(mapId, stage)
        val monsterName = "${MAP_NAMES.getOrElse(mapId) { "未知" }}·${stage}层怪物"

        // 战斗计算：攻击力 = 等级基础 + 装备加成；生命上限同样吃装备 hpBonus（战败回满时生效）
        val playerAtk = GameBalance.PLAYER_ATK_BASE + profile.level * GameBalance.PLAYER_ATK_PER_LEVEL + equip.atkBonus
        val maxHp = getMaxHp(profile.level) + equip.hpBonus
        val outcome = resolveBattle(
            playerAtk = playerAtk,
            playerHp = profile.currentHp.coerceAtMost(maxHp),
            monsterHp = monsterHp,
            monsterAtk = monsterAtk,
            maxRounds = GameBalance.MAX_BATTLE_ROUNDS,
            rng = Random
        )
        val won = outcome.won
        val playerHp = outcome.playerHpLeft
        val rounds = outcome.rounds
        val battleLog = outcome.log
        val expGained = if (won)
            (GameBalance.WIN_EXP_BASE + mapId * GameBalance.WIN_EXP_PER_MAP + stage * GameBalance.WIN_EXP_PER_STAGE)
        else 0L
        val goldGained = if (won)
            (GameBalance.WIN_GOLD_BASE + mapId * GameBalance.WIN_GOLD_PER_MAP + stage * GameBalance.WIN_GOLD_PER_STAGE)
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
                    val yearOrdinal = (mapId / 2).coerceIn(0, 4)
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

        return BattleResponse(
            won = won, rounds = rounds, monsterName = monsterName, monsterMaxHp = monsterHp,
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
        // 成就属性加成经 bonusFor 并入（唯一 choke point，即时生效）
        val equip = equipmentPowerService.bonusFor(userId, profile.level)
        val power = EquipmentPowerService.powerOf(profile.level, equip)
        val won = Random.nextDouble() < EquipmentPowerService.towerWinChance(profile.towerFloor, power)
        val monsterName = GameBalance.TOWER_MONSTERS[Random.nextInt(GameBalance.TOWER_MONSTERS.size)]
        val rounds = 4 + Random.nextInt(6)
        val expGained = if (won) GameBalance.TOWER_EXP_BASE + towerLevel * GameBalance.TOWER_EXP_PER_LEVEL else 0L
        val goldGained = if (won) GameBalance.TOWER_GOLD_BASE + towerLevel * GameBalance.TOWER_GOLD_PER_LEVEL else 0L
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
            profile.currentHp = getMaxHp(profile.level) + equip.hpBonus
        }
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)

        // 每日任务挂点（副路径）：挑战即计数，不论胜负（失败不击穿主流程，见 DailyQuestService）
        dailyQuestService.recordTower(userId)
        // 成就挂点（副路径）：塔层/胜场跳变点，胜败都可触发（失败不击穿主流程，见 AchievementService）
        achievementService.sync(userId)

        // 塔战逐回合日志：插在全部既有掷点之后、用独立种子模拟（不改胜负判定、不动 RNG 次序，
        // LongRunSimulationTest 的 towerWinChance/resolveBattle 镜像契约不受影响）。
        // 玩家属性口径与 battle() 调 resolveBattle 时一致；生命用满血（塔挑战以满血进行，与战败回满语义一致）。
        val towerPlayerAtk = GameBalance.PLAYER_ATK_BASE + profile.level * GameBalance.PLAYER_ATK_PER_LEVEL + equip.atkBonus
        val towerPlayerMaxHp = getMaxHp(profile.level) + equip.hpBonus
        val battleLog = buildTowerBattleLog(userId, foughtFloor, won, towerPlayerAtk, towerPlayerMaxHp)

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
        val effectiveHours = effectiveSeconds / 3600.0
        val goldPerHour = (GameBalance.OFFLINE_GOLD_BASE + profile.level * GameBalance.OFFLINE_GOLD_PER_LEVEL) *
                GameBalance.OFFLINE_EFFICIENCY
        val expPerHour = (GameBalance.OFFLINE_EXP_BASE + profile.level * GameBalance.OFFLINE_EXP_PER_LEVEL) *
                GameBalance.OFFLINE_EFFICIENCY
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
        tutorialStep = p.tutorialStep
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

    /** 玩家魂环吸收容量：根骨×GameBalance.RING_CAPACITY_ROOT_MULT。攻击/生命与战斗结算同源（含装备加成），matk/pdef/mdef 后端未建模取 0 */
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
