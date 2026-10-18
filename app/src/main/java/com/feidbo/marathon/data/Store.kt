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

class Store(context: Context) {
    private val file = File(context.filesDir, "training.json")

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
            val l = arr.getJSONObject(i)
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
                        feel = e.optString("feel").takeIf { it.isNotEmpty() }?.let { Feel.valueOf(it) },
                        rir = e.optIntOrNull("rir"),
                    )
                }
            } ?: emptyList()
            val day = l.getInt("day")
            logs[day] = DayLog(
                dayIndex = day,
                status = DayStatus.valueOf(l.getString("status")),
                run = run,
                strength = strength,
                missReason = l.optString("missReason").takeIf { it.isNotEmpty() }?.let { MissReason.valueOf(it) },
                xp = l.optInt("xp"),
                plannedMiles = if (l.has("plannedMiles")) l.getDouble("plannedMiles") else null,
            )
        }
        return AppData(logs, stretch, settings, o.optBoolean("onboarded"))
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
}
