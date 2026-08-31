package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaselineRetuningTest {
    @Test
    fun retuningUsesOriginalBaselineInsteadOfCurrentProbability() {
        val baseline = listOf(SourceFile("char.cmd", "trigger1 = Random < 900\n"))
        val current = listOf(SourceFile("char.cmd", "trigger1 = Random < 350\n"))
        val desired = MaterializedEditPlan(
            mutations = listOf(
                FileMutation(
                    relativePath = "char.cmd",
                    beforeContent = baseline.single().content,
                    afterContent = "trigger1 = Random < 200\n",
                    appliedEdits = emptyList(),
                ),
            ),
            rejectedEdits = emptyList(),
            errors = emptyList(),
        )

        val retuned = BaselineRetuner.retune(current, baseline, desired)

        assertTrue(retuned.isSafeToApply)
        assertEquals("trigger1 = Random < 350\n", retuned.mutations.single().beforeContent)
        assertEquals("trigger1 = Random < 200\n", retuned.mutations.single().afterContent)
    }

    @Test
    fun emptyDesiredPlanRestoresOriginalBaseline() {
        val baseline = listOf(SourceFile("char.cmd", "trigger1 = Random < 900\n"))
        val current = listOf(SourceFile("char.cmd", "trigger1 = Random < 350\n"))
        val desired = MaterializedEditPlan(emptyList(), emptyList(), emptyList())

        val retuned = BaselineRetuner.retune(current, baseline, desired)

        assertTrue(retuned.isSafeToApply)
        assertEquals("trigger1 = Random < 900\n", retuned.mutations.single().afterContent)
    }
}
