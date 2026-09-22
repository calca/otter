# Home Screen Widget — Design

## Key files

| File | Role |
|---|---|
| `PauseWidgetProvider.kt` | Contains `PauseGlanceWidget` (the Glance UI, now three size-specific composables — see "Three responsive sizes" below), `PauseWidgetTapAction` (tap handling), `buildRingBitmap`/`buildRingFor` (the rasterized progress ring), and `PauseWidgetProvider` itself (the `GlanceAppWidgetReceiver` + the static `updateAllWidgets`/`saveLastDuration`/`getLastDuration` helpers other code calls) |
| `res/xml/widget_pause_info.xml` | `AppWidgetProviderInfo`: default 2×2 placement, resizable down to the original 1×1 or up to 4×2, 30-min update period, points at `widget_pause.xml` as `initialLayout`/`previewLayout` |
| `res/layout/widget_pause.xml` | Plain XML placeholder layout — **required by the Android widget platform**, not legacy code. It's what's shown briefly before Glance renders, and what the widget-picker preview shows (no `providePreview()` implemented — see "What this revision doesn't do" below). Don't remove it when touching this feature. |
| `res/drawable/ic_widget_unlock.xml` | The corner lock icon shown in the 2×2/4×2 active state — a lock, deliberately never a "stop" icon (see "Why a lock, never stop" below). |

## Built on Jetpack Glance, not vanilla Compose

Unlike every screen in the app, the widget cannot use `setContent {}` — home
screen widgets run via `RemoteViews` under the hood. Glance
(`androidx.glance:glance-appwidget`) is the Compose-like API Google provides
specifically for this; `PauseGlanceWidget : GlanceAppWidget()` renders its
UI as a normal-looking Composable (`provideGlance` → `provideContent {}`),
which Glance compiles down to `RemoteViews` internally.

## Three responsive sizes, one widget

Originally a fixed 1×1 cell. On request ("il widget è scarsino"), redesigned
around three sizes the *same* provider now supports via
`override val sizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_SQUARE, SIZE_WIDE))`
(`SIZE_SMALL = DpSize(40.dp, 40.dp)`, `SIZE_SQUARE = 110×110dp`,
`SIZE_WIDE = 250×110dp` — the 40/110/250dp figures follow the same
"70dp × cells − 30dp" convention the original 1×1's `minWidth="40dp"`
already used, not invented fresh for this revision). Inside
`provideContent {}`, `LocalSize.current` (an exact match against one of the
three declared `DpSize` values, since `Responsive` mode always snaps to the
nearest declared size) picks which of three private composables to render:

- **`SmallWidgetContent`** — the original 1×1 UI, pixel-for-pixel unchanged
  from before this revision. Kept as the smallest resize target for anyone
  who wants the minimal footprint, on explicit request ("il vecchio 1×1
  esiste, per chi vuole il minimo").
- **`SquareWidgetContent`** (2×2, the new default placement size) — the
  otter centered inside a progress ring, the current countdown phrase
  below (reusing `CalmCountdown.format()`, not the old compact
  `widget_soon`/`widget_minutes`/`widget_hours` strings — those stay only
  for the 1×1, where there's no room for a full phrase), and a lock icon
  pinned to the top-end corner when a session is active.
- **`WideWidgetContent`** (4×2) — same ring+otter, now on the left with a
  Row; to its right, the countdown phrase, a short rotating reflective
  phrase (`R.array.widget_reflective_phrases` — see "Shortened reflective
  phrases" below), and an explicit "Sblocca"/"Unlock" label next to the
  lock icon (room here to spell it out, unlike the 2×2's icon-only corner).

`widget_pause_info.xml` was updated to match: `minWidth`/`minHeight` now
110dp (2×2 is the default placement), `targetCellWidth`/`targetCellHeight`
= 2, `minResizeWidth`/`minResizeHeight` = 40dp (down to 1×1),
`maxResizeWidth` = 250dp/`maxResizeHeight` = 110dp (up to 4×2),
`resizeMode="horizontal|vertical"`.

## The progress ring is a rasterized `Bitmap`, not a native Glance composable

**Verified, not assumed**: `androidx.glance.appwidget.CircularProgressIndicator`
(decompiled from the actual `glance-appwidget:1.2.0` `.aar` before writing
any ring code) takes no progress parameter at all — it's an indeterminate
spinner, the RemoteViews equivalent of Android's classic spinning
`ProgressBar`. There is no way to ask it for "62% filled." (Glance's
`LinearProgressIndicator`, by contrast, *does* take a `progress: Float`
0–1 — a straight bar was the fallback considered and rejected on request,
"l'anello, è più personale".)

`buildRingBitmap(context, sizeDp, strokeDp, fraction)` draws the ring
directly with `android.graphics.Canvas`/`Paint`/`RectF`/`DashPathEffect`
onto a plain `Bitmap`, then `ImageProvider(bitmap)` (a real, public Glance
factory function — also confirmed by decompiling `glance:1.2.0`, not
assumed) hands that bitmap to an `Image` composable like any drawable
resource. `fraction = null` draws only a dashed track (idle state, no
session to show progress for); a `0f..1f` value draws a second, solid arc
on top using the exact same "elapsed / total" math `ProgressRing` in
`MainScreen.kt` already uses for the live Home/BlockScreen ring — same
formula, rasterized once per refresh instead of animated continuously
(RemoteViews can't animate; see "A ring that advances in steps, not
smoothly" below for why that's an accepted trade-off, not an oversight).

`buildRingFor(context, sessionManager, ringSizeDp)` is the small wrapper
that reads `isSessionActive()`/`remainingMillis()`/`totalMillis()` and
calls `buildRingBitmap()` with the right `fraction` (or `null`) — kept
separate from the drawing function itself so the drawing math has no
`SessionManager` dependency of its own.

## Why a lock, never "stop"

The corner icon shown during an active session is a **lock**
(`ic_widget_unlock.xml`), tapped to reach the same password-gated unlock
screen the whole card already opens. It is deliberately never a "stop"
icon: nothing in this app can end a pause without the accountability
partner's password (see root `CLAUDE.md`/`README.md` — the entire premise
of the app), and a "stop" glyph would visually promise a one-tap end that
doesn't exist. The icon is a **visual affordance, not a second click
target** — it sits inside the same `Box` that already has
`.clickable(actionRunCallback<PauseWidgetTapAction>())` applied to the
whole card, so tapping the lock and tapping anywhere else on the card
during an active session do the exact same thing (open
`BlockOverlayActivity`). This was placed inside the ring itself in the
first pass and moved to a fixed card corner after review ("si può mettere
da un'altra parte, così nel vuoto sta male") — implemented as an outer
`Box(contentAlignment = Alignment.TopEnd)` containing a `fillMaxSize()`
inner `Column` (which, filling all available space, ignores the outer
box's stated alignment for its own contents) plus the small lock `Image`
as a second, non-filling sibling — the only sibling small enough for the
outer box's `TopEnd` alignment to actually move it into the corner, since
Glance's `Box` has one alignment for all its direct children, not a
per-child override like Compose's `BoxScope.align()`.

## Shortened reflective phrases

The 4×2 layout's rotating phrase (`R.array.widget_reflective_phrases`,
both locales) is a *separate*, shorter pool from `pause_phrases`
(BlockScreen's 12 full-sentence reflective phrases) — those don't fit a
widget's available width. Ten of the twelve were kept, each shortened
(e.g. "Respira. Questo momento è tuo." → "Un momento per te"; "La mente ha
bisogno di vuoto per fare spazio alle idee." → "Fai spazio alla mente"),
landing on the exact wording after a couple of review rounds (three of
the first ten proposed replacements — "Stai facendo bene"/"Ce la fai"/"Sei
sulla strada giusta", reused from `notification_encouragement_phrases` at
the time — were flagged as reading like coaching/effort language, out of
step with the rest of the app's "presence, not performance" tone, and
swapped for "Un momento per te"/"Rallenta un attimo"/"Va tutto bene così"
instead). Two of the twelve (the rhetorical "what would you do without
your phone" question, and the body/mind one) were dropped rather than
forced into an awkward short form.

## Tap handling (unchanged in behavior, now shared across three layouts)

`PauseWidgetTapAction : ActionCallback` — Glance's mechanism for widget
clicks, wired via `GlanceModifier.clickable(actionRunCallback<PauseWidgetTapAction>())`
on the outer container of all three size-specific composables. This
replaced an earlier hand-rolled `PendingIntent` + custom
`com.calmotter.app.WIDGET_TAP` broadcast action (removed from
`AndroidManifest.xml` during the original Glance migration — Glance's
callback mechanism handles the broadcast/dispatch internally, so the
custom action is no longer needed).

`onAction`'s logic, identical for all three sizes: if a session is
active, open `BlockOverlayActivity`; if not, call
`sessionManager.startSession(PauseWidgetProvider.getLastDuration(context))`
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

**New**: `SessionForegroundService`'s existing 60-second tick (already
there to refresh the persistent notification, see
`pause-session-core/design.md`) now also calls
`PauseWidgetProvider.updateAllWidgets()` on every tick while a session is
active — not a new mechanism, just one more caller of the same function,
so the 2×2/4×2 ring advances and the 4×2's reflective phrase rotates at
the same cadence the notification's own subtext already does.

### A ring that advances in steps, not smoothly (accepted, not a bug)

RemoteViews can't animate a value between two updates — the ring visibly
"jumps" once a minute (the tick cadence above) rather than sweeping
continuously the way `ProgressRing` does live in Compose on
Home/BlockScreen. This is the same category of trade-off the persistent
notification's own progress bar already has; not a regression introduced
by adding the widget's ring.

### A real, unresolved latency: widget redraws can lag behind `updateAll()` by up to a minute or more

Observed on-device/emulator during this revision's own verification, and
worth recording honestly rather than glossing over: after a tap starts or
ends a session, `updateAllWidgets()` fires immediately, and Glance's own
logs (`GlanceSessionManager: Closing session ... wasOpen=false`) show the
RemoteViews recomposition completing within seconds — but the actual
on-screen redraw of the placed widget can lag those logs by up to a
minute or more before the launcher visibly catches up. A same-session
experiment adding a second, delayed `updateAll()` call a few seconds
later did **not** measurably help (the log evidence showed Glance's own
recomposition was already long finished well before the visible redraw
happened, pointing at system/launcher-side `AppWidgetHostView` redraw
scheduling as the actual bottleneck, not anything in this app's update
calls) — that speculative fix was reverted rather than kept unverified.
During an active session this self-corrects at the next 60-second tick
regardless; at the end of a session, there's no more tick to fall back
on, and the ultimate safety net is the same 30-minute
`updatePeriodMillis` system refresh the original 1×1 widget already
relied on. Restarting the app process (e.g. any update install) also
forces an immediate fresh composition, which is how this was confirmed
to be a rendering-lag issue and not a stuck/incorrect state — the
underlying session data was always correct; only the visible redraw was
slow.

## What this revision doesn't do

- **No `providePreview()`.** The widget-picker's preview card still shows
  the plain `widget_pause.xml` placeholder (the old 1×1 look) scaled up,
  not a real preview of any of the three actual Glance layouts. Glance
  supports a real `providePreview()` override for this; out of scope here.
- **No palette wiring.** Same pre-existing limitation as before this
  revision — colors are still the two static hex literals
  (`0xFF2C4A3E`/`0xFF3D7A5C`) regardless of the Sage/Lavender/Terracotta
  choice made in-app (see `multi-theme-system/design.md`).
- **No named participants for group-pause sessions.** The widget's
  countdown/phrase text doesn't distinguish a Tempo Insieme (group) session
  from a solo one — same generic treatment `BlockScreen` already gives
  group sessions (see `specs/group-pause/design.md`).


## `widget_pause.xml`'s two icons were invisible *and* unlabelled to TalkBack

Found during a full-project review (`TODO.md` "4.1"), not a report. This is
the plain XML `initialLayout` (see "Built on Jetpack Glance" above — still
required by the platform, briefly shown before Glance's real content loads)
— the real Glance widget (`PauseWidgetProvider.kt`) already gets this right,
consistently passing `contentDescription = null` on every decorative
`Image` because the adjacent `Text` already carries the same information in
words. `widget_pause.xml` was the one place that pattern hadn't been
applied: both `ImageView`s (`ic_otter_widget`, `ic_pause_widget`) had no
`android:contentDescription`, and both `TextView`s were `10sp`, under the
11sp floor.

Fixed the same way the Glance version already does it: `android:
contentDescription="@null"` on both images (explicitly decorative — the
label alongside says the same thing, echoing it would be a second,
redundant announcement, not a fix), `textSize` bumped to `11sp` on both.
Verified by lint (`ContentDescription`/`SmallSp`, both gone from the
report).
