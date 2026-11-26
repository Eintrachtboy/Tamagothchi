package app.psykit.core.dungeon

import app.psykit.core.battle.AttackType
import app.psykit.core.battle.BattleEngine
import app.psykit.core.battle.BattleState
import app.psykit.core.battle.BossKind
import app.psykit.core.battle.EnemyKind
import app.psykit.core.battle.EnemySpec
import app.psykit.core.battle.FloorRule
import app.psykit.core.battle.MoveId
import app.psykit.core.battle.MoveSource
import app.psykit.core.battle.Outcome
import app.psykit.core.battle.PetAction
import app.psykit.core.battle.Potion
import app.psykit.core.battle.Role
import app.psykit.core.engine.ActionResult
import app.psykit.core.engine.LevelSystem
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Effect
import app.psykit.core.model.Item
import app.psykit.core.model.PetState
import app.psykit.core.model.Quest
import app.psykit.core.model.Rarity
import app.psykit.core.model.Reaction
import app.psykit.core.model.Stage
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

enum class World(val n: Int, val label: String, val emoji: String, val boss: BossKind) {
    CELLAR(1, "Staubkeller", "🕯️", BossKind.SOOT_KING),
    GARDEN(2, "Wilder Garten", "🌿", BossKind.MORLA),
    GROTTO(3, "Frostgrotte", "❄️", BossKind.KLIRR),
    FORGE(4, "Glutschmiede", "🔥", BossKind.FURNACE),
    DREAM(5, "Traumturm", "🌙", BossKind.NIGHTMARE);

    companion object { fun of(n: Int) = entries.first { it.n == n.coerceIn(1, 5) } }
}

/** Eine Unterebene. sub 1..3, 4 = Boss. Index 0..19 in [Floors.all]; ab 20 die Tiefe. */
data class Floor(val index: Int, val world: Int, val sub: Int, val name: String, val level: Int, val rule: FloorRule) {
    val isBoss get() = sub == 4
    val isDepth get() = index >= Floors.DEPTH_START
    val code get() = when { isDepth -> "Tiefe ${index - Floors.DEPTH_START + 1}"; isBoss -> "Boss"; else -> "$world-$sub" }
}

object Floors {
    const val DEPTH_START = 20
    val all: List<Floor> = listOf(
        Floor(0, 1, 1, "Kistengang", 5, FloorRule.NONE),
        Floor(1, 1, 2, "Spinnwebenecke", 7, FloorRule.WEBS),
        Floor(2, 1, 3, "Heizungsrohre", 9, FloorRule.HEAT),
        Floor(3, 1, 4, "Rußkönig", 10, FloorRule.NONE),
        Floor(4, 2, 1, "Brombeerhecke", 11, FloorRule.THORNS),
        Floor(5, 2, 2, "Froschteich", 13, FloorRule.WET),
        Floor(6, 2, 3, "Pilzring", 15, FloorRule.SPORE_AIR),
        Floor(7, 2, 4, "Pilzkönigin Morla", 16, FloorRule.NONE),
        Floor(8, 3, 1, "Eiszapfenhalle", 17, FloorRule.ICICLES),
        Floor(9, 3, 2, "Gefrorener See", 19, FloorRule.SLIPPERY),
        Floor(10, 3, 3, "Kristallspalte", 21, FloorRule.MIRRORING),
        Floor(11, 3, 4, "Klirr, die Gletscherkrabbe", 22, FloorRule.NONE),
        Floor(12, 4, 1, "Ascheberg", 23, FloorRule.ASH),
        Floor(13, 4, 2, "Lavafluss", 25, FloorRule.BRIDGES),
        Floor(14, 4, 3, "Große Esse", 27, FloorRule.FORGE_HEAT),
        Floor(15, 4, 4, "Eisenofen", 30, FloorRule.NONE),
        Floor(16, 5, 1, "Kissenhalle", 31, FloorRule.SLEEPY),
        Floor(17, 5, 2, "Spiegelgang", 34, FloorRule.MIRROR_HALL),
        Floor(18, 5, 3, "Uhrwerkzimmer", 37, FloorRule.CLOCKWORK),
        Floor(19, 5, 4, "Der Albtraum", 40, FloorRule.NONE),
    )

    /** Stockwerk n (1, 2, …) der endlosen Tiefe. */
    fun depth(n: Int): Floor {
        val rules = FloorRule.entries.filter { it != FloorRule.BRIDGES && it != FloorRule.HEAT && it != FloorRule.WEBS }
        return Floor(DEPTH_START + n - 1, 5, 3, "Die Tiefe", 40 + 2 * n, rules[Random(n * 92821L).nextInt(rules.size)])
    }

    fun get(index: Int) = if (index >= DEPTH_START) depth(index - DEPTH_START + 1) else all[index]
}

enum class RoomType(val label: String, val emoji: String) {
    FIGHT("Kampf", "⚔️"), ELITE("Elitekampf", "💀"), CHEST("Truhe", "🎁"), EVENT("Ereignis", "❓"),
    REST("Rastplatz", "🔥"), MERCHANT("Händler", "🛒"), GUARD("Wächter", "🛡️"), BOSS("Boss", "👑"),
}

enum class EventKind(val title: String, val text: String, val yes: String, val no: String) {
    HEDGEHOG("Ein verletzter Igel", "Ein kleiner Igel hat Hunger und schaut dich traurig an.", "Eine Beere geben", "Weitergehen"),
    LOCKED_CHEST("Eine verschlossene Kiste", "Eine schwere Kiste mit rostigem Schloss.", "Mit Wucht aufbrechen (kostet 10 % KP)", "Liegen lassen"),
    WELL("Ein Wunschbrunnen", "Man sagt, er bringt Glück, wenn man etwas hineinwirft.", "20 Sternenstaub hineinwerfen", "Weitergehen"),
    MUSHROOM("Ein leuchtender Pilz", "Er riecht verlockend. Ob der bekömmlich ist?", "Probieren", "Lieber nicht"),
    DUMMY("Eine alte Trainingspuppe", "Hier haben schon viele Tierchen geübt.", "Kurz trainieren (kostet 5 Energie)", "Weitergehen"),
}

@Serializable
data class DungeonRun(
    val floor: Int,
    val seed: Long,
    val columns: List<List<RoomType>>,
    val step: Int = 0,
    val chosen: List<Int> = emptyList(),
    val hp: Int,
    val maxHp: Int,
    val dust: Int = 0,
    val exp: Int = 0,
    val items: List<String> = emptyList(),
    val moves: List<String> = emptyList(),
    val defeated: Int = 0,
    val chests: Int = 0,
    val healPotions: Int = 0,
    val psiPotions: Int = 0,
    val room: RoomType? = null,
    val battle: BattleState? = null,
    val event: EventKind? = null,
    val bridge: Boolean = false,
    val note: String = "",
    val cleared: Boolean = false,
    /** Fundattacke für diesen Lauf (5. Platz). */
    val fundMove: String? = null,
    /** Alle gewählten Fundattacken dieses Laufs (Befund 31) und davon eingesetzte. */
    val foundMoves: List<String> = emptyList(),
    val usedFoundMoves: List<String> = emptyList(),
    /** Drei Fundkarten zur Auswahl nach einem gewonnenen Kampf. */
    val cards: List<String> = emptyList(),
)

/** Ein abgeschlossener Lauf, zum Anzeigen. */
@Serializable
data class RunReport(
    val floor: Int,
    val won: Boolean,
    val cleared: Boolean,
    val dust: Int,
    val exp: Int,
    val items: List<String> = emptyList(),
    val moves: List<String> = emptyList(),
    val lost: List<String> = emptyList(),
    val text: String = "",
    /** Fundattacken dieses Laufs in der Reihenfolge ihrer Wahl (Runde 12, Befund 31). */
    val found: List<String> = emptyList(),
    /** Davon tatsächlich eingesetzt. */
    val used: List<String> = emptyList(),
)

/** Dungeon-Logik: Zustand rein, Zustand raus. */
object DungeonEngine {
    /** Befund 14: Rastheilung als Anteil der maximalen KP; Texte nutzen [REST_HEAL_PERCENT]. */
    const val REST_HEAL = 0.3
    val REST_HEAL_PERCENT: Int get() = (REST_HEAL * 100).roundToInt()

    const val KEY_STEPS = 3000L
    const val MAX_KEYS = 3
    const val KEY_PRICE = 100
    const val ENTRY_ENERGY = 10f
    const val MIN_ENERGY_TO_ENTER = 20f
    const val ENTRY_SATIETY = 10f
    const val ROOM_ENERGY = 2f
    const val END_HYGIENE = 15f
    const val AFTER_FIGHT_HEAL = 0.15

    fun unlocked(p: PetState, index: Int): Boolean =
        if (index >= Floors.DEPTH_START) p.dungeonProgress >= Floors.DEPTH_START && index - Floors.DEPTH_START <= p.depthRecord
        else index <= p.dungeonProgress

    /** null = darf rein, sonst der Grund. */
    fun cannotEnter(p: PetState, index: Int): String? = when {
        p.run != null -> "${p.name} ist schon im Dungeon."
        p.stage == Stage.EGG -> "Erst muss das Ei schlüpfen."
        p.sleeping -> "${p.name} schläft gerade."
        p.energy < MIN_ENERGY_TO_ENTER -> "${p.name} ist zu müde (Energie mindestens ${MIN_ENERGY_TO_ENTER.toInt()})."
        p.health < 40f -> "${p.name} ist nicht fit genug (Gesundheit mindestens 40)."
        !unlocked(p, index) -> "Diese Ebene ist noch verschlossen."
        p.keys <= 0 -> "Kein Schlüssel mehr. Morgen gibt es einen neuen, oder für Schritte und im Laden."
        else -> null
    }

    fun enter(p: PetState, index: Int, now: Long): ActionResult {
        cannotEnter(p, index)?.let { return ActionResult(p, it, false) }
        val floor = Floors.get(index)
        val seed = now xor (p.bornAt * 31) xor index.toLong()
        val pet = BattleEngine.petFighter(p)
        val run = DungeonRun(
            floor = index, seed = seed, columns = map(floor, Random(seed)), hp = pet.maxHp, maxHp = pet.maxHp,
            healPotions = p.healPotions, psiPotions = p.psiPotions,
        )
        val ns = p.copy(
            run = run, keys = p.keys - 1, healPotions = 0, psiPotions = 0,
            energy = p.energy - ENTRY_ENERGY, satiety = p.satiety - ENTRY_SATIETY,
        )
        return ActionResult(ns, "${p.name} betritt ${floor.name}. Viel Glück!", reaction = Reaction.JUMP)
    }

    // ------------------------------------------------------------ Karte

    private fun map(floor: Floor, rng: Random): List<List<RoomType>> {
        if (floor.isBoss) return listOf(listOf(RoomType.REST), listOf(RoomType.BOSS))
        fun roll(): RoomType {
            val r = rng.nextInt(100)
            return when {
                r < 45 -> RoomType.FIGHT
                r < 55 -> RoomType.ELITE
                r < 70 -> RoomType.CHEST
                r < 85 -> RoomType.EVENT
                r < 95 -> RoomType.REST
                else -> RoomType.MERCHANT
            }
        }
        val cols = mutableListOf(listOf(RoomType.FIGHT))
        repeat(4) {
            val a = roll()
            var b = roll()
            var tries = 0
            while (b == a && tries++ < 10) b = roll()
            cols += listOf(a, b)
        }
        if (floor.sub == 3 && cols.none { RoomType.REST in it }) cols[3] = listOf(cols[3][0], RoomType.REST)
        cols += listOf(RoomType.GUARD)
        return cols
    }

    fun choices(run: DungeonRun): List<RoomType> = run.columns.getOrElse(run.step) { emptyList() }

    /** Raum betreten (Index in [choices]). */
    fun chooseRoom(p: PetState, option: Int): ActionResult {
        val run = p.run ?: return ActionResult(p, "Kein Dungeon-Lauf aktiv.", false)
        if (run.room != null || run.bridge) return ActionResult(p, "Erst diesen Raum abschließen.", false)
        if (run.cards.isNotEmpty()) return ActionResult(p, "Erst eine Fundattacke wählen.", false)
        val type = choices(run).getOrNull(option) ?: return ActionResult(p, "Diesen Weg gibt es nicht.", false)
        val floor = Floors.get(run.floor)
        val energyCost = ROOM_ENERGY * (if (floor.rule == FloorRule.HEAT || floor.rule == FloorRule.FORGE_HEAT) 2 else 1)
        if (p.energy < energyCost) return finish(p, aborted = true, note = "${p.name} ist zu erschöpft und kehrt mit der Beute um.")
        var ns = p.copy(energy = p.energy - energyCost)
        var r = run.copy(room = type, chosen = run.chosen + option, note = "")
        val rng = Random(run.seed + run.step * 104729L)
        val w = floor.world
        when (type) {
            RoomType.FIGHT, RoomType.ELITE, RoomType.GUARD, RoomType.BOSS -> {
                val specs = encounter(floor, type, rng)
                val battle = BattleEngine.start(
                    p, specs, floor.rule, w, rng.nextLong(), hp = run.hp,
                    healPotions = run.healPotions, psiPotions = run.psiPotions,
                    startNet = floor.rule == FloorRule.WEBS && run.step % 2 == 1,
                    fundMove = run.fundMove?.let { MoveId.parse(it) },
                )
                r = r.copy(battle = battle)
            }
            RoomType.CHEST -> {
                val pct = Item.sum<Effect.ChestDust>(p) { it.pct } + Item.sum<Effect.DungeonDust>(p) { it.pct }
                val dust = (15 * w * (1 + rng.nextDouble() * 0.5) * (1 + pct / 100.0)).roundToInt()
                val item = rollItem(w, rng, boss = false)
                val seed = w >= 2 && rng.nextInt(10) == 0
                if (seed) ns = ns.copy(goldSeeds = ns.goldSeeds + 1)
                r = r.copy(dust = r.dust + dust, items = r.items + item.name, chests = r.chests + 1,
                    note = "In der Truhe: $dust Sternenstaub und ${item.label} (${item.rarity.label})." + if (seed) " Und ein Goldbeeren-Samen! 🌟" else "")
                ns = PetEngine.progress(ns, Quest.CHEST)
                r = completeRoom(r)
            }
            RoomType.EVENT -> r = r.copy(event = EventKind.entries[rng.nextInt(EventKind.entries.size)])
            RoomType.REST, RoomType.MERCHANT -> {}
        }
        ns = ns.copy(run = r)
        return ActionResult(ns, r.note.ifEmpty { "${type.emoji} ${type.label}" })
    }

    private fun completeRoom(r: DungeonRun): DungeonRun {
        val cleared = r.step >= r.columns.size - 1
        return r.copy(room = null, battle = null, event = null, step = r.step + 1, cleared = r.cleared || cleared)
    }

    // ------------------------------------------------------------ Gegnergruppen

    fun budget(floor: Floor, type: RoomType): Int {
        val base = if (floor.isDepth) 6 + min(3, (floor.index - Floors.DEPTH_START) / 3) else listOf(3, 4, 4)[floor.sub - 1]
        return when (type) {
            RoomType.ELITE -> base + 2
            RoomType.GUARD -> base + 1
            else -> base
        }
    }

    fun encounter(floor: Floor, type: RoomType, rng: Random): List<EnemySpec> {
        if (type == RoomType.BOSS) return listOf(EnemySpec(boss = World.of(floor.world).boss, level = floor.level))
        val level = if (type == RoomType.GUARD) floor.level + 1 else floor.level
        val pool = if (floor.isDepth) EnemyKind.entries.filter { it != EnemyKind.SLIMELING }.toMutableList()
        else EnemyKind.ofWorld(floor.world).filter { !(it == EnemyKind.SOOT_SPARK && floor.sub < 3) }.toMutableList()
        if (!floor.isDepth && floor.sub == 3 && floor.world < 5 && rng.nextInt(100) < 30) {
            pool += EnemyKind.ofWorld(floor.world + 1).random(rng)
        }
        var left = budget(floor, type)
        val wantElite = type == RoomType.ELITE || type == RoomType.GUARD
        if (wantElite) left -= 2
        val picked = mutableListOf<EnemyKind>()
        while (true) {
            val allSwarm = picked.all { it.role == Role.SWARM }
            val maxCount = if (allSwarm) 4 else 3
            if (picked.size >= maxCount) break
            val fits = pool.filter { k ->
                k.role.cost <= left &&
                    !(k.role == Role.HEALER && picked.any { it.role == Role.HEALER }) &&
                    !(picked.size >= 3 && k.role != Role.SWARM)
            }
            if (fits.isEmpty()) break
            val k = fits.random(rng)
            picked += k
            left -= k.role.cost
        }
        if (picked.isEmpty()) picked += pool.minBy { it.role.cost }
        // Ab Welt 2: mindestens zwei verschiedene Körper
        if ((floor.world >= 2 || floor.isDepth) && picked.size >= 2 && picked.map { it.body }.distinct().size == 1) {
            val other = pool.filter { it.body != picked[0].body && it.role.cost <= picked.last().role.cost && it.role != Role.HEALER }
            if (other.isNotEmpty()) picked[picked.size - 1] = other.random(rng)
        }
        // Stärkster zuerst (er wird bei Elite/Wächter zum Elite-Gegner)
        picked.sortByDescending { it.role.cost }
        return picked.mapIndexed { i, k -> EnemySpec(k, level = level, elite = wantElite && i == 0) }
    }

    // ------------------------------------------------------------ Kampf

    fun battle(p: PetState, action: PetAction): ActionResult {
        val run = p.run ?: return ActionResult(p, "Kein Kampf.", false)
        val b = run.battle ?: return ActionResult(p, "Kein Kampf.", false)
        val nb = BattleEngine.playRound(b, action)
        var r = run.copy(battle = nb, hp = nb.pet.hp, healPotions = nb.healPotions, psiPotions = nb.psiPotions)
        // Befund 31: eingesetzte Fundattacke merken
        nb.usedMove?.name?.takeIf { it in run.foundMoves && it !in run.usedFoundMoves }?.let { r = r.copy(usedFoundMoves = r.usedFoundMoves + it) }
        // Nach einem gewonnenen Kampf kurz verschnaufen
        if (nb.outcome == Outcome.WON) r = r.copy(hp = min(r.maxHp, r.hp + (r.maxHp * AFTER_FIGHT_HEAL).roundToInt()))
        var ns = p.copy(run = r)
        // Attacken-Stufen und entdeckte Kombos zählen sofort, auch wenn der Lauf verloren geht
        nb.usedMove?.let { m -> ns = ns.copy(moveUses = ns.moveUses + (m.name to (ns.moveUses[m.name] ?: 0) + 1)) }
        if (nb.combos.isNotEmpty()) ns = ns.copy(combosFound = (ns.combosFound + nb.combos).distinct())
        if (nb.outcome == null) return ActionResult(ns, nb.log.lastOrNull() ?: "")
        if (nb.outcome == Outcome.LOST) {
            return finish(ns.copy(run = r.copy(dust = max(0, r.dust - nb.stolenDust))), aborted = false, lost = true, note = "${p.name} wurde besiegt …")
        }
        // Gewonnen: Belohnung
        val floor = Floors.get(r.floor)
        val w = if (floor.isDepth) 5 + (floor.index - Floors.DEPTH_START) / 5 else floor.world
        val dustPct = Item.sum<Effect.DungeonDust>(p) { it.pct }
        val expPct = Item.sum<Effect.BattleExp>(p) { it.pct }
        val firstBoss = r.room == RoomType.BOSS && floor.world !in p.bossesBeaten
        var (dust, exp) = when (r.room) {
            RoomType.ELITE -> (2 * (8 + 6 * w)) to (2 * (2 + 2 * w))
            RoomType.GUARD -> (30 * w) to (5 * w)
            RoomType.BOSS -> if (firstBoss) 300 to 25 * w else 100 to 10 * w
            else -> (8 + 6 * w) to (2 + 2 * w)
        }
        dust = (dust * (1 + dustPct / 100.0)).roundToInt() - nb.stolenDust
        exp = (exp * (1 + expPct / 100.0)).roundToInt()
        val loot = mutableListOf<String>()
        val newMoves = mutableListOf<String>()
        val rng = Random(run.seed * 7 + run.step)
        if (r.room == RoomType.BOSS) {
            loot += rollItem(floor.world, rng, boss = true).name
            val drop = when (World.of(floor.world).boss) {
                BossKind.MORLA -> MoveId.SPORES
                BossKind.KLIRR -> MoveId.BLIZZARD
                BossKind.FURNACE -> MoveId.EMBER_BREATH
                BossKind.NIGHTMARE -> MoveId.NIGHTMARE_GAZE
                else -> null
            }
            if (drop != null && drop.name !in p.ownedMoves && firstBoss) newMoves += drop.name
        }
        if (r.room == RoomType.ELITE && floor.world == 3 && MoveId.TIGER_LEAP.name !in p.ownedMoves && rng.nextInt(100) < 25) newMoves += MoveId.TIGER_LEAP.name
        if (r.room == RoomType.GUARD && rng.nextInt(100) < 50) loot += rollItem(floor.world, rng, boss = false).name
        // Versteckte Attacke dieser Ebene: 40 % pro Sieg über Wächter/Boss, spätestens beim dritten Sieg
        if (r.room == RoomType.GUARD || r.room == RoomType.BOSS) {
            val key = floor.index.toString()
            val wins = (ns.floorWins[key] ?: 0) + 1
            ns = ns.copy(floorWins = ns.floorWins + (key to wins))
            val hidden = MoveId.hiddenIn(floor.index)
            if (hidden != null && hidden.name !in p.ownedMoves && hidden.name !in r.moves && hidden.name !in newMoves &&
                (wins >= 3 || rng.nextInt(100) < HIDDEN_CHANCE)
            ) newMoves += hidden.name
        }
        val cards = if (r.room == RoomType.FIGHT || r.room == RoomType.ELITE) rollCards(p, floor, rng, r.fundMove) else emptyList()
        val kills = nb.defeated.size + (if (nb.bossDefeated) 1 else 0)
        r = r.copy(
            dust = r.dust + max(0, dust), exp = r.exp + exp, items = r.items + loot, moves = r.moves + newMoves,
            defeated = r.defeated + kills, cards = cards,
            note = buildString {
                append("Gewonnen! +${max(0, dust)} Sternenstaub, +$exp EP.")
                if (nb.stolenDust > 0) append(" (${nb.stolenDust} wurden geklaut.)")
                loot.mapNotNull { Item.parse(it) }.forEach { append(" Fund: ${it.label} (${it.rarity.label}).") }
                newMoves.mapNotNull { MoveId.parse(it) }.forEach {
                    append(if (it.source is MoveSource.Hidden) " Versteckte Attacke entdeckt: ${it.label}!" else " Neue Attacke: ${it.label}!")
                }
            },
        )
        ns = PetEngine.progress(ns, Quest.DEFEAT, kills)
        val bridge = floor.rule == FloorRule.BRIDGES && r.room != RoomType.GUARD
        r = completeRoom(r).copy(bridge = bridge)
        if (r.cleared) return finish(ns.copy(run = r), aborted = false, note = r.note)
        return ActionResult(ns.copy(run = r), r.note, reaction = Reaction.JUMP)
    }

    // ------------------------------------------------------------ Fundattacken

    const val HIDDEN_CHANCE = 40
    const val SKIP_CARD_DUST = 10

    /** 0 = gewöhnlich (Laden), 1 = selten (Training), 2 = episch (Boss-Beute). */
    fun cardRarity(m: MoveId): Int = when (m.source) {
        is MoveSource.Training -> 1
        is MoveSource.Drop -> 2
        else -> 0
    }

    fun cardRarityLabel(m: MoveId) = listOf("Gewöhnlich", "Selten", "Episch")[cardRarity(m)]

    private val cardPools: List<List<MoveId>> by lazy {
        listOf(
            MoveId.entries.filter { it.source is MoveSource.Shop },
            MoveId.entries.filter { it.source is MoveSource.Training },
            MoveId.entries.filter { it.source is MoveSource.Drop },
        )
    }

    /** Die Schwäche einer Welt: dagegen hilft mindestens eine Karte. */
    fun weakness(floor: Floor): AttackType =
        if (floor.isDepth) AttackType.entries[(floor.index * 7) % AttackType.entries.size] else World.of(floor.world).boss.weakness

    fun rollCards(p: PetState, floor: Floor, rng: Random, current: String? = null): List<String> {
        val blocked = (MoveId.equipped(p).map { it.name } + listOfNotNull(current)).toSet()
        val picked = mutableListOf<MoveId>()
        repeat(3) {
            val r = rng.nextInt(100)
            val tier = when { r < 60 -> 0; r < 90 -> 1; else -> 2 }
            val options = (tier downTo 0).asSequence()
                .map { t -> cardPools[t].filter { it.name !in blocked && it !in picked } }
                .firstOrNull { it.isNotEmpty() } ?: return@repeat
            picked += options.random(rng)
        }
        val weak = weakness(floor)
        if (picked.isNotEmpty() && picked.none { it.type == weak }) {
            val i = rng.nextInt(picked.size)
            val tier = cardRarity(picked[i])
            val options = (tier downTo 0).asSequence()
                .map { t -> cardPools[t].filter { it.type == weak && it.name !in blocked && it !in picked } }
                .firstOrNull { it.isNotEmpty() }
            if (options != null) picked[i] = options.random(rng)
        }
        return picked.map { it.name }
    }

    /** Fundkarte wählen (Index), -1 = überspringen für Sternenstaub. */
    fun chooseCard(p: PetState, index: Int): ActionResult {
        val run = p.run ?: return ActionResult(p, "", false)
        if (run.cards.isEmpty()) return ActionResult(p, "", false)
        if (index < 0) {
            val note = "Karten liegen gelassen: +$SKIP_CARD_DUST Sternenstaub."
            return ActionResult(p.copy(run = run.copy(cards = emptyList(), dust = run.dust + SKIP_CARD_DUST, note = note)), note)
        }
        val m = run.cards.getOrNull(index)?.let { MoveId.parse(it) } ?: return ActionResult(p, "", false)
        val note = if (run.fundMove != null) "${m.label} ersetzt ${MoveId.parse(run.fundMove)?.label ?: "die alte Fundattacke"}." else "Fundattacke für diesen Lauf: ${m.label}."
        return ActionResult(p.copy(run = run.copy(cards = emptyList(), fundMove = m.name, foundMoves = run.foundMoves + m.name, note = note)), note, reaction = Reaction.JUMP)
    }

    /** Kampf-Ergebnis gesehen, zurück zur Karte (nur fürs Anzeigen, Belohnung gibt es sofort). */
    fun rollItem(world: Int, rng: Random, boss: Boolean): Item {
        val w = world.coerceIn(1, 5)
        if (boss && rng.nextInt(100) < 10) return Item.bossItem(w)
        val r = rng.nextInt(100)
        val rarity = when { r < 60 -> Rarity.COMMON; r < 90 -> Rarity.RARE; r < 99 -> Rarity.EPIC; else -> Rarity.LEGENDARY }
        val list = Item.ofWorld(w).filter { it.rarity == rarity }
        return list.randomOrNull(rng) ?: Item.ofWorld(w).filter { it.rarity.ordinal < rarity.ordinal }.random(rng)
    }

    // ------------------------------------------------------------ Ereignisse, Rast, Händler, Brücken

    fun resolveEvent(p: PetState, accept: Boolean): ActionResult {
        val run = p.run ?: return ActionResult(p, "", false)
        val ev = run.event ?: return ActionResult(p, "", false)
        val w = Floors.get(run.floor).world
        val rng = Random(run.seed + run.step * 31337L)
        var ns = p
        var r = run
        val note = if (!accept) "Du gehst weiter." else when (ev) {
            EventKind.HEDGEHOG -> if (p.goldBerries > 0 && p.keys < DungeonEngine.MAX_KEYS) {
                ns = ns.copy(goldBerries = p.goldBerries - 1, keys = p.keys + 1)
                "Der Igel strahlt über die Goldbeere und schenkt dir einen Schlüssel! 🗝️"
            } else if (p.berryStock + p.starBerries + p.psiBerries + p.goldBerries <= 0) "Du hast keine Beeren dabei." else {
                ns = when {
                    p.berryStock > 0 -> ns.copy(berryStock = p.berryStock - 1)
                    p.starBerries > 0 -> ns.copy(starBerries = p.starBerries - 1)
                    p.psiBerries > 0 -> ns.copy(psiBerries = p.psiBerries - 1)
                    else -> ns.copy(goldBerries = p.goldBerries - 1)
                }
                if (rng.nextBoolean()) { val it = rollItem(w, rng, false); r = r.copy(items = r.items + it.name); "Der Igel schenkt dir zum Dank: ${it.label}!" }
                else { r = r.copy(dust = r.dust + 40 * w); "Der Igel zeigt dir ein Versteck mit ${40 * w} Sternenstaub." }
            }
            EventKind.LOCKED_CHEST -> {
                val cost = (run.maxHp * 0.1).roundToInt()
                if (run.hp <= cost) "Dafür bist du gerade zu schwach." else {
                    val dust = 15 * w + rng.nextInt(10 * w)
                    val it = rollItem(w, rng, false)
                    r = r.copy(hp = run.hp - cost, dust = r.dust + dust, items = r.items + it.name, chests = r.chests + 1)
                    ns = PetEngine.progress(ns, Quest.CHEST)
                    "Krach! In der Kiste: $dust Sternenstaub und ${it.label}."
                }
            }
            EventKind.WELL -> if (run.dust < 20) "Du hast noch keine 20 Sternenstaub gesammelt." else if (rng.nextBoolean()) {
                r = r.copy(dust = r.dust + 40); "Der Brunnen funkelt: +60 Sternenstaub!"
            } else { r = r.copy(dust = r.dust - 20); "Plopp. Nichts passiert." }
            EventKind.MUSHROOM -> if (rng.nextBoolean()) {
                r = r.copy(hp = min(run.maxHp, run.hp + (run.maxHp * 0.25).roundToInt())); "Lecker! +25 % KP."
            } else {
                r = r.copy(hp = max(1, run.hp - (run.maxHp * 0.1).roundToInt())); "Bäh, der war giftig. -10 % KP."
            }
            EventKind.DUMMY -> if (p.energy < 8f) "${p.name} ist zu müde zum Trainieren." else {
                ns = ns.copy(energy = p.energy - 5f); r = r.copy(exp = r.exp + 5 * w); "Gutes Training! +${5 * w} EP."
            }
        }
        r = completeRoom(r.copy(note = note))
        return ActionResult(ns.copy(run = r), note)
    }

    fun rest(p: PetState, heal: Boolean): ActionResult {
        val run = p.run ?: return ActionResult(p, "", false)
        if (run.room != RoomType.REST) return ActionResult(p, "", false)
        val r = if (heal) run.copy(hp = min(run.maxHp, run.hp + (run.maxHp * REST_HEAL).roundToInt())) else run
        val note = if (heal) "Am Feuer erholt sich ${p.name}: +$REST_HEAL_PERCENT % KP." else "${p.name} hat die Attacken neu sortiert."
        return ActionResult(p.copy(run = completeRoom(r.copy(note = note))), note)
    }

    /** Beim Händler mit dem eigenen Sternenstaub einkaufen. Tränke landen im Beutel des Laufs. */
    fun buyFromMerchant(p: PetState, potion: Potion): ActionResult {
        val run = p.run ?: return ActionResult(p, "", false)
        if (p.stardust < potion.price) return ActionResult(p, "Dafür reicht dein Sternenstaub nicht.", false)
        val r = if (potion == Potion.HEAL) run.copy(healPotions = run.healPotions + 1) else run.copy(psiPotions = run.psiPotions + 1)
        return ActionResult(p.copy(stardust = p.stardust - potion.price, run = r), "${potion.label} gekauft.")
    }

    fun leaveRoom(p: PetState): ActionResult {
        val run = p.run ?: return ActionResult(p, "", false)
        if (run.room != RoomType.MERCHANT && run.room != RoomType.REST) return ActionResult(p, "", false)
        return ActionResult(p.copy(run = completeRoom(run.copy(note = ""))), "Weiter geht's.")
    }

    fun crossBridge(p: PetState, fast: Boolean): ActionResult {
        val run = p.run ?: return ActionResult(p, "", false)
        if (!run.bridge) return ActionResult(p, "", false)
        val rng = Random(run.seed + run.step * 7777L)
        val w = Floors.get(run.floor).world
        var r = run.copy(bridge = false)
        val note = if (!fast) "Ihr nehmt die sichere Brücke." else if (rng.nextBoolean()) {
            r = r.copy(dust = r.dust + 20 * w); "Schnell rüber! Unterwegs findet ihr ${20 * w} Sternenstaub."
        } else {
            r = r.copy(hp = max(1, r.hp - (r.maxHp * 0.1).roundToInt())); "Die Brücke wackelt, ${p.name} verbrennt sich die Pfoten. -10 % KP."
        }
        return ActionResult(p.copy(run = r.copy(note = note)), note)
    }

    // ------------------------------------------------------------ Ende

    fun canLeave(run: DungeonRun) = run.battle == null || run.battle.outcome != null

    /** Umkehren mit der ganzen Beute. */
    fun leave(p: PetState): ActionResult {
        val run = p.run ?: return ActionResult(p, "", false)
        if (!canLeave(run)) return ActionResult(p, "Mitten im Kampf kann man nicht umkehren.", false)
        return finish(p, aborted = true, note = "${p.name} kehrt mit der Beute um.")
    }

    private fun finish(p: PetState, aborted: Boolean, lost: Boolean = false, note: String = ""): ActionResult {
        val run = p.run ?: return ActionResult(p, note, false)
        val floor = Floors.get(run.floor)
        val rng = Random(run.seed * 13)
        val lostNames = mutableListOf<String>()
        var dust = run.dust
        var items = run.items
        var moves = run.moves
        if (lost) {
            dust /= 2
            items = run.items.filter { if (rng.nextBoolean()) true else { lostNames += Item.parse(it)?.label ?: it; false } }
            moves = run.moves.filter { if (rng.nextBoolean()) true else { lostNames += MoveId.parse(it)?.label ?: it; false } }
        }
        // Doppelte Funde werden zu Sternenstaub
        val owned = p.items.toMutableList()
        val newItems = mutableListOf<Item>()
        for (name in items) {
            val it = Item.parse(name) ?: continue
            if (it.name in owned) dust += it.rarity.duplicateDust else { owned += it.name; newItems += it }
        }
        val cleared = !lost && run.cleared
        var ns = p.copy(
            run = null,
            stardust = p.stardust + dust,
            items = owned,
            ownedMoves = (p.ownedMoves + moves).distinct(),
            healPotions = p.healPotions + run.healPotions,
            psiPotions = p.psiPotions + run.psiPotions,
            battlesWon = p.battlesWon + run.defeated,
            hygiene = p.hygiene - END_HYGIENE,
            health = if (lost) p.health - 10f else p.health,
            happiness = if (lost) p.happiness - 10f else p.happiness + 5f,
        )
        if (cleared) {
            ns = PetEngine.progress(ns, Quest.SUBLEVEL)
            if (floor.isDepth) ns = ns.copy(depthRecord = max(ns.depthRecord, floor.index - Floors.DEPTH_START + 1))
            else if (run.floor == ns.dungeonProgress) ns = ns.copy(dungeonProgress = ns.dungeonProgress + 1)
            if (floor.isBoss && floor.world !in ns.bossesBeaten) ns = ns.copy(bossesBeaten = ns.bossesBeaten + floor.world)
        }
        ns = PetEngine.clampState(ns)
        val (leveled, events) = LevelSystem.addExp(ns, run.exp)
        val msg = buildString {
            append(note)
            if (cleared) append(if (floor.isBoss) " Boss besiegt!" else " Ebene geschafft!")
            append(" Beute: $dust Sternenstaub, ${run.exp} EP")
            if (newItems.isNotEmpty()) append(", ${newItems.joinToString { it.label }}")
            append(".")
            if (lostNames.isNotEmpty()) append(" Verloren: ${lostNames.joinToString()}.")
            events.forEach { append(" $it") }
        }
        val report = RunReport(run.floor, !lost, cleared, dust, run.exp, newItems.map { it.name }, moves, lostNames, msg.trim(), run.foundMoves, run.usedFoundMoves)
        return ActionResult(leveled.copy(lastRun = report), msg.trim(), reaction = if (lost) Reaction.SLEEPY else Reaction.JUMP)
    }
}
