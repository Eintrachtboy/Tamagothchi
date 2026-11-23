package app.psykit.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.psykit.core.engine.ActionResult
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage
import app.psykit.data.PetRepository
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
    val sprites = SpriteStore(app)
    val stepSensor = StepSensor(app)

    val state: StateFlow<PetState> = repo.state
    val stepsToday: StateFlow<Long> = repo.stepsToday

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    /** Zählt hoch, wenn gestreichelt wird (für die Herzchen-Animation). */
    private val _petPulse = MutableStateFlow(0)
    val petPulse: StateFlow<Int> = _petPulse

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
    }

    fun feedMeal() = act { s, now -> PetEngine.feedMeal(s, now) }
    fun feedSnack() = act { s, now -> PetEngine.feedSnack(s, now) }
    fun pet() {
        act { s, now -> PetEngine.pet(s, now) }
        if (!state.value.sleeping && state.value.stage != Stage.EGG) _petPulse.value++
    }
    fun clean() = act { s, now -> PetEngine.clean(s, now) }
    fun toggleSleep() = act { s, now -> PetEngine.toggleSleep(s, now, repo.zone) }
    fun redeemSteps() = act { s, _ -> PetEngine.redeemSteps(s) }

    /** Gibt null zurück, wenn gespielt werden darf, sonst den Grund. */
    fun checkPlay(): String? = PetEngine.canPlay(state.value)?.message?.also { _message.value = it }
    fun finishGame(wins: Int, rounds: Int) = act { s, _ -> PetEngine.playResult(s, wins, rounds) }

    fun rename(name: String) = repo.update { s, _ -> PetEngine.rename(s, name) to Unit }
    fun reset(name: String) { repo.reset(name); _message.value = "Ein neues Ei! Es schlüpft in ein paar Minuten." }

    fun setSprite(stage: Stage, variant: SpriteVariant, uri: Uri) {
        _message.value = if (sprites.save(stage, variant, uri)) "Neues Bild für „${stage.displayName} · ${variant.label}“ gesetzt."
        else "Das Bild konnte nicht geladen werden."
        _spriteVersion.value++
    }
    fun clearSprite(stage: Stage, variant: SpriteVariant) { sprites.clear(stage, variant); _spriteVersion.value++ }

    fun say(msg: String) { _message.value = msg }

    override fun onCleared() { stopSteps() }
}
