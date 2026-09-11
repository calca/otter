# Home Screen Widget — Design

## Key files

| File | Role |
|---|---|
| `PauseWidgetProvider.kt` | Contains `PauseGlanceWidget` (the Glance UI), `PauseWidgetTapAction` (tap handling), and `PauseWidgetProvider` itself (the `GlanceAppWidgetReceiver` + the static `updateAllWidgets`/`saveLastDuration`/`getLastDuration` helpers other code calls) |
| `res/xml/widget_pause_info.xml` | `AppWidgetProviderInfo`: 1×1 cell, 30-min update period, points at `widget_pause.xml` as `initialLayout`/`previewLayout` |
| `res/layout/widget_pause.xml` | Plain XML placeholder layout — **required by the Android widget platform**, not legacy code. It's what's shown briefly before Glance renders; don't remove it when touching this feature. |

## Built on Jetpack Glance, not vanilla Compose

Unlike every screen in the app, the widget cannot use `setContent {}` — home
screen widgets run via `RemoteViews` under the hood. Glance
(`androidx.glance:glance-appwidget`) is the Compose-like API Google provides
specifically for this; `PauseGlanceWidget : GlanceAppWidget()` renders its
UI as a normal-looking Composable (`provideGlance` → `provideContent {}`),
which Glance compiles down to `RemoteViews` internally.

## Tap handling

`PauseWidgetTapAction : ActionCallback` — Glance's mechanism for widget
clicks, wired via `GlanceModifier.clickable(actionRunCallback<PauseWidgetTapAction>())`.
This replaced an earlier hand-rolled `PendingIntent` + custom
`com.calmotter.app.WIDGET_TAP` broadcast action (removed from
`AndroidManifest.xml` during the Glance migration — Glance's callback
mechanism handles the broadcast/dispatch internally, so the custom action is
no longer needed).

`onAction`'s logic: if a session is active, open `BlockOverlayActivity`; if
not, call `sessionManager.startSession(PauseWidgetProvider.getLastDuration(context))`
— note `startSession()` itself already calls back into
`PauseWidgetProvider.saveLastDuration()`/`updateAllWidgets()` (see
`pause-session-core/design.md`), so the tap action doesn't need to do that
bookkeeping itself.

## Refreshing widget instances from elsewhere in the app

`SessionManager.startSession()`/`endSession()` call the static
`PauseWidgetProvider.updateAllWidgets(context)`, which launches a
`CoroutineScope(Dispatchers.Default)` coroutine calling
`PauseGlanceWidget().updateAll(context)` — necessary because
`GlanceAppWidget.updateAll` is a `suspend` function but its callers
(`SessionManager`, a `BroadcastReceiver`) are not coroutine-aware.

## Compact time labels

Rendered inline in `PauseGlanceWidget.provideGlance` (not delegated to
`CalmCountdown`, which is tuned for the larger block-screen phrasing): under
5 minutes → `widget_soon`; under 60 → `widget_minutes` rounded down to the
nearest 5; otherwise → `widget_hours` (`Xh Ym`). Colors are static hex
literals matching each state (`0xFF2C4A3E` idle, `0xFF3D7A5C` active) via
Glance's `ColorProvider(day, night)` — **not** wired to the multi-theme
system (see `multi-theme-system/design.md`); the widget doesn't change
color between Sage/Lavender/Terracotta today.
