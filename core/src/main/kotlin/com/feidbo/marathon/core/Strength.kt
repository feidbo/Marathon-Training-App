package com.feidbo.marathon.core

data class Exercise(
    val id: String,
    val name: String,
    val block: String,          // "1a", "2c", ...
    val machine: String,
    val muscles: String,
    val steps: List<String>,
    val cues: List<String>,
    /** Assisted pull-up: more counterweight = more help, so "progress" means *less* weight. */
    val inverted: Boolean = false,
    val weightLabel: String = "Weight (lb)",
)

data class Prescription(val exercise: Exercise, val sets: Int, val reps: Int)

data class StrengthSession(
    val kind: StrengthKind,
    val items: List<Prescription>,
    val effort: String,
    val rest: String,
    val minutes: String,
    val notes: List<String>,
)

object Exercises {
    val legPress = Exercise(
        id = "leg_press", name = "Leg press", block = "1a", machine = "Leg press",
        muscles = "Quads, glutes",
        steps = listOf(
            "Sit with your back and hips flat against the pad.",
            "Feet about shoulder-width on the platform, toes slightly out.",
            "Release the safety handles. Lower the platform under control until your knees reach about 90°.",
            "Press back up through your whole foot, stopping just short of locking your knees.",
        ),
        cues = listOf(
            "Feet about shoulder-width.",
            "Lower under control to about 90° at the knee.",
            "Don't lock your knees at the top.",
        ),
    )
    val calfRaise = Exercise(
        id = "calf_raise", name = "Calf raise", block = "1b", machine = "Same leg press, balls of feet on the platform edge",
        muscles = "Calves (gastrocnemius, soleus)",
        steps = listOf(
            "On the leg press, press the platform up so your legs are straight (knees soft, not locked).",
            "Slide your feet down so only the balls of your feet are on the bottom edge.",
            "Let your heels drop slowly for a full stretch.",
            "Push through the balls of your feet to point your toes as far as you can, then lower slowly.",
        ),
        cues = listOf(
            "Keep the safety stops engaged.",
            "Lower slowly through a full range.",
        ),
    )
    val pullUp = Exercise(
        id = "assisted_pull_up", name = "Assisted pull-up", block = "1c", machine = "Assisted pull-up machine (counterweight)",
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
    )
    val hipThrust = Exercise(
        id = "hip_thrust", name = "Hip thrust", block = "2a", machine = "Hip thrust machine, or Smith machine",
        muscles = "Glutes, hamstrings",
        steps = listOf(
            "Sit with your upper back against the pad (the bottom of your shoulder blades on the edge).",
            "Belt or bar across your hips, feet flat about hip-width, shins vertical at the top.",
            "Tuck your chin and drive through your heels to lift your hips until your body is flat from knees to shoulders.",
            "Pause one second at the top, then lower under control.",
        ),
        cues = listOf(
            "Chin tucked.",
            "Push through your heels.",
            "Pause one second at the top.",
        ),
    )
    val abduction = Exercise(
        id = "hip_abduction", name = "Hip abduction", block = "2b", machine = "Hip abductor machine",
        muscles = "Glute medius (side of the hip)",
        steps = listOf(
            "Sit tall with the pads on the outside of your knees.",
            "Push your knees apart as far as is comfortable.",
            "Hold briefly, then return slowly without letting the weights touch.",
        ),
        cues = listOf(
            "Sit tall; don't rock your torso.",
            "Control the return.",
        ),
    )
    val legCurl = Exercise(
        id = "leg_curl", name = "Seated leg curl", block = "2c", machine = "Leg curl machine",
        muscles = "Hamstrings",
        steps = listOf(
            "Adjust the seat so your knees line up with the machine's pivot.",
            "Lower pad just above your heels, thigh pad snug on your thighs.",
            "Curl your heels down and back as far as you can.",
            "Return slowly to straight legs.",
        ),
        cues = listOf(
            "Knees lined up with the pivot.",
            "Slow on the way back.",
        ),
    )
    val quadStretch = Exercise(
        id = "quad_stretch", name = "Kneeling quad stretch", block = "Daily", machine = "A pad or folded towel",
        muscles = "Quads, hip flexors",
        steps = listOf(
            "Kneel on a pad with one knee down and the other foot flat in front.",
            "Reach back, hold the back ankle, and draw that heel toward your glute.",
            "Tuck your pelvis under and keep your hips square — feel it in the front of the thigh and hip.",
            "Hold 90 seconds, then switch sides.",
        ),
        cues = listOf(
            "Every day, including rest days.",
            "After runs or in the evening, not right before a run.",
            "Back off the depth if your knee complains.",
        ),
    )

    val routine = listOf(legPress, calfRaise, pullUp, hipThrust, abduction, legCurl)
    val all = routine + quadStretch
    fun byId(id: String): Exercise? = all.firstOrNull { it.id == id }
}

/** The machine routine for a strength day, from the plan's strength table. */
fun strengthSession(kind: StrengthKind): StrengthSession? {
    val e = Exercises
    fun rx(vararg p: Triple<Exercise, Int, Int>) = p.map { Prescription(it.first, it.second, it.third) }
    return when (kind) {
        StrengthKind.NONE -> null
        StrengthKind.LEARN -> StrengthSession(
            kind, rx(
                Triple(e.legPress, 1, 12), Triple(e.calfRaise, 1, 15), Triple(e.pullUp, 1, 8),
                Triple(e.hipThrust, 1, 12), Triple(e.abduction, 1, 15), Triple(e.legCurl, 1, 12),
            ),
            effort = "Light — just learn the machines and settings",
            rest = "As needed", minutes = "15–20 min",
            notes = listOf("Write down seat settings and a comfortable starting weight for each machine."),
        )
        StrengthKind.PHASE1 -> StrengthSession(
            kind, rx(
                Triple(e.legPress, 2, 12), Triple(e.calfRaise, 2, 15), Triple(e.pullUp, 2, 8),
                Triple(e.hipThrust, 2, 12), Triple(e.abduction, 2, 15), Triple(e.legCurl, 2, 12),
            ),
            effort = "Hard, about 3 reps left in the tank",
            rest = "60–90 s between rounds", minutes = "15–20 min",
            notes = standardNotes,
        )
        StrengthKind.PHASE2 -> StrengthSession(
            kind, rx(
                Triple(e.legPress, 3, 6), Triple(e.calfRaise, 2, 10), Triple(e.pullUp, 2, 8),
                Triple(e.hipThrust, 3, 6), Triple(e.abduction, 2, 12), Triple(e.legCurl, 2, 8),
            ),
            effort = "Hard, about 2 reps left in the tank",
            rest = "90 s – 2 min between rounds", minutes = "20–25 min",
            notes = standardNotes,
        )
        StrengthKind.PHASE3 -> StrengthSession(
            kind, rx(
                Triple(e.legPress, 2, 5), Triple(e.calfRaise, 1, 10), Triple(e.pullUp, 1, 8),
                Triple(e.hipThrust, 2, 5), Triple(e.abduction, 1, 12), Triple(e.legCurl, 1, 8),
            ),
            effort = "Hard, about 2 reps left in the tank — keep the loads heavy",
            rest = "90 s – 2 min between rounds", minutes = "About 15 min",
            notes = standardNotes + "One heavy session a week holds your strength (Spiering 2021).",
        )
        StrengthKind.LIGHT -> StrengthSession(
            kind, rx(
                Triple(e.legPress, 1, 5), Triple(e.calfRaise, 1, 10), Triple(e.pullUp, 1, 8),
                Triple(e.hipThrust, 1, 5), Triple(e.abduction, 1, 12), Triple(e.legCurl, 1, 8),
            ),
            effort = "Light — about 60% of your working weight",
            rest = "As needed", minutes = "About 10 min",
            notes = listOf("A light session: 1 set each. Leave the gym feeling fresh."),
        )
    }
}

private val standardNotes = listOf(
    "Lift first, then run — or run later in the day (Eddens 2018).",
    "Warm up with one light set on the leg press and the hip thrust.",
    "Block 1: one set of leg press, calf raise, assisted pull-up, then rest. Repeat for the listed sets.",
    "Block 2: one set each of hip thrust, hip abduction and leg curl, then rest. Repeat.",
    "Add weight when every set feels easier than the target effort.",
)
