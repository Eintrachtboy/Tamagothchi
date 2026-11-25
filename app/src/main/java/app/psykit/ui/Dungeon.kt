package app.psykit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.psykit.core.battle.BattleEngine
import app.psykit.core.battle.BattleState
import app.psykit.core.battle.Fighter
import app.psykit.core.battle.MoveId
import app.psykit.core.battle.MoveSource
import app.psykit.core.battle.PetAction
import app.psykit.core.battle.Potion
import app.psykit.core.battle.TypeChart
import app.psykit.core.dungeon.DungeonEngine
import app.psykit.core.dungeon.DungeonRun
import app.psykit.core.dungeon.Floors
import app.psykit.core.dungeon.RoomType
import app.psykit.core.dungeon.World
import app.psykit.core.model.Item
import app.psykit.core.model.Mood
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import app.psykit.sprites.Sprite

private val Gold = Color(0xFFFFB02E)

/** Kurzinfo im Hauptbildschirm (Modus „Dungeon“). */
@Composable
fun DungeonPanel(s: PetState, onOpen: () -> Unit) {
    Text("⚔️ Dungeon", fontWeight = FontWeight.Bold)
    Text("✨ ${s.stardust} Sternenstaub · 🗝️ ${s.keys} Schlüssel · 🧪 ${s.healPotions} · 💜 ${s.psiPotions}", style = MaterialTheme.typography.bodyMedium)
    val next = Floors.get(s.dungeonProgress.coerceAtMost(Floors.DEPTH_START - 1))
    Text(
        if (s.dungeonProgress >= Floors.DEPTH_START) "Alle Welten geschafft! Die Tiefe wartet (Rekord: ${s.depthRecord})."
        else "Nächstes Ziel: ${World.of(next.world).label} ${next.code} „${next.name}“ (empfohlen ab Level ${next.level}).",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("Ein Lauf kostet einen Schlüssel und Energie. Jeden Tag gibt es einen Schlüssel gratis, weitere für je 3000 Schritte.", style = MaterialTheme.typography.bodySmall)
    if (s.run == null) DungeonEngine.cannotEnter(s, s.dungeonProgress.coerceAtMost(Floors.DEPTH_START - 1))?.let {
        Text("⚠️ $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = onOpen, enabled = s.stage != Stage.EGG) { Text(if (s.run != null) "Weiter im Dungeon" else "Dungeon öffnen") }
}

/**
 * Vollbild-Ebene im Activity-Fenster statt eines Dialog-Fensters: Dialog-Fenster bekommen bei
 * Edge-to-Edge (Android 15) keine Navigationsleisten-Insets, dann verschwindet unten etwas.
 * Hier greift safeDrawingPadding wie im Hauptbildschirm. Zurück-Taste schließt.
 */
@Composable
fun FullScreenLayer(onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
}

@Composable
fun DungeonDialog(vm: PetViewModel, s: PetState, sprite: Sprite?, onClose: () -> Unit) {
    LaunchedEffect(Unit) { vm.say("") }
    FullScreenLayer(onBack = onClose) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                val run = s.run
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val title = run?.let { val f = Floors.get(it.floor); "${World.of(f.world).emoji} ${f.code} · ${f.name}" } ?: "⚔️ Dungeon"
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("✨ ${s.stardust} · 🗝️ ${s.keys} · ⚡ ${s.energy.toInt()} · ❤️ ${s.health.toInt()}", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onClose) { Text(if (run?.battle != null) "Pause" else "Schließen") }
                }
                val msg by vm.message.collectAsState()
                if (run?.battle == null && msg.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(msg, Modifier.padding(10.dp).fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        run == null && s.lastRun != null -> RunResultView(s, onOk = { vm.clearLastRun() })
                        run == null -> FloorSelect(s, vm)
                        run.battle != null -> BattleView(vm, s, run.battle!!, sprite)
                        run.cards.isNotEmpty() -> CardView(vm, s, run)
                        run.bridge -> BridgeView(vm, run)
                        run.event != null -> EventView(vm, s, run)
                        run.room == RoomType.REST -> RestView(vm, s, run)
                        run.room == RoomType.MERCHANT -> MerchantView(vm, s, run)
                        else -> MapView(vm, s, run)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ Auswahl der Ebene

@Composable
private fun FloorSelect(s: PetState, vm: PetViewModel) {
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val blocker = DungeonEngine.cannotEnter(s, s.dungeonProgress.coerceAtMost(Floors.DEPTH_START - 1))
        if (blocker != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text("⚠️ $blocker", Modifier.padding(12.dp).fillMaxWidth(), fontWeight = FontWeight.Bold)
        }
        Text("Zum Betreten: 1 🗝️ Schlüssel, mindestens 30 ⚡ Energie und 40 Gesundheit, und das Tierchen muss wach sein.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { info = !info }) { Text(if (info) "Regeln ausblenden" else "Wie funktioniert der Dungeon?") }
        if (info) DungeonRules()
        World.entries.forEach { w ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val set = Item.completeSets(s).contains(w.n)
                    Text("${w.emoji} Welt ${w.n}: ${w.label}" + if (set) " · ${Item.setTitle(w.n)}" else "", fontWeight = FontWeight.Bold)
                    Floors.all.filter { it.world == w.n }.forEach { f ->
                        val open = DungeonEngine.unlocked(s, f.index)
                        val done = f.index < s.dungeonProgress
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((if (done) "✓ " else if (!open) "🔒 " else "") + "${f.code} ${f.name}", fontWeight = if (open && !done) FontWeight.Bold else FontWeight.Normal)
                                Text("Level ${f.level}" + if (f.rule.name != "NONE") " · ${f.rule.label}: ${f.rule.info}" else "", style = MaterialTheme.typography.bodySmall)
                                MoveId.hiddenIn(f.index)?.let { h ->
                                    val found = h.name in s.ownedMoves
                                    Text(
                                        if (found) "✓ Versteckte Attacke gefunden: ${h.label}" else "❓ Hier ist eine Attacke versteckt",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (found) Color(0xFF2E7D32) else Gold,
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            if (open) Button(onClick = {
                                val why = DungeonEngine.cannotEnter(s, f.index)
                                if (why != null) vm.say(why) else vm.enterDungeon(f.index)
                            }) { Text("Los") }
                        }
                    }
                }
            }
        }
        if (s.dungeonProgress >= Floors.DEPTH_START) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🕳️ Die Tiefe", fontWeight = FontWeight.Bold)
                    Text("Endlos viele Stockwerke, jedes schwerer. Dein Rekord: ${s.depthRecord}.", style = MaterialTheme.typography.bodySmall)
                    val records = vm.allPets().filter { it.second.depthRecord > 0 }.sortedByDescending { it.second.depthRecord }
                    if (records.size > 1) Text("Rangliste: " + records.joinToString { "${it.second.name} ${it.second.depthRecord}" }, style = MaterialTheme.typography.bodySmall)
                    val n = s.depthRecord + 1
                    Button(onClick = {
                        val idx = Floors.DEPTH_START + n - 1
                        val why = DungeonEngine.cannotEnter(s, idx)
                        if (why != null) vm.say(why) else vm.enterDungeon(idx)
                    }) { Text("Stockwerk $n betreten") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DungeonRules() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("• Eine Ebene hat 6 Räume. Ab Raum 2 wählst du zwischen zwei Wegen. Am Ende wartet ein Wächter.", style = MaterialTheme.typography.bodySmall)
            Text("• Zwischen den Räumen heilst du nicht, nur nach einem Sieg ein bisschen. Rastplätze und Tränke helfen.", style = MaterialTheme.typography.bodySmall)
            Text("• Du kannst nach jedem Raum mit der Beute umkehren. Wer verliert, behält nur die Hälfte.", style = MaterialTheme.typography.bodySmall)
            Text("• Gegner kündigen an, was sie vorhaben (Symbol über ihnen). ❗ = Großangriff: Ducken oder Schild!", style = MaterialTheme.typography.bodySmall)
            Text("• Krallen und Wucht treffen nur die vordere Reihe. Feuer, Frost, Gift und Psi treffen auch hinten.", style = MaterialTheme.typography.bodySmall)
            Text("• Typen: Krallen gut gegen Weiches und Pflanzen, Wucht knackt Panzer, Feuer brennt Pflanzen, Frost löscht Glut, Psi trifft Geister.", style = MaterialTheme.typography.bodySmall)
            Text("• Ein gut gepflegtes Tierchen kämpft stärker. Hunger und schlechte Laune kosten bis zu 50 % Kraft.", style = MaterialTheme.typography.bodySmall)
            Text("• Jede Ebene versteckt eine Attacke, die es nirgends zu kaufen gibt. Jeder Sieg über Wächter oder Boss gibt 40 % Chance, spätestens beim dritten Sieg gehört sie dir.", style = MaterialTheme.typography.bodySmall)
            Text("• Nach normalen Kämpfen und Elitekämpfen wählst du eine von drei Fundkarten: eine Attacke nur für diesen Lauf, auf einem 5. Platz. Überspringen gibt +${DungeonEngine.SKIP_CARD_DUST} ✨.", style = MaterialTheme.typography.bodySmall)
            Text("• Kombos: Manche Zustände und Attackentypen passen besonders gut zusammen. Probier aus, was passiert! Gefundene Kombos stehen im Sammelbuch.", style = MaterialTheme.typography.bodySmall)
            Text("• Attacken werden mit Übung besser: nach ${MoveId.LEVEL2_USES} Einsätzen ★ (+15 %), nach ${MoveId.LEVEL3_USES} ★★ (+30 % und ein Extra).", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ------------------------------------------------------------ Karte

@Composable
private fun MapView(vm: PetViewModel, s: PetState, run: DungeonRun) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RunStatus(run)
        if (run.note.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text(run.note, Modifier.padding(12.dp).fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
        }
        // Weg bisher und nächste Räume
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            run.columns.forEachIndexed { i, col ->
                val label = when {
                    i < run.step -> col.getOrNull(run.chosen.getOrElse(i) { 0 })?.emoji ?: "·"
                    i == run.step -> "➡️"
                    else -> if (col.size == 1) col[0].emoji else "?"
                }
                Text(label, fontSize = 20.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        Text("Wohin geht es weiter?", fontWeight = FontWeight.Bold)
        DungeonEngine.choices(run).forEachIndexed { i, t ->
            Card(Modifier.fillMaxWidth().clickable { vm.chooseRoom(i) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t.label, fontWeight = FontWeight.Bold)
                        Text(roomInfo(t), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        OutlinedButton(onClick = { vm.leaveDungeon() }, modifier = Modifier.fillMaxWidth()) { Text("🏃 Mit der Beute umkehren") }
    }
}

private fun roomInfo(t: RoomType) = when (t) {
    RoomType.FIGHT -> "Eine Gruppe Gegner."
    RoomType.ELITE -> "Schwerer Kampf mit einem Elite-Gegner, doppelte Belohnung."
    RoomType.CHEST -> "Sternenstaub und ein Fundstück."
    RoomType.EVENT -> "Etwas Unerwartetes passiert."
    RoomType.REST -> "Ausruhen (30 % KP) oder Attacken tauschen."
    RoomType.MERCHANT -> "Tränke mit deinem Sternenstaub kaufen."
    RoomType.GUARD -> "Der Wächter dieser Ebene. Danach ist sie geschafft."
    RoomType.BOSS -> "Der Boss dieser Welt!"
}

@Composable
private fun RunStatus(run: DungeonRun) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HpBar("KP", run.hp, run.maxHp)
            Text("Beute: ✨ ${run.dust} · ${run.exp} EP · ${run.items.size} Funde · 🧪 ${run.healPotions} · 💜 ${run.psiPotions}", style = MaterialTheme.typography.bodySmall)
            run.fundMove?.let { MoveId.parse(it) }?.let { Text("🃏 Fundattacke: ${it.type?.emoji ?: "✳️"} ${it.label}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun HpBar(label: String, hp: Int, max: Int, color: Color? = null) {
    val f = if (max <= 0) 0f else hp.toFloat() / max
    val c = color ?: when { f < 0.25f -> Color(0xFFE5484D); f < 0.5f -> Color(0xFFF5A524); else -> Color(0xFF46A758) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(34.dp), style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(
            progress = { f.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(8.dp),
            color = c, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round,
        )
        Text(" $hp/$max", style = MaterialTheme.typography.labelSmall)
    }
}

// ------------------------------------------------------------ Ereignis, Rast, Händler, Brücke

@Composable
private fun EventView(vm: PetViewModel, s: PetState, run: DungeonRun) {
    val ev = run.event ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RunStatus(run)
        Text("❓", fontSize = 56.sp)
        Text(ev.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(ev.text, textAlign = TextAlign.Center)
        if (ev.name == "HEDGEHOG") Text("Gartenbeeren dabei: ${s.berryStock}", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.resolveEvent(true) }, modifier = Modifier.fillMaxWidth()) { Text(ev.yes) }
        OutlinedButton(onClick = { vm.resolveEvent(false) }, modifier = Modifier.fillMaxWidth()) { Text(ev.no) }
    }
}

@Composable
private fun RestView(vm: PetViewModel, s: PetState, run: DungeonRun) {
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RunStatus(run)
        Text("🔥", fontSize = 56.sp)
        Text("Ein Rastplatz", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (editing) {
            MovesEditor(s, onSave = { vm.setMoves(it); vm.rest(false) })
        } else {
            Button(onClick = { vm.rest(true) }, modifier = Modifier.fillMaxWidth()) { Text("Ausruhen: +30 % KP") }
            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Attacken tauschen") }
        }
    }
}

@Composable
private fun MerchantView(vm: PetViewModel, s: PetState, run: DungeonRun) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RunStatus(run)
        Text("🛒", fontSize = 56.sp)
        Text("Ein wandernder Händler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Du bezahlst mit deinem Sternenstaub (✨ ${s.stardust}).", style = MaterialTheme.typography.bodySmall)
        Potion.entries.forEach { p ->
            Button(onClick = { vm.merchantBuy(p) }, enabled = s.stardust >= p.price, modifier = Modifier.fillMaxWidth()) {
                Text("${p.emoji} ${p.label} · ${p.price} ✨")
            }
        }
        OutlinedButton(onClick = { vm.leaveRoom() }, modifier = Modifier.fillMaxWidth()) { Text("Weitergehen") }
    }
}

@Composable
private fun CardView(vm: PetViewModel, s: PetState, run: DungeonRun) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RunStatus(run)
        if (run.note.isNotEmpty()) Text(run.note, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        Text("🃏 Fundkarten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (run.fundMove != null) "Eine neue Karte ersetzt deine Fundattacke. Sie gilt nur für diesen Lauf."
            else "Wähle eine Attacke für den Rest dieses Laufs. Sie kommt auf einen 5. Platz.",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
        )
        val weak = DungeonEngine.weakness(Floors.get(run.floor))
        run.cards.mapNotNull { MoveId.parse(it) }.forEachIndexed { i, m ->
            val rarity = DungeonEngine.cardRarity(m)
            val color = listOf(MaterialTheme.colorScheme.outlineVariant, Color(0xFF3E8EDE), Color(0xFF8C6BE0))[rarity]
            Column(
                Modifier.fillMaxWidth()
                    .border(if (rarity > 0) 2.dp else 1.dp, color, RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .clickable { vm.chooseCard(i) }
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.type?.emoji ?: "✳️", fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.label, fontWeight = FontWeight.Bold)
                        Text(DungeonEngine.cardRarityLabel(m) + if (m.type == weak) " · trifft die Schwäche dieser Welt" else "", style = MaterialTheme.typography.labelSmall, color = if (rarity > 0) color else MaterialTheme.colorScheme.onSurface)
                    }
                    if (m in MoveId.known(s)) Text("kennst du", style = MaterialTheme.typography.labelSmall)
                }
                Text(moveLine(m), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { vm.chooseCard(-1) }, modifier = Modifier.fillMaxWidth()) { Text("Liegen lassen: +${DungeonEngine.SKIP_CARD_DUST} ✨") }
    }
}

@Composable
private fun BridgeView(vm: PetViewModel, run: DungeonRun) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RunStatus(run)
        if (run.note.isNotEmpty()) Text(run.note, textAlign = TextAlign.Center)
        Text("🌉", fontSize = 56.sp)
        Text("Zwei Brücken über die Lava", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Button(onClick = { vm.crossBridge(false) }, modifier = Modifier.fillMaxWidth()) { Text("Sichere Brücke") }
        OutlinedButton(onClick = { vm.crossBridge(true) }, modifier = Modifier.fillMaxWidth()) { Text("Schnelle Brücke: Beute oder Brandblasen") }
    }
}

@Composable
private fun RunResultView(s: PetState, onOk: () -> Unit) {
    val r = s.lastRun ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (!r.won) "💤" else if (r.cleared) "🏆" else "🎒", fontSize = 64.sp)
        Text(if (!r.won) "Niederlage" else if (r.cleared) "Geschafft!" else "Zurück mit der Beute", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(r.text, textAlign = TextAlign.Center)
        r.items.mapNotNull { Item.parse(it) }.forEach { Text("${it.emoji} ${it.label} (${it.rarity.label}): ${it.bonusText}", style = MaterialTheme.typography.bodySmall) }
        r.moves.mapNotNull { MoveId.parse(it) }.forEach {
            Text(if (it.source is MoveSource.Hidden) "❓ Versteckte Attacke entdeckt: ${it.label}!" else "Neue Attacke: ${it.label}", fontWeight = FontWeight.Bold, color = if (it.source is MoveSource.Hidden) Gold else Color.Unspecified)
        }
        Button(onClick = onOk) { Text("OK") }
    }
}

// ------------------------------------------------------------ Attacken ausrüsten

@Composable
fun MovesEditor(s: PetState, onSave: (List<MoveId>) -> Unit) {
    val known = MoveId.known(s)
    var chosen by remember { mutableStateOf(MoveId.equipped(s)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Wähle bis zu 4 Attacken (${chosen.size}/4):", fontWeight = FontWeight.Bold)
        known.forEach { m ->
            val on = m in chosen
            Row(
                Modifier.fillMaxWidth()
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .clickable { chosen = if (on) chosen - m else if (chosen.size < 4) chosen + m else chosen }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (on) "☑" else "☐", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${m.type?.emoji ?: "✳️"} ${m.label}" + stars(MoveId.level(s, m)) + (if (MoveId.empowered(s, m)) " ⭐" else ""), fontWeight = FontWeight.Bold)
                    Text(moveLine(m), style = MaterialTheme.typography.bodySmall)
                    Text(usesLine(s, m), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
        Button(onClick = { onSave(chosen) }, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
    }
}

fun stars(level: Int) = when (level) { 3 -> " ★★"; 2 -> " ★"; else -> "" }

fun usesLine(s: PetState, m: MoveId): String {
    val n = s.moveUses[m.name] ?: 0
    return when {
        n >= MoveId.LEVEL3_USES -> "Stufe III · $n Einsätze"
        n >= MoveId.LEVEL2_USES -> "Stufe II · $n/${MoveId.LEVEL3_USES} Einsätze bis ★★"
        else -> "Stufe I · $n/${MoveId.LEVEL2_USES} Einsätze bis ★"
    }
}

fun moveLine(m: MoveId): String = buildString {
    m.type?.let { append(it.label) } ?: append("Unterstützung")
    if (m.power > 0) append(" · Stärke ${m.power}" + if (m.hits > 1) "×${m.hits}" else "")
    append(" · ${m.psiCost} Psi")
    append(" · ${m.info}")
}

// ------------------------------------------------------------ Kampf

@Composable
private fun BattleView(vm: PetViewModel, s: PetState, st: BattleState, sprite: Sprite?) {
    var target by remember { mutableIntStateOf(-1) }
    val alive = st.alive
    LaunchedEffect(alive.map { it.id }) { if (alive.none { it.id == target }) target = alive.firstOrNull { it.front }?.id ?: alive.firstOrNull()?.id ?: -1 }
    val selected = alive.firstOrNull { it.id == target }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Oberer Teil scrollt, damit Attacken unten immer sichtbar bleiben
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Gegner: hinten oben, vorne darunter
            val back = alive.filter { !it.front }
            val front = alive.filter { it.front }
            if (back.isNotEmpty()) EnemyRow(back, st, target, "hinten") { target = it }
            if (front.isNotEmpty()) EnemyRow(front, st, target, "vorne") { target = it }
            if (st.icicleWarning) Text("⚠️ Über dir knackt die Decke!", color = Color(0xFFE5484D), fontWeight = FontWeight.Bold)

            // Kampfbericht: feste Höhe, springt ans Ende
            val logScroll = rememberScrollState()
            LaunchedEffect(st.log.size, st.round) { logScroll.scrollTo(logScroll.maxValue) }
            Card(Modifier.fillMaxWidth().height(120.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(10.dp).verticalScroll(logScroll)) {
                    Text("Runde ${st.round}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    st.log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        // Tierchen
        Row(verticalAlignment = Alignment.CenterVertically) {
            PetIcon(sprite, s.stage, if (st.pet.hpPct() < 0.3f) Mood.SAD else Mood.HAPPY, 48.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(st.pet.name + "  " + st.pet.status.keys.joinToString(" ") { it.emoji }, fontWeight = FontWeight.Bold)
                HpBar("KP", st.pet.hp, st.pet.maxHp)
                HpBar("Psi", st.psi.toInt(), st.maxPsi, Color(0xFF8C6BE0))
            }
        }

        // Attacken
        st.moves.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    val ok = BattleEngine.canUse(st, m)
                    val eff = if (m.type != null && selected != null && BattleEngine.needsTarget(m)) TypeChart.label(BattleEngine.effectiveness(st, m, selected)) else ""
                    val reach = !BattleEngine.needsTarget(m) || selected == null || BattleEngine.targets(st, m).any { it.id == selected.id }
                    val combo = if (selected != null && reach) BattleEngine.comboFor(m, selected) else null
                    val charged = st.charging == m
                    FilledTonalButton(
                        onClick = { vm.battle(PetAction(m, if (reach) target else null)) },
                        enabled = ok,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text((if (m == st.fundMove) "🃏 " else "") + "${m.type?.emoji ?: "✳️"} ${m.label}" + stars(BattleEngine.level(st, m)), fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
                            Text(
                                if (charged) "Aufgeladen: jetzt loslassen!" else
                                    (if (m.power > 0) "Stärke ${m.power}" + (if (m.hits > 1) "×${m.hits}" else "") + " · " else "") + "${BattleEngine.cost(st, m)} Psi",
                                fontSize = 11.sp,
                            )
                            if (combo != null) Text("Kombo: ${combo.label}!", fontSize = 10.sp, color = Gold, fontWeight = FontWeight.Bold)
                            else if (eff.isNotEmpty() || !reach) Text(if (!reach) "erreicht nur vorne" else eff, fontSize = 10.sp, color = if (!reach || eff.startsWith("wenig") || eff.startsWith("fast")) Color(0xFFE5484D) else Color(0xFF2E7D32))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.battle(PetAction(potion = Potion.HEAL)) }, enabled = st.healPotions > 0, modifier = Modifier.weight(1f)) { Text("🧪 ${st.healPotions}") }
            OutlinedButton(onClick = { vm.battle(PetAction(potion = Potion.PSI)) }, enabled = st.psiPotions > 0, modifier = Modifier.weight(1f)) { Text("💜 ${st.psiPotions}") }
            OutlinedButton(onClick = { vm.battle(PetAction()) }, modifier = Modifier.weight(1f)) { Text("Abwarten") }
        }
    }
}

@Composable
private fun EnemyRow(list: List<Fighter>, st: BattleState, target: Int, row: String, onTap: (Int) -> Unit) {
    Column {
        Text("Reihe $row", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { e -> EnemyCard(e, st, e.id == target, Modifier.weight(1f)) { onTap(e.id) } }
            repeat((3 - list.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun EnemyCard(e: Fighter, st: BattleState, selected: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val hit = st.hits.filter { it.target == e.id }
    Column(
        modifier
            .border(if (selected) 3.dp else 1.dp, if (selected) Gold else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .background(if (e.elite || e.boss != null) Color(0x22FFB02E) else MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .clickable(onClick = onTap)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        e.intent?.let { Text("${it.kind.emoji} ${it.label}", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold, color = if (it.kind.name == "BIG" || it.kind.name == "CHARGE") Color(0xFFE5484D) else MaterialTheme.colorScheme.onSurface) }
        Text(e.emoji, fontSize = if (e.boss != null) 52.sp else 36.sp)
        Text(e.name, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold)
        Text((e.body?.label ?: "") + (e.role?.let { " · ${it.label}" } ?: "") + " · Lv ${e.level}", fontSize = 9.sp, maxLines = 1)
        LinearProgressIndicator(
            progress = { e.hpPct().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (e.hpPct() < 0.3f) Color(0xFFE5484D) else Color(0xFF46A758), trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round,
        )
        Text("${e.hp}/${e.maxHp}", fontSize = 9.sp)
        val extra = buildList {
            if (e.shells > 0) add("🛡️×${e.shells}")
            if (e.boss?.name == "FURNACE") add("🌡️${e.heat}/5")
            if (e.enemy?.trick?.name == "COUNTDOWN") add("💣${e.counter}")
            if (e.carried > 0) add("✨${e.carried}")
        }
        val statuses = e.status.keys.joinToString("") { it.emoji }
        if (statuses.isNotEmpty() || extra.isNotEmpty()) Text(statuses + " " + extra.joinToString(" "), fontSize = 11.sp, maxLines = 1)
        if (hit.isNotEmpty()) Text(hit.joinToString(" ") { if (it.heal) "+${it.amount}" else if (it.amount == 0) "verfehlt" else "-${it.amount}" }, fontSize = 11.sp, color = Color(0xFFE5484D), fontWeight = FontWeight.Bold)
    }
}
