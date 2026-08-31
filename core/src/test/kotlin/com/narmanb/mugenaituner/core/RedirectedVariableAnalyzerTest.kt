package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedirectedVariableAnalyzerTest {
    @Test
    fun `opponent var with same index as AI flag does not become AI behavior`() {
        val result = MugenAiAnalyzer.analyze(
            listOf(
                SourceFile(
                    "fighter.cmd",
                    """
                    [State -2, AI activation]
                    type = VarSet
                    trigger1 = AILevel > 0
                    v = 59
                    value = 1

                    [State -1, Opponent bookkeeping check]
                    type = ChangeState
                    trigger1 = EnemyNear, var(59) > 0
                    trigger1 = Random < 900
                    value = 200
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(result.aiFlags.any { it.variable == 59 && it.kind == VariableKind.VAR })
        assertTrue(result.behaviors.isEmpty())
        assertEquals(0, result.aiBehaviorCount)
    }

    @Test
    fun `redirected opponent var does not propagate into local AI flag`() {
        val result = MugenAiAnalyzer.analyze(
            listOf(
                SourceFile(
                    "fighter.cns",
                    """
                    [State -2, AI activation]
                    type = VarSet
                    trigger1 = AILevel > 0
                    v = 59
                    value = 1

                    [State -2, Unrelated local state]
                    type = VarSet
                    trigger1 = EnemyNear, var(59) > 0
                    v = 12
                    value = 1
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(result.aiFlags.any { it.variable == 59 && it.kind == VariableKind.VAR })
        assertFalse(result.aiFlags.any { it.variable == 12 && it.kind == VariableKind.VAR })
    }

    @Test
    fun `local AI flag inside redirection selector remains visible`() {
        val result = MugenAiAnalyzer.analyze(
            listOf(
                SourceFile(
                    "fighter.cmd",
                    """
                    [State -2, AI activation]
                    type = VarSet
                    trigger1 = AILevel > 0
                    v = 7
                    value = 1

                    [State -1, Selector decision]
                    type = ChangeState
                    trigger1 = PlayerID(var(7)), var(40) > 0
                    trigger1 = Random < 700
                    value = 300
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(result.behaviors.isNotEmpty())
        assertTrue(result.behaviors.any { it.confidence == Confidence.HIGH })
    }
}
