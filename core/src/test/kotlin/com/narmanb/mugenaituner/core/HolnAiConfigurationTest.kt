package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HolnAiConfigurationTest {
    @Test
    fun varSetVarKeySyntaxSeedsAiVariable() {
        val cmd = """
            [State -2, AI activation]
            type = VarSet
            trigger1 = AILevel > 0
            var(59) = 1

            [State -1, Attack]
            type = ChangeState
            triggerall = var(59)
            trigger1 = P2BodyDist X < 60
            trigger1 = Random < 700
            value = 1000
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("char.cmd", cmd)))

        assertTrue(result.aiDetected)
        assertTrue(result.aiFlags.any { it.variable == 59 && it.confidence == Confidence.HIGH })
        assertTrue(result.behaviors.isNotEmpty())
    }

    @Test
    fun detectsPackedComboAndMovementLevels() {
        val cns = """
            [State -2, combo config]
            type = VarAdd
            triggerall = !IsHelper
            triggerall = var(59) = 0 && AILevel
            triggerall = RoundState = 2
            trigger1 = (Alive && Ctrl) || var(59) > 0
            v = 59
            value = 10 * 2 ; combo setting 0~3

            [State -2, movement config]
            type = VarAdd
            triggerall = !IsHelper
            triggerall = var(59) = [1,9]
            triggerall = RoundState = 2
            trigger1 = (Alive && Ctrl) || var(59) > 0
            v = 59
            value = 10 * 3 ; movement setting 0~3
        """.trimIndent()

        val source = SourceFile("holn.cns", cns)
        val result = MugenAiAnalyzer.analyze(listOf(source))

        assertTrue(result.aiFlags.any { it.variable == 59 })
        assertEquals(2, result.configurationParameters.size)

        val combo = result.configurationParameters.single { it.kind == AiConfigurationKind.COMBO_LEVEL }
        assertEquals(2, combo.currentLevel)
        assertEquals(3, combo.maximumLevel)
        assertEquals(Confidence.HIGH, combo.confidence)
        assertTrue(combo.safeToEdit)

        val movement = result.configurationParameters.single { it.kind == AiConfigurationKind.MOVEMENT_LEVEL }
        assertEquals(3, movement.currentLevel)
        assertEquals(3, movement.maximumLevel)
        assertEquals(Confidence.HIGH, movement.confidence)
        assertTrue(movement.safeToEdit)

        // Normal (50%) maps a 0..3 author setting to level 2. Combo is already 2; movement 3 -> 2.
        val plan = AiEditPlanner.plan(
            analysis = result,
            profile = SkillProfile.fromPreset(DifficultyPreset.NORMAL),
            engineDifficultyScaling = false,
        )
        val movementEdit = plan.edits.single { it.category == BehaviorCategory.MOVEMENT }
        assertEquals("value = 10 * 3", movementEdit.originalExpression)
        assertEquals("value = 10 * 2", movementEdit.replacementExpression)

        val materialized = AiEditMaterializer.materialize(listOf(source), plan)
        assertTrue(materialized.isSafeToApply)
        assertEquals(1, materialized.mutations.size)
        assertTrue("value = 10 * 2 ; movement setting 0~3" in materialized.mutations.single().afterContent)
    }

    @Test
    fun structuralHolnPatternWorksWithoutEnglishCommentsButStaysReadOnly() {
        val cns = """
            [State -2]
            type = VarAdd
            triggerall = var(41) = 0 && AILevel
            trigger1 = Ctrl
            v = 41
            value = 10 * 1

            [State -2]
            type = VarAdd
            triggerall = var(41) = [1,9]
            trigger1 = Ctrl || var(41) > 0
            v = 41
            value = 10 * 2
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("opaque.cns", cns)))

        assertEquals(2, result.configurationParameters.size)
        assertEquals(Confidence.MEDIUM, result.configurationParameters[0].confidence)
        assertTrue(result.configurationParameters.none { it.safeToEdit })
        assertTrue(result.configurationParameters.any { it.kind == AiConfigurationKind.COMBO_LEVEL })
        assertTrue(result.configurationParameters.any { it.kind == AiConfigurationKind.MOVEMENT_LEVEL })
    }

    @Test
    fun explicitLabelWithoutMatchingHolnGateStaysReadOnly() {
        val cns = """
            [State -2, combo setting]
            type = VarAdd
            triggerall = AILevel > 0
            trigger1 = Ctrl
            v = 22
            value = 10 * 3 ; combo setting 0~3
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("different.cns", cns)))
        val parameter = result.configurationParameters.single()

        assertEquals(AiConfigurationKind.COMBO_LEVEL, parameter.kind)
        assertEquals(Confidence.MEDIUM, parameter.confidence)
        assertFalse(parameter.safeToEdit)
        assertTrue(AiConfigurationTuning.plan(result.configurationParameters, SkillProfile(30)).isEmpty())
    }

    @Test
    fun unrelatedVarAddDoesNotBecomeAi() {
        val cns = """
            [State 1000, hit counter]
            type = VarAdd
            trigger1 = MoveHit
            v = 59
            value = 1
        """.trimIndent()

        val result = MugenAiAnalyzer.analyze(listOf(SourceFile("normal.cns", cns)))

        assertFalse(result.aiDetected)
        assertTrue(result.aiFlags.isEmpty())
        assertTrue(result.configurationParameters.isEmpty())
    }
}
