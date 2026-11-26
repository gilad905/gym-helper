package gymhelper.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
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
                val key = listOf(run.index, run.endsAt, run.pausedRemaining, AppState.settings.value.muted, AppState.plan.value.hashCode())
                if (key != lastKey) {
                    lastKey = key
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
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
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (run == null || run.done) {
            return b.setContentTitle("Gym Helper").setContentText("Workout finished").build()
        }
        val t = describe(run, plan)
        val name = t.exercise?.name ?: run.workoutName
        val title = if (run.step?.kind == gymhelper.core.StepKind.TRANSITION) "Moving to $name" else name
        b.setContentTitle(title)
        val end = run.endsAt
        when {
            end != null -> {
                b.setContentText(t.short)
                b.setWhen(end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            }
            run.paused -> b.setContentText("${t.short} · paused ${gymhelper.core.Durations.format(((run.pausedRemaining ?: 0) / 1000).toInt())}")
            else -> b.setContentText(t.short).setShowWhen(false)
        }
        b.addAction(0, t.action, service(ACTION_PRIMARY, 1))
        b.addAction(0, if (AppState.settings.value.muted) "Unmute" else "Mute", service(ACTION_MUTE, 2))
        return b.build()
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
