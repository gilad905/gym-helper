package gymhelper.core

import java.net.URLDecoder

/**
 * Reads and writes plans as markdown.
 *
 * The reader is lenient: it accepts the clean format written by [write] as well as Notion exports,
 * where exercises are top-level bold bullets with nested "**Key:** value" bullets.
 */
object PlanMarkdown {

    private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
    private val imageRe = Regex("^!\\[[^\\]]*]\\((.+?)\\)$")
    private val bulletRe = Regex("^[-*+]\\s+(.*)$")
    private val boldOnlyRe = Regex("^\\*\\*(.+?)\\*\\*:?$")
    private val numberPrefixRe = Regex("^\\d+[.)]\\s+")
    private val keyValueRe = Regex("^([^:]{1,40}):\\s*(.*)$")
    private val keySpaceValueRe = Regex("^([A-Za-z][A-Za-z ./]{0,30}?)\\s+(\\d.*)$")
    private val workoutPrefixRe = Regex("^workout\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val workoutSuffixRe = Regex("^(.+?)\\s+workout$", RegexOption.IGNORE_CASE)

    private val canonicalKeys = listOf(
        Keys.TYPE, Keys.SETS, Keys.REPS, Keys.WEIGHT, Keys.REST, Keys.DURATION,
        Keys.WARMUP, Keys.COOLDOWN, Keys.INSTRUCTIONS, Keys.NOTES,
    ).associateBy { it.lowercase() }

    private class ExBuilder(val name: String) {
        var image: String? = null
        val props = mutableListOf<Prop>()
        var warmup: Phase? = null
        var cooldown: Phase? = null
        /** Phase currently receiving nested bullets, and the indent of its own bullet. */
        var phaseKey: String? = null
        var phaseIndent = -1

        fun addProp(p: Prop) {
            when (phaseKey) {
                Keys.WARMUP -> warmup = warmup!!.copy(props = warmup!!.props + p)
                Keys.COOLDOWN -> cooldown = cooldown!!.copy(props = cooldown!!.props + p)
                else -> {
                    val existing = props.indexOfFirst { it.key.equals(p.key, true) }
                    if (existing >= 0 && (p.key == Keys.NOTES || p.key == Keys.INSTRUCTIONS)) {
                        props[existing] = props[existing].copy(value = props[existing].value + " " + p.value)
                    } else props += p
                }
            }
        }

        fun build() = Exercise(name, image, props.toList(), warmup, cooldown)
    }

    fun parse(markdown: String): Plan {
        var title: String? = null
        val schedule = mutableListOf<ScheduleEntry>()
        val workouts = LinkedHashMap<String, MutableList<Exercise>>()
        val orphans = mutableListOf<Exercise>()
        var currentWorkout: String? = null
        var ex: ExBuilder? = null

        fun finishExercise() {
            val b = ex ?: return
            val built = b.build()
            val w = currentWorkout
            if (w == null) orphans += built else workouts.getOrPut(w) { mutableListOf() } += built
            ex = null
        }

        for (rawLine in markdown.replace("\r\n", "\n").split('\n')) {
            val line = rawLine.replace("\t", "    ").trimEnd()
            if (line.isBlank()) continue
            val indent = line.length - line.trimStart().length
            val content = line.trim()

            val heading = headingRe.find(content)
            if (heading != null) {
                val m = heading
                val level = m.groupValues[1].length
                val text = stripMd(m.groupValues[2])
                when {
                    level == 1 -> { finishExercise(); if (title == null) title = text }
                    text.equals("schedule", true) -> { finishExercise(); currentWorkout = null }
                    workoutPrefixRe.matches(text) || workoutSuffixRe.matches(text) || level == 2 -> {
                        finishExercise()
                        val name = workoutPrefixRe.find(text)?.groupValues?.get(1)
                            ?: workoutSuffixRe.find(text)?.groupValues?.get(1)
                            ?: text
                        currentWorkout = name.trim()
                        workouts.getOrPut(currentWorkout!!) { mutableListOf() }
                    }
                    else -> { finishExercise(); ex = ExBuilder(cleanName(text)) }
                }
                continue
            }

            imageRe.find(content)?.let { m ->
                ex?.image = decodePath(m.groupValues[1])
                continue
            }

            val bullet = bulletRe.find(content)
            val body = bullet?.groupValues?.get(1) ?: content

            // Schedule lines: "Mon: Full Body" (paragraph or bullet), outside exercises.
            if (ex == null || (bullet == null && indent == 0)) {
                val kv = keyValueRe.find(stripMd(body))
                val day = kv?.let { Days.parse(it.groupValues[1]) }
                if (kv != null && day != null && kv.groupValues[1].trim().all { it.isLetter() }) {
                    finishExercise()
                    schedule += ScheduleEntry(day, kv.groupValues[2].trim())
                    continue
                }
            }

            if (bullet != null) {
                // Notion style: a top-level bold bullet starts an exercise.
                val boldOnly = boldOnlyRe.find(body.trim())
                if (indent == 0 && boldOnly != null && !body.trim().endsWith(":") &&
                    !looksLikeKeyValue(stripMd(body))
                ) {
                    finishExercise()
                    ex = ExBuilder(cleanName(stripMd(boldOnly.groupValues[1])))
                    continue
                }
                val b = ex ?: continue
                if (b.phaseKey != null && indent <= b.phaseIndent) b.phaseKey = null
                val prop = parseProp(stripMd(body), b) ?: continue
                val isPhase = b.phaseKey == null &&
                    (prop.key == Keys.WARMUP || prop.key == Keys.COOLDOWN)
                if (isPhase) {
                    val phase = Phase(prop.value.ifBlank { "3 min" })
                    if (prop.key == Keys.WARMUP) b.warmup = phase else b.cooldown = phase
                    b.phaseKey = prop.key
                    b.phaseIndent = indent
                } else {
                    b.addProp(prop)
                }
                continue
            }

            // Loose paragraph text inside an exercise becomes notes.
            ex?.let { it.phaseKey = null; it.addProp(Prop(Keys.NOTES, stripMd(content))) }
        }
        finishExercise()

        val result = workouts.map { (name, list) -> Workout(name, list.toList()) }.toMutableList()
        if (orphans.isNotEmpty()) {
            val target = schedule.map { it.workout }.distinct()
                .firstOrNull { s -> result.none { it.name.equals(s, true) } }
                ?: "Workout"
            val idx = result.indexOfFirst { it.name.equals(target, true) }
            if (idx >= 0) result[idx] = result[idx].copy(exercises = orphans + result[idx].exercises)
            else result += Workout(target, orphans.toList())
        }
        return Plan(title ?: "Gym", schedule.toList(), result.toList())
    }

    private fun looksLikeKeyValue(text: String) = keyValueRe.find(text)?.let { it.groupValues[2].isNotBlank() } == true

    private fun parseProp(text: String, b: ExBuilder): Prop? {
        if (text.isBlank()) return null
        keyValueRe.find(text)?.let { m ->
            val key = canonical(m.groupValues[1].trim())
            if (key.isNotEmpty() && key.first().isLetter()) return Prop(key, m.groupValues[2].trim())
        }
        keySpaceValueRe.find(text)?.let { m ->
            return Prop(canonical(m.groupValues[1].trim()), m.groupValues[2].trim())
        }
        val alreadyHasDuration = b.props.any { it.key == Keys.DURATION }
        if (b.phaseKey == null && !alreadyHasDuration && Durations.parse(text) != null) {
            return Prop(Keys.DURATION, text.trim())
        }
        return Prop(Keys.NOTES, text.trim())
    }

    private fun canonical(key: String): String {
        val k = key.trim()
        canonicalKeys[k.lowercase()]?.let { return it }
        if (k.equals("note", true)) return Keys.NOTES
        if (k.equals("warm up", true) || k.equals("warm-up", true)) return Keys.WARMUP
        if (k.equals("cool down", true) || k.equals("cool-down", true)) return Keys.COOLDOWN
        return k.replaceFirstChar { it.uppercase() }
    }

    private fun cleanName(text: String) = text.replace(numberPrefixRe, "").trim()

    private fun stripMd(text: String) = text.replace("**", "").replace("__", "").replace("`", "").trim()

    private fun decodePath(p: String): String =
        try { URLDecoder.decode(p.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { p }

    fun write(plan: Plan): String = buildString {
        append("# ").append(plan.title).append("\n\n")
        if (plan.schedule.isNotEmpty()) {
            append("## Schedule\n")
            for (s in plan.schedule.sortedBy { it.day }) {
                append("- ").append(Days.short[s.day - 1]).append(": ").append(s.workout).append('\n')
            }
            append('\n')
        }
        for (w in plan.workouts) {
            append("## Workout: ").append(w.name).append("\n\n")
            for (e in w.exercises) {
                append("### ").append(e.name).append('\n')
                e.image?.let { append("![](").append(it.replace(" ", "%20")).append(")\n") }
                for (p in e.props) append("- ").append(p.key).append(": ").append(p.value).append('\n')
                for ((key, phase) in listOf(Keys.WARMUP to e.warmup, Keys.COOLDOWN to e.cooldown)) {
                    if (phase == null) continue
                    append("- ").append(key).append(": ").append(phase.duration).append('\n')
                    for (p in phase.props) append("    - ").append(p.key).append(": ").append(p.value).append('\n')
                }
                append('\n')
            }
        }
    }.trimEnd() + "\n"
}
