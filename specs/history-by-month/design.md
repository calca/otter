# History by Month — Design

> **Status: Implemented** (2026-10-01).

## Key files

| File | Role |
|---|---|
| `HistoryMonths.kt` (new) | Pure `groupByMonth(sessions, zone)` and `visibleMonths(groups, now, extraMonths)`, unit-tested |
| `HistoryScreen.kt` | Month headers, `extraMonths` state, "Show earlier months" |

## Notes

- **Why not a LazyColumn:** the screen is one vertical scroll with the
  stats and cards on top; nesting a lazy list in it is the wrong
  translation (same reason given for the theme grid and the week card).
  Bounding the number of rows composed achieves the same goal without
  restructuring the screen.
- The data is already loaded in memory (`SessionHistoryManager.getAll()`,
  a few hundred small rows): the limit is on composition, not on the query.
- The month a session belongs to is the local month of its start time.

## Decisioni prese

1. Mese corrente + precedente all'apertura; "Mostra mesi precedenti" carica
   un mese alla volta.
2. Statistiche, settimana, "Insieme" ed export continuano a contare tutto.
