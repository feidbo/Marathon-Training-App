# Road to Eugene — marathon training app

An offline Android app for the **First Marathon Plan: Eugene 2027** (Oct 16, 2026 → Apr 25, 2027).
The plan document is in [`docs/`](docs/First_Marathon_Plan_Eugene_2027.md).

## What it does

- **Tabs**: Today, Progress and Settings along the bottom.
- **Today screen**: just today's workout. Distance, pace zone, effort, how it should feel, the strength
  routine with sets × reps, and the daily quad stretch with a 3-minute timer. Arrows step to other days
  so you can log one you missed.
- **Strength how-to**: each machine exercise has an animated illustration, step-by-step instructions, form cues
  and your history. Strength days show your **last weight as a benchmark** and today's recommended load.
- **Set-by-set strength logging** (single-leg calf raises: 12 per leg): every set has its own weight and reps (change the weight mid-exercise and
  later sets follow), and a ✓ to mark it done. Ticking a set starts the **rest timer between every set**
  (90 s in Phase 1, 2 min in Phases 2–3; adjustable), shown in a bar at the bottom with what's next. It
  vibrates and beeps when rest is over and keeps the screen on while you rest. Only ticked sets are saved.
  Everything you enter is saved as you go, so rotating the phone or leaving the app loses nothing.
- **Timers keep running in the background**: the rest and stretch timers show a live countdown notification,
  and an alarm vibrates (and notifies) when time's up even if the app is closed. The stretch timer buzzes at
  the switch and at the end, then ticks the stretch off.
- **Load progression** (`core/.../Progression.kt`), based on:
  - reps in reserve ("reps left in the tank", the plan's effort target) logged for each exercise
    (Zourdos 2016; Helms 2016);
  - hit every rep with 1+ more reps left than the target → add ~5% (min 10 lb on leg press/hip thrust, 5 lb on
    smaller lifts); 2+ more → ~10% (ACSM 2009: increase 2–10% once you beat the target);
  - on target or harder → same weight; missed 2+ reps → ~10% lighter;
  - no reps-left logged → the "2-for-2" rule (all reps at the same weight two sessions running → add weight);
  - new rep target at a phase change → load from an estimated 1RM (Epley, counting reps left), started a
    little light in the first heavy week as the plan says;
  - more than 3 weeks off → ~10% lighter (strength largely holds for ~3 weeks; Bosquet 2013);
  - light days → ~60% of working weight; assisted pull-up progresses by *removing* counterweight.
- **Logging**: distance, average pace, tempo / marathon-pace segment pace, effort (1–10), pain, race time,
  and weight + reps per set for each lift. You get **live coaching** as you type: easy runs that are too fast,
  runs that break the 10% long-run rule, tempo or marathon pace off target, weights that are too heavy or
  too light, big jumps from last time, and entries that look like typos.
- **Couldn't do it**: pick a reason (sick, pain, tired, busy…) and get the plan's advice. The schedule adapts
  using the plan's own rules:
  - missed under a week → carry on, nothing is made up or moved onto a rest day;
  - missed 1–2 weeks → the next week repeats your last full week;
  - missed more than 2 weeks before March → drop back two weeks;
  - missed because of fatigue, or easy runs that feel 7+/10 → next week is ~15% lighter;
  - always → no run more than 10% longer than your longest in the last 30 days (Frandsen 2025).

  The half-marathon week and the taper are never restructured, and the race is never shortened.
- **Gamification**: XP with a level ring, 11 levels, streaks (sick and pain days don't break them), a daily
  "quests" checklist with XP per item, a **Perfect Day** bonus (+10 XP for the workout plus the stretch), a
  **Full Week** bonus (+50 XP), 23 badges with progress bars (including PR Breaker and Comeback Kid),
  personal-record call-outs, confetti and an XP count-up.
- **Progress screen**: totals, what you've done, what's coming up (adjusted days flagged), badges and
  strength benchmarks. Settings has the Mar 7 half-marathon → marathon-pace calculator (Riegel).

- **Home-screen widget**: today's workout, pace and strength, what's still to do (run / lift / stretch), streak
  and level. Long-press the home screen → Widgets → Eugene 26.2. Tapping it opens the app; it refreshes when
  you log and just after midnight.

All data stays on the phone in one JSON file; nothing needs the internet.

## Install

On your phone, open **https://github.com/feidbo/Marathon-Training-App/releases/tag/latest** and tap the `.apk`
(allow "install unknown apps" when asked). Every push rebuilds it. The same APK is also attached to each
GitHub Actions run (zipped).

Every build is signed with the same key (`app/sideload.keystore`) and has a higher version number, so new
versions install over old ones and keep your logs.

## Develop

```
./gradlew :core:test            # training logic tests (plain JVM)
./gradlew :app:assembleRelease  # APK (needs the Android SDK)
./gradlew -PcoreOnly=true :core:test   # tests without the Android SDK
```

- `core/`: plain Kotlin. Plan data, pace zones, the schedule adjuster, feedback rules, gamification.
- `app/`: Jetpack Compose UI.
- `tools/gen_plan.py` regenerates `PlanData.kt` from the plan document;
  `tools/gen_illustrations.py` regenerates the exercise figures (`--preview` renders PNGs).

## Credits

App icon duck: [Twemoji](https://github.com/jdecked/twemoji) 🦆 (U+1F986) by Twitter, Inc. and other
contributors, licensed under [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/).
