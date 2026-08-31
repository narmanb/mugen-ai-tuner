package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomProbabilityParserTest {
    @Test
    fun parsesComparisonAndEdgeAnchoredInclusiveRanges() {
        val decisions = RandomProbabilityParser.findAll(
            """
            trigger1 = Random < 500
            trigger2 = Random = [0,249]
            trigger3 = Random = [800,999]
            """.trimIndent(),
        )

        assertEquals(3, decisions.size)
        assertEquals(0.5, decisions[0].activationChance, 0.0001)
        assertEquals(RandomProbabilityForm.LOW_INCLUSIVE_RANGE, decisions[1].form)
        assertEquals(0.25, decisions[1].activationChance, 0.0001)
        assertEquals(RandomProbabilityForm.HIGH_INCLUSIVE_RANGE, decisions[2].form)
        assertEquals(0.2, decisions[2].activationChance, 0.0001)
    }

    @Test
    fun ignoresMiddleAndAlwaysTrueRanges() {
        val decisions = RandomProbabilityParser.findAll(
            """
            trigger1 = Random = [100,500]
            trigger2 = Random = [0,999]
            """.trimIndent(),
        )

        assertTrue(decisions.isEmpty())
    }

    @Test
    fun fixedRangeCanBeStandardizedButAiLevelConversionLeavesItUntouched() {
        val behavior = AiBehavior(
            category = BehaviorCategory.DEFENSE,
            summary = "Guard",
            confidence = Confidence.HIGH,
            filePath = "char.cmd",
            lineNumber = 10,
            section = "State -1, Guard",
            rawCode = "triggerall = AILevel > 0\ntrigger1 = InGuardDist\ntrigger1 = Random = [0,899]\ntype = ChangeState",
        )
        val analysis = CharacterAnalysis(
            characterName = "Range AI",
            author = null,
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = listOf(behavior),
            difficultyResponsiveness = DifficultyResponsiveness.NONE,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 1,
            notes = emptyList(),
        )

        val fixedPlan = AiEditPlanner.plan(
            analysis = analysis,
            profile = SkillProfile.fromPreset(DifficultyPreset.NORMAL),
            engineDifficultyScaling = false,
        )
        assertEquals(1, fixedPlan.edits.size)
        assertTrue(fixedPlan.edits.single().replacementExpression.startsWith("Random = [0,"))

        val scaledPlan = AiEditPlanner.plan(
            analysis = analysis,
            profile = SkillProfile.fromPreset(DifficultyPreset.NORMAL),
            engineDifficultyScaling = true,
        )
        assertTrue(scaledPlan.edits.isEmpty())
        assertTrue(scaledPlan.notes.any { "range-style" in it })
    }

    @Test
    fun strengthEstimatorUsesRangeEvidence() {
        val analysis = CharacterAnalysis(
            characterName = "Range AI",
            author = null,
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = listOf(
                AiBehavior(
                    category = BehaviorCategory.REACTION,
                    summary = "React",
                    confidence = Confidence.HIGH,
                    filePath = "char.cmd",
                    lineNumber = 12,
                    section = "State -1, React",
                    rawCode = "triggerall = AILevel > 0\ntrigger1 = Random = [0,899]\ntype = ChangeState",
                ),
            ),
            difficultyResponsiveness = DifficultyResponsiveness.NONE,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 1,
            notes = emptyList(),
        )

        val estimate = AiStrengthEstimator.estimate(analysis)
        assertEquals(1, estimate.evidenceCount)
        assertTrue((estimate.score ?: 0) > 80)
    }
}
