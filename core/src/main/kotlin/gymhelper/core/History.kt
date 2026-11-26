package gymhelper.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One completed set (or cardio phase) with the values in effect when it was done. */
@Serializable
data class SetLog(
    val exerciseKey: String,
    val exerciseIndex: Int = 0,
    val set: Int,
    val phase: String? = null,
    val at: Long,
    /** Exercise name plus every property value at the time, e.g. "Reps" -> "12". */
    val values: Map<String, String>,
)

@Serializable
data class Session(
    val id: Long,
    val workoutName: String,
    val startedAt: Long,
    val endedAt: Long,
    val sets: List<SetLog>,
) {
    /** Sets grouped by exercise, in the order they were done. */
    fun byExercise(): List<Pair<String, List<SetLog>>> =
        sets.groupBy { it.exerciseKey }.map { (k, v) -> k to v }
}

@Serializable
data class History(val sessions: List<Session> = emptyList()) {

    /** Values used for this exercise in the most recent session that included it. */
    fun lastValues(exerciseKey: String): Map<String, String>? =
        sessions.asSequence()
            .sortedByDescending { it.startedAt }
            .mapNotNull { s -> s.sets.lastOrNull { it.exerciseKey == exerciseKey } }
            .firstOrNull()?.values

    fun add(s: Session) = copy(sessions = sessions + s)
    fun remove(id: Long) = copy(sessions = sessions.filterNot { it.id == id })

    /** Exercise progress: (date, value) for one property across sessions, oldest first. */
    fun progress(exerciseKey: String, key: String): List<Pair<Long, String>> =
        sessions.sortedBy { it.startedAt }.mapNotNull { s ->
            s.sets.lastOrNull { it.exerciseKey == exerciseKey }?.values?.get(key)?.let { s.startedAt to it }
        }
}

object LastNext {
    /** Snapshot of an exercise's values for logging. */
    fun snapshot(e: Exercise): Map<String, String> = buildMap {
        put(NAME_KEY, e.name)
        for (p in e.props) if (!e.isTextProp(p.key)) put(p.key, p.value)
        e.warmup?.let { w -> put("Warmup", w.duration); w.props.forEach { put("Warmup ${it.key}", it.value) } }
        e.cooldown?.let { c -> put("Cooldown", c.duration); c.props.forEach { put("Cooldown ${it.key}", it.value) } }
    }

    /** "12 → 15" when last differs from next, otherwise just next. */
    fun display(last: String?, next: String): String =
        if (last == null || last.trim() == next.trim()) next else "$last → $next"

    const val NAME_KEY = "_name"
}

object HistoryMarkdown {
    private val dateFmt = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm")

    fun write(history: History, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append("# Workout history\n\n")
        for (s in history.sessions.sortedByDescending { it.startedAt }) {
            val mins = ((s.endedAt - s.startedAt) / 60000).coerceAtLeast(0)
            append("## ").append(dateFmt.format(Instant.ofEpochMilli(s.startedAt).atZone(zone)))
                .append(" · ").append(s.workoutName).append(" (").append(mins).append(" min)\n\n")
            for ((_, sets) in s.byExercise()) {
                val name = sets.first().values[LastNext.NAME_KEY] ?: sets.first().exerciseKey
                append("### ").append(name).append('\n')
                for (l in sets) {
                    val label = l.phase?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Set ${l.set}"
                    val vals = l.values.filterKeys { it != LastNext.NAME_KEY && it != Keys.SETS && it != Keys.REST && it != Keys.TYPE }
                        .entries.joinToString(", ") { "${it.key} ${it.value}" }
                    append("- ").append(label)
                    if (vals.isNotEmpty()) append(": ").append(vals)
                    append('\n')
                }
                append('\n')
            }
        }
    }.trimEnd() + "\n"
}
