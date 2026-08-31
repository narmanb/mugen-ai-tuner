package com.narmanb.mugenaituner

import android.content.Intent
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
import com.narmanb.mugenaituner.core.AiEditPlanner
import com.narmanb.mugenaituner.core.BehaviorCategory
import com.narmanb.mugenaituner.core.CharacterAnalysis
import com.narmanb.mugenaituner.core.DifficultyPreset
import com.narmanb.mugenaituner.core.DifficultyTuning
import com.narmanb.mugenaituner.core.EditPlan
import com.narmanb.mugenaituner.core.MugenAiAnalyzer
import com.narmanb.mugenaituner.core.SkillProfile
import kotlinx.coroutines.launch

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
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPreset by remember { mutableStateOf(DifficultyPreset.NORMAL) }
    var skillProfile by remember { mutableStateOf(SkillProfile.fromPreset(DifficultyPreset.NORMAL)) }

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
            analysis = null
            runCatching {
                CharacterFolderReader.read(context, uri)
            }.onSuccess { files ->
                if (files.isEmpty()) {
                    error = "No supported .def, .cmd, .cns, .st, .states, or .zss files were found in that folder."
                } else {
                    analysis = MugenAiAnalyzer.analyze(files)
                    selectedPreset = DifficultyPreset.NORMAL
                    skillProfile = SkillProfile.fromPreset(DifficultyPreset.NORMAL)
                }
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
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (analysis == null) "Select character folder" else "Analyze another character")
            }
        }

        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Analyzing character files…", modifier = Modifier.padding(start = 12.dp))
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

        analysis?.let { result ->
            item { AnalysisSummary(result) }

            if (result.aiDetected) {
                val availableCategories = DifficultyTuning.adjustableCategories.filter { category ->
                    result.behaviors.any { it.category == category }
                }
                val plan = AiEditPlanner.plan(result, skillProfile)

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

                item { EditPlanPreview(plan) }
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

            item {
                Text(
                    "Preview is analysis-only for now. Apply, versioned backups, undo, and restore will be enabled only after the file-write path is protected by backup verification.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
private fun EditPlanPreview(plan: EditPlan) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Safe-change preview", style = MaterialTheme.typography.titleMedium)
            Text("High-confidence automatic edits found: ${plan.edits.size}")
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
