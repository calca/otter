# App Blocking & Home Lock — Design

## Key files

| File | Role |
|---|---|
| `AppBlockerAccessibilityService.kt` | Watches `TYPE_WINDOW_STATE_CHANGED` events; launches `BlockOverlayActivity` for non-exempt foreground apps |
| `AllowedAppsManager.kt` | Plain-`SharedPreferences` set of extra allowed package names |
| `AllowedAppsActivity.kt` / `ui/screens/AllowedAppsScreen.kt` | Password-gated editor; loads all launcher-intent activities off the main thread |
| `BlockOverlayActivity.kt` | Hosts `BlockScreen` for the "blocked app opened" case |
| `MainActivity.kt` | Also hosts `BlockScreen`, both for "Home button pressed during an active session" and "launcher icon opened (or a session starts) during an active session" (see "One Activity, two roles" below) — a separate `HomeActivity` class used to own the Home case, merged into `MainActivity` on request |
| `AllowedAppLaunchItems.kt` | `loadAllowedAppLaunchItems()`/`launchAllowedApp()`, shared by `MainActivity` and `BlockOverlayActivity` so both pass the same allowed-apps row to `BlockScreen` |
| `LauncherManager.kt` | Remembers the device's pre-CalmOtter default launcher package |
| `ui/screens/BlockScreen.kt` | Shared Composable for all three block entry points, byte-for-byte identical in every case (see "Three block screens, one screen" below) |

## Blocking decision (`AppBlockerAccessibilityService.allowedPackages()`)

Always-exempt set, recomputed per event (not cached):
`TelecomManager.defaultDialerPackage`, `com.android.systemui`, `android`,
Calm Otter's own package, every **enabled keyboard**
(`InputMethodManager.enabledInputMethodList`) — unioned with
`AllowedAppsManager.getAllowedPackages()`. If `sessionManager.isSessionActive()`
is false, the service returns immediately without even computing the exempt
set (cheap common case).

**Only full-screen windows count.** Before any of the package checks, the
service drops events whose `isFullScreen` is false. A window opening *over*
the current screen is not the user switching app — the screen underneath has
already been judged. This is what finally killed a bug reported twice: with
the keyboard allowlist in place, tapping the password field on a Galaxy S22
*still* flashed the block screen, because the trigger was not the keyboard at
all but `com.google.android.ext.services`, the system suggestion service,
opening its own window over the unlock dialog. Measured over wireless ADB on
the device itself: a real app switch (launcher, Settings, Clock) always
arrives with `isFullScreen = true`, while popups, system bars and this app's
own windows arrive with `false`; the same holds on an API 37 emulator.

That also fixes a case the reports had not reached yet: the same popups
appearing inside an **allowed** app during a pause were being blocked too —
the worst way this feature can fail, covering the screen while you use
something you are permitted to use.

The price, consistent with a lock that is openly soft (README, "Known
Limits"): an activity with a dialog theme would not be blocked. Note the
order matters — the keyboard allowlist still does real work after this
filter, because an IME window reports `isFullScreen = true` (verified on the
S22), so the filter alone would not cover it.

**Why keyboards are in there.** Opening the keyboard fires a
`TYPE_WINDOW_STATE_CHANGED` carrying the IME's package, which without this
fell straight into the "not an allowed app" branch. Reported symptom: during
a session, opening the unlock dialog and starting to type made the block
screen appear on top of it — sometimes for a moment, sometimes closing the
dialog outright, which made ending the pause with the password impossible
from Home. Reproduced on the emulator with `dumpsys activity activities`
showing `BlockOverlayActivity` taking over while the password field had
focus; with the fix, `MainActivity` stays on top through the whole typing.

A keyboard is not an app coming to the foreground: it is a window opening
*over* whatever is already there, and that one has already been judged. The
whole enabled list rather than just `DEFAULT_INPUT_METHOD`, since switching
keyboard mid-typing is ordinary and the new one must not trip the block.
This does not weaken anything meaningfully — the lock is openly "soft"
(README, "Known Limits"), and these are keyboards the user has already
enabled in system settings.

## The XML config alone did not subscribe the service to any event

Blocking did not work *at all* — the reported symptom was "the overlay
never shows on other apps, even with every permission granted", which is
this feature's entire point. It was not a permissions problem, not a
background-activity-launch problem, and not an allow-list problem:
**the service was being bound subscribed to zero event types**, so the
system never delivered it a single `TYPE_WINDOW_STATE_CHANGED`.

How it was pinned down, on an API 37 emulator with the service enabled
through the real Settings UI:

- `dumpsys accessibility` showed the bound service as
  `Service[label=Calm Otter Beta, feedbackType[], capabilities=0,
  eventTypes=, notificationTimeout=0]` — every field empty.
- Temporary logging in `onServiceConnected()` confirmed it from the app
  side too: `getServiceInfo().eventTypes == 0`.
- The APK itself was fine: `aapt2 dump xmltree` on
  `res/xml/accessibility_service_config.xml` showed
  `accessibilityEventTypes=0x20` (`typeWindowStateChanged`),
  `accessibilityFeedbackType=0x10`, `notificationTimeout=100`. So the
  declared config shipped correctly and simply was not applied to the
  running service.

The fix is to re-assert the info at runtime in `onServiceConnected()`
(`serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply { ... }`)
rather than trusting the manifest meta-data to have been honoured. It
starts from the existing info when there is one, so anything the system
*did* populate is kept. The XML config stays: it is still what makes the
service appear in Settings, with label and description, before the user
ever turns it on.

After the fix the same `dumpsys` line reads `feedbackType[FEEDBACK_GENERIC],
eventTypes=TYPE_WINDOW_STATE_CHANGED, notificationTimeout=100`, and
opening a non-allowed app during a session produces
`ActivityTaskManager: START ... BlockOverlayActivity ... (BAL_ALLOW_TOKEN)
result code=0` — worth noting that background activity launch was never
the blocker here: an accessibility service is allowed one by token.

**Verified on the recording, not by eye**: `screenrecord` during the
transition, frames extracted at 5fps, shows the bridge overlay (see below)
covering the screen ~1.2s after the blocked app was launched and the block
screen in place by ~1.8s, with no frame in between where the blocked app's
own content is visible.

## The bridge overlay shows the otter, not a black rectangle

`BlockOverlayActivity` takes ~0.6s to be up and drawn after the
accessibility event arrives (measured: the activity's `START` lands 1.7–2.0s
after the blocked app is launched, the event itself somewhat before that).
The `TYPE_ACCESSIBILITY_OVERLAY` bridge window covers that gap — it used to
do it with a plain black `View`, which read as a glitch rather than as the
app. It now renders, through a `ComposeView`, the same thing the real screen
shows: `CalmOtterTheme` + `OtterAnchoredScreen` + `ProgressRing` +
`OtterFloatMark(124.dp)`, with the ring's fraction taken from
`SessionManager` so it does not jump to the real value a moment later.

Two things make this safe rather than a second place to keep in sync:

- **The position is not recomputed.** The bridge composes the *same*
  `OtterAnchoredScreen` the block screen uses, with the same
  `horizontalPadding` and no header, so the otter's anchor is decided once,
  by the container that exists precisely to make the two agree.
- **The window is not an Activity**, so two things have to be re-added by
  hand: an opaque `Box` behind the content (`calmBackground()` is a
  translucent veil that counts on the XML theme's window background — with
  nothing behind it the blocked app would show through), and a
  `LifecycleOwner`/`SavedStateRegistryOwner` pair on the view tree, which
  `ComposeView` requires and would otherwise find only inside an Activity.

**The overlay window does not get the same insets as an Activity.**
Measured on the emulator: it sees the status bar but not the navigation bar
(bottom inset 0 instead of 72px), so `safeDrawingPadding()` inside
`OtterAnchoredScreen` computed an area 72px taller and centred the otter
36px lower — the otter mask sat at rows 1284–1409 against the real screen's
1242–1367. Neither `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR` nor
`fitInsetsTypes = 0` changed that. The fix is to subtract the difference
between the real system-bar inset (read from
`WindowManager.currentWindowMetrics`) and the one Compose sees, as bottom
padding around the container — so the single positioning formula stays in
`OtterAnchoredScreen`, and the compensation collapses to zero by itself if
the window ever starts reporting insets properly.

Verified by capturing the bridge in isolation (temporary build with the
dismissal disabled and the timeout raised, reverted before committing —
same technique as the otter-position measurements further down): the bridge
otter's centre landed at row 1310, against 1295–1325 for the real screen,
whose otter is bobbing ±15px. In other words the bridge draws it at the
exact midpoint of the real one's float, so the handoff shows no jump.

`notificationTimeout` is also set to 0 rather than the XML's 100ms: it is
the minimum delay the system waits before delivering another event of the
same type, and every millisecond of it is time the blocked app stays
uncovered. The handler returns immediately when no session is active, which
is the common case, so there is nothing to throttle.

## One Activity, two roles: `MainActivity` merges the old `HomeActivity`

`MainActivity` used to be purely `CATEGORY_LAUNCHER` (the app icon); a
separate `HomeActivity` class was `CATEGORY_HOME` (shown when Calm Otter is
set as the device's Home app — see `SettingsActivity.promptSetAsHome()`),
hosting `BlockScreen` when a session was active or forwarding to the
original launcher otherwise. Merged into a single `MainActivity` on
request, at parity of behavior (not a redesign) — having two classes for
what's conceptually one "Home surface" was the actual complaint, not
anything about what each one did.

- **`AndroidManifest.xml`** now declares **two `<intent-filter>` blocks on
  the same `MainActivity` entry**: the original `LAUNCHER` one, plus the
  `HOME`/`DEFAULT` one that used to belong to `HomeActivity`.
  `android:launchMode="singleTask"` (new — plain `MainActivity` had no
  explicit launchMode before, i.e. `standard`) keeps repeated Home-button
  presses from stacking duplicate instances: a new `Intent` to an
  already-running instance is delivered via `onNewIntent()` instead of
  spawning another `onCreate()`. `themeVariant` stays `ThemeVariant.BASE`
  unconditionally — `BASE` and `BLOCK` resolve to byte-for-byte identical
  XML styles (see `multi-theme-system/design.md`; the `.Block` styles are
  bare aliases with zero overrides), so there was never a need to compute
  it dynamically based on how the Activity was invoked.
- **`MainActivity.handleIntent(intent)`** (called from both `onCreate()`
  and `onNewIntent()`) is the merge point: `intent.categories?.contains
  (Intent.CATEGORY_HOME)` decides whether this launch came from the Home
  button — stored in `forwardOnBlockScreenExit`, used only to decide what
  happens on *exit* from `BlockScreen` (see "Three block screens, one
  screen" below), not whether to show it. Whether `BlockScreen` is shown at
  all now depends purely on `sessionManager.isSessionActive()`, regardless
  of `CATEGORY_HOME`. If a session is active, `enterBlockScreen()` sets
  `showBlockScreen = true` (`mutableStateOf<Boolean>`) and rolls a fresh
  phrase into `blockPhraseText`; otherwise `exitBlockScreen()` clears both.
  If `CATEGORY_HOME` and no session is active, it forwards to the original
  launcher and finishes instead, exactly as before. `setContent {}`'s
  single composition branches on `showBlockScreen`: `BlockScreen` vs
  `MainScreen`. Because both are `mutableStateOf`, flipping them from
  `onNewIntent()` (or from `MainScreen`'s own `onSessionStarted` callback,
  see below) on an already-live instance recomposes the right content
  directly — no `recreate()` needed, and none should be added; that would
  defeat the point of reusing the instance and cause a visible flicker.

## Three block screens, one screen

Originally, opening the app via its icon during an active session showed
`MainScreen`'s "Paused" view (a progress ring, no unlock affordance at all)
— the only way to actually unlock from there was to press Home instead, or
open a blocked app to trigger `BlockOverlayActivity`. Reported as a real gap
(not just a visual inconsistency) and fixed on request: **all three
situations where a session is active — the launcher icon, the Home button,
and opening a non-allowed app — now show the exact same `BlockScreen`**, so
there is always exactly one way to see "a pause is active" and exactly one
way out of it (unlock, or wait for expiry).

- **`MainActivity` shows `BlockScreen` whenever `isSessionActive()` is
  true, from any entry point.** A session becoming active *while `MainScreen`
  is already showing* (the user taps the otter) has no new `Intent` to
  react to, so `MainScreen` takes a new `onSessionStarted: () -> Unit`
  callback, invoked right after `sessionManager.startSession(...)`; wired
  in `MainActivity` to `enterBlockScreen()`, same as the `handleIntent()`
  path.
- **Exit behavior still depends on how this instance was reached**
  (`forwardOnBlockScreenExit`, set once per `handleIntent()` call from
  `CATEGORY_HOME`): unlocking/expiry arrived at via the Home button still
  calls `forwardToOriginalLauncher()` (there's nowhere else to go); arrived
  at via the launcher icon, it now just calls `exitBlockScreen()` and falls
  back to `MainScreen` in the same instance — forwarding to another
  launcher would make no sense when the user opened Calm Otter herself.
- **The back button is now blocked while `BlockScreen` is showing, matching
  `BlockOverlayActivity`** (previously `MainActivity` had no back handling
  at all) — an `OnBackPressedCallback` whose `isEnabled` is kept in sync
  with `showBlockScreen` everywhere the latter changes.
- **`BlockOverlayActivity` gains the allowed-apps row** it never had:
  `loadAllowedAppLaunchItems()`/`launchAllowedApp()` moved out of
  `MainActivity` into a new shared top-level file, `AllowedAppLaunchItems.kt`
  (plain functions taking a `Context`, not Activity methods), so both
  Activities pass identical `allowedApps`/`onLaunchApp` to `BlockScreen`.
- **The one remaining behavioral asymmetry between the two old callers —
  a toast on natural session expiry in `BlockOverlayActivity` but not in
  `MainActivity` — is now gone too**: `MainActivity`'s `onExpiredNaturally`
  shows the same `session_ended` toast. `onExpiredImmediately` still shows
  no toast in either caller (session already over before the screen ever
  rendered, so there was nothing to interrupt).

  Reversed later (see "The session toasts are gone" below): both
  `session_started` and `session_ended` are removed entirely, not just kept
  symmetrical — the asymmetry fixed here stopped mattering once neither
  caller shows a toast at all.
- **A pre-existing minor duplication also removed in the same pass**:
  `MainActivity` used to hand-build the phrase string
  (`"“$it”"`) instead of reusing `R.string.phrase_format`, the same
  resource `BlockOverlayActivity` already used — same rendered text either
  way, now the same code path too.
- **Consequence accepted, not a bug**: Settings and History, reachable from
  `MainScreen`'s header, are no longer reachable during an active session
  from *any* entry point (previously the icon path left them reachable) —
  the user has to unlock first, matching the intent behind the "soft lock"
  in the first place.
- **Verified on-device, not just by reading the diff** (this touches
  Android task/launchMode semantics, easy to get subtly wrong): icon-open
  idle, icon-open with active session (immediate `BlockScreen`, not the old
  Paused view), starting a session from an already-open `MainScreen`
  (immediate transition, no separate Intent involved), unlocking from the
  icon path (falls back to `MainScreen` in the same instance, no forward),
  Home-press idle (forwards away), Home-press with active session
  (`BlockScreen`), unlocking from the Home path (forwards to the original
  launcher, unlike the icon path), back button blocked while `BlockScreen`
  is shown, and normal Settings/History navigation when idle (`singleTask`
  doesn't break the ability to push child activities or back-navigate out
  of them). `BlockOverlayActivity`'s own allowed-apps row was verified by
  code review only, not live on-device — this emulator's
  `AppBlockerAccessibilityService` wasn't dispatching
  `TYPE_WINDOW_STATE_CHANGED` events at all during this session (`dumpsys
  accessibility` showed the service bound with empty `eventTypes`/
  `feedbackType` even after a full clean install and completing the real
  system consent dialog) — a pre-existing environment issue unrelated to
  this change, since the service's own code/manifest/XML config were not
  touched at all here. Worth a fresh look separately.
- **`SettingsActivity.promptSetAsHome()`'s disable/re-enable trick** (see
  below) now targets `MainActivity`'s component instead of a separate
  `HomeActivity` one — since it's the same component that also owns the
  launcher intent-filter, the brief disable/re-enable also momentarily
  disables the app's launcher icon, not just its Home eligibility.
  `PackageManager.DONT_KILL_APP` plus immediately re-enabling keeps this to
  an imperceptible flicker in practice, not a real gap.

## `LauncherManager` detection (hardened after a real forwarding-loop bug)

`refreshOriginalLauncherPackage()` prefers `resolveActivity(intent,
MATCH_DEFAULT_ONLY)` — the actual currently-active default Home resolution,
reliable whenever Calm Otter isn't the currently-active Home app itself.
Unlike the original version, this now runs and can overwrite the saved value
on **every** call (`MainActivity.onCreate()` on every launch, plus
`SettingsActivity.promptSetAsHome()` right before the disable/re-enable
toggle) rather than writing once and freezing — the user can switch their
default launcher at any time (install a new one, uninstall the old one,
change it in system Settings), and the saved "original launcher" needs to
track that instead of staying pinned to whatever was detected the very
first time. If `resolveActivity` resolves to Calm Otter itself (this call
happened while Calm Otter is already the active Home app — the common case
on every subsequent Home-press once it's been set) or to nothing, the saved
value is left untouched rather than overwritten from the unreliable
enumeration fallback described below. That fallback only ever runs once,
bootstrapping the very first save when nothing is stored yet (covering the
edge case where Calm Otter becomes the Home app, via system Settings,
before it has ever been opened once) — it's deliberately never repeated on
later calls, since its ordering isn't tied to "which one is the real
launcher" and re-running it every time would risk clobbering a
already-known-good saved value with an arbitrary pick.

Both paths now exclude a fixed `EXCLUDED_PACKAGES` set (currently just
`com.android.settings`) — **a real bug, found and fixed**: the plain
enumeration approach has no concept of "which candidate is the real
launcher", so on a device where the ordering happens to put
`com.android.settings` (which owns `FallbackHome`, AOSP's system fallback
Home activity, shown when no real launcher is enabled — not something
meant to be launched directly) ahead of the actual launcher, the wrong
package got saved as "original launcher". Once saved, this corrupted value
made `MainActivity.forwardToOriginalLauncher()` fail (see below) — a
saved-but-non-functional package is just as dangerous as no saved package
at all, since either way the naive old fallback (an unrestricted `CATEGORY_HOME`
intent, no `setPackage`) could resolve straight back to Calm Otter itself,
which was still the active Home role holder, spawning a new instance that
immediately repeated the same broken forward, indefinitely. Reproduced
deliberately on-device by writing `com.android.settings` into
`calm_otter_launcher.xml` and confirming the fix resolves to the real
launcher instead of looping, before and after each part of the fix below.

**A second, distinct bug surfaced while fixing the first one**: even with
`com.android.settings` correctly excluded, the fallback search still found
nothing — `com.google.android.apps.nexuslauncher` (unlike core AOSP
packages like `com.android.settings`) is subject to Android 11+ package
visibility rules, and `AndroidManifest.xml`'s `<queries>` block only
declared visibility for `CATEGORY_LAUNCHER` (for `AllowedAppsActivity`),
not `CATEGORY_HOME` — so `queryIntentActivities`/`resolveActivity` for Home
intents couldn't see the real launcher from *this app's* visibility scope
at all, even though an externally-issued `adb shell am start` to that exact
package succeeded (shell isn't subject to the same visibility restrictions
our app is). Fixed by adding a second `<intent>` entry to the same
`<queries>` block for `ACTION_MAIN`/`CATEGORY_HOME`.

## `forwardToOriginalLauncher()` can never target itself

Even with the two fixes above, `LauncherManager`'s saved value could in
principle be corrupted or refer to an app since uninstalled — the stored
value is external state, and this method is the one place a wrong value
becomes actively dangerous, not just cosmetically wrong. Hardened so the
loop described above can't recur regardless of what's saved:

- The saved package is only trusted if it's neither Calm Otter's own
  package nor in `LauncherManager.EXCLUDED_PACKAGES`; otherwise a fresh
  `findOtherHomePackages()` lookup runs instead (same
  self/excluded-package filtering as `LauncherManager`, done independently
  here rather than trusting the saved value at all).
- **Every intent this method sends now has an explicit `setPackage(...)`.**
  The old version's exception-fallback branch sent a bare `CATEGORY_HOME`
  intent with no package restriction — exactly the shape of intent that,
  since Calm Otter is still the active Home role holder at the moment this
  runs, can resolve straight back to itself. That fallback is gone; if no
  safe target package can be found at all, the method simply calls
  `finish()` without starting anything, rather than risk a self-targeting
  intent as a last resort.

## Multiple installed launchers: chooser instead of a guess

`resolveActivity(MATCH_DEFAULT_ONLY)` only reflects "the real launcher"
reliably when a single default was already chosen at some point. With the
saved value unusable (missing/corrupted/excluded) and two or more
launchers installed with no prior default, plain enumeration
(`queryIntentActivities`) has no ordering tied to "which one the user
actually wants" — picking `firstOrNull` would be an arbitrary guess, same
class of problem as the `com.android.settings` bug above, just with a
legitimate launcher this time instead of a system fallback.

`findOtherHomePackages()` (renamed from the old single-result
`findAnyOtherHomePackage()`) now returns **all** matching candidates
instead of just the first. `forwardToOriginalLauncher()` branches on the
count: zero → nothing to launch, just `finish()`; exactly one → launch it
directly via `setPackage(...)` as before (this remains the common case in
practice — verified on-device, unchanged); two or more → `launchHomeChooser()`
shows an Android chooser dialog restricted to just those candidates, letting
the user pick instead of guessing on their behalf.

The chooser is built with **`Intent.EXTRA_INITIAL_INTENTS`**, deliberately
*not* `Intent.createChooser(genericHomeIntent, title)`: a generic
`CATEGORY_HOME` intent as the chooser's primary target would be re-resolved
by the system from scratch, and Calm Otter — which also declares
`CATEGORY_HOME` — would reappear as an option in its own disambiguation
dialog. Instead, the target intent passed to `createChooser` is an empty,
action-less `Intent()` that resolves to nothing on its own, and the actual
options come entirely from `EXTRA_INITIAL_INTENTS`: one explicit
`ACTION_MAIN`/`CATEGORY_HOME` intent per already-filtered candidate, each
with its own `setPackage(...)`. Only the pre-vetted candidates are ever
shown; Calm Otter can't reappear because it was never in that list to begin
with (same `it != packageName` filter as everywhere else in this file).

Not verified live on-device: this emulator image ships exactly one real
launcher (`com.google.android.apps.nexuslauncher`), so the 2+-candidate
branch couldn't be exercised end-to-end without installing a second
launcher app. The single-candidate path (the common real-world case) was
re-verified after this change and behaves identically to before.

## Home-forward must not bypass onboarding

Edge case: Android's "Default apps → Home" picker lists every app
declaring `CATEGORY_HOME` regardless of whether it's ever been launched, so
it's possible to set Calm Otter as the Home app from system Settings
*before ever opening the app once* — skipping `OnboardingActivity` and the
password setup it gates entirely. In that case the very first
`MainActivity.onCreate()` is triggered by the Home button itself, with no
session ever started, so `handleIntent()`'s home-and-no-session branch
would call `forwardToOriginalLauncher()` and `finish()` before
`onResume()` ever gets a chance to run its own
`passwordManager.isPasswordSet()` redirect — the user would land on
whatever other launcher is installed, having never set a password.

Fixed by adding `passwordManager.isPasswordSet()` to the forwarding
condition in `handleIntent()`. When it's false, the forward is skipped
entirely (not replaced with a duplicate onboarding-redirect call here) and
`onCreate()`'s normal flow continues into `setContent { MainScreen(...) }`
— `onResume()`'s pre-existing redirect (see above) then takes over
immediately after, exactly as it already does for the ordinary
launcher-icon entry point. Reproduced deliberately on-device: fresh
install (no password set), `cmd role add-role-holder
android.app.role.HOME com.calmotter.app` *without ever launching the app*,
then a Home key press — confirmed `OnboardingActivity` appears (not a
forward, not a blank/looping state), with exactly one `type=home` task in
`dumpsys activity activities`.

## Settings' "Home app" switch: two real bugs, both found via logcat

`SettingsActivity.promptSetAsHome()` (moved here from `MainActivity`, see
`home-and-settings/design.md`) used to disable then re-enable
`MainActivity`'s own component (`COMPONENT_ENABLED_STATE_DISABLED` →
`_ENABLED`) to force Android's Home-app chooser to reappear even if the
user previously dismissed it, then fired a `CATEGORY_HOME` intent so the
chooser would show immediately.

**Bug 1**: on Android 10+ (API 29+), the Home-app preference is owned by
`RoleManager` (`android.app.role.HOME`), not the old `PackageManager`
"always"/`IntentResolver` preferred-activity mechanism the disable/re-enable
trick relies on. Disabling and re-enabling a component has zero effect on a
`RoleManager`-held role — reported as "the button doesn't do anything", and
confirmed via `adb logcat`: tapping it just silently re-resolved the
`CATEGORY_HOME` intent straight back to the already-assigned launcher
(`ActivityTaskManager: START ... cmp=<already-current-launcher>`), no
chooser ever appeared. `RoleManager.createRequestRoleIntent(ROLE_HOME)` is
the API meant for exactly this: it always shows the system's Home-role
picker, even when a different app already holds the role.

**Bug 2, found while fixing Bug 1**: `createRequestRoleIntent()`'s result
must never be launched via a plain `startActivity()`. The system
`RequestRoleActivity` that handles it determines the requesting
package from the calling Activity's token, which Android only propagates
when the intent is launched "for result" — a plain `startActivity()`
produces `RequestRoleActivity: Package name cannot be null or empty: null`
in logcat, and the activity finishes itself immediately with no UI shown at
all, the exact same "nothing happens" symptom as Bug 1 but for a different
reason. Fixed by launching it through a
`registerForActivityResult(ActivityResultContracts.StartActivityForResult())`
launcher instead (the result itself is unused — `onResume()`'s existing
`resumeSignal` refresh already recomputes `isDefaultHome()` when the user
returns).

Below API 29, `RoleManager` doesn't exist and the Home preference really is
governed by the old "always" mechanism, so the original disable/re-enable
trick is kept for that range (`minSdk` is 26).

Verified on-device end to end, both bugs reproduced and re-checked after
each fix via `adb logcat` (the two distinct log signatures above): tapping
the "Home app" switch now shows the real system "Set as default home app?"
dialog listing Calm Otter alongside the current launcher; selecting Calm
Otter and confirming correctly makes `dumpsys role` report
`com.calmotter.app` as the `android.app.role.HOME` holder; pressing Home
afterward with no session active forwards to the previous launcher as
designed (see `LauncherManager` above), and with a session active shows
`BlockScreen` instead — the full loop, not just the role assignment.

## Allowed-apps loading

`AllowedAppsActivity.refreshApps()` runs on `Dispatchers.IO` via
`lifecycleScope.launch`, converting each app icon `Drawable` to a `Bitmap`
up front (`AppItem.icon`) since Compose needs a stable value, not a
lazily-decoded drawable, for list rendering. Toggling a row saves
immediately (`allowedAppsManager.setAllowedPackages`) and rebuilds the
in-memory list with `.map { it.copy(...) }` since `AppItem` is immutable.
`refreshApps()` is called from `onCreate()` (initial load, no confirmation
toast) and from `AllowedAppsScreen`'s `PullToRefreshBox` `onRefresh`
callback (subsequent reloads, `allowed_apps_saved` toast on completion) —
it cancels any in-flight `loadJob` first so a pull mid-load can't race
against the initial load.

`AndroidManifest.xml` declares a `<queries>` element (`ACTION_MAIN` /
`CATEGORY_LAUNCHER`) specifically so `loadApps()` sees every installed app
on Android 11+ package-visibility rules — added after this was flagged as a
gap; don't remove it.

## Launching an allowed app from `MainActivity`: `BlockScreen.allowedApps`

A gap found and closed after the fact: when Calm Otter *is* set as Home and
a session is active, pressing Home shows `BlockScreen` with no launcher UI
at all — unlike the "not set as Home" case, where the real launcher is
still reachable (see User Story/requirements). An allowed app you hadn't
already opened before the session started had no way to be *started* at
all in that state; the whitelist only stopped `AppBlockerAccessibilityService`
from redirecting away from an allowed app already in the foreground, it
never offered a way to launch one.

- **`BlockScreen` gained two optional parameters**, both defaulting to
  "nothing" so `BlockOverlayActivity` (which never passes them) is
  unaffected: `allowedApps: List<AllowedAppLaunchItem> = emptyList()` and
  `onLaunchApp: (String) -> Unit = {}`. The actions row itself (see "Unlock
  moved into the actions row" below) always renders — Unlock has to be
  reachable regardless — but the app icons within it are simply omitted
  when `allowedApps` is empty, and the row's caption switches to a plain
  "Unlock" instead of "Unlock or open an allowed app" in that case, so
  `BlockOverlayActivity` (which never has any) and `MainActivity`'s
  Home-invoked path with no configured allowed apps both read naturally as
  "just an unlock control",
  not a broken empty list.
- **`AllowedAppLaunchItem(label, packageName)`** (in `BlockScreen.kt`) went
  through four looks before landing here: text-only rows (too long/lost
  focus), then real app icons desaturated via
  `ColorFilter.tint(primary, BlendMode.Color)` (a duotone effect),
  **2-letter initial badges** — `app.label.trim().take(2).uppercase()` —
  in a solid `primary`-filled circle with `onPrimary` text (the exact same
  badge construction as the Unlock control next to it, see below), and
  finally the same 2-letter badges re-tinted to `primary.copy(alpha =
  0.18f)` background + `primary` content, 48dp not 40dp — flagged directly
  ("il colore dei bottoni non rispetta il [linguaggio]... li fai un filo
  più grandi"): the solid full-opacity fill predates the tinted-card
  language later established for Settings/AllowedApps/History (see those
  specs) and had come to look out of place next to it. This also means no
  icon/`Bitmap` is loaded at all — `AllowedAppLaunchItem` only carries what's
  needed to derive the initials and launch the app.
- **`MainActivity.loadAllowedAppLaunchItems()`** resolves labels only for
  packages already in `AllowedAppsManager.getAllowedPackages()` — a
  handful of entries, unlike `AllowedAppsActivity.loadApps()`'s full
  installed-app enumeration — so it runs synchronously on the main thread
  rather than dispatching to `Dispatchers.IO` (and, since there's no icon
  to load either, doesn't touch `PackageManager.loadIcon()`/`toBitmap()`
  at all). A package that was allowed but has since been uninstalled is
  dropped silently (`getApplicationInfo()` throwing is caught and mapped
  to `null`, filtered out via `mapNotNull`). The list is also
  `.take(AllowedAppsManager.MAX_ALLOWED_APPS)` as a safety net — the real
  cap is enforced at write time (see below), this should never actually
  trim anything in practice.
- **The phone is always the first item, outside the 3-app cap.** Resolved
  separately via `TelecomManager.defaultDialerPackage` (the same call
  `AppBlockerAccessibilityService.allowedPackages()` already uses to exempt
  it from blocking) rather than reading it from
  `AllowedAppsManager` — it was always implicitly allowed, but before this
  list existed there was nowhere to actually *launch* it from when Calm
  Otter is Home. Deduplicated against the configurable list by package name
  in case a user had also explicitly whitelisted their dialer.
- **`AllowedAppsManager.MAX_ALLOWED_APPS = 3`** (lowered from 5, see
  requirements.md's User Story 4 criterion 7 for the bug that prompted
  it) is the single source of truth for the cap, enforced where an app is
  actually *added* (`AllowedAppsActivity.toggleApp()` — attempting a 4th
  shows a `allowed_apps_limit_reached` toast and does nothing, rather than
  accepting it and truncating the display elsewhere) so the stored
  whitelist and what `BlockScreen` shows never disagree. The phone doesn't
  count against it (see above).
- **`AllowedAppsActivity.loadApps()` excludes the dialer package from the
  editable list entirely** (`dialerPackageName()`, exposed from
  `AllowedAppLaunchItems.kt` for this), reported directly as a bug: the
  dialer is always allowed unconditionally (see above), so before this fix
  it showed up in the full installed-app enumeration like any other app,
  with an unchecked checkbox — implying the user needed to explicitly
  select it to keep it reachable, which isn't true and could even suggest
  it wasn't currently allowed. Same reasoning as excluding Calm Otter's own
  package from this list.
- **Each row in `AllowedAppsScreen.AppRow` is a `Card` tinted with
  `primary.copy(alpha = 0.04f/0.12f)`** (unselected/selected), not
  `colorScheme.surface` — flagged directly ("mi pare un errore"/looked
  flat): in every light palette `surface` equals `background` (see
  `CalmOtterTheme.kt`), so an unselected row was visually indistinguishable
  from the page itself. Same tinted-card ingredient Settings' grouped
  sections already use. The row's control is a `Switch` (with its own
  `calmSwitchColors()`, duplicated from `SettingsScreen.kt` since that
  one's private to its file) — a `Checkbox` was tried first but replaced on
  request to match the `Switch` used everywhere else a boolean is toggled
  in this app (`PhrasesCard` in `SettingsScreen.kt`). Both the Card and the
  Switch/Checkbox read only roles `CalmOtterTheme.kt` actually customizes
  per palette (`primary`/`onPrimary`/`onSurface`), not `surfaceVariant` —
  the same trap already fixed for `AlertDialog` and `PhrasesCard`'s own
  `Switch`.
- **`MainActivity.launchAllowedApp()`** just calls
  `packageManager.getLaunchIntentForPackage(packageName)` and starts it,
  swallowing a null/failed intent silently (package became unlaunchable
  between list-load and tap) rather than surfacing an error — the user
  stays on `BlockScreen`, same as if they'd tapped nothing.
- Placed in `BlockScreen` *after* the countdown/phrase, as its own row —
  originally below a separate always-visible password field and Unlock
  button, since folded into the row itself (see next section). The row
  used to carry a caption above it; removed in a later text-reduction pass,
  see "BlockScreen redesign" below.

## Unlock moved into the actions row, behind a dialog

Originally `BlockScreen` had an always-visible `OutlinedTextField` +
`Button("Unlock")` sitting in the main flow, with the allowed-apps row (if
any) below it. Reworked so Unlock lives in the *same* row as the allowed
apps, **last** (rightmost) rather than first — the allowed-app badges are
listed first, Unlock always trails them — as a tinted-`primary`-badge lock
icon (see above for the solid→tinted repaint), the same circular badge
construction as `PactPawsMark` (`onPrimary` content originally;
`PausePawsMark`, which originally shared this same construction and
appeared above the row on `BlockScreen` itself, has since been removed —
see "BlockScreen redesign" below and `mascot-marks/design.md`) and now
also the same construction the allowed-app initial badges use (see
above) — the whole row reads as one consistent set of round action
badges, distinguished by their content (a lock glyph vs. 2 letters), not
by a different visual treatment for "the important one". Tapping the
lock badge opens a Compose `AlertDialog` containing the password field,
error/lockout text, and the actual "Unlock"/"Cancel" buttons, instead of
keeping that content permanently on-screen.

Deliberately no horizontal scroll on this row: with the cap lowered to 3
allowed apps (see above), phone + 3 apps + Unlock is 5 badges, which fits
one row at 48dp with 16dp spacing on a normal phone screen without one.
Before that fix, phone + 5 apps + Unlock (7 badges) silently overflowed
past the screen edge with no scroll to reach them — the Unlock badge
itself, being last, became completely unreachable, leaving no way to end
the session from this screen. A scrollable row was considered and
rejected as the fix (in favor of lowering the cap instead): Unlock is the
one control this screen cannot afford to hide behind an undiscovered
scroll gesture.

- **`showUnlockDialog` (`mutableStateOf(false)`)** gates the dialog;
  `password`/`statusText`/`isLockedOut`/`lockoutSecondsRemaining` are
  unchanged from before (still hoisted at `BlockScreen`'s top level, not
  inside the dialog) so the lockout countdown `LaunchedEffect` keeps
  running correctly even while the dialog is closed — a user can dismiss
  the dialog mid-lockout and reopen it later to see the countdown exactly
  where it left off.
- **Both `onDismissRequest` and the dialog's own Cancel button** reset
  `password`/`statusText` in addition to closing the dialog, so reopening
  it always starts from a clean field rather than showing a stale wrong
  password or leftover input.
- The verify/lockout logic inside the confirm button's `onClick` is
  otherwise identical to the old inline `Button`'s — same
  `passwordManager.isLockedOut()`/`verify()` calls, same `onUnlocked()`
  callback on success — just relocated, plus `showUnlockDialog = false`
  on success so the dialog doesn't linger visible during the
  forward-to-launcher/finish transition.
- The row itself is always shown, unlike the old inline Unlock button+field
  which was also always shown — not a behavior change, just a relocation
  into a more compact control. Its caption (`block_actions_label` / plain
  `unlock` string when `allowedApps` is empty) was removed later — see
  "BlockScreen redesign" below.

**Superseded by a later extraction**: the state-hoisting details in the
bullets above (`password`/`statusText`/`isLockedOut`/`lockoutSecondsRemaining`
hoisted at `BlockScreen`'s top level, manually reset on dismiss/cancel) no
longer describe the actual code — the whole dialog, state included, moved
into a shared `ui/screens/PasswordVerifyDialog.kt` once Settings' own
password prompt needed the exact same thing (see
`session-history-and-stats/design.md`'s "Non-Compose dialogs"). The manual
resets are gone too, not just relocated: because the dialog composable is
only ever invoked from inside `if (showUnlockDialog) { PasswordVerifyDialog(...) }`,
Compose destroys its `remember`ed state when it leaves composition, so a
fresh dialog is naturally blank next time without needing to reset anything
by hand. `BlockScreen` itself now only owns `showUnlockDialog` and what to
do in `onVerified` (end the session, toast, forward/exit) — the dialog's
own field/error/lockout state is no longer its concern.

## BlockScreen redesign: Home-styled ring + floating otter, less text

On request ("prendi ispirazione dalla home, vorrei otter fluttuante come
prima, riduci le scritte inutili"), `BlockScreen` was redesigned to look
and read like `MainScreen`'s "Living Pond" during an active session,
instead of having its own distinct static badge and a paragraph of rules
text repeated every time it appears:

- **`PausePawsMark` (the static badge above the old title) is gone**,
  replaced by the exact same `ProgressRing` + floating `OtterFloatMark`
  `PondOtter` draws on the Home screen during a session — same code, not a
  lookalike. `ProgressRing` and the floating-offset animation (previously
  private/inline inside `PondOtter`) are now `rememberOtterFloatOffset(periodMillis)`
  and a non-`private` `ProgressRing`, both still declared in `MainScreen.kt`
  — `BlockScreen.kt` calls them directly, same package
  (`ui/screens`), no import needed. See `mascot-marks/design.md`'s
  "Replacing `PausePawsMark`" for the full mark-level history.
- **`BlockScreen` now tracks raw milliseconds, not just the formatted
  countdown string**: `totalMillis` (read once via `remember`, doesn't
  change during a session) and `remainingMillisState` (updated on the same
  once-a-minute tick that already updated the display string), so it can
  compute the ring's fill fraction the same way `PondOtter` does
  (`1f - remaining/total`).
- **`block_title` ("Pause in progress"), `block_message` (the full
  rules paragraph), and `block_actions_label` (the caption above the
  allowed-apps row) are all gone** — removed as repeated, non-essential
  text now that the ring/otter/lock-icon visual language, the countdown
  phrase, and the optional reflective quote already carry the meaning.
  `home_active_label` ("On Pause"/"In pausa") is reused in its place, the
  same string `PondOtter` shows for the same state, rather than adding a
  `BlockScreen`-specific one. The English value was originally "Paused",
  changed to "On Pause" on request: the feature itself is called "a pause",
  so a status chip reading "PAUSED" right above the lock screen for that
  same pause session read as if the pause had itself been paused/interrupted
  — a self-referential ambiguity the Italian "In pausa" doesn't have (it
  reads as an ongoing state, not a verb applied to a noun of the same name),
  so only `values-en/strings.xml` changed.
- **The reflective phrase and the `CalmCountdown`-generated countdown
  phrase are both kept** — neither is "repeated boilerplate text": the
  countdown phrase is the one piece of actually-changing information on
  the screen, and the reflective quote is the one bit of emotional value
  the "soft lock" is meant to add, not mechanical explanation of the rules.
- **A stray emoji in `CalmCountdown.kt`'s `nearEndPhrases`** ("Quasi
  finita" had a trailing emoji) was found and removed while touching this
  exact file for the fraction calculation above — unrelated to the visual
  redesign itself, just noticed in passing.
- **A follow-up pass added `Modifier.calmBackground()`** (the same
  `primary`-tinted vertical gradient Home and Onboarding already used — see
  `home-and-settings/design.md`'s "Tinted background") to `BlockScreen`'s
  root `Column` too, on request to align its background with the rest of
  the redesign rather than the plain neutral `background` it kept
  inheriting from the window theme. `CalmBackground.kt`'s doc comment,
  which used to explicitly call out the block screen as one of the
  screens deliberately excluded, is updated accordingly.

## Home → BlockScreen: one otter, above the crossfade

**Current design.** `MainActivity` puts a single `PersistentOtter` *above*
a `Crossfade` that swaps everything else. The otter is one node: it is not
inside either screen, so it is never recomposed out, never handed over, and
cannot move, scale or lose a transform at the swap. The two screens keep
reserving an equally-tall but **empty** otter slot (`drawOtter = false`), so
the content around it lays out exactly as before.

Position comes from `otterCenterY(viewportHeight)` in
`OtterAnchoredScreen.kt` — **one formula with two callers**, the container
that aligns the empty slot and `MainActivity` that places the real otter.
Two formulas that have to agree is precisely what made the otter slide
before (twice, see below).

Declared consequence: this design **forbids** Home and the block screen from
putting the otter in different places. That is today's invariant — if it
ever has to change, this gets redesigned, not worked around with a second
otter.

`PersistentOtter` owns the bob, the tap zoom, the confirm burst and the tap
target. The progress ring stays inside the screens: it belongs to the block
state, appears with it and fades with it.

Two callers still draw their own otter, and keep the `drawOtter = true`
default: `BlockOverlayActivity` and the accessibility-service overlay in
`AppBlockerAccessibilityService`. Neither arrives from a transition. The
service builds its own `OtterAnchoredScreen`; it now takes the mark's size
from the shared `OtterMarkSize` rather than repeating `118.dp`, since its own
comment promises the otter will not move by a pixel when the real screen
takes over.

### Why the shared element was removed

What follows describes the previous design, kept because its failure is the
reason for the current one.

Three mechanisms had to agree about where the otter was and how big:

1. `OtterAnchoredScreen`, which exists to put it in the **same place** in
   both screens;
2. the shared element, which exists to **animate its movement** between
   them;
3. `graphicsLayer` transforms — bob, tap zoom — which layout does **not**
   see.

The first two worked against each other: the second animated a movement the
first existed to prevent. The third is the one that lost. `Modifier.scale`
put the tap's 1.18× zoom on a *parent* `Box` while `sharedElement` sat on the
mark inside it; shared-element bounds come from layout, which ignores
`graphicsLayer`, so at the handover the 1.18× vanished in a single frame.

Measured on the emulator, screenshots at 10× animator duration scale: otter
ink 119px wide in one shot, 101px in the next, and 101 × 1.18 = 119.2. A
sideways excursion of 32px (≈11dp) from centre showed up in the same frames
and disappeared with the redesign, so it was part of the same handover.

The measurement is worth recording too, because getting it wrong was easy:
pixel-scanning a 720p `screenrecord` mistook the paw mark and the block
screen's own text for the otter more than once. What worked was
`animator_duration_scale 10` plus full-resolution `screencap`, isolating the
otter's eye band, and comparing against the pond disc in the same frame.

The old design, for the record:



The redesign above left the two screens drawing the *same* otter
(`OtterFloatMark` at 124.dp in both), but `MainActivity` still swapped
between them with a bare `if (showBlockScreen)`: Compose replaced one
tree with the other in a single frame, with no transition at all. On
report ("l'animazione di avvio è bella, ma quando passa di pagina la
transizione è brutta") the swap was replaced with a
`SharedTransitionLayout` + `AnimatedContent` pair, and the otter declared
a shared element across the two.

- **Only the otter is shared, under the key `"otter"`.** Everything else
  is genuinely different between the two states (ambient ripples and the
  duration chips on one side; the progress ring, countdown phrase and
  reflective quote on the other) and simply crossfades. Sharing the
  *ring* too was rejected: on the Home screen the ring only exists during
  an active session, so on the start path there is no ring to morph
  *from* — it has to materialize either way.
- **The `Modifier` is built in `MainActivity`, not in the screens.**
  `sharedElement` needs both scopes at once (`SharedTransitionLayout` for
  the shared state, `AnimatedContent` for which side is entering), and
  only `MainActivity` has them. `MainScreen` and `BlockScreen` each take
  an `otterModifier: Modifier = Modifier` parameter and pass it straight
  to `OtterFloatMark`. The default matters: `BlockOverlayActivity` (and
  `MainActivity` itself when it opens already blocked, without passing
  through the Home screen) have no originating screen to animate from and
  render `BlockScreen` unchanged.
- **Asymmetric fade, on purpose**: the outgoing content fades over 220ms
  while the incoming one waits 120ms then fades over 340ms. A symmetric
  crossfade leaves both trees sitting at half opacity simultaneously,
  which reads as a smear; staggering them keeps the shared otter — drawn
  at full opacity in the transition overlay — the only crisp thing on
  screen during the swap, so it's the otter that carries the eye across.
- **`@OptIn(ExperimentalSharedTransitionApi::class)` sits on
  `MainActivity.onCreate`**, the single place in the app that uses the
  API, rather than being opted into project-wide.

None of the above is in the code any more: `SharedTransitionLayout`,
`sharedElement`, `boundsTransform`, the `otterModifier` parameters and the
experimental opt-in all went with it, and so did the single hoisted
`rememberOtterFloatOffset` instance that existed only to keep two bobbing
oscillators in phase — with one otter there is one oscillator by
construction.

### A test now guards the invariant

`app/src/test/java/com/calmotter/app/OtterAnchoredScreenTest.kt` — the first
composition test in this project, which is why `build.gradle.kts` gained
`androidx.compose.ui:ui-test-junit4` on the JVM test configuration (the
Compose BOM has to be repeated there: above it is applied to
`implementation` and `androidTestImplementation` only).

It does not compose the real `MainScreen`/`BlockScreen` — those drag in Room,
the Keystore and system permissions, and a test that fails for those reasons
stops being read. It tests `OtterAnchoredScreen`'s contract, which is where
the invariant actually lives and what both screens inherit it from, against
the two differences that could move the otter: Home has a header and the
block screen does not, and the content below the otter differs a lot in
height.

Two things learned writing it, both now encoded:

- `createComposeRule` allows **one** `setContent` per test, so the two
  configurations come from flipping a `mutableStateOf` — which is closer to
  what really happens anyway, the screen changing under an otter that is not
  recreated.
- Robolectric's default screen is short enough to hit the degenerate case
  `OtterAnchoredScreen` documents: the gap above the otter clamps to zero and
  the header pushes it down again, by exactly `HomeHeaderHeight`. The first
  run failed on this (expected 96.0, was 0.0). The tests therefore declare
  `@Config(qualifiers = "w411dp-h891dp")`, and a third test pins the
  short-screen behaviour itself rather than leaving it as a surprise.

### The otter has to be in the *same place*, or it slides

The first version of this animated the otter between the two screens, and
that was reported as wrong: "l'otter che si sposta in avvio non mi piace,
vorrei stesse fermo in pagina mentre il resto fade-out e fade-in."

Measuring showed the slide wasn't the transition's doing — the two screens
simply drew the otter in different places. On a 1080x2220 device the Home
otter's centre sat at y=812 with an empty history and y=760 once a few
sessions existed, because `PondOtter` was centred in `weight(1f)` space
that shrinks as the bottom card grows; `BlockScreen` meanwhile centred its
own column, landing somewhere else again. No static position could match
both Home states, so the shared element always had a gap to animate.

The fix is therefore layout, not animation. The first version of it gave
the otter a fixed slot at a fixed offset from the top: `OtterSlotTopInset`
+ `OtterSlotHeight` (both in `MainScreen.kt`), with `BlockScreen`
reserving via a hand-placed `Spacer` the same space Home spends on padding
+ header despite having no header. With identical bounds the shared
element has nothing to interpolate, so the otter is motionless while
everything around it crossfades.

A side benefit: Home itself stops reflowing the moment you record your
first session.

### That fix broke twice, so the position moved into one container

Two numbers that have to match, computed by two screens, are not an
invariant — they are a coincidence waiting to be disturbed, and both
disturbances came from changes made elsewhere in good faith:

1. **A scrolling refactor.** Making every screen scroll at large font
   scales meant dropping `Modifier.weight` (illegal inside a vertical
   scroll) for `Arrangement.SpaceBetween`. With three children, that
   splits the leftover space into two gaps — one of them *above* the
   otter, which is exactly the offset that was supposed to be fixed.
2. **`maxHeight` measured outside the safe area.** The same refactor's
   `BoxWithConstraints` sat outside `safeDrawingPadding()`, so
   `heightIn(min = maxHeight)` made Home permanently taller than its own
   viewport by the system-bar insets. Home was therefore always slightly
   scrollable, and any residual scroll offset lifted the otter off its
   anchor — see `CalmScreenColumn`'s doc comment.

Both were reported the same way: the otter slides when the pause starts.
So the position stopped being something each screen computes and became
something neither can: **`OtterAnchoredScreen`** (`ui/screens/`) is the
container for both, and it centres the `OtterSlotHeight` slot in the
viewport, subtracting a *fixed* `headerHeight` (96dp for Home's title
strip, 0 for `BlockScreen`) so that a header only one of the two screens
has cannot shift what follows it. The otter's position is now a function
of the viewport height alone — identical across the two screens by
definition, not by arithmetic that has to be kept in step.

Centring rather than a top offset is what the user asked for, but note it
is the *slot* that is centred, not the content column: centring the
columns would put the otter at the middle of each screen's own content,
and those differ (Home: sessions line, "Tempo insieme"; block: countdown,
phrase, actions), which would reintroduce the slide with a different
magnitude.

Two limits are accepted and documented on the container: once content
overflows and the screen actually scrolls, the anchor is gone (content you
can reach beats an otter that holds still), and on a viewport short enough
that the computed gap clamps to zero the two screens differ by
`headerHeight` — which does not happen on a phone held upright, where the
centre sits around 240dp and the header takes 96.

The float animation (`rememberOtterFloatOffset`) is also hoisted into
`MainActivity` and passed to both screens. Each used to run its own, at
different periods (3200 vs 5200), so at the swap the incoming one started
from its initial value while the outgoing one was at an arbitrary phase —
a difference of up to the full 10dp amplitude. That never showed as a
jump, because the shared element *animates* such a difference, but that
animation is precisely what reads as the otter sliding. One shared
instance removes the residual cause. The cost, accepted: no more slower
bob during a session, since changing the period mid-flight would restart
the animation and reintroduce exactly this.

### Ending the pause: the ring completes and releases

Until this point the only feedback at the end of a session was a system
toast. On request ("a fine sessione, possiamo inserire un'animazione?")
`RingReleaseBurst` (`MainScreen.kt`) was added: the progress ring, having
just reached 100%, detaches from its radius, widens by 45%, thins out and
fades. It is deliberately `TapConfirmBurst` read backwards — that one
brings a wave *inward* onto the otter at the start — rather than a new
visual idea for the ending.

**Only on natural expiry.** `BlockScreen` sets `releasingRing` in the
countdown loop's exhausted branch, and nowhere else:

- **Unlocked with the password**: no release, just the existing crossfade.
  The ring is not at 100% — often nowhere near, as an early unlock is
  usually early — so showing it "complete" would narrate something that
  didn't happen. Ending early is a legitimate part of the pact, so it
  gets a plain exit rather than anything that could read as a reprimand.
- **`onExpiredImmediately`** (session already over before the screen
  rendered): no release either, because there was nothing on screen for
  the user to have been watching.

The ring and the release never render together — `releasingRing` swaps one
for the other — otherwise two concentric circles would be visible instead
of one loosening.

The 700ms delay is only on *leaving the screen*: `endSession()` has
already run by then, so nothing about the block itself is prolonged. It
plays in all three callers, including `BlockOverlayActivity`, rather than
being made caller-specific: this doc's own rule is that the three block
screens may differ in what happens *after* they exit, never in how they
behave while on screen, and an ending animation is squarely the latter.

Verified on-device by writing a session whose end time was a few seconds
out, then capturing the 60-second countdown tick that notices it (the
loop's `delay(60_000)` is real time and is not affected by
`animator_duration_scale`, so the tick has to be waited out even when the
animation itself is slowed 10x). The frames show the ring filling,
closing as a complete circle, then widening and fading. Unlocking in the
same build showed the ring near-empty and no release, confirming the
distinction is doing what it claims.

### How this was verified

Position was measured on-device rather than eyeballed, because the otter
bobs (+/-5dp) and the tap burst scales it 1.18x — both large enough to
swamp the effect being measured, and both of which did mislead an earlier
attempt at this measurement. With the bob temporarily frozen, the otter
occupied rows 706-790 identically in all three states: Home with an empty
history, Home with sessions, and `BlockScreen`. Capturing the transition
frame by frame under `animator_duration_scale 10`, with the burst also
frozen, the centre moved from y=736 to y=740 across the whole swap - the
residue of the bob, with no discontinuity. Both temporary freezes were
reverted before committing.


## Redesign pass: the pause screen

Applied from the Stitch redesign, minus two things it asked for.

**No top bar and no back arrow.** The mockup gives the active-session screen
a title bar with a back arrow and an avatar. That screen exists precisely
because there is no way out; an exit affordance that does nothing is worse
than none, and the avatar promises navigation that does not exist during a
pause.

**No seconds-level countdown.** The mockup shows "24:15 remaining" ticking
down. Watching a timer is what the app is trying to interrupt — the vague
phrasing stays ("you still have about 25 minutes", from
`calm_countdown_template_phrases`).

What was taken: the status chip, which absorbs the old "In pausa" text row
rather than adding to it — the mockup had both a chip *and* a "PAUSED"
label, saying the same thing twice — and a caption under each action badge
(`BlockActionBadge`). Two letters for an app ("PH" for Phone) mean nothing
until you have worked them out once, and the padlock alone does not separate
"unlock the pause" from "lock something". This does not contradict the row
caption removed earlier: that one was a heading for the whole group, these
name the single items.


## The session toasts are gone

Reported directly: "non mi piace la toast 'pause session started' e 'pause
session finished'". There were actually four call sites, not two —
`session_started` (`MainActivity`, tapping the otter) and `session_ended`
(`MainActivity`'s `onExpiredNaturally`, `BlockOverlayActivity`'s
`onExpiredNaturally`, and BOTH manual-unlock paths in `BlockScreen.kt`,
NFC tap and password) — all showing a system `Toast` on top of a screen
that was already visibly changing: the `Crossfade` into/out of
`BlockScreen`, the "ON PAUSE" chip, the unlock dialog closing. Same
reasoning already applied to `block_title`/`block_message` earlier in this
file ("an exit affordance that does nothing is worse than none" — here,
"a toast repeating what the transition already shows is noise, not
information"): removed outright, no replacement text and no haptic
substitute either, since the transition alone already tells the story.

`session_started` and `session_ended` are gone from both `strings.xml`
files — no other caller referenced them (`MainScreen.kt` had a leftover
unused `sessionStartedText` val from before the toast moved to
`MainActivity`, cleaned up in the same pass). Verified on the emulator:
starting a session, letting the block screen close on its own, and
unlocking with the password all now transition silently.


## `loadApps()` only excluded the running flavor, not both

Reported directly: "nella lista delle app deve essere escluso 'Calm
Otter', è sempre abilitato come il phone" — seen with `beta` and `stable`
installed side by side, which `channel`'s whole reason to exist is to
support (see CLAUDE.md). `loadApps()`'s exclusion filter compared each
installed launcher activity's package against `packageName` (this
Activity's own — i.e. whichever flavor is currently running) and against
the default dialer; correct for a single install, but with both flavors
present the *other* one is a distinct `applicationId` and sailed straight
through the filter, showing up as an ordinary toggleable app named "Calm
Otter" — exactly the dialer problem the existing comment already warned
about, just for the app's own other self instead of the phone.

Fixed by deriving `ownBasePackage = packageName.removeSuffix(".beta")` and
excluding both `ownBasePackage` and `"$ownBasePackage.beta"` — works from
either flavor (`removeSuffix` is a no-op running as `stable`) without
hardcoding both `applicationId`s twice. Verified on the emulator with both
flavors installed: beta's list no longer shows "Calm Otter" between
Calendar and Camera.
