# Breathing Pause — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `MainScreen.kt` | `DURATION_LABELS` gains "10 min" first; the label array becomes a list of `(minutes, label)` so the index→minutes mapping is no longer `(index + 1) * 30` |
| `MainActivity.kt` | Initial pill index computed by looking up `DEFAULT_SESSION_DURATION_MINUTES` in the list, not `/ 30` |
| `BlockScreen.kt` | When `totalMillis <= 10 min`: breathing ring and text instead of `ProgressRing` + phrase |
| `ui/screens/BreathingRing.kt` (new) | The animated ring; reads `Settings.Global.ANIMATOR_DURATION_SCALE` for reduced motion |

## Notes

- **Why "≤ 10 min" and not a flag:** the breathing look follows the length,
  so a 10-minute pause started from the widget or a schedule breathes too,
  without a new session field.
- **Group pauses:** 10 minutes is allowed in the host's duration choice
  (the recipe already accepts 1–255 minutes).
- **Animation cost:** like the pond ripples, stepped at ~10 fps, not every
  frame (see `specs/group-pause/design.md`, "The pulse was implemented wrong
  the first time").

## Decisioni aperte

1. **10 o 15 minuti:** raccomandato 10.
2. **Il respiro solo per la pausa breve** (raccomandato) o un'opzione per
   tutte.
