package app.psykit.sprites

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.psykit.core.model.Stage
import java.io.File

/**
 * Austauschbare Grafiken: Für jede Stufe kann ein eigenes Bild gewählt werden.
 * Es wird in den App-Speicher kopiert (filesDir/sprites/<stufe>.png).
 * Ist keins gesetzt, zeichnet die App die eingebaute Figur.
 *
 * Zusätzlich kann man Bilder fest in die App einbauen: PNG-Dateien unter
 * app/src/main/assets/sprites/<stufe>.png (z. B. baby.png, adult.png) ablegen.
 */
class SpriteStore(context: Context) {
    private val ctx = context.applicationContext
    private val dir = File(ctx.filesDir, "sprites").apply { mkdirs() }
    private val cache = mutableMapOf<Stage, ImageBitmap?>()

    private fun file(stage: Stage) = File(dir, "${stage.name.lowercase()}.png")

    fun hasCustom(stage: Stage) = file(stage).exists()

    fun load(stage: Stage): ImageBitmap? = cache.getOrPut(stage) {
        val f = file(stage)
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)
        else runCatching { ctx.assets.open("sprites/${stage.name.lowercase()}.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        bmp?.asImageBitmap()
    }

    fun save(stage: Stage, uri: Uri): Boolean = runCatching {
        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val max = maxOf(info.size.width, info.size.height)
            if (max > 768) {
                val scale = 768f / max
                d.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        file(stage).outputStream().use { src.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cache.remove(stage)
        true
    }.getOrDefault(false)

    fun clear(stage: Stage) {
        file(stage).delete()
        cache.remove(stage)
    }
}
