package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyXorAiIntegrationTest {
    @Test
    fun xoredCommandPairsTraceLegacyAiFlagConservatively() {
        val source = """
            [State -2, legacy detector]
            type = VarSet
            trigger1 = (command = "opaque_a" ^^ command = "opaque_b")
            trigger2 = (command = "opaque_c" ^^ command = "opaque_d")
            v = 58
            value = 1

            [State -1, Guard]
            type = ChangeState
            triggerall = var(58)
            trigger1 = InGuardDist
            trigger1 = Random < 800
            value = 120
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("legacy.cmd", source)))

        assertTrue(result.aiDetected)
        val flag = result.aiFlags.single { it.variable == 58 }
        assertEquals(Confidence.MEDIUM, flag.confidence)
        val behavior = result.behaviors.single()
        assertEquals(BehaviorCategory.DEFENSE, behavior.category)
        assertEquals(Confidence.MEDIUM, behavior.confidence)

        val plan = AiEditPlanner.plan(result, SkillProfile.fromPreset(DifficultyPreset.EASY))
        assertTrue(plan.edits.isEmpty())
    }

    @Test
    fun oneOrdinaryXorPairDoesNotCreateCustomAi() {
        val source = """
            [State -2, unrelated]
            type = VarSet
            trigger1 = command = "left" ^^ command = "right"
            v = 58
            value = 1
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("normal.cmd", source)))

        assertFalse(result.aiDetected)
        assertTrue(result.aiFlags.none { it.variable == 58 })
    }
}
