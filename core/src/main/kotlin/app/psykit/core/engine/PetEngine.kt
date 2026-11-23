package app.psykit.core.engine

import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import java.time.ZoneId
import kotlin.math.min

/** Ergebnis einer Aktion: neuer Zustand plus Text für die Anzeige. */
data class ActionResult(val state: PetState, val message: String, val success: Boolean = true)

/**
 * Die komplette Spiellogik. Zustand rein, Zustand raus, keine Android-Abhängigkeiten.
 * Zeit läuft in echter Uhrzeit weiter, auch wenn die App geschlossen ist:
 * [advance] simuliert die vergangene Zeit in 5-Minuten-Schritten nach.
 */
object PetEngine {
    const val EGG_HATCH_MINUTES = 2L
    private const val STEP_MS = 5 * 60_000L
    private const val MAX_SIM_MS = 7 * 24 * 60 * 60_000L
    private const val MINUTE = 60_000L

    // Raten pro Stunde
    private const val SATIETY_AWAKE = 6f
    private const val SATIETY_ASLEEP = 2f
    private const val HAPPY_AWAKE = 4f
    private const val HAPPY_ASLEEP = 1f
    private const val HYGIENE_BASE = 2f
    private const val HYGIENE_PER_POOP = 3f
    private const val ENERGY_AWAKE_DAY = 5f
    private const val ENERGY_AWAKE_NIGHT = 9f
    private const val ENERGY_SLEEP = 20f
    private const val POOP_EVERY_MIN = 180f
    private const val MAX_POOPS = 3

    fun advance(s: PetState, now: Long, zone: ZoneId): PetState {
        if (now <= s.lastUpdate) return s
        var st = s
        var t = maxOf(s.lastUpdate, now - MAX_SIM_MS)
        while (t < now) {
            val dt = min(STEP_MS, now - t)
            t += dt
            st = tick(st, dt / 60_000f, t, zone)
        }
        return st.copy(lastUpdate = now)
    }

    private fun tick(s0: PetState, minutes: Float, time: Long, zone: ZoneId): PetState {
        if (s0.stage == Stage.EGG) {
            return if (time - s0.bornAt >= EGG_HATCH_MINUTES * MINUTE) s0.copy(stage = Stage.BABY) else s0
        }
        val h = minutes / 60f
        val phase = DayPhase.at(time, zone)
        var s = s0

        if (s.sleeping) {
            s = s.copy(
                satiety = s.satiety - SATIETY_ASLEEP * h,
                happiness = s.happiness - HAPPY_ASLEEP * h,
                hygiene = s.hygiene - HYGIENE_PER_POOP * s.poops * h,
                energy = s.energy + ENERGY_SLEEP * h,
            )
            // morgens von selbst aufwachen, wenn ausgeschlafen
            if (!phase.isNight && s.energy >= 70f) s = s.copy(sleeping = false)
        } else {
            val sadBoost = if (s.satiety < 20f || s.hygiene < 20f) 2f else 0f
            val energyRate = if (phase.isNight) ENERGY_AWAKE_NIGHT else ENERGY_AWAKE_DAY
            s = s.copy(
                satiety = s.satiety - SATIETY_AWAKE * h,
                happiness = s.happiness - (HAPPY_AWAKE + sadBoost) * h,
                hygiene = s.hygiene - (HYGIENE_BASE + HYGIENE_PER_POOP * s.poops) * h,
                energy = s.energy - energyRate * h,
                minutesSincePoop = s.minutesSincePoop + minutes,
            )
            if (s.minutesSincePoop >= POOP_EVERY_MIN) {
                s = s.copy(minutesSincePoop = 0f, poops = min(MAX_POOPS, s.poops + 1))
            }
            // vor Erschöpfung schläft es einfach ein
            if (s.energy <= 0f) s = s.copy(sleeping = true)
        }

        // Gesundheit
        val neglected = s.satiety <= 10f || s.happiness <= 10f || s.hygiene <= 10f
        val wellKept = s.satiety >= 40f && s.happiness >= 40f && s.hygiene >= 40f
        s = when {
            neglected -> s.copy(health = s.health - 4f * h)
            wellKept -> s.copy(health = s.health + 2f * h)
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

    private fun clamp(s: PetState) = s.copy(
        satiety = s.satiety.coerceIn(0f, 100f),
        happiness = s.happiness.coerceIn(0f, 100f),
        hygiene = s.hygiene.coerceIn(0f, 100f),
        energy = s.energy.coerceIn(0f, 100f),
        health = s.health.coerceIn(0f, 100f),
    )

    private fun withExp(s: PetState, exp: Int, message: String): ActionResult {
        val (ns, events) = LevelSystem.addExp(clamp(s), exp)
        return ActionResult(ns, (listOf(message) + events).joinToString(" "))
    }

    private fun blocked(s: PetState): ActionResult? = when {
        s.stage == Stage.EGG -> ActionResult(s, "Das Ei wackelt ein bisschen. Gleich schlüpft etwas!", false)
        s.sleeping -> ActionResult(s, "${s.name} schläft gerade. Psst!", false)
        else -> null
    }

    // ---------------- Aktionen ----------------

    fun feedMeal(s: PetState, now: Long): ActionResult {
        blocked(s)?.let { return it }
        if (s.satiety >= 95f) return ActionResult(
            s.copy(happiness = (s.happiness - 2f).coerceAtLeast(0f)),
            "${s.name} ist pappsatt und dreht den Kopf weg.", false,
        )
        return withExp(
            s.copy(satiety = s.satiety + 30f, health = s.health + 2f, minutesSincePoop = s.minutesSincePoop + 30f),
            2, "Mampf! ${s.name} hat gegessen.",
        )
    }

    fun feedSnack(s: PetState, now: Long): ActionResult {
        blocked(s)?.let { return it }
        val windowFresh = now - s.snackWindowStart > 60 * MINUTE
        val count = if (windowFresh) 1 else s.snackCount + 1
        val start = if (windowFresh) now else s.snackWindowStart
        var ns = s.copy(snackCount = count, snackWindowStart = start)
        if (count > 3) {
            ns = ns.copy(health = ns.health - 5f, happiness = ns.happiness + 3f)
            return ActionResult(clamp(ns), "Zu viele Süßigkeiten! ${s.name} hat Bauchweh.", false)
        }
        return withExp(ns.copy(happiness = ns.happiness + 12f, satiety = ns.satiety + 8f), 1, "Lecker, ein Snack!")
    }

    fun pet(s: PetState, now: Long): ActionResult {
        blocked(s)?.let { return it }
        val windowFresh = now - s.petWindowStart > 10 * MINUTE
        val count = if (windowFresh) 1 else s.petCount + 1
        val start = if (windowFresh) now else s.petWindowStart
        val ns = s.copy(petCount = count, petWindowStart = start)
        return when {
            count > 6 -> ActionResult(clamp(ns.copy(happiness = ns.happiness + 1f)), "${s.name} möchte jetzt lieber etwas anderes machen.")
            count > 3 -> ActionResult(clamp(ns.copy(happiness = ns.happiness + 4f)), "${s.name} schnurrt.")
            else -> withExp(ns.copy(happiness = ns.happiness + 8f), 1, "${s.name} schnurrt glücklich.")
        }
    }

    fun clean(s: PetState, now: Long): ActionResult {
        if (s.stage == Stage.EGG) return blocked(s)!!
        if (s.poops == 0 && s.hygiene >= 90f) return ActionResult(s, "Schon blitzsauber!", false)
        val hadPoops = s.poops > 0
        return withExp(
            s.copy(poops = 0, hygiene = 100f, happiness = s.happiness + 3f),
            if (hadPoops) 3 else 1,
            if (hadPoops) "Alles sauber gemacht!" else "${s.name} ist frisch gebadet.",
        )
    }

    fun toggleSleep(s: PetState, now: Long, zone: ZoneId): ActionResult {
        if (s.stage == Stage.EGG) return blocked(s)!!
        val night = DayPhase.at(now, zone).isNight
        return if (s.sleeping) {
            if (night) ActionResult(clamp(s.copy(sleeping = false, happiness = s.happiness - 10f)), "${s.name} ist aufgewacht und etwas grummelig.")
            else ActionResult(s.copy(sleeping = false), "Guten Morgen, ${s.name}!")
        } else {
            if (!night && s.energy > 80f) ActionResult(s, "${s.name} ist noch gar nicht müde.", false)
            else ActionResult(s.copy(sleeping = true), "Gute Nacht, ${s.name}. Licht aus!")
        }
    }

    fun canPlay(s: PetState): ActionResult? {
        blocked(s)?.let { return it }
        if (s.energy < 15f) return ActionResult(s, "${s.name} ist zu müde zum Spielen.", false)
        if (s.satiety < 10f) return ActionResult(s, "${s.name} hat zu großen Hunger zum Spielen.", false)
        return null
    }

    /** Nach dem Minispiel: [wins] von [rounds] Runden gewonnen. */
    fun playResult(s: PetState, wins: Int, rounds: Int): ActionResult {
        canPlay(s)?.let { return it }
        val ns = s.copy(
            energy = s.energy - 10f,
            satiety = s.satiety - 5f,
            happiness = s.happiness + 5f + 4f * wins,
            gamesWon = if (wins * 2 > rounds) s.gamesWon + 1 else s.gamesWon,
        )
        val msg = when {
            wins == rounds -> "Perfekt! ${s.name} ist begeistert."
            wins * 2 > rounds -> "Gewonnen! $wins von $rounds."
            else -> "Knapp verloren ($wins von $rounds), aber Spaß hat's gemacht."
        }
        return withExp(ns, 2 + 2 * wins, msg)
    }

    /** Fügt neu gezählte Schritte als „offen“ hinzu (werden dann eingelöst). */
    fun addSteps(s: PetState, steps: Long): PetState =
        if (steps <= 0) s else s.copy(pendingSteps = s.pendingSteps + steps)

    /** 100 Schritte = 1 EP, je 500 Schritte +5 Laune (max. +25). Rest bleibt offen. */
    fun redeemSteps(s: PetState): ActionResult {
        if (s.stage == Stage.EGG) return ActionResult(s, "Erst muss das Ei schlüpfen. Deine Schritte bleiben gespeichert.", false)
        val usable = (s.pendingSteps / 100) * 100
        if (usable == 0L) return ActionResult(s, "Lauf noch ein bisschen! Ab 100 Schritten gibt es EP.", false)
        val exp = (usable / 100).toInt()
        val mood = min(25f, (usable / 500) * 5f)
        val ns = s.copy(
            pendingSteps = s.pendingSteps - usable,
            stepsRedeemedTotal = s.stepsRedeemedTotal + usable,
            happiness = s.happiness + mood,
        )
        return withExp(ns, exp, "$usable Schritte eingelöst: +$exp EP.")
    }

    fun rename(s: PetState, name: String): PetState = s.copy(name = name.trim().take(16).ifEmpty { s.name })
}
