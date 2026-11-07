# Evidence log: speed-work definitions

## Question

The runner finds the speed-work descriptions confusing and runs with a Garmin watch (no phone). What does
each workout type mean, so it can be written as clear watch-friendly steps? This is mainly a wording task:
**the plan document is the source of truth** for every workout's content. Outside sources are used only to
define terms.

## Source of truth: the plan document (docs/First_Marathon_Plan_Eugene_2027.md)

"Paces and effort" table (quoted):

| Zone | Pace per mile | Effort (1–10) | Feels like |
| --- | --- | --- | --- |
| Easy / long | 11:45–13:00 | 3–4 | Full sentences |
| Marathon pace | 10:18 for 4:30; reset from your March 7 half | 5–6 | Steady, a few words at a time |
| Tempo (threshold) | About 9:30–10:00 at 2:10 half fitness, 10:15–10:40 at 2:20; start at the slower end | 7 | Comfortably hard; roughly what you could race for an hour |
| Hills / fartlek | By effort | 8 | Hard but controlled |
| Strides | By feel | 8–9 | 15–20 s quick and relaxed, full recovery |

Every quality workout in the plan, as written (from PlanData.kt, generated from the plan):

| Date | Type | Workout |
| --- | --- | --- |
| Nov 4 | Strides | 3.5 mi easy + 4 × 20 s strides |
| Nov 11 | Strides | 3 mi easy + 4 × 20 s strides |
| Nov 18 | Strides | 3.5 mi easy + 6 × 20 s strides |
| Nov 25 | Uphill strides | 4 mi easy + 6 × 15 s uphill strides |
| Dec 2 | Fartlek | 5 mi fartlek: 1 mi easy, 6 × (1 min moderately hard / 2 min easy), easy to finish |
| Dec 9 | Strides | 4 mi easy + 6 × 20 s strides |
| Dec 16 | Hills | 5 mi: 1.5 mi warm-up, 6 × 60 s uphill hard (8/10 effort), jog down; cool down |
| Dec 23 | Tempo | 5 mi: 1.5 mi warm-up, 2 × 10 min tempo with 3 min jog; cool down |
| Dec 30 | Hills | 6 mi: 1.5 mi warm-up, 8 × 60 s uphill hard, jog down; cool down |
| Jan 6 | Strides | 5 mi easy + 6 × 20 s strides |
| Jan 13 | Tempo | 6 mi: 1.5 mi warm-up, 3 × 8 min tempo with 2 min jog; cool down |
| Jan 20 | Hills | 6 mi: 1.5 mi warm-up, 8 × 90 s uphill hard, jog down; cool down |
| Jan 27 | Marathon pace | 7 mi: 1.5 mi warm-up, 4 mi at marathon pace, 1.5 mi cool down |
| Feb 3 | Tempo | 6 mi: 1.5 mi warm-up, 20 min continuous tempo; cool down |
| Feb 10 | Tempo | 7 mi: 1.5 mi warm-up, 4 × 1 mi at tempo with 2 min jog; cool down |
| Feb 17 | Marathon pace | 8 mi: 1.5 mi warm-up, 5 mi at marathon pace, 1.5 mi cool down |
| Feb 21 | Long + MP | 13.5 mi long run: 10.5 easy, last 3 at marathon pace |
| Feb 24 | Tempo | 8 mi: 1.5 mi warm-up, 2 × 2 mi at tempo with 3 min jog; cool down |
| Mar 3 | Marathon pace | 6 mi: 1.5 mi warm-up, 2 mi at marathon pace, cool down |
| Mar 6 | Shakeout | 2 mi shakeout + 4 strides |
| Mar 10 | Marathon pace | 8 mi: 1 mi warm-up, 6 mi at marathon pace, 1 mi cool down |
| Mar 14 | Long + MP | 17 mi long run: 13 easy, last 4 at marathon pace |
| Mar 17 | Tempo | 8 mi: 1.5 mi warm-up, 5 × 1 mi at tempo with 90 s jog; cool down |
| Mar 24 | Tempo | 8 mi: 1.5 mi warm-up, 4 × 1 mi at tempo with 2 min jog; cool down |
| Mar 28 | Dress rehearsal | 14 mi: 3 easy, 8 at marathon pace, 3 easy |
| Mar 31 | Marathon pace | 8 mi: 1.5 mi warm-up, 5 mi at marathon pace, 1.5 mi cool down |
| Apr 7 | Marathon pace | 7 mi: 1.5 mi warm-up, 4 mi at marathon pace, 1.5 mi cool down |
| Apr 14 | Marathon pace | 6 mi: 1.5 mi warm-up, 3 mi at marathon pace, cool down |
| Apr 15, 22 | Strides | 4 mi / 3 mi easy + 4 strides |
| Apr 21 | Marathon pace | 4 mi: 1 mi easy, 2 mi at marathon pace, 1 mi easy |
| Apr 24 | Shakeout | 2 mi shakeout + 4 strides |

Observation (fact, not a decision): the plan's tempo range at 2:20 half fitness (10:15–10:40 /mi) overlaps
the runner's reported easy pace (~10:30 /mi). See `easy-intensity.md`.

## Definitions from outside sources

### D1. Faude O, Kindermann W, Meyer T. Sports Med 2009;39:469–490 — ABS (review)
The "aerobic-anaerobic transition" has two breakpoints: "the intensity at which bLa begin to rise above
baseline levels and the highest intensity at which lactate production and elimination are in equilibrium
(maximal lactate steady state [MLSS])". Tempo / threshold running in the plan aims near the second
breakpoint ("comfortably hard"); easy running sits below the first (see `easy-intensity.md`).

### D2. Tham SK, et al. Medicine (Baltimore) 2026;105:e49977 (PMC13433027) — ABS (systematic review, 13 RCTs)
Uphill-running protocols "commonly involved 10 to 14 uphill sprints of 30 seconds at a ~10% gradient,
performed 2 to 3 times weekly over 2 to 8 weeks"; meta-analysis found no significant pooled effects on
knee strength, jumping or aerobic capacity; certainty low. (Context for hill workouts only; the plan's
hill sessions are kept as written.)

### D3. Bok D, et al. Sports Med 2022 (see easy-intensity.md, E3)
Talk test / RPE locate both thresholds: comfortable speech below the first; speech not comfortable and
RPE 13–15 near the second (respiratory compensation). Supports the plan's "feels like" descriptions.

## Notes for writing steps (no decisions)

- Strides, hills and fartlek are by effort (plan). Tempo and marathon pace have pace targets (plan).
- The runner's watch can show time, distance, pace and heart rate; wrist HR lags during short efforts
  (`easy-intensity.md`, E10), so short reps are better judged by time/effort than by HR.
