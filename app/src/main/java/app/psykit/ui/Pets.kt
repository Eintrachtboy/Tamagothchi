package app.psykit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import app.psykit.core.model.Mood
import app.psykit.core.model.Stage
import app.psykit.sprites.SpriteVariant

/** Liste aller Tierchen: wechseln, neues anlegen, löschen. */
@Composable
fun PetsDialog(vm: PetViewModel, onDismiss: () -> Unit) {
    val currentId by vm.currentPetId.collectAsState()
    val spriteVersion by vm.spriteVersion.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val pets = remember(currentId, spriteVersion, refresh) { vm.allPets() }
    var newName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deine Tierchen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pets.forEach { (id, s) ->
                    val current = id == currentId
                    val sprite = remember(id, s.stage, spriteVersion) { vm.spritesFor(id).load(s.stage, SpriteVariant.forMood(s.mood)) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(8.dp),
                    ) {
                        if (s.stage == Stage.EGG && sprite == null) Text("🥚", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.width(56.dp))
                        else PetIcon(sprite, s.stage, s.mood, 56.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name + if (current) " (aktiv)" else "", fontWeight = FontWeight.Bold)
                            Text(
                                if (s.stage == Stage.EGG) "Ei" else "${s.stage.displayName} · Level ${s.level}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            if (s.stage != Stage.EGG) Text(
                                (if (s.mood in listOf(Mood.SICK, Mood.HUNGRY, Mood.DIRTY, Mood.SAD, Mood.TIRED)) "⚠️ " else "") + "${s.name} ${s.mood.label}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (!current) TextButton(onClick = { vm.switchPet(id); onDismiss() }) { Text("Wechseln") }
                            if (pets.size > 1) {
                                if (confirmDelete == id) TextButton(onClick = { vm.deletePet(id); confirmDelete = null; refresh++ }) {
                                    Text("Wirklich?", color = MaterialTheme.colorScheme.error)
                                } else TextButton(onClick = { confirmDelete = id }) { Text("Löschen") }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("Neues Tierchen", fontWeight = FontWeight.Bold)
                Text(
                    "Es startet als Ei mit eigenen Werten, eigenem Level und eigenen Bildern. Danach in den Einstellungen ein Figuren-Paket wählen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = newName, onValueChange = { newName = it.take(16) }, label = { Text("Name") }, singleLine = true)
                Button(onClick = { vm.addPet(newName); newName = ""; onDismiss() }, enabled = newName.isNotBlank()) { Text("🥚 Ei holen") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
