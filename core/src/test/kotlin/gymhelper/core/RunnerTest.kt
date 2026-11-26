package gymhelper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunnerTest {
    private val plan = PlanMarkdown.parse(javaClass.getResource("/notion-export.md")!!.readText())
    private val full = plan.workout("Full Body")!!

    private fun runner(w: Workout) = Runner { i -> w.exercises[i].historyKey to LastNext.snapshot(w.exercises[i]) }

    @Test
    fun stepsForFullBody() {
        val steps = Steps.build(full, 90, 90)
        // crosstrainer timed, transition
        assertEquals(StepKind.TIMED, steps[0].kind)
        assertEquals(600, steps[0].durationSec)
        assertEquals(StepKind.TRANSITION, steps[1].kind)
        assertEquals(1, steps[1].exerciseIndex)
        // leg extension: set, rest, set, rest, set, transition (transition replaces the last rest)
        assertEquals(
            listOf(StepKind.SET, StepKind.REST, StepKind.SET, StepKind.REST, StepKind.SET, StepKind.TRANSITION),
            steps.subList(2, 8).map { it.kind },
        )
        assertEquals(StepKind.TIMED, steps.last().kind) // bike ends the workout
    }

    @Test
    fun treadmillHasWarmupMainCooldown() {
        val cardio = plan.workout("Cardio")!!
        val steps = Steps.build(cardio, 90, 90)
        assertEquals(listOf("WARMUP", "MAIN", "COOLDOWN"), steps.map { it.phase })
        assertEquals(listOf(180, 1500, 180), steps.map { it.durationSec })
        assertEquals(listOf(false, true, true), steps.map { it.autoStart })

        val r = runner(cardio)
        var s = r.start(cardio, 90, 90, now = 0)
        assertNull(s.endsAt) // waits for Start
        s = r.primary(s, 0).state
        assertEquals(180_000L, s.endsAt)
        val res = r.tick(s, 180_000)
        assertEquals(listOf(RunEvent.BEEP), res.events)
        assertEquals("MAIN", res.state.step!!.phase)
        assertEquals(180_000L + 1_500_000L, res.state.endsAt) // main starts on its own
        val end = r.tick(r.tick(res.state, 1_680_000).state, 1_860_000)
        assertTrue(end.state.done)
        assertEquals(listOf(RunEvent.BEEP, RunEvent.FINISHED), end.events)
        assertEquals(3, end.state.log.size)
    }

    @Test
    fun strengthFlow() {
        val w = Workout("t", listOf(full.exercises[1], full.exercises[2]))
        val r = runner(w)
        var s = r.start(w, 90, 60, now = 0)
        assertEquals(StepKind.SET, s.step!!.kind)
        assertNull(s.endsAt)

        s = r.primary(s, 1000).state // set 1 done
        assertEquals(StepKind.REST, s.step!!.kind)
        assertEquals(91_000L, s.endsAt)
        assertEquals(1, s.log.size)
        assertEquals("15", s.log[0].values["Reps"])

        // nothing happens before time is up
        assertEquals(s, r.tick(s, 90_000).state)
        val up = r.tick(s, 91_000)
        assertEquals(listOf(RunEvent.BEEP), up.events)
        s = up.state
        assertEquals(StepKind.SET, s.step!!.kind)
        assertEquals(2, s.step!!.set)
        assertNull(s.endsAt) // waits for the person, no overtime

        s = r.primary(s, 100_000).state // set 2
        s = r.skip(s, 101_000).state // skip rest
        s = r.primary(s, 102_000).state // set 3 -> transition
        assertEquals(StepKind.TRANSITION, s.step!!.kind)
        assertEquals(1, s.step!!.exerciseIndex)
        assertEquals(192_000L, s.endsAt)

        // pause / resume / +30s
        s = r.togglePause(s, 102_000 + 10_000)
        assertEquals(80_000L, s.remainingMs(500_000))
        s = r.addTime(s, 30)
        s = r.togglePause(s, 200_000)
        assertEquals(310_000L, s.endsAt)
    }
}
