# Session History & Stats — Requirements

## Context

Every completed (or early-unlocked) session is recorded locally so the user
can see their own pause history, a streak, progress toward an optional
weekly goal, and export the raw data.

## User Story 1: Recording sessions

As the phone's user, I want every ended session recorded, so history and
stats reflect reality.

### Acceptance Criteria

1. WHEN a session ends (naturally or via unlock) AND it has a valid start
   time and a positive planned duration THEN the system SHALL append one
   record with start time, planned minutes, actual elapsed minutes, and
   whether it ended naturally.
2. Records are ordered most-recent-first when read back.

## User Story 2: Viewing history and stats

As the phone's user, I want to see totals, a streak, a weekly chart, and
the raw list of past sessions, so I can gauge my own progress.

### Acceptance Criteria

1. WHEN the history screen opens THEN the system SHALL show: total sessions
   and total minutes across all history, a completed-vs-unlocked-early
   fraction, the current consecutive-day streak, a weekly chart, a rolling
   weekly summary (sessions + minutes in the last 7 days), and the full
   session list (or an empty-state message if there is none).
2. WHEN computing the streak THEN the system SHALL count consecutive
   calendar days (local time) with at least one session, counting backward
   from today; if today has no session yet, the streak SHALL still count as
   ongoing as long as yesterday had one (today not "breaking" a streak just
   because the day isn't over).
3. WHEN listing individual sessions THEN the system SHALL show, per row,
   whether the session ended naturally or was unlocked early (and if early,
   the originally planned minutes).

## User Story 3: Optional weekly goal

As the phone's user, I want to optionally set a weekly target (a number of
sessions or a number of minutes), so I have something concrete to aim for.

### Acceptance Criteria

1. By default, no weekly goal is set (the feature is opt-in).
2. WHEN the user sets a goal THEN the system SHALL accept either a session
   count or a minute count as the target, and SHALL reject a target of zero
   or less.
3. WHEN a goal is set THEN the history screen SHALL show progress toward it
   using the current week's sessions/minutes.

## User Story 4: Exporting and clearing history

As the phone's user, I want to export my history as a file I can share, or
wipe it entirely, so the data isn't locked into the app.

### Acceptance Criteria

1. WHEN the user chooses to export history AND history is non-empty THEN
   the system SHALL generate a CSV (date, time, planned minutes, actual
   minutes, outcome) and open the system share sheet for it.
2. WHEN the user chooses to export history AND history is empty THEN the
   system SHALL show a message instead of sharing an empty file.
3. WHEN the user chooses to clear history THEN the system SHALL ask for
   confirmation before deleting every record.
