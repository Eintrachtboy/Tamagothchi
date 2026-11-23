package app.psykit.core

import app.psykit.core.battle.BattleEngine
import app.psykit.core.battle.PetCombatant
import app.psykit.core.engine.LevelSystem
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Mood
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PetEngineTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(h: Int, m: Int = 0, day: Int = 1) =
        LocalDateTime.of(2026, 1, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    private fun hatched(now: Long) =
        PetEngine.advance(PetState.newEgg(now - 10 * 60_000L), now, zone)

    @Test fun eggHatches() {
        val egg = PetState.newEgg(at(12))
        assertEquals(Stage.EGG, PetEngine.advance(egg, at(12, 1), zone).stage)
        assertEquals(Stage.BABY, PetEngine.advance(egg, at(12, 3), zone).stage)
    }

    @Test fun needsDropOverTime() {
        val s = hatched(at(10))
        val later = PetEngine.advance(s, at(14), zone)
        assertTrue(later.satiety < s.satiety - 20f)
        assertTrue(later.energy < s.energy)
        assertTrue(later.poops >= 1)
    }

    @Test fun sleepsAndWakesInMorning() {
        var s = hatched(at(21, 30))
        s = PetEngine.toggleSleep(s, at(21, 30), zone).state
        assertTrue(s.sleeping)
        s = PetEngine.advance(s, at(8, 0, day = 2), zone)
        assertFalse(s.sleeping)
        assertTrue(s.energy >= 70f)
    }

    @Test fun cannotFeedWhenFull() {
        var s = hatched(at(12)).copy(satiety = 96f)
        val r = PetEngine.feedMeal(s, at(12))
        assertFalse(r.success)
        s = s.copy(satiety = 40f)
        assertEquals(70f, PetEngine.feedMeal(s, at(12)).state.satiety)
    }

    @Test fun levelUpAndEvolve() {
        val s = hatched(at(12))
        val (ns, events) = LevelSystem.addExp(s, 500)
        assertTrue(ns.level >= 5)
        assertEquals(Stage.CHILD, ns.stage)
        assertTrue(events.any { "entwickelt" in it })
    }

    @Test fun stepsGiveExp() {
        var s = PetEngine.addSteps(hatched(at(12)), 1250)
        val r = PetEngine.redeemSteps(s)
        assertEquals(50L, r.state.pendingSteps)
        assertEquals(1200L, r.state.stepsRedeemedTotal)
        s = r.state
        assertTrue(s.exp + s.level > 1)
    }

    @Test fun neglectMakesSick() {
        val s = hatched(at(10))
        val later = PetEngine.advance(s, at(10, day = 4), zone)
        assertTrue(later.health < 30f)
        assertEquals(Mood.SICK, later.mood)
    }

    @Test fun longAbsenceIsCapped() {
        val s = hatched(at(10))
        val t0 = System.nanoTime()
        PetEngine.advance(s, s.lastUpdate + 365L * 24 * hour, zone)
        assertTrue(System.nanoTime() - t0 < 2_000_000_000L)
    }

    @Test fun battleDamagePositive() {
        val a = PetCombatant(LevelSystem.addExp(hatched(at(12)), 3000).first)
        val b = PetCombatant(hatched(at(12)))
        val dmg = BattleEngine.damage(a, b, a.moves.first(), Random(1))
        assertTrue(dmg > 0)
    }
}
