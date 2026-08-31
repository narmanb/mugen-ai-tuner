package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RosterAnalysisTest {
    @Test
    fun rosterSummaryCarriesAnalyzerAndStrengthSignals() {
        val analysis = CharacterAnalysis(
            characterName = "Ada",
            author = "Example",
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = listOf(
                AiBehavior(
                    category = BehaviorCategory.DEFENSE,
                    summary = "Guard reaction",
                    confidence = Confidence.HIGH,
                    filePath = "ada.cmd",
                    lineNumber = 10,
                    section = "State -1, Guard",
                    rawCode = "trigger1 = AILevel\ntrigger1 = InGuardDist\ntrigger1 = Random < 900\ntype = ChangeState",
                ),
                AiBehavior(
                    category = BehaviorCategory.ANTI_AIR,
                    summary = "Anti-air",
                    confidence = Confidence.HIGH,
                    filePath = "ada.cmd",
                    lineNumber = 20,
                    section = "State -1, AntiAir",
                    rawCode = "trigger1 = AILevel\ntrigger1 = P2StateType = A\ntrigger1 = Random < 850\ntype = ChangeState",
                ),
            ),
            difficultyResponsiveness = DifficultyResponsiveness.NONE,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 2,
            notes = emptyList(),
        )

        val summary = RosterAnalysis.summarize("ada", analysis)

        assertEquals("ada", summary.folderName)
        assertEquals("Ada", summary.characterName)
        assertTrue(summary.aiDetected)
        assertEquals(DifficultyResponsiveness.NONE, summary.difficultyResponsiveness)
        assertEquals(2, summary.highConfidenceBehaviorCount)
        assertTrue((summary.estimatedStrength ?: 0) > 0)
    }

    @Test
    fun rosterAggregateCountsDifficultyInsensitiveCharacters() {
        val items = listOf(
            RosterCharacterSummary(
                folderName = "a",
                characterName = "A",
                author = null,
                aiDetected = true,
                estimatedStrength = 80,
                estimatedStrengthLabel = "Hard",
                estimateConfidence = Confidence.MEDIUM,
                difficultyResponsiveness = DifficultyResponsiveness.NONE,
                aiBehaviorCount = 4,
                highConfidenceBehaviorCount = 3,
                mediumConfidenceBehaviorCount = 1,
            ),
            RosterCharacterSummary(
                folderName = "b",
                characterName = "B",
                author = null,
                aiDetected = false,
                estimatedStrength = null,
                estimatedStrengthLabel = "No custom AI detected",
                estimateConfidence = Confidence.UNKNOWN,
                difficultyResponsiveness = DifficultyResponsiveness.UNKNOWN,
                aiBehaviorCount = 0,
                highConfidenceBehaviorCount = 0,
                mediumConfidenceBehaviorCount = 0,
            ),
        )

        val roster = RosterAnalysisSummary(items, emptyList())
        assertEquals(1, roster.customAiCount)
        assertEquals(1, roster.difficultyInsensitiveCount)
        assertEquals(1, roster.estimatedHardOrBrutalCount)
    }
}
