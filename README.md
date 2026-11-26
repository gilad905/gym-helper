# Gym Helper

A personal Android app that walks you through a gym workout machine by machine:
tap **Set done**, it times your rest, beeps three times when it's up, and tells you
what's next ("Set 2 of 3", "Moving to Seated Row (03)").

- Plans are imported/exported as markdown (a Notion export zip works as-is, photos included).
- 1.5 min machine-switch timer replaces the last rest of each machine.
- Timed cardio shows a set timer; the treadmill gets warmup → main → cooldown, each with its own slope/pace.
- Every plan value is editable in place, locked by default (🔒 in the top bar). 🔇 mutes the beeps.
- The notification shows the current machine, set and a live countdown, and works with the screen off.
- Workout history, with each value shown as last session → next (e.g. `Reps 12 → 15`).
- Light/dark and colors follow the phone.

## Install

Every push to `main` builds the app on GitHub Actions and publishes it as the
[latest release](../../releases/latest). On the phone, open that page, download
`gym-helper.apk` and open it (allow "install unknown apps" for your browser once).
Updates install over the previous version and keep your data.

## Plan format

```markdown
# Gym

## Schedule
- Mon: Full Body
- Tue: Cardio

## Workout: Full Body

### Beinstreckung (14) — Leg Extension
![](images/leg-extension.jpg)
- Sets: 3
- Reps: 15
- Weight: 15 kg
- Rest: 1 min 30 sec
- Knee: 2
- Instructions: Pull your toes towards your body.

## Workout: Cardio

### Treadmill
- Duration: 25 min
- Slope: 4
- Pace: 5
- Warmup: 3 min
    - Slope: 2
    - Pace: 4
- Cooldown: 3 min
    - Pace: 3.5
```

`Sets`, `Reps`, `Weight`, `Rest`, `Duration`, `Type` (`strength` / `cardio` / `treadmill`),
`Warmup`, `Cooldown`, `Instructions` and `Notes` have meaning; any other key (Seat, Knee, Rpm…)
is a machine setting shown on the card. An exercise with a `Duration` is timed cardio; one named
Treadmill gets warmup/cooldown (3 min each by default).

## Code

- `core/` — plain Kotlin: model, markdown reader/writer, workout state machine, history. Tests run without
  the Android SDK: `gradle -p core test`.
- `app/` — Android app (Jetpack Compose, Material 3), foreground service for timers and the notification.
