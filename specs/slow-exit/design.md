# Slow Exit — Design

> **Status: Implemented** (2026-10-01).

## Key files

| File | Role |
|---|---|
| `SlowExitSettings` (in a small `SlowExitManager.kt`, new) | `enabled`, `waitMinutes`; plain `SharedPreferences`; singleton with `resetInstanceForTests()` |
| `SessionManager.kt` | `KEY_SLOW_EXIT_DEADLINE` (epoch ms, 0 = none); `startSlowExit()`, `cancelSlowExit()`; the expiry alarm logic also fires at the deadline |
| `SessionExpiryReceiver.kt` | Distinguishes natural expiry from slow-exit deadline (extra on the `PendingIntent`) |
| `PasswordVerifyDialog.kt` | Optional secondary action, shown only when slow exit is on |
| `BlockScreen.kt` | Wait state: ring = wait progress, "Stay in the pause" action |
| `SessionRecord.kt` | New column `endReason` (see below) |
| `SettingsScreen.kt` | Password-gated choice of Off / 5 / 10 / 15 min, as small `CalmPill`s in one row (was a radio list) |
| `OnboardingScreen.kt` | One line in the password step: the slow exit exists, and where to switch it off |

## Ending reasons

`SessionRecord.completedNaturally: Boolean` cannot say *how* a pause ended
early. A new column `endReason TEXT NOT NULL DEFAULT 'unknown'` with values
`natural`, `password`, `nfc_release`, `slow_exit`; the migration fills it
from `completedNaturally` (`natural` / `password`) for old rows.
`completedNaturally` stays, since streaks and goals read it. Shares the
version bump with `closing-moment` and `together-activity` when built
together (see `specs/closing-moment/design.md`).

## The wait survives everything

The deadline is persisted, not held in Compose state, and backed by an
`AlarmManager` alarm like the natural expiry. `BootReceiver` already
re-arms the expiry after reboot; it re-arms the slow-exit deadline too. The
block screen reads the deadline on composition, so leaving and coming back
shows the right progress.

## Honesty in the copy

On by default: the app's stance is a soft pact, not a trap. Because nobody
chose it explicitly, the onboarding password step says in one line that it
exists and can be switched off (`OnboardingScreen.kt`, new string), and the
Settings toggle says plainly what it does: "Lets the pause end without the
password after a wait. Weaker than the password: choose it together."
Consistent with the README "Known Limits" stance: no pretending the lock is
stronger than it is. The README gains a line about it under "How it
works".

## Decisioni prese

1. **Attivazione:** accesa di default (più morbida); si spegne solo con la
   password. L'onboarding lo dice.
2. **Attesa:** 10 minuti di default, scelta tra 5, 10 e 15.
3. **Pause di gruppo:** disponibile anche lì.
4. **Testo in Cronologia:** "Finita senza password".
