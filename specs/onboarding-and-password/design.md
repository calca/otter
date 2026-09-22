# Onboarding & Password — Design

## Key files

| File | Role |
|---|---|
| `OnboardingActivity.kt` | Hosts the wizard; just wires `passwordManager` and `onFinished` into the Composable — no permission-check plumbing (removed, see below) |
| `ui/screens/OnboardingScreen.kt` | The 3-step wizard UI (Compose) |
| `PasswordManager.kt` | Password hashing, storage (`EncryptedSharedPreferences`), verification |
| `LockoutPolicy.kt` | Pure rate-limiting state machine (no Context/Keystore dependency) |
| `ui/screens/CalmBackground.kt` | `Modifier.calmBackground()` — the same light `primary`-tinted background applied to `MainScreen.kt`'s root `Column`, applied here too; see `home-and-settings/design.md`'s "Tinted background" for why it's a low-alpha gradient and not `primary` as a solid fill |
| `ui/screens/PasswordOutlinedTextField.kt` | Shared password field with a show/hide toggle — built on `CalmTextField`, see "Show/hide password" below |

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

## One text field for the whole app: `CalmTextField`

Reported plainly: "gli input non sono come quelli di Stitch". They weren't —
every field in the app was a stock Material 3 `OutlinedTextField`: hairline
outline, barely-rounded corners, label floating up into a notch in the
border. The redesign draws them differently, and its markup says so
explicitly: a filled container, `rounded-2xl` (16px), and a **placeholder**
where the `<label>` exists only as `sr-only`.

`CalmTextField` (`CalmBackground.kt`, next to `CalmSecondaryButton` — same
idea: one shared treatment instead of the same details retyped per screen)
is now used by all four places the app takes typing: passwords (via
`PasswordOutlinedTextField`), the trusted person's name in onboarding, the
invite code in group pause, and the app-list search.

- **Container `primary` at 6%**, a touch lighter than the 8% of unselected
  duration chips: an empty field should not compete with a button.
- **Border `primary` at 18%, going to full `primary` on focus** — the
  mockup's way of saying "you are typing here". Its soft outer ring
  (`ring-2 ring-pine/20`) was not copied: it is a web affordance, and the
  border already carries the state.
- **Colours are passed one by one**, not left to
  `OutlinedTextFieldDefaults.colors()`, which reads `surfaceVariant` and
  `onSurfaceVariant` — roles `CalmOtterTheme` does not customise, so stock
  Material 3 purple would show through whatever palette is chosen. The same
  trap CLAUDE.md documents.
- The mockup's hexes (`#f3f4f0`, `#dce3dc`) are deliberately not used, for
  the reason every shared piece here gives: they would stay grey-green
  across all eight palette/theme combinations.

**The label survives for screen readers.** A placeholder disappears the
moment you type, which would leave a TalkBack user with a mute field
half-way through filling it in. `label` is therefore both the placeholder
*and* the field's `contentDescription`, so it keeps being announced once
there is text in it.

Verified on device, focused and unfocused, on the password prompt that
guards the allowed-apps list.

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

### No autofill on these fields

The host view of whatever window contains a `PasswordOutlinedTextField` is
marked `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`, which switches
autofill off for that whole window — the dialog's window for a dialog, the
activity's for a screen.

The reason that matters most is not technical: a pause password stored in
the phone's own password manager contradicts what the password is for. The
trusted person knows it, the person pausing does not; if the phone refills it
on their behalf, they can end their own pause and the pact is worth nothing.

It was found while chasing the block-screen flash (see
specs/app-blocking-and-home-lock/design.md), on the theory that the autofill
popup was the trigger. Measured on a Galaxy S22: it is not — the
`com.google.android.ext.services` window still appears with autofill off, and
what fixes the flash is the full-screen filter in the accessibility service.
The change stays on its own merit.

## Password hashing

`PasswordManager.hash()`: `PBEKeySpec(password, salt, 120_000, 256)` via
`SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`. Salt is 16 random
bytes (`SecureRandom`). Both salt and hash are Base64-encoded
(`Base64.NO_WRAP`) before being written to `EncryptedSharedPreferences`.

### A restored backup could crash the app on every launch, forever

Reported: "ho installato la build da zero e l'app va in crash" — no
logcat at first, so ruled out the obvious things by reproducing rather
than guessing: a clean debug install and the exact signed beta APK
downloaded from the CI run (`gh run download`) both launched fine on the
emulator. The real device (a Galaxy S22, connected over `adb` once
available) gave the actual trace: `javax.crypto.AEADBadTagException` in
`PasswordManager.<init>`, thrown from `EncryptedSharedPreferences.create()`
— a `RuntimeException` at `MainActivity.onCreate()`, i.e. the app cannot
reach a single screen, ever, not even onboarding.

Root cause: `calm_otter_secure_prefs.xml` doesn't just hold the salted
password hash — androidx.security.crypto also stores its own Tink keyset
(the key actually used to encrypt/decrypt individual entries) as two
further string entries in that *same* file, and that keyset is itself
encrypted with a key held in Android Keystore. The manifest had
`android:allowBackup="true"` with no exclusions, so Android's backup
mechanism could restore this file's *contents* (the ciphertext) on a
reinstall — but the Keystore key that ciphertext was encrypted under is
hardware/install-bound and is never part of any backup. Restored
ciphertext against a different key decrypts to garbage, which the AEAD
tag check catches and throws on — precisely the reported symptom, and
reproduced by hand: after setting a password, overwriting just the key
keyset string in `calm_otter_secure_prefs.xml` with different bytes and
relaunching, on the actual S22 reproduced the identical stack trace.

Two independent fixes, not one:

- **`AndroidManifest.xml`** gained `android:dataExtractionRules` (API 31+)
  and `android:fullBackupContent` (API 23–30, still relevant at
  `minSdk = 26`), both excluding `calm_otter_secure_prefs.xml` by name —
  closes the actual trigger. Restoring a Keystore-encrypted file without
  its key was never going to work anyway; the file is simply not backed
  up now.
- **`PasswordManager`'s `prefs` initializer** wraps `EncryptedSharedPreferences.create()`
  in a try/catch: on any failure, it clears the underlying
  `SharedPreferences` file (`context.getSharedPreferences(...).edit().clear()`)
  and creates it again fresh, rather than letting the exception propagate.
  This is deliberately broader than "undo the backup-restore case" — any
  future cause of the same Keystore-key/ciphertext mismatch (a factory
  Keystore reset, an OEM bug, manual tampering) hits the exact same
  crash-on-every-launch otherwise, with no way for the user to recover
  short of clearing app data themselves (which they'd have no reason to
  suspect, since the app never told them why it keeps crashing). The
  recovered state is equivalent to "no password set yet" — a real loss
  if one was set, but the alternative is an app that never opens again.

Verified end to end on the S22, not just by reasoning about the trace:
the original crash (captured live via `adb logcat` while the user
reopened the app) gave the exact stack trace above. Separately, on the
*fixed* build, set a password, corrupted `calm_otter_secure_prefs.xml`'s
key keyset by hand (same file, same entries, deliberately mangled bytes)
to recreate the identical mismatch, then relaunched: no crash,
`PasswordManager` silently rebuilt a fresh keyset, the app landed back on
onboarding instead of crash-looping.

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

### Back/Next is a `Column` now, not a `Row` — and the order flipped

On request ("tutti i bottoni, posso essere a tutta larghezza ed uno sotto
l'altro?"): the bottom navigation went from a side-by-side `Row` to a
full-width `Column`. A follow-up request reversed the order within that
same change: **`Back` is on top, `Next`/`Finish` is last** — the primary
action is the bottom-most one, not the first, which reads as "the one
closest to where your thumb already is" rather than "positive above
negative". `Back` only renders from step 2 on, same as before; step 1
still shows `Next` alone.

Already anchored to the page bottom before this change and unaffected by
it — the scrollable step-content `Column` above carries `Modifier.weight(1f)`
(see the root `Column` at the top of `OnboardingScreen`), so the button
`Column` was already the last, non-scrolling sibling pinned below whatever
space that leaves. No restructuring needed here, unlike the Bluetooth lobby
host screen (`specs/group-pause/design.md`), which centred its content with
`Arrangement.Center` and needed one.

**CTA height made explicit: `.height(48.dp)` on both buttons.** Measured on
the emulator before this change: 120px at this device's 480dpi (density
3.0) = 40dp, Material3's own `ButtonDefaults.MinHeight` and below the
48dp minimum touch target Material Design/WCAG 2.5.5 recommend — not
trusted from the M3 default, verified and then fixed. The gap between the
two stacked buttons is `Arrangement.spacedBy(8.dp)` on the `Column`, not
`.padding(top = 8.dp)` on the second button: that padding, chained *after*
`.height(48.dp)`, was absorbed into the already-fixed height instead of
adding space above it — measured 40dp again on that one button specifically
until caught and moved to the `Column`.

### The keyboard's "Next" now chains through the three fields

Requested: "quando faccio next mi deve portare alla successiva testbox
vuota, così da facilitare l'onboarding". Name/password/confirm-password had
no `KeyboardOptions`/`KeyboardActions` at all before this — the IME's own
action-button label and behavior fell back to whatever default the system
picked (observed as "Done"/closes-and-does-nothing-else on this emulator),
so pressing it never moved focus anywhere.

Both `CalmTextField` (`CalmBackground.kt`) and `PasswordOutlinedTextField`
gained a `keyboardActions` parameter (default `KeyboardActions.Default`, so
every other caller — `ChangePasswordScreen`, `PasswordVerifyDialog`,
`AllowedAppsScreen`'s search field, etc. — is unaffected);
`PasswordOutlinedTextField` also gained `imeAction`, since it hardcoded
`KeyboardOptions(keyboardType = Password)` with no way to override the
action before. Wired as a three-field chain: name → `ImeAction.Next` +
`onNext` requests focus on a `FocusRequester` pinned to the password field;
password → same pattern, requesting focus on a second `FocusRequester`
pinned to the confirm field; confirm → `ImeAction.Done`, no custom
`onDone`. That last part was not the first attempt — a custom
`onDone = { focusManager.clearFocus() }` (and then `clearFocus(force = true)`,
same result) sent focus back to the *name* field instead of releasing it,
verified on the emulator both times before giving up on it; the system's
own default handling of the `Done` action — close the keyboard, leave
focus alone — turned out to already be exactly right, and needed no
override at all.

Verified end to end on the emulator: filling name, pressing the IME action
key (`adb shell input keyevent 66`, not a screen tap — the on-screen "Next"
label the earlier screenshot showed belongs to the wizard's own bottom
button, not the keyboard's action key, which renders as a plain arrow-into-bar
glyph) lands focus on password; from there on confirm; from confirm it
closes the keyboard with focus still on that field, `uiautomator`-dumped
`focused="true"` each time to confirm the actual target, not just eyeballed
from a screenshot.

### Bug report: "on Samsung, with Gboard, Next does nothing" — a discarded fix that was worse

Reported directly, no repro steps beyond device (Samsung) and IME (Gboard,
not the Samsung keyboard). Not reproducible in this environment — no
Samsung hardware, and the verification method documented just above has a
real gap: `adb shell input keyevent 66` injects a hardware Enter key,
which Compose translates into the field's `imeAction` — a completely
different code path from `InputConnection.performEditorAction()`, the one
a real software keyboard calls when its own "Next" glyph is tapped. The
previous verification only ever exercised the first path, never the
second, on this same screen.

**First attempt, reverted**: replace both `FocusRequester`s with
`LocalFocusManager.current.moveFocus(FocusDirection.Next)` — walks the
focus tree in composition order instead of requesting an exact target,
which looked like a plausible fix for "a manually-placed `FocusRequester`
occasionally fails to receive focus on some device/IME combo." Verified
on the emulator (via the same `keyevent 66` method, still not the real
software-keyboard path — but enough to catch this) and found a concrete,
*worse* bug: from the password field, `moveFocus(Next)` does not land on
the confirm field. It lands on the password field's own "show password"
`IconButton` (`PasswordOutlinedTextField`'s trailing icon) — also a valid
focus target for directional search, and the nearer one. Confirmed via
`uiautomator dump`: after the second "Next", the only `focused="true"`
node is `content-desc="Show password"`, keyboard closed, no text field
focused. The *first* hop (name → password, no icon in the way) worked
correctly, which is exactly why a test that only checks one hop can look
like a fix. Reverted to the two `FocusRequester`s — confirmed correct
again on both hops with the same `uiautomator`-dump method.

**Net result: no fix for the actual Samsung/Gboard report.** The reverted
code is identical in behavior to what shipped before this report — this
session could not reproduce the bug, and the one alternative implementation
tried made things concretely worse rather than better. If it recurs, the
next step needs either real Samsung hardware or a more specific repro
(does it happen on the name→password hop too, or only password→confirm —
the two hops are not equivalent, as this investigation found the hard
way).

Each step body highlights exactly one key phrase in bold (e.g. "the rest
can wait" on the final step) rather than reading as a flat block of text.
The obvious API for this — `androidx.compose.ui.text.AnnotatedString.fromHtml()`
(ui-text 1.6.0+) — was tried first and rejected: it failed to resolve at
compile time (`Unresolved reference 'fromHtml'`) against this project's
Kotlin toolchain even though the symbol is present and public in the
resolved `ui-text-android:1.13.0-alpha03` classpath (verified with
`javap`) — almost certainly a mangling/metadata mismatch specific to this
alpha BOM, not a real absence of the API. Rather than chase an alpha
dependency's toolchain quirk, `StepBody` uses a small hand-rolled
`boldAnnotatedString(text: String): AnnotatedString` (private, bottom of
`OnboardingScreen.kt`) that scans for literal `<b>`/`</b>` markers and
wraps the enclosed span in `SpanStyle(fontWeight = FontWeight.Bold)` via
`buildAnnotatedString`/`pushStyle`/`pop` — same "small hand-rolled shape
instead of a bigger dependency" convention as the hand-drawn eye icon
above.

The markers themselves live in `strings.xml`/`values-en/strings.xml` as
`&lt;b&gt;`/`&lt;/b&gt;` (HTML-entity-escaped), **not** bare `<b>` — bare
tags are Android's own native styling-span syntax, which `getString()`
(what `stringResource()` calls) silently strips before the string ever
reaches Kotlin. Escaping them makes `getString()` return the literal
characters `<b>...</b>` as plain text, which `boldAnnotatedString` then
parses itself.

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


## Redesign pass: the three steps

Each step now ends with a tinted note card (`OnboardingNoteCard`) carrying an
icon: the paws for the pact, a padlock for the password, the sprig for the
closing line.

**The card holds no new copy.** It is the last sentence of that step's own
body, moved out of the paragraph — and those sentences were already the ones
in bold: the pact, the act of trust, take care of yourself. They say *why*
the app works this way; buried at the end of a paragraph they read as a
sign-off, standing alone they read as the premise they are. The strings were
split accordingly (`onb1_note`, `onb4_note`, `onb5_note`).

The otter now illustrates all three steps instead of one each of otter, paws
and sprig: those two marks moved into the cards, and the mascot gives the
three steps one face — which is also what the mockup does.

Not taken from the mockup: the "DIGITAL SANCTUARY" chip and the "Zero
intrusions"/"Caring guardian" badges (labels with nothing behind them — the
claims are true, but an onboarding screen is not where the app should
advertise itself), and the "Learn how Calm Otter works" footer link, which
has nowhere to lead.


## `ChangePasswordScreen`'s "Save new password" is bottom-anchored now

Caught in the same audit as the group-pause join flow ("controlla tutte
le CTA button" — see `specs/group-pause/design.md` for the others). This
screen was never `CalmScreenColumn` (a plain scrollable `Column`,
`Arrangement.Top` by default, so it never centred like the others did) —
but the "Cambia password" `Button` still just sat directly after the last
field with no `weight(1f)` pushing it down, so on short error-free content
it landed right below the fields near the top of the screen rather than
pinned to the bottom.

Same fix as everywhere else in this pass: the info text, the three
`PasswordOutlinedTextField`s, and the error message now live in an inner
scrollable `Column` with `weight(1f)` (scroll moved from the outer Column
to this one — same visible behavior, just now bounded above the button
instead of taking the whole screen); the button is the last, non-weighted
sibling, `.fillMaxWidth().height(48.dp)` — the same 48dp standard as every
other CTA fixed in this session ("CTA height made explicit", above).


## `OnboardingNoteCard`'s label wasn't centred for one-line notes

Reported: "in 'all set' la label 'take care ..' non è centrata
verticalmente nel box" — `onb5_note` ("Take care of yourself.") is a
single short line, unlike `onb1_note`/`onb4_note` which wrap to several.
The card's `Row` used `verticalAlignment = Alignment.Top`, which aligns
the icon circle's top edge with the text's top edge — fine when the text
is tall enough to visually "contain" the icon, but for a single line
shorter than the 34dp icon circle it left the text sitting near the top
of the row with visible empty space below it, next to an icon that's
centred within its own circle.

Fixed by switching to `Alignment.CenterVertically`. Checked this doesn't
regress the two longer, wrapping notes (`onb1`, `onb4`): the icon is only
34dp against a multi-line text block, so centring it against the whole
block still reads as "aligned with the text," not detached from the first
line the way a large icon next to a long paragraph might.

Verified on the emulator: `onb1` (three-line note) still looks correctly
aligned centred against its block; `onb5` ("Take care of yourself.") now
has its icon and text vertically centred together in the card instead of
icon-centred/text-top-aligned.


## `PasswordManager` was the only untested state holder — fixed, with a fake AndroidKeyStore

Found during a full-project review (`TODO.md` "2.1"), not a report: the most
security-critical class in the app — password hashing, lockout, the
self-healing recovery path added for the `AEADBadTagException` crash above —
was the one singleton with zero test coverage.

**Why it had none.** `PasswordManager`'s `prefs` property is initialized at
construction via `MasterKey.Builder(context).setKeyScheme(AES256_GCM).build()`,
which needs a real `KeyStore.getInstance("AndroidKeyStore")`. Robolectric
doesn't provide one — `AndroidKeyStore` is a system/hardware-backed service,
not a plain Android class, and out of scope for what Robolectric shadows
(confirmed: no `AndroidKeyStore` shadow anywhere in `shadows-framework-4.17.jar`).
Every `PasswordManagerTest` attempt failed at construction with
`NoSuchAlgorithmException: AndroidKeyStore KeyStore not available` before this
fix.

**Fix: a minimal fake `AndroidKeyStore` JCA provider**, `FakeAndroidKeyStore.kt`
(test-only, `app/src/test/`). Registers a `java.security.Provider` named
`"AndroidKeyStore"` with two services:

- `KeyStore.AndroidKeyStore` — a `KeyStoreSpi` backed by a plain in-memory
  `Map<String, SecretKey>`, enough for `containsAlias`/`getKey`/`setKeyEntry`/
  `deleteEntry` (what `MasterKey`/Tink's keyset manager actually calls).
- `KeyGenerator.AES` — a `KeyGeneratorSpi` that reads the alias out of the
  `KeyGenParameterSpec` it's given, generates a real (software, extractable —
  unlike the hardware-backed original) AES key via the JVM's own `KeyGenerator`,
  and stores it under that alias in the same backend map.

That's the whole surface `EncryptedSharedPreferences`/`MasterKey` touch for
the `AES256_GCM` master key scheme (purely symmetric — no EC/RSA key
generation to fake). `installFakeAndroidKeyStore()` registers it once,
idempotently, called from `@Before`.

**What this does and doesn't prove.** The fake keys are software and
extractable, not hardware-backed like the real Android Keystore — this
suite verifies `PasswordManager`'s own logic (hashing, lockout wiring,
partner-name handling, the corrupted-keyset self-heal path), not the
hardware security guarantees of the real Keystore, which no JVM unit test
can exercise. That distinction is spelled out in the file's own class doc
so it isn't mistaken for more than it is.

Coverage added (`PasswordManagerTest`, 9 cases): set/verify round-trip,
wrong password, `verify()` before any password is set, partner name
surviving a password change that omits it (`PasswordManager.kt:78`'s
documented behavior), partner name absent/trimmed, lockout counters
persisting across a fresh singleton instance on the same prefs file (not
just in-memory), and — the one this class existed to protect —
`corruptedKeysetSelfHealsInsteadOfCrashing`: writes garbage directly into
the Tink keyset's own `SharedPreferences` keys (the same two entries a
backup-restore corrupts, per the crash writeup above), then asserts
`PasswordManager.getInstance()` recovers instead of throwing, and that the
recovered instance works normally from there.

Verified: `assembleDebug`/`lintStableDebug`/`lintBetaDebug`/
`testStableDebugUnitTest`/`testBetaDebugUnitTest` all green, all 9 cases
pass (confirmed via the JUnit XML report, not just "build succeeded").


## Lockout countdown said "1 secondi"/"1 seconds" for the last tick

Found during a full-project review (`TODO.md` "5.1"), not a report.
`password_locked_out` interpolated `%1$d` directly into a fixed plural noun
("secondi"/"seconds") — grammatically wrong on the last second of a lockout
countdown in both locales (Italian more visibly so: "1 secondi" reads as an
obvious mistake, where English's "1 seconds" is a smaller wrongness some
readers skim past).

Converted to `<plurals name="password_locked_out">` (`one`/`other`), called
via `pluralStringResource(R.plurals.password_locked_out, it, it)` in both
`PasswordVerifyDialog.kt` and `ChangePasswordScreen.kt`. Verified:
`lintStableDebug`/`lintBetaDebug` (the `PluralsCandidate` finding is gone),
`testStableDebugUnitTest`/`testBetaDebugUnitTest`; not re-verified on
device specifically for the count-down-to-1 case (would need waiting out a
real lockout) — the same `pluralStringResource` call pattern was verified
end-to-end elsewhere in this same pass (see
`specs/session-history-and-stats/design.md`), so this one is judged safe
by construction rather than independently re-checked live.


## `security-crypto` moved off the alpha it had been pinned to since day one

TODO.md "6.1": `1.1.0-alpha06` → stable `1.1.0`, done as its own commit
rather than folded into the rest of the dependency-drift cleanup ("6.2") —
deliberately, since this is the exact library behind the
`AEADBadTagException` crash fixed earlier in this file, making it the one
upgrade worth attributing on its own if anything regresses.

Nothing about `PasswordManager.kt` needed to change: `MasterKey`/
`EncryptedSharedPreferences`'s public API is unchanged between the two
versions. The compiler does now flag both classes `@Deprecated` — the
library has a newer recommended surface as of 1.1.0 that this project
hasn't adopted — noted here as a fact, not acted on; migrating off a
now-deprecated-but-still-functional API is a separate, larger piece of
work than a version bump, and isn't what this pass set out to do.

Verified beyond the standard build/lint/test pass (all green, including
all 9 `PasswordManagerTest` cases — the fake-Keystore suite from "2.1"
turned out to double as regression coverage for exactly this upgrade):
installed on the emulator and actually exercised the encrypted-prefs
round trip on a real device rather than just the JVM test double — opened
Settings → Change password on the app's existing password (set under the
*old* alpha library, in an earlier session), verified the current
password against it, wrote a new one, confirmed the screen returned to
Settings (the success path) with no exception in `adb logcat`. Both
directions of the exact operation the AEADBadTag crash involved — reading
an existing encrypted file, writing a new one — worked under the upgraded
library.


## `verify()`'s hash comparison, made constant-time

`TODO.md` "7": `PasswordManager.verify()` compared the computed and stored
hashes with `ByteArray.contentEquals()`, which returns as soon as it finds
a differing byte — not a timing side-channel with much practical bite here
(whoever is attempting the unlock is already holding the device, and
120,000 rounds of PBKDF2 dominates any measurement by orders of
magnitude), but `MessageDigest.isEqual()` is a drop-in, constant-time
replacement that removes the question rather than reasoning about how
theoretical it is. One-line swap, no behavior change for any legitimate
caller.


## PBKDF2 measured, then moved off the main thread

`TODO.md` "3.1" set a concrete decision rule rather than a vague "consider
async": measure `verify()`'s real cost first, leave it alone if it's tens
of milliseconds, move it off the main thread if it's hundreds.

**Measured with temporary instrumentation** (a `System.nanoTime()` wrap
around `hash()`, logged, then removed before committing — never shipped):
**~167ms** per PBKDF2 call (120,000 rounds) on the emulator. Real device
measurement (the S22 already used for other verification in this project)
wasn't possible this session — the phone was locked with no one present to
unlock it, and this codebase has an established rule against interacting
with a locked personal device (see the crash writeup earlier in this
file). The emulator number is very likely optimistic, not pessimistic: it
runs as a native ARM64 image accelerated by this Mac's own Apple Silicon
cores, which are not slower than a typical Android phone SoC and are
almost certainly faster than "the oldest realistic device" the TODO item
asked to check. 167ms already lands in the "hundreds" bucket the TODO's
own rule treats as the trigger; a real, older device would not plausibly
be faster.

**Fixed** in both places `PasswordManager.verify()`/`setPassword()` are
called from a button handler — `PasswordVerifyDialog.kt` (used for
`BlockScreen` unlock and "Manage allowed apps") and `ChangePasswordScreen.kt`
(which calls both `verify()` *and* `setPassword()` in its success path, so
up to ~330ms combined before this fix). Both now launch a coroutine from
`rememberCoroutineScope()`, run the PBKDF2 call(s) via `withContext(
Dispatchers.Default)`, and show a small `CircularProgressIndicator` in
place of the confirm button's label while in flight — the button, and in
`ChangePasswordScreen`'s case the three password fields, are disabled for
the same window so a second tap can't race the first. `ChangePasswordScreen`
keeps one pre-existing distinction unchanged: the *new*-password fields
stay editable during an active lockout (only the *current*-password
attempt is rate-limited) — they're now gated on the new `isChanging` flag
alone, not folded into the broader "busy" state that also covers
`isLockedOut`, to avoid quietly changing that behavior while fixing an
unrelated problem.

Verified: full `assembleDebug`/`lintStableDebug`/`lintBetaDebug`/
`testStableDebugUnitTest`/`testBetaDebugUnitTest` pass (16 suites, zero
failures — including `PasswordManagerTest` and `CtaButtonInvariantsTest`'s
existing `ChangePasswordScreen` coverage, neither of which needed changes).
Live on the emulator: onboarded fresh, changed the password through
`ChangePasswordScreen` (caught the spinner mid-flight in a screenshot,
confirmed screen returns to Settings on success), then verified the *new*
password through the separate `PasswordVerifyDialog` path ("Manage allowed
apps" opened) — both async paths exercised end to end, no crash, no
exception in `adb logcat`.
