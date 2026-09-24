package com.douluodalu.game.service

import com.douluodalu.game.entity.EquippedBone
import com.douluodalu.game.entity.EquippedCore
import com.douluodalu.game.entity.EquippedRing
import com.douluodalu.game.model.GameBalance
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
 */
/** 单件装备折出的攻击/生命加成 */
data class EquipmentBonus(val atkBonus: Long, val hpBonus: Long)

@Service
class EquipmentPowerService(
    private val equippedRingRepo: EquippedRingRepository,
    private val equippedBoneRepo: EquippedBoneRepository,
    private val equippedCoreRepo: EquippedCoreRepository
) {

    /** 读取该玩家已装备的魂环/魂骨/魂核，计算攻防加成与战斗力 */
    fun bonusFor(userId: Long, level: Int): EquipmentBonus = bonus(
        level,
        equippedRingRepo.findByUserId(userId),
        equippedBoneRepo.findByUserId(userId),
        equippedCoreRepo.findByUserId(userId)
    )

    companion object {
        private fun qualityMult(ordinal: Int): Double =
            GameBalance.EQUIP_QUALITY_MULT.getOrElse(ordinal.coerceIn(0, GameBalance.EQUIP_QUALITY_MULT.size - 1)) { 1.0 }

        /** 纯函数：由装备列表与等级计算加成（不依赖 Spring，便于测试与仿真镜像复用） */
        fun bonus(
            level: Int,
            rings: List<EquippedRing>,
            bones: List<EquippedBone>,
            cores: List<EquippedCore>
        ): EquipmentBonus {
            var atk = 0.0
            var hp = 0.0
            for (r in rings) {
                val m = (1.0 + r.yearOrdinal.coerceIn(0, 4)) *
                        qualityMult(r.qualityOrdinal) *
                        (1.0 + r.percentage / 1000.0)
                atk += GameBalance.RING_ATK_WEIGHT * m
                hp += GameBalance.RING_HP_WEIGHT * m
            }
            for (b in bones) {
                val m = (1.0 + b.yearOrdinal.coerceIn(0, 4)) *
                        qualityMult(b.qualityOrdinal) *
                        (1.0 + b.enhanceLevel * GameBalance.BONE_ENHANCE_PER_LEVEL)
                atk += GameBalance.BONE_ATK_WEIGHT * m
                hp += GameBalance.BONE_HP_WEIGHT * m
            }
            val baseAtk = GameBalance.PLAYER_ATK_BASE + level * GameBalance.PLAYER_ATK_PER_LEVEL
            for (c in cores) {
                val pct = (GameBalance.CORE_ATK_PCT_WEIGHT * c.coreValue / 100.0 *
                        qualityMult(c.rarityOrdinal)).coerceAtMost(GameBalance.CORE_ATK_PCT_CAP)
                atk += baseAtk * pct
            }
            return EquipmentBonus(atk.toLong(), hp.toLong())
        }

        /** 战斗力 = 常数项 + 等级贡献 + 基础攻击 + 装备攻击加成 + 装备生命折算（POWER_HP_DIVISOR:1） */
        fun powerOf(level: Int, b: EquipmentBonus): Long =
            GameBalance.POWER_BASE + GameBalance.POWER_LEVEL_WEIGHT * level +
                    GameBalance.PLAYER_ATK_BASE + GameBalance.PLAYER_ATK_PER_LEVEL * level +
                    b.atkBonus + (b.hpBonus / GameBalance.POWER_HP_DIVISOR).toLong()

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
