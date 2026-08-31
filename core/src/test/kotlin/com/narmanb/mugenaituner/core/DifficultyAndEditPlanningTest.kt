package com.narmanb.mugenaituner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DifficultyAndEditPlanningTest {
    @Test
    fun presetsUseExpectedStandardizedSkillLevels() {
        assertEquals(30, SkillProfile.fromPreset(DifficultyPreset.EASY).overallSkill)
        assertEquals(50, SkillProfile.fromPreset(DifficultyPreset.NORMAL).overallSkill)
        assertEquals(75, SkillProfile.fromPreset(DifficultyPreset.HARD).overallSkill)
    }

    @Test
    fun categoryOverrideDoesNotChangeOtherCategories() {
        val profile = SkillProfile(overallSkill = 50)
            .withCategorySkill(BehaviorCategory.AGGRESSION, 85)

        assertEquals(85, profile.skillFor(BehaviorCategory.AGGRESSION))
        assertEquals(50, profile.skillFor(BehaviorCategory.DEFENSE))
    }

    @Test
    fun plannerOnlyRewritesHighConfidenceSimpleRandomExpressions() {
        val analysis = CharacterAnalysis(
            characterName = "Test",
            author = null,
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = listOf(
                AiBehavior(
                    category = BehaviorCategory.DEFENSE,
                    summary = "Guard",
                    confidence = Confidence.HIGH,
                    filePath = "test.cmd",
                    lineNumber = 20,
                    section = "State -1, Guard",
                    rawCode = "triggerall = AIFlag\ntrigger1 = InGuardDist\ntrigger1 = Random < 900\ntype = ChangeState",
                ),
                AiBehavior(
                    category = BehaviorCategory.SUPER,
                    summary = "Super",
                    confidence = Confidence.LOW,
                    filePath = "test.cmd",
                    lineNumber = 50,
                    section = "State -1, Super",
                    rawCode = "trigger1 = Random < 900\ntype = ChangeState",
                ),
            ),
            difficultyResponsiveness = DifficultyResponsiveness.NONE,
            directlyScaledBehaviorCount = 0,
            aiBehaviorCount = 2,
            notes = emptyList(),
        )

        val plan = AiEditPlanner.plan(analysis, SkillProfile.fromPreset(DifficultyPreset.NORMAL))

        assertEquals(1, plan.edits.size)
        assertEquals(BehaviorCategory.DEFENSE, plan.edits.single().category)
        assertTrue(plan.edits.single().replacementExpression.startsWith("Random < "))
        assertFalse(plan.edits.single().replacementExpression.endsWith("900"))
        assertEquals(1, plan.skippedBehaviorCount)
    }

    @Test
    fun plannerSkipsExistingAiLevelScaledRandomLogic() {
        val analysis = CharacterAnalysis(
            characterName = "Scaled",
            author = null,
            aiDetected = true,
            aiFlags = emptyList(),
            behaviors = listOf(
                AiBehavior(
                    category = BehaviorCategory.REACTION,
                    summary = "Scaled reaction",
                    confidence = Confidence.HIGH,
                    filePath = "scaled.cmd",
                    lineNumber = 10,
                    section = "State -1, Attack",
                    rawCode = "trigger1 = Random < AILevel * 100\ntype = ChangeState",
                ),
            ),
            difficultyResponsiveness = DifficultyResponsiveness.FULL,
            directlyScaledBehaviorCount = 1,
            aiBehaviorCount = 1,
            notes = emptyList(),
        )

        val plan = AiEditPlanner.plan(analysis, SkillProfile.fromPreset(DifficultyPreset.EASY))
        assertTrue(plan.isEmpty)
        assertEquals(1, plan.skippedBehaviorCount)
    }

    @Test
    fun fingerprintChangesWhenCharacterTextChanges() {
        val first = CharacterFingerprinter.fingerprint(
            listOf(SourceFile("char.cmd", "trigger1 = Random < 900")),
        )
        val second = CharacterFingerprinter.fingerprint(
            listOf(SourceFile("char.cmd", "trigger1 = Random < 500")),
        )

        assertTrue(first.differsFrom(second))
        assertEquals(64, first.files.single().sha256.length)
    }
}
