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

The "week" is the same one the History week card uses, so the two numbers
always agree. The numbers come from the same functions (`weeklyChartData`),
not a parallel computation.

## Decisioni aperte

1. **Attivo di default?** Raccomandato sì, perché arriva solo con almeno una
   pausa ed è spegnibile in un tocco.
2. **Giorno e ora:** domenica 20:00 (raccomandato).
