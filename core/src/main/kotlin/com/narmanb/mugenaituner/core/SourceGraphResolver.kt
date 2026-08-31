package com.narmanb.mugenaituner.core

import java.util.ArrayDeque

data class SourceGraphResult(
    val reachableFiles: List<SourceFile>,
    val ignoredTextFiles: List<SourceFile>,
    val unresolvedReferences: List<String>,
    val defFiles: List<SourceFile>,
)

/**
 * Restricts analysis to text files reachable from the character DEF file(s). Character folders
 * often contain old/disabled AI patches, and scanning every text file can otherwise report code
 * that the character never loads.
 */
object SourceGraphResolver {
    private val supportedExtensions = setOf("def", "cmd", "cns", "st", "states", "zss")
    private val assignmentRegex = Regex("""^\s*([^=;]+?)\s*=\s*([^;]+)""")
    private val includeRegex = Regex("""(?i)^\s*#?include\s*(?:=\s*)?[\"']?([^\"';]+)[\"']?""")

    /** Conservatively starts from every DEF when the caller does not know which one is active. */
    fun resolve(files: List<SourceFile>): SourceGraphResult = resolveInternal(files, activeDefPath = null)

    /**
     * Starts from exactly one active DEF. This is the preferred mode when a character folder ships
     * alternate DEFs for different modes, patches, engines, or AI variants.
     */
    fun resolveFromDef(files: List<SourceFile>, activeDefPath: String): SourceGraphResult =
        resolveInternal(files, activeDefPath)

    private fun resolveInternal(files: List<SourceFile>, activeDefPath: String?): SourceGraphResult {
        if (files.isEmpty()) {
            return SourceGraphResult(emptyList(), emptyList(), emptyList(), emptyList())
        }

        val byNormalizedPath = files.associateBy { normalize(it.path).lowercase() }
        val byBaseName = files.groupBy { normalize(it.path).substringAfterLast('/').lowercase() }
        val defFiles = files.filter { it.path.endsWith(".def", ignoreCase = true) }
        if (defFiles.isEmpty()) {
            return SourceGraphResult(files, emptyList(), emptyList(), emptyList())
        }

        val startingDefs = if (activeDefPath == null) {
            defFiles
        } else {
            val normalizedRequested = normalize(activeDefPath).lowercase()
            val exact = defFiles.firstOrNull { normalize(it.path).lowercase() == normalizedRequested }
                ?: error("Active DEF '$activeDefPath' was not found in the supplied character files.")
            listOf(exact)
        }

        val queue = ArrayDeque<SourceFile>()
        startingDefs.forEach(queue::add)
        val visited = linkedMapOf<String, SourceFile>()
        val unresolved = linkedSetOf<String>()

        while (queue.isNotEmpty()) {
            val file = queue.removeFirst()
            val normalizedPath = normalize(file.path)
            val key = normalizedPath.lowercase()
            if (visited.putIfAbsent(key, file) != null) continue

            extractReferences(file).forEach { reference ->
                val resolved = resolveReference(file.path, reference, byNormalizedPath, byBaseName)
                if (resolved != null) {
                    val resolvedKey = normalize(resolved.path).lowercase()
                    if (resolvedKey !in visited) queue.add(resolved)
                } else if (reference.substringAfterLast('.', "").lowercase() in supportedExtensions) {
                    unresolved += "${file.path} -> $reference"
                }
            }
        }

        val reachable = visited.values.toList()
        val reachableKeys = visited.keys
        val ignored = files.filter { normalize(it.path).lowercase() !in reachableKeys }
        return SourceGraphResult(
            reachableFiles = reachable,
            ignoredTextFiles = ignored,
            unresolvedReferences = unresolved.toList(),
            defFiles = defFiles,
        )
    }

    private fun extractReferences(file: SourceFile): Set<String> {
        val references = linkedSetOf<String>()
        val extension = file.path.substringAfterLast('.', "").lowercase()

        file.content.lineSequence().forEach { raw ->
            val line = stripComment(raw).trim()
            if (line.isEmpty()) return@forEach

            if (extension == "def") {
                val assignment = assignmentRegex.find(line)
                if (assignment != null) {
                    val value = cleanReference(assignment.groupValues[2])
                    if (value.substringAfterLast('.', "").lowercase() in supportedExtensions) {
                        references += value
                    }
                }
            }

            includeRegex.find(line)?.groupValues?.getOrNull(1)?.let(::cleanReference)?.let { value ->
                if (value.substringAfterLast('.', "").lowercase() in supportedExtensions) {
                    references += value
                }
            }
        }
        return references
    }

    private fun resolveReference(
        sourcePath: String,
        reference: String,
        byNormalizedPath: Map<String, SourceFile>,
        byBaseName: Map<String, List<SourceFile>>,
    ): SourceFile? {
        val normalizedReference = normalize(reference).removePrefix("./")
        val sourceDirectory = normalize(sourcePath).substringBeforeLast('/', "")
        val relativeCandidate = if (sourceDirectory.isBlank()) {
            normalizedReference
        } else {
            "$sourceDirectory/$normalizedReference"
        }
        byNormalizedPath[collapse(relativeCandidate).lowercase()]?.let { return it }
        byNormalizedPath[collapse(normalizedReference).lowercase()]?.let { return it }

        // A basename-only fallback is used only when it is unambiguous in the selected folder.
        val basenameMatches = byBaseName[normalizedReference.substringAfterLast('/').lowercase()].orEmpty()
        return basenameMatches.singleOrNull()
    }

    private fun cleanReference(value: String): String = value
        .trim()
        .trim('"', '\'', ' ')
        .substringBefore(',')
        .trim()

    private fun normalize(path: String): String = path.replace('\\', '/').trim()

    private fun collapse(path: String): String {
        val stack = ArrayDeque<String>()
        normalize(path).split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun stripComment(raw: String): String {
        var quoted = false
        raw.forEachIndexed { index, char ->
            when (char) {
                '"' -> quoted = !quoted
                ';' -> if (!quoted) return raw.substring(0, index)
            }
        }
        return raw
    }
}
