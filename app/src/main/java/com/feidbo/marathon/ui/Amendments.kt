package com.feidbo.marathon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Bump when there's a new "What's new" note to show once after updating. */
const val WHATS_NEW_VERSION = 2

/** One change from the original plan document, with what it rests on (docs/decisions.md, docs/evidence/). */
private class Amendment(val title: String, val was: String, val now: String, val evidence: String, val strength: String)

private val amendments = listOf(
    Amendment(
        "Strength, Phase 1 (to Dec 13)",
        "Leg press, hip thrust and leg curl 2 × 12; other machines 2 sets of 8–15. Twice a week.",
        "Main lifts (split squat, hip thrust, single-leg RDL) 3 × 8–10; smaller lifts 2 sets. About 3 reps left in the tank.",
        "More weekly sets give a bit more strength (Ralston 2017); 8–12 reps suits people new to lifting (ACSM 2009).",
        "Moderate",
    ),
    Amendment(
        "Strength, Phase 2 (Dec 14 – Feb 7)",
        "Leg press and hip thrust 3 × 6 (start light the first week); leg curl 2 × 8; others 2 sets.",
        "Main lifts 3 × 5–6, heavy (about 2 reps left), plus 3 × 10 pogo hops once your calf is cleared.",
        "Heavy loads, and heavy loads combined with jumping, improved runners' economy and performance most in two " +
            "meta-analyses (Llanos-Lagos 2024). Hop sets and reps are an app default.",
        "Moderate",
    ),
    Amendment(
        "Strength, Phase 3 (Feb 8 – Apr 5)",
        "Once a week: leg press and hip thrust 2 × 5; others 1 set.",
        "Once a week, heavy: 2 sets on the main lifts, 1 set on the others, plus 2 × 10 pogo hops once your calf is cleared.",
        "One session a week with a maintained load keeps strength (Spiering 2021).",
        "Moderate",
    ),
    Amendment(
        "Dumbbell / bodyweight routine",
        "Leg press, leg-press calf raise, hip thrust machine, hip abductor, seated leg curl, assisted pull-up.",
        "Bulgarian split squat · single-leg calf raise, knee straight · single-leg calf raise, knee bent (new) · " +
            "dumbbell hip thrust · side-lying hip abduction (then side plank) · single-leg RDL (or sliding leg curl) · " +
            "assisted pull-up only when there's a machine. Each has a bodyweight version. Your machine history is kept; " +
            "the new exercises start fresh benchmarks.",
        "Matched by muscle. Split squats built strength as well as back squats (Speirs 2016). Knee straight vs bent " +
            "shifts calf work between gastrocnemius and soleus (Hébert-Losier 2012; Kovács 2024). Side-lying abduction " +
            "and side plank ranked highest for the side of the hip (Distefano 2009; Boren 2011). Muscle-activity studies " +
            "show what an exercise works, not which builds the most strength (Vigotsky 2017).",
        "Weak to moderate",
    ),
    Amendment(
        "Stretching",
        "Kneeling quad stretch, 3 min daily.",
        "Quad stretch plus two wall calf stretches (knee straight, knee bent), shown as optional mobility. Long holds " +
            "after runs or in the evening; under 60 s if you stretch before a run. Calf stretches pause in calf stages 1–2.",
        "Stretching hasn't been shown to prevent running injuries (Cochrane, Yeung 2011; Pope 2000; Lauersen 2014). " +
            "It does improve range of motion; long holds right before effort slightly reduce performance (Behm 2016).",
        "Strong (no injury prevention)",
    ),
    Amendment(
        "Calf guidance (new)",
        "—",
        "Turn it on for a tight or sore calf: four stages (Settle, Rebuild, Strengthen, Return), a calf test log, and the " +
            "experts' running rules. It never changes your schedule by itself. Hold times and sets are app defaults.",
        "No randomized trials exist for calf-strain rehab; this follows expert consensus and reviews (Green 2022; " +
            "Halabchi 2024; Pagan-Rosado 2025). Return benchmark: 30+ single-leg raises each leg, within 10%.",
        "Expert opinion",
    ),
    Amendment(
        "Easy runs",
        "Easy = 11:45–13:00 /mi.",
        "Easy is judged by effort first (talk test, 3–4/10), then by an optional personal heart-rate cap. A faster pace " +
            "is no longer flagged when effort and heart rate say easy. Pace zones aren't changed; the Mar 7 half resets them.",
        "The talk test tracks the first ventilatory threshold (Persinger 2004; Reed 2014); that threshold sits at a very " +
            "individual heart rate, so no age formula is used (age formulas such as Tanaka 2001 predict only an average).",
        "Moderate",
    ),
    Amendment(
        "Speed-work instructions",
        "One line of text per workout.",
        "Numbered steps you can copy into a Garmin workout, plus what each session is for. Every number comes from the " +
            "plan document; the steps are checked by automated tests against every workout.",
        "Your plan document is the source; terms follow the training literature (Faude 2009; Bok 2022).",
        "—",
    ),
    Amendment(
        "Widget",
        "Needed a 4 × 2 space.",
        "Fits a 2 × 2 space by default and fills more room when you make it bigger.",
        "Android developer guidance on widget sizes and responsive layouts.",
        "—",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmendmentsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Changes from your plan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Everything else follows your original plan document. Full sources are in the app's repository " +
                    "(docs/decisions.md and docs/evidence/); the evidence was gathered before these decisions were made.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            amendments.forEach { a ->
                SectionCard {
                    Row {
                        Text(a.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    if (a.strength != "—") {
                        Text("Evidence: ${a.strength}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Labeled("Was", a.was)
                    Labeled("Now", a.now)
                    Labeled("Why", a.evidence)
                }
            }
            SectionCard {
                Text("Calf guidance stages", style = MaterialTheme.typography.titleMedium)
                CalfStagesSummary()
            }
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit, onOpenAmendments: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new 🦆") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "🏋️ Dumbbell & bodyweight routine — no machines needed (the assisted pull-up is optional). Heavier, " +
                        "more effective sets, with pogo hops from mid-December.",
                    "🦵 Calf guidance: switch it on from Today for your tight calf. Four stages, a calf test, and run-day rules.",
                    "🧘 Stretching: quad plus two calf stretches, shown honestly as optional mobility.",
                    "❤️ Log your Garmin average heart rate. Easy runs are judged by effort and heart rate, not pace alone.",
                    "⌚ Speed workouts as numbered steps you can put on your watch.",
                    "📱 The widget now fits a 2 × 2 space.",
                    "✅ Your logged progress, XP and badges are all kept.",
                ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Let's go") } },
        dismissButton = { TextButton(onClick = onOpenAmendments) { Text("See every change") } },
    )
}
