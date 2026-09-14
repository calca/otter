# Home Screen Widget — Requirements

## Context

A resizable home-screen widget lets the user start a pause, or jump to the
block/unlock screen, without opening the app. Originally a fixed 1×1 icon
+ label; redesigned in a later pass ("il widget Android è scarsino") into
three sizes on the same provider — see `design.md` for exactly which
composable renders at which size, and the corner-cases (rendering
latency, no theme wiring) accepted along the way.

## User Story 1: Choosing a size

As the phone's user, I want to pick how big the widget is when I place or
resize it, so I can have either the original minimal footprint or a
richer view with more information, on the same home screen.

### Acceptance Criteria

1. WHEN the widget is placed for the first time THEN the system SHALL
   default to the 2×2 size (otter + progress ring + countdown text).
2. WHEN the user resizes the widget smaller THEN the system SHALL allow
   shrinking down to the original 1×1 size (icon + compact label only) —
   requested explicitly so anyone who wants the minimal footprint keeps
   that option.
3. WHEN the user resizes the widget larger THEN the system SHALL allow
   growing up to a 4×2 size, showing the same ring plus a reflective
   phrase and an explicit "Unlock" label alongside the countdown.
4. The widget SHALL remain a single provider across all three sizes —
   not three separate widgets to choose between when placing it.

## User Story 2: Idle state

As the phone's user, I want a home-screen widget that starts a pause in one
tap when no session is active, so starting a pause doesn't require opening
the app.

### Acceptance Criteria

1. WHEN no session is active AND the widget is at its 1×1 size THEN it
   SHALL show the original compact idle state (an icon and an "idle"
   label).
2. WHEN no session is active AND the widget is at its 2×2 or 4×2 size
   THEN it SHALL show the otter mascot centered in an empty (dashed)
   progress ring, with a "tap the otter to start" hint instead of the
   compact 1×1 label.
3. WHEN the widget is tapped WHILE idle, at any size THEN the system
   SHALL start a session using the most recently used duration (30
   minutes if none was ever used), without opening any Activity.
4. WHEN a session is started from the widget THEN the widget SHALL
   attempt to update itself to the active state immediately — see
   `design.md`'s note on an observed system-level redraw delay that this
   can't fully guarantee on its own.

## User Story 3: Active state

As the phone's user, I want the widget to show that a pause is running,
roughly how much time is left, and let me jump to the unlock screen, so I
don't need to hunt for the app to end a pause early.

### Acceptance Criteria

1. WHEN a session is active AND the widget is at its 1×1 size THEN it
   SHALL show the original compact active state: an icon and a compact
   remaining-time label (never an exact countdown — "soon" under 5
   minutes, rounded minutes under an hour, hours+minutes above that).
2. WHEN a session is active AND the widget is at its 2×2 or 4×2 size THEN
   it SHALL show: the otter centered in a progress ring filled in
   proportion to elapsed time, the same reflective countdown phrasing
   already used on the block screen (e.g. "About 25 minutes left" — not
   the 1×1's compact format), and a lock icon signaling where to tap to
   unlock — never a "stop" icon or label, since no tap on this widget can
   end a pause without the accountability partner's password.
3. WHEN a session is active AND the widget is at its 4×2 size THEN it
   SHALL additionally show a short reflective phrase, rotating over time,
   drawn from a pool of shortened versions of the block screen's
   reflective phrases.
4. WHEN the widget (at any size) is tapped WHILE a session is active THEN
   the system SHALL open the block/unlock screen — the lock icon at 2×2/
   4×2 is a visual cue for where to tap, not a separate or different
   action from tapping anywhere else on the widget.
5. WHEN a session starts or ends anywhere in the app (not just via the
   widget) THEN every placed instance of the widget SHALL attempt to
   refresh to match.
6. The widget SHALL also refresh periodically while a session is active
   (every 60 seconds, piggybacking on the same tick that already refreshes
   the persistent notification) so the ring/phrase advance without
   needing another tap, and via the Android-enforced 30-minute minimum
   refresh as an ultimate fallback once a session has ended.

## Known limitations (accepted, not bugs)

- **Redraw latency.** Telling the widget to refresh (`updateAllWidgets()`)
  does not guarantee an immediate on-screen redraw — observed delays of up
  to a minute or more on-device/emulator, traced to system/launcher-side
  scheduling rather than this app's own update calls (see `design.md`).
  During an active session this self-corrects at the next 60-second tick;
  after a session ends, the 30-minute periodic refresh is the ultimate
  guarantee, same as the original 1×1 widget already relied on.
- **The ring advances in visible steps, not smoothly** — a RemoteViews
  limitation (no continuous animation), same category of trade-off the
  persistent notification's own progress bar already has.
- **No live theme/palette matching.** Same pre-existing limitation as the
  original 1×1 widget — colors don't follow the in-app Sage/Lavender/
  Terracotta choice.
- **No participant names for group-pause sessions** — same generic
  treatment the rest of the app gives Tempo Insieme sessions outside their
  own lobby (see `specs/group-pause/`).

## Out of scope

- A real widget-picker preview (`providePreview()`) showing any of the
  three actual layouts — the picker still shows the original 1×1
  placeholder scaled up.
