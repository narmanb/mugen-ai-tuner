package com.narmanb.mugenaituner.core

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
    private val simpleRandomRegex = Regex("""(?i)\brandom\s*(<=|>=|<|>)\s*(1000|\d{1,3})\b""")
    private val numericAiLevelScalingRegex = Regex(
        """(?i)(random[^\n]*ailevel|ailevel[^\n]*random|ailevel\s*[+\-*/]|[+\-*/]\s*ailevel|ailevel\s*(?:>=|>|==|=|<=|<)\s*[1-8]\b)""",
    )

    fun plan(analysis: CharacterAnalysis, profile: SkillProfile): EditPlan =
        plan(analysis, profile, engineDifficultyScaling = false)

    /**
     * Builds a conservative standardized edit plan. Only high-confidence AI blocks with simple
     * Random comparisons are considered automatically writable.
     *
     * The selected 0–100 skill is mapped onto shared category-specific probability curves. This
     * means 50% aims for the app's common Normal target instead of multiplying whatever arbitrary
     * probability the character author happened to use. Five percent of the original probability
     * is retained as authored style.
     *
     * When [engineDifficultyScaling] is enabled, AILevel 1 starts at one quarter of the selected
     * standardized skill, AILevel 4 equals the selected skill, and AILevel 8 reaches the app's
     * standardized Expert/100 target. Existing numeric AILevel formulas remain untouched.
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
            var producedEdit = false

            matches.forEach matchLoop@{ match ->
                val operator = match.groupValues[1]
                val original = match.groupValues[2].toIntOrNull() ?: return@matchLoop
                val centerThreshold = StandardizedAiCalibration.standardizedThreshold(
                    category = behavior.category,
                    skill = skill,
                    operator = operator,
                    originalThreshold = original,
                ) ?: return@matchLoop

                val replacementExpression = if (engineDifficultyScaling) {
                    val lowThreshold = StandardizedAiCalibration.standardizedThreshold(
                        category = behavior.category,
                        skill = StandardizedAiCalibration.lowEngineSkill(skill),
                        operator = operator,
                        originalThreshold = original,
                    ) ?: return@matchLoop
                    val highThreshold = StandardizedAiCalibration.standardizedThreshold(
                        category = behavior.category,
                        skill = 100,
                        operator = operator,
                        originalThreshold = original,
                    ) ?: return@matchLoop
                    engineScaledExpression(
                        operator = operator,
                        lowThreshold = lowThreshold,
                        centerThreshold = centerThreshold,
                        highThreshold = highThreshold,
                    )
                } else {
                    "Random $operator $centerThreshold"
                }

                if (!engineDifficultyScaling && centerThreshold == original) return@matchLoop

                edits += PlannedEdit(
                    category = behavior.category,
                    filePath = behavior.filePath,
                    sourceLine = behavior.lineNumber,
                    originalExpression = match.value,
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
