package app.psykit.core

import app.psykit.core.battle.BattleEngine
import app.psykit.core.battle.PetCombatant
import app.psykit.core.engine.LevelSystem
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Food
import app.psykit.core.model.GameKind
import app.psykit.core.model.Mood
import app.psykit.core.model.Reaction
import app.psykit.core.model.Zone
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
        s = PetEngine.putToBed(s, at(21, 30), zone, hummed = false).state
        assertTrue(s.sleeping)
        s = PetEngine.advance(s, at(8, 0, day = 2), zone)
        assertFalse(s.sleeping)
        assertTrue(s.energy >= 70f)
    }

    private fun neutralFood(s: PetState) = Food.entries.first {
        !it.sweet && it !in Food.likesOf(s.bornAt) && it !in Food.dislikesOf(s.bornAt)
    }

    @Test fun cannotFeedWhenFull() {
        var s = hatched(at(12)).copy(satiety = 96f)
        val food = neutralFood(s)
        assertFalse(PetEngine.feed(s, food, at(12)).success)
        s = s.copy(satiety = 40f)
        assertEquals(40f + food.satiety, PetEngine.feed(s, food, at(12)).state.satiety)
    }

    @Test fun likesAndDislikesAreRevealed() {
        val s = hatched(at(12)).copy(satiety = 30f)
        val like = Food.likesOf(s.bornAt).first()
        val r = PetEngine.feed(s, like, at(12))
        assertTrue(like in r.state.knownLikes)
        assertEquals(Reaction.LOVE, r.reaction)
        val dislike = Food.dislikesOf(s.bornAt).first()
        val r2 = PetEngine.feed(s, dislike, at(12))
        assertEquals(Reaction.SPIT, r2.reaction)
        assertTrue(dislike in r2.state.knownDislikes)
    }

    @Test fun tooManySweetsMakeSluggish() {
        var s = hatched(at(12)).copy(satiety = 30f)
        val sweet = Food.entries.first { it.sweet && it !in Food.dislikesOf(s.bornAt) }
        repeat(3) { s = PetEngine.feed(s, sweet, at(12, it)).state }
        assertTrue(s.sluggishUntil > at(12))
        assertTrue(PetEngine.advance(s, at(12, 5), zone).sluggish)
        assertTrue(PetEngine.canPlay(PetEngine.advance(s, at(12, 5), zone)) != null)
    }

    @Test fun strokeSpeedMatters() {
        val s = hatched(at(12)).copy(happiness = 50f)
        assertTrue(PetEngine.stroke(s, 300f, at(12)).state.happiness > 50f)
        val fast = PetEngine.stroke(s, 3000f, at(12))
        assertTrue(fast.state.happiness < 50f)
        assertTrue(fast.state.trust < s.trust)
    }

    @Test fun favoriteScratchSpot() {
        val s = hatched(at(12))
        val fav = Zone.favoriteOn(PetEngine.epochDay(at(12), zone), s.bornAt)
        val r = PetEngine.scratch(s, fav, at(12), zone)
        assertEquals(Reaction.JUMP, r.reaction)
        assertTrue(r.state.trust > s.trust)
        // zweites Mal am selben Tag kein Bonus mehr
        assertEquals(Reaction.HAPPY, PetEngine.scratch(r.state, fav, at(13), zone).reaction)
    }

    @Test fun walksMakeDirtyAndCleaningHelps() {
        var s = PetEngine.addSteps(hatched(at(12)), 3200)
        assertEquals(2, s.dirt)
        assertTrue(PetEngine.dirtSpots(s) >= 2)
        s = PetEngine.clean(s, 1f).state
        assertEquals(0, s.dirt)
        assertEquals(100f, s.hygiene)
    }

    @Test fun gamesTrainStats() {
        val s = hatched(at(12))
        val r = PetEngine.gameResult(s, GameKind.MEMORY, 6, 6)
        assertEquals(2, r.state.trainPsi)
        assertTrue(GameKind.dailyOn(0) in GameKind.daily)
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
