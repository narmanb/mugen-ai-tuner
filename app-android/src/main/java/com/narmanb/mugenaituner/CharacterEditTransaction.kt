package com.narmanb.mugenaituner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.narmanb.mugenaituner.core.CharacterFingerprint
import com.narmanb.mugenaituner.core.CharacterFingerprinter
import com.narmanb.mugenaituner.core.CharacterHealthValidator
import com.narmanb.mugenaituner.core.FileMutation
import com.narmanb.mugenaituner.core.MaterializedEditPlan
import com.narmanb.mugenaituner.core.SourceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

internal data class ApplyTransactionResult(
    val snapshotId: String,
    val changedFileCount: Int,
    val appliedEditCount: Int,
    val backupLocation: String,
    val resultingFingerprint: CharacterFingerprint,
)

private data class WritableTarget(
    val mutation: FileMutation,
    val document: DocumentFile,
    val originalBytes: ByteArray,
    val charset: Charset,
)

/**
 * Performs a character edit as a guarded transaction:
 * 1. re-read the active character source graph and reject outside changes;
 * 2. run before/after structural health validation;
 * 3. verify every target file still exactly matches the analyzed text;
 * 4. save exact original bytes in a versioned backup;
 * 5. write and verify every changed file;
 * 6. verify the complete post-write fingerprint;
 * 7. roll all touched files back if any write or verification fails.
 */
internal object CharacterEditTransaction {
    suspend fun apply(
        context: Context,
        treeUri: Uri,
        characterName: String,
        analyzedFiles: List<SourceFile>,
        analyzedFingerprint: CharacterFingerprint,
        materialized: MaterializedEditPlan,
        reason: String,
    ): ApplyTransactionResult = withContext(Dispatchers.IO) {
        require(materialized.isSafeToApply) {
            "The edit plan is not safe to apply. Resolve ambiguous proposed changes first."
        }

        val freshFiles = CharacterFolderReader.read(context, treeUri)
        val freshFingerprint = CharacterFingerprinter.fingerprint(freshFiles)
        check(!analyzedFingerprint.differsFrom(freshFingerprint)) {
            "Character files changed after analysis. Re-analyze before applying any edits."
        }

        check(!analyzedFingerprint.differsFrom(CharacterFingerprinter.fingerprint(analyzedFiles))) {
            "The in-memory analysis snapshot no longer matches its fingerprint."
        }

        val mutationByPath = materialized.mutations.associateBy { it.relativePath }
        val resultingFiles = freshFiles.map { file ->
            mutationByPath[file.path]?.let { mutation -> file.copy(content = mutation.afterContent) } ?: file
        }
        val resultingFingerprint = CharacterFingerprinter.fingerprint(resultingFiles)

        val beforeHealth = CharacterHealthValidator.validate(freshFiles)
        val afterHealth = CharacterHealthValidator.validate(resultingFiles)
        val introducedErrors = CharacterHealthValidator.introducedErrors(beforeHealth, afterHealth)
        check(introducedErrors.isEmpty()) {
            val summary = introducedErrors.take(3).joinToString("; ") { issue ->
                val location = issue.lineNumber?.let { ":$it" }.orEmpty()
                "${issue.filePath}$location: ${issue.message}"
            }
            "Pre-write health check found a structural error introduced by the proposed edit. Nothing was changed. $summary"
        }

        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Android could not reopen the selected character folder.")
        check(root.isDirectory) { "The selected character location is no longer a folder." }

        val targets = materialized.mutations.map { mutation ->
            val document = findRelativeFile(root, mutation.relativePath)
                ?: error("Could not find '${mutation.relativePath}' in the selected character folder.")
            check(document.isFile && document.canWrite()) {
                "'${mutation.relativePath}' is not writable."
            }

            val bytes = context.contentResolver.openInputStream(document.uri)?.use { it.readBytes() }
                ?: error("Could not read '${mutation.relativePath}' before writing.")
            val decoded = CharacterTextCodec.decode(bytes)
            check(decoded.text == mutation.beforeContent) {
                "'${mutation.relativePath}' no longer matches the analyzed version. Re-analyze first."
            }

            WritableTarget(
                mutation = mutation,
                document = document,
                originalBytes = bytes,
                charset = decoded.charset,
            )
        }

        val prepared = BackupStore.prepareSnapshot(
            context = context,
            characterName = characterName,
            sourceTreeUri = treeUri,
            originalFingerprint = analyzedFingerprint,
            resultingFingerprint = resultingFingerprint,
            mutations = materialized.mutations,
            originals = targets.map { OriginalFileBytes(it.mutation.relativePath, it.originalBytes) },
            reason = reason,
        )

        var transactionFailure: Throwable? = null
        try {
            targets.forEach { target ->
                val outputBytes = CharacterTextCodec.encode(target.mutation.afterContent, target.charset)
                context.contentResolver.openOutputStream(target.document.uri, "wt")?.use { output ->
                    output.write(outputBytes)
                    output.flush()
                } ?: error("Could not open '${target.mutation.relativePath}' for writing.")

                val verifyBytes = context.contentResolver.openInputStream(target.document.uri)?.use { it.readBytes() }
                    ?: error("Could not verify '${target.mutation.relativePath}' after writing.")
                val verified = CharacterTextCodec.decode(verifyBytes)
                check(verified.text == target.mutation.afterContent) {
                    "Verification failed after writing '${target.mutation.relativePath}'."
                }
            }

            val postWriteFiles = CharacterFolderReader.read(context, treeUri)
            val postWriteFingerprint = CharacterFingerprinter.fingerprint(postWriteFiles)
            check(!postWriteFingerprint.differsFrom(resultingFingerprint)) {
                "The complete character fingerprint did not match the planned result after writing. The transaction was rolled back."
            }
            val postWriteHealth = CharacterHealthValidator.validate(postWriteFiles)
            check(CharacterHealthValidator.introducedErrors(beforeHealth, postWriteHealth).isEmpty()) {
                "Post-write health verification found a newly introduced structural error. The transaction was rolled back."
            }

            BackupStore.markStatus(context, prepared, "applied")
        } catch (throwable: Throwable) {
            transactionFailure = throwable
            targets.forEach { target ->
                runCatching {
                    context.contentResolver.openOutputStream(target.document.uri, "wt")?.use { output ->
                        output.write(target.originalBytes)
                        output.flush()
                    } ?: error("Could not reopen '${target.mutation.relativePath}' during rollback.")
                }
            }
            runCatching { BackupStore.markStatus(context, prepared, "rolled_back") }
        }

        transactionFailure?.let { throw it }

        ApplyTransactionResult(
            snapshotId = prepared.snapshotId,
            changedFileCount = targets.size,
            appliedEditCount = targets.sumOf { it.mutation.appliedEdits.size },
            backupLocation = prepared.publicLocation,
            resultingFingerprint = resultingFingerprint,
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
