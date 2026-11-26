package app.psykit.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import app.psykit.core.model.BodyMap
import app.psykit.core.model.BodyPoint
import app.psykit.core.model.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Hilfsfunktionen für die Berührungs-Interaktionen in der Szene.
 * Das Tierchen steht ungefähr in der Mitte der Szene; diese „Pet-Box“ dient als Trefferfläche.
 */

/** Normierte Position (0..1) innerhalb der Pet-Box oder null, wenn außerhalb. */
fun petBoxPos(p: Offset, size: IntSize): Offset? {
    val left = size.width * 0.22f; val right = size.width * 0.78f
    val top = size.height * 0.10f; val bottom = size.height * 0.95f
    if (p.x !in left..right || p.y !in top..bottom) return null
    return Offset((p.x - left) / (right - left), (p.y - top) / (bottom - top))
}

/**
 * Körperzonen eines eigenen Bildes in Szenen-Pixeln (Befund 06): verbindet die [BodyMap]
 * (Bildanteile) mit der Lage der Figur in der Szene ([figureRect]).
 */
class SceneBody(val rect: androidx.compose.ui.geometry.Rect, val map: BodyMap) {
    fun toImage(p: Offset) = BodyPoint((p.x - rect.left) / rect.width, (p.y - rect.top) / rect.height)
    fun toScene(b: BodyPoint) = Offset(rect.left + b.x * rect.width, rect.top + b.y * rect.height)
    fun zoneAt(p: Offset): Zone? = map.zoneAt(toImage(p))
    /** Toleranz in Szenen-Pixeln um den Mund. */
    fun mouthHit(p: Offset, tolerancePx: Float): Boolean =
        (p - toScene(map.mouth)).getDistance() <= tolerancePx
    val mouth: Offset get() = toScene(map.mouth)
    val tail: Offset get() = toScene(map.tail)

    companion object {
        fun of(fp: Footprint?, head: HeadPoint, sceneW: Float, sceneH: Float, density: Float, floating: Boolean, hasTail: Boolean? = null): SceneBody? {
            if (fp == null || fp.w <= 0 || fp.h <= 0 || fp.mask.isEmpty() || sceneW <= 0f || sceneH <= 0f) return null
            val map = BodyMap(fp.maskCols, fp.maskRows, fp.mask, head.x, head.y, head.size, head.eyes, head.mouth, head.tail, hasTail, head.bodyWidth)
            return SceneBody(figureRect(sceneW, sceneH, density, fp, floating), map)
        }
    }
}

/** Welche Körperstelle liegt unter dem Finger? */
fun zoneAt(p: Offset, size: IntSize): Zone? {
    val n = petBoxPos(p, size) ?: return null
    val side = n.x < 0.3f || n.x > 0.7f
    return when {
        n.y < 0.18f -> if (side) Zone.EARS else Zone.HEAD
        n.y < 0.38f -> if (side) Zone.CHEEKS else Zone.HEAD
        n.y < 0.50f -> Zone.CHIN
        n.y < 0.78f -> if (n.x > 0.78f) Zone.TAIL else Zone.BELLY
        else -> Zone.TAIL
    }
}

/** Landet das Essen im Mund? (großzügig: obere Hälfte des Tierchens) */
fun mouthHit(p: Offset, size: IntSize): Boolean {
    val n = petBoxPos(p, size) ?: return false
    return n.y < 0.6f && n.x in 0.15f..0.85f
}

/** Schmutzflecken: feste, zufällige Positionen auf dem Körper (normiert auf die Szene). */
fun dirtSpotPositions(count: Int, seed: Long): List<Offset> {
    val r = Random(seed)
    return List(count) { Offset(0.36f + r.nextFloat() * 0.28f, 0.30f + r.nextFloat() * 0.52f) }
}

/** Position der Häufchen in der Szene (wie in PetScene gezeichnet), normiert. */
fun poopPosition(i: Int): Offset = Offset(0.14f + i * 0.11f, 0.90f)

/**
 * Hört ein paar Sekunden ins Mikrofon und erkennt, ob gleichmäßig gesummt wird.
 * Gibt false zurück, wenn keine Berechtigung da ist.
 */
suspend fun detectHumming(ctx: Context, millis: Long = 3500): Boolean = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return@withContext false
    val rate = 16000
    val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec = try {
        AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 8192))
    } catch (e: SecurityException) { return@withContext false }
    if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext false }
    try {
        rec.startRecording()
        val buf = ShortArray(1600)
        var loud = 0; var total = 0
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            val n = rec.read(buf, 0, buf.size)
            if (n > 0) {
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                total++
                if (sqrt(sum / n) > 900) loud++
            }
        }
        total > 0 && loud.toFloat() / total > 0.45f
    } catch (e: Exception) {
        false
    } finally {
        runCatching { rec.stop() }
        rec.release()
    }
}
