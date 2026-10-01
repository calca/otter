# Quick Settings Tile — Design

> **Status: Proposed — not implemented.**

## Key files (planned)

| File | Role |
|---|---|
| `PauseTileService.kt` (new) | `TileService`: `onStartListening` refreshes state, `onClick` starts or opens |
| `AndroidManifest.xml` | `<service>` with `BIND_QUICK_SETTINGS_TILE`, `android.service.quicksettings.action.QS_TILE`, icon, label, `ACTIVE_TILE` meta-data off (state is pulled on listening) |
| `res/drawable/ic_tile_otter.xml` (new) | Monochrome vector: the system tints tiles |
| `SessionManager.kt` | After start/end, `TileService.requestListeningState(...)` like it already calls `PauseWidgetProvider.updateAllWidgets` |
| `PermissionChecks.kt` | Reused to decide "start" vs "open the app" |

## Behaviour

Mirrors `PauseWidgetTapAction`: active → open `BlockOverlayActivity`; idle →
`startSession(PauseWidgetProvider.getLastDuration(context))`. Opening an
Activity from a tile uses `startActivityAndCollapse(PendingIntent)` on API
34+ (the `Intent` overload is deprecated there) and the `Intent` overload
below.

Label: `@string/app_name`, so the beta flavor's override ("Calm Otter
Beta") applies by itself and the two tiles stay distinguishable, like the
two launcher icons.

Subtitle (API 29+): idle → "Tap for a pause · 1 h" with the last used
duration; active → `CalmCountdown.format(remaining)`. The tile is not
updated every minute (that would need `ACTIVE_TILE` and a ticking service),
it is recomputed whenever the shade opens, which is when anyone looks at it.

## Risk to verify first

Like the widget, the tile starts the foreground service from a non-Activity
context. A tile click counts as user interaction and should be exempt from
background-start restrictions, but this must be checked on API 34+ before
anything else. Fallback: `startActivityAndCollapse` to `MainActivity` with
an extra that starts the pause from the foreground.

## Decisioni prese

1. **Durata:** l'ultima usata, cioè il valore unico condiviso con Home e
   widget (vedi `specs/breathing-pause/`, User Story 2).
2. **Nome:** il nome dell'app, con il sottotitolo che dice l'azione.
