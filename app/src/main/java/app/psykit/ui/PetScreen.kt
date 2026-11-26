package app.psykit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import app.psykit.R
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import app.psykit.core.model.Berry
import app.psykit.core.model.Food
import app.psykit.core.model.GameKind
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import app.psykit.core.model.Zone
import app.psykit.sprites.SpriteVariant
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Was man gerade mit dem Tierchen macht. */
enum class Mode(val icon: String, val label: String) {
    FEED("🍙", "Füttern"), STROKE("🤚", "Streicheln"), CLEAN("🧽", "Putzen"),
    SLEEP("🌙", "Schlafen"), PLAY("🎲", "Spielen"),
    GARDEN("🌱", "Garten"), QUESTS("📋", "Aufgaben"), DUNGEON("⚔️", "Dungeon"), SHOP("🛍️", "Laden"),
}

@Composable
fun PetScreen(vm: PetViewModel) {
    val s by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val stepsToday by vm.stepsToday.collectAsState()
    val reaction by vm.reaction.collectAsState()
    val floating by vm.floating.collectAsState()
    val spriteVersion by vm.spriteVersion.collectAsState()
    val packHead by vm.headPoint.collectAsState()
    val headVersion by vm.headVersion.collectAsState()
    val hasTailSetting by vm.hasTail.collectAsState()
    val saveLocked by vm.currentLocked.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(Mode.STROKE) }
    var gardenSeed by remember { mutableStateOf(Berry.BLUE) }
    var fertilizeMode by remember { mutableStateOf(false) }
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
    fun notificationsGranted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notifyPermission by remember { mutableStateOf(notificationsGranted()) }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifyPermission = notificationsGranted() }
    /** Erster Start: beide Freigaben einmal anfragen. */
    fun requestPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
    }
    /** Befund 01: Die Schritt-Schaltfläche fragt nur nach Schritten. */
    fun requestStepPermission() {
        if (Build.VERSION.SDK_INT >= 29) permLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
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
    // Punkte je Stufe (Runde 3), sonst die des Pakets
    val headPoint = remember(s.stage, headVersion, packHead) { vm.headFor(s.stage) }

    // Befund 06: Körperzonen aus dem sichtbaren Umriss des eigenen Bildes (null = eingebaute Figur)
    val density = LocalDensity.current.density
    // Runde 5: Tipp auf einen Zustandsring zeigt einen Hinweis in der Textzeile, nach 4 s wieder weg
    var statHint by remember { mutableStateOf("") }
    LaunchedEffect(statHint) {
        if (statHint.isNotEmpty()) { kotlinx.coroutines.delay(4000); statHint = "" }
    }
    val footprint = rememberFootprint(sprite)

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
    var scratchPos by remember { mutableStateOf<Offset?>(null) }
    var scratchLabel by remember { mutableStateOf("") }

    // ---------- Putzen ----------
    var cleanSession by remember { mutableIntStateOf(0) }
    // Putzwerkzeug: 0 = Schwamm, 1 = Dusche, 2 = Schaufel (nur sie räumt Häufchen weg)
    var tool by remember { mutableIntStateOf(0) }
    val shower = tool == 1
    var toolPos by remember { mutableStateOf<Offset?>(null) }
    val spotCount = remember(cleanSession) { PetEngine.dirtSpots(s) }
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

    // Befund 16: passendes Werkzeug vorschlagen und den nächsten Schritt in einem Satz nennen
    val cleanTickNow = cleanTick
    val suggestedTool = when {
        spotCount > 0 && (0 until spotCount).any { !cleaned[it] && foam[it] < 1f } -> 0
        spotCount > 0 && cleaned.any { !it } -> 1
        s.poops > 0 -> 2
        else -> -1
    }
    LaunchedEffect(suggestedTool, cleanSession) { if (suggestedTool >= 0) tool = suggestedTool }
    val cleanNextStep = when {
        cleanTickNow < 0 -> ""
        spotCount == 0 && s.poops == 0 -> "${s.name} ist schon sauber."
        suggestedTool == 0 -> "Schwamm: über die Flecken wischen, bis Schaum entsteht. Noch ${cleaned.count { !it }} von $spotCount."
        suggestedTool == 1 -> "Dusche: den Schaum abspülen. Noch ${cleaned.count { !it }} von $spotCount."
        suggestedTool == 2 -> "Schaufel: über die Häufchen wischen (${s.poops})."
        else -> "Fertig sauber! Tippe auf „Fertig“."
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
    // Zurück-Taste führt aus Garten und Unteransichten ins Zuhause
    androidx.activity.compose.BackHandler(enabled = mode != Mode.STROKE && mode != Mode.FEED && mode != Mode.CLEAN && mode != Mode.SLEEP) { mode = Mode.STROKE }
    LaunchedEffect(mode) {
        handPos = null; toolPos = null; strokeHint = ""
        if (mode == Mode.CLEAN) cleanSession++
        if (mode != Mode.SLEEP) { light = 1f; if (!s.sleeping) blanket = 0f } else if (s.sleeping) { blanket = 1f; light = 0.1f }
    }

    fun local(root: Offset) = root - sceneTopLeft

    val body = remember(footprint, headPoint, sceneSize, floating, mode) {
        if (mode == Mode.GARDEN) null else SceneBody.of(footprint, headPoint, sceneSize.width.toFloat(), sceneSize.height.toFloat(), density, floating, hasTailSetting)
    }
    val bodyNow by rememberUpdatedState(body)
    /** Zone unter dem Finger: bei eigenen Bildern nur auf sichtbaren Pixeln. */
    fun zoneHere(p: Offset, size: IntSize): Zone? { val b = bodyNow; return if (b != null) b.zoneAt(p) else zoneAt(p, size) }
    // Schmutzflecken liegen bei eigenen Bildern auf sichtbaren Körperpixeln (Befund 06), Anteile der Szene
    val spots = remember(cleanSession, body) {
        val seed = s.bornAt + cleanSession * 7919L
        val b = body
        if (b != null && sceneSize.width > 0 && sceneSize.height > 0) b.map.bodyPoints(spotCount, seed).map { p ->
            val o = b.toScene(p); Offset(o.x / sceneSize.width, o.y / sceneSize.height)
        } else dirtSpotPositions(spotCount, seed)
    }
    fun scratchAt(z: Zone) { val m = bodyNow?.map; vm.scratch(z, m?.let { map -> { a: Zone, t: Zone -> map.hint(a, t) } }, m?.zones ?: Zone.entries) }

    val sceneInteraction: Modifier = when (mode) {
        Mode.STROKE -> Modifier
            .pointerInput(Mode.STROKE) {
                detectTapGestures(
                    onPress = { p ->
                        // Schon beim Auflegen zeigen, welche Stelle unter dem Finger liegt
                        scratchLabel = zoneHere(p, size)?.label ?: ""
                        scratchPos = p
                        tryAwaitRelease()
                        scratchPos = null; scratchLabel = ""
                    },
                    onLongPress = { p ->
                        val z = zoneHere(p, size)
                        if (z != null) scratchAt(z) else vm.say("Halte den Finger direkt auf ${current.name}, um zu kraulen.")
                    },
                )
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
        // Befund 35/15/16/17: Zuhause ohne vertikales Scrollen. Die Szene bekommt den Restplatz,
        // Pflegewerkzeuge liegen direkt darunter, die Aktionsleiste sitzt fest unten.
        val careMode = isEgg || mode == Mode.FEED || mode == Mode.STROKE || mode == Mode.CLEAN || mode == Mode.SLEEP
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactHeader(
                s, onPets = { showPets = true }, onInfo = { showStats = true }, onSettings = { showSettings = true },
                onShop = { mode = Mode.SHOP }, onQuests = { mode = Mode.QUESTS },
            )

            if (careMode) {
                PetScene(
                    stage = s.stage, mood = s.mood, sleeping = s.sleeping, poops = s.poops, phase = phase,
                    custom = sprite, customIsVariant = sprite?.exact == true && variant != SpriteVariant.BASE,
                    reaction = reaction,
                    sceneHeight = null,
                    modifier = Modifier.weight(1f).onGloballyPositioned {
                        val b = it.boundsInRoot()
                        sceneTopLeft = b.topLeft
                        sceneSize = it.size
                    },
                    interaction = if (isEgg) Modifier else sceneInteraction,
                    dim = if (mode == Mode.SLEEP) 1f - light else 0f,
                    floating = floating,
                    wear = Wearables.of(s, headPoint),
                    footprintIn = footprint,
                    showLandmarks = true,
                    overlay = {
                        // Zustandsringe am oberen Bildrand (Runde 3)
                        if (!isEgg) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) { MiniStats(s) { statHint = it } }
                        // Runde 4: Höhle (Dungeon) und Wegweiser (Garten) im linken freien Rand der Szene
                        // Runde 7: Höhle und Wegweiser sind Teil des Hintergrunds; hier nur die Tippflächen
                        if (!isEgg) LandmarkTargets(onDungeon = { showDungeon = true }, onGarden = { mode = Mode.GARDEN })
                        // Seitliche Leiste rechts im freien Rand neben der Figur (Runde 3)
                        if (!isEgg) Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 52.dp)) {
                            CareTray(
                                mode = mode, s = s, dragging = dragFood, tool = tool, onTool = { tool = it },
                                onDone = { finishCleaning(); cleanSession++ },
                                light = light, onLight = { light = it },
                                humming = humming, hummed = hummed,
                                onHum = {
                                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startHumming()
                                    else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onWake = { vm.wake(); blanket = 0f; light = 1f },
                                onFoodStart = { f, root -> dragFood = f; dragPos = root },
                                onFoodMove = { dragPos += it },
                                onFoodDrop = {
                                    val f = dragFood
                                    dragFood = null
                                    if (f != null) {
                                        val b = body
                                        val hit = if (b != null) b.mouthHit(local(dragPos), 56f * density) else mouthHit(local(dragPos), sceneSize)
                                        if (hit) vm.feed(f)
                                        else vm.say("Zieh das Essen bis an ${s.name}s Mund oder tipp es einfach an.")
                                    }
                                },
                                onFoodTap = { f -> vm.feed(f) },
                            )
                        }
                        when (mode) {
                            Mode.FEED -> if (!s.mouthSeen) body?.let { MouthMarker(it.mouth, dragFood != null) }
                            // Runde 7: Texte zum Streicheln/Kraulen stehen in der Textzeile unter der Szene
                            Mode.STROKE -> if (handPos == null) scratchPos?.let { p -> HandEmoji("🤏", p) } else handPos?.let { p -> HandEmoji("✋", p) }
                            Mode.CLEAN -> {
                                CleanOverlay(spots, foam, cleaned, cleanTick)
                                toolPos?.let { p -> HandEmoji(when (tool) { 1 -> "🚿"; 2 -> "🧹"; else -> "🧽" }, p) }
                            }
                            Mode.SLEEP -> Blanket(blanket)
                            else -> {}
                        }
                    },
                )
                // Während des Streichelns/Kraulens steht der Live-Hinweis hier statt über dem Bild
                val liveHint = when {
                    mode == Mode.STROKE && handPos == null && scratchPos != null && scratchLabel.isNotEmpty() -> "Kraulen: $scratchLabel …"
                    mode == Mode.STROKE && handPos != null && strokeHint.isNotEmpty() -> strokeHint
                    else -> ""
                }
                MessageText(liveHint.ifEmpty { statHint.ifEmpty { message.ifEmpty { statusLine(s) } } })
                // Feste Höhe für alle Pflegemodi: die Szene springt beim Wechsel nicht mehr (Runde 3)
                if (!isEgg) Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) {
                    when (mode) {
                        Mode.FEED -> Text("Futter rechts zum Mund ziehen oder antippen. Zu viel Süßes macht träge.", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Mode.STROKE -> ScratchSpots(s, vm.repo.zone, body?.map?.zones ?: Zone.entries)
                        Mode.CLEAN -> Text(cleanNextStep, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Mode.SLEEP -> Text(
                            if (s.sleeping) "${s.name} schläft. Psst!" else "Decke von unten hochziehen, rechts das Licht dimmen.",
                            style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        )
                        else -> {}
                    }
                }
            } else if (mode == Mode.GARDEN) {
                // Garten als eigener Raum: sichtbarer Eingang über „Garten“ unten, Rückweg hier. Kein Wischen auf der Szene.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { mode = Mode.STROKE }) { Text("← Nach Hause") }
                    Text("🌱 Garten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                GardenScene(
                    vm, s, sprite, gardenSeed, fertilizeMode,
                    onFertilized = { fertilizeMode = false },
                    modifier = Modifier.onGloballyPositioned {
                        val b = it.boundsInRoot()
                        sceneTopLeft = b.topLeft
                        sceneSize = it.size
                    },
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MessageText(message.ifEmpty { statusLine(s) })
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GardenPanel(s, vm, gardenSeed, { gardenSeed = it }, fertilizeMode, { fertilizeMode = it })
                        }
                    }
                }
            } else {
                // Spiele, Aufgaben, Dungeon, Laden, Bonus: eigene Ansicht statt der Szene (Befund 35 erlaubt das)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MessageText(message.ifEmpty { statusLine(s) })
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (mode) {
                                Mode.PLAY -> {
                                    Text("Such dir ein Spiel aus:", fontWeight = FontWeight.Bold)
                                    // Beerenfang ist ein normales Spiel in der Liste (früher eigener Modus „Bonus“)
                                    GameKind.entries.forEach { g ->
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
                                Mode.QUESTS -> QuestPanel(s, vm)
                                Mode.DUNGEON -> DungeonPanel(s, onOpen = { showDungeon = true })
                                Mode.SHOP -> ShopPanel(s, onShop = { showShop = true }, onBook = { showBook = true })
                                else -> {}
                            }
                        }
                    }
                    if (mode == Mode.QUESTS) {
                        StepsCard(
                            available = vm.stepSensor.available,
                            permission = stepPermission,
                            stepsToday = stepsToday,
                            pending = s.pendingSteps,
                            onAllow = { requestStepPermission() },
                            onRedeem = vm::redeemSteps,
                        )
                        // Befund 01: Pflege-Erinnerungen getrennt von den Schritten freigeben
                        if (!notifyPermission && Build.VERSION.SDK_INT >= 33) OutlinedButton(
                            onClick = { notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("🔔 Pflege-Erinnerungen erlauben") }
                    }
                }
            }

            if (!isEgg) ModeBar(mode) { mode = it }
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
    // Runde 3: Findet die App bei einem eigenen Bild keine Schwanzfläche, fragt sie einmal nach
    val askTail = body != null && hasTailSetting == null && !body.map.tailAreaFound && !isEgg
    if (askTail) androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text("Hat ${s.name} einen Schwanz?") },
        text = { Text("Ich habe im Bild keinen Schwanz gefunden. Ohne Schwanz ist er nie die Lieblingsstelle zum Kraulen. Das lässt sich im Sammelbuch unter „Sitz“ ändern.") },
        confirmButton = { TextButton(onClick = { vm.setHasTail(true) }) { Text("Ja, hat einen") } },
        dismissButton = { TextButton(onClick = { vm.setHasTail(false) }) { Text("Nein, keinen") } },
    )
    // Spielstand nicht lesbar: Pflege sperren, bis ausdrücklich entschieden wurde (Befund 21)
    if (saveLocked) RecoveryScreen(vm, onOtherPet = { showPets = true })
}

internal fun gameInfo(k: GameKind) = when (k) {
    GameKind.HIDE_SEEK -> "Dein Tierchen versteckt sich hinter einer von drei Wolken. Die Wolken mischen sich, du findest es."
    GameKind.MEMORY -> "Dreh Karten um und finde die passenden Psi-Symbole."
    GameKind.BUBBLES -> "Bring in ${BUBBLES_SECONDS} Sekunden so viele Seifenblasen wie möglich zum Platzen."
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

/** Befund 15: zeigt, wo der Mund ist; beim Ziehen deutlicher. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MouthMarker(p: Offset, active: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val r = 28.dp.toPx()
        drawCircle(Color(0xFFFF9BC8).copy(alpha = if (active) 0.45f else 0.18f), r, p)
        drawCircle(Color(0xFFE0569A).copy(alpha = if (active) 0.9f else 0.45f), r, p, style = Stroke(2.dp.toPx()))
    }
    if (active) Text("👄", fontSize = 18.sp, modifier = Modifier.offset { IntOffset((p.x - 11.dp.toPx()).roundToInt(), (p.y - 40.dp.toPx()).roundToInt()) })
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
    // Eine feste Zeile am unteren Rand; bei wenig Breite seitlich verschiebbar (kein vertikales Scrollen)
    Column {
        // Runde 4: unten nur die fünf Pflege-Knöpfe; Garten/Dungeon in der Szene, Laden/Aufgaben oben
        listOf(listOf(Mode.FEED, Mode.STROKE, Mode.CLEAN, Mode.SLEEP, Mode.PLAY)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    val sel = m == mode
                    val mod = Modifier.weight(1f).height(54.dp)
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

/**
 * Schmale Leiste am rechten Rand der Szene (Runde 3): Futter, Putzwerkzeuge oder Licht und Summen.
 * Sie liegt im freien Rand neben der Figur und verdeckt sie nicht; bei vielen Einträgen scrollt nur die Leiste.
 */
@Composable
private fun CareTray(
    mode: Mode, s: PetState, dragging: Food?, tool: Int, onTool: (Int) -> Unit, onDone: () -> Unit,
    light: Float, onLight: (Float) -> Unit, humming: Boolean, hummed: Boolean, onHum: () -> Unit, onWake: () -> Unit,
    onFoodStart: (Food, Offset) -> Unit, onFoodMove: (Offset) -> Unit, onFoodDrop: () -> Unit, onFoodTap: (Food) -> Unit,
) {
    if (mode != Mode.FEED && mode != Mode.CLEAN && mode != Mode.SLEEP) return
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(54.dp)
            .heightIn(max = 420.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), shape)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        @Composable
        fun slot(label: String, selected: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
            Box(
                Modifier.size(46.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = label, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
        when (mode) {
            Mode.FEED -> Food.entries.filter { it != Food.GARDEN || s.berryStock > 0 }.forEach { f ->
                var itemPos by remember { mutableStateOf(Offset.Zero) }
                val badge = when (f) {
                    Food.GARDEN -> "×${s.berryStock}"
                    in s.knownLikes -> "❤️"
                    in s.knownDislikes -> "👎"
                    else -> ""
                }
                Box(
                    Modifier.size(46.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp))
                        .border(if (f.sweet) 2.dp else 0.dp, if (f.sweet) Color(0xFFFF9BC8) else Color.Transparent, RoundedCornerShape(14.dp))
                        .onGloballyPositioned { itemPos = it.positionInRoot() }
                        .pointerInput(f, "tap") { detectTapGestures(onTap = { onFoodTap(f) }) }
                        .pointerInput(f) {
                            detectDragGestures(
                                onDragStart = { p -> onFoodStart(f, itemPos + p) },
                                onDrag = { change, amount -> change.consume(); onFoodMove(amount) },
                                onDragEnd = onFoodDrop,
                                onDragCancel = onFoodDrop,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(f.emoji, fontSize = 24.sp, modifier = Modifier.alpha(if (dragging == f) 0.3f else 1f))
                    if (badge.isNotEmpty()) Text(badge, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            Mode.CLEAN -> {
                slot("Schwamm", tool == 0, { onTool(0) }) { Text("🧽", fontSize = 22.sp) }
                slot("Dusche", tool == 1, { onTool(1) }) { Text("🚿", fontSize = 22.sp) }
                slot("Schaufel", tool == 2, { onTool(2) }) { Text("🧹", fontSize = 22.sp) }
                slot("Fertig", false, onDone) { Text("✔️", fontSize = 20.sp) }
            }
            Mode.SLEEP -> if (s.sleeping) slot("Wecken", false, onWake) { Text("☀️", fontSize = 22.sp) } else {
                // Licht in Stufen dimmen statt Schieberegler (passt in die schmale Leiste)
                slot("Licht heller", light >= 0.95f, { onLight((light + 0.3f).coerceAtMost(1f)) }) { Text("💡", fontSize = 20.sp) }
                slot("Licht dunkler", light <= 0.3f, { onLight((light - 0.3f).coerceAtLeast(0.1f)) }) { Text("🌑", fontSize = 20.sp) }
                slot("Schlaflied summen", hummed, { if (!humming) onHum() }) { Text(if (humming) "…" else "🎵", fontSize = 20.sp) }
            }
            else -> {}
        }
    }
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
private fun MessageText(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2,
        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
    )
}

/**
 * Befund 17: kompakter Kopf mit Name, Level und EP-Balken; Details (EP-Zahlen, Alter) aufklappbar.
 * Darunter kleine Zustandsringe.
 */
@Composable
private fun CompactHeader(s: PetState, onPets: () -> Unit, onInfo: () -> Unit, onSettings: () -> Unit, onShop: () -> Unit, onQuests: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { open = !open }) {
                Text(
                    s.name + if (s.stage == Stage.EGG) " · Ei" else " · Lv ${s.level}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                if (s.stage != Stage.EGG) {
                    val need = LevelSystem.expForNext(s.level)
                    LinearProgressIndicator(
                        progress = { (s.exp.toFloat() / need).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(0.6f).height(4.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer,
                        strokeCap = StrokeCap.Round,
                    )
                    if (open) Text("${s.stage.displayName} · ${s.exp} / $need EP · ${ageText(s)}", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (s.stage != Stage.EGG) {
                IconButton(onClick = onQuests) { Text("📋", fontSize = 20.sp) }
                IconButton(onClick = onShop) { Text("🛍️", fontSize = 20.sp) }
            }
            IconButton(onClick = onPets) { Text("🐾", fontSize = 20.sp) }
            // Werte und Einstellungen in einem Menü (Runde 3)
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Settings, contentDescription = "Menü") }
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("📊 Werte") }, onClick = { menu = false; onInfo() })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("⚙️ Einstellungen") }, onClick = { menu = false; onSettings() })
                }
            }
        }
    }
}

/** Sechs kleine Zustandsringe in einer Zeile. */
@Composable
private fun MiniStats(s: PetState, onTip: (String) -> Unit) {
    // Runde 4: Antippen zeigt Name, Wert und was hilft
    data class Stat(val icon: String, val label: String, val value: Float, val tip: String)
    val stats = listOf(
        Stat("🍙", "Sattheit", s.satiety, "Füttern hilft."),
        Stat("💖", "Laune", s.happiness, "Streicheln, Kraulen und Spielen helfen."),
        Stat("🫧", "Hygiene", s.hygiene, "Putzen hilft: Schwamm, Dusche, Schaufel."),
        Stat("⚡", "Energie", s.energy, "Schlafen hilft. Spiele und Dungeon kosten Energie."),
        Stat("➕", "Gesundheit", s.health, "Gute Pflege über längere Zeit heilt."),
        Stat("🤝", "Vertrauen", s.trust, "Liebevolle Pflege erhöht es, Nerven senkt es."),
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        stats.forEach { stat ->
            val icon = stat.icon; val value = stat.value
            val v = value.coerceIn(0f, 100f)
            val color = when { v < 25f -> Color(0xFFE5484D); v < 50f -> Color(0xFFF5A524); else -> Color(0xFF46A758) }
            val track = MaterialTheme.colorScheme.surfaceVariant
            Box(Modifier.size(36.dp).clickable(onClickLabel = stat.label) {
                onTip("${stat.icon} ${stat.label} ${stat.value.coerceIn(0f, 100f).toInt()} von 100 · ${stat.tip}")
            }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 3.5.dp.toPx()
                    val inset = sw / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                    drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw))
                    drawArc(color, -90f, 360f * v / 100f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Text(icon, fontSize = 14.sp)
            }
        }
    }
}

/** Die sechs Kraulstellen: heute schon probierte sind durchgestrichen, die gefundene hat einen Stern. */
@Composable
fun ScratchSpots(s: PetState, zone: java.time.ZoneId, zones: List<Zone> = Zone.entries) {
    val now = System.currentTimeMillis()
    val today = PetEngine.epochDay(now, zone)
    val found = s.scratchFoundDay == today
    val tried = PetEngine.scratchTriedToday(s, now, zone)
    val spots = zones.joinToString("  ·  ") { z ->
        when {
            found && z == Zone.favoriteOn(today, s.bornAt, zones) -> "⭐ ${z.label}"
            z in tried -> "✗ ${z.label}"
            else -> z.label
        }
    }
    Text(
        if (found) "Lieblingsstelle für heute gefunden! $spots" else "Stellen: $spots",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                    Text("Erlaube den Zugriff auf deine Schritte. Je ${PetEngine.STEPS_PER_EXP} Schritte gibt es 1 EP. Nach längeren Spaziergängen wird dein Tierchen aber schmutzig.")
                    Button(onClick = onAllow) { Text("Schrittzähler erlauben") }
                }
                else -> {
                    Text("Heute gezählt: $stepsToday")
                    Text("Bereit zum Einlösen: $pending (= ${pending / PetEngine.STEPS_PER_EXP} EP)")
                    Button(onClick = onRedeem, enabled = pending >= PetEngine.STEPS_PER_EXP) { Text("Einlösen") }
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
