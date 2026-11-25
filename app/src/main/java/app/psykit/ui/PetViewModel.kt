package app.psykit.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.psykit.core.battle.MoveId
import app.psykit.core.battle.PetAction
import app.psykit.core.battle.Potion
import app.psykit.core.dungeon.DungeonEngine
import app.psykit.core.dungeon.Shop
import app.psykit.core.engine.ActionResult
import app.psykit.core.model.Item
import app.psykit.core.model.Slot
import app.psykit.core.engine.PetEngine
import app.psykit.core.engine.GardenEngine
import app.psykit.core.model.Food
import app.psykit.core.model.GameKind
import app.psykit.core.model.PetState
import app.psykit.core.model.Quest
import app.psykit.core.model.Reaction
import app.psykit.core.model.Zone
import app.psykit.core.model.Stage
import app.psykit.data.PetRepository
import app.psykit.sprites.CharacterPacks
import app.psykit.sprites.SpriteNames
import app.psykit.sprites.SpriteStore
import app.psykit.sprites.SpriteVariant
import app.psykit.steps.StepSensor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PetViewModel(app: Application) : AndroidViewModel(app) {
    val repo = PetRepository.get(app)
    var sprites = SpriteStore(app, repo.currentId.value)
        private set
    var packs = CharacterPacks(app, sprites)
        private set
    val stepSensor = StepSensor(app)

    val state: StateFlow<PetState> = repo.state
    val stepsToday: StateFlow<Long> = repo.stepsToday

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    /** Letzte Reaktion des Tierchens (für Animationen). */
    private val _reaction = MutableStateFlow(ReactionEvent(0, Reaction.NONE))
    val reaction: StateFlow<ReactionEvent> = _reaction

    /** Zählt hoch, wenn sich ein eigenes Bild ändert, damit die Anzeige neu lädt. */
    private val _spriteVersion = MutableStateFlow(0)
    val spriteVersion: StateFlow<Int> = _spriteVersion

    private var stopSteps: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                repo.refresh()
                delay(20_000)
            }
        }
    }

    fun startSteps() {
        stopSteps?.invoke()
        stopSteps = stepSensor.listen { repo.onStepCounter(it) }
    }

    fun stopSteps() { stopSteps?.invoke(); stopSteps = null }

    private fun act(block: (PetState, Long) -> ActionResult) {
        val r = repo.update { s, now -> block(s, now).let { it.state to it } }
        _message.value = r.message
        if (r.reaction != Reaction.NONE) _reaction.value = ReactionEvent(_reaction.value.id + 1, r.reaction)
    }

    fun greet() { _message.value = PetEngine.greeting(state.value, System.currentTimeMillis(), repo.zone) }

    fun feed(food: Food) = act { s, now -> PetEngine.track(PetEngine.feed(s, food, now), Quest.FEED) }
    fun stroke(speedDp: Float) = act { s, now -> PetEngine.track(PetEngine.stroke(s, speedDp, now), Quest.STROKE) }
    fun scratch(zone: Zone) = act { s, now ->
        val r = PetEngine.scratch(s, zone, now, repo.zone)
        if (r.reaction == Reaction.JUMP) PetEngine.track(r, Quest.SCRATCH) else r
    }
    fun clean(fraction: Float) = act { s, _ -> PetEngine.track(PetEngine.clean(s, fraction), Quest.CLEAN) }

    // Garten & Tagesaufgaben
    private val _watering = MutableStateFlow(0)
    val watering: StateFlow<Int> = _watering
    fun plant(i: Int, berry: app.psykit.core.model.Berry = app.psykit.core.model.Berry.BLUE) = act { s, now -> PetEngine.plant(s, i, now, repo.zone, berry) }
    private fun garden(block: (PetState, Long) -> ActionResult) = act { s, now -> block(GardenEngine.refresh(s, now, repo.zone), now) }
    fun weed(i: Int) = garden { s, _ -> GardenEngine.weed(s, i) }
    fun removeSnail(i: Int) = garden { s, _ -> GardenEngine.removeSnail(s, i) }
    fun fertilize(i: Int) = garden { s, now -> GardenEngine.fertilize(s, i, now) }
    fun unlockBed() = garden { s, _ -> GardenEngine.unlockBed(s) }
    fun buyCan() = act { s, _ -> GardenEngine.buyCan(s) }
    fun mix(blue: Int, star: Int, psi: Int, gold: Int) = act { s, _ -> GardenEngine.mix(s, blue, star, psi, gold) }
    fun eatBerry(b: app.psykit.core.model.Berry) = act { s, _ -> GardenEngine.eat(s, b) }
    fun sellBerry(b: app.psykit.core.model.Berry, n: Int) = act { s, _ -> GardenEngine.sell(s, b, n) }
    fun brewPsiPotion() = act { s, _ -> GardenEngine.brewPsiPotion(s) }
    fun water(i: Int) {
        act { s, now -> PetEngine.water(s, i, now, repo.zone) }
        _watering.value++
    }
    fun harvest(i: Int) = act { s, now -> PetEngine.harvest(s, i, now, repo.zone) }
    fun claimQuest(q: Quest) = act { s, _ -> PetEngine.claimQuest(s, q) }
    fun removePoop() = act { s, _ -> PetEngine.scoopPoop(s) }
    fun putToBed(hummed: Boolean) = act { s, now -> PetEngine.putToBed(s, now, repo.zone, hummed) }
    fun wake() = act { s, now -> PetEngine.wake(s, now, repo.zone) }
    fun redeemSteps() = act { s, _ -> PetEngine.redeemSteps(s) }

    /** Gibt null zurück, wenn gespielt werden darf, sonst den Grund. */
    fun checkPlay(): String? = PetEngine.canPlay(state.value)?.message?.also { _message.value = it }
    fun finishGame(kind: GameKind, score: Int, max: Int, bonusExp: Int = 0) =
        act { s, _ -> PetEngine.track(PetEngine.gameResult(s, kind, score, max, bonusExp), Quest.PLAY) }
    fun todaysGame(): GameKind = GameKind.dailyOn(PetEngine.epochDay(System.currentTimeMillis(), repo.zone))

    fun rename(name: String) = repo.update { s, _ -> PetEngine.rename(s, name) to Unit }
    fun reset(name: String) { repo.reset(name); _message.value = "Ein neues Ei! Es schlüpft in ein paar Minuten." }

    private val _importReport = MutableStateFlow<List<app.psykit.sprites.ReportEntry>>(emptyList())
    /** Ausführlicher Bericht zum letzten Import (jede Datei mit Größe, Auflösung, Frames, Hinweisen). */
    val importReport: StateFlow<List<app.psykit.sprites.ReportEntry>> = _importReport

    /** Figuren-Paket (ZIP) importieren und direkt benutzen. */
    fun importZip(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val fallback = (sprites.displayName(uri) ?: "Paket").substringBeforeLast('.')
            val (info, unknown, report) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { packs.import(uri, fallback) }
            val msg = if (info == null) "Import fehlgeschlagen, das bisherige Paket bleibt unverändert." + (if (unknown.isNotEmpty()) "\n" + unknown.take(8).joinToString("\n") { "• $it" } else " Keine passenden Bilder gefunden.")
            else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { packs.activate(info.id) }
                _floating.value = packs.floating
                _headPoint.value = HeadPoint.decode(packs.headPoint) ?: HeadPoint()
                if (info.petName.isNotBlank()) rename(info.petName)
                _spriteVersion.value++
                "Paket „${info.name}“ mit ${info.images} Bildern importiert und aktiviert." +
                    (if (unknown.isNotEmpty()) "\nHinweise:\n" + unknown.take(8).joinToString("\n") { "• $it" } + (if (unknown.size > 8) "\n… und ${unknown.size - 8} weitere" else "") else "")
            }
            _importReport.value = report
            _message.value = msg
            onDone(msg)
        }
    }

    fun activatePack(id: String) {
        val info = packs.activate(id)
        _floating.value = packs.floating
        _headPoint.value = HeadPoint.decode(packs.headPoint) ?: HeadPoint()
        if (info != null) {
            if (info.petName.isNotBlank()) rename(info.petName)
            _message.value = "Jetzt mit Figur „${info.name}“."
        }
        _spriteVersion.value++
    }

    fun useBuiltIn() { packs.useBuiltIn(); packs.headPoint = null; _headPoint.value = HeadPoint(); _floating.value = packs.floating; _spriteVersion.value++; _message.value = "Jetzt mit der eingebauten Figur." }
    fun deletePack(id: String) { packs.delete(id); _spriteVersion.value++ }
    fun saveCurrentPack(name: String) { packs.saveCurrent(name.ifBlank { "Meine Bilder" }); _spriteVersion.value++ }

    fun setSprite(stage: Stage, variant: SpriteVariant, uri: Uri) {
        packs.markModified()
        val r = sprites.save(stage, variant, uri)
        val what = "„${stage.displayName} · ${variant.label}“"
        _message.value = when {
            r is app.psykit.sprites.ImageResult.Ok -> "Neues Bild für $what gesetzt."
            r.saved -> "Bild für $what gesetzt: ${r.reason()}."
            else -> "Bild für $what nicht übernommen, das bisherige bleibt: ${r.reason()}."
        }
        _spriteVersion.value++
    }
    /** Mehrere Bilder auf einmal importieren und anhand der Dateinamen zuordnen. */
    fun importPack(uris: List<Uri>) {
        packs.markModified()
        var ok = 0
        val notes = mutableListOf<String>()
        val report = mutableListOf<app.psykit.sprites.ReportEntry>()
        uris.forEach { uri ->
            val name = sprites.displayName(uri) ?: "?"
            val target = SpriteNames.guess(name)
            if (target == null) {
                notes += "$name: Dateiname unbekannt, bitte einzeln setzen"
                report += app.psykit.sprites.ReportEntry(name, null, app.psykit.sprites.ReportEntry.Status.REJECTED, "", listOf("Dateiname unbekannt, bitte einzeln setzen"))
                return@forEach
            }
            val r = sprites.save(target.first, target.second, uri)
            report += app.psykit.sprites.SpriteStore.report(name, "${target.first.displayName} · ${target.second.label}", sprites.lastSize, sprites.lastInfo, r)
            if (r.saved) ok++
            r.reason()?.let { notes += "$name: $it" + if (!r.saved) ", bisheriges Bild bleibt" else "" }
        }
        _spriteVersion.value++
        _importReport.value = report
        _message.value = buildString {
            append("$ok Bild(er) importiert.")
            notes.forEach { append("\n• $it") }
        }
    }

    fun clearSprite(stage: Stage, variant: SpriteVariant) { packs.markModified(); sprites.clear(stage, variant); _spriteVersion.value++ }

    fun say(msg: String) { _message.value = msg }

    // ---------- Dungeon, Kampf und Laden ----------
    fun enterDungeon(floor: Int) = act { s, now -> DungeonEngine.enter(s, floor, now) }
    fun chooseRoom(i: Int) = act { s, _ -> DungeonEngine.chooseRoom(s, i) }
    fun battle(action: PetAction) = act { s, _ -> DungeonEngine.battle(s, action) }
    fun resolveEvent(accept: Boolean) = act { s, _ -> DungeonEngine.resolveEvent(s, accept) }
    fun rest(heal: Boolean) = act { s, _ -> DungeonEngine.rest(s, heal) }
    fun merchantBuy(p: Potion) = act { s, _ -> DungeonEngine.buyFromMerchant(s, p) }
    fun leaveRoom() = act { s, _ -> DungeonEngine.leaveRoom(s) }
    fun crossBridge(fast: Boolean) = act { s, _ -> DungeonEngine.crossBridge(s, fast) }
    fun leaveDungeon() = act { s, _ -> DungeonEngine.leave(s) }
    fun clearLastRun() = repo.update { s, _ -> s.copy(lastRun = null) to Unit }

    fun chooseCard(i: Int) = act { s, _ -> DungeonEngine.chooseCard(s, i) }

    private fun shopDay(now: Long) = PetEngine.epochDay(now, repo.zone)
    /** Tagesangebot des Ladens erneuern, wenn ein neuer Tag begonnen hat. */
    fun refreshShop() = repo.update { s, now -> Shop.refresh(s, shopDay(now)) to Unit }
    fun rerollShop() = act { s, now -> Shop.reroll(Shop.refresh(s, shopDay(now)), shopDay(now)) }
    fun buyMove(m: MoveId) = act { s, now -> Shop.buyMove(Shop.refresh(s, shopDay(now)), m) }
    fun buyItem(i: Item) = act { s, now -> Shop.buyItem(Shop.refresh(s, shopDay(now)), i) }
    fun buyPotion(p: Potion) = act { s, _ -> Shop.buyPotion(s, p) }
    fun buyKey() = act { s, _ -> Shop.buyKey(s) }
    fun drinkEnergy() = act { s, _ -> Shop.drinkEnergy(s) }
    fun sellBerries(n: Int) = act { s, _ -> PetEngine.sellBerries(s, n) }
    fun wear(slot: Slot, item: Item?) = repo.update { s, _ -> Shop.wear(s, slot, item) to Unit }
    fun setCharm(item: Item?) = repo.update { s, _ -> Shop.setCharm(s, item) to Unit }
    fun toggleDeco(item: Item) = act { s, _ -> Shop.toggleDeco(s, item) }
    fun setMoves(moves: List<MoveId>) = repo.update { s, _ -> Shop.setMoves(s, moves) to Unit }

    /** Sitz von Hut und Brille auf eigenen Bildern. */
    private val _headPoint = MutableStateFlow(HeadPoint.decode(packs.headPoint) ?: HeadPoint())
    val headPoint: StateFlow<HeadPoint> = _headPoint
    fun setHeadPoint(h: HeadPoint) { packs.headPoint = h.encode(); _headPoint.value = h }

    // ---------- Mehrere Tierchen ----------
    val currentPetId: StateFlow<String> = repo.currentId

    fun allPets() = repo.allPets()
    fun visitedBy(partnerId: String, ratio: Float) = repo.updatePet(partnerId) { PetEngine.visited(it, ratio) }
    fun spritesFor(id: String) = if (id == sprites.petId) sprites else SpriteStore(getApplication(), id)

    private val _floating = MutableStateFlow(packs.floating)
    val floating: StateFlow<Boolean> = _floating
    fun setFloating(on: Boolean) { packs.setFloatingByUser(on); _floating.value = on }

    private fun reloadSprites() {
        sprites = SpriteStore(getApplication(), repo.currentId.value)
        packs = CharacterPacks(getApplication(), sprites)
        _floating.value = packs.floating
        _headPoint.value = HeadPoint.decode(packs.headPoint) ?: HeadPoint()
        _spriteVersion.value++
    }

    fun switchPet(id: String) {
        repo.switchTo(id)
        reloadSprites()
        greet()
    }

    fun addPet(name: String) {
        repo.addPet(name)
        reloadSprites()
        _message.value = "Ein neues Ei für ${state.value.name}! Es schlüpft in ein paar Minuten."
    }

    fun deletePet(id: String) {
        val app = getApplication<Application>()
        repo.deletePet(id)
        if (id != "p1") java.io.File(app.filesDir, "sprites_$id").deleteRecursively()
        else java.io.File(app.filesDir, "sprites").listFiles()?.forEach { it.delete() }
        reloadSprites()
    }

    override fun onCleared() { stopSteps() }
}

data class ReactionEvent(val id: Int, val reaction: Reaction)
