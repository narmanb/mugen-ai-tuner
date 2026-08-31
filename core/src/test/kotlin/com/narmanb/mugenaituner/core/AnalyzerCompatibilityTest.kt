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
        val score = assertNotNull(result.understandingScore)
        assertTrue(score >= 85)
        assertEquals(5, result.highConfidenceBehaviors)
    }

    @Test
    fun understoodPackedSettingCountsAsCompatibilityWithoutPretendingItIsWritable() {
        val parameter = AiConfigurationParameter(
            kind = AiConfigurationKind.COMBO_LEVEL,
            label = "Combo level",
            variable = 59,
            currentLevel = 2,
            minimumLevel = 0,
            maximumLevel = 3,
            confidence = Confidence.HIGH,
            filePath = "ai.cns",
            lineNumber = 20,
            originalExpression = "value = 10 * 2",
            description = "Combo setting",
            safeToEdit = false,
        )
        val analysis = CharacterAnalysis(
            characterName = "Packed",
            author = null,
            aiDetected = true,
            aiFlags = listOf(AiFlag(59, Confidence.HIGH, "AI")),
            behaviors = emptyList(),
            difficultyResponsiveness = DifficultyResponsiveness.NONE,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 0,
            notes = emptyList(),
            configurationParameters = listOf(parameter),
        )

        val result = AnalyzerCompatibilityEstimator.estimate(analysis)

        assertTrue((result.understandingScore ?: 0) >= 85)
        assertEquals(1, result.understoodConfigurationParameters)
        assertEquals(0, result.safeConfigurationParameters)
        assertEquals(0, result.safeEditCandidateCount)
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
