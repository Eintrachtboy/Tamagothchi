package app.psykit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.model.Mood
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import app.psykit.sprites.Sprite
import app.psykit.sprites.SpriteVariant
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- Futter-Koch

private data class Recipe(val name: String, val dish: String, val items: Set<String>)

private val INGREDIENTS = listOf("🫐", "🥛", "🌾", "🐟", "🍚", "🥬", "🍄", "🥕", "💧", "🍎", "🍯", "🥚", "⭐", "💜")
private val INGREDIENT_NAMES = mapOf(
    "🫐" to "Beeren", "🥛" to "Milch", "🌾" to "Mehl", "🐟" to "Fisch", "🍚" to "Reis", "🥬" to "Salat",
    "🍄" to "Pilz", "🥕" to "Karotte", "💧" to "Wasser", "🍎" to "Apfel", "🍯" to "Honig", "🥚" to "Ei",
    "⭐" to "Sternbeere", "💜" to "Psi-Beere",
)
private val RECIPES = listOf(
    Recipe("Beerenkuchen", "🍰", setOf("🫐", "🥛", "🌾")),
    Recipe("Sternen-Joghurt", "🍨", setOf("⭐", "🥛", "🍯")),
    Recipe("Psi-Pudding", "🍮", setOf("💜", "🥛", "🥚")),
    Recipe("Garten-Torte", "🎂", setOf("⭐", "💜", "🌾", "🥚")),
    Recipe("Fisch mit Reis", "🍱", setOf("🐟", "🍚", "🥬")),
    Recipe("Pilzsuppe", "🍲", setOf("🍄", "🥕", "💧")),
    Recipe("Apfelmus", "🥣", setOf("🍎", "🍯", "💧")),
    Recipe("Pfannkuchen", "🥞", setOf("🥚", "🥛", "🌾")),
    Recipe("Gemüse-Reis", "🍛", setOf("🍚", "🥕", "🥬")),
    Recipe("Honig-Milch", "🍼", setOf("🥛", "🍯", "🫐")),
    Recipe("Omelett", "🍳", setOf("🥚", "🍄", "🥬")),
    Recipe("Fischsuppe", "🍜", setOf("🐟", "🥕", "💧", "🥬")),
    Recipe("Beeren-Pfannkuchen", "🥞", setOf("🥚", "🥛", "🌾", "🫐")),
    Recipe("Apfelkuchen", "🥧", setOf("🍎", "🌾", "🥚", "🍯")),
    Recipe("Pilz-Risotto", "🍚", setOf("🍚", "🍄", "🥛", "💧")),
    Recipe("Gemüsepfanne", "🥘", setOf("🥕", "🍄", "🥬", "🍚")),
    Recipe("Honig-Müsli", "🥣", setOf("🌾", "🥛", "🍯", "🍎")),
)

@Composable
fun CookGame(sprite: Sprite?, s: PetState, onDone: (Int, Int, Int) -> Unit) {
    val rounds = 5
    // erst 2 Rezepte mit 3 Zutaten, dann 3 mit 4 Zutaten
    val recipes = remember { RECIPES.filter { it.items.size == 3 }.shuffled().take(2) + RECIPES.filter { it.items.size == 4 }.shuffled().take(3) }
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var showRecipe by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val pot = remember { mutableStateListOf<String>() }

    LaunchedEffect(round) {
        if (round >= rounds) { delay(300); onDone(score, rounds, 0); return@LaunchedEffect }
        pot.clear(); result = null; showRecipe = true
        delay((3000 - round * 350).coerceAtLeast(1600).toLong())
        showRecipe = false
    }
    if (round >= rounds) return
    val r = recipes[round]

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Rezept ${round + 1} von $rounds · Gelungen: $score", fontWeight = FontWeight.Bold)
        Box(
            Modifier.fillMaxWidth().background(Color(0xFFFFF4E0), RoundedCornerShape(20.dp)).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${r.dish} ${r.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (showRecipe || result != null) r.items.joinToString("  +  ") { "$it ${INGREDIENT_NAMES[it]}" }
                    else "Rezept merken … jetzt aus dem Kopf kochen!",
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Kochtopf
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(170.dp, 100.dp)) {
                drawRoundRect(Color(0xFF8A8FA3), topLeft = Offset(0f, size.height * 0.25f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.75f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f, 30f))
                drawRect(Color(0xFF6E7387), topLeft = Offset(-10f, size.height * 0.22f), size = androidx.compose.ui.geometry.Size(size.width + 20f, 14f))
            }
            Text(
                when (result) {
                    true -> r.dish
                    false -> "💨"
                    null -> pot.joinToString(" ").ifEmpty { "🍳" }
                },
                fontSize = 34.sp, modifier = Modifier.offset(y = 10.dp),
            )
        }
        when (result) {
            true -> Text("Lecker! ${s.name} freut sich schon.", fontWeight = FontWeight.Bold)
            false -> Text("Hmm, das war nicht ganz das Rezept.", fontWeight = FontWeight.Bold)
            null -> {}
        }
        if (result == null) {
            INGREDIENTS.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { ing ->
                        val inPot = ing in pot
                        OutlinedButton(
                            onClick = { if (inPot) pot.remove(ing) else if (pot.size < 5) pot += ing },
                            enabled = !showRecipe,
                            modifier = Modifier.weight(1f),
                        ) { Text(ing + if (inPot) "✓" else "", fontSize = 20.sp) }
                    }
                }
            }
            Button(onClick = {
                val ok = pot.toSet() == r.items
                if (ok) score++
                result = ok
            }, enabled = pot.size >= 2) { Text("🔥 Kochen") }
        } else {
            PetIcon(sprite, s.stage, if (result == true) Mood.HAPPY else Mood.OK, 90.dp)
            Button(onClick = { round++ }) { Text(if (round + 1 >= rounds) "Servieren" else "Nächstes Rezept") }
        }
    }
}

// ---------------------------------------------------------------- Wolkenhüpfen

@Composable
fun CloudHopGame(sprite: Sprite?, s: PetState, onDone: (Int, Int, Int) -> Unit) {
    val goal = 25
    val phases = remember { List(goal + 2) { Random.nextFloat() * 6.28f } }
    val dirs = remember { List(goal + 2) { if (Random.nextBoolean()) 1f else -1f } }
    var cur by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var time by remember { mutableFloatStateOf(0f) }
    var jumpStart by remember { mutableFloatStateOf(-1f) }
    var fallStart by remember { mutableFloatStateOf(-1f) }
    var camera by remember { mutableFloatStateOf(0f) }
    var over by remember { mutableStateOf(false) }

    fun cloudX(i: Int, t: Float): Float =
        if (i == 0) 0.5f else 0.5f + 0.38f * sin(t * (1.1f + i * 0.08f) * dirs[i] + phases[i])

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (!over) {
            withFrameNanos { now ->
                val dt = (now - last) / 1e9f; last = now
                time += dt
                if (jumpStart >= 0f && time - jumpStart >= 0.45f) {
                    jumpStart = -1f
                    val px = cloudX(cur, time)
                    if (abs(px - cloudX(cur + 1, time)) < (0.11f - cur * 0.002f).coerceAtLeast(0.07f)) {
                        cur++
                        if (cur >= goal) over = true
                    } else {
                        fallStart = time
                        lives--
                    }
                }
                if (fallStart >= 0f && time - fallStart > 0.7f) {
                    fallStart = -1f
                    if (lives <= 0) over = true
                }
                camera += (cur - camera) * (dt * 4f).coerceAtMost(1f)
            }
        }
        delay(600)
        onDone(cur, goal, 0)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Höhe: $cur / $goal · Leben: " + "❤️".repeat(lives.coerceAtLeast(0)), fontWeight = FontWeight.Bold)
        Text(if (over) (if (cur >= goal) "Ganz oben! ☁️✨" else "Abgestürzt! Höhe $cur.") else "Tippe, wenn die nächste Wolke genau über dir ist.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF6FB8F0), Color(0xFFD9F0FF))), RoundedCornerShape(20.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (!over && jumpStart < 0f && fallStart < 0f) jumpStart = time
                },
        ) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val gap = hPx * 0.26f
            val baseY = hPx * 0.82f
            fun levelY(i: Int) = baseY - (i - camera) * gap
            Canvas(Modifier.fillMaxSize()) {
                if (time < 0f) return@Canvas
                for (i in maxOf(0, cur - 2)..minOf(goal, cur + 4)) {
                    val y = levelY(i)
                    if (y < -50f || y > size.height + 50f) continue
                    drawCloud(Offset(cloudX(i, time) * size.width, y), size.width * 0.13f, if (i == goal) Color(0xFFFFF4C2) else Color.White)
                }
            }
            val iconDp = 70.dp
            val iconPx = with(LocalDensity.current) { iconDp.toPx() }
            val jumpT = if (jumpStart >= 0f) ((time - jumpStart) / 0.45f).coerceIn(0f, 1f) else 0f
            val fallT = if (fallStart >= 0f) ((time - fallStart) / 0.7f).coerceIn(0f, 1f) else 0f
            val px = cloudX(cur, time) * wPx
            val py = levelY(cur) - iconPx * 0.75f - sin(jumpT * PI.toFloat()) * gap * 0.9f - jumpT * gap * 0.3f + fallT * hPx * 0.3f
            PetIcon(
                sprite, s.stage, if (fallStart >= 0f) Mood.SAD else Mood.HAPPY, iconDp,
                Modifier.offset { IntOffset((px - iconPx / 2).roundToInt(), py.roundToInt()) },
            )
        }
    }
}

/** Gleiche flauschige Wolke wie im Zuhause, auf die bisherige Breite (ca. 2,7 r) skaliert. */
private fun DrawScope.drawCloud(c: Offset, r: Float, color: Color) =
    drawFluffyCloud(c + Offset(0f, r * 0.1f), r * 0.5f, alpha = 1f, tint = color)

// ---------------------------------------------------------------- Besuch

@Composable
fun VisitGame(vm: PetViewModel, sprite: Sprite?, s: PetState, onDone: (Int, Int, Int) -> Unit) {
    val currentId = vm.currentPetId.value
    val friends = remember { vm.allPets().filter { it.first != currentId && it.second.stage != Stage.EGG } }
    var partner by remember { mutableStateOf(friends.firstOrNull()?.first) }
    var started by remember { mutableStateOf(false) }

    if (friends.isEmpty()) {
        Text("Für einen Besuch brauchst du mindestens 2 geschlüpfte Tierchen. Hol dir über 🐾 ein weiteres!")
        return
    }
    val p = friends.first { it.first == partner }
    val partnerSprite = remember(partner) { vm.spritesFor(p.first).load(p.second.stage, SpriteVariant.BASE) }

    if (!started) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Wen besucht ${s.name}?", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                friends.forEach { (id, fs) -> FilterChip(selected = id == partner, onClick = { partner = id }, label = { Text(fs.name) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetIcon(sprite, s.stage, Mood.HAPPY, 110.dp)
                Text("💞", fontSize = 32.sp)
                PetIcon(partnerSprite, p.second.stage, Mood.HAPPY, 110.dp)
            }
            Text("Die beiden werfen sich einen Ball zu. Tippe, wenn der Ball beim Fänger im grünen Bereich ist.", textAlign = TextAlign.Center)
            Button(onClick = { started = true }) { Text("Los geht's") }
        }
        return
    }

    val throws = 15
    var thrown by remember { mutableIntStateOf(0) }
    var caught by remember { mutableIntStateOf(0) }
    var t by remember { mutableFloatStateOf(0f) } // 0..1 Flug des Balls
    var toRight by remember { mutableStateOf(true) }
    var handled by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (thrown < throws) {
            withFrameNanos { now ->
                val dt = (now - last) / 1e9f; last = now
                val speed = 0.7f + thrown * 0.07f
                t += dt * speed
                if (t >= 1.08f) {
                    if (!handled) feedback = "Daneben! 🙈"
                    thrown++
                    t = 0f; handled = false; toRight = !toRight
                }
                frame = now
            }
        }
        finished = true
        val ratio = caught.toFloat() / throws
        vm.visitedBy(p.first, ratio)
        delay(500)
        onDone(caught, throws, 0)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Wurf ${minOf(thrown + 1, throws)} von $throws · Gefangen: $caught", fontWeight = FontWeight.Bold)
        Text(if (finished) "Was für ein schöner Besuch!" else feedback.ifEmpty { "Tippe beim Fangen!" })
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFA9DDFB), Color(0xFFCDEBC0))), RoundedCornerShape(20.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (handled || finished) return@clickable
                    handled = true
                    if (t in 0.88f..1.04f) { caught++; feedback = "Gefangen! 🎉" } else feedback = "Zu früh! 🙈"
                },
        ) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            Canvas(Modifier.fillMaxSize()) {
                if (frame < 0) return@Canvas
                val lx = size.width * 0.2f; val rx = size.width * 0.8f
                val y0 = size.height * 0.55f
                // grüner Fangbereich beim Fänger
                val catchX = if (toRight) rx else lx
                drawCircle(Color(0x5546A758), size.width * 0.11f, Offset(catchX, y0))
                val p0 = if (toRight) lx else rx
                val x = p0 + (catchX - p0) * t.coerceAtMost(1.05f)
                val y = y0 - sin(t.coerceIn(0f, 1f) * PI.toFloat()) * size.height * 0.3f
                drawCircle(Color(0xFFFF6FA8), size.width * 0.04f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.7f), size.width * 0.012f, Offset(x - size.width * 0.012f, y - size.width * 0.012f))
            }
            val icon = 96.dp
            val iconPx = with(LocalDensity.current) { icon.toPx() }
            PetIcon(sprite, s.stage, Mood.HAPPY, icon, Modifier.offset { IntOffset((wPx * 0.2f - iconPx / 2).roundToInt(), (hPx * 0.62f).roundToInt()) })
            PetIcon(partnerSprite, p.second.stage, Mood.HAPPY, icon, Modifier.offset { IntOffset((wPx * 0.8f - iconPx / 2).roundToInt(), (hPx * 0.62f).roundToInt()) })
        }
    }
}
