package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyAiDetectionTest {
    @Test
    fun tracesOpaqueImpossibleCommandSetIntoAiFlag() {
        val cmd = """
            [Command]
            name = "mystery_one"
            command = a,b,c,x,y,z,a,b,c

            [Command]
            name = "mystery_two"
            command = x,y,z,a,b,c,x,y,z

            [Command]
            name = "mystery_three"
            command = U,D,F,B,x,y,z,a,b

            [Command]
            name = "mystery_four"
            command = B,F,U,D,a,b,c,x,y

            [State -1, hidden activation]
            type = VarSet
            trigger1 = command = "mystery_one"
            trigger2 = command = "mystery_two"
            trigger3 = command = "mystery_three"
            trigger4 = command = "mystery_four"
            v = 37
            value = 1

            [State -1, Guard]
            type = ChangeState
            triggerall = var(37)
            triggerall = InGuardDist
            trigger1 = Random < 900
            value = 120
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("opaque.cmd", cmd)))

        assertTrue(result.aiDetected)
        assertTrue(result.aiFlags.any { it.variable == 37 })
        assertTrue(result.behaviors.any { it.category == BehaviorCategory.DEFENSE })
    }

    @Test
    fun singleLongHumanCommandDoesNotCreateAiByItself() {
        val cmd = """
            [Command]
            name = "dramatic_finisher"
            command = D,DF,F,D,DF,F,x+y,z,a

            [State -1, Dramatic Finisher]
            type = ChangeState
            trigger1 = command = "dramatic_finisher"
            trigger1 = power >= 3000
            value = 4000
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("human.cmd", cmd)))

        assertFalse(result.aiDetected)
        assertEquals(0, result.aiFlags.size)
    }

    @Test
    fun airSubstringDoesNotCountAsAiNameHint() {
        val cmd = """
            [Command]
            name = "air_combo"
            command = a,b,c,x,y,z,a,b,c

            [State -1, Air Combo]
            type = ChangeState
            trigger1 = command = "air_combo"
            trigger1 = statetype = A
            value = 1300
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("air.cmd", cmd)))

        assertFalse(result.aiDetected)
        assertEquals(0, result.aiFlags.size)
    }

    @Test
    fun cpuNamedImpossibleCommandCanSeedLegacyAiFlag() {
        val cmd = """
            [Command]
            name = "CPU17"
            command = a,b,c,x,y,z,a,b,c

            [State -1, AI activation]
            type = VarSet
            trigger1 = command = "CPU17"
            v = 58
            value = 1

            [State -1, Attack]
            type = ChangeState
            triggerall = var(58)
            trigger1 = P2BodyDist X < 80
            trigger1 = Random < 700
            value = 1000
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("cpu.cmd", cmd)))

        assertTrue(result.aiFlags.any { it.variable == 58 })
        assertTrue(result.aiBehaviorCount > 0)
    }
}
