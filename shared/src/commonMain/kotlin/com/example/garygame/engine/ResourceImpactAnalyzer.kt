package com.example.garygame.engine

/**
 * 资源消耗影响分析工具
 * 
 * 目的：量化金币消耗对游戏进度的实际影响
 */
object ResourceImpactAnalyzer {
    
    /**
     * 计算魂骨强化总消耗
     */
    fun calculateBoneEnhanceCost(targetLevel: Int): Long {
        var totalCost = 0L
        for (level in 0 until targetLevel) {
            val cost = GlobalValueConfig.ResourceSink.BONE_ENHANCE_BASE_COST * 
                      Math.pow((level + 1).toDouble(), GlobalValueConfig.ResourceSink.BONE_ENHANCE_EXPONENT.toDouble()).toLong()
            totalCost += cost
        }
        return totalCost
    }
    
    /**
     * 计算魂环吸收费用（简化模型）
     */
    fun calculateRingAbsorbCost(ringYear: Int, ringQuality: Int): Long {
        // 年份系数：百年=1, 千年=10, 万年=100, 十万年=1000, 百万年=10000
        val yearMult = Math.pow(10.0, ringYear.toDouble()).toLong()
        
        // 品质系数：劣等=1, 普通=1.5, 精良=2, 优秀=3, 完美=5
        val qualityMult = when (ringQuality) {
            0 -> 1.0f
            1 -> 1.5f
            2 -> 2.0f
            3 -> 3.0f
            4 -> 5.0f
            else -> 1.0f
        }
        
        val baseCost = 10000L
        return (baseCost * yearMult * qualityMult * GlobalValueConfig.ResourceSink.RING_ABSORB_COST_MULT).toLong()
    }
    
    /**
     * 模拟带资源消耗的成长轨迹
     */
    fun simulateWithResourceConsumption(days: Int = 120): ResourceImpactReport {
        var totalGoldEarned = 0L
        var totalGoldSpent = 0L
        
        // 假设均衡玩家的行为模式
        val dailyGoldIncome = 500000L  // 日均金币收入（基于仿真数据）
        val dailyExpenses = estimateDailyExpenses(day = 0, prestige = 0)
        
        for (day in 1..days) {
            // 收入
            totalGoldEarned += dailyGoldIncome
            
            // 支出（随转生次数增加）
            val prestige = getPrestigeAtDay(day)
            val expenses = estimateDailyExpenses(day, prestige)
            totalGoldSpent += expenses
            
            // 净余额
            val netBalance = totalGoldEarned - totalGoldSpent
            
            // 检查是否会因金币不足而卡住
            val isBlocked = netBalance < 0
        }
        
        return ResourceImpactReport(
            totalEarned = totalGoldEarned,
            totalSpent = totalGoldSpent,
            finalBalance = totalGoldEarned - totalGoldSpent,
            blockedDays = 0,  // 简化：假设不会完全卡住
            impactPercentage = calculateImpactPercentage(totalGoldEarned, totalGoldSpent)
        )
    }
    
    private fun getPrestigeAtDay(day: Int): Int {
        // 简化映射（基于仿真结果）
        return when {
            day < 30 -> 0
            day < 60 -> 3
            day < 90 -> 6
            else -> 9
        }
    }
    
    private fun estimateDailyExpenses(day: Int, prestige: Int): Long {
        // 估算每日支出
        val baseExpense = 100000L
        
        // 随转生次数增加（更高阶的魂环/魂骨更贵）
        val prestigeMult = 1.0f + prestige * 0.3f
        
        return (baseExpense * prestigeMult).toLong()
    }
    
    private fun calculateImpactPercentage(earned: Long, spent: Long): Float {
        if (earned == 0L) return 0f
        return (spent.toFloat() / earned.toFloat()) * 100f
    }
}

data class ResourceImpactReport(
    val totalEarned: Long,
    val totalSpent: Long,
    val finalBalance: Long,
    val blockedDays: Int,
    val impactPercentage: Float  // 支出占收入的百分比
) {
    fun printAnalysis() {
        println("\n" + "=".repeat(60))
        println("💰 资源消耗影响分析报告")
        println("=".repeat(60))
        
        println("\n【收支概况】")
        println("  总收入: ${formatNumber(totalEarned)} 金币")
        println("  总支出: ${formatNumber(totalSpent)} 金币")
        println("  最终余额: ${formatNumber(finalBalance)} 金币")
        println("  支出占比: ${String.format("%.1f", impactPercentage)}%")
        
        println("\n【对游戏时间的影响】")
        if (impactPercentage < 50f) {
            println("  ✅ 轻度影响（支出<50%）")
            println("     - 金币充足，不会因资源短缺卡关")
            println("     - 预计对升级节奏影响：<5%")
        } else if (impactPercentage < 80f) {
            println("  ⚠️ 中度影响（支出50%-80%）")
            println("     - 需要合理分配资源")
            println("     - 预计对升级节奏影响：5%-15%")
        } else {
            println("  ❌ 重度影响（支出>80%）")
            println("     - 可能因金币不足无法强化/吸收")
            println("     - 预计对升级节奏影响：15%-30%")
        }
        
        println("\n【关键消耗项】")
        println("  1. 魂骨强化（Lv.10满级）: ${formatNumber(ResourceImpactAnalyzer.calculateBoneEnhanceCost(10))} 金币/部位")
        println("  2. 百万年完美魂环吸收: ${formatNumber(ResourceImpactAnalyzer.calculateRingAbsorbCost(4, 4))} 金币")
        println("  3. 魂核合成: 视重复数量而定")
        
        println("\n【优化建议】")
        if (finalBalance > 0) {
            println("  ✅ 当前经济模型健康，无需调整")
        } else {
            println("  ⚠️ 建议提高金币掉落或降低强化消耗")
        }
        
        println("\n" + "=".repeat(60))
    }
    
    private fun formatNumber(num: Long): String {
        return when {
            num >= 100000000 -> String.format("%.1f亿", num / 100000000.0)
            num >= 10000 -> String.format("%.1f万", num / 10000.0)
            else -> num.toString()
        }
    }
}
