package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiLevelDifficultyScalingTest {
    @Test
    fun pureAiOnOffChecksAreNotNumericScaling() {
        assertFalse(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = AILevel > 0"))
        assertFalse(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = AILevel >= 1"))
        assertFalse(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = AILevel != 0"))
        assertFalse(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = AILevel"))
        assertFalse(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = AILevel = [1,8]"))
        assertFalse(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = (AILevel) > 0"))
    }

    @Test
    fun levelDiscriminatingComparisonsAreNumericScaling() {
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = AILevel = 1"))
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = AILevel >= 4"))
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = AILevel < 5"))
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = AILevel = [4,8]"))
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = 3 < AILevel"))
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = ((AILevel)) >= 4"))
    }

    @Test
    fun arithmeticUseIsNumericScaling() {
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = Random < AILevel * 100"))
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = Random < 100 * AILevel"))
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("value = AILevel / 8.0"))
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("trigger1 = Random < (AILevel) * 100"))
        assertTrue(AiLevelDifficultyScaling.hasNumericScaling("value = 100 * ((AILevel))"))
    }
}
