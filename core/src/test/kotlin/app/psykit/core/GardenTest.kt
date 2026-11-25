package app.psykit.core

import app.psykit.core.engine.GardenEngine
import app.psykit.core.model.Berry
import app.psykit.core.model.Item
import app.psykit.core.model.PetState
import app.psykit.core.model.Plot
import app.psykit.core.model.PlotStage
import app.psykit.core.model.Stage
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GardenTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val hour = 3_600_000L
    private fun at(h: Int, day: Int = 5) = LocalDateTime.of(2026, 3, day, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun pet() = PetState.newEgg(at(8, 1)).copy(stage = Stage.BABY, stardust = 1000, level = 10)
    private fun planted(berry: Berry = Berry.BLUE, at: Long = at(9)) = pet().copy(plots = listOf(Plot(plantedAt = at, v = 1, berry = berry.name), Plot(v = 1), Plot(v = 1)))

    @Test fun weedSlowsAndFertilizerSpeedsUp() {
        val s = planted()
        val p = s.plots[0]
        assertEquals(1f, GardenEngine.speed(s, p), 0.001f)
        assertEquals(0.75f, GardenEngine.speed(s, p.copy(weedSince = 1)), 0.001f)
        assertEquals(1.5f, GardenEngine.speed(s, p.copy(fertilized = true)), 0.001f)
        // Mit Unkraut reichen 4 Stunden Abstand nicht mehr
        var w = GardenEngine.water(s, 0, at(9)).state
        w = w.copy(plots = w.plots.mapIndexed { i, q -> if (i == 0) q.copy(weedSince = at(9)) else q })
        assertEquals(1, GardenEngine.water(w, 0, at(13)).state.plots[0].waterings)
        assertEquals(2, GardenEngine.water(w, 0, at(15)).state.plots[0].waterings)
        // Gedüngt: schon nach knapp 3 Stunden
        var f = GardenEngine.water(s.copy(fertilizer = 1), 0, at(9)).state
        f = GardenEngine.fertilize(f, 0, at(9)).state
        assertEquals(2, GardenEngine.water(f, 0, at(12)).state.plots[0].waterings)
    }

    @Test fun blueberryWithersAfterADayWithoutWater() {
        val s = planted()
        assertEquals(PlotStage.SEED, GardenEngine.stage(s.plots[0], at(9) + 23 * hour, s))
        assertEquals(PlotStage.WITHERED, GardenEngine.stage(s.plots[0], at(9) + 25 * hour, s))
        // Kupferkanne: 50 % länger
        assertEquals(PlotStage.SEED, GardenEngine.stage(s.plots[0], at(9) + 30 * hour, s.copy(canLevel = 1)))
    }

    @Test fun snailBlocksRipeningAndBitesBackButNeverDestroys() {
        var s = planted(Berry.STAR)
        s = s.copy(plots = s.plots.mapIndexed { i, q -> if (i == 0) q.copy(waterings = 2, lastWaterAt = at(10), lastCountedAt = at(1), snailSince = at(10), rolledDay = 99999) else q })
        // Schnecke da: wird nicht reif
        val w = GardenEngine.water(s, 0, at(10)).state
        assertEquals(2, w.plots[0].waterings)
        // nach 6 aktiven Stunden eine Stufe zurück
        val r = GardenEngine.refresh(w.copy(plots = w.plots.map { it.copy(rolledDay = 0) }), at(16, 5) + 1, zone)
        assertTrue(r.plots[0].waterings <= 1)
        // Nachts (23–7 Uhr) zählt nicht
        assertEquals(0L, GardenEngine.activeSnailMillis(at(23), at(7, 6), zone))
        assertEquals(16 * hour, GardenEngine.activeSnailMillis(at(0), at(0, 6), zone))
        // Nie unter 0
        val far = GardenEngine.refresh(w, at(22, 7), zone)
        assertTrue(far.plots[0].waterings >= 0)
        assertFalse(far.plots[0].empty)
        // Wegtippen hilft
        assertEquals(0L, GardenEngine.removeSnail(w, 0).state.plots[0].snailSince)
    }

    @Test fun snailsOnlyBetween7And23() {
        // Viele Tage auswürfeln: jede geplante Schnecke liegt zwischen 7 und 23 Uhr
        var count = 0
        for (d in 1..28) {
            var s = planted(Berry.GOLD, at(6, d)).copy(bornAt = d * 977L)
            s = s.copy(plots = s.plots.mapIndexed { i, q -> if (i == 0) q.copy(waterings = 1, lastWaterAt = at(6, d)) else q })
            val r = GardenEngine.refresh(s, at(6, d), zone)
            val t = r.plots[0].snailAt
            if (t > 0) {
                count++
                val h = java.time.Instant.ofEpochMilli(t).atZone(zone).hour
                assertTrue(h in 7..22, "Schnecke um $h Uhr")
            }
        }
        assertTrue(count < 14, "Schnecken zu häufig: $count von 28")
    }

    @Test fun harvestBonusForCleanCare() {
        val p = Plot(plantedAt = 1, v = 1, berry = Berry.BLUE.name, waterings = 2, cleanWaterings = 2)
        assertEquals(4, GardenEngine.harvestCount(p))
        assertEquals(3, GardenEngine.harvestCount(p.copy(cleanWaterings = 0)))
    }

    @Test fun bedsCansAndCompost() {
        var s = pet().copy(level = 5)
        assertFalse(GardenEngine.unlockBed(s).success) // Level 8 nötig
        s = GardenEngine.unlockBed(s.copy(level = 8)).state
        assertEquals(4, s.bedsUnlocked)
        assertEquals(4, GardenEngine.beds(s).size)
        s = GardenEngine.buyCan(s).state
        assertEquals(1, s.canLevel)
        repeat(3) { s = GardenEngine.addCompost(s) }
        assertEquals(1, s.fertilizer)
        assertEquals(0, s.compost)
    }

    @Test fun smoothiesAndGoals() {
        var s = pet().copy(berryStock = 10, starBerries = 4, psiBerries = 4, goldBerries = 1, energy = 50f)
        s = GardenEngine.mix(s, 1, 2, 0, 0).state
        assertEquals(70f, s.energy, 0.01f)
        assertTrue("STAR" in s.smoothiesFound)
        val matsch = GardenEngine.mix(s, 0, 1, 1, 0)
        assertTrue(matsch.state.smoothiesFound.size == 1)
        s = GardenEngine.mix(s, 3, 0, 0, 0).state
        s = GardenEngine.mix(s, 0, 0, 2, 0).state
        s = GardenEngine.mix(s, 2, 0, 1, 0).state
        s = GardenEngine.mix(s, 1, 1, 0, 1).state
        assertEquals(5, s.smoothiesFound.size)
        assertTrue(Item.GLASS_MIXER.name in s.items)
    }

    @Test fun plantingCostsAndGoldNeedsSeed() {
        val s = pet()
        assertEquals(980, GardenEngine.plant(s, 0, Berry.STAR, at(9)).state.stardust)
        assertFalse(GardenEngine.plant(s, 0, Berry.GOLD, at(9)).success)
        assertTrue(GardenEngine.plant(s.copy(goldSeeds = 1), 0, Berry.GOLD, at(9)).success)
    }
}
