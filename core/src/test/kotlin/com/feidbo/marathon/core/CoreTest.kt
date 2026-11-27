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
        assertEquals(2, calf.sets); assertEquals(20, calf.reps)   // 2 × 20 fast, then 2 × 20 slow (user request)
        // Hip thrust removed; abduction second to last, pull-up last (user request, Nov 2026).
        // Then the dumbbell row beside the pull-up (do one), and the abs block to finish.
        // Dumbbells, then bodyweight, then the machine; the pull-up is always last (user request, Nov 2026).
        // Side-lying abduction removed; the abs are weighted now, so they sit with the dumbbell exercises (Nov 2026).
        assertEquals(listOf("split_squat", "calf_raise_straight", "calf_raise_slow", "single_leg_rdl", "dumbbell_row",
            "curl_up", "russian_twist", "assisted_pull_up"), p1.items.map { it.exercise.id })
        for (k in StrengthKind.entries) strengthSession(k)?.let { s -> assertTrue(s.items.none { it.exercise.id == "side_abduction" }) }
        assertTrue(Exercises.byId("side_abduction")!!.retired)   // logged sets still resolve
        assertTrue(Exercises.curlUp.usesWeight && Exercises.russianTwist.usesWeight)
        for (k in StrengthKind.entries) strengthSession(k)?.let { s ->
            assertEquals("assisted_pull_up", s.items.last().exercise.id)
            val groups = s.items.map { if (it.exercise.inverted) 2 else if (it.exercise.usesWeight) 0 else 1 }
            assertEquals(groups.sorted(), groups)
        }
        assertEquals(listOf("russian_twist", "pogo_hops", "assisted_pull_up"), strengthSession(StrengthKind.PHASE2)!!.items.map { it.exercise.id }.takeLast(3))
        val row = p1.items.first { it.exercise.id == "dumbbell_row" }
        assertEquals(p1.items.first { it.exercise.id == "assisted_pull_up" }.sets, row.sets)
        assertEquals("8–10/arm", row.repsShown)
        assertEquals("10–15", p1.items.first { it.exercise.id == "curl_up" }.repsShown)
        assertTrue(strengthSession(StrengthKind.PHASE2)!!.items.map { it.exercise.id }.containsAll(listOf("curl_up", "russian_twist")))
        assertTrue(Exercises.byId("ab_rollout")!!.retired)   // logged roll-outs still resolve
        assertNull(loadAdviceFor(p1.items.first { it.exercise.id == "curl_up" }, mon, emptyMap()))
        for (k in StrengthKind.entries) strengthSession(k)?.let { s -> assertTrue(s.items.none { it.exercise.id == "hip_thrust_db" }) }
        assertTrue(Exercises.byId("hip_thrust_db")!!.retired)   // logged sets still resolve
        val p2 = strengthSession(StrengthKind.PHASE2)!!
        assertEquals(6, p2.items.first { it.exercise.id == "single_leg_rdl" }.reps)
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
        val small = loadAdvice(calf, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "calf_raise_straight", 20.0, listOf(20, 20), rir = 6)))!!
        assertEquals(25.0, small.weight, 0.0)
    }

    @Test fun calfRaisesMovingFrom12To20RepsGetALighterDumbbellNotAMissedSet() {
        // Logged under the old 2 × 12 prescription: 90 lb × 12 with plenty left. Today's target is 20 reps.
        val a = loadAdvice(calf, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "calf_raise_straight", 90.0, listOf(12, 12), rir = 5)))!!
        assertEquals(LoadChange.NEW_TARGET, a.change)
        // Estimated max 90 × (1 + 17/30) = 141 lb → 20 reps with 3 left ≈ 80 lb → the 5-lb step below.
        assertEquals(75.0, a.weight, 0.0)
        assertTrue(a.reason.contains("New target"))
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
        val hops = strengthSession(StrengthKind.PHASE2)!!.items.first { it.exercise.id == "pogo_hops" }
        assertNull(loadAdvice(hops, StrengthKind.PHASE2, thu.date, listOf(entry(mon, "pogo_hops", 0.0, listOf(10, 10), rir = 3))))
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
        val rx = strengthSession(StrengthKind.PHASE1)!!.items.first { it.exercise.id == "single_leg_rdl" }
        assertEquals("8–10/leg", rx.repsShown)
        assertEquals("8–10", rx.repsShown(bodyweight = true))
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

class NutritionTest {
    private val p = NutritionProfile(enabled = true)   // 184 lb, 6'0", 33, male, 21% body fat
    private val restDay = Plan.days.first { !it.isTrainingDay && it.date >= LocalDate.of(2026, 11, 16) }
    private val easy4 = Plan.days.first { it.run == RunKind.EASY && it.miles == 4.0 }

    @Test fun restingEnergyIsMifflinStJeor() {
        // 10 × 83.46 kg + 6.25 × 182.88 cm − 5 × 33 + 5
        assertEquals(1817.6, Nutrition.restingKcal(p), 1.0)
        assertEquals(117.0, Nutrition.runKcal(p, 1.0), 1.0)
        assertEquals(165, Nutrition.proteinTarget(p))
    }

    @Test fun targetsFollowMileageAndPauseInTheTaper() {
        val rest = Nutrition.target(p, restDay)
        assertTrue(rest.deficit)
        assertEquals((1817.6 * 1.4 * 0.82).toInt().toDouble(), rest.kcal.toDouble(), 3.0)
        val run = Nutrition.target(p, easy4)
        assertEquals(rest.kcal + (4 * 117 * 0.82), run.kcal.toDouble(), 6.0)
        assertTrue(run.fuelServings > rest.fuelServings)
        val race = Plan.days.last()
        assertFalse(Nutrition.target(p, race).deficit)
        val taper = Plan.days.first { it.phase == Phase.TAPER }
        assertFalse(Nutrition.target(p, taper).deficit)
        assertFalse(Nutrition.target(p, Plan.dayOn(LocalDate.of(2027, 3, 7))!!).deficit)
        // Energy availability: (eaten − running) ÷ lean mass.
        assertEquals((run.kcal - 4 * 117) / (83.46 * 0.79), run.energyAvailability, 0.3)
    }

    @Test fun everyWeeklyMenuHitsProteinAndSitsNearTheRestDayTarget() {
        for (week in 0..1) {
            val m = MealSlot.entries.map { Nutrition.scheduled(it, week).macros }.reduce { a, b -> a + b }
            assertTrue("protein ${m.protein}", m.protein >= 165)
            assertTrue("kcal ${m.kcal}", m.kcal in 1750.0..2100.0)
            assertTrue("fat share", m.fat * 9 / m.kcal >= 0.15)
        }
        assertTrue(Nutrition.fuelOptions.all { it.macros.kcal in 215.0..275.0 })
    }

    @Test fun recipesAreFoodHerosAsPublished() {
        val recipes = Nutrition.meals.mapNotNull { it.recipe }
        assertEquals(5, recipes.toSet().size)   // quiche muffins (both weeks) + 2 lunches + 2 dinners
        for (r in recipes) {
            assertTrue(r.url, r.url.startsWith("https://foodhero.org/recipes/"))
            assertTrue(r.ingredients.isNotEmpty() && r.steps.isNotEmpty())
            // Label energy roughly matches its own macros (4/4/9 kcal per g), so a transcription slip would show.
            val m = r.perServing
            assertEquals(r.title, m.kcal, 4 * m.protein + 4 * m.carbs + 9 * m.fat, 0.15 * m.kcal + 10)
        }
        // As published: the stir-fry starts with 3 chicken breast halves.
        assertEquals("3 chicken breast halves, cut into strips", foodHeroRecipes.getValue("chicken-cabbage-stir-fry").ingredients.first().line)
        // Three meals, no snack; a light breakfast.
        assertEquals(3, MealSlot.entries.size)
        for (w in 0..1) assertTrue(Nutrition.scheduled(MealSlot.BREAKFAST, w).macros.kcal < 500)
    }

    @Test fun shoppingListScalesTheRecipesForOneCookPerWeek() {
        val weekDays = Plan.weekDays(5)
        val list = Nutrition.shoppingList(p, weekDays)
        assertEquals(list.size, list.map { it.key }.toSet().size)
        assertEquals(list.map { it.dept.ordinal }.sorted(), list.map { it.dept.ordinal })
        // Every recipe batch covers 7 days of its meal.
        for (b in Nutrition.prepBatches(5)) assertTrue(b.recipe.title, b.batches * b.recipe.servings >= 7 * b.forMeal.recipeServings)
        val batches = Nutrition.prepBatches(5).associate { it.recipe.id to it.batches }
        val week5Ids = batches.keys
        if ("chicken-pepper-bake" in week5Ids) {
            assertEquals(3.0, batches.getValue("chicken-pepper-bake"), 0.0)          // 14 cups needed / 5 per batch
            assertTrue(list.any { it.label == "Boneless chicken breast" && it.amount.startsWith("3") })
        }
        assertEquals(listOf(4.0, 3.0), listOf(amountText(4.0), amountText(3.0)).map { it.toDouble() })
        assertEquals("2¼ lb", unitAmount(2.25, "lb"))
        assertEquals("1 cup", unitAmount(1.0, "cups"))
        assertEquals("2½ cans (15 oz)", unitAmount(2.5, "can (15 oz)"))
        // One line per item: no "Oil" in both tsp and Tbsp, and whole counts for things bought whole — both menus.
        assertEquals(list.size, list.map { it.label }.toSet().size)
        val other = Nutrition.shoppingList(p, Plan.weekDays(6))
        assertEquals(other.size, other.map { it.label }.toSet().size)
        for (n in listOf("Onion, medium", "Garlic", "Carrot, large", "Green bell pepper")) {
            list.firstOrNull { it.label == n }?.let { assertFalse(n, it.amount.contains("½")) }
        }
    }

    @Test fun noFishOrPorkAnywhere() {
        val words = listOf("pork", "fish", "salmon", "tuna", "bacon", "ham ", "sausage")
        for (m in Nutrition.meals + Nutrition.fuelOptions) {
            val text = (m.items.map { it.food.usdaName + " " + it.label } + listOfNotNull(m.recipe?.title) +
                m.recipe?.ingredients.orEmpty().map { it.line } + m.name).joinToString(" ")
            for (w in words) assertFalse("$w in ${m.name}", text.contains(w, ignoreCase = true))
        }
    }

    @Test fun theMenuActuallyDeliversTheDeficit() {
        // Over every cutting day of the plan: what the menu provides (that week's four meals + fuel servings at 250 kcal)
        // against maintenance. Rounding to whole fuel servings must not eat the deficit or deepen it.
        val days = Plan.days.filter { it.date >= LocalDate.of(2026, 11, 16) && Nutrition.cuttingDay(it) }
        var eaten = 0.0; var maintenance = 0.0; var protein = 0.0
        for (d in days) {
            val t = Nutrition.target(p, d)
            val meals = MealSlot.entries.map { Nutrition.scheduled(it, d.week).macros }
            eaten += meals.sumOf { it.kcal } + t.fuelServings * Nutrition.FUEL_KCAL
            protein += meals.sumOf { it.protein }
            maintenance += t.maintenanceKcal
        }
        val deficit = 1 - eaten / maintenance
        assertTrue("deficit $deficit", deficit in 0.16..0.20)
        assertTrue("protein ${protein / days.size}", protein / days.size >= 165)
    }

    @Test fun weighInAdvice() {
        assertNull(Nutrition.weighInAdvice(listOf(WeighIn(30, 184.0))))
        assertTrue(Nutrition.weighInAdvice(listOf(WeighIn(30, 184.0), WeighIn(37, 183.0)))!!.contains("on track"))
        assertTrue(Nutrition.weighInAdvice(listOf(WeighIn(30, 184.0), WeighIn(37, 181.0)))!!.contains("Add a fuel serving"))
    }
}

class StrengthMoveTest {
    private val mon = Plan.dayOn(LocalDate.of(2026, 11, 23))!!   // 3 mi easy + strength
    private val tue = Plan.dayOn(LocalDate.of(2026, 11, 24))!!   // rest

    @Test fun movingMondayStrengthToTuesday() {
        assertTrue(mon.strength.isPlanned && !tue.strength.isPlanned)
        assertTrue(Plan.canMoveStrength(mon.index, emptyMap()))
        val plan = Plan.withStrengthMoves(mapOf(mon.index to tue.index))
        assertFalse(plan[mon.index].strength.isPlanned)
        assertEquals(mon.strength, plan[tue.index].strength)
        assertEquals(mon.miles, plan[mon.index].miles, 0.0)          // the run stays on Monday
        assertTrue(plan[tue.index].isTrainingDay)
        // Monday with just the run logged now counts as done, not partial.
        assertEquals(DayStatus.DONE, completionStatus(plan[mon.index], RunLog(3.0), emptyList()))
        // Can't move it twice, and can't move onto a day that already has strength.
        assertFalse(Plan.canMoveStrength(mon.index, mapOf(mon.index to tue.index)))
        val wed = Plan.dayOn(LocalDate.of(2026, 11, 25))!!
        val thu = Plan.dayOn(LocalDate.of(2026, 11, 26))!!
        assertFalse(Plan.canMoveStrength(wed.index, emptyMap()))        // Wednesday has no strength
        assertTrue(thu.strength.isPlanned)
        val schedule = Schedule(emptyMap(), Settings(), mon.date, plan)
        assertEquals(mon.strength, schedule[tue.index]!!.day.strength)
    }
}

class DaySwapTest {
    @Test fun thanksgivingWorkoutSwapsWithFridayRest() {
        val thu = Plan.dayOn(LocalDate.of(2026, 11, 26))!!   // 3 mi easy + strength
        val fri = Plan.dayOn(LocalDate.of(2026, 11, 27))!!   // rest
        assertTrue(thu.isTrainingDay && !fri.isTrainingDay)
        assertTrue(Plan.canSwapDay(thu.index, emptySet(), emptyMap()))
        val plan = Plan.withStrengthMoves(emptyMap(), setOf(thu.index))
        assertFalse(plan[thu.index].isTrainingDay)
        assertEquals(thu.miles, plan[fri.index].miles, 0.0)
        assertEquals(thu.strength, plan[fri.index].strength)
        assertEquals(thu.text, plan[fri.index].text)
        assertEquals(fri.date, plan[fri.index].date)               // dates don't move
        assertFalse(Plan.canSwapDay(thu.index, setOf(thu.index), emptyMap()))
        assertFalse(Plan.canSwapDay(fri.index, setOf(thu.index), emptyMap()))
        // Can't swap onto a training day.
        val wed = Plan.dayOn(LocalDate.of(2026, 11, 25))!!
        assertFalse(Plan.canSwapDay(wed.index, emptySet(), emptyMap()))
        val s = Schedule(emptyMap(), Settings(), thu.date, plan)
        assertEquals(thu.run, s[fri.index]!!.day.run)
    }
}
