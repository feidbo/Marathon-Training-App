package com.feidbo.marathon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feidbo.marathon.core.Exercises
import com.feidbo.marathon.core.MissReason
import com.feidbo.marathon.core.Paces
import com.feidbo.marathon.core.Plan
import com.feidbo.marathon.core.Settings
import com.feidbo.marathon.core.exerciseHistory
import com.feidbo.marathon.core.formatDuration
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.formatPace
import com.feidbo.marathon.core.shortDate
import com.feidbo.marathon.core.strengthSession

@Composable
fun MissedDialog(onDismiss: () -> Unit, onConfirm: (MissReason) -> Unit) {
    var reason by remember { mutableStateOf<MissReason?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("That's OK — what happened?") },
        text = {
            Column {
                Text(
                    "Honest logs help the plan adapt. Missed sessions are never made up or moved onto a rest day.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                MissReason.entries.forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == r, onClick = { reason = r })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { reason?.let(onConfirm) }, enabled = reason != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(state: UiState, exerciseId: String, dayIndex: Int, onBack: () -> Unit) {
    val ex = Exercises.byId(exerciseId) ?: return
    val day = state.schedule[dayIndex]?.day
    val rx = day?.let { strengthSession(it.strength) }?.items?.firstOrNull { it.exercise.id == exerciseId }
    val history = exerciseHistory(exerciseId, state.logs)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ex.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                ExerciseIllustration(ex.id, Modifier.fillMaxWidth().widthIn(max = 360.dp).align(Alignment.CenterHorizontally))
                Text("Machine: ${ex.machine}", style = MaterialTheme.typography.bodyMedium)
                Text("Works: ${ex.muscles}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rx != null) {
                SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Today: ${rx.sets} sets × ${rx.reps} reps", style = MaterialTheme.typography.titleLarge)
                    strengthSession(day!!.strength)?.let {
                        Text(it.effort, style = MaterialTheme.typography.bodyMedium)
                        Text("Rest: ${it.rest}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SectionCard {
                Text("How to do it", style = MaterialTheme.typography.titleMedium)
                ex.steps.forEachIndexed { i, s ->
                    Row(Modifier.padding(top = 6.dp)) {
                        Text("${i + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Text(s, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SectionCard {
                Text("Form cues", style = MaterialTheme.typography.titleMedium)
                ex.cues.forEach { Text("✓ $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                if (ex.inverted) {
                    Text("Remember: on this machine, LESS counterweight = harder. Progress by lowering it.",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (ex.id != "quad_stretch") {
                SectionCard {
                    Text("Your history", style = MaterialTheme.typography.titleMedium)
                    if (history.isEmpty()) Text("Nothing logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val best = if (ex.inverted) history.minOfOrNull { it.log.topWeight } else history.maxOfOrNull { it.log.topWeight }
                    best?.let { Text("Best: ${formatMiles(it)} lb ${if (ex.inverted) "(least assistance)" else ""}", fontWeight = FontWeight.SemiBold) }
                    history.asReversed().take(12).forEach { h ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(shortDate(Plan.days[h.dayIndex].date), modifier = Modifier.width(72.dp))
                            Text("${formatMiles(h.log.topWeight)} lb × ${h.log.sets.joinToString(", ") { it.reps.toString() }}",
                                modifier = Modifier.weight(1f))
                            h.log.feel?.let { Text(it.label, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, onBack: () -> Unit, onSave: (Settings) -> Unit, onReset: () -> Unit) {
    val s = state.settings
    var mpMin by remember { mutableStateOf((s.marathonPaceSec / 60).toString()) }
    var mpSec by remember { mutableStateOf((s.marathonPaceSec % 60).toString().padStart(2, '0')) }
    var hH by remember { mutableStateOf(s.halfTimeSec?.let { (it / 3600).toString() } ?: "") }
    var hM by remember { mutableStateOf(s.halfTimeSec?.let { ((it % 3600) / 60).toString() } ?: "") }
    var hS by remember { mutableStateOf(s.halfTimeSec?.let { (it % 60).toString().padStart(2, '0') } ?: "") }
    var longest by remember { mutableStateOf(formatMiles(s.preplanLongestMiles)) }
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text("Goal marathon pace", style = MaterialTheme.typography.titleMedium)
                Text("10:18 /mi is a 4:30 finish. Your Mar 7 half resets it.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                MinSecField(mpMin, mpSec, { mpMin = it }, { mpSec = it }, "Pace per mile")
                minSecToSeconds(mpMin, mpSec)?.let {
                    Text("Finish time at this pace: ${formatDuration((it * 26.2188).toInt())}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            SectionCard {
                Text("Half-marathon calculator", style = MaterialTheme.typography.titleMedium)
                Text("From the plan: pick a goal between Riegel's prediction and 10 minutes slower.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                MinSecField(hM, hS, { hM = it }, { hS = it }, "Half time (h : min : sec)", hours = hH, onHours = { hH = it })
                minSecToSeconds(hM, hS, hH)?.takeIf { it in 80 * 60..200 * 60 }?.let { half ->
                    val riegel = Paces.riegelMarathonSec(half)
                    Text("Riegel: ${formatDuration(riegel)} (${formatPace(Paces.paceForMarathon(riegel))} /mi)")
                    Text("10 min slower: ${formatDuration(riegel + 600)} (${formatPace(Paces.paceForMarathon(riegel + 600))} /mi)")
                    val mp = Paces.suggestedMpFromHalf(half)
                    OutlinedButton(onClick = {
                        mpMin = (mp / 60).toString(); mpSec = (mp % 60).toString().padStart(2, '0')
                    }) { Text("Use ${formatPace(mp)} /mi (in between)") }
                }
            }
            SectionCard {
                Text("Longest run in the 30 days before Oct 16", style = MaterialTheme.typography.titleMedium)
                Text("Used for the 10% long-run rule at the start of the plan.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(longest, { longest = it }, "Miles", Modifier.fillMaxWidth(), suffix = "mi")
            }
            Button(
                onClick = {
                    onSave(
                        s.copy(
                            marathonPaceSec = minSecToSeconds(mpMin, mpSec)?.takeIf { it in 5 * 60..16 * 60 } ?: s.marathonPaceSec,
                            halfTimeSec = minSecToSeconds(hM, hS, hH)?.takeIf { it in 80 * 60..200 * 60 } ?: s.halfTimeSec,
                            preplanLongestMiles = longest.toDoubleOrNull()?.takeIf { it in 0.5..30.0 } ?: s.preplanLongestMiles,
                        ),
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }

            HorizontalDivider()
            SectionCard {
                Text("About this plan", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Everything runs on your phone — no account, no internet needed. The schedule adapts using the plan's " +
                        "own rules: carry on after short misses, repeat a week after 1–2 weeks off, drop back two weeks after more, " +
                        "lighten a week when you're worn out, and never run more than 10% beyond your longest run of the last 30 days " +
                        "(Frandsen 2025). The half-marathon week and the taper are never restructured.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { confirmReset = true }) {
                Text("Reset all my data", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Erase everything?") },
            text = { Text("All logs, XP and badges will be deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("Erase", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun OnboardingScreen(onDone: (Double) -> Unit) {
    var longest by remember { mutableStateOf("3.5") }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("🦆🏁", fontSize = 56.sp)
        Text("Road to Eugene", style = MaterialTheme.typography.displaySmall)
        Text(
            "191 days. 41-mile peak week. One finish line on April 25, 2027.\n\n" +
                "Each day the app shows you exactly what to do. Log it, earn XP and badges, and the plan adapts " +
                "if life gets in the way. Everything works offline.",
            style = MaterialTheme.typography.bodyLarge,
        )
        SectionCard {
            Text("One question", style = MaterialTheme.typography.titleMedium)
            Text(
                "What was your longest run in the month before Oct 16? The plan never asks for a run more than 10% longer than " +
                    "your longest in the last 30 days.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            NumberField(longest, { longest = it }, "Longest run", Modifier.fillMaxWidth(), suffix = "mi")
        }
        Button(
            onClick = { onDone(longest.toDoubleOrNull()?.coerceIn(0.5, 30.0) ?: 3.5) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Let's go!", fontSize = 18.sp) }
    }
}

@Composable
fun CatchUpDialog(onDismiss: () -> Unit, onConfirm: (MissReason) -> Unit) {
    var reason by remember { mutableStateOf(MissReason.BUSY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome back!") },
        text = {
            Column {
                Text("Mark the unlogged workouts as missed? Your plan will adjust if you were away a week or more.")
                Spacer(Modifier.height(8.dp))
                listOf(MissReason.BUSY, MissReason.SICK_SYSTEMIC, MissReason.PAIN, MissReason.TIRED).forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(reason) }) { Text("Mark as missed") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
