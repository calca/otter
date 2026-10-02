# Scheduled Pauses — Design

> **Status: Implemented** (2026-10-01).

## Key files

| File | Role |
|---|---|
| `ScheduledPauses.kt` (`ScheduleManager`, `ScheduledPause`, `nextOccurrence`) | Singleton like `WeeklyGoalManager`: CRUD of `ScheduledPause`, plain `SharedPreferences`, `resetInstanceForTests()` |
| `ScheduledPauseReceiver.kt` (`ScheduleAlarms`) | Arming/cancelling via `AlarmManager`; the receiver for heads-up and start |
| `BootReceiver.kt` | Also re-arms all enabled schedules |
| `ScheduledPausesActivity.kt` / `ui/screens/ScheduledPausesScreen.kt` | List with on/off switches; a full-page editor (`ScheduleEditorScreen`) in place of the list; password for loosening changes; Settings has a "Scheduled pauses" row |

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

## Starting the foreground service from an alarm: verified, and handled

`startSession()` starts `SessionForegroundService`. Verified on the API 37
emulator with the app process killed: the inexact alarm fired, the pause
started, and Android **refused** the foreground service
(`ForegroundServiceStartNotAllowedException`, `code:DENIED`, no
temp-allowlist reason) — an inexact alarm grants no exemption, unlike a
widget or tile tap.

Handled with option 1 below, no extra permission: `SessionForegroundService.start()`
catches the refusal and logs it. The pause is fully in force without the
service — Do Not Disturb, the expiry alarm and the Accessibility block do
not depend on it; the service only keeps the process and the ongoing
notification alive. `MainActivity.onResume()` starts it again whenever a
pause is active, so the notification appears the first time the app is
opened (verified). Not verified: whether a device with the Accessibility
service actually enabled (the emulator runs the debug bypass) gets an
exemption that lets the service start immediately.

If the missing notification turns out to matter, the remaining option is
asking for `SCHEDULE_EXACT_ALARM` when the first schedule is created, with
an explanation: exact alarms are exempt.

## Password rules

The editor compares old and new schedule: stricter (more days, longer,
new schedule) saves directly; looser (fewer days, shorter, different time,
disabled, deleted, skip-next) goes through `PasswordVerifyDialog` first.
"Different time" counts as looser because moving 21:00 to 23:30 is a way to
dodge the pause.

## The editor page

Was an `AlertDialog` holding days, duration, profile and actions, which
opened a second dialog on top of itself for the time. Now a page that
replaces the list inside `ScheduledPausesActivity` (state `editing`; the
ActionBar title follows it, Back/Up return to the list without saving):

- Material `TimePicker` (24 h dial) inline, with colors taken from
  `primary`/`tertiary` — its defaults use roles the palettes don't set.
- Seven day circles on one row (toggleable, read by full day name), plus
  "Mon–Fri / Sat–Sun / Every day" shortcuts.
- Duration and profile as regular `CalmPill`s in a horizontally scrolling
  row; the selected one is scrolled into view by moving only the row (a
  `bringIntoView` would scroll the page down to it).
- A one-line summary ("Mon–Fri at 21:00, for 1 h"), then Save; for an
  existing schedule "Skip the next one" and a red "Delete pause".
- The page closes only once the change is applied: if the password is
  cancelled, what was set stays on screen.

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
