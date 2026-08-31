package com.narmanb.mugenaituner.core

data class RosterCharacterSummary(
    val folderName: String,
    val characterName: String,
    val author: String?,
    val aiDetected: Boolean,
    val estimatedStrength: Int?,
    val estimatedStrengthLabel: String,
    val estimateConfidence: Confidence,
    val difficultyResponsiveness: DifficultyResponsiveness,
    val aiBehaviorCount: Int,
    val highConfidenceBehaviorCount: Int,
    val mediumConfidenceBehaviorCount: Int,
)

data class RosterAnalysisSummary(
    val characters: List<RosterCharacterSummary>,
    val skippedFolders: List<String>,
) {
    val customAiCount: Int get() = characters.count { it.aiDetected }
    val difficultyInsensitiveCount: Int get() = characters.count {
        it.aiDetected && it.difficultyResponsiveness == DifficultyResponsiveness.NONE
    }
    val estimatedHardOrBrutalCount: Int get() = characters.count {
        (it.estimatedStrength ?: -1) >= 61
    }
}

object RosterAnalysis {
    fun summarize(folderName: String, analysis: CharacterAnalysis): RosterCharacterSummary {
        val strength = AiStrengthEstimator.estimate(analysis)
        return RosterCharacterSummary(
            folderName = folderName,
            characterName = analysis.characterName,
            author = analysis.author,
            aiDetected = analysis.aiDetected,
            estimatedStrength = strength.score,
            estimatedStrengthLabel = strength.label,
            estimateConfidence = strength.confidence,
            difficultyResponsiveness = analysis.difficultyResponsiveness,
            aiBehaviorCount = analysis.aiBehaviorCount,
            highConfidenceBehaviorCount = analysis.behaviors.count { it.confidence == Confidence.HIGH },
            mediumConfidenceBehaviorCount = analysis.behaviors.count { it.confidence == Confidence.MEDIUM },
        )
    }
}
