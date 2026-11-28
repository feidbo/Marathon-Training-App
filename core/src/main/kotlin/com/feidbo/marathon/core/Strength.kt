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
    /** Whether the bodyweight version is one leg at a time (null = same as the dumbbell version). */
    val bodyweightPerSide: Boolean? = null,
    /** Skip it freely (the assisted pull-up needs a machine). */
    val optional: Boolean = false,
    /** Part of the calf work that the calf guidance can take over. */
    val calf: Boolean = false,
    /** Old machine exercises: kept so past logs still show their names; never prescribed. */
    val retired: Boolean = false,
    /** Where the choice comes from (docs/evidence + docs/decisions.md). */
    val why: String = "",
    /** Progress by adding reps, not weight (e.g. when grip, not the muscle, limits the load). No load suggestions. */
    val progressByReps: Boolean = false,
    /** What one side is called: "leg" for most, "arm" for the dumbbell row. */
    val sideWord: String = "leg",
) {
    fun repsText(reps: Int): String = if (perSide) "$reps/$sideWord" else "$reps"
    val usesWeight: Boolean get() = load != LoadType.BODYWEIGHT
    /** Whether the version done is one leg at a time. */
    fun perSideFor(bodyweight: Boolean): Boolean = if (bodyweight) bodyweightPerSide ?: perSide else perSide
    fun nameFor(bodyweight: Boolean): String = if (bodyweight) bodyweightName ?: name else name
}

/** [repsLabel] is shown instead of the number when the plan gives a range (e.g. "8–10"). */
data class Prescription(val exercise: Exercise, val sets: Int, val reps: Int, val repsLabel: String? = null) {
    val repsShown: String get() = repsShown(bodyweight = false)
    fun repsShown(bodyweight: Boolean): String =
        (repsLabel ?: reps.toString()) + if (exercise.perSideFor(bodyweight)) "/${exercise.sideWord}" else ""
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
        id = "calf_raise_straight", name = "Single-leg calf raise — fast", block = "1b", progressByReps = true,
        machine = "A step and dumbbells you can hold comfortably (e.g. 40 lb)",
        muscles = "Calves — mostly gastrocnemius", perSide = true, calf = true,
        weightLabel = "Total weight held (lb)",
        steps = listOf(
            "Stand on one foot with the ball of the foot on a step edge, a hand on a wall for balance if you need it.",
            "Hold the same weight every time — one you can hold comfortably for the whole set (e.g. 40 lb).",
            "Fast: drive up quickly as high as you can, then lower under control to a full stretch — about a second each way.",
            "Keep going until you have about 2 good reps left (20–30 reps), then switch legs. That's one set.",
            "When you get to 30 with reps to spare, keep the weight and add a 2-second hold at the top of every rep.",
        ),
        cues = listOf(
            "Full range every rep, even when it's quick.", "Quick up, controlled down — no bouncing off the bottom.",
            "Calves should burn by the end; if they don't, go closer to failure, not heavier.",
        ),
        bodyweightName = "Single-leg calf raise — fast (bodyweight)",
        bodyweightSteps = listOf("Same movement with no dumbbell."),
        why = "Knee straight shifts a little more of the work to the gastrocnemius (Hébert-Losier 2012; Kovács 2024). " +
            "More reps with a weight you can hold (Nov 2026, your choice): heavier dumbbells gave out your grip before your " +
            "calves. Muscle grows across a wide range of loads (≥ ~30% of max) when sets end close to failure (Schoenfeld " +
            "2021; Refalo 2023). 20–30 reps and the pause progression are app defaults.",
    )
    val calfRaiseSlow = Exercise(
        id = "calf_raise_slow", name = "Single-leg calf raise — slow", block = "1c", progressByReps = true,
        machine = "A step and the same dumbbells as the fast set",
        muscles = "Calves — mostly gastrocnemius", perSide = true, calf = true,
        weightLabel = "Total weight held (lb)",
        steps = listOf(
            "Same set-up and the same weight as the fast raise: one foot on a step edge, knee straight.",
            "Slow: about 3 seconds up, pause at the top, about 3 seconds down to a full stretch.",
            "Keep going until you have about 2 good reps left (15–25 reps), then switch legs. That's one set.",
            "When you get to 25 with reps to spare, keep the weight and make the pause at the top 2–3 seconds.",
        ),
        cues = listOf("Count it: 3 up, pause, 3 down.", "Full range; no resting at the bottom."),
        bodyweightName = "Single-leg calf raise — slow (bodyweight)",
        bodyweightSteps = listOf("Same movement with no dumbbell."),
        why = "Your 2 fast + 2 slow split (Nov 2026), with the same comfortable weight and more reps instead of heavier " +
            "dumbbells (Schoenfeld 2021; Refalo 2023). Note: a straight-knee raise does not target the soleus the way a " +
            "bent-knee raise does (Kovács 2024; Green 2022); the bent-knee raise stays in the calf guidance. The tempo, " +
            "15–25 reps and the pause progression are app defaults.",
    )
    val calfRaiseSeated = Exercise(
        id = "calf_raise_seated", name = "Seated single-leg calf raise — slow", block = "—", retired = true,
        machine = "A bench or chair, a step (or a thick book or plate) and dumbbells",
        muscles = "Calves — mostly soleus", perSide = true, calf = true,
        weightLabel = "Weight on the knee (lb)",
        steps = listOf(
            "Sit on a bench or chair with the ball of one foot on a step, book or plate, heel hanging off, knee bent about 90°.",
            "Rest the dumbbell(s) on top of that thigh, just above the knee. Steady them with your hands — no gripping needed.",
            "Slow: about 3 seconds to rise as high as you can, pause, then about 3 seconds down to a full stretch.",
            "15–20 reps, then the other leg. That's one set. The app suggests when to add weight."
        ),
        cues = listOf("Count it: 3 up, pause, 3 down.", "Push through the ball of the foot, not the toes.",
            "The weight sits on the leg, so grip isn't a limit — load it up."),
        bodyweightName = "Seated single-leg calf raise (no weight)",
        bodyweightSteps = listOf("Same movement; press down on the knee with both hands for resistance."),
        why = "Replaces the slow standing raise (Nov 2026): the dumbbells rest on your knee, so your fingers don't limit the " +
            "load. With the knee bent the soleus works a bit more (Kovács 2024: 15–28% more soleus activity in the first half " +
            "of slow reps; Hébert-Losier 2012 found only about 4%). Experts treat soleus strength as essential in calf-strain " +
            "rehab and load it knee-straight and knee-bent (Green 2022); the routine had lost its bent-knee raise. The bent-" +
            "knee position is weaker (Kovács 2024: 31% less torque), so expect less weight than standing. Tempo and reps are app defaults.",
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
        id = "assisted_pull_up", name = "Assisted pull-up (optional)", block = "3a",
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
    val dumbbellRow = Exercise(
        id = "dumbbell_row", name = "One-arm dumbbell row (no pull-up machine)", block = "1e",
        machine = "One dumbbell and a bench, bed or chair",
        muscles = "Lats, upper back, biceps", perSide = true, sideWord = "arm", optional = true,
        weightLabel = "Dumbbell (lb)",
        steps = listOf(
            "Put one hand and the same-side knee on a bench (or bed edge); the other foot on the floor, back flat.",
            "Hold the dumbbell in the free hand, arm hanging straight down.",
            "Pull the dumbbell up to your lower ribs, elbow close to your side.",
            "Lower slowly to a straight arm. All reps, then the other arm — that's one set.",
        ),
        cues = listOf("Back flat; don't twist your chest open.", "Pull with the elbow, not the hand.", "Slow on the way down."),
        bodyweightName = "Inverted row (bodyweight)", bodyweightPerSide = false,
        bodyweightSteps = listOf(
            "Lie under a sturdy table or low bar, grip the edge, body straight from heels to head.",
            "Pull your chest up to the edge, then lower slowly. Check the table can't tip first.",
        ),
        why = "For days without a pull-up machine. It works the same muscles (lats, upper back, biceps), but no study " +
            "has tested the dumbbell row itself or whether rows improve pull-ups. The inverted row gave the highest lat " +
            "activity of three rows in one small study (Fenwick 2009). The pullover was ruled out: it mainly works the " +
            "chest (Di Fonza 2026).",
    )
    val hipThrust = Exercise(
        id = "hip_thrust_db", name = "Dumbbell hip thrust", block = "—", retired = true,
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
        bodyweightName = "Single-leg hip thrust (bodyweight)", bodyweightPerSide = true,
        bodyweightSteps = listOf(
            "Same set-up with no dumbbell; lift one foot and drive up through the other. All reps, then switch legs.",
        ),
        why = "Removed from the routine at your request (Nov 2026). Your logged sets stay in your history.",
    )
    val sideAbduction = Exercise(
        id = "side_abduction", name = "Side-lying hip abduction", block = "—", retired = true,
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
        id = "single_leg_rdl", name = "Single-leg Romanian deadlift", block = "1d",
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
        bodyweightName = "Sliding leg curl (bodyweight)", bodyweightPerSide = false,
        bodyweightSteps = listOf(
            "Lie on your back, heels on a towel on a smooth floor, hips lifted in a bridge.",
            "Slide your heels out until your legs are nearly straight, keeping the hips up, then curl them back in.",
        ),
        why = "Replaces the leg curl. Single-leg deadlifts were among the top glute exercises (Distefano 2009) and " +
            "train the hamstrings; leg curls including sliding versions show high hamstring activity (Hegyi 2019).",
    )
    val pogoHops = Exercise(
        id = "pogo_hops", name = "Pogo hops", block = "2b",
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

    // ---- Abs block (docs/evidence/abs.md) ---------------------------------------------------------

    val curlUp = Exercise(
        id = "curl_up", name = "Weighted curl-up with a hold", block = "1f",
        machine = "One dumbbell (or a plate) and floor space",
        muscles = "Abs (rectus abdominis)", load = LoadType.LOWER_SMALL,
        weightLabel = "Dumbbell on chest (lb)",
        steps = listOf(
            "Lie on your back, knees bent, feet flat. Hold a dumbbell flat on your chest with both hands.",
            "Curl your shoulders off the floor until your shoulder blades lift.",
            "Hold the top for 3 seconds, then lower slowly.",
            "Pick a weight where the last rep is hard — about 2 reps left in the tank. Add weight when it gets easier.",
        ),
        cues = listOf("Lower back stays on the floor.", "Keep the dumbbell pressed to your chest, not on your neck.", "Slow on the way down."),
        bodyweightName = "Curl-up with a hold (bodyweight)",
        bodyweightSteps = listOf("Same movement with your hands crossed on your chest."),
        why = "A held curl-up reached about 80% of maximum ab activity, against 37–51% for an ordinary curl-up " +
            "(Oliva-Lozano 2020). Weight added (Nov 2026) because bodyweight sets were stopping with 5 reps in reserve: " +
            "muscles grow across a wide range of loads (Schoenfeld 2021), but sets are best ended close to failure " +
            "(Refalo 2023). No trial has tested the dose that grows the abs specifically. Seeing them depends mostly on " +
            "the fat over them (Vispute 2011).",
    )
    val rollOut = Exercise(
        id = "ab_rollout", name = "Towel roll-out", block = "—", retired = true,
        machine = "A towel on a smooth floor (on carpet: walk your hands out)",
        muscles = "Abs (rectus abdominis)", load = LoadType.BODYWEIGHT,
        steps = listOf(
            "Kneel with your hands on a towel on a smooth floor, arms straight, hips over knees.",
            "Slide your hands forward, letting your hips follow, until just before your lower back wants to sag.",
            "Pull back to the start with your abs. On carpet, walk your hands out and back instead.",
        ),
        cues = listOf("Ribs down, glutes tight — no sagging lower back.", "Go only as far as you can control.", "Stop if your lower back hurts."),
        why = "Roll-outs showed the highest ab activity of the floor exercises reviewed: about 100% of maximum " +
            "(Oliva-Lozano 2020). Those studies used a wheel or stable surface; the towel version and the sets and " +
            "reps are app adaptations.",
    )

    val russianTwist = Exercise(
        id = "russian_twist", name = "Weighted Russian twist", block = "1g",
        machine = "One dumbbell (or a plate) and floor space",
        muscles = "Abs — side abs (obliques) and the front", load = LoadType.LOWER_SMALL,
        weightLabel = "Dumbbell held (lb)",
        steps = listOf(
            "Sit on the floor, knees bent, heels on the floor.",
            "Lean back until you feel your abs working. Keep your back straight, chest up.",
            "Hold a dumbbell with both hands in front of your chest.",
            "Turn your shoulders to one side, then to the other. Each turn counts as one rep.",
            "Harder: a heavier dumbbell, or lift your feet off the floor. Aim for about 2 reps left in the tank.",
        ),
        bodyweightName = "Russian twist (bodyweight)",
        bodyweightSteps = listOf("Same movement with your hands together and no dumbbell."),
        cues = listOf("Turn your shoulders, not just your arms.", "Back stays straight — don't round.", "Breathe out as you turn."),
        why = "Your pick, for being easy to learn (Nov 2026); it replaces the towel roll-out. Twisting is the job of the " +
            "side abs: the review of ab exercises calls the external oblique a trunk-rotation muscle (Oliva-Lozano 2020). " +
            "My search found no study measuring the Russian twist itself. Weight added (Nov 2026) because bodyweight sets were too easy. Sets and reps are an app default.",
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

    /** Grouped by equipment: dumbbells (block 1), then bodyweight (block 2), then the machine (block 3, always last). */
    val routine = listOf(splitSquat, calfRaiseStraight, calfRaiseSlow, singleLegRdl, dumbbellRow, curlUp, russianTwist, pullUp)
    /** The pulling slot: do one of these — the pull-up with a machine, the row without. */
    val pullChoices = listOf(pullUp, dumbbellRow)
    /** Dropped from the routine but kept so logged sets still show. */
    val retiredOther = listOf(hipThrust)
    /** Not in the strength routine any more, but still used by the calf guidance and kept for history. */
    val formerRoutine = listOf(calfRaiseBent, calfRaiseSeated, rollOut, sideAbduction)
    val stretches = listOf(quadStretch, calfStretchStraight, calfStretchBent)
    val retiredMachines = listOf(legPress, legPressCalf, hipThrustMachine, abductionMachine, legCurlMachine)
    val all = routine + calfRaiseBent + calfRaiseSeated + rollOut + sideAbduction + pogoHops + stretches + calfIsometric + retiredOther + retiredMachines
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
 * Main lifts: split squat and single-leg RDL. Smaller lifts: the two calf raises, the weighted abs
 * and the optional assisted pull-up. Ordered by equipment (user request, Nov 2026): every dumbbell exercise,
 * then every bodyweight exercise, then the machine — the pull-up is always last.
 */
fun strengthSession(kind: StrengthKind, options: SessionOptions = SessionOptions()): StrengthSession? {
    val e = Exercises
    fun p(ex: Exercise, sets: Int, reps: Int, label: String? = null) = Prescription(ex, sets, reps, label)
    // The dumbbell row sits beside the pull-up with the same sets (do one of the two).
    fun withRow(items: List<Prescription>) = items.flatMap { rx ->
        if (rx.exercise.id == e.pullUp.id) listOf(rx, p(e.dumbbellRow, rx.sets, 10, "8–10")) else listOf(rx)
    }
    fun build(items: List<Prescription>) = withRow(items).filter { !(options.skipCalfRaises && it.exercise.calf) }
    // Abs finish the session (after the hops): 2 sets on working days, 1 on light days.
    fun abs(sets: Int) = listOf(p(e.curlUp, sets, 12, "10–15"), p(e.russianTwist, sets, 20, "20 (10 each side)"))
    val plyo = options.plyometrics
    val session = when (kind) {
        StrengthKind.NONE -> null
        StrengthKind.LEARN -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 1, 10), p(e.calfRaiseStraight, 1, 25, "20–30"), p(e.calfRaiseSlow, 1, 20, "15–25"),
                p(e.singleLegRdl, 1, 10), p(e.pullUp, 1, 8),
            )) + abs(1),
            effort = "Light — learn the movements and pick starting weights",
            rest = "As needed", minutes = "15–20 min", restSec = 60, targetRir = 4,
            notes = listOf("Note a comfortable starting dumbbell for each exercise."),
        )
        StrengthKind.PHASE1 -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 3, 10, "8–10"), p(e.calfRaiseStraight, 2, 25, "20–30"), p(e.calfRaiseSlow, 2, 20, "15–25"),
                p(e.singleLegRdl, 3, 10, "8–10"), p(e.pullUp, 2, 8),
            )) + abs(2),
            effort = "Hard, about 3 reps left in the tank",
            rest = "60–90 s between sets", minutes = "25–30 min", restSec = 90, targetRir = 3,
            notes = standardNotes + "Main lifts 3 sets (was 2 × 12): more weekly sets give a bit more strength (Ralston 2017).",
        )
        StrengthKind.PHASE2 -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 3, 6, "5–6"), p(e.calfRaiseStraight, 2, 25, "20–30"), p(e.calfRaiseSlow, 2, 20, "15–25"),
                p(e.singleLegRdl, 3, 6, "5–6"), p(e.pullUp, 2, 8),
            ) + if (plyo) listOf(p(e.pogoHops, 3, 10)) else emptyList()) + abs(2),
            effort = "Heavy, about 2 reps left in the tank",
            rest = "90 s – 2 min between sets", minutes = "25–30 min", restSec = 120, targetRir = 2,
            notes = standardNotes + "Heavy loads (≈ 5–6 hard reps) are what improved runners' economy and race times " +
                "in the meta-analyses (Llanos-Lagos 2024).",
        )
        StrengthKind.PHASE3 -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 2, 5), p(e.calfRaiseStraight, 1, 25, "20–30"), p(e.calfRaiseSlow, 1, 20, "15–25"),
                p(e.singleLegRdl, 2, 5), p(e.pullUp, 1, 8),
            ) + if (plyo) listOf(p(e.pogoHops, 2, 10)) else emptyList()) + abs(2),
            effort = "Heavy, about 2 reps left in the tank — keep the loads heavy",
            rest = "90 s – 2 min between sets", minutes = "About 20 min", restSec = 120, targetRir = 2,
            notes = standardNotes + "One heavy session a week holds your strength (Spiering 2021).",
        )
        StrengthKind.LIGHT -> StrengthSession(
            kind, build(listOf(
                p(e.splitSquat, 1, 5), p(e.calfRaiseStraight, 1, 25, "20–30"), p(e.calfRaiseSlow, 1, 20, "15–25"),
                p(e.singleLegRdl, 1, 5), p(e.pullUp, 1, 8),
            )) + abs(1),
            effort = "Light — about 60% of your working weight",
            rest = "As needed", minutes = "About 10 min", restSec = 60, targetRir = 5,
            notes = listOf("A light session: 1 set each. Leave feeling fresh."),
        )
    }
    // Ordered by equipment block: dumbbells, bodyweight, then the machine (the pull-up is always last).
    return session?.let { it.copy(items = it.items.sortedBy { rx -> rx.exercise.block }) }
}

private val standardNotes = listOf(
    "Lift first, then run — or run later in the day (Eddens 2018).",
    "Warm up with one light set of split squats and single-leg RDLs.",
    "Block 1, dumbbells: split squat, fast calf raise, slow calf raise, single-leg RDL (and the dumbbell row on days without a " +
        "pull-up machine), weighted curl-ups, weighted Russian twists. Block 2, bodyweight: pogo hops (from mid-December). " +
        "Block 3, machine: the assisted pull-up, always last. One set of each in a block, then repeat the block.",
    "Add weight when every set feels easier than the target effort.",
)
