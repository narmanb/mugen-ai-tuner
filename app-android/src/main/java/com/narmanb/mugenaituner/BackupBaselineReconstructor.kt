package com.narmanb.mugenaituner

import android.content.Context
import android.net.Uri
import com.narmanb.mugenaituner.core.CharacterFingerprint
import com.narmanb.mugenaituner.core.CharacterFingerprinter
import com.narmanb.mugenaituner.core.SourceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BaselineReconstruction(
    val files: List<SourceFile>,
    val fingerprint: CharacterFingerprint,
    val snapshotsTraversed: List<String>,
) {
    val historyDepth: Int get() = snapshotsTraversed.size
}

/**
 * Walks verified backup fingerprints backwards to reconstruct the character state that existed
 * before the first connected tuner change. This allows changing Normal -> Easy -> Hard directly
 * without repeatedly scaling already-modified Random thresholds.
 */
internal object BackupBaselineReconstructor {
    suspend fun reconstruct(
        context: Context,
        characterName: String,
        treeUri: Uri,
        currentFiles: List<SourceFile>,
        currentFingerprint: CharacterFingerprint,
    ): BaselineReconstruction = withContext(Dispatchers.IO) {
        val history = BackupHistoryStore.listAppliedSnapshots(context, characterName, treeUri)
        if (history.isEmpty()) {
            return@withContext BaselineReconstruction(currentFiles, currentFingerprint, emptyList())
        }

        var workingFiles = currentFiles
        var workingFingerprint = currentFingerprint
        var cursor = history.lastIndex
        val traversed = mutableListOf<String>()

        while (cursor >= 0) {
            var matchIndex = -1
            for (index in cursor downTo 0) {
                if (!history[index].afterFingerprint.differsFrom(workingFingerprint)) {
                    matchIndex = index
                    break
                }
            }
            if (matchIndex < 0) break

            val snapshot = history[matchIndex]
            val replacements = snapshot.files.associate { entry ->
                val bytes = BackupHistoryStore.readOriginalBytes(context, snapshot, entry)
                entry.relativePath to CharacterTextCodec.decode(bytes).text
            }

            workingFiles = workingFiles.map { file ->
                replacements[file.path]?.let { original -> file.copy(content = original) } ?: file
            }
            val reconstructedFingerprint = CharacterFingerprinter.fingerprint(workingFiles)
            check(!reconstructedFingerprint.differsFrom(snapshot.beforeFingerprint)) {
                "Backup chain verification failed at snapshot ${snapshot.snapshotId}. Automatic retuning was disabled to protect the character."
            }

            workingFingerprint = reconstructedFingerprint
            traversed += snapshot.snapshotId
            cursor = matchIndex - 1
        }

        BaselineReconstruction(
            files = workingFiles,
            fingerprint = workingFingerprint,
            snapshotsTraversed = traversed,
        )
    }
}
