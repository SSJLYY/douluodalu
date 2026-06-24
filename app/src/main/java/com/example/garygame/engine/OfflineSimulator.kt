package com.example.garygame.engine

import kotlin.math.pow

/**
 * 离线仿真引擎 - 模拟三类玩家120天成长轨迹
 * 
 * 功能：
 * 1. 加速时间模拟（跳过等待）
 * 2. 计算升级速度、转生用时、推图进度
 * 3. 输出CSV报告供Excel分析
 */
class OfflineSimulator {
    
    /**
     * 模拟单个玩家N天的成长轨迹
     */
    fun simulatePlayer(
        profile: GlobalValueConfig.PlayerTemplates.PlayerProfile,
        totalDays: Int = 120
    ): SimulationReport {
        
        val report = SimulationReport(profile.name)
        var currentLevel = 1
        var currentPrestige = 0
        var totalExp = 0L
        var totalGold = 0L
        var currentMap = 0
        var dayInNewbieProtection = 0
        
        for (day in 1..totalDays) {
            dayInNewbieProtection++
            
            // 1. Calculate ticks per day (reduced from 2 to 1 for balance)
            // Assuming 1 tick per minute (500ms/tick in real game, but simulation uses simplified model)
            val ticksPerDay = profile.dailyOnlineMinutes * 1
            
            var dailyExpGain = 0L
            var dailyGoldGain = 0L
            
            repeat(ticksPerDay) {
                // 2. 模拟修炼获得魂力（经验）
                val baseExp = calculateBaseExp(currentLevel)
                val prestigeMult = GlobalValueConfig.getExpMultiplier(currentPrestige)
                val newbieMult = GlobalValueConfig.getNewbieExpMult(dayInNewbieProtection)
                val activeMult = if (profile.dailyOnlineMinutes >= GlobalValueConfig.Lifecycle.DAILY_ACTIVE_BONUS_MINUTES) 
                    GlobalValueConfig.Lifecycle.DAILY_ACTIVE_EXP_MULT else 1.0f
                
                val adjustedExp = (baseExp * prestigeMult * newbieMult * activeMult * profile.autoBattleEfficiency).toLong()
                totalExp += adjustedExp
                dailyExpGain += adjustedExp
                
                // 3. 模拟金币获取
                val goldGain = simulateGoldGain(currentLevel, currentMap, profile.resourceUtilization)
                totalGold += goldGain
                dailyGoldGain += goldGain
                
                // 4. 检查升级
                while (totalExp >= getExpRequiredForNextLevel(currentLevel)) {
                    totalExp -= getExpRequiredForNextLevel(currentLevel)
                    currentLevel++
                    
                    // 5. 检查转生
                    if (currentLevel >= GlobalValueConfig.Lifecycle.PRESTIGE_LEVEL_THRESHOLD && canPrestige(currentPrestige)) {
                        currentPrestige++
                        currentLevel = 1  // 转生后重置等级
                        totalGold = (totalGold * 0.5).toLong()  // 转生保留50%金币
                        
                        // 转生后解锁新地图
                        currentMap = minOf(currentMap + 1, 7)
                    }
                }
                
                // 6. 模拟推图进度（每升10级推进1张地图）
                if (currentLevel % 10 == 0 && currentMap < 7) {
                    currentMap = minOf(currentMap + 1, 7)
                }
                
                // 7. 资源自动消耗（简化模型）
                autoConsumeResources(totalGold, currentPrestige)
            }
            
            // 记录当日状态
            report.recordDay(
                day = day,
                level = currentLevel,
                prestige = currentPrestige,
                gold = totalGold,
                map = currentMap,
                exp = dailyExpGain,
                goldDaily = dailyGoldGain
            )
        }
        
        return report
    }
    
    /**
     * 运行三套模板仿真并生成对比报告
     */
    fun runFullSimulation(): FullSimulationReport {
        println("🚀 开始全模板仿真（120天）...")
        
        val zeroReport = simulatePlayer(GlobalValueConfig.PlayerTemplates.ZERO_SPEND)
        println("✅ 零氪慢速模板完成")
        
        val balancedReport = simulatePlayer(GlobalValueConfig.PlayerTemplates.BALANCED)
        println("✅ 均衡养成模板完成")
        
        val whaleReport = simulatePlayer(GlobalValueConfig.PlayerTemplates.WHALE)
        println("✅ 资源充裕模板完成")
        
        // 检测偏离度
        val deviations = analyzeDeviations(zeroReport, balancedReport, whaleReport)
        
        // 生成修正建议
        val suggestions = generateAdjustmentSuggestions(deviations)
        
        println("\n📊 仿真完成！生成报告...")
        
        return FullSimulationReport(
            zeroReport = zeroReport,
            balancedReport = balancedReport,
            whaleReport = whaleReport,
            deviations = deviations,
            suggestions = suggestions
        )
    }
    
    // ==================== 内部计算方法 ====================
    
    private fun calculateBaseExp(level: Int): Long {
        // Increased to target Day 6 for first prestige (25 -> 70)
        return 70L * level.toLong()
    }
    
    private fun getExpRequiredForNextLevel(level: Int): Long {
        // 沿用文档公式：soulPower = 120 * level^1.55
        return GlobalValueConfig.getBreakthroughCost(level)
    }
    
    private fun simulateGoldGain(level: Int, map: Int, utilization: Float): Long {
        // Gold gain = base × map coefficient × utilization (reduced by 30%)
        val baseGold = 35L * level
        val mapMult = GlobalValueConfig.getMapDifficultyMultiplier(map)
        return (baseGold * mapMult * utilization).toLong()
    }
    
    private fun canPrestige(currentPrestige: Int): Boolean {
        // 简化：无转生次数上限
        return true
    }
    
    private fun autoConsumeResources(totalGold: Long, prestige: Int) {
        // 简化：不实际扣除，仅模拟消耗渠道
        // 实际游戏中会在GameEngine中实现
    }
    
    private fun analyzeDeviations(
        zero: SimulationReport,
        balanced: SimulationReport,
        whale: SimulationReport
    ): DeviationAnalysis {
        
        // 检查均衡模板是否在Day 6达到1转
        val balancedDay1Prestige = balanced.dailyRecords.find { it.prestige >= 1 }?.day ?: 999
        val deviation1 = balancedDay1Prestige - GlobalValueConfig.Lifecycle.FIRST_PRESTIGE_DAYS
        
        // 检查5转、10转、13转
        val balancedDay5Prestige = balanced.dailyRecords.find { it.prestige >= 5 }?.day ?: 999
        val balancedDay10Prestige = balanced.dailyRecords.find { it.prestige >= 10 }?.day ?: 999
        val balancedDay13Prestige = balanced.dailyRecords.find { it.prestige >= 13 }?.day ?: 999
        
        return DeviationAnalysis(
            firstPrestigeDeviation = deviation1,
            fifthPrestigeDeviation = balancedDay5Prestige - GlobalValueConfig.Lifecycle.EXPECTED_5_PRESTIGE_DAYS,
            tenthPrestigeDeviation = balancedDay10Prestige - GlobalValueConfig.Lifecycle.EXPECTED_10_PRESTIGE_DAYS,
            thirteenthPrestigeDeviation = balancedDay13Prestige - GlobalValueConfig.Lifecycle.EXPECTED_13_PRESTIGE_DAYS,
            balancedFirstPrestigeDay = balancedDay1Prestige,
            balancedFifthPrestigeDay = balancedDay5Prestige,
            balancedTenthPrestigeDay = balancedDay10Prestige,
            balancedThirteenthPrestigeDay = balancedDay13Prestige
        )
    }
    
    private fun generateAdjustmentSuggestions(deviations: DeviationAnalysis): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (deviations.firstPrestigeDeviation > 2) {
            suggestions.add("⚠️ 首次转生过慢（延迟${deviations.firstPrestigeDeviation}天），建议：")
            suggestions.add("   - 上调新手期经验倍率从1.5→1.7")
            suggestions.add("   - 或降低突破消耗基数从120→100")
        } else if (deviations.firstPrestigeDeviation < -2) {
            suggestions.add("⚠️ 首次转生过快（提前${-deviations.firstPrestigeDeviation}天），建议：")
            suggestions.add("   - 下调新手期经验倍率从1.5→1.3")
            suggestions.add("   - 或提高突破消耗指数从1.55→1.6")
        }
        
        if (deviations.thirteenthPrestigeDeviation > 10) {
            suggestions.add("⚠️ 13转过慢（延迟${deviations.thirteenthPrestigeDeviation}天），建议：")
            suggestions.add("   - 上调4转后经验倍率从0.58→0.65")
        } else if (deviations.thirteenthPrestigeDeviation < -10) {
            suggestions.add("⚠️ 13转过快（提前${-deviations.thirteenthPrestigeDeviation}天），建议：")
            suggestions.add("   - 下调4转后经验倍率从0.58→0.52")
        }
        
        if (suggestions.isEmpty()) {
            suggestions.add("✅ 节奏符合预期，无需调整")
        }
        
        return suggestions
    }
}

// ==================== 数据类 ====================

data class SimulationReport(
    val playerName: String,
    val dailyRecords: MutableList<DailyRecord> = mutableListOf()
) {
    data class DailyRecord(
        val day: Int,
        val level: Int,
        val prestige: Int,
        val totalGold: Long,
        val currentMap: Int,
        val dailyExp: Long,
        val dailyGold: Long
    )
    
    fun recordDay(day: Int, level: Int, prestige: Int, gold: Long, map: Int, exp: Long, goldDaily: Long) {
        dailyRecords.add(DailyRecord(day, level, prestige, gold, map, exp, goldDaily))
    }
    
    /** 导出为CSV格式 */
    fun exportToCSV(): String {
        val sb = StringBuilder()
        sb.appendLine("Day,Level,Prestige,TotalGold,CurrentMap,DailyExp,DailyGold")
        dailyRecords.forEach {
            sb.appendLine("${it.day},${it.level},${it.prestige},${it.totalGold},${it.currentMap},${it.dailyExp},${it.dailyGold}")
        }
        return sb.toString()
    }
    
    /** 保存为文件 */
    fun saveToFile(filePath: String) {
        java.io.File(filePath).writeText(exportToCSV())
        println("📄 报告已保存至: $filePath")
    }
}

data class FullSimulationReport(
    val zeroReport: SimulationReport,
    val balancedReport: SimulationReport,
    val whaleReport: SimulationReport,
    val deviations: DeviationAnalysis,
    val suggestions: List<String>
) {
    fun printSummary() {
        println("\n" + "=".repeat(60))
        println("📊 仿真结果汇总")
        println("=".repeat(60))
        
        println("\n【零氪慢速】")
        printTemplateSummary(zeroReport)
        
        println("\n【均衡养成】（标准锚点）")
        printTemplateSummary(balancedReport)
        
        println("\n【资源充裕】")
        printTemplateSummary(whaleReport)
        
        println("\n【偏离度分析】")
        println("  首次转生: ${if (deviations.balancedFirstPrestigeDay == 999) "未达成" else "Day ${deviations.balancedFirstPrestigeDay}"} (目标Day 6, 偏差${deviations.firstPrestigeDeviation}天)")
        println("  5转达成: ${if (deviations.balancedFifthPrestigeDay == 999) "未达成" else "Day ${deviations.balancedFifthPrestigeDay}"} (目标Day 35, 偏差${deviations.fifthPrestigeDeviation}天)")
        println("  10转达成: ${if (deviations.balancedTenthPrestigeDay == 999) "未达成" else "Day ${deviations.balancedTenthPrestigeDay}"} (目标Day 75, 偏差${deviations.tenthPrestigeDeviation}天)")
        println("  13转达成: ${if (deviations.balancedThirteenthPrestigeDay == 999) "未达成" else "Day ${deviations.balancedThirteenthPrestigeDay}"} (目标Day 140, 偏差${deviations.thirteenthPrestigeDeviation}天)")
        
        println("\n【修正建议】")
        suggestions.forEach { println("  $it") }
        
        println("\n" + "=".repeat(60))
    }
    
    private fun printTemplateSummary(report: SimulationReport) {
        val day10 = report.dailyRecords.getOrNull(9)
        val day30 = report.dailyRecords.getOrNull(29)
        val day60 = report.dailyRecords.getOrNull(59)
        val day120 = report.dailyRecords.lastOrNull()
        
        println("  Day 10: Lv.${day10?.level ?: "?"} ${if ((day10?.prestige ?: 0) > 0) "${day10?.prestige}转" else ""}")
        println("  Day 30: Lv.${day30?.level ?: "?"} ${if ((day30?.prestige ?: 0) > 0) "${day30?.prestige}转" else ""}")
        println("  Day 60: Lv.${day60?.level ?: "?"} ${if ((day60?.prestige ?: 0) > 0) "${day60?.prestige}转" else ""}")
        println("  Day 120: Lv.${day120?.level ?: "?"} ${if ((day120?.prestige ?: 0) > 0) "${day120?.prestige}转" else ""}")
    }
    
    /** 导出所有报告 */
    fun exportAllReports(outputDir: String) {
        val dir = java.io.File(outputDir)
        if (!dir.exists()) dir.mkdirs()
        
        zeroReport.saveToFile("$outputDir/zero_spend_report.csv")
        balancedReport.saveToFile("$outputDir/balanced_report.csv")
        whaleReport.saveToFile("$outputDir/whale_report.csv")
        
        // 保存汇总建议
        val summaryFile = java.io.File("$outputDir/simulation_summary.txt")
        summaryFile.writeText(buildString {
            appendLine("《斗罗大陆·放置传说 V5》仿真报告")
            appendLine("生成时间: ${java.time.LocalDateTime.now()}")
            appendLine()
            appendLine("修正建议:")
            suggestions.forEach { appendLine("  $it") }
        })
    }
}

data class DeviationAnalysis(
    val firstPrestigeDeviation: Int,      // 首次转生偏差天数
    val fifthPrestigeDeviation: Int,      // 5转偏差天数
    val tenthPrestigeDeviation: Int,      // 10转偏差天数
    val thirteenthPrestigeDeviation: Int, // 13转偏差天数
    val balancedFirstPrestigeDay: Int,
    val balancedFifthPrestigeDay: Int,
    val balancedTenthPrestigeDay: Int,
    val balancedThirteenthPrestigeDay: Int
)
