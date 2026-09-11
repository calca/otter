# Pause Session Core — Requirements

## Context

The pause session is the app's central concept: a timed window during which
the phone is deliberately hard(er) to use. This spec covers starting,
tracking, and ending a session — not what happens to blocked apps (see
`app-blocking-and-home-lock`).

## User Story 1: Starting a session

As the phone's user, I want to start a pause of a chosen length, so that my
accountability partner's block takes effect for that long.

### Acceptance Criteria

1. WHEN the user picks a duration (30-minute increments, 30 min to 4 h) and
   starts a session THEN the system SHALL record the start time, end time,
   and planned duration.
2. WHEN a session starts THEN the system SHALL enable Do Not Disturb,
   allowing only phone calls through (`PRIORITY_CATEGORY_CALLS`,
   `PRIORITY_SENDERS_ANY`), and — on Android 9 (API 28) and above — SHALL
   also suppress notification badges, the notification list, and the status
   bar.
3. WHEN a session starts THEN the system SHALL schedule an inexact
   `AlarmManager` alarm (`setAndAllowWhileIdle`) for the end time, so the
   session terminates even if the app process is not running.
4. WHEN a session starts THEN the system SHALL start a foreground service
   showing a persistent, low-importance notification with the remaining
   time and a rotating encouraging subtext, updated at least once per
   minute.
5. WHEN a session starts THEN the system SHALL remember the chosen duration
   as the widget's default for next time.
6. IF Do Not Disturb access has not been granted, WHEN the app applies the
   notification policy THEN the system SHALL silently skip it rather than
   crash (no permission ≠ error).

## User Story 2: Ending a session

As the phone's user or accountability partner, I want the session to end
either automatically at the planned time or early with the correct
password, so that normal phone use resumes.

### Acceptance Criteria

1. WHEN the scheduled alarm fires THEN the system SHALL end the session
   with `completedNaturally = true`.
2. WHEN the correct password is entered on the block screen THEN the system
   SHALL end the session immediately with `completedNaturally = false`.
3. WHEN a session ends (either path) THEN the system SHALL disable Do Not
   Disturb (`INTERRUPTION_FILTER_ALL`), cancel the pending expiry alarm, stop
   the foreground service, and refresh the home-screen widget.
4. WHEN a session ends AND it had a recorded start time and a positive
   planned duration THEN the system SHALL append one record to session
   history with the actual elapsed minutes (see
   `session-history-and-stats`).
5. WHEN a session's stored end time has already passed THEN the system
   SHALL treat it as inactive and end it automatically the next time
   anything checks `isSessionActive()` (self-healing if the alarm was
   somehow missed).

## User Story 3: Surviving a reboot

As the phone's user, I want an in-progress session to still be enforced
after the phone restarts, so a reboot isn't an escape hatch.

### Acceptance Criteria

1. WHEN the device finishes booting (`BOOT_COMPLETED`, or
   `QUICKBOOT_POWERON` on affected Huawei/MIUI devices) AND a session was
   active before the reboot THEN the system SHALL reapply Do Not Disturb,
   reschedule the expiry alarm, restart the foreground service, and
   immediately bring the block screen to the foreground.
2. IF the session's end time had already passed during the reboot THEN the
   system SHALL do nothing (the normal "session already expired" self-heal
   applies, and the user sees a normal, unlocked phone).

## Out of scope

- What "blocked" means for other apps / the Home button — see
  `app-blocking-and-home-lock/requirements.md`.
- The visual countdown wording — see `design.md` for `CalmCountdown`, which
  deliberately never shows an exact ticking number.
