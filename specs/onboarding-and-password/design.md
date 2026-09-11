# Onboarding & Password — Design

## Key files

| File | Role |
|---|---|
| `OnboardingActivity.kt` | Hosts the wizard; wires permission-check callbacks and `resumeSignal` (see below) into the Composable |
| `ui/screens/OnboardingScreen.kt` | The 5-step wizard UI (Compose) |
| `PasswordManager.kt` | Password hashing, storage (`EncryptedSharedPreferences`), verification |
| `LockoutPolicy.kt` | Pure rate-limiting state machine (no Context/Keystore dependency) |

## Flow

```
App launch
  └─ MainActivity.onResume() checks PasswordManager.isPasswordSet()
       └─ false → redirect to OnboardingActivity (FLAG_ACTIVITY_CLEAR_TASK)
            step 0: intro
            step 1: accountability-partner explanation
            step 2 (STEP_PERMISSIONS): accessibility + DND grant buttons
            step 3 (STEP_PASSWORD): password + confirm, validated on advance
            step 4: done → finishOnboarding() → MainActivity
```

## The `resumeSignal` pattern

`setContent {}` is called once in `onCreate`; Compose has no built-in way to
observe OS-level state (accessibility service enabled, DND access granted)
changing while the Activity is backgrounded (e.g. user went to Settings and
back). `OnboardingActivity.onResume()` increments an `Int` state
(`resumeSignal`), which `OnboardingScreen`'s `LaunchedEffect(resumeSignal,
currentStep)` is keyed on — so the effect re-runs on every resume, but only
actually re-checks permissions while `currentStep == STEP_PERMISSIONS`. The
identical pattern is reused by `MainActivity`/`MainScreen` for the same
reason (accessibility/DND/default-home status).

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
