package com.feidbo.marathon

import com.feidbo.marathon.core.CalfStatus
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.Feel
import com.feidbo.marathon.core.MissReason
import com.feidbo.marathon.core.Schedule
import com.feidbo.marathon.core.exerciseHistory
import com.feidbo.marathon.data.AppData
import com.feidbo.marathon.data.Store
import com.feidbo.marathon.timer.Timers
import com.feidbo.marathon.ui.AppViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A save file written by the previous release (machine routine, no heart rate, no calf data) must load
 * with every log intact, and survive being saved again by this version.
 */
class OldDataTest {
    // Exactly the fields the previous release wrote (Store.encode before this update).
    private val oldFile = """
    {"version":1,"onboarded":true,
     "settings":{"marathonPaceSec":618,"preplanLongestMiles":4},
     "stretch":[0,2,3],
     "logs":[
      {"day":0,"status":"DONE","xp":41,"plannedMiles":2,
       "run":{"distance":2,"pace":630,"effort":4,"pain":false},
       "strength":[
        {"id":"leg_press","rir":3,"sets":[{"w":140,"r":12},{"w":140,"r":12}]},
        {"id":"calf_raise","rir":2,"perSide":true,"sets":[{"w":90,"r":12},{"w":90,"r":12}]},
        {"id":"assisted_pull_up","feel":"RIGHT","sets":[{"w":70,"r":8},{"w":70,"r":8}]},
        {"id":"hip_thrust","rir":3,"sets":[{"w":90,"r":12},{"w":100,"r":11}]},
        {"id":"hip_abduction","sets":[{"w":80,"r":15},{"w":80,"r":15}]},
        {"id":"leg_curl","rir":2,"sets":[{"w":60,"r":12},{"w":60,"r":12}]}
       ]},
      {"day":1,"status":"MISSED","xp":2,"missReason":"BUSY","strength":[]},
      {"day":2,"status":"PARTIAL","xp":15,"plannedMiles":3,
       "run":{"distance":2.5,"pace":615,"segmentPace":600,"effort":5,"pain":true},"strength":[]}
     ]}
    """.trimIndent()

    @Test fun oldFileLoadsIntact() {
        val d = Store.decode(JSONObject(oldFile))
        assertTrue(d.onboarded)
        assertEquals(618, d.settings.marathonPaceSec)
        assertEquals(4.0, d.settings.preplanLongestMiles, 0.0)
        assertNull(d.settings.easyHrCap)
        assertEquals(setOf(0, 2, 3), d.stretchDays)
        assertEquals(3, d.logs.size)
        // New fields take safe defaults.
        assertEquals(CalfStatus(), d.calf)
        assertTrue(d.calfTests.isEmpty() && d.calfDays.isEmpty())
        assertEquals(0, d.whatsNewSeen)

        val day0 = d.logs.getValue(0)
        assertEquals(DayStatus.DONE, day0.status)
        assertEquals(41, day0.xp)
        assertEquals(2.0, day0.plannedMiles!!, 0.0)
        assertEquals(630, day0.run!!.paceSec)
        assertNull(day0.run!!.avgHr)
        assertEquals(listOf("leg_press", "calf_raise", "assisted_pull_up", "hip_thrust", "hip_abduction", "leg_curl"),
            day0.strength.map { it.exerciseId })
        assertTrue(day0.strength[1].perSide)
        assertEquals(Feel.RIGHT, day0.strength[2].feel)
        assertEquals(100.0, day0.strength[3].sets[1].weight, 0.0)
        assertFalse(day0.strength.any { it.bodyweight })

        assertEquals(MissReason.BUSY, d.logs.getValue(1).missReason)
        assertEquals(600, d.logs.getValue(2).run!!.segmentPaceSec)
        assertTrue(d.logs.getValue(2).run!!.pain)
    }

    @Test fun resavingKeepsEverything() {
        val d = Store.decode(JSONObject(oldFile))
        val again = Store.decode(JSONObject(Store.encode(d).toString()))
        assertEquals(d, again)
    }

    @Test fun newFieldsRoundTrip() {
        val d = Store.decode(JSONObject(oldFile))
        val withNew: AppData = d.copy(
            settings = d.settings.copy(easyHrCap = 152),
            calf = CalfStatus(flagged = true, stage = 2, side = com.feidbo.marathon.core.Side.RIGHT, sinceDay = 20),
            calfTests = listOf(com.feidbo.marathon.core.CalfTest(21, soreReps = 18, otherReps = 31, hops = null)),
            calfDays = setOf(20, 21),
            whatsNewSeen = 2,
            logs = d.logs + (5 to d.logs.getValue(0).copy(
                dayIndex = 5,
                run = d.logs.getValue(0).run!!.copy(avgHr = 148),
                strength = listOf(com.feidbo.marathon.core.ExerciseLog("split_squat",
                    listOf(com.feidbo.marathon.core.SetLog(0.0, 10)), perSide = true, bodyweight = true)),
            )),
        )
        assertEquals(withNew, Store.decode(JSONObject(Store.encode(withNew).toString())))
    }

    @Test fun oldHistoryStillCountsAfterUpdate() {
        val d = Store.decode(JSONObject(oldFile))
        val today = LocalDate.of(2026, 10, 20)
        val schedule = Schedule(d.logs, d.settings, today)
        val p = AppViewModel.progressFor(d, schedule, today)
        assertTrue("first_lift" in p.badges)
        assertEquals(1, p.strengthSessions)
        // The machine lifts stay in the history the Progress screen and exercise pages read.
        assertEquals(1, exerciseHistory("leg_press", d.logs).size)
        assertEquals(1, exerciseHistory("leg_curl", d.logs).size)
    }

    @Test fun aStretchTimerSavedByTheOldVersionStillRunsItsThreeMinutes() {
        // The old version saved the label "Quad stretch" for a 3-minute, quad-only countdown.
        val segs = Timers.stretchSegments("Quad stretch")
        assertEquals(listOf(90, 90), segs.map { it.seconds })
        assertTrue(segs.all { it.exercise.id == "quad_stretch" })
    }
}
