package com.narmanb.mugenaituner.core

/**
 * Classifies AI decisions only when there is concrete semantic evidence. Unknown blocks stay
 * UNKNOWN so the edit planner cannot apply a category-specific difficulty curve by guesswork.
 */
object AiBehaviorClassifier {
    private val airborneOpponent = Regex(
        """(?i)(?:\bp2statetype\s*=\s*a\b|\benemynear\s*,\s*statetype\s*=\s*a\b)""",
    )
    private val guardStateChange = Regex(
        """(?i)\btype\s*=\s*changestate\b[\s\S]*\bvalue\s*=\s*120\b""",
    )
    private val opponentAttack = Regex(
        """(?i)(?:\bp2movetype\s*=\s*a\b|\benemynear\s*,\s*movetype\s*=\s*a\b)""",
    )
    private val superPower = Regex("""(?i)\bpower\s*>=\s*\d{4,}\b""")
    private val throwAttr = Regex("""(?i)\battr\s*=.*\b[nsah]t\b""")
    private val horizontalDistance = Regex(
        """(?i)(?:\bp2bodydist\s+x\b|\bp2dist\s+x\b|\benemynear\s*,\s*pos\s+x\b)""",
    )
    private val genericOpponentReaction = Regex(
        """(?i)(?:\bp2movetype\b|\bp2statetype\b|\bp2stateno\b|\benemynear\s*,\s*(?:movetype|statetype|stateno)\b|\bgethitvar\s*\()""",
    )

    fun classify(codeText: String, section: String): BehaviorCategory {
        val joined = codeText.lowercase()
        val sectionLower = section.lowercase()

        val strongDefense = "inguarddist" in joined ||
            "guarddist" in joined ||
            "guard" in sectionLower ||
            (guardStateChange.containsMatchIn(codeText) && opponentAttack.containsMatchIn(codeText))
        if (strongDefense) return BehaviorCategory.DEFENSE

        if (
            "numproj" in joined ||
            "numprojid" in joined ||
            "projcontact" in joined ||
            "projcontacttime" in joined ||
            "projhit" in joined ||
            "projhittime" in joined ||
            "projguarded" in joined ||
            "projguardedtime" in joined ||
            "projectile" in joined
        ) return BehaviorCategory.PROJECTILE_RESPONSE

        if (airborneOpponent.containsMatchIn(codeText) || "anti air" in joined || "anti-air" in joined) {
            return BehaviorCategory.ANTI_AIR
        }

        if (
            "movehit" in joined ||
            "movecontact" in joined ||
            "hitcount" in joined ||
            "numtarget" in joined ||
            "combo" in sectionLower
        ) return BehaviorCategory.COMBO

        if ("super" in sectionLower || "hyper" in sectionLower || superPower.containsMatchIn(codeText)) {
            return BehaviorCategory.SUPER
        }

        if ("throw" in sectionLower || throwAttr.containsMatchIn(codeText)) {
            return BehaviorCategory.THROW
        }

        if (
            "velset" in joined ||
            "veladd" in joined ||
            "posadd" in joined ||
            "dash" in sectionLower ||
            "jump" in sectionLower
        ) return BehaviorCategory.MOVEMENT

        if (horizontalDistance.containsMatchIn(codeText) &&
            ("changestate" in joined || RandomProbabilityParser.findAll(codeText).isNotEmpty())
        ) return BehaviorCategory.AGGRESSION

        if (genericOpponentReaction.containsMatchIn(codeText)) return BehaviorCategory.REACTION

        return BehaviorCategory.UNKNOWN
    }
}
