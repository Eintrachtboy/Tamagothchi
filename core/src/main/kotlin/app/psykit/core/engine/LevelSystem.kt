package app.psykit.core.engine

import app.psykit.core.model.PetState
import app.psykit.core.model.Stage

object LevelSystem {
    const val MAX_LEVEL = 50

    /** EP, die man von [level] bis zum nächsten Level braucht. */
    fun expForNext(level: Int): Int = 20 + (level - 1) * 10

    /** Fügt EP hinzu, steigt ggf. mehrere Level auf und entwickelt die Stufe weiter. */
    fun addExp(s: PetState, amount: Int): Pair<PetState, List<String>> {
        if (amount <= 0 || s.stage == Stage.EGG) return s to emptyList()
        var level = s.level
        var exp = s.exp + amount
        val events = mutableListOf<String>()
        while (level < MAX_LEVEL && exp >= expForNext(level)) {
            exp -= expForNext(level)
            level++
            events += "Level $level erreicht!"
        }
        if (level >= MAX_LEVEL) exp = 0
        val newStage = Stage.forLevel(level)
        if (newStage != s.stage) events += "${s.name} hat sich entwickelt: ${newStage.displayName}!"
        return s.copy(level = level, exp = exp, totalExp = s.totalExp + amount, stage = newStage) to events
    }
}
