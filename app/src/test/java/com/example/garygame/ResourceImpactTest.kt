package com.example.garygame

import com.example.garygame.engine.ResourceImpactAnalyzer
import org.junit.Test

class ResourceImpactTest {
    
    @Test
    fun `analyze resource consumption impact`() {
        println("\n🔍 开始资源消耗影响分析...")
        
        // 计算关键消耗项
        val boneEnhanceCost = ResourceImpactAnalyzer.calculateBoneEnhanceCost(10)
        val ringAbsorbCost = ResourceImpactAnalyzer.calculateRingAbsorbCost(4, 4)
        
        println("\n【关键消耗项计算】")
        println("  魂骨强化Lv.10总消耗: $boneEnhanceCost 金币")
        println("  百万年完美魂环吸收: $ringAbsorbCost 金币")
        
        // 运行完整仿真
        val report = ResourceImpactAnalyzer.simulateWithResourceConsumption(120)
        report.printAnalysis()
        
        // 断言：确保经济模型健康
        assert(report.finalBalance > 0) {
            "经济模型失衡：最终余额为负（${report.finalBalance}）"
        }
        
        println("\n✅ 资源消耗影响分析完成")
    }
}
