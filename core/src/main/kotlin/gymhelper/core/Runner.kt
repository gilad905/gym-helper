package gymhelper.core

import kotlinx.serialization.Serializable

enum class StepKind {
    /** A strength set: waits for "Set done". */
    SET,
    /** A timed set (cardio, treadmill phase): counts down once started. */
    TIMED,
    /** Rest between sets of the same machine. */
    REST,
    /** Moving to the next machine. */
    TRANSITION,
}

enum class Phase3 { WARMUP, MAIN, COOLDOWN }

@Serializable
data class Step(
    val kind: StepKind,
    val exerciseIndex: Int,
    /** 1-based set number this step belongs to (for REST: the set just finished). */
    val set: Int,
    val totalSets: Int,
    val durationSec: Int = 0,
    /** Treadmill phase for TIMED steps, null otherwise. */
    val phase: String? = null,
    /** Starts its countdown by itself when reached (rest, transition, treadmill main/cooldown). */
    val autoStart: Boolean = false,
)

object Steps {
    fun build(workout: Workout, transitionSec: Int, defaultRestSec: Int): List<Step> {
        val out = mutableListOf<Step>()
        workout.exercises.forEachIndexed { i, e ->
            val last = i == workout.exercises.lastIndex
            when (e.type) {
                ExerciseType.TREADMILL -> {
                    val w = e.effectiveWarmup
                    val c = e.effectiveCooldown
                    val main = e.durationSec ?: 0
                    var first = true
                    if (w != null && w.durationSec > 0) {
                        out += Step(StepKind.TIMED, i, 1, 1, w.durationSec, Phase3.WARMUP.name, autoStart = false)
                        first = false
                    }
                    if (main > 0) {
                        out += Step(StepKind.TIMED, i, 1, 1, main, Phase3.MAIN.name, autoStart = !first)
                        first = false
                    }
                    if (c != null && c.durationSec > 0) {
                        out += Step(StepKind.TIMED, i, 1, 1, c.durationSec, Phase3.COOLDOWN.name, autoStart = !first)
                    }
                }
                ExerciseType.CARDIO -> {
                    val sets = e.sets.coerceAtLeast(1)
                    for (s in 1..sets) {
                        out += Step(StepKind.TIMED, i, s, sets, e.durationSec ?: 0)
                        if (s < sets) out += Step(StepKind.REST, i, s, sets, e.restSec ?: defaultRestSec, autoStart = true)
                    }
                }
                ExerciseType.STRENGTH -> {
                    val sets = e.sets.coerceAtLeast(1)
                    for (s in 1..sets) {
                        out += Step(StepKind.SET, i, s, sets)
                        if (s < sets) out += Step(StepKind.REST, i, s, sets, e.restSec ?: defaultRestSec, autoStart = true)
                    }
                }
            }
            if (!last) out += Step(StepKind.TRANSITION, i + 1, 1, 1, transitionSec, autoStart = true)
        }
        return out
    }
}

/**
 * Persistent state of a running workout. Timers are stored as an end timestamp so they stay
 * correct across screen-off and process death.
 */
@Serializable
data class RunState(
    val workoutName: String,
    val steps: List<Step>,
    val index: Int = 0,
    /** Epoch millis at which the current countdown ends; null when not counting. */
    val endsAt: Long? = null,
    /** Remaining millis while paused; null when not paused. */
    val pausedRemaining: Long? = null,
    val startedAt: Long,
    val log: List<SetLog> = emptyList(),
) {
    val step: Step? get() = steps.getOrNull(index)
    val done: Boolean get() = index >= steps.size
    val counting: Boolean get() = endsAt != null
    val paused: Boolean get() = pausedRemaining != null

    fun remainingMs(now: Long): Long = when {
        pausedRemaining != null -> pausedRemaining
        endsAt != null -> (endsAt - now).coerceAtLeast(0)
        else -> (step?.durationSec ?: 0) * 1000L
    }

    /** The next step that needs you at a machine (for "up next" display). */
    fun nextWorkStep(): Step? = steps.drop(index + 1).firstOrNull { it.kind == StepKind.SET || it.kind == StepKind.TIMED }
}

/** What the runner wants the outside world to do after an action. */
enum class RunEvent { BEEP, FINISHED }

data class RunResult(val state: RunState, val events: List<RunEvent> = emptyList())

/** Pure state transitions for a workout run. [snapshot] supplies the exercise values to log for a set. */
class Runner(private val snapshot: (exerciseIndex: Int) -> Pair<String, Map<String, String>>) {

    fun start(workout: Workout, transitionSec: Int, defaultRestSec: Int, now: Long): RunState {
        val steps = Steps.build(workout, transitionSec, defaultRestSec)
        return enter(RunState(workout.name, steps, startedAt = now), 0, now)
    }

    /** The big button: "Set done" on a set, "Start" on a timed set, "Skip" on a countdown. */
    fun primary(s: RunState, now: Long): RunResult {
        val step = s.step ?: return RunResult(s)
        return when {
            step.kind == StepKind.SET -> finishWork(s, now)
            step.kind == StepKind.TIMED && !s.counting && !s.paused ->
                RunResult(s.copy(endsAt = now + step.durationSec * 1000L))
            step.kind == StepKind.TIMED -> finishWork(s, now)
            else -> advance(s, now)
        }
    }

    /** Call regularly; fires when a countdown reaches zero. */
    fun tick(s: RunState, now: Long): RunResult {
        val end = s.endsAt ?: return RunResult(s)
        if (s.paused || now < end) return RunResult(s)
        val step = s.step!!
        val r = if (step.kind == StepKind.TIMED) finishWork(s, now) else advance(s, now)
        return r.copy(events = listOf(RunEvent.BEEP) + r.events)
    }

    fun togglePause(s: RunState, now: Long): RunState = when {
        s.paused -> s.copy(endsAt = now + s.pausedRemaining!!, pausedRemaining = null)
        s.counting -> s.copy(pausedRemaining = (s.endsAt!! - now).coerceAtLeast(0), endsAt = null)
        else -> s
    }

    fun addTime(s: RunState, sec: Int): RunState = when {
        s.paused -> s.copy(pausedRemaining = s.pausedRemaining!! + sec * 1000L)
        s.counting -> s.copy(endsAt = s.endsAt!! + sec * 1000L)
        else -> s
    }

    /** Skip forward without logging. */
    fun skip(s: RunState, now: Long): RunResult = advance(s, now)

    /** Go back one step (does not remove logged sets). */
    fun back(s: RunState, now: Long): RunState {
        if (s.index <= 0) return s.copy(endsAt = null, pausedRemaining = null)
        var i = s.index - 1
        // Going back onto a rest/transition is rarely useful; land on the previous work step.
        while (i > 0 && s.steps[i].kind != StepKind.SET && s.steps[i].kind != StepKind.TIMED) i--
        return s.copy(index = i, endsAt = null, pausedRemaining = null)
    }

    private fun finishWork(s: RunState, now: Long): RunResult {
        val step = s.step!!
        val (key, values) = snapshot(step.exerciseIndex)
        val entry = SetLog(
            exerciseKey = key,
            exerciseIndex = step.exerciseIndex,
            set = step.set,
            phase = step.phase,
            at = now,
            values = values,
        )
        return advance(s.copy(log = s.log + entry), now)
    }

    private fun advance(s: RunState, now: Long): RunResult {
        val next = enter(s, s.index + 1, now)
        return RunResult(next, if (next.done) listOf(RunEvent.FINISHED) else emptyList())
    }

    private fun enter(s: RunState, index: Int, now: Long): RunState {
        val step = s.steps.getOrNull(index)
        val ends = if (step != null && step.autoStart && step.durationSec > 0) now + step.durationSec * 1000L else null
        return s.copy(index = index, endsAt = ends, pausedRemaining = null)
    }
}
