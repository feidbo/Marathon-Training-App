package com.feidbo.marathon.core

import kotlin.math.abs
import kotlin.math.roundToInt

enum class TipLevel { GREAT, INFO, WARN }

data class Tip(val level: TipLevel, val text: String)

/**
 * Checks a logged run against the plan and returns corrective or encouraging tips.
 * [longestPrior30] is the longest run in the 30 days before this one.
 */
fun runFeedback(day: PlannedDay, log: RunLog, settings: Settings, longestPrior30: Double): List<Tip> {
    val tips = mutableListOf<Tip>()
    val mp = settings.marathonPaceSec

    // Entries that are probably typos.
    if (log.distance <= 0.0 || log.distance > 32) {
        tips += Tip(TipLevel.WARN, "${formatMiles(log.distance)} mi looks off — double-check the distance.")
        return tips
    }
    log.paceSec?.let {
        if (it < 5 * 60 || it > 20 * 60) {
            tips += Tip(TipLevel.WARN, "A ${formatPace(it)} /mi average looks off — enter minutes:seconds per mile, like 12:15.")
            return tips
        }
    }

    // Distance against the plan.
    if (day.run.isRun && day.miles > 0 && day.run != RunKind.RACE && day.run != RunKind.HALF_RACE) {
        val ratio = log.distance / day.miles
        when {
            longestPrior30 > 0 && log.distance > longestPrior30 * 1.10 && log.distance > day.miles * 1.05 ->
                tips += Tip(TipLevel.WARN,
                    "That's more than 10% longer than your longest run in the last 30 days (${formatMiles(longestPrior30)} mi). " +
                        "Runs 10–30% longer came with a 64% higher injury rate (Frandsen 2025). Stick to the planned distance.")
            ratio > 1.10 ->
                tips += Tip(TipLevel.INFO,
                    "${formatMiles(log.distance - day.miles)} mi more than planned. Extra miles don't bank fitness — " +
                        "keep to the plan so the long runs stay safe.")
            ratio >= 0.95 -> tips += Tip(TipLevel.GREAT, "Planned distance: done.")
            ratio >= 0.75 -> tips += Tip(TipLevel.INFO,
                "A little short of ${formatMiles(day.miles)} mi — still good work. Don't tack the missing miles onto another day.")
            else -> tips += Tip(TipLevel.INFO,
                "Well short of the planned ${formatMiles(day.miles)} mi. That's OK — skip the rest and carry on. " +
                    "If something hurt or you felt run down, take it easy and see the advice below.")
        }
    }

    // Pace against the zone.
    val avg = log.paceSec
    val seg = log.segmentPaceSec
    when (day.run) {
        RunKind.EASY, RunKind.RECOVERY, RunKind.SHAKEOUT, RunKind.LONG, RunKind.EASY_STRIDES -> avg?.let {
            val slack = if (day.run == RunKind.EASY_STRIDES || day.run == RunKind.SHAKEOUT) 20 else 10
            when {
                it < Paces.EASY_FAST - slack -> tips += Tip(TipLevel.WARN,
                    "${formatPace(it)} /mi is faster than easy (${formatPace(Paces.EASY_FAST)}–${formatPace(Paces.EASY_SLOW)}). " +
                        "Slow down — about 80% of your running should be easy, and runners who kept it easy improved more " +
                        "(Esteve-Lanao 2007). You should be able to talk in full sentences.")
                it > Paces.EASY_SLOW + 90 -> tips += Tip(TipLevel.INFO,
                    "Slower than the easy range — that's fine. Easy is judged by effort; hills, heat and fatigue slow you down, " +
                        "and run-walk counts.")
                else -> tips += Tip(TipLevel.GREAT, "Right in the easy zone. This is how aerobic fitness gets built.")
            }
        }
        RunKind.MARATHON_PACE -> {
            val p = seg ?: avg
            if (p != null) {
                val diff = p - mp
                val label = if (seg != null) "Your marathon-pace miles" else "Your average"
                when {
                    seg == null && avg != null && avg in mp..Paces.EASY_SLOW + 30 ->
                        tips += Tip(TipLevel.GREAT, "Average pace fits a warm-up + marathon pace + cool-down. Nice control.")
                    diff < -15 -> tips += Tip(TipLevel.WARN,
                        "$label (${formatPace(p)}) were ${abs(diff)} s/mi faster than goal marathon pace (${formatPace(mp)}). " +
                            "Faster than goal turns this into a tempo — save it for race day.")
                    diff > 20 -> tips += Tip(TipLevel.INFO,
                        "$label (${formatPace(p)}) were slower than goal marathon pace (${formatPace(mp)}). " +
                            "If ${formatPace(mp)} feels harder than 5–6 out of 10, run these by effort; your Mar 7 half will reset the goal.")
                    else -> tips += Tip(TipLevel.GREAT, "Locked in on marathon pace. That's race-day rhythm.")
                }
            }
        }
        RunKind.TEMPO -> {
            val p = seg ?: avg
            if (p != null) {
                when {
                    seg == null -> tips += Tip(TipLevel.INFO,
                        "Add the pace of just the tempo portion for a better check (target ${formatPace(Paces.TEMPO_FAST)}–${formatPace(Paces.TEMPO_SLOW)}).")
                    p < Paces.TEMPO_FAST - 10 -> tips += Tip(TipLevel.WARN,
                        "${formatPace(p)} /mi is faster than tempo. Threshold should feel comfortably hard (7/10), " +
                            "about what you could race for an hour. Start at the slower end.")
                    p > Paces.TEMPO_SLOW + 20 -> tips += Tip(TipLevel.INFO,
                        "Slower than the tempo range — fine if it felt like 7/10. Hills, heat and fatigue all slow you down.")
                    else -> tips += Tip(TipLevel.GREAT, "Tempo in the zone. Comfortably hard is exactly right.")
                }
            }
        }
        RunKind.HILLS, RunKind.FARTLEK -> log.effort?.let {
            when {
                it < 6 -> tips += Tip(TipLevel.INFO, "The hard efforts should feel about 8/10 — push a little more on the next one.")
                it >= 10 -> tips += Tip(TipLevel.WARN, "Hard but controlled — about 8/10, not all-out.")
                else -> tips += Tip(TipLevel.GREAT, "Hard but controlled. Great session.")
            }
        }
        else -> Unit
    }

    // Effort.
    log.effort?.let {
        if (day.run.isEasyType && it >= 7) {
            tips += Tip(TipLevel.WARN,
                "Easy runs should feel 3–4/10. If easy feels hard for several days, that's a sign of doing too much — " +
                    "take an extra rest day.")
        }
    }

    // Race days.
    if (day.run == RunKind.HALF_RACE) {
        log.raceTimeSec?.let { half ->
            if (half in 80 * 60..200 * 60) {
                val riegel = Paces.riegelMarathonSec(half)
                tips += Tip(TipLevel.GREAT,
                    "Half done in ${formatDuration(half)}! Riegel predicts ${formatDuration(riegel)} " +
                        "(${formatPace(Paces.paceForMarathon(riegel))} /mi); many recreational runners finish about 10 min slower " +
                        "(${formatDuration(riegel + 600)}). Your marathon pace is now ${formatPace(Paces.suggestedMpFromHalf(half))} /mi — change it in Settings.")
            } else {
                tips += Tip(TipLevel.WARN, "${formatDuration(half)} doesn't look like a half-marathon time — use h:mm:ss.")
            }
        }
    }
    if (day.run == RunKind.RACE) tips += Tip(TipLevel.GREAT, "YOU ARE A MARATHONER. Eugene, done.")

    if (log.pain) {
        tips += Tip(TipLevel.WARN,
            "Pain that changes your stride, or gets worse as you run, ends the run that day. " +
                "If it lasts more than a few days, get it checked by a physical therapist or doctor.")
    }
    return tips
}

/** Feedback for one exercise against its prescription and the previous session. */
fun strengthFeedback(
    rx: Prescription,
    log: ExerciseLog,
    previous: ExerciseLog?,
    kind: StrengthKind,
): List<Tip> {
    val tips = mutableListOf<Tip>()
    val ex = rx.exercise
    val sets = log.sets.filter { it.reps > 0 }
    if (sets.isEmpty()) return tips
    if (sets.any { it.weight < 0 || it.weight > 1500 }) {
        return listOf(Tip(TipLevel.WARN, "That weight looks off — double-check it."))
    }
    if (sets.any { it.reps > 50 }) {
        return listOf(Tip(TipLevel.WARN, "That rep count looks off — double-check it."))
    }
    val weight = log.topWeight
    val prevWeight = previous?.topWeight
    if (!ex.inverted && weight <= 0.0) {
        return listOf(Tip(TipLevel.WARN, "Enter the weight you used so the app can track your progress and suggest the next load."))
    }

    val minReps = sets.minOf { it.reps }
    val maxReps = sets.maxOf { it.reps }
    val heavier = if (ex.inverted) "less counterweight" else "more weight"
    val lighter = if (ex.inverted) "more counterweight" else "less weight"

    if (kind == StrengthKind.LIGHT || kind == StrengthKind.LEARN) {
        if (kind == StrengthKind.LIGHT && prevWeight != null && prevWeight > 0 && !ex.inverted && weight > prevWeight * 0.75) {
            tips += Tip(TipLevel.INFO, "Light day: aim for about 60% of your working weight (~${roundWeight(prevWeight * 0.6)} lb).")
        } else {
            tips += Tip(TipLevel.GREAT, if (kind == StrengthKind.LEARN) "Machine learned — note your seat setting." else "Nice and light. Fresh legs for the run.")
        }
        return tips
    }

    // Jumps compared with last time.
    if (prevWeight != null && prevWeight > 0 && weight > 0) {
        val change = (weight - prevWeight) / prevWeight
        if (!ex.inverted && change > 0.15 && weight - prevWeight > 10) {
            tips += Tip(TipLevel.WARN,
                "Big jump from last time (${roundWeight(prevWeight)} → ${roundWeight(weight)} lb). " +
                    "Add about 5–10% at a time to keep your joints and tendons happy.")
        }
        if (ex.inverted && change < -0.20 && prevWeight - weight > 10) {
            tips += Tip(TipLevel.WARN,
                "Big drop in assistance (${roundWeight(prevWeight)} → ${roundWeight(weight)} lb). Reduce the counterweight a little at a time.")
        }
    }

    val targetRir = repsLeft(kind)
    val rir = effectiveRir(log, targetRir)
    when {
        minReps < rx.reps - 1 || rir == 0 && minReps < rx.reps -> tips += Tip(TipLevel.WARN,
            "Too heavy today: you're aiming for ${rx.sets} × ${rx.reps} with about $targetRir reps left. " +
                "Use $lighter next time (about 10%).")
        maxReps > rx.reps + 3 -> tips += Tip(TipLevel.INFO,
            "${maxReps} reps is well over the ${rx.reps} target — the weight is too light. Use $heavier so ${rx.reps} reps is hard.")
        rir != null && rir >= targetRir + 1 && minReps >= rx.reps -> tips += Tip(TipLevel.GREAT,
            "$rir reps left is easier than the target ($targetRir) — you'll get a heavier suggestion next session.")
        rir != null && rir < targetRir -> tips += Tip(TipLevel.INFO,
            "Only $rir left — a bit harder than the $targetRir-left target. Fine today; repeat this weight next time.")
        else -> tips += Tip(TipLevel.GREAT, "Right on target. Strong legs run cheaper (Støren 2008).")
    }
    return tips
}

private fun repsLeft(kind: StrengthKind) = strengthSession(kind)?.targetRir ?: 2

/** Rounds to the nearest 5 lb (2.5 lb under 50 lb), always moving at least one step when [forceUp] is set. */
fun roundWeight(w: Double, forceUp: Boolean = false): Double {
    val step = if (w < 50) 2.5 else 5.0
    val r = (w / step).roundToInt() * step
    return if (forceUp && r <= w / 1.05) r + step else r
}

/** What the plan says to do when a day is missed. */
fun missAdvice(reason: MissReason): List<String> = when (reason) {
    MissReason.SICK_MILD -> listOf(
        "With mild cold symptoms only, a shorter easy run is fine if you feel up to it — or rest.",
        "Don't make up the miles later.",
    )
    MissReason.SICK_SYSTEMIC -> listOf(
        "With fever, chest symptoms or body aches, don't run until you're well. See a doctor if unsure.",
        "Your plan adjusts automatically if you miss a week or more.",
    )
    MissReason.PAIN -> listOf(
        "Pain that changes your stride, or gets worse as you run, ends the run that day.",
        "If it lasts more than a few days, get it checked by a physical therapist or doctor.",
    )
    MissReason.TIRED -> listOf(
        "Rest is training too. Lasting fatigue, poor sleep or a high resting heart rate are signs of doing too much.",
        "Your next week will be about 15% lighter.",
    )
    MissReason.BUSY, MissReason.OTHER -> listOf(
        "No problem — skip it and carry on. Never move a missed run onto Tuesday or stack it on another day.",
    )
} + "Missed under a week: resume where you are. Missed 1–2 weeks: repeat your last full week. The app does this for you."
