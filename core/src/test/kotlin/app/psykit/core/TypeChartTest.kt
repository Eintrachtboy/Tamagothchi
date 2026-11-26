package app.psykit.core

import app.psykit.core.battle.AttackType
import app.psykit.core.battle.Body
import app.psykit.core.battle.TypeChart
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeChartTest {
    @Test fun matrixSymbolMatchesLabel() {
        // Runde 15: Matrix-Zeichen und Wort neben der Attacke kommen aus demselben Faktor
        val expected = mapOf("sehr effektiv" to "++", "effektiv" to "+", "fast wirkungslos" to "−−", "wenig effektiv" to "−", "" to "·")
        for (t in AttackType.entries) for (b in Body.entries) {
            val f = TypeChart.factor(t, b)
            assertEquals(expected.getValue(TypeChart.label(f)), TypeChart.symbol(f), "$t gegen $b")
        }
    }
}
