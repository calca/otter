# Multi-Theme System — Design

## Key files

| File | Role |
|---|---|
| `ThemeManager.kt` | Reads/writes the chosen `AppTheme` (plain `SharedPreferences`); resolves the correct `@style` for an Activity+variant combination |
| `BaseActivity.kt` | Calls `ThemeManager.applyTheme()` before `super.onCreate()` |
| `res/values/themes.xml` (+ `values-night/themes.xml`) | 3 palettes × 3 variants of AppCompat/Material3 XML themes |
| `res/values/colors.xml` | Color values referenced by `themes.xml` (`sage_*`, `lavender_*`, `terracotta_*`, plus shared `m3_*` structural colors) |
| `ui/theme/CalmOtterTheme.kt` | Independent Compose `MaterialExpressiveTheme` color schemes, one per palette × light/dark |

## Two parallel systems — this is intentional, keep them in sync manually

Every screen is Compose content (see CLAUDE.md), but each Activity is still
an `AppCompatActivity` with a real Android window/status bar/splash that
needs a theme applied *before* Compose ever renders — that's what
`BaseActivity.onCreate()` → `ThemeManager.applyTheme()` → `setTheme(styleId)`
does, using the XML `themes.xml`/`colors.xml` system. Once inside Compose,
`CalmOtterTheme(appTheme = ...)` wraps content in a `MaterialTheme` built
from **hardcoded** `Color(...)` literals in `CalmOtterTheme.kt` — it does
**not** read `@color/*` resources.

**Consequence for future changes**: editing a palette color in
`colors.xml`/`themes.xml` alone changes the window chrome but not the
Compose content (or vice versa). Any palette color change must be mirrored
in both places by hand — each `Color(...)` literal in `CalmOtterTheme.kt`
carries a `// @color/xxx` comment naming the XML value it was copied from,
specifically so this can be checked at a glance.

**Also non-obvious**: `values/themes.xml` (light) and `values-night/themes.xml`
(dark) are not the same design language — only the light file was updated
by the "Desing.md" M3 redesign; the night file still uses the pre-redesign
MaterialComponents attributes (`colorPrimaryVariant`,
`android:textColorPrimary/Secondary`) with hardcoded hex values, not
`@color/*` references into a `values-night/colors.xml` (that file doesn't
exist — the one that used to exist held only unrelated dead `pause_*`
colors and was removed). Don't assume light and dark hex values for the
same palette should look like tint/shade variants of each other; they were
authored separately. `CalmOtterTheme.kt`'s light/dark schemes correctly
mirror this split (`*Light` from `values/`, `*Dark` from `values-night/`).

## `ThemeVariant`

Three variants per palette, chosen per-Activity via
`override val themeVariant`:
- `BASE` — plain `NoActionBar` theme (most Compose screens draw their own top bar or none)
- `WITH_ACTION_BAR` — settings-style screens (`HistoryActivity`,
  `ChangePasswordActivity`, `AllowedAppsActivity`, `OnboardingActivity`) that
  use the native `supportActionBar` for a title + Up button
- `BLOCK` — the full-bleed block screen (`BlockOverlayActivity`,
  `HomeActivity`)

## Material 3 Expressive

`CalmOtterTheme` wraps content in `MaterialExpressiveTheme` (not plain
`MaterialTheme`), giving every Material3 component in the app the
Expressive motion scheme, shapes, and typography defaults automatically —
one theme-level switch, not a per-screen/per-component change. This
requires `material3` 1.5.0-alpha, pulled in via
`androidx.compose:compose-bom-alpha:2026.09.00` in `app/build.gradle.kts`
instead of the stable `compose-bom`. That in turn required bumping
compileSdk/targetSdk to 37 (`compileSdkMinor = 1`), AGP to 9.4.0, and the
Gradle wrapper to 9.7.0 — the alpha Compose artifacts compile against a
newer Android API surface and require a newer AGP than the stable channel
did.

**This is a deliberate alpha dependency, not an oversight.** As of this
writing, stable `material3` (1.4.0) does **not** expose
`MaterialExpressiveTheme` or `MotionScheme.expressive()`/`standard()` to
app code — verified by decompiling the actual resolved jar: the methods
exist in the bytecode but are Kotlin `internal`, inaccessible outside the
material3 module. They only become public starting in the 1.5.0 alpha
line. Revisit this once material3 1.5.0 (or whichever version stabilizes
Expressive) ships stable, and consider moving back to the stable
`compose-bom` (and re-evaluating whether the AGP/Gradle/compileSdk bump is
still needed) at that point.

## Theme picker UI

`SettingsScreen`'s `ThemePicker`/`ThemeDot` (moved there from `MainScreen`
by the Home/Settings split, see `specs/home-and-settings/design.md`) wrap
the existing `theme_dot_*.xml` `StateListDrawable`s (selection ring) via
`AndroidView` rather than reproducing the ring in Compose — this was a
deliberate choice to stay pixel-identical to the pre-Compose version rather
than risk a subtly different visual.
