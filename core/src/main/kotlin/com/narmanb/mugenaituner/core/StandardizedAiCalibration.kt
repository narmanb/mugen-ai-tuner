package com.narmanb.mugenaituner.core

import kotlin.math.roundToInt

data class BehaviorProbabilityCurve(
    val beginnerChance: Double,
    val normalChance: Double,
    val expertChance: Double,
)

/**
 * Maps the app's standardized 0–100 skill scale onto decision probabilities.
 *
 * These probabilities apply only after the rest of a behavior's MUGEN triggers are true. They are
 * therefore calibration targets for AI decision pressure, not literal percentages of attacks that
 * will be blocked/hit/answered during a match.
 *
 * A small amount of the author's original threshold is retained so two characters at the same app
 * skill do not become mechanically identical, while the large majority of the result comes from a
 * shared target. This is what makes 50% mean approximately the same "Normal" level across wildly
 * different authored AIs instead of simply meaning half of whatever value the author used.
 */
object StandardizedAiCalibration {
    internal const val authoredStyleWeight: Double = 0.05

    private val curves: Map<BehaviorCategory, BehaviorProbabilityCurve> = mapOf(
        BehaviorCategory.DEFENSE to BehaviorProbabilityCurve(0.04, 0.45, 0.94),
        BehaviorCategory.REACTION to BehaviorProbabilityCurve(0.03, 0.42, 0.95),
        BehaviorCategory.AGGRESSION to BehaviorProbabilityCurve(0.18, 0.60, 0.96),
        BehaviorCategory.COMBO to BehaviorProbabilityCurve(0.05, 0.50, 0.94),
        BehaviorCategory.ANTI_AIR to BehaviorProbabilityCurve(0.03, 0.45, 0.96),
        BehaviorCategory.PROJECTILE_RESPONSE to BehaviorProbabilityCurve(0.03, 0.45, 0.96),
        BehaviorCategory.THROW to BehaviorProbabilityCurve(0.08, 0.32, 0.75),
        BehaviorCategory.SUPER to BehaviorProbabilityCurve(0.05, 0.30, 0.78),
        BehaviorCategory.MOVEMENT to BehaviorProbabilityCurve(0.20, 0.55, 0.92),
    )

    fun curveFor(category: BehaviorCategory): BehaviorProbabilityCurve? = curves[category]

    fun canonicalChance(category: BehaviorCategory, skill: Int): Double? {
        val curve = curves[category] ?: return null
        val normalizedSkill = skill.coerceIn(0, 100)
        return if (normalizedSkill <= 50) {
            lerp(curve.beginnerChance, curve.normalChance, normalizedSkill / 50.0)
        } else {
            lerp(curve.normalChance, curve.expertChance, (normalizedSkill - 50) / 50.0)
        }.coerceIn(0.001, 0.999)
    }

    fun standardizedChance(
        category: BehaviorCategory,
        skill: Int,
        originalChance: Double,
    ): Double? {
        val canonical = canonicalChance(category, skill) ?: return null
        val author = originalChance.coerceIn(0.0, 1.0)
        return (canonical * (1.0 - authoredStyleWeight) + author * authoredStyleWeight)
            .coerceIn(0.001, 0.999)
    }

    /** MUGEN Random returns an integer in 0..999. */
    fun activationChance(operator: String, threshold: Int): Double {
        val n = threshold.coerceIn(0, 1000)
        return when (operator) {
            "<" -> n / 1000.0
            "<=" -> (n + 1).coerceAtMost(1000) / 1000.0
            ">" -> (999 - n).coerceAtLeast(0) / 1000.0
            ">=" -> (1000 - n).coerceAtLeast(0) / 1000.0
            else -> error("Unsupported Random comparison operator '$operator'.")
        }
    }

    fun thresholdForChance(operator: String, chance: Double): Int {
        val count = (chance.coerceIn(0.001, 0.999) * 1000.0)
            .roundToInt()
            .coerceIn(1, 999)
        return when (operator) {
            "<" -> count
            "<=" -> (count - 1).coerceIn(0, 999)
            ">" -> (999 - count).coerceIn(0, 999)
            ">=" -> (1000 - count).coerceIn(0, 999)
            else -> error("Unsupported Random comparison operator '$operator'.")
        }
    }

    fun standardizedThreshold(
        category: BehaviorCategory,
        skill: Int,
        operator: String,
        originalThreshold: Int,
    ): Int? {
        val originalChance = activationChance(operator, originalThreshold)
        val desiredChance = standardizedChance(category, skill, originalChance) ?: return null
        return thresholdForChance(operator, desiredChance)
    }

    /** AILevel 1 begins at one quarter of the selected standardized skill; AILevel 4 is selected. */
    fun lowEngineSkill(selectedSkill: Int): Int =
        (selectedSkill.coerceIn(0, 100) / 4.0).roundToInt().coerceIn(0, 100)

    private fun lerp(start: Double, end: Double, amount: Double): Double =
        start + (end - start) * amount.coerceIn(0.0, 1.0)
}
