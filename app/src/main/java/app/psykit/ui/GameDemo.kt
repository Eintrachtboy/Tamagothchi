package app.psykit.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.model.GameKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Befund 34: „Zeig's mir“ – kleine Endlosschleife, die ohne Worte zeigt, was man in dem Spiel tut. */
@Composable
internal fun GameDemo(kind: GameKind, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "demo").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    BoxWithConstraints(modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE9F7FF))) {
        Canvas(Modifier.fillMaxSize()) { drawDemo(kind, t) }
        val hand = demoHand(kind, t)
        if (hand != null) Text(
            "👆", fontSize = 30.sp,
            modifier = Modifier.offset(maxWidth * hand.first - 14.dp, maxHeight * hand.second - 8.dp),
        )
    }
}

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun tri(t: Float) = 1f - abs(2f * t - 1f)

private fun DrawScope.dot(fx: Float, fy: Float, r: Float, c: Color) = drawCircle(c, r * size.height, Offset(size.width * fx, size.height * fy))
private fun DrawScope.card(fx: Float, fy: Float, fw: Float, fh: Float, c: Color) = drawRoundRect(
    c, Offset(size.width * (fx - fw / 2), size.height * (fy - fh / 2)), Size(size.width * fw, size.height * fh), CornerRadius(12f, 12f),
)
private fun DrawScope.cloud(fx: Float, fy: Float) {
    dot(fx - 0.04f * 0.5f, fy, 0.15f, Color.White); dot(fx + 0.04f, fy + 0.02f, 0.13f, Color.White); dot(fx, fy - 0.07f, 0.12f, Color(0xFFF2F8FF))
}

private val PET = Color(0xFFFFD66B)
private val PURPLE = Color(0xFF9B6BE8)

private fun DrawScope.drawDemo(kind: GameKind, t: Float) {
    when (kind) {
        GameKind.HIDE_SEEK -> {
            val s = seg(t, 0.2f, 0.6f)
            val arc = sin(PI.toFloat() * s) * 0.2f
            if (t < 0.2f) dot(0.25f, 0.42f, 0.1f, PET)
            cloud(mix(0.25f, 0.75f, s), 0.6f - arc); cloud(0.5f, 0.6f); cloud(mix(0.75f, 0.25f, s), 0.6f + arc)
            if (t > 0.85f) dot(0.75f, 0.42f, 0.1f, PET)
        }
        GameKind.MEMORY -> {
            val open0 = t > 0.15f && t < 0.9f; val open2 = t > 0.35f && t < 0.9f
            for (i in 0..3) {
                val open = (i == 0 && open0) || (i == 2 && open2)
                card(0.2f + i * 0.2f, 0.5f, 0.15f, 0.6f, if (open) Color.White else PURPLE)
                if (open) dot(0.2f + i * 0.2f, 0.5f, 0.12f, Color(0xFFFF8A4C))
            }
        }
        GameKind.BUBBLES -> {
            val pops = floatArrayOf(0.2f, 0.45f, 0.7f)
            val xs = floatArrayOf(0.25f, 0.5f, 0.75f)
            for (i in 0..2) {
                val p = seg(t, pops[i], pops[i] + 0.1f)
                val y = 0.75f - 0.35f * seg(t, 0f, pops[i]) * 0.6f
                if (p < 1f) dot(xs[i], y, 0.16f * (1f - p * 0.6f), Color(0x8878C8FF))
                else if (t < pops[i] + 0.25f) dot(xs[i], y, 0.06f, Color(0xFFFFE07A))
            }
        }
        GameKind.BERRIES -> {
            for (i in 0..2) {
                val y = ((t * 1.1f + i * 0.33f) % 1f)
                dot(0.2f + i * 0.3f, 0.1f + y * 0.7f, 0.06f, if (i == 1) Color(0xFFFFC21A) else Color(0xFF4A63D8))
            }
            card(mix(0.2f, 0.8f, tri(t)), 0.88f, 0.2f, 0.16f, PET)
        }
        GameKind.REACTION -> {
            val on = t in 0.4f..0.7f
            dot(0.5f, 0.5f, 0.3f, if (on) PURPLE else Color(0xFFCFD8E3))
        }
        GameKind.ECHO -> {
            val cols = listOf(Color(0xFFE5484D), Color(0xFF3E63DD), Color(0xFF30A46C), Color(0xFFF5C400))
            val order = intArrayOf(0, 2, 1)
            val lit = when {
                t < 0.45f -> order.getOrNull((t / 0.15f).toInt())
                t in 0.5f..0.95f -> order.getOrNull(((t - 0.5f) / 0.15f).toInt())
                else -> null
            }
            for (i in 0..3) card(0.2f + i * 0.2f, 0.5f, 0.15f, 0.55f, cols[i].copy(alpha = if (i == lit) 1f else 0.35f))
        }
        GameKind.SHADOW -> {
            dot(0.5f, 0.35f, 0.22f, if (t > 0.55f) PET else Color(0xFF3A3F4B))
            for (i in 0..2) card(0.25f + i * 0.25f, 0.85f, 0.2f, 0.18f, if (i == 1 && t > 0.55f) Color(0xFF30A46C) else Color(0xFFCFD8E3))
        }
        GameKind.COOK -> {
            val cols = listOf(Color(0xFFE5484D), Color(0xFFF5C400), Color(0xFF30A46C))
            for (i in 0..2) if (t < 0.4f || t > 0.5f + i * 0.15f) dot(0.25f + i * 0.25f, 0.25f, 0.1f, cols[i])
            for (i in 0..2) if (t > 0.5f + i * 0.15f) dot(0.4f + i * 0.1f, 0.78f, 0.07f, cols[i])
            card(0.5f, 0.85f, 0.45f, 0.16f, Color(0xFF8B6B4A))
        }
        GameKind.CLOUDS -> {
            for (i in 0..2) cloud(0.2f + i * 0.3f, 0.78f - i * 0.25f)
            val p = t * 2f
            val step = p.toInt().coerceIn(0, 1)
            val f = p - step
            val x = mix(0.2f + step * 0.3f, 0.5f + step * 0.3f, f)
            val y = mix(0.62f - step * 0.25f, 0.37f - step * 0.25f, f) - sin(PI.toFloat() * f) * 0.12f
            dot(x, y, 0.09f, PET)
        }
        GameKind.VISIT -> {
            dot(0.2f, 0.6f, 0.14f, PET); dot(0.8f, 0.6f, 0.14f, Color(0xFFFFA8C8))
            val f = tri(t * 2f % 1f)
            dot(mix(0.28f, 0.72f, if (t < 0.5f) f.coerceAtMost(1f) else 1f - f), 0.55f - sin(PI.toFloat() * (t * 2f % 1f)) * 0.3f, 0.05f, Color(0xFFE5484D))
        }
    }
}

/** Position der Hand (Bruchteile der Fläche) oder null, wenn gerade keine Hand gebraucht wird. */
private fun demoHand(kind: GameKind, t: Float): Pair<Float, Float>? = when (kind) {
    GameKind.HIDE_SEEK -> if (t > 0.65f) Pair(mix(0.5f, 0.75f, seg(t, 0.65f, 0.8f)), 0.75f) else null
    GameKind.MEMORY -> when { t in 0.1f..0.25f -> Pair(0.2f, 0.65f); t in 0.3f..0.45f -> Pair(0.6f, 0.65f); else -> null }
    GameKind.BUBBLES -> when { t in 0.12f..0.3f -> Pair(0.25f, 0.6f); t in 0.37f..0.55f -> Pair(0.5f, 0.6f); t in 0.62f..0.8f -> Pair(0.75f, 0.6f); else -> null }
    GameKind.BERRIES -> Pair(mix(0.2f, 0.8f, tri(t)), 0.8f)
    GameKind.REACTION -> if (t in 0.45f..0.65f) Pair(0.5f, 0.6f) else null
    GameKind.ECHO -> if (t >= 0.5f) Pair(0.2f + ((((t - 0.5f) / 0.15f).toInt().coerceIn(0, 2)).let { intArrayOf(0, 2, 1)[it] }) * 0.2f, 0.65f) else null
    GameKind.SHADOW -> if (t in 0.4f..0.6f) Pair(0.5f, 0.88f) else null
    GameKind.COOK -> if (t >= 0.45f && t < 0.95f) Pair(0.25f + ((t - 0.45f) / 0.15f).toInt().coerceIn(0, 2) * 0.25f, 0.35f) else null
    GameKind.CLOUDS -> if ((t * 2f) % 1f < 0.15f) Pair(0.2f + (t * 2f).toInt().coerceIn(0, 1) * 0.3f + 0.3f, 0.7f - (t * 2f).toInt().coerceIn(0, 1) * 0.25f) else null
    GameKind.VISIT -> null
}
