package app.psykit.core

import app.psykit.core.debug.DebugTools
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Mood
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugToolsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = 1_780_000_000_000L
    private fun pet() = PetEngine.advance(PetState.newEgg(now - 60_000L), now, zone)

    @Test fun setStageStaysAfterAdvance() {
        for (stage in Stage.entries.filter { it != Stage.EGG }) {
            val s = PetEngine.advance(DebugTools.setStage(pet(), stage, now), now + 60_000L, zone)
            assertEquals(stage, s.stage, "Stufe $stage muss nach dem Nachrechnen bleiben")
        }
    }

    @Test fun eggHatchesSoonWithCelebration() {
        val egg = DebugTools.setStage(pet(), Stage.EGG, now, hatchInMs = 5_000L)
        assertEquals(Stage.EGG, PetEngine.advance(egg, now + 1_000L, zone).stage)
        val hatched = PetEngine.advance(egg, now + 70_000L, zone)
        assertEquals(Stage.BABY, hatched.stage)
        assertFalse(hatched.hatchSeen, "Feier soll erscheinen")
    }

    @Test fun forcedMoodsGiveTheirMood() {
        val baby = DebugTools.setStage(pet(), Stage.BABY, now)
        val expected = mapOf(
            DebugTools.ForcedMood.OK to Mood.OK, DebugTools.ForcedMood.HAPPY to Mood.HAPPY, DebugTools.ForcedMood.SAD to Mood.SAD,
            DebugTools.ForcedMood.HUNGRY to Mood.HUNGRY, DebugTools.ForcedMood.SICK to Mood.SICK, DebugTools.ForcedMood.SLEEPING to Mood.SLEEPING,
        )
        expected.forEach { (f, m) -> assertEquals(m, DebugTools.forceMood(baby, f).mood, "$f") }
    }

    @Test fun skipTimeLetsTimePass() {
        val s = DebugTools.setStage(pet(), Stage.CHILD, now)
        val later = PetEngine.advance(DebugTools.skipTime(s, 8), now, zone)
        assertTrue(later.satiety < s.satiety, "Nach 8 Stunden muss der Hunger gestiegen sein")
    }

    @Test fun unlockAndGrant() {
        val s = DebugTools.unlockDungeon(DebugTools.grant(pet()), 2)
        assertTrue(s.stardust >= 1000)
        assertEquals(PetEngine.MAX_KEYS, s.keys)
        assertTrue(1 in s.bossesBeaten && 2 in s.bossesBeaten)
        assertTrue(s.dungeonProgress >= 8)
    }
}
