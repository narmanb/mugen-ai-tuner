package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrengthUnknownBehaviorTest {
    @Test
    fun unclassifiedProbabilityDoesNotCreateDifficultyScore() {
        val analysis = CharacterAnalysis(
            characterName = "Opaque AI",
            author = null,
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = listOf(
                AiBehavior(
                    category = BehaviorCategory.UNKNOWN,
                    summary = "Unknown",
                    confidence = Confidence.HIGH,
                    filePath = "opaque.cmd",
                    lineNumber = 10,
                    section = "State -1, 10",
                    rawCode = "triggerall = AILevel > 0\ntrigger1 = Random < 999\ntype = ChangeState\nvalue = 9999",
                ),
            ),
            difficultyResponsiveness = DifficultyResponsiveness.NONE,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 1,
            notes = emptyList(),
        )

        val estimate = AiStrengthEstimator.estimate(analysis)

        assertNull(estimate.score)
        assertEquals(0, estimate.evidenceCount)
        assertTrue(estimate.notes.any { "excluded" in it })
    }

    @Test
    fun unknownEvidenceDoesNotDistortClassifiedEvidence() {
        val classified = AiBehavior(
            category = BehaviorCategory.DEFENSE,
            summary = "Guard",
            confidence = Confidence.HIGH,
            filePath = "char.cmd",
            lineNumber = 10,
            section = "State -1, Guard",
            rawCode = "triggerall = AILevel > 0\ntrigger1 = InGuardDist\ntrigger1 = Random < 400\ntype = ChangeState\nvalue = 120",
        )
        val unknown = AiBehavior(
            category = BehaviorCategory.UNKNOWN,
            summary = "Unknown",
            confidence = Confidence.HIGH,
            filePath = "char.cmd",
            lineNumber = 20,
            section = "State -1, 20",
            rawCode = "triggerall = AILevel > 0\ntrigger1 = Random < 999\ntype = ChangeState\nvalue = 9999",
        )

        fun estimate(behaviors: List<AiBehavior>) = AiStrengthEstimator.estimate(
            CharacterAnalysis(
                characterName = "Mixed AI",
                author = null,
                aiDetected = true,
                aiFlags = emptyList(),
                behaviors = behaviors,
                difficultyResponsiveness = DifficultyResponsiveness.NONE,
                directlyScaledBehaviorCount = 0,
                aiBehaviorCount = behaviors.size,
                notes = emptyList(),
            ),
        )

        val classifiedOnly = estimate(listOf(classified))
        val mixed = estimate(listOf(classified, unknown))

        assertEquals(classifiedOnly.score, mixed.score)
        assertEquals(classifiedOnly.evidenceCount, mixed.evidenceCount)
        assertTrue(mixed.notes.any { "excluded" in it })
    }
}
