# Scheduled Pauses — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `ScheduleManager.kt` (new) | Singleton like `WeeklyGoalManager`: CRUD of `ScheduledPause`, plain `SharedPreferences`, `resetInstanceForTests()` |
| `ScheduledPause` (new data class) | `id`, `daysOfWeek` (7-bit mask, Monday = bit 0), `startMinuteOfDay`, `durationMinutes`, `enabled`, `skipNextUntil` (epoch ms or 0) |
| `ScheduleAlarms.kt` (new) | Pure `nextOccurrence(schedule, now, zone)` + arming/cancelling via `AlarmManager` |
| `ScheduledPauseReceiver.kt` (new) | Fires at start time (and 5 min before for the heads-up); starts the pause; re-arms the next occurrence |
| `BootReceiver.kt` | Also re-arms all enabled schedules |
| `SettingsScreen.kt` / new `ScheduleCard` | "Scheduled pauses" section: list, add, edit |

## Storage

`SharedPreferences`, one JSON-ish string per schedule or a compact
`id|mask|minute|duration|enabled|skip` line list. Not Room: the data is a
handful of rows, never queried, and Room would add a migration for nothing.
Same choice as `WeeklyGoalManager`.

## Timing: inexact alarms, on purpose

Exact alarms (`setExact*`, `setAlarmClock`) need `SCHEDULE_EXACT_ALARM`,
denied by default on Android 14+, and `USE_EXACT_ALARM` is reserved by
Play policy for alarm-clock and calendar apps. A pause starting at 21:02
instead of 21:00 is fine, so the plan is `setWindow(start, 5 min)` — the
same family as the expiry alarm (`setAndAllowWhileIdle`), with a bounded
window.

Each receiver run arms only the **next** occurrence (one alarm per
schedule), computed by `nextOccurrence()`, which handles DST and the
`skipNextUntil` marker. That function is pure and gets unit tests (day
wrap, Sunday→Monday, DST change, all days off).

## Risk to verify first: starting the foreground service from an alarm

`startSession()` starts `SessionForegroundService`. Since Android 12,
starting a foreground service from the background throws
`ForegroundServiceStartNotAllowedException` unless an exemption applies.
Exact alarms are an exemption, inexact ones are not documented as one. The
widget gets away with it because a widget tap is a user interaction.

**Before building the UI**, a spike: arm an inexact alarm on API 34+ that
calls `startSession()` with the app swiped away, and check whether the
service starts. If it does not, the options in order of preference:

1. Start the pause without the foreground service and let the service
   start on the next foreground moment. DND, the expiry alarm and the
   Accessibility block do not need the service; it only keeps the process
   and the notification alive.
2. Ask for `SCHEDULE_EXACT_ALARM` only when the user creates the first
   schedule, explaining why.

## Password rules

The edit dialog compares old and new schedule: stricter (more days, longer,
new schedule) saves directly; looser (fewer days, shorter, different time,
disabled, deleted, skip-next) goes through `PasswordVerifyDialog` first.
"Different time" counts as looser because moving 21:00 to 23:30 is a way to
dodge the pause.

## Interaction with other features

- **Allowed-app profiles** (`specs/allowed-app-profiles/`): a schedule
  carries an optional profile id; default profile otherwise.
- **Closing moment**: a scheduled pause ends like any other.
- **History**: `SessionRecord` gains nothing; whether a pause was
  scheduled is not interesting enough to store.

## Decisioni prese

1. **Spostare l'orario:** richiede la password (è un allentamento).
2. **Avviso 5 minuti prima:** sì, notifica silenziosa, nessuna azione.
3. **Permessi mancanti all'ora prevista:** una notifica una tantum.
