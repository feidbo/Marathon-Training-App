package com.feidbo.marathon.timer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.feidbo.marathon.MainActivity
import com.feidbo.marathon.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A countdown measured on [SystemClock.elapsedRealtime] (unaffected by clock changes; keeps counting
 * while the phone sleeps). Either running (`endAt > 0`) or paused (`pausedRemainingMs >= 0`).
 */
data class Countdown(
    val endAt: Long,
    val durationMs: Long,
    val label: String = "",
    val dayIndex: Int = -1,
    val pausedRemainingMs: Long = -1,
) {
    val running: Boolean get() = endAt > 0
    val paused: Boolean get() = !running && pausedRemainingMs >= 0
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long =
        if (running) (endAt - now).coerceAtLeast(0) else pausedRemainingMs.coerceAtLeast(0)
}

/**
 * The rest timer (between strength sets) and the 3-minute stretch timer. State is saved so it survives
 * rotation and the app being closed; system alarms fire the alerts even when the app isn't open, and a
 * notification shows the live countdown.
 */
object Timers {
    const val STRETCH_TOTAL_MS = 180_000L
    const val STRETCH_SIDE_MS = 90_000L

    private const val PREFS = "timers"
    private const val CH_LIVE = "timer_live"
    private const val CH_ALERT = "timer_alert"
    private const val NOTIF_REST = 41
    private const val NOTIF_STRETCH = 42
    private const val NOTIF_ALERT = 43
    internal const val ACTION_REST_DONE = "com.feidbo.marathon.REST_DONE"
    internal const val ACTION_STRETCH_SWITCH = "com.feidbo.marathon.STRETCH_SWITCH"
    internal const val ACTION_STRETCH_DONE = "com.feidbo.marathon.STRETCH_DONE"

    private val _rest = MutableStateFlow<Countdown?>(null)
    val rest: StateFlow<Countdown?> = _rest.asStateFlow()
    private val _stretch = MutableStateFlow<Countdown?>(null)
    val stretch: StateFlow<Countdown?> = _stretch.asStateFlow()

    /** Set by the activity so alerts don't double up with the on-screen timer. */
    @Volatile var appVisible = false
    private var loaded = false
    /** The background alarm already buzzed for the finished stretch; don't buzz again on screen. */
    private var stretchDoneAlerted = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.prefs()
        _rest.value = p.readCountdown("rest")
        _stretch.value = p.readCountdown("stretch")
        createChannels(context)
    }

    // ---- Rest ---------------------------------------------------------------------------------

    fun startRest(context: Context, durationSec: Int, nextUp: String) {
        val now = SystemClock.elapsedRealtime()
        val c = Countdown(now + durationSec * 1000L, durationSec * 1000L, nextUp)
        _rest.value = c
        context.prefs().writeCountdown("rest", c)
        schedule(context, ACTION_REST_DONE, c.endAt, 10)
        showLive(context, NOTIF_REST, "Rest", if (nextUp.isNotEmpty()) "Next: $nextUp" else "Get ready for your next set", c)
    }

    fun addRest(context: Context, seconds: Int) {
        val c = _rest.value?.takeIf { it.running } ?: return
        val now = SystemClock.elapsedRealtime()
        val updated = c.copy(endAt = (c.endAt + seconds * 1000L).coerceAtLeast(now + 1000), durationMs = (c.durationMs + seconds * 1000L).coerceAtLeast(1000))
        _rest.value = updated
        context.prefs().writeCountdown("rest", updated)
        schedule(context, ACTION_REST_DONE, updated.endAt, 10)
        showLive(context, NOTIF_REST, "Rest", if (c.label.isNotEmpty()) "Next: ${c.label}" else "", updated)
    }

    fun stopRest(context: Context) {
        _rest.value = null
        context.prefs().writeCountdown("rest", null)
        cancel(context, ACTION_REST_DONE, 10)
        NotificationManagerCompat.from(context).cancel(NOTIF_REST)
    }

    // ---- Stretch ------------------------------------------------------------------------------

    fun startStretch(context: Context, dayIndex: Int) {
        val now = SystemClock.elapsedRealtime()
        val prior = _stretch.value?.takeIf { it.paused && it.dayIndex == dayIndex }
        val remaining = prior?.pausedRemainingMs ?: STRETCH_TOTAL_MS
        val c = Countdown(now + remaining, STRETCH_TOTAL_MS, "Quad stretch", dayIndex)
        _stretch.value = c
        context.prefs().writeCountdown("stretch", c)
        scheduleStretch(context, c)
    }

    fun pauseStretch(context: Context) {
        val c = _stretch.value?.takeIf { it.running } ?: return
        val paused = c.copy(endAt = 0, pausedRemainingMs = c.remainingMs())
        _stretch.value = paused
        context.prefs().writeCountdown("stretch", paused)
        cancel(context, ACTION_STRETCH_SWITCH, 21)
        cancel(context, ACTION_STRETCH_DONE, 22)
        NotificationManagerCompat.from(context).cancel(NOTIF_STRETCH)
    }

    fun stopStretch(context: Context) {
        stretchDoneAlerted = false
        _stretch.value = null
        context.prefs().writeCountdown("stretch", null)
        cancel(context, ACTION_STRETCH_SWITCH, 21)
        cancel(context, ACTION_STRETCH_DONE, 22)
        NotificationManagerCompat.from(context).cancel(NOTIF_STRETCH)
    }

    /**
     * If a stretch timer has run out (on screen or in the background), clears it and returns the day it
     * was for, so the app can tick the stretch off. Returns null otherwise.
     */
    fun consumeFinishedStretch(context: Context): Int? {
        val c = _stretch.value ?: return null
        if (!c.running || c.remainingMs() > 0) return null
        stopStretch(context)
        return c.dayIndex.takeIf { it >= 0 }
    }

    private fun scheduleStretch(context: Context, c: Countdown) {
        val remaining = c.remainingMs()
        if (remaining > STRETCH_SIDE_MS) schedule(context, ACTION_STRETCH_SWITCH, c.endAt - STRETCH_SIDE_MS, 21)
        schedule(context, ACTION_STRETCH_DONE, c.endAt, 22)
        showLive(context, NOTIF_STRETCH, "Quad stretch", "90 s per side — switch at the halfway buzz", c)
    }

    // ---- Alarms & notifications ---------------------------------------------------------------

    private fun pending(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, Intent(context, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun schedule(context: Context, action: String, atElapsed: Long, code: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, action, code)
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        try {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, pi)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, pi)
        }
    }

    private fun cancel(context: Context, action: String, code: Int) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, action, code))
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** An ongoing notification whose clock counts down by itself, so it stays right without the app. */
    private fun showLive(context: Context, id: Int, title: String, text: String, c: Countdown) {
        val wallEnd = System.currentTimeMillis() + c.remainingMs()
        val n = NotificationCompat.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(wallEnd)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(context))
            .setTimeoutAfter(c.remainingMs() + 1000)
            .build()
        notify(context, id, n)
    }

    /**
     * Ends the rest if it's due and alerts once. Called by the alarm and by the on-screen countdown,
     * whichever gets there first (both run on the main thread, so only one alerts).
     */
    fun finishRestIfDue(context: Context) {
        val c = _rest.value ?: return
        if (!c.running || c.remainingMs() > 1500) return
        _rest.value = null
        context.prefs().writeCountdown("rest", null)
        cancel(context, ACTION_REST_DONE, 10)
        NotificationManagerCompat.from(context).cancel(NOTIF_REST)
        alert(context, "Rest's over — go! 💪", if (c.label.isNotEmpty()) "Next: ${c.label}" else "Time for your next set", long = true)
    }

    /** The on-screen stretch countdown just hit zero: alert, clear it, and return the day to tick off. */
    fun finishStretchNow(context: Context): Int? {
        val c = _stretch.value ?: return null
        if (!c.running || c.remainingMs() > 1500) return null
        val alreadyAlerted = stretchDoneAlerted
        stopStretch(context)
        if (!alreadyAlerted) alert(context, "Stretch done ✅", "Nice — XP collected", long = true)
        return c.dayIndex.takeIf { it >= 0 }
    }

    internal fun onAlarm(context: Context, action: String) {
        init(context)
        when (action) {
            ACTION_REST_DONE -> finishRestIfDue(context)
            ACTION_STRETCH_SWITCH -> if (_stretch.value?.running == true) {
                alert(context, "Switch sides 🔄", "90 seconds on the other leg", long = false)
            }
            ACTION_STRETCH_DONE -> {
                val c = _stretch.value ?: return
                if (!c.running) return
                // Leave the finished countdown in place; the app ticks the stretch off when it next opens.
                NotificationManagerCompat.from(context).cancel(NOTIF_STRETCH)
                stretchDoneAlerted = true
                alert(context, "Stretch done ✅", "Nice — open the app to collect your XP", long = true)
            }
        }
    }

    private fun alert(context: Context, title: String, text: String, long: Boolean) {
        vibrate(context, long)
        if (appVisible) {
            beep()
            return // the screen already shows it
        }
        val n = NotificationCompat.Builder(context, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .setTimeoutAfter(5 * 60_000L)
            .build()
        notify(context, NOTIF_ALERT, n)
    }

    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Notification permission denied: the vibration still fires.
        }
    }

    private fun vibrate(context: Context, long: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            val pattern = if (long) longArrayOf(0, 400, 150, 400, 150, 700) else longArrayOf(0, 250, 120, 250)
            val effect = VibrationEffect.createWaveform(pattern, -1)
            // Alarm usage so the buzz isn't dropped while the app is in the background.
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        } catch (_: Exception) {
        }
    }

    private fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 800)
        } catch (_: Exception) {
        }
    }

    private fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Running timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the rest and stretch countdowns"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Timer alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When rest is over or it's time to switch sides"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 150, 400, 150, 700)
            },
        )
    }

    // ---- Persistence --------------------------------------------------------------------------

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun android.content.SharedPreferences.readCountdown(key: String): Countdown? {
        if (!contains("${key}_dur")) return null
        val c = Countdown(
            endAt = getLong("${key}_end", 0),
            durationMs = getLong("${key}_dur", 0),
            label = getString("${key}_label", "").orEmpty(),
            dayIndex = getInt("${key}_day", -1),
            pausedRemainingMs = getLong("${key}_paused", -1),
        )
        // After a reboot elapsed time restarts from zero, which would make an old end time look far away.
        val now = SystemClock.elapsedRealtime()
        if (c.running && c.endAt - now > c.durationMs + 1000) return null
        return c.takeIf { it.running || it.paused }
    }

    private fun android.content.SharedPreferences.writeCountdown(key: String, c: Countdown?) {
        val e = edit()
        if (c == null) {
            listOf("_end", "_dur", "_label", "_day", "_paused").forEach { e.remove(key + it) }
        } else {
            e.putLong("${key}_end", c.endAt).putLong("${key}_dur", c.durationMs).putString("${key}_label", c.label)
                .putInt("${key}_day", c.dayIndex).putLong("${key}_paused", c.pausedRemainingMs)
        }
        e.apply()
    }
}

/** Receives the timer alarms; works whether or not the app is open. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timers.onAlarm(context, intent.action ?: return)
    }
}
