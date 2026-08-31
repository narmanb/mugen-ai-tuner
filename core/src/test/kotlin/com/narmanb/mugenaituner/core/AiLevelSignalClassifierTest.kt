package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiLevelSignalClassifierTest {
    @Test
    fun distinguishesHumanOnlyPositiveAndArithmeticAiLevelExpressions() {
        assertNull(AiLevelSignalClassifier.classifyExpression("AILevel = 0"))
        assertNull(AiLevelSignalClassifier.classifyExpression("!AILevel"))
        assertEquals(Confidence.HIGH, AiLevelSignalClassifier.classifyExpression("AILevel"))
        assertEquals(Confidence.HIGH, AiLevelSignalClassifier.classifyExpression("!!AILevel"))
        assertEquals(Confidence.HIGH, AiLevelSignalClassifier.classifyExpression("AILevel > 0"))
        assertEquals(Confidence.HIGH, AiLevelSignalClassifier.classifyExpression("AILevel >= 1"))
        assertEquals(Confidence.MEDIUM, AiLevelSignalClassifier.classifyExpression("AILevel * 100"))
    }

    @Test
    fun humanOnlyAiLevelBranchDoesNotBecomeCustomAi() {
        val source = SourceFile(
            "human.cmd",
            """
            [State -2, Human flag]
            type = VarSet
            trigger1 = AILevel = 0
            v = 30
            value = 1

            [State -1, Human move]
            type = ChangeState
            triggerall = var(30)
            trigger1 = Random < 900
            value = 1000
            """.trimIndent(),
        )

        val result = MugenAiAnalyzer.analyze(listOf(source))

        assertFalse(result.aiDetected)
        assertTrue(result.aiFlags.isEmpty())
        assertTrue(result.behaviors.isEmpty())
    }

    @Test
    fun positiveAiLevelBranchSeedsHighConfidenceAiFlag() {
        val source = SourceFile(
            "cpu.cmd",
            """
            [State -2, AI flag]
            type = VarSet
            trigger1 = AILevel > 0
            v = 30
            value = 1

            [State -1, CPU move]
            type = ChangeState
            triggerall = var(30)
            trigger1 = Random < 900
            value = 1000
            """.trimIndent(),
        )

        val result = MugenAiAnalyzer.analyze(listOf(source))

        assertTrue(result.aiDetected)
        assertTrue(result.aiFlags.any { it.variable == 30 && it.confidence == Confidence.HIGH })
        assertTrue(result.behaviors.any { it.confidence == Confidence.HIGH })
    }

    @Test
    fun arithmeticAiLevelBehaviorRemainsReadOnlyWithoutStrongerProof() {
        val source = SourceFile(
            "scaled.cmd",
            """
            [State -1, Scaled]
            type = ChangeState
            trigger1 = Random < AILevel * 100
            value = 1000
            """.trimIndent(),
        )

        val result = MugenAiAnalyzer.analyze(listOf(source))
        assertTrue(result.aiDetected)
        assertEquals(Confidence.MEDIUM, result.behaviors.single().confidence)

        val plan = AiEditPlanner.plan(
            analysis = result,
            profile = SkillProfile.fromPreset(DifficultyPreset.NORMAL),
        )
        assertTrue(plan.edits.isEmpty())
    }
}
