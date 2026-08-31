package com.narmanb.mugenaituner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.narmanb.mugenaituner.core.SourceFile
import com.narmanb.mugenaituner.core.SourceGraphResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CharacterFolderReader {
    private val supportedExtensions = setOf("def", "cmd", "cns", "st", "states", "zss")
    private const val maxFiles = 400
    private const val maxTextFileBytes = 4L * 1024L * 1024L

    suspend fun read(context: Context, treeUri: Uri): List<SourceFile> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Android could not open the selected folder.")
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
        SourceGraphResolver.resolve(files).reachableFiles
    }
}
