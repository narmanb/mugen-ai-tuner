package com.narmanb.mugenaituner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.narmanb.mugenaituner.core.BackupNaming
import com.narmanb.mugenaituner.core.CharacterFingerprint
import com.narmanb.mugenaituner.core.FileFingerprint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class StoredBackupEntry(
    val relativePath: String,
    val backupFileName: String,
    val sha256BeforeBytes: String,
    val sha256BeforeText: String,
    val sha256AfterText: String,
)

internal data class StoredBackupSnapshot(
    val snapshotId: String,
    val characterName: String,
    val createdAtEpochMillis: Long,
    val reason: String,
    val status: String,
    val sourceTreeUri: String,
    val beforeFingerprint: CharacterFingerprint,
    val afterFingerprint: CharacterFingerprint,
    val files: List<StoredBackupEntry>,
    val storageRelativePath: String?,
    val storageDirectory: File?,
)

/** Discovers snapshots written by [BackupStore] so changes can be safely undone later. */
internal object BackupHistoryStore {
    fun listAppliedSnapshots(
        context: Context,
        characterName: String,
        treeUri: Uri,
    ): List<StoredBackupSnapshot> {
        val safeCharacter = BackupNaming.safeCharacterFolderName(characterName)
        val snapshots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStoreManifests(context, safeCharacter)
        } else {
            queryLegacyManifests(context, safeCharacter)
        }

        return snapshots
            .filter { it.status == "applied" && it.sourceTreeUri == treeUri.toString() }
            .sortedBy { it.createdAtEpochMillis }
    }

    fun latestSnapshotEndingAt(
        context: Context,
        characterName: String,
        treeUri: Uri,
        fingerprint: CharacterFingerprint,
    ): StoredBackupSnapshot? = listAppliedSnapshots(context, characterName, treeUri)
        .lastOrNull { !it.afterFingerprint.differsFrom(fingerprint) }

    fun readOriginalBytes(
        context: Context,
        snapshot: StoredBackupSnapshot,
        entry: StoredBackupEntry,
    ): ByteArray {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = snapshot.storageRelativePath
                ?: error("Backup snapshot has no MediaStore path.")
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val args = arrayOf(relativePath, entry.backupFileName)
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                if (!cursor.moveToFirst()) error("Backup file '${entry.backupFileName}' is missing.")
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                return resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read backup file '${entry.backupFileName}'.")
            }
            error("Backup file '${entry.backupFileName}' is missing.")
        }

        val directory = snapshot.storageDirectory ?: error("Backup snapshot directory is unavailable.")
        val file = File(directory, entry.backupFileName)
        check(file.isFile) { "Backup file '${entry.backupFileName}' is missing." }
        return file.readBytes()
    }

    private fun queryMediaStoreManifests(context: Context, safeCharacter: String): List<StoredBackupSnapshot> {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val root = "${Environment.DIRECTORY_DOCUMENTS}/${BackupNaming.rootFolderName}/${BackupNaming.backupsFolderName}/$safeCharacter/"
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("backup.json", "$root%")
        val results = mutableListOf<StoredBackupSnapshot>()

        resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                val path = cursor.getString(pathColumn)
                val text = runCatching {
                    resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull() ?: continue
                parseManifest(text, storageRelativePath = path, storageDirectory = null)?.let(results::add)
            }
        }
        return results
    }

    private fun queryLegacyManifests(context: Context, safeCharacter: String): List<StoredBackupSnapshot> {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return emptyList()
        val characterDirectory = File(
            root,
            "${BackupNaming.rootFolderName}/${BackupNaming.backupsFolderName}/$safeCharacter",
        )
        if (!characterDirectory.isDirectory) return emptyList()

        return characterDirectory.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { snapshotDirectory ->
                val manifest = File(snapshotDirectory, "backup.json")
                if (!manifest.isFile) return@mapNotNull null
                runCatching {
                    parseManifest(
                        manifest.readText(Charsets.UTF_8),
                        storageRelativePath = null,
                        storageDirectory = snapshotDirectory,
                    )
                }.getOrNull()
            }
    }

    private fun parseManifest(
        text: String,
        storageRelativePath: String?,
        storageDirectory: File?,
    ): StoredBackupSnapshot? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (json.optInt("schemaVersion", 0) < 2) return null

        val filesJson = json.optJSONArray("files") ?: JSONArray()
        val files = buildList {
            for (index in 0 until filesJson.length()) {
                val entry = filesJson.optJSONObject(index) ?: continue
                add(
                    StoredBackupEntry(
                        relativePath = entry.optString("relativePath"),
                        backupFileName = entry.optString("backupFileName"),
                        sha256BeforeBytes = entry.optString("sha256BeforeBytes"),
                        sha256BeforeText = entry.optString("sha256BeforeText"),
                        sha256AfterText = entry.optString("sha256AfterText"),
                    ),
                )
            }
        }

        return StoredBackupSnapshot(
            snapshotId = json.optString("snapshotId"),
            characterName = json.optString("characterName"),
            createdAtEpochMillis = json.optLong("createdAtEpochMillis"),
            reason = json.optString("reason"),
            status = json.optString("status"),
            sourceTreeUri = json.optString("sourceTreeUri"),
            beforeFingerprint = parseFingerprint(json.optJSONArray("beforeCharacterFingerprint")),
            afterFingerprint = parseFingerprint(json.optJSONArray("afterCharacterFingerprint")),
            files = files,
            storageRelativePath = storageRelativePath,
            storageDirectory = storageDirectory,
        )
    }

    private fun parseFingerprint(array: JSONArray?): CharacterFingerprint {
        if (array == null) return CharacterFingerprint(emptyList())
        val files = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(FileFingerprint(item.optString("path"), item.optString("sha256")))
            }
        }
        return CharacterFingerprint(files)
    }
}
