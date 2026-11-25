package app.psykit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.psykit.core.engine.DayPhase
import app.psykit.core.engine.LevelSystem
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Food
import app.psykit.core.model.GameKind
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import app.psykit.sprites.SpriteVariant
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Was man gerade mit dem Tierchen macht. */
enum class Mode(val icon: String, val label: String) {
    FEED("🍙", "Füttern"), STROKE("🤚", "Streicheln"), CLEAN("🧽", "Putzen"),
    SLEEP("🌙", "Schlafen"), PLAY("🎲", "Spielen"), BONUS("🫐", "Bonus"),
    GARDEN("🌱", "Garten"), QUESTS("📋", "Aufgaben"), DUNGEON("⚔️", "Dungeon"), SHOP("🛍️", "Laden"),
}

@Composable
fun PetScreen(vm: PetViewModel) {
    val s by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val stepsToday by vm.stepsToday.collectAsState()
    val reaction by vm.reaction.collectAsState()
    val watering by vm.watering.collectAsState()
    val floating by vm.floating.collectAsState()
    val spriteVersion by vm.spriteVersion.collectAsState()
    val headPoint by vm.headPoint.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(Mode.STROKE) }
    var game by remember { mutableStateOf<GameKind?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPets by remember { mutableStateOf(false) }
    var showDungeon by remember { mutableStateOf(false) }
    var showShop by remember { mutableStateOf(false) }
    var showBook by remember { mutableStateOf(false) }
    var stepPermission by remember { mutableStateOf(vm.stepSensor.hasPermission()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        stepPermission = vm.stepSensor.hasPermission()
        if (stepPermission) vm.startSteps()
    }
    fun requestPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
    }
    LaunchedEffect(Unit) {
        vm.greet()
        if (!askedOnce) { askedOnce = true; requestPermissions() }
    }

    val phase = DayPhase.at(s.lastUpdate, vm.repo.zone)
    val variant = SpriteVariant.forMood(s.mood)
    val sprite = remember(s.stage, variant, spriteVersion) { vm.sprites.load(s.stage, variant) }
    val isEgg = s.stage == Stage.EGG
    val current by rememberUpdatedState(s)

    // Geometrie für Drag & Drop (alles in Root-Koordinaten)
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var sceneTopLeft by remember { mutableStateOf(Offset.Zero) }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }

    // ---------- Füttern ----------
    var dragFood by remember { mutableStateOf<Food?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    // ---------- Streicheln ----------
    var handPos by remember { mutableStateOf<Offset?>(null) }
    var strokeHint by remember { mutableStateOf("") }

    // ---------- Putzen ----------
    var cleanSession by remember { mutableIntStateOf(0) }
    // Putzwerkzeug: 0 = Schwamm, 1 = Dusche, 2 = Schaufel (nur sie räumt Häufchen weg)
    var tool by remember { mutableIntStateOf(0) }
    val shower = tool == 1
    var toolPos by remember { mutableStateOf<Offset?>(null) }
    val spotCount = remember(cleanSession) { PetEngine.dirtSpots(s) }
    val spots = remember(cleanSession) { dirtSpotPositions(spotCount, s.bornAt + cleanSession * 7919L) }
    val foam = remember(cleanSession) { FloatArray(spotCount) }
    val cleaned = remember(cleanSession) { BooleanArray(spotCount) }
    var cleanTick by remember { mutableIntStateOf(0) }
    var lastPoop by remember { mutableStateOf(0L) }
    var cleanDone by remember(cleanSession) { mutableStateOf(false) }
    fun finishCleaning() {
        if (cleanDone) return
        cleanDone = true
        if (spotCount > 0) vm.clean(cleaned.count { it }.toFloat() / spotCount)
        else if (current.poops == 0) vm.clean(1f)
    }

    // ---------- Schlafen ----------
    var blanket by remember { mutableFloatStateOf(0f) }
    var light by remember { mutableFloatStateOf(1f) }
    var hummed by remember { mutableStateOf(false) }
    var humming by remember { mutableStateOf(false) }
    fun startHumming() {
        if (humming) return
        humming = true
        vm.say("🎵 Summ jetzt ein Schlaflied … ich höre zu.")
        scope.launch {
            val ok = detectHumming(ctx)
            humming = false
            hummed = hummed || ok
            vm.say(if (ok) "${current.name} hört dir zu und gähnt ganz groß." else "Ich habe kein Summen gehört. Etwas lauter und länger summen.")
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startHumming() else vm.say("Ohne Mikrofon geht es auch, dann dauert das Einschlafen nur etwas länger.")
    }
    LaunchedEffect(blanket >= 0.8f && light <= 0.3f, mode) {
        if (mode == Mode.SLEEP && blanket >= 0.8f && light <= 0.3f && !current.sleeping && !isEgg) {
            vm.putToBed(hummed)
            if (!vm.state.value.sleeping) blanket = 0.3f
            hummed = false
        }
    }
    LaunchedEffect(mode) {
        handPos = null; toolPos = null; strokeHint = ""
        if (mode == Mode.CLEAN) cleanSession++
        if (mode != Mode.SLEEP) { light = 1f; if (!s.sleeping) blanket = 0f } else if (s.sleeping) { blanket = 1f; light = 0.1f }
    }

    fun local(root: Offset) = root - sceneTopLeft

    val sceneInteraction: Modifier = when (mode) {
        Mode.STROKE -> Modifier
            .pointerInput(Mode.STROKE) {
                detectTapGestures(onLongPress = { p ->
                    val z = zoneAt(p, size)
                    if (z != null) vm.scratch(z) else vm.say("Halte den Finger direkt auf ${current.name}, um zu kraulen.")
                })
            }
            .pointerInput("stroke") {
                var dist = 0f
                var start = 0L
                var last = Offset.Zero
                detectDragGestures(
                    onDragStart = { p -> dist = 0f; start = System.currentTimeMillis(); last = p; handPos = p },
                    onDrag = { change, amount ->
                        change.consume()
                        val p = change.position
                        if (petBoxPos(p, size) != null) dist += amount.getDistance()
                        last = p; handPos = p
                        val secs = (System.currentTimeMillis() - start).coerceAtLeast(1) / 1000f
                        val sp = dist / density / secs
                        strokeHint = when {
                            dist / density < 20f -> ""
                            sp > PetEngine.STROKE_FAST -> "zu schnell!"
                            sp < PetEngine.STROKE_SLOW -> "schön sanft …"
                            else -> "okay"
                        }
                    },
                    onDragEnd = {
                        val dp = dist / density
                        val secs = (System.currentTimeMillis() - start).coerceAtLeast(1) / 1000f
                        if (dp >= 40f) vm.stroke(dp / secs) else vm.say("Streichle mit dem Finger über ${current.name}.")
                        handPos = null; strokeHint = ""
                    },
                    onDragCancel = { handPos = null; strokeHint = "" },
                )
            }
        Mode.CLEAN -> Modifier.pointerInput(cleanSession, tool) {
            val radius = 46.dp.toPx()
            detectDragGestures(
                onDragStart = { p -> toolPos = p },
                onDrag = { change, amount ->
                    change.consume()
                    val p = change.position
                    toolPos = p
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    spots.forEachIndexed { i, n ->
                        if (cleaned[i]) return@forEachIndexed
                        val d = (Offset(n.x * w, n.y * h) - p).getDistance()
                        if (d < radius) {
                            if (tool == 0) foam[i] = (foam[i] + amount.getDistance() / (radius * 5f)).coerceAtMost(1f)
                            else if (tool == 1 && foam[i] >= 1f) cleaned[i] = true
                        }
                    }
                    if (tool == 2) {
                        for (i in 0 until current.poops) {
                            val n = poopPosition(i)
                            val now = System.currentTimeMillis()
                            if (now - lastPoop > 500 && (Offset(n.x * w, n.y * h) - p).getDistance() < radius) { lastPoop = now; vm.removePoop(); break }
                        }
                    }
                    cleanTick++
                    if (spotCount > 0 && cleaned.all { it }) finishCleaning()
                },
                onDragEnd = { toolPos = null },
                onDragCancel = { toolPos = null },
            )
        }
        Mode.SLEEP -> Modifier.pointerInput("blanket") {
            detectDragGestures { change, amount ->
                change.consume()
                if (!current.sleeping) blanket = (blanket - amount.y / (size.height * 0.55f)).coerceIn(0f, 1f)
            }
        }
        else -> Modifier
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionInRoot() }) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(s, onPets = { showPets = true }, onInfo = { showStats = true }, onSettings = { showSettings = true })
            if (!isEgg) StatRings(s)

            PetScene(
                stage = s.stage, mood = s.mood, sleeping = s.sleeping, poops = s.poops, phase = phase,
                custom = sprite, customIsVariant = sprite?.exact == true && variant != SpriteVariant.BASE,
                reaction = reaction,
                modifier = Modifier.onGloballyPositioned {
                    val b = it.boundsInRoot()
                    sceneTopLeft = b.topLeft
                    sceneSize = it.size
                },
                interaction = if (isEgg) Modifier else sceneInteraction,
                dim = if (mode == Mode.SLEEP) 1f - light else 0f,
                floating = floating,
                wear = Wearables.of(s, headPoint),
                overlay = {
                    when (mode) {
                        Mode.STROKE -> handPos?.let { p ->
                            HandEmoji("✋", p)
                            if (strokeHint.isNotEmpty()) Text(
                                strokeHint, fontWeight = FontWeight.Bold,
                                color = if (strokeHint == "zu schnell!") Color(0xFFE5484D) else Color(0xFF5B3FA6),
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                            )
                        }
                        Mode.CLEAN -> {
                            CleanOverlay(spots, foam, cleaned, cleanTick)
                            toolPos?.let { p -> HandEmoji(when (tool) { 1 -> "🚿"; 2 -> "🧹"; else -> "🧽" }, p) }
                        }
                        Mode.SLEEP -> Blanket(blanket)
                        Mode.GARDEN -> WateringOverlay(watering)
                        else -> {}
                    }
                },
            )

            Text(
                message.ifEmpty { statusLine(s) },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            )

            if (!isEgg) {
                ModeBar(mode) { mode = it }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (mode) {
                            Mode.FEED -> FoodTray(
                                s = s,
                                dragging = dragFood,
                                onStart = { f, root -> dragFood = f; dragPos = root },
                                onMove = { dragPos += it },
                                onDrop = {
                                    val f = dragFood
                                    dragFood = null
                                    if (f != null) {
                                        if (mouthHit(local(dragPos), sceneSize)) vm.feed(f)
                                        else vm.say("Zieh das Essen bis an ${s.name}s Mund.")
                                    }
                                },
                            )
                            Mode.STROKE -> {
                                Text("Streichle mit dem Finger über ${s.name}. Langsam beruhigt, zu schnell nervt.", style = MaterialTheme.typography.bodyMedium)
                                Text("Finger gedrückt halten = kraulen. Irgendwo ist heute die geheime Lieblingsstelle …", style = MaterialTheme.typography.bodySmall)
                                StatBar("🤝 Vertrauen", s.trust)
                            }
                            Mode.CLEAN -> {
                                Text(
                                    when {
                                        spotCount == 0 && s.poops == 0 -> "${s.name} ist schon sauber."
                                        spotCount == 0 -> "Räum die Häufchen mit der Schaufel weg: einfach drüberwischen."
                                        else -> "Erst mit dem Schwamm einschäumen, bis Schaum entsteht, dann abduschen. Noch ${cleaned.count { !it }} von $spotCount Flecken." +
                                            if (s.poops > 0) " Häufchen kommen mit der Schaufel weg." else ""
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FilterChip(selected = tool == 0, onClick = { tool = 0 }, label = { Text("🧽") })
                                    FilterChip(selected = tool == 1, onClick = { tool = 1 }, label = { Text("🚿") })
                                    FilterChip(selected = tool == 2, onClick = { tool = 2 }, label = { Text("🧹 Schaufel") })
                                    Spacer(Modifier.weight(1f))
                                    OutlinedButton(onClick = { finishCleaning(); cleanSession++ }) { Text("Fertig") }
                                }
                                if (s.dirt > 0) Text("👟 Vom Spazierengehen schmutzig: ${s.dirt}", style = MaterialTheme.typography.bodySmall)
                            }
                            Mode.SLEEP -> {
                                if (s.sleeping) {
                                    Text("${s.name} schläft. Psst!", style = MaterialTheme.typography.bodyMedium)
                                    Button(onClick = { vm.wake(); blanket = 0f; light = 1f }) { Text("☀️ Wecken") }
                                } else {
                                    Text("Zieh die Decke von unten über ${s.name} und dreh das Licht runter.", style = MaterialTheme.typography.bodyMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("💡", Modifier.width(32.dp))
                                        Slider(value = light, onValueChange = { light = it }, modifier = Modifier.weight(1f))
                                    }
                                    OutlinedButton(onClick = {
                                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startHumming()
                                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }, enabled = !humming) {
                                        Text(if (humming) "🎵 Ich höre zu …" else if (hummed) "🎵 Gesummt ✓" else "🎵 Schlaflied summen (optional)")
                                    }
                                }
                            }
                            Mode.PLAY -> {
                                Text("Such dir ein Spiel aus:", fontWeight = FontWeight.Bold)
                                GameKind.daily.forEach { g ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(g.label, fontWeight = FontWeight.Bold)
                                            Text(gameInfo(g) + " Trainiert ${g.trains}.", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        val ok = g != GameKind.VISIT || vm.allPets().count { it.second.stage != Stage.EGG } >= 2
                                        Button(onClick = { if (vm.checkPlay() == null) game = g }, enabled = ok) { Text("Spielen") }
                                    }
                                }
                            }
                            Mode.GARDEN -> GardenPanel(s, vm)
                            Mode.QUESTS -> QuestPanel(s, vm)
                            Mode.DUNGEON -> DungeonPanel(s, onOpen = { showDungeon = true })
                            Mode.SHOP -> ShopPanel(s, onShop = { showShop = true }, onBook = { showBook = true })
                            Mode.BONUS -> {
                                Text("Beerenfang", fontWeight = FontWeight.Bold)
                                Text(gameInfo(GameKind.BERRIES) + " Trainiert ${GameKind.BERRIES.trains}.", style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { if (vm.checkPlay() == null) game = GameKind.BERRIES }) { Text("Starten") }
                            }
                        }
                    }
                }
            }

            StepsCard(
                available = vm.stepSensor.available,
                permission = stepPermission,
                stepsToday = stepsToday,
                pending = s.pendingSteps,
                onAllow = { requestPermissions() },
                onRedeem = vm::redeemSteps,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Essen, das gerade gezogen wird, schwebt über allem
        dragFood?.let { f ->
            val p = dragPos - rootPos
            Text(
                f.emoji, fontSize = 44.sp,
                modifier = Modifier.offset { IntOffset((p.x - 28.dp.toPx()).roundToInt(), (p.y - 34.dp.toPx()).roundToInt()) },
            )
        }
    }

    game?.let { g ->
        GameDialog(
            kind = g, vm = vm, s = s, sprite = sprite,
            onFinish = { score, max, bonus -> vm.finishGame(g, score, max, bonus); game = null },
            onCancel = { game = null },
        )
    }
    if (showStats) StatsDialog(s, onDismiss = { showStats = false })
    if (showSettings) SettingsDialog(vm, s, onDismiss = { showSettings = false })
    if (showPets) PetsDialog(vm, onDismiss = { showPets = false })
    if (showDungeon) DungeonDialog(vm, s, sprite, onClose = { showDungeon = false })
    if (showShop) ShopDialog(vm, s, onClose = { showShop = false })
    if (showBook) CollectionDialog(vm, s, sprite, onClose = { showBook = false })
}

private fun gameInfo(k: GameKind) = when (k) {
    GameKind.HIDE_SEEK -> "Dein Tierchen versteckt sich hinter einer von drei Wolken. Die Wolken mischen sich, du findest es."
    GameKind.MEMORY -> "Dreh Karten um und finde die passenden Psi-Symbole."
    GameKind.BUBBLES -> "Bring in 30 Sekunden so viele Seifenblasen wie möglich zum Platzen."
    GameKind.BERRIES -> "Kipp dein Handy, damit dein Tierchen die fallenden Beeren fängt. Goldene Beeren geben Extra-EP."
    GameKind.REACTION -> "Tippe so schnell wie möglich, wenn dein Tierchen lila aufleuchtet. Bei rotem Leuchten nicht tippen!"
    GameKind.ECHO -> "Merk dir die Farbfolge und spiel sie nach. Jede Runde wird sie länger."
    GameKind.SHADOW -> "Erkenne am Schatten, was es ist."
    GameKind.COOK -> "Merk dir das Rezept und koch es nach. Das Essen bekommt dein Tierchen danach zu futtern."
    GameKind.CLOUDS -> "Hüpf im richtigen Moment von Wolke zu Wolke nach oben."
    GameKind.VISIT -> "Zwei deiner Tierchen spielen zusammen Ball. Beide bekommen Vertrauen und gute Laune. Braucht mindestens 2 Tierchen."
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.HandEmoji(emoji: String, p: Offset) {
    Text(
        emoji, fontSize = 36.sp,
        modifier = Modifier.offset { IntOffset((p.x - 22.dp.toPx()).roundToInt(), (p.y - 26.dp.toPx()).roundToInt()) },
    )
}

@Composable
private fun CleanOverlay(spots: List<Offset>, foam: FloatArray, cleaned: BooleanArray, tick: Int) {
    Canvas(Modifier.fillMaxSize()) {
        if (tick < 0) return@Canvas
        val r = size.height * 0.045f
        spots.forEachIndexed { i, n ->
            val c = Offset(n.x * size.width, n.y * size.height)
            if (cleaned[i]) {
                drawPath(starPath(c, r * 0.7f), Color(0xFFFFF4A8))
                drawPath(starPath(c, r * 0.7f), Color(0xFFFFC94A), style = Stroke(2f))
            } else {
                drawCircle(Color(0xFF7A5A3A).copy(alpha = 0.55f * (1f - foam[i] * 0.6f)), r, c)
                drawCircle(Color(0xFF7A5A3A).copy(alpha = 0.35f * (1f - foam[i] * 0.6f)), r * 0.6f, c + Offset(r * 0.8f, -r * 0.4f))
                if (foam[i] > 0f) {
                    val bubbles = (foam[i] * 7).toInt() + 1
                    for (b in 0 until bubbles) {
                        val a = b * 2.3f
                        val o = Offset(kotlin.math.cos(a) * r * 0.9f, kotlin.math.sin(a) * r * 0.7f)
                        drawCircle(Color.White.copy(alpha = 0.92f), r * (0.35f + 0.1f * (b % 3)), c + o)
                        drawCircle(Color(0xFFB8E3FF), r * (0.35f + 0.1f * (b % 3)), c + o, style = Stroke(1.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Blanket(progress: Float) {
    if (progress <= 0.01f) {
        Text("⬆️ Decke hochziehen", fontWeight = FontWeight.Bold, color = Color(0xFF5B3FA6),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).alpha(0.8f))
        return
    }
    Canvas(Modifier.fillMaxSize()) {
        val top = size.height * (1f - 0.62f * progress)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF8C7BE0), Color(0xFF5B4BB8)), startY = top, endY = size.height),
            topLeft = Offset(size.width * 0.12f, top),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height - top + 30f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f, 40f),
        )
        // Saum und Sternchen-Muster
        drawRoundRect(
            Color(0xFFEDE6FF), topLeft = Offset(size.width * 0.12f, top),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.045f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f, 30f),
        )
        var y = top + size.height * 0.11f
        var row = 0
        while (y < size.height) {
            var x = size.width * (0.2f + (row % 2) * 0.07f)
            while (x < size.width * 0.85f) {
                drawPath(starPath(Offset(x, y), size.height * 0.018f), Color(0xFFFFE89A).copy(alpha = 0.8f))
                x += size.width * 0.14f
            }
            y += size.height * 0.1f; row++
        }
    }
}

@Composable
private fun ModeBar(mode: Mode, onSelect: (Mode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Mode.entries.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    val sel = m == mode
                    val mod = Modifier.weight(1f).height(60.dp)
                    val content: @Composable () -> Unit = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(m.icon, style = MaterialTheme.typography.titleMedium)
                            Text(m.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                        }
                    }
                    val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    if (sel) Button(onClick = { onSelect(m) }, modifier = mod, contentPadding = pad) { content() }
                    else FilledTonalButton(onClick = { onSelect(m) }, modifier = mod, contentPadding = pad) { content() }
                }
            }
        }
    }
}

@Composable
private fun FoodTray(
    s: PetState,
    dragging: Food?,
    onStart: (Food, Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onDrop: () -> Unit,
) {
    Text("Zieh ein Essen in ${s.name}s Mund. Manches mag ${s.name} besonders, manches gar nicht. Zu viel Süßes macht träge.", style = MaterialTheme.typography.bodyMedium)
    Food.entries.filter { it != Food.GARDEN || s.berryStock > 0 }.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { f ->
                var itemPos by remember { mutableStateOf(Offset.Zero) }
                val badge = when (f) {
                    Food.GARDEN -> "×${s.berryStock}"
                    in s.knownLikes -> "❤️"
                    in s.knownDislikes -> "👎"
                    else -> ""
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(68.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                        .border(if (f.sweet) 2.dp else 0.dp, if (f.sweet) Color(0xFFFF9BC8) else Color.Transparent, RoundedCornerShape(16.dp))
                        .onGloballyPositioned { itemPos = it.positionInRoot() }
                        .pointerInput(f) {
                            detectDragGestures(
                                onDragStart = { p -> onStart(f, itemPos + p) },
                                onDrag = { change, amount -> change.consume(); onMove(amount) },
                                onDragEnd = onDrop,
                                onDragCancel = onDrop,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(f.emoji, fontSize = 28.sp, modifier = Modifier.alpha(if (dragging == f) 0.3f else 1f))
                        Text(f.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    if (badge.isNotEmpty()) Text(badge, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (s.sluggish) Text("🍬 ${s.name} ist gerade träge von den Süßigkeiten.", style = MaterialTheme.typography.bodySmall)
}

private fun statusLine(s: PetState): String = when (s.stage) {
    Stage.EGG -> "Ein Ei! Es schlüpft in ein paar Minuten …"
    else -> "${s.name} ${s.mood.label}."
}

private fun ageText(s: PetState): String {
    val days = TimeUnit.MILLISECONDS.toDays(s.lastUpdate - s.bornAt)
    return if (days == 0L) "heute geboren" else if (days == 1L) "1 Tag alt" else "$days Tage alt"
}

@Composable
private fun Header(s: PetState, onPets: () -> Unit, onInfo: () -> Unit, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                if (s.stage == Stage.EGG) "Ei · ${ageText(s)}" else "${s.stage.displayName} · Level ${s.level} · ${ageText(s)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (s.stage != Stage.EGG) {
                val need = LevelSystem.expForNext(s.level)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (s.exp.toFloat() / need).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    strokeCap = StrokeCap.Round,
                )
                Text("${s.exp} / $need EP", style = MaterialTheme.typography.labelSmall)
            }
        }
        IconButton(onClick = onPets) { Text("🐾", fontSize = 22.sp) }
        IconButton(onClick = onInfo) { Icon(Icons.Filled.Info, contentDescription = "Werte") }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen") }
    }
}

@Composable
fun StatBar(label: String, value: Float) {
    val color = when {
        value < 25f -> Color(0xFFE5484D)
        value < 50f -> Color(0xFFF5A524)
        else -> Color(0xFF46A758)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier.weight(1f).height(10.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        Text("${value.toInt()}", Modifier.width(36.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StepsCard(
    available: Boolean,
    permission: Boolean,
    stepsToday: Long,
    pending: Long,
    onAllow: () -> Unit,
    onRedeem: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("👟 Spaziergang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                !available -> Text("Dein Handy hat leider keinen Schrittzähler-Sensor.")
                !permission -> {
                    Text("Erlaube den Zugriff auf deine Schritte. Je 100 Schritte gibt es 1 EP. Nach längeren Spaziergängen wird dein Tierchen aber schmutzig.")
                    Button(onClick = onAllow) { Text("Schrittzähler erlauben") }
                }
                else -> {
                    Text("Heute gezählt: $stepsToday")
                    Text("Bereit zum Einlösen: $pending (= ${pending / 100} EP)")
                    Button(onClick = onRedeem, enabled = pending >= 100) { Text("Einlösen") }
                    Text(
                        "Alle ${PetEngine.STEPS_PER_DIRT} Schritte gibt es einen Schmutzfleck zum Putzen.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** Statuswerte als Ringe um ihr Symbol, direkt unter der EP-Leiste. */
@Composable
private fun StatRings(s: PetState) {
    val stats = listOf(
        Triple("🍙", "Sattheit", s.satiety), Triple("💖", "Laune", s.happiness), Triple("🫧", "Hygiene", s.hygiene),
        Triple("⚡", "Energie", s.energy), Triple("➕", "Gesundheit", s.health), Triple("🤝", "Vertrauen", s.trust),
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            stats.forEach { (icon, label, value) ->
                val v = value.coerceIn(0f, 100f)
                val color = when { v < 25f -> Color(0xFFE5484D); v < 50f -> Color(0xFFF5A524); else -> Color(0xFF46A758) }
                val track = MaterialTheme.colorScheme.surfaceVariant
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = 4.5.dp.toPx()
                            val inset = sw / 2
                            val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw))
                            drawArc(color, -90f, 360f * v / 100f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                        }
                        Text(icon, fontSize = 18.sp)
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, fontSize = 10.sp)
                    Text("${v.toInt()}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
