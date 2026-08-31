package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceCompletenessTest {
    @Test
    fun missingReferencedCodePreventsConfidentNoAiConclusion() {
        val analysis = MugenAiAnalyzer.analyze(
            listOf(
                SourceFile(
                    "char.def",
                    """
                    [Info]
                    name = "Incomplete"
                    [Files]
                    cmd = missing-ai.cmd
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(analysis.unresolvedSourceReferences.any { "missing-ai.cmd" in it })
        val compatibility = AnalyzerCompatibilityEstimator.estimate(analysis)
        assertEquals("Incomplete source graph", compatibility.label)
        assertEquals(0, compatibility.understandingScore)
        assertTrue(compatibility.notes.any { "cannot be ruled out" in it })
    }

    @Test
    fun detectedAiRemainsReadOnlyWhenAnotherReferencedCodeFileIsMissing() {
        val analysis = MugenAiAnalyzer.analyze(
            listOf(
                SourceFile(
                    "char.def",
                    """
                    [Info]
                    name = "Partial AI"
                    [Files]
                    cmd = char.cmd
                    st = missing-extra.st
                    """.trimIndent(),
                ),
                SourceFile(
                    "char.cmd",
                    """
                    [State -1, Attack]
                    type = ChangeState
                    triggerall = AILevel > 0
                    trigger1 = P2BodyDist X < 80
                    trigger1 = Random < 900
                    value = 1000
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(analysis.aiDetected)
        assertTrue(analysis.behaviors.isNotEmpty())
        assertTrue(analysis.unresolvedSourceReferences.any { "missing-extra.st" in it })

        val plan = AiEditPlanner.plan(
            analysis = analysis,
            profile = SkillProfile.fromPreset(DifficultyPreset.EASY),
            engineDifficultyScaling = false,
        )
        assertTrue(plan.edits.isEmpty())
        assertTrue(plan.notes.any { "Automatic editing is blocked" in it })

        val compatibility = AnalyzerCompatibilityEstimator.estimate(analysis)
        assertTrue((compatibility.understandingScore ?: 100) < 100)
        assertTrue(compatibility.notes.any { "could not be resolved" in it })
    }
}
