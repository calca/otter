# Onboarding & Password — Requirements

## Context

Calm Otter's lock model assumes the password is set by someone other than
the phone's user (the "accountability partner"). First run walks whoever
sets the phone up through why, then through granting permissions, then
through choosing that password.

## User Story 1: First-run wizard

As a person setting up Calm Otter on someone else's phone, I want a guided
multi-step introduction, so that I understand the app's purpose before
granting permissions or setting a password.

### Acceptance Criteria

1. WHEN the app has no password set THEN the system SHALL show
   `OnboardingActivity` (a 5-step wizard) instead of the main screen.
2. WHEN the user is on steps 1–2 (intro, accountability-partner explanation)
   THEN the system SHALL show only informational content and a Next control.
3. WHEN the user reaches step 3 (permissions) THEN the system SHALL show the
   live grant status of the Accessibility service and Do Not Disturb access,
   each with its own "grant" button that opens the relevant system settings
   screen.
4. WHEN the user returns to the app from system settings (any `onResume`)
   WHILE step 3 is the visible step THEN the system SHALL re-check both
   permissions and update their displayed status, without requiring the user
   to leave and re-enter the step.
5. WHEN the user reaches step 4 (password) THEN the system SHALL require a
   password and its confirmation to match before advancing.
6. IF the password and confirmation don't match, or the password is empty,
   WHEN the user tries to advance past step 4 THEN the system SHALL show an
   error and SHALL NOT advance or save anything.
7. WHEN step 4 is passed validation THEN the system SHALL persist the
   password via `PasswordManager.setPassword` and advance to step 5.
8. WHEN the user finishes step 5 THEN the system SHALL start `MainActivity`
   and clear the onboarding activity from the back stack (so back-navigation
   cannot return to onboarding).

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
