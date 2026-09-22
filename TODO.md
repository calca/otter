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

### 3.1 PBKDF2 (120k iterations) runs on the main thread at unlock — **S**

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

### 3.2 Four autoboxing state creations — **S**

Lint `AutoboxingStateCreation`: `BlockScreen.kt:122`, `MainScreen.kt:230`
and `:231` want `mutableLongStateOf`; `HistoryScreen.kt:670` wants
`mutableIntStateOf`. Two of them are in per-frame countdown paths, which is
where it actually matters slightly. Mechanical fix.

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

### 4.1 The widget is unusable with TalkBack — **S**

*Verified by lint.* `widget_pause.xml:31` and `:55` are images with no
`contentDescription`, and `:40` and `:64` use 10sp text (below the 11sp
floor lint enforces, and well below comfortable). The widget is a primary
entry point for starting a pause.

### 4.2 Canvas-drawn content is invisible to screen readers — **M**

Nine files draw with `Canvas`, and the drawings carry meaning:
`WeeklyChart.kt` *is* the weekly statistics; `ParticipantRing` says who has
joined the lobby; `MainScreen`'s otter is the main tap target of the app.
To TalkBack these are blank areas.

*Verified:* `WeeklyChart.kt` and `OtterMarks.kt` contain no
`contentDescription` and no `semantics {}` at all, and the only
`contentDescription` in the whole of `MainScreen.kt` is on the settings
icon (`:294`) — meaning "Tap Otter to start", the primary action of the
app, is currently unlabelled.

Two cheap wins: give the otter tap target a label, and give `WeeklyChart` a
`contentDescription` summarising the week ("4 pauses, 2 h 10 min") so the
data is reachable at all.

---

## 5. Internationalisation

### 5.1 Fourteen strings should be `<plurals>` — **M**

*Verified by lint (`PluralsCandidate`), both locales.* Counts are
interpolated into fixed noun forms: `%d secondi`, `%d sessioni`, `%d
giorni`, `%d minuti`, `%d app`, `%d persone`, and their English
counterparts. The visible symptom is "1 giorni" / "1 sessions".

This matters more here than in most apps: `CONTRIBUTING.md` invites
translators into languages whose plural rules are not Italian's or
English's (Polish, Russian, Arabic have three to six forms). Fixing it
before more locales arrive is far cheaper than after, because every
translation file would otherwise need reworking.

### 5.2 Eight unused strings are being handed to translators — **S**

*Verified by lint (`UnusedResources`).* `settings_theme_selected`,
`setup_password_info`, `save_password`, `password_saved`,
`session_active_with_time`, `remaining_time_format`,
`group_pause_chooser_intro`, `group_pause_join_manual_tab` are dead, but
`CONTRIBUTING.md` tells contributors to "translate every `<string>`". That
is volunteer effort spent on strings nothing renders. Delete them from both
locales.

---

## 6. Dependencies

### 6.1 `androidx.security:security-crypto` is on an alpha — **S**

`1.1.0-alpha06` is pinned while **stable `1.1.0` is available**. This is
the library behind the `EncryptedSharedPreferences` crash just fixed, which
makes it the one upgrade worth doing deliberately rather than in a batch:
read its changelog, then upgrade on its own commit so any fallout is
attributable.

### 6.2 The rest of the dependency drift — **M**

Twelve `GradleDependency` warnings. Notable: `core-ktx` 1.13.1 → 1.19.0,
`lifecycle-runtime-ktx` 2.8.3 → 2.11.0, `activity-compose` 1.9.0 → 1.13.0,
CameraX 1.4.1 → 1.6.2 (four artifacts), `kotlinx-coroutines-android` 1.7.3
→ 1.11.0, Gradle 9.7.0 → 9.7.1.

Worth noting the contrast: the Compose BOM and Kotlin/AGP toolchain are
deliberately kept on the bleeding edge (documented in `CLAUDE.md`), while
these have quietly fallen years behind. Not urgent, but the inconsistency
is unintentional rather than chosen.

---

## 7. Housekeeping

- **Eighteen unused resources** — the eight strings above, eight
  `*_accent`/`*_veil` colors, and two `ic_launcher_*_still_otter`
  drawables. **S**
- **`screenshot/` is untracked and un-ignored** — 24 PNGs that show up in
  every `git status`. Either commit them (they are useful for the README
  and a store listing) or add them to `.gitignore`. Right now they are in
  limbo. **S**
- **`mipmap-anydpi-v26` is redundant** — `minSdk` is 26, so the `-v26`
  qualifier does nothing (lint `ObsoleteSdkInt`). **S**
- **`MainScreen.kt` is 1,139 lines** — the largest file in the project
  (next is `HistoryScreen.kt` at 821). No specific defect found in it;
  flagged only because size eventually becomes its own problem. **M**
- **`verify()` compares hashes with `contentEquals`**
  (`PasswordManager.kt:106`) — not constant-time. The timing channel is
  close to theoretical here (the attacker holds the device, and PBKDF2
  dominates the measurement), but `MessageDigest.isEqual()` is a one-line
  swap and removes the question entirely. **S**
- **Lint reports five `Typos` for "momento"** — false positives on Italian
  text. If they are noise on every run, suppress the check for
  `values/strings.xml` rather than living with them. **S**

---

## Suggested order

1. **1.1** and **1.2** — the two real bugs, both user-visible, both small.
2. **2.3** and **2.1** — cheap tests that lock down rules currently held
   only by memory.
3. **5.1** — before more translations land, not after.
4. **4.1** — smallest accessibility win with the widest reach.
5. **6.1** — one deliberate upgrade of the library that already bit once.
6. Everything else as it fits.
