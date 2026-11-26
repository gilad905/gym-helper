package gymhelper.core

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryTest {
    @Test
    fun lastNextShowsPreviousSessionValue() {
        val ex = Exercise("Latzug (05) — Lat Pulldown", props = listOf(Prop("Reps", "12"), Prop("Weight", "25 kg")))
        val log = SetLog(ex.historyKey, 0, 1, null, 1000, LastNext.snapshot(ex))
        val h = History().add(Session(1, "Full Body", 0, 2000, listOf(log)))

        val updated = ex.copy(props = listOf(Prop("Reps", "15"), Prop("Weight", "25 kg")))
        val last = h.lastValues(updated.historyKey)!!
        assertEquals("12 → 15", LastNext.display(last["Reps"], updated.prop("Reps")!!))
        assertEquals("25 kg", LastNext.display(last["Weight"], updated.prop("Weight")!!))
        assertEquals("15", LastNext.display(null, "15"))
    }

    @Test
    fun picksMostRecentSession() {
        val ex = Exercise("Row")
        fun s(id: Long, at: Long, reps: String) =
            Session(id, "W", at, at + 1, listOf(SetLog(ex.historyKey, 0, 1, null, at, mapOf("Reps" to reps))))
        val h = History(listOf(s(2, 200, "14"), s(1, 100, "12")))
        assertEquals("14", h.lastValues(ex.historyKey)!!["Reps"])
        assertEquals(listOf(100L to "12", 200L to "14"), h.progress(ex.historyKey, "Reps"))
    }

    @Test
    fun writesHistoryMarkdown() {
        val log = SetLog("row", 0, 1, null, 0, mapOf(LastNext.NAME_KEY to "Seated Row", "Reps" to "12", "Weight" to "15 kg"))
        val md = HistoryMarkdown.write(History(listOf(Session(1, "Full Body", 0, 3_600_000, listOf(log)))), ZoneOffset.UTC)
        assertTrue("## Thu 1970-01-01 00:00 · Full Body (60 min)" in md, md)
        assertTrue("- Set 1: Reps 12, Weight 15 kg" in md, md)
    }
}
