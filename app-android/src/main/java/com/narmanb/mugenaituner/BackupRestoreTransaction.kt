package com.narmanb.mugenaituner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.narmanb.mugenaituner.core.CharacterFingerprinter
import com.narmanb.mugenaituner.core.FileMutation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class RestoreTransactionResult(
    val restoredSnapshotId: String,
    val safetySnapshotId: String,
    val changedFileCount: Int,
    val backupLocation: String,
)

private data class RestoreTarget(
    val entry: StoredBackupEntry,
    val document: DocumentFile,
    val currentBytes: ByteArray,
    val currentText: String,
    val restoreBytes: ByteArray,
    val restoreText: String,
)

/** Restores the exact files from the latest snapshot whose after-state matches the character. */
internal object BackupRestoreTransaction {
    suspend fun undoLatest(
        context: Context,
        treeUri: Uri,
        characterName: String,
    ): RestoreTransactionResult = withContext(Dispatchers.IO) {
        val currentFiles = CharacterFolderReader.read(context, treeUri)
        val currentFingerprint = CharacterFingerprinter.fingerprint(currentFiles)
        val snapshot = BackupHistoryStore.latestSnapshotEndingAt(
            context = context,
            characterName = characterName,
            treeUri = treeUri,
            fingerprint = currentFingerprint,
        ) ?: error("No verified MUGEN AI Tuner backup matches the character's current state.")

        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Android could not reopen the selected character folder.")

        val targets = snapshot.files.map { entry ->
            val document = findRelativeFile(root, entry.relativePath)
                ?: error("Could not find '${entry.relativePath}' while preparing restore.")
            check(document.isFile && document.canWrite()) { "'${entry.relativePath}' is not writable." }

            val currentBytes = context.contentResolver.openInputStream(document.uri)?.use { it.readBytes() }
                ?: error("Could not read '${entry.relativePath}' before restore.")
            val currentText = CharacterTextCodec.decode(currentBytes).text
            val restoreBytes = BackupHistoryStore.readOriginalBytes(context, snapshot, entry)
            val restoreText = CharacterTextCodec.decode(restoreBytes).text

            RestoreTarget(
                entry = entry,
                document = document,
                currentBytes = currentBytes,
                currentText = currentText,
                restoreBytes = restoreBytes,
                restoreText = restoreText,
            )
        }

        val mutationByPath = targets.associate { target ->
            target.entry.relativePath to FileMutation(
                relativePath = target.entry.relativePath,
                beforeContent = target.currentText,
                afterContent = target.restoreText,
                appliedEdits = emptyList(),
            )
        }
        val mutations = mutationByPath.values.toList()
        val resultingFiles = currentFiles.map { file ->
            mutationByPath[file.path]?.let { mutation -> file.copy(content = mutation.afterContent) } ?: file
        }
        val resultingFingerprint = CharacterFingerprinter.fingerprint(resultingFiles)

        check(!resultingFingerprint.differsFrom(snapshot.beforeFingerprint)) {
            "Backup contents do not reconstruct the expected pre-change character state. Restore was cancelled."
        }

        val safetyBackup = BackupStore.prepareSnapshot(
            context = context,
            characterName = characterName,
            sourceTreeUri = treeUri,
            originalFingerprint = currentFingerprint,
            resultingFingerprint = resultingFingerprint,
            mutations = mutations,
            originals = targets.map { OriginalFileBytes(it.entry.relativePath, it.currentBytes) },
            reason = "Undo snapshot ${snapshot.snapshotId}",
        )

        var failure: Throwable? = null
        try {
            targets.forEach { target ->
                context.contentResolver.openOutputStream(target.document.uri, "wt")?.use { output ->
                    output.write(target.restoreBytes)
                    output.flush()
                } ?: error("Could not write restored '${target.entry.relativePath}'.")

                val verifiedBytes = context.contentResolver.openInputStream(target.document.uri)?.use { it.readBytes() }
                    ?: error("Could not verify restored '${target.entry.relativePath}'.")
                check(verifiedBytes.contentEquals(target.restoreBytes)) {
                    "Exact-byte verification failed while restoring '${target.entry.relativePath}'."
                }
            }
            BackupStore.markStatus(context, safetyBackup, "applied")
        } catch (throwable: Throwable) {
            failure = throwable
            targets.forEach { target ->
                runCatching {
                    context.contentResolver.openOutputStream(target.document.uri, "wt")?.use { output ->
                        output.write(target.currentBytes)
                        output.flush()
                    }
                }
            }
            runCatching { BackupStore.markStatus(context, safetyBackup, "rolled_back") }
        }

        failure?.let { throw it }

        RestoreTransactionResult(
            restoredSnapshotId = snapshot.snapshotId,
            safetySnapshotId = safetyBackup.snapshotId,
            changedFileCount = targets.size,
            backupLocation = safetyBackup.publicLocation,
        )
    }

    private fun findRelativeFile(root: DocumentFile, relativePath: String): DocumentFile? {
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        var current = root
        parts.dropLast(1).forEach { directoryName ->
            current = current.findFile(directoryName)?.takeIf { it.isDirectory } ?: return null
        }
        return current.findFile(parts.last())
    }
}
