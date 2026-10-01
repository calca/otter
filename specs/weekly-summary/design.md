# Weekly Summary — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `WeeklySummaryReceiver.kt` (new) | Fires Sunday 20:00, computes the week, posts or skips, re-arms next week |
| `WeeklySummary.kt` (new) | Pure `summaryFor(sessions, weekStart, weekEnd)` and the text, unit-tested |
| `BootReceiver.kt` | Re-arms the weekly alarm |
| `SettingsScreen.kt` | Toggle "Weekly note" in "Pause experience" |

## Mechanics

Inexact `AlarmManager` (`setWindow`, 30 min), same reasoning as scheduled
pauses: no exact-alarm permission for a note. A separate notification
channel ("Weekly note", low importance, no sound) so the user can mute it
from system settings independently of the session channel. If a session is
active, the receiver sets a flag that `endSession()` checks, and the note
is posted when the pause ends.

The "week" is the same one the History week card uses, so the count
always agrees with it: it comes from the same function (`weeklyChartData`),
not a parallel computation. Only the number of pauses is shown, through a
plural resource (`weekly_note_pauses`), never time.

## Decisioni prese

1. **Attivo di default**, spegnibile senza password.
2. **Contenuto:** solo il numero di pause, niente ore e minuti.
3. **Quando:** domenica alle 20:00.
