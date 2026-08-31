package com.narmanb.mugenaituner.core

/**
 * Distinguishes expressions that actually imply an AI-controlled player from expressions that
 * merely mention AILevel. This prevents human-only checks such as `AILevel = 0` from seeding an AI
 * flag or turning a normal controller into high-confidence custom AI.
 */
object AiLevelSignalClassifier {
    private val assignmentLine = Regex("""^\s*[^=]+?\s*=\s*(.*?)\s*$""")
    private val positiveComparison = Regex(
        """(?i)(?:\bailevel\s*>\s*\d+|\bailevel\s*>=\s*[1-8]\b|\bailevel\s*(?:=|==)\s*[1-8]\b|\bailevel\s*!=\s*0\b|\bailevel\s*=\s*\[\s*[1-8]\s*,\s*[1-8]\s*]|(?:^|[^\d])\d+\s*<\s*ailevel\b|(?:^|[^\d])[1-8]\s*<=\s*ailevel\b)""",
    )
    private val positiveBooleanUse = Regex(
        """(?i)(?:^|[(&|])\s*(?:!!\s*)?ailevel\b(?!\s*(?:=|==|!=|<=|>=|<|>|[+\-*/%]))""",
    )
    private val bareExpression = Regex("""(?i)^\s*(?:!!\s*)?ailevel\s*$""")
    private val humanOnly = listOf(
        Regex("""(?i)^\s*!\s*ailevel\s*$"""),
        Regex("""(?i)\bailevel\s*(?:=|==|<=)\s*0\b"""),
        Regex("""(?i)\bailevel\s*<\s*1\b"""),
        Regex("""(?i)\b0\s*>=\s*ailevel\b"""),
        Regex("""(?i)\b1\s*>\s*ailevel\b"""),
    )
    private val aiArithmetic = Regex("""(?i)(?:\bailevel\s*[+\-*/%]|[+\-*/%]\s*ailevel\b)""")

    fun classifyCodeBlock(code: String): Confidence? {
        var best: Confidence? = null
        code.lineSequence().forEach { rawLine ->
            val expression = assignmentLine.find(rawLine)?.groupValues?.getOrNull(1) ?: rawLine
            val confidence = classifyExpression(expression) ?: return@forEach
            if (confidence == Confidence.HIGH) return Confidence.HIGH
            if (best == null || confidence.ordinal < best!!.ordinal) best = confidence
        }
        return best
    }

    fun classifyExpression(expression: String): Confidence? {
        if (!expression.contains("ailevel", ignoreCase = true)) return null
        val trimmed = expression.trim()

        // A purely human-only expression is not evidence of AI activation.
        if (humanOnly.any { it.containsMatchIn(trimmed) } &&
            !positiveComparison.containsMatchIn(trimmed) &&
            !positiveBooleanUse.containsMatchIn(trimmed)
        ) return null

        if (bareExpression.matches(trimmed)) return Confidence.HIGH
        if (positiveComparison.containsMatchIn(trimmed)) return Confidence.HIGH
        if (positiveBooleanUse.containsMatchIn(trimmed)) return Confidence.HIGH

        // Arithmetic or otherwise mixed AILevel dependencies are useful evidence, but not strong
        // enough to treat a downstream variable as a proven AI on/off flag for automatic editing.
        if (aiArithmetic.containsMatchIn(trimmed)) return Confidence.MEDIUM
        return Confidence.MEDIUM
    }
}
