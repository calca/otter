# Home Screen Widget — Requirements

## Context

A minimal 1×1 home-screen widget lets the user start a pause, or jump to
the block/unlock screen, without opening the app.

## User Story 1: Idle state

As the phone's user, I want a home-screen widget that starts a pause in one
tap when no session is active, so starting a pause doesn't require opening
the app.

### Acceptance Criteria

1. WHEN no session is active THEN the widget SHALL show an idle state (an
   icon and an "idle" label).
2. WHEN the widget is tapped WHILE idle THEN the system SHALL start a
   session using the most recently used duration (30 minutes if none was
   ever used), without opening any Activity.
3. WHEN a session is started from the widget THEN the widget SHALL update
   itself to the active state immediately (not wait for the next periodic
   refresh).

## User Story 2: Active state

As the phone's user, I want the widget to show that a pause is running and
let me jump to the unlock screen, so I don't need to hunt for the app to
end a pause early.

### Acceptance Criteria

1. WHEN a session is active THEN the widget SHALL show an active-state icon
   and a compact remaining-time label (never an exact countdown — see
   `CalmCountdown`-style compact phrasing: "soon" under 5 minutes, rounded
   minutes under an hour, hours+minutes above that).
2. WHEN the widget is tapped WHILE a session is active THEN the system
   SHALL open the block/unlock screen.
3. WHEN a session starts or ends anywhere in the app (not just via the
   widget) THEN every placed instance of the widget SHALL refresh to match.
4. The widget SHALL also refresh periodically (at least every 30 minutes,
   the Android-enforced minimum) so the remaining-time label stays roughly
   current even with no other trigger.

## Out of scope

- Widget visual styling beyond "matches the idle/active states above" —
  see `design.md` for where colors/text live.
