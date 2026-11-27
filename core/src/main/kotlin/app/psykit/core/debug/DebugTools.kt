package app.psykit.core.debug

import app.psykit.core.battle.MoveId
import app.psykit.core.dungeon.Floors
import app.psykit.core.engine.PetEngine
import app.psykit.core.model.PetState
import app.psykit.core.model.Stage

/**
 * Runde 21: Testmodus. Reine Funktionen, die einen Spielstand für Gerätetests verändern.
 * Sie ändern keine Spielregeln und fügen keine neuen Felder hinzu.
 */
object DebugTools {
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L

    /** Launen, für die es eigene Bilder gibt (normal, glücklich, traurig, hungrig, krank, schläft). */
    enum class ForcedMood(val label: String) { OK("Normal"), HAPPY("Glücklich"), SAD("Traurig"), HUNGRY("Hungrig"), SICK("Krank"), SLEEPING("Schläft") }

    /**
     * Stufe setzen. Level und Alter passen genau zur Stufe, damit [PetEngine.advance] sie weder
     * zurück- noch weiterentwickelt. EGG: Ei, das in [hatchInMs] schlüpft (mit Feier).
     */
    fun setStage(s: PetState, stage: Stage, now: Long, hatchInMs: Long = 5_000L): PetState = when (stage) {
        Stage.EGG -> s.copy(stage = Stage.EGG, level = 1, exp = 0, bornAt = now - PetEngine.EGG_HATCH_MS + hatchInMs, lastUpdate = now, hatchSeen = true, sleeping = false)
        else -> s.copy(stage = stage, level = stage.minLevel, exp = 0, bornAt = now - stage.minDays * DAY - HOUR, lastUpdate = now, hatchSeen = true)
    }

    fun forceMood(s: PetState, m: ForcedMood): PetState {
        val good = s.copy(satiety = 80f, happiness = 60f, hygiene = 90f, energy = 80f, health = 100f, poops = 0, sleeping = false, sluggishUntil = 0)
        return when (m) {
            ForcedMood.OK -> good
            ForcedMood.HAPPY -> good.copy(happiness = 95f)
            ForcedMood.SAD -> good.copy(happiness = 15f)
            ForcedMood.HUNGRY -> good.copy(satiety = 10f)
            ForcedMood.SICK -> good.copy(health = 20f)
            ForcedMood.SLEEPING -> good.copy(sleeping = true)
        }
    }

    fun setStats(s: PetState, satiety: Float, happiness: Float, hygiene: Float, energy: Float, health: Float): PetState =
        s.copy(satiety = satiety.coerceIn(0f, 100f), happiness = happiness.coerceIn(0f, 100f), hygiene = hygiene.coerceIn(0f, 100f),
            energy = energy.coerceIn(0f, 100f), health = health.coerceIn(0f, 100f))

    fun addPoops(s: PetState, n: Int) = s.copy(poops = (s.poops + n).coerceIn(0, 5))
    fun addDirt(s: PetState, n: Int) = s.copy(dirt = (s.dirt + n).coerceIn(0, 6), hygiene = (s.hygiene - 20f * n).coerceAtLeast(0f))

    /** Level erhöhen; die Stufe wächst beim nächsten Nachrechnen, wenn auch das Alter passt. */
    fun addLevels(s: PetState, n: Int) = s.copy(level = (s.level + n).coerceIn(1, 99), exp = 0)
    fun addTraining(s: PetState, n: Int) = s.copy(trainAtk = s.trainAtk + n, trainDef = s.trainDef + n, trainSpd = s.trainSpd + n, trainPsi = s.trainPsi + n)

    fun grant(s: PetState) = s.copy(
        stardust = s.stardust + 1000, keys = PetEngine.MAX_KEYS, healPotions = s.healPotions + 5, psiPotions = s.psiPotions + 5,
        berryStock = s.berryStock + 10, starBerries = s.starBerries + 5, psiBerries = s.psiBerries + 5, goldBerries = s.goldBerries + 2,
        goldSeeds = s.goldSeeds + 2, fertilizer = s.fertilizer + 5,
    )

    fun unlockMoves(s: PetState) = s.copy(ownedMoves = (s.ownedMoves + MoveId.entries.map { it.name }).distinct())

    /** Alle Ebenen bis einschließlich Welt [world] gelten als geschafft (Bosse besiegt). */
    fun unlockDungeon(s: PetState, world: Int): PetState {
        val floors = Floors.all.filter { !it.isDepth && it.world <= world }
        val next = (floors.maxOfOrNull { it.index } ?: -1) + 1
        return s.copy(dungeonProgress = maxOf(s.dungeonProgress, next), bossesBeaten = (s.bossesBeaten + (1..world)).distinct())
    }

    /** Zeit vorspulen: alle Zeitstempel rückwärts verschieben, danach rechnet [PetEngine.advance] die Zeit nach. */
    fun skipTime(s: PetState, hours: Int): PetState {
        val d = hours * HOUR
        fun sh(t: Long) = if (t > 0) t - d else t
        return s.copy(
            bornAt = s.bornAt - d, lastUpdate = s.lastUpdate - d, sluggishUntil = sh(s.sluggishUntil),
            lastScratchRewardAt = sh(s.lastScratchRewardAt),
            plots = s.plots.map { p ->
                p.copy(plantedAt = sh(p.plantedAt), lastWaterAt = sh(p.lastWaterAt), lastCountedAt = sh(p.lastCountedAt),
                    weedAt = sh(p.weedAt), weedSince = sh(p.weedSince), snailAt = sh(p.snailAt), snailSince = sh(p.snailSince))
            },
        )
    }

    /** Erstmals-Hinweise wieder zeigen (Spiel-Startseiten, Mund-Markierung, Kraulgrenze des Tages). */
    fun resetFirstTimes(s: PetState) = s.copy(gamesIntroSeen = emptyList(), mouthSeen = false, scratchRewardDay = -1, scratchRewardsToday = 0, lastScratchRewardAt = 0)
}
