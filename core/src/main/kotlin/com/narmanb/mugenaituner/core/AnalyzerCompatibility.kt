package com.narmanb.mugenaituner.core

import kotlin.math.roundToInt

data class AnalyzerCompatibility(
    val understandingScore: Int?,
    val label: String,
    val highConfidenceBehaviors: Int,
    val mediumConfidenceBehaviors: Int,
    val uncertainBehaviors: Int,
    val safeEditCandidateCount: Int,
    val behaviorCount: Int,
    val notes: List<String>,
)

object AnalyzerCompatibilityEstimator {
    fun estimate(analysis: CharacterAnalysis): AnalyzerCompatibility {
        if (!analysis.aiDetected) {
            return AnalyzerCompatibility(
                understandingScore = null,
                label = "No custom AI detected",
                highConfidenceBehaviors = 0,
                mediumConfidenceBehaviors = 0,
                uncertainBehaviors = 0,
                safeEditCandidateCount = 0,
                behaviorCount = 0,
                notes = listOf("There is no detected custom AI to score for analyzer compatibility."),
            )
        }

        val total = analysis.behaviors.size
        if (total == 0) {
            return AnalyzerCompatibility(
                understandingScore = 0,
                label = "Unsupported/unknown",
                highConfidenceBehaviors = 0,
                mediumConfidenceBehaviors = 0,
                uncertainBehaviors = 0,
                safeEditCandidateCount = 0,
                behaviorCount = 0,
                notes = listOf("AI activation was detected, but no AI behavior blocks could be traced confidently."),
            )
        }

        val high = analysis.behaviors.count { it.confidence == Confidence.HIGH && it.category != BehaviorCategory.UNKNOWN }
        val medium = analysis.behaviors.count { it.confidence == Confidence.MEDIUM && it.category != BehaviorCategory.UNKNOWN }
        val uncertain = total - high - medium
        val weighted = (high + medium * 0.6 + uncertain * 0.15) / total.toDouble()
        val score = (weighted * 100.0).roundToInt().coerceIn(0, 100)

        // Count behavior blocks for which the conservative planner can produce at least one
        // normal-difficulty edit. This is deliberately reported separately from understanding:
        // understood AI may still use logic we refuse to rewrite automatically.
        val normalPlan = AiEditPlanner.plan(
            analysis = analysis,
            profile = SkillProfile.fromPreset(DifficultyPreset.NORMAL),
            engineDifficultyScaling = false,
        )
        val safeCandidateLocations = normalPlan.edits
            .map { Triple(it.filePath, it.sourceLine, it.category) }
            .distinct()
            .size

        return AnalyzerCompatibility(
            understandingScore = score,
            label = when (score) {
                in 85..100 -> "High compatibility"
                in 65..84 -> "Good compatibility"
                in 40..64 -> "Partial compatibility"
                in 1..39 -> "Low compatibility"
                else -> "Unsupported/unknown"
            },
            highConfidenceBehaviors = high,
            mediumConfidenceBehaviors = medium,
            uncertainBehaviors = uncertain,
            safeEditCandidateCount = safeCandidateLocations,
            behaviorCount = total,
            notes = buildList {
                add("Compatibility measures analyzer confidence, not how strong the AI is.")
                if (safeCandidateLocations < high) {
                    add("Some high-confidence behavior is understood but intentionally not auto-editable because its expression is more complex than the safe rewrite rules.")
                }
                if (uncertain > 0) {
                    add("$uncertain behavior block(s) remain low-confidence or unclassified and are withheld from automatic editing.")
                }
            },
        )
    }
}
