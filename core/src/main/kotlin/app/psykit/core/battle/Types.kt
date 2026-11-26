package app.psykit.core.battle

/** Attackentypen. Krallen und Wucht nutzen den Angriff, alle anderen den Psi-Wert. */
enum class AttackType(val label: String, val emoji: String, val physical: Boolean) {
    CLAW("Krallen", "🐾", true),
    BLUNT("Wucht", "💥", true),
    FIRE("Feuer", "🔥", false),
    FROST("Frost", "❄️", false),
    POISON("Gift", "☠️", false),
    PSI("Psi", "🔮", false),
}

/** Körper der Gegner. Bestimmt zusammen mit dem Attackentyp, wie gut eine Attacke wirkt. */
enum class Body(val label: String, val emoji: String) {
    SOFT("Weich", "🧸"),
    ARMORED("Gepanzert", "🛡️"),
    QUICK("Flink", "💨"),
    PLANT("Pflanze", "🌿"),
    EMBER("Glut", "🌋"),
    GHOST("Geist", "👻"),
}

object TypeChart {
    private val table: Map<AttackType, List<Double>> = mapOf(
        //                    Weich Panzer Flink Pflanze Glut Geist
        AttackType.CLAW to listOf(1.5, 0.5, 1.0, 1.5, 1.0, 0.25),
        AttackType.BLUNT to listOf(0.5, 1.5, 0.5, 1.0, 1.0, 0.25),
        AttackType.FIRE to listOf(1.0, 1.0, 1.0, 2.0, 0.5, 1.0),
        AttackType.FROST to listOf(1.0, 0.5, 1.5, 1.0, 2.0, 1.0),
        AttackType.POISON to listOf(1.5, 0.5, 1.0, 0.5, 1.0, 0.5),
        AttackType.PSI to listOf(1.0, 1.0, 1.5, 1.0, 1.0, 2.0),
    )

    /** Faktor eines Typs gegen einen Körper. Das eigene Tierchen hat keinen Körper (Faktor 1). */
    fun factor(type: AttackType, body: Body?): Double = if (body == null) 1.0 else table.getValue(type)[body.ordinal]

    /** Kurzzeichen für die Typ-Matrix (Runde 15). */
    fun symbol(f: Double): String = when {
        f >= 2.0 -> "++"
        f > 1.0 -> "+"
        f < 0.3 -> "−−"
        f < 1.0 -> "−"
        else -> "·"
    }

    fun label(f: Double): String = when {
        f >= 2.0 -> "sehr effektiv"
        f > 1.0 -> "effektiv"
        f < 0.3 -> "fast wirkungslos"
        f < 1.0 -> "wenig effektiv"
        else -> ""
    }
}
