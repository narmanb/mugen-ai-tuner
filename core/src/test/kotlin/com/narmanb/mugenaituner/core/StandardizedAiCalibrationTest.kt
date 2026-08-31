package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardizedAiCalibrationTest {
    @Test
    fun normalConvergesVeryDifferentDefenseThresholds() {
        val highOriginal = StandardizedAiCalibration.standardizedThreshold(
            category = BehaviorCategory.DEFENSE,
            skill = 50,
            operator = "<",
            originalThreshold = 900,
        ) ?: error("Missing defense calibration")
        val lowOriginal = StandardizedAiCalibration.standardizedThreshold(
            category = BehaviorCategory.DEFENSE,
            skill = 50,
            operator = "<",
            originalThreshold = 300,
        ) ?: error("Missing defense calibration")

        assertTrue(highOriginal in 460..490)
        assertTrue(lowOriginal in 425..455)
        assertTrue(kotlin.math.abs(highOriginal - lowOriginal) <= 40)
    }

    @Test
    fun easyNormalHardIncreaseDecisionPressure() {
        val easy = StandardizedAiCalibration.standardizedThreshold(
            BehaviorCategory.COMBO, 30, "<", 900,
        ) ?: error("Missing combo calibration")
        val normal = StandardizedAiCalibration.standardizedThreshold(
            BehaviorCategory.COMBO, 50, "<", 900,
        ) ?: error("Missing combo calibration")
        val hard = StandardizedAiCalibration.standardizedThreshold(
            BehaviorCategory.COMBO, 75, "<", 900,
        ) ?: error("Missing combo calibration")

        assertTrue(easy < normal)
        assertTrue(normal < hard)
    }

    @Test
    fun greaterThanComparisonsUseInverseThresholdDirection() {
        val easy = StandardizedAiCalibration.standardizedThreshold(
            BehaviorCategory.REACTION, 30, ">", 100,
        ) ?: error("Missing reaction calibration")
        val hard = StandardizedAiCalibration.standardizedThreshold(
            BehaviorCategory.REACTION, 75, ">", 100,
        ) ?: error("Missing reaction calibration")

        // A lower threshold means Random > N succeeds more often.
        assertTrue(hard < easy)
        assertTrue(
            StandardizedAiCalibration.activationChance(">", hard) >
                StandardizedAiCalibration.activationChance(">", easy),
        )
    }

    @Test
    fun randomOperatorConversionsRoundTripClosely() {
        listOf("<", "<=", ">", ">=").forEach { operator ->
            val threshold = StandardizedAiCalibration.thresholdForChance(operator, 0.45)
            val chance = StandardizedAiCalibration.activationChance(operator, threshold)
            assertTrue(kotlin.math.abs(chance - 0.45) <= 0.001)
        }
    }

    @Test
    fun engineLowSkillIsQuarterOfSelectedSkill() {
        assertEquals(13, StandardizedAiCalibration.lowEngineSkill(50))
        assertEquals(8, StandardizedAiCalibration.lowEngineSkill(30))
        assertEquals(19, StandardizedAiCalibration.lowEngineSkill(75))
    }
}
