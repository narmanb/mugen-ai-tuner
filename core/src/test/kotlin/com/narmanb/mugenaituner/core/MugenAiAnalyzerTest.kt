package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MugenAiAnalyzerTest {
    @Test
    fun `traces var59 from AILevel instead of assuming its meaning`() {
        val files = listOf(
            SourceFile(
                "Ada.def",
                """
                [Info]
                name = "Ada Wong"
                author = "Example"
                """.trimIndent(),
            ),
            SourceFile(
                "Ada.cmd",
                """
                [State -2, AI activation]
                type = VarSet
                trigger1 = AILevel > 0
                v = 59
                value = 1

                [State -1, Guard reaction]
                type = ChangeState
                triggerall = var(59)
                trigger1 = InGuardDist
                trigger1 = Random < 900
                value = 120
                """.trimIndent(),
            ),
        )

        val result = MugenAiAnalyzer.analyze(files)

        assertTrue(result.aiDetected)
        assertTrue(result.aiFlags.any { it.variable == 59 && it.confidence == Confidence.HIGH })
        assertTrue(result.behaviors.any { it.category == BehaviorCategory.DEFENSE })
        assertEquals(DifficultyResponsiveness.NONE, result.difficultyResponsiveness)
    }

    @Test
    fun `does not treat unrelated var59 as AI`() {
        val files = listOf(
            SourceFile(
                "fighter.cmd",
                """
                [State -2, Combo counter]
                type = VarSet
                trigger1 = MoveHit
                v = 59
                value = 10

                [State -1, Attack]
                type = ChangeState
                triggerall = var(59) > 0
                trigger1 = command = "x"
                value = 200
                """.trimIndent(),
            ),
        )

        val result = MugenAiAnalyzer.analyze(files)

        assertFalse(result.aiDetected)
        assertTrue(result.aiFlags.none { it.variable == 59 })
    }

    @Test
    fun `recognizes numeric AILevel scaling`() {
        val files = listOf(
            SourceFile(
                "fighter.cmd",
                """
                [State -1, Anti Air]
                type = ChangeState
                triggerall = AILevel > 0
                trigger1 = P2StateType = A
                trigger1 = Random < AILevel * 100
                value = 1000
                """.trimIndent(),
            ),
        )

        val result = MugenAiAnalyzer.analyze(files)

        assertTrue(result.aiDetected)
        assertEquals(1, result.directlyScaledBehaviorCount)
        assertEquals(DifficultyResponsiveness.FULL, result.difficultyResponsiveness)
        assertEquals(BehaviorCategory.ANTI_AIR, result.behaviors.single().category)
    }

    @Test
    fun `AILevel greater or equal one is only an AI on off gate`() {
        val files = listOf(
            SourceFile(
                "fighter.cmd",
                """
                [State -1, Guard]
                type = ChangeState
                triggerall = AILevel >= 1
                trigger1 = InGuardDist
                trigger1 = Random < 850
                value = 120
                """.trimIndent(),
            ),
        )

        val result = MugenAiAnalyzer.analyze(files)

        assertTrue(result.aiDetected)
        assertEquals(0, result.directlyScaledBehaviorCount)
        assertEquals(DifficultyResponsiveness.NONE, result.difficultyResponsiveness)
    }

    @Test
    fun `AILevel threshold above one is genuine difficulty gating`() {
        val files = listOf(
            SourceFile(
                "fighter.cmd",
                """
                [State -1, Guard]
                type = ChangeState
                triggerall = AILevel >= 4
                trigger1 = InGuardDist
                trigger1 = Random < 850
                value = 120
                """.trimIndent(),
            ),
        )

        val result = MugenAiAnalyzer.analyze(files)

        assertTrue(result.aiDetected)
        assertEquals(1, result.directlyScaledBehaviorCount)
        assertEquals(DifficultyResponsiveness.FULL, result.difficultyResponsiveness)
    }

    @Test
    fun `follows simple AI flag chains`() {
        val files = listOf(
            SourceFile(
                "fighter.cns",
                """
                [State -2, AI activation]
                type = VarSet
                trigger1 = AILevel
                v = 40
                value = 1

                [State -2, Secondary AI flag]
                type = VarSet
                trigger1 = var(40)
                v = 12
                value = 1

                [State -1, Engage]
                type = ChangeState
                triggerall = var(12)
                trigger1 = P2BodyDist X < 70
                trigger1 = Random < 700
                value = 300
                """.trimIndent(),
            ),
        )

        val result = MugenAiAnalyzer.analyze(files)

        assertTrue(result.aiFlags.any { it.variable == 40 && it.confidence == Confidence.HIGH })
        assertTrue(result.aiFlags.any { it.variable == 12 })
        assertTrue(result.behaviors.any { it.category == BehaviorCategory.AGGRESSION })
    }
}
