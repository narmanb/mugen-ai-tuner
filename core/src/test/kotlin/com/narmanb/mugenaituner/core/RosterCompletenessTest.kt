package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RosterCompletenessTest {
    @Test
    fun rosterCarriesCompatibilityAndMissingSourceCounts() {
        val analysis = CharacterAnalysis(
            characterName = "Incomplete",
            author = null,
            aiDetected = false,
            aiFlags = emptyList(),
            behaviors = emptyList(),
            difficultyResponsiveness = DifficultyResponsiveness.UNKNOWN,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 0,
            notes = emptyList(),
            unresolvedSourceReferences = listOf("char.def -> missing.cmd"),
        )

        val character = RosterAnalysis.summarize("incomplete", analysis)
        assertEquals(1, character.unresolvedReferenceCount)
        assertEquals(0, character.analyzerCompatibilityScore)

        val roster = RosterAnalysisSummary(
            characters = listOf(character),
            skippedFolders = emptyList(),
        )
        assertEquals(1, roster.incompleteSourceCount)
    }
}
