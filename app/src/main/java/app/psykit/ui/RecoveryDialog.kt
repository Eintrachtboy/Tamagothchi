package app.psykit.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** Rohtext einer Sicherung über das Android-Teilen-Menü weitergeben (Mail, Messenger, Drive …). */
fun shareBackup(ctx: Context, raw: String, label: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Psykit-Sicherung: $label")
        putExtra(Intent.EXTRA_TEXT, raw)
    }
    ctx.startActivity(Intent.createChooser(send, "Sicherung teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Erscheint, wenn der Spielstand des aktiven Tierchens nicht gelesen werden kann (Befund 21).
 * Blockiert die Pflege; ein neues Spiel beginnt nur nach ausdrücklicher Bestätigung.
 */
@Composable
fun RecoveryScreen(vm: PetViewModel, onOtherPet: () -> Unit) {
    val ctx = LocalContext.current
    var confirmNew by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Lumi") }
    var info by remember { mutableStateOf("") }
    val otherPets = remember { vm.repo.petIds.size > 1 }

    // Zurück-Taste schließt diese Ansicht nicht: ohne Entscheidung bleibt der Stand gesperrt und gesichert.
    FullScreenLayer(onBack = {}) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("⚠️ Spielstand nicht lesbar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Der Spielstand von diesem Tierchen konnte nicht gelesen werden. Er ist gesichert und wird nicht überschrieben.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Du kannst es später noch einmal versuchen (zum Beispiel nach einem Update) oder die Sicherung an dich selbst schicken.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { if (!vm.retryLoad()) info = "Immer noch nicht lesbar. Die Sicherung bleibt erhalten." }, modifier = Modifier.fillMaxWidth()) {
                Text("🔄 Erneut versuchen")
            }
            OutlinedButton(onClick = {
                val raw = vm.currentRaw()
                if (raw != null) shareBackup(ctx, raw, vm.currentPetId.value) else info = "Es ist kein gespeicherter Text vorhanden."
            }, modifier = Modifier.fillMaxWidth()) { Text("📤 Sicherung teilen") }
            if (otherPets) OutlinedButton(onClick = onOtherPet, modifier = Modifier.fillMaxWidth()) { Text("🐾 Anderes Tierchen") }
            if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)

            Spacer(Modifier.padding(4.dp))
            if (!confirmNew) {
                TextButton(onClick = { confirmNew = true }) { Text("Neu beginnen …") }
            } else {
                Text(
                    "Wirklich neu beginnen? Dieses Tierchen startet als neues Ei. Die Sicherung des alten Stands bleibt in den Einstellungen unter „Sicherungen“ erhalten.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(value = name, onValueChange = { name = it.take(16) }, label = { Text("Name") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { vm.startOver(name) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Ja, neues Ei") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { confirmNew = false }) { Text("Abbrechen") }
                }
            }
        }
    }
}

/** Liste aller Sicherungen für die Einstellungen: teilen oder als neues Tierchen wiederherstellen. */
@Composable
fun BackupsSection(vm: PetViewModel) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val backups = remember(refresh) { vm.backups() }
    var info by remember { mutableStateOf("") }
    Text("Sicherungen", fontWeight = FontWeight.Bold)
    if (backups.isEmpty()) {
        Text("Keine. Sicherungen entstehen nur, wenn ein Spielstand nicht gelesen werden konnte.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Text("Sicherungen werden nie automatisch gelöscht.", style = MaterialTheme.typography.bodySmall)
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    backups.forEach { b ->
        Column(Modifier.fillMaxWidth()) {
            Text("Tierchen ${b.petId} · ${fmt.format(Date(b.savedAt))}", style = MaterialTheme.typography.labelLarge)
            Row {
                TextButton(onClick = { shareBackup(ctx, b.raw, b.petId) }) { Text("Teilen") }
                TextButton(onClick = {
                    info = if (vm.restoreBackup(b.key)) "Wiederhergestellt als neues Tierchen." else "Diese Version kann die Sicherung noch nicht lesen."
                    refresh++
                }) { Text("Als neues Tierchen wiederherstellen") }
            }
        }
    }
    if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall)
}
