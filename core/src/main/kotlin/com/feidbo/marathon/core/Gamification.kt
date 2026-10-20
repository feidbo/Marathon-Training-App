package com.feidbo.marathon.core

import java.time.DayOfWeek
import java.time.LocalDate

data class Badge(val id: String, val emoji: String, val name: String, val description: String)

data class LevelInfo(val level: Int, val name: String, val xp: Int, val floorXp: Int, val nextXp: Int?) {
    val progress: Float get() = if (nextXp == null) 1f else (xp - floorXp).toFloat() / (nextXp - floorXp)
}

object Game {
    private val levels = listOf(
        0 to "Fresh Legs",
        100 to "Rookie Runner",
        300 to "Steady Strider",
        600 to "Hill Hunter",
        1000 to "Tempo Tamer",
        1500 to "Long Hauler",
        2200 to "Iron Legs",
        3000 to "Pace Master",
        4000 to "Twenty Miler",
        5200 to "Taper Titan",
        6500 to "Track Town Legend",
    )

    fun level(xp: Int): LevelInfo {
        val idx = levels.indexOfLast { xp >= it.first }.coerceAtLeast(0)
        return LevelInfo(idx + 1, levels[idx].second, xp, levels[idx].first, levels.getOrNull(idx + 1)?.first)
    }

    /** XP for the daily quad stretch; a little more on rest days, when it's the whole job. */
    fun stretchXp(day: PlannedDay): Int = if (day.isTrainingDay) 5 else 10

    /** XP for a day's log. Honest "couldn't do it" logs still earn a little: listening to your body counts. */
    fun xpFor(day: PlannedDay, log: DayLog): Int {
        if (log.status == DayStatus.MISSED) return 2
        var xp = 0
        val run = log.run
        if (day.run.isRun && run != null && run.distance > 0) {
            xp += 10 + (run.distance * 3).toInt()
            if (day.run.isKey) xp += 10
            if (day.run == RunKind.LONG || day.miles >= 10) xp += 15
            if (day.run == RunKind.HALF_RACE) xp += 150
            if (day.run == RunKind.RACE) xp += 500
        }
        if (day.strength.isPlanned && log.strength.any { e -> e.sets.any { it.reps > 0 } }) xp += 25
        if (log.status == DayStatus.PARTIAL) xp = (xp * 0.75).toInt()
        return xp
    }

    val badges = listOf(
        Badge("first_step", "👟", "First Steps", "Log your first workout"),
        Badge("first_lift", "🏋️", "Iron Starter", "Finish your first strength session"),
        Badge("week_complete", "📅", "Week Warrior", "Complete every workout in a week"),
        Badge("streak_7", "🔥", "On Fire", "7 workouts in a row"),
        Badge("streak_21", "⚡", "Unstoppable", "21 workouts in a row"),
        Badge("stretch_30", "🧘", "Bendy", "Stretch on 30 days"),
        Badge("easy_10", "🐢", "Smart Pacer", "10 easy runs in the easy zone"),
        Badge("hills_3", "⛰️", "Hill Hunter", "Finish 3 hill sessions"),
        Badge("tempo_5", "🎯", "Tempo Tamer", "Finish 5 tempo runs"),
        Badge("lift_20", "💪", "Iron Legs", "20 strength sessions"),
        Badge("double_digits", "🔟", "Double Digits", "Run 10 miles or more"),
        Badge("miles_100", "💯", "Century", "100 total miles"),
        Badge("miles_250", "🚀", "250 Club", "250 total miles"),
        Badge("miles_500", "🌎", "500 Club", "500 total miles"),
        Badge("phase_1", "🌱", "Rebuilt", "Reach the end of Phase 1"),
        Badge("phase_2", "🧱", "Base Built", "Reach the end of Phase 2"),
        Badge("half", "🥈", "Half Hero", "Run the Mar 7 half marathon"),
        Badge("twenty", "🏔️", "Twenty Club", "Run 20 miles"),
        Badge("taper", "🧘‍♂️", "Trust the Taper", "Finish the taper"),
        Badge("perfect_10", "✨", "Perfect 10", "10 perfect days: workout + stretch"),
        Badge("comeback", "🦅", "Comeback Kid", "Train again after 3+ missed workouts"),
        Badge("pr", "📈", "PR Breaker", "Beat your best estimated max on a lift"),
        Badge("miles_26", "🦆", "Marathon in Pieces", "26.2 total miles"),
        Badge("long_15", "🦵", "Fifteen Strong", "Run 15 miles in one go"),
        Badge("mp_5", "🎯", "Locked In", "5 marathon-pace runs within 15 s/mi of goal"),
        Badge("easy_50", "🧘‍♂️", "Zen Pacer", "50 easy runs in the easy zone"),
        Badge("sunday_10", "☀️", "Sunday Funday", "10 Sunday long runs"),
        Badge("streak_50", "🌋", "Volcano", "50 workouts in a row"),
        Badge("weeks_4", "🗓️", "Month of Grit", "4 full weeks completed"),
        Badge("stretch_100", "🪢", "Rubber Band", "Stretch on 100 days"),
        Badge("rest_25", "😴", "Rest Is Training", "Stretch on 25 rest days"),
        Badge("lift_35", "🦍", "Gorilla Legs", "35 strength sessions"),
        Badge("pr_5", "🚀", "PR Machine", "Set 5 lifting PRs"),
        Badge("level_5", "⭐", "Rising Star", "Reach level 5"),
        Badge("level_10", "🌟", "Superstar", "Reach level 10"),
        Badge("turkey", "🦃", "Turkey Trotter", "Run on Thanksgiving"),
        Badge("jingle", "🎄", "Jingle Miles", "Train on Christmas Eve"),
        Badge("new_year", "🎆", "New Year, New Miles", "Your first workout of 2027"),
        Badge("halfway", "🧭", "Halfway There", "Train past the plan's halfway point (Jan 20)"),
        Badge("bib_ready", "🎽", "Bib Ready", "Every race-week workout done"),
        Badge("marathoner", "🏅", "Marathoner", "Finish the Eugene Marathon"),
    )

    const val PERFECT_DAY_XP = 10
    const val FULL_WEEK_XP = 50
    fun badge(id: String) = badges.first { it.id == id }
}

data class Progress(
    val xp: Int,
    val level: LevelInfo,
    val streak: Int,
    val bestStreak: Int,
    val totalMiles: Double,
    val longestRun: Double,
    val workoutsDone: Int,
    val workoutsPlannedSoFar: Int,
    val strengthSessions: Int,
    val badges: Set<String>,
    val perfectDays: Int = 0,
    val fullWeeks: Int = 0,
    /** Progress toward countable badges: id → (current, target). */
    val badgeProgress: Map<String, Pair<Double, Double>> = emptyMap(),
)

fun computeProgress(
    schedule: Schedule,
    logs: Map<Int, DayLog>,
    stretchDays: Set<Int>,
    today: LocalDate,
    marathonPaceSec: Int = Paces.DEFAULT_MP,
): Progress {
    val days = schedule.days
    var xp = 0
    var miles = 0.0
    var longest = 0.0
    var done = 0
    var plannedSoFar = 0
    var lifts = 0
    val stretches = stretchDays.size
    var easyInZone = 0
    var hills = 0
    var tempos = 0
    var streak = 0
    var best = 0
    var perfect = 0
    var missedRun = 0
    var prCount = 0
    var mpLocked = 0
    var sundayLongs = 0
    var restStretches = 0
    val earned = mutableSetOf<String>()
    val bestMax = mutableMapOf<String, Double>()

    for (sd in days) {
        val d = sd.day
        val log = logs[d.index]
        if (d.isTrainingDay && d.date <= today) plannedSoFar++
        if (d.index in stretchDays) {
            xp += Game.stretchXp(d)
            if (!d.isTrainingDay) restStretches++
        }
        if (log == null) {
            // A past training day left unlogged breaks the streak; today and rest days don't.
            if (d.isTrainingDay && d.date < today) streak = 0
            continue
        }
        xp += log.xp
        if (log.status == DayStatus.MISSED) {
            if (d.isTrainingDay) missedRun++
            // Sickness and pain shield the streak: resting when you should is the right call.
            if (log.missReason != MissReason.SICK_SYSTEMIC && log.missReason != MissReason.PAIN &&
                log.missReason != MissReason.SICK_MILD) streak = 0
            continue
        }
        if (!d.isTrainingDay) continue
        if (missedRun >= 3) earned += "comeback"
        missedRun = 0
        if (log.status == DayStatus.DONE && d.index in stretchDays) {
            perfect++
            xp += Game.PERFECT_DAY_XP
        }
        done++
        streak++
        best = maxOf(best, streak)
        earned += "first_step"
        if (d.date.year == 2027 && d.date.dayOfYear <= 7) earned += "new_year"
        if (d.date >= LocalDate.of(2027, 1, 20)) earned += "halfway"
        if (d.date == LocalDate.of(2026, 12, 24)) earned += "jingle"
        val run = log.run
        if (run != null && run.distance > 0) {
            miles += run.distance
            longest = maxOf(longest, run.distance)
            if (d.run.isEasyType && run.paceSec != null && run.paceSec in (Paces.EASY_FAST - 10)..(Paces.EASY_SLOW + 90)) easyInZone++
            if (d.run == RunKind.HILLS) hills++
            if (d.run == RunKind.TEMPO) tempos++
            if (d.run == RunKind.HALF_RACE) earned += "half"
            if (d.run == RunKind.RACE) earned += "marathoner"
            if (run.distance >= 10) earned += "double_digits"
            if (run.distance >= 19.5) earned += "twenty"
            if (run.distance >= 14.9) earned += "long_15"
            if (d.date == LocalDate.of(2026, 11, 26)) earned += "turkey"
            if (d.date.dayOfWeek == DayOfWeek.SUNDAY && (d.run == RunKind.LONG || d.miles >= 8)) sundayLongs++
            if (d.run == RunKind.MARATHON_PACE) {
                val p = run.segmentPaceSec
                if (p != null && kotlin.math.abs(p - marathonPaceSec) <= 15) mpLocked++
            }
        }
        if (d.strength.isPlanned && log.strength.any { e -> e.sets.any { it.reps > 0 } }) {
            lifts++
            earned += "first_lift"
            for (e in log.strength) {
                val ex = Exercises.byId(e.exerciseId) ?: continue
                val sets = e.sets.filter { it.reps > 0 }
                if (ex.inverted || sets.isEmpty() || e.topWeight <= 0) continue
                val max = estimatedOneRepMax(e.topWeight, sets.minOf { it.reps }, e.rir ?: 0)
                val key = ex.id + if (e.perSide) ":1" else ":2"   // one-leg and two-leg maxes aren't comparable
                val prev = bestMax[key]
                if (prev != null && max > prev + 0.5) { earned += "pr"; prCount++ }
                if (prev == null || max > prev) bestMax[key] = max
            }
        }
    }

    // Weeks fully completed earn a bonus.
    var fullWeeks = 0
    days.groupBy { it.original.week }.forEach { (_, wDays) ->
        val training = wDays.filter { it.day.isTrainingDay }
        if (training.size >= 3 && training.all { logs[it.index]?.status == DayStatus.DONE }) {
            fullWeeks++
            xp += Game.FULL_WEEK_XP
            earned += "week_complete"
        }
    }
    fun phaseReached(lastDate: LocalDate) = days.filter { it.date <= lastDate && it.day.isTrainingDay }
        .let { t -> t.isNotEmpty() && t.last().let { logs[it.index]?.status } in setOf(DayStatus.DONE, DayStatus.PARTIAL) }
    if (phaseReached(LocalDate.of(2026, 12, 13))) earned += "phase_1"
    if (phaseReached(LocalDate.of(2027, 2, 7))) earned += "phase_2"
    if (phaseReached(LocalDate.of(2027, 4, 24))) earned += "taper"

    // Race week: every training day Apr 19–24 done.
    val raceWeek = days.filter { it.original.week == 27 && it.day.isTrainingDay && it.day.run != RunKind.RACE }
    if (raceWeek.isNotEmpty() && raceWeek.all { logs[it.index]?.status == DayStatus.DONE }) earned += "bib_ready"

    val level = Game.level(xp)
    if (miles >= 26.2) earned += "miles_26"
    if (mpLocked >= 5) earned += "mp_5"
    if (easyInZone >= 50) earned += "easy_50"
    if (sundayLongs >= 10) earned += "sunday_10"
    if (best >= 50) earned += "streak_50"
    if (fullWeeks >= 4) earned += "weeks_4"
    if (stretches >= 100) earned += "stretch_100"
    if (restStretches >= 25) earned += "rest_25"
    if (lifts >= 35) earned += "lift_35"
    if (prCount >= 5) earned += "pr_5"
    if (level.level >= 5) earned += "level_5"
    if (level.level >= 10) earned += "level_10"
    if (perfect >= 10) earned += "perfect_10"
    if (best >= 7) earned += "streak_7"
    if (best >= 21) earned += "streak_21"
    if (stretches >= 30) earned += "stretch_30"
    if (easyInZone >= 10) earned += "easy_10"
    if (hills >= 3) earned += "hills_3"
    if (tempos >= 5) earned += "tempo_5"
    if (lifts >= 20) earned += "lift_20"
    if (miles >= 100) earned += "miles_100"
    if (miles >= 250) earned += "miles_250"
    if (miles >= 500) earned += "miles_500"

    val badgeProgress = mapOf(
        "streak_7" to (best.toDouble() to 7.0),
        "streak_21" to (best.toDouble() to 21.0),
        "stretch_30" to (stretches.toDouble() to 30.0),
        "easy_10" to (easyInZone.toDouble() to 10.0),
        "hills_3" to (hills.toDouble() to 3.0),
        "tempo_5" to (tempos.toDouble() to 5.0),
        "lift_20" to (lifts.toDouble() to 20.0),
        "double_digits" to (longest to 10.0),
        "twenty" to (longest to 20.0),
        "miles_100" to (miles to 100.0),
        "miles_250" to (miles to 250.0),
        "miles_500" to (miles to 500.0),
        "perfect_10" to (perfect.toDouble() to 10.0),
        "miles_26" to (miles to 26.2),
        "long_15" to (longest to 15.0),
        "mp_5" to (mpLocked.toDouble() to 5.0),
        "easy_50" to (easyInZone.toDouble() to 50.0),
        "sunday_10" to (sundayLongs.toDouble() to 10.0),
        "streak_50" to (best.toDouble() to 50.0),
        "weeks_4" to (fullWeeks.toDouble() to 4.0),
        "stretch_100" to (stretches.toDouble() to 100.0),
        "rest_25" to (restStretches.toDouble() to 25.0),
        "lift_35" to (lifts.toDouble() to 35.0),
        "pr_5" to (prCount.toDouble() to 5.0),
        "level_5" to (level.level.toDouble() to 5.0),
        "level_10" to (level.level.toDouble() to 10.0),
    )
    return Progress(xp, level, streak, best, miles, longest, done, plannedSoFar, lifts, earned, perfect, fullWeeks, badgeProgress)
}

object Cheers {
    private val done = listOf(
        "Crushed it! 🎉", "Another brick in the wall. 🧱", "Eugene is getting closer! 🦆",
        "That's how marathoners are made. 💪", "Consistency wins. You're winning. 🏆",
        "Legs of steel, heart of gold. ✨", "Banked it! Future you says thanks. 🙌",
        "Boom. Done. 🔥", "Track Town is calling your name. 📣", "One more step to the finish line. 🏁",
    )
    private val rest = listOf(
        "Rest day honoured. Muscles grow when you rest. 😴", "Recovery is training too. 🛋️",
        "Stretch done — your quads thank you. 🧘",
    )
    private val missed = listOf(
        "Listening to your body is a skill. 💚", "One day doesn't define a training block. Back at it soon. 🌤️",
        "Smart call. The plan bends so you don't break. 🌱",
    )
    fun forDone(seed: Int) = done[Math.floorMod(seed, done.size)]
    fun forRest(seed: Int) = rest[Math.floorMod(seed, rest.size)]
    fun forMissed(seed: Int) = missed[Math.floorMod(seed, missed.size)]
}
