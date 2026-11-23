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
import kotlin.random.Random

/** Minispiel „Wohin schaut es?“: 5 Runden links oder rechts raten. */
@Composable
fun GameDialog(name: String, onFinish: (wins: Int, rounds: Int) -> Unit, onCancel: () -> Unit) {
    val rounds = 5
    var round by remember { mutableIntStateOf(0) }
    var wins by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf<String?>(null) }
    val done = round >= rounds

    fun guess(left: Boolean) {
        val looksLeft = Random.nextBoolean()
        val ok = looksLeft == left
        if (ok) wins++
        round++
        last = (if (looksLeft) "⬅️ $name schaut nach links. " else "$name schaut nach rechts ➡️. ") +
            if (ok) "Richtig!" else "Daneben!"
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Wohin schaut $name?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(if (done) "Fertig!" else "Runde ${round + 1} von $rounds", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(last ?: "Rate, in welche Richtung $name gleich schaut.", textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Treffer: $wins", style = MaterialTheme.typography.titleMedium)
                if (!done) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { guess(true) }) { Text("⬅️", fontSize = 24.sp) }
                        Button(onClick = { guess(false) }) { Text("➡️", fontSize = 24.sp) }
                    }
                }
            }
        },
        confirmButton = {
            if (done) Button(onClick = { onFinish(wins, rounds) }) { Text("Belohnung abholen") }
        },
        dismissButton = { if (!done) TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}

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
                    "Wähle eine Stufe und setze dann ihre Bilder (am besten PNG mit transparentem Hintergrund). " +
                        "„Normal“ wird immer benutzt, wenn für eine Laune kein eigenes Bild da ist. Ohne Bilder zeichnet die App die eingebaute Figur.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
