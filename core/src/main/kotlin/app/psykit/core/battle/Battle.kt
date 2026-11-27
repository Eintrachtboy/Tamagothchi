package app.psykit.core.battle

import app.psykit.core.model.PetState
import app.psykit.core.model.Stage

/*
 * Kampfwerte des Tierchens. Sie werden aus dem Pflegezustand abgeleitet: Level macht stärker,
 * eingelöste Schritte bringen Ausdauer, gute Pflege bringt Tagesform (0,5 bis 1,0).
 * Der eigentliche Kampf steckt in [BattleEngine].
 */

data class BattleStats(val maxHp: Int, val attack: Int, val defense: Int, val speed: Int, val psi: Int) {
    companion object {
        fun form(p: PetState): Float = ((p.health + p.happiness + p.trust) / 300f).coerceIn(0.5f, 1f)

        fun of(p: PetState): BattleStats {
            val lv = p.level
            val stamina = (p.stepsRedeemedTotal / 2000).toInt().coerceAtMost(40)
            val form = form(p)
            fun f(x: Int) = (x * form).toInt().coerceAtLeast(1)
            return BattleStats(
                maxHp = f(20 + lv * 5 + stamina * 2),
                // Runde 19: Angriff wächst wie Psi mit 3 je Level (vorher 2), damit Krallen und Wucht mithalten
                attack = f(5 + lv * 3 + p.trainAtk * 2),
                defense = f(5 + lv * 2 + stamina / 2 + p.trainDef * 2),
                speed = f(5 + lv * 2 + stamina + p.trainSpd * 2),
                psi = f(8 + lv * 3 + p.trainPsi * 2),
            )
        }

        /** Grundwerte eines Gegners auf Level [lv] (vor Rollen-Faktoren). */
        fun enemyBase(lv: Int) = BattleStats(20 + 5 * lv, 5 + 2 * lv, 5 + 2 * lv, 5 + 2 * lv, 8 + 3 * lv)
    }
}

/** Für die Werte-Anzeige: Kampfwerte und Attacken des Tierchens. */
class PetCombatant(pet: PetState) {
    val name = pet.name
    val stats = BattleStats.of(pet)
    val moves: List<MoveId> = if (pet.stage == Stage.EGG) emptyList() else MoveId.equipped(pet)
}
