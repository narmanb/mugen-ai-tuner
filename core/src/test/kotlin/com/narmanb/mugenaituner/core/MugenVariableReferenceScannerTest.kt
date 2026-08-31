package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MugenVariableReferenceScannerTest {
    @Test
    fun `keeps ordinary var and fvar references local`() {
        val refs = MugenVariableReferenceScanner.localReferences(
            "trigger1 = var(12) && fvar(3) > 0",
        )

        assertEquals(
            listOf(
                MugenVariableReferenceScanner.Reference(VariableKind.VAR, 12, 11),
                MugenVariableReferenceScanner.Reference(VariableKind.FVAR, 3, 22),
            ),
            refs,
        )
    }

    @Test
    fun `excludes simple redirected variable references`() {
        val code = "trigger1 = EnemyNear, var(59) || Root, fvar(3) || Parent, var(2)"
        val refs = MugenVariableReferenceScanner.localReferences(code)

        assertTrue(refs.isEmpty())
    }

    @Test
    fun `excludes function style redirected variable references`() {
        val code = "trigger1 = Helper(1000), var(4) || Target(23), fvar(5)"
        val refs = MugenVariableReferenceScanner.localReferences(code)

        assertTrue(refs.isEmpty())
    }

    @Test
    fun `keeps local variables used inside redirection selectors`() {
        val code = "trigger1 = PlayerID(var(7)), var(59) > 0"
        val refs = MugenVariableReferenceScanner.localReferences(code)

        assertEquals(1, refs.size)
        assertEquals(VariableKind.VAR, refs.single().kind)
        assertEquals(7, refs.single().index)
        assertFalse(refs.any { it.index == 59 })
    }

    @Test
    fun `handles nested selector parentheses`() {
        val code = "trigger1 = PlayerID(ifelse(var(6), var(7), 10)), fvar(2)"
        val refs = MugenVariableReferenceScanner.localReferences(code)

        assertEquals(setOf(6, 7), refs.map { it.index }.toSet())
        assertTrue(refs.all { it.kind == VariableKind.VAR })
    }
}
