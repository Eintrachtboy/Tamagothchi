package app.psykit.core.model

enum class Mood(val label: String) {
    SLEEPING("schläft"),
    SICK("fühlt sich krank"),
    HUNGRY("hat Hunger"),
    DIRTY("fühlt sich schmutzig"),
    TIRED("ist müde"),
    SAD("ist traurig"),
    HAPPY("ist glücklich"),
    OK("ist zufrieden");

    companion object {
        fun of(s: PetState): Mood = when {
            s.stage == Stage.EGG -> OK
            s.sleeping -> SLEEPING
            s.health < 30f -> SICK
            s.satiety < 25f -> HUNGRY
            s.hygiene < 25f || s.poops >= 2 -> DIRTY
            s.energy < 20f -> TIRED
            s.happiness < 30f -> SAD
            s.happiness >= 75f && s.satiety >= 50f -> HAPPY
            else -> OK
        }
    }
}
