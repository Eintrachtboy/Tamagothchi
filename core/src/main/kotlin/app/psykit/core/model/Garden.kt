package app.psykit.core.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Ein Beet im Garten. plantedAt = 0 heißt leer. Zeiten in Millisekunden. */
@Serializable
data class Plot(
    val plantedAt: Long = 0,
    // alte Felder (vor Garten 2.0), werden beim ersten Laden umgerechnet
    val lastWateredDay: Long = -1,
    val waterDays: Int = 0,
    // Garten 2.0
    val v: Int = 0,
    val berry: String = "BLUE",
    val waterings: Int = 0,           // gezählte Gießgänge (Wachstum)
    val lastWaterAt: Long = 0,        // letztes Wasser überhaupt (gegen Vertrocknen)
    val lastCountedAt: Long = 0,      // letzter Gießgang, der Wachstum gebracht hat
    val cleanWaterings: Int = 0,      // Gießgänge ohne Unkraut und Schnecke (Ernte-Bonus)
    val fertilized: Boolean = false,
    val rolledDay: Long = -1,         // Tag, für den Unkraut/Schnecke schon ausgewürfelt sind
    val weedAt: Long = 0,             // geplantes Auftauchen
    val weedSince: Long = 0,          // > 0 = Unkraut da
    val snailAt: Long = 0,
    val snailSince: Long = 0,         // > 0 = Schnecke da
    val snailBites: Int = 0,
    val rainedDay: Long = -1,
) {
    val empty get() = plantedAt == 0L
    val kind get() = Berry.parse(berry)
}

/** Beerensorten. [gapHours] = Abstand zwischen zwei Gießgängen, die Wachstum bringen. */
enum class Berry(
    val label: String,
    val emoji: String,
    val color: Long,
    val needWaterings: Int,
    val gapHours: Float,
    val witherHours: Float,
    val harvest: Int,
    val maxBonus: Int,
    val sellPrice: Int,
    /** Samenpreis in Sternenstaub, -1 = nur aus Dungeon-Truhen. */
    val seedPrice: Int,
) {
    BLUE("Blaubeere", "🫐", 0xFF4A6FD8, 2, 4f, 24f, 3, 1, 3, 0),
    STAR("Sternbeere", "⭐", 0xFFF2C230, 3, 8f, 36f, 3, 2, 12, 20),
    PSI("Psi-Beere", "💜", 0xFFA45CE0, 4, 8f, 36f, 2, 2, 8, 30),
    GOLD("Goldbeere", "🌟", 0xFFFFB300, 5, 10f, 48f, 1, 1, 40, -1);

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name } ?: BLUE
    }
}

/** Smoothies aus dem Mixer. Rezept = Anzahl je Sorte (BLUE, STAR, PSI, GOLD). */
enum class Smoothie(val label: String, val emoji: String, val recipe: String, val effect: String) {
    SHAKE("Blaubeer-Shake", "🥤", "3 Blaubeeren", "+30 Sättigung, +10 Laune"),
    STAR("Sternen-Smoothie", "🌠", "2 Sternbeeren + 1 Blaubeere", "+20 Energie"),
    PSI("Psi-Mix", "🔮", "2 Psi-Beeren", "+25 EP"),
    HEAL("Heil-Smoothie", "💚", "1 Psi-Beere + 2 Blaubeeren", "+20 Gesundheit"),
    GOLD("Goldtrunk", "🏆", "1 Goldbeere + 2 beliebige Beeren", "+60 EP, volle Laune und Energie");

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name }
        /** Welches Rezept passt zu genau diesen Zutaten? null = Matsch. */
        fun match(blue: Int, star: Int, psi: Int, gold: Int): Smoothie? {
            val total = blue + star + psi + gold
            return when {
                gold == 1 && total == 3 -> GOLD
                gold > 0 -> null
                blue == 3 && total == 3 -> SHAKE
                star == 2 && blue == 1 && total == 3 -> STAR
                psi == 2 && total == 2 -> PSI
                psi == 1 && blue == 2 && total == 3 -> HEAL
                else -> null
            }
        }
    }
}

/** Ziele im Garten-Sammelbuch. */
enum class GardenGoal(val label: String, val reward: String) {
    ALL_KINDS("Jede Sorte einmal geerntet", "Eintrag im Sammelbuch"),
    HARVEST_10("10 Ernten", "Deko Vogelscheuche"),
    HARVEST_25("25 Ernten", "Deko Gartenzwerg"),
    ALL_SMOOTHIES("Alle 5 Smoothies entdeckt", "Deko Glas-Mixer"),
    MASTER("Alle Sorten und 50 Ernten", "Titel Meistergärtner und Gießkannenhut"),
}

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
    HARVEST("Beeren ernten", 1, 6),
    WEED("Unkraut jäten", 1, 5),
    SMOOTHIE("Einen Smoothie mixen", 1, 6),
    DEFEAT("5 Gegner im Dungeon besiegen", 5, 10),
    CHEST("2 Truhen im Dungeon öffnen", 2, 8),
    SUBLEVEL("Eine Dungeon-Ebene schaffen", 1, 12);

    val dungeon get() = this == DEFEAT || this == CHEST || this == SUBLEVEL

    companion object {
        fun forDay(epochDay: Long, seed: Long, dungeon: Boolean = false): List<Quest> {
            val r = Random(seed xor (epochDay * 7_368_787L))
            // Schritte sind fast immer dabei, der Rest zufällig. Dungeon-Aufgaben erst, wenn der Dungeon offen ist.
            val rest = (entries - STEPS).filter { !it.dungeon }.shuffled(r).let { base ->
                if (!dungeon) base else {
                    val d = entries.filter { it.dungeon }.shuffled(r)
                    // Mit etwa jeder zweiten Liste kommt eine Dungeon-Aufgabe dazu
                    if (r.nextBoolean()) listOf(d.first()) + base else base
                }
            }
            return if (r.nextInt(4) != 0) listOf(STEPS) + rest.take(2) else rest.take(3)
        }
    }
}
