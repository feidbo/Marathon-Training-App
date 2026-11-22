package com.feidbo.marathon.data

import com.feidbo.marathon.core.CalfGuide
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.Exercises
import com.feidbo.marathon.core.MealSlot
import com.feidbo.marathon.core.Nutrition
import com.feidbo.marathon.core.Plan
import com.feidbo.marathon.core.Progress
import com.feidbo.marathon.core.STRENGTH_NOTE
import com.feidbo.marathon.core.Schedule
import com.feidbo.marathon.core.formatDuration
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.formatPace
import com.feidbo.marathon.core.workoutTitle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

/**
 * A plain-text progress report to share (e.g. paste into a chat for a plan check-in): settings, totals, every
 * plan day so far with what was planned and what was logged, the calf, weigh-ins, and the next two weeks.
 */
fun progressReport(data: AppData, schedule: Schedule, progress: Progress, today: LocalDate, appVersion: String): String = buildString {
    val s = data.settings
    val dayNo = Plan.indexOf(today) + 1
    appendLine("EUGENE MARATHON TRAINING — PROGRESS REPORT")
    appendLine("Generated ${dayFmt.format(today)} ${today.year} · app $appVersion")
    appendLine("Plan day $dayNo of ${Plan.days.size} · ${ChronoUnit.DAYS.between(today, Plan.raceDay)} days to race (${dayFmt.format(Plan.raceDay)})")
    schedule.on(today)?.let { appendLine("Phase ${it.day.phase.number}: ${it.day.phase.title} · plan week ${it.original.week}") }
    appendLine()

    appendLine("SETTINGS")
    appendLine("- Goal marathon pace: ${formatPace(s.marathonPaceSec)} /mi (finish ≈ ${formatDuration((s.marathonPaceSec * 26.2188).toInt())})")
    s.halfTimeSec?.let { appendLine("- Half-marathon time: ${formatDuration(it)}") }
    appendLine("- Easy heart-rate cap: ${s.easyHrCap?.let { "$it bpm" } ?: "not set"}")
    appendLine("- Longest run in the 30 days before the plan: ${formatMiles(s.preplanLongestMiles)} mi")
    appendLine()

    appendLine("TOTALS")
    appendLine("- Workouts done: ${progress.workoutsDone} of ${progress.workoutsPlannedSoFar} planned so far")
    appendLine("- Miles run: ${formatMiles(Math.round(progress.totalMiles * 10) / 10.0)} · longest run ${formatMiles(progress.longestRun)} mi")
    appendLine("- Strength sessions: ${progress.strengthSessions}")
    appendLine("- Streak ${progress.streak} (best ${progress.bestStreak}) · level ${progress.level.level} ${progress.level.name} · ${progress.xp} XP · ${progress.badges.size} badges")
    appendLine("- Stretch days: ${data.stretchDays.count { it <= Plan.indexOf(today) }}")
    appendLine()

    if (schedule.notices.isNotEmpty()) {
        appendLine("PLAN CHANGES MADE BY THE APP")
        schedule.notices.forEach { appendLine("- $it") }
        appendLine()
    }

    appendLine("DAY BY DAY (planned → logged)")
    for (sd in schedule.days.filter { it.date <= today }) {
        val d = sd.day
        val log = data.logs[d.index]
        val planned = when {
            d.run.isRun && d.strength.isPlanned -> "${workoutTitle(d)} ${formatMiles(d.miles)} mi + strength"
            d.run.isRun -> "${workoutTitle(d)} ${formatMiles(d.miles)} mi"
            d.strength.isPlanned -> "Strength"
            else -> "Rest"
        } + if (sd.isAdjusted && sd.original.miles != d.miles) " (plan said ${formatMiles(sd.original.miles)})" else ""
        val result = when {
            log == null && d.isTrainingDay -> if (d.date < today) "NOT LOGGED" else "not logged yet"
            log == null -> "—"
            log.status == DayStatus.MISSED -> "MISSED (${log.missReason?.label ?: "no reason"})"
            else -> buildList {
                add(if (log.status == DayStatus.DONE) "done" else "partial")
                log.run?.let { r ->
                    add(buildList {
                        add("${formatMiles(r.distance)} mi")
                        r.paceSec?.let { add("${formatPace(it)}/mi") }
                        r.segmentPaceSec?.let { add("segment ${formatPace(it)}/mi") }
                        r.raceTimeSec?.let { add("time ${formatDuration(it)}") }
                        r.avgHr?.let { add("HR $it") }
                        r.effort?.let { add("effort $it/10") }
                        if (r.pain) add("PAIN")
                    }.joinToString(", "))
                }
            }.joinToString(" · ")
        }
        val extras = buildList {
            if (d.index in data.stretchDays) add("stretch")
            if (d.index in data.calfDays) add("calf work")
            data.mealChecks[d.index]?.let { c ->
                val meals = MealSlot.entries.count { it.name in c }
                if (meals > 0) add("meals $meals/${MealSlot.entries.size}")
            }
        }
        appendLine("${dayFmt.format(d.date)} | $planned | $result" + if (extras.isNotEmpty()) " | ${extras.joinToString(", ")}" else "")
        log?.strength?.filter { e -> e.sets.any { it.reps > 0 } }?.forEach { e ->
            val name = Exercises.byId(e.exerciseId)?.name ?: e.exerciseId
            val sets = e.sets.filter { it.reps > 0 }
            val per = if (e.perSide) "/side" else ""
            val setText = if (e.bodyweight || sets.all { it.weight <= 0.0 }) "bodyweight × " + sets.joinToString(", ") { "${it.reps}" } + per
            else sets.joinToString(", ") { "${formatMiles(it.weight)} lb × ${it.reps}" } + per
            appendLine("    · $name: $setText" + (e.rir?.let { " · $it in reserve" } ?: ""))
        }
    }
    appendLine()

    val c = data.calf
    appendLine("CALF")
    appendLine(if (c.flagged) "- Guidance ON · ${c.side.label.lowercase()} calf · stage ${c.stage} (${c.currentStage.title})" else "- Guidance off")
    data.calfTests.sortedBy { it.dayIndex }.forEach { t ->
        appendLine("- Test ${dayFmt.format(Plan.days[t.dayIndex].date)}: ${CalfGuide.describe(t)}")
    }
    appendLine()

    val n = data.nutrition
    appendLine("FOOD")
    if (!n.enabled) appendLine("- Meal plan off")
    else {
        appendLine("- ${formatMiles(n.weightLb)} lb · age ${n.age} · ${formatMiles(n.bodyFatPct)}% body fat · activity ×${n.activityFactor} · deficit ${n.deficitPct}%")
        schedule.on(today)?.let { sd ->
            val t = Nutrition.target(n, sd.day)
            appendLine("- Today's target: ${t.kcal} kcal, ${t.proteinG} g protein, ${t.fuelServings} fuel servings")
        }
        val days = data.mealDays.count { it <= Plan.indexOf(today) }
        appendLine("- Days with every meal ticked: $days")
    }
    if (data.weighIns.isNotEmpty()) {
        appendLine("- Weigh-ins: " + data.weighIns.sortedBy { it.dayIndex }.joinToString(", ") {
            "${dayFmt.format(Plan.days[it.dayIndex].date)} ${formatMiles(it.lb)} lb"
        })
        Nutrition.weighInAdvice(data.weighIns)?.let { appendLine("- Trend: $it") }
    }
    appendLine()

    appendLine("NEXT 14 DAYS")
    for (sd in schedule.days.filter { it.date > today && it.date <= today.plusDays(14) }) {
        val d = sd.day
        appendLine("${dayFmt.format(d.date)} | ${d.text.replace(STRENGTH_NOTE, "").trim()}" + if (d.strength.isPlanned) " | + strength" else "")
    }
}
