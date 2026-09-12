# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Calm Otter is a native Android app (Kotlin) that helps reduce phone usage. The
user (or, by design, an "accountability partner" who alone knows the unlock
password) starts a pause session; for its duration every app except Phone is
blocked and notifications are silenced, and only the correct password ends it
early. Everything is local — no backend, no network calls, no analytics.

The lock is intentionally "soft" (see README.md "Known Limits" section): it
relies on an AccessibilityService and Do Not Disturb, not on Device Owner /
MDM provisioning. Do not describe it as tamper-proof in code, comments, or UI
copy — the honesty of that framing is part of the project's stated values
(see CONTRIBUTING.md).

## Commands

```bash
./gradlew assembleDebug              # build debug APK
./gradlew testDebugUnitTest          # run all unit tests (Robolectric)
./gradlew test --tests "*.SessionManagerTest"                    # single test class
./gradlew test --tests "*.SessionManagerTest.methodName"         # single test method
./gradlew lintDebug                  # Android Lint (must be 0 errors — CI enforces this)
./gradlew assembleRelease            # signed release build; needs KEYSTORE_PATH/
                                      # KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD env vars
```

CI (`.github/workflows/ci.yml`) runs `lint`, `testDebugUnitTest`, and
`assembleDebug` on every branch/PR — treat lint errors and test failures as
build-breaking. `.github/workflows/android.yml` produces a signed release APK
from a secrets-backed keystore on push to main/master.

Toolchain: Kotlin 2.4.20, AGP 9.4.0, Gradle 9.7.0, compileSdk/targetSdk 37
(`compileSdkMinor = 1`, i.e. platform 37.1), KSP 2.3.12 (its versioning is
decoupled from Kotlin's as of the 2.3.x line — no exact-match requirement
like older KSP). The Compose Compiler is configured via the
`org.jetbrains.kotlin.plugin.compose` Gradle plugin (required since Kotlin
2.0+; there's no `composeOptions.kotlinCompilerExtensionVersion` to set).
Compose comes from `androidx.compose:compose-bom-alpha:2026.09.00` —
**an alpha channel BOM, not the stable one, on purpose**: stable `material3`
(1.4.0) has `MaterialExpressiveTheme` and `MotionScheme.expressive()`/
`standard()` marked Kotlin-`internal` (verified by decompiling the actual
jar), so Material 3 Expressive isn't reachable from app code until 1.5.0,
still alpha. Robolectric is pinned to run its own SDK shadow at 35
(`app/src/test/resources/robolectric.properties`) rather than the real
targetSdk 37, because Robolectric's SDK-36+ shadows require Java 21 and
this project builds with Java 17.

## Architecture

**UI is 100% Jetpack Compose.** Every screen is an `Activity` (in
`com.calmotter.app`, flat package) that calls `setContent { }` with a
Composable from `com.calmotter.app.ui.screens`; there is no XML layout left
for app screens (`viewBinding = false`). The one exception is the home-screen
widget (`PauseWidgetProvider` / `PauseGlanceWidget`), built with **Jetpack
Glance**, not vanilla Compose — the `AppWidgetProviderInfo` XML
(`widget_pause_info.xml`) still points to a plain XML `initialLayout`
(`widget_pause.xml`), which is an Android platform requirement, not legacy
code; don't "clean it up".

`CalmOtterTheme` wraps content in `MaterialExpressiveTheme` (Material 3
Expressive — spring-based motion, expressive shapes/typography by default).
See the toolchain paragraph above for why that requires an alpha Compose
BOM, and `specs/multi-theme-system/design.md` for which `MaterialTheme`
color roles are actually customized per palette — `surfaceVariant` and
`primaryContainer` are **not** (they silently fall back to M3's stock
default, ignoring Sage/Lavender/Terracotta entirely); this has already
caused one real bug, see `specs/mascot-marks/design.md`.

**Every screen's root layout applies `Modifier.safeDrawingPadding()`.**
targetSdk 37 means edge-to-edge is enforced (unconditionally, cannot be
opted out of) on Android 15+ devices — without this, content renders
under the status bar/notch/nav bar on real hardware, though this is
invisible on any emulator running Android ≤14. Any new top-level screen
composable needs this modifier on its root too.

**No DI framework.** State-holding classes (`SessionManager`,
`PasswordManager`, `SessionHistoryManager`, `AllowedAppsManager`,
`LauncherManager`, `PhraseManager`, `WeeklyGoalManager`) are thread-safe
singletons obtained via `ClassName.getInstance(context.applicationContext)`
(double-checked locking, `@Volatile` instance). Each one also exposes an
internal `resetInstanceForTests()` (`@VisibleForTesting`) — **Robolectric
tests must call it in `@Before` for every singleton the test path touches**,
including transitively (e.g. testing `SessionManager` also requires
resetting `SessionHistoryManager` and `CalmOtterDatabase`, since
`endSession()` writes through to Room). Forgetting this leaks state between
tests via the singleton instance.

**Persistence is split by sensitivity:**
- `PasswordManager` — password hash only (PBKDF2-HMAC-SHA256, 120k
  iterations, random salt) inside `EncryptedSharedPreferences`, key-managed
  by Android Keystore (AES256). The plaintext password is never stored.
- `CalmOtterDatabase` (Room, single entity `SessionRecord`) — session
  history, read by `SessionHistoryManager`. Uses `allowMainThreadQueries()`
  deliberately: the dataset is tiny and `SessionManager.endSession()` writes
  synchronously from a `BroadcastReceiver` with no coroutine scope available.
- Plain `SharedPreferences` — non-sensitive local state: selected theme
  (`ThemeManager`), last widget duration, allowed-apps whitelist, saved
  original launcher.

**Core block/session flow** (see also README.md "How it works"):
`SessionManager.startSession()` sets DND (`NotificationManager.Policy`, calls
change depending on `Build.VERSION.SDK_INT`), schedules an inexact
`AlarmManager` auto-expiry (`SessionExpiryReceiver`), starts
`SessionForegroundService` (keeps the process/DND alive), and notifies the
Glance widget. `AppBlockerAccessibilityService` watches foreground-app
changes and launches `BlockOverlayActivity` for anything not on the allowlist
(`AllowedAppsManager`) while a session is active. `HomeActivity` intercepts
the Home button when Calm Otter is set as the default launcher
(`LauncherManager` remembers the user's real launcher to forward to when no
session is active): active session → same `BlockScreen` Composable as
`BlockOverlayActivity`; no session → immediate forward-and-finish. `BootReceiver`
re-applies DND/alarm/service after reboot if a session was in progress.

**Theming** has two parallel systems that must be kept in sync manually:
`values/colors.xml` + `values/themes.xml` define three XML palettes (Sage,
Lavender, Terracotta) × three variants (Base/Block/WithActionBar), applied by
`ThemeManager.applyTheme()` in `BaseActivity.onCreate()` *before*
`super.onCreate()` (needed for correct window chrome/status bar before
Compose ever renders); `ui/theme/CalmOtterTheme.kt` independently hardcodes
matching Material3 `ColorScheme`s for Compose content and does not read
`@color/*` resources. If you touch one palette, mirror the change in the
other file's `Color(...)` literals.

## Specs

`specs/` holds spec-shaped documentation: one `requirements.md` +
`design.md` pair per feature area, describing what's already implemented
(as-built, not a to-do list). Read the relevant pair before making
non-trivial changes to that area — see `specs/README.md` for the index and
for the convention to follow when adding specs for new features.

## Conventions

- Comments and identifiers in the codebase are predominantly Italian
  (`values/strings.xml` is the Italian base locale; `values-en/` is English).
  Match existing language per-file rather than mixing.
- No external network calls, no analytics/telemetry — this is a hard
  constraint from CONTRIBUTING.md's contribution policy, not an oversight.
  The one intentional exception: Settings' "Info" section (see
  `specs/home-and-settings/design.md`) opens GitHub/license links in the
  device's browser via `ACTION_VIEW` — that's the user leaving the app
  through an explicit tap, not the app itself calling out or transmitting
  anything, so it doesn't conflict with this constraint.
- New UI strings need entries in both `values/strings.xml` and
  `values-en/strings.xml`; `values-en/` also doubles as the reference file
  translators copy for new locales (see CONTRIBUTING.md for the full i18n
  workflow).
