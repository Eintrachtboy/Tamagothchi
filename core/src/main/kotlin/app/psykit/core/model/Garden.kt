package app.psykit.core.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Ein Beet im Garten. plantedAt = 0 heißt leer. */
@Serializable
data class Plot(
    val plantedAt: Long = 0,
    val lastWateredDay: Long = -1,
    val waterDays: Int = 0,
)

enum class PlotStage(val label: String, val emoji: String) {
    EMPTY("leer", "🟫"),
    SEED("Samen gesät", "🌰"),
    SPROUT("Keimling", "🌱"),
    BUSH("Busch", "🌿"),
    RIPE("reif", "🫐"),
    WITHERED("vertrocknet", "🥀"),
}

/** Tagesaufgaben: jeden Tag 3 zufällige. */
enum class Quest(val label: String, val target: Int, val rewardExp: Int) {
    STEPS("3000 Schritte gehen", 3000, 12),
    FEED("3× füttern", 3, 6),
    STROKE("5× streicheln", 5, 5),
    PLAY("2 Spiele spielen", 2, 8),
    CLEAN("Einmal putzen", 1, 5),
    WATER("Den Garten gießen", 1, 5),
    SCRATCH("Die Lieblingsstelle finden", 1, 8),
    HARVEST("Beeren ernten", 1, 6);

    companion object {
        fun forDay(epochDay: Long, seed: Long): List<Quest> {
            val r = Random(seed xor (epochDay * 7_368_787L))
            // Schritte sind fast immer dabei, der Rest zufällig
            val rest = (entries - STEPS).shuffled(r)
            return if (r.nextInt(4) != 0) listOf(STEPS) + rest.take(2) else rest.take(3)
        }
    }
}
