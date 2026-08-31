package com.narmanb.mugenaituner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.narmanb.mugenaituner.core.MugenAiAnalyzer
import com.narmanb.mugenaituner.core.RosterAnalysis
import com.narmanb.mugenaituner.core.RosterAnalysisSummary
import com.narmanb.mugenaituner.core.RosterSkipReason
import com.narmanb.mugenaituner.core.RosterSkippedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object RosterFolderScanner {
    private const val maxCharacterFolders = 1200

    /**
     * Scans immediate subdirectories of a MUGEN/IKEMEN chars folder. This is analysis-only:
     * no character or backup file is modified.
     */
    suspend fun scan(context: Context, charsTreeUri: Uri): RosterAnalysisSummary = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, charsTreeUri)
            ?: error("Android could not open the selected chars folder.")
        require(root.isDirectory) { "The selected item is not a folder." }

        val summaries = mutableListOf<com.narmanb.mugenaituner.core.RosterCharacterSummary>()
        val skipped = mutableListOf<RosterSkippedFolder>()
        val characterDirectories = root.listFiles()
            .asSequence()
            .filter { it.isDirectory }
            .take(maxCharacterFolders)
            .toList()

        characterDirectories.forEach { directory ->
            val folderName = directory.name ?: return@forEach
            runCatching {
                val files = CharacterFolderReader.readDirectory(context, directory)
                require(files.any { it.path.endsWith(".def", ignoreCase = true) }) {
                    "No reachable character DEF was found."
                }
                val analysis = MugenAiAnalyzer.analyze(files)
                RosterAnalysis.summarize(folderName, analysis)
            }.onSuccess { summaries += it }
                .onFailure { throwable -> skipped += classifySkip(folderName, throwable) }
        }

        val sortedSkipped = skipped.sortedBy { it.folderName.lowercase() }
        RosterAnalysisSummary(
            characters = summaries.sortedWith(
                compareByDescending<com.narmanb.mugenaituner.core.RosterCharacterSummary> { it.estimatedStrength ?: -1 }
                    .thenBy { it.characterName.lowercase() },
            ),
            skippedFolders = sortedSkipped.map { it.folderName },
            skippedDetails = sortedSkipped,
        )
    }

    private fun classifySkip(folderName: String, throwable: Throwable): RosterSkippedFolder {
        val detail = throwable.message?.takeIf { it.isNotBlank() } ?: throwable::class.simpleName.orEmpty()
        val reason = when {
            throwable is AmbiguousCharacterDefException -> RosterSkipReason.NEEDS_DEF_SELECTION
            throwable is SecurityException || throwable is IOException -> RosterSkipReason.UNREADABLE
            detail.contains("No reachable character DEF", ignoreCase = true) -> RosterSkipReason.NO_DEF
            detail.contains("could not open", ignoreCase = true) ||
                detail.contains("permission", ignoreCase = true) ||
                detail.contains("read", ignoreCase = true) -> RosterSkipReason.UNREADABLE
            else -> RosterSkipReason.ANALYSIS_ERROR
        }
        return RosterSkippedFolder(
            folderName = folderName,
            reason = reason,
            detail = detail,
        )
    }
}
