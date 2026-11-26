package com.feidbo.marathon.data

import android.content.Context
import com.feidbo.marathon.core.CalfStatus
import com.feidbo.marathon.core.CalfTest
import com.feidbo.marathon.core.DayLog
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.ExerciseLog
import com.feidbo.marathon.core.Feel
import com.feidbo.marathon.core.MealSlot
import com.feidbo.marathon.core.MissReason
import com.feidbo.marathon.core.NutritionProfile
import com.feidbo.marathon.core.RunLog
import com.feidbo.marathon.core.SetLog
import com.feidbo.marathon.core.Settings
import com.feidbo.marathon.core.Side
import com.feidbo.marathon.core.WeighIn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything the app remembers. Stored as one small JSON file on the device, so it all works offline. */
data class AppData(
    val logs: Map<Int, DayLog> = emptyMap(),
    val stretchDays: Set<Int> = emptySet(),
    val settings: Settings = Settings(),
    val onboarded: Boolean = false,
    /** Calf guidance (docs/decisions.md §4). */
    val calf: CalfStatus = CalfStatus(),
    val calfTests: List<CalfTest> = emptyList(),
    /** Days the calf-guidance exercises were done. */
    val calfDays: Set<Int> = emptySet(),
    /** The newest "What's new" note already shown. */
    val whatsNewSeen: Int = 0,
    /** Meal plan (docs/evidence/nutrition.md). */
    val nutrition: NutritionProfile = NutritionProfile(),
    /** Day → what's been ticked: meal-slot names and "fuel1", "fuel2", … */
    val mealChecks: Map<Int, Set<String>> = emptyMap(),
    /** Plan week → shopping-list items ticked (food ids). */
    val shopChecks: Map<Int, Set<String>> = emptyMap(),
    val weighIns: List<WeighIn> = emptyList(),
    /** Strength sessions moved to the next day: from-day index → to-day index. */
    val strengthMoves: Map<Int, Int> = emptyMap(),
    /** Days whose whole workout swapped with the rest day after them. */
    val daySwaps: Set<Int> = emptySet(),
) {
    /** The plan as the runner will actually do it, with swaps and moved strength sessions. */
    val planDays: List<com.feidbo.marathon.core.PlannedDay>
        get() = com.feidbo.marathon.core.Plan.withStrengthMoves(strengthMoves, daySwaps)
    /** Days with all four meals ticked. */
    val mealDays: Set<Int> get() = mealChecks.filterValues { c -> MealSlot.entries.all { it.name in c } }.keys
}

/** A strength session in progress: everything typed and ticked so far, saved as you go. */
data class StrengthDraft(val dayIndex: Int, val exercises: List<DraftExercise>)
data class DraftExercise(
    val id: String,
    val sets: List<DraftSet>,
    val rir: Int?,
    val skipped: Boolean,
    val bodyweight: Boolean = false,
)
data class DraftSet(val weight: String, val reps: String, val done: Boolean)

class Store(context: Context) {
    private val file = File(context.filesDir, "training.json")
    private val draftFile = File(context.filesDir, "strength_draft.json")

    fun loadDraft(): StrengthDraft? = try {
        if (!draftFile.exists()) null else JSONObject(draftFile.readText()).let { o ->
            val ex = o.getJSONArray("exercises")
            StrengthDraft(o.getInt("day"), (0 until ex.length()).map { i ->
                val e = ex.getJSONObject(i)
                val sets = e.getJSONArray("sets")
                DraftExercise(
                    e.getString("id"),
                    (0 until sets.length()).map { j ->
                        val s = sets.getJSONObject(j)
                        DraftSet(s.optString("w"), s.optString("r"), s.optBoolean("done"))
                    },
                    if (e.has("rir")) e.getInt("rir") else null,
                    e.optBoolean("skipped"),
                    e.optBoolean("bw"),
                )
            })
        }
    } catch (e: Exception) {
        null
    }

    fun saveDraft(d: StrengthDraft) {
        val o = JSONObject().put("day", d.dayIndex).put("exercises", JSONArray().apply {
            d.exercises.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    e.rir?.let { put("rir", it) }
                    put("skipped", e.skipped)
                    if (e.bodyweight) put("bw", true)
                    put("sets", JSONArray().apply {
                        e.sets.forEach { s -> put(JSONObject().put("w", s.weight).put("r", s.reps).put("done", s.done)) }
                    })
                })
            }
        })
        val tmp = File(draftFile.parentFile, "strength_draft.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(draftFile)
    }

    fun clearDraft() {
        draftFile.delete()
    }

    fun load(): AppData = try {
        if (file.exists()) decode(JSONObject(file.readText())) else AppData()
    } catch (e: Exception) {
        // Keep a copy of anything we couldn't read rather than silently losing it.
        file.copyTo(File(file.parentFile, "training.corrupt.json"), overwrite = true)
        AppData()
    }

    fun save(data: AppData) {
        val tmp = File(file.parentFile, "training.json.tmp")
        tmp.writeText(encode(data).toString())
        tmp.renameTo(file)
    }

    companion object Codec {
        /** JSON for [d]; also used by the tests that check old files still load. */
        internal fun encode(d: AppData) = JSONObject().apply {
            put("version", 1)
            put("onboarded", d.onboarded)
            put("settings", JSONObject().apply {
                put("marathonPaceSec", d.settings.marathonPaceSec)
                put("preplanLongestMiles", d.settings.preplanLongestMiles)
                d.settings.halfTimeSec?.let { put("halfTimeSec", it) }
                d.settings.easyHrCap?.let { put("easyHrCap", it) }
            })
            if (d.whatsNewSeen > 0) put("whatsNewSeen", d.whatsNewSeen)
            put("calf", JSONObject().apply {
                put("flagged", d.calf.flagged)
                put("stage", d.calf.stage)
                put("side", d.calf.side.name)
                d.calf.sinceDay?.let { put("since", it) }
            })
            put("calfDays", JSONArray(d.calfDays.sorted()))
        put("nutrition", JSONObject().apply {
            val n = d.nutrition
            put("enabled", n.enabled); put("weightLb", n.weightLb); put("heightIn", n.heightIn); put("age", n.age)
            put("male", n.male); put("bodyFatPct", n.bodyFatPct); put("activityFactor", n.activityFactor); put("deficitPct", n.deficitPct)
        })
        fun checks(m: Map<Int, Set<String>>) = JSONObject().apply { m.forEach { (k, v) -> if (v.isNotEmpty()) put(k.toString(), JSONArray(v.sorted())) } }
        put("mealChecks", checks(d.mealChecks))
        put("shopChecks", checks(d.shopChecks))
        put("daySwaps", JSONArray(d.daySwaps.sorted()))
        put("strengthMoves", JSONObject().apply { d.strengthMoves.forEach { (k, v) -> put(k.toString(), v) } })
        put("weighIns", JSONArray().apply { d.weighIns.forEach { put(JSONObject().put("day", it.dayIndex).put("lb", it.lb)) } })
            put("calfTests", JSONArray().apply {
                d.calfTests.forEach { t ->
                    put(JSONObject().apply {
                        put("day", t.dayIndex)
                        t.soreReps?.let { put("sore", it) }
                        t.otherReps?.let { put("other", it) }
                        t.hops?.let { put("hops", it) }
                        t.hopsSymptomFree?.let { put("hopsOk", it) }
                    })
                }
            })
            put("stretch", JSONArray(d.stretchDays.sorted()))
            put("logs", JSONArray().apply {
                d.logs.values.sortedBy { it.dayIndex }.forEach { log ->
                    put(JSONObject().apply {
                        put("day", log.dayIndex)
                        put("status", log.status.name)
                        put("xp", log.xp)
                        log.missReason?.let { put("missReason", it.name) }
                        log.plannedMiles?.let { put("plannedMiles", it) }
                        log.run?.let { r ->
                            put("run", JSONObject().apply {
                                put("distance", r.distance)
                                r.paceSec?.let { put("pace", it) }
                                r.segmentPaceSec?.let { put("segmentPace", it) }
                                r.effort?.let { put("effort", it) }
                                put("pain", r.pain)
                                r.raceTimeSec?.let { put("raceTime", it) }
                                r.avgHr?.let { put("avgHr", it) }
                            })
                        }
                        put("strength", JSONArray().apply {
                            log.strength.forEach { e ->
                                put(JSONObject().apply {
                                    put("id", e.exerciseId)
                                    e.feel?.let { put("feel", it.name) }
                                    e.rir?.let { put("rir", it) }
                                    if (e.perSide) put("perSide", true)
                                    if (e.bodyweight) put("bw", true)
                                    put("sets", JSONArray().apply {
                                        e.sets.forEach { s -> put(JSONObject().put("w", s.weight).put("r", s.reps)) }
                                    })
                                })
                            }
                        })
                    })
                }
            })
        }

        internal fun decode(o: JSONObject): AppData {
            val s = o.optJSONObject("settings")
            val settings = if (s == null) Settings() else Settings(
                marathonPaceSec = s.optInt("marathonPaceSec", 618),
                preplanLongestMiles = s.optDouble("preplanLongestMiles", 3.5),
                halfTimeSec = s.optIntOrNull("halfTimeSec"),
                easyHrCap = s.optIntOrNull("easyHrCap"),
            )
            val stretch = o.optJSONArray("stretch")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
            val logs = mutableMapOf<Int, DayLog>()
            val arr = o.optJSONArray("logs") ?: JSONArray()
            for (i in 0 until arr.length()) {
                // One unreadable entry is skipped rather than losing the whole history.
                val l = arr.optJSONObject(i) ?: continue
                val status = enumOrNull<DayStatus>(l.optString("status")) ?: continue
                try {
                    val run = l.optJSONObject("run")?.let { r ->
                        RunLog(
                            distance = r.getDouble("distance"),
                            paceSec = r.optIntOrNull("pace"),
                            segmentPaceSec = r.optIntOrNull("segmentPace"),
                            effort = r.optIntOrNull("effort"),
                            pain = r.optBoolean("pain"),
                            raceTimeSec = r.optIntOrNull("raceTime"),
                            avgHr = r.optIntOrNull("avgHr"),
                        )
                    }
                    val strength = l.optJSONArray("strength")?.let { a ->
                        (0 until a.length()).map { j ->
                            val e = a.getJSONObject(j)
                            val sets = e.getJSONArray("sets")
                            ExerciseLog(
                                exerciseId = e.getString("id"),
                                sets = (0 until sets.length()).map { k ->
                                    val st = sets.getJSONObject(k)
                                    SetLog(st.getDouble("w"), st.getInt("r"))
                                },
                                feel = enumOrNull<Feel>(e.optString("feel")),
                                rir = e.optIntOrNull("rir"),
                                perSide = e.optBoolean("perSide"),
                                bodyweight = e.optBoolean("bw"),
                            )
                        }
                    } ?: emptyList()
                    val day = l.getInt("day")
                    logs[day] = DayLog(
                        dayIndex = day,
                        status = status,
                        run = run,
                        strength = strength,
                        missReason = enumOrNull<MissReason>(l.optString("missReason")),
                        xp = l.optInt("xp"),
                        plannedMiles = if (l.has("plannedMiles")) l.getDouble("plannedMiles") else null,
                    )
                } catch (_: Exception) {
                    continue
                }
            }
            // Calf data is optional: files saved before this update simply don't have it.
            val calf = try {
                o.optJSONObject("calf")?.let { c ->
                    CalfStatus(
                        flagged = c.optBoolean("flagged"),
                        stage = c.optInt("stage", 1).coerceIn(1, 4),
                        side = enumOrNull<Side>(c.optString("side")) ?: Side.RIGHT,
                        sinceDay = c.optIntOrNull("since"),
                    )
                } ?: CalfStatus()
            } catch (_: Exception) {
                CalfStatus()
            }
            val calfDays = o.optJSONArray("calfDays")?.let { a -> (0 until a.length()).mapNotNull { a.optInt(it, -1).takeIf { d -> d >= 0 } }.toSet() }
                ?: emptySet()
            val calfTests = o.optJSONArray("calfTests")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val t = a.optJSONObject(i) ?: return@mapNotNull null
                    try {
                        CalfTest(
                            dayIndex = t.getInt("day"),
                            soreReps = t.optIntOrNull("sore"),
                            otherReps = t.optIntOrNull("other"),
                            hops = t.optIntOrNull("hops"),
                            hopsSymptomFree = if (t.has("hopsOk")) t.getBoolean("hopsOk") else null,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            } ?: emptyList()
            // Meal-plan data is optional too.
        val nutrition = try {
            o.optJSONObject("nutrition")?.let { n ->
                val d = NutritionProfile()
                NutritionProfile(
                    enabled = n.optBoolean("enabled"),
                    weightLb = n.optDouble("weightLb", d.weightLb), heightIn = n.optDouble("heightIn", d.heightIn),
                    age = n.optInt("age", d.age), male = n.optBoolean("male", d.male),
                    bodyFatPct = n.optDouble("bodyFatPct", d.bodyFatPct),
                    activityFactor = n.optDouble("activityFactor", d.activityFactor), deficitPct = n.optInt("deficitPct", d.deficitPct),
                )
            } ?: NutritionProfile()
        } catch (_: Exception) {
            NutritionProfile()
        }
        fun checks(key: String): Map<Int, Set<String>> = try {
            o.optJSONObject(key)?.let { m ->
                m.keys().asSequence().mapNotNull { k ->
                    val a = m.optJSONArray(k) ?: return@mapNotNull null
                    (k.toIntOrNull() ?: return@mapNotNull null) to (0 until a.length()).map { a.getString(it) }.toSet()
                }.toMap()
            } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
        val weighIns = o.optJSONArray("weighIns")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val w = a.optJSONObject(i) ?: return@mapNotNull null
                try { WeighIn(w.getInt("day"), w.getDouble("lb")) } catch (_: Exception) { null }
            }
        } ?: emptyList()
        val moves = try {
            o.optJSONObject("strengthMoves")?.let { m ->
                m.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to m.getInt(k) } }.toMap()
            } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
        return AppData(logs, stretch, settings, o.optBoolean("onboarded"), calf, calfTests, calfDays, o.optInt("whatsNewSeen"),
            nutrition, checks("mealChecks"), checks("shopChecks"), weighIns, moves,
            o.optJSONArray("daySwaps")?.let { a -> (0 until a.length()).mapNotNull { a.optInt(it, -1).takeIf { d -> d >= 0 } }.toSet() } ?: emptySet())
        }

        private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null

        private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
            enumValues<T>().firstOrNull { it.name == name }
    }
}
