package app.psykit.core.engine

import app.psykit.core.model.Effect
import app.psykit.core.model.Difficulty
import app.psykit.core.model.Food
import app.psykit.core.model.Item
import app.psykit.core.model.Plot
import app.psykit.core.model.PlotStage
import app.psykit.core.model.Quest
import app.psykit.core.model.GameKind
import app.psykit.core.model.PetState
import app.psykit.core.model.Reaction
import app.psykit.core.model.Stage
import app.psykit.core.model.Zone
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

/** Ergebnis einer Aktion: neuer Zustand, Text und Reaktion für die Animation. */
data class ActionResult(
    val state: PetState,
    val message: String,
    val success: Boolean = true,
    val reaction: Reaction = Reaction.NONE,
)

/**
 * Die komplette Spiellogik. Zustand rein, Zustand raus, keine Android-Abhängigkeiten.
 * Zeit läuft in echter Uhrzeit weiter, auch wenn die App geschlossen ist:
 * [advance] simuliert die vergangene Zeit in 5-Minuten-Schritten nach.
 */
object PetEngine {
    /** Runde 12 (Befund 28): Das Ei schlüpft nach so vielen Millisekunden (vorher 2 Minuten). */
    const val EGG_HATCH_MS = 30_000L
    private const val STEP_MS = 5 * 60_000L
    private const val MAX_SIM_MS = 7 * 24 * 60 * 60_000L
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    // Raten pro Stunde
    private const val SATIETY_AWAKE = 6f
    private const val SATIETY_ASLEEP = 2f
    private const val HAPPY_AWAKE = 4f
    private const val HAPPY_ASLEEP = 1f
    private const val HYGIENE_BASE = 2f
    private const val HYGIENE_PER_POOP = 3f
    private const val ENERGY_AWAKE_DAY = 4f
    private const val ENERGY_AWAKE_NIGHT = 9f
    private const val ENERGY_SLEEP = 40f
    private const val POOP_EVERY_MIN = 180f
    private const val MAX_POOPS = 3

    /** Schritte pro „Spaziergang“, nach dem das Tierchen etwas schmutzig wird. */
    const val STEPS_PER_DIRT = 1500L
    /** Befund 03: so viele Schritte ergeben 1 EP (Anzeige und Rechnung nutzen denselben Wert). */
    const val STEPS_PER_EXP = 100L
    const val GARDEN_DAYS = 3
    const val HARVEST_BERRIES = 3
    const val MAX_DIRT = 6
    const val MAX_KEYS = 3
    const val KEY_STEPS = 3000L

    // Sternenstaub
    const val GAME_DUST_MIN = 5
    const val GAME_DUST_MAX = 20
    const val GAME_DUST_PER_DAY = 100
    const val QUEST_DUST = 30
    const val BERRY_PRICE = 3

    // Streicheln: Geschwindigkeit in dp pro Sekunde
    const val STROKE_SLOW = 700f
    const val STROKE_FAST = 1600f

    fun epochDay(now: Long, zone: ZoneId): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

    fun advance(s: PetState, now: Long, zone: ZoneId): PetState {
        if (now <= s.lastUpdate) return s
        var st = s
        var t = maxOf(s.lastUpdate, now - MAX_SIM_MS)
        while (t < now) {
            val dt = min(STEP_MS, now - t)
            t += dt
            st = tick(st, dt / 60_000f, t, zone)
        }
        st = st.copy(lastUpdate = now)
        st = GardenEngine.refresh(st, now, zone)
        // Mindestalter erreicht? Dann jetzt entwickeln
        val grown = Stage.next(st.stage, st.level, (now - st.bornAt) / 86_400_000L)
        if (grown != st.stage) st = st.copy(stage = grown)
        val today = epochDay(now, zone)
        if (st.questDay != today) {
            // Jeden Tag ein Gratis-Schlüssel für den Dungeon (höchstens 3 auf Vorrat)
            val key = if (st.questDay >= 0 && st.keys < MAX_KEYS) 1 else 0
            st = st.copy(questDay = today, questProgress = emptyMap(), questsClaimed = emptyList(), keys = st.keys + key)
        }
        return st
    }

    private fun tick(s0: PetState, minutes: Float, time: Long, zone: ZoneId): PetState {
        if (s0.stage == Stage.EGG) {
            // hatchSeen = false: Die Oberfläche feiert das Schlüpfen einmal
            return if (time - s0.bornAt >= EGG_HATCH_MS) s0.copy(stage = Stage.BABY, hatchSeen = false) else s0
        }
        val h = minutes / 60f
        // Glücksbringer außerhalb des Kampfs
        val moodKeep = 1f - Item.sum<Effect.MoodDecay>(s0) { it.pct } / 100f
        val sleepBoost = 1f + Item.sum<Effect.SleepEnergy>(s0) { it.pct } / 100f
        val phase = DayPhase.at(time, zone)
        val sluggish = s0.sluggishUntil > time
        var s = s0

        if (s.sleeping) {
            s = s.copy(
                satiety = s.satiety - SATIETY_ASLEEP * h,
                happiness = s.happiness - HAPPY_ASLEEP * h * moodKeep,
                hygiene = s.hygiene - HYGIENE_PER_POOP * s.poops * h,
                energy = s.energy + ENERGY_SLEEP * h * sleepBoost,
            )
            if (!phase.isNight && s.energy >= 70f) s = s.copy(sleeping = false)
        } else {
            val sadBoost = if (s.satiety < 20f || s.hygiene < 20f) 2f else 0f
            val energyRate = (if (phase.isNight) ENERGY_AWAKE_NIGHT else ENERGY_AWAKE_DAY) * (if (sluggish) 2f else 1f)
            s = s.copy(
                satiety = s.satiety - SATIETY_AWAKE * h,
                happiness = s.happiness - (HAPPY_AWAKE + sadBoost) * h * moodKeep,
                hygiene = s.hygiene - (HYGIENE_BASE + HYGIENE_PER_POOP * s.poops) * h,
                energy = s.energy - energyRate * h,
                minutesSincePoop = s.minutesSincePoop + minutes,
            )
            if (s.minutesSincePoop >= POOP_EVERY_MIN) {
                s = s.copy(minutesSincePoop = 0f, poops = min(MAX_POOPS, s.poops + 1))
            }
            if (s.energy <= 0f) s = s.copy(sleeping = true)
        }

        // Gesundheit und Vertrauen
        val neglected = s.satiety <= 10f || s.happiness <= 10f || s.hygiene <= 10f
        val wellKept = s.satiety >= 40f && s.happiness >= 40f && s.hygiene >= 40f
        s = when {
            neglected -> s.copy(health = s.health - 4f * h, trust = s.trust - 1f * h)
            wellKept -> s.copy(health = s.health + 2f * h, trust = s.trust + 0.3f * h)
            else -> s
        }
        s = clamp(s)

        // gute Pflege bringt langsam EP: 2 EP pro Stunde in gutem Zustand
        if (s.satiety >= 50f && s.happiness >= 50f && s.hygiene >= 50f && s.health >= 50f) {
            var care = s.goodCareMinutes + minutes
            var bonus = 0
            while (care >= 60f) { care -= 60f; bonus += 2 }
            s = s.copy(goodCareMinutes = care)
            if (bonus > 0) s = LevelSystem.addExp(s, bonus).first
        }
        return s
    }

    fun clampState(s: PetState) = clamp(s)

    private fun clamp(s: PetState) = s.copy(
        satiety = s.satiety.coerceIn(0f, 100f),
        happiness = s.happiness.coerceIn(0f, 100f),
        hygiene = s.hygiene.coerceIn(0f, 100f),
        energy = s.energy.coerceIn(0f, 100f),
        health = s.health.coerceIn(0f, 100f),
        trust = s.trust.coerceIn(0f, 100f),
        dirt = s.dirt.coerceIn(0, MAX_DIRT),
    )

    internal fun withExp(s: PetState, exp: Int, message: String, reaction: Reaction = Reaction.HAPPY): ActionResult {
        val (ns, events) = LevelSystem.addExp(clamp(s), exp)
        return ActionResult(ns, (listOf(message) + events).joinToString(" "), reaction = reaction)
    }

    private fun blocked(s: PetState): ActionResult? = when {
        s.stage == Stage.EGG -> ActionResult(s, "Das Ei wackelt ein bisschen. Gleich schlüpft etwas!", false)
        s.sleeping -> ActionResult(s, "${s.name} schläft gerade. Psst!", false, Reaction.SLEEPY)
        else -> null
    }

    private fun kind(s: PetState, trust: Float = 1f) = s.copy(trust = s.trust + trust, kindCount = s.kindCount + 1)

    // ---------------- Füttern ----------------

    fun feed(s: PetState, food: Food, now: Long): ActionResult {
        blocked(s)?.let { return it }
        if (food == Food.GARDEN && s.berryStock <= 0) return ActionResult(s, "Keine Gartenbeeren mehr. Ernte neue im Garten!", false)
        val likes = Food.likesOf(s.bornAt)
        val dislikes = Food.dislikesOf(s.bornAt)

        if (food in dislikes) {
            val ns = s.copy(
                happiness = s.happiness - 5f,
                knownDislikes = (s.knownDislikes + food).distinct(),
            )
            return ActionResult(clamp(ns), "Bäh! ${s.name} spuckt ${food.label} wieder aus. Das mag es gar nicht.", false, Reaction.SPIT)
        }
        if (!food.sweet && s.satiety >= 95f) {
            return ActionResult(s, "${s.name} ist pappsatt und dreht den Kopf weg.", false, Reaction.ANNOYED)
        }

        var ns = s.copy(
            berryStock = if (food == Food.GARDEN) s.berryStock - 1 else s.berryStock,
            satiety = s.satiety + food.satiety,
            happiness = s.happiness + food.joy,
            minutesSincePoop = s.minutesSincePoop + food.satiety,
        )
        var msg = "Mampf! ${s.name} isst ${food.label}."
        var exp = 2
        var reaction = Reaction.HAPPY

        if (food in likes) {
            val first = food !in s.knownLikes
            ns = kind(ns, 2f).copy(happiness = ns.happiness + 10f, knownLikes = (s.knownLikes + food).distinct())
            msg = if (first) "${food.label} ist ${s.name}s Lieblingsessen! Es strahlt über das ganze Gesicht." else "Lieblingsessen! ${s.name} ist selig."
            exp = 4
            reaction = Reaction.LOVE
        } else if (!food.sweet) {
            ns = ns.copy(health = ns.health + 2f)
        }

        if (food.sweet) {
            val fresh = now - s.sweetsWindowStart > 2 * HOUR
            val count = if (fresh) 1 else s.sweetsCount + 1
            ns = ns.copy(sweetsCount = count, sweetsWindowStart = if (fresh) now else s.sweetsWindowStart)
            if (count >= 3) {
                ns = ns.copy(sluggishUntil = now + 2 * HOUR, energy = ns.energy - 15f, health = ns.health - 3f)
                return ActionResult(clamp(ns), "Zu viel Süßes! ${s.name} wird ganz träge und mag sich kaum bewegen.", false, Reaction.SLEEPY)
            }
        }
        return withExp(ns, exp, msg, reaction)
    }

    // ---------------- Streicheln & Kraulen ----------------

    /** Wie viele angenehme Streicheleinheiten pro 10 Minuten (wächst mit Vertrauen). */
    fun strokeAllowance(s: PetState) = 3 + (s.trust / 15f).toInt()

    /** Ein Wisch über das Tierchen mit [speed] dp/s. */
    fun stroke(s: PetState, speed: Float, now: Long): ActionResult {
        blocked(s)?.let { return it }
        val fresh = now - s.petWindowStart > 10 * MINUTE
        val count = if (fresh) 1 else s.petCount + 1
        var ns = s.copy(petCount = count, petWindowStart = if (fresh) now else s.petWindowStart)
        return when {
            speed > STROKE_FAST -> {
                ns = ns.copy(happiness = ns.happiness - 4f, trust = ns.trust - 2f, annoyCount = ns.annoyCount + 1)
                ActionResult(clamp(ns), "Zu wild! ${s.name} zuckt erschrocken zurück.", false, Reaction.ANNOYED)
            }
            count > strokeAllowance(s) -> ActionResult(
                clamp(ns.copy(happiness = ns.happiness + 0.5f)),
                if (s.trust < 50f) "${s.name} hat erstmal genug. Mit mehr Vertrauen mag es länger kuscheln." else "${s.name} döst zufrieden vor sich hin.",
            )
            speed < STROKE_SLOW -> {
                ns = kind(ns, 0.7f).copy(happiness = ns.happiness + 6f)
                if (count <= 3) withExp(ns, 1, "${s.name} schnurrt und entspannt sich.", Reaction.LOVE)
                else ActionResult(clamp(ns), "${s.name} schmiegt sich an deine Hand.", reaction = Reaction.LOVE)
            }
            else -> ActionResult(clamp(ns.copy(happiness = ns.happiness + 2f)), "${s.name} hält still. Etwas langsamer gefällt es ihm noch besser.", reaction = Reaction.HAPPY)
        }
    }

    /** K1 (vorläufige Werte): Kraulen bringt nur mit Abstand und bis zu einer Tagesgrenze Freude. */
    /** Befund 32: Energie je Spielrunde und Mindestenergie zum Spielen. */
    const val GAME_ENERGY = 5f
    const val GAME_MIN_ENERGY = 10f
    const val SCRATCH_COOLDOWN_MS = 10_000L
    const val SCRATCH_REWARDS_PER_DAY = 20

    /** Nach so vielen Fehlversuchen verrät das Tierchen die Lieblingsstelle. */
    const val SCRATCH_REVEAL_AFTER = 3

    /** Heute schon probierte Kraulstellen. */
    fun scratchTriedToday(s: PetState, now: Long, tz: ZoneId): List<Zone> =
        if (s.scratchTriedDay == epochDay(now, tz)) s.scratchTried else emptyList()

    /** Kraulen an einer Stelle. Jeden Tag gibt es eine geheime Lieblingsstelle. */
    fun scratch(
        s: PetState, zone: Zone, now: Long, tz: ZoneId,
        /** Richtungstipp; bei eigenen Bildern aus den echten Zonenmitten der BodyMap (K3). */
        hint: (from: Zone, to: Zone) -> String = { a, b -> a.hintTowards(b) },
        /** Mögliche Stellen; ohne Schwanz bei Figuren ohne Schwanz. */
        zones: List<Zone> = Zone.entries,
    ): ActionResult {
        blocked(s)?.let { return it }
        val today = epochDay(now, tz)
        val favorite = Zone.favoriteOn(today, s.bornAt, zones)
        val rewardsToday = if (s.scratchRewardDay == today) s.scratchRewardsToday else 0
        val rested = now - s.lastScratchRewardAt >= SCRATCH_COOLDOWN_MS
        // Freude nur mit Abstand und bis zur Tagesgrenze; der Fund der Lieblingsstelle zählt immer.
        val mayReward = rested && rewardsToday < SCRATCH_REWARDS_PER_DAY
        fun rewarded(x: PetState) = x.copy(scratchRewardDay = today, scratchRewardsToday = rewardsToday + 1, lastScratchRewardAt = now)
        if (s.scratchFoundDay == today) {
            if (!mayReward) return ActionResult(s, scratchTiredMessage(s, rested), false, Reaction.NONE)
            return ActionResult(clamp(rewarded(s).copy(happiness = s.happiness + 2f)), "${s.name} genießt das Kraulen (${zone.label}).", reaction = Reaction.HAPPY)
        }
        val tried = (scratchTriedToday(s, now, tz) + zone).distinct()
        val st = s.copy(scratchTriedDay = today, scratchTried = tried)
        if (zone == favorite) {
            val ns = kind(st, 5f).copy(happiness = s.happiness + 15f, scratchFoundDay = today)
            return withExp(ns, 5, "Volltreffer! Heute liebt ${s.name} es, an dieser Stelle gekrault zu werden (${zone.label}). Es macht einen Freudensprung!", Reaction.JUMP)
        }
        val misses = tried.count { it != favorite }
        val hint = if (misses >= SCRATCH_REVEAL_AFTER) "Psst … heute ist es: ${favorite.label}!" else hint(zone, favorite)
        val text = "${zone.label}: ganz nett, aber nicht die Lieblingsstelle. $hint"
        if (!mayReward) return ActionResult(st, text, reaction = Reaction.NONE)
        return ActionResult(clamp(rewarded(st).copy(happiness = s.happiness + 1f)), text, reaction = Reaction.HAPPY)
    }

    private fun scratchTiredMessage(s: PetState, rested: Boolean) =
        if (!rested) "${s.name} braucht kurz eine Pause vom Kraulen." else "${s.name} ist für heute genug gekrault worden."

    // ---------------- Putzen ----------------

    /** Anzahl Schmutzflecken, die beim Putzen angezeigt werden. */
    fun dirtSpots(s: PetState): Int = min(8, s.dirt + ((100f - s.hygiene) / 15f).toInt())

    /** Nach Einschäumen und Abduschen: [cleanedFraction] = Anteil der Flecken, die sauber sind. */
    fun clean(s: PetState, cleanedFraction: Float): ActionResult {
        if (s.stage == Stage.EGG) return blocked(s)!!
        val f = cleanedFraction.coerceIn(0f, 1f)
        if (dirtSpots(s) == 0 && s.hygiene >= 90f) return ActionResult(s, "Schon blitzsauber!", false)
        val ns = s.copy(
            hygiene = s.hygiene + (100f - s.hygiene) * f,
            dirt = (s.dirt * (1f - f)).roundToInt(),
            happiness = s.happiness + 4f * f,
        )
        return when {
            f >= 0.99f -> withExp(kind(ns, 1f), 4, "Blitzblank! ${s.name} glänzt und duftet.", Reaction.LOVE)
            f >= 0.5f -> withExp(ns, 2, "Schon viel sauberer, aber ein paar Stellen glänzen noch nicht.")
            else -> ActionResult(clamp(ns), "Da sind noch einige Flecken übrig.", false)
        }
    }

    /** Häufchen mit der Schaufel wegräumen: landet im Kompost (3 = 1 Dünger). */
    fun removePoop(s: PetState): PetState = if (s.poops > 0) GardenEngine.addCompost(s.copy(poops = s.poops - 1)) else s

    // ---------------- Schlafen ----------------

    /** Ins Bett bringen (Decke drüber, Licht aus). [hummed] = du hast ein Schlaflied gesummt. */
    fun putToBed(s: PetState, now: Long, tz: ZoneId, hummed: Boolean): ActionResult {
        if (s.stage == Stage.EGG) return blocked(s)!!
        if (s.sleeping) return ActionResult(s, "${s.name} schläft schon.", false, Reaction.SLEEPY)
        val night = DayPhase.at(now, tz).isNight
        if (!night && s.energy > 80f && !hummed) return ActionResult(s, "${s.name} ist noch gar nicht müde und strampelt die Decke weg.", false, Reaction.ANNOYED)
        var ns = s.copy(sleeping = true)
        if (hummed) {
            ns = kind(ns, 1f).copy(energy = ns.energy + 15f, happiness = ns.happiness + 3f)
            return ActionResult(clamp(ns), "Bei deinem Summen schlummert ${s.name} sofort ein.", reaction = Reaction.SLEEPY)
        }
        return ActionResult(ns, "Gute Nacht, ${s.name}. Licht aus!", reaction = Reaction.SLEEPY)
    }

    fun wake(s: PetState, now: Long, tz: ZoneId): ActionResult {
        if (!s.sleeping) return ActionResult(s, "${s.name} ist schon wach.", false)
        val night = DayPhase.at(now, tz).isNight
        return if (night) ActionResult(clamp(s.copy(sleeping = false, happiness = s.happiness - 10f, trust = s.trust - 1f)), "${s.name} ist aufgewacht und ziemlich grummelig.", reaction = Reaction.ANNOYED)
        else ActionResult(s.copy(sleeping = false), "Guten Morgen, ${s.name}!", reaction = Reaction.HAPPY)
    }

    // ---------------- Spielen ----------------

    fun canPlay(s: PetState): ActionResult? {
        blocked(s)?.let { return it }
        if (s.sluggish) return ActionResult(s, "${s.name} ist von den Süßigkeiten noch zu träge zum Spielen.", false, Reaction.SLEEPY)
        if (s.energy < GAME_MIN_ENERGY) return ActionResult(s, "${s.name} ist zu müde zum Spielen.", false, Reaction.SLEEPY)
        if (s.satiety < 10f) return ActionResult(s, "${s.name} hat zu großen Hunger zum Spielen.", false)
        return null
    }

    /** Ergebnis eines Spiels. Gute Ergebnisse trainieren einen Kampfwert. */
    fun gameResult(s: PetState, game: GameKind, score: Int, max: Int, bonusExp: Int = 0, difficulty: Difficulty = Difficulty.NORMAL): ActionResult {
        canPlay(s)?.let { return it }
        val ratio = if (max <= 0) 0f else (score.toFloat() / max).coerceIn(0f, 1f)
        val train = when { ratio >= 0.99f -> 2; ratio >= 0.5f -> 1; else -> 0 }
        var ns = kind(s, 1f).copy(
            energy = s.energy - GAME_ENERGY,
            satiety = s.satiety - 5f,
            happiness = s.happiness + 5f + 10f * ratio,
            gamesWon = if (ratio >= 0.5f) s.gamesWon + 1 else s.gamesWon,
        )
        ns = when (game) {
            GameKind.HIDE_SEEK, GameKind.REACTION, GameKind.CLOUDS -> ns.copy(trainSpd = ns.trainSpd + train)
            GameKind.COOK -> ns.copy(trainAtk = ns.trainAtk + train, satiety = ns.satiety + 8f * score)
            GameKind.VISIT -> ns.copy(trust = ns.trust + 2f + 4f * ratio, happiness = ns.happiness + 5f)
            GameKind.MEMORY, GameKind.ECHO -> ns.copy(trainPsi = ns.trainPsi + train)
            GameKind.BUBBLES -> ns.copy(trainAtk = ns.trainAtk + train)
            GameKind.BERRIES, GameKind.SHADOW -> ns.copy(trainDef = ns.trainDef + train)
        }
        val trained = when {
            game == GameKind.VISIT -> " Die beiden verstehen sich immer besser."
            game == GameKind.COOK && score > 0 -> " Mmh, das selbst gekochte Essen schmeckt!" + if (train > 0) " ${game.trains} +$train." else ""
            train > 0 -> " ${game.trains} +$train."
            else -> ""
        }
        val msg = when {
            ratio >= 0.99f -> "Perfekt! $score von $max.$trained"
            ratio >= 0.5f -> "Gut gemacht: $score von $max.$trained"
            else -> "$score von $max. Nächstes Mal klappt's besser!"
        }
        // Sternenstaub, höchstens 100 pro Tag aus Spielen
        val day = s.questDay
        val already = if (s.stardustDay == day) s.stardustFromGames else 0
        val raw = ((GAME_DUST_MIN + (GAME_DUST_MAX - GAME_DUST_MIN) * ratio) * (1f + Item.sum<Effect.GameDust>(s) { it.pct } / 100f)).roundToInt()
        val dust = raw.coerceAtMost(GAME_DUST_PER_DAY - already).coerceAtLeast(0)
        ns = ns.copy(stardust = ns.stardust + dust, stardustDay = day, stardustFromGames = already + dust)
        val dustMsg = if (dust > 0) " +$dust ✨" else if (already >= GAME_DUST_PER_DAY) " (Sternenstaub aus Spielen für heute aufgebraucht)" else ""
        // Schwer gibt etwas mehr EP (aufgerundet); Leicht und Mittel geben den normalen Fortschritt
        val rawExp = 3 + 8 * ratio
        val baseExp = if (difficulty.expFactor > 1f) kotlin.math.ceil(rawExp * difficulty.expFactor).toInt() else rawExp.toInt()
        return withExp(ns, baseExp + bonusExp, msg + dustMsg, if (ratio >= 0.5f) Reaction.JUMP else Reaction.HAPPY)
    }

    /** Gartenbeeren für Sternenstaub verkaufen. */
    fun sellBerries(s: PetState, n: Int): ActionResult {
        val k = n.coerceAtMost(s.berryStock)
        if (k <= 0) return ActionResult(s, "Keine Gartenbeeren zum Verkaufen.", false)
        return ActionResult(s.copy(berryStock = s.berryStock - k, stardust = s.stardust + k * BERRY_PRICE), "$k Gartenbeeren verkauft: +${k * BERRY_PRICE} ✨")
    }

    /** Der Besuch tut auch dem anderen Tierchen gut. */
    fun visited(s: PetState, ratio: Float): PetState = clamp(
        s.copy(trust = s.trust + 2f + 4f * ratio, happiness = s.happiness + 10f + 10f * ratio, kindCount = s.kindCount + 1),
    )

    // ---------------- Garten ----------------

    fun plotStage(p: Plot, now: Long, tz: ZoneId): PlotStage = GardenEngine.stage(p, now)

    fun plant(s: PetState, i: Int, now: Long, tz: ZoneId, berry: app.psykit.core.model.Berry = app.psykit.core.model.Berry.BLUE): ActionResult =
        GardenEngine.plant(GardenEngine.refresh(s, now, tz), i, berry, now)

    fun water(s: PetState, i: Int, now: Long, tz: ZoneId): ActionResult = GardenEngine.water(GardenEngine.refresh(s, now, tz), i, now)

    fun harvest(s: PetState, i: Int, now: Long, tz: ZoneId): ActionResult = GardenEngine.harvest(GardenEngine.refresh(s, now, tz), i, now)

    // ---------------- Tagesaufgaben ----------------

    fun quests(s: PetState): List<Quest> = Quest.forDay(s.questDay, s.bornAt, dungeon = s.level >= 5)

    fun progress(s: PetState, q: Quest, n: Int = 1): PetState =
        if (q !in quests(s)) s else s.copy(questProgress = s.questProgress + (q.name to ((s.questProgress[q.name] ?: 0) + n)))

    fun questDone(s: PetState, q: Quest) = (s.questProgress[q.name] ?: 0) >= q.target

    /** Erfolgreiche Aktion zählt für eine Tagesaufgabe. */
    fun track(r: ActionResult, q: Quest): ActionResult = if (r.success) r.copy(state = progress(r.state, q)) else r

    fun claimQuest(s: PetState, q: Quest): ActionResult {
        if (q !in quests(s) || !questDone(s, q)) return ActionResult(s, "Diese Aufgabe ist noch nicht geschafft.", false)
        if (q.name in s.questsClaimed) return ActionResult(s, "Belohnung schon abgeholt.", false)
        var ns = s.copy(questsClaimed = s.questsClaimed + q.name, happiness = s.happiness + 5f, stardust = s.stardust + QUEST_DUST)
        var exp = q.rewardExp
        var msg = "Aufgabe geschafft: ${q.label}! +${q.rewardExp} EP, +$QUEST_DUST ✨."
        if (ns.questsClaimed.size == quests(s).size) {
            exp += 10; ns = ns.copy(berryStock = ns.berryStock + 2)
            msg += " Alle Aufgaben erledigt: +10 EP und 2 Gartenbeeren als Bonus!"
        }
        return withExp(ns, exp, msg, Reaction.JUMP)
    }

    // ---------------- Schritte ----------------

    /** Neu gezählte Schritte: werden „offen“ zum Einlösen und machen nach Spaziergängen schmutzig. */
    fun addSteps(s: PetState, steps: Long): PetState {
        if (steps <= 0) return s
        var walk = s.walkSteps + steps
        var dirt = s.dirt
        var hygiene = s.hygiene
        while (walk >= STEPS_PER_DIRT) {
            walk -= STEPS_PER_DIRT
            if (s.stage != Stage.EGG) { dirt += 1; hygiene -= 6f }
        }
        return progress(clamp(s.copy(pendingSteps = s.pendingSteps + steps, walkSteps = walk, dirt = dirt, hygiene = hygiene)), Quest.STEPS, steps.toInt())
    }

    /** 100 Schritte = 1 EP, je 500 Schritte +5 Laune (max. +25). Rest bleibt offen. */
    fun redeemSteps(s: PetState): ActionResult {
        if (s.stage == Stage.EGG) return ActionResult(s, "Erst muss das Ei schlüpfen. Deine Schritte bleiben gespeichert.", false)
        val usable = (s.pendingSteps / STEPS_PER_EXP) * STEPS_PER_EXP
        if (usable == 0L) return ActionResult(s, "Lauf noch ein bisschen! Ab $STEPS_PER_EXP Schritten gibt es EP.", false)
        val exp = (usable / STEPS_PER_EXP).toInt()
        val mood = min(25f, (usable / 500) * 5f)
        // 10 Sternenstaub pro 1000 Schritte, 1 Schlüssel pro 3000 Schritte (höchstens 3)
        val dust = (usable / 100).toInt()
        var keySteps = s.keySteps + usable
        var keys = s.keys
        var newKeys = 0
        while (keySteps >= KEY_STEPS) { keySteps -= KEY_STEPS; if (keys < MAX_KEYS) { keys++; newKeys++ } }
        val ns = s.copy(
            pendingSteps = s.pendingSteps - usable,
            stepsRedeemedTotal = s.stepsRedeemedTotal + usable,
            happiness = s.happiness + mood,
            stardust = s.stardust + dust,
            keySteps = keySteps,
            keys = keys,
        )
        val dirty = if (s.dirt > 0) " Vom Spaziergang ist ${s.name} etwas schmutzig geworden." else ""
        val keyMsg = if (newKeys > 0) " +$newKeys 🗝️ Schlüssel." else ""
        return withExp(ns, exp, "$usable Schritte eingelöst: +$exp EP, +$dust ✨.$keyMsg$dirty")
    }

    // ---------------- Begrüßung ----------------

    /** Begrüßung je nach Tageszeit, Laune und Vertrauen. */
    fun greeting(s: PetState, now: Long, tz: ZoneId): String {
        if (s.stage == Stage.EGG) return "Ein Ei! Gleich schlüpft etwas …"
        val n = s.name
        if (s.sleeping) return "$n schläft tief und fest."
        val base = when (DayPhase.at(now, tz)) {
            DayPhase.MORNING -> "Guten Morgen! $n streckt sich und gähnt."
            DayPhase.DAY -> "$n schwebt neugierig auf dich zu."
            DayPhase.EVENING -> "$n wird langsam ruhiger, der Abend kommt."
            DayPhase.NIGHT -> "Es ist spät. $n reibt sich die Augen."
        }
        val relation = when {
            s.trust >= 80f -> " Es freut sich riesig, dich zu sehen!"
            s.trust < 20f || s.annoyCount > s.kindCount + 5 -> " Es beobachtet dich noch etwas misstrauisch."
            else -> ""
        }
        return base + relation
    }

    fun rename(s: PetState, name: String): PetState = s.copy(name = name.trim().take(16).ifEmpty { s.name })
}
