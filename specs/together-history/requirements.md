# Time Together in History — Requirements

> **Status: Proposed — not implemented.**

## Context

History already stores, for every shared pause, who it was with
(`SessionRecord.companions`), and shows "with Marta" on each row. Nothing
adds it up. "6 hours with Marta this month" is the most personal number the
app could show, and it is already on the device.

## User Story 1: Who I paused with

As the phone's user, I want to see the time I spent in pauses with each
person, so the shared pauses feel like time together, not just time off.

### Acceptance Criteria

1. WHEN History contains at least one shared pause THEN it SHALL show a
   "Together" card listing each companion with total time and number of
   pauses ("Marta · 6 h in 4 pauses"), most time first.
2. The card SHALL cover the current month, with a switch to "all time".
3. Names SHALL be grouped ignoring case and surrounding spaces; different
   spellings of the same person stay separate (no guessing).
4. WHEN History contains no shared pause THEN the card SHALL NOT appear.
5. Next to each companion the card SHALL show the icons of the activity
   categories most often proposed in pauses with them (outside, at the
   table, games, slow — see `specs/together-activity/`): up to three,
   each category once, most frequent first. Pauses without an activity
   don't count; a companion with none shows no icons.
6. Early-ended pauses SHALL count with their effective time.

## User Story 2: Only the shared pauses

As the phone's user, I want to filter History to shared pauses, so I can
look back at them on their own.

### Acceptance Criteria

1. History SHALL offer a filter "Together" next to "All".
2. WHEN the filter is on THEN the stats bar, the week card and the list
   SHALL all refer only to shared pauses.

## Out of scope

- Renaming or merging companions.
- Any data leaving the device.
