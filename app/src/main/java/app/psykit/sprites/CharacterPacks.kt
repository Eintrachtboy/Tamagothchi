package app.psykit.sprites

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/** Beschreibung eines Figuren-Pakets (pack.json im ZIP, alles optional). */
data class PackManifest(val name: String = "", val petName: String = "", val author: String = "") {
    fun toJson(): String = JSONObject().put("name", name).put("petName", petName).put("author", author).toString(2)

    companion object {
        fun parse(text: String): PackManifest? = runCatching {
            val o = JSONObject(text.trim().removePrefix("\uFEFF"))
            PackManifest(o.optString("name", ""), o.optString("petName", ""), o.optString("author", ""))
        }.getOrNull()
    }
}

data class PackInfo(val id: String, val name: String, val petName: String, val images: Int)

/**
 * Figuren-Pakete: ZIP-Dateien mit Bildern je Stufe und Laune.
 * Jedes Paket liegt unter filesDir/packs/<id>/. Das aktive Paket wird in den Bilder-Ordner kopiert.
 */
class CharacterPacks(context: Context, private val store: SpriteStore) {
    private val ctx = context.applicationContext
    private val root = File(ctx.filesDir, "packs").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("psykit_packs", Context.MODE_PRIVATE)

    var activeId: String?
        get() = prefs.getString(activeKey, null)
        private set(v) { prefs.edit().putString(activeKey, v).apply() }
    private val activeKey = if (store.petId == "p1") "active" else "active_${store.petId}"

    fun list(): List<PackInfo> = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { info(it) }?.sortedBy { it.name.lowercase() } ?: emptyList()

    private fun info(dir: File): PackInfo? {
        val m = runCatching { PackManifest.parse(File(dir, "pack.json").readText()) }.getOrNull() ?: return null
        val images = dir.listFiles()?.count { it.extension == "png" || it.extension == "anim" } ?: 0
        return PackInfo(dir.name, m.name.ifBlank { dir.name }, m.petName, images)
    }

    /** Liest ein ZIP ein. Gibt das neue Paket und die nicht zugeordneten Dateinamen zurück. */
    fun import(uri: Uri, fallbackName: String): Pair<PackInfo?, List<String>> {
        val tmp = File(root, ".import-${System.currentTimeMillis()}").apply { mkdirs() }
        var manifest = PackManifest()
        val unknown = mutableListOf<String>()
        var count = 0
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var total = 0L
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.isDirectory) continue
                        val base = e.name.substringAfterLast('/').substringAfterLast('\\')
                        if (e.name.contains("__MACOSX") || base.startsWith(".")) continue
                        val bytes = zip.readBytes()
                        total += bytes.size
                        if (total > 150L * 1024 * 1024) break
                        val ext = base.substringAfterLast('.', "").lowercase()
                        when {
                            base.equals("pack.json", ignoreCase = true) ->
                                manifest = PackManifest.parse(bytes.decodeToString()) ?: manifest
                            ext in setOf("png", "webp", "gif", "jpg", "jpeg") -> {
                                val target = SpriteNames.guess(base)
                                if (target != null && SpriteStore.writeImage(tmp, SpriteStore.keyOf(target.first, target.second), bytes)) count++
                                else unknown += base
                            }
                            ext in setOf("txt", "md") -> {}
                            else -> unknown += base
                        }
                    }
                }
            } ?: return null to listOf("Datei konnte nicht geöffnet werden")
            if (count == 0) { tmp.deleteRecursively(); return null to unknown }
            val name = manifest.name.ifBlank { fallbackName }
            val m = manifest.copy(name = name)
            File(tmp, "pack.json").writeText(m.toJson())
            val dest = File(root, idFor(name))
            if (activeId == dest.name) activeId = "" // gleiches Paket neu importiert: die neue Version gilt
            dest.deleteRecursively()
            tmp.renameTo(dest)
            return info(dest) to unknown
        } catch (e: Exception) {
            tmp.deleteRecursively()
            return null to listOf("Das ist kein gültiges ZIP (${e.javaClass.simpleName})")
        }
    }

    private fun idFor(name: String): String {
        val s = name.lowercase().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        return s.ifEmpty { "paket" }
    }

    /** Speichert die aktuell gesetzten Bilder als Paket. */
    fun saveCurrent(name: String): PackInfo? {
        val files = store.dir.listFiles()?.filter { it.extension == "png" || it.extension == "anim" }.orEmpty()
        if (files.isEmpty()) return null
        val dest = File(root, idFor(name)).apply { deleteRecursively(); mkdirs() }
        files.forEach { it.copyTo(File(dest, it.name), overwrite = true) }
        File(dest, "pack.json").writeText(PackManifest(name = name).toJson())
        activeId = dest.name
        return info(dest)
    }

    /** Paket benutzen: ersetzt die aktuellen Bilder. Ungesicherte eigene Bilder werden vorher als „Meine Bilder“ gesichert. */
    fun activate(id: String): PackInfo? {
        val src = File(root, id)
        val info = info(src) ?: return null
        keepCurrent()
        val current = store.dir.listFiles()?.filter { it.extension == "png" || it.extension == "anim" }.orEmpty()
        current.forEach { it.delete() }
        src.listFiles()?.filter { it.extension == "png" || it.extension == "anim" }?.forEach { it.copyTo(File(store.dir, it.name), overwrite = true) }
        store.clearCache()
        activeId = id
        return info
    }

    /** Eingebaute Figur benutzen (alle eigenen Bilder aus, Pakete bleiben erhalten). */
    fun useBuiltIn() {
        keepCurrent()
        store.dir.listFiles()?.forEach { it.delete() }
        store.clearCache()
        activeId = ""
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
        if (activeId == id) {
            store.dir.listFiles()?.forEach { it.delete() }
            store.clearCache()
            activeId = ""
        }
    }

    /** Vor einem Wechsel: Änderungen ins aktive Paket zurückschreiben bzw. lose Einzelbilder als „Meine Bilder“ sichern. */
    private fun keepCurrent() {
        val current = store.dir.listFiles()?.filter { it.extension == "png" || it.extension == "anim" }.orEmpty()
        val id = activeId
        val dest = if (id.isNullOrEmpty()) null else File(root, id)
        when {
            current.isEmpty() -> {}
            dest != null && dest.isDirectory -> {
                dest.listFiles()?.filter { it.extension == "png" || it.extension == "anim" }?.forEach { it.delete() }
                current.forEach { it.copyTo(File(dest, it.name), overwrite = true) }
            }
            id == null -> saveCurrent(uniqueName("Meine Bilder"))
        }
    }

    private fun uniqueName(base: String): String {
        if (!File(root, idFor(base)).exists()) return base
        var i = 2
        while (File(root, idFor("$base $i")).exists()) i++
        return "$base $i"
    }

    /** Eigene Bilder wurden von Hand geändert. Bei der eingebauten Figur gelten sie dann als neue Einzelbilder. */
    fun markModified() { if (activeId == "") activeId = null }
}
