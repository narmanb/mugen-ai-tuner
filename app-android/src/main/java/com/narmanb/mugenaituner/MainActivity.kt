package com.narmanb.mugenaituner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.narmanb.mugenaituner.core.AiBehavior
import com.narmanb.mugenaituner.core.AiEditMaterializer
import com.narmanb.mugenaituner.core.AiEditPlanner
import com.narmanb.mugenaituner.core.BehaviorCategory
import com.narmanb.mugenaituner.core.CharacterAnalysis
import com.narmanb.mugenaituner.core.CharacterFingerprint
import com.narmanb.mugenaituner.core.CharacterFingerprinter
import com.narmanb.mugenaituner.core.DifficultyPreset
import com.narmanb.mugenaituner.core.DifficultyTuning
import com.narmanb.mugenaituner.core.EditPlan
import com.narmanb.mugenaituner.core.MaterializedEditPlan
import com.narmanb.mugenaituner.core.MugenAiAnalyzer
import com.narmanb.mugenaituner.core.SkillProfile
import com.narmanb.mugenaituner.core.SourceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MugenAiTunerApp()
                }
            }
        }
    }
}

@Composable
private fun MugenAiTunerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var analysis by remember { mutableStateOf<CharacterAnalysis?>(null) }
    var analyzedFiles by remember { mutableStateOf<List<SourceFile>>(emptyList()) }
    var analyzedFingerprint by remember { mutableStateOf<CharacterFingerprint?>(null) }
    var selectedTreeUri by remember { mutableStateOf<Uri?>(null) }
    var matchingSnapshot by remember { mutableStateOf<StoredBackupSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var selectedPreset by remember { mutableStateOf(DifficultyPreset.NORMAL) }
    var skillProfile by remember { mutableStateOf(SkillProfile.fromPreset(DifficultyPreset.NORMAL)) }

    suspend fun refreshFromDisk(uri: Uri, resetDifficulty: Boolean) {
        val files = CharacterFolderReader.read(context, uri)
        if (files.isEmpty()) error("No supported MUGEN/IKEMEN text files were found in that folder.")
        val result = MugenAiAnalyzer.analyze(files)
        val fingerprint = CharacterFingerprinter.fingerprint(files)
        val snapshot = withContext(Dispatchers.IO) {
            BackupHistoryStore.latestSnapshotEndingAt(
                context = context,
                characterName = result.characterName,
                treeUri = uri,
                fingerprint = fingerprint,
            )
        }

        selectedTreeUri = uri
        analyzedFiles = files
        analyzedFingerprint = fingerprint
        analysis = result
        matchingSnapshot = snapshot
        if (resetDifficulty) {
            selectedPreset = DifficultyPreset.NORMAL
            skillProfile = SkillProfile.fromPreset(DifficultyPreset.NORMAL)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }

        scope.launch {
            loading = true
            error = null
            actionMessage = null
            analysis = null
            runCatching {
                refreshFromDisk(uri, resetDifficulty = true)
            }.onFailure { throwable ->
                error = throwable.message ?: "The character folder could not be analyzed."
            }
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("MUGEN AI Tuner", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Offline AI analysis for MUGEN and IKEMEN characters. The analyzer traces AI variables rather than assuming var(59) means AI.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            Button(
                onClick = { folderPicker.launch(null) },
                enabled = !loading && !applying,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (analysis == null) "Select character folder" else "Analyze another character")
            }
        }

        if (loading || applying) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        if (applying) "Verifying, backing up, and writing…" else "Analyzing character files…",
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        error?.let { message ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        actionMessage?.let { message ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(message, modifier = Modifier.padding(16.dp))
                }
            }
        }

        analysis?.let { result ->
            item { AnalysisSummary(result) }

            if (result.aiDetected) {
                val availableCategories = DifficultyTuning.adjustableCategories.filter { category ->
                    result.behaviors.any { it.category == category }
                }
                val plan = AiEditPlanner.plan(result, skillProfile)
                val materialized = AiEditMaterializer.materialize(analyzedFiles, plan)
                val currentSnapshot = matchingSnapshot
                val currentlyTuned = currentSnapshot != null && !currentSnapshot.reason.startsWith("Undo snapshot")

                item {
                    DifficultyControls(
                        selectedPreset = selectedPreset,
                        profile = skillProfile,
                        availableCategories = availableCategories,
                        onPresetSelected = { preset ->
                            selectedPreset = preset
                            if (preset != DifficultyPreset.CUSTOM) {
                                skillProfile = SkillProfile.fromPreset(preset)
                            }
                        },
                        onOverallChanged = { value ->
                            selectedPreset = DifficultyPreset.CUSTOM
                            skillProfile = skillProfile.withOverallSkill(value)
                        },
                        onCategoryChanged = { category, value ->
                            selectedPreset = DifficultyPreset.CUSTOM
                            skillProfile = skillProfile.withCategorySkill(category, value)
                        },
                    )
                }

                item { EditPlanPreview(plan, materialized) }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Apply and recovery", style = MaterialTheme.typography.titleMedium)
                            if (currentlyTuned) {
                                Text(
                                    "This exact character state matches a previous MUGEN AI Tuner change. To prevent accidental compounding, restore the previous state before applying a different preset.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Text(
                                    "Apply writes only the unambiguous changes shown above. Exact original bytes are backed up first under Documents/MUGEN AI Tuner/Backups.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Button(
                                onClick = {
                                    val uri = selectedTreeUri ?: return@Button
                                    val fingerprint = analyzedFingerprint ?: return@Button
                                    scope.launch {
                                        applying = true
                                        error = null
                                        actionMessage = null
                                        runCatching {
                                            CharacterEditTransaction.apply(
                                                context = context,
                                                treeUri = uri,
                                                characterName = result.characterName,
                                                analyzedFiles = analyzedFiles,
                                                analyzedFingerprint = fingerprint,
                                                materialized = materialized,
                                                reason = "Apply ${selectedPreset.label} at ${skillProfile.overallSkill}% overall skill",
                                            )
                                        }.onSuccess { transaction ->
                                            actionMessage = "Applied ${transaction.appliedEditCount} AI change(s) across ${transaction.changedFileCount} file(s). Backup: ${transaction.backupLocation}"
                                            runCatching { refreshFromDisk(uri, resetDifficulty = false) }
                                                .onFailure { error = it.message ?: "Edits were applied, but re-analysis failed." }
                                        }.onFailure { throwable ->
                                            error = throwable.message ?: "The AI changes could not be applied."
                                        }
                                        applying = false
                                    }
                                },
                                enabled = !applying && materialized.isSafeToApply && !currentlyTuned,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Apply safe changes")
                            }

                            if (currentSnapshot != null) {
                                OutlinedButton(
                                    onClick = {
                                        val uri = selectedTreeUri ?: return@OutlinedButton
                                        scope.launch {
                                            applying = true
                                            error = null
                                            actionMessage = null
                                            runCatching {
                                                BackupRestoreTransaction.undoLatest(
                                                    context = context,
                                                    treeUri = uri,
                                                    characterName = result.characterName,
                                                )
                                            }.onSuccess { restore ->
                                                actionMessage = "Restored ${restore.changedFileCount} file(s) from snapshot ${restore.restoredSnapshotId}. A safety backup of the state you just left was also created."
                                                runCatching { refreshFromDisk(uri, resetDifficulty = false) }
                                                    .onFailure { error = it.message ?: "Restore succeeded, but re-analysis failed." }
                                            }.onFailure { throwable ->
                                                error = throwable.message ?: "The previous AI state could not be restored."
                                            }
                                            applying = false
                                        }
                                    },
                                    enabled = !applying,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Undo last tuner change")
                                }
                                Text(
                                    "Matched snapshot: ${currentSnapshot.snapshotId} — ${currentSnapshot.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            if (result.aiFlags.isNotEmpty()) {
                item {
                    Text("Traced AI variables", style = MaterialTheme.typography.titleMedium)
                }
                items(result.aiFlags) { flag ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("var(${flag.variable}) — ${flag.confidence}", style = MaterialTheme.typography.titleSmall)
                            Text(flag.reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (result.behaviors.isNotEmpty()) {
                item {
                    Text("Detected AI behavior", style = MaterialTheme.typography.titleMedium)
                }
                items(result.behaviors) { behavior ->
                    BehaviorCard(behavior)
                }
            }

            if (result.notes.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Analyzer notes", style = MaterialTheme.typography.titleSmall)
                            result.notes.forEach { note -> Text("• $note") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyControls(
    selectedPreset: DifficultyPreset,
    profile: SkillProfile,
    availableCategories: List<BehaviorCategory>,
    onPresetSelected: (DifficultyPreset) -> Unit,
    onOverallChanged: (Int) -> Unit,
    onCategoryChanged: (BehaviorCategory, Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Target AI difficulty", style = MaterialTheme.typography.titleMedium)
            Text(
                "50% is the app's Normal target. Presets adjust all understood AI behavior together; Custom lets you tune detected categories individually.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetButton(DifficultyPreset.EASY, selectedPreset, Modifier.weight(1f), onPresetSelected)
                PresetButton(DifficultyPreset.NORMAL, selectedPreset, Modifier.weight(1f), onPresetSelected)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetButton(DifficultyPreset.HARD, selectedPreset, Modifier.weight(1f), onPresetSelected)
                PresetButton(DifficultyPreset.CUSTOM, selectedPreset, Modifier.weight(1f), onPresetSelected)
            }

            Text("Overall skill: ${profile.overallSkill}% — ${DifficultyTuning.labelFor(profile.overallSkill)}")
            Slider(
                value = profile.overallSkill.toFloat(),
                onValueChange = { onOverallChanged(it.toInt()) },
                valueRange = 0f..100f,
                steps = 99,
            )

            if (selectedPreset == DifficultyPreset.CUSTOM && availableCategories.isNotEmpty()) {
                Text("Custom behavior controls", style = MaterialTheme.typography.titleSmall)
                availableCategories.forEach { category ->
                    val value = profile.skillFor(category)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${categoryLabel(category)}: $value%")
                        Text(
                            DifficultyTuning.descriptionFor(category),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = value.toFloat(),
                            onValueChange = { onCategoryChanged(category, it.toInt()) },
                            valueRange = 0f..100f,
                            steps = 99,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    preset: DifficultyPreset,
    selected: DifficultyPreset,
    modifier: Modifier,
    onPresetSelected: (DifficultyPreset) -> Unit,
) {
    if (preset == selected) {
        Button(onClick = { onPresetSelected(preset) }, modifier = modifier) {
            Text(preset.label)
        }
    } else {
        OutlinedButton(onClick = { onPresetSelected(preset) }, modifier = modifier) {
            Text(preset.label)
        }
    }
}

@Composable
private fun EditPlanPreview(plan: EditPlan, materialized: MaterializedEditPlan) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Safe-change preview", style = MaterialTheme.typography.titleMedium)
            Text("High-confidence proposed edits: ${plan.edits.size}")
            Text("Verified writable edits: ${materialized.mutations.sumOf { it.appliedEdits.size }}")
            Text("AI behavior blocks intentionally left unchanged: ${plan.skippedBehaviorCount}")

            plan.edits.take(8).forEach { edit ->
                Text(
                    "${categoryLabel(edit.category)} • ${edit.originalExpression} → ${edit.replacementExpression}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (plan.edits.size > 8) {
                Text("+ ${plan.edits.size - 8} more proposed change(s)", style = MaterialTheme.typography.bodySmall)
            }
            plan.notes.forEach { note -> Text("• $note", style = MaterialTheme.typography.bodySmall) }
            materialized.errors.forEach { note ->
                Text("• Write protection: $note", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AnalysisSummary(result: CharacterAnalysis) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(result.characterName, style = MaterialTheme.typography.titleLarge)
            result.author?.let { Text("Author: $it") }
            Text("Custom AI detected: ${if (result.aiDetected) "Yes" else "No"}")
            Text("AI behavior blocks: ${result.aiBehaviorCount}")
            Text("Difficulty responsiveness: ${result.difficultyResponsiveness}")
            Text("Directly AILevel-scaled behaviors: ${result.directlyScaledBehaviorCount}")
        }
    }
}

@Composable
private fun BehaviorCard(behavior: AiBehavior) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(categoryLabel(behavior.category), style = MaterialTheme.typography.titleSmall)
            Text(behavior.summary)
            Text("Confidence: ${behavior.confidence}", style = MaterialTheme.typography.bodySmall)
            Text("${behavior.filePath}:${behavior.lineNumber} — [${behavior.section}]", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun categoryLabel(category: BehaviorCategory): String =
    category.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
