package com.feidbo.marathon.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feidbo.marathon.core.CalfGuide
import com.feidbo.marathon.core.ExerciseLog
import com.feidbo.marathon.core.Exercises
import com.feidbo.marathon.core.Feel
import com.feidbo.marathon.core.Prescription
import com.feidbo.marathon.core.RunKind
import com.feidbo.marathon.core.RunLog
import com.feidbo.marathon.core.SessionOptions
import com.feidbo.marathon.core.SetLog
import com.feidbo.marathon.core.StrengthKind
import com.feidbo.marathon.core.Tip
import com.feidbo.marathon.core.TipLevel
import com.feidbo.marathon.core.fmt
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.lastWorkingSet
import com.feidbo.marathon.core.loadAdviceFor
import com.feidbo.marathon.core.runFeedback
import com.feidbo.marathon.core.strengthFeedback
import com.feidbo.marathon.core.strengthSession
import com.feidbo.marathon.core.workoutTitle
import com.feidbo.marathon.data.DraftExercise
import com.feidbo.marathon.data.DraftSet
import com.feidbo.marathon.data.StrengthDraft
import com.feidbo.marathon.timer.Timers
import com.feidbo.marathon.ui.theme.StatusColors

/** Editable state for one set. Only sets ticked as done are saved. */
private class SetEntry(weight: String, reps: String, done: Boolean) {
    var weight by mutableStateOf(weight)
    var reps by mutableStateOf(reps)
    var done by mutableStateOf(done)
}

/** Editable state for one exercise. */
private class ExerciseEntry(
    val rx: Prescription, sets: List<SetEntry>, rir: Int?, skipped: Boolean, private val legacyFeel: Feel?,
    bodyweight: Boolean,
) {
    val sets = mutableStateListOf<SetEntry>().apply { addAll(sets) }
    var rir by mutableStateOf(rir)
    var skipped by mutableStateOf(skipped)
    /** Doing the no-equipment version: reps only, no weight. */
    var bodyweight by mutableStateOf(bodyweight)
    val ex get() = rx.exercise
    val weighted: Boolean get() = ex.usesWeight && !bodyweight

    /** Changing one set's weight carries forward to later sets that still had the old weight and aren't done. */
    fun setWeight(i: Int, value: String) {
        val old = sets[i].weight
        sets[i].weight = value
        for (j in i + 1 until sets.size) if (!sets[j].done && sets[j].weight == old) sets[j].weight = value
    }

    fun toLog(): ExerciseLog? {
        if (skipped) return null
        val done = sets.filter { it.done }.mapNotNull { st ->
            st.reps.toIntOrNull()?.takeIf { it > 0 }?.let { SetLog(if (weighted) st.weight.toDoubleOrNull() ?: 0.0 else 0.0, it) }
        }
        return if (done.isEmpty()) null
        else ExerciseLog(
            ex.id, done, if (rir == null) legacyFeel else null, rir,
            perSide = ex.perSideFor(bodyweight),
            bodyweight = bodyweight && ex.usesWeight,
        )
    }

    fun toDraft() = DraftExercise(ex.id, sets.map { DraftSet(it.weight, it.reps, it.done) }, rir, skipped, bodyweight)
}

/** The block an exercise belongs to ("1a" → "1"), for the circuit order. */
private fun blockOf(e: ExerciseEntry) = e.ex.block.takeWhile { it.isDigit() }.ifEmpty { e.ex.block }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    state: UiState,
    dayIndex: Int,
    draft: StrengthDraft?,
    onDraft: (StrengthDraft) -> Unit,
    onBack: () -> Unit,
    onSave: (RunLog?, List<ExerciseLog>) -> Unit,
) {
    val sd = state.schedule[dayIndex] ?: return
    val day = sd.day
    val existing = state.logs[dayIndex]
    val existingRun = existing?.run
    val context = LocalContext.current
    val focus = LocalFocusManager.current

    var distance by rememberSaveable { mutableStateOf(formatMiles(existingRun?.distance ?: day.miles)) }
    var paceMin by rememberSaveable { mutableStateOf(existingRun?.paceSec?.let { (it / 60).toString() } ?: "") }
    var paceSec by rememberSaveable { mutableStateOf(existingRun?.paceSec?.let { (it % 60).toString().padStart(2, '0') } ?: "") }
    var segMin by rememberSaveable { mutableStateOf(existingRun?.segmentPaceSec?.let { (it / 60).toString() } ?: "") }
    var segSec by rememberSaveable { mutableStateOf(existingRun?.segmentPaceSec?.let { (it % 60).toString().padStart(2, '0') } ?: "") }
    var raceH by rememberSaveable { mutableStateOf(existingRun?.raceTimeSec?.let { (it / 3600).toString() } ?: "") }
    var raceM by rememberSaveable { mutableStateOf(existingRun?.raceTimeSec?.let { ((it % 3600) / 60).toString() } ?: "") }
    var raceS by rememberSaveable { mutableStateOf(existingRun?.raceTimeSec?.let { (it % 60).toString().padStart(2, '0') } ?: "") }
    var effort by rememberSaveable { mutableStateOf((existingRun?.effort ?: defaultEffort(day.run)).toFloat()) }
    var pain by rememberSaveable { mutableStateOf(existingRun?.pain ?: false) }
    var avgHr by rememberSaveable { mutableStateOf(existingRun?.avgHr?.toString() ?: "") }
    var didRun by rememberSaveable { mutableStateOf(existing == null || existingRun != null) }

    // The calf guidance can take over the calf raises and hold back the hops (docs/decisions.md §4).
    val calf = state.data.calf
    val options = SessionOptions(
        skipCalfRaises = CalfGuide.skipRoutineCalfRaises(calf),
        plyometrics = CalfGuide.plyometricsAllowed(calf),
    )
    val session = strengthSession(day.strength, options)
    // Exercises already logged for this day that today's routine doesn't list (e.g. machine lifts logged
    // before the dumbbell routine) stay editable, so saving never drops them.
    val extraRx = remember {
        val ids = session?.items?.map { it.exercise.id }.orEmpty().toSet()
        val logged = existing?.strength.orEmpty().map { it.exerciseId } + draft?.exercises.orEmpty().filter { d -> d.sets.any { it.done } }.map { it.id }
        logged.distinct().filter { it !in ids }.mapNotNull { id ->
            val ex = Exercises.byId(id) ?: return@mapNotNull null
            val p = existing?.strength?.firstOrNull { it.exerciseId == id }
            Prescription(ex, p?.sets?.size ?: 1, p?.sets?.firstOrNull()?.reps ?: 10)
        }
    }
    // Start from the saved draft if there is one (it's the newest), else the existing log, else today's suggestions.
    val entries = remember {
        (session?.items.orEmpty() + extraRx).map { rx ->
            val fromDraft = draft?.exercises?.firstOrNull { it.id == rx.exercise.id }
            val prior = existing?.strength?.firstOrNull { it.exerciseId == rx.exercise.id }
            val start = loadAdviceFor(rx, day, state.logs)?.weight?.let(::formatMiles) ?: ""
            when {
                fromDraft != null -> ExerciseEntry(
                    rx,
                    List(maxOf(rx.sets, fromDraft.sets.size)) { i ->
                        fromDraft.sets.getOrNull(i)?.let { SetEntry(it.weight, it.reps, it.done) }
                            ?: SetEntry(fromDraft.sets.lastOrNull()?.weight ?: start, rx.reps.toString(), false)
                    },
                    fromDraft.rir, fromDraft.skipped, prior?.feel, fromDraft.bodyweight,
                )
                else -> ExerciseEntry(
                    rx,
                    List(maxOf(rx.sets, prior?.sets?.size ?: 0)) { i ->
                        val p = prior?.sets?.getOrNull(i)
                        if (p != null) SetEntry(formatMiles(p.weight), p.reps.toString(), done = true)
                        else SetEntry(start, rx.reps.toString(), done = false)
                    },
                    prior?.rir, false, prior?.feel, prior?.bodyweight ?: false,
                )
            }
        }
    }

    // Save what's been entered: on every tick, when leaving, and when the app goes to the background.
    var committed by remember { mutableStateOf(false) }
    fun saveDraft() {
        if (!committed && entries.isNotEmpty()) onDraft(StrengthDraft(dayIndex, entries.map { it.toDraft() }))
    }
    val saveDraftLatest by rememberUpdatedState(::saveDraft)
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) saveDraftLatest() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            saveDraftLatest()
        }
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
            avgHr = avgHr.toIntOrNull()?.takeIf { it in 40..230 },
        )
    }

    var restSec by rememberSaveable { mutableIntStateOf(session?.restSec ?: 90) }
    val rest by Timers.rest.collectAsStateWithLifecycle()
    // Circuit order: one set of each exercise in a block, then the next round.
    val blocks = remember(entries) { entries.groupBy(::blockOf).values.toList() }
    val order = remember(blocks) {
        blocks.flatMap { block ->
            val rounds = block.maxOfOrNull { it.rx.sets } ?: 0
            (0 until rounds).flatMap { r -> block.filter { it.sets.size > r }.map { it to r } }
        }
    }
    fun nextUp(): String? = order.firstOrNull { (e, i) -> !e.skipped && !e.sets[i].done }
        ?.let { (e, i) -> "${e.ex.nameFor(e.bodyweight)}, set ${i + 1} of ${e.sets.size}" }

    // Ask once for notification permission (Android 13+) so rest alerts can show when the app is closed.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun leave() {
        saveDraft()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log: ${workoutTitle(day)}") },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // Keyboard and navigation bar share the bottom inset (the larger of the two), so nothing is padded twice.
        val bottomInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(bottomInsets)
                    .pointerInput(Unit) { detectTapGestures(onTap = { focus.clearFocus() }) }
                    .verticalScroll(rememberScrollState())
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
                            NumberField(distance, { distance = it }, "Distance", Modifier.fillMaxWidth(), suffix = "mi", imeAction = ImeAction.Next)
                            Spacer(Modifier.height(8.dp))
                            if (day.run == RunKind.HALF_RACE || day.run == RunKind.RACE) {
                                MinSecField(raceM, raceS, { raceM = it }, { raceS = it }, "Finish time (h : min : sec)",
                                    hours = raceH, onHours = { raceH = it })
                                Spacer(Modifier.height(8.dp))
                            }
                            MinSecField(paceMin, paceSec, { paceMin = it }, { paceSec = it }, "Average pace per mile")
                            Spacer(Modifier.height(8.dp))
                            NumberField(avgHr, { if (it.length <= 3) avgHr = it }, "Average heart rate (from your watch)",
                                Modifier.fillMaxWidth(), decimal = false, suffix = "bpm")
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
                                "Tap ✓ after each set to start your rest. Rest: ${fmt("%d:%02d", restSec / 60, restSec % 60)}",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { restSec = (restSec - 15).coerceAtLeast(30) }) { Text("−15s") }
                            TextButton(onClick = { restSec = (restSec + 15).coerceAtMost(300) }) { Text("+15s") }
                        }
                        if (session.restSec >= 120) {
                            Text("2+ minute rests build more strength on heavy sets (Grgic 2018).",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "Order: one set of each exercise in a block, then repeat the block. " +
                                blocks.mapIndexed { i, b -> "Block ${i + 1}: " + b.joinToString(" → ") { it.ex.nameFor(it.bodyweight) } }
                                    .joinToString(". ") + ". Warm up first with a light set of split squats and single-leg RDLs.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (options.skipCalfRaises) {
                            Text("Calf raises are left out today — your Calf card on the Today screen has your calf exercises.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (!options.plyometrics && day.strength in setOf(StrengthKind.PHASE2, StrengthKind.PHASE3)) {
                            Text("Pogo hops join once your calf passes its hop test (Calf card).",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        entries.forEach { e ->
                            ExerciseEditor(state, dayIndex, e, onChange = ::saveDraft) { nowDone ->
                                focus.clearFocus() // put the keyboard away so the rest timer is in view
                                saveDraft()
                                if (!nowDone) return@ExerciseEditor
                                val next = nextUp()
                                // Rest after every set, except after the very last one.
                                if (next != null) {
                                    ensureNotificationPermission()
                                    Timers.startRest(context, restSec, next)
                                } else {
                                    Timers.stopRest(context)
                                }
                            }
                        }
                    }
                }

                val run = currentRun()
                val lifts = entries.mapNotNull { it.toLog() }
                val setsDone = lifts.sumOf { it.sets.size }
                val canSave = run != null || lifts.isNotEmpty() || (!day.run.isRun && session == null)
                Button(
                    onClick = {
                        committed = true
                        Timers.stopRest(context)
                        onSave(run, lifts)
                    },
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
                // Room so the floating rest bar never covers the last field.
                if (rest?.running == true) Spacer(Modifier.height(80.dp))
            }
            RestBar(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(bottomInsets))
        }
    }
}

@Composable
private fun ExerciseEditor(
    state: UiState,
    dayIndex: Int,
    e: ExerciseEntry,
    onChange: () -> Unit,
    onSetToggled: (nowDone: Boolean) -> Unit,
) {
    val rx = e.rx
    val ex = rx.exercise
    val day = state.schedule[dayIndex]!!.day
    val last = lastWorkingSet(ex.id, dayIndex, state.logs)
    // Weight suggestions only for dumbbell sets (docs/decisions.md §2).
    val advice = if (e.weighted) loadAdviceFor(rx, day, state.logs) else null
    val targetRir = strengthSession(day.strength)?.targetRir ?: 2
    val allDone = e.sets.all { it.done }
    val perSide = ex.perSideFor(e.bodyweight)
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseIllustration(if (e.bodyweight && ex.id == Exercises.singleLegRdl.id) SLIDING_LEG_CURL else ex.id,
                Modifier.size(48.dp), animate = false)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${if (allDone) "✅ " else ""}${ex.nameFor(e.bodyweight)} · ${rx.sets} × ${rx.repsShown(e.bodyweight)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    last?.let { "Last: ${setsText(it.log)}" }
                        ?: if (e.weighted) "First time — pick a weight that's hard by the last rep" else "Reps only — no weight to log",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { e.skipped = !e.skipped; onChange() }) { Text(if (e.skipped) "Undo skip" else "Skip") }
        }
        if (ex.retired) {
            Text("No longer in your routine. Kept so your history stays complete.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!e.skipped && ex.usesWeight && ex.bodyweightName != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)) {
                FilterChip(selected = !e.bodyweight, onClick = { e.bodyweight = false; onChange() }, label = { Text("Dumbbells") })
                FilterChip(selected = e.bodyweight, onClick = { e.bodyweight = true; onChange() }, label = { Text("Bodyweight") })
            }
            if (e.bodyweight) {
                ex.bodyweightSteps.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (advice != null && !e.skipped) {
            val each = if (ex.weightLabel.startsWith("Each")) " in each hand" else ""
            Text(
                "Suggested ${formatMiles(advice.weight)} lb$each (${advice.deltaText}). ${advice.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (perSide && !e.skipped) {
            Text("One leg at a time: do the reps on each leg — that's one set. Log the reps per leg.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (e.weighted) {
                        NumberField(set.weight, { e.setWeight(i, it) },
                            when {
                                ex.inverted -> "Assist"
                                ex.weightLabel.startsWith("Each") -> "Each DB"
                                else -> "Weight"
                            },
                            Modifier.weight(1.2f), suffix = "lb", imeAction = ImeAction.Next)
                        Text("×", style = MaterialTheme.typography.titleMedium)
                    }
                    NumberField(set.reps, { set.reps = it }, "Reps", Modifier.weight(0.9f), decimal = false)
                    IconButton(onClick = {
                        set.done = !set.done
                        onSetToggled(set.done)
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
                        onClick = { e.rir = if (e.rir == n) null else n; onChange() },
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
