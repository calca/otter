# Scheduled Pauses — Requirements

> **Status: Implemented** (2026-10-01). The ongoing notification may only appear once the app is opened: see design.md.

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

## User Story 2: My schedules are mine; a protected one is a pact

As the user, I want to manage the scheduled pauses I set for myself without
the password. As the accountability partner, I want to be able to protect a
schedule so that loosening it requires the password, and it can't be
removed in a weak moment.

### Acceptance Criteria

1. WHEN the user adds, edits, disables or deletes a scheduled pause that is
   not protected THEN the system SHALL NOT ask for the password.
2. WHEN a scheduled pause is marked "Protected by password" (on a new or an
   existing one) THEN the system SHALL require the password — protecting
   is the partner's gesture. New schedules are unprotected by default.
3. WHEN the user deletes, disables, shortens, moves, removes days from,
   changes the profile of, or removes the protection from a protected
   scheduled pause THEN the system SHALL require the password, through the
   same `PasswordVerifyDialog` used for the allowed-apps list. Making a
   protected one longer or more frequent SHALL NOT.
4. WHEN the user wants to skip only the next occurrence ("not tonight")
   THEN the system SHALL offer it without disabling the schedule,
   password-gated only if the schedule is protected.
5. Protected schedules SHALL show a lock in the list.
6. Once a scheduled pause is running, ending it early SHALL need the
   password like any other pause, protected or not.

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
