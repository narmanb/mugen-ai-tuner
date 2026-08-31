package com.narmanb.mugenaituner.core

import kotlin.math.pow
import kotlin.math.roundToInt

data class PlannedEdit(
    val category: BehaviorCategory,
    val filePath: String,
    val sourceLine: Int,
    val originalExpression: String,
    val replacementExpression: String,
    val confidence: Confidence,
    val reason: String,
)

data class EditPlan(
    val profile: SkillProfile,
    val edits: List<PlannedEdit>,
    val skippedBehaviorCount: Int,
    val notes: List<String>,
) {
    val isEmpty: Boolean get() = edits.isEmpty()
}

object AiEditPlanner {
    private val simpleRandomRegex = Regex("""(?i)\brandom\s*(<=|<)\s*(\d{1,3})\b""")
    private val numericAiLevelScalingRegex = Regex(
        """(?i)(random[^\n]*ailevel|ailevel[^\n]*random|ailevel\s*[+\-*/]|[+\-*/]\s*ailevel)""",
    )

    /**
     * Builds a preview-only plan. The first implementation is deliberately conservative:
     * only high-confidence AI blocks with simple Random < N / Random <= N expressions
     * are proposed for automatic adjustment. More complex expressions remain visible to
     * the analyzer but are not rewritten automatically.
     */
    fun plan(analysis: CharacterAnalysis, profile: SkillProfile): EditPlan {
        val normalized = profile.normalized()
        val edits = mutableListOf<PlannedEdit>()
        var skipped = 0

        analysis.behaviors.forEach { behavior ->
            if (behavior.confidence != Confidence.HIGH || behavior.category == BehaviorCategory.UNKNOWN) {
                skipped++
                return@forEach
            }

            if (numericAiLevelScalingRegex.containsMatchIn(behavior.rawCode)) {
                // Already difficulty-scaled code needs a dedicated rewrite strategy so we do not
                // accidentally apply scaling twice.
                skipped++
                return@forEach
            }

            val matches = simpleRandomRegex.findAll(behavior.rawCode).toList()
            if (matches.isEmpty()) {
                skipped++
                return@forEach
            }

            val skill = normalized.skillFor(behavior.category)
            val factor = adjustmentFactor(behavior.category, skill)

            matches.forEach { match ->
                val operator = match.groupValues[1]
                val original = match.groupValues[2].toIntOrNull() ?: return@forEach
                val replacement = (original * factor).roundToInt().coerceIn(1, 999)
                if (replacement == original) return@forEach

                edits += PlannedEdit(
                    category = behavior.category,
                    filePath = behavior.filePath,
                    sourceLine = behavior.lineNumber,
                    originalExpression = match.value,
                    replacementExpression = "Random $operator $replacement",
                    confidence = behavior.confidence,
                    reason = "Adjust ${behavior.category.name.lowercase().replace('_', ' ')} decision frequency toward ${skill}% (${DifficultyTuning.labelFor(skill)}).",
                )
            }
        }

        return EditPlan(
            profile = normalized,
            edits = edits.distinctBy { Triple(it.filePath, it.sourceLine, it.originalExpression) },
            skippedBehaviorCount = skipped,
            notes = buildList {
                if (skipped > 0) add("$skipped analyzed AI behavior block(s) were left unchanged because their rewrite was not yet considered safe enough.")
                if (edits.isEmpty() && analysis.aiDetected) add("AI was detected, but no high-confidence simple probability edits are currently safe to apply automatically.")
                add("This plan is a preview. File writes and backups are handled separately so analysis never modifies a character by itself.")
            },
        )
    }

    /**
     * 100 preserves the character's original probability. Lower standardized skill values
     * reduce different behavior categories at different rates instead of treating every
     * behavior as the same kind of percentage.
     */
    internal fun adjustmentFactor(category: BehaviorCategory, skill: Int): Double {
        val s = skill.coerceIn(0, 100) / 100.0
        val (minimum, exponent) = when (category) {
            BehaviorCategory.DEFENSE -> 0.08 to 1.60
            BehaviorCategory.REACTION -> 0.10 to 1.80
            BehaviorCategory.AGGRESSION -> 0.25 to 1.10
            BehaviorCategory.COMBO -> 0.15 to 1.35
            BehaviorCategory.ANTI_AIR -> 0.10 to 1.60
            BehaviorCategory.PROJECTILE_RESPONSE -> 0.10 to 1.60
            BehaviorCategory.THROW -> 0.20 to 1.20
            BehaviorCategory.SUPER -> 0.20 to 1.00
            BehaviorCategory.MOVEMENT -> 0.30 to 1.00
            BehaviorCategory.UNKNOWN -> 1.00 to 1.00
        }
        return minimum + (1.0 - minimum) * s.pow(exponent)
    }
}
