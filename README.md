# Road to Eugene — marathon training app

An offline Android app for the **First Marathon Plan: Eugene 2027** (Oct 16, 2026 → Apr 25, 2027).
The plan document is in [`docs/`](docs/First_Marathon_Plan_Eugene_2027.md).

## What it does

- **Today screen**: just today's workout. Distance, pace zone, effort, how it should feel, the strength
  routine with sets × reps, and the daily quad stretch with a 3-minute timer. Arrows step to other days
  so you can log one you missed.
- **Strength how-to**: each machine exercise has an animated illustration, step-by-step instructions, form cues
  and your history. Strength days show your **last weight as a benchmark** and suggest today's load
  (+5% after an easy session that hit the reps, −10% after one that was too hard).
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
- **Gamification**: XP, 11 levels, streaks (sick and pain days don't break them), 20 badges, confetti.
- **Progress screen**: totals, what you've done, what's coming up (adjusted days flagged), badges and
  strength benchmarks. Settings has the Mar 7 half-marathon → marathon-pace calculator (Riegel).

All data stays on the phone in one JSON file; nothing needs the internet.

## Install

Every push builds an APK in GitHub Actions (**Actions → Build APK → EugeneMarathon-apk**). Download it on your
phone, unzip, and open `app-release.apk` (allow "install unknown apps" when asked). Pushing a tag like `v1.0`
also attaches the APK to a GitHub Release.

Every build is signed with the same key (`app/sideload.keystore`), so new versions install over old ones
and keep your logs.

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
