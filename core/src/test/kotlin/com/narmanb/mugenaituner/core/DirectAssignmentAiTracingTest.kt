package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectAssignmentAiTracingTest {
    @Test
    fun plainEqualsComparisonDoesNotSeedAiVariable() {
        val source = SourceFile(
            "compare.cmd",
            """
            [State -1, Compare]
            type = ChangeState
            trigger1 = var(17) = AILevel
            value = 1000
            """.trimIndent(),
        )

        val result = MugenAiAnalyzer.analyze(listOf(source))

        assertFalse(result.aiFlags.any { it.variable == 17 })
    }

    @Test
    fun colonEqualsAssignmentSeedsAndPropagatesAiVariable() {
        val source = SourceFile(
            "assign.cmd",
            """
            [State -2, Seed]
            type = Null
            trigger1 = var(17) := AILevel > 0

            [State -2, Alias]
            type = Null
            trigger1 = var(23) := var(17) * 2

            [State -1, Attack]
            type = ChangeState
            triggerall = var(23) > 0
            trigger1 = Random < 700
            value = 1000
            """.trimIndent(),
        )

        val result = MugenAiAnalyzer.analyze(listOf(source))

        assertTrue(result.aiFlags.any { it.variable == 17 })
        assertTrue(result.aiFlags.any { it.variable == 23 })
        assertTrue(result.behaviors.any { it.filePath == "assign.cmd" })
    }
}
