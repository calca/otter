# TODO — review findings

A review of the codebase as of 2026-09-22, ordered by value rather than by
effort. Written in the same spirit as `specs/`: what is actually wrong and
why it matters, not a wish list.

**Two conventions used below:**

- **Verified** means I read the code path end to end, or lint/a tool
  reported it. **Suspected** means the reasoning holds but nothing was run
  on a device to confirm it — reproduce before fixing.
- Effort is a rough size: **S** (under an hour), **M** (an afternoon),
  **L** (bigger than one sitting).

Things deliberately *not* on this list: everything under "Known Limits of
this version" in `README.md`. The soft block is a stated design position,
not a defect, and this document does not relitigate it.

---

## 1. Correctness

### 1.1 A pause that ends naturally can be written to history twice — **S** — ✅ fixed

*Verified by reading, then reproduced-and-confirmed-fixed on the emulator
and locked in by `SessionManagerTest.endSessionCalledTwiceRecordsHistoryOnlyOnce`.
See `specs/pause-session-core/design.md`.*

`SessionManager.endSession()` (`SessionManager.kt:111`) has no guard and is
not idempotent:

- It never clears `KEY_START_TIME` or `KEY_PLANNED_MINUTES` — only
  `KEY_ACTIVE`, `KEY_COMPANIONS`, `KEY_GROUP_TAG`, `KEY_IS_HOST`,
  `KEY_END_TIME`.
- Its history-writing condition is `startTime > 0 && plannedMinutes > 0`,
  both of which stay true after the first call.
- `SessionRecord` has an `autoGenerate` primary key and the DAO does a
  plain `@Insert` with no unique index on `startTimeMs`, so nothing
  deduplicates downstream.

There are five call sites, and at natural expiry **two of them fire for the
same pause**:

- `SessionExpiryReceiver.kt:14` — the `AlarmManager` broadcast at `endTime`.
- `BlockScreen.kt:180` — the in-screen countdown loop, which polls
  `remainingMillis()` every 60s and calls `endSession()` when it reaches 0.

These are independent of each other and neither checks whether the session
is still active. The block screen being open at expiry is the *normal*
case, not an edge case.

Symptom to look for: two identical rows in History for one pause, and
correspondingly inflated streak (`SessionStreak`), weekly goal progress
(`WeeklyGoalManager`), and session totals.

**Fix:** make `endSession()` return early when `KEY_ACTIVE` is already
false, and clear `KEY_START_TIME`/`KEY_PLANNED_MINUTES` with the rest. Add
a `SessionManagerTest` case that calls `endSession()` twice and asserts one
record — the test harness for this already exists.

### 1.2 The user's own Do Not Disturb setting is overwritten and never restored — **M** — ✅ fixed

*Verified: there is no `getNotificationPolicy()` or
`getCurrentInterruptionFilter()` call anywhere in the codebase. Now fixed
and locked in by `SessionManagerTest.endSessionRestoresThePreviousDndPolicyInsteadOfClearingIt`.
See `specs/pause-session-core/design.md`.*

`SessionManager.setPauseDnd()` (`SessionManager.kt:180`):

- On start it calls `nm.setNotificationPolicy(...)` with a policy built
  from scratch, permanently replacing whatever policy the user had
  configured. That policy is global device state, and it is never read
  first nor put back.
- On end it calls `setInterruptionFilter(INTERRUPTION_FILTER_ALL)`
  unconditionally — so a user who already had DND **on** before starting a
  pause finds it **off** afterwards.

For an app whose entire premise is being gentle and predictable with
someone's phone, silently editing a global setting and not giving it back
is the sharpest edge in the codebase.

**Fix:** capture `nm.currentInterruptionFilter` and `nm.notificationPolicy`
at `startSession()`, persist them alongside the session state (they must
survive a reboot — `reapplyAfterBoot()` needs them too), and restore both
in `endSession()` instead of hardcoding `FILTER_ALL`. Worth a line in
`specs/pause-session-core/design.md` once done.

### 1.3 Date formatting breaks on a runtime locale change — **S** — ✅ fixed

*Verified by lint (`ConstantLocale`), `HistoryScreen.kt:67`. Fixed by
reading `LocalLocale.current.platformLocale` (a `CompositionLocal`, so
actually observable by recomposition) instead of `Locale.getDefault()` —
lint caught a first attempt too (`NonObservableLocale`) and pointed at the
right fix. See `specs/session-history-and-stats/design.md`.*

`Locale.getDefault()` is captured into a top-level/static formatter, so
dates keep rendering in the old locale if the user switches language while
the app is alive. Given this project ships two locales and actively courts
translators, it will be seen.

### 1.4 Room migrations are hand-written, unexported, and untested — **M** — ✅ fixed

*Verified: `CalmOtterDatabase.kt:11` has `exportSchema = false`, two
hand-written migrations, and no `androidTest`. Now `exportSchema = true`
(protects future migrations; versions 1/2 predate this and can't be
retrofitted), and both migrations are tested directly against a
hand-seeded v1 database opened through the real production
`Room.databaseBuilder()` call — no `androidTest` needed, this runs under
Robolectric. See `specs/session-history-and-stats/design.md`.*

Session history is the only irreplaceable data this app holds — there is no
backend and no cloud copy. A missing or wrong migration is a crash loop on
launch for everyone who upgrades (exactly the failure mode already seen
with the encrypted prefs, see `specs/onboarding-and-password/design.md`).
Today nothing verifies v1→v2→v3 actually works on a populated database.

**Fix:** set `exportSchema = true`, commit `app/schemas/`, and add
`MigrationTestHelper` tests. This requires instrumented tests, which do not
exist yet — see 2.2.

---

## 2. Tests

Current state: 11 test files, ~990 lines, against ~12,800 lines of main
source. What is tested is tested well (pure logic: `LockoutPolicy`,
`SessionStreak`, `CalmCountdown`, `GroupPauseRecipe`, the Bluetooth
protocol). The gaps are structural.

### 2.1 `PasswordManager` has no test at all — **M** — ✅ fixed

The most security-critical class in the app is the only state holder with
zero coverage — including the self-healing recovery path added after the
`AEADBadTagException` crash, which is precisely the code that must not
regress. `LockoutPolicy` is tested, but the wiring around it is not.

*Fixed:* Robolectric has no `AndroidKeyStore` (hardware-backed, out of its
scope), so `PasswordManagerTest` ships a minimal fake JCA provider
(`FakeAndroidKeyStore.kt`) — a `KeyStoreSpi` + `KeyGeneratorSpi` backed by an
in-memory map, enough for `MasterKey`/`EncryptedSharedPreferences`'s
`AES256_GCM` scheme. 9 cases: round-trip, wrong password, partner name never
overwritten by a blank one, lockout persisting across a fresh singleton
instance, and — the one this class exists to protect — a corrupted Tink
keyset (same two prefs keys a backup-restore corrupts) self-healing instead
of crashing. See `specs/onboarding-and-password/design.md`.

### 2.2 No instrumented or Compose UI tests exist — **L** — partially addressed

`app/src/androidTest/` is empty. The app is 100% Compose, and every UI
defect fixed recently (CTA anchoring, button order, vertical alignment,
missing mascot) was caught the same way: build, install, `uiautomator
dump`, screenshot, look at it. That loop is slow and catches only what
someone thought to look at.

A modest `createComposeRule` suite would lock in the invariants that keep
being re-broken by hand:

- Every screen's primary CTA is the last button in its column.
- CTA buttons are 48dp and full width.
- Every top-level screen root applies `safeDrawingPadding()`.

That last one is currently enforced only by a paragraph in `CLAUDE.md` and
by remembering. ~~Also unblocks 1.4.~~ (1.4 was fixed a different way —
Room's own runtime schema validation, not this suite — so that
cross-reference no longer applies.)

*Partially addressed, deliberately not exhaustively:* `app/src/androidTest/`
is still empty — everything below runs under Robolectric in
`app/src/test/`, matching `OtterAnchoredScreenTest`'s existing precedent of
testing shared building blocks/hand-picked screens rather than composing
every real screen (which would pull in Room/Keystore/permissions per
screen, same reasoning as that file's own comment).

- **`safeDrawingPadding()` on every top-level screen** — done exhaustively,
  all 10 `setContent{}` entry points, but as a static source-text check
  (`TopLevelScreenSafeDrawingPaddingTest`), not a runtime Compose test:
  the modifier leaves no trace in the semantics tree a `createComposeRule`
  test could query, and simulating real window insets under Robolectric
  to verify it structurally isn't worth the fragility.
- **CTA 48dp/full-width + primary-CTA-last** — done for two screens
  (`CtaButtonInvariantsTest`): `ChangePasswordScreen` (safe to compose —
  takes `PasswordManager` as a parameter rather than reading a singleton)
  and `GroupPauseCodeEntryScreen`'s manual-entry mode (protects the
  Cancel/Scan order fixed earlier this same session). Not every screen —
  see the file's own comment for why that's "modest" rather than
  exhaustive. Verified these two tests actually catch a regression, not
  just pass tautologically: temporarily re-broke the Cancel/Scan order,
  confirmed the test failed, reverted.

### 2.3 Nothing enforces the two-palette sync rule — **S** — ✅ fixed

`CLAUDE.md` states the invariant plainly: `values/colors.xml` +
`values/themes.xml` and `ui/theme/CalmOtterTheme.kt` define the same three
palettes twice and must be mirrored by hand. There are 66 `<color>` entries
on one side and 76 `Color(...)` literals on the other, with no check
between them. Drift here has already caused one real bug
(`specs/mascot-marks/design.md`).

A Robolectric test that resolves each `@color/*` and asserts it equals the
corresponding `ColorScheme` role would make the rule self-enforcing instead
of a thing to remember. Cheap, and permanently useful.

*Fixed, and it paid for itself immediately:* `CalmOtterThemeColorSyncTest`
failed on its very first run — `DuskSandLight`/`DawnClayLight` in
`CalmOtterTheme.kt` were still carrying old Lavender/Terracotta hex values
for `surface`/`primary`/`onSurface`/`onPrimary` (the rename had only
updated `secondary`/`tertiary`). Both palettes were rendering a color
scheme that never fully existed in either the old or new design. Fixed and
verified visually on the emulator. See
`specs/multi-theme-system/design.md`.

---

## 3. Performance

### 3.1 PBKDF2 (120k iterations) runs on the main thread at unlock — **S** — ✅ fixed

*Verified as synchronous; **not measured**, so measure before deciding.*

`PasswordManager.verify()` is called straight from button handlers:
`PasswordVerifyDialog.kt:112` and `ChangePasswordScreen.kt:145`. 120,000
PBKDF2-HMAC-SHA256 iterations is deliberately expensive — that is the
point — but it is being paid on the UI thread at the single most sensitive
moment in the product: another person, holding someone else's phone,
typing a password in front of them. A visible freeze there reads as "the
app broke," not "the app is being careful."

Measure it on the S22 and on the oldest realistic device first. If it is
tens of milliseconds, leave it and write down the number. If it is hundreds,
move it into a coroutine with a spinner.

*Measured, then fixed:* ~167ms per call on the emulator (temporary
instrumentation, removed before committing) — the S22 wasn't available
(locked, no one present to unlock it; this codebase has an established
rule against touching a locked personal device). The emulator number is
likely optimistic (native ARM64 on Apple Silicon, probably faster than
"the oldest realistic device"), and 167ms already clears the "hundreds"
threshold on its own. Moved `verify()`/`setPassword()` in both call sites
onto `Dispatchers.Default` via a launched coroutine, with a
`CircularProgressIndicator` replacing the button label and inputs disabled
while in flight. Verified live on the emulator: caught the spinner
mid-operation in a screenshot, confirmed both the change-password and
verify-password paths complete correctly afterward with the new password.
See `specs/onboarding-and-password/design.md`.

### 3.2 Four autoboxing state creations — **S** — ✅ fixed

Lint `AutoboxingStateCreation`: `BlockScreen.kt:122`, `MainScreen.kt:230`
and `:231` want `mutableLongStateOf`; `HistoryScreen.kt:670` wants
`mutableIntStateOf`. Two of them are in per-frame countdown paths, which is
where it actually matters slightly. Mechanical fix.

*Fixed:* all four swapped (`mutableLongStateOf` ×3, `mutableIntStateOf` ×1).
Confirmed gone from the lint report.

### 3.3 `allowMainThreadQueries()` is justified but unbounded — **S**

The reasoning in `CalmOtterDatabase.kt` is sound today (tiny dataset,
synchronous write from a `BroadcastReceiver`). It stops being sound at some
number of rows nobody has defined. History has no pagination and
`getAll()` returns everything. Worth either writing down the bound or
capping/paginating the query.

---

## 4. Accessibility

This is the weakest area relative to the rest of the project's care. Nine
`semantics {}` usages in the whole app.

### 4.1 The widget is unusable with TalkBack — **S** — ✅ fixed

*Verified by lint.* `widget_pause.xml:31` and `:55` are images with no
`contentDescription`, and `:40` and `:64` use 10sp text (below the 11sp
floor lint enforces, and well below comfortable). The widget is a primary
entry point for starting a pause.

*Fixed:* `android:contentDescription="@null"` on both images (decorative —
matches the pattern the real Glance widget already used correctly), both
`TextView`s bumped to `11sp`. Confirmed gone from the lint report. See
`specs/home-screen-widget/design.md`.

### 4.2 Canvas-drawn content is invisible to screen readers — **M** — partially addressed

Nine files draw with `Canvas`, and the drawings carry meaning:
`WeeklyChart.kt` *is* the weekly statistics; `ParticipantRing` says who has
joined the lobby; `MainScreen`'s otter is the main tap target of the app.
To TalkBack these are blank areas.

*Verified:* `WeeklyChart.kt` and `OtterMarks.kt` contain no
`contentDescription` and no `semantics {}` at all, and the only
`contentDescription` in the whole of `MainScreen.kt` is on the settings
icon (`:294`) — meaning "Tap Otter to start", the primary action of the
app, is currently unlabelled.

*Fixed, the two cheap wins named above:* the otter tap target now carries
`contentDescription` = the plain-text version of `home_start_hint` (only
while actually tappable), and `WeeklyChart` takes an
`accessibilityLabel` now wired to the same `summaryText` already rendered
below it, not a new phrase. Both verified end-to-end via `uiautomator
dump`'s `content-desc` output after a full force-stop-and-relaunch (an
`am start` on an already-resumed Activity does not pick up a freshly
installed build — caught the hard way, see
`specs/home-and-settings/design.md`). The real fix landed in
`PersistentOtter.kt`, not `MainScreen.kt`'s `PondOtter` — same writeup for
why. `ParticipantRing` (group-pause lobby) is untouched — still open,
lower traffic than the two fixed here.

---

## 5. Internationalisation

### 5.1 Fourteen strings should be `<plurals>` — **M** — ✅ fixed

*Verified by lint (`PluralsCandidate`), both locales.* Counts are
interpolated into fixed noun forms: `%d secondi`, `%d sessioni`, `%d
giorni`, `%d minuti`, `%d app`, `%d persone`, and their English
counterparts. The visible symptom is "1 giorni" / "1 sessions".

This matters more here than in most apps: `CONTRIBUTING.md` invites
translators into languages whose plural rules are not Italian's or
English's (Polish, Russian, Arabic have three to six forms). Fixing it
before more locales arrive is far cheaper than after, because every
translation file would otherwise need reworking.

*Fixed:* all 7 strings (14 findings = 7 strings × 2 locales) converted to
`<plurals>` — `password_locked_out`, `weekly_summary_one`/`_many` (merged
into one `weekly_summary_sessions`), `streak_days`,
`weekly_goal_progress_sessions`/`_minutes` (quantity on the *target*, not
the current count — the one place in this batch where the "obvious" arg
choice would've been wrong), `allowed_apps_limit_reached`,
`group_pause_lobby_with_many` (quantity="one" is unreachable given how
it's called today, kept anyway — lint's `MissingQuantity` requires it, and
a future call site could change that guarantee). `CONTRIBUTING.md` now
tells translators to add whichever quantities their language needs, not
just copy English's two. Verified live on the emulator for the three
call sites reachable without lengthy setup (weekly summary, streak, goal
progress) by seeding history directly via SQLite; the other two
(`password_locked_out`'s live countdown, `allowed_apps_limit_reached`'s
compile-time constant) verified by lint + the same code pattern rather
than re-triggered live. See `specs/session-history-and-stats/design.md`,
`specs/onboarding-and-password/design.md`,
`specs/app-blocking-and-home-lock/design.md`, `specs/group-pause/design.md`.

### 5.2 Eight unused strings are being handed to translators — **S** — ✅ fixed

*Verified by lint (`UnusedResources`).* `settings_theme_selected`,
`setup_password_info`, `save_password`, `password_saved`,
`session_active_with_time`, `remaining_time_format`,
`group_pause_chooser_intro`, `group_pause_join_manual_tab` are dead, but
`CONTRIBUTING.md` tells contributors to "translate every `<string>`". That
is volunteer effort spent on strings nothing renders. Delete them from both
locales.

*Fixed:* all 8 removed from both `values/strings.xml` and
`values-en/strings.xml`, confirmed gone from `R.string.*` usage first.
Side effect noticed in the same lint pass: the eight `*_accent`/`*_veil`
color resources originally flagged unused in the initial review (section
7, "Eighteen unused resources") are no longer flagged — not because
anything started rendering them, but because `CalmOtterThemeColorSyncTest`
(fixed under "2.3") now reads them via `ContextCompat.getColor()`, and
lint's unused-resource scan includes test sources. A real reference, if a
narrow one; the housekeeping bullet below is updated to reflect it.

---

## 6. Dependencies

### 6.1 `androidx.security:security-crypto` is on an alpha — **S** — ✅ fixed

`1.1.0-alpha06` is pinned while **stable `1.1.0` is available**. This is
the library behind the `EncryptedSharedPreferences` crash just fixed, which
makes it the one upgrade worth doing deliberately rather than in a batch:
read its changelog, then upgrade on its own commit so any fallout is
attributable.

*Fixed:* bumped to `1.1.0`, no API changes needed in `PasswordManager.kt`
(though `MasterKey`/`EncryptedSharedPreferences` are now `@Deprecated` as
of this version — noted, not acted on; a real migration is separate,
larger work). Verified beyond build/lint/test: all 9
`PasswordManagerTest` cases still pass, and — since this is exactly the
library behind a real crash already fixed once — exercised live on the
emulator too: changed the password on the app's existing (old-library)
encrypted store, both the read of the old value and the write of the new
one succeeded with no exception. See
`specs/onboarding-and-password/design.md`.

### 6.2 The rest of the dependency drift — **M** — ✅ fixed

Twelve `GradleDependency` warnings. Notable: `core-ktx` 1.13.1 → 1.19.0,
`lifecycle-runtime-ktx` 2.8.3 → 2.11.0, `activity-compose` 1.9.0 → 1.13.0,
CameraX 1.4.1 → 1.6.2 (four artifacts), `kotlinx-coroutines-android` 1.7.3
→ 1.11.0, Gradle 9.7.0 → 9.7.1.

Worth noting the contrast: the Compose BOM and Kotlin/AGP toolchain are
deliberately kept on the bleeding edge (documented in `CLAUDE.md`), while
these have quietly fallen years behind. Not urgent, but the inconsistency
is unintentional rather than chosen.

*Fixed:* all twelve bumped (`appcompat` 1.7.0 → 1.8.0, `material` 1.12.0 →
1.14.0, `recyclerview` 1.3.2 → 1.4.0, and `androidx.test:core` 1.6.1 →
1.7.0 in addition to the ones named above), plus the two
`NewerVersionAvailable` findings (`zxing:core` 3.5.3 → 3.5.4) and the
Gradle wrapper itself (9.7.0 → 9.7.1) — all fifteen `GradleDependency`/
`NewerVersionAvailable`/`AndroidGradlePluginVersion` findings confirmed
gone from the lint report afterward.

One real breakage, not a routine version bump: `material:material` 1.14.0
stopped exposing `com.google.android.material.R.attr.colorPrimary`
(`MainActivity.kt`'s status-bar-color call), failing the Kotlin compile
outright rather than a runtime surprise. Fixed by switching to
`android.R.attr.colorPrimary` — the platform's own attribute since API 21,
which this project's `minSdk 26` has always satisfied. See
`specs/multi-theme-system/design.md`.

Verified: full `assembleDebug`/`lintStableDebug`/`lintBetaDebug`/
`testStableDebugUnitTest`/`testBetaDebugUnitTest` pass (all 16 test
suites, zero failures). Installed on the emulator and exercised the
libraries most likely to break silently rather than trusting the compile
alone: a full Home → start pause → unlock round trip (core-ktx,
lifecycle-runtime-ktx, activity-compose, the fixed `colorPrimary` path —
all run on every one of those transitions), and the group-pause QR
scanner (CameraX, bumped two minor versions across four artifacts) —
granted the camera permission live and confirmed the preview actually
renders a frame, not just that the screen opens. No crash, no exception
in `adb logcat`, across either.

---

## 7. Housekeeping

- ~~`ThemeManager.accentColor()` is dead code with the same rename bug
  2.3 just fixed elsewhere~~ — ✅ deleted. It was called from nowhere in
  the app, so there was nothing to fold into `CalmOtterThemeColorSyncTest`
  either — dead code, not a bug worth keeping around to fix. **S**
- ~~Eighteen unused resources~~ — the eight strings are gone (✅ "5.2");
  the eight `*_accent`/`*_veil` colors are no longer flagged either, but
  only because a test now reads them (see "5.2"'s note), not because
  anything in the app does — worth revisiting if that test ever changes.
  The two `ic_launcher_*_still_otter` drawables — ✅ deleted (confirmed
  only referenced from a comment, not `@drawable/...`, before removing;
  the two comments pointing at them updated too). **S**
- ~~`screenshot/` is untracked and un-ignored~~ — ✅ resolved: added to
  `.gitignore` (used only for manual verification, not meant to be
  committed). **S**
- ~~`mipmap-anydpi-v26` is redundant~~ — ✅ renamed to `mipmap-anydpi`.
  Caught a real Gradle incremental-build gap doing this: a plain rebuild
  after the `git mv` failed resource linking (`AAPT: error: resource
  mipmap/ic_launcher not found`) because stale merged-resource
  intermediates from the old path name weren't invalidated; `./gradlew
  clean` before rebuilding fixed it. Not a lasting issue (CI always
  builds clean), but worth knowing if a local incremental build ever
  does something similar after a resource-directory rename. **S**
- ~~`MainScreen.kt` is 1,139 lines~~ — ✅ split into three files along its
  existing natural seams: `HomePond.kt` (the pond scene — `PondOtter`,
  ripples, ring; three of these are also shared with `BlockScreen.kt`),
  `HomeSummary.kt` (the weekly stats row, Home-only), and `MainScreen.kt`
  itself trimmed to the screen entry point + permission dialog. Same
  package throughout, so no import changed anywhere else in the project —
  only a handful of `private` → `internal` visibility bumps for the
  functions now called from a sibling file. See
  `specs/home-and-settings/design.md`. **M**
- ~~`verify()` compares hashes with `contentEquals`~~ — ✅ swapped to
  `MessageDigest.isEqual()`. **S**
- ~~Lint reports five `Typos` for "momento"~~ — ✅ suppressed via
  `app/lint.xml`, scoped to `values/strings.xml` only (not globally —
  `values-en/strings.xml` and the rest of the codebase stay covered).
  **S**

---

## Suggested order

1. **1.1** and **1.2** — the two real bugs, both user-visible, both small.
2. **2.3** and **2.1** — cheap tests that lock down rules currently held
   only by memory.
3. **5.1** — before more translations land, not after.
4. **4.1** — smallest accessibility win with the widest reach.
5. **6.1** — one deliberate upgrade of the library that already bit once.
6. Everything else as it fits.
