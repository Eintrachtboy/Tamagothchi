package app.psykit.core.battle

import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import kotlin.random.Random

/*
 * Vorbereitung für das spätere Kampfsystem.
 * Die Kampfwerte werden aus dem Pflegezustand abgeleitet: Level macht stärker,
 * eingelöste Schritte bringen Ausdauer, gute Pflege bringt Tagesform.
 * Ein späterer Kampfbildschirm braucht nur [Combatant] + [BattleEngine].
 */

enum class Element { PSI, NORMAL, SHADOW, LIGHT }

data class Move(val name: String, val power: Int, val element: Element, val accuracy: Int = 95)

data class BattleStats(val maxHp: Int, val attack: Int, val defense: Int, val speed: Int, val psi: Int) {
    companion object {
        fun of(p: PetState): BattleStats {
            val lv = p.level
            val stamina = (p.stepsRedeemedTotal / 2000).toInt().coerceAtMost(40)
            val form = ((p.health + p.happiness) / 200f).coerceIn(0.5f, 1f)
            fun f(x: Int) = (x * form).toInt().coerceAtLeast(1)
            return BattleStats(
                maxHp = f(20 + lv * 5 + stamina * 2),
                attack = f(5 + lv * 2),
                defense = f(5 + lv * 2 + stamina / 2),
                speed = f(5 + lv * 2 + stamina),
                psi = f(8 + lv * 3),
            )
        }
    }
}

interface Combatant {
    val name: String
    val stats: BattleStats
    val moves: List<Move>
}

/** Das eigene Haustier als Kämpfer. */
class PetCombatant(pet: PetState) : Combatant {
    override val name = pet.name
    override val stats = BattleStats.of(pet)
    override val moves = buildList {
        add(Move("Tatzenhieb", 30, Element.NORMAL))
        if (pet.stage >= Stage.CHILD) add(Move("Psi-Welle", 45, Element.PSI, 90))
        if (pet.stage >= Stage.TEEN) add(Move("Mondschild", 0, Element.LIGHT, 100))
        if (pet.stage >= Stage.ADULT) add(Move("Sternensturm", 80, Element.PSI, 80))
    }
}

object BattleEngine {
    /** Schaden eines Angriffs; 0 bei Fehlschlag. Absichtlich einfach gehalten. */
    fun damage(attacker: Combatant, defender: Combatant, move: Move, rng: Random = Random.Default): Int {
        if (move.power == 0) return 0
        if (rng.nextInt(100) >= move.accuracy) return 0
        val atk = if (move.element == Element.PSI) attacker.stats.psi else attacker.stats.attack
        val base = move.power * atk / (defender.stats.defense + 10)
        val variance = 0.85 + rng.nextDouble() * 0.15
        return (base * variance).toInt().coerceAtLeast(1)
    }
}
