# Floating Bubble — Requirements

> **Status: Proposed** (2026-11-07). Not built yet. Origin: issue #4.

## Context

During a pause the app is mostly invisible, and before a pause starts
(scheduled, surprise, group) the user may be deep in another app with no
hint that it is about to begin. A small floating bubble over other apps
would show the pause state at a glance. Two use cases, from the issue:
**during an active session** and **when one is about to start**.

## User Story 1: Bubble during an active pause

As the phone's user, I want a small bubble while a pause is running, so I
can see it is on and how long is left when only some apps are blocked.

### Acceptance Criteria

1. WHEN a pause is active and the profile is in "all except these" mode
   (blocklist) THEN the system SHALL show a bubble over the permitted apps
   with the remaining time. With the "only these" mode everything else is
   already covered by the block screen, so no bubble is shown.
2. Tapping the bubble SHALL open the block screen; it SHALL NOT end or
   loosen the pause and SHALL NOT ask for the password.
3. THE bubble SHALL disappear when the pause ends, for any reason.
4. THE bubble SHALL be draggable and remember its edge/position.

## User Story 2: Bubble before a pause starts

As the phone's user, I want a heads-up that a pause is about to begin, so
it does not catch me in the middle of something.

### Acceptance Criteria

1. WHEN a scheduled or surprise pause is due in the next minutes THEN the
   system SHALL show the bubble with a countdown. The lead time is fixed
   (proposal: 5 minutes), not configurable in the first version.
2. WHEN the pause starts THEN the bubble SHALL turn into the active-pause
   bubble (Story 1) or disappear, depending on the mode.
3. WHEN the user cancels or skips the upcoming pause THEN the bubble SHALL
   disappear.

## Settings and permission

1. THE bubble SHALL be off by default; a switch in Settings turns it on.
2. THE app SHALL NOT need a new permission: the bubble uses the
   accessibility service already required (see design).
3. THE bubble SHALL honour "remove animations" and expose a name and a
   48 dp touch area for TalkBack.
4. The bubble is a courtesy, not part of the lock: it SHALL NOT be described
   as making the lock stronger.

## Out of scope

- Group pauses (guests see their own state on the block screen).
- Controls on the bubble (pause, extend, unlock).
