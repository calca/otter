# Onboarding & Password — Design

## Key files

| File | Role |
|---|---|
| `OnboardingActivity.kt` | Hosts the wizard; just wires `passwordManager` and `onFinished` into the Composable — no permission-check plumbing (removed, see below) |
| `ui/screens/OnboardingScreen.kt` | The 3-step wizard UI (Compose) |
| `PasswordManager.kt` | Password hashing, storage (`EncryptedSharedPreferences`), verification |
| `LockoutPolicy.kt` | Pure rate-limiting state machine (no Context/Keystore dependency) |
| `ui/screens/CalmBackground.kt` | `Modifier.calmBackground()` — the same light `primary`-tinted background applied to `MainScreen.kt`'s root `Column`, applied here too; see `home-and-settings/design.md`'s "Tinted background" for why it's a low-alpha gradient and not `primary` as a solid fill |
| `ui/screens/PasswordOutlinedTextField.kt` | Shared password `OutlinedTextField` with a show/hide toggle — see "Show/hide password" below |

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

## Show/hide password: `PasswordOutlinedTextField`

Every password field in the app (this wizard's new-password/confirm pair,
`ChangePasswordScreen`'s current/new/confirm trio, and
`PasswordVerifyDialog`'s single field — see
`session-history-and-stats/design.md` and
`app-blocking-and-home-lock/design.md` for that dialog's own history) went
through a single `OutlinedTextField` + `PasswordVisualTransformation()`
each, independently, until a shared `PasswordOutlinedTextField` composable
replaced all six call sites at once, on request ("l'occhiolino per fare
vedere la password").

- **The eye glyph is hand-drawn via `Canvas`** (an almond outline + pupil
  dot, plus a diagonal strike-through when hidden), not
  `Icons.Filled.Visibility`/`VisibilityOff` — those two live only in
  `material-icons-extended`, an artifact `app/build.gradle.kts`
  deliberately doesn't include (only the much smaller `material-icons-core`
  is a dependency, and it doesn't have this pair). Adding the extended
  artifact for two glyphs wasn't judged worth its size; drawing them by
  hand instead matches the mascot marks' own established convention
  (`ui/mascot/OtterMarks.kt`) of small Canvas-drawn shapes over pulling in
  more icons.
- **`passwordVisible` (`mutableStateOf(false)`) is internal to the
  composable**, not hoisted by any caller — same reasoning as
  `PasswordVerifyDialog`'s own internal state (see its doc comment):
  whichever screen embeds this field doesn't need to know or manage
  whether it's currently shown in clear text.
- The toggle's `IconButton` carries a `Modifier.semantics { contentDescription = ... }`
  (`show_password`/`hide_password`, swapping with state) for accessibility,
  since the hand-drawn `Canvas` icon has no built-in semantics of its own
  the way `Icon(imageVector = ...)` would.
- All six call sites kept their existing `enabled`/`modifier` per-field
  behavior (e.g. `ChangePasswordScreen`'s current-password field disables
  during lockout) — `PasswordOutlinedTextField` accepts both as parameters,
  same shape as a plain `OutlinedTextField` would.

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

## Keeping the password step short and keyboard-safe

The password step is the one step with a form, so it's the one place this
wizard can get too tall or have its controls hidden by the keyboard — both
reported directly.

- **`.imePadding()` on the whole root `Column`** (not just the scrollable
  inner one): without it, the Back/Next `Row` — a fixed-height sibling
  *below* the scrollable middle `Column`, not inside it — stayed at its
  laid-out position while the keyboard covered it from below, since nothing
  told the layout to shrink for the keyboard. The symptom was that Back/Next
  became genuinely unreachable while typing a password, forcing the keyboard
  to be dismissed first to proceed. `imePadding()` on the root adds bottom
  padding equal to the keyboard's height, which both frees room for
  Back/Next above it and shrinks the scrollable middle `Column` enough that
  it can still scroll the currently-focused field into view.
- **`onb4_body` was cut from three short paragraphs to one** (see
  requirements.md) — same information, far less vertical space.
- **`PactPawsMark` is smaller on this step specifically** (72dp vs the
  120dp/96dp used on the other two steps) and `bodyBottomPadding` dropped
  from 24dp to 16dp — this step has three text fields below the intro
  content, so the intro itself is given less room on purpose, unlike the
  other two steps which have nothing else competing for vertical space.
- Field-to-field padding was trimmed from 12dp to 10dp — minor on its own,
  meaningful stacked across three fields.

None of this touches the other two steps' spacing (they had no reported
problem and still fit comfortably without scrolling), so `StepBody`'s
shared 24dp illustration-to-title spacer was left alone; only this step's
own values were tightened.

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
