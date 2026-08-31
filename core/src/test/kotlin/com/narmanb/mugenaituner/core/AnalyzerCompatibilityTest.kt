package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnalyzerCompatibilityTest {
    @Test
    fun highConfidenceClassifiedAiScoresHighly() {
        val behavior = AiBehavior(
            category = BehaviorCategory.DEFENSE,
            summary = "Guard",
            confidence = Confidence.HIGH,
            filePath = "test.cmd",
            lineNumber = 10,
            section = "State -1, Guard",
            rawCode = "triggerall = AILevel\ntrigger1 = InGuardDist\ntrigger1 = Random < 900\ntype = ChangeState",
        )
        val analysis = CharacterAnalysis(
            characterName = "Test",
            author = null,
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = List(5) { index -> behavior.copy(lineNumber = 10 + index) },
            difficultyResponsiveness = DifficultyResponsiveness.NONE,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 5,
            notes = emptyList(),
        )

        val result = AnalyzerCompatibilityEstimator.estimate(analysis)
        assertNotNull(result.understandingScore)
        assertTrue(result.understandingScore >= 85)
        assertEquals(5, result.highConfidenceBehaviors)
    }

    @Test
    fun uncertainAiDoesNotPretendToBeWellSupported() {
        val analysis = CharacterAnalysis(
            characterName = "Odd",
            author = null,
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = listOf(
                AiBehavior(
                    category = BehaviorCategory.UNKNOWN,
                    summary = "Unknown",
                    confidence = Confidence.UNKNOWN,
                    filePath = "odd.cns",
                    lineNumber = 1,
                    section = "State -1",
                    rawCode = "type = ChangeState",
                ),
            ),
            difficultyResponsiveness = DifficultyResponsiveness.UNKNOWN,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 1,
            notes = emptyList(),
        )

        val result = AnalyzerCompatibilityEstimator.estimate(analysis)
        assertTrue((result.understandingScore ?: 100) < 40)
        assertEquals(1, result.uncertainBehaviors)
        assertEquals(0, result.safeEditCandidateCount)
    }
}
