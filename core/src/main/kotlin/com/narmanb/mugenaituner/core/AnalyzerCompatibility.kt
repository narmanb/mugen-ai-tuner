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
    val understoodConfigurationParameters: Int = 0,
    val safeConfigurationParameters: Int = 0,
)

object AnalyzerCompatibilityEstimator {
    fun estimate(analysis: CharacterAnalysis): AnalyzerCompatibility {
        val unresolved = analysis.unresolvedSourceReferences.distinct()
        if (!analysis.aiDetected) {
            if (unresolved.isNotEmpty()) {
                return AnalyzerCompatibility(
                    understandingScore = 0,
                    label = "Incomplete source graph",
                    highConfidenceBehaviors = 0,
                    mediumConfidenceBehaviors = 0,
                    uncertainBehaviors = 0,
                    safeEditCandidateCount = 0,
                    behaviorCount = 0,
                    notes = buildList {
                        add("Custom AI cannot be ruled out because ${unresolved.size} referenced character-code file(s) could not be resolved.")
                        unresolved.take(4).forEach { add("Missing reference: $it") }
                    },
                )
            }
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

        val behaviorTotal = analysis.behaviors.size
        val highBehaviors = analysis.behaviors.count {
            it.confidence == Confidence.HIGH && it.category != BehaviorCategory.UNKNOWN
        }
        val mediumBehaviors = analysis.behaviors.count {
            it.confidence == Confidence.MEDIUM && it.category != BehaviorCategory.UNKNOWN
        }
        val uncertainBehaviors = behaviorTotal - highBehaviors - mediumBehaviors

        val parameters = analysis.configurationParameters
        val highParameters = parameters.count {
            it.confidence == Confidence.HIGH && it.kind != AiConfigurationKind.GENERIC
        }
        val mediumParameters = parameters.count {
            it.confidence == Confidence.MEDIUM && it.kind != AiConfigurationKind.GENERIC
        }
        val uncertainParameters = parameters.size - highParameters - mediumParameters
        val totalUnits = behaviorTotal + parameters.size

        if (totalUnits == 0) {
            return AnalyzerCompatibility(
                understandingScore = 0,
                label = if (unresolved.isEmpty()) "Unsupported/unknown" else "Incomplete source graph",
                highConfidenceBehaviors = 0,
                mediumConfidenceBehaviors = 0,
                uncertainBehaviors = 0,
                safeEditCandidateCount = 0,
                behaviorCount = 0,
                notes = buildList {
                    add("AI activation was detected, but no AI behavior or configuration units could be traced confidently.")
                    if (unresolved.isNotEmpty()) {
                        add("${unresolved.size} referenced character-code file(s) could not be resolved, so analysis is incomplete.")
                    }
                },
            )
        }

        val weightedUnits =
            highBehaviors + highParameters +
                (mediumBehaviors + mediumParameters) * 0.6 +
                (uncertainBehaviors + uncertainParameters) * 0.15
        val baseScore = (weightedUnits / totalUnits.toDouble() * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        val sourcePenalty = (unresolved.size * 12).coerceAtMost(35)
        val score = (baseScore - sourcePenalty).coerceIn(0, 100)

        // Count locations for which the conservative planner can produce at least one
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
        val safeParameters = parameters.count { it.safeToEdit && it.confidence == Confidence.HIGH }

        return AnalyzerCompatibility(
            understandingScore = score,
            label = when {
                unresolved.isNotEmpty() && score < 65 -> "Incomplete/partial compatibility"
                score in 85..100 -> "High compatibility"
                score in 65..84 -> "Good compatibility"
                score in 40..64 -> "Partial compatibility"
                score in 1..39 -> "Low compatibility"
                else -> "Unsupported/unknown"
            },
            highConfidenceBehaviors = highBehaviors,
            mediumConfidenceBehaviors = mediumBehaviors,
            uncertainBehaviors = uncertainBehaviors,
            safeEditCandidateCount = safeCandidateLocations,
            behaviorCount = behaviorTotal,
            understoodConfigurationParameters = highParameters + mediumParameters,
            safeConfigurationParameters = safeParameters,
            notes = buildList {
                add("Compatibility measures analyzer confidence, not how strong the AI is.")
                if (unresolved.isNotEmpty()) {
                    add("Compatibility was reduced because ${unresolved.size} referenced character-code file(s) could not be resolved.")
                    unresolved.take(4).forEach { add("Missing reference: $it") }
                }
                if (parameters.isNotEmpty()) {
                    add("${highParameters + mediumParameters}/${parameters.size} author-exposed AI configuration setting(s) were understood; $safeParameters passed the stricter automatic-edit check.")
                }
                if (safeCandidateLocations < highBehaviors + safeParameters) {
                    add("Some high-confidence AI logic is understood but intentionally not auto-editable because its expression is more complex than the safe rewrite rules.")
                }
                val totalUncertain = uncertainBehaviors + uncertainParameters
                if (totalUncertain > 0) {
                    add("$totalUncertain AI behavior/configuration unit(s) remain low-confidence or unclassified and are withheld from automatic editing.")
                }
            },
        )
    }
}
