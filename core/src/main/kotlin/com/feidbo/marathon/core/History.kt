package com.feidbo.marathon.core

/** Whether a logged day counts as fully done or partly done. */
fun completionStatus(day: PlannedDay, run: RunLog?, strength: List<ExerciseLog>): DayStatus {
    if (!day.isTrainingDay) return DayStatus.DONE
    val ranEnough = !day.run.isRun || (run != null && run.distance >= day.miles * 0.9)
    val lifted = !day.strength.isPlanned || strength.count { e -> e.sets.any { it.reps > 0 } } >= 4
    return if (ranEnough && lifted) DayStatus.DONE else DayStatus.PARTIAL
}

data class ExerciseHistoryEntry(val dayIndex: Int, val strength: StrengthKind, val log: ExerciseLog)

/** Every logged set of an exercise, oldest first. */
fun exerciseHistory(exerciseId: String, logs: Map<Int, DayLog>): List<ExerciseHistoryEntry> =
    logs.values.sortedBy { it.dayIndex }.mapNotNull { dl ->
        val e = dl.strength.firstOrNull { it.exerciseId == exerciseId && it.sets.any { s -> s.reps > 0 } }
            ?: return@mapNotNull null
        ExerciseHistoryEntry(dl.dayIndex, Plan.days.getOrNull(dl.dayIndex)?.strength ?: StrengthKind.NONE, e)
    }

/**
 * The benchmark for an exercise: the most recent working (non-light) session before [beforeIndex].
 * Light and learning sessions are skipped so they don't drag the benchmark down.
 */
fun lastWorkingSet(exerciseId: String, beforeIndex: Int, logs: Map<Int, DayLog>): ExerciseHistoryEntry? {
    val history = exerciseHistory(exerciseId, logs).filter { it.dayIndex < beforeIndex }
    return history.lastOrNull { it.strength != StrengthKind.LIGHT && it.strength != StrengthKind.LEARN }
        ?: history.lastOrNull()
}
