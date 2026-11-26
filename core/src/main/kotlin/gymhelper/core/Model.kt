package gymhelper.core

import kotlinx.serialization.Serializable

/** A whole training plan: a weekly schedule plus the workouts it refers to. */
@Serializable
data class Plan(
    val title: String = "Gym",
    val schedule: List<ScheduleEntry> = emptyList(),
    val workouts: List<Workout> = emptyList(),
) {
    fun workout(name: String): Workout? = workouts.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

    /** The workout scheduled for [day] (1 = Monday ... 7 = Sunday), if any. */
    fun workoutForDay(day: Int): Workout? =
        schedule.firstOrNull { it.day == day }?.let { workout(it.workout) }
}

/** [day] uses ISO numbering: 1 = Monday ... 7 = Sunday. */
@Serializable
data class ScheduleEntry(val day: Int, val workout: String)

@Serializable
data class Workout(
    val name: String,
    val exercises: List<Exercise> = emptyList(),
)

enum class ExerciseType { STRENGTH, CARDIO, TREADMILL }

/** A key/value property such as "Reps: 12 - 15" or "Seat: 7". Order is kept for round-tripping. */
@Serializable
data class Prop(val key: String, val value: String)

/** A treadmill warmup or cooldown phase. */
@Serializable
data class Phase(
    val duration: String,
    val props: List<Prop> = emptyList(),
) {
    val durationSec: Int get() = Durations.parse(duration) ?: 180
}

@Serializable
data class Exercise(
    /** Full display name as written, e.g. "Beinstreckung (14) — Leg Extension". */
    val name: String,
    val image: String? = null,
    val props: List<Prop> = emptyList(),
    val warmup: Phase? = null,
    val cooldown: Phase? = null,
) {
    fun prop(key: String): String? = props.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    val type: ExerciseType
        get() {
            when (prop(Keys.TYPE)?.trim()?.lowercase()) {
                "treadmill" -> return ExerciseType.TREADMILL
                "cardio" -> return ExerciseType.CARDIO
                "strength" -> return ExerciseType.STRENGTH
            }
            val n = name.lowercase()
            if ("treadmill" in n || "laufband" in n) return ExerciseType.TREADMILL
            return if (prop(Keys.DURATION) != null) ExerciseType.CARDIO else ExerciseType.STRENGTH
        }

    val sets: Int get() = prop(Keys.SETS)?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() } ?: 1
    val restSec: Int? get() = prop(Keys.REST)?.let { Durations.parse(it) }
    val durationSec: Int? get() = prop(Keys.DURATION)?.let { Durations.parse(it) }

    /** Machine number in parentheses, e.g. "14" from "Beinstreckung (14) — Leg Extension". */
    val machine: String? get() = Regex("\\((\\d+)\\)").find(name)?.groupValues?.get(1)

    val effectiveWarmup: Phase? get() = warmup ?: if (type == ExerciseType.TREADMILL) Phase("3 min") else null
    val effectiveCooldown: Phase? get() = cooldown ?: if (type == ExerciseType.TREADMILL) Phase("3 min") else null

    /** Props shown as text blocks rather than values. */
    fun isTextProp(key: String) = key.equals(Keys.INSTRUCTIONS, true) || key.equals(Keys.NOTES, true)

    /** Stable key for matching history entries to this exercise. */
    val historyKey: String get() = name.trim().lowercase()
}

object Keys {
    const val TYPE = "Type"
    const val SETS = "Sets"
    const val REPS = "Reps"
    const val WEIGHT = "Weight"
    const val REST = "Rest"
    const val DURATION = "Duration"
    const val WARMUP = "Warmup"
    const val COOLDOWN = "Cooldown"
    const val INSTRUCTIONS = "Instructions"
    const val NOTES = "Notes"
}

object Durations {
    /**
     * Parses "1 min 30 sec", "10 minutes", "25 min", "1:30", "90s", "90" (seconds) and "1.5 min".
     * Returns seconds, or null if nothing recognisable.
     */
    fun parse(text: String): Int? {
        val t = text.trim().lowercase()
        Regex("^(\\d+):(\\d{1,2})$").find(t)?.let {
            return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt()
        }
        var total = 0.0
        var found = false
        val unit = Regex("(\\d+(?:[.,]\\d+)?)\\s*(h|hours?|hrs?|m|mins?|minutes?|s|secs?|seconds?)\\b")
        for (m in unit.findAll(t)) {
            found = true
            val n = m.groupValues[1].replace(',', '.').toDouble()
            total += when (m.groupValues[2].first()) {
                'h' -> n * 3600
                'm' -> n * 60
                else -> n
            }
        }
        if (found) return total.toInt()
        return Regex("^(\\d+)$").find(t)?.groupValues?.get(1)?.toInt()
    }

    fun format(sec: Int): String {
        val s = if (sec < 0) 0 else sec
        return "%d:%02d".format(s / 60, s % 60)
    }
}

object Days {
    private val names = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    val short = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** "Mon", "monday", "Thur" -> 1..7, or null. */
    fun parse(text: String): Int? {
        val t = text.trim().lowercase()
        if (t.length < 3) return null
        val i = names.indexOf(t.take(3))
        return if (i >= 0) i + 1 else null
    }
}
