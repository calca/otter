# App Blocking & Home Lock — Design

## Key files

| File | Role |
|---|---|
| `AppBlockerAccessibilityService.kt` | Watches `TYPE_WINDOW_STATE_CHANGED` events; launches `BlockOverlayActivity` for non-exempt foreground apps |
| `AllowedAppsManager.kt` | Plain-`SharedPreferences` set of extra allowed package names |
| `AllowedAppsActivity.kt` / `ui/screens/AllowedAppsScreen.kt` | Password-gated editor; loads all launcher-intent activities off the main thread |
| `BlockOverlayActivity.kt` | Hosts `BlockScreen` for the "blocked app" case |
| `HomeActivity.kt` | Hosts the same `BlockScreen` for the "Home button" case, or forwards to the original launcher |
| `LauncherManager.kt` | Remembers the device's pre-CalmOtter default launcher package |
| `ui/screens/BlockScreen.kt` | Shared Composable for both block entry points |

## Blocking decision (`AppBlockerAccessibilityService.allowedPackages()`)

Always-exempt set, recomputed per event (not cached):
`TelecomManager.defaultDialerPackage`, `com.android.systemui`, `android`,
Calm Otter's own package — unioned with
`AllowedAppsManager.getAllowedPackages()`. If `sessionManager.isSessionActive()`
is false, the service returns immediately without even computing the exempt
set (cheap common case).

## Why `BlockOverlayActivity` and `HomeActivity` share `BlockScreen`

Both entry points need identical behavior once shown (countdown, phrase,
lock/unlock action, allowed-apps row) — the only differences are what happens *after*:
`BlockOverlayActivity.finish()`s itself (returns to whatever was blocked);
`HomeActivity` forwards to the original launcher on unlock/expiry via
`LauncherManager.getOriginalLauncherPackage()`. These differences are
injected as lambdas (`onExpiredImmediately`, `onExpiredNaturally`,
`onUnlocked`) rather than duplicating the Composable. `BlockOverlayActivity`
additionally shows a toast on natural expiry; `HomeActivity` does not (see
each file's lambda wiring — this is the one behavioral difference between
the two callers).

## `LauncherManager` detection

`saveOriginalLauncherIfNeeded()` queries every activity that declares
`ACTION_MAIN` + `CATEGORY_HOME` via `queryIntentActivities`, which returns
**all** Home-capable apps regardless of which one is currently the actual
default — this is why detection still works even if called after Calm Otter
has already become the default Home app. It only writes once (`if
(prefs.contains(KEY_PACKAGE)) return`), so it must be called before Calm
Otter has become Home for the first time to capture the real original
launcher; `MainActivity.onCreate()` calls it unconditionally, before the
"Set as Home" row (now in Settings, see `home-and-settings/design.md`'s
"Permissions off Home" — it moved off Home along with the rest of the
permission/Home-app status) is ever tapped.

## Setting Calm Otter as Home

`SettingsActivity.promptSetAsHome()` (moved here from `MainActivity`, see
`home-and-settings/design.md`) disables then re-enables `HomeActivity`'s
component (`COMPONENT_ENABLED_STATE_DISABLED` → `_ENABLED`) to force
Android's Home-app chooser to reappear even if the user previously
dismissed it, then fires a `CATEGORY_HOME` intent so the chooser shows
immediately.

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

## Launching an allowed app from `HomeActivity`: `BlockScreen.allowedApps`

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
  `BlockOverlayActivity` (which never has any) and `HomeActivity` with no
  configured allowed apps both read naturally as "just an unlock control",
  not a broken empty list.
- **`AllowedAppLaunchItem(label, packageName, icon: Bitmap)`** (in
  `BlockScreen.kt`) started out text-only (no icon) on the theory that
  plain text would read as more low-key than icons; revised after seeing
  it on-device to icons after all, laid out in a single `Row`, not a
  vertical list — recognizable at a glance without the length of a text
  list. Colored via `ColorFilter.tint(MaterialTheme.colorScheme.primary,
  BlendMode.Color)` rather than a flat grayscale desaturation
  (`ColorMatrix().setToSaturation(0f)`, the first attempt): `BlendMode.Color`
  takes hue+saturation from the tint and luminance from the source icon —
  the standard Android duotone trick — so the icons read as muted/low-key
  like a plain desaturation would, but tinted toward whichever palette
  (Sage/Lavender/Terracotta) is active instead of a fixed neutral gray,
  consistent with every other mark in the app reading from `primary`
  rather than an uncustomized/neutral role. `icon` is loaded the same way
  `AppItem.icon` is in `AllowedAppsScreen.kt`
  (`loadIcon(packageManager).toBitmap()`), converted to `ImageBitmap` only
  at draw time via `.asImageBitmap()`.
- **`HomeActivity.loadAllowedAppLaunchItems()`** resolves labels/icons only
  for packages already in `AllowedAppsManager.getAllowedPackages()` — a
  handful of entries, unlike `AllowedAppsActivity.loadApps()`'s full
  installed-app enumeration — so it runs synchronously on the main thread
  rather than dispatching to `Dispatchers.IO`. A package that was allowed
  but has since been uninstalled is dropped silently
  (`getApplicationInfo()` throwing is caught and mapped to `null`, filtered
  out via `mapNotNull`). The list is also `.take(AllowedAppsManager.MAX_ALLOWED_APPS)`
  as a safety net — the real cap is enforced at write time (see below), this
  should never actually trim anything in practice.
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
- **`HomeActivity.launchAllowedApp()`** just calls
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
apps, as a solid-`primary`-badge lock icon (same circular badge
construction as `PausePawsMark`/`PactPawsMark` — filled `primary` circle,
`onPrimary` icon — deliberately more visually prominent than the
desaturated app icons next to it, since it's the primary action, not a
utility) — tapping it opens a Compose `AlertDialog` containing the
password field, error/lockout text, and the actual "Unlock"/"Cancel"
buttons, instead of keeping that content permanently on-screen.

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
