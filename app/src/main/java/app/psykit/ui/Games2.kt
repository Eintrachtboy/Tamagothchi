package app.psykit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.model.Mood
import app.psykit.core.model.PetState
import app.psykit.sprites.Sprite
import kotlinx.coroutines.delay
import kotlin.random.Random

// ---------------------------------------------------------------- Psi-Reaktion

@Composable
fun ReactionGame(sprite: Sprite?, s: PetState, onDone: (Int, Int, Int) -> Unit) {
    val total = 10
    // 7 echte Leuchten, 3 Fallen, gemischt
    val events = remember { (List(7) { true } + List(3) { false }).shuffled() }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf("wait") } // wait, glow, trap, result
    var shownAt by remember { mutableLongStateOf(0L) }
    var feedback by remember { mutableStateOf("Warte, bis ${s.name} lila leuchtet …") }
    var best by remember { mutableLongStateOf(0L) }
    var tappedThisRound by remember { mutableStateOf(false) }
    var pendingNext by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(index) {
        if (index >= total) { delay(500); onDone(score, total, 0); return@LaunchedEffect }
        tappedThisRound = false
        state = "wait"
        delay(900L + Random.nextLong(1600))
        if (tappedThisRound) return@LaunchedEffect
        shownAt = System.currentTimeMillis()
        state = if (events[index]) "glow" else "trap"
        delay(if (events[index]) 900 else 1100)
        if (!tappedThisRound) {
            if (events[index]) feedback = "Zu langsam!" else { score++; feedback = "Gut widerstanden! 👍" }
            state = "result"
            delay(600)
            index++
        }
    }

    fun tap() {
        if (tappedThisRound || index >= total) return
        when (state) {
            "glow" -> {
                val ms = System.currentTimeMillis() - shownAt
                tappedThisRound = true; score++
                if (best == 0L || ms < best) best = ms
                feedback = "Treffer! $ms ms ⚡"
            }
            "trap" -> { tappedThisRound = true; feedback = "Falle! Bei Rot nicht tippen. 💢" }
            "wait" -> { tappedThisRound = true; feedback = "Zu früh!" }
            else -> return
        }
        state = "result"
        pendingNext = index + 1 // kurze Pause, dann nächste Runde
    }

    LaunchedEffect(pendingNext) {
        val n = pendingNext ?: return@LaunchedEffect
        delay(700)
        pendingNext = null
        index = n
    }

    val glow by animateFloatAsState(if (state == "glow" || state == "trap") 1f else 0f, tween(120), label = "glow")
    val color = if (state == "trap" || (state == "result" && !events.getOrElse(index) { true })) Color(0xFFFF4D4D) else Color(0xFFB57BFF)

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Runde ${minOf(index + 1, total)} von $total · Punkte: $score" + if (best > 0) " · Bestzeit: $best ms" else "", fontWeight = FontWeight.Bold)
        Text(feedback, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Brush.verticalGradient(listOf(Color(0xFF151A3D), Color(0xFF3B2A63))), RoundedCornerShape(24.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tap() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (glow > 0f) {
                    val r = size.minDimension * 0.42f
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.9f * glow), Color.Transparent), center = center, radius = r), r, center)
                }
            }
            PetIcon(sprite, s.stage, Mood.HAPPY, 170.dp, Modifier.scale(1f + 0.08f * glow))
            Text("Irgendwo hier tippen", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

// ---------------------------------------------------------------- Echo

private val ECHO_COLORS = listOf(Color(0xFFFF6FA8), Color(0xFF7B5CD6), Color(0xFF3AB0E0), Color(0xFFFFC94A))
private val ECHO_SYMBOLS = listOf("♥", "☾", "✦", "☀")

@Composable
fun EchoGame(onDone: (Int, Int, Int) -> Unit) {
    val max = 10
    val seq = remember { mutableStateListOf<Int>() }
    var lit by remember { mutableIntStateOf(-1) }
    var phase by remember { mutableStateOf("show") } // show, input, fail, win
    var pos by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(0) }
    var bestLen by remember { mutableIntStateOf(0) }

    LaunchedEffect(round) {
        if (seq.isEmpty()) repeat(2) { seq += Random.nextInt(4) }
        seq += Random.nextInt(4)
        phase = "show"; pos = 0
        delay(700)
        val on = (520 - round * 30).coerceAtLeast(280).toLong()
        for (c in seq) {
            lit = c; delay(on); lit = -1; delay(170)
        }
        phase = "input"
    }

    LaunchedEffect(phase) {
        if (phase == "fail" || phase == "win") { delay(1100); onDone(bestLen, max, 0) }
    }

    fun press(c: Int) {
        if (phase != "input") return
        if (seq[pos] == c) {
            pos++
            if (pos == seq.size) {
                bestLen = seq.size
                if (seq.size >= max) phase = "win" else { phase = "ok"; round++ }
            }
        } else phase = "fail"
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Länge: ${seq.size} · Geschafft: $bestLen / $max", fontWeight = FontWeight.Bold)
        Text(
            when (phase) {
                "show" -> "Gut aufpassen …"
                "input" -> "Jetzt du! (${pos} / ${seq.size})"
                "fail" -> "Oh, falsch! Geschafft hast du $bestLen."
                "win" -> "Unglaublich, alle $max!"
                else -> "Richtig! Es wird länger …"
            },
        )
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 2) {
                    val c = row * 2 + col
                    var pressed by remember { mutableStateOf(false) }
                    LaunchedEffect(pressed) { if (pressed) { delay(180); pressed = false } }
                    val on = lit == c || pressed
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .graphicsLayer { alpha = if (on) 1f else 0.4f; scaleX = if (on) 1.04f else 1f; scaleY = if (on) 1.04f else 1f }
                            .background(ECHO_COLORS[c], RoundedCornerShape(28.dp))
                            .clickable(enabled = phase == "input") { pressed = true; press(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(ECHO_SYMBOLS[c], fontSize = 44.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Schattenraten

private val SHADOW_ITEMS = listOf(
    "🍎" to "Apfel", "🍌" to "Banane", "🍐" to "Birne", "🍇" to "Trauben", "🍓" to "Erdbeere", "🍒" to "Kirschen",
    "🥕" to "Karotte", "🍄" to "Pilz", "🐟" to "Fisch", "🍙" to "Reisbällchen", "🧁" to "Muffin", "🍦" to "Eis",
    "☂️" to "Schirm", "🎈" to "Ballon", "⭐" to "Stern", "🌙" to "Mond", "⚽" to "Ball", "🔑" to "Schlüssel",
    "🎸" to "Gitarre", "✈️" to "Flugzeug", "🚲" to "Fahrrad", "🐌" to "Schnecke", "🦋" to "Schmetterling", "🐢" to "Schildkröte",
    "🌵" to "Kaktus", "🍁" to "Blatt", "☕" to "Tasse", "🔔" to "Glocke", "🏠" to "Haus", "🦆" to "Ente",
)

@Composable
fun ShadowGame(onDone: (Int, Int, Int) -> Unit) {
    val rounds = 8
    val picks = remember { SHADOW_ITEMS.shuffled().take(rounds) }
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf<Int?>(null) }
    val options = remember(round) {
        if (round >= rounds) emptyList()
        else (SHADOW_ITEMS.filter { it != picks[round] }.shuffled().take(3) + picks[round]).shuffled()
    }

    LaunchedEffect(round) { if (round >= rounds) { delay(300); onDone(score, rounds, 0) } }
    if (round >= rounds) return
    val target = picks[round]
    val revealed = answered != null

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Bild ${round + 1} von $rounds · Richtig: $score", fontWeight = FontWeight.Bold)
        Text(if (!revealed) "Was ist das?" else if (options[answered!!] == target) "Richtig! 🎉" else "Leider nein, das ist: ${target.second}")
        Box(
            Modifier.fillMaxWidth().height(220.dp).background(Color(0xFFF3EEFF), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val shadow = if (revealed) Modifier else Modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(Color(0xFF2B2140), blendMode = BlendMode.SrcIn)
                }
            Text(target.first, fontSize = 120.sp, modifier = shadow)
        }
        options.chunked(2).forEachIndexed { r, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEachIndexed { c, opt ->
                    val i = r * 2 + c
                    val label = if (revealed) "${opt.first} ${opt.second}" else opt.second
                    if (revealed && opt == target) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(label) }
                    else OutlinedButton(
                        onClick = { if (!revealed) { answered = i; if (opt == target) score++ } },
                        modifier = Modifier.weight(1f),
                    ) { Text(label, color = if (revealed && answered == i) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                }
            }
        }
        if (revealed) Button(onClick = { answered = null; round++ }) { Text(if (round + 1 >= rounds) "Ergebnis" else "Weiter") }
    }
}
