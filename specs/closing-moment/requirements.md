# Closing Moment — Requirements

> **Status: Implemented** (2026-10-01).

## Context

When a pause ends today the user gets "Pause ended at 09:31" on Home and
nothing else. The time just spent away from the phone passes without a
trace of what it was *for*. A short, optional closing moment gives it
meaning without turning it into a score.

## User Story 1: A gentle question at the end

As the phone's user, when a pause completes, I want a quiet moment that asks
how it went, so the time feels like something I did, not something I lost.

### Acceptance Criteria

1. WHEN a pause completes naturally AND the app is on screen THEN the
   system SHALL show the closing moment before Home: the otter, the
   duration ("An hour of calm"), and the question "How was it?".
2. WHEN a pause completed while the app was not on screen THEN the Home
   summary ("Pause ended at 09:31") SHALL become tappable and open the same
   closing moment for that pause.
3. The closing moment SHALL offer three answers in words — calm,
   ordinary, hard — plus an optional note ("What did you do?", up to 30
   characters, so it always fits one line in History).
4. The closing moment SHALL appear after shared pauses too, the same way.
5. The system SHALL let the user skip the closing moment with one tap,
   and SHALL NOT ask again for that pause.
6. WHEN a pause is ended early (password, NFC release, slow exit) THEN the
   system SHALL NOT show the closing moment: it is a reward for a pause
   kept, never a reproach for one cut short.
7. The closing moment SHALL never show streaks, goals or comparisons.

## User Story 2: Seeing it later

As the phone's user, I want my answers in History, so I can notice what
makes a pause good.

### Acceptance Criteria

1. WHEN a session has an answer or a note THEN its History row SHALL show
   them under the outcome line.
2. WHEN History is exported to CSV THEN the answer and note SHALL be
   included as two columns.
3. WHEN a session has neither THEN its History row SHALL look exactly as
   today.

## Out of scope

- Statistics on moods ("you're calmer on Sundays"). Possibly later; not
  needed for the moment itself.
- Free-form journaling beyond one line.
