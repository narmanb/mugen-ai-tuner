package com.narmanb.mugenaituner.core

import kotlin.math.abs
import kotlin.math.roundToInt

enum class RandomProbabilityForm {
    COMPARISON,
    MODULO_COMPARISON,
    LOW_INCLUSIVE_RANGE,
    HIGH_INCLUSIVE_RANGE,
}

data class RandomProbabilityDecision(
    val expression: String,
    val activationChance: Double,
    val form: RandomProbabilityForm,
    val comparisonOperator: String? = null,
    val modulus: Int? = null,
    val originalThreshold: Int? = null,
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
            RandomProbabilityForm.MODULO_COMPARISON -> {
                val operator = requireNotNull(comparisonOperator)
                val mod = requireNotNull(modulus)
                val original = requireNotNull(originalThreshold)
                val threshold = RandomProbabilityParser.bestModuloThreshold(
                    modulus = mod,
                    operator = operator,
                    desiredChance = desired,
                    originalThreshold = original,
                )
                "Random % $mod $operator $threshold"
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
    private val moduloComparisonRegex = Regex(
        """(?i)\brandom\s*%\s*(\d{1,4})\s*(<=|>=|<|>)\s*(-?\d{1,4})\b""",
    )
    private val comparisonRegex = Regex(
        """(?i)\brandom\s*(<=|>=|<|>)\s*(1000|\d{1,3})\b""",
    )
    private val inclusiveRangeRegex = Regex(
        """(?i)\brandom\s*=\s*\[\s*(\d{1,3})\s*,\s*(\d{1,3})\s*]""",
    )

    fun findAll(code: String): List<RandomProbabilityDecision> = buildList {
        moduloComparisonRegex.findAll(code).forEach { match ->
            val modulus = match.groupValues[1].toIntOrNull() ?: return@forEach
            val operator = match.groupValues[2]
            val threshold = match.groupValues[3].toIntOrNull() ?: return@forEach
            if (modulus !in 2..1000) return@forEach
            add(
                RandomProbabilityDecision(
                    expression = match.value,
                    activationChance = moduloActivationChance(modulus, operator, threshold),
                    form = RandomProbabilityForm.MODULO_COMPARISON,
                    comparisonOperator = operator,
                    modulus = modulus,
                    originalThreshold = threshold,
                ),
            )
        }

        comparisonRegex.findAll(code).forEach { match ->
            val operator = match.groupValues[1]
            val threshold = match.groupValues[2].toIntOrNull() ?: return@forEach
            add(
                RandomProbabilityDecision(
                    expression = match.value,
                    activationChance = StandardizedAiCalibration.activationChance(operator, threshold),
                    form = RandomProbabilityForm.COMPARISON,
                    comparisonOperator = operator,
                    originalThreshold = threshold,
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
    }.distinctBy { it.expression }

    /** Exact chance across all 1000 possible MUGEN Random values. */
    fun moduloActivationChance(modulus: Int, operator: String, threshold: Int): Double {
        require(modulus in 2..1000)
        var matches = 0
        for (random in 0..999) {
            val value = random % modulus
            val active = when (operator) {
                "<" -> value < threshold
                "<=" -> value <= threshold
                ">" -> value > threshold
                ">=" -> value >= threshold
                else -> error("Unsupported modulo Random operator '$operator'.")
            }
            if (active) matches++
        }
        return matches / 1000.0
    }

    internal fun bestModuloThreshold(
        modulus: Int,
        operator: String,
        desiredChance: Double,
        originalThreshold: Int,
    ): Int {
        val desired = desiredChance.coerceIn(0.001, 0.999)
        return (0 until modulus).minWithOrNull(
            compareBy<Int> { threshold ->
                abs(moduloActivationChance(modulus, operator, threshold) - desired)
            }.thenBy { threshold -> abs(threshold - originalThreshold) },
        ) ?: originalThreshold.coerceIn(0, modulus - 1)
    }
}
