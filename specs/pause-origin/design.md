# Pause Origin — Design

## Storage

`PauseOrigin` (`PauseOrigin.kt`) is `MANUAL`, `SCHEDULED`, `SURPRISE`,
`USAGE`. `SessionManager.startSession` takes `origin` (default `MANUAL`)
and `originDetail`, saved in the session prefs (`session_origin`,
`session_origin_detail`) and removed by `endSession`, like the other
per-pause fields. The detail is:

- `SCHEDULED`: the schedule's `startMinuteOfDay` — the time the user set,
  not the actual start, which can slip with inexact alarms;
- `USAGE`: the rule's `afterMinutes`;
- otherwise 0.

Callers: `ScheduledPauseReceiver` (`SCHEDULED` or `SURPRISE`, by
`schedule.isSurprise`) and `UsageWatcher.startPause` (`USAGE`). Every other
start is manual by default.

`SessionManager.originLabel()` returns the label text, or null for a manual
pause. The time is formatted "HH:MM", the same as the scheduled pauses
list, so it reads as the time the user typed.

## Block screen

The status label (dot + uppercase text) shows `originLabel()` uppercased,
falling back to `home_active_label`. Still one line.

The welcome: `startSession` sets `session_arrival_welcome` for automatic
origins; `consumeArrivalWelcome()` returns it once and clears it at once,
not at the end of the welcome — `BlockScreen` is recomposed from scratch at
every blocked app, so consuming late would repeat it. `BlockScreen` shows
`origin.welcomeRes` in the phrase slot for `ArrivalWelcomeMillis` (8 s),
then a `Crossfade` (700 ms) to whatever the slot would normally show
(group activity, phrase, or nothing in a breathing pause).

## Notification

`SessionForegroundService.buildNotification` uses `originLabel()` as the
title for a non-group pause, falling back to "Sei in pausa".

## Tests

`SessionManagerTest` (origin stored, label text, welcome once, nothing left
after the pause ends) and the `block-scheduled` screenshot in
`ScreenScreenshotTest`.
