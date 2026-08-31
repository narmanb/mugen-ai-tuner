package com.narmanb.mugenaituner.core

/**
 * Traces working variables whose stored value depends on the numeric engine AILevel rather than
 * merely representing the AI-on/off condition. This is used to avoid flattening characters that
 * already route IKEMEN/MUGEN difficulty through an intermediate var/fvar.
 */
object AiLevelVariableDependencyTracer {
    data class Dependency(
        val kind: VariableKind,
        val variable: Int,
        val expandedExpression: String,
        val filePath: String,
        val lineNumber: Int,
    ) {
        val expressionName: String
            get() = if (kind == VariableKind.FVAR) "fvar($variable)" else "var($variable)"
    }

    private data class Key(val kind: VariableKind, val index: Int)
    private data class Candidate(
        val key: Key,
        val expression: String,
        val filePath: String,
        val lineNumber: Int,
    )

    private val sectionRegex = Regex("""^\s*\[([^]]+)]""")
    private val assignmentRegex = Regex("""^\s*([^=]+?)\s*=\s*(.*?)\s*$""")
    private val directAssignmentRegex = Regex("""(?i)\b(f?var)\s*\(\s*(\d+)\s*\)\s*:=\s*(.+)$""")
    private val variableRegex = Regex("""(?i)\b(f?var)\s*\(\s*(\d+)\s*\)""")
    private val assignedVariableKeyRegex = Regex("""(?i)^(f?var)\s*\(\s*(\d+)\s*\)$""")
    private val bareAiLevelRegex = Regex("""(?i)^\s*\(*\s*ailevel\s*\)*\s*$""")
    private val simpleAiLevelConditionRegex = Regex(
        """(?is)^\s*(?:!\s*ailevel|!!\s*ailevel|ailevel\s*(?:<=|>=|!=|==|=|<|>)\s*-?\d+(?:\.\d+)?|ailevel\s*(?:=|==)\s*\[[^]]+]|-?\d+(?:\.\d+)?\s*(?:<=|>=|!=|==|=|<|>)\s*ailevel)\s*$""",
    )

    fun trace(files: List<SourceFile>): List<Dependency> {
        val candidates = files.flatMap(::candidates)
        val known = linkedMapOf<Key, Dependency>()

        var changed: Boolean
        do {
            changed = false
            for (candidate in candidates) {
                val expanded = expand(candidate.expression, known)
                if (!carriesNumericDifficulty(expanded)) continue

                val replacement = Dependency(
                    kind = candidate.key.kind,
                    variable = candidate.key.index,
                    expandedExpression = expanded,
                    filePath = candidate.filePath,
                    lineNumber = candidate.lineNumber,
                )
                val previous = known[candidate.key]
                if (previous == null || previous.expandedExpression != replacement.expandedExpression) {
                    known[candidate.key] = replacement
                    changed = true
                }
            }
        } while (changed)

        return known.values.sortedWith(compareBy<Dependency> { it.kind.ordinal }.thenBy { it.variable })
    }

    fun hasDifficultyScaling(code: String, dependencies: List<Dependency>): Boolean {
        if (AiLevelDifficultyScaling.hasNumericScaling(code)) return true
        if (dependencies.isEmpty()) return false

        val known = dependencies.associateBy { Key(it.kind, it.variable) }
        val expanded = expand(code, known)
        return AiLevelDifficultyScaling.hasNumericScaling(expanded)
    }

    private fun candidates(file: SourceFile): List<Candidate> {
        val result = mutableListOf<Candidate>()
        val lines = file.content.lines()

        lines.forEachIndexed { index, raw ->
            val code = stripComment(raw).trim()
            val direct = directAssignmentRegex.find(code) ?: return@forEachIndexed
            if (MugenVariableReferenceScanner.isRedirected(code, direct.range.first)) return@forEachIndexed
            val variable = direct.groupValues[2].toIntOrNull() ?: return@forEachIndexed
            result += Candidate(
                key = Key(variableKind(direct.groupValues[1]), variable),
                expression = direct.groupValues[3].trim(),
                filePath = file.path,
                lineNumber = index + 1,
            )
        }

        var blockStart = 0
        var blockLines = mutableListOf<Pair<Int, String>>()

        fun flushBlock() {
            if (blockLines.isEmpty()) return
            controllerCandidate(file.path, blockLines)?.let(result::add)
            blockLines = mutableListOf()
        }

        lines.forEachIndexed { index, raw ->
            val code = stripComment(raw).trim()
            if (sectionRegex.containsMatchIn(code)) {
                flushBlock()
                blockStart = index + 1
            } else if (code.isNotEmpty()) {
                blockLines += (index + 1) to code
            }
        }
        flushBlock()

        return result.distinctBy { listOf(it.key.kind, it.key.index, it.filePath, it.lineNumber, it.expression) }
    }

    private fun controllerCandidate(
        filePath: String,
        lines: List<Pair<Int, String>>,
    ): Candidate? {
        val assignments = linkedMapOf<String, Pair<Int, String>>()
        lines.forEach { (lineNumber, code) ->
            val match = assignmentRegex.find(code) ?: return@forEach
            assignments[match.groupValues[1].trim().lowercase()] = lineNumber to match.groupValues[2].trim()
        }

        val controllerType = assignments["type"]?.second.orEmpty()
        if (!controllerType.equals("VarSet", ignoreCase = true) &&
            !controllerType.equals("VarAdd", ignoreCase = true)
        ) return null

        val integerTarget = assignments["v"]?.second?.toIntOrNull()?.let { VariableKind.VAR to it }
        val floatTarget = assignments["fv"]?.second?.toIntOrNull()?.let { VariableKind.FVAR to it }
        val explicitTarget = integerTarget ?: floatTarget
        if (explicitTarget != null) {
            val value = assignments["value"] ?: return null
            return Candidate(
                key = Key(explicitTarget.first, explicitTarget.second),
                expression = value.second,
                filePath = filePath,
                lineNumber = value.first,
            )
        }

        assignments.forEach { (keyText, lineAndExpression) ->
            val match = assignedVariableKeyRegex.matchEntire(keyText) ?: return@forEach
            val variable = match.groupValues[2].toIntOrNull() ?: return@forEach
            return Candidate(
                key = Key(variableKind(match.groupValues[1]), variable),
                expression = lineAndExpression.second,
                filePath = filePath,
                lineNumber = lineAndExpression.first,
            )
        }
        return null
    }

    private fun expand(expression: String, known: Map<Key, Dependency>): String {
        if (known.isEmpty()) return expression
        val matches = variableRegex.findAll(expression).toList()
        if (matches.isEmpty()) return expression

        val builder = StringBuilder(expression)
        matches.asReversed().forEach { match ->
            if (MugenVariableReferenceScanner.isRedirected(expression, match.range.first)) return@forEach
            val variable = match.groupValues[2].toIntOrNull() ?: return@forEach
            val dependency = known[Key(variableKind(match.groupValues[1]), variable)] ?: return@forEach
            builder.replace(
                match.range.first,
                match.range.last + 1,
                "(${dependency.expandedExpression})",
            )
        }
        return builder.toString()
    }

    private fun carriesNumericDifficulty(expression: String): Boolean {
        if (!expression.contains("ailevel", ignoreCase = true)) return false
        if (bareAiLevelRegex.matches(expression)) return true
        if (simpleAiLevelConditionRegex.matches(expression)) {
            return AiLevelDifficultyScaling.hasNumericScaling(expression)
        }
        return AiLevelDifficultyScaling.hasNumericScaling(expression)
    }

    private fun variableKind(token: String): VariableKind =
        if (token.equals("fvar", ignoreCase = true)) VariableKind.FVAR else VariableKind.VAR

    private fun stripComment(raw: String): String {
        var quoted = false
        raw.forEachIndexed { index, char ->
            when (char) {
                '"' -> quoted = !quoted
                ';' -> if (!quoted) return raw.substring(0, index)
            }
        }
        return raw
    }
}
