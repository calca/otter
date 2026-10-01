# History by Month — Requirements

> **Status: Implemented** (2026-10-01).

## Context

History lists every session in one column, all composed when the screen
opens. With one pause a day that is ~365 rows a year: long to scroll and
slower every month. Nothing is lost by showing less at first: the numbers
at the top already summarise everything.

## User Story 1: Recent months first

As the phone's user, I want History to open on the recent sessions, grouped
by month, so it stays quick to read however long I've used the app.

### Acceptance Criteria

1. The session list SHALL be grouped by month, each group under a header
   ("October 2026").
2. WHEN History opens THEN the system SHALL show the current month and the
   previous one.
3. WHEN older sessions exist THEN a "Show earlier months" action SHALL load
   one more month each time it is used, never the whole history at once.
4. WHEN the current month has no sessions yet THEN its group SHALL say so
   in one line instead of disappearing.
5. The stats bar, the week card, the "Together" card and the CSV export
   SHALL keep using the whole history: only what is listed is limited,
   not what is counted.
6. WHEN the "Together" filter is on THEN the same rule SHALL apply to shared
   pauses only.
