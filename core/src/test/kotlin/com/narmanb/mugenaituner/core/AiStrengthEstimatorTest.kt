package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiStrengthEstimatorTest {
    @Test
    fun greaterThanLowThresholdLooksStrongerThanGreaterThanHighThreshold() {
        val strong = estimateFor("Random > 100")
        val weak = estimateFor("Random > 900")

        assertTrue(assertNotNull(strong.score) > assertNotNull(weak.score))
    }

    @Test
    fun equivalentLessAndGreaterProbabilitiesScoreSimilarly() {
        val less = estimateFor("Random < 450")
        val greater = estimateFor("Random > 549")

        val left = assertNotNull(less.score)
        val right = assertNotNull(greater.score)
        assertTrue(kotlin.math.abs(left - right) <= 1)
    }

    private fun estimateFor(randomExpression: String): AiStrengthEstimate {
        val analysis = CharacterAnalysis(
            characterName = "Test",
            author = null,
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = listOf(
                AiBehavior(
                    category = BehaviorCategory.REACTION,
                    summary = "Reaction",
                    confidence = Confidence.HIGH,
                    filePath = "test.cmd",
                    lineNumber = 10,
                    section = "State -1, Reaction",
                    rawCode = "triggerall = AILevel\ntrigger1 = $randomExpression\ntype = ChangeState",
                ),
            ),
            difficultyResponsiveness = DifficultyResponsiveness.NONE,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 1,
            notes = emptyList(),
        )
        return AiStrengthEstimator.estimate(analysis)
    }
}
