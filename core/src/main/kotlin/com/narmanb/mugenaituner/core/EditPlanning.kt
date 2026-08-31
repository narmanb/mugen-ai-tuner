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
    val engineDifficultyScaling: Boolean = false,
) {
    val isEmpty: Boolean get() = edits.isEmpty()
}

object AiEditPlanner {
    private val simpleRandomRegex = Regex("""(?i)\brandom\s*(<=|<)\s*(\d{1,3})\b""")
    private val numericAiLevelScalingRegex = Regex(
        """(?i)(random[^\n]*ailevel|ailevel[^\n]*random|ailevel\s*[+\-*/]|[+\-*/]\s*ailevel)""",
    )

    fun plan(analysis: CharacterAnalysis, profile: SkillProfile): EditPlan =
        plan(analysis, profile, engineDifficultyScaling = false)

    /**
     * Builds a conservative edit plan. Only high-confidence AI blocks with simple
     * Random < N / Random <= N expressions are considered automatically writable.
     *
     * When [engineDifficultyScaling] is enabled, AILevel 4 is treated as the selected target
     * profile, levels 1-3 scale below it, and levels 5-8 interpolate back toward the character's
     * original probability. This makes IKEMEN/MUGEN 1.0+ difficulty useful without blindly
     * multiplying every Random expression in the character.
     */
    fun plan(
        analysis: CharacterAnalysis,
        profile: SkillProfile,
        engineDifficultyScaling: Boolean,
    ): EditPlan {
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
                val target = (original * factor).roundToInt().coerceIn(1, 999)
                val replacementExpression = if (engineDifficultyScaling) {
                    engineScaledExpression(operator, target, original)
                } else {
                    "Random $operator $target"
                }
                if (!engineDifficultyScaling && target == original) return@forEach

                edits += PlannedEdit(
                    category = behavior.category,
                    filePath = behavior.filePath,
                    sourceLine = behavior.lineNumber,
                    originalExpression = match.value,
                    replacementExpression = replacementExpression,
                    confidence = behavior.confidence,
                    reason = if (engineDifficultyScaling) {
                        "Scale ${behavior.category.name.lowercase().replace('_', ' ')} around ${skill}% (${DifficultyTuning.labelFor(skill)}) at AILevel 4, rising toward the original behavior at AILevel 8."
                    } else {
                        "Adjust ${behavior.category.name.lowercase().replace('_', ' ')} decision frequency toward ${skill}% (${DifficultyTuning.labelFor(skill)})."
                    },
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
                if (engineDifficultyScaling && edits.isNotEmpty()) {
                    add("IKEMEN/MUGEN AILevel scaling is enabled: AILevel 4 targets the selected skill, 1-3 are weaker, and 5-8 move toward the character's original probability.")
                }
                add("This plan is a preview. File writes and backups are handled separately so analysis never modifies a character by itself.")
            },
            engineDifficultyScaling = engineDifficultyScaling,
        )
    }

    internal fun engineScaledExpression(operator: String, target: Int, original: Int): String {
        val safeTarget = target.coerceIn(1, 999)
        val safeOriginal = original.coerceIn(safeTarget, 999)
        return "Random $operator ifelse(AILevel <= 4, $safeTarget * AILevel / 4.0, $safeTarget + ($safeOriginal - $safeTarget) * (AILevel - 4) / 4.0)"
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
