package app.psykit.data

import android.content.Context
import android.content.SharedPreferences
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.PetState
import app.psykit.core.save.Backup
import app.psykit.core.save.KeyValueStore
import app.psykit.core.save.LoadResult
import app.psykit.core.save.SaveStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Speichert den Spielstand als JSON in den SharedPreferences.
 * Singleton, damit App-Oberfläche und Hintergrund-Worker denselben Stand benutzen.
 *
 * Spielstandschutz (Befund 21, 22): Ein Stand, der nicht gelesen werden kann, wird nie überschrieben.
 * Er wird gesichert, das Tierchen wird gesperrt (nichts wird dafür gespeichert), und erst eine
 * ausdrückliche Wahl in der Oberfläche startet neu. Sicherungen werden nie automatisch gelöscht.
 */
class PetRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("psykit", Context.MODE_PRIVATE)
    private val store = SaveStore(PrefsStore(prefs))
    private val lock = Any()

    val zone: ZoneId get() = ZoneId.systemDefault()

    // ---------- Mehrere Tierchen ----------
    // Das erste Tierchen heißt intern „p1“ und nutzt die alten Speicherplätze weiter.

    private val _currentId = MutableStateFlow(store.resolveCurrent())
    val currentId: StateFlow<String> = _currentId

    val petIds: List<String> get() = store.petIds

    /** Tierchen, deren Spielstand nicht gelesen werden konnte. Für sie wird nichts gespeichert. */
    private val locked = mutableSetOf<String>()
    private val _currentLocked = MutableStateFlow(false)
    /** true, solange der Spielstand des aktiven Tierchens nicht lesbar ist. */
    val currentLocked: StateFlow<Boolean> = _currentLocked
    private fun publishLock() { _currentLocked.value = _currentId.value in locked }

    private val _state = MutableStateFlow(load(_currentId.value))
    val state: StateFlow<PetState> = _state

    init { publishLock() }

    /** Liest einen Stand. Ist er defekt, wird er gesichert und das Tierchen gesperrt. */
    private fun readOk(id: String): PetState? = when (val r = store.read(id)) {
        is LoadResult.Ok -> r.state
        LoadResult.Missing -> null
        is LoadResult.Corrupt -> {
            store.backupCorrupt(id, r.raw, System.currentTimeMillis())
            locked += id
            null
        }
    }

    private fun load(id: String): PetState {
        when (val r = store.read(id)) {
            is LoadResult.Ok -> { locked -= id; return r.state }
            LoadResult.Missing -> {
                locked -= id
                return PetState.newEgg(System.currentTimeMillis()).also { save(id, it) }
            }
            is LoadResult.Corrupt -> {
                store.backupCorrupt(id, r.raw, System.currentTimeMillis())
                locked += id
                // Platzhalter nur für die Anzeige; save() schreibt für gesperrte Tierchen nie.
                return PetState.newEgg(System.currentTimeMillis())
            }
        }
    }

    private fun save(id: String, s: PetState) {
        if (id in locked) return
        store.write(id, s)
    }

    private fun save(s: PetState) = save(_currentId.value, s)

    /** Alle lesbaren Tierchen mit nachgerechneter Zeit (nur zum Anzeigen, nicht gespeichert). */
    fun allPets(): List<Pair<String, PetState>> = synchronized(lock) {
        val now = System.currentTimeMillis()
        petIds.mapNotNull { id ->
            val s = if (id == _currentId.value) _state.value.takeIf { id !in locked } else readOk(id)
            s?.let { id to PetEngine.advance(it, now, zone) }
        }
    }

    /** Tierchen, deren Spielstand nicht gelesen werden konnte (sie fehlen in [allPets]). */
    fun brokenPets(): List<String> = synchronized(lock) {
        petIds.filter { id -> if (id == _currentId.value) id in locked else store.read(id) is LoadResult.Corrupt }
    }

    fun switchTo(id: String) = synchronized(lock) {
        if (id !in petIds) return@synchronized
        if (id != _currentId.value) refresh()
        store.setCurrent(id)
        _currentId.value = id
        _state.value = PetEngine.advance(load(id), System.currentTimeMillis(), zone)
        save(_state.value)
        publishLock()
    }

    /** Ein anderes (nicht aktives) Tierchen verändern, z. B. beim Besuch. */
    fun updatePet(id: String, block: (PetState) -> PetState) = synchronized(lock) {
        if (id == _currentId.value) { update { s, _ -> block(s) to Unit }; return@synchronized }
        val s = readOk(id) ?: return@synchronized
        save(id, block(PetEngine.advance(s, System.currentTimeMillis(), zone)))
    }

    /**
     * Besuch abrechnen (Befund 08): aktives Tierchen und Besuchstier werden gemeinsam
     * in einem Schreibvorgang gespeichert, genau einmal. Ist das Besuchstier nicht lesbar,
     * bekommt nur das aktive Tierchen seine Belohnung.
     */
    fun <T> updateWithPartner(
        partnerId: String,
        partner: (PetState) -> PetState,
        current: (PetState, Long) -> Pair<PetState, T>,
        /** V77-03: Das Besuchstier bekommt nur etwas, wenn der eigene Abschluss gültig war. */
        applyPartner: (T) -> Boolean = { true },
    ): T = synchronized(lock) {
        val now = System.currentTimeMillis()
        val (next, result) = current(PetEngine.advance(_state.value, now, zone), now)
        val other = if (partnerId != _currentId.value && partnerId !in locked) readOk(partnerId) else null
        val writes = buildMap {
            if (_currentId.value !in locked) put(_currentId.value, next)
            if (other != null && partnerId !in locked && applyPartner(result)) put(partnerId, partner(PetEngine.advance(other, now, zone)))
        }
        if (writes.isNotEmpty()) store.writeAll(writes)
        _state.value = next
        result
    }

    /** Neues Tierchen (Ei) anlegen und dorthin wechseln. */
    fun addPet(name: String): String = synchronized(lock) {
        addPetWith(PetState.newEgg(System.currentTimeMillis(), name.ifBlank { "Lumi" }))
    }

    private fun addPetWith(s: PetState): String {
        refresh()
        val id = "p" + System.currentTimeMillis().toString(36)
        store.addPet(id, s)
        _currentId.value = id
        locked -= id
        _state.value = PetEngine.advance(s, System.currentTimeMillis(), zone)
        save(_state.value)
        publishLock()
        return id
    }

    /** Tierchen löschen (nicht das letzte). Liste, Stand und neue Auswahl werden gemeinsam geschrieben. */
    fun deletePet(id: String) = synchronized(lock) {
        val wasCurrent = id == _currentId.value
        val next = store.deletePet(id, _currentId.value) ?: return@synchronized
        locked -= id
        if (wasCurrent) {
            _currentId.value = next
            _state.value = PetEngine.advance(load(next), System.currentTimeMillis(), zone)
            save(_state.value)
        }
        publishLock()
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

    /** Neues Ei für das aktive Tierchen (ausdrückliche Wahl). Eine vorhandene Sicherung bleibt erhalten. */
    fun reset(name: String) = synchronized(lock) {
        val s = PetState.newEgg(System.currentTimeMillis(), name.ifBlank { "Lumi" })
        locked -= _currentId.value
        save(s); _state.value = s
        publishLock()
        // Der Tages-Schrittzähler gehört allen Tierchen gemeinsam und bleibt beim Neustart eines Tiers erhalten.
    }

    // ---------- Wiederherstellung ----------

    /** Den Stand des aktiven Tierchens erneut lesen. true, wenn er jetzt lesbar ist. */
    fun retry(): Boolean = synchronized(lock) {
        val id = _currentId.value
        val r = store.read(id)
        if (r is LoadResult.Ok) {
            locked -= id
            _state.value = PetEngine.advance(r.state, System.currentTimeMillis(), zone)
            save(_state.value)
        }
        publishLock()
        r is LoadResult.Ok
    }

    /** Rohtext des aktiven Tierchens (zum Teilen), unverändert wie gespeichert. */
    fun currentRaw(): String? = store.raw(_currentId.value)

    fun backups(): List<Backup> = store.backups()

    /** Sicherung als neues Tierchen anlegen, falls sie lesbar ist. Nichts wird überschrieben. */
    fun restoreBackup(key: String): Boolean = synchronized(lock) {
        val b = store.backups().firstOrNull { it.key == key } ?: return@synchronized false
        val r = SaveStore.decode(b.raw) as? LoadResult.Ok ?: return@synchronized false
        addPetWith(r.state)
        true
    }

    // ---------- Schrittzähler ----------
    // Der Android-Sensor liefert „Schritte seit dem letzten Neustart“. Wir merken uns den
    // letzten Wert und schreiben die Differenz dem Tierchen gut.

    /** Verarbeitet einen neuen Sensorwert und gibt die neu hinzugekommenen Schritte zurück. */
    fun onStepCounter(counter: Long): Long = synchronized(lock) {
        // Gesperrtes Tierchen: Zählerstand nicht weitersetzen, damit die Schritte später gutgeschrieben werden.
        if (_currentId.value in locked) return@synchronized 0L
        val last = prefs.getLong(KEY_LAST_COUNTER, -1L)
        val delta = when {
            last < 0 -> 0L // erster Kontakt: nur merken
            counter >= last -> counter - last
            else -> counter // Handy wurde neu gestartet
        }
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone).toString()
        val sameDay = prefs.getString(KEY_STEPS_DAY, null) == today
        val stepsToday = if (sameDay) prefs.getLong(KEY_STEPS_TODAY, 0) else 0L
        // Messpause über Mitternacht: nur den Anteil nach Mitternacht für heute zählen (zeitlich anteilig).
        val lastAt = prefs.getLong(KEY_LAST_SAMPLE_AT, 0L)
        val todayDelta = if (sameDay || delta <= 0 || lastAt <= 0 || lastAt >= now) delta
        else {
            val midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val share = ((now - midnight).toDouble() / (now - lastAt)).coerceIn(0.0, 1.0)
            kotlin.math.round(delta * share).toLong()
        }
        // Das Tierchen bekommt alle Schritte gutgeschrieben, nur die Tagesanzeige wird aufgeteilt.
        // V77-02: Zählerstand, Tageswerte und Tierstand in einem Schreibvorgang, damit ein Prozessende
        // dazwischen keine Schritte ohne Gutschrift verbraucht.
        val next = if (delta > 0) PetEngine.addSteps(PetEngine.advance(_state.value, now, zone), delta) else null
        val ed = prefs.edit()
            .putLong(KEY_LAST_COUNTER, counter)
            .putLong(KEY_LAST_SAMPLE_AT, now)
            .putString(KEY_STEPS_DAY, today)
            .putLong(KEY_STEPS_TODAY, stepsToday + todayDelta)
        if (next != null) ed.putString(SaveStore.stateKey(_currentId.value), SaveStore.encode(next))
        ed.commit()
        if (next != null) _state.value = next
        _stepsToday.value = stepsToday + todayDelta
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
        private const val KEY_LAST_COUNTER = "step_counter_last"
        private const val KEY_STEPS_TODAY = "steps_today"
        private const val KEY_STEPS_DAY = "steps_day"
        private const val KEY_LAST_SAMPLE_AT = "step_sample_at"
        private const val KEY_LAST_NOTIF = "last_notification"
        const val FIRST = SaveStore.FIRST

        @Volatile private var instance: PetRepository? = null
        fun get(context: Context): PetRepository =
            instance ?: synchronized(this) { instance ?: PetRepository(context.applicationContext).also { instance = it } }
    }
}

/** SharedPreferences als [KeyValueStore]: ein write-Block ist genau ein edit()…apply(). */
private class PrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun keys(): Set<String> = prefs.all.keys
    override fun write(block: KeyValueStore.Batch.() -> Unit) {
        val e = prefs.edit()
        object : KeyValueStore.Batch {
            override fun put(key: String, value: String) { e.putString(key, value) }
            override fun remove(key: String) { e.remove(key) }
        }.block()
        e.apply()
    }
}
