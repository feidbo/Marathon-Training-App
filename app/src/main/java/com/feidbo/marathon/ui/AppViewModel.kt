package com.feidbo.marathon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.feidbo.marathon.core.Badge
import com.feidbo.marathon.core.Cheers
import com.feidbo.marathon.core.DayLog
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.ExerciseLog
import com.feidbo.marathon.core.Game
import com.feidbo.marathon.core.LevelInfo
import com.feidbo.marathon.core.MissReason
import com.feidbo.marathon.core.Paces
import com.feidbo.marathon.core.Plan
import com.feidbo.marathon.core.Progress
import com.feidbo.marathon.core.RunKind
import com.feidbo.marathon.core.RunLog
import com.feidbo.marathon.core.Schedule
import com.feidbo.marathon.core.ScheduledDay
import com.feidbo.marathon.core.Settings
import com.feidbo.marathon.core.Tip
import com.feidbo.marathon.core.completionStatus
import com.feidbo.marathon.core.computeProgress
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.missAdvice
import com.feidbo.marathon.core.runFeedback
import com.feidbo.marathon.core.shortDate
import com.feidbo.marathon.data.AppData
import com.feidbo.marathon.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class UiState(
    val data: AppData,
    val today: LocalDate,
    val schedule: Schedule,
    val progress: Progress,
) {
    val logs: Map<Int, DayLog> get() = data.logs
    val settings: Settings get() = data.settings
}

/** What the reward screen shows after logging. */
data class Celebration(
    val kind: Kind,
    val title: String,
    val xp: Int,
    val levelUp: LevelInfo?,
    val newBadges: List<Badge>,
    val tips: List<Tip> = emptyList(),
    val advice: List<String> = emptyList(),
    val planChanges: List<String> = emptyList(),
) {
    enum class Kind { DONE, PARTIAL, REST, MISSED }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val _state = MutableStateFlow(build(store.load(), LocalDate.now()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()

    private fun build(data: AppData, today: LocalDate): UiState {
        val schedule = Schedule(data.logs, data.settings, today)
        return UiState(data, today, schedule, computeProgress(schedule, data.logs, data.stretchDays, today))
    }

    /** Re-reads the date, e.g. when the app comes back to the foreground on a new day. */
    fun refreshDate() {
        val now = LocalDate.now()
        if (now != _state.value.today) _state.value = build(_state.value.data, now)
    }

    private fun update(change: (AppData) -> AppData): Pair<UiState, UiState> {
        val before = _state.value
        val data = change(before.data)
        store.save(data)
        val after = build(data, before.today)
        _state.value = after
        return before to after
    }

    fun dismissCelebration() {
        _celebration.value = null
    }

    fun saveWorkout(dayIndex: Int, run: RunLog?, strength: List<ExerciseLog>) {
        val sd = _state.value.schedule[dayIndex] ?: return
        val status = completionStatus(sd.day, run, strength)
        val draft = DayLog(dayIndex, status, run, strength)
        val log = draft.copy(xp = Game.xpFor(sd.day, draft))
        val (before, after) = update { d ->
            var settings = d.settings
            // The Mar 7 half sets marathon pace for the rest of the plan.
            val half = run?.raceTimeSec
            if (sd.day.run == RunKind.HALF_RACE && half != null && half in 80 * 60..200 * 60) {
                settings = settings.copy(halfTimeSec = half, marathonPaceSec = Paces.suggestedMpFromHalf(half))
            }
            d.copy(logs = d.logs + (dayIndex to log), settings = settings)
        }
        val tips = run?.let { runFeedback(sd.day, it, after.settings, after.schedule.longestInPrior30(sd.date)) }.orEmpty()
        val gained = after.progress.xp - before.progress.xp
        _celebration.value = Celebration(
            kind = if (status == DayStatus.DONE) Celebration.Kind.DONE else Celebration.Kind.PARTIAL,
            title = if (status == DayStatus.DONE) Cheers.forDone(dayIndex + log.xp) else "Good work getting out there! 👏",
            xp = gained,
            levelUp = levelUp(before, after),
            newBadges = newBadges(before, after),
            tips = tips,
            planChanges = planChanges(before, after),
        )
    }

    fun markMissed(dayIndex: Int, reason: MissReason) {
        val sd = _state.value.schedule[dayIndex] ?: return
        val draft = DayLog(dayIndex, DayStatus.MISSED, missReason = reason)
        val log = draft.copy(xp = Game.xpFor(sd.day, draft))
        val (before, after) = update { it.copy(logs = it.logs + (dayIndex to log)) }
        _celebration.value = Celebration(
            kind = Celebration.Kind.MISSED,
            title = Cheers.forMissed(dayIndex),
            xp = after.progress.xp - before.progress.xp,
            levelUp = null,
            newBadges = newBadges(before, after),
            advice = missAdvice(reason),
            planChanges = planChanges(before, after).ifEmpty {
                listOf("No change to your plan needed — just carry on with the next workout.")
            },
        )
    }

    /** Marks every unlogged training day before today as missed (for catching up after time away). */
    fun markUnloggedMissed(reason: MissReason) {
        val s = _state.value
        val missing = s.schedule.days.filter { it.day.isTrainingDay && it.date < s.today && it.index !in s.logs }
        if (missing.isEmpty()) return
        val (before, after) = update { d ->
            d.copy(logs = d.logs + missing.associate { it.index to DayLog(it.index, DayStatus.MISSED, missReason = reason, xp = 2) })
        }
        _celebration.value = Celebration(
            kind = Celebration.Kind.MISSED,
            title = "Welcome back! 👋",
            xp = after.progress.xp - before.progress.xp,
            levelUp = null,
            newBadges = emptyList(),
            advice = missAdvice(reason),
            planChanges = planChanges(before, after).ifEmpty { listOf("No change to your plan needed — pick up with today's workout.") },
        )
    }

    fun clearLog(dayIndex: Int) {
        update { it.copy(logs = it.logs - dayIndex) }
    }

    fun toggleStretch(dayIndex: Int) {
        val sd = _state.value.schedule[dayIndex] ?: return
        val wasDone = dayIndex in _state.value.data.stretchDays
        val (before, after) = update { d ->
            d.copy(stretchDays = if (wasDone) d.stretchDays - dayIndex else d.stretchDays + dayIndex)
        }
        if (!wasDone) {
            val rest = !sd.day.isTrainingDay
            val badges = newBadges(before, after)
            val levelUp = levelUp(before, after)
            // Small wins get a small celebration; rest days and milestones get the full one.
            if (rest || badges.isNotEmpty() || levelUp != null) {
                _celebration.value = Celebration(
                    kind = Celebration.Kind.REST,
                    title = Cheers.forRest(dayIndex),
                    xp = after.progress.xp - before.progress.xp,
                    levelUp = levelUp,
                    newBadges = badges,
                )
            }
        }
    }

    fun updateSettings(settings: Settings) {
        update { it.copy(settings = settings) }
    }

    fun finishOnboarding(preplanLongest: Double) {
        update { it.copy(onboarded = true, settings = it.settings.copy(preplanLongestMiles = preplanLongest)) }
    }

    fun resetAll() {
        update { AppData(onboarded = true, settings = Settings(preplanLongestMiles = it.settings.preplanLongestMiles)) }
    }

    private fun levelUp(before: UiState, after: UiState): LevelInfo? =
        after.progress.level.takeIf { it.level > before.progress.level.level }

    private fun newBadges(before: UiState, after: UiState): List<Badge> =
        (after.progress.badges - before.progress.badges).map { Game.badge(it) }

    /** Plain-language list of upcoming days whose workout changed because of this log. */
    private fun planChanges(before: UiState, after: UiState): List<String> {
        val changes = mutableListOf<String>()
        changes += after.schedule.notices.filter { it !in before.schedule.notices }
        val changedDays = after.schedule.days.filter { sd ->
            sd.date >= after.today && sd.day.run.isRun &&
                before.schedule[sd.index]?.day?.let { it.miles != sd.day.miles || it.text != sd.day.text } == true
        }
        changedDays.take(6).forEach { sd: ScheduledDay ->
            val old = before.schedule[sd.index]!!.day
            changes += "${shortDate(sd.date)}: ${formatMiles(old.miles)} → ${formatMiles(sd.day.miles)} mi"
        }
        if (changedDays.size > 6) changes += "…and ${changedDays.size - 6} more days. See Coming up."
        return changes
    }

    companion object {
        val planRange = Plan.start..Plan.raceDay
    }
}
