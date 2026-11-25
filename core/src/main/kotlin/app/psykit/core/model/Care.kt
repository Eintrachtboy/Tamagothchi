package app.psykit.core.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Essen, das man in den Mund ziehen kann. */
@Serializable
enum class Food(val label: String, val emoji: String, val satiety: Float, val joy: Float, val sweet: Boolean = false) {
    BERRIES("Beeren", "🫐", 12f, 6f),
    FISH("Fisch", "🐟", 30f, 4f),
    RICEBALL("Reisbällchen", "🍙", 28f, 3f),
    APPLE("Apfel", "🍎", 16f, 5f),
    MUSHROOM("Pilz", "🍄", 20f, 2f),
    MILK("Milch", "🥛", 10f, 6f),
    CAKE("Kuchen", "🍰", 10f, 14f, sweet = true),
    CANDY("Bonbon", "🍬", 4f, 12f, sweet = true),
    /** Selbst geerntet, nur verfügbar, wenn Vorrat da ist. */
    GARDEN("Gartenbeeren", "🫐", 18f, 14f);

    companion object {
        /** Jedes Tierchen hat feste, aber geheime Vorlieben (abhängig vom Schlupfzeitpunkt). */
        fun likesOf(seed: Long): List<Food> = entries.filter { it != GARDEN }.shuffled(Random(seed)).take(2)
        fun dislikesOf(seed: Long): List<Food> {
            val likes = likesOf(seed)
            return entries.filter { it !in likes && !it.sweet && it != GARDEN }.shuffled(Random(seed * 31 + 7)).take(1)
        }
    }
}

/** Stellen zum Kraulen. Eine davon ist jeden Tag die geheime Lieblingsstelle. */
enum class Zone(val label: String, val row: Float, val side: Boolean) {
    // row: grobe Höhe am Körper (0 = ganz oben), side: eher am Rand als in der Mitte
    HEAD("Kopf", 0.5f, false), EARS("Ohren", 0f, true), CHEEKS("Wangen", 1f, true),
    CHIN("Kinn", 1.5f, false), BELLY("Bauch", 2.5f, false), TAIL("Schwanz", 3.5f, true);

    /** Richtungstipp von dieser Stelle zur gesuchten Stelle. */
    fun hintTowards(target: Zone): String {
        val dy = target.row - row
        return when {
            dy <= -1.5f -> "Viel weiter oben!"
            dy < -0.5f -> "Etwas weiter oben."
            dy >= 1.5f -> "Viel weiter unten!"
            dy > 0.5f -> "Etwas weiter unten."
            target.side && !side -> "Ganz nah dran! Eher seitlich."
            !target.side && side -> "Ganz nah dran! Eher in der Mitte."
            dy < 0f -> "Ganz nah dran! Ein kleines Stück höher."
            else -> "Ganz nah dran! Ein kleines Stück tiefer."
        }
    }

    companion object {
        fun favoriteOn(epochDay: Long, seed: Long): Zone = entries[Random(seed xor (epochDay * 1_000_003L)).nextInt(entries.size)]
    }
}

/** Spiele. Jeden Tag gibt es ein anderes Tagesspiel; Beerenfang ist das Bonusspiel. */
enum class GameKind(val label: String, val trains: String) {
    HIDE_SEEK("Wo steckt es?", "Tempo"),
    MEMORY("Psi-Memory", "Psi"),
    BUBBLES("Seifenblasen", "Angriff"),
    BERRIES("Beerenfang", "Abwehr"),
    REACTION("Psi-Reaktion", "Tempo"),
    ECHO("Echo", "Psi"),
    SHADOW("Schattenraten", "Abwehr"),
    COOK("Futter-Koch", "Angriff"),
    CLOUDS("Wolkenhüpfen", "Tempo"),
    VISIT("Besuch", "Vertrauen");

    companion object {
        /** Spiele in der Spiel-Auswahl (Beerenfang ist das Bonusspiel). */
        val daily = listOf(HIDE_SEEK, MEMORY, BUBBLES, REACTION, ECHO, SHADOW, COOK, CLOUDS, VISIT)
        fun dailyOn(epochDay: Long): GameKind = daily[Math.floorMod(epochDay, daily.size.toLong()).toInt()]
    }
}

/** Wie das Tierchen auf eine Aktion reagiert (für Animationen). */
enum class Reaction { NONE, HAPPY, LOVE, JUMP, ANNOYED, SPIT, SLEEPY }
