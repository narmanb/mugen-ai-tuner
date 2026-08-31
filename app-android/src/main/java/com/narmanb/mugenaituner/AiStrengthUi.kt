package com.narmanb.mugenaituner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.narmanb.mugenaituner.core.AiComparisonResult
import com.narmanb.mugenaituner.core.AiStrengthEstimate
import com.narmanb.mugenaituner.core.BehaviorCategory

@Composable
internal fun AiStrengthCard(estimate: AiStrengthEstimate) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Original AI strength estimate", style = MaterialTheme.typography.titleMedium)
            if (estimate.score != null) {
                Text(
                    "${estimate.score}% — ${estimate.label}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "Confidence: ${estimate.confidence} • ${estimate.evidenceCount} probability clue(s)",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(estimate.label, style = MaterialTheme.typography.titleSmall)
            }

            if (estimate.categoryScores.isNotEmpty()) {
                estimate.categoryScores.forEach { (category, score) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(categoryLabelForStrength(category))
                        Text("$score%")
                    }
                }
            }

            estimate.notes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun AiComparisonCard(comparison: AiComparisonResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("AI comparison", style = MaterialTheme.typography.titleMedium)
            Text("${comparison.leftName} vs ${comparison.rightName}")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    comparison.leftStrength.score?.let { "$it% ${comparison.leftStrength.label}" }
                        ?: comparison.leftStrength.label,
                )
                Text(
                    comparison.rightStrength.score?.let { "$it% ${comparison.rightStrength.label}" }
                        ?: comparison.rightStrength.label,
                )
            }

            comparison.categories.forEach { item ->
                val left = item.leftEstimatedScore?.let { "$it%" } ?: "—"
                val right = item.rightEstimatedScore?.let { "$it%" } ?: "—"
                Text(
                    "${categoryLabelForStrength(item.category)}: $left vs $right " +
                        "(${item.leftBehaviorCount}/${item.rightBehaviorCount} detected blocks)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            comparison.summaries.forEach { summary ->
                Text("• $summary", style = MaterialTheme.typography.bodySmall)
            }

            Text(
                "Comparison scores are static-code estimates, not measured match win rates.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun categoryLabelForStrength(category: BehaviorCategory): String =
    category.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
