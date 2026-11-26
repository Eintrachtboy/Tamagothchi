package app.psykit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.engine.DayPhase
import app.psykit.core.engine.GardenEngine
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Berry
import app.psykit.core.model.GardenGoal
import app.psykit.core.model.PetState
import app.psykit.core.model.Plot
import app.psykit.core.model.PlotStage
import app.psykit.core.model.Smoothie
import app.psykit.sprites.Sprite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Spalte und Reihe von Beet [i] im Gartenbild (Anteile von Breite und Höhe). */
private fun bedCenter(i: Int): Offset = Offset(0.2f + (i % 3) * 0.3f, if (i < 3) 0.6f else 0.84f)
private const val BED_W = 0.25f
private const val BED_H = 0.13f

/**
 * Der Garten als Bild: alle Beete liegen sichtbar im Boden. Tippst du ein Beet an, läuft dein Tierchen hin
 * und kümmert sich um genau dieses Beet (Schnecke wegtragen, jäten, ernten, säen oder gießen).
 */
@Composable
fun GardenScene(vm: PetViewModel, s: PetState, sprite: Sprite?, seed: Berry, fertilizeMode: Boolean, onFertilized: () -> Unit, modifier: Modifier = Modifier, fixedHeight: androidx.compose.ui.unit.Dp? = 340.dp) {
    val zone = vm.repo.zone
    val now = s.lastUpdate
    val phase = DayPhase.at(now, zone)
    val beds = GardenEngine.beds(s)
    val today = PetEngine.epochDay(now, zone)
    val raining = GardenEngine.rainy(s, today) && java.time.Instant.ofEpochMilli(now).atZone(zone).hour >= 8
    val rainTomorrow = GardenEngine.rainy(s, today + 1)
    val scope = rememberCoroutineScope()
    val petX = remember { Animatable(0.5f) }
    var busyBed by remember { mutableIntStateOf(-1) }
    var pouring by remember { mutableStateOf(false) }
    val anim = rememberInfiniteTransition(label = "garden")
    val t by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t")
    val bob by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    val pour = remember { Animatable(0f) }

    fun tapBed(i: Int) {
        if (busyBed >= 0) return
        if (i >= beds.size) { vm.unlockBed(); return }
        val p = beds[i]
        val st = GardenEngine.stage(p, System.currentTimeMillis(), s)
        busyBed = i
        scope.launch {
            petX.animateTo(bedCenter(i).x, tween(650, easing = FastOutSlowInEasing))
            when {
                p.snailSince > 0 -> vm.removeSnail(i)
                p.weedSince > 0 -> vm.weed(i)
                fertilizeMode && GardenEngine.growing(st) -> { vm.fertilize(i); onFertilized() }
                st == PlotStage.RIPE -> vm.harvest(i)
                st == PlotStage.EMPTY || st == PlotStage.WITHERED -> vm.plant(i, seed)
                else -> {
                    pouring = true
                    pour.snapTo(0f); pour.animateTo(1f, tween(1300, easing = LinearEasing))
                    vm.water(i)
                    pouring = false
                }
            }
            delay(250)
            busyBed = -1
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .then(if (fixedHeight != null) Modifier.height(fixedHeight) else Modifier.fillMaxHeight())
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(skyColors(phase)))
            .pointerInput(beds.size, busyBed, seed, fertilizeMode, s.lastUpdate) {
                detectTapGestures { pos ->
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val shown = minOf(GardenEngine.MAX_BEDS, beds.size + 1)
                    for (i in 0 until shown) {
                        val c = bedCenter(i)
                        if (kotlin.math.abs(pos.x / w - c.x) < BED_W / 2 + 0.02f && kotlin.math.abs(pos.y / h - c.y) < BED_H / 2 + 0.05f) { tapBed(i); break }
                    }
                }
            },
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // Wiese, Weg und Zaun
            drawRect(Color(0xFF9ED38A), Offset(0f, h * 0.36f), Size(w, h * 0.64f))
            drawRect(Color(0xFFE3CFA3), Offset(0f, h * 0.38f), Size(w, h * 0.1f))
            for (k in 0..12) {
                val x = w * k / 12f
                drawRect(Color(0xFFF4EBDD), Offset(x - 3f, h * 0.27f), Size(6f, h * 0.1f))
            }
            drawRect(Color(0xFFF4EBDD), Offset(0f, h * 0.3f), Size(w, 5f))
            val shown = minOf(GardenEngine.MAX_BEDS, beds.size + 1)
            for (i in 0 until shown) {
                val c = bedCenter(i)
                val topLeft = Offset((c.x - BED_W / 2) * w, (c.y - BED_H / 2) * h)
                val bedSize = Size(BED_W * w, BED_H * h)
                if (i >= beds.size) {
                    drawRoundRect(Color(0x55795548), topLeft, bedSize, CornerRadius(18f))
                    drawRoundRect(Color(0x88795548), topLeft, bedSize, CornerRadius(18f), style = Stroke(4f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
                    continue
                }
                val p = beds[i]
                val st = GardenEngine.stage(p, now, s)
                drawRoundRect(if (p.fertilized) Color(0xFF5D4037) else Color(0xFF7B5236), topLeft, bedSize, CornerRadius(18f))
                // Furchen
                for (r in 1..2) drawLine(Color(0x33000000), Offset(topLeft.x + 10f, topLeft.y + bedSize.height * r / 3f), Offset(topLeft.x + bedSize.width - 10f, topLeft.y + bedSize.height * r / 3f), 3f)
                drawPlant(p, st, Offset(c.x * w, c.y * h), bedSize.width, bedSize.height, bob)
                if (p.weedSince > 0) drawWeed(Offset(topLeft.x + bedSize.width * 0.85f, topLeft.y + bedSize.height * 0.6f), bedSize.height)
                if (p.weedSince > 0) drawWeed(Offset(topLeft.x + bedSize.width * 0.12f, topLeft.y + bedSize.height * 0.75f), bedSize.height * 0.8f)
            }
            // Regen
            if (raining) for (k in 0 until 40) {
                val x = ((k * 97) % 100) / 100f * w
                val y = ((t + k / 40f) % 1f) * h
                drawLine(Color(0x995AB4F0), Offset(x, y), Offset(x - 4f, y + 18f), 3f)
            }
            // Wasser aus der Gießkanne
            if (pouring && busyBed >= 0) {
                val c = bedCenter(busyBed)
                for (k in 0 until 9) {
                    val ph = (pour.value * 5f + k / 9f) % 1f
                    drawCircle(Color(0xFF5AB4F0).copy(alpha = 1f - ph * 0.5f), 5f, Offset(c.x * w + 30f + (k % 3 - 1) * 8f, h * 0.45f + ph * (c.y * h - h * 0.45f)))
                }
            }
        }
        // Beschriftung, Schnecken, Schlösser
        val shown = minOf(GardenEngine.MAX_BEDS, beds.size + 1)
        for (i in 0 until shown) {
            val c = bedCenter(i)
            val x = with(density) { (c.x * wPx).toDp() }
            val y = with(density) { (c.y * hPx).toDp() }
            if (i >= beds.size) {
                val k = GardenEngine.nextBed(s)
                if (k != null) Text(
                    "🔒 ${GardenEngine.BED_PRICES[k]} ✨\nab Lvl ${GardenEngine.BED_LEVELS[k]}", fontSize = 11.sp, lineHeight = 13.sp,
                    modifier = Modifier.offset(x - 34.dp, y - 14.dp).width(68.dp), color = Color(0xFF4E342E),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                continue
            }
            val p = beds[i]
            if (p.snailSince > 0) Text("🐌", fontSize = 24.sp, modifier = Modifier.offset(x + 14.dp + (sin(t * 6.28f) * 2).dp, y - 6.dp))
            val st = GardenEngine.stage(p, now, s)
            // Kurz und mit Symbolen: Sorte, Gießstand, Wartezeit, Unkraut, Dünger
            val label = when (st) {
                PlotStage.EMPTY -> "frei"
                PlotStage.WITHERED -> "🥀 neu säen"
                PlotStage.RIPE -> "${p.kind.emoji} reif ×${GardenEngine.harvestCount(p)}"
                else -> {
                    val hrs = GardenEngine.hoursUntilGrowth(s, p, now)
                    val wait = if (hrs <= 0f) "💧 jetzt" else "⏳ " + GardenEngine.formatHours(hrs).removePrefix("etwa ").replace(" Stunden", " h").replace(" Stunde", " h").replace(" Minuten", " min")
                    "${p.kind.emoji} ${p.waterings}/${p.kind.needWaterings} · $wait"
                }
            } + (if (p.weedSince > 0) " 🌿" else "") + (if (p.fertilized && GardenEngine.growing(st)) " 🧪" else "")
            Text(
                label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2723),
                maxLines = 1,
                modifier = Modifier.offset(x - 52.dp, y + with(density) { (BED_H / 2 * hPx).toDp() }).width(104.dp)
                    .background(Color(0xBBFFFFFF), RoundedCornerShape(8.dp)),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (rainTomorrow && !raining) Text("☁️ Morgen Regen", fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xAAFFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        if (raining) Text("🌧️ Regentag: alles gegossen", fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xAAFFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        // Das Tierchen auf dem Weg
        val px = with(density) { (petX.value * wPx).toDp() }
        val py = with(density) { (hPx * 0.43f).toDp() }
        val hopping = busyBed >= 0 && !pouring
        Box(Modifier.offset(px - 38.dp, py - 70.dp - (if (hopping) (bob * 6).dp else 0.dp))) {
            PetIcon(sprite, s.stage, s.mood, 76.dp)
        }
        if (pouring) Text("🫗", fontSize = 34.sp, modifier = Modifier.offset(px + 14.dp, py - 46.dp).rotate(-25f + sin(pour.value * 20f) * 6f))
    }
}

/** Pflanze je nach Stadium zeichnen, Beeren in der Farbe der Sorte. */
private fun DrawScope.drawPlant(p: Plot, st: PlotStage, c: Offset, bw: Float, bh: Float, bob: Float) {
    val leaf = Color(0xFF4CAF50)
    val dark = Color(0xFF2E7D32)
    val berry = Color(p.kind.color)
    when (st) {
        PlotStage.EMPTY -> {}
        PlotStage.SEED -> {
            drawOval(Color(0xFF8D6E63), Offset(c.x - bw * 0.18f, c.y - bh * 0.12f), Size(bw * 0.36f, bh * 0.3f))
            drawCircle(Color(0xFFD7B98E), bh * 0.06f, Offset(c.x, c.y - bh * 0.02f))
        }
        PlotStage.SPROUT -> {
            drawLine(dark, Offset(c.x, c.y + bh * 0.1f), Offset(c.x, c.y - bh * 0.35f), 5f)
            drawOval(leaf, Offset(c.x - bh * 0.42f, c.y - bh * 0.5f), Size(bh * 0.42f, bh * 0.22f))
            drawOval(leaf, Offset(c.x, c.y - bh * 0.6f), Size(bh * 0.42f, bh * 0.22f))
        }
        PlotStage.BUSH, PlotStage.RIPE -> {
            val sway = (bob - 0.5f) * 3f
            listOf(-0.22f to 0f, 0.22f to 0f, 0f to -0.25f, -0.1f to -0.12f, 0.12f to -0.14f).forEach { (dx, dy) ->
                drawCircle(if (dy < -0.2f) leaf else dark, bh * 0.42f, Offset(c.x + bw * dx + sway, c.y - bh * 0.35f + bh * dy * 1.6f))
            }
            if (st == PlotStage.RIPE) {
                listOf(-0.25f to -0.2f, 0.18f to -0.3f, -0.05f to -0.55f, 0.28f to -0.05f, -0.15f to 0.05f, 0.05f to -0.15f).forEach { (dx, dy) ->
                    val o = Offset(c.x + bw * dx + sway, c.y - bh * 0.3f + bh * dy * 1.4f)
                    drawCircle(berry, bh * 0.13f, o)
                    drawCircle(Color.White.copy(alpha = 0.6f), bh * 0.04f, o + Offset(-bh * 0.04f, -bh * 0.04f))
                }
            }
        }
        PlotStage.WITHERED -> {
            val brown = Color(0xFF8D6E63)
            drawLine(brown, Offset(c.x, c.y + bh * 0.1f), Offset(c.x + bh * 0.2f, c.y - bh * 0.3f), 5f)
            drawOval(brown, Offset(c.x + bh * 0.1f, c.y - bh * 0.2f), Size(bh * 0.4f, bh * 0.16f))
            drawOval(brown, Offset(c.x - bh * 0.35f, c.y - bh * 0.05f), Size(bh * 0.4f, bh * 0.16f))
        }
    }
}

private fun DrawScope.drawWeed(at: Offset, h: Float) {
    val c = Color(0xFF33691E)
    for (k in -2..2) drawLine(c, at, Offset(at.x + k * h * 0.12f, at.y - h * (0.45f - kotlin.math.abs(k) * 0.08f)), 4f)
}

/** Unter dem Gartenbild: Saatgut, Dünger, Vorrat, Mixer und Überblick über alle Beete. */
@Composable
fun GardenPanel(s: PetState, vm: PetViewModel, seed: Berry, onSeed: (Berry) -> Unit, fertilizeMode: Boolean, onFertilizeMode: (Boolean) -> Unit) {
    var showMixer by remember { mutableStateOf(false) }
    // Runde 12: kompakt, ohne Scrollen. Zustand der Beete steht am Beet im Bild, Vorrat (naschen) liegt im Füttern-Modus.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🌱", fontSize = 18.sp)
        Berry.entries.forEach { b ->
            val price = when {
                b.seedPrice < 0 -> "${s.goldSeeds}×"
                b.seedPrice == 0 -> "gratis"
                else -> "${b.seedPrice}✨"
            }
            FilterChip(
                selected = seed == b, onClick = { onSeed(b) },
                label = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { BerryIcon(b, 20.dp); Text(price) } },
                enabled = b.seedPrice >= 0 || s.goldSeeds > 0,
            )
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = fertilizeMode, onClick = { onFertilizeMode(!fertilizeMode) }, enabled = s.fertilizer > 0 || fertilizeMode, label = { Text("🧪 ${s.fertilizer}  ♻️ ${s.compost}/${GardenEngine.COMPOST_PER_FERTILIZER}") })
        OutlinedButton(onClick = { showMixer = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("🥤 Mixer") }
        GardenEngine.nextBed(s)?.let { k ->
            OutlinedButton(
                onClick = { vm.unlockBed() }, enabled = s.level >= GardenEngine.BED_LEVELS[k] && s.stardust >= GardenEngine.BED_PRICES[k],
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) { Text("🔓 Beet ${k + 4} · ${GardenEngine.BED_PRICES[k]}✨") }
        }
    }
    if (fertilizeMode) Text("Pflanze antippen: +${GardenEngine.fertilizerPercent} % Wachstum.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    if (showMixer) MixerDialog(s, vm, onDismiss = { showMixer = false })
}

/** Mixer: bis zu 3 Beeren hineinlegen und mixen. Entdeckte Rezepte stehen im Rezeptbuch. */
@Composable
fun MixerDialog(s: PetState, vm: PetViewModel, onDismiss: () -> Unit) {
    val counts = remember { mutableStateOf(IntArray(4)) }
    val c = counts.value
    val total = c.sum()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🥤 Smoothie-Mixer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Leg 2 oder 3 Beeren in den Mixer. Unbekannte Mischungen einfach ausprobieren!", style = MaterialTheme.typography.bodySmall)
                Berry.entries.forEachIndexed { i, b ->
                    val have = GardenEngine.count(s, b)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BerryIcon(b, 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("${b.label} (hast $have)", Modifier.weight(1f))
                        TextButton(onClick = { counts.value = c.copyOf().also { it[i] = maxOf(0, it[i] - 1) } }, enabled = c[i] > 0) { Text("−") }
                        Text("${c[i]}", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { counts.value = c.copyOf().also { it[i] = it[i] + 1 } }, enabled = total < 3 && c[i] < have) { Text("+") }
                    }
                }
                if (s.psiBerries >= 2) OutlinedButton(onClick = { vm.brewPsiPotion(); onDismiss() }) { Text("💜→🧪 Psi-Trank brauen (2 Psi-Beeren)") }
                Text("Rezeptbuch", fontWeight = FontWeight.Bold)
                Smoothie.entries.forEach { sm ->
                    val known = sm.name in s.smoothiesFound
                    Text(if (known) "${sm.emoji} ${sm.label}: ${sm.recipe} → ${sm.effect}" else "❔ unbekanntes Rezept", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.mix(c[0], c[1], c[2], c[3]); counts.value = IntArray(4); onDismiss() }, enabled = total in 2..3) { Text("Mixen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

/** Garten-Seite im Sammelbuch: Sorten, Rezepte und Ziele. */
@Composable
fun GardenBookTab(s: PetState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (GardenEngine.isMasterGardener(s)) Text("🏅 Titel: Meistergärtner", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Ernten insgesamt: ${s.harvestsTotal}", fontWeight = FontWeight.Bold)
        Text("Sorten", fontWeight = FontWeight.Bold)
        Berry.entries.forEach { b ->
            val got = b.name in s.berriesHarvested
            Text(if (got) "${b.emoji} ${b.label}: ${b.needWaterings}× gießen, ${b.harvest} Beeren, verkauft je ${b.sellPrice} ✨" else "❔ noch nicht geerntet", style = MaterialTheme.typography.bodySmall)
        }
        Text("Smoothies (${s.smoothiesFound.size}/${Smoothie.entries.size})", fontWeight = FontWeight.Bold)
        Smoothie.entries.forEach { sm ->
            Text(if (sm.name in s.smoothiesFound) "${sm.emoji} ${sm.label}: ${sm.recipe}" else "❔ unbekannt", style = MaterialTheme.typography.bodySmall)
        }
        Text("Ziele", fontWeight = FontWeight.Bold)
        GardenGoal.entries.forEach { g ->
            val done = g.name in s.gardenRewards
            Text((if (done) "✅ " else "▫️ ") + "${g.label} → ${g.reward}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
