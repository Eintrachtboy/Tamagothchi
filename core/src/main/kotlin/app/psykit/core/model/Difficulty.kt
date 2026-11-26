package app.psykit.core.model

/**
 * Schwierigkeit eines Minispiels (Runde 12, Befund 20/23). Wählbar auf der Startseite und je Spiel gemerkt.
 * Zusätzlich steigt in jedem Spiel der Schwierigkeitsgrad automatisch mit den Runden bzw. Stufen.
 */
enum class Difficulty(val label: String, val emoji: String, val expFactor: Float) {
    EASY("Leicht", "🟢", 1f),
    NORMAL("Mittel", "🟡", 1f),
    HARD("Schwer", "🔴", 1.25f);

    /** Wählt den Wert zur Stufe. */
    fun <T> pick(easy: T, normal: T, hard: T): T = when (this) { EASY -> easy; NORMAL -> normal; HARD -> hard }

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/** Parameter der Spiele je Stufe, an einer Stelle, damit Anzeige und Rechnung nicht auseinanderlaufen. */
object GameTuning {
    // Wolkenhüpfen
    fun cloudWindow(d: Difficulty, level: Int): Float {
        val start = d.pick(0.24f, 0.20f, 0.15f)
        val floor = d.pick(0.16f, 0.12f, 0.09f)
        return (start - level * 0.004f).coerceAtLeast(floor)
    }
    fun cloudSpeed(d: Difficulty, level: Int): Float = d.pick(0.55f, 0.7f, 0.9f) + level * 0.05f

    // Wo steckt es?
    fun swapMs(d: Difficulty, round: Int): Int = (d.pick(800, 700, 520) - round * 60).coerceAtLeast(250)
    fun swapCount(d: Difficulty, round: Int): Int = d.pick(5, 7, 9) + round * 3

    // Psi-Memory: erlaubte Versuche
    fun memoryTries(d: Difficulty): Int = d.pick(20, 16, 13)
    // Psi-Reaktion: Zeit, in der das Leuchten sichtbar bleibt (ms), sinkt mit der Runde
    fun reactionGlowMs(d: Difficulty, index: Int): Long =
        (d.pick(850, 600, 470) - index * 15).coerceAtLeast(d.pick(520, 380, 300)).toLong()
    // Echo: Zeit je Leuchten (ms), sinkt mit der Runde
    fun echoOnMs(d: Difficulty, round: Int): Long = (d.pick(520, 420, 340) - round * 30).coerceAtLeast(d.pick(280, 200, 160)).toLong()
    // Seifenblasen: Größenfaktor und Steiggeschwindigkeit
    fun bubbleSize(d: Difficulty): Float = d.pick(1.25f, 1f, 0.8f)
    fun bubbleSpeed(d: Difficulty): Float = d.pick(0.8f, 1f, 1.25f)
    // Futter-Koch: Merkzeit des Rezepts (ms), sinkt mit der Runde
    fun cookShowMs(d: Difficulty, round: Int): Long = (d.pick(3800, 3000, 2300) - round * 350).coerceAtLeast(d.pick(2200, 1600, 1200)).toLong()
    // Schattenraten: Sekunden je Frage
    fun shadowSeconds(d: Difficulty): Int = d.pick(12, 9, 7)

    // Beerenfang
    fun berryFall(d: Difficulty): Float = d.pick(0.8f, 1f, 1.25f)
    fun berryGap(d: Difficulty): Float = d.pick(1.25f, 1f, 0.8f)
    fun catcherHalfWidth(d: Difficulty): Float = d.pick(0.11f, 0.085f, 0.07f)
}
