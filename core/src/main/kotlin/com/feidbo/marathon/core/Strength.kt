package com.feidbo.marathon.core

/** How loads move for an exercise: big lower-body lifts take bigger jumps (ACSM 2009). */
enum class LoadType { LOWER_COMPOUND, LOWER_SMALL, ASSISTED, BODYWEIGHT }

data class Exercise(
    val id: String,
    val name: String,
    val block: String,          // "1a", "2c", ... (the number is the circuit block)
    val machine: String,        // equipment needed
    val muscles: String,
    val steps: List<String>,
    val cues: List<String>,
    /** Assisted pull-up: more counterweight = more help, so "progress" means *less* weight. */
    val inverted: Boolean = false,
    val weightLabel: String = "Weight (lb)",
    val load: LoadType = LoadType.LOWER_SMALL,
    /** Done one side at a time: reps are per leg, so every set is the reps on each leg. */
    val perSide: Boolean = false,
    /** A no-equipment version for days without dumbbells (null when the exercise is already bodyweight). */
    val bodyweightName: String? = null,
    val bodyweightSteps: List<String> = emptyList(),
    /** Skip it freely (the assisted pull-up needs a machine). */
    val optional: Boolean = false,
    /** Part of the calf work that the calf guidance can take over. */
    val calf: Boolean = false,
    /** Old machine exercises: kept so past logs still show their names; never prescribed. */
    val retired: Boolean = false,
    /** Where the choice comes from (docs/evidence + docs/decisions.md). */
    val why: String = "",
) {
    fun repsText(reps: Int): String = if (perSide) "$reps/leg" else "$reps"
    val usesWeight: Boolean get() = load != LoadType.BODYWEIGHT
}

/** [repsLabel] is shown instead of the number when the plan gives a range (e.g. "8–10"). */
data class Prescription(val exercise: Exercise, val sets: Int, val reps: Int, val repsLabel: String? = null) {
    val repsShown: String get() = (repsLabel ?: reps.toString()) + if (exercise.perSide) "/leg" else ""
}

data class StrengthSession(
    val kind: StrengthKind,
    val items: List<Prescription>,
    val effort: String,
    val rest: String,
    val minutes: String,
    val notes: List<String>,
    val restSec: Int,           // rest between sets
    val targetRir: Int,         // reps to leave in the tank
)

object Exercises {
    // ---- Current routine: dumbbells or bodyweight (docs/decisions.md §2) ------------------------

    val splitSquat = Exercise(
        id = "split_squat", name = "Bulgarian split squat", block = "1a",
        machine = "Two dumbbells and a bench, bed or step behind you",
        muscles = "Quads, glutes", load = LoadType.LOWER_COMPOUND, perSide = true,
        weightLabel = "Each dumbbell (lb)",
        steps = listOf(
            "Stand about two steps in front of a bench (or bed edge), a dumbbell in each hand.",
            "Rest the top of your back foot on the bench behind you.",
            "Lower straight down until your front thigh is about level with the floor; the back knee drops toward the floor.",
            "Push through your whole front foot to stand back up. Do all reps, then switch legs — that's one set.",
        ),
        cues = listOf(
            "Most of your weight stays on the front foot.",
            "Front knee tracks over your toes.",
            "Control the way down; drive up.",
        ),
        bodyweightName = "Bulgarian split squat (bodyweight)",
        bodyweightSteps = listOf("Same movement with no dumbbells; slow the lowering (about 3 seconds) to make it harder."),
        why = "Replaces the leg press. In a training study, rear-foot-elevated split squats built strength as well as " +
            "back squats (Speirs 2016), with similar quad activity (Andersen 2014).",
    )
    val calfRaiseStraight = Exercise(
        id = "calf_raise_straight", name = "Single-leg calf raise (knee straight)", block = "1b",
        machine = "A step and one dumbbell",
        muscles = "Calves — mostly gastrocnemius", perSide = true, calf = true,
        weightLabel = "Dumbbell (lb)",
        steps = listOf(
            "Stand on one foot with the ball of the foot on a step edge, dumbbell in the same-side hand, other hand on a wall.",
            "Keep the knee straight (not locked).",
            "Lower your heel slowly below the step for a full stretch.",
            "Rise as high as you can onto the ball of the foot, pause, then lower slowly. All reps, then the other leg.",
        ),
        cues = listOf("Full range: all the way down, all the way up.", "Slow on the way down."),
        bodyweightName = "Single-leg calf raise, knee straight (bodyweight)",
        bodyweightSteps = listOf("Same movement with no dumbbell."),
        why = "Knee straight shifts the work toward the gastrocnemius (Hébert-Losier 2012; Kovács 2024). " +
            "12 per leg, as you asked.",
    )
    val calfRaiseBent = Exercise(
        id = "calf_raise_bent", name = "Single-leg calf raise (knee bent)", block = "1c",
        machine = "A step and one dumbbell",
        muscles = "Calves — more soleus", perSide = true, calf = true,
        weightLabel = "Dumbbell (lb)",
        steps = listOf(
            "Same set-up as the straight-knee raise, but bend the knee to about 30–45° and keep it bent.",
            "Lower the heel slowly below the step.",
            "Rise onto the ball of the foot without straightening the knee, then lower slowly. All reps, then the other leg.",
        ),
        cues = listOf("Keep the knee bend the same the whole set.", "Slow on the way down."),
        bodyweightName = "Single-leg calf raise, knee bent (bodyweight)",
        bodyweightSteps = listOf("Same movement with no dumbbell."),
        why = "Knee bent shifts load toward the soleus (Kovács 2024: soleus activity 15–28% higher); experts treat " +
            "soleus capacity as central to calf-strain recovery (Green 2022).",
    )
    val pullUp = Exercise(
        id = "assisted_pull_up", name = "Assisted pull-up (optional)", block = "1d",
        machine = "Assisted pull-up machine — skip it when there isn't one",
        muscles = "Lats, upper back, biceps",
        steps = listOf(
            "Set the counterweight. More counterweight means more help.",
            "Kneel (or stand) on the platform and grab the handles a little wider than your shoulders.",
            "Start from straight arms. Pull your chest toward the bar, driving your elbows down.",
            "Lower slowly to straight arms.",
        ),
        cues = listOf(
            "Set it so the last two reps are hard.",
            "Pull your chest toward the bar; lower slowly.",
            "Use less counterweight as you get stronger.",
        ),
        inverted = true,
        weightLabel = "Counterweight (lb)",
        load = LoadType.ASSISTED,
        optional = true,
        why = "Kept as an optional extra, as you asked.",
    )
    val hipThrust = Exercise(
        id = "hip_thrust_db", name = "Dumbbell hip thrust", block = "2a",
        machine = "One dumbbell and a bench or bed edge",
        muscles = "Glutes, hamstrings", load = LoadType.LOWER_COMPOUND,
        weightLabel = "Dumbbell (lb)",
        steps = listOf(
            "Sit on the floor with your upper back against the bench edge, a dumbbell across your hips (hold it with both hands).",
            "Feet flat about hip-width, close enough that your shins are vertical at the top.",
            "Tuck your chin and drive through your heels to lift your hips until your body is flat from knees to shoulders.",
            "Pause one second at the top, then lower under control.",
        ),
        cues = listOf("Chin tucked.", "Push through your heels.", "Pause one second at the top."),
        bodyweightName = "Single-leg hip thrust (bodyweight)",
        bodyweightSteps = listOf(
            "Same set-up with no dumbbell; lift one foot and drive up through the other. All reps, then switch legs.",
        ),
        why = "Replaces the hip thrust machine; hip thrusts give very high glute activation (Neto 2020).",
    )
    val sideAbduction = Exercise(
        id = "side_abduction", name = "Side-lying hip abduction", block = "2b",
        machine = "Floor space",
        muscles = "Glute medius (side of the hip)", load = LoadType.BODYWEIGHT, perSide = true,
        steps = listOf(
            "Lie on your side, bottom knee bent for balance, top leg straight in line with your body.",
            "Turn the top toes slightly down and lift the leg up and slightly back to about 45°.",
            "Hold one second, lower slowly. All reps, then the other side.",
            "When 15 are easy: do it from a side plank (forearm and bottom knee down), lifting the top leg.",
        ),
        cues = listOf("Don't roll backwards.", "Lead with the heel."),
        why = "Replaces the hip abductor machine. Side-lying abduction was the best gluteus medius exercise of 12 " +
            "(Distefano 2009); side plank with abduction was highest of 18 (Boren 2011).",
    )
    val singleLegRdl = Exercise(
        id = "single_leg_rdl", name = "Single-leg Romanian deadlift", block = "2c",
        machine = "One dumbbell",
        muscles = "Hamstrings, glutes", load = LoadType.LOWER_COMPOUND, perSide = true,
        weightLabel = "Dumbbell (lb)",
        steps = listOf(
            "Stand on one leg with a soft knee, dumbbell in the opposite hand.",
            "Hinge at the hip: chest goes down as the free leg goes back, back stays flat.",
            "Lower until you feel a strong hamstring stretch (dumbbell about mid-shin).",
            "Drive the hip forward to stand tall. All reps, then the other leg.",
        ),
        cues = listOf("Hips stay square to the floor.", "Back flat; move at the hip, not the waist."),
        bodyweightName = "Sliding leg curl (bodyweight)",
        bodyweightSteps = listOf(
            "Lie on your back, heels on a towel on a smooth floor, hips lifted in a bridge.",
            "Slide your heels out until your legs are nearly straight, keeping the hips up, then curl them back in.",
        ),
        why = "Replaces the leg curl. Single-leg deadlifts were among the top glute exercises (Distefano 2009) and " +
            "train the hamstrings; leg curls including sliding versions show high hamstring activity (Hegyi 2019).",
    )
    val pogoHops = Exercise(
        id = "pogo_hops", name = "Pogo hops", block = "3a",
        machine = "Floor space",
        muscles = "Calves and Achilles — springiness", load = LoadType.BODYWEIGHT,
        steps = listOf(
            "Stand tall on both feet, knees nearly straight.",
            "Bounce quickly off the balls of your feet with short ground contacts, like skipping without a rope.",
            "Once these are easy and pain-free, progress to single-leg hops.",
        ),
        cues = listOf("Quick and light — 'hot ground'.", "Stop if your calf hurts."),
        why = "Heavy strength plus jumping work had the largest effects on running economy and performance " +
            "(Llanos-Lagos 2024). Sets and reps are an app default, not from a study. Only shown once your calf is cleared.",
    )

    // ---- Daily stretches (docs/decisions.md §3) --------------------------------------------------

    val quadStretch = Exercise(
        id = "quad_stretch", name = "Kneeling quad stretch", block = "Daily", machine = "A pad or folded towel",
        muscles = "Quads, hip flexors", load = LoadType.BODYWEIGHT,
        steps = listOf(
            "Kneel on a pad with one knee down and the other foot flat in front.",
            "Reach back, hold the back ankle, and draw that heel toward your glute.",
            "Tuck your pelvis under and keep your hips square — feel it in the front of the thigh and hip.",
            "Hold 90 seconds, then switch sides.",
        ),
        cues = listOf(
            "For range of motion and comfort — stretching hasn't been shown to prevent injuries.",
            "Long holds after runs or in the evening, not right before a run.",
            "Back off the depth if your knee complains.",
        ),
    )
    val calfStretchStraight = Exercise(
        id = "calf_stretch_straight", name = "Wall calf stretch (knee straight)", block = "Daily", machine = "A wall",
        muscles = "Gastrocnemius", load = LoadType.BODYWEIGHT,
        steps = listOf(
            "Hands on a wall, one foot back, heel down, back knee straight.",
            "Lean in until you feel a stretch in the upper calf. Hold, then switch sides.",
        ),
        cues = listOf("Back heel stays on the floor.", "A stretch, not pain."),
    )
    val calfStretchBent = Exercise(
        id = "calf_stretch_bent", name = "Wall calf stretch (knee bent)", block = "Daily", machine = "A wall",
        muscles = "Soleus (lower calf)", load = LoadType.BODYWEIGHT,
        steps = listOf(
            "Same position, but bring the back foot a little closer and bend the back knee.",
            "Keep the heel down; feel it lower in the calf. Hold, then switch sides.",
        ),
        cues = listOf("Back heel stays on the floor.", "A stretch, not pain."),
    )

    // ---- Calf rehab exercises (docs/decisions.md §4) ---------------------------------------------

    val calfIsometric = Exercise(
        id = "calf_isometric", name = "Single-leg calf hold (isometric)", block = "Calf", machine = "A wall for balance",
        muscles = "Calves", load = LoadType.BODYWEIGHT, perSide = true, calf = true,
        steps = listOf(
            "Stand on one leg, hand on a wall.",
            "Rise partway onto the ball of the foot — mid-range, where it's comfortable — and hold still.",
            "Do it with the knee straight and again with the knee slightly bent.",
        ),
        cues = listOf("Comfortable range only.", "Mild discomfort is OK; sharp or rising pain means stop."),
        why = "Experts start calf-strain loading with isometric holds because they are often less symptomatic early " +
            "(Green 2022; Halabchi 2024). Hold times are app defaults, not from a study.",
    )

    // ---- Retired machine exercises (history only) -------------------------------------------------

    private fun retired(id: String, name: String, muscles: String, inverted: Boolean = false) = Exercise(
        id = id, name = name, block = "—", machine = "Machine (no longer in the routine)", muscles = muscles,
        steps = emptyList(), cues = emptyList(), inverted = inverted, retired = true,
    )
    val legPress = retired("leg_press", "Leg press (machine)", "Quads, glutes")
    val legPressCalf = retired("calf_raise", "Calf raise on leg press (machine)", "Calves").copy(perSide = true)
    val hipThrustMachine = retired("hip_thrust", "Hip thrust (machine)", "Glutes, hamstrings")
    val abductionMachine = retired("hip_abduction", "Hip abduction (machine)", "Glute medius")
    val legCurlMachine = retired("leg_curl", "Seated leg curl (machine)", "Hamstrings")

    val routine = listOf(splitSquat, calfRaiseStraight, calfRaiseBent, pullUp, hipThrust, sideAbduction, singleLegRdl)
    val stretches = listOf(quadStretch, calfStretchStraight, calfStretchBent)
    val retiredMachines = listOf(legPress, legPressCalf, hipThrustMachine, abductionMachine, legCurlMachine)
    val all = routine + pogoHops + stretches + calfIsometric + retiredMachines
    fun byId(id: String): Exercise? = all.firstOrNull { it.id == id }
}

/** What the calf guidance allows in today's strength session. */
data class SessionOptions(
    /** Leave out the routine's calf raises (the calf card has its own exercises). */
    val skipCalfRaises: Boolean = false,
    /** Include the plyometric block (only once the calf is cleared). */
    val plyometrics: Boolean = true,
)

/**
 * The dumbbell/bodyweight routine for a strength day (docs/decisions.md §1–2).
 * Main lifts: split squat, hip thrust, single-leg RDL. Smaller lifts: the two calf raises, side-lying
 * abduction and the optional assisted pull-up.
 */
fun strengthSession(kind: StrengthKind, options: SessionOptions = SessionOptions()): StrengthSession? {
    val e = Exercises
    fun p(ex: Exercise, sets: Int, reps: Int, label: String? = null) = Prescription(ex, sets, reps, label)
    fun build(items: List<Prescription>) = items.filter { !(options.skipCalfRaises && it.exercise.calf) }
    val plyo = options.plyometrics
    return when (kind) {
        StrengthKind.NONE -> null
        StrengthKind.LEARN -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 1, 10), p(e.calfRaiseStraight, 1, 12), p(e.calfRaiseBent, 1, 12), p(e.pullUp, 1, 8),
                p(e.hipThrust, 1, 10), p(e.sideAbduction, 1, 12), p(e.singleLegRdl, 1, 10),
            )),
            effort = "Light — learn the movements and pick starting weights",
            rest = "As needed", minutes = "15–20 min", restSec = 60, targetRir = 4,
            notes = listOf("Note a comfortable starting dumbbell for each exercise."),
        )
        StrengthKind.PHASE1 -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 3, 10, "8–10"), p(e.calfRaiseStraight, 2, 12), p(e.calfRaiseBent, 2, 12), p(e.pullUp, 2, 8),
                p(e.hipThrust, 3, 10, "8–10"), p(e.sideAbduction, 2, 12), p(e.singleLegRdl, 3, 10, "8–10"),
            )),
            effort = "Hard, about 3 reps left in the tank",
            rest = "60–90 s between sets", minutes = "25–30 min", restSec = 90, targetRir = 3,
            notes = standardNotes + "Main lifts 3 sets (was 2 × 12): more weekly sets give a bit more strength (Ralston 2017).",
        )
        StrengthKind.PHASE2 -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 3, 6, "5–6"), p(e.calfRaiseStraight, 2, 12), p(e.calfRaiseBent, 2, 12), p(e.pullUp, 2, 8),
                p(e.hipThrust, 3, 6, "5–6"), p(e.sideAbduction, 2, 12), p(e.singleLegRdl, 3, 6, "5–6"),
            ) + if (plyo) listOf(p(e.pogoHops, 3, 10)) else emptyList()),
            effort = "Heavy, about 2 reps left in the tank",
            rest = "90 s – 2 min between sets", minutes = "25–30 min", restSec = 120, targetRir = 2,
            notes = standardNotes + "Heavy loads (≈ 5–6 hard reps) are what improved runners' economy and race times " +
                "in the meta-analyses (Llanos-Lagos 2024).",
        )
        StrengthKind.PHASE3 -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 2, 5), p(e.calfRaiseStraight, 1, 12), p(e.calfRaiseBent, 1, 12), p(e.pullUp, 1, 8),
                p(e.hipThrust, 2, 5), p(e.sideAbduction, 1, 12), p(e.singleLegRdl, 2, 5),
            ) + if (plyo) listOf(p(e.pogoHops, 2, 10)) else emptyList()),
            effort = "Heavy, about 2 reps left in the tank — keep the loads heavy",
            rest = "90 s – 2 min between sets", minutes = "About 15–20 min", restSec = 120, targetRir = 2,
            notes = standardNotes + "One heavy session a week holds your strength (Spiering 2021).",
        )
        StrengthKind.LIGHT -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 1, 5), p(e.calfRaiseStraight, 1, 12), p(e.calfRaiseBent, 1, 12), p(e.pullUp, 1, 8),
                p(e.hipThrust, 1, 5), p(e.sideAbduction, 1, 12), p(e.singleLegRdl, 1, 5),
            )),
            effort = "Light — about 60% of your working weight",
            rest = "As needed", minutes = "About 10 min", restSec = 60, targetRir = 5,
            notes = listOf("A light session: 1 set each. Leave feeling fresh."),
        )
    }
}

private val standardNotes = listOf(
    "Lift first, then run — or run later in the day (Eddens 2018).",
    "Warm up with one light set of split squats and hip thrusts.",
    "Block 1: split squat, calf raises (and the optional pull-up). Block 2: hip thrust, side-lying abduction, " +
        "single-leg RDL. One set of each in a block, then repeat.",
    "Add weight when every set feels easier than the target effort.",
)
