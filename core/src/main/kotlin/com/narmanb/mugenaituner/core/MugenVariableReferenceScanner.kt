package com.narmanb.mugenaituner.core

/**
 * Finds working-variable references that belong to the player currently evaluating an expression.
 *
 * MUGEN redirection changes the owner of the trigger after a comma, for example
 * `EnemyNear, var(10)` or `Root, fvar(2)`. Those references must not be confused with the current
 * player's var/fvar bank. References used inside a redirection selector itself remain local, e.g.
 * the `var(7)` in `PlayerID(var(7)), var(10)`.
 */
object MugenVariableReferenceScanner {
    data class Reference(
        val kind: VariableKind,
        val index: Int,
        val startIndex: Int,
    )

    private val variableRegex = Regex("""(?i)\b(f?var)\s*\(\s*(\d+)\s*\)""")
    private val redirectionNames = setOf(
        "root",
        "parent",
        "helper",
        "target",
        "partner",
        "enemy",
        "enemynear",
        "playerid",
    )

    fun localReferences(code: String): List<Reference> =
        variableRegex.findAll(code)
            .mapNotNull { match ->
                if (isRedirected(code, match.range.first)) return@mapNotNull null
                val index = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                Reference(
                    kind = if (match.groupValues[1].equals("fvar", ignoreCase = true)) {
                        VariableKind.FVAR
                    } else {
                        VariableKind.VAR
                    },
                    index = index,
                    startIndex = match.range.first,
                )
            }
            .toList()

    /** Returns true when the variable beginning at [startIndex] is evaluated through a redirection. */
    fun isRedirected(code: String, startIndex: Int): Boolean {
        if (startIndex <= 0 || startIndex > code.length) return false

        var cursor = startIndex - 1
        while (cursor >= 0 && code[cursor].isWhitespace()) cursor--
        if (cursor < 0 || code[cursor] != ',') return false

        cursor--
        while (cursor >= 0 && code[cursor].isWhitespace()) cursor--
        if (cursor < 0) return false

        val name = if (code[cursor] == ')') {
            val open = matchingOpenParen(code, cursor) ?: return false
            identifierBefore(code, open - 1)
        } else {
            identifierBefore(code, cursor)
        }

        return name.lowercase() in redirectionNames
    }

    private fun matchingOpenParen(code: String, closeIndex: Int): Int? {
        var depth = 0
        for (index in closeIndex downTo 0) {
            when (code[index]) {
                ')' -> depth++
                '(' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun identifierBefore(code: String, endIndex: Int): String {
        var end = endIndex
        while (end >= 0 && code[end].isWhitespace()) end--
        if (end < 0) return ""

        var start = end
        while (start >= 0 && (code[start].isLetterOrDigit() || code[start] == '_')) start--
        return code.substring(start + 1, end + 1)
    }
}
