package com.feidbo.marathon.core

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Calf guidance (docs/decisions.md §4, evidence in docs/evidence/calf.md). It guides; it never changes the
 * schedule by itself. Stages and the readiness tests come from expert consensus and narrative reviews
 * (Green 2022; Halabchi 2024; Pagan-Rosado 2025). Exact holds/sets are app defaults and labelled as such.
 */
enum class Side(val label: String) { LEFT("Left"), RIGHT("Right") }

/** A calf exercise the guidance prescribes for a stage. */
data class CalfExercise(val exercise: Exercise, val dose: String, val note: String = "")

enum class CalfStage(
    val number: Int,
    val title: String,
    val goal: String,
    val moveOnWhen: String,
) {
    SETTLE(
        1, "Settle",
        "Calm it down while keeping the calf working.",
        "Walking is pain-free and the holds don't hurt.",
    ),
    REBUILD(
        2, "Rebuild",
        "Get single-leg calf raises back, small range to full range, knee straight and bent.",
        "Full-range single-leg calf raises are pain-free.",
    ),
    STRENGTHEN(
        3, "Strengthen",
        "Load the calf (dumbbell raises), bring stretching back, and start hopping.",
        "20 single-leg hops on the sore side without symptoms.",
    ),
    RETURN(
        4, "Return",
        "Prove capacity, then clear the calf.",
        "At least 30 single-leg calf raises on each leg, within 10% of each other.",
    );

    val exercises: List<CalfExercise>
        get() = when (this) {
            SETTLE -> listOf(
                CalfExercise(Exercises.calfIsometric, "5 × 30 s holds each leg, 2–3 times a day",
                    "App default — the sources give the stage, not the numbers."),
            )
            REBUILD -> listOf(
                CalfExercise(Exercises.calfRaiseStraight, "3 × 10–15 each leg (bodyweight), daily",
                    "Start with a small range; go to full range as soon as it's comfortable. App default."),
                CalfExercise(Exercises.calfRaiseBent, "3 × 10–15 each leg (bodyweight), daily", "App default."),
            )
            STRENGTHEN -> listOf(
                CalfExercise(Exercises.calfRaiseStraight, "With a dumbbell on strength days; 3 × 15 bodyweight on other days", "App default."),
                CalfExercise(Exercises.calfRaiseBent, "With a dumbbell on strength days; 3 × 15 bodyweight on other days", "App default."),
                CalfExercise(Exercises.pogoHops, "3 × 10 pogo hops, then single-leg hops", "App default."),
            )
            RETURN -> listOf(
                CalfExercise(Exercises.calfRaiseStraight, "Keep loading with the strength routine"),
            )
        }

    /** Wall calf stretches are paused early on (Halabchi 2024: no stretching in the first phase). */
    val calfStretchesAllowed: Boolean get() = number >= 3

    companion object {
        fun of(n: Int): CalfStage = entries.firstOrNull { it.number == n } ?: SETTLE
    }
}

data class CalfStatus(
    val flagged: Boolean = false,
    val stage: Int = 1,
    val side: Side = Side.RIGHT,
    /** Plan day the calf was flagged (for "days since"). */
    val sinceDay: Int? = null,
) {
    val currentStage: CalfStage get() = CalfStage.of(stage)
}

/** One calf test: single-leg calf raises to fatigue on each leg, and/or single-leg hops on the sore side. */
data class CalfTest(
    val dayIndex: Int,
    val soreReps: Int? = null,
    val otherReps: Int? = null,
    val hops: Int? = null,
    val hopsSymptomFree: Boolean? = null,
)

object CalfGuide {
    /** Return-to-play benchmark from the expert checklist (Green 2022, Table 4). */
    const val TARGET_RAISES = 30
    const val MAX_ASYMMETRY = 0.10
    /** One expert's readiness-to-run rule (Green 2022). */
    const val TARGET_HOPS = 20

    /** How much weaker the sore side is, as a fraction of the other side (0.0 = equal). */
    fun asymmetry(t: CalfTest): Double? {
        val sore = t.soreReps ?: return null
        val other = t.otherReps ?: return null
        if (other <= 0) return null
        return max(0, other - sore).toDouble() / other
    }

    fun raisesPassed(t: CalfTest): Boolean {
        val a = asymmetry(t) ?: return false
        return (t.soreReps ?: 0) >= TARGET_RAISES && (t.otherReps ?: 0) >= TARGET_RAISES && a <= MAX_ASYMMETRY
    }

    fun hopsPassed(t: CalfTest): Boolean = (t.hops ?: 0) >= TARGET_HOPS && t.hopsSymptomFree == true

    fun describe(t: CalfTest): String = buildList {
        if (t.soreReps != null && t.otherReps != null) {
            val a = asymmetry(t)
            add("Calf raises: sore ${t.soreReps}, other ${t.otherReps}" +
                (a?.let { " (${(it * 100).roundToInt()}% weaker)" } ?: ""))
        }
        if (t.hops != null) add("Hops: ${t.hops}${if (t.hopsSymptomFree == true) ", no symptoms" else if (t.hopsSymptomFree == false) ", with symptoms" else ""}")
    }.joinToString(" · ")

    /** Expert running rules after a calf strain (Green 2022 "rules of thumb"). */
    val runningRules = listOf(
        "Run on alternate days at first.",
        "Don't increase distance and pace on back-to-back days.",
        "Do strength work after running, not before.",
        "Avoid long, slow runs early on — shorter runs first.",
        "Avoid sudden changes in surface or shoes.",
    )

    /** Early-stage advice from the reviews (Halabchi 2024; Pagan-Rosado 2025). */
    val earlyAdvice = listOf(
        "No calf stretching yet, and no hard foam rolling on the sore spot.",
        "Keep the calf working with the holds — experts start loading early (Green 2022).",
    )

    /** Plyometrics join the strength routine only once the calf is cleared or in its final stage. */
    fun plyometricsAllowed(s: CalfStatus): Boolean = !s.flagged || s.stage >= 4

    /** The routine's own calf raises are replaced by the calf card's exercises in stages 1–2. */
    fun skipRoutineCalfRaises(s: CalfStatus): Boolean = s.flagged && s.stage <= 2
}
