package app.psykit.core

import app.psykit.core.battle.AttackType
import app.psykit.core.battle.BattleEngine
import app.psykit.core.battle.Body
import app.psykit.core.battle.EnemyKind
import app.psykit.core.battle.EnemySpec
import app.psykit.core.battle.FloorRule
import app.psykit.core.battle.MoveId
import app.psykit.core.battle.Outcome
import app.psykit.core.dungeon.DungeonEngine
import app.psykit.core.dungeon.Floors
import app.psykit.core.dungeon.RoomType
import java.io.File
import kotlin.random.Random
import kotlin.test.Test

/**
 * Runde 18: Balance der Wirkungstafel. Läuft nur mit Umgebungsvariable BALANCE=1, damit der
 * normale Testlauf nicht länger dauert. Ändert keine Werte, schreibt nur core/build/balance-report.md.
 *
 * Je Attackentyp ein typreines Set (die 3 stärksten Attacken des Typs + Ducken) plus ein gemischtes
 * Referenz-Set. Gemessen werden 1-gegen-1-Kämpfe je Gegnerkörper und normale Kampfräume je Welt.
 */
class BalanceSimulation {
    private val dt = DungeonTest()
    private val seeds = 120

    private fun loadouts(): Map<String, List<MoveId>> {
        val byType = AttackType.entries.associate { t ->
            "${t.emoji} ${t.label}" to (MoveId.entries.filter { it.type == t }.sortedByDescending { it.power * it.hits }.take(3) + MoveId.DUCK)
        }
        return byType + ("🎲 Gemischt" to listOf(MoveId.PSI_WAVE, MoveId.BELLY_FLOP, MoveId.FROST_BREATH, MoveId.DUCK))
    }

    private data class Res(val won: Boolean, val rounds: Int, val hpLeft: Double)

    private fun fight(moves: List<MoveId>, level: Int, specs: List<EnemySpec>, world: Int, seed: Long): Res {
        val p = dt.pet(level, moves, moves)
        var st = BattleEngine.start(p, specs, FloorRule.NONE, world, seed)
        var guard = 0
        while (st.outcome == null && guard++ < 80) st = BattleEngine.playRound(st, dt.choose(st))
        return Res(st.outcome == Outcome.WON, st.round, st.pet.hpPct().toDouble().coerceAtLeast(0.0))
    }

    private fun worldLevel(w: Int) = Floors.all.filter { it.world == w && !it.isBoss && !it.isDepth }.map { it.level }.average().toInt()

    @Test fun simulate() {
        if (System.getenv("BALANCE") != "1") return
        val lo = loadouts()
        val out = StringBuilder("# Balance-Simulation Wirkungstafel\n\n")
        out.append("Sets (je 3 stärkste Attacken des Typs + Ducken):\n\n")
        lo.forEach { (n, m) -> out.append("- $n: ${m.joinToString { "${it.label} (${it.power}×${it.hits}, ${it.psiCost} Psi)" }}\n") }

        // 1) Duelle je Gegnerkörper: jeder Gegner seiner Welt auf Weltlevel, Tierchen gleiches Level
        out.append("\n## 1 gegen 1 je Körper (Siegquote % / Ø Runden bei Sieg / Ø KP übrig %)\n\n")
        out.append("| Set | " + Body.entries.joinToString(" | ") { "${it.emoji} ${it.label}" } + " |\n")
        out.append("|---|" + Body.entries.joinToString("") { "---|" } + "\n")
        lo.forEach { (name, moves) ->
            val cells = Body.entries.map { b ->
                val res = EnemyKind.entries.filter { it.body == b && it.world in 1..5 }.flatMap { k ->
                    val lv = worldLevel(k.world).coerceAtLeast(5)
                    (0 until seeds).map { fight(moves, lv, listOf(EnemySpec(k, level = lv)), k.world, 7_000L + it * 31L + k.ordinal) }
                }
                if (res.isEmpty()) "–" else {
                    val wins = res.filter { it.won }
                    "%d / %.1f / %d".format(wins.size * 100 / res.size, wins.map { it.rounds }.average().takeIf { !it.isNaN() } ?: 0.0, (wins.map { it.hpLeft }.average().takeIf { !it.isNaN() } ?: 0.0).times(100).toInt())
                }
            }
            out.append("| $name | ${cells.joinToString(" | ")} |\n")
        }

        // 2) Normale Kampfräume je Welt (gemischte Gegnergruppen aus DungeonEngine.encounter)
        out.append("\n## Kampfräume je Welt (Siegquote %)\n\n")
        val worlds = Floors.all.filter { !it.isDepth }.map { it.world }.distinct().sorted()
        out.append("| Set | " + worlds.joinToString(" | ") { "Welt $it" } + " |\n|---|" + worlds.joinToString("") { "---|" } + "\n")
        lo.forEach { (name, moves) ->
            val cells = worlds.map { w ->
                val floors = Floors.all.filter { it.world == w && !it.isBoss && !it.isDepth }
                val res = floors.flatMap { f ->
                    (0 until seeds).map {
                        val rng = Random(11_000L + it * 17L + f.index)
                        fight(moves, f.level, DungeonEngine.encounter(f, RoomType.FIGHT, rng), w, rng.nextLong())
                    }
                }
                "${res.count { it.won } * 100 / res.size}"
            }
            out.append("| $name | ${cells.joinToString(" | ")} |\n")
        }
        out.append("\nHinweis: einfache Spiel-KI aus DungeonTest.choose, ohne Tränke, ohne Training, Regeln der Ebenen ausgeschaltet.\n")
        val f = File("build/balance-report.md")
        f.parentFile.mkdirs()
        f.writeText(out.toString())
        println(out)
    }
}
