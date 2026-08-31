package com.narmanb.mugenaituner.core

data class FileMutation(
    val relativePath: String,
    val beforeContent: String,
    val afterContent: String,
    val appliedEdits: List<PlannedEdit>,
)

data class MaterializedEditPlan(
    val mutations: List<FileMutation>,
    val rejectedEdits: List<PlannedEdit>,
    val errors: List<String>,
) {
    val isSafeToApply: Boolean get() = mutations.isNotEmpty() && errors.isEmpty()
}

/**
 * Converts a preview plan into exact file contents without touching disk.
 *
 * The first write-capable implementation deliberately requires each original expression to
 * identify one unambiguous location in its file. If an expression occurs more than once, or if
 * the file has changed since analysis, the edit is rejected instead of guessing which occurrence
 * the user meant to tune.
 */
object AiEditMaterializer {
    fun materialize(sourceFiles: List<SourceFile>, plan: EditPlan): MaterializedEditPlan {
        val filesByPath = sourceFiles.associateBy { it.path }
        val mutations = mutableListOf<FileMutation>()
        val rejected = mutableListOf<PlannedEdit>()
        val errors = mutableListOf<String>()

        plan.edits.groupBy { it.filePath }.forEach { (path, edits) ->
            val source = filesByPath[path]
            if (source == null) {
                rejected += edits
                errors += "The analyzed file '$path' is no longer available."
                return@forEach
            }

            var content = source.content
            val applied = mutableListOf<PlannedEdit>()

            // Identical source expressions with different desired replacements are inherently
            // ambiguous in this conservative first implementation.
            val conflicting = edits.groupBy { it.originalExpression }
                .filterValues { group -> group.map { it.replacementExpression }.distinct().size > 1 }
                .keys

            edits.forEach { edit ->
                if (edit.originalExpression in conflicting) {
                    rejected += edit
                    return@forEach
                }

                val occurrenceCount = countOccurrences(content, edit.originalExpression)
                if (occurrenceCount != 1) {
                    rejected += edit
                    return@forEach
                }

                content = content.replaceFirst(edit.originalExpression, edit.replacementExpression)
                applied += edit
            }

            if (applied.isNotEmpty() && content != source.content) {
                mutations += FileMutation(
                    relativePath = path,
                    beforeContent = source.content,
                    afterContent = content,
                    appliedEdits = applied,
                )
            }
        }

        if (rejected.isNotEmpty()) {
            errors += "${rejected.size} proposed edit(s) were not uniquely identifiable and were withheld from automatic writing."
        }

        return MaterializedEditPlan(
            mutations = mutations,
            rejectedEdits = rejected.distinct(),
            errors = errors.distinct(),
        )
    }

    private fun countOccurrences(text: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(needle, index)
            if (index < 0) return count
            count++
            index += needle.length
        }
    }
}
