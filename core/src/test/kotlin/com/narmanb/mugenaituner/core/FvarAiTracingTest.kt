package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FvarAiTracingTest {
    @Test
    fun varSetFvSyntaxSeedsFloatAiVariable() {
        val source = """
            [State -2, AI]
            type = VarSet
            trigger1 = AILevel > 0
            fv = 5
            value = 1.0

            [State -1, Guard]
            type = ChangeState
            triggerall = fvar(5) > 0
            trigger1 = InGuardDist
            trigger1 = Random < 800
            value = 120
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("fighter.cmd", source)))

        val flag = result.aiFlags.single { it.kind == VariableKind.FVAR && it.variable == 5 }
        assertEquals(Confidence.HIGH, flag.confidence)
        assertEquals("fvar(5)", flag.expressionName)
        assertEquals(Confidence.HIGH, result.behaviors.single().confidence)
    }

    @Test
    fun alternateFvarControllerSyntaxIsRecognized() {
        val source = """
            [State -2, AI]
            type = VarSet
            trigger1 = AILevel
            fvar(7) = 1.0

            [State -1, Guard]
            type = ChangeState
            triggerall = fvar(7)
            trigger1 = InGuardDist
            trigger1 = Random < 700
            value = 120
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("fighter.cmd", source)))

        assertTrue(result.aiFlags.any { it.kind == VariableKind.FVAR && it.variable == 7 })
        assertTrue(result.behaviors.isNotEmpty())
    }

    @Test
    fun directFloatAssignmentCanSeedAiVariable() {
        val source = """
            [State -2, direct]
            type = Null
            trigger1 = (fvar(3) := AILevel)

            [State -1, Guard]
            type = ChangeState
            triggerall = fvar(3)
            trigger1 = InGuardDist
            trigger1 = Random < 600
            value = 120
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("fighter.cmd", source)))

        assertTrue(result.aiFlags.any {
            it.kind == VariableKind.FVAR && it.variable == 3 && it.confidence == Confidence.HIGH
        })
    }

    @Test
    fun mixedVarAndFvarDependencyChainRemainsTyped() {
        val source = """
            [State -2, root]
            type = VarSet
            trigger1 = AILevel
            v = 40
            value = 1

            [State -2, float alias]
            type = VarSet
            trigger1 = var(40)
            fv = 2
            value = 1.0

            [State -2, int alias]
            type = VarSet
            trigger1 = fvar(2)
            v = 12
            value = 1

            [State -1, Engage]
            type = ChangeState
            triggerall = var(12)
            trigger1 = P2BodyDist X < 60
            trigger1 = Random < 650
            value = 300
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("fighter.cns", source)))

        assertTrue(result.aiFlags.any { it.kind == VariableKind.VAR && it.variable == 40 })
        assertTrue(result.aiFlags.any { it.kind == VariableKind.FVAR && it.variable == 2 })
        assertTrue(result.aiFlags.any { it.kind == VariableKind.VAR && it.variable == 12 })
        assertTrue(result.behaviors.any { it.category == BehaviorCategory.AGGRESSION })
    }

    @Test
    fun integerAndFloatVariablesWithSameIndexDoNotCollide() {
        val source = """
            [State -2, AI]
            type = VarSet
            trigger1 = AILevel
            v = 5
            value = 1

            [State -1, Human float behavior]
            type = ChangeState
            triggerall = fvar(5) > 0
            trigger1 = InGuardDist
            trigger1 = Random < 900
            value = 120
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("fighter.cmd", source)))

        assertTrue(result.aiFlags.any { it.kind == VariableKind.VAR && it.variable == 5 })
        assertFalse(result.aiFlags.any { it.kind == VariableKind.FVAR && it.variable == 5 })
        assertTrue(result.behaviors.isEmpty())
    }
}
