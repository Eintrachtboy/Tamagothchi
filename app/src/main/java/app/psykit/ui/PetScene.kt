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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.engine.DayPhase
import app.psykit.core.model.Mood
import app.psykit.core.model.Reaction
import app.psykit.core.model.Stage
import app.psykit.sprites.Sprite
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

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
    custom: Sprite?,
    customIsVariant: Boolean,
    reaction: ReactionEvent,
    modifier: Modifier = Modifier,
    interaction: Modifier = Modifier,
    dim: Float = 0f,
    floating: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
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

    val breath by anim.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(if (sleeping) 3400 else 2200), RepeatMode.Reverse), label = "breath",
    )

    val hearts = remember { Animatable(1f) }
    val hop = remember { Animatable(1f) }
    val shake = remember { Animatable(1f) }
    LaunchedEffect(reaction.id) {
        if (reaction.id == 0) return@LaunchedEffect
        when (reaction.reaction) {
            Reaction.LOVE -> { launch { hop.snapTo(0.3f); hop.animateTo(1f, tween(500)) }; hearts.snapTo(0f); hearts.animateTo(1f, tween(1300)) }
            Reaction.JUMP -> { launch { hop.snapTo(0f); hop.animateTo(1f, tween(800)) }; hearts.snapTo(0f); hearts.animateTo(1f, tween(1300)) }
            Reaction.HAPPY -> { hop.snapTo(0.4f); hop.animateTo(1f, tween(450)) }
            Reaction.ANNOYED, Reaction.SPIT -> { shake.snapTo(0f); shake.animateTo(1f, tween(600)) }
            else -> {}
        }
    }
    val jumpHeight = if (reaction.reaction == Reaction.JUMP) 110f else 60f
    val stars = remember { List(28) { Offset(Random.nextFloat(), Random.nextFloat() * 0.6f) } }
    val night = phase == DayPhase.NIGHT
    val lightsOff = sleeping && night

    Box(
        modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(skyColors(phase)))
            .then(interaction),
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
                val sw = w * 0.3f * (if (floating) 1f - 0.15f * bob else 1f)
                drawOval(Color(0x26000000), topLeft = Offset(w / 2 - sw / 2, h * 0.935f), size = androidx.compose.ui.geometry.Size(sw, h * 0.045f))
            }
            for (i in 0 until poops) drawPoop(Offset(w * (0.14f + i * 0.11f), h * 0.93f), h * 0.07f)
        }

        // Figur: eigenes Bild (Standbild oder animiertes GIF/WebP) oder gezeichnete Standardfigur
        if (custom != null) {
            // Bewegung je nach Laune: fröhlich hüpft höher, traurig/krank hängt tiefer und ruhiger
            val amp = when {
                !floating -> 0f
                sleeping -> 6f
                mood == Mood.HAPPY -> 28f
                mood == Mood.SAD || mood == Mood.SICK || mood == Mood.TIRED -> 7f
                else -> 18f
            }
            val sink = if (floating && (mood == Mood.SAD || mood == Mood.SICK)) 14f else 0f
            val sway = when { sleeping -> 1f; mood == Mood.HAPPY -> 4f; else -> 2.5f }
            val figure = Modifier
                .fillMaxSize()
                .padding(start = 60.dp, end = 60.dp, top = 40.dp, bottom = if (floating) 14.dp else 8.dp)
                .graphicsLayer {
                    val jump = if (hop.value < 1f) sin(hop.value * PI.toFloat()) else 0f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationY = -bob * amp + sink - jump * jumpHeight
                    translationX = if (shake.value < 1f) sin(shake.value * 6f * PI.toFloat()) * 14f * (1f - shake.value) else 0f
                    // Atmen: leicht strecken und stauchen
                    val b = breath * (if (sleeping) 0.035f else if (floating) 0.02f else 0.03f)
                    scaleX = 1f - b + jump * 0.06f
                    scaleY = 1f + b - jump * 0.04f
                    rotationZ = if (stage == Stage.EGG) wobble * 8f else swing * sway
                }
                .alpha(if (sleeping && !customIsVariant) 0.75f else 1f)
            val animFile = custom.animated
            if (animFile != null) {
                val drawable = remember(animFile, animFile.lastModified()) {
                    runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(animFile)) }.getOrNull()
                }
                AndroidView(
                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                    update = { iv ->
                        if (iv.drawable !== drawable) {
                            iv.setImageDrawable(drawable)
                            (drawable as? AnimatedImageDrawable)?.start()
                        }
                    },
                    modifier = figure,
                )
            } else if (custom.image != null) {
                Image(bitmap = custom.image, contentDescription = "Dein Tierchen", contentScale = ContentScale.Fit, modifier = figure)
            }
        } else {
            Canvas(
                Modifier.fillMaxSize().padding(top = 24.dp).graphicsLayer {
                    val jump = if (hop.value < 1f) sin(hop.value * PI.toFloat()) else 0f
                    translationY = -jump * jumpHeight
                    translationX = if (shake.value < 1f) sin(shake.value * 6f * PI.toFloat()) * 14f * (1f - shake.value) else 0f
                },
            ) {
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

        // Reaktions-Symbole
        if (shake.value < 1f) {
            Text(
                if (reaction.reaction == Reaction.SPIT) "💦" else "💢",
                fontSize = 30.sp,
                modifier = Modifier.align(Alignment.TopCenter).offset(x = 60.dp, y = 50.dp).alpha(1f - shake.value),
            )
        }
        if (reaction.reaction == Reaction.JUMP && hearts.value < 1f) {
            Text("✨", fontSize = 34.sp, modifier = Modifier.align(Alignment.TopCenter).offset(x = (-70).dp, y = (60 - hearts.value * 30).dp).alpha(1f - hearts.value))
        }

        overlay()

        // Licht aus / gedimmt: abdunkeln, Zzz
        val darkness = maxOf(dim, if (lightsOff) 0.35f else 0f)
        if (darkness > 0f) Box(Modifier.fillMaxSize().background(Color(0xFF000018).copy(alpha = darkness * 0.75f)))
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
