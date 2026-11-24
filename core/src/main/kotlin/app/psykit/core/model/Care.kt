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
    CANDY("Bonbon", "🍬", 4f, 12f, sweet = true);

    companion object {
        /** Jedes Tierchen hat feste, aber geheime Vorlieben (abhängig vom Schlupfzeitpunkt). */
        fun likesOf(seed: Long): List<Food> = entries.shuffled(Random(seed)).take(2)
        fun dislikesOf(seed: Long): List<Food> {
            val likes = likesOf(seed)
            return entries.filter { it !in likes && !it.sweet }.shuffled(Random(seed * 31 + 7)).take(1)
        }
    }
}

/** Stellen zum Kraulen. Eine davon ist jeden Tag die geheime Lieblingsstelle. */
enum class Zone(val label: String) {
    HEAD("Kopf"), EARS("Ohren"), CHEEKS("Wangen"), CHIN("Kinn"), BELLY("Bauch"), TAIL("Schwanz");

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
    SHADOW("Schattenraten", "Abwehr");

    companion object {
        /** Spiele in der Spiel-Auswahl (Beerenfang ist das Bonusspiel). */
        val daily = listOf(HIDE_SEEK, MEMORY, BUBBLES, REACTION, ECHO, SHADOW)
        fun dailyOn(epochDay: Long): GameKind = daily[Math.floorMod(epochDay, daily.size.toLong()).toInt()]
    }
}

/** Wie das Tierchen auf eine Aktion reagiert (für Animationen). */
enum class Reaction { NONE, HAPPY, LOVE, JUMP, ANNOYED, SPIT, SLEEPY }
