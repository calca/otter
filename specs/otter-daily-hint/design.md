# Otter Daily Hint — Design

## Rules

`OtterHint.pick(context, history, hasSchedules, today, zone)` returns the
`Hint` to show (text, `opensSchedules`, `daily`) or null:

1. empty history and no `otter_hint_done` → the first-pause hint;
2. switch off (`otter_daily_hint_enabled`, default true) → null;
3. already picked today (`otter_daily_hint_last_day` = today's epoch day)
   → null;
4. a `SessionRecord` started today → null;
5. otherwise it saves today and returns, if `!hasSchedules` and
   `random.nextInt(4) == 0`, `otter_hint_schedules` with
   `opensSchedules = true`; else a random item of `otter_daily_phrases`.

`random` is an `internal var` so tests can fix it, like
`CalmCountdown.random`.

## Home

`MainScreen` picks once per composition (`otterHintPicked`), not at every
`resumeSignal`: returning from Settings must not consume another day or
swap the phrase. During a session the hint is cleared. `OtterHintBubble`
takes the text, an `onTap` and an optional `autoHideMillis` (8 s for the
daily one); it fades in after 2 s as before, and keeps the last text during
the fade-out. The text is centered and at most 260dp wide, so the longer
phrases wrap instead of reaching the edges. A tap on the first-pause hint
marks it done; on the schedules one it calls `onScheduledPauses`
(`MainActivity` opens `ScheduledPausesActivity`).

## Settings

One `SettingsSwitchRow` at the end of the Automatic card, with
`OtterTapMark` as icon.

## Tests

`OtterHintTest` (first hint, once a day, not after a pause today, switch,
schedules phrase only without schedules), screenshots `home-daily-hint`
and `home-daily-hint-schedules` (Home captured at 3 s, after the fade-in).
