# Time Together in History — Design

> **Status: Implemented** (2026-10-01).

## Key files

| File | Role |
|---|---|
| `CompanionStats.kt` (new) | Pure `companionTotals(sessions, from, to): List<CompanionTotal>` — grouping, sorting, and per-companion top categories, unit-tested |
| `ui/screens/HistoryTogether.kt` (new) | The "Together" card, same tinted-card language as `WeekOverviewCard` |
| `HistoryScreen.kt` | A "Together" tab (only with group pauses): their totals, the Together card, their list by month. Was an "All / Together" filter; see specs/session-history-and-stats/design.md, "Two tabs" |

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
