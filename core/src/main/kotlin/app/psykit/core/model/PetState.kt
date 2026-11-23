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
) {
    val mood: Mood get() = Mood.of(this)

    companion object {
        fun newEgg(now: Long, name: String = "Lumi") = PetState(name = name, bornAt = now, lastUpdate = now)
    }
}
