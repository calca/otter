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

### The alarm didn't arrive: Doze (fixed)

Reported from a test on a real phone: "non è partita la pausa". The alarms
were `setWindow`, which Doze (screen off, phone still: exactly the evening)
defers to the next maintenance window, possibly hours later; Samsung's app
sleeping makes it worse. Now (`ScheduleAlarms.set`):

- with **"Alarms & reminders"** (`SCHEDULE_EXACT_ALARM`, declared, granted
  by the user — never required): `setExactAndAllowWhileIdle`. Verified on
  the emulator: fires on the minute (21:12:03 for 21:12), and the alarm's
  `ALARM_MANAGER_WHILE_IDLE` exemption lets `SessionForegroundService`
  start right away, so the ongoing notification is there too;
- without it: `setAndAllowWhileIdle` — inexact but delivered in Doze.
  Verified: fired inside the ~2-minute window; the foreground service is
  still refused (see above) until the app is opened.

The list shows a card inviting to allow "Alarms & reminders" (only when
there is at least one schedule and the permission is missing; re-read on
resume), whose "Allow" opens `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` for the
app. Granting it broadcasts `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`,
caught by `BootReceiver` → `AppScheduler.reconcile()` to re-arm as exact.
Not a Play-restricted permission (unlike `USE_EXACT_ALARM`).

## The list

One-line intro ("Pauses that start by themselves, on the days and at the
time you choose") — the protection rule is explained in the editor, next to
its switch; it used to be five lines before any schedule. "Add a scheduled
pause" is pinned to the bottom (CTA rule); with no schedules, an otter and
"No scheduled pauses yet" instead of an empty page.

## Password rules

Requested: "le scheduling che faccio io senza password". The app can't know
who created a schedule (creating never asked for the password), so it's an
explicit flag: `ScheduledPause.locked`, "Protected by password", off by
default, a switch in the editor; a lock icon in the list.

`passwordNeededToSave(old, updated)` (pure, tested):

- unprotected (or new) → password only if `updated.locked` (protecting is
  the partner's gesture; prompt "Protecting … needs the password");
- protected → password if the protection is removed or
  `updated.isLooserThan(old)` (fewer days, shorter, different time,
  disabled, other profile, skip-next). "Different time" counts as looser
  because moving 21:00 to 23:30 is a way to dodge the pause.

Delete (trash in the ActionBar) and "Skip the next one" ask for the
password only when the schedule is protected. Storage: an 8th field in the
`|`-separated line; 7-field lines (saved before the flag) read as
unprotected.

## The editor page

Was an `AlertDialog` holding days, duration, profile and actions, which
opened a second dialog on top of itself for the time. Now a page that
replaces the list inside `ScheduledPausesActivity` (state `editing`; the
ActionBar title follows it, Back/Up return to the list without saving),
laid out like Android's alarm editor:

- On top, the time as a large "21:00" that opens Material's
  `TimePickerDialog` (24 h dial, keyboard toggle for typing), with a
  one-line summary under it ("Mon–Fri · for 1 h"). Picker colors come from
  `primary`/`tertiary` — the defaults use roles the palettes don't set.
- Two cards in the Settings style, so the page speaks the same language as
  the rest of the app (before, every field had a look of its own — circles,
  pills, a loose link, a card — and the page didn't hold together):
  - **When**: "Days" (seven toggleable circles on one row, read by full day
    name) and, below a divider, "Duration" as a stepper row: "–" / "+"
    step through the same options as Home (`SESSION_DURATION_OPTIONS`) and
    switch off at the two ends; the signs are drawn, not typed (the font's
    "+" and "–" sit at different heights). It was a scrolling row of pills,
    which inside the card got cut at the edge.
  - **How**: rows with a tinted round icon and dividers — "Allowed apps …
    Standard →" (only with 2+ profiles; opens the same profile dialog as
    Home, `AllowedProfileDialog`), "Protected by password" with its switch,
    and "Skip the next one" (existing, enabled schedules only). The rows
    reuse `SettingsActionRow`/`SettingsRowIcon` from Settings.
- **Save pinned to the bottom**, outside the scroll (project design rule,
  see CLAUDE.md). **Delete** is a trash icon in the ActionBar, only for an
  existing schedule — away from Save, as in the Clock app.
- The page closes only once the change is applied: if the password is
  cancelled, what was set stays on screen.

Tried and dropped along the way: the dial inline (half the page on its
own), `TimeInput` inline (opened the keyboard on entry; "00" + typing "30"
gave "03"), the Mon–Fri / Sat–Sun / Every day shortcut pills (a row of
pills to save a couple of taps), centered uppercase section labels and the
summary sentence above Save (the page read as cluttered).

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

## The heads-up goes away at the start

Reported once scheduled pauses started reliably: the "Your scheduled pause
starts at 21:00" notification stayed in the shade after the pause began.
Now the start alarm cancels it, and it carries `setTimeoutAfter` up to the
start time, so it also disappears when the pause doesn't start (skipped,
permissions missing — that case has its own notification).

