package app.psykit.sprites

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.psykit.core.model.Mood
import app.psykit.core.model.Stage
import java.io.File

/** Bild-Varianten je Laune. Fehlt eine Variante, wird das normale Bild der Stufe benutzt. */
enum class SpriteVariant(val label: String, val suffix: String) {
    BASE("Normal", ""),
    SLEEPING("Schläft", "_sleeping"),
    HAPPY("Glücklich", "_happy"),
    SAD("Traurig", "_sad"),
    HUNGRY("Hungrig", "_hungry"),
    SICK("Krank", "_sick");

    companion object {
        fun forMood(mood: Mood): SpriteVariant = when (mood) {
            Mood.SLEEPING -> SLEEPING
            Mood.HAPPY -> HAPPY
            Mood.SAD, Mood.DIRTY -> SAD
            Mood.HUNGRY -> HUNGRY
            Mood.SICK -> SICK
            else -> BASE
        }
    }
}

/**
 * Ein geladenes Bild: entweder Standbild ([image]) oder animierte GIF/WebP-Datei ([animated]).
 * [exact] = genau die gewünschte Variante (sonst das Ersatzbild „Normal“).
 */
data class Sprite(val image: ImageBitmap?, val animated: File?, val exact: Boolean)

/**
 * Austauschbare Grafiken: Für jede Stufe und Laune kann ein eigenes Bild gewählt werden.
 * Es wird in den App-Speicher kopiert (filesDir/sprites/<stufe><_variante>.png).
 * Ist keins gesetzt, zeichnet die App die eingebaute Figur.
 *
 * Fest einbauen geht auch: app/src/main/assets/sprites/<stufe><_variante>.png,
 * z. B. baby.png, baby_sleeping.png, adult_sad.png.
 */
class SpriteStore(context: Context, val petId: String = "p1") {
    private val ctx = context.applicationContext
    /** Jedes Tierchen hat seinen eigenen Bilder-Ordner (das erste nutzt den alten „sprites“). */
    internal val dir = File(ctx.filesDir, if (petId == "p1") "sprites" else "sprites_$petId").apply { mkdirs() }
    private val cache = mutableMapOf<String, ImageBitmap?>()

    /** Nach dem Wechsel eines Figuren-Pakets alles neu laden. */
    fun clearCache() = cache.clear()

    private fun key(stage: Stage, v: SpriteVariant) = keyOf(stage, v)
    private fun file(stage: Stage, v: SpriteVariant) = File(dir, key(stage, v) + ".png")
    private fun animFile(stage: Stage, v: SpriteVariant) = File(dir, key(stage, v) + ".anim")

    fun hasCustom(stage: Stage, v: SpriteVariant = SpriteVariant.BASE) = file(stage, v).exists() || animFile(stage, v).exists()

    private fun loadExact(stage: Stage, v: SpriteVariant): ImageBitmap? = cache.getOrPut(key(stage, v)) {
        val f = file(stage, v)
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)
        else runCatching { ctx.assets.open("sprites/${key(stage, v)}.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        bmp?.asImageBitmap()
    }

    private fun loadAny(stage: Stage, v: SpriteVariant, exact: Boolean): Sprite? {
        val anim = animFile(stage, v)
        if (anim.exists()) return Sprite(null, anim, exact)
        return loadExact(stage, v)?.let { Sprite(it, null, exact) }
    }

    fun load(stage: Stage, v: SpriteVariant = SpriteVariant.BASE): Sprite? =
        loadAny(stage, v, true)
            ?: (if (v != SpriteVariant.BASE) loadAny(stage, SpriteVariant.BASE, false) else null)
            ?: otherStages(stage).firstNotNullOfOrNull { loadAny(it, v, false) ?: loadAny(it, SpriteVariant.BASE, false) }

    /** Fehlt für eine Stufe ein Bild, nimm das der nächstgelegenen Stufe (das Ei bleibt ein Ei). */
    private fun otherStages(stage: Stage): List<Stage> =
        if (stage == Stage.EGG) emptyList()
        else Stage.entries.filter { it != Stage.EGG && it != stage }.sortedBy { kotlin.math.abs(it.ordinal - stage.ordinal) * 2 + if (it.ordinal > stage.ordinal) 1 else 0 }

    /**
     * Ein einzelnes Bild übernehmen. Gleiche Regeln und gleiche Größengrenze wie beim ZIP-Import.
     * Schlägt es fehl, bleibt das bisherige Bild erhalten.
     */
    fun save(stage: Stage, v: SpriteVariant, uri: Uri): ImageResult {
        val bytes = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_IMAGE_BYTES) }
        }.getOrNull() ?: return ImageResult.Unreadable(0)
        if (bytes.size > MAX_IMAGE_BYTES) return ImageResult.TooLarge(sizeOf(uri) ?: bytes.size.toLong(), MAX_IMAGE_BYTES.toLong())
        val r = writeImage(dir, key(stage, v), bytes)
        if (r.saved) cache.remove(key(stage, v))
        return r
    }

    private fun sizeOf(uri: Uri): Long? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    /** Dateiname eines ausgewählten Bildes (für den Paket-Import). */
    fun displayName(uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        fun keyOf(stage: Stage, v: SpriteVariant) = stage.name.lowercase() + v.suffix

        /** Größengrenze pro Bild, gleich für ZIP- und Einzelimport (40 MiB). */
        const val MAX_IMAGE_BYTES = 40 * 1024 * 1024

        /** Liest höchstens [limit] + 1 Bytes, damit zu große Dateien nicht ganz in den Speicher wandern. */
        fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) { out.write(buf, 0, n); break }
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }

        fun mb(bytes: Long) = String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1024.0 / 1024.0)

        /**
         * Speichert Bilddaten unter [key] in [dir]: animierte GIF/WebP unverändert als .anim,
         * alles andere als PNG (max. 768 px).
         */
        fun writeImage(dir: File, key: String, bytes: ByteArray): ImageResult {
            if (bytes.size > MAX_IMAGE_BYTES) return ImageResult.TooLarge(bytes.size.toLong(), MAX_IMAGE_BYTES.toLong())
            val animated = isAnimated(bytes)
            if (animated) {
                val ok = runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) is android.graphics.drawable.AnimatedImageDrawable }.getOrDefault(false)
                if (ok) {
                    val written = runCatching {
                        val t = File(dir, "$key.anim.part")
                        t.writeBytes(bytes)
                        val f = File(dir, "$key.anim")
                        f.delete()
                        check(t.renameTo(f))
                    }.isSuccess
                    if (written) { File(dir, "$key.png").delete(); return ImageResult.Ok(true) }
                    File(dir, "$key.anim.part").delete()
                    return ImageResult.Unreadable(bytes.size.toLong())
                }
            }
            val bmp = decodeScaled(bytes) ?: return ImageResult.Unreadable(bytes.size.toLong())
            val written = runCatching {
                val t = File(dir, "$key.png.part")
                t.outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                val f = File(dir, "$key.png")
                f.delete()
                check(t.renameTo(f))
            }.isSuccess
            if (!written) { File(dir, "$key.png.part").delete(); return ImageResult.Unreadable(bytes.size.toLong()) }
            File(dir, "$key.anim").delete()
            return if (animated) ImageResult.StillOnly(android.os.Build.VERSION.SDK_INT < 28) else ImageResult.Ok(false)
        }

        /** Animiert? WebP mit ANIM-Block oder GIF. */
        private fun isAnimated(b: ByteArray): Boolean {
            if (b.size > 6 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()) return true
            if (b.size > 16 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP") {
                val head = String(b, 0, minOf(b.size, 4096), Charsets.ISO_8859_1)
                return head.contains("ANIM")
            }
            return false
        }

        /** Auf max. 768 px verkleinert dekodieren; mit Ersatzweg für große oder ungewöhnliche Dateien. */
        private fun decodeScaled(bytes: ByteArray): Bitmap? {
            runCatching {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { d, info, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val max = maxOf(info.size.width, info.size.height)
                    if (max > 768) {
                        val scale = 768f / max
                        d.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    }
                }
            }
            return runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var sample = 1
                while (maxOf(o.outWidth, o.outHeight) / sample > 1536) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }

    fun clear(stage: Stage, v: SpriteVariant) {
        file(stage, v).delete()
        animFile(stage, v).delete()
        cache.remove(key(stage, v))
    }
}

/** Ergebnis beim Übernehmen eines Bildes, mit genauem Grund. */
sealed class ImageResult {
    /** Gespeichert, und ob als Animation. */
    data class Ok(val animated: Boolean) : ImageResult()
    /** Animierte Datei, aber nur das erste Bild wurde übernommen. [oldAndroid] = Android 8 kann keine Animation. */
    data class StillOnly(val oldAndroid: Boolean) : ImageResult()
    data class TooLarge(val actual: Long, val limit: Long) : ImageResult()
    data class Unreadable(val size: Long) : ImageResult()

    val saved get() = this is Ok || this is StillOnly

    /** Grund als kurzer Text, oder null wenn alles geklappt hat. */
    fun reason(): String? = when (this) {
        is Ok -> null
        is StillOnly -> if (oldAndroid) "Animation nur als Standbild übernommen (Animation braucht Android 9 oder neuer)"
            else "Animation nur als Standbild übernommen (Animation ließ sich nicht abspielen)"
        is TooLarge -> "zu groß: ${SpriteStore.mb(actual)}, erlaubt sind ${SpriteStore.mb(limit)}"
        is Unreadable -> "Bild nicht lesbar" + if (size > 0) " (${SpriteStore.mb(size)})" else ""
    }
}

/**
 * Ordnet einen Dateinamen einer Stufe und Laune zu, z. B.
 * „1-ei.png“, „2-baby.png“, „baby_traurig.png“, „Psykit 5-erwachsen.png“, „adult_sleeping.png“.
 */
object SpriteNames {
    private val stageWords = mapOf(
        Stage.EGG to setOf("1", "ei", "egg"),
        Stage.BABY to setOf("2", "baby"),
        Stage.CHILD to setOf("3", "kind", "child"),
        Stage.TEEN to setOf("4", "jugendlich", "jugend", "teen"),
        Stage.ADULT to setOf("5", "erwachsen", "adult"),
    )
    private val variantWords = mapOf(
        SpriteVariant.SLEEPING to setOf("schlaeft", "schlafen", "schlaf", "schlafend", "sleeping", "sleep", "asleep"),
        SpriteVariant.HAPPY to setOf("gluecklich", "froh", "happy", "freude"),
        SpriteVariant.SAD to setOf("traurig", "sad"),
        SpriteVariant.HUNGRY to setOf("hungrig", "hunger", "hungry"),
        SpriteVariant.SICK to setOf("krank", "sick"),
    )

    fun guess(fileName: String): Pair<Stage, SpriteVariant>? {
        val norm = fileName.lowercase()
            .substringBeforeLast('.')
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        val tokens = norm.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        // erst Wörter (baby, kind …), dann Ziffern (1–5), damit „baby_1.png“ nicht als Ei zählt
        val stage = stageWords.entries.firstOrNull { (_, words) -> words.any { !it[0].isDigit() && it in tokens } }?.key
            ?: stageWords.entries.firstOrNull { (_, words) -> words.any { it[0].isDigit() && it in tokens } }?.key
            ?: return null
        val variant = variantWords.entries.firstOrNull { (_, words) -> words.any { it in tokens } }?.key ?: SpriteVariant.BASE
        return stage to variant
    }
}
