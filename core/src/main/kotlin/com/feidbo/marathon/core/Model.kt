package com.feidbo.marathon.core

import java.time.LocalDate

/** The type of run planned for a day. Drives the pace target and the feedback rules. */
enum class RunKind(val title: String, val isKey: Boolean = false) {
    NONE("No run"),
    REST("Rest day"),
    EASY("Easy run"),
    EASY_STRIDES("Easy run + strides"),
    RECOVERY("Recovery run"),
    SHAKEOUT("Shakeout run"),
    LONG("Long run"),
    FARTLEK("Fartlek", isKey = true),
    HILLS("Hill repeats", isKey = true),
    TEMPO("Tempo run", isKey = true),
    MARATHON_PACE("Marathon-pace run", isKey = true),
    HALF_RACE("Half marathon", isKey = true),
    RACE("Eugene Marathon", isKey = true);

    val isRun: Boolean get() = this != NONE && this != REST
    val isEasyType: Boolean get() = this in setOf(EASY, EASY_STRIDES, RECOVERY, SHAKEOUT, LONG)
}

/** Which strength prescription applies to a day. */
enum class StrengthKind(val title: String) {
    NONE(""),
    LEARN("Strength · learn the machines"),
    PHASE1("Strength · 15–20 min"),
    PHASE2("Strength · 20–25 min"),
    PHASE3("Strength · 15 min, heavy"),
    LIGHT("Strength · light, 10 min");

    val isPlanned: Boolean get() = this != NONE
}

enum class Phase(val number: Int, val title: String, val focus: String) {
    REBUILD(1, "Rebuild", "Easy running, strides, learning the machines"),
    BASE(2, "Strength & base", "Heavier lifting, hills, tempo"),
    SPECIFIC(3, "Marathon-specific", "Marathon pace, long runs to 20 mi"),
    TAPER(4, "Taper & race", "Less volume, same intensity"),
}

/** One day of the plan exactly as written in the plan document. */
data class PlannedDay(
    val index: Int,            // 0-based position in the plan
    val date: LocalDate,
    val week: Int,             // plan week, 0..27
    val label: String,         // "Day 4" or "Race"
    val text: String,          // workout text from the plan, without the stretch note
    val miles: Double,
    val run: RunKind,
    val strength: StrengthKind,
    val mpMiles: Double = 0.0, // miles at marathon pace within the run
    val weekNote: String = "",
) {
    val phase: Phase get() = phaseFor(date)
    val isTrainingDay: Boolean get() = run.isRun || strength.isPlanned
}

fun phaseFor(date: LocalDate): Phase = when {
    date < LocalDate.of(2026, 12, 14) -> Phase.REBUILD
    date < LocalDate.of(2027, 2, 8) -> Phase.BASE
    date < LocalDate.of(2027, 4, 5) -> Phase.SPECIFIC
    else -> Phase.TAPER
}

enum class DayStatus { DONE, PARTIAL, MISSED }

enum class MissReason(val label: String) {
    SICK_MILD("Sick: head cold only (above the neck)"),
    SICK_SYSTEMIC("Sick: fever, chest or body aches"),
    PAIN("Pain or injury"),
    TIRED("Worn out / overly tired"),
    BUSY("No time / life got in the way"),
    OTHER("Something else"),
}

/** How a strength exercise felt compared with the target effort. */
enum class Feel(val label: String) {
    EASY("Easy"),
    RIGHT("Just right"),
    HARD("Too hard"),
}

data class RunLog(
    val distance: Double,
    val paceSec: Int? = null,         // average pace, seconds per mile
    val segmentPaceSec: Int? = null,  // pace of the tempo / marathon-pace portion
    val effort: Int? = null,          // 1–10
    val pain: Boolean = false,
    val raceTimeSec: Int? = null,     // finish time on race days
)

data class SetLog(val weight: Double, val reps: Int)

data class ExerciseLog(
    val exerciseId: String,
    val sets: List<SetLog>,
    val feel: Feel? = null,
) {
    val topWeight: Double get() = sets.maxOfOrNull { it.weight } ?: 0.0
}

data class DayLog(
    val dayIndex: Int,
    val status: DayStatus,
    val run: RunLog? = null,
    val strength: List<ExerciseLog> = emptyList(),
    val missReason: MissReason? = null,
    val xp: Int = 0,
)

data class Settings(
    val marathonPaceSec: Int = 618,      // 10:18 per mile, for a 4:30 finish
    val preplanLongestMiles: Double = 3.5,
    val halfTimeSec: Int? = null,
)
