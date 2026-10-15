# Pause After Long Use — Design

> **Status: Proposed** (2026-10-15).

## How to measure "in use": screen events, not UsageStats

Two ways to know the phone is being used:

| | `UsageStatsManager` | Screen events in the Accessibility service |
|---|---|---|
| Permission | `PACKAGE_USAGE_STATS`, another system settings page, Play declaration | none new (Accessibility is already required) |
| Per-app detail | yes | no, by design |
| Wakes up | polling | events: `SCREEN_OFF`, `USER_PRESENT`, plus the window events the service already gets |

**Proposal: screen events.** This is the only option that adds no permission.
It also counts only what the requirements ask for (continuous use), and
the app never learns anything new about which apps the person opens.
Per-app limits would need `UsageStats` and stay a separate decision.

`AppBlockerAccessibilityService` is the right host. It is bound by the
system for as long as Accessibility is on, so its process stays alive
without a foreground service. It already sees which full-screen app is in
front (`lastFullScreenPackage`, added for issue #1), which is what the
"time in a call or an allowed app doesn't count" rule needs. `SCREEN_ON`
and `SCREEN_OFF` can't be declared in the manifest anyway: they need a
receiver registered at runtime by a component that lives on.

## `UsageCounter`: pure, then wired

```kotlin
/** Tempo d'uso continuo. Puro: riceve eventi con il loro istante. */
class UsageCounter(private val breakMillis: Long = 5 * 60_000L) {
    fun screenOn(at: Long)      // unlocked (USER_PRESENT) or screen on while already unlocked
    fun screenOff(at: Long)
    fun countingPaused(at: Long, paused: Boolean) // call screen / allowed app / pause in force
    fun usedMillis(at: Long): Long
    fun reset(at: Long)
    // stato serializzabile in due-tre Long, vedi sotto
}
```

- `screenOff` stops the clock. `screenOn` after `breakMillis` or more
  resets it; after less, it carries on.
- `countingPaused(true)` stops it without resetting.
- State: `accumulatedMillis`, `runningSince` (0 = stopped),
  `lastScreenOffAt`. Saved in plain `SharedPreferences` on every event (a
  handful per minute at most), so a restarted process picks up where it
  was. No history, no package names.

Unit tests on `UsageCounter` alone cover the rules: break shorter or longer
than 5 min, an allowed app in the middle, a call, reset after a pause.

## The trigger

The service keeps one `Handler` callback aimed at "limit − used". It
re-aims it on every event that changes the clock, and cancels it while
the clock is stopped. Nothing ticks every minute. When it fires:

1. Checks again: rule on, no pause active, no scheduled pause within
   15 min (`nextOccurrence` over `ScheduleManager.all()`).
2. Posts the heads-up (channel of the scheduled heads-up, high importance
   so it peeks, `timeoutAfter` 1 min). It has a "Not now" action only for
   an unprotected rule, a `PendingIntent` to `UsagePauseReceiver`.
3. Arms the start with `AlarmManager.setExactAndAllowWhileIdle` if allowed,
   otherwise a `Handler` with the screen on. One minute, screen almost
   surely on, process alive thanks to the service, so the Handler alone
   would do. The alarm is a guard for a process killed in between.
4. The start goes through the same path as `ScheduledPauseReceiver`'s
   `ACTION_START` (factor out `startScheduledLike(profileId, minutes)`):
   `startSession(rememberDuration = false, profileId = …)`. Then
   `AppBlockerAccessibilityService.onSessionStarted()` covers the app in
   front at once (issue #1's fix).

"Not now" cancels the start and sets the next trigger to `used + 15 min`.

A new request code in `AlarmIds` for the start alarm.

## Storage and password

`UsagePauseRule(enabled, afterMinutes, durationMinutes, profileId, locked)`,
one line in `SharedPreferences` via a small `UsagePauseManager` in
`AppGraph`. `isLooserThan`: switched off, higher `afterMinutes`, shorter
`durationMinutes`, other profile, protection removed. A pure function with
tests, shaped like the scheduled pauses one, using the same
`PasswordVerifyDialog`.

## UI

Settings → "The pause": new row "After long use" / "Dopo un uso lungo"
with a subtitle showing the state ("Off" / "After 45 min · 10 min pause").
Page `UsagePauseActivity`, laid out like the schedule editor: one card with
"After" (stepper over 20/30/45/60/90) and "Pause" (stepper over 10/30/60),
one card with "Apps that stay" (only with 2+ profiles) and "Protected by
password". The on/off is a switch at the top of the page. It saves as it
changes, as Settings switches do, with no Save CTA: there is no form to
confirm, and the password is asked at the change that loosens.

Strings (IT / EN):

| key | IT | EN |
|---|---|---|
| `usage_pause_title` | Dopo un uso lungo | After long use |
| `usage_pause_after` | Dopo | After |
| `usage_pause_heads_up` | Sei al telefono da %1$s. Tra un minuto parte una pausa di %2$s | You've been on your phone for %1$s. A %2$s pause starts in a minute |
| `usage_pause_not_now` | Non ora | Not now |
| `usage_pause_explain` | Conta il tempo con lo schermo acceso, senza guardare quali app usi. Si azzera dopo 5 minuti a schermo spento. | Counts the time with the screen on, without looking at which apps you use. It resets after 5 minutes with the screen off. |

## Privacy

Nothing about which apps are used is saved or counted. The only use of
the foreground package is the yes/no "is this a call or an app that
stays", decided on the spot. The state is three timestamps. This goes in
`docs/privacy-policy.md` in one sentence when it ships.

## Decisioni aperte

1. **Proposal or automatic start?** Proposal: an automatic start after one
   minute, with "Not now" only if unprotected. The alternative, a
   notification "Take a pause?" that starts nothing unless tapped, is
   kinder but easy to ignore, which is the problem it is meant to solve.
2. **Allowed apps don't count**: right for Maps and calls, but if
   someone puts Instagram among the apps that stay, it never counts. OK
   (it's their choice), or count everything except calls?
3. **Quiet hours**: don't propose at night (e.g. 23:00–07:00), when the
   phone in bed is the problem but a forced pause at 1:00 isn't
   welcome? Or is that exactly when it helps?
4. **Per-app limits** (a v2 with `UsageStats`): worth the extra permission
   and Play declaration, or does it go against "it doesn't look at what you
   use"?
5. **"Not now" limit**: unlimited every 15 min, or at most once per
   count (the second time it starts without asking)?
