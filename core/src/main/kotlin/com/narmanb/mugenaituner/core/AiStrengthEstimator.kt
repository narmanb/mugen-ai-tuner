package com.narmanb.mugenaituner.core

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AiStrengthEstimate(
    val score: Int?,
    val label: String,
    val confidence: Confidence,
    val evidenceCount: Int,
    val categoryScores: Map<BehaviorCategory, Int>,
    val notes: List<String>,
)

/**
 * Estimates how demanding the character's original AI appears from static decision logic.
 *
 * This is deliberately labelled a heuristic: Random thresholds are evaluated only after all of a
 * controller's other triggers become true, so source code alone cannot know how often a situation
 * occurs in a real match. The estimate is useful for comparing obviously passive vs. extremely
 * reactive AI, not as a measured win-rate or frame-perfect skill rating.
 */
object AiStrengthEstimator {
    fun estimate(analysis: CharacterAnalysis): AiStrengthEstimate {
        if (!analysis.aiDetected) {
            return AiStrengthEstimate(
                score = null,
                label = "No custom AI detected",
                confidence = Confidence.UNKNOWN,
                evidenceCount = 0,
                categoryScores = emptyMap(),
                notes = listOf("No custom AI signal was detected, so a static strength estimate is not meaningful."),
            )
        }

        val evidence = mutableListOf<Evidence>()
        for (behavior in analysis.behaviors) {
            if (behavior.confidence == Confidence.LOW || behavior.confidence == Confidence.UNKNOWN) continue
            for (decision in RandomProbabilityParser.findAll(behavior.rawCode)) {
                evidence += Evidence(
                    category = behavior.category,
                    chance = decision.activationChance,
                    confidence = behavior.confidence,
                )
            }
        }

        if (evidence.isEmpty()) {
            return AiStrengthEstimate(
                score = null,
                label = "Insufficient static evidence",
                confidence = Confidence.LOW,
                evidenceCount = 0,
                categoryScores = emptyMap(),
                notes = listOf(
                    "Custom AI exists, but the analyzer did not find enough simple probability decisions to estimate its original strength safely.",
                ),
            )
        }

        val categoryScores = evidence.groupBy { it.category }
            .filterKeys { it != BehaviorCategory.UNKNOWN }
            .mapValues { (_, values) ->
                weightedAverage(values.map { pressureScore(it.chance) to confidenceWeight(it.confidence) })
                    .roundToInt()
                    .coerceIn(0, 100)
            }

        val weightedOverall = weightedAverage(
            evidence.map { item ->
                val behaviorWeight = categoryWeight(item.category)
                pressureScore(item.chance) to behaviorWeight * confidenceWeight(item.confidence)
            },
        )

        // Broad coverage is harder to exploit than an AI that is excellent in only one situation.
        val meaningfulCategories = categoryScores.keys.count { it != BehaviorCategory.UNKNOWN }
        val coverageBonus = ((meaningfulCategories - 2).coerceAtLeast(0) * 1.5).coerceAtMost(9.0)
        val score = (weightedOverall + coverageBonus).roundToInt().coerceIn(0, 100)
        val confidence = when {
            evidence.size >= 8 && meaningfulCategories >= 4 -> Confidence.HIGH
            evidence.size >= 3 && meaningfulCategories >= 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        return AiStrengthEstimate(
            score = score,
            label = labelFor(score),
            confidence = confidence,
            evidenceCount = evidence.size,
            categoryScores = categoryScores.toSortedMap(compareBy { it.ordinal }),
            notes = buildList {
                add("This is a static-code estimate, not a measured match win rate.")
                if (analysis.difficultyResponsiveness == DifficultyResponsiveness.NONE) {
                    add("The detected AI does not appear to scale numerically with AILevel, so low engine difficulty may still feel close to its full authored behavior.")
                }
                if (confidence == Confidence.LOW) {
                    add("Only limited simple probability evidence was available; treat the score as approximate.")
                }
            },
        )
    }

    fun labelFor(score: Int): String = when (score.coerceIn(0, 100)) {
        in 0..20 -> "Beginner"
        in 21..40 -> "Easy"
        in 41..60 -> "Normal"
        in 61..80 -> "Hard"
        else -> "Brutal"
    }

    /** Square-root compression reflects that even a modest per-check chance can fire frequently. */
    private fun pressureScore(chance: Double): Double = sqrt(chance.coerceIn(0.0, 1.0)) * 100.0

    private fun confidenceWeight(confidence: Confidence): Double = when (confidence) {
        Confidence.HIGH -> 1.0
        Confidence.MEDIUM -> 0.65
        Confidence.LOW -> 0.25
        Confidence.UNKNOWN -> 0.0
    }

    private fun categoryWeight(category: BehaviorCategory): Double = when (category) {
        BehaviorCategory.DEFENSE -> 1.25
        BehaviorCategory.REACTION -> 1.20
        BehaviorCategory.COMBO -> 1.15
        BehaviorCategory.ANTI_AIR -> 1.15
        BehaviorCategory.PROJECTILE_RESPONSE -> 1.15
        BehaviorCategory.AGGRESSION -> 0.95
        BehaviorCategory.THROW -> 0.90
        BehaviorCategory.SUPER -> 0.85
        BehaviorCategory.MOVEMENT -> 0.80
        BehaviorCategory.UNKNOWN -> 0.40
    }

    private fun weightedAverage(values: List<Pair<Double, Double>>): Double {
        val totalWeight = values.sumOf { it.second }
        if (totalWeight <= 0.0) return 0.0
        return values.sumOf { (value, weight) -> value * weight } / totalWeight
    }

    private data class Evidence(
        val category: BehaviorCategory,
        val chance: Double,
        val confidence: Confidence,
    )
}
