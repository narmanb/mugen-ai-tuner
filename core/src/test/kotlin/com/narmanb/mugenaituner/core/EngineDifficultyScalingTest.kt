package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertTrue

class EngineDifficultyScalingTest {
    @Test
    fun engineScalingInterpolatesLowSelectedAndExpertThresholds() {
        val expression = AiEditPlanner.engineScaledExpression(
            operator = "<",
            lowThreshold = 150,
            centerThreshold = 470,
            highThreshold = 900,
        )

        assertTrue("AILevel <= 1" in expression)
        assertTrue("AILevel <= 4" in expression)
        assertTrue("150" in expression)
        assertTrue("470 - 150" in expression)
        assertTrue("900 - 470" in expression)
        assertTrue(expression.startsWith("Random < ifelse"))
    }

    @Test
    fun plannerCanConvertBinaryAiToNumericDifficultyResponse() {
        val analysis = CharacterAnalysis(
            characterName = "Binary AI",
            author = null,
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = listOf(
                AiBehavior(
                    category = BehaviorCategory.DEFENSE,
                    summary = "Guard",
                    confidence = Confidence.HIGH,
                    filePath = "char.cmd",
                    lineNumber = 20,
                    section = "State -1, Guard",
                    rawCode = "triggerall = AILevel > 0\ntrigger1 = InGuardDist\ntrigger1 = Random < 900\ntype = ChangeState",
                ),
            ),
            difficultyResponsiveness = DifficultyResponsiveness.NONE,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 1,
            notes = emptyList(),
        )

        val plan = AiEditPlanner.plan(
            analysis = analysis,
            profile = SkillProfile.fromPreset(DifficultyPreset.NORMAL),
            engineDifficultyScaling = true,
        )

        val replacement = plan.edits.single().replacementExpression
        assertTrue(plan.engineDifficultyScaling)
        assertTrue(replacement.contains("AILevel"))
        assertTrue(replacement.contains("AILevel <= 1"))
        assertTrue(replacement.contains("AILevel <= 4"))
        assertTrue(plan.notes.any { "Expert/100" in it })
    }
}
