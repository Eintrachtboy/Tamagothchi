package app.psykit.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.psykit.core.engine.ActionResult
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.Food
import app.psykit.core.model.GameKind
import app.psykit.core.model.PetState
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

    fun feed(food: Food) = act { s, now -> PetEngine.feed(s, food, now) }
    fun stroke(speedDp: Float) = act { s, now -> PetEngine.stroke(s, speedDp, now) }
    fun scratch(zone: Zone) = act { s, now -> PetEngine.scratch(s, zone, now, repo.zone) }
    fun clean(fraction: Float) = act { s, _ -> PetEngine.clean(s, fraction) }
    fun removePoop() = repo.update { s, _ -> PetEngine.removePoop(s) to Unit }
    fun putToBed(hummed: Boolean) = act { s, now -> PetEngine.putToBed(s, now, repo.zone, hummed) }
    fun wake() = act { s, now -> PetEngine.wake(s, now, repo.zone) }
    fun redeemSteps() = act { s, _ -> PetEngine.redeemSteps(s) }

    /** Gibt null zurück, wenn gespielt werden darf, sonst den Grund. */
    fun checkPlay(): String? = PetEngine.canPlay(state.value)?.message?.also { _message.value = it }
    fun finishGame(kind: GameKind, score: Int, max: Int, bonusExp: Int = 0) =
        act { s, _ -> PetEngine.gameResult(s, kind, score, max, bonusExp) }
    fun todaysGame(): GameKind = GameKind.dailyOn(PetEngine.epochDay(System.currentTimeMillis(), repo.zone))

    fun rename(name: String) = repo.update { s, _ -> PetEngine.rename(s, name) to Unit }
    fun reset(name: String) { repo.reset(name); _message.value = "Ein neues Ei! Es schlüpft in ein paar Minuten." }

    /** Figuren-Paket (ZIP) importieren und direkt benutzen. */
    fun importZip(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val fallback = (sprites.displayName(uri) ?: "Paket").substringBeforeLast('.')
            val (info, unknown) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { packs.import(uri, fallback) }
            val msg = if (info == null) "Import fehlgeschlagen: keine passenden Bilder gefunden." + (if (unknown.isNotEmpty()) " (${unknown.take(5).joinToString()})" else "")
            else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { packs.activate(info.id) }
                if (info.petName.isNotBlank()) rename(info.petName)
                _spriteVersion.value++
                "Paket „${info.name}“ mit ${info.images} Bildern importiert und aktiviert." +
                    (if (unknown.isNotEmpty()) " Nicht zugeordnet: ${unknown.take(6).joinToString()}." else "")
            }
            _message.value = msg
            onDone(msg)
        }
    }

    fun activatePack(id: String) {
        val info = packs.activate(id)
        if (info != null) {
            if (info.petName.isNotBlank()) rename(info.petName)
            _message.value = "Jetzt mit Figur „${info.name}“."
        }
        _spriteVersion.value++
    }

    fun useBuiltIn() { packs.useBuiltIn(); _spriteVersion.value++; _message.value = "Jetzt mit der eingebauten Figur." }
    fun deletePack(id: String) { packs.delete(id); _spriteVersion.value++ }
    fun saveCurrentPack(name: String) { packs.saveCurrent(name.ifBlank { "Meine Bilder" }); _spriteVersion.value++ }

    fun setSprite(stage: Stage, variant: SpriteVariant, uri: Uri) {
        packs.markModified()
        _message.value = if (sprites.save(stage, variant, uri)) "Neues Bild für „${stage.displayName} · ${variant.label}“ gesetzt."
        else "Das Bild konnte nicht geladen werden."
        _spriteVersion.value++
    }
    /** Mehrere Bilder auf einmal importieren und anhand der Dateinamen zuordnen. */
    fun importPack(uris: List<Uri>) {
        packs.markModified()
        var ok = 0
        val unknown = mutableListOf<String>()
        uris.forEach { uri ->
            val name = sprites.displayName(uri) ?: "?"
            val target = SpriteNames.guess(name)
            if (target != null && sprites.save(target.first, target.second, uri)) ok++ else unknown += name
        }
        _spriteVersion.value++
        _message.value = buildString {
            append("$ok Bild(er) importiert.")
            if (unknown.isNotEmpty()) append(" Nicht zugeordnet: ${unknown.joinToString()}. Diese bitte einzeln setzen.")
        }
    }

    fun clearSprite(stage: Stage, variant: SpriteVariant) { packs.markModified(); sprites.clear(stage, variant); _spriteVersion.value++ }

    fun say(msg: String) { _message.value = msg }

    // ---------- Mehrere Tierchen ----------
    val currentPetId: StateFlow<String> = repo.currentId

    fun allPets() = repo.allPets()
    fun spritesFor(id: String) = if (id == sprites.petId) sprites else SpriteStore(getApplication(), id)

    private fun reloadSprites() {
        sprites = SpriteStore(getApplication(), repo.currentId.value)
        packs = CharacterPacks(getApplication(), sprites)
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
