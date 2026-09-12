# Onboarding & Password — Requirements

## Context

Calm Otter's lock model assumes the password is set by someone other than
the phone's user (the "accountability partner"). First run walks whoever
sets the phone up through why, then through choosing that password.

The wizard originally had 5 steps and included a dedicated permissions
step. It was cut to 3 (see "3-step wizard" below) once permission requests
moved off Home entirely and became purely tap-triggered instead (see
`home-and-settings/requirements.md`'s "Permissions off Home") — at that
point, asking for them a second time during onboarding was pure
redundancy, and cutting the step got the wizard under the explicit 3-page
target on its own, without needing to also trim the remaining content.

## User Story 1: First-run wizard

As a person setting up Calm Otter on someone else's phone, I want a guided
multi-step introduction, so that I understand the app's purpose before
setting a password.

### Acceptance Criteria

1. WHEN the app has no password set THEN the system SHALL show
   `OnboardingActivity` (a 3-step wizard) instead of the main screen.
2. WHEN the user is on step 1 (welcome + how it works, combined) THEN the
   system SHALL show only informational content and a Next control — no
   permission grant buttons or status live here (see "3-step wizard").
3. WHEN the user reaches step 2 (password) THEN the system SHALL require a
   password and its confirmation to match before advancing, and SHALL also
   offer an optional "Your name" field for the accountability partner to
   identify themselves (see "Partner name" below) — leaving it blank SHALL
   NOT block advancing.
4. IF the password and confirmation don't match, or the password is empty,
   WHEN the user tries to advance past step 2 THEN the system SHALL show an
   error and SHALL NOT advance or save anything.
5. WHEN step 2 is passed validation THEN the system SHALL persist the
   password (and the optional name, if provided) via
   `PasswordManager.setPassword` and advance to step 3.
6. WHEN the user finishes step 3 THEN the system SHALL start `MainActivity`
   and clear the onboarding activity from the back stack (so back-navigation
   cannot return to onboarding).

## 3-step wizard

Cut down from the original 5 steps on explicit feedback that the wizard had
too many pages:

1. **Removed the permissions step entirely** (previously step 3: live
   Accessibility/Do Not Disturb grant status with a button each,
   re-checked via `resumeSignal` on every `onResume` while that step was
   visible). Accessibility and Do Not Disturb are no longer requested
   proactively anywhere — the first time either is still missing, tapping
   the otter on Home shows `PermissionExplainerDialog` with the same
   reasoning, contextually, instead. `OnboardingActivity` lost its
   `resumeSignal` entirely as a result: nothing left in the wizard depends
   on OS-level state that can change while backgrounded.
2. **Merged the original steps 1 and 2** (welcome, and "how it works") into
   one step: one intro paragraph plus a tightened 2-sentence mechanic
   summary (choose a duration → everything blocks except calls → ends on
   timer or on the trusted person's password), replacing the original's
   separate welcome blurb and 4-paragraph "how it works" explanation.
3. Steps 2 (password) and 3 (done) are otherwise unchanged in content —
   only their step index shifted (were 4 and 5).

## Partner name

A later pass added the optional "Your name" field described in User Story
1's Acceptance Criteria 3, so Settings can later show who set the current
password (see `home-and-settings/requirements.md`'s "Full list-card
redesign").

1. WHEN a name is entered in that field THEN the system SHALL store it
   (trimmed) alongside the password hash and SHALL make it retrievable
   independently of verifying the password itself (it is a display label,
   not a secret).
2. WHEN the password is later changed via `ChangePasswordScreen` (which
   does not collect a name) THEN the system SHALL leave any previously
   stored name untouched — changing the password does not imply the
   accountability partner changed.

## User Story 2: Password storage and verification

As the accountability partner, I want my password never stored in a way
that could leak in plain text, so that only I can end a pause session
early.

### Acceptance Criteria

1. WHEN a password is set THEN the system SHALL derive a PBKDF2-HMAC-SHA256
   hash (120,000 iterations, random 16-byte salt) and SHALL store only the
   salt and hash — never the plaintext password.
2. WHEN storing the salt/hash THEN the system SHALL use
   `EncryptedSharedPreferences` backed by an Android Keystore–managed
   AES256-GCM key.
3. WHEN a password is submitted for verification THEN the system SHALL
   recompute the hash with the stored salt and compare it to the stored
   hash; a mismatch SHALL be treated as a failed attempt.

## User Story 3: Rate-limited attempts (lockout)

As the accountability partner, I want repeated wrong guesses to be
throttled, so that the password can't be brute-forced by trial and error
from the block screen.

### Acceptance Criteria

1. WHEN 5 consecutive verification attempts fail THEN the system SHALL
   enter a lockout state for 30 seconds from the failing attempt.
2. WHILE in lockout, WHEN a verification is attempted THEN the system SHALL
   reject it immediately without touching the stored hash, the failed-attempt
   counter, or the lockout timer (so probing during lockout cannot reset or
   extend anything).
3. WHEN a correct password is verified (lockout not active) THEN the system
   SHALL reset the failed-attempt counter to zero.
4. WHEN the UI needs to show a lockout countdown THEN the system SHALL
   expose remaining lockout seconds, rounded up to the nearest whole second.

## Out of scope / known limits

- No password recovery flow: forgetting the password means uninstall/reinstall
  (data loss) is the only path back in, by design (see README "Known Limits").
- Changing the password (`ChangePasswordScreen`) reuses the same
  `PasswordManager.setPassword` and is not separately specified here beyond
  the storage guarantees above.
