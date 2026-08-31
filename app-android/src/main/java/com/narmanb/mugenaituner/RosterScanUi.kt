package com.narmanb.mugenaituner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.narmanb.mugenaituner.core.RosterAnalysisSummary
import com.narmanb.mugenaituner.core.RosterSkipReason
import kotlinx.coroutines.launch

@Composable
internal fun RosterScanCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RosterAnalysisSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            scanning = true
            result = null
            error = null
            runCatching { RosterFolderScanner.scan(context, uri) }
                .onSuccess { result = it }
                .onFailure { throwable ->
                    error = throwable.message ?: "The chars folder could not be scanned."
                }
            scanning = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Roster scan", style = MaterialTheme.typography.titleMedium)
            Text(
                "Select the engine's chars folder to analyze each immediate character folder. Roster scan never modifies character files.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { picker.launch(null) },
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (result == null) "Scan chars folder" else "Scan another chars folder")
            }
            if (scanning) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Scanning roster…")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            result?.let { roster ->
                Text("Characters analyzed: ${roster.characters.size}")
                Text("Custom AI detected: ${roster.customAiCount}")
                Text("No numeric AILevel scaling: ${roster.difficultyInsensitiveCount}")
                Text("Estimated Hard/Brutal: ${roster.estimatedHardOrBrutalCount}")
                if (roster.skippedFolders.isNotEmpty()) {
                    Text("Folders needing attention: ${roster.skippedFolders.size}", style = MaterialTheme.typography.bodySmall)
                    if (roster.needsDefSelectionCount > 0) {
                        Text(
                            "Need active DEF selection: ${roster.needsDefSelectionCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    roster.skippedDetails.take(12).forEach { skipped ->
                        val compactDetail = skipped.detail.replace('\n', ' ').take(180)
                        Text(
                            "${skipped.folderName} — ${skipReasonLabel(skipped.reason)}: $compactDetail",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (roster.skippedDetails.size > 12) {
                        Text(
                            "+ ${roster.skippedDetails.size - 12} additional folder diagnostic(s)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (roster.characters.isNotEmpty()) {
                    Text("Highest static estimates", style = MaterialTheme.typography.titleSmall)
                    roster.characters.take(25).forEach { character ->
                        val strength = character.estimatedStrength?.let { "$it% ${character.estimatedStrengthLabel}" }
                            ?: character.estimatedStrengthLabel
                        Text(
                            "${character.characterName} — $strength • AILevel ${character.difficultyResponsiveness}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (roster.characters.size > 25) {
                        Text(
                            "+ ${roster.characters.size - 25} additional analyzed character(s)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun skipReasonLabel(reason: RosterSkipReason): String = when (reason) {
    RosterSkipReason.NEEDS_DEF_SELECTION -> "Needs DEF selection"
    RosterSkipReason.NO_DEF -> "No usable DEF"
    RosterSkipReason.UNREADABLE -> "Unreadable"
    RosterSkipReason.ANALYSIS_ERROR -> "Analysis error"
}
