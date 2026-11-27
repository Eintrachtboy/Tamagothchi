package app.psykit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Feier beim Schlüpfen (Befund 28): Konfetti fällt über die ganze Szene, dazu „Es ist da!“.
 * Läuft einmal (PetState.hatchSeen), auch wenn das Ei geschlüpft ist, während die App zu war.
 * Ein Tipp beendet sie früher.
 */
@Composable
fun HatchCelebration(name: String, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    val pieces = remember {
        List(60) { Triple(Random.nextFloat(), Random.nextFloat() * 0.4f, Random.nextInt(5)) }
    }
    val colors = listOf(Color(0xFFFF8FB5), Color(0xFFFFD36E), Color(0xFFB79BFF), Color(0xFF7BC4F0), Color(0xFF7ED68B))
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(2800, easing = LinearEasing))
        onDone()
    }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            pieces.forEachIndexed { i, (fx, delay, c) ->
                val p = ((t.value - delay) / (1f - delay)).coerceIn(0f, 1f)
                if (p <= 0f || p >= 1f) return@forEachIndexed
                val x = fx * size.width + sin((p * 6f + i)) * size.width * 0.03f
                val y = -size.height * 0.05f + p * size.height * 1.1f
                drawRect(colors[c].copy(alpha = 1f - p * 0.4f), Offset(x, y), Size(size.width * 0.018f, size.width * 0.03f))
            }
        }
        Text(
            "🎉 $name ist da!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5B3FA6),
            fontSize = 30.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { val s = 0.7f + 0.3f * kotlin.math.min(1f, t.value * 6f); scaleX = s; scaleY = s; alpha = if (t.value > 0.85f) (1f - t.value) / 0.15f else 1f },
        )
    }
}
