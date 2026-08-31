package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiLevelVariableDependencyTracerTest {
    @Test
    fun `traces bare AILevel stored in var`() {
        val dependencies = AiLevelVariableDependencyTracer.trace(
            listOf(
                SourceFile(
                    "fighter.cns",
                    """
                    [State -2, Difficulty value]
                    type = VarSet
                    trigger1 = 1
                    v = 40
                    value = AILevel
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(1, dependencies.size)
        assertEquals(VariableKind.VAR, dependencies.single().kind)
        assertEquals(40, dependencies.single().variable)
        assertEquals("AILevel", dependencies.single().expandedExpression)
    }

    @Test
    fun `does not treat pure AI on off value as numeric difficulty`() {
        val dependencies = AiLevelVariableDependencyTracer.trace(
            listOf(
                SourceFile(
                    "fighter.cns",
                    """
                    [State -2, AI flag]
                    type = VarSet
                    trigger1 = 1
                    v = 59
                    value = AILevel > 0
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(dependencies.isEmpty())
    }

    @Test
    fun `propagates numeric AILevel through mixed var and fvar values`() {
        val dependencies = AiLevelVariableDependencyTracer.trace(
            listOf(
                SourceFile(
                    "fighter.cns",
                    """
                    [State -2, Difficulty source]
                    type = VarSet
                    trigger1 = 1
                    v = 40
                    value = AILevel

                    [State -2, Float difficulty]
                    type = VarSet
                    trigger1 = 1
                    fv = 2
                    value = var(40) * 0.5
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(dependencies.any { it.kind == VariableKind.VAR && it.variable == 40 })
        val floatDependency = dependencies.single { it.kind == VariableKind.FVAR && it.variable == 2 }
        assertTrue(floatDependency.expandedExpression.contains("AILevel", ignoreCase = true))
    }

    @Test
    fun `redirected source variable does not propagate numeric difficulty`() {
        val dependencies = AiLevelVariableDependencyTracer.trace(
            listOf(
                SourceFile(
                    "fighter.cns",
                    """
                    [State -2, Difficulty source]
                    type = VarSet
                    trigger1 = 1
                    v = 40
                    value = AILevel

                    [State -2, Opponent-derived value]
                    type = VarSet
                    trigger1 = 1
                    v = 12
                    value = EnemyNear, var(40)
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(dependencies.any { it.variable == 40 && it.kind == VariableKind.VAR })
        assertFalse(dependencies.any { it.variable == 12 && it.kind == VariableKind.VAR })
    }

    @Test
    fun `detects indirect Random scaling through AILevel alias`() {
        val dependencies = listOf(
            AiLevelVariableDependencyTracer.Dependency(
                kind = VariableKind.VAR,
                variable = 40,
                expandedExpression = "AILevel",
                filePath = "fighter.cns",
                lineNumber = 1,
            ),
        )

        assertTrue(
            AiLevelVariableDependencyTracer.hasDifficultyScaling(
                "trigger1 = Random < var(40) * 100",
                dependencies,
            ),
        )
    }

    @Test
    fun `does not call simple alias AI gate numeric scaling`() {
        val dependencies = listOf(
            AiLevelVariableDependencyTracer.Dependency(
                kind = VariableKind.VAR,
                variable = 40,
                expandedExpression = "AILevel",
                filePath = "fighter.cns",
                lineNumber = 1,
            ),
        )

        assertFalse(
            AiLevelVariableDependencyTracer.hasDifficultyScaling(
                """
                triggerall = var(40) > 0
                trigger1 = Random < 900
                """.trimIndent(),
                dependencies,
            ),
        )
    }

    @Test
    fun `self referenced AILevel assignment converges`() {
        val dependencies = AiLevelVariableDependencyTracer.trace(
            listOf(
                SourceFile(
                    "fighter.cns",
                    """
                    [State -2, Accumulator]
                    type = VarAdd
                    trigger1 = 1
                    v = 1
                    value = AILevel + var(1)
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(1, dependencies.size)
        assertEquals(1, dependencies.single().variable)
    }
}
