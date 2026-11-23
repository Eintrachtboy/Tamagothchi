package app.psykit.data

import android.content.Context
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.PetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

/**
 * Speichert den Spielstand als JSON in den SharedPreferences.
 * Singleton, damit App-Oberfläche und Hintergrund-Worker denselben Stand benutzen.
 */
class PetRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("psykit", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    val zone: ZoneId get() = ZoneId.systemDefault()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<PetState> = _state

    private fun load(): PetState {
        val raw = prefs.getString(KEY_STATE, null)
        val now = System.currentTimeMillis()
        val loaded = raw?.let { runCatching { json.decodeFromString(PetState.serializer(), it) }.getOrNull() }
        return loaded ?: PetState.newEgg(now).also { save(it) }
    }

    private fun save(s: PetState) {
        prefs.edit().putString(KEY_STATE, json.encodeToString(PetState.serializer(), s)).apply()
    }

    /** Liest, rechnet die vergangene Zeit nach, wendet [block] an und speichert. */
    fun <T> update(block: (PetState, Long) -> Pair<PetState, T>): T = synchronized(lock) {
        val now = System.currentTimeMillis()
        val current = PetEngine.advance(_state.value, now, zone)
        val (next, result) = block(current, now)
        save(next)
        _state.value = next
        result
    }

    fun refresh() = update { s, _ -> s to Unit }

    fun reset(name: String) = synchronized(lock) {
        val s = PetState.newEgg(System.currentTimeMillis(), name.ifBlank { "Lumi" })
        save(s); _state.value = s
        prefs.edit().remove(KEY_STEPS_TODAY).remove(KEY_STEPS_DAY).apply()
    }

    // ---------- Schrittzähler ----------
    // Der Android-Sensor liefert „Schritte seit dem letzten Neustart“. Wir merken uns den
    // letzten Wert und schreiben die Differenz dem Tierchen gut.

    /** Verarbeitet einen neuen Sensorwert und gibt die neu hinzugekommenen Schritte zurück. */
    fun onStepCounter(counter: Long): Long = synchronized(lock) {
        val last = prefs.getLong(KEY_LAST_COUNTER, -1L)
        val delta = when {
            last < 0 -> 0L // erster Kontakt: nur merken
            counter >= last -> counter - last
            else -> counter // Handy wurde neu gestartet
        }
        val today = LocalDate.now(zone).toString()
        val stepsToday = if (prefs.getString(KEY_STEPS_DAY, null) == today) prefs.getLong(KEY_STEPS_TODAY, 0) else 0L
        prefs.edit()
            .putLong(KEY_LAST_COUNTER, counter)
            .putString(KEY_STEPS_DAY, today)
            .putLong(KEY_STEPS_TODAY, stepsToday + delta)
            .apply()
        if (delta > 0) update { s, _ -> PetEngine.addSteps(s, delta) to Unit }
        _stepsToday.value = stepsToday + delta
        delta
    }

    private val _stepsToday = MutableStateFlow(
        if (prefs.getString(KEY_STEPS_DAY, null) == LocalDate.now(zone).toString()) prefs.getLong(KEY_STEPS_TODAY, 0) else 0L
    )
    val stepsToday: StateFlow<Long> = _stepsToday

    var lastNotificationAt: Long
        get() = prefs.getLong(KEY_LAST_NOTIF, 0)
        set(v) = prefs.edit().putLong(KEY_LAST_NOTIF, v).apply()

    companion object {
        private const val KEY_STATE = "state"
        private const val KEY_LAST_COUNTER = "step_counter_last"
        private const val KEY_STEPS_TODAY = "steps_today"
        private const val KEY_STEPS_DAY = "steps_day"
        private const val KEY_LAST_NOTIF = "last_notification"

        @Volatile private var instance: PetRepository? = null
        fun get(context: Context): PetRepository =
            instance ?: synchronized(this) { instance ?: PetRepository(context.applicationContext).also { instance = it } }
    }
}
