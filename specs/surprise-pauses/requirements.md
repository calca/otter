# Surprise Pauses — Requirements

> **Status: Implemented** (2026-10-15).

## Context

A scheduled pause is a habit: same days, same time. Its strength is also
its weakness: the mind learns the time, and the hour before 21:00 becomes
the hour to "get things done" on the phone. A pause that starts at an
unknown moment, inside a window the user chose ("sometime between 14:00
and 18:00"), cannot be planned around. It's also lighter to commit to: one
short pause somewhere in the afternoon rather than a fixed block.

It is a variant of a scheduled pause, not a new system: same days, same
duration, same profile, same password rules, same heads-up. Only the start
time changes from a fixed time to a window.

## User Story 1: A pause at an unknown moment

As the phone's user, I want a pause that starts by itself at a random time
inside a window I choose, so I can't arrange my phone use around it.

### Acceptance Criteria

1. WHEN the user adds or edits a scheduled pause THEN the editor SHALL
   offer a **"Surprise"** switch (off = at a set time, today's behavior;
   on = the user picks only "not before <time>").
2. The window SHALL be computed by the app: the pause starts at a random
   moment in the **2 hours** after "not before" (e.g. 14:00 → between
   14:00 and 16:00), and ends inside the window. Near midnight the window
   shortens, never below 1 hour to start (minimum: the duration plus 1
   hour), moving "not before" earlier if needed, with no error message. It
   SHALL fit inside one day: no window across midnight. (First built with
   both ends chosen by the user; simplified on 2026-10-15 at the user's
   request.)
3. WHEN a surprise pause runs on a day THEN the system SHALL draw one start
   time, uniformly at random, between "from" and "to" minus the duration,
   so the pause ends inside the window.
4. The drawn time SHALL be kept until it is used: opening the app,
   rebooting or changing the time zone SHALL NOT draw a new one (reopening
   the app must not be a way to move the pause).
5. WHEN the drawn time arrives THEN the pause SHALL start exactly as a
   scheduled pause does today (same checks: no second pause, permissions,
   the "couldn't start" notification).
6. The list SHALL show a surprise pause as "Mon–Fri · between 14:00 and
   18:00 · 30 min", never the drawn time.

## User Story 2: A surprise, but not an ambush

As the phone's user, I want a moment's warning, so a surprise pause
doesn't cut me off mid-sentence.

### Acceptance Criteria

1. WHEN a surprise pause is 5 minutes away THEN the system SHALL show the
   same quiet heads-up as a scheduled pause, worded for the surprise
   ("A surprise pause starts in 5 minutes").
2. The app SHALL NOT show the drawn time anywhere before the heads-up.
3. "Skip the next one" SHALL skip today's draw, and work as today
   (password only if the schedule is protected).

## User Story 3: Same pact as a scheduled pause

### Acceptance Criteria

1. "Protected by password" SHALL work as for scheduled pauses.
2. On a protected surprise pause, the following SHALL count as loosening
   and SHALL require the password: switching back to "At a set time",
   making the window narrower, and moving it. Making it wider SHALL NOT.

## Out of scope

- Several surprise pauses in the same window on the same day (use two
  schedules with two windows).
- Windows across midnight.
- "Surprise duration".
- Surprise pauses together (Time together stays live and in person).
