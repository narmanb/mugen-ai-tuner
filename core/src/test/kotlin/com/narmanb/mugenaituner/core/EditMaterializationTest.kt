package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditMaterializationTest {
    @Test
    fun materializerAppliesUniqueExpression() {
        val source = SourceFile(
            path = "char.cmd",
            content = "triggerall = AILevel\ntrigger1 = Random < 900\ntype = ChangeState\n",
        )
        val edit = PlannedEdit(
            category = BehaviorCategory.DEFENSE,
            filePath = "char.cmd",
            sourceLine = 2,
            originalExpression = "Random < 900",
            replacementExpression = "Random < 350",
            confidence = Confidence.HIGH,
            reason = "test",
        )
        val plan = EditPlan(
            profile = SkillProfile(overallSkill = 50),
            edits = listOf(edit),
            skippedBehaviorCount = 0,
            notes = emptyList(),
        )

        val materialized = AiEditMaterializer.materialize(listOf(source), plan)

        assertTrue(materialized.isSafeToApply)
        assertEquals(1, materialized.mutations.size)
        assertTrue("Random < 350" in materialized.mutations.single().afterContent)
        assertFalse("Random < 900" in materialized.mutations.single().afterContent)
    }

    @Test
    fun materializerUsesSourceHintWhenThresholdRepeats() {
        val source = SourceFile(
            path = "char.cmd",
            content = "trigger1 = Random < 900\nother = 1\nother = 2\ntrigger2 = Random < 900\n",
        )
        val edit = PlannedEdit(
            category = BehaviorCategory.REACTION,
            filePath = "char.cmd",
            sourceLine = 4,
            originalExpression = "Random < 900",
            replacementExpression = "Random < 400",
            confidence = Confidence.HIGH,
            reason = "test",
        )
        val plan = EditPlan(
            profile = SkillProfile(overallSkill = 50),
            edits = listOf(edit),
            skippedBehaviorCount = 0,
            notes = emptyList(),
        )

        val materialized = AiEditMaterializer.materialize(listOf(source), plan)

        assertTrue(materialized.isSafeToApply)
        assertEquals(
            "trigger1 = Random < 900\nother = 1\nother = 2\ntrigger2 = Random < 400\n",
            materialized.mutations.single().afterContent,
        )
    }

    @Test
    fun materializerRejectsEquidistantRepeatedExpression() {
        val source = SourceFile(
            path = "char.cmd",
            content = "trigger1 = Random < 900\nother = 1\ntrigger2 = Random < 900\n",
        )
        val edit = PlannedEdit(
            category = BehaviorCategory.REACTION,
            filePath = "char.cmd",
            sourceLine = 2,
            originalExpression = "Random < 900",
            replacementExpression = "Random < 400",
            confidence = Confidence.HIGH,
            reason = "test",
        )
        val plan = EditPlan(
            profile = SkillProfile(overallSkill = 50),
            edits = listOf(edit),
            skippedBehaviorCount = 0,
            notes = emptyList(),
        )

        val materialized = AiEditMaterializer.materialize(listOf(source), plan)

        assertFalse(materialized.isSafeToApply)
        assertTrue(materialized.mutations.isEmpty())
        assertEquals(1, materialized.rejectedEdits.size)
    }

    @Test
    fun materializerPreservesCrLfLineEndings() {
        val source = SourceFile(
            path = "char.cmd",
            content = "triggerall = AILevel\r\ntrigger1 = Random < 900\r\ntype = ChangeState\r\n",
        )
        val edit = PlannedEdit(
            category = BehaviorCategory.DEFENSE,
            filePath = "char.cmd",
            sourceLine = 2,
            originalExpression = "Random < 900",
            replacementExpression = "Random < 350",
            confidence = Confidence.HIGH,
            reason = "test",
        )
        val plan = EditPlan(SkillProfile(50), listOf(edit), 0, emptyList())

        val after = AiEditMaterializer.materialize(listOf(source), plan).mutations.single().afterContent
        assertEquals("triggerall = AILevel\r\ntrigger1 = Random < 350\r\ntype = ChangeState\r\n", after)
    }
}
