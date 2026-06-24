package com.example.garygame

import com.example.garygame.engine.GlobalValueConfig
import com.example.garygame.engine.OfflineSimulator
import org.junit.Test

/**
 * Numerical Simulation Test - Verify accelerated lifecycle rhythm
 */
class SimulationTest {
    
    @Test
    fun `test full simulation for three player types`() {
        println("\n" + "=".repeat(60))
        println("Douluo Idle V5 - Accelerated Numerical Simulation")
        println("=".repeat(60))
        
        val simulator = OfflineSimulator()
        val report = simulator.runFullSimulation()
        
        // Print summary
        report.printSummary()
        
        // Export CSV reports
        val outputDir = "E:\\android\\testGame\\simulation_reports"
        report.exportAllReports(outputDir)
        
        println("\nSimulation test completed!")
        println("Reports saved to: $outputDir")
        
        // Assertion: ensure rhythm is within reasonable range
        assert(report.deviations.firstPrestigeDeviation in -2..3) {
            "First prestige deviation too large: ${report.deviations.firstPrestigeDeviation} days"
        }
        
        println("\nAll assertions passed! Rhythm meets expectations.")
    }
    
    @Test
    fun `test exp multiplier gradient`() {
        println("\nExp Multiplier Gradient Verification:")
        
        val expectedMultipliers = mapOf(
            0 to 1.2f,
            1 to 1.1f,
            2 to 1.0f,
            3 to 0.9f,
            4 to 0.8f,
            5 to 0.75f,
            10 to 0.6f,
            13 to 0.52f
        )
        
        expectedMultipliers.forEach { (prestige, expected) ->
            val actual = GlobalValueConfig.getExpMultiplier(prestige)
            val status = if (actual == expected) "PASS" else "FAIL"
            println("  Prestige $prestige: expected x$expected, actual x$actual [$status]")
            assert(actual == expected) { 
                "Exp multiplier error at prestige $prestige: expected $expected, actual $actual" 
            }
        }
        
        println("Exp multiplier gradient verification PASSED")
    }
    
    @Test
    fun `test breakthrough cost formula`() {
        println("\nBreakthrough Cost Formula Verification:")
        
        val testLevels = listOf(10, 30, 50, 90, 100)
        
        testLevels.forEach { level ->
            val cost = GlobalValueConfig.getBreakthroughCost(level)
            val expectedBase = 120.0 * Math.pow(level.toDouble(), 1.55)
            println("  Lv.$level: ${cost} soulPower (formula: 120 * $level^1.55)")
            
            // Allow ±1 error (rounding)
            assert(Math.abs(cost - expectedBase.toLong()) <= 1) {
                "Breakthrough cost error at Lv.$level: expected ${expectedBase.toLong()}, actual $cost"
            }
        }
        
        println("Breakthrough cost formula verification PASSED")
    }
    
    @Test
    fun `test newbie protection period`() {
        println("\nNewbie Protection Period Verification:")
        
        // First 3 days should have bonus
        for (day in 1..3) {
            assert(GlobalValueConfig.isInNewbieProtection(day)) { "Day $day should be in newbie protection" }
            val mult = GlobalValueConfig.getNewbieExpMult(day)
            val status = if (mult == 1.3f) "PASS" else "FAIL"
            println("  Day $day: exp multiplier x$mult [$status]")
            assert(mult == 1.3f) { "Newbie multiplier error at Day $day: expected 1.3, actual $mult" }
        }
        
        // From day 4 onwards, no bonus
        for (day in 4..10) {
            assert(!GlobalValueConfig.isInNewbieProtection(day)) { "Day $day should not be in newbie protection" }
            val mult = GlobalValueConfig.getNewbieExpMult(day)
            assert(mult == 1.0f) { "Multiplier should be 1.0 at Day $day, actual $mult" }
        }
        
        println("Newbie protection period verification PASSED")
    }
    
    @Test
    fun `test map difficulty curve`() {
        println("\nMap Difficulty Curve Verification:")
        
        val expectedCurve = mapOf(
            0 to 1.0f,
            1 to 1.15f,
            2 to 1.35f,
            3 to 1.6f,
            4 to 2.0f,
            5 to 2.5f,
            6 to 3.2f,
            7 to 4.0f
        )
        
        expectedCurve.forEach { (mapId, expected) ->
            val actual = GlobalValueConfig.getMapDifficultyMultiplier(mapId)
            val status = if (actual == expected) "PASS" else "FAIL"
            println("  Map $mapId: expected x$expected, actual x$actual [$status]")
            assert(actual == expected) { 
                "Map difficulty error at map $mapId: expected $expected, actual $actual" 
            }
        }
        
        println("Map difficulty curve verification PASSED")
    }
    
    @Test
    fun `test lifecycle constants`() {
        println("\nLifecycle Constants Verification:")
        
        println("  First prestige target days: ${GlobalValueConfig.Lifecycle.FIRST_PRESTIGE_DAYS}")
        assert(GlobalValueConfig.Lifecycle.FIRST_PRESTIGE_DAYS == 6)
        
        println("  First prestige level threshold: Lv.${GlobalValueConfig.Lifecycle.FIRST_PRESTIGE_LEVEL}")
        assert(GlobalValueConfig.Lifecycle.FIRST_PRESTIGE_LEVEL == 90)
        
        println("  Expected 5th prestige days: ${GlobalValueConfig.Lifecycle.EXPECTED_5_PRESTIGE_DAYS}")
        assert(GlobalValueConfig.Lifecycle.EXPECTED_5_PRESTIGE_DAYS == 35)
        
        println("  Expected 10th prestige days: ${GlobalValueConfig.Lifecycle.EXPECTED_10_PRESTIGE_DAYS}")
        assert(GlobalValueConfig.Lifecycle.EXPECTED_10_PRESTIGE_DAYS == 75)
        
        println("  Expected 13th prestige days: ${GlobalValueConfig.Lifecycle.EXPECTED_13_PRESTIGE_DAYS}")
        assert(GlobalValueConfig.Lifecycle.EXPECTED_13_PRESTIGE_DAYS == 140)
        
        println("Lifecycle constants verification PASSED")
    }
}
