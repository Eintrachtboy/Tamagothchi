package app.psykit.core.battle

import app.psykit.core.model.Effect
import app.psykit.core.model.Item
import app.psykit.core.model.PetState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Besonderheit einer Unterebene, die den Kampf verändert. */
enum class FloorRule(val label: String, val info: String) {
    NONE("Keine", "Keine Besonderheit."),
    WEBS("Netze", "In jedem 2. Raum klebt man zu Beginn fest. Krallen schneiden frei."),
    HEAT("Hitze", "Energie sinkt im Dungeon doppelt so schnell."),
    THORNS("Dornen", "Jeder Krallenangriff kostet 2 eigene KP."),
    WET("Nass", "Feuer wirkt nur halb, Frost doppelt."),
    SPORE_AIR("Sporenluft", "Jede Runde kleiner Giftschaden für alle."),
    ICICLES("Fallende Eiszapfen", "Jede 4. Runde fällt ein Eiszapfen. Ducken oder Schild hilft."),
    SLIPPERY("Glatt", "Wer langsamer ist, rutscht mit 20 % aus und verliert die Runde."),
    MIRRORING("Spiegelnd", "Psi-Attacken werfen 15 % des Schadens auf dich zurück."),
    ASH("Asche", "Treffsicherheit -10 %."),
    BRIDGES("Brücken", "Nach jedem Kampf: sichere oder schnelle Brücke."),
    FORGE_HEAT("Große Esse", "Hitze, aber Frost-Attacken heilen dich um 5 KP."),
    SLEEPY("Schläfrig", "Jede Runde 10 % Chance einzuschlafen."),
    MIRROR_HALL("Spiegelgang", "Gegner kopieren oft deine letzte Attacke."),
    CLOCKWORK("Uhrwerk", "Jede 3. Runde handeln alle zweimal."),
}

/** Zustände. Aussetz-Zustände (Schlaf, Netz …) werden beim nächsten Zug verbraucht, die anderen laufen am Rundenende ab. */
enum class Status(val label: String, val emoji: String, val skips: Boolean = false) {
    BURN("Brand", "🔥"), POISON("Gift", "☠️"), SLOW("Langsam", "🐢"),
    FROZEN("Eingefroren", "🧊", true), STUN("Betäubt", "💫", true), SLEEP("Schlaf", "💤", true),
    NET("Netz", "🕸️", true), CONFUSED("Verwirrt", "😵"), BLIND("Geblendet", "🤧"),
    SHIELD("Mondschild", "🌙"), CURLED("Eingerollt", "🛡️"), BURROWED("Eingegraben", "⛰️"),
    MIRROR("Spiegelt", "✨"), SOOT("Rußwolke", "🌫️"), ROOTED("Wurzelgriff", "🌱"),
    TOXIC("Tiefes Gift", "🧪"),
}

/** Kombos: Zustand auf dem Gegner + passender Angriffstyp. Der Zustand wird verbraucht. */
enum class Combo(val label: String, val info: String, val status: Status, val type: AttackType) {
    SHATTER("Zerschmettern", "Eingefroren + Wucht: +50 % Schaden", Status.FROZEN, AttackType.BLUNT),
    POISON_BLAST("Giftexplosion", "Vergiftet + Feuer: 25 % des Treffers an alle anderen Gegner", Status.POISON, AttackType.FIRE),
    HELPLESS("Wehrloser Hieb", "Betäubt + Krallen: sicherer kritischer Treffer", Status.STUN, AttackType.CLAW),
    STEAM("Dampfstoß", "Brennt + Frost: Gegner verliert seine nächste Aktion", Status.BURN, AttackType.FROST),
    NIGHTMARE("Albtraum", "Schläft + Psi: +40 % Schaden und 2 Runden verwirrt", Status.SLEEP, AttackType.PSI),
    DEEP_POISON("Tiefes Gift", "Verlangsamt + Gift: Gift wirkt doppelt so stark", Status.SLOW, AttackType.POISON);

    companion object {
        fun of(f: Fighter, type: AttackType?): Combo? = if (type == null) null else entries.firstOrNull { it.type == type && f.has(it.status) }
    }
}

enum class StatKind { ATK, DEF, SPD }

enum class IntentKind(val emoji: String) { ATTACK("⚔️"), BIG("❗"), TRICK("🌀"), CHARGE("⏳"), SUMMON("📣"), COPY("🪞") }

@Serializable
data class Intent(val kind: IntentKind, val label: String)

@Serializable
class Fighter(
    val id: Int,
    val name: String,
    val emoji: String,
    val isPet: Boolean,
    val enemy: EnemyKind? = null,
    val boss: BossKind? = null,
    val body: Body? = null,
    var front: Boolean = true,
    val level: Int = 1,
    var maxHp: Int,
    var hp: Int,
    var atk: Int,
    var def: Int,
    var spd: Int,
    var psiStat: Int,
    val elite: Boolean = false,
    var stages: MutableMap<StatKind, Int> = mutableMapOf(),
    var status: MutableMap<Status, Int> = mutableMapOf(),
    var immune: MutableMap<Status, Int> = mutableMapOf(),
    var intent: Intent? = null,
    var pattern: Int = 0,
    var counter: Int = 0,
    var acted: Int = 0,
    var shells: Int = 0,
    var heat: Int = 0,
    var carried: Int = 0,
    var fled: Boolean = false,
) {
    val alive get() = hp > 0 && !fled
    val role get() = enemy?.role
    fun has(s: Status) = (status[s] ?: 0) > 0
    fun stage(k: StatKind) = stages[k] ?: 0
    fun hpPct() = hp.toFloat() / maxHp
}

/** Boni aus Hut, Brille, Glücksbringer und kompletten Sets. */
@Serializable
data class PetBonuses(
    val typeDmg: Map<AttackType, Int> = emptyMap(),
    val bodyDmg: Map<Body, Int> = emptyMap(),
    val accuracy: Int = 0,
    val crit: Int = 0,
    val psiRegen: Int = 0,
    val startPsi: Int = 0,
    val healPerRound: Int = 0,
    val netDodge: Int = 0,
    val burnTaken: Int = 0,
    val accLock: Boolean = false,
    val noFreezeSlow: Boolean = false,
    val noConfusion: Boolean = false,
    val longerBurn: Boolean = false,
    val shorterSleep: Boolean = false,
    val setWorlds: List<Int> = emptyList(),
) {
    companion object {
        fun of(p: PetState): PetBonuses {
            val fx = Item.effects(p)
            return PetBonuses(
                typeDmg = fx.filterIsInstance<Effect.TypeDamage>().groupBy { it.type }.mapValues { e -> e.value.sumOf { it.pct } },
                bodyDmg = fx.filterIsInstance<Effect.BodyDamage>().groupBy { it.body }.mapValues { e -> e.value.sumOf { it.pct } },
                accuracy = fx.filterIsInstance<Effect.Accuracy>().sumOf { it.pct },
                crit = fx.filterIsInstance<Effect.CritPoints>().sumOf { it.points },
                psiRegen = fx.filterIsInstance<Effect.PsiRegen>().sumOf { it.pct },
                startPsi = fx.filterIsInstance<Effect.StartPsi>().sumOf { it.points },
                healPerRound = fx.filterIsInstance<Effect.HealPerRound>().sumOf { it.pct },
                netDodge = fx.filterIsInstance<Effect.NetDodge>().sumOf { it.pct },
                burnTaken = fx.filterIsInstance<Effect.BurnTaken>().sumOf { it.pct },
                accLock = fx.any { it is Effect.AccuracyLock },
                noFreezeSlow = fx.any { it is Effect.NoFreezeSlow },
                noConfusion = fx.any { it is Effect.NoConfusion },
                longerBurn = fx.any { it is Effect.LongerBurn },
                shorterSleep = fx.any { it is Effect.ShorterSleep },
                setWorlds = Item.completeSets(p),
            )
        }
    }
}

enum class Outcome { WON, LOST }

enum class Potion(val label: String, val emoji: String, val price: Int) {
    HEAL("Heiltrank", "🧪", 40), PSI("Psi-Trank", "💜", 60)
}

/** Treffer der letzten Runde, für Zahlen über den Figuren. */
@Serializable
data class Hit(val target: Int, val amount: Int, val heal: Boolean = false, val note: String = "")

/** Kompletter Kampfzustand. Wird zwischen den Runden gespeichert. */
@Serializable
class BattleState(
    val seed: Long,
    var round: Int = 1,
    val pet: Fighter,
    var enemies: MutableList<Fighter>,
    var psi: Float,
    val maxPsi: Int,
    var moves: MutableList<MoveId>,
    val empowered: List<MoveId> = emptyList(),
    val rule: FloorRule = FloorRule.NONE,
    val world: Int = 1,
    val bonuses: PetBonuses = PetBonuses(),
    var healPotions: Int = 0,
    var psiPotions: Int = 0,
    var lastPetMove: MoveId? = null,
    var purrUsed: Boolean = false,
    var lucky: Boolean = false,
    var ducking: Boolean = false,
    var bossPhase: Int = 1,
    var roundsWithoutBlunt: Int = 0,
    var icicleWarning: Boolean = false,
    var nextId: Int = 100,
    var stolenDust: Int = 0,
    var defeated: MutableList<EnemyKind> = mutableListOf(),
    var bossDefeated: Boolean = false,
    var log: MutableList<String> = mutableListOf(),
    var hits: MutableList<Hit> = mutableListOf(),
    var outcome: Outcome? = null,
    /** Stufe je Attacke (1 bis 3), beim Start aus den Einsätzen berechnet. */
    val levels: Map<MoveId, Int> = emptyMap(),
    var trap: Boolean = false,
    var trapHit: Boolean = false,
    var charging: MoveId? = null,
    var chargeTarget: Int? = null,
    var mirrorPaw: Boolean = false,
    var crystal: Int = 0,
    var lavaHeart: Int = 0,
    var sureHit: Boolean = false,
    /** In dieser Runde eingesetzte Attacke und ausgelöste Kombos (für Stufen und Kombo-Buch). */
    var usedMove: MoveId? = null,
    var combos: MutableList<String> = mutableListOf(),
    /** Fundattacke dieses Laufs (5. Platz). */
    val fundMove: MoveId? = null,
) {
    val alive get() = enemies.filter { it.alive }
    fun copy(): BattleState = BattleEngine.json.decodeFromString(serializer(), BattleEngine.json.encodeToString(serializer(), this))
}

/** Eine Aktion des Tierchens in einer Runde. */
data class PetAction(val move: MoveId? = null, val target: Int? = null, val potion: Potion? = null)

/** Gegner, wie der Dungeon ihn aufstellt. */
data class EnemySpec(val kind: EnemyKind? = null, val boss: BossKind? = null, val level: Int, val elite: Boolean = false)

object BattleEngine {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }

    // ------------------------------------------------------------ Startwerte (Feintuning hier)
    const val NORMAL_POWER = 6
    const val BIG_POWER = 16
    const val CRIT_CHANCE = 6
    const val CRIT_MULT = 1.5
    const val EVADE_QUICK = 15
    const val PSI_REGEN = 2f
    const val ELITE_HP = 1.5
    const val ELITE_STAT = 1.25
    const val BOSS_HP = 3.5
    const val BOSS_ATK = 1.25
    const val BOSS_DEF = 1.2
    /** Runde 19 (Morla): Sporenpilz heilt Bosse um diesen Anteil (andere Gegner 15 %). */
    internal var SPORE_HEAL_BOSS = 0.06
    /** Runde 19 (Morla): Sporensturm vergiftet nur, solange ein Sporenpilz lebt. */
    internal var SPORE_STORM_NEEDS_SHROOM = true
    /** Runde 19 (Morla): KP-Faktor. Mit 3,5 war sie in der Simulation fast unschaffbar (0–8 %), 2,0 ergibt 42 % ohne, 85 % mit Training. */
    internal var MORLA_HP = 2.0
    const val SET_BONUS = 5

    fun maxPsi(p: PetState) = (10 + p.trainPsi / 5).coerceAtMost(20)

    fun petFighter(p: PetState, hp: Int? = null): Fighter {
        val st = BattleStats.of(p)
        fun pct(v: Int, n: Int) = (v * (100 + n) / 100.0).roundToInt().coerceAtLeast(1)
        val maxHp = pct(st.maxHp, Item.sum<Effect.Hp>(p) { it.pct })
        return Fighter(
            id = 0, name = p.name, emoji = "🐾", isPet = true,
            maxHp = maxHp, hp = (hp ?: maxHp).coerceIn(1, maxHp),
            atk = pct(st.attack, Item.sum<Effect.Attack>(p) { it.pct }),
            def = pct(st.defense, Item.sum<Effect.Defense>(p) { it.pct }),
            spd = pct(st.speed, Item.sum<Effect.Speed>(p) { it.pct }),
            psiStat = st.psi, level = p.level,
        )
    }

    /** Später Bosse halten mehr aus, weil ihre Mechaniken leichter zu kontern sind. */
    fun bossHp(b: BossKind) = when (b) { BossKind.MORLA -> MORLA_HP; BossKind.SOOT_KING -> BOSS_HP; BossKind.NIGHTMARE -> BOSS_HP + 1.5; else -> BOSS_HP + 1.0 }

    fun enemyFighter(spec: EnemySpec, id: Int): Fighter {
        val b = BattleStats.enemyBase(spec.level)
        spec.boss?.let { boss ->
            return Fighter(
                id = id, name = boss.label, emoji = boss.emoji, isPet = false, boss = boss, body = boss.body, level = spec.level,
                maxHp = (b.maxHp * bossHp(boss)).roundToInt(), hp = (b.maxHp * bossHp(boss)).roundToInt(),
                atk = (b.attack * BOSS_ATK).roundToInt(), def = (b.defense * BOSS_DEF).roundToInt(), spd = b.speed,
                psiStat = (b.attack * BOSS_ATK).roundToInt(),
                shells = if (boss == BossKind.KLIRR) 3 else 0,
            )
        }
        val k = spec.kind ?: EnemyKind.DUST_BUNNY
        val r = k.role
        val e = if (spec.elite) ELITE_HP else 1.0
        val es = if (spec.elite) ELITE_STAT else 1.0
        val hp = (b.maxHp * r.hp * e).roundToInt().coerceAtLeast(1)
        val atk = (b.attack * r.atk * es).roundToInt().coerceAtLeast(1)
        return Fighter(
            id = id, name = (if (spec.elite) "Elite-" else "") + k.label, emoji = k.emoji, isPet = false, enemy = k, body = k.body,
            front = r.front, level = spec.level, maxHp = hp, hp = hp, atk = atk,
            def = (b.defense * r.def * es).roundToInt().coerceAtLeast(1), spd = (b.speed * r.spd).roundToInt().coerceAtLeast(1),
            psiStat = atk, elite = spec.elite, counter = if (k.trick == Trick.COUNTDOWN) 4 else 0,
        )
    }

    fun start(p: PetState, specs: List<EnemySpec>, rule: FloorRule, world: Int, seed: Long, hp: Int? = null,
              healPotions: Int = 0, psiPotions: Int = 0, startNet: Boolean = false, fundMove: MoveId? = null): BattleState {
        val bonuses = PetBonuses.of(p)
        val maxPsi = maxPsi(p)
        val pet = petFighter(p, hp)
        val st = BattleState(
            seed = seed, pet = pet,
            enemies = specs.mapIndexed { i, s -> enemyFighter(s, i + 1) }.toMutableList(),
            psi = (maxPsi + bonuses.startPsi).toFloat(), maxPsi = maxPsi,
            moves = (MoveId.equipped(p) + listOfNotNull(fundMove)).distinct().toMutableList(),
            empowered = MoveId.known(p).filter { MoveId.empowered(p, it) },
            rule = rule, world = world, bonuses = bonuses, healPotions = healPotions, psiPotions = psiPotions,
            levels = (MoveId.known(p) + listOfNotNull(fundMove)).associateWith { MoveId.level(p, it) }.filterValues { it > 1 },
            fundMove = fundMove,
        )
        if (startNet) addStatus(st, pet, Status.NET, 1)
        val rng = Random(seed)
        st.enemies.forEach { it.pattern = rng.nextInt(4); if (it.boss != null) it.pattern = 0 }
        st.log += when {
            st.enemies.any { it.boss != null } -> "${st.enemies.first { it.boss != null }.name} stellt sich dir in den Weg!"
            st.enemies.size == 1 -> "Ein Gegner taucht auf!"
            else -> "${st.enemies.size} Gegner tauchen auf!"
        }
        chooseIntents(st, rng)
        return st
    }

    // ------------------------------------------------------------ Hilfen für die Oberfläche

    fun level(st: BattleState, m: MoveId) = st.levels[m] ?: 1

    /** Psi-Kosten mit Stufen-Rabatt. */
    fun cost(st: BattleState, m: MoveId): Int {
        val lv = level(st, m)
        var c = m.psiCost
        if (m == MoveId.PSI_WAVE && lv >= 3) c = 2
        else if (m.type == null && lv >= 2) c -= 1
        else if (lv >= 3 && m !in LEVEL3_EXTRAS) c -= 1
        return c.coerceAtLeast(0)
    }

    /** Attacken mit eigenem Zusatz auf Stufe III (alle anderen kosten dort 1 Psi weniger). */
    private val LEVEL3_EXTRAS = setOf(MoveId.PAW, MoveId.BUMP, MoveId.DUCK, MoveId.PSI_WAVE, MoveId.SPARK_SNEEZE, MoveId.EMBER_SPARK,
        MoveId.FROST_BREATH, MoveId.ICICLE_SPEAR)

    /** Kombo, die [m] gegen [f] auslösen würde (für die Anzeige). */
    fun comboFor(m: MoveId, f: Fighter): Combo? = Combo.of(f, m.type)

    fun canUse(st: BattleState, m: MoveId): Boolean {
        if (st.charging != null) return m == st.charging
        if (st.psi + 0.001f < cost(st, m)) return false
        if (m == MoveId.PURR && st.purrUsed) return false
        if (st.pet.has(Status.ROOTED) && m.type?.physical == true) return false
        return true
    }

    fun needsTarget(m: MoveId) = m.target == Target.ONE || m.target == Target.ONE_ANY_ROW

    /** Wen eine Attacke treffen darf. Krallen und Wucht erreichen die hintere Reihe erst, wenn vorne niemand mehr steht. */
    fun targets(st: BattleState, m: MoveId): List<Fighter> {
        val alive = st.alive
        val front = alive.filter { it.front }
        return when (m.target) {
            Target.ONE -> if (front.isEmpty()) alive else front
            Target.ONE_ANY_ROW -> alive
            Target.ALL_FRONT -> if (front.isEmpty()) alive else front
            Target.ALL -> alive
            Target.SELF -> emptyList()
            Target.RANDOM -> alive
        }
    }

    fun effectiveness(st: BattleState, m: MoveId, f: Fighter): Double {
        val t = m.type ?: return 1.0
        var x = TypeChart.factor(t, f.body)
        if (st.rule == FloorRule.WET) { if (t == AttackType.FIRE) x *= 0.5; if (t == AttackType.FROST) x *= 2.0 }
        return x
    }

    // ------------------------------------------------------------ Runde

    fun playRound(prev: BattleState, action: PetAction): BattleState {
        if (prev.outcome != null) return prev
        val st = prev.copy()
        st.log = mutableListOf(); st.hits = mutableListOf()
        val rng = Random(st.seed * 31 + st.round * 7919L)
        val pet = st.pet
        st.ducking = false
        st.trap = false; st.trapHit = false; st.usedMove = null; st.combos = mutableListOf()

        if (st.rule == FloorRule.SLEEPY && !pet.has(Status.SLEEP) && rng.nextInt(100) < 10) {
            addStatus(st, pet, Status.SLEEP, 1); st.log += "${pet.name} nickt kurz ein …"
        }

        // Reihenfolge: Priorität, dann Tempo
        data class Turn(val f: Fighter, val prio: Boolean, val speed: Double, val tie: Int)
        val petPrio = st.charging == null && action.move?.priority == true
        val turns = mutableListOf(Turn(pet, petPrio, speedOf(pet), rng.nextInt(1000)))
        st.alive.forEach { turns += Turn(it, false, speedOf(it), rng.nextInt(1000)) }
        val ordered = turns.sortedWith(compareByDescending<Turn> { it.prio }.thenByDescending { it.speed }.thenBy { it.tie })
        val doubleRound = st.rule == FloorRule.CLOCKWORK && st.round % 3 == 0

        for (t in ordered) {
            val reps = if (doubleRound || (t.f.enemy?.trick == Trick.CLOCKWORK && st.round % 3 == 0)) 2 else 1
            repeat(reps) {
                if (st.outcome != null || !t.f.alive) return@repeat
                if (t.f.isPet) petTurn(st, action, rng) else enemyTurn(st, t.f, rng)
                checkOutcome(st)
            }
        }
        if (st.trap && !st.trapHit && st.outcome == null) st.log += "Die Krallenfalle verpufft, niemand hat angegriffen."
        if (st.outcome == null) endOfRound(st, rng)
        checkOutcome(st)
        if (st.outcome == null) {
            st.round++
            chooseIntents(st, rng)
        } else {
            st.log += if (st.outcome == Outcome.WON) "Gewonnen!" else "${pet.name} kann nicht mehr kämpfen …"
        }
        return st
    }

    private fun speedOf(f: Fighter): Double {
        var s = f.spd * stageMult(f.stage(StatKind.SPD))
        if (f.has(Status.SLOW)) s *= 0.8
        return s
    }

    private fun stageMult(n: Int) = if (n >= 0) 1.0 + 0.25 * n else Math.pow(0.8, -n.toDouble())

    private fun changeStage(f: Fighter, k: StatKind, by: Int) {
        f.stages[k] = (f.stage(k) + by).coerceIn(-3, 3)
    }

    private fun addStatus(st: BattleState, f: Fighter, s: Status, n: Int): Boolean {
        if (f.isPet) {
            if ((s == Status.FROZEN || s == Status.SLOW) && st.bonuses.noFreezeSlow) return false
            if (s == Status.CONFUSED && st.bonuses.noConfusion) return false
            if (s == Status.BURN && st.lavaHeart > 0) return false
        }
        if ((f.immune[s] ?: 0) > 0) return false
        if (f.boss != null && s.skips && s != Status.STUN) return false
        f.status[s] = max(f.status[s] ?: 0, n)
        return true
    }

    /** Kann [f] handeln? Verbraucht Aussetz-Zustände. */
    private fun canAct(st: BattleState, f: Fighter, rng: Random, clawAction: Boolean = false): Boolean {
        for (s in listOf(Status.FROZEN, Status.STUN, Status.SLEEP, Status.NET)) {
            if (f.has(s)) {
                if (s == Status.NET && f.isPet && clawAction) {
                    f.status.remove(Status.NET); f.immune[Status.NET] = 2
                    st.log += "${f.name} schneidet sich mit den Krallen frei!"
                    return true
                }
                f.status[s] = (f.status[s] ?: 1) - 1
                if ((f.status[s] ?: 0) <= 0) f.status.remove(s)
                if (s == Status.FROZEN || s == Status.STUN || s == Status.NET) f.immune[s] = 2
                st.log += "${f.name} " + when (s) {
                    Status.FROZEN -> "ist eingefroren."
                    Status.STUN -> "ist betäubt."
                    Status.SLEEP -> "schläft."
                    else -> "klebt im Netz fest."
                }
                return false
            }
        }
        if (st.rule == FloorRule.SLIPPERY || (st.alive.any { it.boss == BossKind.KLIRR } && st.bossPhase >= 2)) {
            val others = if (f.isPet) st.alive else listOf(st.pet)
            if (others.any { speedOf(it) > speedOf(f) } && rng.nextInt(100) < 20) {
                st.log += "${f.name} rutscht auf dem Eis aus!"; return false
            }
        }
        return true
    }

    // ------------------------------------------------------------ Zug des Tierchens

    private fun petTurn(st: BattleState, a: PetAction, rng: Random) {
        val pet = st.pet
        val m = a.move
        if (!canAct(st, pet, rng, clawAction = m?.type == AttackType.CLAW)) return
        // Aufgeladener Eiszapfen fliegt jetzt, egal was gewählt wurde
        st.charging?.let { c ->
            st.charging = null
            st.usedMove = c
            attackWith(st, c, st.chargeTarget, c in st.empowered, rng)
            return
        }
        a.potion?.let { usePotion(st, it); return }
        if (m == null) { st.log += "${pet.name} wartet ab."; return }
        if (!canUse(st, m)) { st.log += "Dafür reicht es gerade nicht."; return }
        // Verwirrung
        if (pet.has(Status.CONFUSED)) {
            pet.status[Status.CONFUSED] = (pet.status[Status.CONFUSED] ?: 1) - 1
            if ((pet.status[Status.CONFUSED] ?: 0) <= 0) pet.status.remove(Status.CONFUSED)
            if (rng.nextInt(100) < 33) {
                val d = hitRaw(pet, pet, 8, AttackType.CLAW, rng)
                damage(st, pet, d, "verwirrt")
                st.log += "${pet.name} ist verwirrt und trifft sich selbst (-$d)."
                return
            }
        }
        st.psi -= cost(st, m)
        st.lastPetMove = m
        st.usedMove = m
        if (m.type == AttackType.BLUNT) { pet.status.remove(Status.SOOT); st.roundsWithoutBlunt = 0 }
        if (m.type == AttackType.CLAW && st.rule == FloorRule.THORNS) damage(st, pet, 2, "Dornen")
        val emp = m in st.empowered
        when (m) {
            MoveId.DUCK -> { st.ducking = true; st.log += "${pet.name} duckt sich." }
            MoveId.MOON_SHIELD -> { pet.status[Status.SHIELD] = 2; st.log += "Ein Mondschild schützt ${pet.name}." }
            MoveId.HISS -> { st.alive.forEach { changeStage(it, StatKind.ATK, -1) }; st.log += "${pet.name} faucht. Die Gegner werden vorsichtiger." }
            MoveId.GROOM -> {
                listOf(Status.POISON, Status.BURN, Status.CONFUSED, Status.BLIND, Status.SOOT).forEach { pet.status.remove(it) }
                st.log += "${pet.name} putzt sich gründlich."
            }
            MoveId.NAP -> { st.psi = st.maxPsi.toFloat(); st.log += "${pet.name} macht ein Nickerchen. Psi-Punkte voll." }
            MoveId.PURR -> { st.purrUsed = true; heal(st, pet, (pet.maxHp * 0.25).roundToInt()); st.log += "${pet.name} schnurrt und erholt sich." }
            MoveId.LUCKY_PAW -> { st.lucky = true; if (emp) st.psi += 2; st.log += "${pet.name} reibt sich die Glückspfote." }
            MoveId.IRON_FUR -> { changeStage(pet, StatKind.DEF, if (emp) 3 else 2); st.log += "${pet.name}s Fell wird hart wie Eisen." }
            MoveId.DUST_WHIRL -> { st.alive.forEach { addStatus(st, it, Status.BLIND, 3) }; st.log += "${pet.name} wirbelt Staub auf. Die Gegner sehen schlechter." }
            MoveId.CLAW_TRAP -> { st.trap = true; st.log += "${pet.name} stellt eine Krallenfalle." }
            MoveId.MIRROR_PAW -> { st.mirrorPaw = true; st.log += "${pet.name}s Pfote glänzt wie ein Spiegel." }
            MoveId.CRYSTAL_ARMOR -> { st.crystal = 30; st.log += "Ein Kristallpanzer umhüllt ${pet.name}." }
            MoveId.LAVA_HEART -> { st.lavaHeart = 3; pet.status.remove(Status.BURN); st.log += "${pet.name}s Herz glüht wie Lava." }
            MoveId.HOT_WATER_BOTTLE -> {
                heal(st, pet, (pet.maxHp * 0.15).roundToInt()); pet.status.remove(Status.FROZEN); pet.status.remove(Status.SLOW)
                st.log += "${pet.name} kuschelt sich an die Wärmflasche."
            }
            MoveId.LULLABY_SONG -> {
                st.log += "${pet.name} summt ein Schlaflied …"
                st.alive.forEach { if (rng.nextInt(100) < 50 && addStatus(st, it, Status.SLEEP, 1)) st.log += "${it.name} schläft ein." }
            }
            MoveId.ICICLE_SPEAR -> {
                st.charging = m
                st.chargeTarget = a.target
                st.log += "${pet.name} lässt einen riesigen Eiszapfen wachsen …"
            }
            else -> attackWith(st, m, a.target, emp, rng)
        }
        // Befund 12: Eine Aufladeattacke zählt genau einmal. Beim Beginn des Aufladens wird noch nichts gezählt,
        // erst beim Auslösen (oben im Zweig „st.charging“).
        if (st.charging != null) st.usedMove = null
        st.psi = st.psi.coerceIn(0f, st.maxPsi.toFloat() + st.bonuses.startPsi)
    }

    private fun usePotion(st: BattleState, p: Potion) {
        when (p) {
            Potion.HEAL -> if (st.healPotions > 0) {
                st.healPotions--; heal(st, st.pet, (st.pet.maxHp * 0.4).roundToInt()); st.log += "Heiltrank getrunken."
            } else st.log += "Kein Heiltrank mehr."
            Potion.PSI -> if (st.psiPotions > 0) {
                st.psiPotions--; st.psi = min(st.maxPsi.toFloat(), st.psi + 5); st.log += "Psi-Trank getrunken: +5 Psi-Punkte."
            } else st.log += "Kein Psi-Trank mehr."
        }
    }

    private fun attackWith(st: BattleState, m: MoveId, targetId: Int?, emp: Boolean, rng: Random) {
        val pet = st.pet
        val type = m.type ?: return
        val possible = targets(st, m)
        if (possible.isEmpty()) return
        val victims = when (m.target) {
            Target.ALL, Target.ALL_FRONT -> possible
            Target.RANDOM -> emptyList()
            else -> listOf(possible.firstOrNull { it.id == targetId } ?: possible.first())
        }
        st.log += "${pet.name} setzt ${m.label} ein!"
        val lvMult = 1.0 + MoveId.bonusFor(level(st, m))
        fun strike(v: Fighter, h: Int) {
                if (!v.alive) return
                // Treffer?
                if (v.has(Status.BURROWED) && type != AttackType.BLUNT && type != AttackType.PSI) {
                    st.log += "${v.name} ist eingegraben, ${m.label} geht ins Leere."; return
                }
                val sure = st.sureHit && m != MoveId.FLARE
                if (sure) st.sureHit = false
                if (!sure && !hits(st, pet, v, m.accuracy, rng)) { st.log += "Daneben!"; st.hits += Hit(v.id, 0, note = "verfehlt"); return }
                var power = m.power.toDouble() * lvMult
                if (emp) power *= 1.3
                if (m == MoveId.POISON_BITE && (v.has(Status.POISON) || v.has(Status.TOXIC))) power *= 2.0
                if (m == MoveId.AVALANCHE && v.body == Body.ARMORED) power *= 1.25
                if (type == AttackType.FIRE && st.lavaHeart > 0) power *= 1.25
                val combo = Combo.of(v, type)
                if (combo == Combo.SHATTER) power *= 1.5
                if (combo == Combo.NIGHTMARE) power *= 1.4
                if (m == MoveId.RAGE_CLAW) power *= 1.0 + (1.0 - pet.hpPct())
                // Spiegel der Kristallfledermaus
                if (type == AttackType.PSI && v.has(Status.MIRROR)) {
                    v.status.remove(Status.MIRROR)
                    val d = (hitRaw(pet, pet, power.roundToInt(), type, rng) * 0.5).roundToInt().coerceAtLeast(1)
                    damage(st, pet, d, "gespiegelt")
                    st.log += "${v.name} wirft die Psi-Kraft zurück! (-$d)"
                    return
                }
                val forced = combo == Combo.HELPLESS || (m == MoveId.SHOOTING_STAR && v.has(Status.SLEEP))
                val crit = forced || st.lucky || rng.nextInt(100) < CRIT_CHANCE + st.bonuses.crit
                if (crit && !forced) st.lucky = false
                var mult = effectiveness(st, m, v) * (if (crit) CRIT_MULT else 1.0)
                mult *= 1.0 + ((st.bonuses.typeDmg[type] ?: 0) + (v.body?.let { st.bonuses.bodyDmg[it] } ?: 0)) / 100.0
                if (v.enemy?.world in st.bonuses.setWorlds || v.boss?.world in st.bonuses.setWorlds) mult *= 1.0 + SET_BONUS / 100.0
                val ignoreArmor = m == MoveId.LEVITATE_THROW || m == MoveId.SCISSOR_CUT
                val d = (hitRaw(pet, v, power.roundToInt(), type, rng, ignoreArmor) * mult * bossTaken(st, v, ignoreArmor)).roundToInt().coerceAtLeast(1)
                if (h == 0 || m.hits == 1) {
                    val eff = TypeChart.label(effectiveness(st, m, v))
                    st.log += "${v.name}: -$d" + (if (crit) ", kritisch" else "") + (if (eff.isNotEmpty()) ", $eff" else "")
                }
                damage(st, v, d, if (crit) "kritisch!" else TypeChart.label(effectiveness(st, m, v)))
                if (type == AttackType.PSI && st.rule == FloorRule.MIRRORING) damage(st, pet, (d * 0.15).roundToInt().coerceAtLeast(1), "Spiegel")
                if (combo != null) applyCombo(st, combo, v, d)
                afterPetHit(st, m, type, v, d, rng)
        }
        if (m.target == Target.RANDOM) {
            repeat(m.hits) { h -> st.alive.randomOrNull(rng)?.let { strike(it, h) } }
        } else for (v in victims) repeat(m.hits) { h -> strike(v, h) }
        if (m == MoveId.EARTH_STOMP) {
            st.alive.filter { !it.front }.maxByOrNull { it.hp }?.let { it.front = true; st.log += "${it.name} wird nach vorne gezogen!" }
        }
        if (type == AttackType.FROST && st.rule == FloorRule.FORGE_HEAT) heal(st, pet, 5)
    }

    private fun applyCombo(st: BattleState, c: Combo, v: Fighter, d: Int) {
        v.status.remove(c.status)
        st.log += "💥 Kombo: ${c.label}!"
        if (c.name !in st.combos) st.combos += c.name
        when (c) {
            Combo.POISON_BLAST -> st.alive.filter { it.id != v.id }.forEach { damage(st, it, (d * 0.25).roundToInt().coerceAtLeast(1), "Explosion") }
            Combo.STEAM -> if (v.alive) { v.status[Status.STUN] = 1; v.immune.remove(Status.STUN) }
            Combo.NIGHTMARE -> if (v.alive) addStatus(st, v, Status.CONFUSED, 2)
            Combo.DEEP_POISON -> if (v.alive) { v.status[Status.POISON] = 99; v.status[Status.TOXIC] = 99 }
            else -> {}
        }
    }

    private fun burnTurns(st: BattleState, base: Int, m: MoveId) = base + (if (st.bonuses.longerBurn) 1 else 0) + (if (level(st, m) >= 3) 1 else 0)

    /** Nebeneffekte eines Treffers des Tierchens. */
    private fun afterPetHit(st: BattleState, m: MoveId, type: AttackType, v: Fighter, d: Int, rng: Random) {
        val pet = st.pet
        val lv3 = level(st, m) >= 3
        // Wirkungen auf das Tierchen selbst, auch wenn das Ziel fällt
        when (m) {
            MoveId.MIND_THEFT -> { st.psi = min(st.maxPsi.toFloat(), st.psi + 3); st.log += "${pet.name} klaut 3 Psi-Punkte." }
            MoveId.ICE_GLIDE -> changeStage(pet, StatKind.SPD, 1)
            MoveId.FLARE -> { st.sureHit = true; st.log += "Alles ist hell erleuchtet. Der nächste Treffer sitzt." }
            MoveId.HEADBUTT -> damage(st, pet, (pet.maxHp * 0.05).roundToInt().coerceAtLeast(1), "Kopfnuss")
            MoveId.HAMMER -> pet.status[Status.SLOW] = 2
            else -> {}
        }
        when (type) {
            AttackType.CLAW -> if (rng.nextInt(100) < 20 || m == MoveId.SCRATCH_SPIN || m == MoveId.PAW_WHIRL || (m == MoveId.PAW && lv3)) changeStage(v, StatKind.DEF, -1)
            AttackType.BLUNT -> {
                if (v.has(Status.CURLED)) { v.status.remove(Status.CURLED); st.log += "${v.name}s Panzer wird aufgebrochen!" }
                if (v.boss == BossKind.KLIRR && v.shells > 0) { v.shells--; st.log += "Eine Panzerschicht von Klirr zerbricht! (noch ${v.shells})" }
            }
            AttackType.FROST -> {
                if (v.enemy == EnemyKind.SOOT_SPARK && v.alive) { v.hp = 0; st.log += "Der Rußfunke ist gelöscht!"; markDefeated(st, v) }
                if (v.boss == BossKind.FURNACE) v.heat = max(0, v.heat - 2)
            }
            else -> {}
        }
        if (!v.alive) return
        when (m) {
            MoveId.BUMP -> if (rng.nextInt(100) < (if (lv3) 30 else 20) && addStatus(st, v, Status.STUN, 1)) st.log += "${v.name} ist betäubt!"
            MoveId.BELLY_FLOP -> {
                if (rng.nextInt(100) < 30 && addStatus(st, v, Status.STUN, 1)) st.log += "${v.name} ist betäubt!"
                pet.status[Status.SLOW] = 2
            }
            MoveId.STINK_CLOUD -> addStatus(st, v, Status.POISON, 99)
            MoveId.SPORES -> { addStatus(st, v, Status.POISON, 99); if (rng.nextInt(100) < 40 && addStatus(st, v, Status.SLEEP, 2)) st.log += "${v.name} schläft ein." }
            MoveId.SPARK_SNEEZE, MoveId.EMBER_BREATH -> addStatus(st, v, Status.BURN, burnTurns(st, 3, m))
            MoveId.FROST_BREATH -> { addStatus(st, v, Status.SLOW, 3); if (lv3 && rng.nextInt(100) < 10 && addStatus(st, v, Status.FROZEN, 1)) st.log += "${v.name} friert ein!" }
            MoveId.EMBER_SPARK -> if (rng.nextInt(100) < 30) addStatus(st, v, Status.BURN, burnTurns(st, 2, m))
            MoveId.POISON_BITE -> if (!v.has(Status.POISON) && rng.nextInt(100) < 30) addStatus(st, v, Status.POISON, 99)
            MoveId.ICICLE_SPEAR -> if (rng.nextInt(100) < (if (lv3) 40 else 30) && addStatus(st, v, Status.FROZEN, 1)) st.log += "${v.name} friert ein!"
            MoveId.SPARK_RAIN -> if (rng.nextInt(100) < 20) addStatus(st, v, Status.BURN, burnTurns(st, 2, m))
            MoveId.SOOT_CLOUD -> { addStatus(st, v, Status.POISON, 99); addStatus(st, v, Status.BLIND, 3) }
            MoveId.HAMMER -> if (rng.nextInt(100) < 25 && addStatus(st, v, Status.STUN, 1)) st.log += "${v.name} ist betäubt!"
            MoveId.DREAM_CATCHER -> {
                listOf(Status.CURLED, Status.MIRROR, Status.BURROWED, Status.SHIELD).forEach { v.status.remove(it) }
                StatKind.entries.forEach { k -> if (v.stage(k) > 0) v.stages[k] = 0 }
                st.log += "${v.name} verliert Schutz und Stärkungen."
            }
            MoveId.ECLIPSE -> if (rng.nextInt(100) < 30) addStatus(st, v, Status.CONFUSED, 2)
            MoveId.SNOWBALL -> if (rng.nextInt(100) < 20) addStatus(st, v, Status.SLOW, 2)
            MoveId.STINKY_FOOT -> addStatus(st, v, Status.POISON, 99)
            MoveId.BLIZZARD -> if (rng.nextInt(100) < 30 && addStatus(st, v, Status.FROZEN, 1)) st.log += "${v.name} friert ein!"
            MoveId.NIGHTMARE_GAZE -> { addStatus(st, v, Status.CONFUSED, 2); changeStage(v, StatKind.ATK, -1) }
            else -> {}
        }
        // passive Tricks
        when (v.enemy?.trick) {
            Trick.THORNS -> if (type.physical) { damage(st, pet, (pet.maxHp * 0.08).roundToInt().coerceAtLeast(1), "Dornen"); st.log += "Autsch, Dornen!" }
            Trick.PINCER -> if (type == AttackType.CLAW) {
                val c = hitRaw(v, pet, 14, AttackType.BLUNT, rng); damage(st, pet, c, "Konter"); st.log += "${v.name} kneift zurück (-$c)."
            }
            Trick.SPLIT -> if (type == AttackType.CLAW && v.hp > 4 && st.alive.size < 4) {
                val half = v.hp / 2
                v.hp -= half
                val s = enemyFighter(EnemySpec(EnemyKind.SLIMELING, level = v.level), st.nextId++)
                s.maxHp = half; s.hp = half
                st.enemies.add(s)
                st.log += "${v.name} teilt sich!"
            }
            else -> {}
        }
    }

    // ------------------------------------------------------------ Schaden

    private fun hits(st: BattleState, att: Fighter, def: Fighter, accuracy: Int, rng: Random): Boolean {
        if (accuracy >= 100 && att.isPet) return true
        var acc = accuracy
        if (att.isPet) {
            var malus = 0
            if (att.has(Status.BLIND)) malus += 15
            if (att.has(Status.SOOT)) malus += 25
            if (st.rule == FloorRule.ASH) malus += 10
            if (st.bonuses.accLock) malus = 0
            acc += st.bonuses.accuracy - malus
            if (def.role == Role.QUICK) acc -= EVADE_QUICK
        } else {
            if (st.rule == FloorRule.ASH) acc -= 10
            if (att.has(Status.BLIND)) acc -= 15
        }
        return rng.nextInt(100) < acc
    }

    /** Grundschaden nach der Formel aus dem Konzept, ohne Typfaktor. */
    private fun hitRaw(att: Fighter, def: Fighter, power: Int, type: AttackType, rng: Random, ignoreArmor: Boolean = false): Int {
        val a = (if (type.physical) att.atk else att.psiStat) * stageMult(att.stage(StatKind.ATK))
        var v = def.def * stageMult(def.stage(StatKind.DEF))
        if (def.has(Status.CURLED) && !ignoreArmor) v *= 2.0
        if (ignoreArmor) v *= 0.5
        val variance = 0.85 + rng.nextDouble() * 0.15
        return (power * a / (v + 10) * variance).roundToInt().coerceAtLeast(1)
    }

    private fun bossTaken(st: BattleState, v: Fighter, ignoreArmor: Boolean): Double = when (v.boss) {
        BossKind.KLIRR -> if (v.shells == 0 || ignoreArmor) 2.0 else 1.0
        BossKind.FURNACE -> if (st.bossPhase >= 3) 1.5 else 1.0
        else -> 1.0
    }

    private fun damage(st: BattleState, f: Fighter, amount: Int, note: String = "") {
        if (amount <= 0 || !f.alive) return
        f.hp = max(0, f.hp - amount)
        st.hits += Hit(f.id, amount, note = note)
        if (f.has(Status.SLEEP)) f.status.remove(Status.SLEEP)
        if (!f.alive && !f.isPet) markDefeated(st, f)
    }

    private fun heal(st: BattleState, f: Fighter, amount: Int) {
        if (!f.alive || amount <= 0) return
        val before = f.hp
        f.hp = min(f.maxHp, f.hp + amount)
        if (f.hp > before) st.hits += Hit(f.id, f.hp - before, heal = true)
    }

    private fun markDefeated(st: BattleState, f: Fighter) {
        f.enemy?.let { st.defeated += it }
        if (f.boss != null) st.bossDefeated = true
        if (f.carried > 0) { st.log += "Der gestohlene Sternenstaub ist wieder da."; f.carried = 0 }
        st.log += "${f.name} ist besiegt."
    }

    /** Gegner trifft das Tierchen. */
    private fun enemyHit(st: BattleState, e: Fighter, power: Int, type: AttackType, rng: Random, label: String? = null): Int {
        val pet = st.pet
        if (!hits(st, e, pet, 95, rng)) { st.log += "${e.name} verfehlt ${pet.name}."; st.hits += Hit(0, 0, note = "verfehlt"); trapCounter(st, e, rng); return 0 }
        var d = hitRaw(e, pet, power, type, rng).toDouble()
        if (e.enemy?.trick == Trick.RAGE && e.hpPct() < 0.5f) d *= 2.0
        if (pet.has(Status.SHIELD)) d *= 0.5
        if (st.ducking) d *= 0.5
        var dmg = d.roundToInt().coerceAtLeast(1)
        if (st.crystal > 0) {
            val absorbed = min(st.crystal, dmg)
            st.crystal -= absorbed; dmg -= absorbed
            st.log += if (st.crystal > 0) "Der Kristallpanzer schluckt $absorbed Schaden." else "Der Kristallpanzer zerspringt."
        }
        if (dmg > 0) damage(st, pet, dmg)
        st.log += "${e.name} ${label ?: "greift an"}: -$dmg"
        if (st.ducking && level(st, MoveId.DUCK) >= 3 && e.alive) { damage(st, e, 10, "Ducken"); st.log += "${e.name} stößt sich an ${pet.name} (-10)." }
        trapCounter(st, e, rng)
        return dmg
    }

    /** Krallenfalle: Wer angreift, bekommt den Hieb. */
    private fun trapCounter(st: BattleState, e: Fighter, rng: Random) {
        if (!st.trap || !e.alive || !st.pet.alive) return
        val m = MoveId.CLAW_TRAP
        val lvMult = 1.0 + MoveId.bonusFor(level(st, m))
        val f = TypeChart.factor(AttackType.CLAW, e.body)
        val d = (hitRaw(st.pet, e, (m.power * lvMult).roundToInt(), AttackType.CLAW, rng) * f * bossTaken(st, e, false)).roundToInt().coerceAtLeast(1)
        st.trapHit = true
        damage(st, e, d, "Falle")
        st.log += "Die Krallenfalle schnappt zu! ${e.name}: -$d"
    }

    /** Ein Gegner gibt dem Tierchen einen Zustand. Mit Spiegelpfote trifft er sich selbst. */
    private fun enemyStatus(st: BattleState, e: Fighter, s: Status, n: Int): Boolean {
        if (st.mirrorPaw) {
            st.mirrorPaw = false
            addStatus(st, e, s, n)
            st.log += "Die Spiegelpfote wirft ${s.label} auf ${e.name} zurück!"
            return false
        }
        return addStatus(st, st.pet, s, n)
    }

    // ------------------------------------------------------------ Gegnerzug

    private fun enemyTurn(st: BattleState, e: Fighter, rng: Random) {
        e.acted++
        if (!canAct(st, e, rng)) return
        if (e.has(Status.CONFUSED)) {
            e.status[Status.CONFUSED] = (e.status[Status.CONFUSED] ?: 1) - 1
            if ((e.status[Status.CONFUSED] ?: 0) <= 0) e.status.remove(Status.CONFUSED)
            if (rng.nextInt(100) < 33) { val d = hitRaw(e, e, 8, AttackType.CLAW, rng); damage(st, e, d, "verwirrt"); st.log += "${e.name} ist verwirrt und trifft sich selbst."; return }
        }
        val intent = e.intent ?: Intent(IntentKind.ATTACK, "Angriff")
        if (e.boss != null) { bossTurn(st, e, intent, rng); return }
        val k = e.enemy ?: return
        val pet = st.pet
        when (intent.kind) {
            IntentKind.CHARGE -> st.log += "${e.name} ${if (k == EnemyKind.ICICLE) "knackt bedrohlich …" else "holt aus …"}"
            IntentKind.BIG -> enemyHit(st, e, when (k) { EnemyKind.ICICLE -> 28; EnemyKind.ASH_GOLEM -> 32; else -> BIG_POWER }, k.attackType, rng, "schlägt mit voller Kraft zu")
            IntentKind.COPY -> copyAttack(st, e, rng)
            IntentKind.SUMMON -> summon(st, e, EnemyKind.DUST_BUNNY, rng)
            IntentKind.ATTACK -> {
                if (st.rule == FloorRule.MIRROR_HALL && st.lastPetMove?.type != null && rng.nextBoolean()) copyAttack(st, e, rng)
                else enemyHit(st, e, NORMAL_POWER, k.attackType, rng)
            }
            IntentKind.TRICK -> when (k.trick) {
                Trick.SNEEZE -> { if (!st.bonuses.accLock) { enemyStatus(st, e, Status.BLIND, 3); st.log += "${e.name} niest! ${pet.name} sieht schlechter." } else st.log += "${e.name} niest, aber ${pet.name} lässt sich nicht beirren." }
                Trick.CURL -> {
                    val group = if (e.elite) st.alive else listOf(e)
                    group.forEach { it.status[Status.CURLED] = 2 }
                    st.log += if (e.elite) "${e.name} rollt sich ein und schützt alle!" else "${e.name} rollt sich ein."
                }
                Trick.DUST_CLOUD -> if (enemyStatus(st, e, Status.CONFUSED, 2)) st.log += "${e.name} wirbelt Staub auf. ${pet.name} ist verwirrt!" else st.log += "Die Staubwolke verfliegt."
                Trick.WEB -> {
                    if (rng.nextInt(100) < st.bonuses.netDodge) st.log += "${pet.name} weicht dem Netz aus!"
                    else { enemyStatus(st, e, Status.NET, 1); st.log += "${e.name} wirft ein Netz! ${pet.name} klebt fest." }
                }
                Trick.TONGUE -> when {
                    st.healPotions > 0 -> { st.healPotions--; st.log += "${e.name} schnappt sich einen Heiltrank!" }
                    st.psiPotions > 0 -> { st.psiPotions--; st.log += "${e.name} schnappt sich einen Psi-Trank!" }
                    else -> enemyHit(st, e, NORMAL_POWER, k.attackType, rng, "schnappt zu")
                }
                Trick.SPORE_HEAL -> {
                    st.alive.forEach { heal(st, it, (it.maxHp * (if (it.boss != null) SPORE_HEAL_BOSS else 0.15)).roundToInt()) }
                    enemyStatus(st, e, Status.POISON, 99)
                    st.log += "${e.name} verteilt Sporen: Gegner heilen sich, ${pet.name} ist vergiftet."
                }
                Trick.DOUBLE_STING -> { enemyHit(st, e, NORMAL_POWER, k.attackType, rng, "sticht"); if (pet.alive) enemyHit(st, e, NORMAL_POWER, k.attackType, rng, "sticht nochmal") }
                Trick.FROST_TOUCH -> { if (enemyHit(st, e, NORMAL_POWER, AttackType.FROST, rng, "berührt") > 0 && rng.nextInt(100) < 30 && enemyStatus(st, e, Status.FROZEN, 1)) st.log += "${pet.name} friert ein!" }
                Trick.MIRROR -> { e.status[Status.MIRROR] = 2; st.log += "${e.name} funkelt …" }
                Trick.BURROW -> { e.status[Status.BURROWED] = 2; st.log += "${e.name} gräbt sich ein." }
                Trick.STEAL -> {
                    val amount = 10 * st.world
                    e.carried += amount
                    st.log += "${e.name} klaut $amount Sternenstaub!"
                }
                Trick.BURN_TRAIL -> { enemyHit(st, e, NORMAL_POWER, AttackType.FIRE, rng, "kriecht heran"); enemyStatus(st, e, Status.BURN, 3) }
                Trick.FORGE -> { st.alive.forEach { changeStage(it, StatKind.ATK, 1) }; st.log += "${e.name} schmiedet: Angriff aller Gegner steigt!" }
                Trick.LULLABY -> { if (enemyStatus(st, e, Status.SLEEP, if (st.bonuses.shorterSleep) 1 else 2)) st.log += "${e.name} singt ein Gute-Nacht-Lied. ${pet.name} schläft ein." }
                Trick.WHISPER -> {
                    st.psi = max(0f, st.psi - 3)
                    st.alive.forEach { heal(st, it, (it.maxHp * 0.10).roundToInt()) }
                    st.log += "${e.name} flüstert. ${pet.name} verliert 3 Psi-Punkte."
                }
                Trick.HOWL -> summon(st, e, EnemyKind.DUST_BUNNY, rng)
                Trick.COPY -> copyAttack(st, e, rng)
                Trick.ICICLE, Trick.HEAVY_SMASH -> enemyHit(st, e, BIG_POWER, k.attackType, rng, "schlägt hart zu")
                else -> enemyHit(st, e, NORMAL_POWER, k.attackType, rng)
            }
        }
    }

    private fun copyAttack(st: BattleState, e: Fighter, rng: Random) {
        val m = st.lastPetMove
        if (m?.type == null) { enemyHit(st, e, NORMAL_POWER, e.enemy?.attackType ?: AttackType.PSI, rng); return }
        enemyHit(st, e, (m.power / 2).coerceIn(NORMAL_POWER, 30), m.type, rng, "kopiert ${m.label}")
    }

    private fun summon(st: BattleState, e: Fighter, kind: EnemyKind, rng: Random) {
        if (st.alive.size >= 4) { enemyHit(st, e, NORMAL_POWER, e.enemy?.attackType ?: AttackType.CLAW, rng); return }
        val f = enemyFighter(EnemySpec(kind, level = e.level), st.nextId++)
        f.intent = Intent(IntentKind.ATTACK, "Angriff")
        st.enemies.add(f)
        st.log += "${e.name} ruft ${f.name} zu Hilfe!"
    }

    // ------------------------------------------------------------ Bosse

    private fun bossTurn(st: BattleState, b: Fighter, intent: Intent, rng: Random) {
        val pet = st.pet
        val type = when (b.boss) { BossKind.MORLA -> AttackType.POISON; BossKind.KLIRR -> AttackType.BLUNT; BossKind.NIGHTMARE -> AttackType.PSI; else -> AttackType.FIRE }
        when (intent.kind) {
            IntentKind.SUMMON -> when (b.boss) {
                BossKind.MORLA -> if (st.alive.count { it.enemy == EnemyKind.SPORE_SHROOM } < 1) summon(st, b, EnemyKind.SPORE_SHROOM, rng) else enemyHit(st, b, NORMAL_POWER, type, rng)
                else -> summon(st, b, EnemyKind.DUST_BUNNY, rng)
            }
            IntentKind.TRICK -> when (b.boss) {
                BossKind.SOOT_KING -> { enemyStatus(st, b, Status.SOOT, 99); st.log += "Der Rußkönig hüllt alles in Ruß! Eine Wucht-Attacke wirbelt ihn weg." }
                else -> enemyHit(st, b, NORMAL_POWER, type, rng)
            }
            IntentKind.BIG -> enemyHit(st, b, BIG_POWER + 4, type, rng, "setzt zum Großangriff an")
            IntentKind.COPY -> {
                if (st.bossPhase >= 2) {
                    val m = st.moves.filter { it.type != null }.randomOrNull(rng)
                    if (m != null) enemyHit(st, b, (m.power / 2).coerceIn(NORMAL_POWER, 24), m.type!!, rng, "nutzt deine Attacke ${m.label}")
                    else enemyHit(st, b, NORMAL_POWER, type, rng)
                } else {
                    val m = st.lastPetMove
                    if (m?.type != null) enemyHit(st, b, (m.power / 3).coerceIn(NORMAL_POWER, 20), m.type, rng, "kopiert ${m.label}") else enemyHit(st, b, NORMAL_POWER, type, rng)
                }
            }
            IntentKind.CHARGE -> st.log += "${b.name} sammelt Kraft …"
            IntentKind.ATTACK -> enemyHit(st, b, NORMAL_POWER + 2, type, rng)
        }
    }

    private fun bossPhases(st: BattleState) {
        val b = st.alive.firstOrNull { it.boss != null } ?: return
        val pct = b.hpPct()
        val newPhase = when { pct <= 0.2f -> 3; pct <= 0.5f -> 2; else -> 1 }
        if (newPhase <= st.bossPhase) return
        val maxPhase = if (b.boss == BossKind.SOOT_KING) 2 else 3
        st.bossPhase = min(newPhase, maxPhase)
        when (b.boss) {
            BossKind.SOOT_KING -> { addStatus(st, st.pet, Status.SOOT, 99); st.log += "Phase 2: Rußwolke! Treffsicherheit -25 %, bis du Wucht einsetzt." }
            BossKind.MORLA -> st.log += if (st.bossPhase == 2) "Phase 2: Sporensturm! Jede Runde Gift." else {
                st.pet.status[Status.ROOTED] = 99; "Phase 3: Wurzelgriff! Krallen und Wucht sind blockiert."
            }
            BossKind.KLIRR -> st.log += if (st.bossPhase == 2) "Phase 2: Klirr friert den Boden. Vorsicht, glatt!" else "Phase 3: Klirr baut Panzer nach, wenn du 2 Runden keine Wucht nutzt."
            BossKind.FURNACE -> st.log += if (st.bossPhase == 2) "Phase 2: Die Hitze steigt doppelt so schnell!" else "Phase 3: Der Eisenofen schmilzt. Panzer weg, aber alles brennt!"
            BossKind.NIGHTMARE -> {
                if (st.bossPhase == 2) {
                    b.atk = max(b.atk, st.pet.atk); b.def = st.pet.def; b.spd = st.pet.spd; b.psiStat = max(b.psiStat, st.pet.psiStat)
                    st.log += "Phase 2: Der Albtraum wird zu deinem Schatten!"
                } else st.log += "Phase 3: Der Albtraum vertauscht deine Attacken!"
            }
            null -> {}
        }
    }

    // ------------------------------------------------------------ Rundenende

    private fun endOfRound(st: BattleState, rng: Random) {
        val pet = st.pet
        val all = listOf(pet) + st.alive
        for (f in all) {
            if (!f.alive) continue
            if (f.has(Status.BURN)) {
                var d = f.maxHp * 0.06
                if (f.isPet) d *= 1.0 - st.bonuses.burnTaken / 100.0
                damage(st, f, d.roundToInt().coerceAtLeast(1), "Brand")
            }
            if (f.alive && f.has(Status.POISON)) damage(st, f, (f.maxHp * (if (f.has(Status.TOXIC)) 0.10 else 0.05)).roundToInt().coerceAtLeast(1), "Gift")
            if (f.alive && st.rule == FloorRule.SPORE_AIR) damage(st, f, (f.maxHp * 0.03).roundToInt().coerceAtLeast(1), "Sporen")
            // Laufzeiten
            for (s in listOf(Status.BURN, Status.SLOW, Status.BLIND, Status.SHIELD, Status.CURLED, Status.BURROWED, Status.MIRROR)) {
                val n = f.status[s] ?: continue
                if (n <= 1) f.status.remove(s) else f.status[s] = n - 1
            }
            f.immune.keys.toList().forEach { s -> val n = (f.immune[s] ?: 0) - 1; if (n <= 0) f.immune.remove(s) else f.immune[s] = n }
        }
        if (st.lavaHeart > 0) st.lavaHeart--
        if (pet.alive && st.bonuses.healPerRound > 0) heal(st, pet, (pet.maxHp * st.bonuses.healPerRound / 100.0).roundToInt().coerceAtLeast(1))

        // Eiszapfen-Decke
        if (st.rule == FloorRule.ICICLES) {
            if (st.icicleWarning) {
                st.icicleWarning = false
                val d = (pet.maxHp * 0.15 * (if (pet.has(Status.SHIELD)) 0.5 else 1.0) * (if (st.ducking) 0.0 else 1.0)).roundToInt()
                if (d > 0) { damage(st, pet, d, "Eiszapfen"); st.log += "Ein Eiszapfen fällt von der Decke! (-$d)" } else st.log += "Ein Eiszapfen fällt, aber ${pet.name} ist in Deckung."
            }
            if ((st.round + 1) % 4 == 0) { st.icicleWarning = true; st.log += "Über dir knackt es in der Decke …" }
        }

        // Rußfunken zählen herunter, Kobolde fliehen
        for (e in st.alive) {
            if (e.enemy?.trick == Trick.COUNTDOWN) {
                e.counter--
                if (e.counter <= 0) {
                    val d = (hitRaw(e, pet, 24, AttackType.FIRE, rng) * (if (pet.has(Status.SHIELD)) 0.5 else 1.0) * (if (st.ducking) 0.5 else 1.0)).roundToInt()
                    e.hp = 0; markDefeated(st, e)
                    damage(st, pet, d, "Explosion")
                    st.log += "Der Rußfunke explodiert! (-$d)"
                }
            }
            if (e.enemy?.trick == Trick.STEAL && e.acted >= 3) {
                e.fled = true
                st.stolenDust += e.carried
                st.log += if (e.carried > 0) "${e.name} flieht mit ${e.carried} Sternenstaub!" else "${e.name} flieht."
            }
        }

        // Bosse
        val boss = st.alive.firstOrNull { it.boss != null }
        if (boss != null) {
            bossPhases(st)
            when (boss.boss) {
                BossKind.MORLA -> if (st.bossPhase >= 2 && (!SPORE_STORM_NEEDS_SHROOM || st.alive.any { it.enemy == EnemyKind.SPORE_SHROOM })) addStatus(st, pet, Status.POISON, 99)
                BossKind.KLIRR -> {
                    st.roundsWithoutBlunt++
                    if (st.bossPhase >= 3 && st.roundsWithoutBlunt >= 2 && boss.shells < 3) {
                        boss.shells++; st.roundsWithoutBlunt = 0; st.log += "Klirr baut eine Panzerschicht nach! (${boss.shells})"
                    }
                }
                BossKind.FURNACE -> {
                    boss.heat += if (st.bossPhase >= 2) 2 else 1
                    if (st.bossPhase >= 3) addStatus(st, pet, Status.BURN, 2)
                    if (boss.heat >= 5) {
                        boss.heat = 0
                        val d = (hitRaw(boss, pet, 45, AttackType.FIRE, rng) * (if (pet.has(Status.SHIELD)) 0.5 else 1.0) * (if (st.ducking) 0.5 else 1.0)).roundToInt()
                        damage(st, pet, d, "Explosion")
                        st.log += "Der Eisenofen explodiert vor Hitze! (-$d)"
                    }
                }
                BossKind.NIGHTMARE -> if (st.bossPhase >= 3) st.moves = st.moves.shuffled(rng).toMutableList()
                else -> {}
            }
        }

        st.psi = min(st.maxPsi.toFloat(), st.psi + PSI_REGEN * (1f + st.bonuses.psiRegen / 100f))
    }

    private fun checkOutcome(st: BattleState) {
        if (st.outcome != null) return
        if (!st.pet.alive) st.outcome = Outcome.LOST
        else if (st.alive.isEmpty()) st.outcome = Outcome.WON
    }

    // ------------------------------------------------------------ Absichten der Gegner

    private fun chooseIntents(st: BattleState, rng: Random) {
        for (e in st.alive) {
            e.intent = if (e.boss != null) bossIntent(st, e) else enemyIntent(st, e, rng)
            e.pattern++
        }
    }

    private fun enemyIntent(st: BattleState, e: Fighter, rng: Random): Intent {
        val k = e.enemy ?: return Intent(IntentKind.ATTACK, "Angriff")
        val trick = Intent(IntentKind.TRICK, k.trick.label)
        val atk = Intent(IntentKind.ATTACK, "Angriff")
        val big = Intent(IntentKind.BIG, "Großangriff")
        if (k.role == Role.HEALER && st.alive.any { it.hpPct() < 0.5f }) return trick
        return when (k.trick) {
            Trick.HEAVY_SMASH -> if (e.pattern % 2 == 0) Intent(IntentKind.CHARGE, "Holt aus") else Intent(IntentKind.BIG, "Schwerer Schlag")
            Trick.ICICLE -> listOf(atk, Intent(IntentKind.CHARGE, "Es knackt"), Intent(IntentKind.BIG, "Eiszapfen-Fall"))[e.pattern % 3]
            Trick.STEAL -> if (e.pattern % 2 == 0) trick else atk
            Trick.HOWL -> if (st.alive.size < 3 && e.pattern % 3 == 1) Intent(IntentKind.SUMMON, "Heulen") else listOf(atk, atk, big)[e.pattern % 3]
            Trick.COPY -> if (st.lastPetMove?.type != null && e.pattern % 2 == 0) Intent(IntentKind.COPY, "Kopiert dich") else atk
            Trick.MIRROR -> if (e.pattern % 3 == 0) Intent(IntentKind.TRICK, "Funkelt") else atk
            else -> if (k.trick.passive) listOf(atk, atk, big)[e.pattern % 3]
                    else listOf(atk, trick, atk, big)[e.pattern % 4]
        }.let { if (it.kind == IntentKind.BIG && k.role == Role.SWARM) atk else it }
    }

    private fun bossIntent(st: BattleState, b: Fighter): Intent {
        val atk = Intent(IntentKind.ATTACK, "Angriff")
        val big = Intent(IntentKind.BIG, "Großangriff")
        val i = b.pattern
        return when (b.boss) {
            BossKind.SOOT_KING -> if (st.bossPhase == 1) listOf(atk, Intent(IntentKind.SUMMON, "Ruft Staubflusen"), atk, big)[i % 4]
                else listOf(atk, Intent(IntentKind.TRICK, "Rußwolke"), Intent(IntentKind.SUMMON, "Ruft Staubflusen"), big)[i % 4]
            BossKind.MORLA -> listOf(atk, Intent(IntentKind.SUMMON, "Pflanzt einen Pilz"), atk, big, atk)[i % 5]
            BossKind.KLIRR -> listOf(atk, atk, big)[i % 3]
            BossKind.FURNACE -> if (b.heat >= 4 || (st.bossPhase >= 2 && b.heat >= 3)) Intent(IntentKind.CHARGE, "Glüht! Gleich explodiert er") else listOf(atk, atk, big)[i % 3]
            BossKind.NIGHTMARE -> if (st.bossPhase == 1) (if (st.lastPetMove?.type != null) Intent(IntentKind.COPY, "Kopiert dich") else atk)
                else listOf(Intent(IntentKind.COPY, "Deine eigene Attacke"), atk, big)[i % 3]
            null -> atk
        }
    }
}
