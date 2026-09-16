# Pause Session Core — Design

## Key files

| File | Role |
|---|---|
| `SessionManager.kt` | Single source of truth for session state (SharedPreferences: `calm_otter_session`); orchestrates DND, alarm, foreground service, widget refresh, history write |
| `SessionExpiryReceiver.kt` | `BroadcastReceiver` fired by the `AlarmManager` alarm; just calls `endSession(completedNaturally = true)` |
| `SessionForegroundService.kt` | Persistent notification while a session is active; self-stops if it notices the session is no longer active |
| `BootReceiver.kt` | Re-arms DND/alarm/service and re-shows the block screen after reboot |
| `CalmCountdown.kt` | Formatter: remaining minutes → a rotating relaxing phrase, never an exact countdown number. Takes a `Context` — see "Localizing `CalmCountdown`'s phrases" below |

## Session state

Stored directly in `SharedPreferences` (`calm_otter_session`), not Room:
`session_active` (bool), `session_start_time`, `session_end_time`,
`session_planned_minutes`. `isSessionActive()` also self-heals: if
`now >= end_time`, it calls `endSession(completedNaturally = true)` inline
and returns `false` — every caller of `isSessionActive()` gets this cleanup
for free, which is why `BootReceiver` and `AppBlockerAccessibilityService`
don't need their own expiry checks.

## Why a foreground service AND an alarm

The `AlarmManager` alarm (`RTC_WAKEUP`, `setAndAllowWhileIdle`) is what
actually guarantees the session ends on time even if the process was
killed. The foreground service's job is different: it keeps the process
alive in the meantime, which reduces (does not eliminate — see README
"Known Limits") the chance that aggressive OEM battery management kills the
`AccessibilityService` mid-session. The service is `START_STICKY` and polls
`sessionManager.isSessionActive()` on its own 60-second `Handler` loop as a
second self-stop path independent of `endSession()` being called from
elsewhere.

## DND priority categories and suppressed effects

`SessionManager.setPauseDnd(true)` (named `setOnlyCallsAllowed` until alarms
were let through) builds
`NotificationManager.Policy(priorityCategories, PRIORITY_SENDERS_ANY, 0,
suppressedEffects)`. Both varying arguments are decided by the same API 28
check:

- **Below API 28**: `priorityCategories` is `PRIORITY_CATEGORY_CALLS` and
  `suppressedEffects` is `0`.
- **API 28 and up**: `priorityCategories` also carries
  `PRIORITY_CATEGORY_ALARMS` and `PRIORITY_CATEGORY_MEDIA`, and
  `suppressedEffects` is `SUPPRESSED_EFFECT_BADGE |
  SUPPRESSED_EFFECT_NOTIFICATION_LIST | SUPPRESSED_EFFECT_STATUS_BAR`
  (hides notification dots, the pull-down shade content, and the status bar
  icons — added after the initial DND implementation; see
  `SessionManager.kt` history if diffing).

**Alarms and media are deliberately let through.** The line the policy
draws is not "how much can we silence" but *incoming distraction vs.
everything else*: an alarm is a commitment already made, media is something
already playing, and neither is a thing arriving to pull attention away.
Since a pause runs up to four hours, muting an alarm — or cutting music off
mid-track at the exact moment the otter is tapped — lands outside what the
block is for. Both were reported as real bugs after the DND policy first
shipped allowing calls only.

The API 28 check is shared with the suppressed effects but for an unrelated
reason: `PRIORITY_CATEGORY_ALARMS`/`_MEDIA` only exist from API 28, which is
also where DND gained the ability to silence those two channels at all —
below that the priority filter left them alone, so there is nothing to
grant.

Note the scope: this governs **audio only**. Whether a player app can be
*opened* mid-pause is a separate decision, made by the allowed-apps
whitelist (`AllowedAppsManager`, see `app-blocking-and-home-lock/`). The two
have to agree to be useful — a whitelisted Spotify with media muted would
just play to nobody — but they stay separate settings, and neither implies
the other.

## Boot restore vs. self-heal race

`BootReceiver` calls `sessionManager.reapplyAfterBoot()` (re-applies DND +
reschedules the alarm from the *existing* stored end time — it does not
extend it) only `if (sessionManager.isSessionActive())`. Because
`isSessionActive()` already self-heals expired sessions, a session whose end
time passed while the phone was off is correctly treated as over with no
special-casing in `BootReceiver` itself.

## Localizing `CalmCountdown`'s phrases

**Real bug, reported directly by the user**: the countdown phrase
("Ancora circa 25 minuti"/"Freedom's close"/etc.) stayed in Italian no
matter the device's language, unlike every other piece of text on the same
screen. Root cause: `nearEndPhrases`/`templatePhrases` were plain Kotlin
`List<String>` literals hardcoded directly in `CalmCountdown.kt`, not
sourced from `strings.xml` at all — so they never had a `values-en/`
counterpart to pick up, unlike everything else in this app (see CLAUDE.md's
i18n convention). Easy to miss specifically because the surrounding text on
both callers' screens (`BlockScreen`'s "Paused" label and reflective quote,
`SessionForegroundService`'s notification title) *did* localize correctly,
making the countdown phrase alone look like a deliberate exception rather
than a bug.

Fixed the same way `PhraseManager.randomPhrase()` already reads
`R.array.pause_phrases` — moved both lists into
`R.array.calm_countdown_near_end_phrases`/`calm_countdown_template_phrases`
(`values/strings.xml` + `values-en/strings.xml`, English phrases written
fresh for the same calm/anti-anxiety tone, not literal translations) and
changed `CalmCountdown.format()` to take a `Context` and read them via
`context.resources.getStringArray(...)`. Both real call sites already had a
`Context` available: `BlockScreen.kt` passes the `LocalContext.current`
already captured for other purposes in its Composable body (captured into
the `LaunchedEffect` closure, same pattern as `sessionEndedText`/
`wrongPasswordText`), and `SessionForegroundService` (a `Service`, itself a
`Context`) passes `this`. `CalmCountdownTest` became a `RobolectricTestRunner`
test (previously a plain JVM test with no Android dependency) to get a real
`Context` via `ApplicationProvider.getApplicationContext()`, the same
pattern `PhraseManagerTest` already used.

## Testing notes

`SessionManagerTest` (Robolectric) must reset `SessionManager`,
`SessionHistoryManager`, and `CalmOtterDatabase` singletons in `@Before` —
`endSession()` writes through to Room via `SessionHistoryManager`, so all
three singleton instances leak across tests otherwise. See CLAUDE.md.
