package com.feidbo.marathon

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.feidbo.marathon.core.CalfStatus
import com.feidbo.marathon.core.DayLog
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.ExerciseLog
import com.feidbo.marathon.core.MissReason
import com.feidbo.marathon.core.Plan
import com.feidbo.marathon.core.RunKind
import com.feidbo.marathon.core.RunLog
import com.feidbo.marathon.core.Schedule
import com.feidbo.marathon.core.SetLog
import com.feidbo.marathon.core.Settings
import com.feidbo.marathon.core.Side
import com.feidbo.marathon.core.StrengthKind
import com.feidbo.marathon.data.AppData
import com.feidbo.marathon.ui.AppViewModel
import com.feidbo.marathon.ui.CalfActions
import com.feidbo.marathon.ui.LogScreen
import com.feidbo.marathon.ui.TodayScreen
import com.feidbo.marathon.ui.UiState
import com.feidbo.marathon.ui.theme.MarathonTheme
import com.feidbo.marathon.widget.TodayWidget
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Screenshots for checking the layout by eye (docs/decisions.md §8): the widget at 2×2 and other sizes,
 * the "Done!" card, large text, and the new cards. Record with ./gradlew :app:recordPaparazziDebug.
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 4200, softButtons = false),
        maxPercentDifference = 0.1,
        useDeviceResolution = true,
    )

    private val noCalf = CalfActions({}, {}, {})

    private fun state(today: LocalDate, data: AppData): UiState {
        val schedule = Schedule(data.logs, data.settings, today)
        return UiState(data, today, schedule, AppViewModel.progressFor(data, schedule, today))
    }

    private fun firstDay(from: LocalDate, pred: (com.feidbo.marathon.core.PlannedDay) -> Boolean) =
        Plan.days.first { it.date >= from && pred(it) }

    private val runAndLift = firstDay(LocalDate.of(2026, 11, 9)) { it.run.isRun && it.strength == StrengthKind.PHASE1 }
    private val tempo = firstDay(LocalDate.of(2026, 12, 14)) { it.run == RunKind.TEMPO }
    private val rest = firstDay(LocalDate.of(2026, 11, 9)) { !it.isTrainingDay }
    private val long = firstDay(LocalDate.of(2027, 2, 1)) { it.run == RunKind.LONG }
    private val phase2Lift = firstDay(LocalDate.of(2026, 12, 14)) { it.strength == StrengthKind.PHASE2 }

    private fun base() = AppData(onboarded = true, whatsNewSeen = 2, settings = Settings(easyHrCap = 150))

    @Composable
    private fun Today(s: UiState, date: LocalDate) {
        MarathonTheme {
            Box(Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                TodayScreen(s, date, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, noCalf)
            }
        }
    }

    private fun doneData(): AppData {
        val d = runAndLift
        val lifts = listOf(
            ExerciseLog("split_squat", List(3) { SetLog(25.0, 10) }, rir = 3, perSide = true),
            ExerciseLog("hip_thrust_db", List(3) { SetLog(50.0, 10) }, rir = 3),
        )
        // A run that triggers long coach tips, to check the card with plenty of text.
        val log = DayLog(d.index, DayStatus.DONE, RunLog(d.miles, paceSec = 625, effort = 6, avgHr = 162), lifts, xp = 47,
            plannedMiles = d.miles)
        return base().copy(logs = mapOf(d.index to log), stretchDays = setOf(d.index))
    }

    @Test fun doneCard() {
        paparazzi.snapshot { Today(state(runAndLift.date, doneData()), runAndLift.date) }
    }

    @Test fun doneCardLargeText() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenHeight = 4200, softButtons = false, fontScale = 1.5f))
        paparazzi.snapshot { Today(state(runAndLift.date, doneData()), runAndLift.date) }
    }

    @Test fun restTakenCardLargeText() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenHeight = 4200, softButtons = false, fontScale = 1.5f))
        val d = runAndLift
        val data = base().copy(logs = mapOf(d.index to DayLog(d.index, DayStatus.MISSED, missReason = MissReason.SICK_SYSTEMIC, xp = 2)))
        paparazzi.snapshot { Today(state(d.date, data), d.date) }
    }

    @Test fun calfGuidanceOnARunDay() {
        val d = runAndLift
        val data = base().copy(calf = CalfStatus(flagged = true, stage = 1, side = Side.RIGHT, sinceDay = d.index))
        paparazzi.snapshot { Today(state(d.date, data), d.date) }
    }

    @Test fun tempoSteps() {
        paparazzi.snapshot { Today(state(tempo.date, base()), tempo.date) }
    }

    @Test fun strengthLogging() {
        val day = phase2Lift
        val prior = Plan.days.last { it.date < day.date && it.strength == StrengthKind.PHASE1 }
        val history = DayLog(prior.index, DayStatus.DONE, null, listOf(
            ExerciseLog("split_squat", List(3) { SetLog(30.0, 10) }, rir = 3, perSide = true),
            ExerciseLog("single_leg_rdl", List(3) { SetLog(0.0, 10) }, rir = 3, perSide = false, bodyweight = true),
        ), xp = 25)
        val data = base().copy(logs = mapOf(prior.index to history))
        paparazzi.snapshot {
            // LogScreen asks for notification permission through an activity-result launcher.
            val owner = object : androidx.activity.result.ActivityResultRegistryOwner {
                override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                    override fun <I, O> onLaunch(
                        requestCode: Int,
                        contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                        input: I,
                        options: androidx.core.app.ActivityOptionsCompat?,
                    ) = Unit
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.activity.compose.LocalActivityResultRegistryOwner provides owner,
            ) {
                MarathonTheme { LogScreen(state(day.date, data), day.index, null, {}, {}) { _, _ -> } }
            }
        }
    }

    // ---- Widget -------------------------------------------------------------------------------

    private fun widget(name: String, size: TodayWidget.Size, wDp: Int, hDp: Int, day: com.feidbo.marathon.core.PlannedDay) {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenHeight = 900, screenWidth = 1080, softButtons = false))
        val content = TodayWidget.content(base().copy(stretchDays = emptySet()), day.date)
        val ctx = paparazzi.context
        val density = ctx.resources.displayMetrics.density
        val frame = FrameLayout(ctx).apply { setBackgroundColor(Color.parseColor("#FF3A4A5C")) }
        val rv = TodayWidget.views(ctx, size, content)
        val view: View = rv.apply(ctx, frame)
        frame.addView(view, FrameLayout.LayoutParams((wDp * density).toInt(), (hDp * density).toInt(), Gravity.CENTER))
        paparazzi.snapshot(frame, name)
    }

    // Sizes from the Android sizing table (docs/evidence/widget.md, W1).
    @Test fun widget2x2PortraitRunAndLift() = widget("2x2_portrait_run_lift", TodayWidget.Size.TALL, 130, 220, runAndLift)
    @Test fun widget2x2PortraitRest() = widget("2x2_portrait_rest", TodayWidget.Size.TALL, 130, 220, rest)
    @Test fun widget2x2PortraitTempo() = widget("2x2_portrait_tempo", TodayWidget.Size.TALL, 130, 220, tempo)
    @Test fun widget2x2PortraitLong() = widget("2x2_portrait_long", TodayWidget.Size.TALL, 130, 220, long)
    @Test fun widget2x2LandscapeRunAndLift() = widget("2x2_landscape_run_lift", TodayWidget.Size.WIDE, 269, 117, runAndLift)
    @Test fun widget2x2LandscapeRest() = widget("2x2_landscape_rest", TodayWidget.Size.WIDE, 269, 117, rest)
    @Test fun widget4x2() = widget("4x2_tempo", TodayWidget.Size.LARGE, 276, 220, tempo)
    @Test fun widgetCompact() = widget("compact_run_lift", TodayWidget.Size.COMPACT, 120, 110, runAndLift)
}
