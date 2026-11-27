package app.psykit.core.engine

import app.psykit.core.model.Berry
import app.psykit.core.model.Effect
import app.psykit.core.model.GardenGoal
import app.psykit.core.model.Item
import app.psykit.core.model.PetState
import app.psykit.core.model.Plot
import app.psykit.core.model.PlotStage
import app.psykit.core.model.Quest
import app.psykit.core.model.Reaction
import app.psykit.core.model.Smoothie
import app.psykit.core.model.Stage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Beerengarten 2.0: Sorten, Wachstum nach Stunden, Unkraut, Dünger, Schnecken, Regen,
 * mehr Beete, Gießkannen, Smoothies und Garten-Sammelbuch.
 */
object GardenEngine {
    private const val HOUR = 3_600_000L

    const val MAX_BEDS = 6
    /** Preis und Mindestlevel für Beet 4, 5, 6. */
    val BED_PRICES = listOf(150, 300, 500)
    val BED_LEVELS = listOf(8, 18, 30)

    /** Gießkannen: Kupfer, Silber, Gold. */
    val CAN_NAMES = listOf("Kupferkanne", "Silberkanne", "Goldkanne")
    val CAN_PRICES = listOf(120, 300, 600)
    val CAN_TEXTS = listOf(
        "Pflanzen vertrocknen 50 % später",
        "Ein Gießen gilt auch für das Nachbarbeet",
        "10 % Chance auf doppeltes Wachstum",
    )

    const val WEED_CHANCE = 0.30
    const val WEED_SLOWDOWN = 0.75f   // 25 % langsamer
    const val FERTILIZER_SPEEDUP = 1.5f // 50 % schneller
    /** Befund 10: Anzeigetext zum Düngerfaktor, abgeleitet aus [FERTILIZER_SPEEDUP]. */
    val fertilizerPercent: Int get() = kotlin.math.round((FERTILIZER_SPEEDUP - 1f) * 100f).toInt()
    fun hoursText(h: Float): String = if (h == kotlin.math.floor(h)) "${h.toInt()} Stunden" else "${"%.1f".format(h).replace('.', ',')} Stunden"
    const val SNAIL_CHANCE = 0.10
    const val SNAIL_FROM_HOUR = 7
    const val SNAIL_TO_HOUR = 23
    const val SNAIL_BITE_HOURS = 6L
    const val COMPOST_PER_FERTILIZER = 2

    // ---------------- Zustand eines Beets ----------------

    fun stage(p: Plot, now: Long, s: PetState? = null): PlotStage {
        if (p.empty) return PlotStage.EMPTY
        val k = p.kind
        if (p.waterings >= k.needWaterings) return PlotStage.RIPE
        val witherH = k.witherHours * (if ((s?.canLevel ?: 0) >= 1) 1.5f else 1f)
        val last = max(p.lastWaterAt, p.plantedAt)
        if (now - last > (witherH * HOUR).toLong()) return PlotStage.WITHERED
        return when {
            p.waterings == 0 -> PlotStage.SEED
            p.waterings * 2 < k.needWaterings -> PlotStage.SPROUT
            else -> PlotStage.BUSH
        }
    }

    fun growing(st: PlotStage) = st == PlotStage.SEED || st == PlotStage.SPROUT || st == PlotStage.BUSH

    /** Wachstumstempo: Unkraut bremst, Dünger und Glücksbringer beschleunigen. */
    fun speed(s: PetState, p: Plot): Float {
        var f = 1f + Item.sum<Effect.GardenGrowth>(s) { it.pct } / 100f
        if (p.weedSince > 0) f *= WEED_SLOWDOWN
        if (p.fertilized) f *= FERTILIZER_SPEEDUP
        return f
    }

    /** Wie viele Stunden bis zum nächsten Gießgang mit Wachstum (0 = jetzt). */
    fun hoursUntilGrowth(s: PetState, p: Plot, now: Long): Float {
        if (p.waterings == 0 || p.lastCountedAt == 0L) return 0f
        val gap = p.kind.gapHours / speed(s, p)
        val waited = (now - p.lastCountedAt) / HOUR.toFloat()
        return (gap - waited).coerceAtLeast(0f)
    }

    fun beds(s: PetState): List<Plot> {
        val n = s.bedsUnlocked.coerceIn(3, MAX_BEDS)
        return (0 until n).map { s.plots.getOrElse(it) { Plot() } }
    }

    private fun withPlot(s: PetState, i: Int, p: Plot): PetState {
        val plots = beds(s).toMutableList()
        plots[i] = p
        return s.copy(plots = plots)
    }

    // ---------------- Wetter ----------------

    fun rainy(s: PetState, epochDay: Long): Boolean = Random(s.bornAt * 31 + epochDay * 7_919L).nextInt(7) == 0

    // ---------------- Zeitlauf: Unkraut, Schnecken, Regen ----------------

    /** Wird bei jedem Zeitschritt aufgerufen. Würfelt pro Tag und Beet Unkraut und Schnecken aus. */
    fun refresh(s0: PetState, now: Long, zone: ZoneId): PetState {
        if (s0.stage == Stage.EGG) return s0
        var s = migrate(s0, zone)
        val today = PetEngine.epochDay(now, zone)
        val dayStart = LocalDate.ofEpochDay(today).atStartOfDay(zone).toInstant().toEpochMilli()
        val fewerSnails = Item.has<Effect.FewerSnails>(s)
        var changed = false
        val plots = beds(s).mapIndexed { i, p0 ->
            var p = p0
            if (p.empty) return@mapIndexed p
            val st = stage(p, now, s)
            if (!growing(st)) {
                // Reif oder vertrocknet: Gäste verschwinden
                if (p.weedSince > 0 || p.snailSince > 0 || p.weedAt > 0 || p.snailAt > 0) {
                    p = p.copy(weedSince = 0, weedAt = 0, snailSince = 0, snailAt = 0, snailBites = 0)
                }
                return@mapIndexed p
            }
            if (p.rolledDay != today) {
                val rng = Random(s.bornAt xor (today * 1_000_003L) xor (i * 7_777L) xor p.plantedAt)
                var np = p.copy(rolledDay = today)
                if (np.weedSince == 0L && rng.nextDouble() < WEED_CHANCE) {
                    np = np.copy(weedAt = dayStart + (6 + rng.nextInt(16)) * HOUR + rng.nextInt(60) * 60_000L)
                }
                val snailChance = SNAIL_CHANCE * (if (fewerSnails) 0.5 else 1.0)
                if (np.snailSince == 0L && np.waterings >= 1 && rng.nextDouble() < snailChance) {
                    np = np.copy(snailAt = dayStart + (SNAIL_FROM_HOUR + rng.nextInt(SNAIL_TO_HOUR - SNAIL_FROM_HOUR)) * HOUR + rng.nextInt(60) * 60_000L)
                }
                p = np
            }
            if (p.weedAt in 1..now) p = p.copy(weedSince = p.weedAt, weedAt = 0)
            if (p.snailAt in 1..now) p = p.copy(snailSince = p.snailAt, snailAt = 0, snailBites = 0)
            // Schnecke knabbert alle 6 aktiven Stunden eine Stufe zurück, höchstens bis vor das erste Gießen
            if (p.snailSince > 0) {
                val due = (activeSnailMillis(p.snailSince, now, zone) / (SNAIL_BITE_HOURS * HOUR)).toInt()
                if (due > p.snailBites) {
                    p = p.copy(waterings = max(0, p.waterings - (due - p.snailBites)), snailBites = due)
                }
            }
            // Regentag: ab 8 Uhr gießt der Regen alle Beete
            val rainAt = dayStart + 8 * HOUR
            if (rainy(s, today) && now >= rainAt && p.rainedDay != today) {
                p = if (rainAt < p.plantedAt) p.copy(rainedDay = today) // erst nach dem Regen gesät
                else grow(s, p, rainAt, rng = Random(rainAt + i)).first.copy(rainedDay = today)
            }
            if (p != p0) changed = true
            p
        }
        if (changed || s !== s0) s = s.copy(plots = plots)
        return s
    }

    /** Zeit, in der Schnecken aktiv sind (7 bis 23 Uhr), zwischen [from] und [to]. */
    fun activeSnailMillis(from: Long, to: Long, zone: ZoneId): Long {
        if (to <= from) return 0
        var total = 0L
        var day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        var guard = 0
        while (!day.isAfter(last) && guard++ < 60) {
            val a = day.atTime(SNAIL_FROM_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
            val b = day.atTime(SNAIL_TO_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
            val lo = max(a, from); val hi = min(b, to)
            if (hi > lo) total += hi - lo
            day = day.plusDays(1)
        }
        return total
    }

    /** Alte Beete (vor Garten 2.0) einmalig umrechnen: als Blaubeere weiterwachsen lassen. */
    private fun migrate(s: PetState, zone: ZoneId): PetState {
        if (s.plots.none { it.v == 0 && !it.empty }) return s
        return s.copy(plots = s.plots.map { p ->
            if (p.v != 0 || p.empty) p else {
                val lastDay = if (p.lastWateredDay >= 0) p.lastWateredDay else PetEngine.epochDay(p.plantedAt, zone)
                val at = LocalDate.ofEpochDay(lastDay).atStartOfDay(zone).toInstant().toEpochMilli() + 12 * HOUR
                val w = min(p.waterDays, Berry.BLUE.needWaterings)
                p.copy(v = 1, berry = Berry.BLUE.name, waterings = w, lastWaterAt = if (p.waterDays > 0) at else 0L, lastCountedAt = if (p.waterDays > 0) at else 0L)
            }
        })
    }

    // ---------------- Aktionen ----------------

    /** Ein Gießgang (Tier oder Regen). Gibt das neue Beet zurück und ob er Wachstum gebracht hat. */
    private fun grow(s: PetState, p: Plot, now: Long, rng: Random): Pair<Plot, Boolean> {
        val k = p.kind
        val gapMs = (k.gapHours / speed(s, p) * HOUR).toLong()
        val counts = p.waterings == 0 || p.lastCountedAt == 0L || now - p.lastCountedAt >= gapMs
        if (!counts) return p.copy(lastWaterAt = now) to false
        var add = 1
        if (s.canLevel >= 3 && rng.nextInt(100) < 10) add = 2
        var w = p.waterings + add
        // Solange eine Schnecke da ist, wird die Pflanze nicht reif
        if (p.snailSince > 0) w = min(w, k.needWaterings - 1)
        w = min(w, k.needWaterings)
        val clean = if (p.weedSince == 0L && p.snailSince == 0L) p.cleanWaterings + 1 else p.cleanWaterings
        return p.copy(waterings = w, lastWaterAt = now, lastCountedAt = now, cleanWaterings = clean) to (w > p.waterings)
    }

    fun plant(s: PetState, i: Int, berry: Berry, now: Long): ActionResult {
        if (i !in 0 until beds(s).size) return ActionResult(s, "Dieses Beet ist noch nicht frei.", false)
        val p = beds(s)[i]
        val st = stage(p, now, s)
        if (st != PlotStage.EMPTY && st != PlotStage.WITHERED) return ActionResult(s, "Hier wächst schon etwas.", false)
        var ns = s
        when {
            berry.seedPrice < 0 -> {
                if (s.goldSeeds <= 0) return ActionResult(s, "Goldbeeren-Samen findest du nur in Dungeon-Truhen.", false)
                ns = ns.copy(goldSeeds = s.goldSeeds - 1)
            }
            berry.seedPrice > 0 -> {
                if (s.stardust < berry.seedPrice) return ActionResult(s, "${berry.label}-Samen kosten ${berry.seedPrice} ✨.", false)
                ns = ns.copy(stardust = s.stardust - berry.seedPrice)
            }
        }
        ns = withPlot(ns, i, Plot(plantedAt = now, v = 1, berry = berry.name))
        // Befund 09: Werte kommen aus der Sorte; der Abstand ist ein Grundwert (Dünger und Gegenstände verkürzen ihn)
        val hint = "Gieß sie ${berry.needWaterings}-mal, mit mindestens ${hoursText(berry.gapHours)} Grundabstand" +
            (if (berry == Berry.BLUE) ", dann ist sie reif." else ".")
        return ActionResult(ns, "${berry.emoji} ${berry.label} gesät! $hint", reaction = Reaction.HAPPY)
    }

    /** Gemeinsam mit dem Tierchen gießen. Mit der Silberkanne bekommt das Nachbarbeet auch Wasser. */
    fun water(s: PetState, i: Int, now: Long): ActionResult {
        if (s.stage == Stage.EGG) return ActionResult(s, "Ein Ei kann noch nicht gießen.", false)
        if (s.sleeping) return ActionResult(s, "${s.name} schläft. Gießen macht ihr zusammen, wenn es wach ist.", false, Reaction.SLEEPY)
        val beds = beds(s)
        val p = beds.getOrNull(i) ?: return ActionResult(s, "Dieses Beet ist noch nicht frei.", false)
        when (stage(p, now, s)) {
            PlotStage.EMPTY -> return ActionResult(s, "Erst etwas säen.", false)
            PlotStage.WITHERED -> return ActionResult(s, "Zu spät, die Pflanze ist vertrocknet. Säe neu!", false)
            PlotStage.RIPE -> return ActionResult(s, "Die Beeren sind reif. Ernten!", false)
            else -> {}
        }
        val rng = Random(now xor i.toLong())
        val (np, grew) = grow(s, p, now, rng)
        var ns = withPlot(s, i, np)
        var extra = ""
        if (s.canLevel >= 2) {
            val n = if (i % 3 < 2 && i + 1 < beds.size) i + 1 else i - 1
            val q = beds.getOrNull(n)
            if (q != null && growing(stage(q, now, s))) {
                ns = withPlot(ns, n, grow(s, q, now, Random(now + n)).first)
                extra = " Die Silberkanne gießt das Nachbarbeet mit."
            }
        }
        ns = PetEngine.progress(ns, Quest.WATER)
        ns = ns.copy(happiness = ns.happiness + 4f, energy = ns.energy - 2f)
        val k = np.kind
        val msg = when {
            np.waterings >= k.needWaterings -> "${s.name} hilft fleißig mit der Gießkanne. Die ${k.label}n sind reif!"
            grew && np.snailSince > 0 -> "Gegossen, aber die Schnecke hält die Pflanze klein. Tipp sie weg!"
            grew -> "${s.name} gießt mit dir zusammen. Es wächst! (${np.waterings}/${k.needWaterings})"
            else -> {
                val h = hoursUntilGrowth(s, p, now)
                "Das Beet ist noch feucht. Wachsen tut es erst wieder in ${formatHours(h)}, vertrocknen kann es jetzt aber nicht."
            }
        } + extra
        return PetEngine.withExp(ns, if (grew) 2 else 0, msg, Reaction.HAPPY)
    }

    fun formatHours(h: Float): String = when {
        h >= 1.5f -> "etwa ${kotlin.math.round(h).toInt()} Stunden"
        h >= 0.9f -> "etwa 1 Stunde"
        else -> "etwa ${max(5, (h * 60).toInt() / 5 * 5)} Minuten"
    }

    fun weed(s: PetState, i: Int): ActionResult {
        val p = beds(s).getOrNull(i) ?: return ActionResult(s, "", false)
        if (p.weedSince == 0L) return ActionResult(s, "Hier ist kein Unkraut.", false)
        val ns = PetEngine.progress(withPlot(s, i, p.copy(weedSince = 0)), Quest.WEED)
        return ActionResult(ns.copy(happiness = ns.happiness + 2f, trust = ns.trust + 0.5f), "Unkraut rausgezogen! ${s.name} zieht kräftig mit.", reaction = Reaction.HAPPY)
    }

    fun removeSnail(s: PetState, i: Int): ActionResult {
        val p = beds(s).getOrNull(i) ?: return ActionResult(s, "", false)
        if (p.snailSince == 0L) return ActionResult(s, "Hier ist keine Schnecke.", false)
        val ns = withPlot(s, i, p.copy(snailSince = 0, snailBites = 0))
        return ActionResult(ns.copy(happiness = ns.happiness + 2f, trust = ns.trust + 0.5f), "${s.name} trägt die Schnecke vorsichtig in die Wiese. 🐌", reaction = Reaction.HAPPY)
    }

    fun fertilize(s: PetState, i: Int, now: Long): ActionResult {
        val p = beds(s).getOrNull(i) ?: return ActionResult(s, "", false)
        if (!growing(stage(p, now, s))) return ActionResult(s, "Dünger hilft nur Pflanzen, die gerade wachsen.", false)
        if (p.fertilized) return ActionResult(s, "Dieses Beet ist schon gedüngt.", false)
        if (s.fertilizer <= 0) return ActionResult(s, "Kein Dünger da. Je $COMPOST_PER_FERTILIZER weggeschaufelte Häufchen ergeben 1 Dünger.", false)
        return ActionResult(withPlot(s, i, p.copy(fertilized = true)).copy(fertilizer = s.fertilizer - 1), "Gedüngt! Diese Pflanze wächst jetzt $fertilizerPercent % schneller.", reaction = Reaction.HAPPY)
    }

    fun harvestCount(p: Plot): Int = p.kind.harvest + min(p.kind.maxBonus, max(0, p.cleanWaterings - 1))

    fun harvest(s: PetState, i: Int, now: Long): ActionResult {
        val p = beds(s).getOrNull(i) ?: return ActionResult(s, "", false)
        if (stage(p, now, s) != PlotStage.RIPE) return ActionResult(s, "Noch nicht reif.", false)
        val k = p.kind
        val n = harvestCount(p)
        var ns = withPlot(s, i, Plot(v = 1))
        ns = addBerries(ns, k, n).copy(
            happiness = ns.happiness + 5f,
            harvestsTotal = ns.harvestsTotal + 1,
            berriesHarvested = if (k.name in ns.berriesHarvested) ns.berriesHarvested else ns.berriesHarvested + k.name,
        )
        ns = PetEngine.progress(ns, Quest.HARVEST)
        val bonus = n - k.harvest
        val (rs, rewardMsg) = checkGoals(ns)
        val msg = "Ernte! $n× ${k.emoji} ${k.label}" + (if (bonus > 0) " (+$bonus Pflege-Bonus)" else "") + "." + rewardMsg
        return PetEngine.withExp(rs, 5, msg, Reaction.JUMP)
    }

    fun count(s: PetState, b: Berry) = when (b) {
        Berry.BLUE -> s.berryStock; Berry.STAR -> s.starBerries; Berry.PSI -> s.psiBerries; Berry.GOLD -> s.goldBerries
    }

    fun addBerries(s: PetState, b: Berry, n: Int): PetState = when (b) {
        Berry.BLUE -> s.copy(berryStock = max(0, s.berryStock + n))
        Berry.STAR -> s.copy(starBerries = max(0, s.starBerries + n))
        Berry.PSI -> s.copy(psiBerries = max(0, s.psiBerries + n))
        Berry.GOLD -> s.copy(goldBerries = max(0, s.goldBerries + n))
    }

    fun sell(s: PetState, b: Berry, n: Int): ActionResult {
        val k = min(n, count(s, b))
        if (k <= 0) return ActionResult(s, "Keine ${b.label}n im Vorrat.", false)
        return ActionResult(addBerries(s, b, -k).copy(stardust = s.stardust + k * b.sellPrice), "$k× ${b.emoji} verkauft: +${k * b.sellPrice} ✨")
    }

    /** Eine Beere direkt naschen (Blaubeeren füttert man wie bisher über das Futter). */
    fun eat(s: PetState, b: Berry): ActionResult {
        if (s.stage == Stage.EGG) return ActionResult(s, "Ein Ei nascht noch nicht.", false)
        if (count(s, b) <= 0) return ActionResult(s, "Keine ${b.label}n im Vorrat.", false)
        val ns = addBerries(s, b, -1)
        return when (b) {
            Berry.BLUE -> ActionResult(ns.copy(satiety = min(100f, ns.satiety + 15f)), "Mmh, eine Blaubeere! +15 Sättigung.", reaction = Reaction.HAPPY)
            Berry.STAR -> ActionResult(ns.copy(satiety = min(100f, ns.satiety + 10f), happiness = min(100f, ns.happiness + 5f)), "Die Sternbeere funkelt beim Kauen. +10 Sättigung.", reaction = Reaction.HAPPY)
            Berry.PSI -> PetEngine.withExp(ns, 15, "Die Psi-Beere kribbelt! +15 EP.", Reaction.JUMP)
            Berry.GOLD -> PetEngine.withExp(ns.copy(happiness = 100f), 30, "Eine Goldbeere! +30 EP und beste Laune.", Reaction.LOVE)
        }
    }

    /** 2 Psi-Beeren ergeben einen Psi-Trank für den Dungeon. */
    fun brewPsiPotion(s: PetState): ActionResult {
        if (s.psiBerries < 2) return ActionResult(s, "Dafür brauchst du 2 Psi-Beeren.", false)
        return ActionResult(s.copy(psiBerries = s.psiBerries - 2, psiPotions = s.psiPotions + 1), "Aus 2 Psi-Beeren wird ein Psi-Trank. 💜")
    }

    /** Smoothie mixen. Passt kein Rezept, wird es ein Matsch-Smoothie. */
    fun mix(s: PetState, blue: Int, star: Int, psi: Int, gold: Int): ActionResult {
        if (s.stage == Stage.EGG) return ActionResult(s, "Ein Ei trinkt noch nichts.", false)
        val total = blue + star + psi + gold
        if (total < 2) return ActionResult(s, "Leg mindestens 2 Beeren in den Mixer.", false)
        if (total > 3) return ActionResult(s, "Mehr als 3 Beeren passen nicht in den Mixer.", false)
        if (blue > s.berryStock || star > s.starBerries || psi > s.psiBerries || gold > s.goldBerries) return ActionResult(s, "So viele Beeren hast du nicht.", false)
        var ns = s.copy(berryStock = s.berryStock - blue, starBerries = s.starBerries - star, psiBerries = s.psiBerries - psi, goldBerries = s.goldBerries - gold)
        val sm = Smoothie.match(blue, star, psi, gold)
        if (sm == null) {
            ns = ns.copy(satiety = min(100f, ns.satiety + 5f))
            return ActionResult(PetEngine.progress(ns, Quest.SMOOTHIE), "Blubb … ein Matsch-Smoothie. Schmeckt komisch, +5 Sättigung. Probier eine andere Mischung!", reaction = Reaction.ANNOYED)
        }
        val isNew = sm.name !in ns.smoothiesFound
        if (isNew) ns = ns.copy(smoothiesFound = ns.smoothiesFound + sm.name)
        var back = ""
        if (Item.has<Effect.MixerBonus>(s)) {
            val first = listOf(Berry.BLUE to blue, Berry.STAR to star, Berry.PSI to psi, Berry.GOLD to gold).first { it.second > 0 }.first
            ns = addBerries(ns, first, 1); back = " Der Glas-Mixer gibt dir eine ${first.label} zurück."
        }
        ns = PetEngine.progress(ns, Quest.SMOOTHIE)
        val prefix = (if (isNew) "Neues Rezept: " else "") + "${sm.emoji} ${sm.label}! "
        val r = when (sm) {
            Smoothie.SHAKE -> ActionResult(ns.copy(satiety = min(100f, ns.satiety + 30f), happiness = min(100f, ns.happiness + 10f)), prefix + "+30 Sättigung, +10 Laune.", reaction = Reaction.HAPPY)
            Smoothie.STAR -> ActionResult(ns.copy(energy = min(100f, ns.energy + 20f), sleeping = false), prefix + "+20 Energie.", reaction = Reaction.JUMP)
            Smoothie.PSI -> PetEngine.withExp(ns, 25, prefix + "+25 EP.", Reaction.JUMP)
            Smoothie.HEAL -> ActionResult(ns.copy(health = min(100f, ns.health + 20f)), prefix + "+20 Gesundheit.", reaction = Reaction.LOVE)
            Smoothie.GOLD -> PetEngine.withExp(ns.copy(happiness = 100f, energy = 100f, sleeping = false), 60, prefix + "+60 EP, volle Laune und Energie.", Reaction.LOVE)
        }
        val (gs, rewardMsg) = checkGoals(r.state)
        return r.copy(state = gs, message = r.message + back + rewardMsg)
    }

    // ---------------- Ausbau ----------------

    fun nextBed(s: PetState): Int? = if (s.bedsUnlocked >= MAX_BEDS) null else s.bedsUnlocked - 3

    fun unlockBed(s: PetState): ActionResult {
        val k = nextBed(s) ?: return ActionResult(s, "Alle Beete sind schon frei.", false)
        if (s.level < BED_LEVELS[k]) return ActionResult(s, "Beet ${k + 4} gibt es ab Level ${BED_LEVELS[k]}.", false)
        if (s.stardust < BED_PRICES[k]) return ActionResult(s, "Dafür fehlen noch ${BED_PRICES[k] - s.stardust} ✨.", false)
        val ns = s.copy(stardust = s.stardust - BED_PRICES[k], bedsUnlocked = s.bedsUnlocked + 1)
        return ActionResult(ns.copy(plots = beds(ns)), "Beet ${k + 4} ist frei! 🌱", reaction = Reaction.JUMP)
    }

    fun buyCan(s: PetState): ActionResult {
        val k = s.canLevel
        if (k >= CAN_NAMES.size) return ActionResult(s, "Du hast schon die beste Gießkanne.", false)
        if (s.stardust < CAN_PRICES[k]) return ActionResult(s, "Dafür fehlen noch ${CAN_PRICES[k] - s.stardust} ✨.", false)
        return ActionResult(s.copy(stardust = s.stardust - CAN_PRICES[k], canLevel = k + 1), "${CAN_NAMES[k]} gekauft: ${CAN_TEXTS[k]}.", reaction = Reaction.HAPPY)
    }

    /** Weggeschaufeltes Häufchen kommt in den Kompost; 3 ergeben 1 Dünger. */
    fun addCompost(s: PetState): PetState {
        val c = s.compost + 1
        return if (c >= COMPOST_PER_FERTILIZER) s.copy(compost = c - COMPOST_PER_FERTILIZER, fertilizer = s.fertilizer + 1) else s.copy(compost = c)
    }

    // ---------------- Sammelbuch ----------------

    fun goalDone(s: PetState, g: GardenGoal): Boolean = when (g) {
        GardenGoal.ALL_KINDS -> Berry.entries.all { it.name in s.berriesHarvested }
        GardenGoal.HARVEST_10 -> s.harvestsTotal >= 10
        GardenGoal.HARVEST_25 -> s.harvestsTotal >= 25
        GardenGoal.ALL_SMOOTHIES -> Smoothie.entries.all { it.name in s.smoothiesFound }
        GardenGoal.MASTER -> Berry.entries.all { it.name in s.berriesHarvested } && s.harvestsTotal >= 50
    }

    fun goalItem(g: GardenGoal): Item? = when (g) {
        GardenGoal.HARVEST_10 -> Item.SCARECROW
        GardenGoal.HARVEST_25 -> Item.GARDEN_GNOME
        GardenGoal.ALL_SMOOTHIES -> Item.GLASS_MIXER
        GardenGoal.MASTER -> Item.WATERING_HAT
        GardenGoal.ALL_KINDS -> null
    }

    fun isMasterGardener(s: PetState) = GardenGoal.MASTER.name in s.gardenRewards

    /** Neu erreichte Ziele vergeben (Teile landen direkt in der Sammlung). */
    fun checkGoals(s: PetState): Pair<PetState, String> {
        var ns = s
        var msg = ""
        GardenGoal.entries.forEach { g ->
            if (g.name !in ns.gardenRewards && goalDone(ns, g)) {
                val item = goalItem(g)
                ns = ns.copy(gardenRewards = ns.gardenRewards + g.name, items = if (item != null && item.name !in ns.items) ns.items + item.name else ns.items)
                msg += " 🏅 ${g.label}: ${g.reward}!"
            }
        }
        return ns to msg
    }
}
