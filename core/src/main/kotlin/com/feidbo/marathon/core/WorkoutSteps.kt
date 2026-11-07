package com.feidbo.marathon.core

import kotlin.math.max

/**
 * Turns a plan day's workout text into numbered, watch-friendly steps (docs/decisions.md §6).
 * The plan document is the source of truth: every number comes from the day's text; only the effort
 * descriptions come from the plan's "Paces and effort" table.
 */
enum class StepKind { WARMUP, WORK, RECOVERY, COOLDOWN, EASY, NOTE }

data class WorkoutStep(val kind: StepKind, val title: String, val detail: String)

object WorkoutSteps {
    const val GARMIN_TIP = "On your Garmin: create a workout with these steps (Garmin Connect → Training → " +
        "Workouts), or start a normal run and press Lap at each step."

    private const val NUM = """(\d+(?:\.\d+)?)"""
    private val easyEffort = "Effort 3–4/10 — you can talk in full sentences."

    /** What each kind of session is for and how it should feel (from the plan's effort table). */
    fun purpose(day: PlannedDay): String? = when (day.run) {
        RunKind.EASY_STRIDES -> "Strides: short, quick, relaxed bursts (effort 8–9) with full recovery. They keep your legs " +
            "used to faster turnover without tiring you."
        RunKind.FARTLEK -> "Fartlek: alternating moderately hard and easy running, by feel. Hard but controlled."
        RunKind.HILLS -> "Hill repeats: hard uphill efforts (8/10, hard but controlled) with easy jogs back down."
        RunKind.TEMPO -> "Tempo (threshold): comfortably hard (7/10), roughly what you could race for an hour. " +
            "Start at the slower end of the range."
        RunKind.MARATHON_PACE -> "Marathon pace: steady (5–6/10), a few words at a time — the rhythm of race day."
        RunKind.SHAKEOUT -> "Shakeout: very easy, just to loosen up, plus a few relaxed strides."
        RunKind.LONG -> "Long run: easy the whole way (3–4/10). Practise fuelling if the day says so."
        RunKind.RECOVERY -> "Recovery: very easy, shorter than usual."
        else -> null
    }

    fun steps(day: PlannedDay, settings: Settings): List<WorkoutStep> {
        if (!day.run.isRun) return emptyList()
        val text = day.text.replace(STRENGTH_NOTE, "").trim()
        val total = day.miles
        val mp = "${formatPace(settings.marathonPaceSec)} /mi"
        val tempo = Zones.tempo.rangeText
        val mpDetail = "At $mp — effort 5–6/10, steady, a few words at a time."
        val tempoDetail = "At $tempo — effort 7/10, comfortably hard. Start at the slower end."
        fun mi(x: Double) = "${formatMiles(x)} mi"
        fun cooldownTo() = WorkoutStep(StepKind.COOLDOWN, "Cool-down", "Easy until you reach ${mi(total)} total.")
        fun warm(w: Double) = WorkoutStep(StepKind.WARMUP, "Warm-up ${mi(w)}", "Easy. $easyEffort")
        fun extra(rest: String): List<WorkoutStep> =
            rest.trim().trimStart(',', '.', ';').trim().takeIf { it.isNotEmpty() }
                ?.let { listOf(WorkoutStep(StepKind.NOTE, "Note", it.replaceFirstChar { c -> c.uppercase() })) }.orEmpty()

        Regex("""^$NUM mi (easy|shakeout) \+ (\d+) (?:× (\d+) s )?(uphill )?strides(.*)$""").find(text)?.let { m ->
            val (_, kind, n, sec, uphill, rest) = m.destructured
            val secText = if (sec.isNotEmpty()) "$sec s" else "15–20 s"
            return listOf(
                WorkoutStep(StepKind.EASY, "Run ${mi(total)} ${if (kind == "shakeout") "very easy" else "easy"}", easyEffort),
                WorkoutStep(StepKind.WORK, "Then $n × $secText ${if (uphill.isNotEmpty()) "uphill strides" else "strides"}",
                    "Quick and relaxed (8–9/10), not a sprint." +
                        (if (uphill.isNotEmpty()) " On a gentle hill; walk back down." else "") +
                        " Full recovery between — walk until you feel ready."),
            ) + extra(rest)
        }
        Regex("""^$NUM mi fartlek: $NUM mi easy, (\d+) × \((\d+) min moderately hard / (\d+) min easy\), easy to finish$""").find(text)?.let { m ->
            val (_, w, n, hard, easy) = m.destructured
            return listOf(
                warm(w.toDouble()),
                WorkoutStep(StepKind.WORK, "$n × $hard min moderately hard", "By feel — hard but controlled (about 8/10)."),
                WorkoutStep(StepKind.RECOVERY, "… each followed by $easy min easy", "Easy jog between the hard minutes."),
                cooldownTo(),
            )
        }
        Regex("""^$NUM mi: $NUM mi warm-up, (\d+) × (\d+) s uphill hard(?: \(8/10 effort\))?, jog down; cool down$""").find(text)?.let { m ->
            val (_, w, n, sec) = m.destructured
            return listOf(
                warm(w.toDouble()),
                WorkoutStep(StepKind.WORK, "$n × $sec s uphill, hard", "Effort 8/10 — hard but controlled. Find a steady hill."),
                WorkoutStep(StepKind.RECOVERY, "… jog back down after each", "Easy jog down to the start = your recovery."),
                cooldownTo(),
            )
        }
        Regex("""^$NUM mi: $NUM mi warm-up, (\d+) × (\d+) min tempo with (\d+) min jog; cool down$""").find(text)?.let { m ->
            val (_, w, n, dur, jog) = m.destructured
            return listOf(
                warm(w.toDouble()),
                WorkoutStep(StepKind.WORK, "$n × $dur min tempo", tempoDetail),
                WorkoutStep(StepKind.RECOVERY, "… with $jog min easy jog between", "Easy jog, don't stop."),
                cooldownTo(),
            )
        }
        Regex("""^$NUM mi: $NUM mi warm-up, (\d+) min continuous tempo; cool down$""").find(text)?.let { m ->
            val (_, w, dur) = m.destructured
            return listOf(warm(w.toDouble()), WorkoutStep(StepKind.WORK, "$dur min tempo, continuous", tempoDetail), cooldownTo())
        }
        Regex("""^$NUM mi: $NUM mi warm-up, (\d+) × $NUM mi at tempo with (\d+) (min|s) jog; cool down$""").find(text)?.let { m ->
            val (_, w, n, d, jog, unit) = m.destructured
            return listOf(
                warm(w.toDouble()),
                WorkoutStep(StepKind.WORK, "$n × ${mi(d.toDouble())} tempo", tempoDetail),
                WorkoutStep(StepKind.RECOVERY, "… with $jog $unit easy jog between", "Easy jog, don't stop."),
                cooldownTo(),
            )
        }
        Regex("""^$NUM mi: $NUM mi (warm-up|easy), $NUM mi at marathon pace(?: \(see Paces\))?, (?:$NUM mi )?(cool down|easy)$""").find(text)?.let { m ->
            val (_, w, _, mpMi, cool) = m.destructured
            val warmMi = w.toDouble()
            val cd = cool.toDoubleOrNull() ?: max(0.0, total - warmMi - mpMi.toDouble())
            return listOf(
                warm(warmMi),
                WorkoutStep(StepKind.WORK, "${mi(mpMi.toDouble())} at marathon pace", mpDetail),
                WorkoutStep(StepKind.COOLDOWN, "Cool-down ${mi(cd)}", "Easy, to reach ${mi(total)} total."),
            )
        }
        Regex("""^$NUM mi long run: $NUM easy, last $NUM at marathon pace(.*)$""").find(text)?.let { m ->
            val (_, _, mpMi, rest) = m.destructured
            val easyMi = max(0.0, total - mpMi.toDouble())
            return listOf(
                WorkoutStep(StepKind.EASY, "${mi(easyMi)} easy", easyEffort),
                WorkoutStep(StepKind.WORK, "Last ${mi(mpMi.toDouble())} at marathon pace", mpDetail),
            ) + extra(rest)
        }
        Regex("""^$NUM mi dress rehearsal: $NUM easy, $NUM at marathon pace, $NUM easy\.?(.*)$""").find(text)?.let { m ->
            val (_, a, b, c, rest) = m.destructured
            return listOf(
                WorkoutStep(StepKind.WARMUP, "${mi(a.toDouble())} easy", easyEffort),
                WorkoutStep(StepKind.WORK, "${mi(b.toDouble())} at marathon pace", mpDetail),
                WorkoutStep(StepKind.COOLDOWN, "${mi(c.toDouble())} easy", "Easy to finish."),
            ) + extra(rest)
        }
        if (day.run == RunKind.HALF_RACE) {
            return listOf(
                WorkoutStep(StepKind.WARMUP, "Warm-up about 1 mi", "Easy jogging."),
                WorkoutStep(StepKind.WORK, "Half marathon, 13.1 mi", "Race it evenly — your time sets your marathon pace."),
                WorkoutStep(StepKind.COOLDOWN, "Cool-down about 1.4 mi", "Easy jogging."),
            )
        }
        if (day.run == RunKind.RACE) {
            return listOf(
                WorkoutStep(StepKind.WORK, "Miles 1–3 a little slower than goal", "Start conservatively; crowds make early miles feel easy."),
                WorkoutStep(StepKind.WORK, "Miles 4–26.2 at goal pace ($mp)", "Steady. Gel every 20–25 min, as practised."),
            )
        }
        Regex("""^$NUM mi (easy|recovery)(.*)$""").find(text)?.let { m ->
            val (_, kind, rest) = m.destructured
            val title = "Run ${mi(total)} ${if (kind == "recovery") "very easy (recovery)" else "easy"}"
            val note = rest.trim().removePrefix("(long run)").removePrefix("(peak long run)").removePrefix("(last long run)").trim()
            return listOf(WorkoutStep(StepKind.EASY, title, easyEffort)) + extra(note)
        }
        return listOf(WorkoutStep(StepKind.NOTE, "Workout", text))
    }
}
