package com.narmanb.mugenaituner.core

/**
 * Detects whether AILevel is used in a way that actually distinguishes between engine AI levels
 * 1..8. Pure AI on/off checks such as `AILevel > 0`, `AILevel >= 1`, `AILevel != 0`, bare
 * `AILevel`, and `AILevel = [1,8]` are deliberately NOT considered numeric difficulty scaling.
 */
object AiLevelDifficultyScaling {
    private val arithmetic = Regex("""(?i)(?:\bailevel\s*[+\-*/%]|[+\-*/%]\s*ailevel\b)""")
    private val forwardComparison = Regex("""(?i)\bailevel\s*(<=|>=|!=|==|=|<|>)\s*(-?\d+(?:\.\d+)?)""")
    private val reverseComparison = Regex("""(?i)(-?\d+(?:\.\d+)?)\s*(<=|>=|!=|==|=|<|>)\s*ailevel\b""")
    private val rangeComparison = Regex(
        """(?i)\bailevel\s*(?:=|==)\s*\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*]""",
    )

    fun hasNumericScaling(code: String): Boolean {
        if (!code.contains("ailevel", ignoreCase = true)) return false
        if (arithmetic.containsMatchIn(code)) return true

        rangeComparison.findAll(code).forEach { match ->
            val low = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            val high = match.groupValues[2].toDoubleOrNull() ?: return@forEach
            if (variesAcrossAiLevels { level -> level.toDouble() in low..high }) return true
        }

        forwardComparison.findAll(code).forEach { match ->
            val operator = match.groupValues[1]
            val value = match.groupValues[2].toDoubleOrNull() ?: return@forEach
            if (variesAcrossAiLevels { level -> compare(level.toDouble(), operator, value) }) return true
        }

        reverseComparison.findAll(code).forEach { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            val operator = match.groupValues[2]
            if (variesAcrossAiLevels { level -> compare(value, operator, level.toDouble()) }) return true
        }

        return false
    }

    private fun variesAcrossAiLevels(predicate: (Int) -> Boolean): Boolean {
        val first = predicate(1)
        return (2..8).any { predicate(it) != first }
    }

    private fun compare(left: Double, operator: String, right: Double): Boolean = when (operator) {
        "<" -> left < right
        "<=" -> left <= right
        ">" -> left > right
        ">=" -> left >= right
        "=", "==" -> left == right
        "!=" -> left != right
        else -> false
    }
}
