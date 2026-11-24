package app.psykit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.battle.BattleStats
import app.psykit.core.battle.PetCombatant
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import app.psykit.sprites.SpriteVariant

@Composable
fun StatsDialog(s: PetState, onDismiss: () -> Unit) {
    val c = PetCombatant(s)
    val st: BattleStats = c.stats
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Werte von ${s.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Gesamt-EP: ${s.totalExp}")
                Text("Eingelöste Schritte: ${s.stepsRedeemedTotal}")
                Text("Gewonnene Spiele: ${s.gamesWon}")
                Spacer(Modifier.height(8.dp))
                Text("Was ${s.name} sich merkt", fontWeight = FontWeight.Bold)
                Text("🤝 Vertrauen: ${s.trust.toInt()} / 100")
                Text("Lieb behandelt: ${s.kindCount}× · Genervt: ${s.annoyCount}×")
                Text("❤️ Lieblingsessen: " + (s.knownLikes.joinToString { it.emoji + " " + it.label }.ifEmpty { "noch geheim" }))
                Text("👎 Mag nicht: " + (s.knownDislikes.joinToString { it.emoji + " " + it.label }.ifEmpty { "noch unbekannt" }))
                Text("Training: Angriff +${s.trainAtk} · Abwehr +${s.trainDef} · Tempo +${s.trainSpd} · Psi +${s.trainPsi}")
                Spacer(Modifier.height(8.dp))
                Text("Kampfwerte", fontWeight = FontWeight.Bold)
                if (s.stage == Stage.EGG) Text("Noch im Ei.")
                else {
                    Text("KP ${st.maxHp} · Angriff ${st.attack} · Abwehr ${st.defense}")
                    Text("Tempo ${st.speed} · Psi ${st.psi}")
                    Text("Attacken: " + c.moves.joinToString { it.name })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Das Kampfsystem kommt in einer späteren Version. Level, Pflege und deine Schritte machen dein Tierchen schon jetzt stärker.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
fun SettingsDialog(vm: PetViewModel, s: PetState, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    var pending by remember { mutableStateOf<Pair<Stage, SpriteVariant>?>(null) }
    var selectedStage by remember { mutableStateOf(if (s.stage == Stage.EGG) Stage.BABY else s.stage) }
    var confirmReset by remember { mutableStateOf(false) }
    val spriteVersion by vm.spriteVersion.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = pending
        if (uri != null && target != null) vm.setSprite(target.first, target.second, uri)
        pending = null
    }

    var importInfo by remember { mutableStateOf<String?>(null) }
    val packPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            vm.importPack(uris)
            importInfo = vm.message.value
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(16) }, label = { Text("Name") }, singleLine = true)
                Button(onClick = { vm.rename(name) }) { Text("Namen speichern") }
                HorizontalDivider()
                Text("Eigene Bilder", fontWeight = FontWeight.Bold)
                Text(
                    "Wähle eine Stufe und setze dann ihre Bilder (PNG mit transparentem Hintergrund, oder animiertes GIF/WebP). " +
                        "„Normal“ wird immer benutzt, wenn für eine Laune kein eigenes Bild da ist. Ohne Bilder zeichnet die App die eingebaute Figur.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { packPicker.launch(arrayOf("image/*")) }) { Text("Bildpaket importieren …") }
                Text(
                    "Mehrere Bilder auf einmal auswählen. Zuordnung über den Dateinamen: Stufe (1-ei, 2-baby, 3-kind, 4-jugendlich, 5-erwachsen) " +
                        "und optional Laune (schlaeft, gluecklich, traurig, hungrig, krank), z. B. „baby_traurig.png“.",
                    style = MaterialTheme.typography.bodySmall,
                )
                importInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stage.entries.forEach { st ->
                        FilterChip(selected = st == selectedStage, onClick = { selectedStage = st }, label = { Text(st.displayName) })
                    }
                }
                val variants = if (selectedStage == Stage.EGG) listOf(SpriteVariant.BASE) else SpriteVariant.entries
                variants.forEach { v ->
                    val has = remember(spriteVersion, selectedStage, v) { vm.sprites.hasCustom(selectedStage, v) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(v.label + if (has) " ✓" else "", Modifier.width(96.dp))
                        TextButton(onClick = {
                            pending = selectedStage to v
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("Bild wählen") }
                        if (has) TextButton(onClick = { vm.clearSprite(selectedStage, v) }) { Text("Entfernen") }
                    }
                }
                HorizontalDivider()
                if (!confirmReset) {
                    OutlinedButton(onClick = { confirmReset = true }) { Text("Neu anfangen …") }
                } else {
                    Text("Wirklich neu anfangen? Level und Fortschritt gehen verloren.", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.reset(name); confirmReset = false; onDismiss() }) { Text("Ja, neues Ei") }
                        TextButton(onClick = { confirmReset = false }) { Text("Nein") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
}
