package com.feidbo.marathon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.feidbo.marathon.ui.XpToast
import com.feidbo.marathon.ui.theme.MarathonTheme
import java.time.LocalDate

/** Full-screen pages that open on top of the tabs. */
sealed interface Screen {
    data class Log(val dayIndex: Int) : Screen
    data class Exercise(val id: String, val dayIndex: Int) : Screen
}

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Today),
    PROGRESS("Progress", Icons.Filled.Insights),
    SETTINGS("Settings", Icons.Filled.Settings),
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
    val toast by vm.toast.collectAsStateWithLifecycle()

    if (!state.data.onboarded) {
        OnboardingScreen(onDone = vm::finishOnboarding)
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val stack = remember { mutableStateListOf<Screen>() }
    var date by remember { mutableStateOf(clampToPlan(state.today)) }
    var missedFor by remember { mutableStateOf<Int?>(null) }
    var catchUp by remember { mutableStateOf(false) }
    fun push(s: Screen) = stack.add(s)
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    // A new day (app left open past midnight, or resumed the next morning) moves Today along with it.
    LaunchedEffect(state.today) { date = clampToPlan(state.today) }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.TODAY) { tab = Tab.TODAY }
    BackHandler(enabled = stack.isEmpty() && tab == Tab.TODAY && date != clampToPlan(state.today)) {
        date = clampToPlan(state.today)
    }

    val top = stack.lastOrNull()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (top == null) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                if (t == Tab.TODAY && tab == Tab.TODAY) date = clampToPlan(state.today)
                                tab = t
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (top) {
                // Keyed so each day or exercise gets fresh screen state.
                is Screen.Log -> key(top) {
                    LogScreen(state, top.dayIndex, onBack = ::pop) { run, lifts ->
                        vm.saveWorkout(top.dayIndex, run, lifts)
                        pop()
                    }
                }
                is Screen.Exercise -> key(top) { ExerciseScreen(state, top.id, top.dayIndex, onBack = ::pop) }
                null -> when (tab) {
                    Tab.TODAY -> TodayScreen(
                        state = state,
                        date = date,
                        onDateChange = { date = it.coerceIn(Plan.start, Plan.raceDay) },
                        onOpenProgress = { tab = Tab.PROGRESS },
                        onLog = { push(Screen.Log(it)) },
                        onMissed = { missedFor = it },
                        onOpenExercise = { id, day -> push(Screen.Exercise(id, day)) },
                        onToggleStretch = vm::toggleStretch,
                        onUndo = vm::clearLog,
                        onCatchUp = { catchUp = true },
                    )
                    Tab.PROGRESS -> ProgressScreen(
                        state = state,
                        onOpenDay = { date = it; tab = Tab.TODAY },
                        onOpenExercise = { push(Screen.Exercise(it, -1)) },
                    )
                    Tab.SETTINGS -> SettingsScreen(
                        state,
                        onSaved = { tab = Tab.TODAY },
                        onSave = vm::updateSettings,
                        onReset = { vm.resetAll(); tab = Tab.TODAY },
                    )
                }
            }
            XpToast(
                toast?.text, key = toast?.id, onShown = { toast?.let(vm::clearToast) },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        }
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
