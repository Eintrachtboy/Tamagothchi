package app.psykit.ui

import androidx.compose.material3.HorizontalDivider

import androidx.compose.ui.layout.onSizeChanged

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
import app.psykit.core.battle.MoveId
import app.psykit.core.battle.MoveSource
import app.psykit.core.battle.Potion
import app.psykit.core.dungeon.DungeonEngine
import app.psykit.core.dungeon.Shop
import app.psykit.core.dungeon.World
import app.psykit.core.engine.GardenEngine
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Berry
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
    FullScreenLayer(onBack = onClose) {
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
    is MoveSource.Hidden -> "Versteckt im Dungeon (${app.psykit.core.dungeon.Floors.get(src.floor).let { "${it.code} ${it.name}" }})"
}

/** Stunden bis Mitternacht, dann gibt es ein neues Angebot. */
private fun hoursToMidnight(zone: java.time.ZoneId): Long {
    val now = java.time.ZonedDateTime.now(zone)
    val next = now.toLocalDate().plusDays(1).atStartOfDay(zone)
    return (java.time.Duration.between(now, next).toMinutes() + 59) / 60
}

// ------------------------------------------------------------ Laden

@Composable
fun ShopDialog(vm: PetViewModel, s: PetState, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshShop() }
    val today = PetEngine.epochDay(System.currentTimeMillis(), vm.repo.zone)
    FullDialog("🛍️ Laden", s, onClose) {
        Tabs(listOf("⚔️ Attacken", "🧪 Tränke & Schlüssel", "🎩 Kosmetik", "🫐 Garten"), tab) { tab = it }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageLine(vm)
            when (tab) {
                0 -> {
                    val known = MoveId.known(s)
                    val h = hoursToMidnight(vm.repo.zone)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(10.dp).fillMaxWidth()) {
                            Text("📅 Heute im Angebot" + if (Shop.isHighlightDay(today)) " · 🌟 Montags-Highlight" else "", fontWeight = FontWeight.Bold)
                            Text("Neues Angebot in ${if (h <= 1) "unter 1 Std." else "$h Std."} Jeden Tag liegen andere Attacken aus.", style = MaterialTheme.typography.bodySmall)
                            val can = Shop.canReroll(s, today)
                            TextButton(onClick = { vm.rerollShop() }, enabled = can && s.stardust >= Shop.REROLL_PRICE) {
                                Text(if (can) "🎲 Neu würfeln (${Shop.REROLL_PRICE} ✨, einmal am Tag)" else "🎲 Heute schon neu gewürfelt")
                            }
                        }
                    }
                    val offer = Shop.offerMoves(s)
                    if (offer.isEmpty()) Text("Heute ist alles ausverkauft. Dein Tierchen kann schon jede Laden-Attacke!", style = MaterialTheme.typography.bodyMedium)
                    offer.forEachIndexed { i, m ->
                        val has = m in known
                        val deal = m.name == s.shopDeal
                        val highlight = i == 0 && Shop.isHighlightDay(today) && Shop.price(m) >= Shop.HIGHLIGHT_PRICE
                        val tag = (if (deal) "🏷️ Angebot −${Shop.DEAL_PCT} % · " else "") + (if (highlight) "🌟 Highlight · " else "")
                        Entry("${m.type?.emoji ?: "✳️"} ${m.label}", tag + moveLine(m)) {
                            if (has) Text("✓ kann es", style = MaterialTheme.typography.labelLarge)
                            else Button(onClick = { vm.buyMove(m) }, enabled = s.stardust >= Shop.price(s, m)) {
                                Text(if (deal) "${Shop.price(m)} → ${Shop.price(s, m)} ✨" else "${Shop.price(m)} ✨")
                            }
                        }
                    }
                    val later = Shop.moves.filter { it !in known && it !in offer }
                    if (later.isNotEmpty()) Text("Kommen an anderen Tagen in den Laden: " + later.joinToString { it.label }, style = MaterialTheme.typography.bodySmall)
                    Text("Weitere Attacken lernt dein Tierchen durch Wachsen, Training und Funde im Dungeon:", style = MaterialTheme.typography.bodySmall)
                    MoveId.entries.filter { it.source !is MoveSource.Shop && it !in known }.forEach { m ->
                        if (m.source is MoveSource.Hidden) Entry("❔ ???", sourceText(m) + " · Nicht zu kaufen") {}
                        else Entry("🔒 ${m.label}", sourceText(m) + " · " + moveLine(m)) {}
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
                    Text("Jeden Tag ${Shop.OFFER_ITEMS} andere Teile.", style = MaterialTheme.typography.bodySmall)
                    val goods = Shop.offerItems(s)
                    if (goods.isEmpty()) Text("Heute nichts Neues: Du hast schon alles aus dem Laden.", style = MaterialTheme.typography.bodyMedium)
                    goods.sortedBy { it.price }.forEach { i ->
                        val has = i.name in s.items
                        Entry("${i.emoji} ${i.label}", "${i.slot.label} · ${i.rarity.label} · ${i.bonusText}") {
                            if (has) Text("✓ hast du", style = MaterialTheme.typography.labelLarge)
                            else Button(onClick = { vm.buyItem(i) }, enabled = s.stardust >= i.price) { Text("${i.price} ✨") }
                        }
                    }
                    Text("Die schönsten Teile gibt es nur im Dungeon: in Truhen und bei den Bossen.", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text("Beeren verkaufen", fontWeight = FontWeight.Bold)
                    Berry.values().forEach { b ->
                        val have = GardenEngine.count(s, b)
                        Entry("${b.emoji} ${b.label}", "je ${b.sellPrice} ✨ · du hast $have") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { vm.sellBerry(b, 1) }, enabled = have >= 1) { Text("1") }
                                Button(onClick = { vm.sellBerry(b, have) }, enabled = have > 0) { Text("Alle") }
                            }
                        }
                    }
                    Entry("🧪 Psi-Trank brauen", "2 Psi-Beeren werden zu einem Psi-Trank (du hast ${s.psiBerries})") {
                        Button(onClick = { vm.brewPsiPotion() }, enabled = s.psiBerries >= 2) { Text("Brauen") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Gießkanne", fontWeight = FontWeight.Bold)
                    val k = s.canLevel
                    if (k > 0) Text("Du hast: ${GardenEngine.CAN_NAMES[k - 1]}", style = MaterialTheme.typography.bodyMedium)
                    if (k < GardenEngine.CAN_NAMES.size) {
                        Entry("🚿 ${GardenEngine.CAN_NAMES[k]}", GardenEngine.CAN_TEXTS[k]) {
                            Button(onClick = { vm.buyCan() }, enabled = s.stardust >= GardenEngine.CAN_PRICES[k]) { Text("${GardenEngine.CAN_PRICES[k]} ✨") }
                        }
                    } else Text("Das ist schon die beste Gießkanne.", style = MaterialTheme.typography.bodySmall)
                    Text("Beeren, die du behältst, kann dein Tierchen essen oder im Smoothie-Mixer verwenden.", style = MaterialTheme.typography.bodySmall)
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
    val footprint = rememberFootprint(sprite)
    var previewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    FullDialog("📖 Sammelbuch", s, onClose) {
        // Vorschau mit allem, was gerade getragen wird; im Tab „Sitz“ mit Körperpunkten (Befund 06)
        val body = remember(footprint, headPoint, previewSize) {
            SceneBody.of(footprint, headPoint, previewSize.width.toFloat(), previewSize.height.toFloat(), density, false)
        }
        PetScene(
            stage = s.stage, mood = s.mood, sleeping = false, poops = 0,
            phase = app.psykit.core.engine.DayPhase.at(s.lastUpdate, vm.repo.zone),
            custom = sprite, customIsVariant = sprite?.exact == true,
            reaction = ReactionEvent(0, app.psykit.core.model.Reaction.NONE),
            modifier = Modifier.fillMaxWidth().height(200.dp).onSizeChanged { previewSize = it },
            floating = false,
            wear = Wearables.of(s, headPoint),
            footprintIn = footprint,
            overlay = { if (tab == 5) body?.let { BodyMarkers(it) } },
        )
        Spacer(Modifier.height(6.dp))
        Tabs(listOf("🎩 Anziehen", "🏠 Deko", "⚔️ Attacken", "✨ Kombos", "🌱 Garten", "📐 Sitz"), tab) { tab = it }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageLine(vm)
            when (tab) {
                0 -> WearTab(vm, s)
                1 -> DecoTab(vm, s)
                2 -> MovesEditor(s) { vm.setMoves(it); vm.say("Attacken gespeichert.") }
                3 -> ComboTab(s)
                4 -> GardenBookTab(s)
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
private fun ComboTab(s: PetState) {
    val found = app.psykit.core.battle.Combo.entries.count { it.name in s.combosFound }
    Text("Kombos ($found/${app.psykit.core.battle.Combo.entries.size})", fontWeight = FontWeight.Bold)
    Text("Manche Zustände und Attackentypen wirken zusammen besonders stark. Eine Kombo erscheint hier, sobald dein Tierchen sie zum ersten Mal auslöst. Im Kampf leuchtet sie golden auf der Attacke.", style = MaterialTheme.typography.bodySmall)
    app.psykit.core.battle.Combo.entries.forEach { c ->
        if (c.name in s.combosFound) Entry("✨ ${c.label}", c.info) {}
        else Entry("❔ ???", "Noch nicht entdeckt") {}
    }
    val hidden = MoveId.entries.filter { it.source is MoveSource.Hidden }
    Text("Versteckte Attacken (${hidden.count { it.name in s.ownedMoves }}/${hidden.size})", fontWeight = FontWeight.Bold)
    hidden.forEach { m ->
        if (m.name in s.ownedMoves) Entry("${m.type?.emoji ?: "✳️"} ${m.label}", sourceText(m) + " · " + moveLine(m)) {}
        else Entry("❔ ???", sourceText(m)) {}
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
    // Befund 06: Mund und Schwanz für Füttern und Kraulen (ohne Einstellung automatisch aus dem Umriss)
    HorizontalDivider()
    Text("Mund und Schwanz", fontWeight = FontWeight.Bold)
    Text(
        "Der rosa Kreis ist der Mund (dorthin Futter ziehen), der grüne der Schwanz. Ohne Einstellung sucht die App beides selbst im Bild.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (h.mouthX == null) OutlinedButton(onClick = { vm.setHeadPoint(h.copy(mouthX = h.x, mouthY = (h.eyes + 0.12f).coerceAtMost(1f))) }, enabled = custom) { Text("Mund selbst einstellen") }
    else {
        slider("Mund links ↔ rechts", h.mouthX, 0f..1f) { vm.setHeadPoint(h.copy(mouthX = it)) }
        slider("Mund Höhe", h.mouthY ?: 0.4f, 0f..1f) { vm.setHeadPoint(h.copy(mouthY = it)) }
        TextButton(onClick = { vm.setHeadPoint(h.copy(mouthX = null, mouthY = null)) }) { Text("Mund automatisch") }
    }
    if (h.tailX == null) OutlinedButton(onClick = { vm.setHeadPoint(h.copy(tailX = 0.8f, tailY = 0.75f)) }, enabled = custom) { Text("Schwanz selbst einstellen") }
    else {
        slider("Schwanz links ↔ rechts", h.tailX, 0f..1f) { vm.setHeadPoint(h.copy(tailX = it)) }
        slider("Schwanz Höhe", h.tailY ?: 0.75f, 0f..1f) { vm.setHeadPoint(h.copy(tailY = it)) }
        TextButton(onClick = { vm.setHeadPoint(h.copy(tailX = null, tailY = null)) }) { Text("Schwanz automatisch") }
    }
    OutlinedButton(onClick = { vm.setHeadPoint(HeadPoint()) }, enabled = custom) { Text("Zurücksetzen") }
}

/** Zeigt Kopf-, Mund- und Schwanzpunkt auf der Vorschau (Befund 06). */
@Composable
fun androidx.compose.foundation.layout.BoxScope.BodyMarkers(b: SceneBody) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val r = 10.dp.toPx()
        drawCircle(androidx.compose.ui.graphics.Color(0xFFE0569A), r, b.mouth, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        drawCircle(androidx.compose.ui.graphics.Color(0xFF3FA66B), r, b.tail, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        drawRect(androidx.compose.ui.graphics.Color(0x553F6FD8), topLeft = b.rect.topLeft, size = b.rect.size, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}
