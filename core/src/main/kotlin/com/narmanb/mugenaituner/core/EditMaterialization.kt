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

private data class PhysicalLine(
    val text: String,
    val ending: String,
)

/**
 * Converts a preview plan into exact file contents without touching disk.
 *
 * Repeated expressions are resolved using the analyzer's source-line hint. A repeated threshold is
 * writable only when one occurrence is uniquely closest to the behavior block that produced the
 * edit. Ties and other ambiguous cases are rejected instead of guessed.
 */
object AiEditMaterializer {
    private const val maxRepeatedExpressionLineDistance = 40

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

            val lines = splitPhysicalLines(source.content)
            val resolved = linkedMapOf<Int, MutableList<PlannedEdit>>()

            edits.forEach { edit ->
                val targetLine = resolveTargetLine(lines, edit)
                if (targetLine == null) {
                    rejected += edit
                } else {
                    resolved.getOrPut(targetLine) { mutableListOf() } += edit
                }
            }

            val applied = mutableListOf<PlannedEdit>()
            resolved.forEach { (lineIndex, lineEdits) ->
                val originalLine = lines[lineIndex]
                var rewritten = originalLine.text
                val acceptedForLine = mutableListOf<PlannedEdit>()

                lineEdits.forEach { edit ->
                    val count = countOccurrences(rewritten, edit.originalExpression)
                    if (count != 1) {
                        rejected += edit
                    } else {
                        rewritten = rewritten.replaceFirst(edit.originalExpression, edit.replacementExpression)
                        acceptedForLine += edit
                    }
                }

                if (acceptedForLine.isNotEmpty() && rewritten != originalLine.text) {
                    lines[lineIndex] = originalLine.copy(text = rewritten)
                    applied += acceptedForLine
                }
            }

            val afterContent = lines.joinToString(separator = "") { it.text + it.ending }
            if (applied.isNotEmpty() && afterContent != source.content) {
                mutations += FileMutation(
                    relativePath = path,
                    beforeContent = source.content,
                    afterContent = afterContent,
                    appliedEdits = applied,
                )
            }
        }

        if (rejected.isNotEmpty()) {
            errors += "${rejected.distinct().size} proposed edit(s) could not be mapped to a unique source location and were withheld from automatic writing."
        }

        return MaterializedEditPlan(
            mutations = mutations,
            rejectedEdits = rejected.distinct(),
            errors = errors.distinct(),
        )
    }

    private fun resolveTargetLine(lines: List<PhysicalLine>, edit: PlannedEdit): Int? {
        val candidates = lines.indices.filter { index ->
            edit.originalExpression in lines[index].text
        }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()

        val sourceIndex = (edit.sourceLine - 1).coerceAtLeast(0)
        val distances = candidates.map { candidate -> candidate to kotlin.math.abs(candidate - sourceIndex) }
        val minimum = distances.minOf { it.second }
        if (minimum > maxRepeatedExpressionLineDistance) return null
        val nearest = distances.filter { it.second == minimum }
        return nearest.singleOrNull()?.first
    }

    private fun splitPhysicalLines(text: String): MutableList<PhysicalLine> {
        if (text.isEmpty()) return mutableListOf(PhysicalLine("", ""))
        val result = mutableListOf<PhysicalLine>()
        var start = 0
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    val ending = if (index + 1 < text.length && text[index + 1] == '\n') "\r\n" else "\r"
                    result += PhysicalLine(text.substring(start, index), ending)
                    index += ending.length
                    start = index
                }
                '\n' -> {
                    result += PhysicalLine(text.substring(start, index), "\n")
                    index++
                    start = index
                }
                else -> index++
            }
        }
        if (start < text.length) {
            result += PhysicalLine(text.substring(start), "")
        }
        return result
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
