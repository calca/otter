# Onboarding & Password — Design

## Key files

| File | Role |
|---|---|
| `OnboardingActivity.kt` | Hosts the wizard; just wires `passwordManager` and `onFinished` into the Composable — no permission-check plumbing (removed, see below) |
| `ui/screens/OnboardingScreen.kt` | The 3-step wizard UI (Compose) |
| `PasswordManager.kt` | Password hashing, storage (`EncryptedSharedPreferences`), verification |
| `LockoutPolicy.kt` | Pure rate-limiting state machine (no Context/Keystore dependency) |
| `ui/screens/CalmBackground.kt` | `Modifier.calmBackground()` — the same light `primary`-tinted background applied to `MainScreen.kt`'s root `Column`, applied here too; see `home-and-settings/design.md`'s "Tinted background" for why it's a low-alpha gradient and not `primary` as a solid fill |

## Flow

```
App launch
  └─ MainActivity.onResume() checks PasswordManager.isPasswordSet()
       └─ false → redirect to OnboardingActivity (FLAG_ACTIVITY_CLEAR_TASK)
            step 0: welcome + how it works (merged)
            step 1 (STEP_PASSWORD): password + confirm, validated on advance
            step 2: done → finishOnboarding() → MainActivity
```

## No more `resumeSignal` here

Earlier versions of this wizard had a dedicated permissions step (live
Accessibility/DND grant status, re-checked on every `onResume` via a
`resumeSignal` `Int` state — the same pattern `MainActivity`/`MainScreen`
still uses today for its own OS-level state). That step is gone (see
requirements.md's "3-step wizard"), and with it the only thing in this
screen that depended on state which could change while the Activity was
backgrounded. `OnboardingScreen` now takes only `passwordManager` and
`onFinished` — no `resumeSignal`, no permission-check lambdas — and
`OnboardingActivity` has no `onResume()` override at all.

## Password hashing

`PasswordManager.hash()`: `PBEKeySpec(password, salt, 120_000, 256)` via
`SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`. Salt is 16 random
bytes (`SecureRandom`). Both salt and hash are Base64-encoded
(`Base64.NO_WRAP`) before being written to `EncryptedSharedPreferences`.

## Partner name

The password step (`STEP_PASSWORD`) also has an optional "Your name"
`OutlinedTextField` (`hint_partner_name`) above the password/confirm
fields, captured because Settings later wants to show *who* set the
password (see `home-and-settings/design.md`'s "Full list-card redesign").
`setPassword(password: String, partnerName: String? = null)` gained the
second parameter with a default, so `ChangePasswordScreen.kt`'s existing
single-argument call still compiles unchanged; the trimmed name is only
written to `KEY_PARTNER_NAME` when non-blank, and calling `setPassword`
without a name (e.g. from Change Password) deliberately does **not** erase
a previously-stored name — changing the password doesn't imply the
accountability partner changed. `getPartnerName(): String?` is the read
side, called from `SettingsActivity` and passed straight into
`SettingsScreen` as `partnerName`. Like the password hash itself, the name
lives in `EncryptedSharedPreferences` alongside it — it's not sensitive on
its own, but it's already the same store and there's no reason to split it
out.

## Lockout state machine

`LockoutPolicy` is a pure `object` operating on `State(failedAttempts,
lockoutUntilMs)` — no Context, no I/O, fully unit-testable
(`LockoutPolicyTest`). `PasswordManager.verify()` is the only caller: it
reads current state from prefs, asks `LockoutPolicy.isLockedOut()` before
even attempting the hash comparison, and — only if not locked out — computes
`LockoutPolicy.afterAttempt(current, matches, now)` and persists the result.
This ordering is why a locked-out `verify()` call is side-effect-free.

## Singleton pattern

Both `PasswordManager` and `OnboardingActivity`'s other collaborators follow
the project-wide `getInstance(context.applicationContext)` singleton
pattern (see CLAUDE.md). Tests must call
`PasswordManager.resetInstanceForTests()` (actually there isn't one exposed
today — verify before relying on it in new tests; `SessionManager` and other
managers do expose it).
