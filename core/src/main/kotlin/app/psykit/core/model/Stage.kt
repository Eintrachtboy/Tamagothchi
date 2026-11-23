package app.psykit.core.model

/** Entwicklungsstufen. Jede Stufe hat eine eigene (austauschbare) Grafik. */
enum class Stage(val displayName: String, val minLevel: Int) {
    EGG("Ei", 0),
    BABY("Baby", 1),
    CHILD("Kind", 5),
    TEEN("Jugendlich", 10),
    ADULT("Erwachsen", 20);

    companion object {
        fun forLevel(level: Int): Stage =
            entries.filter { it != EGG && level >= it.minLevel }.maxByOrNull { it.minLevel } ?: BABY
    }
}
