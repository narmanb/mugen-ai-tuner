package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyXorAiDetectorTest {
    @Test
    fun detectsMultipleXoredCommandPairs() {
        val code = """
            trigger1 = (command = "cpu_a" ^^ command = "cpu_b")
            trigger2 = (command = "cpu_c" ^^ command = "cpu_d")
        """.trimIndent()

        val detected = LegacyXorAiDetector.detectCommandNames(code)

        assertEquals(setOf("cpu_a", "cpu_b", "cpu_c", "cpu_d"), detected)
    }

    @Test
    fun singleXorPairIsNotEnoughEvidence() {
        val code = "trigger1 = command = \"left\" ^^ command = \"right\""

        assertTrue(LegacyXorAiDetector.detectCommandNames(code).isEmpty())
    }

    @Test
    fun nonCommandXorLogicIsIgnored() {
        val code = """
            trigger1 = var(1) ^^ var(2)
            trigger2 = var(3) ^^ var(4)
        """.trimIndent()

        assertTrue(LegacyXorAiDetector.detectCommandNames(code).isEmpty())
    }
}
