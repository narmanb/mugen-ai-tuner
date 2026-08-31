package com.narmanb.mugenaituner.core

/**
 * Recognizes the old WinMUGEN/Winane family of AI activation schemes that compare pairs of command
 * states with the logical XOR operator (`^^`). These schemes exploit differences between human
 * input and engine-generated command activation.
 *
 * Detection is intentionally conservative: one XOR expression is not enough. We require at least
 * two XOR command-pair expressions and at least four distinct command names before returning a
 * likely legacy AI command set.
 */
object LegacyXorAiDetector {
    private val commandComparison = Regex(
        """(?i)\bcommand\s*(?:=|==|!=)\s*\"([^\"]+)\"""",
    )

    fun detectCommandNames(code: String): Set<String> {
        val xorLines = code.lineSequence()
            .map { it.substringBefore(';') }
            .filter { "^^" in it }
            .toList()

        if (xorLines.isEmpty()) return emptySet()

        var pairExpressions = 0
        val names = linkedSetOf<String>()
        xorLines.forEach { line ->
            val lineNames = commandComparison.findAll(line)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
            if (lineNames.size >= 2) {
                pairExpressions++
                names += lineNames
            }
        }

        return if (pairExpressions >= 2 && names.size >= 4) names else emptySet()
    }
}
