package app.psykit.core

import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Difficulty
import app.psykit.core.model.GameKind
import app.psykit.core.model.GameTuning
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DifficultyTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun now() = LocalDateTime.of(2026, 1, 1, 12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun pet() = PetEngine.advance(PetState.newEgg(now() - 10 * 60_000L), now(), zone).copy(stage = Stage.CHILD, energy = 100f, satiety = 100f)

    @Test fun easyIsNeverHarderThanNormalOrHard() {
        for (lvl in 0..20) {
            assertTrue(GameTuning.cloudWindow(Difficulty.EASY, lvl) >= GameTuning.cloudWindow(Difficulty.NORMAL, lvl))
            assertTrue(GameTuning.cloudWindow(Difficulty.NORMAL, lvl) >= GameTuning.cloudWindow(Difficulty.HARD, lvl))
            assertTrue(GameTuning.cloudSpeed(Difficulty.EASY, lvl) <= GameTuning.cloudSpeed(Difficulty.NORMAL, lvl))
            assertTrue(GameTuning.cloudSpeed(Difficulty.NORMAL, lvl) <= GameTuning.cloudSpeed(Difficulty.HARD, lvl))
        }
        for (r in 0..4) {
            assertTrue(GameTuning.swapMs(Difficulty.EASY, r) >= GameTuning.swapMs(Difficulty.NORMAL, r))
            assertTrue(GameTuning.swapMs(Difficulty.NORMAL, r) >= GameTuning.swapMs(Difficulty.HARD, r))
            assertTrue(GameTuning.swapCount(Difficulty.EASY, r) <= GameTuning.swapCount(Difficulty.HARD, r))
        }
    }

    @Test fun gamesGetHarderWithRounds() {
        assertTrue(GameTuning.swapMs(Difficulty.NORMAL, 4) < GameTuning.swapMs(Difficulty.NORMAL, 0))
        assertTrue(GameTuning.cloudWindow(Difficulty.NORMAL, 10) < GameTuning.cloudWindow(Difficulty.NORMAL, 0))
        assertTrue(GameTuning.swapMs(Difficulty.HARD, 99) >= 250, "nie schneller als 250 ms")
        assertTrue(GameTuning.cloudWindow(Difficulty.HARD, 99) >= 0.09f, "Trefferfenster nie unter dem Boden")
    }

    @Test fun hardGivesMoreExpEasyAndNormalTheSame() {
        val s = pet()
        val easy = PetEngine.gameResult(s, GameKind.CLOUDS, 10, 10, difficulty = Difficulty.EASY).state
        val normal = PetEngine.gameResult(s, GameKind.CLOUDS, 10, 10, difficulty = Difficulty.NORMAL).state
        val hard = PetEngine.gameResult(s, GameKind.CLOUDS, 10, 10, difficulty = Difficulty.HARD).state
        assertEquals(easy.totalExp, normal.totalExp, "Leicht und Mittel geben den normalen Fortschritt")
        assertTrue(hard.totalExp > normal.totalExp, "Schwer gibt mehr EP")
    }

    @Test fun parseFallsBackToNormal() {
        assertEquals(Difficulty.NORMAL, Difficulty.parse(null))
        assertEquals(Difficulty.HARD, Difficulty.parse("HARD"))
    }
}
