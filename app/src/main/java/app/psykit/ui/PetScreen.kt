package app.psykit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.psykit.core.engine.DayPhase
import app.psykit.core.engine.LevelSystem
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import app.psykit.sprites.SpriteVariant
import java.util.concurrent.TimeUnit

@Composable
fun PetScreen(vm: PetViewModel) {
    val s by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val stepsToday by vm.stepsToday.collectAsState()
    val petPulse by vm.petPulse.collectAsState()
    val spriteVersion by vm.spriteVersion.collectAsState()

    var showFeed by remember { mutableStateOf(false) }
    var showGame by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
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
        if (!askedOnce) { askedOnce = true; requestPermissions() }
    }

    val phase = DayPhase.at(s.lastUpdate, vm.repo.zone)
    val variant = SpriteVariant.forMood(s.mood)
    val sprite = remember(s.stage, variant, spriteVersion) { vm.sprites.load(s.stage, variant) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(s, onSettings = { showSettings = true })

        PetScene(
            stage = s.stage, mood = s.mood, sleeping = s.sleeping, poops = s.poops, phase = phase,
            custom = sprite, customIsVariant = sprite?.exact == true && variant != SpriteVariant.BASE, petPulse = petPulse, onTapPet = vm::pet,
        )

        Text(
            message.ifEmpty { statusLine(s) },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        )

        if (s.stage != Stage.EGG) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBar("🍙 Sattheit", s.satiety)
                    StatBar("💖 Laune", s.happiness)
                    StatBar("🫧 Hygiene", s.hygiene)
                    StatBar("⚡ Energie", s.energy)
                    StatBar("➕ Gesundheit", s.health)
                }
            }
        }

        val actionsEnabled = s.stage != Stage.EGG
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("🍙", "Füttern", actionsEnabled && !s.sleeping, Modifier.weight(1f)) { showFeed = true }
            ActionButton("🤚", "Streicheln", actionsEnabled && !s.sleeping, Modifier.weight(1f)) { vm.pet() }
            ActionButton("🧽", "Putzen", actionsEnabled, Modifier.weight(1f)) { vm.clean() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(if (s.sleeping) "☀️" else "🌙", if (s.sleeping) "Wecken" else "Schlafen", actionsEnabled, Modifier.weight(1f)) { vm.toggleSleep() }
            ActionButton("🎲", "Spielen", actionsEnabled && !s.sleeping, Modifier.weight(1f)) {
                if (vm.checkPlay() == null) showGame = true
            }
            ActionButton("📊", "Werte", true, Modifier.weight(1f)) { showStats = true }
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

    if (showFeed) FeedDialog(s.name, onMeal = { vm.feedMeal(); showFeed = false }, onSnack = { vm.feedSnack(); showFeed = false }, onDismiss = { showFeed = false })
    if (showGame) GameDialog(s.name, onFinish = { w, r -> vm.finishGame(w, r); showGame = false }, onCancel = { showGame = false })
    if (showStats) StatsDialog(s, onDismiss = { showStats = false })
    if (showSettings) SettingsDialog(vm, s, onDismiss = { showSettings = false })
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
private fun Header(s: PetState, onSettings: () -> Unit) {
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
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen") }
    }
}

@Composable
private fun StatBar(label: String, value: Float) {
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
private fun ActionButton(icon: String, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.height(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
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
            Text("👟 Schritte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                !available -> Text("Dein Handy hat leider keinen Schrittzähler-Sensor.")
                !permission -> {
                    Text("Erlaube den Zugriff auf deine Schritte. Jede 100 Schritte bringen 1 EP, und Bewegung macht gute Laune.")
                    Button(onClick = onAllow) { Text("Schrittzähler erlauben") }
                }
                else -> {
                    Text("Heute gezählt: $stepsToday")
                    Text("Bereit zum Einlösen: $pending (= ${pending / 100} EP)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRedeem, enabled = pending >= 100) { Text("Einlösen") }
                    }
                    Text(
                        "Gezählt wird, wenn die App offen ist und etwa stündlich im Hintergrund.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedDialog(name: String, onMeal: () -> Unit, onSnack: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Was bekommt $name?") },
        text = { Text("Eine Mahlzeit macht satt. Ein Snack macht glücklich, aber nicht zu viele!") },
        confirmButton = { Button(onClick = onMeal) { Text("🍙 Mahlzeit") } },
        dismissButton = { OutlinedButton(onClick = onSnack) { Text("🍬 Snack") } },
    )
}
