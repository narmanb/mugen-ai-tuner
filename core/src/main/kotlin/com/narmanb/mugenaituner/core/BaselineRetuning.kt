package com.narmanb.mugenaituner.core

/**
 * Converts a desired state calculated from the original character baseline into mutations from the
 * character's current state. This prevents difficulty changes from compounding previous tuning.
 */
object BaselineRetuner {
    fun retune(
        currentFiles: List<SourceFile>,
        baselineFiles: List<SourceFile>,
        desiredFromBaseline: MaterializedEditPlan,
    ): MaterializedEditPlan {
        if (desiredFromBaseline.errors.isNotEmpty()) {
            return MaterializedEditPlan(
                mutations = emptyList(),
                rejectedEdits = desiredFromBaseline.rejectedEdits,
                errors = desiredFromBaseline.errors,
            )
        }

        val currentByPath = currentFiles.associateBy { it.path }
        val baselineByPath = baselineFiles.associateBy { it.path }
        if (currentByPath.keys != baselineByPath.keys) {
            return MaterializedEditPlan(
                mutations = emptyList(),
                rejectedEdits = desiredFromBaseline.rejectedEdits,
                errors = listOf("The current character source graph no longer matches the verified tuning baseline."),
            )
        }

        val desiredMutationByPath = desiredFromBaseline.mutations.associateBy { it.relativePath }
        val mutations = baselineFiles.mapNotNull { baseline ->
            val current = currentByPath.getValue(baseline.path)
            val desiredMutation = desiredMutationByPath[baseline.path]
            val desiredContent = desiredMutation?.afterContent ?: baseline.content
            if (current.content == desiredContent) return@mapNotNull null

            FileMutation(
                relativePath = baseline.path,
                beforeContent = current.content,
                afterContent = desiredContent,
                appliedEdits = desiredMutation?.appliedEdits.orEmpty(),
            )
        }

        return MaterializedEditPlan(
            mutations = mutations,
            rejectedEdits = desiredFromBaseline.rejectedEdits,
            errors = emptyList(),
        )
    }
}
