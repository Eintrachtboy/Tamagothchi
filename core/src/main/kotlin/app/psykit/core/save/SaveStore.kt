package app.psykit.core.save

import app.psykit.core.model.PetState
import kotlinx.serialization.json.Json

/**
 * Einfacher Schlüssel-Wert-Speicher (in der App: SharedPreferences).
 * Alles, was in einem [write]-Block passiert, wird in einem einzigen Schreibvorgang übernommen.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun keys(): Set<String>
    fun write(block: Batch.() -> Unit)

    interface Batch {
        fun put(key: String, value: String)
        fun remove(key: String)
    }
}

/** Ergebnis beim Lesen eines Spielstands. Fehlend und defekt sind bewusst getrennt (Befund 21). */
sealed interface LoadResult {
    data object Missing : LoadResult
    data class Ok(val state: PetState) : LoadResult
    data class Corrupt(val raw: String, val error: String) : LoadResult
}

/** Eine gesicherte Kopie eines Spielstands, der nicht gelesen werden konnte. Wird nie automatisch gelöscht. */
data class Backup(val key: String, val petId: String, val savedAt: Long, val raw: String)

/**
 * Spielstände mehrerer Tierchen lesen und schreiben, ohne dass ein nicht lesbarer Stand überschrieben wird.
 * Reine Logik ohne Android, damit sie mit einem Speicher im Arbeitsspeicher getestet werden kann.
 */
class SaveStore(private val kv: KeyValueStore) {

    val petIds: List<String>
        get() = kv.getString(KEY_IDS)?.split(',')?.filter { it.isNotBlank() }?.ifEmpty { null } ?: listOf(FIRST)

    /** Aktives Tierchen. Zeigt die gespeicherte Auswahl auf ein gelöschtes Tierchen, wird sie korrigiert (Befund 22). */
    fun resolveCurrent(): String {
        val stored = kv.getString(KEY_CURRENT)
        val fixed = repairCurrent(petIds, stored)
        if (stored != null && stored != fixed) kv.write { put(KEY_CURRENT, fixed) }
        return fixed
    }

    fun setCurrent(id: String) = kv.write { put(KEY_CURRENT, id) }

    fun raw(id: String): String? = kv.getString(stateKey(id))

    fun read(id: String): LoadResult = raw(id)?.let(::decode) ?: LoadResult.Missing

    fun write(id: String, s: PetState) = kv.write { put(stateKey(id), encode(s)) }

    /** Mehrere Spielstände in einem Schreibvorgang (z. B. beide Tierchen beim Besuch, Befund 08). */
    fun writeAll(states: Map<String, PetState>) = kv.write { states.forEach { (id, s) -> put(stateKey(id), encode(s)) } }

    /**
     * Legt eine Kopie eines nicht lesbaren Stands an. Gibt es schon eine Kopie mit genau diesem Inhalt,
     * entsteht keine zweite. Gibt den Schlüssel der Kopie zurück.
     */
    fun backupCorrupt(id: String, raw: String, now: Long): String {
        backups().firstOrNull { it.petId == id && it.raw == raw }?.let { return it.key }
        // V77-01: anderer Inhalt mit gleicher Zeitmarke darf keine vorhandene Sicherung überschreiben
        var t = now
        while (kv.getString("$BACKUP_PREFIX${id}_$t") != null) t++
        val key = "$BACKUP_PREFIX${id}_$t"
        kv.write { put(key, raw) }
        return key
    }

    fun backups(): List<Backup> = kv.keys().filter { it.startsWith(BACKUP_PREFIX) }.mapNotNull { key ->
        val rest = key.removePrefix(BACKUP_PREFIX)
        val petId = rest.substringBeforeLast('_', "")
        val at = rest.substringAfterLast('_').toLongOrNull() ?: return@mapNotNull null
        val raw = kv.getString(key) ?: return@mapNotNull null
        if (petId.isEmpty()) null else Backup(key, petId, at, raw)
    }.sortedByDescending { it.savedAt }

    /** Neues Tierchen anlegen und aktiv setzen: Stand, Liste und Auswahl in einem Schreibvorgang. */
    fun addPet(id: String, s: PetState) {
        val ids = petIds + id
        kv.write {
            put(stateKey(id), encode(s))
            put(KEY_IDS, ids.joinToString(","))
            put(KEY_CURRENT, id)
        }
    }

    /**
     * Tierchen löschen (nicht das letzte): Liste, Spielstand und neue Auswahl in einem Schreibvorgang (Befund 22).
     * Sicherungen bleiben erhalten. Gibt die neue aktive id zurück oder null, wenn nicht gelöscht wurde.
     */
    fun deletePet(id: String, current: String): String? {
        val ids = petIds
        if (ids.size <= 1 || id !in ids) return null
        val rest = ids - id
        val next = if (current == id || current !in rest) rest.first() else current
        kv.write {
            put(KEY_IDS, rest.joinToString(","))
            remove(stateKey(id))
            put(KEY_CURRENT, next)
        }
        return next
    }

    companion object {
        /** Unverändert gegenüber v52. Bewusst ohne coerceInputValues: Werte sollen nie still verloren gehen. */
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        const val KEY_STATE = "state"
        const val KEY_CURRENT = "current_pet"
        const val KEY_IDS = "pet_ids"
        const val FIRST = "p1"
        const val BACKUP_PREFIX = "state_backup_"

        /** Das erste Tierchen nutzt den alten Speicherplatz „state“ weiter. */
        fun stateKey(id: String) = if (id == FIRST) KEY_STATE else "${KEY_STATE}_$id"

        fun encode(s: PetState): String = json.encodeToString(PetState.serializer(), s)

        /** Jeder Fehler beim Lesen zählt als defekt, nicht nur Serialisierungsfehler. */
        fun decode(raw: String): LoadResult = try {
            LoadResult.Ok(json.decodeFromString(PetState.serializer(), raw))
        } catch (e: Exception) {
            LoadResult.Corrupt(raw, e.message ?: e::class.simpleName ?: "unbekannter Fehler")
        }

        fun repairCurrent(ids: List<String>, current: String?): String =
            if (current != null && current in ids) current else ids.first()
    }
}
