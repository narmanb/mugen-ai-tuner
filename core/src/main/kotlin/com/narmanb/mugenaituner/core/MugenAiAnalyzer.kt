package com.narmanb.mugenaituner.core

object MugenAiAnalyzer {
    private val sectionRegex = Regex("""^\s*\[([^]]+)]""")
    private val assignmentRegex = Regex("""^\s*([^=]+?)\s*=\s*(.*?)\s*$""")
    private val varReferenceRegex = Regex("""(?i)\bvar\s*\(\s*(\d+)\s*\)""")
    private val assignedVarKeyRegex = Regex("""(?i)^var\s*\(\s*(\d+)\s*\)$""")
    private val directVarAssignmentRegex = Regex("""(?i)\bvar\s*\(\s*(\d+)\s*\)\s*:=\s*(.+)$""")
    private val commandTriggerNameRegex = Regex("""(?i)\bcommand\s*=\s*\"([^\"]+)\"""")
    private val legacyAiNameHintRegex = Regex(
        """(?i)(?:^(?:ai|cpu|computer)\d*$|(?:^|[_\-\s])(?:ai|cpu|computer)(?:\d+)?(?:$|[_\-\s]))""",
    )
    private val packedTenLevelRegex = Regex(
        """(?i)^\s*(?:10\s*\*\s*([0-9])|([0-9])\s*\*\s*10)\s*$""",
    )
    private val explicitLevelRangeRegex = Regex("""(?i)\b0\s*(?:~|-|to)\s*([0-9])\b""")

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

        val unresolvedSourceReferences = SourceGraphResolver.resolve(files)
            .unresolvedReferences
            .distinct()
        val blocks = files.flatMap(::parseBlocks)
        val metadata = readMetadata(files)
        val aiCommandNames = findLegacyAiCommands(blocks)
        val flags = traceAiFlags(blocks, aiCommandNames)
        val flagNumbers = flags.map { it.variable }.toSet()
        val configurationParameters = detectConfigurationParameters(blocks, flagNumbers)
        val behaviors = mutableListOf<AiBehavior>()
        var scaledCount = 0

        for (block in blocks) {
            if (!isPotentialBehaviorBlock(block)) continue

            val joined = block.codeText.lowercase()
            val directAiConfidence = AiLevelSignalClassifier.classifyCodeBlock(block.codeText)
            val referencedFlags = varReferenceRegex.findAll(joined)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .filter { it in flagNumbers }
                .toSet()
            val legacyCommand = aiCommandNames.any { command ->
                Regex("""(?i)\bcommand\s*=\s*[\"]?${Regex.escape(command)}[\"]?""").containsMatchIn(joined)
            }

            if (directAiConfidence == null && referencedFlags.isEmpty() && !legacyCommand) continue
            if (isPureAiActivationBlock(block)) continue

            val confidence = when {
                directAiConfidence != null -> directAiConfidence
                referencedFlags.any { number -> flags.any { it.variable == number && it.confidence == Confidence.HIGH } } -> Confidence.HIGH
                referencedFlags.isNotEmpty() -> Confidence.MEDIUM
                legacyCommand -> Confidence.MEDIUM
                else -> Confidence.LOW
            }

            val category = AiBehaviorClassifier.classify(block.codeText, block.section)
            val firstRelevant = block.lines.firstOrNull {
                val lower = it.code.lowercase()
                "ailevel" in lower || varReferenceRegex.containsMatchIn(lower) || "command" in lower
            } ?: block.lines.firstOrNull()

            if (AiLevelDifficultyScaling.hasNumericScaling(block.codeText)) scaledCount++

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

        val aiDetected = flags.isNotEmpty() || distinctBehaviors.isNotEmpty() || configurationParameters.isNotEmpty() ||
            blocks.any { AiLevelSignalClassifier.classifyCodeBlock(it.codeText) != null } || aiCommandNames.isNotEmpty()

        val responsiveness = responsiveness(distinctBehaviors.size, scaledCount, aiDetected)
        val notes = buildList {
            if (unresolvedSourceReferences.isNotEmpty()) {
                add("${unresolvedSourceReferences.size} referenced character-code file(s) could not be resolved; analysis completeness is reduced.")
            }
            if (flags.isNotEmpty()) {
                add("AI-related variables were traced from their activation logic instead of assuming a fixed variable number such as var(59).")
            }
            if (configurationParameters.isNotEmpty()) {
                add("Author-exposed packed AI configuration was detected separately from ordinary probability decisions.")
                configurationParameters.forEach { parameter ->
                    add(
                        "${parameter.label}: ${parameter.currentLevel}/${parameter.maximumLevel} in var(${parameter.variable}) — " +
                            if (parameter.safeToEdit) "verified safe for automatic tuning." else "understood but kept read-only by the safety rules.",
                    )
                }
            }
            if (aiCommandNames.isNotEmpty()) {
                add("Legacy command-based AI activation was detected; these findings are treated more cautiously than direct AILevel logic.")
            }
            if (aiDetected && responsiveness == DifficultyResponsiveness.NONE) {
                add("AI was detected, but no analyzed behavior distinguishes between numeric AILevel values 1–8. Engine difficulty may mainly act as an on/off switch for this character.")
            }
            if (distinctBehaviors.any { it.category == BehaviorCategory.UNKNOWN }) {
                add("Some AI behavior is visible but semantically unclassified; those blocks are kept read-only rather than assigned a tuning category by guesswork.")
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
            configurationParameters = configurationParameters,
            unresolvedSourceReferences = unresolvedSourceReferences,
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

        for (block in blocks) {
            for (line in block.lines) {
                val assignment = directVarAssignmentRegex.find(line.code) ?: continue
                val variable = assignment.groupValues[1].toIntOrNull() ?: continue
                val rightHandSide = assignment.groupValues[2]
                val signalConfidence = AiLevelSignalClassifier.classifyExpression(rightHandSide) ?: continue
                flags[variable] = AiFlag(
                    variable,
                    signalConfidence,
                    if (signalConfidence == Confidence.HIGH) {
                        "Assigned directly from an AI-positive AILevel expression using :=."
                    } else {
                        "Direct := assignment depends on AILevel, but the expression is not a proven AI on/off signal."
                    },
                )
            }
        }

        blocks.forEach { block ->
            val write = variableControllerTarget(block) ?: return@forEach
            val signalConfidence = AiLevelSignalClassifier.classifyCodeBlock(block.codeText)
            val joined = block.codeText.lowercase()
            when {
                signalConfidence != null -> flags[write.variable] = AiFlag(
                    write.variable,
                    signalConfidence,
                    if (signalConfidence == Confidence.HIGH) {
                        "${write.controllerType} is controlled by AI-positive AILevel logic."
                    } else {
                        "${write.controllerType} depends on AILevel, but not as a proven AI-only gate."
                    },
                )
                aiCommandNames.any { command -> commandReferenced(joined, command) } -> flags.putIfAbsent(
                    write.variable,
                    AiFlag(
                        write.variable,
                        Confidence.MEDIUM,
                        "${write.controllerType} is controlled by a likely legacy AI-only command set.",
                    ),
                )
            }
        }

        var changed: Boolean
        do {
            changed = false
            val known = flags.keys.toSet()

            for (block in blocks) {
                for (line in block.lines) {
                    val assignment = directVarAssignmentRegex.find(line.code) ?: continue
                    val target = assignment.groupValues[1].toIntOrNull() ?: continue
                    if (target in known) continue
                    val rightHandSide = assignment.groupValues[2]
                    val referencesKnown = varReferenceRegex.findAll(rightHandSide)
                        .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                        .any { it in known }
                    if (referencesKnown) {
                        flags[target] = AiFlag(
                            target,
                            Confidence.MEDIUM,
                            "Direct := assignment depends on another variable already traced to AI activation.",
                        )
                        changed = true
                    }
                }
            }

            val knownAfterDirectAssignments = flags.keys.toSet()
            blocks.forEach { block ->
                val write = variableControllerTarget(block) ?: return@forEach
                if (write.variable in knownAfterDirectAssignments) return@forEach
                val refs = varReferenceRegex.findAll(block.codeText)
                    .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                    .toSet()
                if (refs.any { it in knownAfterDirectAssignments }) {
                    flags[write.variable] = AiFlag(
                        write.variable,
                        Confidence.MEDIUM,
                        "${write.controllerType} is gated by another variable already traced to AI activation.",
                    )
                    changed = true
                }
            }
        } while (changed)

        return flags.values.toList()
    }

    private fun detectConfigurationParameters(
        blocks: List<ParsedBlock>,
        aiVariables: Set<Int>,
    ): List<AiConfigurationParameter> {
        val parameters = mutableListOf<AiConfigurationParameter>()

        blocks.forEach { block ->
            val write = variableControllerTarget(block) ?: return@forEach
            if (!write.controllerType.equals("VarAdd", ignoreCase = true)) return@forEach

            val joined = block.codeText.lowercase()
            val referencesKnownAi = varReferenceRegex.findAll(joined)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .any { it in aiVariables }
            if (write.variable !in aiVariables && AiLevelSignalClassifier.classifyCodeBlock(block.codeText) == null && !referencesKnownAi) return@forEach

            val values = block.assignments()
            val valueExpression = values["value"] ?: return@forEach
            val levelMatch = packedTenLevelRegex.matchEntire(valueExpression) ?: return@forEach
            val level = (levelMatch.groupValues[1].ifBlank { levelMatch.groupValues[2] }).toIntOrNull()
                ?: return@forEach

            val raw = block.lines.joinToString("\n") { it.raw }
            val context = (block.section + "\n" + raw).lowercase()
            val escapedVariable = Regex.escape(write.variable.toString())
            val zeroGate = Regex(
                """(?i)var\s*\(\s*$escapedVariable\s*\)\s*(?:=|==)\s*0\b""",
            ).containsMatchIn(joined)
            val oneToNineGate = Regex(
                """(?i)var\s*\(\s*$escapedVariable\s*\)\s*=\s*\[\s*1\s*,\s*9\s*]""",
            ).containsMatchIn(joined)

            val explicitKind = when {
                "combo" in context -> AiConfigurationKind.COMBO_LEVEL
                "movement" in context || "move setting" in context || "movement setting" in context -> AiConfigurationKind.MOVEMENT_LEVEL
                "guard" in context && "setting" in context -> AiConfigurationKind.GUARD_LEVEL
                else -> null
            }
            val structuralKind = when {
                zeroGate -> AiConfigurationKind.COMBO_LEVEL
                oneToNineGate -> AiConfigurationKind.MOVEMENT_LEVEL
                else -> null
            }
            val kind = explicitKind ?: structuralKind ?: AiConfigurationKind.GENERIC
            if (kind == AiConfigurationKind.GENERIC) return@forEach

            val rangeFromComment = explicitLevelRangeRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val defaultMaximum = when (kind) {
                AiConfigurationKind.COMBO_LEVEL -> 3
                AiConfigurationKind.MOVEMENT_LEVEL -> 3
                AiConfigurationKind.GUARD_LEVEL -> 3
                AiConfigurationKind.GENERIC -> level.coerceAtLeast(1)
            }
            val maximum = maxOf(level, rangeFromComment ?: defaultMaximum)
            val valueLine = block.lines.firstOrNull {
                it.code.trimStart().startsWith("value", ignoreCase = true)
            }
            val structureAndLabelAgree = explicitKind != null && structuralKind != null && explicitKind == structuralKind
            val confidence = when {
                structureAndLabelAgree -> Confidence.HIGH
                explicitKind != null || structuralKind != null -> Confidence.MEDIUM
                else -> Confidence.LOW
            }
            val safeToEdit = structureAndLabelAgree &&
                rangeFromComment != null &&
                rangeFromComment in 1..4 &&
                level in 0..rangeFromComment &&
                valueLine != null
            val label = when (kind) {
                AiConfigurationKind.COMBO_LEVEL -> "Combo level"
                AiConfigurationKind.MOVEMENT_LEVEL -> "Movement level"
                AiConfigurationKind.GUARD_LEVEL -> "Guard level"
                AiConfigurationKind.GENERIC -> "AI setting"
            }
            val description = when (kind) {
                AiConfigurationKind.COMBO_LEVEL -> "Author-exposed combo sophistication setting packed into an AI variable. Higher levels generally enable more advanced follow-ups."
                AiConfigurationKind.MOVEMENT_LEVEL -> "Author-exposed AI movement/decision setting packed into an AI variable. Higher levels generally increase consistency and movement capability."
                AiConfigurationKind.GUARD_LEVEL -> "Author-exposed guarding setting packed into an AI variable."
                AiConfigurationKind.GENERIC -> "Author-exposed packed AI configuration setting."
            }

            parameters += AiConfigurationParameter(
                kind = kind,
                label = label,
                variable = write.variable,
                currentLevel = level,
                minimumLevel = 0,
                maximumLevel = maximum,
                confidence = confidence,
                filePath = block.filePath,
                lineNumber = valueLine?.lineNumber ?: block.startLine,
                originalExpression = valueLine?.code ?: "value = $valueExpression",
                description = description,
                safeToEdit = safeToEdit,
            )
        }

        return parameters.distinctBy {
            listOf(it.kind, it.variable, it.filePath, it.lineNumber)
        }
    }

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
                val hinted = legacyAiNameHintRegex.containsMatchIn(name)
                val separators = command.count { it == '+' || it == ',' }
                val commandLower = command.lowercase()
                val directionalContradiction = Regex("""(?i)(?:^|[, +])(?:u|d|f|b)[, +]+(?:u|d|f|b)(?:$|[, +])""")
                    .containsMatchIn(command) &&
                    (("u" in commandLower && "d" in commandLower) ||
                        ("f" in commandLower && "b" in commandLower))
                val impractical = separators >= 7 || command.length >= 32 || directionalContradiction
                CommandEvidence(name, command, hinted, impractical)
            }

        val byName = definitions.associateBy { it.name.lowercase() }
        val result = linkedSetOf<String>()

        // Winane-style XOR activation is structurally distinctive enough to treat the involved
        // command names as likely legacy AI commands, but downstream flags remain medium-confidence.
        blocks.forEach { block ->
            result += LegacyXorAiDetector.detectCommandNames(block.codeText)
        }

        definitions.filter { it.hinted && it.impractical }.forEach { result += it.name }

        blocks.forEach { block ->
            if (variableControllerTarget(block) == null) return@forEach
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

    private fun variableControllerTarget(block: ParsedBlock): VariableWrite? {
        val values = block.assignments()
        val controllerType = values["type"].orEmpty()
        if (!controllerType.equals("VarSet", ignoreCase = true) &&
            !controllerType.equals("VarAdd", ignoreCase = true)
        ) return null

        values["v"]?.trim()?.toIntOrNull()?.let { variable ->
            return VariableWrite(variable, controllerType)
        }

        values.keys.forEach { key ->
            val variable = assignedVarKeyRegex.matchEntire(key)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (variable != null) return VariableWrite(variable, controllerType)
        }
        return null
    }

    private fun isPureAiActivationBlock(block: ParsedBlock): Boolean {
        val values = block.assignments()
        return values["type"].orEmpty().equals("VarSet", ignoreCase = true) &&
            (AiLevelSignalClassifier.classifyCodeBlock(block.codeText) != null ||
                block.section.startsWith("State -2", ignoreCase = true) ||
                block.section.startsWith("State -3", ignoreCase = true))
    }

    private fun isPotentialBehaviorBlock(block: ParsedBlock): Boolean {
        if (block.section.startsWith("State -1", ignoreCase = true)) return true
        val type = block.assignments()["type"]?.lowercase() ?: return false
        return type in setOf(
            "changestate", "selfstate", "hitdef", "projectile", "helper",
            "velset", "veladd", "posadd", "turn", "ctrlset",
        )
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
        BehaviorCategory.UNKNOWN -> "AI-related behavior whose purpose could not be classified confidently; it is kept read-only."
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

    private data class VariableWrite(
        val variable: Int,
        val controllerType: String,
    )

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
