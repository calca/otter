# Pause After Long Use — Design

> **Status: Implemented** (2026-10-15).

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

The service keeps one `Handler` callback aimed at the next threshold.
That is "limit − 5 min − used" before the heads-up, and "limit − used"
after it. It re-aims the callback on every event that changes the clock
and cancels it while the clock is stopped. Nothing ticks every minute.
Both thresholds are in minutes **of use**, so a `Handler` is enough: the
clock only runs with the screen on, and the bound Accessibility service
keeps the process alive. No alarm is needed.

At "limit − 5 min":

1. Checks: rule on, no pause active, no scheduled pause within 15 min
   (`nextOccurrence` over `ScheduleManager.all()`).
2. Posts the heads-up on the scheduled heads-up's channel. It has "Not
   now" only when the rule is unprotected **and** fewer than 3 "Not now"
   have been used today. The action is a `PendingIntent` to
   `UsagePauseReceiver`.

At "limit":

3. Removes the heads-up and starts the pause through the same path as
   `ScheduledPauseReceiver`'s `ACTION_START` (factor out
   `startScheduledLike(profileId, minutes)`): `startSession(rememberDuration
   = false, profileId = …)`. Then
   `AppBlockerAccessibilityService.onSessionStarted()` covers the app in
   front at once (issue #1's fix).

A reset of the count (a 5-minute break) in between removes the heads-up:
the break has happened.

"Not now" removes the heads-up and moves the limit 15 minutes later
(`limit = used + 15 min`, so the next heads-up arrives after 10 more
minutes of use). It also increments `notNowCount` for today, saved as
`notNowDay` (epoch day) plus `notNowCount`. On a new day the count reads as 0.

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
| `usage_pause_heads_up` | Sei al telefono da %1$s. Tra 5 minuti parte una pausa di %2$s | You've been on your phone for %1$s. A %2$s pause starts in 5 minutes |
| `usage_pause_not_now` | Non ora | Not now |
| `usage_pause_explain` | Conta il tempo con lo schermo acceso, senza guardare quali app usi. Si azzera dopo 5 minuti a schermo spento. | Counts the time with the screen on, without looking at which apps you use. It resets after 5 minutes with the screen off. |

## Privacy

Nothing about which apps are used is saved or counted. The only use of
the foreground package is the yes/no "is this a call or an app that
stays", decided on the spot. The state is three timestamps. This goes in
`docs/privacy-policy.md` in one sentence when it ships.

## Decisions (2026-10-15)

1. **Automatic start**, with a heads-up 5 minutes before (in minutes of
   use), as for scheduled pauses. An invitation that starts nothing unless
   tapped would be easy to ignore, and ignoring it is the problem this
   feature exists for.
2. **Apps that stay don't count**, including a feed if someone puts one
   there: it's a pact of trust.
3. **"Not now" at most 3 times a day**, only on an unprotected rule.
4. **Nothing at night**, 23:00–07:00, fixed in this version (not a
   setting). The checks at "limit − 5 min" and "limit" skip that time
   window. A threshold that falls inside it is re-aimed at 07:00, so the
   count is still running when the night ends. Anyone who wants pauses at
   night uses a scheduled pause.

## As built: notes

- **Files.** `UsagePause.kt` holds the pure parts: `UsageCounter`,
  `UsagePauseRule`, `usagePasswordNeededToSave`, the night helpers and
  `nextScheduledStart`. It also holds `UsagePauseManager` (in `AppGraph`).
  `UsageWatcher.kt` holds the wiring inside the Accessibility service and
  `UsagePauseReceiver` ("Not now"). The page is `UsagePauseActivity` /
  `UsagePauseScreen`, with a row in Settings → The pause (`ic_phone_time`).
- **Off means nothing is counted.** With the rule off, the watcher ignores
  screen and app events, and switching it off clears the count. The
  Accessibility disclosure, `docs/privacy-policy.md` and the Play
  declaration text (`docs/store/declarations.md`) now say the service also
  serves this feature.
- **The thresholds** run on one `Handler` callback and no alarm, since the
  clock only runs with the screen on. The page saves on every change,
  shares the editor's `OptionStepper`/`EditorCard`, and has no Save CTA.
- **The heads-up channel** is `calm_otter_usage_pause` at high importance,
  so it shows on top of the app in use. "Not now" appears only when the
  rule is unprotected and there are uses left today.
- **Verified on the API 37 emulator** (Europe/Rome, daytime), with the
  count seeded at 14.5 min and the limit at 20. The heads-up arrived at 15
  min ("Sei al telefono da 15 min…", with "Non ora"). "Non ora" removed it,
  moved the limit 15 minutes of use later and recorded 1 of 3. Seeded at
  19.5 min with the heads-up already shown, the pause started by itself at
  20 min, lasting 10 min, and the count reset. Changing app then showed
  the block every time.
- **Known gap.** When the process has just restarted and the app in front
  hasn't changed since, the service doesn't know that app yet. A pause
  starting at that moment covers it at the next app change, not
  immediately: the same limit as issue #1's fix. With Accessibility on, the
  process normally lives on, so this needs a crash or a kill.

## Decisioni aperte

1. **Per-app limits** (a v2 with `UsageStats`): worth the extra permission
   and Play declaration, or does it go against "it doesn't look at what you
   use"?
