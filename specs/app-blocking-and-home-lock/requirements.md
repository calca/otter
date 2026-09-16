# App Blocking & Home Lock — Requirements

## Context

This is the enforcement side of a pause session: what happens when the
user tries to use another app, or presses Home, while a session is active.
The lock is intentionally **soft** — see "Known limits" below, and don't
describe it otherwise in code, comments, or copy.

## User Story 1: Blocking foreground apps during a session

As the accountability partner, I want every app except the phone dialer
(and anything I've explicitly allowed) blocked while a session is active,
so the user can't just use another app instead.

### Acceptance Criteria

1. WHEN a session is active AND the foreground app changes to a package
   that is not the device's default dialer, not `com.android.systemui`, not
   `android`, not Calm Otter itself, and not in the allowed-apps list THEN
   the system SHALL bring `BlockOverlayActivity` to the foreground.
2. WHEN no session is active THEN the system SHALL NOT intervene on any
   foreground-app change.
3. WHEN the block screen is shown THEN the system SHALL display the
   remaining time (via the shared countdown wording, not an exact number),
   an optional reflective phrase, and an Unlock control (a lock icon in
   the actions row — see User Story 4) that opens a password-entry dialog.
4. WHEN the correct password is entered and confirmed in that dialog THEN
   the system SHALL end the session and return control to the user.
5. WHEN the session expires naturally while the block screen is showing
   THEN the system SHALL show a brief confirmation and close the block
   screen.
6. The user cannot dismiss the block screen with the back button while it
   is showing (`BlockOverlayActivity` swallows back-press).

## User Story 2: Allowed-apps whitelist

As the accountability partner, I want to choose additional apps (e.g. maps,
a family messaging app) that stay usable during a pause, so the block isn't
all-or-nothing.

### Acceptance Criteria

1. WHEN opening the allowed-apps editor from Settings (see
   `home-and-settings/requirements.md` — this used to be reachable directly
   from the home screen) THEN the system SHALL first require the correct
   password.
2. WHEN the editor is open THEN the system SHALL list every launchable app
   on the device except Calm Otter itself, with already-allowed apps sorted
   to the top.
3. WHEN a row's toggle is changed THEN the system SHALL persist the new
   allowed-set immediately — there is no separate save step.
4. WHEN `AppBlockerAccessibilityService` decides whether to block an app
   THEN the system SHALL treat the current allowed-set as part of the
   always-exempt list, alongside the dialer/systemUI/self exemptions.
5. WHEN the user pulls down to refresh THEN the system SHALL reload the
   installed-app list from `PackageManager` (an app installed/uninstalled
   while the editor was already open otherwise wouldn't appear/disappear
   until the screen is reopened) and confirm completion with a brief
   message.
6. WHEN a search yields no matching app THEN the system SHALL show an empty
   state instead of a blank list; the search field offers a clear (×)
   button once non-empty.
7. WHEN the allowed-set already has 3 apps THEN toggling on a 4th SHALL be
   rejected (brief message shown, toggle stays off) — see User Story 4 for
   why (the block screen's icon row needs to stay short; lowered from 5 to
   3 after phone + 5 apps + unlock, 7 badges, didn't fit one row and the
   unlock badge itself became unreachable — see that story for the fix).

## User Story 3: Home-button interception

As the accountability partner, I want the Home button itself to lead back
to the block screen during a session, so the normal home screen (with its
app icons) isn't an easier escape route than switching apps.

### Acceptance Criteria

1. WHEN Calm Otter is set as the device's Home app AND the Home button is
   pressed WHILE a session is active THEN the system SHALL show the same
   block screen used for other blocked apps.
2. WHEN Calm Otter is set as the device's Home app AND the Home button is
   pressed WHILE no session is active THEN the system SHALL immediately
   forward to the user's original launcher and close itself, so normal
   daily use of the phone is unaffected.
3. WHEN Calm Otter is first set as Home THEN the system SHALL have already
   detected and remembered the previously-default launcher, so the forward
   in the previous criterion has somewhere to go.
4. The Home-lock back button is swallowed the same way the app-block screen's is (criterion 1's screen is the same Composable).

## User Story 4: Launching an allowed app from the Home-lock block screen

As the phone's user, I want to be able to actually open an app I'm allowed
to use during a session, even when Calm Otter is set as Home and pressing
Home shows the block screen instead of any launcher — otherwise being
"allowed" only helps for an app already open, not one I want to start.

### Acceptance Criteria

1. WHEN the block screen is shown THEN the system SHALL display a single
   actions row containing, WHEN Calm Otter is set as Home, a session is
   active, and at least one package is in the allowed-apps list
   (Home-button case only — see design.md for why not the regular
   app-block case too), that list as 2-letter initial badges, followed
   last by an Unlock control (a lock-icon badge, same round shape/fill as
   the app badges) — deliberately understated (no full-color icons, no
   vertical list, no visually distinct "primary action" styling setting
   Unlock apart from the app badges beyond its content), not a mini
   launcher to browse.
2. WHEN the Unlock badge is tapped THEN the system SHALL open a dialog with
   a password field and Unlock/Cancel actions, rather than keeping a
   password field permanently visible on the block screen.
3. WHEN one of the allowed-app badges is tapped THEN the system SHALL
   launch that app directly, without opening the Unlock dialog.
4. WHEN the allowed-apps list is empty THEN only the Unlock badge SHALL
   show in the row (caption reads plain "Unlock") — no empty-state
   placeholder for the apps portion.
5. WHEN an allowed package can no longer be resolved or launched (e.g.
   uninstalled since being allowed) THEN the system SHALL drop it from the
   list (or silently no-op the tap) rather than showing an error.
6. The phone (device's default dialer) SHALL always appear as the first
   app badge (before the Unlock badge and before the configurable
   allowed-apps list), regardless of the configurable allowed-apps list's
   contents — it was already always exempt from blocking, this just makes
   it launchable from this screen too, same as the configured apps.
7. The user-configurable allowed-apps list SHALL be capped at 3 apps
   (the phone from criterion 6 doesn't count against this cap): attempting
   to allow a 4th app in the editor SHALL be rejected with a brief message,
   not silently accepted and trimmed elsewhere — this keeps the icon row
   short enough to read at a glance. Lowered from an original cap of 5,
   reported directly as a bug: phone + 5 apps + Unlock (7 badges) didn't
   fit one row on a normal phone screen, and — since the row has no
   horizontal scroll by design (see Design doc) — the Unlock badge itself,
   being last, ended up pushed off-screen and untappable, leaving no way
   to end the session from this screen at all.

## User Story 5: Continuity between the Home screen and the block screen

As the phone's user, when a pause starts I want the block screen to feel
like the Home screen settling into it, not like a different screen
appearing out of nowhere — the tap animation on the otter already builds
up to the moment, and a hard cut right at its peak throws that away.

### Acceptance Criteria

1. WHEN a session is started from the Home screen THEN the system SHALL
   animate the change to the block screen, NOT replace one screen with
   the other in a single frame.
2. WHEN that transition runs THEN the otter SHALL remain continuously on
   screen throughout AND SHALL NOT move: it stays put while the rest of
   the screen changes around it. It SHALL NOT disappear, flicker, slide to
   a new position, or be re-drawn from scratch on the other side.
   (Originally this criterion allowed the otter to travel to a new
   position; that was rejected on sight — see design.md.)
3. WHEN that transition runs THEN the content that genuinely differs
   between the two screens (ambient ripples and duration chips on one
   side; progress ring, countdown phrase and reflective quote on the
   other) SHALL fade rather than snap.
4. WHEN the block screen is reached WITHOUT coming from the Home screen —
   `BlockOverlayActivity` via the AccessibilityService, or the launcher
   icon opened during an already-active session — THEN it SHALL render
   exactly as before, with no transition and no dependency on one having
   occurred.
5. The transition SHALL be symmetric: leaving the block screen back to
   the Home screen animates the same way, not just entering it.
6. The otter's resting position SHALL NOT depend on how much session
   history exists, nor on the length of the text below it — it is the one
   element both screens place identically, so anything that moves it in
   one screen and not the other reintroduces the slide.

## Known limits (do not misrepresent in code/UI)

- The user can disable the Accessibility service from system Settings at
  any time without a password — there is no OS-level way to prevent this
  short of Device Owner provisioning (out of scope for this app).
- Booting into Safe Mode disables third-party accessibility services
  entirely.
- The Home app can be changed back from Settings > Apps > Default apps >
  Home at any time.
- On Android 11+ (API 30+), enumerating "every launchable app" is subject to
  package-visibility restrictions unless the app declares a `<queries>`
  element or holds the (Play-Store-gated) `QUERY_ALL_PACKAGES` permission.
  **Fixed**: `AndroidManifest.xml` now declares a `<queries>` element for
  `ACTION_MAIN`/`CATEGORY_LAUNCHER`, so `AllowedAppsActivity.loadApps()`
  sees every installed app on Android 11+. This was previously a gap in
  this same list — verify the manifest still has it before assuming this
  point is moot.
