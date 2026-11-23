package app.psykit.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import app.psykit.core.model.Mood
import app.psykit.core.model.Stage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Die eingebaute Figur „Psykit“: eine kleine rosa Psycho-Katze mit Mondsichel auf der Stirn,
 * Haarlocke, Sternschweif und schwebender Psi-Kugel. Komplett mit Formen gezeichnet,
 * damit keine fremden Grafiken nötig sind. Eigene Bilder ersetzen sie (siehe SpriteStore).
 */

private val Pink = Color(0xFFF7A8D8)
private val PinkShade = Color(0xFFE88CC6)
private val PinkLight = Color(0xFFFFE3F2)
private val Violet = Color(0xFF5B3FA8)
private val Gold = Color(0xFFFFD36E)
private val AuraBlue = Color(0xFF9BE7FF)
private val Line = Color(0xFF9C4A7E)
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

private fun stageScale(stage: Stage) = when (stage) {
    Stage.EGG, Stage.BABY -> 0.6f
    Stage.CHILD -> 0.75f
    Stage.TEEN -> 0.88f
    Stage.ADULT -> 1f
}

/**
 * @param blink 0 = Augen offen, 1 = zu
 * @param bob vertikale Wipp-Verschiebung in px
 * @param swing Schweif-Pendel −1..1
 */
fun DrawScope.drawPsykit(stage: Stage, mood: Mood, blink: Float, bob: Float, swing: Float, wobble: Float) {
    val unit = min(size.width, size.height)
    val cx = size.width / 2f
    val ground = size.height * 0.92f

    if (stage == Stage.EGG) { drawEgg(cx, ground, unit * 0.2f, wobble); return }

    val r = unit * 0.2f * stageScale(stage)
    val baby = stage == Stage.BABY
    val sick = mood == Mood.SICK
    val skin = if (sick) lerp(Pink, SickTint, 0.45f) else Pink
    val shade = if (sick) lerp(PinkShade, SickTint, 0.45f) else PinkShade

    // Schatten
    oval(Color(0x33000000), Offset(cx, ground), r * 1.9f, r * 0.32f)

    val by = ground - r * 0.8f + bob
    val hr = if (baby) r * 1.0f else r * 0.85f
    val hy = by - r * (if (baby) 0.95f else 1.1f)

    // Psi-Aura (erwachsen)
    if (stage == Stage.ADULT) {
        drawOval(
            AuraBlue.copy(alpha = 0.35f), topLeft = Offset(cx - r * 1.9f, hy - hr * 1.5f),
            size = Size(r * 3.8f, (by + r * 0.9f) - (hy - hr * 1.5f)), style = Stroke(width = r * 0.08f),
        )
    }

    // Schweif mit Stern
    val tailLen = when (stage) { Stage.BABY -> 0.45f; Stage.CHILD -> 0.75f; Stage.TEEN -> 1.0f; else -> 1.2f }
    val start = Offset(cx + r * 0.7f, by + r * 0.35f)
    val end = Offset(cx + r * (0.9f + 0.9f * tailLen) + swing * r * 0.25f, by - r * 1.2f * tailLen)
    val tail = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(
            cx + r * (1.3f + tailLen), by + r * 0.4f,
            cx + r * (1.0f + tailLen) + swing * r * 0.3f, by - r * 0.3f * tailLen,
            end.x, end.y,
        )
    }
    drawPath(tail, shade, style = Stroke(width = r * 0.17f, cap = StrokeCap.Round))
    drawPath(starPath(end, r * 0.24f, rotation = -90f + swing * 15f), Gold)

    // Körper
    oval(skin, Offset(cx, by), r * 1.7f, r * 1.5f)
    oval(PinkLight, Offset(cx, by + r * 0.15f), r * 1.0f, r * 0.95f)
    // Füße
    oval(shade, Offset(cx - r * 0.45f, by + r * 0.68f), r * 0.6f, r * 0.32f)
    oval(shade, Offset(cx + r * 0.45f, by + r * 0.68f), r * 0.6f, r * 0.32f)
    // Ärmchen
    oval(shade, Offset(cx - r * 0.78f, by - r * 0.05f), r * 0.32f, r * 0.5f)
    oval(shade, Offset(cx + r * 0.78f, by - r * 0.05f), r * 0.32f, r * 0.5f)

    // Ohren (rund gezeichnet)
    fun ear(sign: Float) {
        val outer = Path().apply {
            moveTo(cx + sign * hr * 0.88f, hy - hr * 0.15f)
            lineTo(cx + sign * hr * 0.78f, hy - hr * 1.18f)
            lineTo(cx + sign * hr * 0.18f, hy - hr * 0.78f)
            close()
        }
        drawPath(outer, skin)
        drawPath(outer, skin, style = Stroke(width = hr * 0.14f, join = StrokeJoin.Round))
        val inner = Path().apply {
            moveTo(cx + sign * hr * 0.76f, hy - hr * 0.38f)
            lineTo(cx + sign * hr * 0.72f, hy - hr * 0.98f)
            lineTo(cx + sign * hr * 0.36f, hy - hr * 0.74f)
            close()
        }
        drawPath(inner, PinkLight)
    }
    ear(-1f); ear(1f)

    // Kopf
    drawCircle(skin, hr, Offset(cx, hy))
    // Haarlocke
    drawCircle(skin, hr * 0.2f, Offset(cx - hr * 0.12f, hy - hr * 0.93f))
    drawCircle(skin, hr * 0.16f, Offset(cx + hr * 0.14f, hy - hr * 0.97f))
    drawCircle(skin, hr * 0.12f, Offset(cx + hr * 0.3f, hy - hr * 1.07f))
    // Mondsichel auf der Stirn
    drawCircle(Gold, hr * 0.17f, Offset(cx, hy - hr * 0.52f))
    drawCircle(skin, hr * 0.15f, Offset(cx + hr * 0.08f, hy - hr * 0.58f))

    // Augen
    val eyeY = hy + hr * 0.08f
    val eyeW = hr * 0.32f
    val eyeH = hr * 0.42f
    val closed = mood == Mood.SLEEPING || blink > 0.85f
    val openness = when (mood) { Mood.SICK, Mood.TIRED -> 0.5f; else -> 1f } * (1f - blink)
    for (sign in listOf(-1f, 1f)) {
        val ec = Offset(cx + sign * hr * 0.38f, eyeY)
        if (closed) {
            val p = Path().apply {
                moveTo(ec.x - eyeW / 2, ec.y)
                quadraticBezierTo(ec.x, ec.y + eyeH * 0.35f, ec.x + eyeW / 2, ec.y)
            }
            drawPath(p, Line, style = Stroke(width = hr * 0.06f, cap = StrokeCap.Round))
        } else {
            val h = eyeH * openness.coerceAtLeast(0.15f)
            oval(Violet, ec, eyeW, h)
            drawCircle(Color.White, eyeW * 0.2f, Offset(ec.x + eyeW * 0.12f, ec.y - h * 0.22f))
            drawCircle(Color.White.copy(alpha = 0.7f), eyeW * 0.09f, Offset(ec.x - eyeW * 0.15f, ec.y + h * 0.2f))
        }
        if (mood == Mood.SAD || mood == Mood.SICK) {
            drawLine(
                // innen höher als außen = traurig
                Line, Offset(ec.x - sign * eyeW * 0.6f, eyeY - eyeH * 0.9f),
                Offset(ec.x + sign * eyeW * 0.5f, eyeY - eyeH * 0.65f), strokeWidth = hr * 0.05f, cap = StrokeCap.Round,
            )
        }
    }

    // Bäckchen
    drawCircle(Color(0x66FF6FAE), hr * 0.12f, Offset(cx - hr * 0.64f, hy + hr * 0.38f))
    drawCircle(Color(0x66FF6FAE), hr * 0.12f, Offset(cx + hr * 0.64f, hy + hr * 0.38f))

    // Mund
    val my = hy + hr * 0.42f
    val mw = hr * 0.14f
    val stroke = Stroke(width = hr * 0.05f, cap = StrokeCap.Round)
    when (mood) {
        Mood.HUNGRY -> oval(Line, Offset(cx, my + mw * 0.3f), mw * 0.9f, mw * 1.1f)
        Mood.SAD, Mood.SICK, Mood.DIRTY -> drawPath(Path().apply {
            moveTo(cx - mw, my + mw * 0.6f); quadraticBezierTo(cx, my - mw * 0.2f, cx + mw, my + mw * 0.6f)
        }, Line, style = stroke)
        Mood.TIRED, Mood.SLEEPING -> drawLine(Line, Offset(cx - mw * 0.6f, my + mw * 0.3f), Offset(cx + mw * 0.6f, my + mw * 0.3f), strokeWidth = hr * 0.05f, cap = StrokeCap.Round)
        else -> {
            val big = if (mood == Mood.HAPPY) 1.3f else 1f
            drawPath(Path().apply {
                moveTo(cx - mw * big, my)
                quadraticBezierTo(cx - mw * big / 2, my + mw * 0.9f * big, cx, my)
                quadraticBezierTo(cx + mw * big / 2, my + mw * 0.9f * big, cx + mw * big, my)
            }, Line, style = stroke)
        }
    }

    // Schweißtropfen bei Krankheit
    if (sick) {
        val d = Offset(cx + hr * 0.85f, hy - hr * 0.2f)
        drawPath(Path().apply {
            moveTo(d.x, d.y - hr * 0.2f)
            quadraticBezierTo(d.x + hr * 0.13f, d.y, d.x, d.y + hr * 0.08f)
            quadraticBezierTo(d.x - hr * 0.13f, d.y, d.x, d.y - hr * 0.2f)
        }, Color(0xFF7FC8F8))
    }

    // Psi-Kugel (ab Jugend)
    if (stage == Stage.TEEN || stage == Stage.ADULT) {
        val orb = Offset(cx - r * 1.55f, hy + hr * 0.3f - bob * 1.5f)
        drawCircle(AuraBlue.copy(alpha = 0.3f), r * 0.26f, orb)
        drawCircle(AuraBlue, r * 0.16f, orb)
        drawCircle(Color.White, r * 0.06f, Offset(orb.x + r * 0.05f, orb.y - r * 0.05f))
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
