package app.psykit.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import app.psykit.core.model.Mood
import app.psykit.core.model.Stage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Die eingebaute Figur „Lumi“: eine schlanke, schwebende rosa Psycho-Katze mit Luchs-Ohren,
 * Fellkragen und Mondsicheln. Komplett mit Formen gezeichnet,
 * damit keine fremden Grafiken nötig sind. Eigene Bilder ersetzen sie (siehe SpriteStore).
 */

private val Pink = Color(0xFFF4A6D7)
private val PinkShade = Color(0xFFDE86C0)
private val PinkLight = Color(0xFFFFE6F4)
private val Lavender = Color(0xFFC9A8F0)
private val Violet = Color(0xFF5B3FA8)
private val Gold = Color(0xFFFFD36E)
private val AuraBlue = Color(0xFF9BE7FF)
private val Line = Color(0xFF8E3F75)
private val SickTint = Color(0xFFC8D9A4)

private fun DrawScope.oval(color: Color, center: Offset, w: Float, h: Float) =
    drawOval(color, topLeft = Offset(center.x - w / 2, center.y - h / 2), size = Size(w, h))

fun starPath(c: Offset, rOuter: Float, rInner: Float = rOuter * 0.45f, rotation: Float = -90f): Path {
    val p = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) rOuter else rInner
        val a = (rotation + i * 36f) * PI.toFloat() / 180f
        val pt = Offset(c.x + r * cos(a), c.y + r * sin(a))
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    return p
}

fun heartPath(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y + s * 0.35f)
    cubicTo(c.x - s * 1.0f, c.y - s * 0.3f, c.x - s * 0.45f, c.y - s * 1.0f, c.x, c.y - s * 0.45f)
    cubicTo(c.x + s * 0.45f, c.y - s * 1.0f, c.x + s * 1.0f, c.y - s * 0.3f, c.x, c.y + s * 0.35f)
    close()
}

/** 0 = Baby (rundlich, klein) … 1 = Erwachsen (schlank, lange Beine). */
private fun stageK(stage: Stage) = when (stage) {
    Stage.EGG, Stage.BABY -> 0f
    Stage.CHILD -> 0.4f
    Stage.TEEN -> 0.8f
    Stage.ADULT -> 1f
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun crescentPath(c: Offset, r: Float, cut: Offset): Path {
    val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    val inner = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + cut, r * 0.82f)) }
    return Path.combine(PathOperation.Difference, outer, inner)
}

private fun DrawScope.strokePath(p: Path, color: Color, width: Float) =
    drawPath(p, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))

/**
 * Lumi: schlanke, schwebende Psycho-Katze mit großen Luchs-Ohren (Pinselspitzen),
 * Fellkragen, Mondsichel auf der Stirn und als Schweifspitze.
 *
 * @param blink 0 = Augen offen, 1 = zu
 * @param bob vertikale Schwebe-Verschiebung in px (negativ = höher)
 * @param swing Schweif-Pendel −1..1
 */
fun DrawScope.drawPsykit(stage: Stage, mood: Mood, blink: Float, bob: Float, swing: Float, wobble: Float) {
    val unit = min(size.width, size.height)
    val cx = size.width / 2f
    val ground = size.height * 0.94f

    if (stage == Stage.EGG) { drawEgg(cx, ground, unit * 0.2f, wobble); return }

    val k = stageK(stage)
    val u = unit * 0.2f * mix(0.62f, 1f, k)
    val sick = mood == Mood.SICK
    val skin = if (sick) lerp(Pink, SickTint, 0.45f) else Pink
    val shade = if (sick) lerp(PinkShade, SickTint, 0.45f) else PinkShade

    val legLen = mix(0.25f, 0.85f, k) * u
    val bodyLen = mix(0.55f, 1.0f, k) * u
    val headW = mix(1.55f, 1.4f, k) * u
    val headH = mix(1.4f, 1.2f, k) * u
    val hover = u * mix(0.35f, 0.6f, k)
    val hy = ground - (headH * 0.42f + bodyLen + legLen) - hover + bob
    val neck = hy + headH * 0.4f
    val hip = neck + bodyLen

    // Schatten: kleiner, wenn Lumi höher schwebt
    val lift = (-bob / (u * 0.3f)).coerceIn(-1f, 1f)
    oval(Color(0x26000000), Offset(cx, ground), u * mix(1.0f, 0.9f, k) * (1f - 0.15f * lift), u * 0.18f)

    // Psi-Aura (erwachsen)
    if (stage == Stage.ADULT) {
        val top = hy - headH * 0.9f
        val bottom = hip + legLen + u * 0.1f
        drawOval(
            AuraBlue.copy(alpha = 0.45f), topLeft = Offset(cx - u * 1.5f, top),
            size = Size(u * 3f, bottom - top), style = Stroke(width = u * 0.06f),
        )
    }

    // Schweif mit Mondsichel
    val tl = mix(0.5f, 1.25f, k)
    val tip = Offset(cx + u * (0.2f + 1.1f * tl) + swing * u * 0.15f, hy - u * 0.2f * tl)
    strokePath(Path().apply {
        moveTo(cx + u * 0.15f, hip - u * 0.05f)
        cubicTo(cx + u * (0.9f * tl + 0.3f), hip + u * 0.35f, cx + u * 1.3f * tl + swing * u * 0.2f, hip - u * 0.2f, tip.x, tip.y)
    }, shade, u * mix(0.14f, 0.09f, k))
    val cr = u * mix(0.2f, 0.24f, k)
    drawPath(crescentPath(tip, cr, Offset(cr * 0.45f, -cr * 0.3f)), Gold)

    // Beine (schmal zulaufend, baumelnd)
    for (sgn in listOf(-1f, 1f)) {
        val hx = cx + sgn * u * 0.13f
        val fx = hx + sgn * u * (if (sgn < 0) 0f else 0.1f) + sgn * swing * u * 0.03f
        val fy = hip + legLen
        val tw = u * mix(0.15f, 0.11f, k)
        val fw = u * mix(0.1f, 0.055f, k)
        drawPath(Path().apply {
            moveTo(hx - tw, hip - u * 0.3f)
            quadraticBezierTo(hx - tw * 1.1f, hip + legLen * 0.35f, fx - fw, fy)
            lineTo(fx + fw, fy)
            quadraticBezierTo(hx + tw * 1.1f, hip + legLen * 0.35f, hx + tw, hip - u * 0.3f)
            close()
        }, skin)
        oval(shade, Offset(fx + sgn * u * 0.03f, fy), u * mix(0.28f, 0.2f, k), u * 0.12f)
    }

    // Körper (Tropfenform) mit hellem Bauch
    val bw = mix(0.5f, 0.38f, k) * u
    val hw = mix(0.55f, 0.44f, k) * u
    drawPath(Path().apply {
        moveTo(cx - bw * 0.6f, neck)
        cubicTo(cx - bw * 1.2f, neck + bodyLen * 0.4f, cx - hw * 1.1f, hip, cx, hip + u * 0.05f)
        cubicTo(cx + hw * 1.1f, hip, cx + bw * 1.2f, neck + bodyLen * 0.4f, cx + bw * 0.6f, neck)
        close()
    }, skin)
    oval(PinkLight, Offset(cx, neck + bodyLen * 0.6f), bw, bodyLen * 0.55f)

    // Ohren mit Pinselspitzen
    for (sgn in listOf(-1f, 1f)) {
        val ear = Path().apply {
            moveTo(cx + sgn * headW * 0.42f, hy - headH * 0.18f)
            quadraticBezierTo(cx + sgn * headW * 0.62f, hy - headH * 0.6f, cx + sgn * headW * 0.66f, hy - headH * 0.95f)
            quadraticBezierTo(cx + sgn * headW * 0.35f, hy - headH * 0.72f, cx + sgn * headW * 0.12f, hy - headH * 0.45f)
            close()
        }
        drawPath(ear, skin)
        strokePath(ear, skin, u * 0.06f)
        drawPath(Path().apply {
            moveTo(cx + sgn * headW * 0.42f, hy - headH * 0.3f)
            quadraticBezierTo(cx + sgn * headW * 0.55f, hy - headH * 0.6f, cx + sgn * headW * 0.6f, hy - headH * 0.82f)
            quadraticBezierTo(cx + sgn * headW * 0.36f, hy - headH * 0.66f, cx + sgn * headW * 0.22f, hy - headH * 0.45f)
            close()
        }, PinkLight)
        if (k > 0f) strokePath(Path().apply {
            moveTo(cx + sgn * headW * 0.66f, hy - headH * 0.95f)
            quadraticBezierTo(cx + sgn * headW * 0.72f, hy - headH * 1.1f, cx + sgn * headW * 0.82f, hy - headH * 1.12f)
        }, Lavender, u * 0.05f)
    }

    // Kopf mit seitlichen Fellspitzen
    oval(skin, Offset(cx, hy), headW, headH)
    for (sgn in listOf(-1f, 1f)) {
        drawPath(Path().apply {
            moveTo(cx + sgn * headW * 0.44f, hy - headH * 0.05f)
            lineTo(cx + sgn * headW * 0.66f, hy + headH * 0.2f)
            lineTo(cx + sgn * headW * 0.42f, hy + headH * 0.22f)
            lineTo(cx + sgn * headW * 0.58f, hy + headH * 0.36f)
            lineTo(cx + sgn * headW * 0.3f, hy + headH * 0.4f)
            close()
        }, skin)
    }
    // Mondsichel auf der Stirn
    val mr = headH * 0.13f
    drawPath(crescentPath(Offset(cx, hy - headH * 0.3f), mr, Offset(mr * 0.46f, -mr * 0.38f)), Gold)

    // Mandelaugen mit Lidstrich
    val ey = hy + headH * 0.04f
    val ew = headW * 0.24f
    val eh = headH * mix(0.3f, 0.24f, k)
    val closed = mood == Mood.SLEEPING || blink > 0.85f
    val openness = (if (sick || mood == Mood.TIRED) 0.55f else 1f) * (1f - blink)
    for (sgn in listOf(-1f, 1f)) {
        val ex = cx + sgn * headW * 0.22f
        if (closed) {
            strokePath(Path().apply {
                moveTo(ex - ew / 2, ey); quadraticBezierTo(ex, ey + eh * 0.5f, ex + ew / 2, ey)
            }, Line, u * 0.05f)
        } else {
            val h = eh * openness.coerceAtLeast(0.2f)
            val eye = Path().apply {
                moveTo(ex - ew / 2, ey)
                quadraticBezierTo(ex, ey - h * 1.15f, ex + ew / 2, ey)
                quadraticBezierTo(ex, ey + h, ex - ew / 2, ey)
                close()
            }
            drawPath(eye, Color.White)
            clipPath(eye) {
                val ic = Offset(ex + sgn * ew * 0.04f, ey + eh * 0.05f)
                drawCircle(Violet, eh * 0.62f, ic)
                drawCircle(Color(0xFF2A1B5C), eh * 0.28f, ic)
                drawCircle(Color.White, eh * 0.17f, Offset(ic.x + eh * 0.2f, ic.y - eh * 0.18f))
            }
            strokePath(Path().apply {
                moveTo(ex - sgn * ew * 0.5f, ey + h * 0.05f)
                quadraticBezierTo(ex, ey - h * 1.15f, ex + sgn * ew * 0.5f, ey)
                lineTo(ex + sgn * ew * 0.68f, ey - h * 0.28f)
            }, Line, u * 0.045f)
        }
        if (mood == Mood.SAD || sick) {
            // innen höher als außen = traurig
            drawLine(
                Line, Offset(ex - sgn * ew * 0.45f, ey - eh * 1.25f), Offset(ex + sgn * ew * 0.4f, ey - eh * 1.0f),
                strokeWidth = u * 0.04f, cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(Color(0x59FF6FAE), headH * 0.08f, Offset(cx - headW * 0.36f, ey + headH * 0.22f))
    drawCircle(Color(0x59FF6FAE), headH * 0.08f, Offset(cx + headW * 0.36f, ey + headH * 0.22f))

    // Mund
    val my = hy + headH * 0.3f
    val mw = headH * 0.07f
    val mouthW = u * 0.04f
    when (mood) {
        Mood.HUNGRY -> oval(Line, Offset(cx, my + mw * 0.4f), mw * 1.1f, mw * 1.3f)
        Mood.SAD, Mood.SICK, Mood.DIRTY -> strokePath(Path().apply {
            moveTo(cx - mw, my + mw * 0.6f); quadraticBezierTo(cx, my - mw * 0.2f, cx + mw, my + mw * 0.6f)
        }, Line, mouthW)
        Mood.TIRED, Mood.SLEEPING -> drawLine(Line, Offset(cx - mw * 0.6f, my + mw * 0.3f), Offset(cx + mw * 0.6f, my + mw * 0.3f), strokeWidth = mouthW, cap = StrokeCap.Round)
        else -> {
            val big = if (mood == Mood.HAPPY) 1.3f else 1f
            strokePath(Path().apply {
                moveTo(cx - mw * big, my)
                quadraticBezierTo(cx - mw * big / 2, my + mw * 0.9f * big, cx, my)
                quadraticBezierTo(cx + mw * big / 2, my + mw * 0.9f * big, cx + mw * big, my)
            }, Line, mouthW)
        }
    }

    // Fellkragen
    for (i in -2..2) {
        val a = kotlin.math.abs(i)
        drawCircle(PinkLight, u * mix(0.15f, 0.13f, k) * (1f - a * 0.12f), Offset(cx + i * bw * 0.48f, hy + headH * 0.47f + (2 - a) * u * 0.06f))
    }
    drawCircle(PinkLight, u * mix(0.11f, 0.09f, k), Offset(cx, hy + headH * 0.47f + u * 0.2f))

    // Ärmchen, Pfoten vor der Brust
    for (sgn in listOf(-1f, 1f)) {
        strokePath(Path().apply {
            moveTo(cx + sgn * bw * 0.95f, neck + bodyLen * 0.3f)
            quadraticBezierTo(cx + sgn * bw * 1.25f, neck + bodyLen * 0.6f, cx + sgn * bw * 0.28f, neck + bodyLen * 0.68f)
        }, shade, u * mix(0.15f, 0.1f, k))
        drawCircle(shade, u * mix(0.09f, 0.065f, k), Offset(cx + sgn * bw * 0.25f, neck + bodyLen * 0.68f))
    }

    // Schweißtropfen bei Krankheit
    if (sick) {
        val d = Offset(cx + headW * 0.5f, hy - headH * 0.2f)
        drawPath(Path().apply {
            moveTo(d.x, d.y - u * 0.17f)
            quadraticBezierTo(d.x + u * 0.11f, d.y, d.x, d.y + u * 0.07f)
            quadraticBezierTo(d.x - u * 0.11f, d.y, d.x, d.y - u * 0.17f)
        }, Color(0xFF7FC8F8))
    }

    // Psi-Kugel (ab Jugend)
    if (k >= 0.8f) {
        val orb = Offset(cx - u * 1.3f, neck + u * 0.2f + bob * 0.8f)
        drawCircle(AuraBlue.copy(alpha = 0.3f), u * 0.22f, orb)
        drawCircle(AuraBlue, u * 0.13f, orb)
        drawCircle(Color.White, u * 0.05f, Offset(orb.x + u * 0.04f, orb.y - u * 0.04f))
    }
}

private fun DrawScope.drawEgg(cx: Float, ground: Float, r: Float, wobble: Float) {
    oval(Color(0x33000000), Offset(cx, ground), r * 1.6f, r * 0.3f)
    val c = Offset(cx, ground - r * 1.0f)
    rotate(wobble * 8f, pivot = Offset(cx, ground)) {
        val egg = Path().apply {
            moveTo(c.x, c.y - r * 1.05f)
            cubicTo(c.x + r * 0.85f, c.y - r * 1.05f, c.x + r * 0.85f, c.y + r * 0.95f, c.x, c.y + r * 0.95f)
            cubicTo(c.x - r * 0.85f, c.y + r * 0.95f, c.x - r * 0.85f, c.y - r * 1.05f, c.x, c.y - r * 1.05f)
            close()
        }
        drawPath(egg, Color(0xFFFFF4E0), style = Fill)
        drawPath(egg, Color(0xFFE8C9A8), style = Stroke(width = r * 0.05f))
        drawCircle(Pink, r * 0.16f, Offset(c.x - r * 0.3f, c.y + r * 0.3f))
        drawCircle(Pink, r * 0.11f, Offset(c.x + r * 0.35f, c.y + r * 0.05f))
        drawCircle(Pink, r * 0.08f, Offset(c.x + r * 0.1f, c.y + r * 0.6f))
        drawCircle(Gold, r * 0.22f, Offset(c.x, c.y - r * 0.45f))
        drawCircle(Color(0xFFFFF4E0), r * 0.19f, Offset(c.x + r * 0.1f, c.y - r * 0.52f))
    }
}

/** Ein Häufchen (wird mit „Putzen“ entfernt). */
fun DrawScope.drawPoop(c: Offset, s: Float) {
    val brown = Color(0xFF8A5A3B)
    oval(Color(0x22000000), Offset(c.x, c.y + s * 0.05f), s * 1.3f, s * 0.25f)
    oval(brown, Offset(c.x, c.y - s * 0.15f), s * 1.2f, s * 0.42f)
    oval(brown, Offset(c.x, c.y - s * 0.45f), s * 0.85f, s * 0.36f)
    oval(brown, Offset(c.x, c.y - s * 0.72f), s * 0.5f, s * 0.3f)
    drawCircle(Color(0x55FFFFFF), s * 0.08f, Offset(c.x - s * 0.2f, c.y - s * 0.5f))
}
