package com.narmanb.mugenaituner.core

import kotlin.math.abs

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
    private val numericAiLevelScalingRegex = Regex(
        """(?i)(random[^\n]*ailevel|ailevel[^\n]*random|ailevel\s*[+\-*/]|[+\-*/]\s*ailevel|ailevel\s*(?:>=|>|==|=|<=|<)\s*[1-8]\b)""",
    )

    fun plan(analysis: CharacterAnalysis, profile: SkillProfile): EditPlan =
        plan(analysis, profile, engineDifficultyScaling = false)

    /**
     * Builds a conservative standardized edit plan. Only high-confidence AI blocks with Random
     * probability forms that map cleanly to one activation chance are automatically writable.
     *
     * The selected 0–100 skill is mapped onto shared category-specific probability curves. This
     * means 50% aims for the app's common Normal target instead of multiplying whatever arbitrary
     * probability the character author happened to use. Five percent of the original probability
     * is retained as authored style.
     *
     * When [engineDifficultyScaling] is enabled, AILevel 1 starts at one quarter of the selected
     * standardized skill, AILevel 4 equals the selected skill, and AILevel 8 reaches the app's
     * standardized Expert/100 target. Existing numeric AILevel formulas remain untouched.
     * Inclusive Random ranges are tunable for fixed presets/custom settings but are left unchanged
     * by AILevel conversion until dynamic range-bound portability is verified across engines.
     */
    fun plan(
        analysis: CharacterAnalysis,
        profile: SkillProfile,
        engineDifficultyScaling: Boolean,
    ): EditPlan {
        val normalized = profile.normalized()
        val edits = mutableListOf<PlannedEdit>()
        var skipped = 0
        var rangeScalingSkipped = 0

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

            val decisions = RandomProbabilityParser.findAll(behavior.rawCode)
            if (decisions.isEmpty()) {
                skipped++
                return@forEach
            }

            val skill = normalized.skillFor(behavior.category)
            var producedEdit = false

            decisions.forEach decisionLoop@{ decision ->
                val centerChance = StandardizedAiCalibration.standardizedChance(
                    category = behavior.category,
                    skill = skill,
                    originalChance = decision.activationChance,
                ) ?: return@decisionLoop

                val replacementExpression = if (engineDifficultyScaling) {
                    if (decision.form != RandomProbabilityForm.COMPARISON) {
                        rangeScalingSkipped++
                        return@decisionLoop
                    }
                    val operator = decision.comparisonOperator ?: return@decisionLoop
                    val lowChance = StandardizedAiCalibration.standardizedChance(
                        category = behavior.category,
                        skill = StandardizedAiCalibration.lowEngineSkill(skill),
                        originalChance = decision.activationChance,
                    ) ?: return@decisionLoop
                    val highChance = StandardizedAiCalibration.standardizedChance(
                        category = behavior.category,
                        skill = 100,
                        originalChance = decision.activationChance,
                    ) ?: return@decisionLoop
                    engineScaledExpression(
                        operator = operator,
                        lowThreshold = StandardizedAiCalibration.thresholdForChance(operator, lowChance),
                        centerThreshold = StandardizedAiCalibration.thresholdForChance(operator, centerChance),
                        highThreshold = StandardizedAiCalibration.thresholdForChance(operator, highChance),
                    )
                } else {
                    if (abs(centerChance - decision.activationChance) < 0.0005) return@decisionLoop
                    decision.replacementForChance(centerChance)
                }

                edits += PlannedEdit(
                    category = behavior.category,
                    filePath = behavior.filePath,
                    sourceLine = behavior.lineNumber,
                    originalExpression = decision.expression,
                    replacementExpression = replacementExpression,
                    confidence = behavior.confidence,
                    reason = if (engineDifficultyScaling) {
                        "Standardize ${behavior.category.name.lowercase().replace('_', ' ')} around ${skill}% (${DifficultyTuning.labelFor(skill)}) at AILevel 4, with lower levels weaker and AILevel 8 reaching Expert/100 behavior."
                    } else {
                        "Standardize ${behavior.category.name.lowercase().replace('_', ' ')} toward ${skill}% (${DifficultyTuning.labelFor(skill)}) on the shared MUGEN AI Tuner scale."
                    },
                )
                producedEdit = true
            }

            if (!producedEdit) skipped++
        }

        return EditPlan(
            profile = normalized,
            edits = edits.distinctBy { Triple(it.filePath, it.sourceLine, it.originalExpression) },
            skippedBehaviorCount = skipped,
            notes = buildList {
                if (skipped > 0) add("$skipped analyzed AI behavior block(s) were left unchanged because their rewrite was not yet considered safe enough.")
                if (rangeScalingSkipped > 0) {
                    add("$rangeScalingSkipped range-style Random decision(s) were left fixed because automatic AILevel conversion for dynamic range bounds is intentionally disabled.")
                }
                if (edits.isEmpty() && analysis.aiDetected) add("AI was detected, but no high-confidence simple probability edits are currently safe to apply automatically.")
                if (edits.isNotEmpty()) {
                    add("The 0–100 scale is standardized: 50% targets a shared Normal behavior level rather than half of the author's original probability.")
                }
                if (engineDifficultyScaling && edits.isNotEmpty()) {
                    add("IKEMEN/MUGEN AILevel scaling is enabled: AILevel 1 starts below the selected target, AILevel 4 matches it, and AILevel 8 reaches the standardized Expert/100 target.")
                }
                add("This plan is a preview. File writes and backups are handled separately so analysis never modifies a character by itself.")
            },
            engineDifficultyScaling = engineDifficultyScaling,
        )
    }

    internal fun engineScaledExpression(
        operator: String,
        lowThreshold: Int,
        centerThreshold: Int,
        highThreshold: Int,
    ): String =
        "Random $operator ifelse(AILevel <= 1, $lowThreshold, " +
            "ifelse(AILevel <= 4, $lowThreshold + ($centerThreshold - $lowThreshold) * (AILevel - 1) / 3.0, " +
            "$centerThreshold + ($highThreshold - $centerThreshold) * (AILevel - 4) / 4.0))"

    /** Temporary compatibility overload for older internal tests/callers. */
    internal fun engineScaledExpression(operator: String, target: Int, original: Int): String =
        engineScaledExpression(
            operator = operator,
            lowThreshold = target,
            centerThreshold = target,
            highThreshold = original,
        )
}
