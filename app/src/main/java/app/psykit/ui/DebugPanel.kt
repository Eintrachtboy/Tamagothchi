package app.psykit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.debug.DebugTools
import app.psykit.core.model.Mood
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import app.psykit.sprites.SpriteVariant

/** Runde 21: Testmodus für Gerätetests. Wirkt auf das aktive Tierchen (Test-Tierchen oder, mit Sicherung, das echte). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(vm: PetViewModel, onClose: () -> Unit) {
    val s by vm.state.collectAsState()
    val spriteVersion by vm.spriteVersion.collectAsState()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛠 Testmodus", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Schließen") }
                }

                val msg by vm.message.collectAsState()
                if (msg.startsWith("Testmodus")) Text("✓ " + msg.removePrefix("Testmodus: "), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                // 1) Ziel
                val isTest = s.name == TEST_PET_NAME
                Card(colors = CardDefaults.cardColors(containerColor = if (isTest) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Wirkt auf: ${s.name} · ${s.stage.displayName} · Level ${s.level}", fontWeight = FontWeight.Bold)
                        Text(
                            if (isTest) "Test-Tierchen – dein echter Spielstand bleibt unberührt."
                            else "Achtung: echtes Tierchen. Vor der ersten Änderung wird automatisch eine Sicherung angelegt (Einstellungen → Sicherungen).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!isTest) Button(onClick = { vm.useTestPet() }, modifier = Modifier.padding(top = 6.dp)) { Text("Zum Test-Tierchen wechseln") }
                        if (isTest) Text("Eigene Figuren: im Test-Tierchen unter Einstellungen → Figuren-Pakete „Benutzen“ tippen.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // 2) Bildvorschau – ändert nichts am Spielstand
                Section("Bildvorschau (alle Stufen × Launen)")
                Text("Nur Ansicht, ändert nichts. Animierte Bilder laufen. „Ersatz“ = für diese Laune fehlt ein eigenes Bild.", style = MaterialTheme.typography.bodySmall)
                val variants = SpriteVariant.entries
                Row {
                    Text("", Modifier.width(64.dp))
                    variants.forEach { v -> Text(v.label, Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1) }
                }
                Stage.entries.forEach { st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(st.displayName, Modifier.width(64.dp), fontSize = 11.sp)
                        variants.forEach { v ->
                            val sprite = remember(spriteVersion, st, v) { vm.sprites.load(st, v) }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                PetIcon(sprite, st, moodOf(v), 48.dp)
                                if (sprite != null && !sprite.exact) Text("Ersatz", fontSize = 8.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // 3) Stufe und Laune
                Section("Stufe setzen")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stage.entries.forEach { st ->
                        OutlinedButton(onClick = { vm.debug("Stufe ${st.displayName}") { p, now -> DebugTools.setStage(p, st, now) } }) {
                            Text(if (st == Stage.EGG) "🥚 Ei (schlüpft in 5 s)" else st.displayName)
                        }
                    }
                }
                Section("Laune erzwingen")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DebugTools.ForcedMood.entries.forEach { m ->
                        OutlinedButton(onClick = { vm.debug("Laune ${m.label}") { p, _ -> DebugTools.forceMood(p, m) } }) { Text(m.label) }
                    }
                }

                // 4) Werte
                Section("Werte")
                StatsEditor(s) { a, b, c, d, e -> vm.debug("Werte gesetzt") { p, _ -> DebugTools.setStats(p, a, b, c, d, e) } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.debug("+1 Häufchen") { p, _ -> DebugTools.addPoops(p, 1) } }) { Text("💩 +1") }
                    OutlinedButton(onClick = { vm.debug("+2 Flecken") { p, _ -> DebugTools.addDirt(p, 2) } }) { Text("🟤 Flecken +2") }
                    OutlinedButton(onClick = { vm.debug("+1 Level") { p, _ -> DebugTools.addLevels(p, 1) } }) { Text("Level +1") }
                    OutlinedButton(onClick = { vm.debug("+5 Level") { p, _ -> DebugTools.addLevels(p, 5) } }) { Text("Level +5") }
                    OutlinedButton(onClick = { vm.debug("+5 Training") { p, _ -> DebugTools.addTraining(p, 5) } }) { Text("Training +5") }
                }

                // 5) Ressourcen und Freischalten
                Section("Ressourcen & Freischalten")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.debug("Ressourcen") { p, _ -> DebugTools.grant(p) } }) { Text("✨ Staub, Schlüssel, Tränke, Beeren") }
                    OutlinedButton(onClick = { vm.debug("Alle Attacken") { p, _ -> DebugTools.unlockMoves(p) } }) { Text("⚔️ Alle Attacken") }
                    (1..5).forEach { w ->
                        OutlinedButton(onClick = { vm.debug("Dungeon bis Welt $w frei") { p, _ -> DebugTools.unlockDungeon(p, w) } }) { Text("Welt $w frei") }
                    }
                }

                // 6) Zeit
                Section("Zeit vorspulen")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "+1 Std", 8 to "+8 Std", 24 to "+1 Tag").forEach { (h, l) ->
                        OutlinedButton(onClick = { vm.debug("$l vorgespult") { p, _ -> DebugTools.skipTime(p, h) } }) { Text("⏩ $l") }
                    }
                }
                Text("Hunger, Energie, Garten und Alter rechnen die Zeit nach. Höchstens so viel, wie das Spiel sonst auch nachholt.", style = MaterialTheme.typography.bodySmall)

                // 7) Erstmals-Hinweise
                Section("Hinweise")
                OutlinedButton(onClick = { vm.debug("Erstmals-Hinweise zurückgesetzt") { p, _ -> DebugTools.resetFirstTimes(p) } }) {
                    Text("Spiel-Intros, Mund-Markierung und Kraulgrenze zurücksetzen")
                }
                HorizontalDivider()
                Text("Der Testmodus ändert nur Werte des Spielstands, keine Spielregeln.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Section(title: String) = Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))

private fun moodOf(v: SpriteVariant) = when (v) {
    SpriteVariant.BASE -> Mood.OK
    SpriteVariant.SLEEPING -> Mood.SLEEPING
    SpriteVariant.HAPPY -> Mood.HAPPY
    SpriteVariant.SAD -> Mood.SAD
    SpriteVariant.HUNGRY -> Mood.HUNGRY
    SpriteVariant.SICK -> Mood.SICK
}

@Composable
private fun StatsEditor(s: PetState, onApply: (Float, Float, Float, Float, Float) -> Unit) {
    var sat by remember(s.satiety) { mutableFloatStateOf(s.satiety) }
    var hap by remember(s.happiness) { mutableFloatStateOf(s.happiness) }
    var hyg by remember(s.hygiene) { mutableFloatStateOf(s.hygiene) }
    var ene by remember(s.energy) { mutableFloatStateOf(s.energy) }
    var hea by remember(s.health) { mutableFloatStateOf(s.health) }
    @Composable fun row(label: String, v: Float, set: (Float) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${v.toInt()}", Modifier.width(110.dp), fontSize = 12.sp)
        Slider(value = v, onValueChange = set, valueRange = 0f..100f, modifier = Modifier.weight(1f))
    }
    Column(Modifier.fillMaxWidth()) {
        row("🍖 Sattheit", sat) { sat = it }
        row("😊 Laune", hap) { hap = it }
        row("🧼 Sauber", hyg) { hyg = it }
        row("⚡ Energie", ene) { ene = it }
        row("❤️ Gesund", hea) { hea = it }
        Button(onClick = { onApply(sat, hap, hyg, ene, hea) }) { Text("Werte übernehmen") }
    }
}
