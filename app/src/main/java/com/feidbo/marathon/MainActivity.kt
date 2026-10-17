package com.feidbo.marathon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feidbo.marathon.core.Plan
import com.feidbo.marathon.ui.AppViewModel
import com.feidbo.marathon.ui.CatchUpDialog
import com.feidbo.marathon.ui.CelebrationOverlay
import com.feidbo.marathon.ui.ExerciseScreen
import com.feidbo.marathon.ui.LogScreen
import com.feidbo.marathon.ui.MissedDialog
import com.feidbo.marathon.ui.OnboardingScreen
import com.feidbo.marathon.ui.ProgressScreen
import com.feidbo.marathon.ui.SettingsScreen
import com.feidbo.marathon.ui.TodayScreen
import com.feidbo.marathon.ui.theme.MarathonTheme
import java.time.LocalDate

sealed interface Screen {
    data object Progress : Screen
    data object Settings : Screen
    data class Log(val dayIndex: Int) : Screen
    data class Exercise(val id: String, val dayIndex: Int) : Screen
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MarathonTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    App(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDate()
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val celebration by vm.celebration.collectAsStateWithLifecycle()

    if (!state.data.onboarded) {
        OnboardingScreen(onDone = vm::finishOnboarding)
        return
    }

    // Today is the home screen; everything else stacks on top of it.
    val stack = remember { mutableStateListOf<Screen>() }
    var date by remember { mutableStateOf(clampToPlan(state.today)) }
    var missedFor by remember { mutableStateOf<Int?>(null) }
    var catchUp by remember { mutableStateOf(false) }
    fun push(s: Screen) = stack.add(s)
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    BackHandler(enabled = stack.isEmpty() && date != clampToPlan(state.today)) { date = clampToPlan(state.today) }

    when (val top = stack.lastOrNull()) {
        null -> TodayScreen(
            state = state,
            date = date,
            onDateChange = { date = it.coerceIn(Plan.start, Plan.raceDay) },
            onOpenProgress = { push(Screen.Progress) },
            onLog = { push(Screen.Log(it)) },
            onMissed = { missedFor = it },
            onOpenExercise = { id, day -> push(Screen.Exercise(id, day)) },
            onToggleStretch = vm::toggleStretch,
            onUndo = vm::clearLog,
            onCatchUp = { catchUp = true },
        )
        Screen.Progress -> ProgressScreen(
            state = state,
            onBack = ::pop,
            onOpenDay = { date = it; stack.clear() },
            onOpenSettings = { push(Screen.Settings) },
            onOpenExercise = { push(Screen.Exercise(it, -1)) },
        )
        Screen.Settings -> SettingsScreen(state, onBack = ::pop, onSave = vm::updateSettings, onReset = { vm.resetAll(); stack.clear() })
        is Screen.Log -> LogScreen(state, top.dayIndex, onBack = ::pop) { run, lifts ->
            vm.saveWorkout(top.dayIndex, run, lifts)
            pop()
        }
        is Screen.Exercise -> ExerciseScreen(state, top.id, top.dayIndex, onBack = ::pop)
    }

    missedFor?.let { idx ->
        MissedDialog(onDismiss = { missedFor = null }) { reason ->
            vm.markMissed(idx, reason)
            missedFor = null
        }
    }
    if (catchUp) {
        CatchUpDialog(onDismiss = { catchUp = false }) { reason ->
            vm.markUnloggedMissed(reason)
            catchUp = false
        }
    }
    celebration?.let { CelebrationOverlay(it, onDismiss = vm::dismissCelebration) }
}

private fun clampToPlan(d: LocalDate): LocalDate = d.coerceIn(Plan.start, Plan.raceDay)
