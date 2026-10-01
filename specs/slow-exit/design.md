# Slow Exit — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `SlowExitSettings` (in a small `SlowExitManager.kt`, new) | `enabled`, `waitMinutes`; plain `SharedPreferences`; singleton with `resetInstanceForTests()` |
| `SessionManager.kt` | `KEY_SLOW_EXIT_DEADLINE` (epoch ms, 0 = none); `startSlowExit()`, `cancelSlowExit()`; the expiry alarm logic also fires at the deadline |
| `SessionExpiryReceiver.kt` | Distinguishes natural expiry from slow-exit deadline (extra on the `PendingIntent`) |
| `PasswordVerifyDialog.kt` | Optional secondary action, shown only when slow exit is on |
| `BlockScreen.kt` | Wait state: ring = wait progress, "Stay in the pause" action |
| `SessionRecord.kt` | New column `endReason` (see below) |
| `SettingsScreen.kt` | Password-gated toggle and wait length |

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

The Settings toggle says plainly what it does: "Lets the pause end without
the password after a wait. Weaker than the password: choose it together."
Consistent with the README "Known Limits" stance: no pretending the lock is
stronger than it is.

## Decisioni aperte

1. **Durata dell'attesa:** 5/10/15 minuti, default 10 (raccomandato).
2. **Disponibile anche in una pausa di gruppo?** Raccomandato sì: è una
   scelta di chi tiene la password su quel telefono.
3. **Testo in Cronologia:** "Finita senza password" (raccomandato) oppure
   "Uscita lenta".
