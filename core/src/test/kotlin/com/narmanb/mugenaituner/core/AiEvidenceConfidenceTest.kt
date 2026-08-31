package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AiEvidenceConfidenceTest {
    @Test
    fun highConfidenceFlagOutranksMediumDirectAiLevelEvidence() {
        val source = """
            [State -2, AI]
            type = VarSet
            trigger1 = AILevel > 0
            v = 40
            value = 1

            [State -1, Guard]
            type = ChangeState
            triggerall = var(40)
            trigger1 = AILevel * 100 > 0
            trigger1 = InGuardDist
            trigger1 = Random < 700
            value = 120
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("fighter.cmd", source)))

        assertEquals(Confidence.HIGH, result.behaviors.single().confidence)
    }
}
