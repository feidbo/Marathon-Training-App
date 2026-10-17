package com.feidbo.marathon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feidbo.marathon.core.Tip
import com.feidbo.marathon.core.TipLevel
import com.feidbo.marathon.core.fmt
import com.feidbo.marathon.ui.theme.Coral
import com.feidbo.marathon.ui.theme.Gold
import com.feidbo.marathon.ui.theme.Green
import com.feidbo.marathon.ui.theme.GreenLight
import com.feidbo.marathon.ui.theme.Sky
import com.feidbo.marathon.ui.theme.StatusColors
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TipRow(tip: Tip) {
    val (icon, color) = when (tip.level) {
        TipLevel.GREAT -> Icons.Filled.CheckCircle to StatusColors.done
        TipLevel.INFO -> Icons.Filled.Info to Sky
        TipLevel.WARN -> Icons.Filled.Warning to StatusColors.warn
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(tip.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun TipList(tips: List<Tip>, modifier: Modifier = Modifier) {
    if (tips.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) { tips.forEach { TipRow(it) } }
}

/** A numeric text field that keeps whatever the user typed and reports it raw. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 7 && v.all { it.isDigit() || (decimal && it == '.') }) onValueChange(v) },
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** Minutes + seconds entry (e.g. pace per mile), since phone number pads have no colon. */
@Composable
fun MinSecField(
    min: String, sec: String,
    onMin: (String) -> Unit, onSec: (String) -> Unit,
    label: String, modifier: Modifier = Modifier,
    hours: String? = null, onHours: ((String) -> Unit)? = null,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hours != null && onHours != null) {
                NumberField(hours, { if (it.length <= 1) onHours(it) }, "h", Modifier.width(64.dp), decimal = false)
                Text(" : ", style = MaterialTheme.typography.titleLarge)
            }
            NumberField(min, { if (it.length <= 3) onMin(it) }, "min", Modifier.width(80.dp), decimal = false)
            Text(" : ", style = MaterialTheme.typography.titleLarge)
            NumberField(sec, { if (it.length <= 2) onSec(it) }, "sec", Modifier.width(80.dp), decimal = false)
        }
    }
}

fun minSecToSeconds(min: String, sec: String, hours: String = ""): Int? {
    val m = min.toIntOrNull()
    val s = sec.toIntOrNull() ?: if (m != null) 0 else null
    val h = hours.toIntOrNull() ?: 0
    if (m == null || s == null || s > 59) return null
    return h * 3600 + m * 60 + s
}

/** A 3-minute quad-stretch timer: 90 s per side. */
@Composable
fun StretchTimer(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    var running by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(180) }
    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }
        if (running && remaining == 0) {
            running = false
            onFinished()
        }
    }
    val side = if (remaining > 90) "Left side" else "Right side"
    val sideRemaining = if (remaining > 90) remaining - 90 else remaining
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            val progress = sideRemaining / 90f
            val track = MaterialTheme.colorScheme.surfaceVariant
            Canvas(Modifier.fillMaxSize()) {
                drawArc(track, 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(8.dp.toPx()))
                drawArc(Green, -90f, 360f * progress, false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Text(fmt("%d:%02d", sideRemaining / 60, sideRemaining % 60), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (remaining == 0) "All done!" else side, style = MaterialTheme.typography.titleMedium)
            Text(
                if (remaining in 89..90 && running) "Switch sides!" else "Kneel, hold your ankle, tuck your pelvis.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.FilledTonalButton(onClick = {
            if (remaining == 0) remaining = 180
            running = !running
        }) { Text(if (running) "Pause" else if (remaining in 1..179) "Resume" else "Start") }
    }
}

/** Falling confetti for celebrations. */
@Composable
fun Confetti(modifier: Modifier = Modifier, pieces: Int = 90) {
    val colors = listOf(Gold, Green, GreenLight, Coral, Sky, Color(0xFFB57BFF))
    val particles = remember {
        List(pieces) {
            floatArrayOf(
                Random.nextFloat(),                 // x
                -Random.nextFloat() * 0.6f,         // start y
                0.6f + Random.nextFloat() * 0.9f,   // speed
                Random.nextFloat() * 360f,          // rotation
                (Random.nextFloat() - 0.5f) * 0.25f, // drift
                Random.nextInt(colors.size).toFloat(),
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2800, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        for (p in particles) {
            val y = (p[1] + t * p[2] * 1.6f) * size.height
            if (y < -20f || y > size.height + 20f) continue
            val x = (p[0] + p[4] * t + kotlin.math.sin((t * 10 + p[3]).toDouble()).toFloat() * 0.02f) * size.width
            rotate(p[3] + t * 720f, Offset(x, y)) {
                drawRect(colors[p[5].toInt()].copy(alpha = (1.2f - t).coerceIn(0f, 1f)), Offset(x - 6f, y - 10f), Size(12f, 20f))
            }
        }
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 12.dp, filled: Boolean = true) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.25f)),
    )
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SpacerH(h: Dp) = Spacer(Modifier.height(h))
