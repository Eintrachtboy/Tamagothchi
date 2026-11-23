package app.psykit.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import app.psykit.core.model.Mood
import app.psykit.core.model.Stage
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * Die eingebaute Figur „Lumi“: ein schwebendes Fantasiewesen mit herzförmigem Kopf,
 * langen Blattohren, Bernsteinaugen mit türkisem Ring, leuchtenden Punkten auf Stirn
 * und Brust und einem Schwanz mit geteilter Flossenspitze.
 * Komplett mit Formen gezeichnet. Eigene Bilder ersetzen sie (siehe SpriteStore).
 */

private val Apricot = Color(0xFFF9C4B4)
private val ApricotShade = Color(0xFFEDA593)
private val Cream = Color(0xFFFFF1E6)
private val Lavender = Color(0xFFCDB6F2)
private val LavenderShade = Color(0xFFB49BE3)
private val Pearl = Color(0xFFF3EAF8)
private val Line = Color(0xFF8A4E5E)
private val Amber = Color(0xFFF2A93B)
private val AmberDark = Color(0xFFB86A12)
private val AmberLight = Color(0xFFFFD27A)
private val Turquoise = Color(0xFF3FD1C8)
private val Glow = Color(0xFFFFF7C2)
private val SickTint = Color(0xFFC8D9A4)

private fun DrawScope.oval(color: Color, center: Offset, w: Float, h: Float) =
    drawOval(color, topLeft = Offset(center.x - w / 2, center.y - h / 2), size = Size(w, h))

private fun DrawScope.strokePath(p: Path, color: Color, width: Float) =
    drawPath(p, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

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

/** Blattform von [from] nach [to] mit der Breite [width] in der Mitte. */
private fun leafPath(from: Offset, to: Offset, width: Float): Path {
    val dx = to.x - from.x; val dy = to.y - from.y
    val len = hypot(dx, dy).coerceAtLeast(0.001f)
    val nx = -dy / len; val ny = dx / len
    val mx = (from.x + to.x) / 2; val my = (from.y + to.y) / 2
    return Path().apply {
        moveTo(from.x, from.y)
        quadraticBezierTo(mx + nx * width, my + ny * width, to.x, to.y)
        quadraticBezierTo(mx - nx * width, my - ny * width, from.x, from.y)
        close()
    }
}

private fun DrawScope.glowDot(c: Offset, r: Float) {
    drawCircle(Glow.copy(alpha = 0.45f), r * 2f, c)
    drawCircle(Glow, r, c)
}

/** 0 = Baby (klein, rund) … 1 = Erwachsen (größer, lange Ohren und Schweif). */
private fun stageK(stage: Stage) = when (stage) {
    Stage.EGG, Stage.BABY -> 0f
    Stage.CHILD -> 0.4f
    Stage.TEEN -> 0.8f
    Stage.ADULT -> 1f
}

/**
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
    val u = unit * 0.2f * mix(0.65f, 1f, k)
    val sick = mood == Mood.SICK
    val skin = if (sick) lerp(Apricot, SickTint, 0.45f) else Apricot
    val outline = if (sick) lerp(ApricotShade, SickTint, 0.45f) else ApricotShade
    val ow = u * 0.03f

    val headW = mix(1.6f, 1.45f, k) * u
    val headH = mix(1.35f, 1.2f, k) * u
    val bodyH = mix(0.75f, 1.0f, k) * u
    val bodyW = mix(0.95f, 0.85f, k) * u
    val legLen = mix(0.18f, 0.4f, k) * u
    val hover = u * mix(0.3f, 0.45f, k)
    val footY = ground - hover + bob
    val hip = footY - legLen
    val bodyC = hip - bodyH * 0.4f
    val neck = bodyC - bodyH * 0.5f
    val hy = neck - headH * 0.38f

    // Schatten (kleiner, wenn höher)
    val lift = (-bob / (u * 0.3f)).coerceIn(-1f, 1f)
    oval(Color(0x24000000), Offset(cx, ground), u * (1f - 0.15f * lift), u * 0.17f)

    // Schwanz: dick am Ansatz, dünn zur Spitze, Farbverlauf zu Lavendel
    val tl = mix(0.55f, 1.2f, k)
    val p0 = Offset(cx + bodyW * 0.35f, bodyC + bodyH * 0.25f)
    val c1 = Offset(cx + u * (0.9f + 0.8f * tl), bodyC + bodyH * 0.6f)
    val c2 = Offset(cx + u * (1.2f + 0.8f * tl) + swing * u * 0.2f, bodyC - u * 0.6f * tl)
    val p3 = Offset(cx + u * (0.75f + 0.75f * tl) + swing * u * 0.25f, bodyC - u * (0.4f + 0.9f * tl))
    val n = 24
    val pts = List(n + 1) { i ->
        val t = i / n.toFloat(); val a = (1 - t) * (1 - t) * (1 - t); val b = 3 * (1 - t) * (1 - t) * t
        val c = 3 * (1 - t) * t * t; val d = t * t * t
        Offset(a * p0.x + b * c1.x + c * c2.x + d * p3.x, a * p0.y + b * c1.y + c * c2.y + d * p3.y)
    }
    for (i in 0 until n) {
        val t = i / n.toFloat()
        val col = lerp(skin, Lavender, ((t - 0.55f) / 0.4f).coerceIn(0f, 1f))
        drawLine(col, pts[i], pts[i + 1], strokeWidth = u * mix(0.2f, 0.05f, t) * mix(1f, 0.85f, k), cap = StrokeCap.Round)
    }
    // geteilte Flossenspitze
    val e = pts[n]; val pe = pts[n - 3]
    val ang = atan2(e.y - pe.y, e.x - pe.x)
    val fl = u * mix(0.32f, 0.42f, k)
    for (sgn in listOf(-1f, 1f)) {
        val a = ang + sgn * 0.55f
        drawPath(leafPath(e, Offset(e.x + cos(a) * fl, e.y + sin(a) * fl), fl * 0.35f), Lavender)
    }

    // Beine: kurz, kräftig, leicht gebogen; runde Lavendel-Füße mit drei Zehen
    for (sgn in listOf(-1f, 1f)) {
        val hx = cx + sgn * bodyW * 0.28f
        strokePath(Path().apply {
            moveTo(hx, hip - u * 0.15f)
            quadraticBezierTo(hx + sgn * u * 0.12f, hip + legLen * 0.5f, hx + sgn * u * 0.04f, footY - u * 0.04f)
        }, skin, u * mix(0.26f, 0.22f, k))
        val fc = Offset(hx + sgn * u * 0.07f, footY)
        oval(Lavender, fc, u * mix(0.32f, 0.3f, k), u * 0.18f)
        for (d in -1..1) drawLine(LavenderShade, Offset(fc.x + d * u * 0.07f, fc.y + u * 0.02f), Offset(fc.x + d * u * 0.07f, fc.y + u * 0.08f), strokeWidth = u * 0.018f, cap = StrokeCap.Round)
    }

    // Körper (Tropfenform) mit cremefarbenem Bauch und Leuchtpunkten
    val body = Path().apply {
        moveTo(cx, neck)
        cubicTo(cx + bodyW * 0.45f, neck, cx + bodyW * 0.62f, bodyC + bodyH * 0.55f, cx, bodyC + bodyH * 0.55f)
        cubicTo(cx - bodyW * 0.62f, bodyC + bodyH * 0.55f, cx - bodyW * 0.45f, neck, cx, neck)
        close()
    }
    strokePath(body, outline, ow * 2)
    drawPath(body, skin)
    oval(Cream, Offset(cx, bodyC + bodyH * 0.12f), bodyW * 0.58f, bodyH * 0.62f)
    glowDot(Offset(cx, bodyC - u * 0.05f), u * 0.05f)
    glowDot(Offset(cx - u * 0.12f, bodyC + u * 0.1f), u * 0.032f)
    glowDot(Offset(cx + u * 0.13f, bodyC + u * 0.14f), u * 0.028f)

    // Ärmchen mit runden Händen
    for (sgn in listOf(-1f, 1f)) {
        val s0 = Offset(cx + sgn * bodyW * 0.33f, neck + bodyH * 0.22f)
        val hand = Offset(cx + sgn * bodyW * 0.62f, s0.y + bodyH * 0.2f)
        strokePath(Path().apply {
            moveTo(s0.x, s0.y); quadraticBezierTo(cx + sgn * bodyW * 0.55f, s0.y, hand.x, hand.y)
        }, skin, u * 0.13f)
        drawCircle(Lavender, u * 0.085f, hand)
    }

    // Blattohren, seitlich nach außen
    val el = u * mix(0.7f, 1.05f, k)
    for (sgn in listOf(-1f, 1f)) {
        val b = Offset(cx + sgn * headW * 0.36f, hy - headH * 0.18f)
        val tip = Offset(b.x + sgn * el, b.y - el * mix(0.05f, 0.25f, k))
        val ear = leafPath(b, tip, el * 0.32f)
        strokePath(ear, outline, ow * 2)
        drawPath(ear, skin)
        drawPath(leafPath(Offset(b.x + sgn * el * 0.08f, b.y), Offset(tip.x - sgn * el * 0.12f, tip.y + el * 0.02f), el * 0.17f), Pearl)
    }

    // herzförmiger Kopf
    val head = Path().apply {
        moveTo(cx, hy - headH * 0.36f)
        cubicTo(cx + headW * 0.18f, hy - headH * 0.58f, cx + headW * 0.55f, hy - headH * 0.5f, cx + headW * 0.52f, hy - headH * 0.02f)
        cubicTo(cx + headW * 0.5f, hy + headH * 0.38f, cx + headW * 0.22f, hy + headH * 0.52f, cx, hy + headH * 0.52f)
        cubicTo(cx - headW * 0.22f, hy + headH * 0.52f, cx - headW * 0.5f, hy + headH * 0.38f, cx - headW * 0.52f, hy - headH * 0.02f)
        cubicTo(cx - headW * 0.55f, hy - headH * 0.5f, cx - headW * 0.18f, hy - headH * 0.58f, cx, hy - headH * 0.36f)
        close()
    }
    strokePath(head, outline, ow * 2)
    drawPath(head, skin)
    glowDot(Offset(cx, hy - headH * 0.2f), u * 0.045f)
    glowDot(Offset(cx - headW * 0.1f, hy - headH * 0.27f), u * 0.025f)
    glowDot(Offset(cx + headW * 0.1f, hy - headH * 0.27f), u * 0.025f)

    // große Bernsteinaugen mit türkisem Ring
    val ey = hy + headH * 0.08f
    val ew = headW * 0.25f
    val eh = headH * 0.36f
    val closed = mood == Mood.SLEEPING || blink > 0.85f
    val openness = (if (sick || mood == Mood.TIRED) 0.55f else 1f) * (1f - blink)
    for (sgn in listOf(-1f, 1f)) {
        val ex = cx + sgn * headW * 0.24f
        if (closed) {
            strokePath(Path().apply {
                moveTo(ex - ew / 2, ey); quadraticBezierTo(ex, ey + eh * 0.4f, ex + ew / 2, ey)
            }, Line, u * 0.05f)
        } else {
            val h = eh * openness.coerceAtLeast(0.2f)
            val top = ey + eh / 2 - h // Lid senkt sich von oben
            val eyeRect = Rect(ex - ew * 0.475f, top, ex + ew * 0.475f, ey + eh * 0.475f)
            oval(Color(0xFF3A2230), Offset(ex, (top + ey + eh / 2) / 2), ew * 1.08f, (ey + eh / 2 - top) * 1.06f)
            val eye = Path().apply { addOval(eyeRect) }
            clipPath(eye) {
                drawRect(Brush.verticalGradient(listOf(AmberDark, Amber, AmberLight), startY = ey - eh / 2, endY = ey + eh / 2),
                    topLeft = Offset(ex - ew, ey - eh), size = Size(ew * 2, eh * 2))
                val pc = Offset(ex, ey + eh * 0.05f)
                drawOval(Turquoise, topLeft = Offset(pc.x - ew * 0.27f, pc.y - eh * 0.27f), size = Size(ew * 0.54f, eh * 0.54f), style = Stroke(width = ew * 0.09f))
                oval(Color(0xFF2A1520), pc, ew * 0.3f, eh * 0.32f)
                drawCircle(Color.White, ew * 0.17f, Offset(ex + ew * 0.15f, ey - eh * 0.2f))
                drawCircle(Color.White.copy(alpha = 0.75f), ew * 0.08f, Offset(ex - ew * 0.15f, ey + eh * 0.22f))
            }
        }
        if (mood == Mood.SAD || sick) {
            // innen höher als außen = traurig
            drawLine(Line, Offset(ex - sgn * ew * 0.45f, ey - eh * 0.85f), Offset(ex + sgn * ew * 0.4f, ey - eh * 0.65f),
                strokeWidth = u * 0.035f, cap = StrokeCap.Round)
        }
    }
    drawCircle(Color(0x40FF7878), headH * 0.07f, Offset(cx - headW * 0.36f, ey + headH * 0.2f))
    drawCircle(Color(0x40FF7878), headH * 0.07f, Offset(cx + headW * 0.36f, ey + headH * 0.2f))
    oval(ApricotShade, Offset(cx, hy + headH * 0.25f), u * 0.06f, u * 0.04f)

    // Mund
    val my = hy + headH * 0.31f
    val mw = headW * 0.06f
    val mouthW = u * 0.035f
    when (mood) {
        Mood.HUNGRY -> oval(Line, Offset(cx, my + mw * 0.3f), mw * 0.9f, mw * 1.1f)
        Mood.SAD, Mood.SICK, Mood.DIRTY -> strokePath(Path().apply {
            moveTo(cx - mw, my + mw * 0.5f); quadraticBezierTo(cx, my - mw * 0.3f, cx + mw, my + mw * 0.5f)
        }, Line, mouthW)
        Mood.TIRED, Mood.SLEEPING -> drawLine(Line, Offset(cx - mw * 0.5f, my + mw * 0.2f), Offset(cx + mw * 0.5f, my + mw * 0.2f), strokeWidth = mouthW, cap = StrokeCap.Round)
        else -> {
            val big = if (mood == Mood.HAPPY) 1.35f else 1f
            strokePath(Path().apply {
                moveTo(cx - mw * big, my); quadraticBezierTo(cx, my + mw * 0.9f * big, cx + mw * big, my)
            }, Line, mouthW)
        }
    }

    // Schweißtropfen bei Krankheit
    if (sick) {
        val d = Offset(cx + headW * 0.45f, hy - headH * 0.25f)
        drawPath(Path().apply {
            moveTo(d.x, d.y - u * 0.17f)
            quadraticBezierTo(d.x + u * 0.11f, d.y, d.x, d.y + u * 0.07f)
            quadraticBezierTo(d.x - u * 0.11f, d.y, d.x, d.y - u * 0.17f)
        }, Color(0xFF7FC8F8))
    }
}

private fun DrawScope.drawEgg(cx: Float, ground: Float, r: Float, wobble: Float) {
    oval(Color(0x24000000), Offset(cx, ground), r * 1.6f, r * 0.3f)
    val c = Offset(cx, ground - r * 1.0f)
    rotate(wobble * 8f, pivot = Offset(cx, ground)) {
        val egg = Path().apply {
            moveTo(c.x, c.y - r * 1.05f)
            cubicTo(c.x + r * 0.85f, c.y - r * 1.05f, c.x + r * 0.85f, c.y + r * 0.95f, c.x, c.y + r * 0.95f)
            cubicTo(c.x - r * 0.85f, c.y + r * 0.95f, c.x - r * 0.85f, c.y - r * 1.05f, c.x, c.y - r * 1.05f)
            close()
        }
        drawPath(egg, Brush.verticalGradient(listOf(Cream, Apricot, Lavender), startY = c.y - r, endY = c.y + r))
        strokePath(egg, ApricotShade, r * 0.04f)
        // Blattmuster und Leuchtpunkte
        drawPath(leafPath(Offset(c.x - r * 0.55f, c.y + r * 0.15f), Offset(c.x - r * 0.05f, c.y - r * 0.2f), r * 0.12f), Pearl)
        drawPath(leafPath(Offset(c.x + r * 0.55f, c.y + r * 0.15f), Offset(c.x + r * 0.05f, c.y - r * 0.2f), r * 0.12f), Pearl)
        glowDot(Offset(c.x, c.y - r * 0.5f), r * 0.07f)
        glowDot(Offset(c.x - r * 0.18f, c.y - r * 0.62f), r * 0.04f)
        glowDot(Offset(c.x + r * 0.18f, c.y - r * 0.62f), r * 0.04f)
        drawCircle(Color.White.copy(alpha = 0.5f), r * 0.12f, Offset(c.x - r * 0.35f, c.y - r * 0.55f))
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
