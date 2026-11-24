package app.psykit.core

import app.psykit.core.battle.AttackType
import app.psykit.core.battle.BattleEngine
import app.psykit.core.battle.BattleState
import app.psykit.core.battle.Body
import app.psykit.core.battle.EnemyKind
import app.psykit.core.battle.EnemySpec
import app.psykit.core.battle.FloorRule
import app.psykit.core.battle.IntentKind
import app.psykit.core.battle.MoveId
import app.psykit.core.battle.Outcome
import app.psykit.core.battle.PetAction
import app.psykit.core.battle.Potion
import app.psykit.core.battle.Role
import app.psykit.core.battle.Status
import app.psykit.core.battle.TypeChart
import app.psykit.core.dungeon.DungeonEngine
import app.psykit.core.dungeon.EventKind
import app.psykit.core.dungeon.Floors
import app.psykit.core.dungeon.RoomType
import app.psykit.core.dungeon.Shop
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Item
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DungeonTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Tierchen auf Level [lv] ohne Training, gut gepflegt. */
    fun pet(lv: Int, moves: List<MoveId> = emptyList(), equip: List<MoveId> = emptyList()): PetState {
        val stage = when { lv >= 30 -> Stage.ADULT; lv >= 18 -> Stage.TEEN; lv >= 8 -> Stage.CHILD; else -> Stage.BABY }
        val base = PetState(bornAt = 0, lastUpdate = 0, level = lv, stage = stage, health = 100f, happiness = 90f, trust = 80f,
            energy = 100f, satiety = 100f, keys = 3, ownedMoves = moves.map { it.name })
        val eq = equip.ifEmpty { if (moves.isEmpty()) emptyList() else MoveId.equipped(base.copy(ownedMoves = emptyList())).take(4 - moves.size) + moves }
        return base.copy(equippedMoves = eq.map { it.name })
    }

    @Test fun typeChartMatchesConcept() {
        assertEquals(1.5, TypeChart.factor(AttackType.BLUNT, Body.ARMORED))
        assertEquals(0.5, TypeChart.factor(AttackType.CLAW, Body.ARMORED))
        assertEquals(2.0, TypeChart.factor(AttackType.FIRE, Body.PLANT))
        assertEquals(2.0, TypeChart.factor(AttackType.PSI, Body.GHOST))
        assertEquals(1.0, TypeChart.factor(AttackType.FROST, null))
    }

    @Test fun catalogsAreComplete() {
        assertEquals(25, EnemyKind.entries.count { it != EnemyKind.SLIMELING })
        assertEquals(26, MoveId.entries.size)
        assertEquals(33, Item.entries.size)
        (1..5).forEach { w -> assertEquals(5, Item.ofWorld(w).size); assertEquals(5, EnemyKind.ofWorld(w).size) }
        assertEquals(20, Floors.all.size)
    }

    @Test fun enemyStatsFollowRoleFactors() {
        val slime = BattleEngine.enemyFighter(EnemySpec(EnemyKind.MUD_SLIME, level = 13), 1)
        assertEquals(128, slime.maxHp)
        assertEquals(37, slime.atk)
        val vine = BattleEngine.enemyFighter(EnemySpec(EnemyKind.THORN_VINE, level = 13), 1)
        assertEquals(50, vine.def)
    }

    @Test fun oldSaveLoadsWithDefaults() {
        val old = """{"name":"Lumi","bornAt":1,"lastUpdate":2,"level":7,"stage":"BABY"}"""
        val s = json.decodeFromString(PetState.serializer(), old)
        assertEquals(0L, s.stardust)
        assertEquals(1, s.keys)
        assertNull(s.run)
    }

    @Test fun runSurvivesSaveAndLoad() {
        var p = pet(6)
        p = DungeonEngine.enter(p, 0, 12345L).state
        p = DungeonEngine.chooseRoom(p, 0).state
        assertNotNull(p.run?.battle)
        val back = json.decodeFromString(PetState.serializer(), json.encodeToString(PetState.serializer(), p))
        assertEquals(p.run?.battle?.enemies?.size, back.run?.battle?.enemies?.size)
        val st = back.run!!.battle!!
        val next = BattleEngine.playRound(st, PetAction(MoveId.PAW, st.alive.first().id))
        assertTrue(next.round == 2 || next.outcome != null)
    }

    @Test fun enterCostsKeyAndEnergy() {
        val p = pet(6)
        val r = DungeonEngine.enter(p, 0, 1L)
        assertTrue(r.success)
        assertEquals(2, r.state.keys)
        assertTrue(r.state.energy < p.energy)
        assertFalse(DungeonEngine.enter(p.copy(keys = 0), 0, 1L).success)
        assertFalse(DungeonEngine.enter(p, 1, 1L).success) // noch verschlossen
    }

    @Test fun groupsFollowRules() {
        val rng = Random(7)
        for (f in Floors.all.filter { !it.isBoss }) repeat(200) {
            for (type in listOf(RoomType.FIGHT, RoomType.ELITE, RoomType.GUARD)) {
                val g = DungeonEngine.encounter(f, type, rng)
                val kinds = g.mapNotNull { it.kind }
                assertTrue(kinds.size in 1..4, "$f $type $kinds")
                if (kinds.size == 4) assertTrue(kinds.all { it.role == Role.SWARM })
                assertTrue(kinds.count { it.role == Role.HEALER } <= 1)
                if (type != RoomType.FIGHT) assertTrue(g.first().elite)
            }
        }
    }

    @Test fun bluntCracksKlirr() {
        val p = pet(22, listOf(MoveId.BELLY_FLOP))
        var st = BattleEngine.start(p, listOf(EnemySpec(boss = app.psykit.core.battle.BossKind.KLIRR, level = 22)), FloorRule.NONE, 3, 1L)
        val shells = st.enemies.first().shells
        assertEquals(3, shells)
        repeat(4) { st = BattleEngine.playRound(st, PetAction(MoveId.BUMP, st.enemies.first().id)) }
        assertTrue(st.enemies.first().shells < shells)
    }

    @Test fun frostExtinguishesSootSpark() {
        val p = pet(9, listOf(MoveId.FROST_BREATH))
        var st = BattleEngine.start(p, listOf(EnemySpec(EnemyKind.SOOT_SPARK, level = 9)), FloorRule.NONE, 1, 3L)
        st = BattleEngine.playRound(st, PetAction(MoveId.FROST_BREATH, st.enemies.first().id))
        // Frosthauch trifft zu 95 %, mit diesem Seed trifft er
        if (st.hits.any { it.target == 1 && it.amount > 0 }) assertEquals(Outcome.WON, st.outcome)
    }

    @Test fun webSkipsTurnUnlessClaws() {
        val p = pet(7)
        val st = BattleEngine.start(p, listOf(EnemySpec(EnemyKind.WOODLOUSE, level = 7)), FloorRule.WEBS, 1, 5L, startNet = true)
        assertTrue(st.pet.has(Status.NET))
        val next = BattleEngine.playRound(st, PetAction(MoveId.BUMP, 1))
        assertFalse(next.pet.has(Status.NET))
        assertTrue(next.enemies.first().hp == next.enemies.first().maxHp)
    }

    @Test fun shopAndWallet() {
        var p = pet(10).copy(stardust = 500)
        p = Shop.buyMove(p, MoveId.SPARK_SNEEZE).state
        assertEquals(250L, p.stardust)
        assertTrue(MoveId.SPARK_SNEEZE in MoveId.known(p))
        p = Shop.buyItem(p, Item.STRAW_HAT).state
        p = Shop.wear(p, app.psykit.core.model.Slot.HEAD, Item.STRAW_HAT)
        assertEquals(listOf(Item.STRAW_HAT), Item.active(p))
        assertFalse(Shop.buyItem(p, Item.SOOT_TOPHAT).success)
    }

    @Test fun gamesGiveCappedStardust() {
        var p = pet(5).copy(questDay = 10)
        repeat(10) { p = PetEngine.gameResult(p.copy(energy = 100f, satiety = 100f), app.psykit.core.model.GameKind.MEMORY, 10, 10).state }
        assertEquals(100L, p.stardust)
    }

    @Test fun stepsGiveKeysAndDust() {
        val p = pet(5).copy(keys = 0, pendingSteps = 6100)
        val r = PetEngine.redeemSteps(p).state
        assertEquals(2, r.keys)
        assertEquals(61L, r.stardust)
    }

    // ------------------------------------------------------------ Balancing-Simulation

    /** Einfache Spiel-KI: beste erwartete Wirkung, ducken vor Großangriffen, Tränke bei wenig KP. */
    fun choose(st: BattleState): PetAction {
        val pet = st.pet
        if (pet.hpPct() < 0.3f && st.healPotions > 0) return PetAction(potion = Potion.HEAL)
        if (pet.hpPct() < 0.5f && MoveId.PURR in st.moves && BattleEngine.canUse(st, MoveId.PURR)) return PetAction(MoveId.PURR)
        if (st.alive.any { it.intent?.kind == IntentKind.BIG } && pet.hpPct() < 0.6f && MoveId.DUCK in st.moves) return PetAction(MoveId.DUCK)
        var best: PetAction? = null
        var bestScore = -1.0
        for (m in st.moves) {
            if (m.type == null || !BattleEngine.canUse(st, m)) continue
            val targets = BattleEngine.targets(st, m)
            if (targets.isEmpty()) continue
            val a = if (m.type.physical) pet.atk else pet.psiStat
            for (t in targets) {
                val eff = BattleEngine.effectiveness(st, m, t)
                val count = if (m.target == app.psykit.core.battle.Target.ALL || m.target == app.psykit.core.battle.Target.ALL_FRONT) targets.size else 1
                // Gegner mit wenig KP und Heiler zuerst
                val prio = (if (t.role == Role.HEALER) 1.4 else 1.0) * (if (t.enemy?.trick == app.psykit.core.battle.Trick.COUNTDOWN) 1.6 else 1.0)
                val mirrored = m.type == AttackType.PSI && (if (count > 1) targets.any { it.has(Status.MIRROR) || it.intent?.label == "Funkelt" } else (t.has(Status.MIRROR) || t.intent?.label == "Funkelt"))
                val score = m.power * m.hits * a / (t.def + 10.0) * eff * count * prio * (if (mirrored) -1.0 else 1.0) - m.psiCost * 1.5
                if (score > bestScore) { bestScore = score; best = PetAction(m, t.id) }
            }
        }
        return best ?: st.moves.firstOrNull { BattleEngine.canUse(st, it) }?.let { PetAction(it) } ?: PetAction()
    }

    private fun simulateRun(p0: PetState, floor: Int, seed: Long): Boolean {
        var p = DungeonEngine.enter(p0.copy(dungeonProgress = 30.coerceAtMost(19)), floor, seed).state
        var guard = 0
        while (p.run != null && guard++ < 400) {
            val run = p.run!!
            when {
                run.battle != null -> p = DungeonEngine.battle(p, choose(run.battle!!)).state
                run.bridge -> p = DungeonEngine.crossBridge(p, fast = false).state
                run.event != null -> p = DungeonEngine.resolveEvent(p, accept = run.event != EventKind.LOCKED_CHEST && run.event != EventKind.MUSHROOM).state
                run.room == RoomType.REST -> p = DungeonEngine.rest(p, heal = true).state
                run.room == RoomType.MERCHANT -> p = DungeonEngine.leaveRoom(p).state
                else -> {
                    val ch = DungeonEngine.choices(run)
                    val low = run.hp < run.maxHp * 0.5
                    val pref = if (low) listOf(RoomType.REST, RoomType.CHEST, RoomType.EVENT, RoomType.MERCHANT, RoomType.FIGHT, RoomType.ELITE)
                    else listOf(RoomType.CHEST, RoomType.EVENT, RoomType.FIGHT, RoomType.REST, RoomType.MERCHANT, RoomType.ELITE)
                    val idx = ch.indices.minBy { i -> pref.indexOf(ch[i]).let { if (it < 0) 0 else it } }
                    p = DungeonEngine.chooseRoom(p, idx).state
                }
            }
        }
        return p.lastRun?.cleared == true
    }

    /** Was ein Spieler ohne Training bis dahin realistisch gekauft und ausgerüstet hat. */
    private fun equipFor(world: Int, lv: Int): List<MoveId> = when (world) {
        1 -> listOf(MoveId.PAW, MoveId.BUMP, MoveId.STINK_CLOUD, MoveId.DUCK)
        2 -> listOf(MoveId.PSI_WAVE, MoveId.SPARK_SNEEZE, MoveId.PAW, MoveId.DUCK)
        3 -> listOf(MoveId.PSI_WAVE, MoveId.BELLY_FLOP, MoveId.SPARK_SNEEZE, MoveId.DUCK)
        4 -> listOf(MoveId.PSI_WAVE, MoveId.FROST_BREATH, MoveId.BELLY_FLOP, MoveId.MOON_SHIELD)
        else -> listOf(MoveId.PSI_WAVE, MoveId.STAR_STORM, MoveId.BELLY_FLOP, MoveId.MOON_SHIELD)
    }

    @Test fun balancingSimulation() {
        val report = StringBuilder("\nBalancing: Tierchen ohne Training auf empfohlenem Level, 300 Läufe je Ebene\n")
        val rates = mutableListOf<Double>()
        for (f in Floors.all) {
            val eq = equipFor(f.world, f.level)
            val p = pet(f.level, eq.filter { it.source is app.psykit.core.battle.MoveSource.Shop }, eq).copy(healPotions = 1)
            val wins = (0 until 300).count { simulateRun(p, f.index, 1000L + it * 7919L) }
            val rate = wins / 300.0
            rates += rate
            report.append("%-28s Lv %2d  %3d %%\n".format("${f.code} ${f.name}", f.level, (rate * 100).toInt()))
        }
        println(report)
        assertTrue(rates.first() > 0.6, "Die erste Ebene muss gut schaffbar sein")
        assertTrue(rates.all { it > 0.2 }, "Keine Ebene darf praktisch unschaffbar sein")
    }
}

