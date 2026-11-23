package app.psykit.core.engine

import java.time.Instant
import java.time.ZoneId

enum class DayPhase { MORNING, DAY, EVENING, NIGHT;
    val isNight get() = this == NIGHT

    companion object {
        const val WAKE_HOUR = 7
        const val BED_HOUR = 21

        fun ofHour(hour: Int): DayPhase = when (hour) {
            in WAKE_HOUR until 10 -> MORNING
            in 10 until 18 -> DAY
            in 18 until BED_HOUR -> EVENING
            else -> NIGHT
        }

        fun at(timeMs: Long, zone: ZoneId): DayPhase =
            ofHour(Instant.ofEpochMilli(timeMs).atZone(zone).hour)
    }
}
