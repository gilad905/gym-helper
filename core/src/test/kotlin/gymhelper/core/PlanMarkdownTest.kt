package gymhelper.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlanMarkdownTest {
    private val notion = javaClass.getResource("/notion-export.md")!!.readText()

    @Test
    fun parsesNotionExport() {
        val plan = PlanMarkdown.parse(notion)
        assertEquals("Gym", plan.title)
        assertEquals(
            listOf(1 to "Full Body", 2 to "Cardio", 4 to "Full Body", 6 to "Cardio"),
            plan.schedule.map { it.day to it.workout },
        )
        assertEquals(listOf("Full Body", "Cardio"), plan.workouts.map { it.name })

        val full = plan.workout("Full Body")!!
        assertEquals(8, full.exercises.size)
        assertEquals("Crosstrainer", full.exercises[0].name)
        assertEquals(ExerciseType.CARDIO, full.exercises[0].type)
        assertEquals(600, full.exercises[0].durationSec)
        assertEquals("Gym/103864.jpg", full.exercises[0].image)

        val leg = full.exercises[1]
        assertEquals("Beinstreckung (14) — Leg Extension", leg.name)
        assertEquals("14", leg.machine)
        assertEquals(ExerciseType.STRENGTH, leg.type)
        assertEquals(3, leg.sets)
        assertEquals("15", leg.prop("Reps"))
        assertEquals("15 kg", leg.prop("Weight"))
        assertEquals(90, leg.restSec)
        assertEquals("2", leg.prop("Knee"))
        assertEquals("5", leg.prop("Back"))
        assertNotNull(leg.prop("Instructions"))

        val chest = full.exercises[4]
        assertEquals("10 kg - 15", chest.prop("Weight"))
        assertEquals("shoulders down", full.exercises[5].prop("Notes"))

        val bike = full.exercises[7]
        assertEquals(ExerciseType.CARDIO, bike.type)
        assertEquals("70/75", bike.prop("Rpm"))
        assertEquals("3", bike.prop("Resistance"))

        val tread = plan.workout("Cardio")!!.exercises.single()
        assertEquals("Treadmill", tread.name)
        assertEquals(ExerciseType.TREADMILL, tread.type)
        assertEquals(1500, tread.durationSec)
        assertEquals("4", tread.prop("Slope"))
        assertEquals("5", tread.prop("Pace"))
        assertEquals("130", tread.prop("Bpm"))
        assertEquals(180, tread.effectiveWarmup!!.durationSec)
        assertEquals(180, tread.effectiveCooldown!!.durationSec)
    }

    @Test
    fun roundTripsThroughCleanFormat() {
        val plan = PlanMarkdown.parse(notion)
        val md = PlanMarkdown.write(plan)
        assertEquals(plan, PlanMarkdown.parse(md))
        assertEquals(md, PlanMarkdown.write(PlanMarkdown.parse(md)))
    }

    @Test
    fun parsesTreadmillPhases() {
        val md = """
            # Gym
            ## Workout: Cardio
            ### Treadmill
            - Duration: 25 min
            - Slope: 4
            - Warmup: 3 min
                - Slope: 2
                - Pace: 4
            - Cooldown: 2 min
                - Pace: 3.5
            - Bpm: 130
        """.trimIndent()
        val t = PlanMarkdown.parse(md).workouts.single().exercises.single()
        assertEquals(listOf("Duration", "Slope", "Bpm"), t.props.map { it.key })
        assertEquals(listOf(Prop("Slope", "2"), Prop("Pace", "4")), t.warmup!!.props)
        assertEquals(120, t.cooldown!!.durationSec)
        assertEquals(listOf(Prop("Pace", "3.5")), t.cooldown!!.props)
        assertEquals(t, PlanMarkdown.parse(PlanMarkdown.write(PlanMarkdown.parse(md))).workouts.single().exercises.single())
    }

    @Test
    fun durations() {
        assertEquals(90, Durations.parse("1 min 30 sec"))
        assertEquals(600, Durations.parse("10 minutes"))
        assertEquals(90, Durations.parse("1:30"))
        assertEquals(90, Durations.parse("1.5 min"))
        assertEquals(45, Durations.parse("45"))
        assertEquals(30, Durations.parse("30s"))
        assertNull(Durations.parse("Slope 4"))
    }
}
