package com.feidbo.marathon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feidbo.marathon.core.fmt
import com.feidbo.marathon.timer.Timers
import com.feidbo.marathon.ui.theme.Gold
import kotlinx.coroutines.delay

/**
 * The rest countdown between sets: one compact row that floats just above the keyboard (or the bottom
 * of the screen). The timer itself lives in [Timers], so it keeps going through rotation and when the
 * app is in the background, and the alarm buzzes even if the app is closed.
 */
@Composable
fun RestBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val rest by Timers.rest.collectAsStateWithLifecycle()
    val c = rest
    val running = c?.running == true
    var remaining by remember { mutableLongStateOf(0L) }

    // Keep the screen on while resting so the countdown stays in view.
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(c) {
        while (c != null && c.running) {
            remaining = c.remainingMs()
            if (remaining == 0L) {
                Timers.finishRestIfDue(context)
                break
            }
            delay(200)
        }
    }

    AnimatedVisibility(running, modifier = modifier, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 10.dp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val duration = (c?.durationMs ?: 1L).coerceAtLeast(1L)
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(52.dp)) {
                        drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(6.dp.toPx()))
                        drawArc(Gold, -90f, 360f * (remaining / duration.toFloat()).coerceIn(0f, 1f), false,
                            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                    }
                    val s = ((remaining + 999) / 1000).toInt()
                    Text(fmt("%d:%02d", s / 60, s % 60), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rest", style = MaterialTheme.typography.titleSmall)
                    if (!c?.label.isNullOrEmpty()) {
                        Text("Next: ${c?.label}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                FilledTonalButton(onClick = { Timers.addRest(context, -15) }, modifier = Modifier.padding(end = 4.dp)) { Text("−15") }
                FilledTonalButton(onClick = { Timers.addRest(context, 15) }) { Text("+15") }
                IconButton(onClick = { Timers.stopRest(context) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Skip rest", tint = Gold)
                }
            }
        }
    }
}
