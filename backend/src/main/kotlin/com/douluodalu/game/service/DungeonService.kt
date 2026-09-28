package com.douluodalu.game.service

import com.douluodalu.game.dto.BackpackItemDto
import com.douluodalu.game.dto.DungeonFightResponse
import com.douluodalu.game.dto.DungeonStateDto
import com.douluodalu.game.dto.DungeonSweepResponse
import com.douluodalu.game.dto.DungeonTierStateDto
import com.douluodalu.game.entity.DungeonProgressEntity
import com.douluodalu.game.entity.PlayerProfileEntity
import com.douluodalu.game.exception.PlayerSaveNotFoundException
import com.douluodalu.game.model.GameBalance
import com.douluodalu.game.repository.DungeonProgressRepository
import com.douluodalu.game.repository.PlayerProfileRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 每日副本（第二十九轮，设计文档 §8）：五大难度每天一次挑战机会 + 通关后扫荡。
 *
 * 【每日节奏——实现口径】设计文档 §8 原文「重置时间：每日凌晨（每天一次机会）」，且 V13
 * 进度表以单列 challenge_date 记录「当日已挑战日期」——故每日节奏取【每天一次机会（任选
 * 一个已解锁难度）】：战斗（胜或败）与扫荡都占用当日名额（「胜或败都算当日已挑战，防刷」；
 * 扫荡拿「同当日唯一一次奖励」——战斗与扫荡共用名额，不可同日双拿）。每个难度每天至多
 * 被参与一次由该规则自然保证。跨天重置为惰性式（照 DailyQuestService 的 LocalDate.now() +
 * 日期列模式，JVM 本地时区、serverTimezone=Asia/Shanghai 同一口径）：读路径读到旧日期
 * 视为「机会未用、tier_completed=-1」不写库；写路径（fight/sweep）落库前归位，无定时任务。
 *
 * 【Boss 数值】以玩家当前推图怪为基准：GameService.monsterStats(currentMapId, currentStage)
 * （与 battle() 的怪物构造完全同源），HP × hpMult、攻击 × atkMult（§8 表逐位）；怪 matk
 * 镜像攻击、双防 = 放大后攻击 × MONSTER_DEF_FACTOR（monsterStats 的构造约定——设计文档
 * 未定义独立防御倍率，防御随攻击同步放大以保持「防=攻×0.35」的内部一致性）。战斗结算
 * 复用 battle 的回合制口径（resolveBattle：power 对比实打 + 随机掷点），回合日志同构
 * （前端复用 BattleReplay 回放）；玩家属性/生命口径与 battle() 逐项一致
 * （playerCombatStats + schoolScaledMaxHp，含装备/成就/武魂/流派与转生倍率）。
 *
 * 【奖励】金币 × GameBalance.prestigeMultiplier（收入口径③，与 battle/tower 一致）；
 * 杀气按名义值直加（稀缺货币不乘倍率，同签到/任务固定表口径）；掉落调
 * GameService.rollBackpackDrop(userId, GameBalance.dungeonDropLevel(dropTier))——掉落
 * 成功与否遵循 rollBackpackDrop 内部口径（背包有空间必掉，背包满则丢失并在响应提示），
 * 本服务不再叠加独立概率（与塔的 TOWER_DROP_CHANCE 外层概率分层：副本奖励是一次性的
 * 大额产出，掉落确定性是难度选择的预期管理）。失败无奖励。
 *
 * 【进度写入】胜局：tier_completed = 难度、ever_cleared 置位（历史通关，扫荡前置）；
 * 败局：tier_completed 保持 -1、challenge_date 照写（占当日名额）。扫荡不写
 * tier_completed（未战斗无「通关」事实）、不置 ever_cleared（历史已通关才能扫荡）。
 * 胜败都计 totalBattleWins/Losses（与 battle/tower 的胜负统计口径一致），胜局为
 * totalBattleWins 跳变点 → achievementService.sync（副路径）；每日任务无副本类任务 id，
 * 不挂钩点（口径依据：DAILY_QUESTS 七任务与 §8 副本无交集）。
 *
 * 【无循环依赖】本类依赖 GameService（rollBackpackDrop 实例方法 + companion 静态构造），
 * GameService 不反向依赖本类。
 */
@Service
class DungeonService(
    private val dungeonProgressRepo: DungeonProgressRepository,
    private val profileRepo: PlayerProfileRepository,
    /** 掉落入库（rollBackpackDrop）与怪物/战斗构造静态成员的宿主；无反向依赖不成环 */
    private val gameService: GameService,
    private val equipmentPowerService: EquipmentPowerService,
    /** 成就挂点（副路径）：胜局 totalBattleWins 跳变点（失败不击穿主流程） */
    private val achievementService: AchievementService,
    /** 业务计数器（Micrometer，Spring Boot 自动配置 bean）；测试注入 SimpleMeterRegistry */
    private val meterRegistry: MeterRegistry
) {
    companion object {
        /** 与 DTO/前端契约一致的固定文案：IllegalArgumentException → GlobalExceptionHandler 统一 400 */
        const val MSG_INVALID_TIER = "无效的副本难度"
        const val MSG_ALREADY_CHALLENGED = "今日副本机会已用（胜败都算已挑战），明天再来吧"
        const val MSG_NOT_CLEARED = "该难度尚未通关，无法扫荡"
        const val MSG_BACKPACK_FULL = "背包已满，掉落物品丢失，请及时整理背包"

        /** 业务计数器名（ops Grafana 面板按名建面板，逐字契约，勿改） */
        const val METRIC_DUNGEON_FIGHT_TOTAL = "douluo.dungeon.fight.total"
        const val METRIC_DUNGEON_SWEEP_TOTAL = "douluo.dungeon.sweep.total"

        fun lockedMessage(def: GameBalance.DungeonDef): String =
            "未解锁：${def.name}需要${def.unlockPrestige}转"
    }

    // ======== 状态查询（只读，不建行） ========

    /**
     * 五难度状态合成：解锁/今日已挑战/今日已通关/可扫荡/奖励预览。
     * 读路径不建行、不写库（无行 = 从未参与：challengedToday=false、tierCompleted=-1）。
     * 奖励预览的金币已乘转生倍率（与结算同式），扫荡费用按当前等级现算——前端零镜像。
     */
    @Transactional(readOnly = true)
    fun getState(userId: Long): DungeonStateDto {
        val profile = getProfile(userId)
        val row = dungeonProgressRepo.findById(userId).orElse(null)
        val today = LocalDate.now()
        // 惰性重置的读侧口径：challengeDate != today → 当日机会未用、tierCompleted 视为 -1
        val challengedToday = row?.challengeDate == today
        val tierCompleted = if (challengedToday) row!!.tierCompleted else -1
        val mult = GameBalance.prestigeMultiplier(profile.prestigeCount)
        val sweepCost = GameBalance.dungeonSweepSoulPowerCost(profile.level)
        return DungeonStateDto(
            date = today.toString(),
            challengedToday = challengedToday,
            tierCompleted = tierCompleted,
            tiers = GameBalance.DUNGEON_DEFS.mapIndexed { tier, def ->
                val unlocked = profile.prestigeCount >= def.unlockPrestige
                DungeonTierStateDto(
                    tier = tier,
                    name = def.name,
                    difficultyName = def.difficultyName,
                    bossName = def.bossName,
                    hpMult = def.hpMult,
                    atkMult = def.atkMult,
                    goldReward = (def.goldReward * mult).toLong(),
                    killingReward = def.killingReward,
                    dropTier = def.dropTier,
                    unlockPrestige = def.unlockPrestige,
                    unlocked = unlocked,
                    challengedToday = challengedToday,
                    // 当日最高已通关难度覆盖到的层级都算「今日已通关」（一天至多一次胜局）
                    clearedToday = challengedToday && tierCompleted >= tier,
                    sweepable = unlocked && row != null &&
                            GameBalance.dungeonHasCleared(row.everCleared, tier) && !challengedToday,
                    sweepSoulPowerCost = sweepCost
                )
            }
        )
    }

    // ======== 挑战 ========

    /**
     * 挑战难度 tier（0~4）：校验（层级合法 → 解锁 → 当日机会未用）→ 惰性重置当日进度 →
     * 复用 battle 口径实打（resolveBattle）→ 胜局发奖 + 写通关标记，败局零奖励。
     * 资格类前置不满足（层级/解锁/当日已挑战）抛 IllegalArgumentException → 统一 400
     * （与 DailyQuestService.claim 的「未达标/已领取」同款资格校验口径）。
     */
    @Transactional
    fun fight(userId: Long, tier: Int): DungeonFightResponse {
        val def = GameBalance.dungeonDefByTier(tier) ?: throw IllegalArgumentException(MSG_INVALID_TIER)
        val profile = getProfile(userId)
        if (profile.prestigeCount < def.unlockPrestige) throw IllegalArgumentException(lockedMessage(def))
        val today = LocalDate.now()
        var row = dungeonProgressRepo.findById(userId).orElse(null)
        if (row != null && row.challengeDate == today) throw IllegalArgumentException(MSG_ALREADY_CHALLENGED)
        if (row == null) row = DungeonProgressEntity(userId = userId)
        // 跨天惰性重置（写侧落库归位）：新的一天当日完成标记从 -1 开始
        row.tierCompleted = -1
        row.challengeDate = today

        // 玩家战斗属性组装：与 battle() 逐项同源（装备+成就加成经 bonusFor 乘转生倍率、
        // 武魂/流派同口径并入），保证副本胜负与推图战力一致可比
        val equip = EquipmentPowerService.plus(
            equipmentPowerService.bonusFor(userId, profile.level, profile.prestigeCount),
            GameService.soulBonusOf(profile.martialSoulName, profile.prestigeCount)
        )
        val school = GameService.schoolModsOf(profile.chosenSchool)
        val power = EquipmentPowerService.powerOf(profile.level, EquipmentPowerService.applySchool(equip, school))
        val maxHp = schoolScaledMaxHp(profile, equip.hpBonus, school)
        val player = GameService.playerCombatStats(
            profile.level, profile.prestigeCount, equip, school,
            GameService.soulSkillOf(profile.martialSoulName)
        )
        val boss = dungeonMonster(profile, def)
        val outcome = GameService.resolveBattle(
            player = player,
            playerHp = profile.currentHp.coerceAtMost(maxHp),
            monster = boss,
            maxRounds = GameBalance.MAX_BATTLE_ROUNDS,
            rng = Random,
            playerMaxHp = maxHp
        )
        val won = outcome.won

        var goldGained = 0L
        var killingGained = 0
        var dropLostMessage: String? = null
        val drops = mutableListOf<BackpackItemDto>()
        if (won) {
            // 转生倍率（收入口径③）：金币 ×(1+转数×0.1)；杀气直加不乘倍率（稀缺货币，见类注释）
            val mult = GameBalance.prestigeMultiplier(profile.prestigeCount)
            goldGained = (def.goldReward * mult).toLong()
            killingGained = def.killingReward
            profile.gold += goldGained
            profile.killingIntent += killingGained
            profile.totalBattleWins += 1
            // 胜利保留战果血量（battle 同款，下限 1）；败局回满血（与 battle 战败语义一致）
            profile.currentHp = outcome.playerHpLeft.coerceAtLeast(1)
            row.tierCompleted = tier
            row.everCleared = row.everCleared or GameBalance.dungeonClearedBit(tier)
            // 掉落：level = dropTier×12（命中对应魂环年份档，换算依据见 GameBalance 注释）；
            // 背包满 → rollBackpackDrop 返回 null，照 battle 口径给出丢失提示
            val drop = gameService.rollBackpackDrop(userId, GameBalance.dungeonDropLevel(def.dropTier))
            if (drop != null) drops.add(drop) else dropLostMessage = MSG_BACKPACK_FULL
            // 成就挂点（副路径）：totalBattleWins 跳变点（失败不击穿主流程，见 AchievementService）
            achievementService.sync(userId)
        } else {
            profile.totalBattleLosses += 1
            profile.currentHp = maxHp
        }
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        dungeonProgressRepo.save(row)
        // 业务计数器：副本挑战结算（胜败都计；Micrometer 不抛业务异常，不影响主流程）
        meterRegistry.counter(METRIC_DUNGEON_FIGHT_TOTAL, "tier", tier.toString(),
            "outcome", if (won) "win" else "lose").increment()

        return DungeonFightResponse(
            won = won,
            rounds = outcome.rounds,
            tier = tier,
            monsterName = def.bossName,
            monsterMaxHp = boss.hp,
            goldGained = goldGained,
            killingGained = killingGained,
            drops = drops,
            playerHp = profile.currentHp,
            playerLevel = profile.level,
            playerGold = profile.gold,
            playerKillingIntent = profile.killingIntent,
            battleLog = outcome.log,
            message = dropLostMessage,
            power = power
        )
    }

    // ======== 扫荡 ========

    /**
     * 扫荡难度 tier：不战斗直接拿该难度的金币+杀气+掉落（同当日唯一一次奖励名额），
     * 消耗魂力 GameBalance.dungeonSweepSoulPowerCost(level)（定价依据见 GameBalance 注释：
     * 文档「战斗魂力×3」语义不明——battle 不耗魂力无量纲可乘，取与等级线性挂钩的温和定价）。
     * 前置校验逐条 400：层级合法 → 已解锁 → 历史已通关该难度（ever_cleared 位掩码）→
     * 当日机会未用（与战斗共用名额）→ 魂力充足（「资格类前置不足即 400」与 fight 同口径）。
     * 扫荡不写 tier_completed/ever_cleared（无战斗事实）、不计胜负、不触发成就同步
     * （无任何成就口径维度跳变）。
     */
    @Transactional
    fun sweep(userId: Long, tier: Int): DungeonSweepResponse {
        val def = GameBalance.dungeonDefByTier(tier) ?: throw IllegalArgumentException(MSG_INVALID_TIER)
        val profile = getProfile(userId)
        if (profile.prestigeCount < def.unlockPrestige) throw IllegalArgumentException(lockedMessage(def))
        val row = dungeonProgressRepo.findById(userId).orElse(null)
            ?: throw IllegalArgumentException(MSG_NOT_CLEARED)
        if (!GameBalance.dungeonHasCleared(row.everCleared, tier)) throw IllegalArgumentException(MSG_NOT_CLEARED)
        val today = LocalDate.now()
        if (row.challengeDate == today) throw IllegalArgumentException(MSG_ALREADY_CHALLENGED)
        val cost = GameBalance.dungeonSweepSoulPowerCost(profile.level)
        if (profile.soulPower < cost) {
            throw IllegalArgumentException("扫荡需要${cost}魂力（当前${profile.soulPower}）")
        }
        profile.soulPower -= cost
        val mult = GameBalance.prestigeMultiplier(profile.prestigeCount)
        val goldGained = (def.goldReward * mult).toLong()
        profile.gold += goldGained
        profile.killingIntent += def.killingReward
        val drop = gameService.rollBackpackDrop(userId, GameBalance.dungeonDropLevel(def.dropTier))
        val drops = listOfNotNull(drop)
        val dropLostMessage = if (drop == null) MSG_BACKPACK_FULL else null
        // 占用当日名额（与战斗共用 challenge_date）；跨天惰性重置当日完成标记（扫荡无通关事实）
        row.tierCompleted = -1
        row.challengeDate = today
        profile.updatedAt = LocalDateTime.now()
        profileRepo.save(profile)
        dungeonProgressRepo.save(row)
        // 业务计数器：扫荡成功（Micrometer 不抛业务异常，不影响主流程）
        meterRegistry.counter(METRIC_DUNGEON_SWEEP_TOTAL, "tier", tier.toString()).increment()

        return DungeonSweepResponse(
            tier = tier,
            soulPowerSpent = cost,
            goldGained = goldGained,
            killingGained = def.killingReward,
            drops = drops,
            playerGold = profile.gold,
            playerSoulPower = profile.soulPower,
            message = dropLostMessage
        )
    }

    // ======== 私有组装（与 GameService 同源口径的本地镜像） ========

    private fun getProfile(userId: Long): PlayerProfileEntity {
        return profileRepo.findByUserId(userId)
            ?: throw PlayerSaveNotFoundException()
    }

    /**
     * 副本 Boss：当前推图怪（GameService.monsterStats，与 battle() 同源）× 难度倍率。
     * HP ×hpMult、攻击 ×atkMult（§8 表）；matk 镜像攻击、双防 = 放大后攻击 ×
     * MONSTER_DEF_FACTOR（monsterStats 构造约定，设计文档无独立防御倍率——防御随攻击
     * 同步放大保持「防=攻×0.35」内部一致）；倍率下限夹 1 防御御极端配置出 0 属性怪。
     */
    private fun dungeonMonster(profile: PlayerProfileEntity, def: GameBalance.DungeonDef): GameService.MonsterStats {
        val base = GameService.monsterStats(profile.currentMapId, profile.currentStage)
        val atk = (base.atk * def.atkMult).toInt().coerceAtLeast(1)
        val hp = (base.hp * def.hpMult).toLong().coerceAtLeast(1)
        val defV = (atk * GameBalance.MONSTER_DEF_FACTOR).toInt()
        return GameService.MonsterStats(hp, atk, atk, defV, defV)
    }

    /**
     * 转生倍率下的基础生命上限 + 装备生命加成的流派乘区：与 battle() 调 resolveBattle 的
     * maxHp 口径逐项同式（基础 = GameBalance.playerBaseMaxHp × prestigeMultiplier——
     * GameService.scaledBaseMaxHp 为 private 无法直调，公式以 GameBalance.playerBaseMaxHp
     * 为锚点镜像；加成乘区复用公开的 GameService.schoolScaledMaxHp）。
     */
    private fun schoolScaledMaxHp(
        profile: PlayerProfileEntity,
        equipHpBonus: Long,
        school: GameBalance.SchoolMods?
    ): Long {
        val base = GameBalance.playerBaseMaxHp(profile.level)
        val scaled = if (profile.prestigeCount <= 0) base
        else (base * GameBalance.prestigeMultiplier(profile.prestigeCount)).toLong()
        return GameService.schoolScaledMaxHp(scaled + equipHpBonus, school)
    }
}
