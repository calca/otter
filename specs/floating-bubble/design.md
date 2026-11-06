# Floating Bubble — Design

> **Status: Proposed** (2026-11-07). Origin: issue #4.

## Window

`AppBlockerAccessibilityService` already adds an overlay window (the
"bridge" window). The bubble reuses the same mechanism:
`TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE`, wrap-content size, drawn
with a small Compose view (`ComposeView` needs a lifecycle owner set by
hand in a service). This avoids `SYSTEM_ALERT_WINDOW` ("draw over other
apps"), which would be a second permission and a Play declaration. Trade-off:
the bubble exists only while the accessibility service is enabled, which is
already a precondition for any pause.

## State

A `BubbleState` derived, not stored: `Hidden`, `Upcoming(startsAt)`,
`Active(endsAt)`. Inputs: `SessionManager` (active session, profile mode),
`ScheduleManager` / surprise alarms (next start). `SessionAlarms` gets one
extra alarm, `AlarmIds.BUBBLE_LEAD`, set 5 minutes before the next start by
`AppScheduler.reconcile()` (so it survives boot and time changes). The
service updates the label once a minute; no per-second tick.

## Position

Stored in plain SharedPreferences (`bubble_x`, `bubble_y`, snapped to the
left/right edge on release). Setting key `bubble_enabled`.

## Interaction

Tap opens `MainActivity` (which shows `BlockScreen` during a session). Drag
uses `WindowManager.updateViewLayout`. The window never takes focus, so it
does not interfere with the keyboard or back handling.

## Decisioni aperte

1. Lead time 5 minutes: fixed or a setting?
2. Show the bubble during an "only these" pause when the user is inside a
   permitted app (the block screen does not cover it)? Proposed: yes, same
   as blocklist, since there the bubble is the only hint.
3. Group pauses: bubble for the host only, or none.

## Tests

`BubbleState` derivation as plain unit tests (modes, lead window, ended
session); position snapping; a screenshot of the bubble view.
