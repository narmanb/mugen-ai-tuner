package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndirectAiLevelScalingIntegrationTest {
    @Test
    fun `AILevel alias used numerically is reported and protected from rewriting`() {
        val analysis = MugenAiAnalyzer.analyze(
            listOf(
                SourceFile(
                    "fighter.cmd",
                    """
                    [State -2, Difficulty alias]
                    type = VarSet
                    trigger1 = 1
                    v = 40
                    value = AILevel

                    [State -1, Guard reaction]
                    type = ChangeState
                    triggerall = var(40) > 0
                    trigger1 = InGuardDist
                    trigger1 = Random < var(40) * 100
                    value = 120
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(1, analysis.behaviors.size)
        val behavior = analysis.behaviors.single()
        assertEquals(Confidence.HIGH, behavior.confidence)
        assertEquals(BehaviorCategory.DEFENSE, behavior.category)
        assertTrue(behavior.difficultyScaled)
        assertEquals(0, analysis.directlyScaledBehaviorCount)
        assertEquals(1, analysis.indirectlyScaledBehaviorCount)
        assertEquals(DifficultyResponsiveness.FULL, analysis.difficultyResponsiveness)

        val plan = AiEditPlanner.plan(
            analysis,
            SkillProfile.fromPreset(DifficultyPreset.EASY),
        )

        assertTrue(plan.edits.isEmpty())
        assertEquals(1, plan.skippedBehaviorCount)
    }

    @Test
    fun `AILevel alias used only as AI gate does not block fixed probability tuning`() {
        val analysis = MugenAiAnalyzer.analyze(
            listOf(
                SourceFile(
                    "fighter.cmd",
                    """
                    [State -2, AI alias]
                    type = VarSet
                    trigger1 = 1
                    v = 40
                    value = AILevel

                    [State -1, Guard reaction]
                    type = ChangeState
                    triggerall = var(40) > 0
                    trigger1 = InGuardDist
                    trigger1 = Random < 900
                    value = 120
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(1, analysis.behaviors.size)
        val behavior = analysis.behaviors.single()
        assertEquals(Confidence.HIGH, behavior.confidence)
        assertFalse(behavior.difficultyScaled)
        assertEquals(0, analysis.directlyScaledBehaviorCount)
        assertEquals(0, analysis.indirectlyScaledBehaviorCount)
        assertEquals(DifficultyResponsiveness.NONE, analysis.difficultyResponsiveness)

        val plan = AiEditPlanner.plan(
            analysis,
            SkillProfile.fromPreset(DifficultyPreset.EASY),
        )

        assertEquals(1, plan.edits.size)
        assertEquals("Random < 900", plan.edits.single().originalExpression)
    }
}
