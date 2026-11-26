package app.psykit.core

import app.psykit.core.battle.AttackType
import app.psykit.core.battle.BattleEngine
import app.psykit.core.battle.Body
import app.psykit.core.battle.EnemyKind
import app.psykit.core.battle.EnemySpec
import app.psykit.core.battle.FloorRule
import app.psykit.core.battle.MoveId
import app.psykit.core.battle.MoveSource
import app.psykit.core.battle.BossKind
import app.psykit.core.dungeon.World
import app.psykit.core.model.Stage
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

    private fun fight(moves: List<MoveId>, level: Int, specs: List<EnemySpec>, world: Int, seed: Long,
                      tune: (app.psykit.core.model.PetState) -> app.psykit.core.model.PetState = { it }): Res {
        val p = tune(dt.pet(level, moves, moves))
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

    /** Was man auf Ebene [floorIndex] realistisch haben kann: Stufen-Attacken, Laden (Preis passend zur Welt),
     *  Boss-Beute früherer Welten, versteckte Attacken früherer Ebenen. Kein Training. */
    private fun available(floorIndex: Int): List<MoveId> {
        val f = Floors.all[floorIndex]
        val stage = when { f.level >= 30 -> Stage.ADULT; f.level >= 18 -> Stage.TEEN; f.level >= 8 -> Stage.CHILD; else -> Stage.BABY }
        val drops = mapOf(BossKind.MORLA to MoveId.SPORES, BossKind.KLIRR to MoveId.BLIZZARD, BossKind.FURNACE to MoveId.EMBER_BREATH, BossKind.NIGHTMARE to MoveId.NIGHTMARE_GAZE)
        val beaten = World.entries.filter { it.n < f.world }.mapNotNull { drops[it.boss] }
        return MoveId.entries.filter { m ->
            when (val src = m.source) {
                is MoveSource.FromStage -> stage.ordinal >= src.stage.ordinal
                is MoveSource.Shop -> src.price <= 150 + 100 * f.world
                is MoveSource.Hidden -> src.floor < floorIndex
                is MoveSource.Drop -> m in beaten
                else -> false
            }
        }
    }

    @Test fun simulateRealistic() {
        if (System.getenv("BALANCE") != "1") return
        val out = StringBuilder("# Balance-Simulation mit erreichbaren Attacken\n\n")
        out.append("Je Ebene: Tierchen auf Ebenen-Level, je Typ die bis dahin erreichbaren 3 stärksten Attacken dieses Typs + Ducken. ")
        out.append("Erreichbar = Stufe, Laden bis Preis 150 + 100 × Welt, Boss-Beute früherer Welten, versteckte Attacken früherer Ebenen; kein Training. ")
        out.append("$seeds Kämpfe je Feld, Kampfraum bzw. Boss. Wert = Siegquote %, „–“ = Typ dort noch nicht erreichbar.\n\n")
        val types = AttackType.entries
        out.append("| Ebene | Lv | " + types.joinToString(" | ") { "${it.emoji} ${it.label}" } + " | 🎲 Beste Mischung |\n|---|---|" + types.joinToString("") { "---|" } + "---|\n")
        val sets = StringBuilder("\n## Verwendete Sets je Welt\n\n")
        for (f in Floors.all.filter { !it.isDepth }) {
            val av = available(f.index)
            val cells = types.map { t ->
                val own = av.filter { it.type == t }.sortedByDescending { it.power * it.hits }.take(3)
                if (own.isEmpty()) "–" else {
                    val moves = own + MoveId.DUCK
                    val res = (0 until seeds).map {
                        val rng = Random(21_000L + it * 13L + f.index)
                        val specs = DungeonEngine.encounter(f, if (f.isBoss) RoomType.BOSS else RoomType.FIGHT, rng)
                        fight(moves, f.level, specs, f.world, rng.nextLong())
                    }
                    "${res.count { it.won } * 100 / res.size}"
                }
            }
            // Mischung: je stärkste Attacke der 3 verbreitetsten Schwächen-Typen
            val mixed = av.filter { it.type != null }.groupBy { it.type }.values.map { g -> g.maxBy { it.power * it.hits } }
                .sortedByDescending { it.power * it.hits }.take(3) + MoveId.DUCK
            val mixRes = (0 until seeds).map {
                val rng = Random(21_000L + it * 13L + f.index)
                val specs = DungeonEngine.encounter(f, if (f.isBoss) RoomType.BOSS else RoomType.FIGHT, rng)
                fight(mixed, f.level, specs, f.world, rng.nextLong())
            }
            out.append("| ${f.code} ${f.name} | ${f.level} | ${cells.joinToString(" | ")} | ${mixRes.count { it.won } * 100 / mixRes.size} |\n")
            if (f.sub == 1) sets.append("- Welt ${f.world}: " + types.joinToString("; ") { t ->
                "${t.emoji} " + av.filter { it.type == t }.sortedByDescending { it.power * it.hits }.take(3).joinToString { it.label }.ifEmpty { "–" }
            } + "\n")
        }
        out.append(sets)
        out.append("\nHinweis: einfache Spiel-KI aus DungeonTest.choose, ohne Tränke, ohne Training, Regeln der Ebenen ausgeschaltet.\n")
        val file = File("build/balance-realistic.md")
        file.parentFile.mkdirs()
        file.writeText(out.toString())
        println(out)
    }

    /**
     * Dritte Messung: Wertentwicklung. Angriff wächst im Spiel mit 2·Level, Psi mit 3·Level (Battle.kt).
     * Varianten werden über Trainingspunkte nachgebildet (1 Training = +2 Wert), ohne Spielcode zu ändern:
     * Angriff 2,5·Level = +Level/4 Training, Angriff 3·Level = +Level/2 Training.
     * Trainingsstand „typisch“ = jeder Wert Level/2 Training (zusätzlich).
     */
    @Test fun simulateGrowth() {
        if (System.getenv("BALANCE") != "1") return
        val n = 80
        val types = AttackType.entries
        val growth = listOf("Angriff 2·Lv (jetzt)" to 0.0, "Angriff 2,5·Lv" to 0.25, "Angriff 3·Lv" to 0.5)
        val training = listOf("ohne Training" to 0.0, "typisches Training (Lv/2 je Wert)" to 0.5)
        val floors = Floors.all.filter { !it.isDepth }
        val out = StringBuilder("# Balance: Wertentwicklung\n\n")
        out.append("Erreichbare Attacken je Ebene wie in der zweiten Messung. $n Kämpfe je Feld. Normale Ebenen = Kampfräume aller 15 Nicht-Boss-Ebenen.\n\n")
        out.append("| Wachstum | Training | " + types.joinToString(" | ") { "${it.emoji}" } + " | Spanne | 🎯 Bosse mit Schwäche-Set |\n|---|---|" + types.joinToString("") { "---|" } + "---|---|\n")
        val detail = StringBuilder()
        for ((gName, g) in growth) for ((tName, tr) in training) {
            fun tune(lv: Int): (app.psykit.core.model.PetState) -> app.psykit.core.model.PetState = { p ->
                val extraAtk = (lv * g).toInt()
                val t = (lv * tr).toInt()
                p.copy(trainAtk = extraAtk + t, trainDef = t, trainSpd = t, trainPsi = t)
            }
            val perType = types.map { t ->
                val res = floors.filter { !it.isBoss }.flatMap { f ->
                    val own = available(f.index).filter { it.type == t }.sortedByDescending { it.power * it.hits }.take(3)
                    if (own.isEmpty()) emptyList() else (0 until n).map {
                        val rng = Random(31_000L + it * 13L + f.index)
                        fight(own + MoveId.DUCK, f.level, DungeonEngine.encounter(f, RoomType.FIGHT, rng), f.world, rng.nextLong(), tune(f.level))
                    }
                }
                if (res.isEmpty()) 0 else res.count { it.won } * 100 / res.size
            }
            // Spieler-nahes Set für Bosse: stärkste Attacke des Schwäche-Typs + 2 stärkste andere + Ducken
            val bossRes = floors.filter { it.isBoss }.map { f ->
                val av = available(f.index).filter { it.type != null }.sortedByDescending { it.power * it.hits }
                val weak = DungeonEngine.weakness(f)
                val set = (listOfNotNull(av.firstOrNull { it.type == weak }) + av.filter { it.type != weak }.take(2)).take(3) + MoveId.DUCK
                val wins = (0 until n).count {
                    val rng = Random(41_000L + it * 13L + f.index)
                    fight(set, f.level, DungeonEngine.encounter(f, RoomType.BOSS, rng), f.world, rng.nextLong(), tune(f.level)).won
                }
                f.name.substringBefore(',') to wins * 100 / n
            }
            out.append("| $gName | $tName | ${perType.joinToString(" | ")} | ${perType.max() - perType.min()} | ${bossRes.joinToString(", ") { "${it.first} ${it.second}" }} |\n")
        }
        out.append("\nSpanne = beste minus schlechteste Siegquote der sechs Typen (kleiner = ausgeglichener).\n")
        out.append("Hinweis: einfache Spiel-KI, ohne Tränke, Ausrüstung und Ebenenregeln.\n")
        val file = File("build/balance-growth.md")
        file.parentFile.mkdirs()
        file.writeText(out.toString())
        println(out)
    }
}
