# Quick Settings Tile — Requirements

> **Status: Proposed — not implemented.**

## Context

The moment someone wants a pause is often the moment they've already picked
up the phone to scroll. The widget needs the home screen; the notification
shade is reachable from anywhere, including inside the app that's eating
the time.

## User Story 1: Start from the shade

As the phone's user, I want a Quick Settings tile that starts a pause, so I
can stop from wherever I am.

### Acceptance Criteria

1. The app SHALL provide a Quick Settings tile labelled "Pause" with the
   otter icon, which the user can add from the system tile editor.
2. WHEN no pause is active AND permissions are granted AND the tile is
   tapped THEN the system SHALL start a pause with the last used duration
   (the same value the widget uses) and collapse the shade.
3. WHEN no pause is active AND a required permission is missing THEN
   tapping the tile SHALL open the app on the screen that asks for it,
   instead of starting a pause that would not block anything.
4. WHEN a pause is active THEN the tile SHALL show as active with the
   remaining time as subtitle in the same calm wording as the block screen
   (no exact countdown), and tapping it SHALL open the block screen.
5. WHEN a pause starts or ends from anywhere THEN the tile state SHALL
   update the next time the shade is shown.

## Out of scope

- Choosing a duration from the tile (long-press opens the app, which
  already has the duration pills).
- Starting Time together from the tile.
