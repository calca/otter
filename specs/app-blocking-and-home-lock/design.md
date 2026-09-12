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
password field, unlock) — the only differences are what happens *after*:
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
  `onLaunchApp: (String) -> Unit = {}`. The section they drive only renders
  `if (allowedApps.isNotEmpty())`, so `BlockOverlayActivity` — and
  `HomeActivity` on a session with no allowed apps configured — render
  identically to before.
- **`AllowedAppLaunchItem(label, packageName)`** (in `BlockScreen.kt`) is
  deliberately just those two fields — no icon, unlike `AppItem` in
  `AllowedAppsScreen.kt` — per explicit direction that this list should
  read as plain text to consult, not a mini app-drawer to browse: reducing
  visual pull is the point, since this sits on the same screen whose whole
  purpose is discouraging phone use.
- **`HomeActivity.loadAllowedAppLaunchItems()`** resolves labels only for
  packages already in `AllowedAppsManager.getAllowedPackages()` — a
  handful of entries, unlike `AllowedAppsActivity.loadApps()`'s full
  installed-app enumeration — so it runs synchronously on the main thread
  rather than dispatching to `Dispatchers.IO`. A package that was allowed
  but has since been uninstalled is dropped silently
  (`getApplicationInfo()` throwing is caught and mapped to `null`, filtered
  out via `mapNotNull`).
- **`HomeActivity.launchAllowedApp()`** just calls
  `packageManager.getLaunchIntentForPackage(packageName)` and starts it,
  swallowing a null/failed intent silently (package became unlaunchable
  between list-load and tap) rather than surfacing an error — the user
  stays on `BlockScreen`, same as if they'd tapped nothing.
- Placed in `BlockScreen` *after* the Unlock button, not above the
  countdown/phrase — it's a secondary affordance, the primary one being
  either waiting out the session or having the accountability partner
  unlock it.
