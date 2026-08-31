package com.narmanb.mugenaituner.core

object MugenAiAnalyzer {
    private val sectionRegex = Regex("""^\s*\[([^]]+)]""")
    private val assignmentRegex = Regex("""^\s*([^=]+?)\s*=\s*(.*?)\s*$""")
    private val varReferenceRegex = Regex("""(?i)\bvar\s*\(\s*(\d+)\s*\)""")
    private val directVarAssignmentRegex = Regex("""(?i)\bvar\s*\(\s*(\d+)\s*\)\s*:?=""")
    private val randomThresholdRegex = Regex("""(?i)\brandom\s*(?:<|<=|>|>=)\s*\d+""")
    private val commandTriggerNameRegex = Regex("""(?i)\bcommand\s*=\s*\"([^\"]+)\"""")
    private val scaledAiLevelRegex = Regex(
        """(?i)(ailevel\s*[+\-*/]|[+\-*/]\s*ailevel|ailevel\s*(?:>=|>|==|=|<=|<)\s*[1-8]\b)""",
    )

    fun analyze(files: List<SourceFile>): CharacterAnalysis {
        if (files.isEmpty()) {
            return CharacterAnalysis(
                characterName = "Unknown character",
                author = null,
                aiDetected = false,
                aiFlags = emptyList(),
                behaviors = emptyList(),
                difficultyResponsiveness = DifficultyResponsiveness.UNKNOWN,
                directlyScaledBehaviorCount = 0,
                aiBehaviorCount = 0,
                notes = listOf("No readable MUGEN/IKEMEN text files were supplied."),
            )
        }

        val blocks = files.flatMap(::parseBlocks)
        val metadata = readMetadata(files)
        val aiCommandNames = findLegacyAiCommands(blocks)
        val flags = traceAiFlags(blocks, aiCommandNames)
        val flagNumbers = flags.map { it.variable }.toSet()
        val behaviors = mutableListOf<AiBehavior>()
        var scaledCount = 0

        for (block in blocks) {
            if (!isPotentialBehaviorBlock(block)) continue

            val joined = block.codeText.lowercase()
            val directAi = "ailevel" in joined
            val referencedFlags = varReferenceRegex.findAll(joined)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .filter { it in flagNumbers }
                .toSet()
            val legacyCommand = aiCommandNames.any { command ->
                Regex("""(?i)\bcommand\s*=\s*[\"]?${Regex.escape(command)}[\"]?""").containsMatchIn(joined)
            }

            if (!directAi && referencedFlags.isEmpty() && !legacyCommand) continue
            if (isPureAiActivationBlock(block)) continue

            val confidence = when {
                directAi -> Confidence.HIGH
                referencedFlags.any { number -> flags.any { it.variable == number && it.confidence == Confidence.HIGH } } -> Confidence.HIGH
                referencedFlags.isNotEmpty() -> Confidence.MEDIUM
                legacyCommand -> Confidence.MEDIUM
                else -> Confidence.LOW
            }

            val category = classifyBehavior(joined, block.section)
            val firstRelevant = block.lines.firstOrNull {
                val lower = it.code.lowercase()
                "ailevel" in lower || varReferenceRegex.containsMatchIn(lower) || "command" in lower
            } ?: block.lines.firstOrNull()

            if (scaledAiLevelRegex.containsMatchIn(joined)) scaledCount++

            behaviors += AiBehavior(
                category = category,
                summary = summarize(category, joined),
                confidence = confidence,
                filePath = block.filePath,
                lineNumber = firstRelevant?.lineNumber ?: block.startLine,
                section = block.section,
                rawCode = block.lines.joinToString("\n") { it.raw },
            )
        }

        val distinctBehaviors = behaviors.distinctBy {
            Triple(it.filePath, it.lineNumber, it.rawCode)
        }

        val aiDetected = flags.isNotEmpty() || distinctBehaviors.isNotEmpty() ||
            blocks.any { "ailevel" in it.codeText.lowercase() } || aiCommandNames.isNotEmpty()

        val responsiveness = responsiveness(distinctBehaviors.size, scaledCount, aiDetected)
        val notes = buildList {
            if (flags.isNotEmpty()) {
                add("AI flag variables were traced from their activation logic instead of assuming a fixed variable number such as var(59).")
            }
            if (aiCommandNames.isNotEmpty()) {
                add("Legacy command-based AI activation was detected; these findings are treated more cautiously than direct AILevel logic.")
            }
            if (aiDetected && responsiveness == DifficultyResponsiveness.NONE) {
                add("AI was detected, but no analyzed behavior directly scales with the numeric AILevel. Engine difficulty may mainly act as an on/off switch for this character.")
            }
            if (distinctBehaviors.any { it.confidence == Confidence.LOW || it.confidence == Confidence.UNKNOWN }) {
                add("Some AI-related code is uncertain and should not be modified automatically.")
            }
        }

        return CharacterAnalysis(
            characterName = metadata.first,
            author = metadata.second,
            aiDetected = aiDetected,
            aiFlags = flags.sortedBy { it.variable },
            behaviors = distinctBehaviors,
            difficultyResponsiveness = responsiveness,
            directlyScaledBehaviorCount = scaledCount,
            aiBehaviorCount = distinctBehaviors.size,
            notes = notes,
        )
    }

    private fun readMetadata(files: List<SourceFile>): Pair<String, String?> {
        val def = files.firstOrNull { it.path.endsWith(".def", ignoreCase = true) }
            ?: return (files.firstOrNull()?.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Unknown character") to null

        var name: String? = null
        var displayName: String? = null
        var author: String? = null
        var inInfo = false

        def.content.lineSequence().forEach { raw ->
            val code = stripComment(raw).trim()
            val section = sectionRegex.find(code)?.groupValues?.getOrNull(1)?.trim()
            if (section != null) {
                inInfo = section.equals("Info", ignoreCase = true)
                return@forEach
            }
            if (!inInfo) return@forEach
            val match = assignmentRegex.find(code) ?: return@forEach
            val key = match.groupValues[1].trim().lowercase()
            val value = match.groupValues[2].trim().trim('"')
            when (key) {
                "name" -> name = value
                "displayname" -> displayName = value
                "author" -> author = value
            }
        }

        val fallback = def.path.substringAfterLast('/').substringBeforeLast('.')
        return (displayName ?: name ?: fallback) to author
    }

    private fun traceAiFlags(blocks: List<ParsedBlock>, aiCommandNames: Set<String>): List<AiFlag> {
        val flags = linkedMapOf<Int, AiFlag>()

        // Direct assignment syntax such as var(42) := AILevel > 0.
        blocks.forEach { block ->
            block.lines.forEach { line ->
                if ("ailevel" !in line.code.lowercase()) return@forEach
                directVarAssignmentRegex.find(line.code)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { variable ->
                    flags[variable] = AiFlag(variable, Confidence.HIGH, "Assigned directly from an AILevel expression.")
                }
            }
        }

        // Standard VarSet controllers directly gated by AILevel or a traced legacy AI command.
        blocks.forEach { block ->
            val target = varSetTarget(block) ?: return@forEach
            val joined = block.codeText.lowercase()
            when {
                "ailevel" in joined -> flags[target] = AiFlag(
                    target,
                    Confidence.HIGH,
                    "VarSet is controlled by AILevel.",
                )
                aiCommandNames.any { command -> commandReferenced(joined, command) } -> flags.putIfAbsent(
                    target,
                    AiFlag(target, Confidence.MEDIUM, "VarSet is controlled by a likely legacy AI-only command set."),
                )
            }
        }

        // Follow simple chains: AI flag A gates a VarSet that establishes AI flag B.
        var changed: Boolean
        do {
            changed = false
            val known = flags.keys.toSet()
            blocks.forEach { block ->
                val target = varSetTarget(block) ?: return@forEach
                if (target in known) return@forEach
                val refs = varReferenceRegex.findAll(block.codeText)
                    .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                    .toSet()
                if (refs.any { it in known }) {
                    flags[target] = AiFlag(
                        target,
                        Confidence.MEDIUM,
                        "VarSet is gated by another variable already traced to AI activation.",
                    )
                    changed = true
                }
            }
        } while (changed)

        return flags.values.toList()
    }

    /**
     * Old WinMUGEN characters often activate AI by having the engine randomly satisfy commands a
     * human could not reasonably enter. Names such as cpu1/cpu2 are common, but not guaranteed.
     *
     * We therefore require structural evidence before treating opaque command names as AI:
     * a VarSet controller must be driven by several command definitions that themselves look
     * deliberately impractical. This is much safer than declaring every long combo command AI.
     */
    private fun findLegacyAiCommands(blocks: List<ParsedBlock>): Set<String> {
        data class CommandEvidence(
            val name: String,
            val command: String,
            val hinted: Boolean,
            val impractical: Boolean,
        )

        val definitions = blocks
            .filter { it.section.equals("Command", ignoreCase = true) }
            .mapNotNull { block ->
                val values = block.assignments()
                val name = values["name"]?.trim('"') ?: return@mapNotNull null
                val command = values["command"]?.trim('"').orEmpty()
                val lowerName = name.lowercase()
                val hinted = lowerName.contains("ai") ||
                    lowerName.contains("cpu") ||
                    lowerName.contains("computer")
                val separators = command.count { it == '+' || it == ',' }
                val directionalContradiction = Regex("""(?i)(?:^|[, +])(?:u|d|f|b)[, +]+(?:u|d|f|b)(?:$|[, +])""")
                    .containsMatchIn(command) &&
                    (("u" in command.lowercase() && "d" in command.lowercase()) ||
                        ("f" in command.lowercase() && "b" in command.lowercase()))
                val impractical = separators >= 7 || command.length >= 32 || directionalContradiction
                CommandEvidence(name, command, hinted, impractical)
            }

        val byName = definitions.associateBy { it.name.lowercase() }
        val result = linkedSetOf<String>()

        // Strong naming + complexity evidence is sufficient for common cpu1..cpu63 systems.
        definitions.filter { it.hinted && it.impractical }.forEach { result += it.name }

        // Opaque names require a VarSet that ORs together several impractical commands.
        blocks.forEach { block ->
            if (varSetTarget(block) == null) return@forEach
            val referenced = commandTriggerNameRegex.findAll(block.codeText)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
            val suspicious = referenced.filter { name -> byName[name.lowercase()]?.impractical == true }
            if (suspicious.size >= 4) {
                result += suspicious
            }
        }

        return result
    }

    private fun varSetTarget(block: ParsedBlock): Int? {
        val values = block.assignments()
        if (!values["type"].orEmpty().equals("VarSet", ignoreCase = true)) return null
        return values["v"]?.trim()?.toIntOrNull()
    }

    private fun isPureAiActivationBlock(block: ParsedBlock): Boolean {
        val values = block.assignments()
        return values["type"].orEmpty().equals("VarSet", ignoreCase = true) &&
            ("ailevel" in block.codeText.lowercase() || block.section.startsWith("State -2", ignoreCase = true) || block.section.startsWith("State -3", ignoreCase = true))
    }

    private fun isPotentialBehaviorBlock(block: ParsedBlock): Boolean {
        if (block.section.startsWith("State -1", ignoreCase = true)) return true
        val type = block.assignments()["type"]?.lowercase() ?: return false
        return type in setOf(
            "changestate", "selfstate", "hitdef", "projectile", "helper",
            "velset", "veladd", "posadd", "turn", "ctrlset",
        )
    }

    private fun classifyBehavior(joined: String, section: String): BehaviorCategory = when {
        "numproj" in joined || "projcontact" in joined || "projhit" in joined || "projguarded" in joined || "projectile" in joined -> BehaviorCategory.PROJECTILE_RESPONSE
        "p2statetype" in joined && Regex("""(?i)p2statetype\s*=\s*a""").containsMatchIn(joined) -> BehaviorCategory.ANTI_AIR
        "anti air" in joined || "anti-air" in joined -> BehaviorCategory.ANTI_AIR
        "inguarddist" in joined || "guarddist" in joined || "guard" in section.lowercase() -> BehaviorCategory.DEFENSE
        "movehit" in joined || "movecontact" in joined || "hitcount" in joined || "combo" in section.lowercase() -> BehaviorCategory.COMBO
        "super" in section.lowercase() || "hyper" in section.lowercase() || Regex("""(?i)power\s*>=\s*[1-9]\d{2,}""").containsMatchIn(joined) -> BehaviorCategory.SUPER
        "throw" in section.lowercase() || Regex("""(?i)attr\s*=.*\b[nsah]t\b""").containsMatchIn(joined) -> BehaviorCategory.THROW
        "velset" in joined || "veladd" in joined || "posadd" in joined || "dash" in section.lowercase() || "jump" in section.lowercase() -> BehaviorCategory.MOVEMENT
        "p2bodydist" in joined && ("changestate" in joined || randomThresholdRegex.containsMatchIn(joined)) -> BehaviorCategory.AGGRESSION
        else -> BehaviorCategory.REACTION
    }

    private fun summarize(category: BehaviorCategory, joined: String): String = when (category) {
        BehaviorCategory.DEFENSE -> if ("inguarddist" in joined) "AI defensive reaction while the opponent is within guarding distance." else "AI-controlled defensive behavior."
        BehaviorCategory.REACTION -> "AI-controlled decision or reaction behavior."
        BehaviorCategory.AGGRESSION -> "AI attack/engagement decision influenced by opponent distance or probability."
        BehaviorCategory.COMBO -> "AI combo continuation or hit-confirm behavior."
        BehaviorCategory.ANTI_AIR -> "AI reaction to an airborne opponent."
        BehaviorCategory.PROJECTILE_RESPONSE -> "AI behavior that reacts to or manages projectiles."
        BehaviorCategory.THROW -> "AI throw-related decision behavior."
        BehaviorCategory.SUPER -> "AI super/hyper or high-resource attack decision."
        BehaviorCategory.MOVEMENT -> "AI-controlled movement, positioning, dash, or jump behavior."
        BehaviorCategory.UNKNOWN -> "AI-related behavior whose purpose could not be classified confidently."
    }

    private fun responsiveness(
        behaviorCount: Int,
        scaledCount: Int,
        aiDetected: Boolean,
    ): DifficultyResponsiveness {
        if (!aiDetected) return DifficultyResponsiveness.UNKNOWN
        if (behaviorCount == 0) return DifficultyResponsiveness.UNKNOWN
        if (scaledCount == 0) return DifficultyResponsiveness.NONE
        val ratio = scaledCount.toDouble() / behaviorCount.toDouble()
        return when {
            ratio < 0.25 -> DifficultyResponsiveness.LOW
            ratio < 0.70 -> DifficultyResponsiveness.MODERATE
            else -> DifficultyResponsiveness.FULL
        }
    }

    private fun parseBlocks(file: SourceFile): List<ParsedBlock> {
        val blocks = mutableListOf<ParsedBlock>()
        var currentSection = "Preamble"
        var currentStart = 1
        var currentLines = mutableListOf<CodeLine>()

        fun flush() {
            if (currentLines.isNotEmpty()) {
                blocks += ParsedBlock(file.path, currentSection, currentStart, currentLines.toList())
            }
            currentLines = mutableListOf()
        }

        file.content.lineSequence().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            val code = stripComment(raw).trim()
            val section = sectionRegex.find(code)?.groupValues?.getOrNull(1)?.trim()
            if (section != null) {
                flush()
                currentSection = section
                currentStart = lineNumber
            } else if (code.isNotEmpty()) {
                currentLines += CodeLine(lineNumber, raw, code)
            }
        }
        flush()
        return blocks
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

    private fun commandReferenced(joined: String, command: String): Boolean =
        Regex("""(?i)\bcommand\s*=\s*[\"]?${Regex.escape(command)}[\"]?""").containsMatchIn(joined)

    private data class CodeLine(
        val lineNumber: Int,
        val raw: String,
        val code: String,
    )

    private data class ParsedBlock(
        val filePath: String,
        val section: String,
        val startLine: Int,
        val lines: List<CodeLine>,
    ) {
        val codeText: String = lines.joinToString("\n") { it.code }

        fun assignments(): Map<String, String> = buildMap {
            lines.forEach { line ->
                val match = assignmentRegex.find(line.code) ?: return@forEach
                put(match.groupValues[1].trim().lowercase(), match.groupValues[2].trim())
            }
        }
    }
}
