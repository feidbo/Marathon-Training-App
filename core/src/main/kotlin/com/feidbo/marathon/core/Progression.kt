package com.feidbo.marathon.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Session-to-session load recommendations.
 *
 * The evidence this follows:
 *  - Progressive overload: raise the load 2–10% once you can do 1–2 reps more than the target
 *    (ACSM position stand on progression models, 2009). Bigger jumps suit big lower-body lifts.
 *  - Effort is judged by reps in reserve (RIR), the same "reps left in the tank" the plan uses;
 *    lifters estimate it well when close to failure (Zourdos 2016; Helms 2016).
 *  - When the effort target isn't logged, the "2-for-2" rule: add load after completing every rep
 *    at the same weight in two sessions in a row (NSCA).
 *  - When the rep target changes between phases, today's load comes from an estimated 1RM
 *    (Epley: 1RM = w × (1 + reps/30), counting reps in reserve as reps you could have done).
 *  - Strength is generally kept for about 3 weeks without training (Bosquet 2013); after longer
 *    breaks, ease back in about 10% lighter.
 */
enum class LoadChange { UP, BIG_UP, SAME, DOWN, NEW_TARGET, RETURNING, LIGHT }

data class LoadAdvice(
    val weight: Double,
    val lastWeight: Double,
    val lastDate: LocalDate,
    val change: LoadChange,
    val reason: String,
) {
    val delta: Double get() = weight - lastWeight
    /** "+10 lb", "−5 lb" or "same". */
    val deltaText: String
        get() = when {
            abs(delta) < 0.01 -> "same as last time"
            delta > 0 -> "+${formatMiles(delta)} lb"
            else -> "−${formatMiles(-delta)} lb"
        }
}

/** Reps in reserve for a log: the recorded value, or one inferred from the older "feel" chips. */
fun effectiveRir(log: ExerciseLog, targetRir: Int): Int? = log.rir ?: when (log.feel) {
    Feel.EASY -> targetRir + 2
    Feel.RIGHT -> targetRir
    Feel.HARD -> 0
    null -> null
}

/** Epley estimated 1RM, counting reps left in the tank as reps you could have done. */
fun estimatedOneRepMax(weight: Double, reps: Int, rir: Int): Double = weight * (1 + (reps + rir) / 30.0)

private fun loadForReps(oneRm: Double, reps: Int, rir: Int): Double = oneRm / (1 + (reps + rir) / 30.0)

/** Smallest practical load step: 5 lb plates/pins, 2.5 lb under 50 lb. */
private fun step(w: Double) = if (w < 50) 2.5 else 5.0
private fun roundDown(w: Double) = floor(w / step(w) + 1e-6) * step(w)
private fun roundUp(w: Double) = ceil(w / step(w) - 1e-6) * step(w)

/**
 * Recommends today's load for [rx] on a [kind] strength day dated [date], from the earlier sessions
 * in [history] (oldest first). Returns null when there's nothing to go on yet.
 */
fun loadAdvice(rx: Prescription, kind: StrengthKind, date: LocalDate, history: List<ExerciseHistoryEntry>): LoadAdvice? {
    if (history.isEmpty()) return null
    val ex = rx.exercise
    val session = strengthSession(kind) ?: return null
    val working = history.lastOrNull { it.strength != StrengthKind.LIGHT && it.strength != StrengthKind.LEARN }
    val last = working ?: history.last()
    val lastDate = Plan.days[last.dayIndex].date
    val lastW = last.log.topWeight
    if (lastW <= 0.0 && !ex.inverted) return null
    val inv = ex.inverted

    fun advice(w: Double, change: LoadChange, reason: String) =
        LoadAdvice(maxOf(0.0, w), lastW, lastDate, change, reason)

    // Light sessions: about 60% of working weight (plan, Mar 1 and Apr 12).
    if (kind == StrengthKind.LIGHT) {
        return if (inv) advice(roundUp(lastW + maxOf(10.0, lastW * 0.25)), LoadChange.LIGHT,
            "Light session: add counterweight so it's easy — leave the gym fresh.")
        else advice(roundDown(lastW * 0.6), LoadChange.LIGHT, "Light session: about 60% of your working weight.")
    }

    val days = ChronoUnit.DAYS.between(lastDate, date)
    if (days > 21) {
        val w = if (inv) roundUp(lastW * 1.10 + 2.5) else roundDown(lastW * 0.90)
        return advice(w, LoadChange.RETURNING,
            "It's been $days days since your last session. Strength mostly holds for about 3 weeks (Bosquet 2013), " +
                "so ease back in about 10% ${if (inv) "more assisted" else "lighter"} and build back up.")
    }

    val lastSession = strengthSession(last.strength)
    val lastRx = lastSession?.items?.firstOrNull { it.exercise.id == ex.id }
    val lastTargetReps = lastRx?.reps ?: rx.reps
    val lastTargetRir = lastSession?.targetRir ?: session.targetRir
    val sets = last.log.sets.filter { it.reps > 0 }
    val minReps = sets.minOfOrNull { it.reps } ?: 0
    val rir = effectiveRir(last.log, lastTargetRir)
    val repsText = sets.joinToString(", ") { it.reps.toString() }

    // New rep target (Phase 1 → 2 → 3, or the first working session after learning the machines).
    if (!inv && (lastTargetReps != rx.reps || last.strength == StrengthKind.LEARN)) {
        val r = rir ?: lastTargetRir
        val oneRm = estimatedOneRepMax(lastW, minReps, r)
        var w = loadForReps(oneRm, rx.reps, session.targetRir)
        val firstHeavyWeek = kind == StrengthKind.PHASE2 && last.strength != StrengthKind.PHASE2
        if (firstHeavyWeek) w *= 0.9
        w = w.coerceIn(lastW * 0.85, lastW * 1.30)
        return advice(roundDown(w), LoadChange.NEW_TARGET,
            "New target: ${rx.sets} × ${rx.reps} with about ${session.targetRir} left. From ${formatMiles(lastW)} lb × $minReps " +
                "(~$r left) your estimated max is about ${formatMiles(oneRm.roundToInt().toDouble())} lb, which puts ${rx.reps} reps at " +
                "about ${formatMiles(roundDown(loadForReps(oneRm, rx.reps, session.targetRir)))} lb." +
                if (firstHeavyWeek) " The plan says start the loads light this first heavy week, so a bit under that." else "")
    }

    val target = lastTargetReps
    val allHit = sets.isNotEmpty() && minReps >= target
    val missedBig = minReps <= target - 2

    // Step sizes: ACSM's 2–10%, with bigger jumps for the big lower-body lifts.
    fun up(big: Boolean): Double = when (ex.load) {
        LoadType.ASSISTED -> lastW - if (big) 10.0 else 5.0
        LoadType.LOWER_COMPOUND -> roundUp(lastW + maxOf(if (big) 20.0 else 10.0, lastW * if (big) 0.10 else 0.05))
        LoadType.LOWER_SMALL -> roundUp(lastW + maxOf(if (big) 10.0 else 5.0, lastW * if (big) 0.075 else 0.05))
    }
    fun down(): Double = if (inv) roundUp(lastW + maxOf(5.0, lastW * 0.10)) else roundDown(lastW * 0.90)

    return when {
        missedBig -> advice(down(), LoadChange.DOWN,
            "Last time you got $repsText of $target. Go about 10% ${if (inv) "more assisted" else "lighter"} " +
                "so every rep is done with about $lastTargetRir left — quality reps build strength.")
        allHit && (rir != null && rir >= lastTargetRir + 2 || minReps >= target + 3) -> advice(up(true), LoadChange.BIG_UP,
            "You got all $target reps${rir?.let { " with $it left" } ?: ""} — well under the target effort. " +
                "A bigger jump (ACSM: add 2–10% once you beat the target).")
        allHit && rir != null && rir >= lastTargetRir + 1 -> advice(up(false), LoadChange.UP,
            "You got all $target reps with $rir left, easier than the $lastTargetRir-left target. " +
                "Time to add weight (ACSM: 2–10%; the plan: add weight when every set feels easier than target).")
        allHit && rir == null && twoForTwo(history, last, target) -> advice(up(false), LoadChange.UP,
            "All reps done at ${formatMiles(lastW)} lb two sessions in a row — the \"2-for-2\" rule says add weight. " +
                "Log reps left in the tank for sharper suggestions.")
        allHit && rir == null -> advice(lastW, LoadChange.SAME,
            "All reps done. Repeat ${formatMiles(lastW)} lb; if you complete it again (or log reps left), you'll move up.")
        allHit && rir != null && rir < lastTargetRir -> advice(lastW, LoadChange.SAME,
            "All reps done, but harder than target ($rir left vs $lastTargetRir). Repeat ${formatMiles(lastW)} lb until it feels easier.")
        allHit -> advice(lastW, LoadChange.SAME,
            "Right on target last time. Same weight until every set feels easier than $lastTargetRir reps left — then add.")
        else -> advice(lastW, LoadChange.SAME,
            "One rep short last time ($repsText of $target). Stay at ${formatMiles(lastW)} lb and own it.")
    }
}

/** Completed every rep at the same weight in the previous working session too. */
private fun twoForTwo(history: List<ExerciseHistoryEntry>, last: ExerciseHistoryEntry, target: Int): Boolean {
    val prev = history.lastOrNull { it.dayIndex < last.dayIndex && it.strength == last.strength } ?: return false
    val prevMin = prev.log.sets.filter { it.reps > 0 }.minOfOrNull { it.reps } ?: return false
    return abs(prev.log.topWeight - last.log.topWeight) < 0.01 && prevMin >= target
}

/**
 * Lifts in [newLogs] (for day [dayIndex]) that beat the best estimated max logged on any earlier day.
 * Assisted pull-ups are skipped: their "weight" is help, not load.
 */
fun personalRecords(dayIndex: Int, newLogs: List<ExerciseLog>, logs: Map<Int, DayLog>): List<Exercise> =
    newLogs.mapNotNull { e ->
        val ex = Exercises.byId(e.exerciseId)?.takeIf { !it.inverted } ?: return@mapNotNull null
        fun max(l: ExerciseLog): Double? = l.sets.filter { it.reps > 0 }.takeIf { it.isNotEmpty() && l.topWeight > 0 }
            ?.let { estimatedOneRepMax(l.topWeight, it.minOf { s -> s.reps }, l.rir ?: 0) }
        val now = max(e) ?: return@mapNotNull null
        val before = exerciseHistory(ex.id, logs).filter { it.dayIndex != dayIndex }.mapNotNull { max(it.log) }.maxOrNull()
            ?: return@mapNotNull null
        ex.takeIf { now > before + 0.5 }
    }

/** Load advice for a strength day, using everything logged before it. */
fun loadAdviceFor(rx: Prescription, day: PlannedDay, logs: Map<Int, DayLog>): LoadAdvice? =
    loadAdvice(rx, day.strength, day.date, exerciseHistory(rx.exercise.id, logs).filter { it.dayIndex < day.index })
