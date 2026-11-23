package app.psykit.sprites

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
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

/** Ein geladenes Bild und ob es genau die gewünschte Variante ist (oder nur das Ersatzbild). */
data class Sprite(val image: ImageBitmap, val exact: Boolean)

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
    private val dir = File(ctx.filesDir, "sprites").apply { mkdirs() }
    private val cache = mutableMapOf<String, ImageBitmap?>()

    private fun key(stage: Stage, v: SpriteVariant) = stage.name.lowercase() + v.suffix
    private fun file(stage: Stage, v: SpriteVariant) = File(dir, key(stage, v) + ".png")

    fun hasCustom(stage: Stage, v: SpriteVariant = SpriteVariant.BASE) = file(stage, v).exists()

    private fun loadExact(stage: Stage, v: SpriteVariant): ImageBitmap? = cache.getOrPut(key(stage, v)) {
        val f = file(stage, v)
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)
        else runCatching { ctx.assets.open("sprites/${key(stage, v)}.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        bmp?.asImageBitmap()
    }

    fun load(stage: Stage, v: SpriteVariant = SpriteVariant.BASE): Sprite? {
        loadExact(stage, v)?.let { return Sprite(it, true) }
        if (v != SpriteVariant.BASE) loadExact(stage, SpriteVariant.BASE)?.let { return Sprite(it, false) }
        return null
    }

    fun save(stage: Stage, v: SpriteVariant, uri: Uri): Boolean = runCatching {
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

    fun clear(stage: Stage, v: SpriteVariant) {
        file(stage, v).delete()
        cache.remove(key(stage, v))
    }
}
