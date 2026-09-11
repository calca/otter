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
"Set as Home" button is ever pressed.

## Setting Calm Otter as Home

`MainActivity.promptSetAsHome()` disables then re-enables
`HomeActivity`'s component (`COMPONENT_ENABLED_STATE_DISABLED` →
`_ENABLED`) to force Android's Home-app chooser to reappear even if the
user previously dismissed it, then fires a `CATEGORY_HOME` intent so the
chooser shows immediately.

## Allowed-apps loading

`AllowedAppsActivity.loadApps()` runs on `Dispatchers.IO` via
`lifecycleScope.launch`, converting each app icon `Drawable` to a `Bitmap`
up front (`AppItem.icon`) since Compose needs a stable value, not a
lazily-decoded drawable, for list rendering. Toggling a row saves
immediately (`allowedAppsManager.setAllowedPackages`) and rebuilds the
in-memory list with `.map { it.copy(...) }` since `AppItem` is immutable.

**Manifest gap**: no `<queries>` element declares visibility into other
apps' `ACTION_MAIN`/`CATEGORY_LAUNCHER` activities. Under Android 11+
package visibility rules this can silently truncate the list `loadApps()`
builds — see requirements.md "Known limits" before changing targetSdk
behavior around this.
