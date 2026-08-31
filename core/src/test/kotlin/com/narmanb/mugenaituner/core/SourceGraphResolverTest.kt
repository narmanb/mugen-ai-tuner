package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourceGraphResolverTest {
    @Test
    fun ignoresUnreferencedOldAiFiles() {
        val files = listOf(
            SourceFile(
                "char.def",
                """
                [Files]
                cmd = char.cmd
                cns = data/main.cns
                st = data/states.st
                """.trimIndent(),
            ),
            SourceFile("char.cmd", "[State -1, AI]"),
            SourceFile("data/main.cns", "#include states-extra.st"),
            SourceFile("data/states.st", "[Statedef 1000]"),
            SourceFile("data/states-extra.st", "[Statedef 2000]"),
            SourceFile("old-ai.cmd", "trigger1 = AILevel"),
        )

        val result = SourceGraphResolver.resolve(files)
        val reachable = result.reachableFiles.map { it.path }.toSet()

        assertTrue("char.def" in reachable)
        assertTrue("char.cmd" in reachable)
        assertTrue("data/main.cns" in reachable)
        assertTrue("data/states.st" in reachable)
        assertTrue("data/states-extra.st" in reachable)
        assertEquals(listOf("old-ai.cmd"), result.ignoredTextFiles.map { it.path })
    }

    @Test
    fun constAndCdsFilesReferencedByDefAreReachable() {
        val files = listOf(
            SourceFile(
                "char.def",
                """
                [Files]
                cmd = char.cds
                cns = data/char.const
                st = states.zss
                """.trimIndent(),
            ),
            SourceFile("char.cds", "[State -1, Guard]\ntrigger1 = AILevel > 0"),
            SourceFile("data/char.const", "[Data]\nlife = 1000"),
            SourceFile("states.zss", "[Statedef 1000]"),
            SourceFile("old-ai.cds", "trigger1 = AILevel"),
        )

        val result = SourceGraphResolver.resolveFromDef(files, "char.def")
        val reachable = result.reachableFiles.map { it.path }.toSet()

        assertEquals(setOf("char.def", "char.cds", "data/char.const", "states.zss"), reachable)
        assertEquals(listOf("old-ai.cds"), result.ignoredTextFiles.map { it.path })
        assertTrue(result.unresolvedReferences.isEmpty())
    }

    @Test
    fun explicitDefKeepsAlternateModesIsolated() {
        val files = listOf(
            SourceFile("normal.def", "[Files]\ncmd = normal.cmd\nst = shared.st"),
            SourceFile("boss.def", "[Files]\ncmd = boss.cmd\nst = shared.st"),
            SourceFile("normal.cmd", "trigger1 = AILevel\ntrigger1 = Random < 400"),
            SourceFile("boss.cmd", "trigger1 = AILevel\ntrigger1 = Random < 950"),
            SourceFile("shared.st", "[Statedef 1000]"),
        )

        val result = SourceGraphResolver.resolveFromDef(files, "normal.def")
        val reachable = result.reachableFiles.map { it.path }.toSet()

        assertEquals(setOf("normal.def", "normal.cmd", "shared.st"), reachable)
        assertTrue("boss.def" in result.ignoredTextFiles.map { it.path })
        assertTrue("boss.cmd" in result.ignoredTextFiles.map { it.path })
    }

    @Test
    fun explicitDefMustExist() {
        val files = listOf(SourceFile("char.def", "[Files]\ncmd = char.cmd"))

        assertFailsWith<IllegalStateException> {
            SourceGraphResolver.resolveFromDef(files, "missing.def")
        }
    }

    @Test
    fun reportsMissingReferencedTextFile() {
        val files = listOf(
            SourceFile("char.def", "[Files]\ncmd = missing.cmd"),
        )

        val result = SourceGraphResolver.resolve(files)

        assertEquals(1, result.unresolvedReferences.size)
        assertTrue(result.unresolvedReferences.single().contains("missing.cmd"))
    }

    @Test
    fun engineProvidedStcommonDoesNotMakeCharacterIncomplete() {
        val files = listOf(
            SourceFile(
                "char.def",
                """
                [Files]
                cmd = char.cmd
                stcommon = common1.cns
                """.trimIndent(),
            ),
            SourceFile("char.cmd", "[State -1, Attack]\ntype = ChangeState"),
        )

        val result = SourceGraphResolver.resolve(files)

        assertTrue(result.unresolvedReferences.isEmpty())
        assertEquals(setOf("char.def", "char.cmd"), result.reachableFiles.map { it.path }.toSet())
    }

    @Test
    fun bundledCustomStcommonIsStillAnalyzedWhenPresent() {
        val files = listOf(
            SourceFile(
                "char.def",
                """
                [Files]
                cmd = char.cmd
                stcommon = custom-common.cns
                """.trimIndent(),
            ),
            SourceFile("char.cmd", "[State -1, Attack]\ntype = ChangeState"),
            SourceFile("custom-common.cns", "[State -1, Common AI]\ntrigger1 = AILevel > 0"),
        )

        val result = SourceGraphResolver.resolve(files)

        assertTrue(result.unresolvedReferences.isEmpty())
        assertTrue("custom-common.cns" in result.reachableFiles.map { it.path })
    }
}
