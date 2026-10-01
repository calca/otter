# Breathing Pause — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `MainScreen.kt` | `DURATION_LABELS` gains "10 min" first; the label array becomes a list of `(minutes, label)` so the index→minutes mapping is no longer `(index + 1) * 30` |
| `MainActivity.kt` | Initial pill index computed by looking up `DEFAULT_SESSION_DURATION_MINUTES` in the list, not `/ 30` |
| `BlockScreen.kt` | When `totalMillis <= 10 min`: breathing ring and text instead of `ProgressRing` + phrase |
| `ui/screens/BreathingRing.kt` (new) | The animated ring; reads `Settings.Global.ANIMATOR_DURATION_SCALE` for reduced motion |

## One remembered duration

`PauseWidgetProvider.saveLastDuration()`/`getLastDuration()` already exist
and are written by `startSession()`; the widget reads them. They become the
app's single "last duration": moved to a small `LastDurationStore` (or kept
where they are, renamed), written also when a Home pill is tapped, read by
`MainActivity` for the initial pill, by the widget and by the Quick Settings
tile. `startSession()` gains a `rememberDuration: Boolean = true` parameter
that scheduled pauses pass as `false`. The default for a fresh install moves
from the widget's 30 min to `DEFAULT_SESSION_DURATION_MINUTES` (1 h), so
Home and widget agree from the first launch.

## Notes

- **Why "≤ 10 min" and not a flag:** the breathing look follows the length,
  so a 10-minute pause started from the widget or a schedule breathes too,
  without a new session field.
- **Group pauses:** 10 minutes is allowed in the host's duration choice
  (the recipe already accepts 1–255 minutes).
- **Animation cost:** like the pond ripples, stepped at ~10 fps, not every
  frame (see `specs/group-pause/design.md`, "The pulse was implemented wrong
  the first time").

## Decisioni prese

1. **Durata:** 10 minuti.
2. **Respiro:** solo per la pausa breve.
3. **Durata ricordata:** un solo valore per Home, widget e riquadro, salvato
   al tocco della pillola; le pause programmate non lo cambiano.
