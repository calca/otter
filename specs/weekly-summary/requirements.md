# Weekly Summary — Requirements

> **Status: Proposed — not implemented.**

## Context

The week card in History is only seen by people who open History. A single,
quiet note at the end of the week can acknowledge the time spent away from
the phone. It is also the feature most likely to drift into manipulative
patterns ("don't lose your streak!"), so the rules below are mostly about
what it must not do.

## User Story 1: One quiet note a week

As the phone's user, I want a short note at the end of the week about my
pauses, so I notice them without having to look.

### Acceptance Criteria

1. WHEN Sunday at 20:00 arrives AND the week had at least one pause THEN
   the system SHALL show one notification with the number of pauses only:
   "This week: 7 pauses of calm." (singular "1 pause of calm"). No hours
   or minutes: the note acknowledges the habit, it doesn't measure it.
2. WHEN the week had no pauses THEN the system SHALL NOT send anything.
3. The notification SHALL NOT mention streaks, goals missed, comparisons
   with other weeks or other people, and SHALL NOT use urgency.
4. Tapping it SHALL open History.
5. WHEN a pause is active at that time THEN the note SHALL wait until the
   pause ends.
6. The note SHALL be on by default; the user SHALL be able to turn it off
   in Settings, without password.
7. WHEN the notification permission is not granted THEN the system SHALL
   NOT ask for it for this feature alone; the toggle explains that
   notifications are off.

## Out of scope

- Daily notes or reminders to start a pause (scheduled pauses cover the
  "remind me" need without a nagging notification).
