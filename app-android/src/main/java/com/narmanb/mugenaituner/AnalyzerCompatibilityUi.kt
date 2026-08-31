package com.narmanb.mugenaituner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.narmanb.mugenaituner.core.AnalyzerCompatibility

@Composable
internal fun AnalyzerCompatibilityCard(compatibility: AnalyzerCompatibility) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Analyzer compatibility", style = MaterialTheme.typography.titleMedium)
            if (compatibility.understandingScore != null) {
                Text(
                    "${compatibility.understandingScore}% — ${compatibility.label}",
                    style = MaterialTheme.typography.titleLarge,
                )
            } else {
                Text(compatibility.label, style = MaterialTheme.typography.titleSmall)
            }
            Text("High-confidence behavior: ${compatibility.highConfidenceBehaviors}")
            Text("Medium-confidence behavior: ${compatibility.mediumConfidenceBehaviors}")
            Text("Uncertain behavior: ${compatibility.uncertainBehaviors}")
            if (compatibility.understoodConfigurationParameters > 0 || compatibility.safeConfigurationParameters > 0) {
                Text("Author AI settings understood: ${compatibility.understoodConfigurationParameters}")
                Text("Author AI settings safe to edit: ${compatibility.safeConfigurationParameters}")
            }
            Text("Safe automatic edit candidates: ${compatibility.safeEditCandidateCount}")
            compatibility.notes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
