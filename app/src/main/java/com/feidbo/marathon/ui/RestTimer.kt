package com.feidbo.marathon.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feidbo.marathon.core.fmt
import com.feidbo.marathon.ui.theme.Gold
import kotlinx.coroutines.delay

/**
 * Rest countdown between sets. Times use [SystemClock.elapsedRealtime] so the countdown stays
 * correct if the app is briefly in the background, and survive rotation.
 */
@Stable
class RestTimerState(durationSec: Int, endAt: Long = 0L) {
    var durationSec by mutableIntStateOf(durationSec)
    var endAt by mutableLongStateOf(endAt)      // 0 = not resting
        private set
    var remainingMs by mutableLongStateOf(0L)
        internal set
    val resting: Boolean get() = endAt > 0L

    fun start() {
        endAt = SystemClock.elapsedRealtime() + durationSec * 1000L
        remainingMs = durationSec * 1000L
    }
    fun add(seconds: Int) {
        if (resting) endAt = (endAt + seconds * 1000L).coerceAtLeast(SystemClock.elapsedRealtime() + 1000)
    }
    fun stop() {
        endAt = 0L
    }

    companion object {
        val Saver = listSaver<RestTimerState, Long>(
            save = { listOf(it.durationSec.toLong(), it.endAt) },
            restore = { RestTimerState(it[0].toInt(), it[1]) },
        )
    }
}

@Composable
fun rememberRestTimer(defaultSec: Int): RestTimerState =
    rememberSaveable(saver = RestTimerState.Saver) { RestTimerState(defaultSec) }

/** The countdown bar pinned to the bottom of the log screen while resting. */
@Composable
fun RestBar(timer: RestTimerState, nextUp: String?, scienceNote: String?) {
    val context = LocalContext.current
    val view = LocalView.current

    // Keep the screen on while resting so the countdown and alert aren't lost.
    DisposableEffect(timer.resting) {
        view.keepScreenOn = timer.resting
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(timer.endAt) {
        while (timer.resting) {
            timer.remainingMs = (timer.endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            if (timer.remainingMs == 0L) {
                timer.stop()
                alert(context)
                break
            }
            delay(200)
        }
    }

    AnimatedVisibility(timer.resting, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 12.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    val progress = timer.remainingMs / (timer.durationSec * 1000f)
                    Canvas(Modifier.size(72.dp)) {
                        drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(8.dp.toPx()))
                        drawArc(Gold, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                    }
                    val s = ((timer.remainingMs + 999) / 1000).toInt()
                    Text(fmt("%d:%02d", s / 60, s % 60), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Rest", style = MaterialTheme.typography.titleMedium)
                    nextUp?.let { Text("Next: $it", style = MaterialTheme.typography.bodySmall) }
                    scienceNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { timer.add(-15) }) { Text("−15s") }
                        FilledTonalButton(onClick = { timer.add(15) }) { Text("+15s") }
                        TextButton(onClick = { timer.stop() }) { Text("Skip", color = Gold) }
                    }
                }
            }
        }
    }
}

private fun alert(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1))
    } catch (_: Exception) {
    }
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 800)
    } catch (_: Exception) {
    }
}
