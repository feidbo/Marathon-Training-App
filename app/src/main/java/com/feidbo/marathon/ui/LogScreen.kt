package com.feidbo.marathon.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feidbo.marathon.core.ExerciseLog
import com.feidbo.marathon.core.Feel
import com.feidbo.marathon.core.Prescription
import com.feidbo.marathon.core.RunKind
import com.feidbo.marathon.core.RunLog
import com.feidbo.marathon.core.SetLog
import com.feidbo.marathon.core.Tip
import com.feidbo.marathon.core.TipLevel
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.lastWorkingSet
import com.feidbo.marathon.core.runFeedback
import com.feidbo.marathon.core.strengthFeedback
import com.feidbo.marathon.core.strengthSession
import com.feidbo.marathon.core.loadAdviceFor
import com.feidbo.marathon.core.workoutTitle

/** Editable state for one exercise. */
private class ExerciseEntry(val rx: Prescription, weight: String, reps: List<String>, rir: Int?, private val legacyFeel: Feel?) {
    var weight by mutableStateOf(weight)
    val reps = mutableStateListOf<String>().apply { addAll(reps) }
    var rir by mutableStateOf(rir)
    var skipped by mutableStateOf(false)

    fun toLog(): ExerciseLog? {
        if (skipped) return null
        val w = weight.toDoubleOrNull() ?: 0.0
        val sets = reps.mapNotNull { r -> r.toIntOrNull()?.takeIf { it > 0 }?.let { SetLog(w, it) } }
        return if (sets.isEmpty()) null else ExerciseLog(rx.exercise.id, sets, if (rir == null) legacyFeel else null, rir)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(state: UiState, dayIndex: Int, onBack: () -> Unit, onSave: (RunLog?, List<ExerciseLog>) -> Unit) {
    val sd = state.schedule[dayIndex] ?: return
    val day = sd.day
    val existing = state.logs[dayIndex]
    val existingRun = existing?.run

    var distance by remember { mutableStateOf(formatMiles(existingRun?.distance ?: day.miles)) }
    var paceMin by remember { mutableStateOf(existingRun?.paceSec?.let { (it / 60).toString() } ?: "") }
    var paceSec by remember { mutableStateOf(existingRun?.paceSec?.let { (it % 60).toString().padStart(2, '0') } ?: "") }
    var segMin by remember { mutableStateOf(existingRun?.segmentPaceSec?.let { (it / 60).toString() } ?: "") }
    var segSec by remember { mutableStateOf(existingRun?.segmentPaceSec?.let { (it % 60).toString().padStart(2, '0') } ?: "") }
    var raceH by remember { mutableStateOf(existingRun?.raceTimeSec?.let { (it / 3600).toString() } ?: "") }
    var raceM by remember { mutableStateOf(existingRun?.raceTimeSec?.let { ((it % 3600) / 60).toString() } ?: "") }
    var raceS by remember { mutableStateOf(existingRun?.raceTimeSec?.let { (it % 60).toString().padStart(2, '0') } ?: "") }
    var effort by remember { mutableStateOf((existingRun?.effort ?: defaultEffort(day.run)).toFloat()) }
    var pain by remember { mutableStateOf(existingRun?.pain ?: false) }
    var didRun by remember { mutableStateOf(existing == null || existingRun != null) }
    var didLift by remember { mutableStateOf(existing == null || existing.strength.isNotEmpty()) }

    val session = strengthSession(day.strength)
    val entries = remember {
        session?.items?.map { rx ->
            val prior = existing?.strength?.firstOrNull { it.exerciseId == rx.exercise.id }
            val advice = loadAdviceFor(rx, day, state.logs)
            ExerciseEntry(
                rx,
                weight = prior?.topWeight?.let(::formatMiles) ?: advice?.weight?.let(::formatMiles) ?: "",
                reps = prior?.sets?.map { it.reps.toString() }?.let { r -> List(rx.sets) { r.getOrElse(it) { "" } } }
                    ?: List(rx.sets) { rx.reps.toString() },
                rir = prior?.rir,
                legacyFeel = prior?.feel,
            )
        }.orEmpty()
    }

    fun currentRun(): RunLog? {
        if (!day.run.isRun || !didRun) return null
        val d = distance.toDoubleOrNull() ?: return null
        return RunLog(
            distance = d,
            paceSec = minSecToSeconds(paceMin, paceSec),
            segmentPaceSec = minSecToSeconds(segMin, segSec),
            effort = effort.toInt(),
            pain = pain,
            raceTimeSec = minSecToSeconds(raceM, raceS, raceH),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log: ${workoutTitle(day)}") },
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
            if (day.run.isRun) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏃  Run", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (day.strength.isPlanned) {
                            Text("Ran it", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp))
                            Switch(checked = didRun, onCheckedChange = { didRun = it })
                        }
                    }
                    Text("Planned: ${formatMiles(day.miles)} mi", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (didRun) {
                        Spacer(Modifier.height(8.dp))
                        NumberField(distance, { distance = it }, "Distance", Modifier.fillMaxWidth(), suffix = "mi")
                        Spacer(Modifier.height(8.dp))
                        if (day.run == RunKind.HALF_RACE || day.run == RunKind.RACE) {
                            MinSecField(raceM, raceS, { raceM = it }, { raceS = it }, "Finish time (h : min : sec)",
                                hours = raceH, onHours = { raceH = it })
                            Spacer(Modifier.height(8.dp))
                        }
                        MinSecField(paceMin, paceSec, { paceMin = it }, { paceSec = it }, "Average pace per mile")
                        if (day.run == RunKind.MARATHON_PACE || day.run == RunKind.TEMPO) {
                            Spacer(Modifier.height(8.dp))
                            MinSecField(segMin, segSec, { segMin = it }, { segSec = it },
                                if (day.run == RunKind.TEMPO) "Pace of the tempo part" else "Pace of the marathon-pace miles")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("How hard did it feel? ${effort.toInt()} / 10 — ${effortWords(effort.toInt())}",
                            style = MaterialTheme.typography.labelLarge)
                        Slider(value = effort, onValueChange = { effort = it }, valueRange = 1f..10f, steps = 8)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Any pain that changed my stride", modifier = Modifier.weight(1f))
                            Switch(checked = pain, onCheckedChange = { pain = it })
                        }
                        currentRun()?.let { run ->
                            Spacer(Modifier.height(8.dp))
                            TipList(runFeedback(day, run, state.settings, state.schedule.longestInPrior30(day.date)))
                        }
                    }
                }
            }

            if (session != null) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏋️  Strength", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (day.run.isRun) {
                            Text("Lifted", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp))
                            Switch(checked = didLift, onCheckedChange = { didLift = it })
                        }
                    }
                    Text(session.effort, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (didLift) {
                        Spacer(Modifier.height(8.dp))
                        RestTimer(session)
                        entries.forEach { e -> ExerciseEditor(state, dayIndex, e) }
                    }
                }
            }

            val run = currentRun()
            val lifts = if (didLift) entries.mapNotNull { it.toLog() } else emptyList()
            val canSave = run != null || lifts.isNotEmpty() || (!day.run.isRun && session == null)
            Button(
                onClick = { onSave(run, lifts) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Save & celebrate 🎉", fontSize = 18.sp) }
            if (!canSave) {
                Text("Enter a distance or at least one lift to save. Couldn't train? Go back and tap \"I couldn't do it today\".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExerciseEditor(state: UiState, dayIndex: Int, e: ExerciseEntry) {
    val rx = e.rx
    val day = state.schedule[dayIndex]!!.day
    val last = lastWorkingSet(rx.exercise.id, dayIndex, state.logs)
    val advice = loadAdviceFor(rx, day, state.logs)
    val targetRir = strengthSession(day.strength)?.targetRir ?: 2
    Column(Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseIllustration(rx.exercise.id, Modifier.size(48.dp), animate = false)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${rx.exercise.name} · ${rx.sets} × ${rx.reps}", style = MaterialTheme.typography.titleMedium)
                Text(
                    last?.let { "Benchmark: ${formatMiles(it.log.topWeight)} lb × ${it.log.sets.joinToString(", ") { s -> s.reps.toString() }}" }
                        ?: "First time — pick a weight that's hard by the last rep",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { e.skipped = !e.skipped }) { Text(if (e.skipped) "Undo skip" else "Skip") }
        }
        if (advice != null && !e.skipped) {
            Text(
                "Suggested ${formatMiles(advice.weight)} lb (${advice.deltaText}). ${advice.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (!e.skipped) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(e.weight, { e.weight = it }, rx.exercise.weightLabel.substringBefore(" ("), Modifier.weight(1.3f), suffix = "lb")
                e.reps.indices.forEach { i ->
                    NumberField(e.reps[i], { e.reps[i] = it }, "Set ${i + 1}", Modifier.weight(1f), decimal = false)
                }
            }
            Text(
                "Reps left in the tank on your last set (target about $targetRir):",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..5).forEach { n ->
                    FilterChip(
                        selected = e.rir == n,
                        onClick = { e.rir = if (e.rir == n) null else n },
                        label = { Text(if (n == 5) "5+" else "$n") },
                    )
                }
            }
            e.toLog()?.let { log ->
                val tips: List<Tip> = strengthFeedback(rx, log, last?.log, day.strength)
                TipList(tips.filter { it.level != TipLevel.GREAT || tips.size == 1 })
            }
        }
    }
}

private fun defaultEffort(kind: RunKind) = when (kind) {
    RunKind.MARATHON_PACE -> 5
    RunKind.TEMPO -> 7
    RunKind.HILLS, RunKind.FARTLEK -> 8
    RunKind.HALF_RACE, RunKind.RACE -> 9
    else -> 4
}

private fun effortWords(e: Int) = when (e) {
    in 1..2 -> "very easy"
    in 3..4 -> "easy, full sentences"
    in 5..6 -> "steady, a few words"
    7 -> "comfortably hard"
    8 -> "hard but controlled"
    9 -> "very hard"
    else -> "all-out"
}
