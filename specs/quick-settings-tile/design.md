# Quick Settings Tile — Design

> **Status: Implemented** (2026-10-01).

## Key files

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

## Starting the foreground service from the tile: verified

Like the widget, the tile starts the foreground service from a non-Activity
context. Verified on the API 37 emulator with the app force-stopped: the
tap started the pause and the service went foreground, with the system
reporting `tempAllowListReason: tile onclick` — a tile click grants a
temporary exemption from background-start restrictions. No fallback
needed.

After starting, the tile opens the block screen with
`startActivityAndCollapse`: it confirms the pause started and is the only
API that closes the shade. Below API 34 the `Intent` overload is the only
one available; lint flags it as deprecated even behind the version check,
so it is isolated in `openAndCollapseBeforeApi34()` with the suppression.

## Decisioni prese

1. **Durata:** l'ultima usata, cioè il valore unico condiviso con Home e
   widget (vedi `specs/breathing-pause/`, User Story 2).
2. **Nome:** il nome dell'app, con il sottotitolo che dice l'azione.
