package com.feidbo.marathon.core

import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pace zones from the plan's "Paces and effort" table. Paces are seconds per mile. */
object Paces {
    const val EASY_FAST = 11 * 60 + 45   // 11:45
    const val EASY_SLOW = 13 * 60        // 13:00
    const val TEMPO_FAST = 9 * 60 + 30   // 9:30 at 2:10 half fitness
    const val TEMPO_SLOW = 10 * 60 + 40  // 10:40 at 2:20 half fitness
    const val DEFAULT_MP = 10 * 60 + 18  // 10:18 for a 4:30 marathon

    /** Riegel: T2 = T1 × (D2 / D1)^1.06. */
    fun riegelMarathonSec(halfSec: Int): Int = (halfSec * 2.0.pow(1.06)).roundToInt()

    /** Marathon pace (s/mi) for a marathon finish time. */
    fun paceForMarathon(marathonSec: Int): Int = (marathonSec / 26.2188).roundToInt()

    /**
     * Suggested marathon pace from the Mar 7 half: the plan says pick between Riegel and Riegel + 10 min.
     * We suggest the midpoint, which the user can override.
     */
    fun suggestedMpFromHalf(halfSec: Int): Int {
        val riegel = riegelMarathonSec(halfSec)
        return paceForMarathon(riegel + 5 * 60)
    }
}

data class PaceTarget(
    val zone: String,
    val fastSec: Int?,     // null = run by effort
    val slowSec: Int?,
    val effort: String,
    val feelsLike: String,
) {
    val rangeText: String
        get() = when {
            fastSec == null || slowSec == null -> "By effort"
            fastSec == slowSec -> "${formatPace(fastSec)} /mi"
            else -> "${formatPace(fastSec)}–${formatPace(slowSec)} /mi"
        }
}

object Zones {
    val easy = PaceTarget("Easy", Paces.EASY_FAST, Paces.EASY_SLOW, "3–4 / 10", "Full sentences")
    fun marathon(mp: Int) = PaceTarget("Marathon pace", mp, mp, "5–6 / 10", "Steady, a few words at a time")
    val tempo = PaceTarget("Tempo", Paces.TEMPO_FAST, Paces.TEMPO_SLOW, "7 / 10", "Comfortably hard; start at the slower end")
    val hills = PaceTarget("Hills", null, null, "8 / 10", "Hard but controlled")
    val fartlek = PaceTarget("Fartlek", null, null, "8 / 10 on the fast bits", "Hard but controlled, easy between")
    val strides = PaceTarget("Strides", null, null, "8–9 / 10", "15–20 s quick and relaxed, full recovery")
    fun race(mp: Int) = PaceTarget("Race", mp, mp, "Goal pace", "Start conservatively")
    fun half(mp: Int) = PaceTarget("Half marathon", null, null, "Race effort", "Run it hard but even — it sets your marathon pace")
}

/** The headline target for a day's run, plus an optional second zone for the quality portion. */
fun primaryTarget(day: PlannedDay, mp: Int): PaceTarget? = when (day.run) {
    RunKind.NONE, RunKind.REST -> null
    RunKind.EASY, RunKind.RECOVERY, RunKind.SHAKEOUT, RunKind.LONG, RunKind.EASY_STRIDES -> Zones.easy
    RunKind.MARATHON_PACE -> Zones.marathon(mp)
    RunKind.TEMPO -> Zones.tempo
    RunKind.HILLS -> Zones.hills
    RunKind.FARTLEK -> Zones.fartlek
    RunKind.HALF_RACE -> Zones.half(mp)
    RunKind.RACE -> Zones.race(mp)
}

/** A friendlier title than the bare run kind. */
fun workoutTitle(day: PlannedDay): String = when {
    day.run == RunKind.MARATHON_PACE && day.text.contains("dress rehearsal", ignoreCase = true) -> "Dress rehearsal"
    day.run == RunKind.MARATHON_PACE && day.miles >= 12 -> "Long run, marathon-pace finish"
    day.run == RunKind.LONG && day.miles >= 20 -> "Peak long run"
    day.run == RunKind.NONE && day.strength.isPlanned -> "Strength day"
    day.run == RunKind.REST && day.text.contains("walk") -> "Rest (or an easy walk)"
    else -> day.run.title
}

fun formatPace(sec: Int): String = fmt("%d:%02d", sec / 60, sec % 60)

fun formatDuration(sec: Int): String =
    if (sec >= 3600) fmt("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
    else fmt("%d:%02d", sec / 60, sec % 60)

/** Parses "10:18" or "618" (seconds) into seconds. Returns null if it can't. */
fun parsePace(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val parts = t.split(":")
    return try {
        when (parts.size) {
            1 -> t.toDouble().let { if (it < 30) (it * 60).roundToInt() else it.roundToInt() } // "10.5" min or seconds
            2 -> parts[0].toInt() * 60 + parts[1].toInt().also { require(it in 0..59) }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/** Parses "2:09:30" or "129:30" into seconds. */
fun parseDuration(text: String): Int? {
    val parts = text.trim().split(":")
    return try {
        when (parts.size) {
            3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
            2 -> parts[0].toInt() * 60 + parts[1].toInt()
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

fun formatMiles(mi: Double): String =
    if (mi == mi.toLong().toDouble()) mi.toLong().toString() else fmt("%.2f", mi).trimEnd('0').trimEnd('.')

fun fmt(pattern: String, vararg args: Any): String = String.format(Locale.US, pattern, *args)
