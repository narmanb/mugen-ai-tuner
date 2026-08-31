package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterHealthValidatorTest {
    @Test
    fun validTunerGeneratedIfElseExpressionPassesStructuralChecks() {
        val files = listOf(
            SourceFile("char.def", "[Files]\ncmd = char.cmd\n"),
            SourceFile(
                "char.cmd",
                "trigger1 = Random < ifelse(AILevel <= 4, 350 * AILevel / 4.0, 350 + (900 - 350) * (AILevel - 4) / 4.0)\n",
            ),
        )

        val report = CharacterHealthValidator.validate(files)

        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun introducedBrokenParenthesisIsBlocking() {
        val before = CharacterHealthValidator.validate(
            listOf(SourceFile("char.cmd", "trigger1 = Random < 900\n")),
        )
        val after = CharacterHealthValidator.validate(
            listOf(SourceFile("char.cmd", "trigger1 = Random < ifelse(AILevel, 400\n")),
        )

        val introduced = CharacterHealthValidator.introducedErrors(before, after)

        assertEquals(1, introduced.size)
        assertEquals("UNBALANCED_PARENTHESES", introduced.single().code)
    }

    @Test
    fun existingAuthorQuirkIsNotBlamedOnTuner() {
        val before = CharacterHealthValidator.validate(
            listOf(SourceFile("char.cmd", "trigger1 = (Random < 900\n")),
        )
        val after = CharacterHealthValidator.validate(
            listOf(SourceFile("char.cmd", "trigger1 = (Random < 400\n")),
        )

        assertTrue(CharacterHealthValidator.introducedErrors(before, after).isEmpty())
    }

    @Test
    fun malformedSectionAndEmptyAssignmentAreReported() {
        val report = CharacterHealthValidator.validate(
            listOf(SourceFile("bad.cmd", "[State -1, Test\ntrigger1 =\n")),
        )

        val codes = report.errors.map { it.code }.toSet()
        assertTrue("MALFORMED_SECTION_HEADER" in codes)
        assertTrue("EMPTY_ASSIGNMENT" in codes)
    }
}
