package app.psykit.core.model

import kotlinx.serialization.Serializable

/**
 * Der komplette Spielstand. Alle Bedürfniswerte gehen von 0 bis 100 (100 = bestens).
 * Zeiten sind Millisekunden seit Epoch.
 */
@Serializable
data class PetState(
    val name: String = "Lumi",
    val bornAt: Long,
    val lastUpdate: Long,
    val satiety: Float = 70f,
    val happiness: Float = 70f,
    val hygiene: Float = 100f,
    val energy: Float = 100f,
    val health: Float = 100f,
    val poops: Int = 0,
    val sleeping: Boolean = false,
    val stage: Stage = Stage.EGG,
    val level: Int = 1,
    val exp: Int = 0,
    val totalExp: Long = 0,
    // interne Zähler
    val minutesSincePoop: Float = 0f,
    val goodCareMinutes: Float = 0f,
    val petWindowStart: Long = 0,
    val petCount: Int = 0,
    val snackWindowStart: Long = 0,
    val snackCount: Int = 0,
    // Schritte
    val pendingSteps: Long = 0,
    val stepsRedeemedTotal: Long = 0,
    // Statistik / spätere Kämpfe
    val gamesWon: Int = 0,
    val battlesWon: Int = 0,
    // Beziehung: Vertrauen 0..100 und Erinnerung, wie es behandelt wurde
    val trust: Float = 30f,
    val kindCount: Int = 0,
    val annoyCount: Int = 0,
    // Essen: entdeckte Vorlieben, Süßigkeiten-Zähler, träge bis
    val knownLikes: List<Food> = emptyList(),
    val knownDislikes: List<Food> = emptyList(),
    val sweetsWindowStart: Long = 0,
    val sweetsCount: Int = 0,
    val sluggishUntil: Long = 0,
    // Kraulstelle: Tag (epochDay), an dem sie gefunden wurde
    val scratchFoundDay: Long = -1,
    // Schmutz durch Spaziergänge
    val walkSteps: Long = 0,
    val dirt: Int = 0,
    // Training aus Spielen (für das spätere Kampfsystem)
    val trainAtk: Int = 0,
    val trainDef: Int = 0,
    val trainSpd: Int = 0,
    val trainPsi: Int = 0,
    // Garten
    val plots: List<Plot> = List(3) { Plot() },
    val berryStock: Int = 0,
    // Tagesaufgaben
    val questDay: Long = -1,
    val questProgress: Map<String, Int> = emptyMap(),
    val questsClaimed: List<String> = emptyList(),
) {
    val sluggish: Boolean get() = sluggishUntil > lastUpdate

    val mood: Mood get() = Mood.of(this)

    companion object {
        fun newEgg(now: Long, name: String = "Lumi") = PetState(name = name, bornAt = now, lastUpdate = now)
    }
}
