# Together Activity — Requirements

> **Status: Proposed — not implemented.**

## Context

A shared pause takes the phones away and leaves a gap: "so, what now?". A
concrete suggestion fills that moment, which is what the phrases do for a
pause alone, but for a group. It also makes Time together more than two
synchronised timers.

## User Story 1: The host proposes something to do

As the person starting a shared pause, I want the app to suggest an
activity we can do together, and change it if it doesn't fit, so we start
with an idea instead of a silence.

### Acceptance Criteria

1. WHEN the host sets up a shared pause (QR/code or live lobby) THEN the
   system SHALL show a suggested activity that fits the chosen duration.
2. The host SHALL be able to ask for another suggestion ("Another one")
   as many times as they like; suggestions SHALL not repeat until the
   fitting ones run out.
3. WHEN the host changes the duration THEN the suggestion SHALL change if
   it no longer fits.
4. The catalog SHALL always include a "nothing" entry ("Nothing: just be
   together"), so silence is a valid proposal too.

## User Story 2: Everyone sees the same thing

As a person joining a shared pause, I want to see the same suggestion as the
host, so we're all thinking of the same thing.

### Acceptance Criteria

1. WHEN a guest joins by QR, typed code or live lobby THEN the system
   SHALL receive the host's chosen activity with the pause and show it
   during the pause.
2. WHEN a guest is in the live lobby before the start THEN the system SHALL
   show the proposed activity there too, updated if the host changes it.
3. Devices with an older version of the app are not supported: a code
   from a different version SHALL be rejected as invalid, as any malformed
   code is today (the app is not distributed, so no compatibility layer).

## User Story 3: The suggestion during the pause

As anyone in a shared pause, I want the suggestion on the block screen, so I
can look at it again.

### Acceptance Criteria

1. WHEN a shared pause has an activity THEN the block screen SHALL show it
   in place of the phrase, regardless of the phrases setting. It needs no
   introduction: the text is already an invitation ("Let's take a walk").
2. The system SHALL never ask whether the activity was done.
3. WHEN the shared pause ends THEN History SHALL show the activity on that
   pause's row in a short form ("with Marta · a long walk").

## Tone (applies to the catalog)

- Written in the first person plural ("Facciamo due passi", "Let's take
  a walk"): the group proposing something to itself, never the app giving
  instructions ("Fate due passi").
- One activity per entry: never "this or that". An alternative is its own
  entry.
- Invitations, never tasks. Always ignorable.
- Suitable for a couple, a family or colleagues: nothing intimate, nothing
  requiring fitness, special equipment or spending money.
- Doable at home or nearby, at any hour the pause might happen.
