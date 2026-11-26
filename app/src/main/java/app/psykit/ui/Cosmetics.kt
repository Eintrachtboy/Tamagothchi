package app.psykit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.model.Item
import app.psykit.core.model.Look
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import kotlin.math.min

/**
 * Wo Kopf, Augen, Mund und Schwanz auf einem eigenen Bild liegen (Anteile des Bildes).
 * Mund und Schwanz sind optional (null = automatisch aus dem Umriss, Befund 06).
 */
data class HeadPoint(
    val x: Float = 0.5f, val y: Float = 0.1f, val size: Float = 0.42f, val eyes: Float = 0.3f,
    val mouthX: Float? = null, val mouthY: Float? = null,
    val tailX: Float? = null, val tailY: Float? = null,
) {
    /** Altes Format mit 4 Werten bleibt lesbar; Mund und Schwanz hängen hinten an (-1 = automatisch). */
    fun encode(): String {
        val base = "$x,$y,$size,$eyes"
        if (mouthX == null && tailX == null) return base
        fun v(f: Float?) = f ?: -1f
        return "$base,${v(mouthX)},${v(mouthY)},${v(tailX)},${v(tailY)}"
    }

    val mouth: app.psykit.core.model.BodyPoint? get() = if (mouthX != null && mouthY != null) app.psykit.core.model.BodyPoint(mouthX, mouthY) else null
    val tail: app.psykit.core.model.BodyPoint? get() = if (tailX != null && tailY != null) app.psykit.core.model.BodyPoint(tailX, tailY) else null

    companion object {
        fun decode(s: String?): HeadPoint? {
            val v = s?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: return null
            if (v.size != 4 && v.size != 8) return null
            fun opt(i: Int) = v.getOrNull(i)?.takeIf { it >= 0f }
            return HeadPoint(v[0], v[1], v[2], v[3], opt(4), opt(5), opt(6), opt(7))
        }
    }
}

/** Was das Tierchen trägt. */
data class Wearables(val head: Item?, val eyes: Item?, val deco: List<Item>, val anchor: HeadPoint = HeadPoint()) {
    val empty get() = head == null && eyes == null && deco.isEmpty()

    companion object {
        fun of(s: PetState, anchor: HeadPoint) = Wearables(
            Item.parse(s.headItem)?.takeIf { it.name in s.items },
            Item.parse(s.eyesItem)?.takeIf { it.name in s.items },
            s.placedDeco.mapNotNull { Item.parse(it) }.filter { it.name in s.items },
            anchor,
        )
    }
}

/** Kopf-Geometrie in Pixeln. */
data class HeadGeo(val cx: Float, val top: Float, val width: Float, val eyeY: Float, val eyeDx: Float)

private fun mixf(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Kopf der gezeichneten Standardfigur (gleiche Formeln wie in PetArt). */
fun DrawScope.psykitHead(stage: Stage, bob: Float): HeadGeo {
    val unit = min(size.width, size.height)
    val k = when (stage) { Stage.EGG, Stage.BABY -> 0f; Stage.CHILD -> 0.4f; Stage.TEEN -> 0.8f; Stage.ADULT -> 1f }
    val u = unit * 0.2f * mixf(0.65f, 1f, k)
    val headW = mixf(1.6f, 1.45f, k) * u
    val headH = mixf(1.35f, 1.2f, k) * u
    val bodyH = mixf(0.75f, 1.0f, k) * u
    val legLen = mixf(0.18f, 0.4f, k) * u
    val hover = u * mixf(0.3f, 0.45f, k)
    val footY = size.height * 0.94f - hover + bob
    val bodyC = footY - legLen - bodyH * 0.4f
    val hy = bodyC - bodyH * 0.5f - headH * 0.38f
    return HeadGeo(size.width / 2f, hy - headH * 0.42f, headW, hy + headH * 0.08f, headW * 0.24f)
}

/** Kopf auf einem eigenen Bild, das FIT_CENTER in [size] liegt. */
fun DrawScope.imageHead(imgW: Int, imgH: Int, a: HeadPoint): HeadGeo {
    val r = if (imgW <= 0 || imgH <= 0) Rect(Offset.Zero, size) else {
        val scale = min(size.width / imgW, size.height / imgH)
        val w = imgW * scale; val h = imgH * scale
        Rect(Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h))
    }
    val w = r.width * a.size
    return HeadGeo(r.left + r.width * a.x, r.top + r.height * a.y, w, r.top + r.height * a.eyes, w * 0.24f)
}

fun DrawScope.drawWear(w: Wearables, g: HeadGeo) {
    w.eyes?.let { drawEyes(it.look, g) }
    w.head?.let { if (it.look == Look.MASK) drawEyes(Look.MASK, g) else drawHat(it.look, g) }
}

private fun DrawScope.drawHat(look: Look, g: HeadGeo) {
    val cx = g.cx
    val y = g.top
    val w = g.width
    fun ellipse(c: Color, center: Offset, rw: Float, rh: Float) = drawOval(c, Offset(center.x - rw, center.y - rh), Size(rw * 2, rh * 2))
    when (look) {
        Look.STRAW -> {
            ellipse(Color(0xFFE8C77A), Offset(cx, y + w * 0.06f), w * 0.62f, w * 0.12f)
            ellipse(Color(0xFFD9B261), Offset(cx, y - w * 0.08f), w * 0.3f, w * 0.2f)
            drawRect(Color(0xFFE5484D), Offset(cx - w * 0.3f, y - w * 0.02f), Size(w * 0.6f, w * 0.07f))
        }
        Look.BOW -> {
            val c = Color(0xFFE5484D)
            val l = Path().apply { moveTo(cx, y); lineTo(cx - w * 0.3f, y - w * 0.16f); lineTo(cx - w * 0.3f, y + w * 0.14f); close() }
            val r = Path().apply { moveTo(cx, y); lineTo(cx + w * 0.3f, y - w * 0.16f); lineTo(cx + w * 0.3f, y + w * 0.14f); close() }
            drawPath(l, c); drawPath(r, c); drawCircle(Color(0xFFB4232B), w * 0.07f, Offset(cx, y))
        }
        Look.WIZARD -> {
            drawPath(Path().apply { moveTo(cx - w * 0.32f, y + w * 0.04f); lineTo(cx + w * 0.05f, y - w * 0.75f); lineTo(cx + w * 0.32f, y + w * 0.04f); close() }, Color(0xFF5B3FA6))
            ellipse(Color(0xFF4A2F8F), Offset(cx, y + w * 0.05f), w * 0.5f, w * 0.09f)
            drawPath(starPath(Offset(cx - w * 0.02f, y - w * 0.3f), w * 0.07f), Color(0xFFFFE07A))
            drawPath(starPath(Offset(cx + w * 0.1f, y - w * 0.12f), w * 0.045f), Color(0xFFFFE07A))
        }
        Look.FEATHER -> {
            ellipse(Color(0xFF8C8C9A), Offset(cx, y - w * 0.02f), w * 0.36f, w * 0.16f)
            drawRect(Color(0xFF6E6E7A), Offset(cx - w * 0.36f, y - w * 0.02f), Size(w * 0.72f, w * 0.06f))
            drawPath(Path().apply {
                moveTo(cx + w * 0.15f, y - w * 0.08f)
                quadraticBezierTo(cx + w * 0.55f, y - w * 0.6f, cx + w * 0.2f, y - w * 0.75f)
                quadraticBezierTo(cx + w * 0.28f, y - w * 0.35f, cx + w * 0.15f, y - w * 0.08f)
            }, Color(0xFFB98BD9))
        }
        Look.TOPHAT -> {
            drawRect(Color(0xFF222226), Offset(cx - w * 0.25f, y - w * 0.55f), Size(w * 0.5f, w * 0.55f))
            drawRect(Color(0xFF8A2BE2), Offset(cx - w * 0.25f, y - w * 0.14f), Size(w * 0.5f, w * 0.08f))
            ellipse(Color(0xFF222226), Offset(cx, y), w * 0.45f, w * 0.08f)
            drawCircle(Color(0x55555555), w * 0.05f, Offset(cx + w * 0.3f, y - w * 0.62f))
            drawCircle(Color(0x44555555), w * 0.07f, Offset(cx + w * 0.38f, y - w * 0.76f))
        }
        Look.MUSHROOM -> {
            drawArc(Color(0xFFE5484D), 180f, 180f, true, Offset(cx - w * 0.48f, y - w * 0.4f), Size(w * 0.96f, w * 0.8f))
            listOf(-0.25f to -0.18f, 0.05f to -0.3f, 0.28f to -0.12f, -0.05f to -0.08f).forEach { (dx, dy) ->
                drawCircle(Color.White, w * 0.06f, Offset(cx + w * dx, y + w * dy))
            }
        }
        Look.FLOWERCROWN -> {
            val cols = listOf(Color(0xFFFF8FC0), Color(0xFFFFE07A), Color(0xFFB98BD9), Color(0xFFFFE07A), Color(0xFFFF8FC0))
            cols.forEachIndexed { i, c ->
                val t = i / 4f
                val p = Offset(cx - w * 0.42f + w * 0.84f * t, y - w * 0.05f - w * 0.08f * (1f - 4f * (t - 0.5f) * (t - 0.5f)))
                ellipse(Color(0xFF5DBB63), p + Offset(w * 0.06f, w * 0.03f), w * 0.07f, w * 0.03f)
                for (a in 0 until 5) {
                    val ang = a * 72f * (Math.PI / 180f).toFloat()
                    drawCircle(c, w * 0.045f, p + Offset(kotlin.math.cos(ang) * w * 0.05f, kotlin.math.sin(ang) * w * 0.05f))
                }
                drawCircle(Color(0xFFFFF4C2), w * 0.03f, p)
            }
        }
        Look.BEANIE -> {
            drawArc(Color(0xFF4F7DD9), 180f, 180f, true, Offset(cx - w * 0.42f, y - w * 0.38f), Size(w * 0.84f, w * 0.76f))
            drawRect(Color(0xFFE9F0FF), Offset(cx - w * 0.44f, y - w * 0.06f), Size(w * 0.88f, w * 0.12f))
            drawCircle(Color(0xFFE9F0FF), w * 0.1f, Offset(cx, y - w * 0.42f))
        }
        Look.HELMET -> {
            drawArc(Color(0xFFA9DDFB), 180f, 180f, true, Offset(cx - w * 0.46f, y - w * 0.4f), Size(w * 0.92f, w * 0.8f))
            drawArc(Color(0xFF6FB7E6), 180f, 180f, false, Offset(cx - w * 0.46f, y - w * 0.4f), Size(w * 0.92f, w * 0.8f), style = Stroke(w * 0.04f))
            for (i in -2..2) drawPath(Path().apply {
                val bx = cx + i * w * 0.15f
                moveTo(bx - w * 0.05f, y - w * 0.3f + kotlin.math.abs(i) * w * 0.07f); lineTo(bx, y - w * 0.5f + kotlin.math.abs(i) * w * 0.08f); lineTo(bx + w * 0.05f, y - w * 0.3f + kotlin.math.abs(i) * w * 0.07f); close()
            }, Color(0xFFE9F7FF))
        }
        Look.CAP -> {
            drawArc(Color(0xFF7A5A3A), 180f, 180f, true, Offset(cx - w * 0.38f, y - w * 0.32f), Size(w * 0.76f, w * 0.64f))
            ellipse(Color(0xFF5E4329), Offset(cx + w * 0.3f, y), w * 0.25f, w * 0.06f)
        }
        Look.CROWN -> {
            val p = Path().apply {
                moveTo(cx - w * 0.35f, y); lineTo(cx - w * 0.35f, y - w * 0.3f); lineTo(cx - w * 0.18f, y - w * 0.15f)
                lineTo(cx, y - w * 0.38f); lineTo(cx + w * 0.18f, y - w * 0.15f); lineTo(cx + w * 0.35f, y - w * 0.3f); lineTo(cx + w * 0.35f, y); close()
            }
            drawPath(p, Color(0xFFFFB02E)); drawPath(p, Color(0xFFE5484D), style = Stroke(w * 0.025f))
            drawCircle(Color(0xFFE5484D), w * 0.05f, Offset(cx, y - w * 0.1f))
        }
        Look.NIGHTCAP -> {
            drawPath(Path().apply {
                moveTo(cx - w * 0.4f, y); quadraticBezierTo(cx - w * 0.1f, y - w * 0.6f, cx + w * 0.45f, y - w * 0.35f)
                quadraticBezierTo(cx + w * 0.1f, y - w * 0.3f, cx + w * 0.4f, y); close()
            }, Color(0xFF3B4BA8))
            drawRect(Color(0xFFEDE6FF), Offset(cx - w * 0.42f, y - w * 0.05f), Size(w * 0.84f, w * 0.1f))
            drawCircle(Color(0xFFFFE07A), w * 0.08f, Offset(cx + w * 0.47f, y - w * 0.35f))
            drawPath(starPath(Offset(cx - w * 0.05f, y - w * 0.22f), w * 0.05f), Color(0xFFFFE07A))
        }
        else -> {}
    }
}

private fun DrawScope.drawEyes(look: Look, g: HeadGeo) {
    val r = g.eyeDx * 0.62f
    val l = Offset(g.cx - g.eyeDx, g.eyeY)
    val rr = Offset(g.cx + g.eyeDx, g.eyeY)
    val sw = r * 0.18f
    when (look) {
        Look.ROUND -> {
            drawCircle(Color(0xFF3A2230), r, l, style = Stroke(sw)); drawCircle(Color(0xFF3A2230), r, rr, style = Stroke(sw))
            drawLine(Color(0xFF3A2230), l + Offset(r, 0f), rr - Offset(r, 0f), sw)
        }
        Look.MONOCLE -> {
            drawCircle(Color(0x33FFFFFF), r, rr); drawCircle(Color(0xFFFFB02E), r, rr, style = Stroke(sw))
            drawLine(Color(0xFFFFB02E), rr + Offset(r * 0.7f, r * 0.7f), rr + Offset(r * 1.2f, r * 2.6f), sw * 0.6f)
        }
        Look.WEBBED -> {
            drawCircle(Color(0xFF555566), r, l, style = Stroke(sw)); drawCircle(Color(0xFF555566), r, rr, style = Stroke(sw))
            drawLine(Color(0xFF555566), l + Offset(r, 0f), rr - Offset(r, 0f), sw)
            for (i in 0 until 4) {
                val a = i * 45f * (Math.PI / 180f).toFloat()
                val d = Offset(kotlin.math.cos(a) * r, kotlin.math.sin(a) * r)
                drawLine(Color(0xCCFFFFFF), l - d, l + d, sw * 0.3f)
            }
            drawCircle(Color(0xCCFFFFFF), r * 0.5f, l, style = Stroke(sw * 0.3f))
        }
        Look.SUNGLASSES -> {
            listOf(l, rr).forEach { c -> drawRoundRect(Color(0xE6222233), c - Offset(r * 1.1f, r * 0.8f), Size(r * 2.2f, r * 1.6f), androidx.compose.ui.geometry.CornerRadius(r * 0.6f)) }
            drawLine(Color(0xFF222233), l + Offset(r * 1.1f, 0f), rr - Offset(r * 1.1f, 0f), sw)
            drawCircle(Color(0xFFFF8FC0), r * 0.25f, rr + Offset(r * 0.9f, -r * 0.7f))
        }
        Look.CRYSTAL -> listOf(l, rr).forEach { c ->
            val p = Path().apply { moveTo(c.x, c.y - r * 1.1f); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r * 1.1f); lineTo(c.x - r, c.y); close() }
            drawPath(p, Color(0x8899E1FF)); drawPath(p, Color(0xFF6FB7E6), style = Stroke(sw * 0.7f))
        }
        Look.GOGGLES -> {
            drawLine(Color(0xFF5E4329), Offset(g.cx - g.width * 0.55f, g.eyeY), Offset(g.cx + g.width * 0.55f, g.eyeY), sw * 2.2f)
            listOf(l, rr).forEach { c -> drawCircle(Color(0xFF3A3A44), r * 1.15f, c); drawCircle(Color(0xAA2BB673), r * 0.85f, c) }
        }
        Look.MOON -> listOf(l, rr).forEach { c ->
            drawCircle(Color(0xFFFFE07A), r, c, style = Stroke(sw))
            drawCircle(Color(0x66FFE07A), r * 0.9f, c + Offset(-r * 0.25f, 0f))
        }
        Look.MASK -> {
            val p = Path().apply {
                moveTo(g.cx - g.width * 0.48f, g.eyeY - r * 0.3f)
                quadraticBezierTo(g.cx, g.eyeY - r * 2.2f, g.cx + g.width * 0.48f, g.eyeY - r * 0.3f)
                quadraticBezierTo(g.cx + g.width * 0.4f, g.eyeY + r * 1.6f, g.cx, g.eyeY + r * 0.6f)
                quadraticBezierTo(g.cx - g.width * 0.4f, g.eyeY + r * 1.6f, g.cx - g.width * 0.48f, g.eyeY - r * 0.3f)
                close()
            }
            drawPath(p, Color(0xE61A1033))
            listOf(l, rr).forEach { c -> drawOval(Color(0xFFB98BD9), c - Offset(r * 0.6f, r * 0.35f), Size(r * 1.2f, r * 0.7f)) }
        }
        else -> {}
    }
}

/** Plätze für aufgestellte Deko im Bild (Anteile der Szene). */
private val decoSpots = listOf(Offset(0.1f, 0.78f), Offset(0.9f, 0.78f), Offset(0.12f, 0.42f), Offset(0.88f, 0.48f))

/** Deko im Hintergrund. Der Sternenhimmel färbt den Himmel, Lichterketten hängen oben. */
@Composable
fun BoxScope.DecoLayer(deco: List<Item>) {
    var spot = 0
    deco.forEach { d ->
        when (d) {
            Item.STARRY_SKY -> androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.matchParentSize()) {
                val rnd = kotlin.random.Random(42)
                repeat(40) {
                    drawCircle(Color.White.copy(alpha = 0.3f + rnd.nextFloat() * 0.6f), 1.5f + rnd.nextFloat() * 2f,
                        Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.55f))
                }
            }
            Item.FAIRY_LIGHTS, Item.ICICLE_LIGHTS -> Text(
                (if (d == Item.FAIRY_LIGHTS) "✨ " else "🧊 ").repeat(6), fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 6.dp),
            )
            else -> {
                val p = decoSpots[spot % decoSpots.size]; spot++
                Box(Modifier.matchParentSize()) {
                    Text(d.emoji, fontSize = 30.sp, modifier = Modifier.align(
                        androidx.compose.ui.BiasAlignment(p.x * 2 - 1, p.y * 2 - 1),
                    ))
                }
            }
        }
    }
}
