package app.psykit.core

import app.psykit.core.model.PetState
import app.psykit.core.save.KeyValueStore
import app.psykit.core.save.LoadResult
import app.psykit.core.save.SaveStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Speicher im Arbeitsspeicher; zählt Schreibvorgänge und kann nach n Vorgängen „abstürzen“. */
private class MemoryStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    val data = initial.toMutableMap()
    var writes = 0
    var crashAfter = Int.MAX_VALUE

    override fun getString(key: String) = data[key]
    override fun keys() = data.keys.toSet()
    override fun write(block: KeyValueStore.Batch.() -> Unit) {
        if (writes >= crashAfter) throw IllegalStateException("simulierter Abbruch")
        val puts = mutableMapOf<String, String>()
        val removes = mutableSetOf<String>()
        object : KeyValueStore.Batch {
            override fun put(key: String, value: String) { puts[key] = value; removes -= key }
            override fun remove(key: String) { removes += key; puts -= key }
        }.block()
        // erst alles sammeln, dann gemeinsam übernehmen (wie SharedPreferences.Editor)
        removes.forEach { data -= it }
        data += puts
        writes++
    }
}

class SaveStoreTest {
    private val fixture: String =
        requireNotNull(javaClass.getResource("/fixtures/state_v52.json")) { "Fixture fehlt" }.readText()

    private fun egg(name: String = "Lumi") = PetState.newEgg(1_000L, name)

    @Test fun v52SaveIsReadCompletely() {
        // Ein voll befüllter Spielstand, erzeugt mit dem Code von v52, muss ohne Verlust gelesen werden.
        val r = SaveStore.decode(fixture)
        assertIs<LoadResult.Ok>(r)
        // Jedes Feld aus v52 muss mit demselben Wert erhalten bleiben; neue Felder dürfen dazukommen.
        val before = Json.parseToJsonElement(fixture).jsonObject
        val after = Json.parseToJsonElement(SaveStore.encode(r.state)).jsonObject
        // Neue Felder dürfen dazukommen (auch in verschachtelten Objekten); kein altes Feld darf fehlen oder sich ändern.
        fun contains(old: JsonElement?, new: JsonElement?): Boolean = when {
            old is JsonObject && new is JsonObject -> old.all { (k, v) -> contains(v, new[k]) }
            old is JsonArray && new is JsonArray -> old.size == new.size && old.indices.all { contains(old[it], new[it]) }
            else -> old == new
        }
        before.forEach { (key, value) ->
            assertTrue(contains(value, after[key]), "Feld „$key“ ist beim Lesen und erneuten Schreiben verloren gegangen oder verändert worden")
        }
        assertEquals("Enko", r.state.name)
        assertTrue(r.state.run != null, "Laufender Dungeon-Lauf fehlt")
    }

    @Test fun missingAndCorruptAreDifferent() {
        val kv = MemoryStore(mapOf("state" to "{kaputt"))
        val store = SaveStore(kv)
        assertIs<LoadResult.Corrupt>(store.read("p1"))
        assertEquals(LoadResult.Missing, store.read("p2"))
    }

    @Test fun unknownEnumValueIsCorruptNotSilentlyReplaced() {
        // Ein unbekannter Wert darf nicht still durch einen Standardwert ersetzt werden.
        val broken = fixture.replace("\"stage\": \"CHILD\"", "\"stage\": \"GIANT\"")
        val r = SaveStore.decode(broken)
        assertIs<LoadResult.Corrupt>(r)
        assertEquals(broken, r.raw)
    }

    @Test fun corruptStateIsBackedUpByteIdenticalAndOnlyOnce() {
        val raw = fixture.replace("\"level\": 12", "\"level\": \"zwölf\"")
        val kv = MemoryStore(mapOf("state" to raw))
        val store = SaveStore(kv)
        val key1 = store.backupCorrupt("p1", raw, now = 5_000L)
        val key2 = store.backupCorrupt("p1", raw, now = 9_000L) // zweiter App-Start
        assertEquals(key1, key2)
        assertEquals(1, store.backups().size)
        assertEquals(raw, store.backups().single().raw)
        assertEquals("p1", store.backups().single().petId)
        assertEquals(raw, kv.data["state"], "Original muss unverändert bleiben")
    }

    @Test fun backupsOfDifferentContentAreAllKept() {
        val store = SaveStore(MemoryStore())
        store.backupCorrupt("p1", "{a", 1L)
        store.backupCorrupt("p1", "{b", 2L)
        store.backupCorrupt("pX", "{a", 3L)
        assertEquals(3, store.backups().size)
        assertEquals(listOf(3L, 2L, 1L), store.backups().map { it.savedAt })
    }

    @Test fun currentPointingToDeletedPetIsRepairedWithoutNewEgg() {
        val kv = MemoryStore(mapOf("pet_ids" to "p1,pA", "current_pet" to "pGone", "state" to SaveStore.encode(egg()), "state_pA" to SaveStore.encode(egg("A"))))
        val store = SaveStore(kv)
        assertEquals("p1", store.resolveCurrent())
        assertEquals("p1", kv.data["current_pet"])
        assertNull(kv.data["state_pGone"], "Für die verwaiste id darf kein Ei angelegt werden")
        assertEquals("p1", SaveStore.repairCurrent(listOf("p1"), null))
        assertEquals("pA", SaveStore.repairCurrent(listOf("p1", "pA"), "pA"))
    }

    @Test fun deleteWritesIdsStateAndCurrentInOneStep() {
        val kv = MemoryStore(mapOf("pet_ids" to "p1,pA", "current_pet" to "pA", "state" to SaveStore.encode(egg()), "state_pA" to SaveStore.encode(egg("A"))))
        val store = SaveStore(kv)
        val before = kv.writes
        assertEquals("p1", store.deletePet("pA", current = "pA"))
        assertEquals(1, kv.writes - before, "Löschen muss ein einziger Schreibvorgang sein")
        assertEquals("p1", kv.data["pet_ids"])
        assertEquals("p1", kv.data["current_pet"])
        assertNull(kv.data["state_pA"])
        assertIs<LoadResult.Ok>(store.read("p1"), "Das andere Tierchen bleibt unberührt")
    }

    @Test fun crashDuringDeleteLeavesConsistentState() {
        val kv = MemoryStore(mapOf("pet_ids" to "p1,pA", "current_pet" to "pA", "state" to SaveStore.encode(egg()), "state_pA" to SaveStore.encode(egg("A"))))
        kv.crashAfter = 0
        val store = SaveStore(kv)
        runCatching { store.deletePet("pA", current = "pA") }
        // nichts halb geschrieben: alter Zustand vollständig erhalten
        assertEquals("p1,pA", kv.data["pet_ids"])
        assertEquals("pA", kv.data["current_pet"])
        assertIs<LoadResult.Ok>(store.read("pA"))
        assertEquals("pA", SaveStore(kv).resolveCurrent())
    }

    @Test fun deleteKeepsBackupsAndRefusesLastPet() {
        val kv = MemoryStore(mapOf("pet_ids" to "p1,pA", "state" to SaveStore.encode(egg()), "state_pA" to "{kaputt"))
        val store = SaveStore(kv)
        store.backupCorrupt("pA", "{kaputt", 7L)
        assertEquals("p1", store.deletePet("pA", current = "p1"))
        assertEquals(1, store.backups().size, "Sicherung bleibt nach dem Löschen erhalten")
        assertNull(store.deletePet("p1", current = "p1"), "Das letzte Tierchen wird nicht gelöscht")
    }

    @Test fun corruptPetDoesNotTouchOtherPets() {
        val good = SaveStore.encode(egg("Gut"))
        val kv = MemoryStore(mapOf("pet_ids" to "p1,pA", "state" to "{kaputt", "state_pA" to good))
        val store = SaveStore(kv)
        assertIs<LoadResult.Corrupt>(store.read("p1"))
        store.backupCorrupt("p1", "{kaputt", 1L)
        assertEquals(good, kv.data["state_pA"])
        assertEquals("Gut", (store.read("pA") as LoadResult.Ok).state.name)
    }

    @Test fun addPetWritesEverythingInOneStep() {
        val kv = MemoryStore(mapOf("state" to SaveStore.encode(egg())))
        val store = SaveStore(kv)
        store.addPet("pB", egg("B"))
        assertEquals(1, kv.writes)
        assertEquals(listOf("p1", "pB"), store.petIds)
        assertEquals("pB", store.resolveCurrent())
    }
}
