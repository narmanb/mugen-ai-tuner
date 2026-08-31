package com.narmanb.mugenaituner

import android.content.Context
import android.net.Uri
import com.narmanb.mugenaituner.core.BaselineRetuner
import com.narmanb.mugenaituner.core.CharacterFingerprinter
import com.narmanb.mugenaituner.core.MaterializedEditPlan

internal data class RestoreOriginalResult(
    val snapshotId: String,
    val changedFileCount: Int,
    val backupLocation: String,
    val traversedBackupCount: Int,
)

/** Restores the earliest verified pre-tuner state connected to the current character state. */
internal object OriginalRestoreTransaction {
    suspend fun restore(
        context: Context,
        treeUri: Uri,
        characterName: String,
    ): RestoreOriginalResult {
        val currentFiles = CharacterFolderReader.read(context, treeUri)
        check(currentFiles.isNotEmpty()) { "No supported character text files were found." }
        val currentFingerprint = CharacterFingerprinter.fingerprint(currentFiles)

        val baseline = BackupBaselineReconstructor.reconstruct(
            context = context,
            characterName = characterName,
            treeUri = treeUri,
            currentFiles = currentFiles,
            currentFingerprint = currentFingerprint,
        )
        check(baseline.historyDepth > 0) {
            "No verified earlier MUGEN AI Tuner state exists for this character."
        }

        val restorePlan = BaselineRetuner.retune(
            currentFiles = currentFiles,
            baselineFiles = baseline.files,
            desiredFromBaseline = MaterializedEditPlan(
                mutations = emptyList(),
                rejectedEdits = emptyList(),
                errors = emptyList(),
            ),
        )
        check(restorePlan.errors.isEmpty()) {
            restorePlan.errors.joinToString(" ")
        }
        check(restorePlan.mutations.isNotEmpty()) {
            "The character already matches its verified original pre-tuner state."
        }

        val transaction = CharacterEditTransaction.apply(
            context = context,
            treeUri = treeUri,
            characterName = characterName,
            analyzedFiles = currentFiles,
            analyzedFingerprint = currentFingerprint,
            materialized = restorePlan,
            reason = "Restore original pre-tuner state",
        )

        return RestoreOriginalResult(
            snapshotId = transaction.snapshotId,
            changedFileCount = transaction.changedFileCount,
            backupLocation = transaction.backupLocation,
            traversedBackupCount = baseline.historyDepth,
        )
    }
}
