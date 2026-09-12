# Home & Settings Split — Requirements

## Context

The home screen used to be a single flat column of buttons: theme picker,
status, duration, start-pause, manage allowed apps, change password,
history, a phrases checkbox. This splits it into a calm Home (only what's
needed to start a pause) and a separate Settings screen (occasional,
password-adjacent configuration) — see `specs/mascot-marks/` for the
similarly-motivated visual-identity work from the same design pass.

Home was later redesigned again as "Living Pond": the otter mark itself
became the start-pause control (an ambient scene, not a form), and the
separate streak text + History button were merged into one sessions-chart
card. See "Living Pond redesign" below for that second pass; User Story 1
and User Story 3 describe the *current* behavior, not the original
flat-column one.

## User Story 1: A calm Home

As the phone's user, I want the home screen to show only what I need to
start a pause (and to see my streak), so opening the app isn't a wall of
buttons for things I rarely touch.

### Acceptance Criteria

1. WHEN the home screen is shown AND no session is active THEN the system
   SHALL display: the app name with a settings icon, ambient decorative
   ripples around the otter mark, a horizontally-scrollable row of duration
   chips (30 min through 4h in 30-minute steps), and the sessions-chart card
   (see User Story 3).
2. WHEN the otter mark is tapped AND accessibility access AND Do Not Disturb
   access are both granted AND no session is active THEN the system SHALL
   start a pause session for the currently-selected chip's duration — the
   otter is the Start-Pause control, there is no separate "Start" button.
3. WHEN accessibility access, Do Not Disturb access, or default-Home status
   is not yet granted/set THEN the system SHALL show the corresponding
   prompt button above the pond scene, exactly as before this change (only
   relocated, not removed) — these are not "settings", they gate the
   start-pause action itself and must stay visible on Home until resolved;
   while any permission is missing the otter mark is not tappable.
4. WHEN a session is active THEN the system SHALL replace the ambient
   ripples with a functional progress ring around the otter (fraction of
   time elapsed) and replace the duration chips with a status readout
   ("Paused" + minutes remaining); the duration chips and permission/Home
   prompts are not shown while a session is active.
5. WHEN the settings icon is tapped THEN the system SHALL open Settings.

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

1. History is NOT part of Settings — it stays reachable directly from Home,
   per an explicit choice made when this split was designed (checking
   progress is frequent and rewarding, configuration is occasional).
2. WHEN the home screen is shown THEN the system SHALL display a card
   showing, for each of the last 7 calendar days (oldest to newest, today
   last), a bar proportional to that day's total session minutes, today's
   bar visually emphasized; the card's header shows the current streak
   (`streak_days`) when it is ≥ 1 day, or a neutral "recent sessions" label
   otherwise.
3. WHEN the card is tapped (anywhere on it) THEN the system SHALL open
   History — the card itself is the CTA, there is no separate button.
4. WHILE a session is active THE card SHALL remain visible but visually
   de-emphasized (reduced opacity), since it isn't the focus of that state.

## Living Pond redesign

The second Home redesign pass (see `specs/mascot-marks/` for the otter
marks it reuses) deliberately keeps color use restrained, per explicit
direction that saturated color on an idle screen is distracting, while
still following the currently-selected palette (Sage/Lavender/Terracotta)
rather than rendering as flat gray regardless of theme: the otter mark,
ambient ripples, duration chip backgrounds, and chart bars are all
`primary` at low alpha (a soft tint of whichever palette is active, not a
saturated block of color); text stays `onSurface` for legibility. `primary`
at full intensity appears in exactly two places: the active-session
progress ring (because there it carries real information — time elapsed —
rather than being decorative) and the otter's nose, a small fixed accent
mirroring the pebble in `OtterAtRestIllustration` (see
`specs/mascot-marks/`).
