package app.psykit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.engine.DayPhase
import app.psykit.core.model.Mood
import app.psykit.core.model.Stage
import kotlin.random.Random

private fun skyColors(phase: DayPhase) = when (phase) {
    DayPhase.MORNING -> listOf(Color(0xFFFFD9C2), Color(0xFFFFF1E6))
    DayPhase.DAY -> listOf(Color(0xFFA9DDFB), Color(0xFFE9F7FF))
    DayPhase.EVENING -> listOf(Color(0xFFB98BD9), Color(0xFFFFC4B0))
    DayPhase.NIGHT -> listOf(Color(0xFF151A3D), Color(0xFF3B2A63))
}

@Composable
fun PetScene(
    stage: Stage,
    mood: Mood,
    sleeping: Boolean,
    poops: Int,
    phase: DayPhase,
    custom: ImageBitmap?,
    petPulse: Int,
    onTapPet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anim = rememberInfiniteTransition(label = "idle")
    val bob by anim.animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (sleeping) 2600 else 1400), RepeatMode.Reverse), label = "bob",
    )
    val swing by anim.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "swing")
    val blink by anim.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes<Float> {
            durationMillis = 4200
            0f at 0; 0f at 3900; 1f at 4000; 0f at 4150
        }),
        label = "blink",
    )
    val wobble by anim.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes<Float> {
            durationMillis = 2400
            0f at 0; -1f at 150; 1f at 300; -1f at 450; 0f at 600; 0f at 2400
        }),
        label = "wobble",
    )
    val zFloat by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "z")

    val hearts = remember { Animatable(1f) }
    LaunchedEffect(petPulse) {
        if (petPulse > 0) { hearts.snapTo(0f); hearts.animateTo(1f, tween(1300)) }
    }
    val stars = remember { List(28) { Offset(Random.nextFloat(), Random.nextFloat() * 0.6f) } }
    val night = phase == DayPhase.NIGHT
    val lightsOff = sleeping && night

    Box(
        modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(skyColors(phase)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTapPet),
    ) {
        // Hintergrund: Himmel, Sonne/Mond, Sterne, Boden
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            if (night) {
                stars.forEachIndexed { i, s ->
                    drawCircle(Color.White.copy(alpha = if (i % 3 == 0) 0.9f else 0.5f), if (i % 4 == 0) 3f else 1.8f, Offset(s.x * w, s.y * h))
                }
                drawCircle(Color(0xFFFFF2B8), h * 0.07f, Offset(w * 0.82f, h * 0.16f))
                drawCircle(skyColors(phase)[0], h * 0.062f, Offset(w * 0.85f, h * 0.14f))
            } else {
                val sunColor = if (phase == DayPhase.EVENING) Color(0xFFFF9E7A) else Color(0xFFFFE07A)
                drawCircle(sunColor.copy(alpha = 0.35f), h * 0.11f, Offset(w * 0.83f, h * 0.17f))
                drawCircle(sunColor, h * 0.075f, Offset(w * 0.83f, h * 0.17f))
                val cloud = Color.White.copy(alpha = 0.8f)
                drawCircle(cloud, h * 0.05f, Offset(w * 0.18f, h * 0.18f))
                drawCircle(cloud, h * 0.065f, Offset(w * 0.25f, h * 0.15f))
                drawCircle(cloud, h * 0.045f, Offset(w * 0.32f, h * 0.19f))
            }
            val groundColor = if (night) Color(0xFF2A2452) else Color(0xFFCDEBC0)
            drawOval(groundColor, topLeft = Offset(-w * 0.2f, h * 0.8f), size = androidx.compose.ui.geometry.Size(w * 1.4f, h * 0.5f))
            if (custom != null) {
                // Schatten unter eigenen Bildern, wird kleiner, wenn die Figur hochschwebt
                val sw = w * 0.3f * (1f - 0.15f * bob)
                drawOval(Color(0x26000000), topLeft = Offset(w / 2 - sw / 2, h * 0.935f), size = androidx.compose.ui.geometry.Size(sw, h * 0.045f))
            }
            for (i in 0 until poops) drawPoop(Offset(w * (0.14f + i * 0.11f), h * 0.93f), h * 0.07f)
        }

        // Figur: eigenes Bild oder gezeichnete Standardfigur
        if (custom != null) {
            Image(
                bitmap = custom,
                contentDescription = "Dein Tierchen",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 60.dp, end = 60.dp, top = 40.dp, bottom = 14.dp)
                    .graphicsLayer {
                        translationY = -bob * (if (sleeping) 6f else 18f)
                        if (stage == Stage.EGG) rotationZ = wobble * 8f
                    }
                    .alpha(if (sleeping) 0.75f else 1f),
            )
        } else {
            Canvas(Modifier.fillMaxSize().padding(top = 24.dp)) {
                drawPsykit(stage, mood, blink, -bob * size.height * (if (sleeping) 0.015f else 0.04f), swing, wobble)
            }
        }

        // Herzchen beim Streicheln
        if (hearts.value < 1f) {
            Canvas(Modifier.fillMaxSize()) {
                val t = hearts.value
                listOf(-0.16f to 0f, 0.02f to 0.15f, 0.17f to 0.05f).forEach { (dx, delay) ->
                    val p = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
                    if (p > 0f) drawPath(
                        heartPath(Offset(size.width * (0.5f + dx), size.height * (0.45f - p * 0.3f)), size.height * 0.05f),
                        Color(0xFFFF5FA2).copy(alpha = 1f - p),
                    )
                }
            }
        }

        // Licht aus: abdunkeln, Zzz
        if (lightsOff) Box(Modifier.fillMaxSize().background(Color(0x55000018)))
        if (sleeping) {
            Text(
                "Z z z",
                color = if (night) Color(0xFFE7DEFF) else Color(0xFF6A4FB8),
                fontSize = (18 + zFloat * 8).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 50.dp, y = (70 - zFloat * 30).dp)
                    .alpha(1f - zFloat * 0.7f),
            )
        }
    }
}
