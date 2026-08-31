package com.narmanb.mugenaituner.core

data class BackupFileRecord(
    val relativePath: String,
    val sha256Before: String,
    val sha256After: String?,
)

data class BackupSnapshot(
    val schemaVersion: Int = 1,
    val characterName: String,
    val snapshotId: String,
    val createdAtEpochMillis: Long,
    val reason: String,
    val files: List<BackupFileRecord>,
    val originalCharacterFingerprint: CharacterFingerprint,
)

data class BackupHistory(
    val characterName: String,
    val originalSnapshotId: String?,
    val snapshots: List<BackupSnapshot>,
) {
    val latest: BackupSnapshot? get() = snapshots.maxByOrNull { it.createdAtEpochMillis }

    fun withSnapshot(snapshot: BackupSnapshot): BackupHistory = copy(
        originalSnapshotId = originalSnapshotId ?: snapshot.snapshotId,
        snapshots = (snapshots + snapshot).sortedBy { it.createdAtEpochMillis },
    )
}

object BackupNaming {
    const val rootFolderName: String = "MUGEN AI Tuner"
    const val backupsFolderName: String = "Backups"

    fun safeCharacterFolderName(characterName: String): String {
        val cleaned = characterName
            .replace(Regex("""[\\/:*?\"<>|]"""), "_")
            .trim()
            .take(80)
        return cleaned.ifBlank { "Unknown Character" }
    }
}
