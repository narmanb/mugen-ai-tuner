package com.narmanb.mugenaituner

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun CharacterNotesCard(treeUri: Uri, characterName: String) {
    val context = LocalContext.current
    var note by remember(treeUri, characterName) {
        mutableStateOf(CharacterNotesStore.load(context, treeUri, characterName))
    }
    var savedMessage by remember(treeUri, characterName) { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Character notes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Private notes stored by MUGEN AI Tuner. They do not modify the character files.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it.take(8000)
                    savedMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 3,
                maxLines = 8,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        CharacterNotesStore.save(context, treeUri, characterName, note)
                        savedMessage = "Saved"
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save notes")
                }
                OutlinedButton(
                    onClick = {
                        CharacterNotesStore.clear(context, treeUri, characterName)
                        note = ""
                        savedMessage = "Cleared"
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Clear")
                }
            }
            savedMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
