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

    @Test fun easyTooFastWarns() {
        val tips = runFeedback(easyDay, RunLog(3.0, paceSec = 10 * 60), settings, 3.75)
        assertTrue(tips.any { it.level == TipLevel.WARN && it.text.contains("faster than easy") })
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
    private val legPress = p1.items.first { it.exercise.id == "leg_press" }
    private val calf = p1.items.first { it.exercise.id == "calf_raise" }
    private val pull = p1.items.first { it.exercise.inverted }
    private fun day(y: Int, m: Int, d: Int) = Plan.dayOn(LocalDate.of(y, m, d))!!
    private fun entry(day: PlannedDay, id: String, w: Double, reps: List<Int>, rir: Int? = null, feel: Feel? = null) =
        ExerciseHistoryEntry(day.index, day.strength, ExerciseLog(id, reps.map { SetLog(w, it) }, feel, rir))

    private val mon = day(2026, 10, 19)
    private val thu = day(2026, 10, 22)
    private val nextMon = day(2026, 10, 26)

    @Test fun easierThanTargetAddsAboutFivePercent() {
        val a = loadAdvice(legPress, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "leg_press", 140.0, listOf(12, 12), rir = 4)))!!
        assertEquals(LoadChange.UP, a.change)
        assertEquals(150.0, a.weight, 0.0) // +10 lb minimum step on a big lift
    }

    @Test fun muchEasierThanTargetTakesBiggerJump() {
        val a = loadAdvice(legPress, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "leg_press", 140.0, listOf(12, 12), rir = 6)))!!
        assertEquals(LoadChange.BIG_UP, a.change)
        assertEquals(160.0, a.weight, 0.0)
        val small = loadAdvice(calf, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "calf_raise", 60.0, listOf(15, 15), rir = 6)))!!
        assertEquals(70.0, small.weight, 0.0)
    }

    @Test fun onTargetHoldsAndMissedRepsBacksOff() {
        val same = loadAdvice(legPress, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "leg_press", 140.0, listOf(12, 12), rir = 3)))!!
        assertEquals(LoadChange.SAME, same.change)
        assertEquals(140.0, same.weight, 0.0)
        val down = loadAdvice(legPress, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "leg_press", 140.0, listOf(10, 8), rir = 0)))!!
        assertEquals(LoadChange.DOWN, down.change)
        assertEquals(125.0, down.weight, 0.0)
    }

    @Test fun twoForTwoWithoutRir() {
        val once = loadAdvice(legPress, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "leg_press", 140.0, listOf(12, 12))))!!
        assertEquals(LoadChange.SAME, once.change)
        val twice = loadAdvice(legPress, StrengthKind.PHASE1, nextMon.date,
            listOf(entry(mon, "leg_press", 140.0, listOf(12, 12)), entry(thu, "leg_press", 140.0, listOf(12, 12))))!!
        assertEquals(LoadChange.UP, twice.change)
    }

    @Test fun olderFeelLogsStillWork() {
        val a = loadAdvice(legPress, StrengthKind.PHASE1, thu.date, listOf(entry(mon, "leg_press", 100.0, listOf(12, 12), feel = Feel.EASY)))!!
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
        val rx = strengthSession(StrengthKind.PHASE2)!!.items.first { it.exercise.id == "leg_press" }
        val a = loadAdvice(rx, StrengthKind.PHASE2, firstP2.date, listOf(entry(lastP1, "leg_press", 200.0, listOf(12, 12), rir = 3)))!!
        assertEquals(LoadChange.NEW_TARGET, a.change)
        // e1RM = 200 × (1 + 15/30) = 300; 6 reps with 2 left = 300 / (1 + 8/30) ≈ 237; ×0.9 first heavy week ≈ 213 → 210.
        assertEquals(210.0, a.weight, 0.0)
    }

    @Test fun longBreakEasesBackIn() {
        val a = loadAdvice(legPress, StrengthKind.PHASE1, LocalDate.of(2026, 11, 23),
            listOf(entry(mon, "leg_press", 140.0, listOf(12, 12), rir = 3)))!!
        assertEquals(LoadChange.RETURNING, a.change)
        assertEquals(125.0, a.weight, 0.0)
    }

    @Test fun lightDayIsSixtyPercent() {
        val rx = strengthSession(StrengthKind.LIGHT)!!.items.first { it.exercise.id == "leg_press" }
        val p3 = day(2027, 2, 22)
        val a = loadAdvice(rx, StrengthKind.LIGHT, LocalDate.of(2027, 3, 1), listOf(entry(p3, "leg_press", 250.0, listOf(5, 5), rir = 2)))!!
        assertEquals(150.0, a.weight, 0.0)
    }

    @Test fun noHistoryNoAdvice() {
        assertNull(loadAdvice(legPress, StrengthKind.PHASE1, thu.date, emptyList()))
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
