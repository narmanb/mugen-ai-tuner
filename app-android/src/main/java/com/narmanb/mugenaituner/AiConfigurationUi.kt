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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.narmanb.mugenaituner.core.AiConfigurationParameter

@Composable
internal fun AiConfigurationCard(parameters: List<AiConfigurationParameter>) {
    if (parameters.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Author AI configuration", style = MaterialTheme.typography.titleMedium)
            Text(
                "These are explicit settings encoded by the character/AI patch author rather than probabilities inferred by MUGEN AI Tuner. They are shown separately so packed variables are not mistaken for simple on/off flags.",
                style = MaterialTheme.typography.bodySmall,
            )

            parameters.forEach { parameter ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "${parameter.label}: ${parameter.currentLevel}/${parameter.maximumLevel}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(parameter.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "var(${parameter.variable}) • Confidence: ${parameter.confidence}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${parameter.filePath}:${parameter.lineNumber}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        parameter.originalExpression,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Text(
                "Automatic editing of these packed settings remains disabled until the app verifies the exact patch structure and safe level range.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
