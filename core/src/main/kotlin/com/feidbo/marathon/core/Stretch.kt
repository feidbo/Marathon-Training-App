package com.feidbo.marathon.core

/** One timed hold in the daily stretch routine. */
data class StretchSegment(val exercise: Exercise, val side: Side, val seconds: Int)

/**
 * The daily stretch (docs/decisions.md §3): the plan's quad stretch plus two wall calf stretches.
 * Shown as optional mobility — stretching hasn't been shown to prevent injuries.
 *
 * Hold times: the plan's 90 s for the quad; 60 s for each calf stretch (an app default inside the
 * 60–90 s "long hold" range). Before a run every hold is kept under 60 s, because long static holds
 * right before effort slightly reduce performance (stretching P6, P7).
 */
object StretchRoutine {
    const val QUAD_SEC = 90
    const val CALF_SEC = 60
    const val QUAD_BEFORE_RUN_SEC = 45
    const val CALF_BEFORE_RUN_SEC = 30

    /** Calf stretches are paused while the calf guidance is in stages 1–2. */
    fun calfStretchesAllowed(calf: CalfStatus): Boolean = !calf.flagged || calf.currentStage.calfStretchesAllowed

    fun exercises(calfAllowed: Boolean): List<Exercise> =
        if (calfAllowed) Exercises.stretches else listOf(Exercises.quadStretch)

    fun segments(calfAllowed: Boolean, beforeRun: Boolean): List<StretchSegment> =
        exercises(calfAllowed).flatMap { ex ->
            val sec = when {
                ex.id == Exercises.quadStretch.id -> if (beforeRun) QUAD_BEFORE_RUN_SEC else QUAD_SEC
                else -> if (beforeRun) CALF_BEFORE_RUN_SEC else CALF_SEC
            }
            listOf(StretchSegment(ex, Side.LEFT, sec), StretchSegment(ex, Side.RIGHT, sec))
        }

    fun totalSeconds(segments: List<StretchSegment>): Int = segments.sumOf { it.seconds }

    /** The segment running after [elapsedSec] seconds, and the seconds left in it; null when finished. */
    fun at(segments: List<StretchSegment>, elapsedSec: Int): Pair<Int, Int>? {
        var start = 0
        segments.forEachIndexed { i, s ->
            if (elapsedSec < start + s.seconds) return i to (start + s.seconds - elapsedSec)
            start += s.seconds
        }
        return null
    }

    /** Seconds from the start at which each segment after the first begins (the "switch" buzzes). */
    fun boundaries(segments: List<StretchSegment>): List<Int> =
        segments.runningFold(0) { acc, s -> acc + s.seconds }.drop(1).dropLast(1)
}
