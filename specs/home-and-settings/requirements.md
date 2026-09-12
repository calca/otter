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
card. See "Living Pond redesign" below for that second pass; a fourth pass
("Permissions off Home") then moved the accessibility/DND/Home-app status
entirely off Home too — see that section for why and User Story 1's
current acceptance criteria for the result. User Story 1 and User Story 3
describe the *current* behavior throughout, not the original flat-column
one.

## User Story 1: A calm Home

As the phone's user, I want the home screen to show only what I need to
start a pause (and to see my streak), so opening the app isn't a wall of
buttons for things I rarely touch.

### Acceptance Criteria

1. WHEN the home screen is shown AND no session is active THEN the system
   SHALL display: the app name with a settings icon, ambient decorative
   ripples around the otter mark, a horizontally-scrollable row of duration
   chips (30 min through 4h in 30-minute steps), and the sessions-chart card
   (see User Story 3) — nothing about accessibility/DND/Home-app status is
   shown here, see "Permissions off Home" below.
2. WHEN the otter mark is tapped AND no session is active THEN the system
   SHALL, if accessibility access AND Do Not Disturb access are both
   already granted, start a pause session for the currently-selected chip's
   duration; otherwise it SHALL show [PermissionExplainerDialog] instead —
   the otter is always tappable while idle, never disabled, and is the only
   Start-Pause control (no separate "Start" button).
3. WHEN a session is active THEN the system SHALL replace the ambient
   ripples with a functional progress ring around the otter (fraction of
   time elapsed) and replace the duration chips with a status readout
   ("Paused" + minutes remaining); the duration chips are not shown while a
   session is active.
4. WHEN the settings icon is tapped THEN the system SHALL open Settings.

## User Story 2: Settings holds the occasional stuff

As the phone's user or accountability partner, I want theme, password, and
app-whitelist management in one place I visit occasionally, not mixed into
the screen I open every time I want a break.

### Acceptance Criteria

1. WHEN Settings is opened THEN the system SHALL show, each as its own
   labeled section with a card in the same visual style (see "Full
   list-card redesign" below): a permissions status card (see "Permissions
   off Home"), a Home section (a single toggle for Home-app status), a
   Theme section (a list of Sage/Lavender/Terracotta, each row showing its
   own swatch color, with the active one marked "Selected"), a Password
   section (who set it, if known; "Change password"; "Manage allowed
   apps"), a reflective-phrases toggle section, and an "Info" section (see
   "Info section" below) — every one of these except "Info" previously
   lived on the home screen; "Info" is new to Settings, not moved from
   anywhere.
2. WHEN "Manage allowed apps" is tapped THEN the system SHALL require the
   correct password before opening the allowed-apps editor, exactly as
   before this change (see `app-blocking-and-home-lock/requirements.md`) —
   only the screen that asks has moved, not the gate itself.
3. WHEN a new theme is picked in Settings THEN the system SHALL apply it
   immediately (activity recreation), same as when this lived on Home.
4. WHEN Settings is reopened after the user has been to a system settings
   screen (accessibility, Do Not Disturb, or the Home-app chooser) and back
   THEN the permissions status card SHALL reflect the new state — Settings
   now refreshes this OS-level state on every resume, the same way Home
   always has (see "Permissions off Home").

## Info section

As anyone using the app, I want to find the project's source, its license,
and who made it without having to search for it outside the app.

### Acceptance Criteria

1. WHEN Settings is opened THEN the system SHALL show an "Info" section
   with three rows: a link to the project's GitHub repository, the
   project's license (MIT), and the developer's GitHub handle.
2. WHEN any of the three rows is tapped THEN the system SHALL open that
   row's URL in the device's browser (or app of the user's choice for that
   link) — this is the only place in the app that opens anything external;
   every other screen stays fully local, per CLAUDE.md's "no network
   calls" constraint (opening a browser is the user leaving the app, not
   the app itself making a network call).
3. The license shown SHALL match a real `LICENSE` file committed at the
   repository root — the in-app text is not a standalone claim independent
   of the actual repository.

## User Story 3: History stays on Home

As the phone's user, I want to check my streak and history without going
through Settings, because checking progress is something I do often and
enjoy, unlike changing the password.

### Acceptance Criteria

1. History is NOT part of Settings — it stays reachable directly from Home,
   per an explicit choice made when this split was designed (checking
   progress is frequent and rewarding, configuration is occasional).
2. WHEN the home screen is shown AND at least one session exists in history
   THEN the system SHALL display a card showing, for each of the last 7
   days (oldest to newest, today last), a bar proportional to that day's
   total session minutes with its weekday initial underneath (today's bar
   and initial visually emphasized); the card's header shows the current
   streak (`streak_days`) when it is ≥ 1 day, or a neutral "recent
   sessions" label otherwise.
3. WHEN no session has ever been recorded THEN the system SHALL replace the
   bars with a single "no pauses yet" line instead of seven flattened,
   identical bars — at the minimum-height floor every bar renders the same
   regardless of the (all-zero) data, which reads as a rendering bug more
   than as "zero sessions", especially for a brand-new user.
4. WHEN the card is tapped (anywhere on it) THEN the system SHALL open
   History — the card itself is the CTA, there is no separate button.
5. WHILE a session is active THE card SHALL remain visible but visually
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

## Anchored layout ("the home is a bit empty")

A third pass addressed Home reading as top-heavy: title, permission
prompts, chips, and card all sat one under the next starting from the top,
leaving the bottom third of the screen empty on most phones (worse on
taller ones). Fixed without adding new content by redistributing what
already existed:

1. WHEN the home screen is shown THEN the system SHALL keep the title row
   at the top and the sessions-chart card at the bottom, and SHALL let the
   space between them (permission prompts if any, then the pond scene)
   expand to fill whatever room is left, vertically centering the pond
   scene within that space — so the otter sits near the true center of the
   screen rather than immediately below the header, and the card stays
   anchored to the bottom edge rather than trailing directly under the
   pond.
2. WHEN no session is active THEN the otter mark and the ring/ripple ring
   around it SHALL render at a larger fixed size than before (124dp otter,
   up from 96dp) — the pond scene is now the dominant visual element of an
   otherwise mostly-empty middle region, not one element among several
   competing for a cramped top section.
3. WHEN no session is active THEN the system SHALL show a one-line rolling
   weekly summary (reusing the exact `weekly_summary_none`/`_one`/`_many`
   strings and the "last 7 days" window already used by
   `session-history-and-stats` — not a calendar week) directly below the
   pond scene, giving the enlarged empty space a second piece of real
   content instead of bare padding.

## Permissions off Home

A fourth pass removed the permission-gating banner and "Set as Home"
button from Home entirely, on explicit feedback that checking these on
every single app open was annoying, plus a specific design goal for two of
the three: check them only at the moment the user actually needs them
(tapping the otter), not proactively.

1. Home SHALL NOT show any accessibility/DND/Home-app status, banner, or
   button anywhere, ever — not even while all three are missing. The otter
   remaining tappable at all times (see User Story 1) is what replaces the
   old banner's job of surfacing the gap.
2. "Set as Home" is NOT part of the tap-to-explain flow described below —
   it does not block starting a session at all (only the separate
   Home-button-interception feature needs it, see
   `app-blocking-and-home-lock/requirements.md`), so it moved to Settings
   as a plain status row with no explainer dialog of its own.
3. WHEN the otter is tapped WHILE accessibility access or Do Not Disturb
   access (or both) are still missing THEN the system SHALL show
   `PermissionExplainerDialog`: a title ("Permissions needed") and, for
   each *currently missing* permission only, one row with a short
   explanation of why Calm Otter needs it (the same reasoning as
   onboarding step 3, reworded as standalone strings) and a "Grant" action
   that opens the matching system settings screen and dismisses the
   dialog. A permission already granted does not get a row — if the user
   granted one on a previous visit to this dialog, only the remaining one(s)
   show up next time.
4. Settings SHALL show the two system permissions (accessibility, Do Not
   Disturb) as a single always-visible status card, each row reading either
   a checkmark + "Done" or an empty ring + a "Grant" action that opens the
   matching system settings screen — a place to check or fix these
   deliberately, not a reminder that follows the user onto the screen they
   open most often. "Home app" status is its own separate section (see
   "Full list-card redesign" below), shown as a toggle rather than grouped
   into this card, since setting it is an app-level preference, not a
   system permission grant.

## Full list-card redesign

A later pass made every Settings section use the same labeled-card visual
language, and changed two controls from their original form to match the
rest of the screen.

1. The Theme section SHALL be a list (one row per palette, each showing
   that palette's own true color as a swatch and the active one marked
   "Selected"), not the earlier row of selectable colored dots.
2. The "Show phrases during pause" preference SHALL be a Switch, not a
   checkbox.
3. The Home-app status SHALL be a Switch in its own "Home" section (see
   User Story 2, Acceptance Criteria 4), not a text row inside the
   permissions card.
4. The Password section SHALL show, above "Change password" and "Manage
   allowed apps", the name of the accountability partner who set the
   current password — if one was captured (see
   `onboarding-and-password/requirements.md`) — and SHALL show nothing in
   that spot if no name was ever captured, rather than an empty
   placeholder.
