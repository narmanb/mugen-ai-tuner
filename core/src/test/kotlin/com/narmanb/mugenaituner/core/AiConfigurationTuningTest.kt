package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiConfigurationTuningTest {
    private fun parameter(
        kind: AiConfigurationKind,
        current: Int,
        maximum: Int,
        safe: Boolean = true,
    ) = AiConfigurationParameter(
        kind = kind,
        label = if (kind == AiConfigurationKind.COMBO_LEVEL) "Combo level" else "Movement level",
        variable = 59,
        currentLevel = current,
        minimumLevel = 0,
        maximumLevel = maximum,
        confidence = Confidence.HIGH,
        filePath = "ai.cns",
        lineNumber = 10,
        originalExpression = "value = 10 * $current",
        description = "Test",
        safeToEdit = safe,
    )

    @Test
    fun standardizedBandsMapToExpectedSmallLevels() {
        assertEquals(0, AiConfigurationTuning.targetLevel(10, 3))
        assertEquals(1, AiConfigurationTuning.targetLevel(30, 3))
        assertEquals(2, AiConfigurationTuning.targetLevel(50, 3))
        assertEquals(3, AiConfigurationTuning.targetLevel(75, 3))
        assertEquals(4, AiConfigurationTuning.targetLevel(100, 4))
    }

    @Test
    fun customComboAndMovementOverridesAreIndependent() {
        val parameters = listOf(
            parameter(AiConfigurationKind.COMBO_LEVEL, current = 3, maximum = 3),
            parameter(AiConfigurationKind.MOVEMENT_LEVEL, current = 3, maximum = 3).copy(lineNumber = 20),
        )
        val profile = SkillProfile(
            overallSkill = 50,
            categoryOverrides = mapOf(
                BehaviorCategory.COMBO to 30,
                BehaviorCategory.MOVEMENT to 75,
            ),
        )

        val edits = AiConfigurationTuning.plan(parameters, profile)

        assertEquals(1, edits.size)
        assertEquals(BehaviorCategory.COMBO, edits.single().category)
        assertEquals("value = 10 * 1", edits.single().replacementExpression)
    }

    @Test
    fun unsafeOrUncertainParametersNeverProduceEdits() {
        val unsafe = parameter(AiConfigurationKind.COMBO_LEVEL, current = 3, maximum = 3, safe = false)
        val uncertain = unsafe.copy(safeToEdit = true, confidence = Confidence.MEDIUM)

        assertTrue(AiConfigurationTuning.plan(listOf(unsafe, uncertain), SkillProfile(30)).isEmpty())
    }
}
