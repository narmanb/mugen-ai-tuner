package com.narmanb.mugenaituner.core

enum class HealthSeverity {
    ERROR,
    WARNING,
}

data class HealthIssue(
    val severity: HealthSeverity,
    val code: String,
    val filePath: String,
    val lineNumber: Int?,
    val message: String,
)

data class CharacterHealthReport(
    val issues: List<HealthIssue>,
) {
    val errors: List<HealthIssue> get() = issues.filter { it.severity == HealthSeverity.ERROR }
    val warnings: List<HealthIssue> get() = issues.filter { it.severity == HealthSeverity.WARNING }
    val hasErrors: Boolean get() = errors.isNotEmpty()
}

object CharacterHealthValidator {
    fun validate(files: List<SourceFile>): CharacterHealthReport {
        val issues = mutableListOf<HealthIssue>()

        if (files.none { it.path.endsWith(".def", ignoreCase = true) }) {
            issues += HealthIssue(
                severity = HealthSeverity.WARNING,
                code = "NO_DEF",
                filePath = "",
                lineNumber = null,
                message = "No character DEF file was present in the analyzed source graph.",
            )
        }

        files.forEach { file ->
            validateFile(file, issues)
        }

        val graph = SourceGraphResolver.resolve(files)
        graph.unresolvedReferences.forEach { reference ->
            issues += HealthIssue(
                severity = HealthSeverity.WARNING,
                code = "UNRESOLVED_SOURCE_REFERENCE",
                filePath = reference.substringBefore(" -> "),
                lineNumber = null,
                message = "Referenced character text file could not be resolved: $reference",
            )
        }

        return CharacterHealthReport(issues.distinct())
    }

    /**
     * Returns only blocking structural errors that exist in [after] but not in [before].
     * Existing author quirks are not treated as something the tuner introduced.
     */
    fun introducedErrors(
        before: CharacterHealthReport,
        after: CharacterHealthReport,
    ): List<HealthIssue> {
        val beforeKeys = before.errors.map(::stableKey).toSet()
        return after.errors.filter { stableKey(it) !in beforeKeys }
    }

    private fun validateFile(file: SourceFile, issues: MutableList<HealthIssue>) {
        if ('\u0000' in file.content) {
            issues += HealthIssue(
                severity = HealthSeverity.ERROR,
                code = "NUL_BYTE_IN_TEXT",
                filePath = file.path,
                lineNumber = null,
                message = "Text source contains a NUL character and is unsafe to rewrite as ordinary MUGEN text.",
            )
        }

        file.content.lineSequence().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            val code = stripComment(raw)
            val trimmed = code.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            if (trimmed.startsWith("[") && !trimmed.endsWith("]")) {
                issues += HealthIssue(
                    severity = HealthSeverity.ERROR,
                    code = "MALFORMED_SECTION_HEADER",
                    filePath = file.path,
                    lineNumber = lineNumber,
                    message = "Section header starts with '[' but does not end with ']'.",
                )
            }

            if (hasUnclosedQuote(code)) {
                issues += HealthIssue(
                    severity = HealthSeverity.ERROR,
                    code = "UNCLOSED_QUOTE",
                    filePath = file.path,
                    lineNumber = lineNumber,
                    message = "Quoted text is not closed on this line.",
                )
            }

            val parenthesisProblem = parenthesisProblem(code)
            if (parenthesisProblem != null) {
                issues += HealthIssue(
                    severity = HealthSeverity.ERROR,
                    code = parenthesisProblem.first,
                    filePath = file.path,
                    lineNumber = lineNumber,
                    message = parenthesisProblem.second,
                )
            }

            val assignmentIndex = findAssignmentEquals(code)
            if (assignmentIndex >= 0 && code.substring(assignmentIndex + 1).trim().isEmpty()) {
                issues += HealthIssue(
                    severity = HealthSeverity.ERROR,
                    code = "EMPTY_ASSIGNMENT",
                    filePath = file.path,
                    lineNumber = lineNumber,
                    message = "Assignment has no expression after '='.",
                )
            }
        }
    }

    private fun stableKey(issue: HealthIssue): Triple<String, Int?, String> = Triple(
        issue.filePath.lowercase(),
        issue.lineNumber,
        issue.code,
    )

    private fun hasUnclosedQuote(text: String): Boolean {
        var quoted = false
        var escaped = false
        text.forEach { char ->
            if (escaped) {
                escaped = false
                return@forEach
            }
            if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                quoted = !quoted
            }
        }
        return quoted
    }

    private fun parenthesisProblem(text: String): Pair<String, String>? {
        var quoted = false
        var escaped = false
        var depth = 0
        text.forEach { char ->
            if (escaped) {
                escaped = false
                return@forEach
            }
            if (char == '\\' && quoted) {
                escaped = true
                return@forEach
            }
            if (char == '"') {
                quoted = !quoted
                return@forEach
            }
            if (quoted) return@forEach

            when (char) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth < 0) {
                        return "UNEXPECTED_CLOSE_PAREN" to "Expression contains a ')' without a matching earlier '('."
                    }
                }
            }
        }
        return if (depth != 0) {
            "UNBALANCED_PARENTHESES" to "Expression has unbalanced parentheses."
        } else {
            null
        }
    }

    private fun findAssignmentEquals(text: String): Int {
        var quoted = false
        var escaped = false
        text.forEachIndexed { index, char ->
            if (escaped) {
                escaped = false
                return@forEachIndexed
            }
            if (char == '\\' && quoted) {
                escaped = true
                return@forEachIndexed
            }
            if (char == '"') {
                quoted = !quoted
                return@forEachIndexed
            }
            if (!quoted && char == '=') return index
        }
        return -1
    }

    private fun stripComment(raw: String): String {
        var quoted = false
        var escaped = false
        raw.forEachIndexed { index, char ->
            if (escaped) {
                escaped = false
                return@forEachIndexed
            }
            if (char == '\\' && quoted) {
                escaped = true
                return@forEachIndexed
            }
            if (char == '"') {
                quoted = !quoted
            } else if (char == ';' && !quoted) {
                return raw.substring(0, index)
            }
        }
        return raw
    }
}
