package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiOnOffGatePlanningTest {
    private fun analysisFor(rawCode: String) = CharacterAnalysis(
        characterName = "Test",
        author = null,
        aiDetected = true,
        aiFlags = emptyList(),
        behaviors = listOf(
            AiBehavior(
                category = BehaviorCategory.DEFENSE,
                summary = "Guard",
                confidence = Confidence.HIGH,
                filePath = "test.cmd",
                lineNumber = 4,
                section = "State -1, Guard",
                rawCode = rawCode,
            ),
        ),
        difficultyResponsiveness = DifficultyResponsiveness.NONE,
        directlyScaledBehaviorCount = 0,
        aiBehaviorCount = 1,
        notes = emptyList(),
    )

    @Test
    fun aiOnOffGateDoesNotBlockFixedProbabilityTuning() {
        val analysis = analysisFor(
            """
            type = ChangeState
            triggerall = AILevel >= 1
            trigger1 = InGuardDist
            trigger1 = Random < 900
            value = 120
            """.trimIndent(),
        )

        val plan = AiEditPlanner.plan(analysis, SkillProfile.fromPreset(DifficultyPreset.EASY))

        assertEquals(1, plan.edits.size)
        assertEquals("Random < 900", plan.edits.single().originalExpression)
    }

    @Test
    fun realNumericDifficultyGateRemainsProtected() {
        val analysis = analysisFor(
            """
            type = ChangeState
            triggerall = AILevel >= 4
            trigger1 = InGuardDist
            trigger1 = Random < 900
            value = 120
            """.trimIndent(),
        )

        val plan = AiEditPlanner.plan(analysis, SkillProfile.fromPreset(DifficultyPreset.EASY))

        assertTrue(plan.edits.isEmpty())
        assertEquals(1, plan.skippedBehaviorCount)
    }

    @Test
    fun arithmeticDifficultyScalingRemainsProtected() {
        val analysis = analysisFor(
            """
            type = ChangeState
            triggerall = AILevel > 0
            trigger1 = InGuardDist
            trigger1 = Random < AILevel * 100
            value = 120
            """.trimIndent(),
        )

        val plan = AiEditPlanner.plan(analysis, SkillProfile.fromPreset(DifficultyPreset.EASY))

        assertTrue(plan.edits.isEmpty())
        assertEquals(1, plan.skippedBehaviorCount)
    }
}
