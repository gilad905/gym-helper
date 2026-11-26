package gymhelper.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Three short beeps on the alarm stream (speaker and headphones together, follows the alarm
 * volume), briefly ducking any music, plus a vibration.
 */
object Beeper {
    private val handler = Handler(Looper.getMainLooper())

    fun beep(context: Context, settings: Settings) {
        handler.post {
            if (!settings.muted) playTones(context)
            if (settings.vibrate) vibrate(context)
        }
    }

    private fun playTones(context: Context) {
        val am = context.getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        am.requestAudioFocus(focus)
        val tone = try {
            ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: RuntimeException) {
            am.abandonAudioFocusRequest(focus)
            return
        }
        for (i in 0 until 3) {
            handler.postDelayed({ tone.startTone(ToneGenerator.TONE_PROP_BEEP, 220) }, i * 400L)
        }
        handler.postDelayed({
            tone.release()
            am.abandonAudioFocusRequest(focus)
        }, 1500)
    }

    private fun vibrate(context: Context) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 220, 180, 220, 180), -1))
    }
}
