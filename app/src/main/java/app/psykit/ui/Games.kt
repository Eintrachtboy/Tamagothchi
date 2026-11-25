package app.psykit.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.ui.viewinterop.AndroidView
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.model.GameKind
import app.psykit.core.model.Mood
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import app.psykit.sprites.Sprite
import app.psykit.sprites.SpriteVariant
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/** Vollbild-Dialog für alle Minispiele. onFinish(score, max, bonusExp). */
@Composable
fun GameDialog(
    kind: GameKind,
    vm: PetViewModel,
    s: PetState,
    sprite: Sprite?,
    onFinish: (Int, Int, Int) -> Unit,
    onCancel: () -> Unit,
) {
    var result by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    // In den Spielen immer dein Standardbild („Normal“) zeigen
    val sprite = remember(s.stage) { vm.sprites.load(s.stage, SpriteVariant.BASE) ?: sprite }
    // Vollbild im App-Fenster statt eigenem Dialog-Fenster, damit unten nichts unter der Navigationsleiste verschwindet
    FullScreenLayer(onBack = onCancel) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kind.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Abbrechen") }
                }
                Text("Trainiert: ${kind.trains}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val r = result
                    if (r != null) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            PetIcon(sprite, s.stage, if (r.first * 2 >= r.second) Mood.HAPPY else Mood.OK, 140.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("${r.first} von ${r.second}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            if (r.third > 0) Text("✨ Goldene Beeren: +${r.third} Bonus-EP")
                            Text(
                                when {
                                    r.first >= r.second -> "Perfekt! ${s.name} ist begeistert."
                                    r.first * 2 >= r.second -> "Gut gemacht! ${s.name} wird stärker."
                                    else -> "Knapp! Nächstes Mal klappt es besser."
                                },
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { onFinish(r.first, r.second, r.third) }) { Text("Belohnung abholen") }
                        }
                    } else {
                        val done: (Int, Int, Int) -> Unit = { a, b, c -> result = Triple(a, b, c) }
                        when (kind) {
                            GameKind.HIDE_SEEK -> HideSeekGame(sprite, s, done)
                            GameKind.MEMORY -> MemoryGame(done)
                            GameKind.BUBBLES -> BubblesGame(done)
                            GameKind.BERRIES -> BerriesGame(sprite, s, done)
                            GameKind.REACTION -> ReactionGame(sprite, s, done)
                            GameKind.ECHO -> EchoGame(done)
                            GameKind.SHADOW -> ShadowGame(done)
                            GameKind.COOK -> CookGame(sprite, s, done)
                            GameKind.CLOUDS -> CloudHopGame(sprite, s, done)
                            GameKind.VISIT -> VisitGame(vm, sprite, s, done)
                        }
                    }
                }
            }
        }
    }
}

/** Kleines Bild des Tierchens: dein eigenes Bild (auch animiert) oder die eingebaute Figur. */
@Composable
fun PetIcon(sprite: Sprite?, stage: Stage, mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    val img = sprite?.image
    val anim = sprite?.animated
    when {
        anim != null -> {
            val drawable = remember(anim, anim.lastModified()) {
                runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(anim)) }.getOrNull()
            }
            AndroidView(
                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { iv ->
                    if (iv.drawable !== drawable) {
                        iv.setImageDrawable(drawable)
                        (drawable as? AnimatedImageDrawable)?.start()
                    }
                },
                modifier = modifier.size(size),
            )
        }
        img != null -> Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.size(size))
        else -> Canvas(modifier.size(size)) { drawPsykit(if (stage == Stage.EGG) Stage.BABY else stage, mood, 0f, 0f, 0f, 0f) }
    }
}

// ---------------------------------------------------------------- Wo steckt es?

@Composable
private fun HideSeekGame(sprite: Sprite?, s: PetState, onDone: (Int, Int, Int) -> Unit) {
    val rounds = 5
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    // slot[i] = an welcher Position (0..2) Wolke i gerade steht
    val slot = remember { mutableStateListOf(0, 1, 2) }
    var hidden by remember { mutableIntStateOf(Random.nextInt(3)) } // Index der Wolke mit dem Tierchen
    var phase by remember { mutableStateOf("show") } // show, hide, shuffle, guess, reveal
    var picked by remember { mutableIntStateOf(-1) }
    var speed by remember { mutableIntStateOf(450) }
    var go by remember { mutableIntStateOf(0) }

    // Neue Runde: Tierchen sitzt sichtbar auf einer Wolke und wartet auf „Mischen!“
    LaunchedEffect(round) {
        if (round >= rounds) { onDone(score, rounds, 0); return@LaunchedEffect }
        picked = -1
        hidden = Random.nextInt(3)
        phase = "show"
    }
    // Nach „Mischen!“: erst verstecken, dann mischen
    LaunchedEffect(go) {
        if (go == 0) return@LaunchedEffect
        phase = "hide"
        delay(900)
        phase = "shuffle"
        speed = (340 - round * 50).coerceAtLeast(150)
        repeat(9 + round * 4) {
            val a = Random.nextInt(3); var b = Random.nextInt(3); if (b == a) b = (a + 1) % 3
            val ia = slot.indexOf(a); val ib = slot.indexOf(b)
            slot[ia] = b; slot[ib] = a
            delay(speed.toLong() + 15)
        }
        phase = "guess"
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Runde ${minOf(round + 1, rounds)} von $rounds · Gefunden: $score", fontWeight = FontWeight.Bold)
        Text(
            when (phase) {
                "show" -> "Hier startet ${s.name}. Merk dir die Wolke!"
                "hide" -> "${s.name} versteckt sich …"
                "shuffle" -> "Die Wolken mischen sich …"
                "guess" -> "Hinter welcher Wolke steckt ${s.name}?"
                else -> if (picked == hidden) "Gefunden! 🎉" else "Leider nicht. Hier war es!"
            },
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(240.dp)) {
            val slotW = maxWidth / 3
            for (i in 0 until 3) {
                val x by animateFloatAsState(slot[i].toFloat(), tween(speed), label = "cloud$i")
                val up = i == hidden && (phase == "show" || phase == "reveal")
                val petDown by animateFloatAsState(if (up) 0f else 1f, tween(if (up) 400 else 800), label = "pet$i")
                Box(
                    Modifier
                        .offset(x = slotW * x, y = 20.dp)
                        .size(slotW, 210.dp)
                        .clickable(enabled = phase == "guess") {
                            picked = i
                            if (i == hidden) score++
                            phase = "reveal"
                        },
                ) {
                    // Tierchen sitzt oben auf der Wolke und rutscht beim Verstecken dahinter
                    if (i == hidden && petDown < 0.99f) {
                        PetIcon(
                            sprite, s.stage, Mood.HAPPY, 110.dp,
                            Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (petDown * 90).dp)
                                .alpha(1f - petDown * 0.6f),
                        )
                    }
                    Canvas(Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter).padding(horizontal = 4.dp)) {
                        val w = size.width; val h = size.height
                        val c = if (phase == "reveal" && i == picked && i != hidden) Color(0xFFFFD6D6) else Color.White
                        drawCircle(c, w * 0.26f, Offset(w * 0.28f, h * 0.55f))
                        drawCircle(c, w * 0.33f, Offset(w * 0.52f, h * 0.42f))
                        drawCircle(c, w * 0.25f, Offset(w * 0.76f, h * 0.58f))
                        drawRoundRect(
                            c, topLeft = Offset(w * 0.06f, h * 0.5f), size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.38f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.19f, h * 0.19f),
                        )
                        drawCircle(Color(0xFFB8D4F0), w * 0.33f, Offset(w * 0.52f, h * 0.42f), style = Stroke(3f))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (phase) {
            "show" -> Button(onClick = { go++ }) { Text("Mischen!") }
            "reveal" -> Button(onClick = { round++ }) { Text(if (round + 1 >= rounds) "Ergebnis" else "Nächste Runde") }
            else -> {}
        }
    }
}

// ---------------------------------------------------------------- Psi-Memory

private val PSI_SYMBOLS = listOf("☾", "✦", "☯", "◈", "⚛", "✺", "✧", "◉")
private val PSI_COLORS = listOf(Color(0xFF7B5CD6), Color(0xFF7B5CD6), Color(0xFF7B5CD6), Color(0xFF7B5CD6), Color(0xFF7B5CD6), Color(0xFF7B5CD6), Color(0xFF7B5CD6), Color(0xFF7B5CD6))

@Composable
private fun MemoryGame(onDone: (Int, Int, Int) -> Unit) {
    val maxTries = 16
    val cards = remember { (PSI_SYMBOLS.indices + PSI_SYMBOLS.indices).shuffled() }
    val open = remember { mutableStateListOf<Int>() }
    val matched = remember { mutableStateListOf<Int>() }
    var tries by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val pairs = matched.size / 2

    LaunchedEffect(open.size) {
        if (open.size == 2) {
            busy = true
            tries++
            if (cards[open[0]] == cards[open[1]]) { matched += open[0]; matched += open[1]; delay(250) } else delay(550)
            open.clear() // ändert den Schlüssel dieses Effekts, danach läuft hier nichts mehr
            busy = false
        }
    }
    // Spielende getrennt prüfen (sonst würde open.clear() die Prüfung abbrechen)
    LaunchedEffect(matched.size, tries) {
        if (matched.size == cards.size || tries >= maxTries) { busy = true; delay(500); onDone(matched.size / 2, PSI_SYMBOLS.size, 0) }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Paare: $pairs / ${PSI_SYMBOLS.size} · Versuche übrig: ${maxTries - tries}", fontWeight = FontWeight.Bold)
        cards.indices.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    val faceUp = i in open || i in matched
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.25f)
                            .alpha(if (i in matched) 0.55f else 1f)
                            .background(
                                if (faceUp) Brush.linearGradient(listOf(Color(0xFFFFF7FD), Color(0xFFEDE6FF)))
                                else Brush.linearGradient(listOf(Color(0xFF8C7BE0), Color(0xFFE88BC4))),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable(enabled = !busy && !faceUp && open.size < 2) { open += i },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (faceUp) Text(PSI_SYMBOLS[cards[i]], fontSize = 34.sp, color = PSI_COLORS[cards[i]])
                        else Text("?", fontSize = 26.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Seifenblasen

private class Bubble(var x: Float, var y: Float, val r: Float, val v: Float, val phase: Float)

@Composable
private fun BubblesGame(onDone: (Int, Int, Int) -> Unit) {
    val max = 35
    val bubbles = remember { ArrayList<Bubble>() }
    var popped by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableFloatStateOf(30f) }
    var frame by remember { mutableLongStateOf(0L) }
    var area by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val pops = remember { ArrayList<Pair<Offset, Long>>() }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        var spawn = 0f
        while (timeLeft > 0f) {
            withFrameNanos { now ->
                val dt = (now - last) / 1e9f; last = now
                timeLeft = (timeLeft - dt).coerceAtLeast(0f)
                if (area.width > 0f) {
                    spawn -= dt
                    if (spawn <= 0f) {
                        spawn = (0.6f - (30f - timeLeft) * 0.012f).coerceAtLeast(0.3f) + Random.nextFloat() * 0.2f
                        val r = area.width * (0.045f + Random.nextFloat() * 0.035f)
                        bubbles += Bubble(r + Random.nextFloat() * (area.width - 2 * r), area.height + r, r, area.height * (0.3f + (30f - timeLeft) * 0.008f + Random.nextFloat() * 0.25f), Random.nextFloat() * 6f)
                    }
                    bubbles.forEach { it.y -= it.v * dt }
                    bubbles.removeAll { it.y < -it.r }
                }
                frame = now
            }
        }
        delay(300)
        onDone(minOf(popped, max), max, 0)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Geplatzt: $popped · Zeit: ${timeLeft.roundToInt()} s", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFA9DDFB), Color(0xFFE9F7FF))), RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    // Platzen schon beim Berühren (nicht erst beim Loslassen), auch mehrere Finger
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            ev.changes.filter { it.pressed && !it.previousPressed }.forEach { ch ->
                                val p = ch.position
                                val t = frame / 1e9f
                                // gleiche Position wie gezeichnet (inkl. Seitwärts-Wackeln), großzügige Trefferfläche
                                val hit = bubbles.minByOrNull { b ->
                                    (Offset(b.x + kotlin.math.sin(t * 2f + b.phase) * b.r * 0.25f, b.y) - p).getDistance() / b.r
                                }?.takeIf { b ->
                                    (Offset(b.x + kotlin.math.sin(t * 2f + b.phase) * b.r * 0.25f, b.y) - p).getDistance() < b.r * 1.4f
                                }
                                if (hit != null && timeLeft > 0f) {
                                    bubbles.remove(hit); popped++
                                    pops += Offset(hit.x, hit.y) to System.currentTimeMillis()
                                    ch.consume()
                                }
                            }
                        }
                    }
                },
        ) {
            area = size
            val t = frame / 1e9f
            bubbles.forEach { b ->
                val c = Offset(b.x + kotlin.math.sin(t * 2f + b.phase) * b.r * 0.25f, b.y)
                drawCircle(Brush.radialGradient(listOf(Color(0x33FFFFFF), Color(0x66C7A6FF), Color(0x99FF9BD6)), center = c, radius = b.r), b.r, c)
                drawCircle(Color.White.copy(alpha = 0.8f), b.r, c, style = Stroke(2.5f))
                drawCircle(Color.White.copy(alpha = 0.9f), b.r * 0.18f, c + Offset(-b.r * 0.4f, -b.r * 0.4f))
            }
            val now = System.currentTimeMillis()
            pops.removeAll { now - it.second > 300 }
            pops.forEach { (p, at) ->
                val k = (now - at) / 300f
                for (i in 0 until 8) {
                    val a = i * 0.785f
                    drawCircle(Color.White.copy(alpha = 1f - k), 4f, p + Offset(kotlin.math.cos(a), kotlin.math.sin(a)) * (20f + 40f * k))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Beerenfang

private class Berry(var x: Float, var y: Float, val v: Float, val golden: Boolean)

@Composable
private fun BerriesGame(sprite: Sprite?, s: PetState, onDone: (Int, Int, Int) -> Unit) {
    val ctx = LocalContext.current
    val berries = remember { ArrayList<Berry>() }
    var caught by remember { mutableIntStateOf(0) }
    var golden by remember { mutableIntStateOf(0) }
    var spawned by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableFloatStateOf(45f) }
    var frame by remember { mutableLongStateOf(0L) }
    var area by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var catcherX by remember { mutableFloatStateOf(0.5f) } // 0..1
    var tilt by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { tilt = e.values[0] }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (acc != null) { hasSensor = true; sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        var spawn = 0f
        while (timeLeft > 0f) {
            withFrameNanos { now ->
                val dt = (now - last) / 1e9f; last = now
                timeLeft = (timeLeft - dt).coerceAtLeast(0f)
                // Kippen: Handy nach rechts geneigt -> x-Beschleunigung negativ -> nach rechts
                catcherX = (catcherX - tilt * 0.12f * dt).coerceIn(0.08f, 0.92f)
                if (area.width > 0f) {
                    spawn -= dt
                    val elapsed = 45f - timeLeft
                    if (spawn <= 0f && timeLeft > 1.5f) {
                        spawn = (0.75f - elapsed * 0.012f).coerceAtLeast(0.3f)
                        val g = Random.nextFloat() < 0.1f
                        berries += Berry(0.06f + Random.nextFloat() * 0.88f, -0.05f, (0.36f + elapsed * 0.01f) * (if (g) 1.4f else 1f), g)
                        spawned++
                    }
                    val catchY = 0.86f
                    val iter = berries.iterator()
                    while (iter.hasNext()) {
                        val b = iter.next()
                        val before = b.y
                        b.y += b.v * dt
                        if (before < catchY && b.y >= catchY && kotlin.math.abs(b.x - catcherX) < 0.085f) {
                            if (b.golden) golden++ else caught++
                            iter.remove()
                        } else if (b.y > 1.05f) iter.remove()
                    }
                }
                frame = now
            }
        }
        delay(400)
        onDone(caught + golden, maxOf(1, spawned), golden * 3)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Gefangen: ${caught + golden} · ✨ $golden · Zeit: ${timeLeft.roundToInt()} s", fontWeight = FontWeight.Bold)
        Text(if (hasSensor) "Kipp das Handy nach links und rechts (oder zieh mit dem Finger)." else "Zieh mit dem Finger nach links und rechts.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFFFD9C2), Color(0xFFCDEBC0))), RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        catcherX = (catcherX + amount.x / size.width).coerceIn(0.08f, 0.92f)
                    }
                },
        ) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            Canvas(Modifier.fillMaxSize()) {
                area = size
                if (frame < 0) return@Canvas
                berries.forEach { b ->
                    val c = Offset(b.x * size.width, b.y * size.height)
                    val r = size.width * 0.035f
                    if (b.golden) {
                        drawCircle(Color(0x55FFE07A), r * 1.8f, c)
                        drawCircle(Color(0xFFFFC94A), r, c)
                    } else {
                        drawCircle(Color(0xFF4B4FC9), r, c)
                        drawCircle(Color(0xFF6A3FB8), r * 0.7f, c + Offset(r * 0.6f, r * 0.3f))
                    }
                    drawCircle(Color.White.copy(alpha = 0.6f), r * 0.25f, c + Offset(-r * 0.35f, -r * 0.35f))
                    drawLine(Color(0xFF3E8E3E), c + Offset(0f, -r), c + Offset(r * 0.3f, -r * 1.6f), 3f)
                }
            }
            val iconSize = 96.dp
            val half = with(androidx.compose.ui.platform.LocalDensity.current) { iconSize.toPx() / 2 }
            PetIcon(
                sprite, s.stage, Mood.HAPPY, iconSize,
                Modifier.offset { IntOffset((catcherX * wPx - half).roundToInt(), (0.86f * hPx - half * 0.9f).roundToInt()) },
            )
        }
    }
}
