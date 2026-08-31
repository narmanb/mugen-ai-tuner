package com.narmanb.mugenaituner.core

object AiConfigurationTuning {
    /**
     * Maps the standardized skill scale to small author-defined level ranges.
     * 0–20 => 0, 21–40 => 1, 41–60 => 2, 61–80 => 3, 81–100 => maximum.
     */
    fun targetLevel(skill: Int, maximumLevel: Int): Int {
        val max = maximumLevel.coerceAtLeast(0)
        if (max == 0) return 0
        return when (skill.coerceIn(0, 100)) {
            in 0..20 -> 0
            in 21..40 -> minOf(1, max)
            in 41..60 -> minOf(2, max)
            in 61..80 -> minOf(3, max)
            else -> max
        }
    }

    fun behaviorCategory(parameter: AiConfigurationParameter): BehaviorCategory? = when (parameter.kind) {
        AiConfigurationKind.COMBO_LEVEL -> BehaviorCategory.COMBO
        AiConfigurationKind.MOVEMENT_LEVEL -> BehaviorCategory.MOVEMENT
        AiConfigurationKind.GUARD_LEVEL -> BehaviorCategory.DEFENSE
        AiConfigurationKind.GENERIC -> null
    }

    /**
     * Produces ordinary PlannedEdit objects so packed settings use the exact same preview,
     * materialization, backup, verification, and restore pipeline as Random probability edits.
     */
    fun plan(
        parameters: List<AiConfigurationParameter>,
        profile: SkillProfile,
    ): List<PlannedEdit> = buildList {
        val normalized = profile.normalized()
        parameters.forEach { parameter ->
            if (!parameter.safeToEdit || parameter.confidence != Confidence.HIGH) return@forEach
            val category = behaviorCategory(parameter) ?: return@forEach
            val target = targetLevel(normalized.skillFor(category), parameter.maximumLevel)
                .coerceIn(parameter.minimumLevel, parameter.maximumLevel)
            if (target == parameter.currentLevel) return@forEach

            add(
                PlannedEdit(
                    category = category,
                    filePath = parameter.filePath,
                    sourceLine = parameter.lineNumber,
                    originalExpression = parameter.originalExpression,
                    replacementExpression = "value = 10 * $target",
                    confidence = parameter.confidence,
                    reason = "Set the author-exposed ${parameter.label.lowercase()} to $target/${parameter.maximumLevel} for ${normalized.skillFor(category)}% standardized ${category.name.lowercase().replace('_', ' ')} skill.",
                ),
            )
        }
    }
}
