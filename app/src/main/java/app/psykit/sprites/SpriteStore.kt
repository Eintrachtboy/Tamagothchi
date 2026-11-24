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
class SpriteStore(context: Context) {
    private val ctx = context.applicationContext
    internal val dir = File(ctx.filesDir, "sprites").apply { mkdirs() }
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

    /** Animierte GIF/WebP unverändert übernehmen, damit die Animation erhalten bleibt. */
    private fun trySaveAnimated(stage: Stage, v: SpriteVariant, uri: Uri): Boolean {
        val mime = ctx.contentResolver.getType(uri) ?: ""
        if (mime != "image/gif" && mime != "image/webp") return false
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        if (bytes.size > 20 * 1024 * 1024) return false
        val drawable = runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) }.getOrNull()
        if (drawable !is android.graphics.drawable.AnimatedImageDrawable) return false
        animFile(stage, v).writeBytes(bytes)
        file(stage, v).delete()
        cache.remove(key(stage, v))
        return true
    }

    fun save(stage: Stage, v: SpriteVariant, uri: Uri): Boolean = runCatching {
        if (trySaveAnimated(stage, v, uri)) return true
        animFile(stage, v).delete()
        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val max = maxOf(info.size.width, info.size.height)
            if (max > 768) {
                val scale = 768f / max
                d.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        file(stage, v).outputStream().use { src.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cache.remove(key(stage, v))
        true
    }.getOrDefault(false)

    /** Dateiname eines ausgewählten Bildes (für den Paket-Import). */
    fun displayName(uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        fun keyOf(stage: Stage, v: SpriteVariant) = stage.name.lowercase() + v.suffix

        /**
         * Speichert Bilddaten unter [key] in [dir]: animierte GIF/WebP unverändert als .anim,
         * alles andere als PNG (max. 768 px).
         */
        fun writeImage(dir: File, key: String, bytes: ByteArray): Boolean = runCatching {
            if (bytes.size > 20 * 1024 * 1024) return false
            val drawable = runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) }.getOrNull()
            if (drawable is android.graphics.drawable.AnimatedImageDrawable) {
                File(dir, "$key.anim").writeBytes(bytes)
                File(dir, "$key.png").delete()
                return true
            }
            val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val max = maxOf(info.size.width, info.size.height)
                if (max > 768) {
                    val scale = 768f / max
                    d.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
            File(dir, "$key.png").outputStream().use { src.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "$key.anim").delete()
            true
        }.getOrDefault(false)
    }

    fun clear(stage: Stage, v: SpriteVariant) {
        file(stage, v).delete()
        animFile(stage, v).delete()
        cache.remove(key(stage, v))
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
