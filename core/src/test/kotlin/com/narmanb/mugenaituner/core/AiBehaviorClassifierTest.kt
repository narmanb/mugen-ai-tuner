package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AiBehaviorClassifierTest {
    @Test
    fun leavesSemanticallyOpaqueProbabilityBlockUnknown() {
        val code = """
            type = ChangeState
            triggerall = AILevel > 0
            trigger1 = Random < 700
            value = 9999
        """.trimIndent()
        assertEquals(BehaviorCategory.UNKNOWN, AiBehaviorClassifier.classify(code, "State -1, 99"))
    }

    @Test
    fun recognizesStrongGuardAntiAirProjectileAndReactionEvidence() {
        assertEquals(
            BehaviorCategory.DEFENSE,
            AiBehaviorClassifier.classify(
                "type = ChangeState\ntrigger1 = P2MoveType = A\nvalue = 120",
                "State -1, 10",
            ),
        )
        assertEquals(
            BehaviorCategory.ANTI_AIR,
            AiBehaviorClassifier.classify(
                "type = ChangeState\ntrigger1 = EnemyNear, StateType = A\nvalue = 1000",
                "State -1, 11",
            ),
        )
        assertEquals(
            BehaviorCategory.PROJECTILE_RESPONSE,
            AiBehaviorClassifier.classify(
                "type = ChangeState\ntrigger1 = ProjHitTime(0) >= 0\nvalue = 1001",
                "State -1, 12",
            ),
        )
        assertEquals(
            BehaviorCategory.REACTION,
            AiBehaviorClassifier.classify(
                "type = ChangeState\ntrigger1 = P2StateNo = 5000\nvalue = 1002",
                "State -1, 13",
            ),
        )
    }

    @Test
    fun doesNotCallLowResourceAttackASuperOnlyBecauseItChecksPower() {
        val code = """
            type = ChangeState
            trigger1 = P2BodyDist X < 80
            trigger1 = Power >= 500
            value = 1100
        """.trimIndent()
        assertEquals(BehaviorCategory.AGGRESSION, AiBehaviorClassifier.classify(code, "State -1, Attack"))

        val superCode = """
            type = ChangeState
            trigger1 = Power >= 3000
            value = 3000
        """.trimIndent()
        assertEquals(BehaviorCategory.SUPER, AiBehaviorClassifier.classify(superCode, "State -1, 3000"))
    }

    @Test
    fun comboEvidenceWinsOverGenericDistance() {
        val code = """
            type = ChangeState
            trigger1 = MoveHit
            trigger1 = P2BodyDist X < 40
            value = 1200
        """.trimIndent()
        assertEquals(BehaviorCategory.COMBO, AiBehaviorClassifier.classify(code, "State -1, 14"))
    }
}
