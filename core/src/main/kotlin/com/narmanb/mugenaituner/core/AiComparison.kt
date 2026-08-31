package com.narmanb.mugenaituner.core

data class CategoryComparison(
    val category: BehaviorCategory,
    val leftBehaviorCount: Int,
    val rightBehaviorCount: Int,
    val leftEstimatedScore: Int?,
    val rightEstimatedScore: Int?,
) {
    val estimatedScoreDelta: Int? = if (leftEstimatedScore != null && rightEstimatedScore != null) {
        rightEstimatedScore - leftEstimatedScore
    } else {
        null
    }
}

data class AiComparisonResult(
    val leftName: String,
    val rightName: String,
    val leftStrength: AiStrengthEstimate,
    val rightStrength: AiStrengthEstimate,
    val categories: List<CategoryComparison>,
    val summaries: List<String>,
)

object AiComparison {
    fun compare(left: CharacterAnalysis, right: CharacterAnalysis): AiComparisonResult {
        val leftStrength = AiStrengthEstimator.estimate(left)
        val rightStrength = AiStrengthEstimator.estimate(right)
        val categories = DifficultyTuning.adjustableCategories.map { category ->
            CategoryComparison(
                category = category,
                leftBehaviorCount = left.behaviors.count { it.category == category },
                rightBehaviorCount = right.behaviors.count { it.category == category },
                leftEstimatedScore = leftStrength.categoryScores[category],
                rightEstimatedScore = rightStrength.categoryScores[category],
            )
        }.filter { comparison ->
            comparison.leftBehaviorCount > 0 || comparison.rightBehaviorCount > 0
        }

        return AiComparisonResult(
            leftName = left.characterName,
            rightName = right.characterName,
            leftStrength = leftStrength,
            rightStrength = rightStrength,
            categories = categories,
            summaries = buildList {
                if (leftStrength.score != null && rightStrength.score != null) {
                    val difference = rightStrength.score - leftStrength.score
                    when {
                        difference >= 10 -> add("${right.characterName} appears substantially more demanding in the static AI estimate.")
                        difference <= -10 -> add("${left.characterName} appears substantially more demanding in the static AI estimate.")
                        else -> add("Their overall static AI estimates are in a similar range.")
                    }
                }

                val largestCategoryDelta = categories
                    .mapNotNull { comparison ->
                        comparison.estimatedScoreDelta?.let { delta -> comparison to delta }
                    }
                    .maxByOrNull { (_, delta) -> kotlin.math.abs(delta) }

                largestCategoryDelta?.let { (comparison, delta) ->
                    if (kotlin.math.abs(delta) >= 10) {
                        val strongerName = if (delta > 0) right.characterName else left.characterName
                        add(
                            "$strongerName shows the larger ${comparison.category.name.lowercase().replace('_', ' ')} pressure in the detected probability logic.",
                        )
                    }
                }

                if (left.difficultyResponsiveness != right.difficultyResponsiveness) {
                    add(
                        "Engine difficulty responsiveness differs: ${left.characterName}=${left.difficultyResponsiveness}, ${right.characterName}=${right.difficultyResponsiveness}.",
                    )
                }
            },
        )
    }
}
