# App Blocking & Home Lock — Design

## Key files

| File | Role |
|---|---|
| `AppBlockerAccessibilityService.kt` | Watches `TYPE_WINDOW_STATE_CHANGED` events; launches `BlockOverlayActivity` for non-exempt foreground apps |
| `AllowedAppsManager.kt` | Plain-`SharedPreferences` set of extra allowed package names |
| `AllowedAppsActivity.kt` / `ui/screens/AllowedAppsScreen.kt` | Password-gated editor; loads all launcher-intent activities off the main thread |
| `BlockOverlayActivity.kt` | Hosts `BlockScreen` for the "blocked app" case |
| `MainActivity.kt` | Also hosts `BlockScreen`, for the "Home button pressed during an active session" case (see "One Activity, two roles" below) — a separate `HomeActivity` class used to own this, merged into `MainActivity` on request |
| `LauncherManager.kt` | Remembers the device's pre-CalmOtter default launcher package |
| `ui/screens/BlockScreen.kt` | Shared Composable for both block entry points |

## Blocking decision (`AppBlockerAccessibilityService.allowedPackages()`)

Always-exempt set, recomputed per event (not cached):
`TelecomManager.defaultDialerPackage`, `com.android.systemui`, `android`,
Calm Otter's own package — unioned with
`AllowedAppsManager.getAllowedPackages()`. If `sessionManager.isSessionActive()`
is false, the service returns immediately without even computing the exempt
set (cheap common case).

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
  button. If so and no session is active, it forwards to the original
  launcher and finishes (`HomeActivity`'s old idle behavior) — `onCreate()`
  checks `isFinishing` right after and skips `setContent {}` if so. If so
  and a session *is* active, it sets `showBlockForHome = true` (a
  `mutableStateOf<Boolean>`) and rolls a fresh phrase into
  `blockPhraseText`; otherwise (opened via the launcher icon, any session
  state) it's `false`. `setContent {}`'s single composition branches on
  `showBlockForHome`: `BlockScreen` vs `MainScreen`. Because both are
  `mutableStateOf`, flipping them from `onNewIntent()` on an already-live
  instance recomposes the right content directly — no `recreate()` needed,
  and none should be added; that would defeat the point of reusing the
  instance and cause a visible flicker.
- **Opening the app via its icon during an active session still shows
  `MainScreen`'s "Paused" view, never `BlockScreen`** — `showBlockForHome`
  is only ever set from the `CATEGORY_HOME` branch, so a `LAUNCHER` intent
  can never trigger it regardless of session state. This was the one
  behavior that had to survive the merge exactly as-is.
- **The two behavioral differences between the two old callers survive
  unchanged**, just now living in the one class: `BlockOverlayActivity`
  shows a toast on natural session expiry, this path (via
  `forwardToOriginalLauncher()`) does not; `BlockOverlayActivity.finish()`s
  itself back to whatever was blocked, this path forwards to the original
  launcher via `LauncherManager.getOriginalLauncherPackage()`. Both are
  still injected into the shared `BlockScreen` as lambdas
  (`onExpiredImmediately`, `onExpiredNaturally`, `onUnlocked`), not
  duplicated Composable logic.
- **Verified on-device, not just by reading the diff** (this touches
  Android task/launchMode semantics, easy to get subtly wrong): icon-open
  idle, icon-open with active session (Paused view, not block), Home-press
  idle (forwards away), Home-press with active session (`BlockScreen`),
  Home-press *again* while already showing `BlockScreen` (reuses the
  instance via `onNewIntent`, fresh phrase, no duplicate/leaked instance),
  unlocking from that screen, and normal Settings/History navigation
  (`singleTask` doesn't break the ability to push child activities or
  back-navigate out of them).
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

`SettingsActivity.promptSetAsHome()` (moved here from `MainActivity`, see
`home-and-settings/design.md`) disables then re-enables `MainActivity`'s
own component (`COMPONENT_ENABLED_STATE_DISABLED` → `_ENABLED`) to force
Android's Home-app chooser to reappear even if the user previously
dismissed it, then fires a `CATEGORY_HOME` intent so the chooser shows
immediately — see "One Activity, two roles" above for why this now
targets `MainActivity` rather than a separate `HomeActivity`.

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
  through three looks before landing here: text-only rows (too long/lost
  focus), then real app icons desaturated via
  `ColorFilter.tint(primary, BlendMode.Color)` (a duotone effect), and
  finally **2-letter initial badges** — `app.label.trim().take(2).uppercase()`
  rendered in a solid `primary`-filled circle with `onPrimary` text,
  the exact same badge construction as the Unlock control next to it (see
  below) rather than a distinct style for "app" vs "action". This also
  means no icon/`Bitmap` is loaded at all anymore — `AllowedAppLaunchItem`
  only carries what's needed to derive the initials and launch the app.
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
- **The phone is always the first item, outside the 5-app cap.** Resolved
  separately via `TelecomManager.defaultDialerPackage` (the same call
  `AppBlockerAccessibilityService.allowedPackages()` already uses to exempt
  it from blocking) rather than reading it from
  `AllowedAppsManager` — it was always implicitly allowed, but before this
  list existed there was nowhere to actually *launch* it from when Calm
  Otter is Home. Deduplicated against the configurable list by package name
  in case a user had also explicitly whitelisted their dialer.
- **`AllowedAppsManager.MAX_ALLOWED_APPS = 5`** is the single source of
  truth for the cap, enforced where an app is actually *added*
  (`AllowedAppsActivity.toggleApp()` — attempting a 6th shows a
  `allowed_apps_limit_reached` toast and does nothing, rather than
  accepting it and truncating the display elsewhere) so the stored
  whitelist and what `BlockScreen` shows never disagree. The phone doesn't
  count against it (see above).
- **`MainActivity.launchAllowedApp()`** just calls
  `packageManager.getLaunchIntentForPackage(packageName)` and starts it,
  swallowing a null/failed intent silently (package became unlaunchable
  between list-load and tap) rather than surfacing an error — the user
  stays on `BlockScreen`, same as if they'd tapped nothing.
- Placed in `BlockScreen` *after* the countdown/phrase, as its own labeled
  row — originally below a separate always-visible password field and
  Unlock button, since folded into the row itself (see next section).

## Unlock moved into the actions row, behind a dialog

Originally `BlockScreen` had an always-visible `OutlinedTextField` +
`Button("Unlock")` sitting in the main flow, with the allowed-apps row (if
any) below it. Reworked so Unlock lives in the *same* row as the allowed
apps, **last** (rightmost) rather than first — the allowed-app badges are
listed first, Unlock always trails them — as a solid-`primary`-badge lock
icon, the same circular badge construction as `PausePawsMark`/`PactPawsMark`
(filled `primary` circle, `onPrimary` content) and now also the same
construction the allowed-app initial badges use (see above) — the whole
row reads as one consistent set of round action badges, distinguished by
their content (a lock glyph vs. 2 letters), not by a different visual
treatment for "the important one". Tapping the lock badge opens a Compose
`AlertDialog` containing the password field, error/lockout text, and the
actual "Unlock"/"Cancel" buttons, instead of keeping that content
permanently on-screen.

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
- The row's caption (`block_actions_label` / plain `unlock` string when
  `allowedApps` is empty) and the row itself are always shown — unlike the
  old inline Unlock button+field, which were also always shown, this isn't
  a behavior change, just a relocation into a more compact control.
