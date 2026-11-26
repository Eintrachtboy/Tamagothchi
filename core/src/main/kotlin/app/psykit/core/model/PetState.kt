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
    /** Runde 6: Mund-Markierung ausblenden, sobald Futter einmal am Mund gelandet ist. */
    val mouthSeen: Boolean = false,
    // Heute schon probierte Kraulstellen (für Hinweise und die Anzeige)
    val scratchTriedDay: Long = -1,
    val scratchTried: List<Zone> = emptyList(),
    // Kraulgrenze (K1): belohnte Kraulvorgänge heute und Zeitpunkt des letzten
    val scratchRewardDay: Long = -1,
    val scratchRewardsToday: Int = 0,
    val lastScratchRewardAt: Long = 0,
    // Minispiele, deren Startseite dieses Tierchen schon gesehen hat (Befund 34, Lernstand pro Tier)
    val gamesIntroSeen: List<String> = emptyList(),
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
    val berryStock: Int = 0,          // Blaubeeren (früher „Gartenbeeren“)
    val starBerries: Int = 0,
    val psiBerries: Int = 0,
    val goldBerries: Int = 0,
    val goldSeeds: Int = 0,
    val bedsUnlocked: Int = 3,
    val compost: Int = 0,             // weggeschaufelte Häufchen, 3 = 1 Dünger
    val fertilizer: Int = 0,
    val canLevel: Int = 0,            // 0 normal, 1 Kupfer, 2 Silber, 3 Gold
    val harvestsTotal: Int = 0,
    val berriesHarvested: List<String> = emptyList(),
    val smoothiesFound: List<String> = emptyList(),
    val gardenRewards: List<String> = emptyList(),
    // Tagesaufgaben
    val questDay: Long = -1,
    val questProgress: Map<String, Int> = emptyMap(),
    val questsClaimed: List<String> = emptyList(),
    // Sternenstaub, Schlüssel und Tränke
    val stardust: Long = 0,
    val stardustDay: Long = -1,
    val stardustFromGames: Int = 0,
    val keys: Int = 1,
    val keySteps: Long = 0,
    val healPotions: Int = 0,
    val psiPotions: Int = 0,
    // Energiedrinks: höchstens ein paar pro Tag
    val energyDrinkDay: Long = -1,
    val energyDrinksToday: Int = 0,
    // Attacken: gekauft/gefunden und die 4 ausgerüsteten (leer = Standard)
    val ownedMoves: List<String> = emptyList(),
    val equippedMoves: List<String> = emptyList(),
    // Kosmetik: Besitz, angelegte Teile, Glücksbringer und aufgestellte Deko
    val items: List<String> = emptyList(),
    val headItem: String? = null,
    val eyesItem: String? = null,
    val charmItem: String? = null,
    val placedDeco: List<String> = emptyList(),
    // Dungeon: geschaffte Ebenen der Reihe nach, besiegte Bosse, Rekord in der Tiefe, laufender Lauf
    val dungeonProgress: Int = 0,
    val bossesBeaten: List<Int> = emptyList(),
    val depthRecord: Int = 0,
    val run: app.psykit.core.dungeon.DungeonRun? = null,
    val lastRun: app.psykit.core.dungeon.RunReport? = null,
    /** Einsätze je Attacke (für Attacken-Stufen). */
    val moveUses: Map<String, Int> = emptyMap(),
    /** Schon entdeckte Kombos (Namen aus Combo). */
    val combosFound: List<String> = emptyList(),
    /** Siege je Ebene (Wächter/Boss), für versteckte Attacken. */
    val floorWins: Map<String, Int> = emptyMap(),
    /** Laden: Tag des Angebots, Angebot, Tagesangebot und ob heute neu gewürfelt wurde. */
    val shopDay: Long = -1,
    val shopMoves: List<String> = emptyList(),
    val shopItems: List<String> = emptyList(),
    val shopDeal: String? = null,
    val shopRerolled: Boolean = false,
) {
    val sluggish: Boolean get() = sluggishUntil > lastUpdate

    val mood: Mood get() = Mood.of(this)

    companion object {
        fun newEgg(now: Long, name: String = "Lumi") = PetState(name = name, bornAt = now, lastUpdate = now)
    }
}
