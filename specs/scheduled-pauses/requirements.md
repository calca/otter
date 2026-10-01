# Scheduled Pauses — Requirements

> **Status: Proposed — not implemented.** Written before the code, to be
> reviewed. Turns into an as-built spec when the feature ships.

## Context

Today a pause starts only when someone taps the otter, the widget or the
Time together flow. Good intentions ("no phone after 21:00") depend on
remembering to start the pause at the right moment, which is exactly when
the phone is most distracting. A recurring pause turns the intention into a
habit.

## User Story 1: Planning a recurring pause

As the phone's user, I want to set a pause that starts by itself on chosen
days at a chosen time, so I don't have to remember to start it.

### Acceptance Criteria

1. WHEN the user adds a scheduled pause THEN the system SHALL ask for the
   days of the week (any subset, at least one), a start time and a
   duration chosen from the same durations offered on Home.
2. The system SHALL allow several scheduled pauses (e.g. weekday evenings
   and Sunday morning), each independently enabled or disabled.
3. WHEN a scheduled pause's start time arrives AND no pause is active AND
   the required permissions (Accessibility, Do Not Disturb) are granted
   THEN the system SHALL start a pause of that duration exactly as a tap on
   the otter would.
4. WHEN a scheduled pause's start time arrives AND a pause is already
   active THEN the system SHALL NOT start a second one nor extend the
   current one.
5. WHEN the required permissions are missing at start time THEN the system
   SHALL NOT start the pause and SHALL show a single notification saying
   the scheduled pause could not start and why.
6. The start SHALL happen within a few minutes of the chosen time; exact
   to-the-second timing is not required (see design: inexact alarms).
7. WHEN the device reboots THEN the system SHALL re-arm every enabled
   scheduled pause.

## User Story 2: The schedule is part of the pact

As the accountability partner, I want loosening the schedule to require the
password, so that it can't be removed in a weak moment.

### Acceptance Criteria

1. WHEN the user adds a scheduled pause, or makes an existing one longer
   or more frequent THEN the system SHALL NOT ask for the password —
   these only make the pact stricter.
2. WHEN the user deletes, disables, shortens, moves, or removes days from
   a scheduled pause THEN the system SHALL require the password, through
   the same `PasswordVerifyDialog` used for the allowed-apps list.
3. WHEN the user wants to skip only the next occurrence ("not tonight")
   THEN the system SHALL offer it, password-gated, without disabling the
   schedule.

## User Story 3: No surprises

As the phone's user, I want to know a scheduled pause is about to start,
so it doesn't cut me off mid-sentence.

### Acceptance Criteria

1. WHEN a scheduled pause is 5 minutes away THEN the system SHALL show a
   quiet notification ("Your evening pause starts at 21:00").
2. The notification SHALL NOT offer a way to skip without the password.

## Out of scope

- Different durations per day within one schedule (use two schedules).
- Calendar integration or location triggers.
- Group scheduled pauses (Time together stays live and in person).
