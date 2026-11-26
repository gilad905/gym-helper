package gymhelper.app

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import gymhelper.core.Exercise
import gymhelper.core.History
import gymhelper.core.LastNext
import gymhelper.core.Plan
import gymhelper.core.RunEvent
import gymhelper.core.RunState
import gymhelper.core.Runner
import gymhelper.core.Session
import gymhelper.core.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Settings(
    val transitionSec: Int = 90,
    val defaultRestSec: Int = 90,
    val muted: Boolean = false,
    val vibrate: Boolean = true,
    val keepScreenOn: Boolean = true,
)

class GymApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
    }
}

/** Process-wide app state: plan, history, settings and the running workout. Saved as JSON files. */
object AppState {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Application
    lateinit var dir: File
        private set

    private val _plan = MutableStateFlow(Plan())
    private val _history = MutableStateFlow(History())
    private val _settings = MutableStateFlow(Settings())
    private val _run = MutableStateFlow<RunState?>(null)
    private val _locked = MutableStateFlow(true)

    val plan: StateFlow<Plan> = _plan
    val history: StateFlow<History> = _history
    val settings: StateFlow<Settings> = _settings
    val run: StateFlow<RunState?> = _run
    /** Plan editing lock. Always starts locked. */
    val locked: StateFlow<Boolean> = _locked

    private val runner = Runner { i -> exerciseOfRun(i)?.let { it.historyKey to LastNext.snapshot(it) } ?: ("?" to emptyMap()) }

    @OptIn(FlowPreview::class)
    fun init(application: Application) {
        app = application
        dir = application.filesDir
        load("plan.json", Plan.serializer())?.let { _plan.value = it }
        load("history.json", History.serializer())?.let { _history.value = it }
        load("settings.json", Settings.serializer())?.let { _settings.value = it }
        load("run.json", RunState.serializer())?.let { _run.value = it }

        _plan.drop(1).debounce(300).onEach { save("plan.json", Plan.serializer(), it) }.launchIn(scope)
        _history.drop(1).onEach { save("history.json", History.serializer(), it) }.launchIn(scope)
        _settings.drop(1).onEach { save("settings.json", Settings.serializer(), it) }.launchIn(scope)
        _run.drop(1).debounce(200).onEach {
            if (it == null) File(dir, "run.json").delete() else save("run.json", RunState.serializer(), it)
        }.launchIn(scope)

        if (_run.value != null) ensureService()
    }

    private fun <T> load(name: String, s: KSerializer<T>): T? = try {
        File(dir, name).takeIf { it.exists() }?.readText()?.let { json.decodeFromString(s, it) }
    } catch (e: Exception) {
        null
    }

    @Synchronized
    private fun <T> save(name: String, s: KSerializer<T>, value: T) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(json.encodeToString(s, value))
        tmp.renameTo(File(dir, name))
    }

    // ---- lock, mute, settings ----

    fun toggleLock() { _locked.value = !_locked.value }
    fun toggleMute() { _settings.value = _settings.value.copy(muted = !_settings.value.muted) }
    fun updateSettings(f: (Settings) -> Settings) { _settings.value = f(_settings.value) }

    // ---- plan editing ----

    fun setPlan(p: Plan) { _plan.value = p }

    fun updatePlan(f: (Plan) -> Plan) { _plan.value = f(_plan.value) }

    fun updateWorkout(workoutIndex: Int, f: (Workout) -> Workout) = updatePlan { p ->
        p.copy(workouts = p.workouts.mapIndexed { i, w -> if (i == workoutIndex) f(w) else w })
    }

    fun updateExercise(workoutIndex: Int, exerciseIndex: Int, f: (Exercise) -> Exercise) =
        updateWorkout(workoutIndex) { w ->
            w.copy(exercises = w.exercises.mapIndexed { i, e -> if (i == exerciseIndex) f(e) else e })
        }

    fun imageFile(path: String?): File? = path?.let { File(dir, it) }?.takeIf { it.exists() }

    // ---- history ----

    fun deleteSession(id: Long) { _history.value = _history.value.remove(id) }

    // ---- workout run ----

    fun runWorkoutIndex(): Int = _run.value?.let { r -> _plan.value.workouts.indexOfFirst { it.name == r.workoutName } } ?: -1

    fun exerciseOfRun(i: Int): Exercise? =
        _run.value?.let { r -> _plan.value.workout(r.workoutName)?.exercises?.getOrNull(i) }

    fun start(workout: Workout) {
        val s = _settings.value
        _run.value = runner.start(workout, s.transitionSec, s.defaultRestSec, now())
        ensureService()
    }

    fun primary() = act { runner.primary(it, now()) }
    fun skip() = act { runner.skip(it, now()) }
    fun back() = mutate { runner.back(it, now()) }
    fun togglePause() = mutate { runner.togglePause(it, now()) }
    fun addTime(sec: Int) = mutate { runner.addTime(it, sec) }

    /** Called by the service clock. */
    fun tick() = act { runner.tick(it, now()) }

    /** End the workout now. Saves what was done so far. */
    fun stop() {
        val r = _run.value ?: return
        if (!r.done) saveSession(r)
        _run.value = null
    }

    /** Close the "workout complete" screen. */
    fun dismissFinished() { if (_run.value?.done == true) _run.value = null }

    private fun mutate(f: (RunState) -> RunState) {
        val r = _run.value ?: return
        _run.value = f(r)
    }

    @Synchronized
    private fun act(f: (RunState) -> gymhelper.core.RunResult) {
        val r = _run.value ?: return
        if (r.done) return
        val res = f(r)
        if (res.state != r) _run.value = res.state
        if (RunEvent.BEEP in res.events) Beeper.beep(app, _settings.value)
        if (RunEvent.FINISHED in res.events) saveSession(res.state)
    }

    private fun saveSession(r: RunState) {
        if (r.log.isEmpty()) return
        _history.value = _history.value.add(Session(r.startedAt, r.workoutName, r.startedAt, now(), r.log))
    }

    fun ensureService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, WorkoutService::class.java))
        } catch (e: Exception) {
            // Not allowed from the background; MainActivity calls this again when it opens.
        }
    }

    fun now() = System.currentTimeMillis()

    fun context(): Context = app
}
