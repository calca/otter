# Pause After Long Use — Requirements

> **Status: Proposed** (2026-10-15). Not built yet; see "Decisioni aperte" in design.md.

## Context

Pauses today start when someone decides: a tap, a schedule, a friend.
The moment that most needs one is the one nobody decides: forty minutes
into a scroll that was meant to last two. By then the user is the last
person who'll tap the otter.

This feature measures one thing, **how long the phone has been in use
without a break**. When that passes a limit the user chose, it proposes a
short pause and then starts it. It doesn't look at which apps are used or
for how long each: no per-app counting, no new permission, nothing stored
beyond a couple of timestamps.

## User Story 1: The phone notices for me

As the phone's user, I want a pause to start by itself after I've been on
the phone for a while without a break, so the long scroll ends even when I
don't notice it.

### Acceptance Criteria

1. Settings → "The pause" SHALL have a **"After long use"** row, off by
   default, opening a page with: on/off, "after" (20, 30, 45, 60, 90
   minutes of use; default 45), the pause's duration (10, 30, 60 minutes;
   default 10, the breathing pause), and "Protected by password".
2. "In use" SHALL mean: screen on and unlocked, not in a pause. The count
   SHALL reset after the screen has been off for at least 5 minutes in a
   row (a real break). A shorter screen-off SHALL only stop the count,
   not reset it.
3. Time in a phone call, and time in an app that stays during a pause
   (the profile chosen for these pauses), SHALL NOT count. Maps while
   driving or a call with a friend is not the long scroll.
4. WHEN the count reaches the limit THEN the system SHALL show a heads-up
   notification "You've been on your phone for 45 min. A 10-minute pause
   starts in 1 minute" and SHALL start the pause one minute later, exactly
   as a tap on the otter would (same permission checks, same "couldn't
   start" notification as a scheduled pause).
5. WHEN the screen turns off during that minute THEN the pause SHALL still
   start: the user has already taken the hint, and the pause keeps it.
6. WHEN a pause (of any kind) ends THEN the count SHALL restart from zero.

## User Story 2: "Not now", when it's mine to say

### Acceptance Criteria

1. WHEN the rule is not protected THEN the heads-up SHALL offer **"Not
   now"**, which cancels this pause and moves the next proposal 15 minutes
   later. Not 45: the person is still on the phone.
2. WHEN the rule is protected THEN the heads-up SHALL NOT offer "Not now",
   and switching the rule off, raising the limit, shortening the pause or
   changing its profile SHALL require the password (same rules and dialog
   as scheduled pauses). Lowering the limit or lengthening the pause SHALL
   NOT.
3. Ending the pause early SHALL need the password like any other pause.

## User Story 3: Never at the wrong moment

### Acceptance Criteria

1. WHEN a scheduled pause starts within the next 15 minutes THEN no
   long-use pause SHALL be proposed: the scheduled one is coming.
2. It SHALL never start while a pause is active, nor stack one pause onto
   another.
3. WHEN Accessibility is off THEN nothing SHALL be counted. The page
   SHALL say that the feature needs the same permissions as the pause.

## Out of scope

- Per-app limits ("30 min of Instagram"), see design.md "Decisioni aperte".
- Daily totals ("3 hours today").
- Showing the user a usage graph. History stays about pauses, not about
  phone use.
