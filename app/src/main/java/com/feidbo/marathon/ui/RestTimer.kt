package com.feidbo.marathon.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feidbo.marathon.core.StrengthSession
import com.feidbo.marathon.core.fmt
import com.feidbo.marathon.ui.theme.Green
import kotlinx.coroutines.delay

/**
 * Walks through the plan's block structure — one set of each exercise in a block, then rest —
 * with a countdown between rounds. Rest defaults come from the plan (60–90 s in Phase 1,
 * 90 s – 2 min after); each muscle also rests while you do the other two exercises in the round.
 */
@Composable
fun RestTimer(session: StrengthSession, modifier: Modifier = Modifier) {
    val blocks = listOf(session.items.take(3), session.items.drop(3)).filter { it.isNotEmpty() }
    var block by rememberSaveable { mutableIntStateOf(0) }
    var round by rememberSaveable { mutableIntStateOf(1) }
    var restSec by rememberSaveable { mutableIntStateOf(session.restSec) }
    var endAt by rememberSaveable { mutableLongStateOf(0L) }     // elapsedRealtime when rest ends; 0 = not resting
    var remainingMs by rememberSaveable { mutableLongStateOf(0L) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var restOver by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val view = LocalView.current
    val resting = endAt > 0L
    val roundsInBlock = blocks.getOrNull(block)?.maxOf { it.sets } ?: 1

    // Keep the screen on while resting so the countdown and alert aren't lost.
    DisposableEffect(resting) {
        view.keepScreenOn = resting
        onDispose { view.keepScreenOn = false }
    }

    fun nextRound() {
        if (round < roundsInBlock) round++
        else if (block < blocks.lastIndex) { block++; round = 1 }
        else finished = true
    }

    LaunchedEffect(endAt) {
        while (endAt > 0L) {
            remainingMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            if (remainingMs == 0L) {
                endAt = 0L
                restOver = true
                nextRound()
                alert(context)
                break
            }
            delay(200)
        }
    }

    SectionCard(modifier, container = MaterialTheme.colorScheme.primaryContainer) {
        Text("⏱  Workout guide & rest timer", style = MaterialTheme.typography.titleMedium)
        if (finished) {
            Text("All rounds done! 🎉 Enter your weights and reps below.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { finished = false; restOver = false; block = 0; round = 1 }) { Text("Start over") }
            return@SectionCard
        }
        val items = blocks[block]
        val doing = items.filter { it.sets >= round }
        Text(
            "Block ${block + 1} · Round $round of $roundsInBlock",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            doing.joinToString("  →  ") { "${it.exercise.name} ×${it.reps}" },
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
        )
        if (block == 0 && round == 1 && !resting) {
            Text("First: one light warm-up set on the leg press and the hip thrust.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        if (resting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    val progress = remainingMs / (restSec * 1000f)
                    val track = MaterialTheme.colorScheme.surface
                    Canvas(Modifier.size(84.dp)) {
                        drawArc(track, 0f, 360f, false, style = Stroke(9.dp.toPx()))
                        drawArc(Green, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    }
                    val s = ((remainingMs + 999) / 1000).toInt()
                    Text(fmt("%d:%02d", s / 60, s % 60), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rest. Breathe. Sip water.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { endAt = (endAt - 15_000).coerceAtLeast(SystemClock.elapsedRealtime() + 1000) }) { Text("−15 s") }
                        OutlinedButton(onClick = { endAt += 15_000 }) { Text("+15 s") }
                    }
                    TextButton(onClick = { endAt = 0L; restOver = true; nextRound() }) { Text("Skip rest") }
                }
            }
        } else {
            if (restOver) {
                Text("Rest's over — go! 💪", style = MaterialTheme.typography.titleSmall, color = Green)
                Spacer(Modifier.height(4.dp))
            }
            val lastRound = round >= roundsInBlock && block >= blocks.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    restOver = false
                    if (lastRound) finished = true
                    else endAt = SystemClock.elapsedRealtime() + restSec * 1000L
                }, modifier = Modifier.weight(1f)) {
                    Text(if (lastRound) "Last round done" else "Round done · rest ${fmt("%d:%02d", restSec / 60, restSec % 60)}")
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { restSec = (restSec - 15).coerceAtLeast(30) }) { Text("−") }
                TextButton(onClick = { restSec = (restSec + 15).coerceAtMost(300) }) { Text("+") }
            }
        }
        Text(
            "Plan: ${session.rest}. Each muscle also rests while you do the other exercises in the round.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (session.restSec >= 120) {
            Text(
                "Why 2 min: for heavy, low-rep sets, rests of 2 minutes or more give bigger strength gains than short rests " +
                    "(Grgic 2018; Schoenfeld 2016).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 800)
    } catch (_: Exception) {
    }
}
