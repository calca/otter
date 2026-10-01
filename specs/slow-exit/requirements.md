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
   SHALL offer a secondary action "I don't have the password".
4. WHEN the user chooses it THEN the system SHALL explain what will
   happen ("The pause will end in 10 minutes. It will show in History as
   ended without password.") and ask for confirmation.
5. WHEN confirmed THEN the system SHALL start the wait: the block screen
   shows the otter and a ring filling over the wait, with a "Stay in the
   pause" action that cancels it.
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
