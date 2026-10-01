# Time Together in History — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `CompanionStats.kt` (new) | Pure `companionTotals(sessions, from, to): List<CompanionTotal>` — the grouping and sorting rules, unit-tested |
| `ui/screens/HistoryTogether.kt` (new) | The "Together" card, same tinted-card language as `WeekOverviewCard` |
| `HistoryScreen.kt` | Filter chips "All / Together"; passes the filtered list to everything below |

## Data

No schema change. `companions` is a newline-separated string written by
`SessionManager.endSession()`. Time per companion is the session's
`effectiveMinutes`: a pause with two companions counts fully for each.

## Decisioni aperte

1. **Periodo predefinito:** mese corrente (raccomandato) o sempre.
2. **Pause finite in anticipo:** contano col tempo effettivo (raccomandato).
