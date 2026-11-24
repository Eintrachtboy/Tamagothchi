package app.psykit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Vorlage für ChatGPT (oder einen anderen Bildgenerator), die ein fertiges Figuren-Paket erzeugt. */
const val PACK_PROMPT = """Erstelle mir ein Figuren-Paket für meine Tamagotchi-App „Psykit“.

Figur: [HIER DEINE FIGUR BESCHREIBEN, z. B. „ein kleiner grauer Fuchs mit buschigem Schwanz und großen blauen Augen“]
Name des Pakets: [z. B. Graufuchs]
Name des Tierchens: [z. B. Grauli]
Stil: [z. B. niedlicher Anime-Stil, weiche Farben, klare Umrisse]

Erzeuge diese Bilder, immer dieselbe Figur, gleicher Stil, gleiche Blickrichtung und gleicher Bildausschnitt:
- egg.png: nur ein Ei, passend zur Figur (Farbe/Muster), noch nicht geschlüpft
- Für jede Stufe baby, child, teen, adult je 6 Bilder:
  <stufe>.png (normal, wach, zufrieden)
  <stufe>_happy.png (sehr glücklich, lacht, strahlende Augen)
  <stufe>_sad.png (traurig, hängende Ohren, evtl. ein Tränchen)
  <stufe>_hungry.png (hungrig, leckt sich die Lippen oder hält den Bauch)
  <stufe>_sick.png (krank, blass, müde Augen, evtl. Pflaster oder Thermometer)
  <stufe>_sleeping.png (schläft, Augen zu, eingerollt oder liegend)
- Die Stufen zeigen dieselbe Figur beim Älterwerden: baby = sehr klein und rund mit großem Kopf, child = etwas größer, teen = schlanker und sportlicher, adult = ausgewachsen und am detailreichsten.

Technische Regeln für JEDES Bild:
- PNG mit echtem transparentem Hintergrund (kein Weiß, kein Schachbrettmuster, keine Landschaft)
- quadratisch, 1024 × 1024 Pixel
- Figur mittig, füllt etwa 80 % der Bildhöhe, steht mit den Füßen knapp über dem unteren Rand
- kein Text, keine Schrift, kein Rahmen, kein Bodenschatten
- alle Bilder einer Stufe gleich groß und gleich positioniert, damit der Wechsel zwischen den Launen nicht springt

Pack zum Schluss alles in eine ZIP-Datei namens [Paketname].zip, ohne Unterordner, mit genau diesen Dateinamen und zusätzlich einer Datei pack.json mit diesem Inhalt:
{"name": "[Paketname]", "petName": "[Name des Tierchens]"}

Wenn du nicht alle 25 Bilder auf einmal schaffst, fang mit egg.png und den 6 baby-Bildern an und gib mir dazu schon die ZIP. Fehlende Bilder ersetzt die App automatisch durch das normale Bild."""

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row {
                    Text("Anleitung: eigene Figuren", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }

                H("So funktionieren Figuren-Pakete")
                P("Ein Figuren-Paket ist eine einzige ZIP-Datei mit allen Bildern einer Figur. Du kannst beliebig viele Pakete importieren und jederzeit wechseln, zum Beispiel heute Mew, morgen ein Graufuchs. Level, Werte und Fortschritt deines Tierchens bleiben beim Wechsel gleich, nur das Aussehen (und wenn im Paket angegeben, der Name) ändert sich.")
                P("Zahnrad → Figuren-Pakete → „Paket importieren (ZIP)“ → Datei wählen. Danach in der Liste „Benutzen“ tippen. Deine bisherigen Einzelbilder werden beim ersten Wechsel automatisch als Paket „Meine Bilder“ gesichert.")

                H("Inhalt der ZIP-Datei")
                Mono(
                    "Graufuchs.zip\n" +
                        "├─ pack.json        (optional)\n" +
                        "├─ egg.png\n" +
                        "├─ baby.png         (Pflicht, mindestens dieses)\n" +
                        "├─ baby_happy.png\n" +
                        "├─ baby_sad.png\n" +
                        "├─ baby_hungry.png\n" +
                        "├─ baby_sick.png\n" +
                        "├─ baby_sleeping.png\n" +
                        "├─ child.png … child_sleeping.png\n" +
                        "├─ teen.png … teen_sleeping.png\n" +
                        "└─ adult.png … adult_sleeping.png",
                )
                P("pack.json enthält den Namen des Pakets und optional den Namen des Tierchens:")
                Mono("{\"name\": \"Graufuchs\", \"petName\": \"Grauli\"}")
                P("Deutsche Namen gehen auch: ei, baby, kind, jugendlich, erwachsen bzw. 1 bis 5, und für die Laune gluecklich, traurig, hungrig, krank, schlaeft (z. B. „kind_traurig.png“). Unterordner im ZIP sind egal. Fehlt ein Bild, nimmt die App das normale Bild der Stufe, und fehlt eine ganze Stufe, das Bild der nächsten Stufe.")

                H("Was die Bilder zeigen sollen")
                P("Stufen: Das Tierchen wächst mit dem Level. Ei (vor dem Schlüpfen), Baby (ab Level 1), Kind (ab Level 5), Jugendlich (ab Level 10), Erwachsen (ab Level 20). Es ist immer dieselbe Figur, die älter wird: Baby klein und rund mit großem Kopf, Erwachsen groß und detailreich.")
                MoodLine("normal", "wach und zufrieden. Wird am häufigsten gezeigt und ersetzt jedes fehlende Bild.")
                MoodLine("_happy (glücklich)", "lacht, strahlt, gern mit kleinen Funken. Erscheint bei sehr guter Laune.")
                MoodLine("_sad (traurig)", "hängende Ohren, Blick nach unten, evtl. Tränchen. Bei schlechter Laune oder wenn es schmutzig ist.")
                MoodLine("_hungry (hungrig)", "leckt sich die Lippen, hält sich den Bauch, schaut bettelnd. Wenn die Sattheit niedrig ist.")
                MoodLine("_sick (krank)", "blass oder leicht grünlich, müde Augen, evtl. Pflaster. Wenn die Gesundheit niedrig ist.")
                MoodLine("_sleeping (schläft)", "Augen zu, eingerollt oder liegend. „Zzz“ und Dunkelheit zeichnet die App selbst dazu.")

                H("So machst du gute Bilder")
                P("• PNG mit echtem transparentem Hintergrund. Weiß oder ein gemaltes Schachbrettmuster sieht in der App wie ein Kasten aus.\n" +
                    "• Quadratisch, ideal 1024 × 1024 Pixel (die App verkleinert auf 768).\n" +
                    "• Figur mittig, etwa 80 % der Bildhöhe, Füße knapp über dem unteren Rand.\n" +
                    "• Alle Bilder einer Stufe gleich groß und gleich positioniert, sonst springt die Figur beim Launenwechsel.\n" +
                    "• Kein Text, kein Rahmen, kein Bodenschatten (den Schatten zeichnet die App).\n" +
                    "• Animierte GIF- oder WebP-Dateien funktionieren auch, z. B. für Schwanzwedeln. Standbilder bewegen sich in der App automatisch (atmen, schweben, hüpfen).")
                P("Tipp: Bildgeneratoren liefern oft einen weißen Hintergrund. Dann die Bilder vorher freistellen lassen, zum Beispiel mit einem Online-Tool zum Hintergrund entfernen.")

                H("Vorlage für ChatGPT")
                P("Kopiere diesen Text, ersetze die Teile in eckigen Klammern und gib ihn ChatGPT. Die fertige ZIP-Datei lädst du herunter und importierst sie in der App.")
                Button(onClick = { clipboard.setText(AnnotatedString(PACK_PROMPT)); copied = true }) {
                    Text(if (copied) "Kopiert ✓" else "Vorlage kopieren")
                }
                Mono(PACK_PROMPT)
            }
        }
    }
}

@Composable private fun H(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
@Composable private fun P(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)
@Composable private fun MoodLine(name: String, text: String) = Text("• $name: $text", style = MaterialTheme.typography.bodyMedium)

@Composable
private fun Mono(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp)) }
    }
}
