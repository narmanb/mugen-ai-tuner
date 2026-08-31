package com.narmanb.mugenaituner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.narmanb.mugenaituner.core.AiComparison
import com.narmanb.mugenaituner.core.CharacterAnalysis
import com.narmanb.mugenaituner.core.MugenAiAnalyzer
import kotlinx.coroutines.launch

@Composable
internal fun AiComparisonPicker(leftAnalysis: CharacterAnalysis) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rightAnalysis by remember(leftAnalysis.characterName) { mutableStateOf<CharacterAnalysis?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            loading = true
            error = null
            runCatching {
                val files = CharacterFolderReader.read(context, uri)
                require(files.isNotEmpty()) { "No supported character files were found in that folder." }
                MugenAiAnalyzer.analyze(files)
            }.onSuccess { rightAnalysis = it }
                .onFailure { throwable ->
                    error = throwable.message ?: "The comparison character could not be analyzed."
                }
            loading = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Compare AI", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick another character to compare static AI strength, behavior pressure, and engine-difficulty responsiveness. Comparison is analysis-only.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { picker.launch(null) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (rightAnalysis == null) "Select comparison character" else "Choose a different character")
            }
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    rightAnalysis?.let { right ->
        AiComparisonCard(AiComparison.compare(leftAnalysis, right))
    }
}
