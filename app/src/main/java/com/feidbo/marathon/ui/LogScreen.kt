package com.feidbo.marathon.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import com.feidbo.marathon.core.fmt
import com.feidbo.marathon.ui.theme.StatusColors
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

/** Editable state for one set. Only sets ticked as done are saved. */
private class SetEntry(weight: String, reps: String, done: Boolean) {
    var weight by mutableStateOf(weight)
    var reps by mutableStateOf(reps)
    var done by mutableStateOf(done)
}

/** Editable state for one exercise. */
private class ExerciseEntry(val rx: Prescription, sets: List<SetEntry>, rir: Int?, private val legacyFeel: Feel?) {
    val sets = mutableStateListOf<SetEntry>().apply { addAll(sets) }
    var rir by mutableStateOf(rir)
    var skipped by mutableStateOf(false)

    /** Changing one set's weight carries forward to later sets that still had the old weight and aren't done. */
    fun setWeight(i: Int, value: String) {
        val old = sets[i].weight
        sets[i].weight = value
        for (j in i + 1 until sets.size) if (!sets[j].done && sets[j].weight == old) sets[j].weight = value
    }

    fun toLog(): ExerciseLog? {
        if (skipped) return null
        val done = sets.filter { it.done }.mapNotNull { st ->
            st.reps.toIntOrNull()?.takeIf { it > 0 }?.let { SetLog(st.weight.toDoubleOrNull() ?: 0.0, it) }
        }
        return if (done.isEmpty()) null else ExerciseLog(rx.exercise.id, done, if (rir == null) legacyFeel else null, rir)
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

    val session = strengthSession(day.strength)
    val entries = remember {
        session?.items?.map { rx ->
            val prior = existing?.strength?.firstOrNull { it.exerciseId == rx.exercise.id }
            val advice = loadAdviceFor(rx, day, state.logs)
            val start = advice?.weight?.let(::formatMiles) ?: ""
            ExerciseEntry(
                rx,
                sets = List(maxOf(rx.sets, prior?.sets?.size ?: 0)) { i ->
                    val p = prior?.sets?.getOrNull(i)
                    if (p != null) SetEntry(formatMiles(p.weight), p.reps.toString(), done = true)
                    else SetEntry(start, rx.reps.toString(), done = false)
                },
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

    val timer = rememberRestTimer(session?.restSec ?: 90)
    // Circuit order from the plan: one set of each exercise in a block, then the next round.
    val order = remember(entries) {
        listOf(entries.take(3), entries.drop(3)).flatMap { block ->
            val rounds = block.maxOfOrNull { it.rx.sets } ?: 0
            (0 until rounds).flatMap { r -> block.filter { it.sets.size > r }.map { it to r } }
        }
    }
    val nextUp = order.firstOrNull { (e, i) -> !e.skipped && !e.sets[i].done }
        ?.let { (e, i) -> "${e.rx.exercise.name}, set ${i + 1} of ${e.sets.size}" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log: ${workoutTitle(day)}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            RestBar(
                timer, nextUp,
                if ((session?.restSec ?: 0) >= 120) "2+ min rests build more strength on heavy sets (Grgic 2018)" else null,
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
                    Text("🏋️  Strength", style = MaterialTheme.typography.titleLarge)
                    Text(session.effort, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Tap ✓ after each set to start your rest. Rest: ${fmt("%d:%02d", timer.durationSec / 60, timer.durationSec % 60)}",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { timer.durationSec = (timer.durationSec - 15).coerceAtLeast(30) }) { Text("−15s") }
                        TextButton(onClick = { timer.durationSec = (timer.durationSec + 15).coerceAtMost(300) }) { Text("+15s") }
                    }
                    Text(
                        "Order: one set of each exercise in a block, then repeat (${session.items.take(3).joinToString(" → ") { it.exercise.name }}, " +
                            "then ${session.items.drop(3).joinToString(" → ") { it.exercise.name }}). Warm up first with a light set on the leg press and hip thrust.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entries.forEach { e ->
                        ExerciseEditor(state, dayIndex, e) {
                            // Rest after every set, except after the very last one.
                            if (order.any { (x, i) -> !x.skipped && !x.sets[i].done }) timer.start() else timer.stop()
                        }
                    }
                }
            }

            val run = currentRun()
            val lifts = entries.mapNotNull { it.toLog() }
            val setsDone = lifts.sumOf { it.sets.size }
            val canSave = run != null || lifts.isNotEmpty() || (!day.run.isRun && session == null)
            Button(
                onClick = { onSave(run, lifts) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (setsDone > 0) "Save & celebrate 🎉 · $setsDone sets" else "Save & celebrate 🎉", fontSize = 18.sp) }
            if (session != null && setsDone == 0 && entries.any { e -> e.sets.any { it.reps.isNotEmpty() } }) {
                Text("No sets ticked yet — only sets marked ✓ are saved.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (!canSave) {
                Text("Enter a distance or at least one lift to save. Couldn't train? Go back and tap \"I couldn't do it today\".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExerciseEditor(state: UiState, dayIndex: Int, e: ExerciseEntry, onSetDone: () -> Unit) {
    val rx = e.rx
    val day = state.schedule[dayIndex]!!.day
    val last = lastWorkingSet(rx.exercise.id, dayIndex, state.logs)
    val advice = loadAdviceFor(rx, day, state.logs)
    val targetRir = strengthSession(day.strength)?.targetRir ?: 2
    val allDone = e.sets.all { it.done }
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseIllustration(rx.exercise.id, Modifier.size(48.dp), animate = false)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${if (allDone) "✅ " else ""}${rx.exercise.name} · ${rx.sets} × ${rx.reps}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    last?.let { "Last: ${setsText(it.log)}" }
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
            e.sets.forEachIndexed { i, set ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (set.done) StatusColors.done.copy(alpha = 0.10f) else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Set ${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp))
                    NumberField(set.weight, { e.setWeight(i, it) }, if (rx.exercise.inverted) "Assist" else "Weight",
                        Modifier.weight(1.2f), suffix = "lb")
                    Text("×", style = MaterialTheme.typography.titleMedium)
                    NumberField(set.reps, { set.reps = it }, "Reps", Modifier.weight(0.9f), decimal = false)
                    IconButton(onClick = {
                        set.done = !set.done
                        if (set.done) onSetDone()
                    }) {
                        Icon(
                            if (set.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = if (set.done) "Set done" else "Mark set done",
                            tint = if (set.done) StatusColors.done else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
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
