package app.psykit.core.model

/**
 * Entwicklungsstufen. Jede Stufe hat eine eigene (austauschbare) Grafik.
 * Weiterentwickeln braucht ein Mindest-Level UND ein Mindestalter in Tagen.
 */
enum class Stage(val displayName: String, val minLevel: Int, val minDays: Int) {
    EGG("Ei", 0, 0),
    BABY("Baby", 1, 0),
    CHILD("Kind", 8, 2),
    TEEN("Jugendlich", 18, 12),
    ADULT("Erwachsen", 30, 30);

    companion object {
        fun forLevel(level: Int, ageDays: Long = Long.MAX_VALUE): Stage =
            entries.filter { it != EGG && level >= it.minLevel && ageDays >= it.minDays }.maxByOrNull { it.ordinal } ?: BABY

        /** Nie zurückentwickeln. */
        fun next(current: Stage, level: Int, ageDays: Long): Stage {
            if (current == EGG) return EGG
            val s = forLevel(level, ageDays)
            return if (s.ordinal > current.ordinal) s else current
        }
    }
}
