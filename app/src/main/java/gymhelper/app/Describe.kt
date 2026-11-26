package gymhelper.app

import gymhelper.core.Exercise
import gymhelper.core.Plan
import gymhelper.core.RunState
import gymhelper.core.StepKind

/** Human text for the current step, shared by the workout screen and the notification. */
data class StepText(
    /** Big label, e.g. "Set 2 of 3", "Rest", "Moving to machine", "Warmup". */
    val headline: String,
    /** The exercise this step is about (for a transition: the next machine). */
    val exercise: Exercise?,
    /** Smaller line, e.g. "Next: set 3 of 3". */
    val detail: String?,
    /** Button label. */
    val action: String,
    /** Short form for the notification, e.g. "Set 2/3". */
    val short: String,
)

fun describe(run: RunState, plan: Plan): StepText {
    val step = run.step ?: return StepText("Workout complete", null, null, "Close", "Done")
    val ex = plan.workout(run.workoutName)?.exercises?.getOrNull(step.exerciseIndex)
    return when (step.kind) {
        StepKind.SET -> StepText(
            "Set ${step.set} of ${step.totalSets}", ex, null, "Set done", "Set ${step.set}/${step.totalSets}",
        )
        StepKind.REST -> StepText(
            "Rest", ex, "Next: set ${step.set + 1} of ${step.totalSets}", "Skip rest",
            "Rest · next set ${step.set + 1}/${step.totalSets}",
        )
        StepKind.TRANSITION -> StepText(
            "Moving to", ex, null, "I'm ready", "Moving to ${ex?.name ?: "next machine"}",
        )
        StepKind.TIMED -> {
            val label = when (step.phase) {
                "WARMUP" -> "Warmup"
                "MAIN" -> "Main walk"
                "COOLDOWN" -> "Cooldown"
                else -> if (step.totalSets > 1) "Set ${step.set} of ${step.totalSets}" else "Cardio"
            }
            val started = run.counting || run.paused
            StepText(label, ex, null, if (started) "Finish early" else "Start", label)
        }
    }
}
