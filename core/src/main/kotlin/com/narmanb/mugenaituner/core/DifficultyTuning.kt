package com.narmanb.mugenaituner.core

enum class DifficultyPreset(val label: String, val overallSkill: Int) {
    EASY("Easy", 30),
    NORMAL("Normal", 50),
    HARD("Hard", 75),
    CUSTOM("Custom", 50),
}

data class SkillProfile(
    val overallSkill: Int,
    val categoryOverrides: Map<BehaviorCategory, Int> = emptyMap(),
) {
    fun normalized(): SkillProfile = copy(
        overallSkill = overallSkill.coerceIn(0, 100),
        categoryOverrides = categoryOverrides.mapValues { (_, value) -> value.coerceIn(0, 100) },
    )

    fun skillFor(category: BehaviorCategory): Int =
        categoryOverrides[category]?.coerceIn(0, 100) ?: overallSkill.coerceIn(0, 100)

    fun withOverallSkill(skill: Int): SkillProfile = copy(overallSkill = skill.coerceIn(0, 100))

    fun withCategorySkill(category: BehaviorCategory, skill: Int): SkillProfile = copy(
        categoryOverrides = categoryOverrides + (category to skill.coerceIn(0, 100)),
    )

    fun clearCategoryOverride(category: BehaviorCategory): SkillProfile = copy(
        categoryOverrides = categoryOverrides - category,
    )

    companion object {
        fun fromPreset(preset: DifficultyPreset): SkillProfile = SkillProfile(preset.overallSkill)
    }
}

object DifficultyTuning {
    val adjustableCategories: List<BehaviorCategory> = listOf(
        BehaviorCategory.DEFENSE,
        BehaviorCategory.REACTION,
        BehaviorCategory.AGGRESSION,
        BehaviorCategory.COMBO,
        BehaviorCategory.ANTI_AIR,
        BehaviorCategory.PROJECTILE_RESPONSE,
        BehaviorCategory.THROW,
        BehaviorCategory.SUPER,
        BehaviorCategory.MOVEMENT,
    )

    fun labelFor(skill: Int): String = when (skill.coerceIn(0, 100)) {
        in 0..20 -> "Beginner"
        in 21..40 -> "Easy"
        in 41..60 -> "Normal"
        in 61..80 -> "Hard"
        else -> "Expert"
    }

    fun descriptionFor(category: BehaviorCategory): String = when (category) {
        BehaviorCategory.DEFENSE -> "Blocking, guarding, defensive reactions, and defensive decision frequency."
        BehaviorCategory.REACTION -> "General reaction and decision frequency when the AI sees an opportunity."
        BehaviorCategory.AGGRESSION -> "How readily the AI approaches, attacks, and commits to offensive choices."
        BehaviorCategory.COMBO -> "Hit confirms, combo continuation, and follow-up decisions."
        BehaviorCategory.ANTI_AIR -> "How consistently the AI reacts to airborne opponents."
        BehaviorCategory.PROJECTILE_RESPONSE -> "How consistently the AI responds to projectiles or projectile situations."
        BehaviorCategory.THROW -> "Throw attempts and throw-related decisions."
        BehaviorCategory.SUPER -> "Super, hyper, and high-resource attack decisions."
        BehaviorCategory.MOVEMENT -> "Dashes, jumps, positioning, evasive movement, and approach behavior."
        BehaviorCategory.UNKNOWN -> "Behavior the analyzer cannot classify confidently."
    }
}
