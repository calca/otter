# Home & Settings Split — Requirements

## Context

The home screen used to be a single flat column of buttons: theme picker,
status, duration, start-pause, manage allowed apps, change password,
history, a phrases checkbox. This splits it into a calm Home (only what's
needed to start a pause) and a separate Settings screen (occasional,
password-adjacent configuration) — see `specs/mascot-marks/` for the
similarly-motivated visual-identity work from the same design pass.

## User Story 1: A calm Home

As the phone's user, I want the home screen to show only what I need to
start a pause (and to see my streak), so opening the app isn't a wall of
buttons for things I rarely touch.

### Acceptance Criteria

1. WHEN the home screen is shown THEN the system SHALL display, and only
   display: the app name with a settings icon, the current status, the
   duration picker (hidden once a session is active), the Start Pause
   button, a streak indicator (shown only when the streak is ≥ 1 day), and
   a History button.
2. WHEN accessibility access, Do Not Disturb access, or default-Home status
   is not yet granted/set THEN the system SHALL show the corresponding
   prompt button, exactly as before this change — these are not "settings",
   they gate the Start Pause action itself and must stay visible on Home
   until resolved.
3. WHEN the settings icon is tapped THEN the system SHALL open Settings.

## User Story 2: Settings holds the occasional stuff

As the phone's user or accountability partner, I want theme, password, and
app-whitelist management in one place I visit occasionally, not mixed into
the screen I open every time I want a break.

### Acceptance Criteria

1. WHEN Settings is opened THEN the system SHALL show: the theme picker
   (Sage/Lavender/Terracotta), a "Change password" button, a "Manage
   allowed apps" button, and the reflective-phrases toggle — every one of
   these previously lived on the home screen.
2. WHEN "Manage allowed apps" is tapped THEN the system SHALL require the
   correct password before opening the allowed-apps editor, exactly as
   before this change (see `app-blocking-and-home-lock/requirements.md`) —
   only the screen that asks has moved, not the gate itself.
3. WHEN a new theme is picked in Settings THEN the system SHALL apply it
   immediately (activity recreation), same as when this lived on Home.

## User Story 3: History stays on Home

As the phone's user, I want to check my streak and history without going
through Settings, because checking progress is something I do often and
enjoy, unlike changing the password.

### Acceptance Criteria

1. History is NOT part of Settings — it keeps its own button directly on
   Home, per an explicit choice made when this split was designed (checking
   progress is frequent and rewarding, configuration is occasional).
