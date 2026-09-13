# Group Pause — Requirements

## Context

Calm Otter's core loop (`pause-session-core/`) is single-device: one person
starts a session and a trusted partner alone can end it early. This adds a
second, complementary way to start a session — two or more people begin
the *same* pause together, each on their own phone.

This is **Phase 1** of a two-phase design, deliberately scoped down from
the fuller vision discussed with the project owner. Phase 1 ships a fully
offline, connectionless handshake: a QR code or short manual fallback code
that encodes a duration and an agreed start time. There is no live
connection at any point, including the "lobby" — a consequence explained
below and accepted explicitly by the project owner when choosing this
phasing over waiting for the live-lobby version. Phase 2 (a live lobby over
Bluetooth, with NFC as an alternative way to start that connection) is
deferred — see design.md's "Deferred: Phase 2" section — and not
implemented by anything described here.

## User Story 1: Create a group pause

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

## User Story 2: Join a group pause

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

## User Story 3: Recognizing a group pause afterward

As a user, I want to tell a group pause apart from a solo one when I see it
during or after the session, even though Phase 1 can't tell me who else
was in it.

### Acceptance Criteria

1. WHEN the block screen is shown for a session that was started via
   Group Pause THEN the system SHALL show a generic "Part of a group
   pause" indicator — not a participant count or names, since Phase 1 has
   no way to know who else joined (see "Known limitation" below).
2. WHEN the History list shows a past session that was started via Group
   Pause THEN the system SHALL show a "Group pause" tag on that row,
   alongside the existing outcome/duration text — again generic, no
   participant names.

## Known limitations (accepted, not bugs)

- **No participant count or names.** Because Phase 1 has no live channel,
  the host cannot know whether anyone scanned the code, how many people
  did, or who they are, at any point — before, during, or after the pause.
  There is deliberately no host-side "who's joined" list and no "at least
  one participant" gate on starting: the host's countdown starts and
  reaches zero regardless of whether anyone else ever sees the code. This
  was raised explicitly to the project owner during design and accepted as
  the tradeoff for shipping a connectionless Phase 1 now rather than
  waiting for the live-lobby version (Phase 2).
- **Foreground-only countdown.** The countdown between "create/join" and
  the session actually starting runs only while its screen is open in the
  app process. If the app is backgrounded and the OS kills the process
  during that window (at most a few minutes, per the "starts in" choices
  offered), the scheduled start will not fire on that device. This is the
  same category of risk as any foreground-only timer and is called out
  here rather than silently accepted.
- **Codes are single-use in spirit, not enforced.** Nothing stops the same
  code from being scanned/typed twice (by the same or different people) —
  there is no server to enforce one-time use. Anyone with the code before
  its start moment can join.
