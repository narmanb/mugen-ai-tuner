package com.narmanb.mugenaituner.core

import kotlin.math.roundToInt

enum class RandomProbabilityForm {
    COMPARISON,
    LOW_INCLUSIVE_RANGE,
    HIGH_INCLUSIVE_RANGE,
}

data class RandomProbabilityDecision(
    val expression: String,
    val activationChance: Double,
    val form: RandomProbabilityForm,
    val comparisonOperator: String? = null,
) {
    /**
     * Rewrites only the probability-bearing part while preserving the same decision shape.
     * Middle ranges are intentionally not represented by this model because changing them can
     * alter semantics beyond simple activation frequency.
     */
    fun replacementForChance(chance: Double): String {
        val desired = chance.coerceIn(0.001, 0.999)
        return when (form) {
            RandomProbabilityForm.COMPARISON -> {
                val operator = requireNotNull(comparisonOperator)
                val threshold = StandardizedAiCalibration.thresholdForChance(operator, desired)
                "Random $operator $threshold"
            }
            RandomProbabilityForm.LOW_INCLUSIVE_RANGE -> {
                val count = (desired * 1000.0).roundToInt().coerceIn(1, 999)
                "Random = [0,${count - 1}]"
            }
            RandomProbabilityForm.HIGH_INCLUSIVE_RANGE -> {
                val count = (desired * 1000.0).roundToInt().coerceIn(1, 999)
                "Random = [${1000 - count},999]"
            }
        }
    }
}

/** Recognizes only Random forms that map cleanly to a single activation probability. */
object RandomProbabilityParser {
    private val comparisonRegex = Regex(
        """(?i)\brandom\s*(<=|>=|<|>)\s*(1000|\d{1,3})\b""",
    )
    private val inclusiveRangeRegex = Regex(
        """(?i)\brandom\s*=\s*\[\s*(\d{1,3})\s*,\s*(\d{1,3})\s*]""",
    )

    fun findAll(code: String): List<RandomProbabilityDecision> = buildList {
        comparisonRegex.findAll(code).forEach { match ->
            val operator = match.groupValues[1]
            val threshold = match.groupValues[2].toIntOrNull() ?: return@forEach
            add(
                RandomProbabilityDecision(
                    expression = match.value,
                    activationChance = StandardizedAiCalibration.activationChance(operator, threshold),
                    form = RandomProbabilityForm.COMPARISON,
                    comparisonOperator = operator,
                ),
            )
        }

        inclusiveRangeRegex.findAll(code).forEach { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@forEach
            val end = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (start !in 0..999 || end !in 0..999 || start > end) return@forEach

            when {
                start == 0 && end < 999 -> add(
                    RandomProbabilityDecision(
                        expression = match.value,
                        activationChance = (end + 1) / 1000.0,
                        form = RandomProbabilityForm.LOW_INCLUSIVE_RANGE,
                    ),
                )
                end == 999 && start > 0 -> add(
                    RandomProbabilityDecision(
                        expression = match.value,
                        activationChance = (1000 - start) / 1000.0,
                        form = RandomProbabilityForm.HIGH_INCLUSIVE_RANGE,
                    ),
                )
                // [0,999] is always true and therefore not a useful tunable probability gate.
            }
        }
    }
}
