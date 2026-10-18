package com.feidbo.marathon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feidbo.marathon.core.AdjustmentKind
import com.feidbo.marathon.core.DayLog
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.ExerciseLog
import com.feidbo.marathon.core.Exercises
import com.feidbo.marathon.core.Game
import com.feidbo.marathon.core.RunLog
import com.feidbo.marathon.core.SetLog
import com.feidbo.marathon.core.Paces
import com.feidbo.marathon.core.Plan
import com.feidbo.marathon.core.STRENGTH_NOTE
import com.feidbo.marathon.core.RunKind
import com.feidbo.marathon.core.ScheduledDay
import com.feidbo.marathon.core.Tip
import com.feidbo.marathon.core.TipLevel
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.formatPace
import com.feidbo.marathon.core.lastWorkingSet
import com.feidbo.marathon.core.primaryTarget
import com.feidbo.marathon.core.runFeedback
import com.feidbo.marathon.core.strengthSession
import com.feidbo.marathon.core.LoadChange
import com.feidbo.marathon.core.loadAdviceFor
import com.feidbo.marathon.core.workoutTitle
import com.feidbo.marathon.ui.theme.BigNumber
import com.feidbo.marathon.ui.theme.Gold
import com.feidbo.marathon.ui.theme.GoldDeep
import com.feidbo.marathon.ui.theme.Green
import com.feidbo.marathon.ui.theme.GreenDark
import com.feidbo.marathon.ui.theme.StatusColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val longDate = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)

@Composable
fun TodayScreen(
    state: UiState,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onOpenProgress: () -> Unit,
    onLog: (Int) -> Unit,
    onMissed: (Int) -> Unit,
    onOpenExercise: (String, Int) -> Unit,
    onToggleStretch: (Int) -> Unit,
    onUndo: (Int) -> Unit,
    onCatchUp: () -> Unit,
) {
    val sd = state.schedule.on(date)
    val today = state.today
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(state, onOpenProgress)
        DateNav(date, today, onDateChange)

        val unlogged = state.schedule.days.count { it.day.isTrainingDay && it.date < today && it.index !in state.logs }
        if (unlogged >= 3 && date == today) {
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("You have $unlogged unlogged workouts", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Log them so your plan can adapt, or mark them as missed if you took time off.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        state.schedule.days.firstOrNull { it.day.isTrainingDay && it.date < today && it.index !in state.logs }
                            ?.let { onDateChange(it.date) }
                    }) { Text("Review") }
                    TextButton(onClick = onCatchUp) { Text("I took time off") }
                }
            }
        }

        // Only mention a restructure while it's still ahead of you.
        val restructuredSoon = state.schedule.days.any {
            it.date >= today && it.date < today.plusDays(14) &&
                it.adjustments.any { a -> a.kind != AdjustmentKind.LONG_RUN_CAP }
        }
        state.schedule.notices.takeLast(1).forEach { notice ->
            if (date >= today && restructuredSoon) SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Plan adjusted", style = MaterialTheme.typography.titleSmall)
                Text(notice, style = MaterialTheme.typography.bodyMedium)
            }
        }

        when {
            sd == null && date < Plan.start -> SectionCard {
                Text("Your plan starts ${longDate.format(Plan.start)} 🎉", style = MaterialTheme.typography.titleLarge)
            }
            sd == null -> SectionCard {
                Text("🏅 Eugene is done!", style = MaterialTheme.typography.headlineSmall)
                Text("Check Progress for everything you accomplished.")
            }
            else -> DayContent(state, sd, onLog, onMissed, onOpenExercise, onToggleStretch, onUndo)
        }

        WeekStrip(state, date, onDateChange)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(state: UiState, onOpenProgress: () -> Unit) {
    val p = state.progress
    val today = state.today
    val dayNumber = Plan.indexOf(today) + 1
    val toRace = ChronoUnit.DAYS.between(today, Plan.raceDay)
    val xp by animateIntAsState(p.xp, tween(900), label = "xp")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenProgress)
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelRing(p.level.level, p.level.progress)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.level.name, style = MaterialTheme.typography.titleMedium)
            Text(
                p.level.nextXp?.let { "$xp XP · ${it - p.xp} to level ${p.level.level + 1}" } ?: "$xp XP · max level",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(
                    when {
                        dayNumber < 1 -> "Starts in ${1 - dayNumber}d"
                        dayNumber > Plan.days.size -> "Plan complete"
                        else -> "Day $dayNumber/${Plan.days.size}"
                    },
                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Pill(
                    if (toRace > 0) "🦆 ${toRace}d to Eugene" else if (toRace == 0L) "🏁 RACE DAY" else "🏅 Done",
                    MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (p.streak > 0) "🔥" else "🌱", fontSize = 28.sp)
            Text("${p.streak}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DateNav(date: LocalDate, today: LocalDate, onDateChange: (LocalDate) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onDateChange(date.minusDays(1)) }, enabled = date > Plan.start) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    today.plusDays(1) -> "Tomorrow"
                    else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(longDate.format(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onDateChange(date.plusDays(1)) }, enabled = date < Plan.raceDay) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
    }
    if (date != today && today in AppViewModel.planRange) {
        TextButton(onClick = { onDateChange(today) }, modifier = Modifier.fillMaxWidth()) { Text("Back to today") }
    }
}

@Composable
private fun DayContent(
    state: UiState,
    sd: ScheduledDay,
    onLog: (Int) -> Unit,
    onMissed: (Int) -> Unit,
    onOpenExercise: (String, Int) -> Unit,
    onToggleStretch: (Int) -> Unit,
    onUndo: (Int) -> Unit,
) {
    val day = sd.day
    val log = state.logs[day.index]
    val isFuture = day.date > state.today

    if (log != null) LoggedCard(state, sd, onLog, onUndo)

    WorkoutHero(state, sd)

    if (day.isTrainingDay || day.date <= state.today) QuestsCard(state, sd)

    if (log == null && day.isTrainingDay) {
        if (isFuture) {
            Text("Coming up — you can log this on the day.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        } else {
            val preview = Game.xpFor(day, DayLog(day.index, DayStatus.DONE, if (day.run.isRun) RunLog(day.miles) else null,
                if (day.strength.isPlanned) Exercises.routine.map { ExerciseLog(it.id, listOf(SetLog(1.0, 1))) } else emptyList()))
            Button(
                onClick = { onLog(day.index) },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log what I did", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Pill("+$preview XP", Gold, Color.Black)
            }
            OutlinedButton(
                onClick = { onMissed(day.index) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("I couldn't do it today") }
        }
    }

    strengthSession(day.strength)?.let { session ->
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏋️  ${day.strength.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(session.minutes, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(session.effort, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Lift first, then run. Tap an exercise to see how.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            session.items.forEachIndexed { i, rx ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                val last = lastWorkingSet(rx.exercise.id, day.index, state.logs)
                val advice = loadAdviceFor(rx, day, state.logs)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenExercise(rx.exercise.id, day.index) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExerciseIllustration(rx.exercise.id, Modifier.size(56.dp), animate = false)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${rx.exercise.block}. ${rx.exercise.name}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                last == null -> "No benchmark yet — find a weight that's hard by the last rep"
                                else -> "Last: ${setsText(last.log)}"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (advice != null && state.logs[day.index] == null) {
                            val up = advice.change == LoadChange.UP || advice.change == LoadChange.BIG_UP
                            Text(
                                "${if (up) "📈 " else ""}Today: ${formatMiles(advice.weight)} lb (${advice.deltaText})",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (up) StatusColors.done else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Text("${rx.sets} × ${rx.reps}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    StretchCard(state, sd, onToggleStretch, onOpenExercise)
}

/** Today's checklist: each part of the day with its XP, plus the perfect-day bonus. */
@Composable
private fun QuestsCard(state: UiState, sd: ScheduledDay) {
    val day = sd.day
    val log = state.logs[day.index]
    val stretched = day.index in state.data.stretchDays
    data class Quest(val emoji: String, val label: String, val xp: Int, val done: Boolean)
    val quests = buildList {
        if (day.run.isRun) {
            val runXp = Game.xpFor(day, DayLog(day.index, DayStatus.DONE, RunLog(day.miles)))
            add(Quest("🏃", "${workoutTitle(day)} · ${formatMiles(day.miles)} mi", runXp,
                log?.status != DayStatus.MISSED && (log?.run?.distance ?: 0.0) > 0))
        }
        if (day.strength.isPlanned) add(Quest("🏋️", "Strength routine", 25, log?.strength?.any { e -> e.sets.any { it.reps > 0 } } == true))
        add(Quest("🧘", "Quad stretch · 3 min", Game.stretchXp(day), stretched))
        if (day.isTrainingDay) add(Quest("✨", "Perfect day bonus", Game.PERFECT_DAY_XP, log?.status == DayStatus.DONE && stretched))
    }
    val done = quests.count { it.done }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today's quests", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$done / ${quests.size}", style = MaterialTheme.typography.titleMedium,
                color = if (done == quests.size) StatusColors.done else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        val progress by animateFloatAsState(done / quests.size.toFloat(), tween(600), label = "quests")
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = StatusColors.done, trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        quests.forEach { q ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (q.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = null,
                    tint = if (q.done) StatusColors.done else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("${q.emoji}  ${q.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    color = if (q.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text("+${q.xp}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = if (q.done) StatusColors.done else GoldDeep)
            }
        }
    }
}

@Composable
private fun WorkoutHero(state: UiState, sd: ScheduledDay) {
    val day = sd.day
    val mp = state.settings.marathonPaceSec
    val target = primaryTarget(day, mp)
    val rest = !day.run.isRun
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (rest) Brush.linearGradient(listOf(Color(0xFF5B7F95), Color(0xFF3E5B6E)))
                else Brush.linearGradient(listOf(Green, GreenDark)),
            )
            .padding(20.dp),
    ) {
        Text(
            heroEmoji(day.run, day.strength.isPlanned), fontSize = 110.sp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-12).dp).alpha(0.16f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "WEEK ${day.week} · PHASE ${day.phase.number}: ${day.phase.title.uppercase()}",
                    color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                if (day.run.isKey) Pill("KEY", Gold, Color.Black)
            }
            Text(workoutTitle(day), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            if (day.run.isRun) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatMiles(day.miles), style = BigNumber, color = Color.White)
                    Text(" mi", color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 10.dp))
                }
                if (sd.isAdjusted && sd.original.miles != day.miles) {
                    Text("Plan said ${formatMiles(sd.original.miles)} mi", color = Gold, style = MaterialTheme.typography.labelLarge)
                }
            } else if (day.strength.isPlanned) {
                Text("No run today — just the machines.", color = Color.White.copy(alpha = 0.9f))
            } else {
                Text("Recover. Your body adapts on rest days. 😴", color = Color.White.copy(alpha = 0.9f))
            }

            if (target != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStat("Pace", target.rangeText, Modifier.weight(1f))
                    HeroStat("Effort", target.effort, Modifier.weight(1f))
                }
                Text("Feels like: ${target.feelsLike}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                if (day.run == RunKind.MARATHON_PACE || day.run == RunKind.TEMPO || day.run == RunKind.HILLS || day.run == RunKind.FARTLEK) {
                    Text(
                        "Warm-up, cool-down and recoveries easy: ${formatPace(Paces.EASY_FAST)}–${formatPace(Paces.EASY_SLOW)} /mi",
                        color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (day.run == RunKind.EASY_STRIDES) {
                    Text("Strides: 15–20 s quick and relaxed (8–9/10), full recovery between.", color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(day.text.replace(STRENGTH_NOTE, ""), color = Color.White, style = MaterialTheme.typography.bodyLarge)
            if (day.weekNote.isNotEmpty()) {
                Text("📝 ${day.weekNote}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
            sd.adjustments.forEach {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(10.dp),
                ) {
                    Text("⚙️ ${it.message}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StretchCard(state: UiState, sd: ScheduledDay, onToggle: (Int) -> Unit, onOpenExercise: (String, Int) -> Unit) {
    val done = sd.index in state.data.stretchDays
    val canLog = sd.date <= state.today
    var showTimer by remember { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { onOpenExercise("quad_stretch", sd.index) }) {
                ExerciseIllustration("quad_stretch", Modifier.size(56.dp), animate = false)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Kneeling quad stretch", style = MaterialTheme.typography.titleSmall)
                Text("3 min · 90 s per side · after your run or in the evening", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onToggle(sd.index) }, enabled = canLog) {
                Icon(
                    if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (done) "Stretch done" else "Mark stretch done",
                    tint = if (done) StatusColors.done else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        if (canLog && !done) {
            if (showTimer) StretchTimer(onFinished = { onToggle(sd.index) })
            else TextButton(onClick = { showTimer = true }) { Text("⏱  Start 3-minute timer") }
        }
    }
}

@Composable
private fun LoggedCard(state: UiState, sd: ScheduledDay, onEdit: (Int) -> Unit, onUndo: (Int) -> Unit) {
    val log = state.logs[sd.index] ?: return
    val (emoji, label, color) = when (log.status) {
        DayStatus.DONE -> Triple("✅", "Done!", StatusColors.done)
        DayStatus.PARTIAL -> Triple("💪", "Partly done", StatusColors.partial)
        DayStatus.MISSED -> Triple("💚", "Rest taken: ${log.missReason?.label ?: ""}", StatusColors.missed)
    }
    val tips: List<Tip> = log.run?.let {
        runFeedback(sd.day, it, state.settings, state.schedule.longestInPrior30(sd.date))
    }.orEmpty().filter { it.level != TipLevel.GREAT }
    var confirmUndo by remember { mutableStateOf(false) }
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("Undo this log?") },
            text = { Text("This removes what you logged for this day, along with its XP.") },
            confirmButton = { TextButton(onClick = { confirmUndo = false; onUndo(sd.index) }) { Text("Undo") } },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Keep it") } },
        )
    }
    SectionCard(container = color.copy(alpha = 0.12f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                val parts = buildList {
                    log.run?.let { r ->
                        add("${formatMiles(r.distance)} mi")
                        r.paceSec?.let { add("${formatPace(it)} /mi") }
                    }
                    val lifts = log.strength.count { e -> e.sets.any { it.reps > 0 } }
                    if (lifts > 0) add("$lifts lifts")
                }
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            Pill("+${log.xp} XP", Gold, Color.Black)
        }
        if (tips.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TipList(tips)
        }
        Row {
            if (log.status != DayStatus.MISSED) TextButton(onClick = { onEdit(sd.index) }) { Text("Edit") }
            TextButton(onClick = { confirmUndo = true }) { Text("Undo") }
        }
    }
}

@Composable
private fun WeekStrip(state: UiState, date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val week = state.schedule.on(date)?.original?.week ?: return
    val days = state.schedule.days.filter { it.original.week == week }
    SectionCard {
        val planned = days.sumOf { it.day.miles }
        val ran = days.sumOf { state.logs[it.index]?.run?.distance ?: 0.0 }
        Text("This week: ${formatMiles(ran)} of ${formatMiles(planned)} mi", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                val log = state.logs[d.index]
                val color = when {
                    log?.status == DayStatus.DONE -> StatusColors.done
                    log?.status == DayStatus.PARTIAL -> StatusColors.partial
                    log?.status == DayStatus.MISSED -> StatusColors.missed
                    !d.day.isTrainingDay && d.index in state.data.stretchDays -> StatusColors.done.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDateChange(d.date) }
                        .background(if (d.date == date) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(d.date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (d.day.run.isRun) formatMiles(d.day.miles) else if (d.day.strength.isPlanned) "🏋" else "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (log?.status == DayStatus.DONE) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun heroEmoji(run: RunKind, strength: Boolean): String = when (run) {
    RunKind.REST -> "😴"
    RunKind.NONE -> if (strength) "🏋️" else "😴"
    RunKind.HILLS -> "⛰️"
    RunKind.TEMPO, RunKind.FARTLEK -> "⚡"
    RunKind.MARATHON_PACE -> "🎯"
    RunKind.LONG -> "🛣️"
    RunKind.HALF_RACE -> "🥈"
    RunKind.RACE -> "🏅"
    else -> "🏃"
}
