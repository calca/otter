# Slow Exit — Requirements

> **Status: Implemented** (2026-10-01).

## Context

Today the only way to end a pause early is the password, which by design
belongs to someone else. When that person can't be reached and the need is
real, the user is stuck — or turns Accessibility off, which defeats the app
silently and teaches the wrong habit. A slow exit keeps the friction and
removes the hostage situation.

## User Story 1: Leaving without the password, slowly

As the phone's user, when I can't get the password, I want a way out that
costs time instead of a secret, so that leaving is possible but never
impulsive.

### Acceptance Criteria

1. The slow exit SHALL be **on by default**, with a 10-minute wait. Only
   the password holder SHALL be able to switch it off or change the wait
   (5, 10 or 15 minutes), through a password-gated setting.
2. WHEN the password is set during onboarding THEN the system SHALL say in
   one line that the slow exit exists and can be switched off in Settings,
   so the person holding the password knows the pact has this way out.
3. WHEN the slow exit is on THEN the unlock dialog on the block screen
   SHALL show it as a separate section under the Cancel/Unlock buttons:
   the situation ("Can't reach whoever holds the password?"), what it
   means ("The pause can also end with a wait. It will show in History.")
   and the action with its cost ("Leave after 10 minutes").
4. WHEN the user chooses it THEN the system SHALL start the wait at once,
   with no second dialog: the wait itself is the confirmation, since it
   can be cancelled for its whole length. Until 2026-10-16 the action was a
   bare "I don't have the password" link that opened a confirmation
   dialog; it read as a confession rather than a choice, and the two
   dialogs in a row felt heavy (reported).
5. WHEN the wait starts THEN the block screen shows the otter and a ring
   filling over the wait, with a "Stay in the pause" action that cancels it.
6. The wait SHALL continue if the user leaves the block screen or the
   phone sleeps; every other app stays blocked until it ends.
7. WHEN the wait ends THEN the system SHALL end the pause and record it in
   History as "Ended without password".
8. WHEN the user cancels the wait THEN the pause SHALL continue unchanged,
   and starting a new wait SHALL start from zero.
9. WHEN the pause would end naturally before the wait finishes THEN the
   pause SHALL simply end naturally.
10. The slow exit SHALL be available in shared pauses too.

## Out of scope

- Notifying the accountability partner (would need a network).
- Escalating waits ("longer each time this week"). Possibly later.
