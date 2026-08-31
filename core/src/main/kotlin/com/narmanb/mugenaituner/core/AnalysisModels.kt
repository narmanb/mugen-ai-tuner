package com.narmanb.mugenaituner.core

data class SourceFile(
    val path: String,
    val content: String,
)

enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
}

enum class BehaviorCategory {
    DEFENSE,
    REACTION,
    AGGRESSION,
    COMBO,
    ANTI_AIR,
    PROJECTILE_RESPONSE,
    THROW,
    SUPER,
    MOVEMENT,
    UNKNOWN,
}

enum class DifficultyResponsiveness {
    NONE,
    LOW,
    MODERATE,
    FULL,
    UNKNOWN,
}

enum class VariableKind {
    VAR,
    FVAR,
}

data class AiFlag(
    val variable: Int,
    val confidence: Confidence,
    val reason: String,
    val kind: VariableKind = VariableKind.VAR,
) {
    val expressionName: String
        get() = when (kind) {
            VariableKind.VAR -> "var($variable)"
            VariableKind.FVAR -> "fvar($variable)"
        }
}

data class AiBehavior(
    val category: BehaviorCategory,
    val summary: String,
    val confidence: Confidence,
    val filePath: String,
    val lineNumber: Int,
    val section: String,
    val rawCode: String,
    /** True when the decision already varies with numeric AILevel, directly or through var/fvar. */
    val difficultyScaled: Boolean = false,
)

enum class AiConfigurationKind {
    COMBO_LEVEL,
    MOVEMENT_LEVEL,
    GUARD_LEVEL,
    GENERIC,
}

/**
 * An author-exposed AI setting encoded directly in the character state code rather than as a
 * per-decision Random gate. Holn-style AI patches commonly pack several small settings into one
 * AI variable using VarAdd controllers.
 */
data class AiConfigurationParameter(
    val kind: AiConfigurationKind,
    val label: String,
    val variable: Int,
    val currentLevel: Int,
    val minimumLevel: Int,
    val maximumLevel: Int,
    val confidence: Confidence,
    val filePath: String,
    val lineNumber: Int,
    val originalExpression: String,
    val description: String,
    val safeToEdit: Boolean = false,
)

data class CharacterAnalysis(
    val characterName: String,
    val author: String?,
    val aiDetected: Boolean,
    val aiFlags: List<AiFlag>,
    val behaviors: List<AiBehavior>,
    val difficultyResponsiveness: DifficultyResponsiveness,
    val directlyScaledBehaviorCount: Int,
    val aiBehaviorCount: Int,
    val notes: List<String>,
    val configurationParameters: List<AiConfigurationParameter> = emptyList(),
    /** Referenced character-code files that the selected source graph could not resolve/read. */
    val unresolvedSourceReferences: List<String> = emptyList(),
    /** Behaviors scaled through an AILevel-derived var/fvar rather than a literal AILevel token. */
    val indirectlyScaledBehaviorCount: Int = 0,
)
