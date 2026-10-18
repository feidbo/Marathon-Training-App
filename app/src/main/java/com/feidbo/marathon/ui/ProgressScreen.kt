package com.feidbo.marathon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.Exercises
import com.feidbo.marathon.core.Game
import com.feidbo.marathon.core.ScheduledDay
import com.feidbo.marathon.core.exerciseHistory
import com.feidbo.marathon.core.loadAdviceFor
import com.feidbo.marathon.core.strengthSession
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.formatPace
import com.feidbo.marathon.core.shortDate
import com.feidbo.marathon.core.workoutTitle
import com.feidbo.marathon.ui.theme.Gold
import com.feidbo.marathon.ui.theme.StatusColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    state: UiState,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Done", "Coming up", "Badges", "Strength")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your progress") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Summary(state) }
            item {
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
            }
            when (tab) {
                0 -> {
                    val past = state.schedule.days.filter { it.date <= state.today && (it.day.isTrainingDay || it.index in state.logs) }
                        .asReversed()
                    if (past.isEmpty()) item { EmptyNote("Nothing yet — your first workout is waiting on the Today screen.") }
                    items(past, key = { it.index }) { sd -> DayRow(state, sd) { onOpenDay(sd.date) } }
                }
                1 -> {
                    val upcoming = state.schedule.days.filter { it.date > state.today }
                    if (upcoming.isEmpty()) item { EmptyNote("No more workouts. You did it! 🏅") }
                    upcoming.groupBy { it.original.week }.forEach { (week, days) ->
                        item(key = "w$week") {
                            val first = days.first().day
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                                Text("Week $week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(
                                    "${formatMiles(days.sumOf { it.day.miles })} mi · ${first.phase.title}",
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (first.weekNote.isNotEmpty()) {
                                Text(first.weekNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(days.filter { it.day.isTrainingDay }, key = { it.index }) { sd -> DayRow(state, sd) { onOpenDay(sd.date) } }
                    }
                }
                2 -> item { BadgeGrid(state) }
                3 -> item { StrengthSummary(state, onOpenExercise) }
            }
        }
    }
}

@Composable
private fun Summary(state: UiState) {
    val p = state.progress
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⭐", fontSize = 36.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Level ${p.level.level}: ${p.level.name}", style = MaterialTheme.typography.titleMedium)
                Text(p.level.nextXp?.let { "${p.xp} XP · ${it - p.xp} to next level" } ?: "${p.xp} XP · max level!",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { p.level.progress },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
            color = Gold, trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("🏃", formatMiles(Math.round(p.totalMiles * 10) / 10.0), "miles run", Modifier.weight(1f))
        Stat("✅", "${p.workoutsDone}/${p.workoutsPlannedSoFar}", "workouts", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("🔥", "${p.streak}", "streak (best ${p.bestStreak})", Modifier.weight(1f))
        Stat("📏", formatMiles(p.longestRun), "longest run", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    val planMiles = state.schedule.days.sumOf { it.day.miles }
    SectionCard {
        Text("Road to Eugene", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (p.totalMiles / planMiles).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
            color = StatusColors.done, trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text("${formatMiles(Math.round(p.totalMiles).toDouble())} of ${formatMiles(Math.round(planMiles).toDouble())} plan miles · " +
            "${p.badges.size} of ${Game.badges.size} badges", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Text(emoji, fontSize = 22.sp)
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayRow(state: UiState, sd: ScheduledDay, onClick: () -> Unit) {
    val log = state.logs[sd.index]
    val past = sd.date <= state.today
    val (icon, color) = when {
        log?.status == DayStatus.DONE -> "✅" to StatusColors.done
        log?.status == DayStatus.PARTIAL -> "💪" to StatusColors.partial
        log?.status == DayStatus.MISSED -> "💤" to StatusColors.missed
        past && sd.date < state.today -> "⭕" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "📅" to MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${sd.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${shortDate(sd.date)} · ${workoutTitle(sd.day)}",
                style = MaterialTheme.typography.titleSmall,
            )
            val detail = when {
                log?.status == DayStatus.MISSED -> log.missReason?.label ?: "Missed"
                log != null -> buildList {
                    log.run?.let { r -> add("${formatMiles(r.distance)} mi"); r.paceSec?.let { add("${formatPace(it)} /mi") } }
                    val lifts = log.strength.count { e -> e.sets.any { it.reps > 0 } }
                    if (lifts > 0) add("$lifts lifts")
                }.joinToString(" · ")
                past && sd.date < state.today -> "Not logged — tap to log"
                else -> buildList {
                    if (sd.day.run.isRun) add("${formatMiles(sd.day.miles)} mi")
                    if (sd.day.strength.isPlanned) add("strength")
                }.joinToString(" + ")
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sd.isAdjusted && !past) {
                Text("⚙️ Adjusted: plan said ${formatMiles(sd.original.miles)} mi", style = MaterialTheme.typography.labelMedium,
                    color = StatusColors.partial)
            }
        }
        if (log != null && log.xp > 0) Text("+${log.xp}", color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BadgeGrid(state: UiState) {
    val earned = state.progress.badges
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Game.badges.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b ->
                    val has = b.id in earned
                    SectionCard(
                        Modifier.weight(1f),
                        container = if (has) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(if (has) b.emoji else "🔒", fontSize = 34.sp, modifier = Modifier.alpha(if (has) 1f else 0.5f))
                        }
                        Text(b.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                        Text(b.description, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StrengthSummary(state: UiState, onOpenExercise: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Lifts: ${state.progress.strengthSessions} sessions", style = MaterialTheme.typography.titleMedium)
        val nextDay = state.schedule.days.firstOrNull { it.date >= state.today && it.day.strength.isPlanned && it.index !in state.logs }?.day
        Exercises.routine.forEach { ex ->
            val h = exerciseHistory(ex.id, state.logs)
            val last = h.lastOrNull()
            val best = if (ex.inverted) h.minOfOrNull { it.log.topWeight } else h.maxOfOrNull { it.log.topWeight }
            SectionCard(onClick = { onOpenExercise(ex.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExerciseIllustration(ex.id, Modifier.size(52.dp), animate = false)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ex.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (last == null) "Not logged yet"
                            else "Last: ${formatMiles(last.log.topWeight)} lb (${shortDate(state.schedule.days[last.dayIndex].date)})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val nextRx = nextDay?.let { d -> strengthSession(d.strength)?.items?.firstOrNull { it.exercise.id == ex.id } }
                        val advice = if (nextDay != null && nextRx != null) loadAdviceFor(nextRx, nextDay, state.logs) else null
                        advice?.let {
                            Text("Next (${shortDate(nextDay!!.date)}): ${formatMiles(it.weight)} lb · ${it.deltaText}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold)
                        }
                        if (h.size >= 2) {
                            val first = h.first().log.topWeight
                            val change = last!!.log.topWeight - first
                            val better = if (ex.inverted) change < 0 else change > 0
                            if (change != 0.0) Text(
                                "${if (better) "📈" else "📉"} ${if (change > 0) "+" else ""}${formatMiles(change)} lb since you started",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (better) StatusColors.done else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    best?.let { Text("Best\n${formatMiles(it)} lb", textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}
