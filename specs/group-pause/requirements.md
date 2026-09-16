# Group Pause — Requirements

## Context

Calm Otter's core loop (`pause-session-core/`) is single-device: one person
starts a session and a trusted partner alone can end it early. This adds a
second, complementary way to start a session — two or more people begin
the *same* pause together, each on their own phone.

Built in two passes:

- **Phase 1**: a fully offline, connectionless handshake — a QR code or
  short manual fallback code that encodes a duration and an agreed start
  time. No live connection at any point, including the "lobby" (a
  consequence of that design, accepted by the project owner when choosing
  this scope).
- **Phase 2**: an additional pairing mode, a live lobby over classic
  Bluetooth (with NFC as a tap-to-connect shortcut for finding the right
  device), which resolves Phase 1's "no live channel" limitation — the
  host now sees who has joined and gates "Start" on it. Phase 1's
  QR/manual-code mode is unchanged and remains available as a fallback,
  one tap away from the live lobby rather than a coequal pairing-mode
  choice (see the later UI/UX refinement pass, below).

Displayed to the user as **"Tempo Insieme"/"Time Together"** (renamed from
"Group pause" in a later UI/UX refinement pass, for a warmer tone
consistent with the rest of the app's copy — see design.md's "Naming"
section). This document keeps using "Group Pause" as the feature's name
throughout, matching the unchanged internal/code name.

## User Story 1: Create a group pause (QR/manual code)

As someone who wants to pause together with someone else, I want to set a
duration and a start time and get something I can share, so the other
person can start the same pause with me.

### Acceptance Criteria

1. WHEN the user opens "Group pause" from Home and chooses "Create" THEN
   the system SHALL show a duration picker (15/30/60/90/120 minutes) and a
   "starts in" picker (1/2/5 minutes from now).
2. WHEN the user taps "Create" THEN the system SHALL generate a QR code
   and an equivalent short text code, both encoding the same duration and
   the same absolute start moment, and show a live "starting in mm:ss"
   countdown beneath them.
3. WHEN the user cancels before the countdown reaches zero THEN the system
   SHALL discard the code and return to Home with no session started and
   no state left behind — nothing is persisted until the countdown reaches
   zero.
4. WHEN the countdown reaches zero while this screen is still open THEN
   the system SHALL start a pause session of the chosen duration on this
   device, the same way any other pause starts (DND, block, foreground
   notification, alarm-based expiry — see `pause-session-core/`).
5. The QR SHALL be rendered in the active palette rather than as a pure
   black-on-white square, and SHALL NOT sit on an opaque white field in
   light themes.
6. The QR SHALL remain dark-modules-on-a-light-field in every theme and
   palette — it SHALL NOT be inverted in dark themes, because the join
   path's decoder cannot read inverted codes (see design.md). Where a
   transparent field would leave the modules on a dark background, a light
   plate SHALL be drawn behind the QR instead.
7. Module/field colour pairs SHALL be verified by actually decoding the
   generated QR, per palette and per theme, not by inspection alone.

## User Story 2: Join a group pause (QR/manual code)

As the person receiving a shared code, I want to scan or type it and have
my phone start the same pause at the same moment, so we're actually paused
together.

### Acceptance Criteria

1. WHEN the user opens "Group pause" from Home and chooses "Join" THEN the
   system SHALL offer both a camera scanner and a manual text-entry field
   for the code, since a QR isn't always practical (e.g. it was sent as
   text).
2. WHEN a scanned or typed code decodes successfully AND its encoded start
   moment is still in the future THEN the system SHALL show the same
   "starting in mm:ss" countdown as the host sees, using the decoded
   duration and start moment.
3. WHEN that countdown reaches zero while this screen is still open THEN
   the system SHALL start a pause session of the decoded duration on this
   device — independently of the host, with no further communication
   needed between the two devices.
4. WHEN a scanned or typed code is malformed, corrupted, already past its
   start moment, or implausibly far in the future THEN the system SHALL
   reject it with one generic "invalid or expired code" message — the
   system does not distinguish these cases for the user, since none of
   them are actionable differently ("try getting the code again" covers
   all of them).
5. WHEN the user cancels before the countdown reaches zero THEN the system
   SHALL discard the decoded recipe and return to Home with no session
   started.

## User Story 3: Create a group pause (live Bluetooth lobby)

As someone who wants to pause together with someone else and wants to know
they're actually joined before starting, I want a live lobby instead of a
one-shot code.

### Acceptance Criteria

1. WHEN the user opens "Group pause" → "Create" THEN the system SHALL ask
   only for a duration (no pairing-mode choice up front, no "starts in"
   delay — starting is a live, host-triggered action here, not a
   scheduled one) and then open the live lobby directly; QR/manual code is
   reachable from inside the lobby via a "Prefer a code or a QR?" link,
   not offered as an equal first choice.
2. WHEN Bluetooth permissions or the Bluetooth radio are missing/off THEN
   the system SHALL still show the lobby's real content (not a dedicated
   permission/setup screen) with a single non-blocking notice inline,
   whose tap action requests whichever is currently missing; device
   discoverability is requested automatically, once, as soon as both are
   satisfied, with no separate prompt of its own.
3. WHEN a nearby device connects to the lobby THEN the system SHALL add
   its announced name to a live, visible list of participants.
4. WHEN the participant list is empty THEN the system SHALL disable the
   "Start" action — at least one other participant is required, unlike
   Phase 1's QR/manual-code mode where this could not be enforced.
5. WHEN the host taps "Start" THEN the system SHALL send the agreed
   duration and start moment to every connected participant and begin the
   same pause session locally, the same way Phase 1's countdown does.

## User Story 4: Join a group pause (live Bluetooth lobby)

As the person joining, I want to find the right host either by browsing
nearby devices or, more conveniently, by tapping my phone against theirs.

### Acceptance Criteria

1. WHEN the user opens "Group pause" → "Join" THEN the system SHALL land
   directly on the live lobby (no pairing-mode choice up front): tapping
   phones (NFC) as the default action if the device supports NFC, or
   manual Bluetooth discovery as the default if it doesn't — either way, a
   secondary link offers the other Bluetooth method and another offers
   QR/manual code, so nothing is unreachable, it's just not presented as
   three equal buttons any more.
2. WHEN the user taps their phone against the host's THEN the system
   SHALL read the host's advertised name via NFC and connect to the
   matching Bluetooth device automatically, without requiring the user to
   pick it from a list.
3. WHEN the user instead searches manually THEN the system SHALL show
   discovered nearby devices by name and connect to whichever one the user
   taps.
4. WHEN connected but the host hasn't started yet THEN the system SHALL
   show a waiting state, with no further action needed from the user.
5. WHEN the host starts the pause THEN the system SHALL receive the
   duration/start-moment and begin the same pause session locally, the
   same way Phase 1's countdown does.
6. WHEN the connection is lost before the host starts THEN the system
   SHALL show an error and let the user retry from the start of this flow.

## User Story 5: Recognizing a group pause afterward

As a user, I want to tell a group pause apart from a solo one when I see it
during or after the session, even though Phase 1 can't tell me who else
was in it.

### Acceptance Criteria

1. WHEN the block screen is shown for a session that was started via
   Group Pause THEN the system SHALL show a generic "you're having time
   together" indicator — not a participant count or names, since neither
   pairing mode reliably knows who else joined by the time the session is
   actually running (see "Known limitation" below).
2. WHEN the History list shows a past session that was started via Group
   Pause THEN the system SHALL show an icon (not a text label) on that
   row distinguishing it from a solo session — the otter mascot plain for
   solo, with two small signal arcs above its head for a group session —
   inside a chip whose color already communicates completed-vs-interrupted
   (see design.md's "History icon system"); no participant names, same
   reasoning as criterion 1.

## User Story 6: The lobby tells the truth about its own state

As someone opening a live lobby, I want the screen to say what is actually
happening, so I don't stare at "waiting for someone" while nothing is in
fact listening.

### Acceptance Criteria

1. WHEN Bluetooth permissions are missing, or Bluetooth is off, THEN the
   lobby screens SHALL NOT claim to be waiting, searching, or ready to be
   tapped — neither in the title/subtitle nor through an illustration that
   mimes activity.
2. WHEN in that not-ready state THEN the screen SHALL say that something is
   still missing, and the existing inline banner SHALL remain the place
   that names which thing and offers the action. The two SHALL NOT repeat
   each other.
3. The not-ready wording SHALL match the role: the host is prevented from
   *inviting*, the joiner from *joining*.
4. WHEN permissions and Bluetooth are both in place THEN the screens SHALL
   return to their normal wording and animated/dashed illustrations.
5. This SHALL NOT reintroduce the dedicated permission/Bluetooth-off step
   screens removed earlier — the lobby stays one screen throughout.

## User Story 7: Choosing between creating and joining

As someone opening Tempo Insieme, I want to understand which of the two
paths I want before committing to one.

### Acceptance Criteria

1. Create and Join SHALL be presented as two equally-weighted choices —
   neither occupying a dialog's affirmative or dismissive action slot,
   since neither is a confirmation or a cancellation.
2. Each choice SHALL carry a short description of what it leads to.
3. The chooser SHALL offer an explicit way out, not only system back or a
   tap outside.
4. Duration options SHALL all remain visible and legible at once; no
   option's label may be truncated or wrapped mid-label to make the row
   fit.

## Known limitations (accepted, not bugs)

- **QR/manual-code mode has no participant count or names.** Because it
  has no live channel, the host cannot know whether anyone scanned the
  code, how many people did, or who they are, at any point — before,
  during, or after the pause. There is deliberately no host-side "who's
  joined" list and no "at least one participant" gate on starting for this
  mode: the host's countdown starts and reaches zero regardless of whether
  anyone else ever sees the code. This was raised explicitly to the
  project owner during design and accepted as the tradeoff for shipping a
  connectionless mode — the Bluetooth lobby mode (User Stories 3–4) does
  not have this limitation, since it has a live channel.
- **Names never reach BlockScreen/History, even for the Bluetooth lobby.**
  The live lobby shows real connected names while it's open, but nothing
  carries them forward: `block_group_indicator` stays generic ("you're
  having time together") for both pairing modes, and History's icon (see
  design.md's "History icon system") only encodes solo-vs-group, not who.
  This was judged out of scope for the current pass, not an oversight.
- **NFC exchanges a name, not a device address.** Reading a phone's own
  Bluetooth MAC address isn't possible on modern Android (the platform
  returns a fixed dummy value for privacy) — so the NFC tap conveys the
  host's advertised Bluetooth *name* instead, and the joiner still runs
  ordinary Bluetooth discovery to find the matching device automatically.
  In practice this is invisible to the user (tap → connect), but it means
  the host still needs to be Bluetooth-discoverable for the NFC path to
  work too, same as the manual-search path.
- **Foreground-only countdown (QR/manual-code mode).** The countdown
  between "create/join" and the session actually starting runs only while
  its screen is open in the app process. If the app is backgrounded and
  the OS kills the process during that window (at most a few minutes, per
  the "starts in" choices offered), the scheduled start will not fire on
  that device. This is the same category of risk as any foreground-only
  timer and is called out here rather than silently accepted. The
  Bluetooth lobby mode uses a short fixed buffer (a few seconds) instead of
  a user-chosen delay, so this risk window is much smaller there.
- **Codes/lobbies are single-use in spirit, not enforced.** Nothing stops
  the same QR/manual code from being scanned/typed twice, or a Bluetooth
  lobby from accepting more participants than intended — there is no
  server to enforce a limit. Anyone with the code, or anyone who can see
  the host's Bluetooth lobby, can join.
