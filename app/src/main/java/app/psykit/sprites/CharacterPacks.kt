package app.psykit.sprites

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/** Beschreibung eines Figuren-Pakets (pack.json im ZIP, alles optional). */
data class PackManifest(
    val name: String = "",
    val petName: String = "",
    val author: String = "",
    val floating: Boolean? = null,
    /** Kopfpunkt für Hut und Brille: "x,y,size,eyes" als Anteile des Bildes. */
    val head: String? = null,
) {
    fun toJson(): String = JSONObject().put("name", name).put("petName", petName).put("author", author)
        .apply { if (floating != null) put("float", floating) }
        .apply {
            val h = head?.split(',')?.mapNotNull { it.toFloatOrNull() }
            if (h != null && h.size == 4) put("head", JSONObject().put("x", h[0].toDouble()).put("y", h[1].toDouble()).put("size", h[2].toDouble()).put("eyes", h[3].toDouble()))
        }
        .toString(2)

    companion object {
        fun parse(text: String): PackManifest? = runCatching {
            val o = JSONObject(text.trim().removePrefix("\uFEFF"))
            val h = o.optJSONObject("head")
            PackManifest(
                o.optString("name", ""), o.optString("petName", ""), o.optString("author", ""),
                if (o.has("float")) o.optBoolean("float", true) else null,
                h?.let { "${it.optDouble("x", 0.5)},${it.optDouble("y", 0.1)},${it.optDouble("size", 0.42)},${it.optDouble("eyes", 0.3)}" },
            )
        }.getOrNull()
    }
}

/** Ergebnis eines ZIP-Imports: das Paket (oder null), kurze Hinweise und der ausführliche Bericht pro Datei. */
data class ImportOutcome(val pack: PackInfo?, val notes: List<String>, val report: List<ReportEntry>)

data class PackInfo(val id: String, val name: String, val petName: String, val images: Int, val floating: Boolean? = null, val head: String? = null)

/**
 * Figuren-Pakete: ZIP-Dateien mit Bildern je Stufe und Laune.
 * Jedes Paket liegt unter filesDir/packs/<id>/. Das aktive Paket wird in den Bilder-Ordner kopiert.
 */
class CharacterPacks(context: Context, private val store: SpriteStore) {
    companion object { const val MAX_ZIP_BYTES = 150L * 1024 * 1024 }

    private val ctx = context.applicationContext
    private val root = File(ctx.filesDir, "packs").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("psykit_packs", Context.MODE_PRIVATE)

    var activeId: String?
        get() = prefs.getString(activeKey, null)
        private set(v) { prefs.edit().putString(activeKey, v).apply() }
    private val activeKey = if (store.petId == "p1") "active" else "active_${store.petId}"

    /** Schwebt die Figur (true) oder steht sie auf dem Boden (false)? Pro Tierchen. */
    var floating: Boolean
        get() = prefs.getBoolean("float_${store.petId}", true)
        set(v) { prefs.edit().putBoolean("float_${store.petId}", v).apply() }

    /** Sitz von Hut und Brille auf eigenen Bildern ("x,y,size,eyes"), null = Standard. Pro Tierchen. */
    var headPoint: String?
        get() = prefs.getString("head_${store.petId}", null)
        set(v) { prefs.edit().putString("head_${store.petId}", v).apply() }

    fun list(): List<PackInfo> = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { info(it) }?.sortedBy { it.name.lowercase() } ?: emptyList()

    private fun info(dir: File): PackInfo? {
        val m = runCatching { PackManifest.parse(File(dir, "pack.json").readText()) }.getOrNull() ?: return null
        val images = dir.listFiles()?.count { it.extension == "png" || it.extension == "anim" } ?: 0
        return PackInfo(dir.name, m.name.ifBlank { dir.name }, m.petName, images, m.floating, m.head)
    }

    /**
     * Liest ein ZIP ein. Gibt das neue Paket und pro Datei den Grund zurück, falls etwas nicht übernommen wurde.
     *
     * Ein bestehendes Paket gleichen Namens wird nur ersetzt, wenn alles geklappt hat. Bilder, die im neuen
     * ZIP fehlen oder fehlschlagen, bleiben aus dem bisherigen Stand erhalten.
     */
    fun import(uri: Uri, fallbackName: String): ImportOutcome {
        val tmp = File(root, ".import-${System.currentTimeMillis()}").apply { mkdirs() }
        var manifest = PackManifest()
        val notes = mutableListOf<String>()
        val failedKeys = mutableSetOf<String>()
        val report = mutableListOf<ReportEntry>()
        fun role(t: Pair<app.psykit.core.model.Stage, SpriteVariant>) = "${t.first.displayName} · ${t.second.label}"
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
                        val ext = base.substringAfterLast('.', "").lowercase()
                        val isImage = ext in setOf("png", "webp", "gif", "jpg", "jpeg")
                        val target = if (isImage) SpriteNames.guess(base) else null
                        if (isImage && target == null) {
                            notes += "$base: Dateiname unbekannt (z. B. adult.webp oder baby_happy.webp)"
                            report += ReportEntry(base, null, ReportEntry.Status.REJECTED, if (e.size > 0) SpriteStore.mb(e.size) else "", listOf("Dateiname unbekannt, z. B. adult.webp oder baby_happy.webp"))
                            continue
                        }
                        val bytes = SpriteStore.readLimited(zip, SpriteStore.MAX_IMAGE_BYTES)
                        if (bytes.size > SpriteStore.MAX_IMAGE_BYTES) {
                            // Rest des Eintrags überspringen, um die echte Größe zu melden
                            var actual = bytes.size.toLong()
                            val buf = ByteArray(64 * 1024)
                            while (true) { val n = zip.read(buf); if (n < 0) break; actual += n }
                            total += actual
                            if (target != null) failedKeys += SpriteStore.keyOf(target.first, target.second)
                            val r = ImageResult.TooLarge(actual, SpriteStore.MAX_IMAGE_BYTES.toLong())
                            notes += "$base: ${r.reason()}"
                            report += SpriteStore.report(base, target?.let(::role), actual, app.psykit.core.image.ImageInfo.parse(bytes), r)
                            continue
                        }
                        total += bytes.size
                        if (total > MAX_ZIP_BYTES) {
                            notes += "Ab $base nicht mehr gelesen: Paket größer als ${SpriteStore.mb(MAX_ZIP_BYTES)}"
                            report += ReportEntry(base, target?.let(::role), ReportEntry.Status.REJECTED, SpriteStore.mb(bytes.size.toLong()), listOf("ab hier nicht mehr gelesen: Paket entpackt größer als ${SpriteStore.mb(MAX_ZIP_BYTES)}"))
                            break
                        }
                        when {
                            base.equals("pack.json", ignoreCase = true) -> {
                                val m = PackManifest.parse(bytes.decodeToString())
                                if (m != null) manifest = m else notes += "pack.json: nicht lesbar, wird ignoriert"
                                report += ReportEntry(base, null, if (m != null) ReportEntry.Status.OK else ReportEntry.Status.WARN, "Paketbeschreibung", if (m != null) emptyList() else listOf("nicht lesbar, wird ignoriert"))
                            }
                            target != null -> {
                                val key = SpriteStore.keyOf(target.first, target.second)
                                val r = SpriteStore.writeImage(tmp, key, bytes)
                                if (r.saved) count++ else failedKeys += key
                                r.reason()?.let { notes += "$base: $it" }
                                report += SpriteStore.report(base, role(target), bytes.size.toLong(), app.psykit.core.image.ImageInfo.parse(bytes), r)
                            }
                            ext in setOf("txt", "md") -> {}
                            else -> {
                                notes += "$base: kein Bild, ignoriert"
                                report += ReportEntry(base, null, ReportEntry.Status.IGNORED, SpriteStore.mb(bytes.size.toLong()), listOf("kein Bild, ignoriert"))
                            }
                        }
                    }
                }
            } ?: run { tmp.deleteRecursively(); return ImportOutcome(null, listOf("Datei konnte nicht geöffnet werden"), report) }
            if (count == 0) { tmp.deleteRecursively(); return ImportOutcome(null, notes, report) }

            val name = manifest.name.ifBlank { fallbackName }
            val dest = File(root, idFor(name))
            val old = if (dest.isDirectory) runCatching { PackManifest.parse(File(dest, "pack.json").readText()) }.getOrNull() else null

            if (dest.isDirectory) {
                // Ist das Paket gerade aktiv, zuerst den sichtbaren Stand (inkl. Einzeländerungen) ins Paket schreiben.
                if (activeId == dest.name) keepCurrent()
                // Bilder aus dem bisherigen Stand behalten, die im neuen ZIP fehlen oder fehlgeschlagen sind.
                val newKeys = tmp.listFiles()?.filter { it.extension == "png" || it.extension == "anim" }?.map { it.nameWithoutExtension }?.toSet().orEmpty()
                dest.listFiles()?.filter { (it.extension == "png" || it.extension == "anim") && it.nameWithoutExtension !in newKeys }?.forEach { f ->
                    f.copyTo(File(tmp, f.name), overwrite = true)
                    if (f.nameWithoutExtension in failedKeys) {
                        notes += "${f.nameWithoutExtension}: bisheriges Bild bleibt erhalten"
                        report += ReportEntry(f.name.substringBefore('.') + " (bisher)", null, ReportEntry.Status.KEPT, SpriteStore.mb(f.length()), listOf("neues Bild abgelehnt, das bisherige bleibt erhalten"))
                    }
                }
            }
            val m = manifest.copy(
                name = name,
                floating = manifest.floating ?: old?.floating,
                head = manifest.head ?: old?.head,
                petName = manifest.petName.ifBlank { old?.petName ?: "" },
                author = manifest.author.ifBlank { old?.author ?: "" },
            )
            File(tmp, "pack.json").writeText(m.toJson())

            // Austausch: alter Stand wird erst gelöscht, wenn der neue sicher an seinem Platz liegt.
            val backup = File(root, ".old-${dest.name}-${System.currentTimeMillis()}")
            if (dest.exists() && !dest.renameTo(backup)) {
                tmp.deleteRecursively()
                return ImportOutcome(null, notes + "Paket konnte nicht ersetzt werden, der bisherige Stand bleibt unverändert", report)
            }
            if (!tmp.renameTo(dest)) {
                if (backup.exists()) backup.renameTo(dest)
                tmp.deleteRecursively()
                return ImportOutcome(null, notes + "Paket konnte nicht gespeichert werden, der bisherige Stand bleibt unverändert", report)
            }
            backup.deleteRecursively()
            // Gleiches Paket neu importiert: beim Aktivieren nicht mit dem alten Stand überschreiben.
            if (activeId == dest.name) activeId = ""
            return ImportOutcome(info(dest), notes, report)
        } catch (e: Exception) {
            tmp.deleteRecursively()
            return ImportOutcome(null, notes + "Das ist kein gültiges ZIP (${e.javaClass.simpleName}). Der bisherige Stand bleibt unverändert", report)
        }
    }

    /** Übrig gebliebene Ordner eines abgebrochenen Imports aufräumen bzw. wiederherstellen. */
    private fun recoverLeftovers() {
        val stale = System.currentTimeMillis() - 10 * 60 * 1000
        root.listFiles()?.filter { it.lastModified() < stale }?.forEach { f ->
            when {
                f.name.startsWith(".import-") -> f.deleteRecursively()
                f.name.startsWith(".old-") -> {
                    val id = f.name.removePrefix(".old-").substringBeforeLast('-')
                    val dest = File(root, id)
                    if (!dest.exists()) f.renameTo(dest) else f.deleteRecursively()
                }
            }
        }
    }

    init { runCatching { recoverLeftovers() } }

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
        File(dest, "pack.json").writeText(PackManifest(name = name, head = headPoint).toJson())
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
        info.floating?.let { floating = it }
        headPoint = info.head
        return info
    }

    /** Eingebaute Figur benutzen (alle eigenen Bilder aus, Pakete bleiben erhalten). */
    fun useBuiltIn() {
        keepCurrent()
        store.dir.listFiles()?.forEach { it.delete() }
        store.clearCache()
        activeId = ""
        floating = true
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
