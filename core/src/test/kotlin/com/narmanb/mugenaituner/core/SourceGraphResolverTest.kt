package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun reportsMissingReferencedTextFile() {
        val files = listOf(
            SourceFile("char.def", "[Files]\ncmd = missing.cmd"),
        )

        val result = SourceGraphResolver.resolve(files)

        assertEquals(1, result.unresolvedReferences.size)
        assertTrue(result.unresolvedReferences.single().contains("missing.cmd"))
    }
}
