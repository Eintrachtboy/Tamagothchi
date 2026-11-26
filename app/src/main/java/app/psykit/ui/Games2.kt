package app.psykit.ui

import app.psykit.core.model.GameTuning

import app.psykit.core.model.Difficulty

import android.os.SystemClock
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
fun ReactionGame(sprite: Sprite?, s: PetState, difficulty: Difficulty, onDone: (Int, Int, Int) -> Unit) {
    val total = 15
    // 10 echte Leuchten, 5 Fallen, gemischt
    val events = remember { (List(10) { true } + List(5) { false }).shuffled() }
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
        delay(700L + Random.nextLong(2300))
        if (tappedThisRound) return@LaunchedEffect
        shownAt = SystemClock.elapsedRealtime()
        state = if (events[index]) "glow" else "trap"
        delay(if (events[index]) GameTuning.reactionGlowMs(difficulty, index) else 1100L)
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
                val ms = SystemClock.elapsedRealtime() - shownAt
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
fun EchoGame(difficulty: Difficulty, onDone: (Int, Int, Int) -> Unit) {
    val max = 15
    val seq = remember { mutableStateListOf<Int>() }
    var lit by remember { mutableIntStateOf(-1) }
    var phase by remember { mutableStateOf("show") } // show, input, fail, win
    var pos by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(0) }
    var bestLen by remember { mutableIntStateOf(0) }

    LaunchedEffect(round) {
        if (seq.isEmpty()) repeat(3) { seq += Random.nextInt(4) }
        seq += Random.nextInt(4)
        phase = "show"; pos = 0
        delay(700)
        val on = GameTuning.echoOnMs(difficulty, round)
        for (c in seq) {
            lit = c; delay(on); lit = -1; delay(120)
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

// Gruppen ähnlicher Dinge: falsche Antworten kommen bevorzugt aus derselben Gruppe (schwerer)
private val SHADOW_GROUPS: List<List<Pair<String, String>>> = listOf(
    listOf("🍎" to "Apfel", "🍐" to "Birne", "🍑" to "Pfirsich", "🍊" to "Orange", "🍋" to "Zitrone", "🍒" to "Kirschen", "🍓" to "Erdbeere", "🍇" to "Trauben", "🍌" to "Banane", "🍍" to "Ananas", "🥝" to "Kiwi", "🥭" to "Mango", "🫐" to "Heidelbeeren", "🍉" to "Melone"),
    listOf("🥕" to "Karotte", "🌽" to "Mais", "🥦" to "Brokkoli", "🍆" to "Aubergine", "🥒" to "Gurke", "🌶️" to "Chili", "🧄" to "Knoblauch", "🧅" to "Zwiebel", "🥔" to "Kartoffel", "🍄" to "Pilz", "🫑" to "Paprika", "🥜" to "Erdnuss"),
    listOf("🐶" to "Hund", "🐱" to "Katze", "🐭" to "Maus", "🐹" to "Hamster", "🐰" to "Hase", "🦊" to "Fuchs", "🐻" to "Bär", "🐼" to "Panda", "🐨" to "Koala", "🐯" to "Tiger", "🦁" to "Löwe", "🐮" to "Kuh", "🐷" to "Schwein", "🐸" to "Frosch", "🐵" to "Affe"),
    listOf("🐔" to "Huhn", "🐧" to "Pinguin", "🐦" to "Vogel", "🦆" to "Ente", "🦅" to "Adler", "🦉" to "Eule", "🦜" to "Papagei", "🦢" to "Schwan", "🦩" to "Flamingo", "🕊️" to "Taube", "🦚" to "Pfau"),
    listOf("🐟" to "Fisch", "🐠" to "Tropenfisch", "🐡" to "Kugelfisch", "🦈" to "Hai", "🐬" to "Delfin", "🐳" to "Wal", "🐙" to "Krake", "🦀" to "Krebs", "🦞" to "Hummer", "🦐" to "Garnele", "🐚" to "Muschel", "🦭" to "Robbe"),
    listOf("🐌" to "Schnecke", "🦋" to "Schmetterling", "🐛" to "Raupe", "🐝" to "Biene", "🐞" to "Marienkäfer", "🦗" to "Grille", "🕷️" to "Spinne", "🦂" to "Skorpion", "🐜" to "Ameise", "🪲" to "Käfer", "🦟" to "Mücke"),
    listOf("🚗" to "Auto", "🚕" to "Taxi", "🚌" to "Bus", "🚑" to "Krankenwagen", "🚒" to "Feuerwehr", "🚜" to "Traktor", "🚲" to "Fahrrad", "🛵" to "Roller", "🏍️" to "Motorrad", "✈️" to "Flugzeug", "🚁" to "Hubschrauber", "🚀" to "Rakete", "⛵" to "Segelboot", "🚂" to "Lokomotive"),
    listOf("⚽" to "Fußball", "🏀" to "Basketball", "🏈" to "Football", "⚾" to "Baseball", "🎾" to "Tennisball", "🏐" to "Volleyball", "🏓" to "Tischtennis", "🏸" to "Federball", "🥊" to "Boxhandschuh", "⛳" to "Golf", "🎳" to "Bowling", "🛹" to "Skateboard"),
    listOf("🎸" to "Gitarre", "🎹" to "Klavier", "🎺" to "Trompete", "🎻" to "Geige", "🥁" to "Trommel", "🎷" to "Saxofon", "🪗" to "Akkordeon", "🔔" to "Glocke", "🎤" to "Mikrofon", "🎧" to "Kopfhörer"),
    listOf("🔑" to "Schlüssel", "🔒" to "Schloss", "✂️" to "Schere", "🔨" to "Hammer", "🪛" to "Schraubenzieher", "🔧" to "Schraubenschlüssel", "📎" to "Büroklammer", "📌" to "Pinnnadel", "🖊️" to "Stift", "🪚" to "Säge", "🧲" to "Magnet", "💡" to "Glühbirne"),
    listOf("☂️" to "Schirm", "👓" to "Brille", "🎩" to "Zylinder", "👑" to "Krone", "👟" to "Turnschuh", "👢" to "Stiefel", "🧤" to "Handschuhe", "🧣" to "Schal", "👜" to "Handtasche", "🎒" to "Rucksack", "🧢" to "Kappe"),
    listOf("☕" to "Tasse", "🍵" to "Teeschale", "🥛" to "Glas Milch", "🍷" to "Weinglas", "🍼" to "Fläschchen", "🫖" to "Teekanne", "🥄" to "Löffel", "🍴" to "Besteck", "🔪" to "Messer", "🍳" to "Pfanne"),
    listOf("⭐" to "Stern", "🌙" to "Mond", "☀️" to "Sonne", "☁️" to "Wolke", "⚡" to "Blitz", "❄️" to "Schneeflocke", "🔥" to "Feuer", "💧" to "Tropfen", "🌈" to "Regenbogen", "🌪️" to "Wirbelsturm"),
    listOf("🌵" to "Kaktus", "🌲" to "Tanne", "🌳" to "Baum", "🌴" to "Palme", "🍁" to "Ahornblatt", "🍀" to "Kleeblatt", "🌻" to "Sonnenblume", "🌹" to "Rose", "🌷" to "Tulpe", "🌼" to "Blume"),
    listOf("🏠" to "Haus", "🏰" to "Burg", "⛪" to "Kirche", "🗼" to "Turm", "🏭" to "Fabrik", "⛺" to "Zelt", "🏟️" to "Stadion", "🗽" to "Freiheitsstatue", "🌉" to "Brücke"),
    listOf("🧁" to "Muffin", "🍦" to "Eis", "🍩" to "Donut", "🍪" to "Keks", "🎂" to "Torte", "🍰" to "Kuchen", "🍫" to "Schokolade", "🍭" to "Lutscher", "🥐" to "Croissant", "🥨" to "Brezel", "🍕" to "Pizza", "🍔" to "Burger"),
)
private val SHADOW_ITEMS = SHADOW_GROUPS.flatten()

@Composable
fun ShadowGame(difficulty: Difficulty, onDone: (Int, Int, Int) -> Unit) {
    val rounds = 12
    val picks = remember { SHADOW_ITEMS.shuffled().take(rounds) }
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf<Int?>(null) }
    var visible by remember { mutableStateOf(false) }
    var timeUp by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    val options = remember(round) {
        if (round >= rounds) emptyList()
        else {
            val target = picks[round]
            val group = SHADOW_GROUPS.first { target in it }
            val similar = group.filter { it != target }.shuffled().take(4)
            val other = SHADOW_ITEMS.filter { it != target && it !in similar }.shuffled().take(5 - similar.size)
            (similar + other + target).shuffled()
        }
    }
    // Silhouette leicht gedreht, gespiegelt und verkleinert, damit man genau hinschauen muss
    val look = remember(round) { Triple(Random.nextInt(-35, 36).toFloat(), Random.nextBoolean(), 0.75f + Random.nextFloat() * 0.35f) }

    LaunchedEffect(round) {
        if (round >= rounds) { delay(300); onDone(score, rounds, 0); return@LaunchedEffect }
        timeUp = false
        delay(600)
        // blitzt nur kurz auf, jede Runde kürzer (650 ms bis 220 ms)
        visible = true
        delay((650 - round * 40).coerceAtLeast(220).toLong())
        visible = false
        secondsLeft = GameTuning.shadowSeconds(difficulty)
        while (secondsLeft > 0 && answered == null) { delay(1000); if (answered == null) secondsLeft-- }
        if (answered == null) { timeUp = true; answered = -1 }
    }
    if (round >= rounds) return
    val target = picks[round]
    val revealed = answered != null

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Bild ${round + 1} von $rounds · Richtig: $score", fontWeight = FontWeight.Bold)
        Text(
            when {
                !revealed && visible -> "Jetzt!"
                !revealed && secondsLeft == 0 -> "Gut aufpassen, gleich blitzt es auf …"
                !revealed -> "Was war das? Noch $secondsLeft s"
                timeUp -> "Zeit um! Das war: ${target.first} ${target.second}"
                options.getOrNull(answered!!) == target -> "Richtig! 🎉"
                else -> "Leider nein, das war: ${target.first} ${target.second}"
            },
        )
        Box(
            Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF1E1830), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                revealed -> Text(target.first, fontSize = 110.sp)
                visible -> Text(
                    target.first, fontSize = 110.sp,
                    modifier = Modifier
                        .graphicsLayer(
                            compositingStrategy = CompositingStrategy.Offscreen,
                            rotationZ = look.first,
                            scaleX = look.third * (if (look.second) -1f else 1f),
                            scaleY = look.third,
                        )
                        .drawWithContent {
                            drawContent()
                            drawRect(Color(0xFFEDE6FF), blendMode = BlendMode.SrcIn)
                        },
                )
                else -> Text("?", fontSize = 80.sp, color = Color.White.copy(alpha = 0.25f), fontWeight = FontWeight.Bold)
            }
        }
        options.chunked(2).forEachIndexed { r, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEachIndexed { c, opt ->
                    val i = r * 2 + c
                    val label = if (revealed) "${opt.first} ${opt.second}" else opt.second
                    if (revealed && opt == target) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                    else OutlinedButton(
                        onClick = { if (!revealed && !visible && secondsLeft > 0) { answered = i; if (opt == target) score++ } },
                        modifier = Modifier.weight(1f),
                    ) { Text(label, maxLines = 1, color = if (revealed && answered == i) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                }
            }
        }
        if (revealed) Button(onClick = { answered = null; secondsLeft = 0; round++ }) { Text(if (round + 1 >= rounds) "Ergebnis" else "Weiter") }
    }
}
