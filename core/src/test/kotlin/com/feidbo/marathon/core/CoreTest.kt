package com.feidbo.marathon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanDataTest {
    @Test fun planMatchesDocument() {
        val days = Plan.days
        assertEquals(192, days.size)
        assertEquals(LocalDate.of(2026, 10, 16), days.first().date)
        assertEquals(RunKind.RACE, days.last().run)
        assertEquals(26.2, days.last().miles, 0.0)
        // Every Tuesday is a rest day.
        assertTrue(days.filter { it.date.dayOfWeek.value == 2 }.all { it.run == RunKind.REST })
        // Peak week is 41 miles.
        assertEquals(41.0, Plan.weekDays(24).sumOf { it.miles }, 0.001)
        assertEquals(20.0, days.first { it.date == LocalDate.of(2027, 4, 4) }.miles, 0.0)
    }

    @Test fun strengthDaysFollowThePhases() {
        val d = { y: Int, m: Int, dd: Int -> Plan.dayOn(LocalDate.of(y, m, dd))!! }
        assertEquals(StrengthKind.LEARN, d(2026, 10, 16).strength)
        assertEquals(StrengthKind.PHASE1, d(2026, 10, 19).strength)
        assertEquals(StrengthKind.PHASE2, d(2026, 12, 14).strength)
        assertEquals(StrengthKind.PHASE3, d(2027, 2, 8).strength)
        assertEquals(StrengthKind.NONE, d(2027, 2, 11).strength)
        assertEquals(StrengthKind.LIGHT, d(2027, 3, 1).strength)
        assertEquals(StrengthKind.NONE, d(2027, 3, 8).strength)
        assertEquals(StrengthKind.PHASE3, d(2027, 3, 11).strength)
        assertEquals(StrengthKind.LIGHT, d(2027, 4, 12).strength)
        assertEquals(StrengthKind.NONE, d(2027, 4, 19).strength)
    }

    @Test fun marathonPaceMilesParsed() {
        assertEquals(4.0, Plan.dayOn(LocalDate.of(2027, 1, 27))!!.mpMiles, 0.0)
        assertEquals(3.0, Plan.dayOn(LocalDate.of(2027, 2, 21))!!.mpMiles, 0.0)
        assertEquals(8.0, Plan.dayOn(LocalDate.of(2027, 3, 28))!!.mpMiles, 0.0)
    }
}

class PaceTest {
    @Test fun riegelMatchesPlanTable() {
        // 2:09:30 half -> 4:30:00 (10:18/mi) in the plan's table.
        val m = Paces.riegelMarathonSec(2 * 3600 + 9 * 60 + 30)
        assertTrue(kotlin.math.abs(m - (4 * 3600 + 30 * 60)) < 30)
        assertTrue(kotlin.math.abs(Paces.paceForMarathon(4 * 3600 + 30 * 60) - 618) <= 1)
    }

    @Test fun parsing() {
        assertEquals(618, parsePace("10:18"))
        assertEquals(null, parsePace("10:75"))
        assertEquals(7770, parseDuration("2:09:30"))
        assertEquals("10:18", formatPace(618))
        assertEquals("3.75", formatMiles(3.75))
        assertEquals("4", formatMiles(4.0))
    }
}

class ScheduleTest {
    private val settings = Settings()

    private fun done(day: PlannedDay, miles: Double = day.miles) =
        DayLog(day.index, DayStatus.DONE, run = if (day.run.isRun) RunLog(miles) else null)

    private fun missed(day: PlannedDay, reason: MissReason = MissReason.BUSY) =
        DayLog(day.index, DayStatus.MISSED, missReason = reason)

    @Test fun followingThePlanChangesNothing() {
        val today = LocalDate.of(2027, 4, 1)
        val logs = Plan.days.filter { it.date < today && it.isTrainingDay }.associate { it.index to done(it) }
        val s = Schedule(logs, settings, today)
        assertTrue(s.days.none { it.isAdjusted })
        assertTrue(s.notices.isEmpty())
    }

    @Test fun freshPlanHasNoAdjustments() {
        val s = Schedule(emptyMap(), settings, LocalDate.of(2026, 10, 17))
        assertTrue(s.days.none { it.isAdjusted })
    }

    @Test fun shortMissCarriesOnButLongRunRuleApplies() {
        // Miss the Sunday 5.5 mi long run (Dec 6); the next week's 6 mi long run must respect the 10% rule.
        val today = LocalDate.of(2026, 12, 7)
        val logs = Plan.days.filter { it.date < today && it.isTrainingDay }.associate {
            it.index to if (it.date == LocalDate.of(2026, 12, 6)) missed(it) else done(it)
        }
        val s = Schedule(logs, settings, today)
        assertTrue(s.notices.isEmpty())
        val sunday = s.on(LocalDate.of(2026, 12, 13))!!
        // Longest in prior 30 days is 5 mi (Nov 29) -> cap 5.5.
        assertEquals(5.5, sunday.day.miles, 0.0)
        assertEquals(AdjustmentKind.LONG_RUN_CAP, sunday.adjustments.single().kind)
    }

    @Test fun missingOneToTwoWeeksRepeatsLastFullWeek() {
        // Weeks 1–5 done, then sick Nov 23 – Dec 6 (weeks 6 and 7). Week 8 should repeat week 5.
        val today = LocalDate.of(2026, 12, 7)
        val logs = Plan.days.filter { it.date < today && it.isTrainingDay }.associate {
            it.index to if (it.date >= LocalDate.of(2026, 11, 23)) missed(it, MissReason.SICK_SYSTEMIC) else done(it)
        }
        val s = Schedule(logs, settings, today)
        assertEquals(1, s.notices.size)
        assertTrue(s.notices[0], s.notices[0].contains("Week 8 repeats Week 5"))
        val wed = s.on(LocalDate.of(2026, 12, 9))!!
        assertEquals(Plan.dayOn(LocalDate.of(2026, 11, 18))!!.text, wed.day.text)
        assertTrue(wed.adjustments.any { it.kind == AdjustmentKind.REPEAT_WEEK })
        // Strength stays as planned.
        assertEquals(StrengthKind.PHASE1, s.on(LocalDate.of(2026, 12, 7))!!.day.strength)
        // Tuesday stays rest.
        assertEquals(RunKind.REST, s.on(LocalDate.of(2026, 12, 8))!!.day.run)
    }

    @Test fun missingMoreThanTwoWeeksDropsBackTwoWeeks() {
        // Weeks 1–12 done, then miss Jan 11 – Jan 31 (weeks 13–15). Resume Feb 1 (week 16).
        val today = LocalDate.of(2027, 2, 1)
        val logs = Plan.days.filter { it.date < today && it.isTrainingDay }.associate {
            it.index to if (it.date >= LocalDate.of(2027, 1, 11)) missed(it) else done(it)
        }
        val s = Schedule(logs, settings, today)
        assertTrue(s.notices.single(), s.notices.single().contains("drop back two weeks"))
        val sun16 = s.on(LocalDate.of(2027, 2, 7))!!
        assertTrue(sun16.adjustments.any { it.kind == AdjustmentKind.DROP_BACK })
        // Week 16 follows week 11; week 17 follows week 12.
        assertEquals(Plan.dayOn(LocalDate.of(2027, 1, 3))!!.miles.coerceAtMost(sun16.day.miles), sun16.day.miles, 0.0)
        // Nothing jumps more than 10% over the last 30 days.
        s.days.filter { it.date >= today && it.day.run.isRun && it.day.run != RunKind.RACE && it.day.run != RunKind.HALF_RACE }
            .forEach { assertTrue("${it.date} ${it.day.miles}", it.day.miles <= s.longestInPrior30(it.date) * 1.1 + 0.01) }
    }

    @Test fun halfMarathonWeekIsNeverCopied() {
        val today = LocalDate.of(2027, 3, 22)
        val logs = Plan.days.filter { it.date < today && it.isTrainingDay }.associate {
            it.index to if (it.date >= LocalDate.of(2027, 3, 8)) missed(it) else done(it)
        }
        val s = Schedule(logs, settings, today)
        assertTrue(s.notices.single(), s.notices.single().contains("Week 23 repeats Week 19"))
        assertTrue(s.days.filter { it.date >= today }.none { it.day.run == RunKind.HALF_RACE })
        // Strength notes follow the target date, not the copied week.
        assertTrue(s.on(LocalDate.of(2027, 3, 22))!!.day.text.contains("Strength (15 min)"))
        assertFalse(s.on(LocalDate.of(2027, 3, 25))!!.day.text.contains("Strength"))
    }

    @Test fun taperIsNeverRestructured() {
        val today = LocalDate.of(2027, 4, 12)
        val logs = Plan.days.filter { it.date < today && it.isTrainingDay }.associate {
            it.index to if (it.date >= LocalDate.of(2027, 3, 29)) missed(it) else done(it)
        }
        val s = Schedule(logs, settings, today)
        assertTrue(s.days.filter { it.original.week >= 25 }.none { a -> a.adjustments.any { it.kind == AdjustmentKind.REPEAT_WEEK } })
        // The race is never shortened.
        assertEquals(26.2, s.days.last().day.miles, 0.0)
    }

    @Test fun tiredMissMakesNextWeekLighter() {
        val today = LocalDate.of(2026, 11, 25)
        val logs = Plan.days.filter { it.date < today && it.isTrainingDay }.associate {
            it.index to if (it.date == LocalDate.of(2026, 11, 23)) missed(it, MissReason.TIRED) else done(it)
        }
        val s = Schedule(logs, settings, today)
        val week7 = s.days.filter { it.original.week == 7 && it.day.run.isRun }
        assertTrue(week7.all { sd -> sd.adjustments.any { it.kind == AdjustmentKind.LIGHTER_WEEK } })
        assertTrue(week7.sumOf { it.day.miles } < 20.0 * 0.9)
    }

    @Test fun adjustedDistanceSticksAfterLogging() {
        // Dec 13's long run was capped at 5.5; once logged (and in the past) it should still read 5.5.
        val logs = Plan.days.filter { it.date < LocalDate.of(2026, 12, 7) && it.isTrainingDay }.associate {
            it.index to if (it.date == LocalDate.of(2026, 12, 6)) missed(it) else done(it)
        }.toMutableMap()
        val dec13 = Plan.dayOn(LocalDate.of(2026, 12, 13))!!
        logs[dec13.index] = DayLog(dec13.index, DayStatus.DONE, RunLog(5.5), plannedMiles = 5.5)
        val later = Schedule(logs, settings, LocalDate.of(2026, 12, 20))
        assertEquals(5.5, later.on(dec13.date)!!.day.miles, 0.0)
    }

    @Test fun repeatedWeekStaysAfterLogging() {
        val today = LocalDate.of(2026, 12, 10)
        val logs = Plan.days.filter { it.date < LocalDate.of(2026, 12, 7) && it.isTrainingDay }.associate {
            it.index to if (it.date >= LocalDate.of(2026, 11, 23)) missed(it, MissReason.SICK_SYSTEMIC) else done(it)
        }.toMutableMap()
        val wed = Plan.dayOn(LocalDate.of(2026, 12, 9))!!
        logs[wed.index] = DayLog(wed.index, DayStatus.DONE, RunLog(3.5))
        val s = Schedule(logs, settings, today)
        assertEquals(Plan.dayOn(LocalDate.of(2026, 11, 18))!!.text, s.on(wed.date)!!.day.text)
    }

    @Test fun preplanLongestRunCapsWeekZero() {
        val s = Schedule(emptyMap(), Settings(preplanLongestMiles = 2.5), LocalDate.of(2026, 10, 17))
        val sunday = s.on(LocalDate.of(2026, 10, 18))!!
        assertEquals(2.75, sunday.day.miles, 0.0)
    }
}

class FeedbackTest {
    private val settings = Settings()
    private val easyDay = Plan.dayOn(LocalDate.of(2026, 10, 21))!! // 3 mi easy

    @Test fun easyFastPaceAloneIsNotAWarning() {
        // Pace alone no longer flags "too fast" (decisions §5): with no effort/HR it's an info prompt.
        val tips = runFeedback(easyDay, RunLog(3.0, paceSec = 10 * 60 + 30), settings, 3.75)
        assertTrue(tips.none { it.level == TipLevel.WARN })
        assertTrue(tips.any { it.level == TipLevel.INFO && it.text.contains("full sentences") })
    }

    @Test fun easyFastPaceWithEasyEffortTrustsEffort() {
        val tips = runFeedback(easyDay, RunLog(3.0, paceSec = 10 * 60 + 30, effort = 3), settings, 3.75)
        assertTrue(tips.none { it.level == TipLevel.WARN })
        assertTrue(tips.any { it.text.contains("trust that") })
    }

    @Test fun easyWithHrAboveCapWarns() {
        val capped = settings.copy(easyHrCap = 150)
        val tips = runFeedback(easyDay, RunLog(3.0, paceSec = 10 * 60 + 30, effort = 3, avgHr = 158), capped, 3.75)
        assertTrue(tips.any { it.level == TipLevel.WARN && it.text.contains("above your easy cap") })
        val ok = runFeedback(easyDay, RunLog(3.0, paceSec = 10 * 60 + 30, effort = 3, avgHr = 141), capped, 3.75)
        assertTrue(ok.none { it.level == TipLevel.WARN })
    }

    @Test fun easyEffortFiveWarns() {
        val tips = runFeedback(easyDay, RunLog(3.0, paceSec = 12 * 60, effort = 5), settings, 3.75)
        assertTrue(tips.any { it.level == TipLevel.WARN && it.text.contains("above easy") })
    }

    @Test fun suggestsHrCapWhenEasyAndNoCap() {
        val tips = runFeedback(easyDay, RunLog(3.0, paceSec = 12 * 60, effort = 3, avgHr = 139), settings, 3.75)
        assertTrue(tips.any { it.text.contains("~139") })
    }

    @Test fun easyInZoneIsGreat() {
        val tips = runFeedback(easyDay, RunLog(3.0, paceSec = 12 * 60 + 20), settings, 3.75)
        assertTrue(tips.all { it.level == TipLevel.GREAT })
    }

    @Test fun tooLongWarnsAboutLongRunRule() {
        val tips = runFeedback(easyDay, RunLog(6.0, paceSec = 12 * 60 + 20), settings, 3.75)
        assertTrue(tips.any { it.level == TipLevel.WARN && it.text.contains("10%") })
    }

    @Test fun typoPaceFlagged() {
        val tips = runFeedback(easyDay, RunLog(3.0, paceSec = 2 * 60), settings, 3.75)
        assertEquals(TipLevel.WARN, tips.single().level)
    }

    @Test fun marathonPaceTooFast() {
        val mpDay = Plan.dayOn(LocalDate.of(2027, 1, 27))!!
        val tips = runFeedback(mpDay, RunLog(7.0, paceSec = 11 * 60, segmentPaceSec = 9 * 60 + 40), settings, 10.0)
        assertTrue(tips.any { it.level == TipLevel.WARN && it.text.contains("faster than goal") })
    }

    @Test fun personalRecordDetected() {
        val mon = Plan.dayOn(LocalDate.of(2026, 10, 19))!!
        val thu = Plan.dayOn(LocalDate.of(2026, 10, 22))!!
        val logs = mapOf(mon.index to DayLog(mon.index, DayStatus.DONE, strength = listOf(ExerciseLog("leg_press", listOf(SetLog(100.0, 12)), rir = 3))))
        val prs = personalRecords(thu.index, listOf(ExerciseLog("leg_press", listOf(SetLog(110.0, 12)), rir = 3)), logs)
        assertEquals("leg_press", prs.single().id)
        assertTrue(personalRecords(mon.index, logs[mon.index]!!.strength, logs).isEmpty())
    }

    @Test fun strengthTooHeavyAndTooLight() {
        val rx = strengthSession(StrengthKind.PHASE1)!!.items.first()
        val heavy = strengthFeedback(rx, ExerciseLog(rx.exercise.id, listOf(SetLog(200.0, 8), SetLog(200.0, 7))), null, StrengthKind.PHASE1)
        assertTrue(heavy.any { it.text.contains("Too heavy") })
        val light = strengthFeedback(rx, ExerciseLog(rx.exercise.id, listOf(SetLog(50.0, 20), SetLog(50.0, 20))), null, StrengthKind.PHASE1)
        assertTrue(light.any { it.text.contains("too light") })
        val jump = strengthFeedback(rx, ExerciseLog(rx.exercise.id, listOf(SetLog(150.0, 12), SetLog(150.0, 12))),
            ExerciseLog(rx.exercise.id, listOf(SetLog(100.0, 12))), StrengthKind.PHASE1)
        assertTrue(jump.any { it.text.contains("Big jump") })
    }

}

class ProgressionTest {
    private val p1 = strengthSession(StrengthKind.PHASE1)!!
    private val squat = p1.items.first { it.exercise.id == "split_squat" }
    private val calf = p1.items.first { it.exercise.id == "calf_raise_straight" }
    private val pull = p1.items.first { it.exercise.inverted }
    private fun day(y: Int, m: Int, d: Int) = Plan.dayOn(LocalDate.of(y, m, d))!!
    private fun entry(day: PlannedDay, id: String, w: Double, reps: List<Int>, rir: Int? = null, feel: Feel? = null,
                      perSide: Boolean = false, bodyweight: Boolean = false) =
        ExerciseHistoryEntry(day.index, day.strength, ExerciseLog(id, reps.map { SetLog(w, it) }, feel, rir, perSide, bodyweight))

    private val mon = day(2026, 10, 19)
    private val thu = day(2026, 10, 22)
    private val nextMon = day(2026, 10, 26)

    @Test fun phase1PrescriptionMatchesDecisions() {
        assertEquals(3, squat.sets); assertEquals(10, squat.reps); assertEquals("8–10/leg", squat.repsShown)
        assertEquals(2, calf.sets); assertEquals(12, calf.reps)
        assertEquals(listOf("split_squat", "calf_raise_straight", "calf_raise_bent", "assisted_pull_up", "hip_thrust_db",
            "side_abduction", "single_leg_rdl"), p1.items.map { it.exercise.id })
        val p2 = strengthSession(StrengthKind.PHASE2)!!
        assertEquals(6, p2.items.first { it.exercise.id == "hip_thrust_db" }.reps)
        assertTrue(p2.items.any { it.exercise.id == "pogo_hops" })
        assertTrue(strengthSession(StrengthKind.PHASE2, SessionOptions(plyometrics = false))!!.items.none { it.exercise.id == "pogo_hops" })
        assertTrue(strengthSession(StrengthKind.PHASE1, SessionOptions(skipCalfRaises = true))!!.items.none { it.exercise.calf })
        assertTrue(strengthSession(StrengthKind.LIGHT)!!.items.none { it.exercise.id == "pogo_hops" })
    }

    @Test fun easierThanTargetAddsOneDumbbellStep() {
        val a = loadAdvice(squat, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "split_squat", 40.0, listOf(10, 10, 10), rir = 4, perSide = true)))!!
        assertEquals(LoadChange.UP, a.change)
        assertEquals(45.0, a.weight, 0.0)
    }

    @Test fun muchEasierThanTargetTakesBiggerJump() {
        val a = loadAdvice(squat, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "split_squat", 40.0, listOf(10, 10, 10), rir = 6)))!!
        assertEquals(LoadChange.BIG_UP, a.change)
        assertEquals(50.0, a.weight, 0.0)
        val small = loadAdvice(calf, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "calf_raise_straight", 20.0, listOf(12, 12), rir = 6)))!!
        assertEquals(25.0, small.weight, 0.0)
    }

    @Test fun onTargetHoldsAndMissedRepsBacksOff() {
        val same = loadAdvice(squat, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "split_squat", 40.0, listOf(10, 10, 10), rir = 3)))!!
        assertEquals(LoadChange.SAME, same.change)
        assertEquals(40.0, same.weight, 0.0)
        val down = loadAdvice(squat, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "split_squat", 40.0, listOf(8, 6), rir = 0)))!!
        assertEquals(LoadChange.DOWN, down.change)
        assertEquals(35.0, down.weight, 0.0)
    }

    @Test fun twoForTwoWithoutRir() {
        val once = loadAdvice(squat, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "split_squat", 40.0, listOf(10, 10, 10))))!!
        assertEquals(LoadChange.SAME, once.change)
        val twice = loadAdvice(squat, StrengthKind.PHASE1, nextMon.date,
            listOf(entry(mon, "split_squat", 40.0, listOf(10, 10, 10)), entry(thu, "split_squat", 40.0, listOf(10, 10, 10))))!!
        assertEquals(LoadChange.UP, twice.change)
    }

    @Test fun olderFeelLogsStillWork() {
        val a = loadAdvice(squat, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "split_squat", 40.0, listOf(10, 10, 10), feel = Feel.EASY)))!!
        assertEquals(LoadChange.BIG_UP, a.change)
    }

    @Test fun assistedPullUpProgressesByRemovingCounterweight() {
        val a = loadAdvice(pull, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "assisted_pull_up", 60.0, listOf(8, 8), rir = 4)))!!
        assertEquals(55.0, a.weight, 0.0)
        val hard = loadAdvice(pull, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "assisted_pull_up", 60.0, listOf(6, 5), rir = 0)))!!
        assertTrue(hard.weight > 60.0)
    }

    @Test fun phaseChangeUsesEstimatedMaxAndStartsLight() {
        val lastP1 = day(2026, 12, 10)
        val firstP2 = day(2026, 12, 14)
        val rx = strengthSession(StrengthKind.PHASE2)!!.items.first { it.exercise.id == "split_squat" }
        val a = loadAdvice(rx, StrengthKind.PHASE2, firstP2.date, listOf(entry(lastP1, "split_squat", 40.0, listOf(10, 10, 10), rir = 3)))!!
        assertEquals(LoadChange.NEW_TARGET, a.change)
        // e1RM = 40 × (1 + 13/30) = 57.3; 6 reps with 2 left = 57.3 / (1 + 8/30) = 45.3; ×0.9 first heavy week = 40.7 → 40.
        assertEquals(40.0, a.weight, 0.0)
    }

    @Test fun longBreakEasesBackIn() {
        val a = loadAdvice(squat, StrengthKind.PHASE1, LocalDate.of(2026, 11, 23),
            listOf(entry(mon, "split_squat", 40.0, listOf(10, 10, 10), rir = 3)))!!
        assertEquals(LoadChange.RETURNING, a.change)
        assertEquals(35.0, a.weight, 0.0)
    }

    @Test fun lightDayIsSixtyPercent() {
        val rx = strengthSession(StrengthKind.LIGHT)!!.items.first { it.exercise.id == "split_squat" }
        val p3 = day(2027, 2, 22)
        val a = loadAdvice(rx, StrengthKind.LIGHT, LocalDate.of(2027, 3, 1), listOf(entry(p3, "split_squat", 50.0, listOf(5, 5), rir = 2)))!!
        assertEquals(30.0, a.weight, 0.0)
    }

    @Test fun perSetWeightsUseWorkingSets() {
        val ramp = ExerciseHistoryEntry(mon.index, mon.strength,
            ExerciseLog("split_squat", listOf(SetLog(30.0, 10), SetLog(40.0, 10)), rir = 4))
        assertEquals(45.0, loadAdvice(squat, StrengthKind.PHASE1, thu.date, listOf(ramp))!!.weight, 0.0)
        val backOff = ExerciseHistoryEntry(mon.index, mon.strength,
            ExerciseLog("split_squat", listOf(SetLog(40.0, 10), SetLog(30.0, 10)), rir = 4))
        val a = loadAdvice(squat, StrengthKind.PHASE1, thu.date, listOf(backOff))!!
        assertEquals(LoadChange.SAME, a.change)
        assertEquals(40.0, a.weight, 0.0)
    }

    @Test fun bodyweightGetsNoLoadAdvice() {
        assertNull(loadAdvice(squat, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "split_squat", 0.0, listOf(12, 12), rir = 3, bodyweight = true))))
        val abd = p1.items.first { it.exercise.id == "side_abduction" }
        assertNull(loadAdvice(abd, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "side_abduction", 0.0, listOf(12, 12), rir = 3))))
        // A bodyweight set never triggers the "enter the weight" warning.
        val tips = strengthFeedback(squat, ExerciseLog("split_squat", listOf(SetLog(0.0, 10)), rir = 3, bodyweight = true), null, StrengthKind.PHASE1)
        assertTrue(tips.none { it.text.contains("Enter the weight") })
    }

    @Test fun retiredMachineHistoryStillResolves() {
        for (id in listOf("leg_press", "calf_raise", "hip_thrust", "hip_abduction", "leg_curl")) {
            val ex = Exercises.byId(id)
            assertTrue(id, ex != null && ex.retired)
        }
        assertTrue(Exercises.routine.none { it.retired })
    }

    @Test fun noHistoryNoAdvice() {
        assertNull(loadAdvice(squat, StrengthKind.PHASE1, thu.date, emptyList()))
    }
}

class CalfGuideTest {
    @Test fun asymmetryAndBenchmarks() {
        val t = CalfTest(10, soreReps = 27, otherReps = 30)
        assertEquals(0.1, CalfGuide.asymmetry(t)!!, 1e-9)
        assertFalse(CalfGuide.raisesPassed(t)) // sore side under 30
        assertTrue(CalfGuide.raisesPassed(CalfTest(10, soreReps = 31, otherReps = 33)))
        assertFalse(CalfGuide.raisesPassed(CalfTest(10, soreReps = 30, otherReps = 40))) // 25% weaker
        assertTrue(CalfGuide.hopsPassed(CalfTest(10, hops = 20, hopsSymptomFree = true)))
        assertFalse(CalfGuide.hopsPassed(CalfTest(10, hops = 20, hopsSymptomFree = false)))
    }

    @Test fun stagesGateRoutineAndStretches() {
        val early = CalfStatus(flagged = true, stage = 1)
        assertTrue(CalfGuide.skipRoutineCalfRaises(early))
        assertFalse(CalfGuide.plyometricsAllowed(early))
        assertFalse(early.currentStage.calfStretchesAllowed)
        val s3 = CalfStatus(flagged = true, stage = 3)
        assertFalse(CalfGuide.skipRoutineCalfRaises(s3))
        assertTrue(s3.currentStage.calfStretchesAllowed)
        assertTrue(CalfGuide.plyometricsAllowed(CalfStatus()))
        assertEquals(4, CalfStage.entries.size)
    }
}

class WorkoutStepsTest {
    private val settings = Settings()

    @Test fun everyPlanRunDayParses() {
        for (d in Plan.days.filter { it.run.isRun }) {
            val steps = WorkoutSteps.steps(d, settings)
            assertTrue("no steps for ${d.date}: ${d.text}", steps.isNotEmpty())
            assertTrue("unparsed ${d.date}: ${d.text}", steps.none { it.kind == StepKind.NOTE && it.title == "Workout" })
        }
    }

    @Test fun qualityWorkoutsHaveWarmupWorkAndCooldown() {
        for (d in Plan.days.filter { it.run in setOf(RunKind.TEMPO, RunKind.HILLS, RunKind.FARTLEK) }) {
            val kinds = WorkoutSteps.steps(d, settings).map { it.kind }
            assertTrue("${d.date}", StepKind.WARMUP in kinds && StepKind.WORK in kinds && StepKind.COOLDOWN in kinds)
        }
    }

    @Test fun specificWorkouts() {
        val tempo = WorkoutSteps.steps(Plan.dayOn(LocalDate.of(2026, 12, 23))!!, settings)
        assertEquals("Warm-up 1.5 mi", tempo[0].title)
        assertEquals("2 × 10 min tempo", tempo[1].title)
        assertEquals("… with 3 min easy jog between", tempo[2].title)
        assertTrue(tempo[3].detail.contains("5 mi total"))
        val mp = WorkoutSteps.steps(Plan.dayOn(LocalDate.of(2027, 1, 27))!!, settings)
        assertEquals("4 mi at marathon pace", mp[1].title)
        assertTrue(mp[1].detail.contains("10:18"))
        assertEquals("Cool-down 1.5 mi", mp[2].title)
        val long = WorkoutSteps.steps(Plan.dayOn(LocalDate.of(2027, 3, 14))!!, settings)
        assertEquals("13 mi easy", long[0].title)
        assertEquals("Last 4 mi at marathon pace", long[1].title)
        val strides = WorkoutSteps.steps(Plan.dayOn(LocalDate.of(2026, 11, 25))!!, settings)
        assertEquals("Then 6 × 15 s uphill strides", strides[1].title)
        val noSeconds = WorkoutSteps.steps(Plan.dayOn(LocalDate.of(2027, 4, 15))!!, settings)
        assertEquals("Then 4 × 15–20 s strides", noSeconds[1].title)
        val fuel = WorkoutSteps.steps(Plan.dayOn(LocalDate.of(2027, 1, 10))!!, settings)
        assertTrue(fuel.any { it.kind == StepKind.NOTE && it.detail.startsWith("Start practicing fuel") })
    }

    @Test fun cappedLongRunUsesScheduledDistance() {
        // When the long-run rule shortens a run, the steps follow the scheduled distance.
        val day = Plan.dayOn(LocalDate.of(2027, 3, 14))!!
        val capped = day.copy(miles = 15.0, text = rewriteMiles(day.text, 15.0))
        val steps = WorkoutSteps.steps(capped, settings)
        assertEquals("11 mi easy", steps[0].title)
    }
}

class GameTest {
    @Test fun xpAndLevels() {
        val day = Plan.dayOn(LocalDate.of(2026, 10, 19))!! // 2 mi easy + strength
        val log = DayLog(day.index, DayStatus.DONE, RunLog(2.0),
            Exercises.routine.map { ExerciseLog(it.id, listOf(SetLog(50.0, 12))) })
        assertEquals(10 + 6 + 25, Game.xpFor(day, log))
        assertEquals(1, Game.level(0).level)
        assertEquals(2, Game.level(150).level)
        assertFalse(Game.level(150).progress > 1f)
    }

    @Test fun halfMarathonAt13Point1IsDone() {
        val half = Plan.dayOn(LocalDate.of(2027, 3, 7))!!
        assertEquals(DayStatus.DONE, completionStatus(half, RunLog(13.1, raceTimeSec = 7800), emptyList()))
        assertTrue(runFeedback(half, RunLog(13.1, raceTimeSec = 7800), Settings(), 14.5).none { it.text.contains("short") })
    }

    @Test fun perfectDaysFullWeeksComebacksAndPrs() {
        val today = LocalDate.of(2026, 11, 2)
        val week1 = Plan.weekDays(1).filter { it.isTrainingDay }
        val week2 = Plan.weekDays(2).filter { it.isTrainingDay }
        val logs = mutableMapOf<Int, DayLog>()
        // Week 1 all done, with lifts that get stronger.
        week1.forEachIndexed { i, d ->
            logs[d.index] = DayLog(d.index, DayStatus.DONE, RunLog(d.miles),
                if (d.strength.isPlanned) Exercises.routine.map { ExerciseLog(it.id, listOf(SetLog(100.0 + i * 10, 12)), rir = 3) } else emptyList())
        }
        // Week 2: three misses, then a comeback on Sunday.
        week2.dropLast(1).forEach { logs[it.index] = DayLog(it.index, DayStatus.MISSED, missReason = MissReason.BUSY) }
        week2.last().let { logs[it.index] = DayLog(it.index, DayStatus.DONE, RunLog(it.miles)) }
        val stretch = week1.map { it.index }.toSet()
        val p = computeProgress(Schedule(logs, Settings(), today), logs, stretch, today)
        assertEquals(week1.size, p.perfectDays)
        assertEquals(1, p.fullWeeks)
        assertTrue("comeback" in p.badges)
        assertTrue("pr" in p.badges)
        assertTrue("week_complete" in p.badges)
    }

    @Test fun newBadges() {
        val today = LocalDate.of(2027, 1, 25)
        val logs = Plan.days.filter { it.date < today && it.isTrainingDay }.associate {
            it.index to DayLog(it.index, DayStatus.DONE, run = if (it.run.isRun) RunLog(it.miles, segmentPaceSec = 618) else null, xp = 40)
        }
        val p = computeProgress(Schedule(logs, Settings(), today), logs, emptySet(), today)
        for (b in listOf("miles_26", "turkey", "jingle", "new_year", "halfway", "weeks_4", "streak_50", "level_5", "sunday_10"))
            assertTrue("$b missing: ${p.badges}", b in p.badges)
        assertTrue("bib_ready" !in p.badges)
        // By mid-March there have been 6 marathon-pace runs, all logged on goal pace.
        val feb = LocalDate.of(2027, 3, 15)
        val logs2 = Plan.days.filter { it.date < feb && it.isTrainingDay }.associate {
            it.index to DayLog(it.index, DayStatus.DONE, run = if (it.run.isRun) RunLog(it.miles, segmentPaceSec = 625) else null)
        }
        assertTrue("mp_5" in computeProgress(Schedule(logs2, Settings(), feb), logs2, emptySet(), feb).badges)
        assertEquals(Game.badges.size, Game.badges.map { it.id }.toSet().size) // ids are unique
        assertTrue(p.badgeProgress.keys.all { id -> Game.badges.any { it.id == id } })
    }

    @Test fun streakShieldedBySickness() {
        val today = LocalDate.of(2026, 10, 26)
        val logs = Plan.days.filter { it.date < today && it.isTrainingDay }.associate {
            val status = if (it.date == LocalDate.of(2026, 10, 21)) DayStatus.MISSED else DayStatus.DONE
            it.index to DayLog(it.index, status, run = if (status == DayStatus.DONE && it.run.isRun) RunLog(it.miles) else null,
                missReason = if (status == DayStatus.MISSED) MissReason.SICK_SYSTEMIC else null, xp = 10)
        }
        val p = computeProgress(Schedule(logs, Settings(), today), logs, emptySet(), today)
        assertEquals(logs.values.count { it.status == DayStatus.DONE }, p.streak)
        assertTrue("first_step" in p.badges)
    }
}

class StretchRoutineTest {
    @Test fun fullRoutineAfterRuns() {
        val segs = StretchRoutine.segments(calfAllowed = true, beforeRun = false)
        assertEquals(6, segs.size)
        assertEquals(listOf(90, 90, 60, 60, 60, 60), segs.map { it.seconds })
        assertEquals(420, StretchRoutine.totalSeconds(segs))
        assertEquals(listOf(90, 180, 240, 300, 360), StretchRoutine.boundaries(segs))
    }

    @Test fun beforeRunHoldsStayUnder60Seconds() {
        val segs = StretchRoutine.segments(calfAllowed = true, beforeRun = true)
        assertTrue(segs.all { it.seconds < 60 })
    }

    @Test fun calfStretchesPausedInEarlyStages() {
        assertFalse(StretchRoutine.calfStretchesAllowed(CalfStatus(flagged = true, stage = 1)))
        assertFalse(StretchRoutine.calfStretchesAllowed(CalfStatus(flagged = true, stage = 2)))
        assertTrue(StretchRoutine.calfStretchesAllowed(CalfStatus(flagged = true, stage = 3)))
        assertTrue(StretchRoutine.calfStretchesAllowed(CalfStatus()))
        val segs = StretchRoutine.segments(calfAllowed = false, beforeRun = false)
        assertEquals(listOf("quad_stretch", "quad_stretch"), segs.map { it.exercise.id })
    }

    @Test fun positionInRoutine() {
        val segs = StretchRoutine.segments(calfAllowed = true, beforeRun = false)
        assertEquals(0 to 90, StretchRoutine.at(segs, 0))
        assertEquals(1 to 1, StretchRoutine.at(segs, 179))
        assertEquals(2 to 60, StretchRoutine.at(segs, 180))
        assertNull(StretchRoutine.at(segs, 420))
    }
}

class EasyZoneTest {
    @Test fun effortFirstThenPace() {
        assertTrue(easyRunInZone(RunLog(4.0, paceSec = 630, effort = 3), null))          // 10:30 but easy effort
        assertFalse(easyRunInZone(RunLog(4.0, paceSec = 720, effort = 6), null))         // slow but hard
        assertFalse(easyRunInZone(RunLog(4.0, paceSec = 630, effort = 3, avgHr = 160), 150)) // HR over cap
        assertTrue(easyRunInZone(RunLog(4.0, paceSec = 720), null))                       // no effort: pace decides
        assertFalse(easyRunInZone(RunLog(4.0, paceSec = 600), null))
    }

    @Test fun calfXpAndBadges() {
        val today = LocalDate.of(2026, 11, 2)
        val calfDays = Plan.days.filter { it.date < today }.take(10).map { it.index }.toSet()
        val base = computeProgress(Schedule(emptyMap(), Settings(), today), emptyMap(), emptySet(), today)
        val p = computeProgress(Schedule(emptyMap(), Settings(), today), emptyMap(), emptySet(), today,
            calfDays = calfDays, calfTests = listOf(CalfTest(0, soreReps = 30, otherReps = 32)))
        assertEquals(base.xp + 10 * Game.CALF_XP, p.xp)
        assertTrue("calf_10" in p.badges)
        assertTrue("calf_cleared" in p.badges)
    }
}

class BodyweightVersionTest {
    @Test fun perSideFollowsTheVersionDone() {
        assertFalse(Exercises.hipThrust.perSideFor(false))
        assertTrue(Exercises.hipThrust.perSideFor(true))          // single-leg hip thrust
        assertTrue(Exercises.singleLegRdl.perSideFor(false))
        assertFalse(Exercises.singleLegRdl.perSideFor(true))      // sliding leg curl uses both legs
        assertTrue(Exercises.splitSquat.perSideFor(true))
        assertEquals("Sliding leg curl (bodyweight)", Exercises.singleLegRdl.nameFor(true))
        val rx = strengthSession(StrengthKind.PHASE1)!!.items.first { it.exercise.id == "hip_thrust_db" }
        assertEquals("8–10", rx.repsShown)
        assertEquals("8–10/leg", rx.repsShown(bodyweight = true))
    }
}

class RecentEasyPaceTest {
    @Test fun averagesTheLastEasyRuns() {
        val easy = Plan.days.filter { it.run == RunKind.EASY }.take(6)
        val logs = easy.mapIndexed { i, d -> d.index to DayLog(d.index, DayStatus.DONE, RunLog(d.miles, paceSec = 620 + i * 2)) }.toMap()
        assertNull(recentEasyPace(logs, easy[2].index))                   // only 2 before it
        assertEquals((622 + 624 + 626 + 628 + 630) / 5, recentEasyPace(logs, easy.last().index + 1))
    }
}
