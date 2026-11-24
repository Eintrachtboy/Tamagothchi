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

    // ---------- Mehrere Tierchen ----------
    // Das erste Tierchen heißt intern „p1“ und nutzt die alten Speicherplätze weiter.

    private val _currentId = MutableStateFlow(prefs.getString(KEY_CURRENT, null) ?: FIRST)
    val currentId: StateFlow<String> = _currentId

    val petIds: List<String>
        get() = prefs.getString(KEY_IDS, null)?.split(',')?.filter { it.isNotBlank() }?.ifEmpty { null } ?: listOf(FIRST)

    private fun stateKey(id: String) = if (id == FIRST) KEY_STATE else "${KEY_STATE}_$id"

    private val _state = MutableStateFlow(load(_currentId.value))
    val state: StateFlow<PetState> = _state

    private fun read(id: String): PetState? =
        prefs.getString(stateKey(id), null)?.let { runCatching { json.decodeFromString(PetState.serializer(), it) }.getOrNull() }

    private fun load(id: String): PetState = read(id) ?: PetState.newEgg(System.currentTimeMillis()).also { save(id, it) }

    private fun save(id: String, s: PetState) {
        prefs.edit().putString(stateKey(id), json.encodeToString(PetState.serializer(), s)).apply()
    }

    private fun save(s: PetState) = save(_currentId.value, s)

    /** Alle Tierchen mit nachgerechneter Zeit (nur zum Anzeigen, nicht gespeichert). */
    fun allPets(): List<Pair<String, PetState>> = synchronized(lock) {
        val now = System.currentTimeMillis()
        petIds.mapNotNull { id ->
            val s = if (id == _currentId.value) _state.value else read(id)
            s?.let { id to PetEngine.advance(it, now, zone) }
        }
    }

    fun switchTo(id: String) = synchronized(lock) {
        if (id !in petIds) return@synchronized
        refresh()
        prefs.edit().putString(KEY_CURRENT, id).apply()
        _currentId.value = id
        _state.value = PetEngine.advance(load(id), System.currentTimeMillis(), zone)
        save(_state.value)
    }

    /** Ein anderes (nicht aktives) Tierchen verändern, z. B. beim Besuch. */
    fun updatePet(id: String, block: (PetState) -> PetState) = synchronized(lock) {
        if (id == _currentId.value) { update { s, _ -> block(s) to Unit }; return@synchronized }
        val s = read(id) ?: return@synchronized
        save(id, block(PetEngine.advance(s, System.currentTimeMillis(), zone)))
    }

    /** Neues Tierchen (Ei) anlegen und dorthin wechseln. */
    fun addPet(name: String): String = synchronized(lock) {
        val id = "p" + System.currentTimeMillis().toString(36)
        save(id, PetState.newEgg(System.currentTimeMillis(), name.ifBlank { "Lumi" }))
        prefs.edit().putString(KEY_IDS, (petIds + id).joinToString(",")).apply()
        switchTo(id)
        id
    }

    /** Tierchen löschen (nicht das letzte). Gibt die id zurück, zu der gewechselt wurde. */
    fun deletePet(id: String) = synchronized(lock) {
        val ids = petIds
        if (ids.size <= 1 || id !in ids) return@synchronized
        val rest = ids - id
        prefs.edit().putString(KEY_IDS, rest.joinToString(",")).remove(stateKey(id)).apply()
        if (_currentId.value == id) switchTo(rest.first())
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
        private const val KEY_CURRENT = "current_pet"
        private const val KEY_IDS = "pet_ids"
        const val FIRST = "p1"
        private const val KEY_LAST_COUNTER = "step_counter_last"
        private const val KEY_STEPS_TODAY = "steps_today"
        private const val KEY_STEPS_DAY = "steps_day"
        private const val KEY_LAST_NOTIF = "last_notification"

        @Volatile private var instance: PetRepository? = null
        fun get(context: Context): PetRepository =
            instance ?: synchronized(this) { instance ?: PetRepository(context.applicationContext).also { instance = it } }
    }
}
