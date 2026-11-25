package app.psykit.core

import app.psykit.core.dungeon.Shop
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnergyDrinkTest {
    private fun pet() = PetState.newEgg(0L, "Lumi").copy(stage = Stage.BABY, energy = 20f, stardust = 500, questDay = 10, sleeping = true)

    @Test fun drinkGivesEnergyAndWakes() {
        val r = Shop.drinkEnergy(pet())
        assertTrue(r.success)
        assertEquals(60f, r.state.energy)
        assertFalse(r.state.sleeping)
        assertEquals(450, r.state.stardust)
    }

    @Test fun limitPerDayResetsNextDay() {
        var p = pet()
        repeat(3) { p = Shop.drinkEnergy(p.copy(energy = 10f)).state }
        val fourth = Shop.drinkEnergy(p.copy(energy = 10f))
        assertFalse(fourth.success)
        assertEquals(0, Shop.drinksLeft(p))
        assertTrue(Shop.drinkEnergy(p.copy(energy = 10f, questDay = 11)).success)
    }

    @Test fun capsAtHundred() {
        assertEquals(100f, Shop.drinkEnergy(pet().copy(energy = 90f)).state.energy)
    }
}
