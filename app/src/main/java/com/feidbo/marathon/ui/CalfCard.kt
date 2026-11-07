package com.feidbo.marathon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feidbo.marathon.core.CalfGuide
import com.feidbo.marathon.core.CalfStage
import com.feidbo.marathon.core.CalfStatus
import com.feidbo.marathon.core.CalfTest
import com.feidbo.marathon.core.Game
import com.feidbo.marathon.core.ScheduledDay
import com.feidbo.marathon.core.Side
import com.feidbo.marathon.core.shortDate
import com.feidbo.marathon.ui.theme.StatusColors
import kotlin.math.roundToInt

/** What the calf card can change. The guidance never edits the schedule itself (docs/decisions.md §4). */
class CalfActions(
    val onStatus: (CalfStatus) -> Unit,
    val onTest: (CalfTest) -> Unit,
    val onToggleDay: (Int) -> Unit,
)

/** A slim row to switch the calf guidance on. */
@Composable
fun CalfOffRow(sd: ScheduledDay, actions: CalfActions) {
    var ask by rememberSaveable { mutableStateOf(false) }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Which calf?") },
            text = {
                Text("The app will guide you through four stages. It won't change your schedule: you decide whether to run, " +
                    "shorten, or log a day as missed. It can't diagnose an injury — see a physio if it's getting worse, " +
                    "you can't walk normally, or there's swelling or bruising.")
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { ask = false; actions.onStatus(CalfStatus(true, 1, Side.LEFT, sd.index)) }) { Text("Left") }
                    TextButton(onClick = { ask = false; actions.onStatus(CalfStatus(true, 1, Side.RIGHT, sd.index)) }) { Text("Right") }
                }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancel") } },
        )
    }
    TextButton(onClick = { ask = true }, modifier = Modifier.fillMaxWidth()) {
        Text("🦵  Tight or sore calf? Turn on calf guidance")
    }
}

@Composable
fun CalfCard(
    status: CalfStatus,
    tests: List<CalfTest>,
    sd: ScheduledDay,
    today: Boolean,
    doneToday: Boolean,
    actions: CalfActions,
    onOpenExercise: (String, Int) -> Unit,
) {
    val stage = status.currentStage
    var showTest by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (showTest) CalfTestDialog(status, sd.index, onDismiss = { showTest = false }, onSave = { showTest = false; actions.onTest(it) })
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the calf guidance?") },
            text = {
                Text("Experts' return benchmark: at least ${CalfGuide.TARGET_RAISES} single-leg calf raises on each leg, within " +
                    "${(CalfGuide.MAX_ASYMMETRY * 100).roundToInt()}% of each other. A previous calf strain is the strongest " +
                    "risk factor for another, so keep the calf raises in your routine.")
            },
            confirmButton = { TextButton(onClick = { confirmClear = false; actions.onStatus(CalfStatus()) }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep it on") } },
        )
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🦵 Calf guidance · ${status.side.label.lowercase()} calf", style = MaterialTheme.typography.titleMedium)
                Text("Stage ${stage.number} of 4: ${stage.title}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (today) {
                IconButton(onClick = { actions.onToggleDay(sd.index) }) {
                    Icon(
                        if (doneToday) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (doneToday) "Calf work done" else "Mark calf work done",
                        tint = if (doneToday) StatusColors.done else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { stage.number / 4f },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp).clip(RoundedCornerShape(50)),
            color = StatusColors.done, trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(stage.goal, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        stage.exercises.forEach { ce ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenExercise(ce.exercise.id, sd.index) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExerciseIllustration(ce.exercise.id, Modifier.size(48.dp), animate = false)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ce.exercise.name, style = MaterialTheme.typography.titleSmall)
                    Text(ce.dose, style = MaterialTheme.typography.bodySmall)
                    if (ce.note.isNotEmpty()) {
                        Text(ce.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (stage.number <= 2) {
            Spacer(Modifier.height(4.dp))
            CalfGuide.earlyAdvice.forEach { Bullet(it) }
            Bullet("Your routine's calf raises and the calf stretches are paused for now.")
        }
        Spacer(Modifier.height(6.dp))
        Text("Move on when: ${stage.moveOnWhen}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text("Mild discomfort is OK; sharp or rising pain means stop and stay at this stage. Dose numbers are app defaults, " +
            "not from a study — the stages come from expert reviews.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (sd.day.run.isRun) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Running with a recovering calf", style = MaterialTheme.typography.titleSmall)
            Text("You choose: run it, shorten it, or log it as missed (\"Pain or injury\"). The experts' rules:",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CalfGuide.runningRules.forEach { Bullet(it) }
            if (stage.number < 3) Bullet("Experts suggest running once you can do ${CalfGuide.TARGET_HOPS} single-leg hops on the sore side without symptoms.")
        }

        val last = tests.lastOrNull()
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("Calf test", style = MaterialTheme.typography.titleSmall)
        if (last == null) {
            Text("Single-leg calf raises to fatigue on each leg (and hops once you're in stage 3). Target: " +
                "${CalfGuide.TARGET_RAISES}+ each leg, within ${(CalfGuide.MAX_ASYMMETRY * 100).roundToInt()}%.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            tests.takeLast(3).reversed().forEach { t ->
                val passed = CalfGuide.raisesPassed(t)
                Text("${shortDate(com.feidbo.marathon.core.Plan.days[t.dayIndex].date)}: ${CalfGuide.describe(t)}" +
                    if (passed) " ✅" else "",
                    style = MaterialTheme.typography.bodySmall)
            }
            last.soreReps?.let { sore ->
                Text("Sore side: $sore of ${CalfGuide.TARGET_RAISES} raises", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(
                    progress = { (sore / CalfGuide.TARGET_RAISES.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(50)),
                    color = StatusColors.done, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { showTest = true }) { Text("Log a test") }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stage.number > 1) TextButton(onClick = { actions.onStatus(status.copy(stage = stage.number - 1)) }) { Text("◀ Stage ${stage.number - 1}") }
            Spacer(Modifier.weight(1f))
            if (stage.number < 4) TextButton(onClick = { actions.onStatus(status.copy(stage = stage.number + 1)) }) { Text("Stage ${stage.number + 1} ▶") }
            else TextButton(onClick = { confirmClear = true }) { Text("Calf cleared ✓") }
        }
        if (stage.number < 4) {
            TextButton(onClick = { confirmClear = true }) {
                Text("Turn off calf guidance", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (today && !doneToday) {
            Text("Tick ✓ when you've done today's calf exercises: +${Game.CALF_XP} XP.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodySmall)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CalfTestDialog(status: CalfStatus, dayIndex: Int, onDismiss: () -> Unit, onSave: (CalfTest) -> Unit) {
    var sore by rememberSaveable { mutableStateOf("") }
    var other by rememberSaveable { mutableStateOf("") }
    var hops by rememberSaveable { mutableStateOf("") }
    var hopsOk by rememberSaveable { mutableStateOf(true) }
    val showHops = status.stage >= 3
    val test = CalfTest(
        dayIndex,
        soreReps = sore.toIntOrNull(),
        otherReps = other.toIntOrNull(),
        hops = if (showHops) hops.toIntOrNull() else null,
        hopsSymptomFree = if (showHops && hops.toIntOrNull() != null) hopsOk else null,
    )
    val valid = (test.soreReps != null && test.otherReps != null) || test.hops != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calf test") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Single-leg calf raises, knee straight, at a steady pace, full range, until you can't do another good rep. " +
                    "Stop if it hurts, and only test once the raises themselves are pain-free.",
                    style = MaterialTheme.typography.bodySmall)
                NumberField(sore, { if (it.length <= 3) sore = it }, "${status.side.label} (sore) leg — reps", Modifier.fillMaxWidth(), decimal = false)
                NumberField(other, { if (it.length <= 3) other = it }, "Other leg — reps", Modifier.fillMaxWidth(), decimal = false)
                CalfGuide.asymmetry(test)?.let { a ->
                    Text("Sore side is ${(a * 100).roundToInt()}% weaker (target ≤ ${(CalfGuide.MAX_ASYMMETRY * 100).roundToInt()}%).",
                        style = MaterialTheme.typography.labelMedium)
                }
                if (showHops) {
                    NumberField(hops, { if (it.length <= 3) hops = it }, "Single-leg hops on the sore side", Modifier.fillMaxWidth(), decimal = false)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("No symptoms during or after", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = hopsOk, onCheckedChange = { hopsOk = it })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(test) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The stage's chips, for showing all four at once (used on the amendments page). */
@Composable
fun CalfStagesSummary() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CalfStage.entries.forEach { s ->
            Text("${s.number}. ${s.title} — ${s.goal} Move on when: ${s.moveOnWhen}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
