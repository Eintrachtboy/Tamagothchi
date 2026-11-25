package app.psykit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.psykit.core.battle.MoveId
import app.psykit.core.battle.MoveSource
import app.psykit.core.battle.Potion
import app.psykit.core.dungeon.DungeonEngine
import app.psykit.core.dungeon.Shop
import app.psykit.core.dungeon.World
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Item
import app.psykit.core.model.PetState
import app.psykit.core.model.Slot
import app.psykit.sprites.Sprite

/** Kurzinfo im Hauptbildschirm (Modus „Laden“). */
@Composable
fun ShopPanel(s: PetState, onShop: () -> Unit, onBook: () -> Unit) {
    Text("🛍️ Laden & Sammelbuch", fontWeight = FontWeight.Bold)
    Text("✨ ${s.stardust} Sternenstaub · 🫐 ${s.berryStock} Gartenbeeren", style = MaterialTheme.typography.bodyMedium)
    Text(
        "Sternenstaub gibt es für Minispiele, Aufgaben, eingelöste Schritte und im Dungeon. " +
            "Im Laden kaufst du Attacken, Tränke, Schlüssel und Kosmetik. Im Sammelbuch ziehst du dein Tierchen an.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onShop) { Text("🛍️ Laden") }
        OutlinedButton(onClick = onBook) { Text("📖 Sammelbuch") }
    }
}

@Composable
private fun FullDialog(title: String, s: PetState, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("✨ ${s.stardust} · 🗝️ ${s.keys} · 🧪 ${s.healPotions} · 💜 ${s.psiPotions}", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onClose) { Text("Schließen") }
                }
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
}

@Composable
private fun MessageLine(vm: PetViewModel) {
    val msg by vm.message.collectAsState()
    if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Tabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEachIndexed { i, t -> FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(t) }) }
    }
}

@Composable
private fun Entry(title: String, sub: String, action: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

fun sourceText(m: MoveId): String = when (val src = m.source) {
    is MoveSource.FromStage -> "Lernt man als ${src.stage.displayName}"
    is MoveSource.Shop -> "Im Laden für ${src.price} ✨"
    is MoveSource.Training -> "Ab ${src.needed} Training in ${src.stat.label}"
    is MoveSource.Drop -> "Fund: ${src.where}"
}

// ------------------------------------------------------------ Laden

@Composable
fun ShopDialog(vm: PetViewModel, s: PetState, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    FullDialog("🛍️ Laden", s, onClose) {
        Tabs(listOf("⚔️ Attacken", "🧪 Tränke & Schlüssel", "🎩 Kosmetik", "🫐 Beeren verkaufen"), tab) { tab = it }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageLine(vm)
            when (tab) {
                0 -> {
                    val known = MoveId.known(s)
                    Shop.moves.sortedBy { Shop.price(it) }.forEach { m ->
                        val has = m in known
                        Entry("${m.type?.emoji ?: "✳️"} ${m.label}", moveLine(m)) {
                            if (has) Text("✓ kann es", style = MaterialTheme.typography.labelLarge)
                            else Button(onClick = { vm.buyMove(m) }, enabled = s.stardust >= Shop.price(m)) { Text("${Shop.price(m)} ✨") }
                        }
                    }
                    Text("Weitere Attacken lernt dein Tierchen durch Wachsen, Training und Funde im Dungeon:", style = MaterialTheme.typography.bodySmall)
                    MoveId.entries.filter { it.source !is MoveSource.Shop && it !in known }.forEach { m ->
                        Entry("🔒 ${m.label}", sourceText(m) + " · " + moveLine(m)) {}
                    }
                }
                1 -> {
                    Potion.entries.forEach { p ->
                        val have = if (p == Potion.HEAL) s.healPotions else s.psiPotions
                        val what = if (p == Potion.HEAL) "Heilt im Kampf ${BattleEngineInfo.heal}" else "Füllt im Kampf ${BattleEngineInfo.psi}"
                        Entry("${p.emoji} ${p.label} (hast $have)", "$what. Ein Trank kostet einen Zug.") {
                            Button(onClick = { vm.buyPotion(p) }, enabled = s.stardust >= p.price) { Text("${p.price} ✨") }
                        }
                    }
                    val left = Shop.drinksLeft(s)
                    Entry("⚡ Energiedrink (heute noch $left)", "Sofort +${Shop.DRINK_ENERGY.toInt()} Energie, weckt dein Tierchen auf. Höchstens ${Shop.DRINKS_PER_DAY} pro Tag. Energie jetzt: ${s.energy.toInt()}") {
                        Button(onClick = { vm.drinkEnergy() }, enabled = s.stardust >= Shop.DRINK_PRICE && left > 0 && s.energy < 99f && s.stage != app.psykit.core.model.Stage.EGG) { Text("${Shop.DRINK_PRICE} ✨") }
                    }
                    Entry("🗝️ Schlüssel (hast ${s.keys}/${DungeonEngine.MAX_KEYS})", "Jeder Dungeon-Lauf braucht einen Schlüssel. Gratis: einer pro Tag und einer je ${DungeonEngine.KEY_STEPS} eingelöste Schritte.") {
                        Button(onClick = { vm.buyKey() }, enabled = s.stardust >= DungeonEngine.KEY_PRICE && s.keys < DungeonEngine.MAX_KEYS) { Text("${DungeonEngine.KEY_PRICE} ✨") }
                    }
                }
                2 -> {
                    Shop.items.sortedBy { it.price }.forEach { i ->
                        val has = i.name in s.items
                        Entry("${i.emoji} ${i.label}", "${i.slot.label} · ${i.rarity.label} · ${i.bonusText}") {
                            if (has) Text("✓ hast du", style = MaterialTheme.typography.labelLarge)
                            else Button(onClick = { vm.buyItem(i) }, enabled = s.stardust >= i.price) { Text("${i.price} ✨") }
                        }
                    }
                    Text("Die schönsten Teile gibt es nur im Dungeon: in Truhen und bei den Bossen.", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text("Gartenbeeren bringen je ${PetEngine.BERRY_PRICE} ✨. Du hast ${s.berryStock}.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 10).forEach { n ->
                            OutlinedButton(onClick = { vm.sellBerries(n) }, enabled = s.berryStock >= n) { Text("$n verkaufen") }
                        }
                        Button(onClick = { vm.sellBerries(s.berryStock) }, enabled = s.berryStock > 0) { Text("Alle") }
                    }
                    Text("Beeren, die du behältst, kann dein Tierchen weiter essen.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Texte für die Tränke, damit Laden und Kampf dasselbe sagen. */
private object BattleEngineInfo {
    val heal = "40 % deiner KP"
    val psi = "5 Psi-Punkte"
}

// ------------------------------------------------------------ Sammelbuch

@Composable
fun CollectionDialog(vm: PetViewModel, s: PetState, sprite: Sprite?, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val headPoint by vm.headPoint.collectAsState()
    FullDialog("📖 Sammelbuch", s, onClose) {
        // Vorschau mit allem, was gerade getragen wird
        PetScene(
            stage = s.stage, mood = s.mood, sleeping = false, poops = 0,
            phase = app.psykit.core.engine.DayPhase.at(s.lastUpdate, vm.repo.zone),
            custom = sprite, customIsVariant = sprite?.exact == true,
            reaction = ReactionEvent(0, app.psykit.core.model.Reaction.NONE),
            modifier = Modifier.fillMaxWidth().height(200.dp),
            floating = false,
            wear = Wearables.of(s, headPoint),
        )
        Spacer(Modifier.height(6.dp))
        Tabs(listOf("🎩 Anziehen", "🏠 Deko", "⚔️ Attacken", "📐 Sitz"), tab) { tab = it }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageLine(vm)
            when (tab) {
                0 -> WearTab(vm, s)
                1 -> DecoTab(vm, s)
                2 -> MovesEditor(s) { vm.setMoves(it); vm.say("Attacken gespeichert.") }
                else -> FitTab(vm, headPoint, custom = sprite != null)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WearTab(vm: PetViewModel, s: PetState) {
    val sets = Item.completeSets(s)
    if (sets.isNotEmpty()) Text("🏆 Titel: " + sets.joinToString { Item.setTitle(it) }, fontWeight = FontWeight.Bold)
    Text(
        "Hut, Brille und ein Glücksbringer wirken gleichzeitig. Ein komplettes Welt-Set gibt +5 % Schaden gegen die Gegner dieser Welt.",
        style = MaterialTheme.typography.bodySmall,
    )
    (0..5).forEach { w ->
        val items = Item.ofWorld(w)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val head = if (w == 0) "🛍️ Aus dem Laden" else World.of(w).let { "${it.emoji} Welt $w: ${it.label}" }
                Text("$head (${items.count { it.name in s.items }}/${items.size})", fontWeight = FontWeight.Bold)
                items.forEach { i -> ItemRow(vm, s, i) }
            }
        }
    }
}

@Composable
private fun ItemRow(vm: PetViewModel, s: PetState, i: Item) {
    val has = i.name in s.items
    val worn = when (i.slot) {
        Slot.HEAD -> s.headItem == i.name
        Slot.EYES -> s.eyesItem == i.name
        Slot.DECO -> i.name in s.placedDeco
    }
    val charm = s.charmItem == i.name
    Column(
        Modifier.fillMaxWidth()
            .background(if (worn || charm) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (has) i.emoji else "❔", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(if (has) i.label else "???", fontWeight = FontWeight.Bold)
                Text("${i.slot.label} · ${i.rarity.label}" + if (has) " · ${i.bonusText}" else "", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (has) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (i.slot) {
                Slot.HEAD, Slot.EYES -> TextButton(onClick = { vm.wear(i.slot, if (worn) null else i) }) { Text(if (worn) "Ablegen" else "Anziehen") }
                Slot.DECO -> TextButton(onClick = { vm.toggleDeco(i) }) { Text(if (worn) "Wegräumen" else "Aufstellen") }
            }
            if (i.slot == Slot.DECO || !worn) {
                TextButton(onClick = { vm.setCharm(if (charm) null else i) }) { Text(if (charm) "Kein Glücksbringer" else "Als Glücksbringer") }
            }
        }
    }
}

@Composable
private fun DecoTab(vm: PetViewModel, s: PetState) {
    Text("Bis zu ${Shop.MAX_DECO} Deko-Teile stehen im Bild (${s.placedDeco.size}/${Shop.MAX_DECO}). Deko ist Schmuck, ihr Bonus wirkt nur als Glücksbringer.", style = MaterialTheme.typography.bodySmall)
    val deco = Item.entries.filter { it.slot == Slot.DECO && it.name in s.items }
    if (deco.isEmpty()) Text("Noch keine Deko. Im Laden oder in Dungeon-Truhen findest du welche.")
    deco.forEach { ItemRow(vm, s, it) }
}

@Composable
private fun FitTab(vm: PetViewModel, h: HeadPoint, custom: Boolean) {
    Text("Sitz von Hut und Brille", fontWeight = FontWeight.Bold)
    Text(
        if (custom) "Bei eigenen Bildern weiß die App nicht, wo der Kopf ist. Schieb Hut und Brille hier zurecht. Das gilt für dieses Bildpaket."
        else "Bei der eingebauten Figur sitzen Hut und Brille automatisch. Die Regler wirken nur bei eigenen Bildern.",
        style = MaterialTheme.typography.bodySmall,
    )
    @Composable
    fun slider(label: String, v: Float, range: ClosedFloatingPointRange<Float>, set: (Float) -> Unit) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(value = v, onValueChange = set, valueRange = range, enabled = custom)
    }
    slider("Links ↔ rechts", h.x, 0f..1f) { vm.setHeadPoint(h.copy(x = it)) }
    slider("Höhe des Kopfes", h.y, 0f..0.8f) { vm.setHeadPoint(h.copy(y = it)) }
    slider("Kopfbreite", h.size, 0.15f..0.9f) { vm.setHeadPoint(h.copy(size = it)) }
    slider("Höhe der Augen", h.eyes, 0.05f..0.8f) { vm.setHeadPoint(h.copy(eyes = it)) }
    OutlinedButton(onClick = { vm.setHeadPoint(HeadPoint()) }, enabled = custom) { Text("Zurücksetzen") }
}
