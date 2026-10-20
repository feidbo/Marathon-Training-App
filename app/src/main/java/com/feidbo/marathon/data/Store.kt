package com.feidbo.marathon.data

import android.content.Context
import com.feidbo.marathon.core.DayLog
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.ExerciseLog
import com.feidbo.marathon.core.Feel
import com.feidbo.marathon.core.MissReason
import com.feidbo.marathon.core.RunLog
import com.feidbo.marathon.core.SetLog
import com.feidbo.marathon.core.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything the app remembers. Stored as one small JSON file on the device, so it all works offline. */
data class AppData(
    val logs: Map<Int, DayLog> = emptyMap(),
    val stretchDays: Set<Int> = emptySet(),
    val settings: Settings = Settings(),
    val onboarded: Boolean = false,
)

/** A strength session in progress: everything typed and ticked so far, saved as you go. */
data class StrengthDraft(val dayIndex: Int, val exercises: List<DraftExercise>)
data class DraftExercise(val id: String, val sets: List<DraftSet>, val rir: Int?, val skipped: Boolean)
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

    private fun encode(d: AppData) = JSONObject().apply {
        put("version", 1)
        put("onboarded", d.onboarded)
        put("settings", JSONObject().apply {
            put("marathonPaceSec", d.settings.marathonPaceSec)
            put("preplanLongestMiles", d.settings.preplanLongestMiles)
            d.settings.halfTimeSec?.let { put("halfTimeSec", it) }
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
                        })
                    }
                    put("strength", JSONArray().apply {
                        log.strength.forEach { e ->
                            put(JSONObject().apply {
                                put("id", e.exerciseId)
                                e.feel?.let { put("feel", it.name) }
                                e.rir?.let { put("rir", it) }
                                if (e.perSide) put("perSide", true)
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

    private fun decode(o: JSONObject): AppData {
        val s = o.optJSONObject("settings")
        val settings = if (s == null) Settings() else Settings(
            marathonPaceSec = s.optInt("marathonPaceSec", 618),
            preplanLongestMiles = s.optDouble("preplanLongestMiles", 3.5),
            halfTimeSec = if (s.has("halfTimeSec")) s.getInt("halfTimeSec") else null,
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
        return AppData(logs, stretch, settings, o.optBoolean("onboarded"))
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
