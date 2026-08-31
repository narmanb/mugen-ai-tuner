package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertTrue

class EngineDifficultyScalingTest {
    @Test
    fun engineScalingUsesAiLevelAndPreservesOriginalAtTopEnd() {
        val expression = AiEditPlanner.engineScaledExpression("<", target = 350, original = 900)

        assertTrue("AILevel <= 4" in expression)
        assertTrue("350" in expression)
        assertTrue("900 - 350" in expression)
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

        assertTrue(plan.engineDifficultyScaling)
        assertTrue(plan.edits.single().replacementExpression.contains("AILevel"))
    }
}
