# Time Together in History — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `CompanionStats.kt` (new) | Pure `companionTotals(sessions, from, to): List<CompanionTotal>` — grouping, sorting, and per-companion top categories, unit-tested |
| `ui/screens/HistoryTogether.kt` (new) | The "Together" card, same tinted-card language as `WeekOverviewCard` |
| `HistoryScreen.kt` | Filter chips "All / Together"; passes the filtered list to everything below |

## Data

No schema change. `companions` is a newline-separated string written by
`SessionManager.endSession()`. Time per companion is the session's
`effectiveMinutes`: a pause with two companions counts fully for each.

## Categories

`CompanionTotal` carries `topCategories: List<TogetherCategory>` (at most
three, distinct, by frequency; ties broken by the most recent pause). The
category comes from `SessionRecord.activityId` through
`TogetherActivities.byId()`; id `0` is skipped. This makes the feature
depend on `together-activity` being built first, which the suggested order
in `specs/README.md` already does.

## Decisioni prese

1. **Periodo predefinito:** mese corrente, con "sempre" a un tocco.
2. **Pause finite in anticipo:** contano con il tempo effettivo.
3. **Categorie:** fino a tre icone per persona, le più frequenti.
