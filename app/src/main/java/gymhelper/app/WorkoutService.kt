package gymhelper.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import gymhelper.core.Durations
import gymhelper.core.StepKind
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the workout clock running with the screen off: ticks the runner, fires beeps and keeps an
 * ongoing notification with the current machine, set and a live countdown.
 */
class WorkoutService : LifecycleService() {

    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastKey: Any? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Workout", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Current machine, set and timer"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Must call startForeground promptly on every start.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        when (intent?.action) {
            ACTION_PRIMARY -> AppState.primary()
            ACTION_SKIP -> AppState.skip()
            ACTION_MUTE -> AppState.toggleMute()
            ACTION_STOP -> AppState.stop()
        }
        if (loop == null) startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gymhelper:workout").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
        loop = lifecycleScope.launch {
            while (isActive) {
                AppState.tick()
                val run = AppState.run.value
                if (run == null || run.done) {
                    stopSelfCleanly()
                    break
                }
                val secs = run.remainingMs(AppState.now()) / 1000
                val key = listOf(run.index, secs, run.paused, AppState.settings.value.muted, AppState.plan.value.hashCode())
                if (key != lastKey) {
                    val first = lastKey == null
                    lastKey = key
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
                    if (first || secs % 10 == 0L) checkPromoted()
                }
                delay(250)
            }
        }
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        loop?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val run = AppState.run.value
        val plan = AppState.plan.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (run == null || run.done) {
            return b.setContentTitle("Gym Helper").setContentText("Workout finished").build()
        }
        val t = describe(run, plan)
        val step = run.step!!
        val counting = step.kind != StepKind.SET && (run.counting || run.paused)
        val time = if (counting || step.kind != StepKind.SET) Durations.format(((run.remainingMs(AppState.now()) + 999) / 1000).toInt()) else null
        val stage = when (step.kind) {
            StepKind.SET -> t.headline                       // "Set 2 of 3"
            StepKind.REST -> "Rest"
            StepKind.TRANSITION -> "Next machine"
            StepKind.TIMED -> t.headline                     // "Warmup", "Main walk", ...
        } + if (run.paused) " (paused)" else ""
        // Big line: the countdown and stage. Small line: the machine, once.
        b.setContentTitle(if (time != null) "$time · $stage" else stage)
        b.setContentText(
            when (step.kind) {
                StepKind.REST -> "${t.exercise?.name ?: ""} · next: set ${step.set + 1} of ${step.totalSets}"
                else -> t.exercise?.name ?: run.workoutName
            },
        )
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16+ Live Update: shows a chip next to the clock in the status bar.
            b.extras.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            b.setShortCriticalText(time ?: "Set ${step.set}/${step.totalSets}")
        }
        b.addAction(Notification.Action.Builder(null, t.action, service(ACTION_PRIMARY, 1)).build())
        b.addAction(Notification.Action.Builder(null, if (AppState.settings.value.muted) "Unmute" else "Mute", service(ACTION_MUTE, 2)).build())
        return b.build()
    }

    /** Whether Android shows our notification as a Live Update (status bar chip), for Settings. */
    private fun checkPromoted() {
        if (Build.VERSION.SDK_INT < 36) {
            AppState.liveUpdateStatus.value = "Needs Android 16 or newer"
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        val n = nm.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }?.notification ?: return
        AppState.liveUpdateStatus.value = when {
            !nm.canPostPromotedNotifications() -> "Turned off for this app in system settings"
            (n.flags and Notification.FLAG_PROMOTED_ONGOING) != 0 -> "On (Android accepted the Live Update)"
            !n.hasPromotableCharacteristics() -> "Not eligible (notification format)"
            else -> "Requested, but the system didn't promote it"
        }
    }

    private fun service(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, WorkoutService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL = "workout"
        const val NOTIFICATION_ID = 1
        const val ACTION_PRIMARY = "primary"
        const val ACTION_SKIP = "skip"
        const val ACTION_MUTE = "mute"
        const val ACTION_STOP = "stop"
    }
}
