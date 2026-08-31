package com.narmanb.mugenaituner

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.narmanb.mugenaituner.core.BackupNaming
import com.narmanb.mugenaituner.core.CharacterFingerprint
import com.narmanb.mugenaituner.core.FileMutation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal data class OriginalFileBytes(
    val relativePath: String,
    val bytes: ByteArray,
)

internal data class PreparedBackupSnapshot(
    val snapshotId: String,
    val manifestUri: Uri?,
    val manifestFile: File?,
    val manifestJson: String,
    val publicLocation: String,
)

/** Stores exact pre-edit bytes outside the character folder before any write is attempted. */
internal object BackupStore {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneOffset.UTC)

    fun prepareSnapshot(
        context: Context,
        characterName: String,
        sourceTreeUri: Uri,
        originalFingerprint: CharacterFingerprint,
        resultingFingerprint: CharacterFingerprint,
        mutations: List<FileMutation>,
        originals: List<OriginalFileBytes>,
        reason: String,
    ): PreparedBackupSnapshot {
        require(mutations.isNotEmpty()) { "A backup snapshot requires at least one modified file." }
        require(mutations.map { it.relativePath }.toSet() == originals.map { it.relativePath }.toSet()) {
            "Backup input does not match the files scheduled for modification."
        }

        val safeCharacter = BackupNaming.safeCharacterFolderName(characterName)
        val snapshotId = timestampFormatter.format(Instant.now())
        val rootRelative = "${Environment.DIRECTORY_DOCUMENTS}/${BackupNaming.rootFolderName}/${BackupNaming.backupsFolderName}/$safeCharacter/$snapshotId/"
        val originalByPath = originals.associateBy { it.relativePath }

        val entries = JSONArray()
        mutations.forEachIndexed { index, mutation ->
            val original = originalByPath.getValue(mutation.relativePath)
            val backupName = "%03d_%s.original".format(
                index + 1,
                safeFileName(mutation.relativePath.substringAfterLast('/')),
            )
            writeBackupBytes(context, rootRelative, backupName, original.bytes)

            entries.put(
                JSONObject()
                    .put("relativePath", mutation.relativePath)
                    .put("backupFileName", backupName)
                    .put("sha256BeforeBytes", sha256(original.bytes))
                    .put("sha256BeforeText", sha256(mutation.beforeContent.toByteArray(Charsets.UTF_8)))
                    .put("sha256AfterText", sha256(mutation.afterContent.toByteArray(Charsets.UTF_8)))
                    .put("editCount", mutation.appliedEdits.size),
            )
        }

        val manifest = JSONObject()
            .put("schemaVersion", 2)
            .put("status", "prepared")
            .put("characterName", characterName)
            .put("snapshotId", snapshotId)
            .put("createdAtEpochMillis", System.currentTimeMillis())
            .put("reason", reason)
            .put("sourceTreeUri", sourceTreeUri.toString())
            .put("beforeCharacterFingerprint", fingerprintJson(originalFingerprint))
            .put("afterCharacterFingerprint", fingerprintJson(resultingFingerprint))
            .put("files", entries)

        val manifestText = manifest.toString(2)
        val target = writeManifest(context, rootRelative, manifestText)

        return PreparedBackupSnapshot(
            snapshotId = snapshotId,
            manifestUri = target.first,
            manifestFile = target.second,
            manifestJson = manifestText,
            publicLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                rootRelative
            } else {
                target.second?.parent ?: rootRelative
            },
        )
    }

    fun markStatus(context: Context, prepared: PreparedBackupSnapshot, status: String) {
        val updated = JSONObject(prepared.manifestJson)
            .put("status", status)
            .put("statusUpdatedAtEpochMillis", System.currentTimeMillis())
            .toString(2)

        when {
            prepared.manifestUri != null -> {
                context.contentResolver.openOutputStream(prepared.manifestUri, "wt")?.use { output ->
                    output.write(updated.toByteArray(Charsets.UTF_8))
                } ?: error("Could not update backup manifest status.")
            }
            prepared.manifestFile != null -> prepared.manifestFile.writeText(updated, Charsets.UTF_8)
            else -> error("Backup manifest has no writable destination.")
        }
    }

    private fun fingerprintJson(fingerprint: CharacterFingerprint): JSONArray = JSONArray().also { array ->
        fingerprint.files.forEach { file ->
            array.put(
                JSONObject()
                    .put("path", file.path)
                    .put("sha256", file.sha256),
            )
        }
    }

    private fun writeBackupBytes(context: Context, relativePath: String, displayName: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: error("Android could not create backup file $displayName.")
            try {
                resolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error("Android could not open backup file $displayName for writing.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (throwable: Throwable) {
                resolver.delete(uri, null, null)
                throw throwable
            }
        } else {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: error("External Documents storage is unavailable.")
            val directory = File(root, relativePath.substringAfter("${Environment.DIRECTORY_DOCUMENTS}/"))
            check(directory.mkdirs() || directory.isDirectory) { "Could not create backup directory." }
            File(directory, displayName).writeBytes(bytes)
        }
    }

    private fun writeManifest(context: Context, relativePath: String, text: String): Pair<Uri?, File?> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "backup.json")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: error("Android could not create the backup manifest.")
            try {
                resolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error("Android could not write the backup manifest.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri to null
            } catch (throwable: Throwable) {
                resolver.delete(uri, null, null)
                throw throwable
            }
        }

        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: error("External Documents storage is unavailable.")
        val directory = File(root, relativePath.substringAfter("${Environment.DIRECTORY_DOCUMENTS}/"))
        check(directory.mkdirs() || directory.isDirectory) { "Could not create backup directory." }
        val file = File(directory, "backup.json")
        file.writeText(text, Charsets.UTF_8)
        return null to file
    }

    private fun safeFileName(name: String): String = name
        .replace(Regex("""[\\/:*?\"<>|]"""), "_")
        .take(90)
        .ifBlank { "character-file" }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
