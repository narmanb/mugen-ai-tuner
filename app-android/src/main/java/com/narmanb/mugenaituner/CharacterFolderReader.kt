package com.narmanb.mugenaituner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.narmanb.mugenaituner.core.CharacterCodeFileTypes
import com.narmanb.mugenaituner.core.SourceFile
import com.narmanb.mugenaituner.core.SourceGraphResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AmbiguousCharacterDefException(
    val defPaths: List<String>,
) : IllegalStateException(
    "This character folder contains multiple possible active DEF files: ${defPaths.joinToString()}. Choose the active DEF before analysis so alternate modes are not mixed together.",
)

data class CharacterFolderReadResult(
    val files: List<SourceFile>,
    val unresolvedReferences: List<String>,
    val ignoredTextFileCount: Int,
    val activeDefPath: String?,
)

object CharacterFolderReader {
    private val supportedExtensions = CharacterCodeFileTypes.supportedExtensions
    private const val maxFiles = 400
    private const val maxTextFileBytes = 4L * 1024L * 1024L

    suspend fun read(
        context: Context,
        treeUri: Uri,
        activeDefPath: String? = null,
    ): List<SourceFile> = readDetailed(context, treeUri, activeDefPath).files

    suspend fun readDetailed(
        context: Context,
        treeUri: Uri,
        activeDefPath: String? = null,
    ): CharacterFolderReadResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Android could not open the selected folder.")
        require(root.isDirectory) { "The selected item is not a folder." }
        readDirectoryDetailed(context, root, activeDefPath)
    }

    /** Reads one character directory. Call from an IO dispatcher. */
    internal fun readDirectory(
        context: Context,
        root: DocumentFile,
        activeDefPath: String? = null,
    ): List<SourceFile> = readDirectoryDetailed(context, root, activeDefPath).files

    /** Reads one character directory plus source-graph completeness diagnostics. */
    internal fun readDirectoryDetailed(
        context: Context,
        root: DocumentFile,
        activeDefPath: String? = null,
    ): CharacterFolderReadResult {
        require(root.isDirectory) { "The selected item is not a folder." }
        val files = mutableListOf<SourceFile>()

        fun visit(directory: DocumentFile, relativePath: String) {
            if (files.size >= maxFiles) return
            directory.listFiles().forEach { child ->
                if (files.size >= maxFiles) return@forEach
                val name = child.name ?: return@forEach
                val childPath = if (relativePath.isBlank()) name else "$relativePath/$name"

                if (child.isDirectory) {
                    visit(child, childPath)
                    return@forEach
                }
                if (!child.isFile) return@forEach

                val extension = name.substringAfterLast('.', "").lowercase()
                if (extension !in supportedExtensions) return@forEach
                val length = child.length()
                if (length > maxTextFileBytes) return@forEach

                val bytes = context.contentResolver.openInputStream(child.uri)?.use { it.readBytes() }
                    ?: return@forEach
                val decoded = CharacterTextCodec.decode(bytes)
                files += SourceFile(childPath, decoded.text)
            }
        }

        visit(root, "")

        val defFiles = files.filter { it.path.endsWith(".def", ignoreCase = true) }
        if (defFiles.isEmpty()) {
            return CharacterFolderReadResult(
                files = files,
                unresolvedReferences = emptyList(),
                ignoredTextFileCount = 0,
                activeDefPath = null,
            )
        }

        val selectedDef = when {
            activeDefPath != null -> defFiles.firstOrNull { it.path.equals(activeDefPath, ignoreCase = true) }
                ?: error("Selected active DEF '$activeDefPath' no longer exists in this character folder.")
            defFiles.size == 1 -> defFiles.single()
            else -> {
                val folderName = root.name.orEmpty()
                val folderMatches = defFiles.filter { def ->
                    def.path.substringAfterLast('/').substringBeforeLast('.').equals(folderName, ignoreCase = true)
                }
                when (folderMatches.size) {
                    1 -> folderMatches.single()
                    else -> throw AmbiguousCharacterDefException(defFiles.map { it.path }.sortedBy { it.lowercase() })
                }
            }
        }

        val graph = SourceGraphResolver.resolveFromDef(files, selectedDef.path)
        return CharacterFolderReadResult(
            files = graph.reachableFiles,
            unresolvedReferences = graph.unresolvedReferences,
            ignoredTextFileCount = graph.ignoredTextFiles.size,
            activeDefPath = selectedDef.path,
        )
    }
}
