# Onboarding & Password — Design

## Key files

| File | Role |
|---|---|
| `OnboardingActivity.kt` | Hosts the wizard; just wires `passwordManager` and `onFinished` into the Composable — no permission-check plumbing (removed, see below) |
| `ui/screens/OnboardingScreen.kt` | The 3-step wizard UI (Compose) |
| `PasswordManager.kt` | Password hashing, storage (`EncryptedSharedPreferences`), verification |
| `LockoutPolicy.kt` | Pure rate-limiting state machine (no Context/Keystore dependency) |

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
