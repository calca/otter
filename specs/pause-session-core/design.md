# Pause Session Core — Design

## Key files

| File | Role |
|---|---|
| `SessionManager.kt` | Single source of truth for session state (SharedPreferences: `calm_otter_session`); orchestrates DND, alarm, foreground service, widget refresh, history write |
| `SessionExpiryReceiver.kt` | `BroadcastReceiver` fired by the `AlarmManager` alarm; just calls `endSession(completedNaturally = true)` |
| `SessionForegroundService.kt` | Persistent notification while a session is active; self-stops if it notices the session is no longer active |
| `BootReceiver.kt` | Re-arms DND/alarm/service and re-shows the block screen after reboot |
| `CalmCountdown.kt` | Pure formatter: remaining minutes → a rotating relaxing phrase, never an exact countdown number |

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

## DND suppressed effects

`SessionManager.setOnlyCallsAllowed(true)` builds
`NotificationManager.Policy(PRIORITY_CATEGORY_CALLS, PRIORITY_SENDERS_ANY, 0,
suppressedEffects)`, where `suppressedEffects` is `0` below API 28 and
`SUPPRESSED_EFFECT_BADGE | SUPPRESSED_EFFECT_NOTIFICATION_LIST |
SUPPRESSED_EFFECT_STATUS_BAR` from API 28 up (hides notification dots, the
pull-down shade content, and the status bar icons — added after the initial
DND implementation; see `SessionManager.kt` history if diffing).

## Boot restore vs. self-heal race

`BootReceiver` calls `sessionManager.reapplyAfterBoot()` (re-applies DND +
reschedules the alarm from the *existing* stored end time — it does not
extend it) only `if (sessionManager.isSessionActive())`. Because
`isSessionActive()` already self-heals expired sessions, a session whose end
time passed while the phone was off is correctly treated as over with no
special-casing in `BootReceiver` itself.

## Testing notes

`SessionManagerTest` (Robolectric) must reset `SessionManager`,
`SessionHistoryManager`, and `CalmOtterDatabase` singletons in `@Before` —
`endSession()` writes through to Room via `SessionHistoryManager`, so all
three singleton instances leak across tests otherwise. See CLAUDE.md.
