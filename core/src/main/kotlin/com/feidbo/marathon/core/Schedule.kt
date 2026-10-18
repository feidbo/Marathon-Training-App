package com.feidbo.marathon.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.roundToInt

enum class AdjustmentKind { LONG_RUN_CAP, REPEAT_WEEK, DROP_BACK, LIGHTER_WEEK }

data class Adjustment(val kind: AdjustmentKind, val message: String)

/** A plan day after any changes the app made because of what was actually logged. */
data class ScheduledDay(
    val original: PlannedDay,
    val day: PlannedDay,
    val adjustments: List<Adjustment> = emptyList(),
) {
    val index: Int get() = day.index
    val date: LocalDate get() = day.date
    val isAdjusted: Boolean get() = adjustments.isNotEmpty()
}

object Plan {
    val days: List<PlannedDay> get() = planDays
    val start: LocalDate = LocalDate.of(2026, 10, 16)
    val raceDay: LocalDate = LocalDate.of(2027, 4, 25)

    /** Weeks the app never restructures: the half-marathon checkpoint and the taper. */
    val protectedWeeks = setOf(20, 25, 26, 27)
    /** Weeks the plan already makes lighter. */
    val lighterWeeks = setOf(4, 8, 12, 16, 20)

    fun indexOf(date: LocalDate): Int = ChronoUnit.DAYS.between(start, date).toInt()
    fun dayOn(date: LocalDate): PlannedDay? = days.getOrNull(indexOf(date))
    fun weekDays(week: Int): List<PlannedDay> = days.filter { it.week == week }
}

/**
 * Builds the schedule the runner should follow from the written plan plus their logs.
 *
 * The rules come straight from the plan's "Ground rules" and "Adjusting the plan" sections:
 *  - Missed under a week: carry on from today's date; nothing is made up, and nothing moves onto a rest day.
 *  - Missed 1–2 weeks: repeat the last full week completed, then continue.
 *  - Missed more than 2 weeks before March: drop back two weeks.
 *  - Signs of doing too much (or a miss because of fatigue): make the next week lighter.
 *  - Always: no run more than 10% longer than the longest run in the previous 30 days (Frandsen 2025).
 * The half-marathon week and the taper are never restructured; only the long-run rule applies there.
 */
class Schedule(
    private val logs: Map<Int, DayLog>,
    private val settings: Settings,
    private val today: LocalDate,
    plan: List<PlannedDay> = Plan.days,
) {
    private val _notices = mutableListOf<String>()
    /** Plain-language explanations of any restructuring, oldest first. */
    val notices: List<String> get() = _notices
    val days: List<ScheduledDay>

    init {
        val working = plan.map { ScheduledDay(it, it) }.toMutableList()
        applyMissedTime(working, plan)
        applyLighterWeeks(working)
        applyLongRunRule(working)
        days = working
    }

    operator fun get(index: Int): ScheduledDay? = days.getOrNull(index)
    fun on(date: LocalDate): ScheduledDay? = days.getOrNull(Plan.indexOf(date))

    /** Longest run in the 30 days before [date], counting logged runs (or the plan for unlogged past days). */
    fun longestInPrior30(date: LocalDate): Double = longest30(date, days)

    // ---- Missed time ---------------------------------------------------------------------------

    private data class Gap(val first: PlannedDay, val last: PlannedDay) {
        val spanDays: Int get() = ChronoUnit.DAYS.between(first.date, last.date).toInt() + 1
    }

    private fun findGaps(plan: List<PlannedDay>): List<Gap> {
        val gaps = mutableListOf<Gap>()
        var first: PlannedDay? = null
        var last: PlannedDay? = null
        for (d in plan) {
            if (!d.isTrainingDay) continue
            val status = logs[d.index]?.status
            when (status) {
                DayStatus.MISSED -> {
                    if (first == null) first = d
                    last = d
                }
                DayStatus.DONE, DayStatus.PARTIAL -> {
                    if (first != null) gaps += Gap(first, last!!)
                    first = null; last = null
                }
                null -> Unit // unlogged days neither break nor extend a gap
            }
        }
        if (first != null) gaps += Gap(first, last!!)
        return gaps
    }

    private fun weekCompleted(week: Int, plan: List<PlannedDay>): Boolean {
        val training = plan.filter { it.week == week && it.isTrainingDay }
        return training.isNotEmpty() && training.all {
            val s = logs[it.index]?.status
            s == DayStatus.DONE || s == DayStatus.PARTIAL
        }
    }

    private fun applyMissedTime(working: MutableList<ScheduledDay>, plan: List<PlannedDay>) {
        for (gap in findGaps(plan)) {
            if (gap.spanDays < 7) continue
            val resume = gap.last.date.plusDays(1)
            val resumeWeek = Plan.dayOn(resume)?.week ?: continue
            val target = if (resume.dayOfWeek <= DayOfWeek.WEDNESDAY) resumeWeek else resumeWeek + 1
            val gapWeek = gap.first.week
            val beforeMarch = gap.first.date < LocalDate.of(2027, 3, 1)
            val missedText = "You missed ${gap.spanDays} days (${shortDate(gap.first.date)} – ${shortDate(gap.last.date)})."

            if (gap.spanDays > 14 && beforeMarch) {
                val base = maxOf(1, gapWeek - 2)
                val changed = mutableListOf<Int>()
                for ((offset, targetWeek) in listOf(target, target + 1).withIndex()) {
                    if (targetWeek in Plan.protectedWeeks || targetWeek > 24) continue
                    val source = base + offset
                    if (source >= targetWeek) continue
                    replaceWeek(working, plan, targetWeek, source, AdjustmentKind.DROP_BACK,
                        "Dropped back two weeks after missed time: this week follows Week $source.")
                    changed += targetWeek
                }
                _notices += if (changed.isEmpty()) "$missedText Ease back in and let the long-run rule guide your distances."
                else "$missedText Per the plan, you drop back two weeks: Week ${changed.joinToString(" and Week ")} " +
                    "now follow Weeks ${changed.indices.joinToString(" and ") { (base + it).toString() }}. Recheck your goal at the Mar 7 half."
            } else {
                // The half-marathon week is a checkpoint, not a training week to repeat.
                val source = (gapWeek - 1 downTo 1).firstOrNull { it != 20 && weekCompleted(it, plan) }
                    ?: (gapWeek - 1 downTo 1).firstOrNull { it != 20 } ?: 1
                if (target in Plan.protectedWeeks || target > 27 || source >= target) {
                    _notices += "$missedText Carry on with the plan; the long-run rule keeps your distances safe."
                    continue
                }
                replaceWeek(working, plan, target, source, AdjustmentKind.REPEAT_WEEK,
                    "Repeating Week $source, your last full week, after missed time.")
                _notices += "$missedText Per the plan, Week $target repeats Week $source (your last full week), then you continue." +
                    if (gap.spanDays > 14) " With this much time off in the last block, recheck your goal pace." else ""
            }
        }
    }

    private fun replaceWeek(
        working: MutableList<ScheduledDay>, plan: List<PlannedDay>,
        targetWeek: Int, sourceWeek: Int, kind: AdjustmentKind, message: String,
    ) {
        val source = plan.filter { it.week == sourceWeek }.associateBy { it.date.dayOfWeek }
        for (i in working.indices) {
            val sd = working[i]
            if (sd.original.week != targetWeek) continue
            val src = source[sd.date.dayOfWeek] ?: continue
            // Running comes from the source week; strength stays as planned for this date.
            val strengthNote = STRENGTH_NOTE.find(sd.original.text)?.value.orEmpty()
            val text = src.text.replace(STRENGTH_NOTE, "").let { if (src.run.isRun) it + strengthNote else sd.original.text }
            val newDay = sd.day.copy(text = text, miles = src.miles, run = src.run, mpMiles = src.mpMiles)
            if (newDay.miles == sd.day.miles && newDay.run == sd.day.run && newDay.text == sd.day.text) continue
            working[i] = sd.copy(day = newDay, adjustments = sd.adjustments + Adjustment(kind, message))
        }
    }

    // ---- Fatigue -------------------------------------------------------------------------------

    private fun applyLighterWeeks(working: MutableList<ScheduledDay>) {
        val flagged = mutableSetOf<Int>()
        val byWeek = working.groupBy { it.original.week }
        for ((week, wDays) in byWeek) {
            val tiredMiss = wDays.any { logs[it.index]?.missReason == MissReason.TIRED }
            val hardEasyRuns = wDays.count {
                val log = logs[it.index]
                it.day.run.isEasyType && (log?.run?.effort ?: 0) >= 7
            }
            if (tiredMiss || hardEasyRuns >= 3) flagged += week + 1
        }
        for (week in flagged.sorted()) {
            if (week in Plan.lighterWeeks || week in Plan.protectedWeeks || week > 24) continue
            if (working.any { it.original.week == week && it.adjustments.any { a -> a.kind != AdjustmentKind.LONG_RUN_CAP } }) continue
            _notices += "Signs of doing too much last week, so Week $week is about 15% lighter. Take an extra rest day if you need it."
            for (i in working.indices) {
                val sd = working[i]
                if (sd.original.week != week || !sd.day.run.isRun) continue
                val miles = quarter(sd.day.miles * 0.85).coerceAtLeast(1.0)
                working[i] = sd.copy(
                    day = sd.day.copy(miles = miles, text = rewriteMiles(sd.day.text, miles)),
                    adjustments = sd.adjustments + Adjustment(
                        AdjustmentKind.LIGHTER_WEEK,
                        "Lighter week: ${formatMiles(sd.day.miles)} → ${formatMiles(miles)} mi. " +
                            if (sd.day.run.isKey) "Keep the hard part; trim the warm-up and cool-down." else "Keep it easy.",
                    ),
                )
            }
        }
    }

    // ---- Long-run rule -------------------------------------------------------------------------

    private fun applyLongRunRule(working: MutableList<ScheduledDay>) {
        for (i in working.indices) {
            val sd = working[i]
            val d = sd.day
            if (!d.run.isRun || d.run == RunKind.RACE || d.run == RunKind.HALF_RACE) continue
            val log = logs[d.index]
            if (log != null || d.date < today) {
                // Logged or past: keep whatever distance the schedule asked for at the time.
                val planned = log?.plannedMiles
                if (planned != null && planned < d.miles - 0.01) {
                    working[i] = sd.copy(
                        day = d.copy(miles = planned, text = rewriteMiles(d.text, planned)),
                        adjustments = sd.adjustments + Adjustment(AdjustmentKind.LONG_RUN_CAP,
                            "Shortened from ${formatMiles(d.miles)} to ${formatMiles(planned)} mi by the long-run rule."),
                    )
                }
                continue
            }
            val longest = longest30(d.date, working)
            val cap = longest * 1.10
            if (d.miles <= cap + 0.01) continue
            val miles = floor(cap * 4) / 4.0
            if (miles < 1.0 || miles >= d.miles) continue
            working[i] = sd.copy(
                day = d.copy(miles = miles, text = rewriteMiles(d.text, miles)),
                adjustments = sd.adjustments + Adjustment(
                    AdjustmentKind.LONG_RUN_CAP,
                    "Shortened from ${formatMiles(d.miles)} to ${formatMiles(miles)} mi. Your longest run in the last 30 days " +
                        "was ${formatMiles(longest)} mi, and no run should be more than 10% longer than that " +
                        "(runs 10–30% longer had a 64% higher injury rate — Frandsen 2025)." +
                        if (d.run.isKey) " Keep the hard part; trim the easy miles." else "",
                ),
            )
        }
    }

    private fun longest30(date: LocalDate, working: List<ScheduledDay>): Double {
        // Days are contiguous, so the 30-day window is a slice of indices.
        val idx = Plan.indexOf(date)
        val from = idx - 30
        var longest = if (from < 0) settings.preplanLongestMiles else 0.0
        for (i in maxOf(0, from) until minOf(idx, working.size)) longest = maxOf(longest, milesRun(working[i]))
        return longest
    }

    /** Actual miles for a logged day; plan miles for days not yet logged (assumed done as planned). */
    private fun milesRun(sd: ScheduledDay): Double {
        val log = logs[sd.index] ?: return if (sd.day.run.isRun) sd.day.miles else 0.0
        if (log.status == DayStatus.MISSED) return 0.0
        return log.run?.distance ?: if (log.status == DayStatus.DONE && sd.day.run.isRun) sd.day.miles else 0.0
    }
}

/** The " + Strength (15–20 min)" part of a plan day's text. */
val STRENGTH_NOTE = Regex("""\s*\+\s*Strength(,\s*light)?\s*\([^)]*\)(,\s*last heavy session)?""")

internal fun quarter(x: Double): Double = (x * 4).roundToInt() / 4.0

/** Replaces the leading "N mi" of a workout description with the new distance. */
internal fun rewriteMiles(text: String, miles: Double): String {
    val re = Regex("""^\d+(\.\d+)?\s*mi\b""")
    return if (re.containsMatchIn(text)) text.replaceFirst(re, "${formatMiles(miles)} mi") else text
}

fun shortDate(d: LocalDate): String =
    "${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}"
